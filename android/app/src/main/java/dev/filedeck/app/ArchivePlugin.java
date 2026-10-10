package dev.filedeck.app;

import android.os.SystemClock;

import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.exception.ZipException;
import net.lingala.zip4j.io.inputstream.ZipInputStream;
import net.lingala.zip4j.io.outputstream.ZipOutputStream;
import net.lingala.zip4j.model.FileHeader;
import net.lingala.zip4j.model.LocalFileHeader;
import net.lingala.zip4j.model.ZipParameters;
import net.lingala.zip4j.model.enums.AesKeyStrength;
import net.lingala.zip4j.model.enums.CompressionLevel;
import net.lingala.zip4j.model.enums.CompressionMethod;
import net.lingala.zip4j.model.enums.EncryptionMethod;

import org.apache.commons.compress.PasswordRequiredException;
import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry;
import org.apache.commons.compress.archivers.sevenz.SevenZFile;
import org.apache.commons.compress.archivers.sevenz.SevenZMethod;
import org.apache.commons.compress.archivers.sevenz.SevenZMethodConfiguration;
import org.apache.commons.compress.archivers.sevenz.SevenZOutputFile;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipParameters;
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream;
import org.apache.commons.compress.compressors.xz.XZCompressorOutputStream;

import org.tukaani.xz.LZMA2Options;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Архиватор FileDeck.
 *
 * Создание: ZIP (в т. ч. с паролем AES-256), 7z, TAR, TAR.GZ, TAR.BZ2, TAR.XZ.
 * Распаковка и просмотр: ZIP (с паролем), 7z (с паролем), TAR/TGZ/TBZ2/TXZ, одиночные GZ/BZ2/XZ, RAR (v4, через junrar).
 *
 * Все операции потоковые, с прогрессом (событие archiveProgress) и отменой. Распаковка защищена от Zip Slip,
 * символические ссылки внутри архивов пропускаются, при ошибке недоделанный результат удаляется.
 */
@CapacitorPlugin(name = "Archive")
public class ArchivePlugin extends Plugin {

    private static final int BUF = 128 * 1024;
    private static final int MAX_LIST = 20000;

    private final ExecutorService pool = Executors.newFixedThreadPool(2);
    private volatile boolean cancelRequested = false;
    private volatile boolean busy = false;
    private long lastEmit = 0;

    enum Fmt { ZIP, SEVENZ, TAR, TGZ, TBZ2, TXZ, GZ, BZ2, XZ, RAR, UNKNOWN }

    /** Ошибка с кодом, который понимает интерфейс (PASSWORD_REQUIRED, WRONG_PASSWORD, ...). */
    static final class ArchiveError extends IOException {
        final String code;

        ArchiveError(String code, String message) {
            super(message);
            this.code = code;
        }
    }

    private static final class Cancelled extends RuntimeException {
        Cancelled() {
            super("cancelled");
        }
    }

    private interface Job {
        JSObject run() throws Exception;
    }

    @Override
    protected void handleOnDestroy() {
        cancelRequested = true;
        pool.shutdownNow();
        super.handleOnDestroy();
    }

    private void checkCancel() {
        if (cancelRequested) throw new Cancelled();
    }

    @PluginMethod
    public void cancel(PluginCall call) {
        cancelRequested = true;
        call.resolve();
    }

    /** Запускает тяжёлую операцию в пуле; по одной операции за раз. */
    private void run(final PluginCall call, final Job job) {
        if (busy) {
            call.reject("Другая операция с архивом ещё выполняется");
            return;
        }
        busy = true;
        cancelRequested = false;
        pool.execute(() -> {
            JSObject result = null;
            String errMsg = null;
            String errCode = null;
            try {
                result = job.run();
            } catch (Cancelled c) {
                result = new JSObject();
                result.put("cancelled", true);
            } catch (ArchiveError e) {
                errMsg = e.getMessage();
                errCode = e.code;
            } catch (Throwable t) {
                String msg = t.getMessage();
                errMsg = msg == null || msg.isEmpty() ? t.getClass().getSimpleName() : msg;
                errCode = "FAILED";
            }
            // флаг снимаем ДО ответа: следующий вызов из интерфейса приходит сразу после resolve
            busy = false;
            if (errMsg != null) call.reject(errMsg, errCode);
            else call.resolve(result);
        });
    }

    private void emit(String op, double fraction, String current, boolean force) {
        long now = SystemClock.elapsedRealtime();
        if (!force && now - lastEmit < 120) return;
        lastEmit = now;
        JSObject o = new JSObject();
        o.put("op", op);
        o.put("fraction", Math.max(0, Math.min(1, fraction)));
        if (current != null) o.put("current", current);
        notifyListeners("archiveProgress", o);
    }

    // =====================================================================================
    //  Форматы
    // =====================================================================================

    static Fmt detect(File f) {
        String n = f.getName().toLowerCase(Locale.ROOT);
        if (n.endsWith(".zip") || n.endsWith(".jar") || n.endsWith(".cbz") || n.endsWith(".apk")) return Fmt.ZIP;
        if (n.endsWith(".7z")) return Fmt.SEVENZ;
        if (n.endsWith(".rar")) return Fmt.RAR;
        if (n.endsWith(".tar.gz") || n.endsWith(".tgz")) return Fmt.TGZ;
        if (n.endsWith(".tar.bz2") || n.endsWith(".tbz2") || n.endsWith(".tbz")) return Fmt.TBZ2;
        if (n.endsWith(".tar.xz") || n.endsWith(".txz")) return Fmt.TXZ;
        if (n.endsWith(".tar")) return Fmt.TAR;
        if (n.endsWith(".gz")) return Fmt.GZ;
        if (n.endsWith(".bz2")) return Fmt.BZ2;
        if (n.endsWith(".xz")) return Fmt.XZ;
        return sniff(f);
    }

    private static Fmt sniff(File f) {
        byte[] b = new byte[8];
        int n;
        try (InputStream in = new FileInputStream(f)) {
            n = in.read(b);
        } catch (IOException e) {
            return Fmt.UNKNOWN;
        }
        if (n >= 4 && b[0] == 'P' && b[1] == 'K' && b[2] == 3 && b[3] == 4) return Fmt.ZIP;
        if (n >= 6 && (b[0] & 0xFF) == 0x37 && (b[1] & 0xFF) == 0x7A && (b[2] & 0xFF) == 0xBC && (b[3] & 0xFF) == 0xAF) return Fmt.SEVENZ;
        if (n >= 4 && b[0] == 'R' && b[1] == 'a' && b[2] == 'r' && b[3] == '!') return Fmt.RAR;
        if (n >= 2 && (b[0] & 0xFF) == 0x1F && (b[1] & 0xFF) == 0x8B) return Fmt.GZ;
        if (n >= 3 && b[0] == 'B' && b[1] == 'Z' && b[2] == 'h') return Fmt.BZ2;
        if (n >= 6 && (b[0] & 0xFF) == 0xFD && b[1] == '7' && b[2] == 'z' && b[3] == 'X' && b[4] == 'Z') return Fmt.XZ;
        return Fmt.UNKNOWN;
    }

    private static Fmt formatByName(String s) {
        switch (s == null ? "" : s.toLowerCase(Locale.ROOT)) {
            case "zip":
                return Fmt.ZIP;
            case "7z":
                return Fmt.SEVENZ;
            case "tar":
                return Fmt.TAR;
            case "tar.gz":
            case "tgz":
                return Fmt.TGZ;
            case "tar.bz2":
            case "tbz2":
                return Fmt.TBZ2;
            case "tar.xz":
            case "txz":
                return Fmt.TXZ;
            default:
                return Fmt.UNKNOWN;
        }
    }

    private static String extFor(Fmt f) {
        switch (f) {
            case ZIP:
                return ".zip";
            case SEVENZ:
                return ".7z";
            case TAR:
                return ".tar";
            case TGZ:
                return ".tar.gz";
            case TBZ2:
                return ".tar.bz2";
            case TXZ:
                return ".tar.xz";
            default:
                return "";
        }
    }

    private static String stripArchiveExt(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        String[] exts = { ".tar.gz", ".tar.bz2", ".tar.xz", ".tgz", ".tbz2", ".tbz", ".txz", ".zip", ".7z", ".rar", ".tar", ".gz", ".bz2", ".xz", ".jar", ".cbz" };
        for (String e : exts) {
            if (lower.endsWith(e) && name.length() > e.length()) return name.substring(0, name.length() - e.length());
        }
        return name;
    }

    // =====================================================================================
    //  СОЗДАНИЕ
    // =====================================================================================

    private static final class Item {
        final File file;
        final String name;
        final boolean dir;

        Item(File file, String name, boolean dir) {
            this.file = file;
            this.name = name;
            this.dir = dir;
        }
    }

    private interface Sink {
        void dir(String name, long mtime) throws IOException;

        void begin(String name, File f) throws IOException;

        void write(byte[] b, int off, int len) throws IOException;

        void end() throws IOException;

        void close() throws IOException;
    }

    @PluginMethod
    public void create(final PluginCall call) {
        final List<String> paths = strings(call.getArray("paths"));
        final String destDir = call.getString("dest");
        final String name = call.getString("name", "archive");
        final Fmt fmt = formatByName(call.getString("format", "zip"));
        final String password = call.getString("password", "");
        final Integer lv = call.getInt("level", 5);
        final int level = lv == null ? 5 : lv;
        run(call, () -> {
            if (paths.isEmpty()) throw new ArchiveError("EMPTY", "Нечего архивировать");
            if (fmt == Fmt.UNKNOWN) throw new ArchiveError("FORMAT", "Неизвестный формат архива");
            if (destDir == null) throw new ArchiveError("DEST", "Не указана папка");
            File dest = new File(destDir);
            if (!dest.isDirectory()) throw new ArchiveError("DEST", "Папка назначения недоступна");
            boolean usePassword = password != null && !password.isEmpty();
            if (usePassword && fmt != Fmt.ZIP) throw new ArchiveError("PASSWORD_UNSUPPORTED", "Пароль поддерживается только для ZIP");

            String base = stripArchiveExt(name.trim().isEmpty() ? "archive" : name.trim()).replace("/", "_");
            File target = FsUtil.uniqueTarget(dest, base + extFor(fmt));
            String targetCanon = target.getCanonicalPath();

            List<Item> items = new ArrayList<>();
            long total = 0;
            for (String p : paths) {
                File f = new File(p);
                if (!f.exists()) continue;
                total += collect(f, f.getName(), items, targetCanon);
            }
            if (items.isEmpty()) throw new ArchiveError("EMPTY", "Нечего архивировать");

            Sink sink = null;
            try {
                sink = openSink(fmt, target, usePassword ? password : null, level);
                long done = 0;
                byte[] buf = new byte[BUF];
                for (Item it : items) {
                    checkCancel();
                    if (it.dir) {
                        sink.dir(it.name, it.file.lastModified());
                        continue;
                    }
                    sink.begin(it.name, it.file);
                    try (InputStream in = new FileInputStream(it.file)) {
                        int n;
                        while ((n = in.read(buf)) > 0) {
                            sink.write(buf, 0, n);
                            done += n;
                            checkCancel();
                            emit("create", total > 0 ? (double) done / total : 0, it.name, false);
                        }
                    }
                    sink.end();
                }
                sink.close();
                sink = null;
            } catch (Throwable t) {
                if (sink != null) {
                    try {
                        sink.close();
                    } catch (Throwable ignored) {
                        // уже в ошибке
                    }
                }
                //noinspection ResultOfMethodCallIgnored
                target.delete();
                throw t;
            }
            emit("create", 1, null, true);
            JSObject ret = new JSObject();
            ret.put("path", target.getAbsolutePath());
            ret.put("size", target.length());
            ret.put("items", items.size());
            return ret;
        });
    }

    /** Собирает файлы для архива, возвращает суммарный размер файлов. */
    private long collect(File f, String name, List<Item> out, String skipCanon) throws IOException {
        if (f.getCanonicalPath().equals(skipCanon)) return 0;
        if (f.isDirectory()) {
            if (FsUtil.isSymlink(f)) return 0;
            out.add(new Item(f, name, true));
            long sum = 0;
            File[] ch = f.listFiles();
            if (ch != null) {
                java.util.Arrays.sort(ch);
                for (File c : ch) sum += collect(c, name + "/" + c.getName(), out, skipCanon);
            }
            return sum;
        }
        if (!f.isFile()) return 0;
        out.add(new Item(f, name, false));
        return f.length();
    }

    private Sink openSink(Fmt fmt, File target, String password, int level) throws IOException {
        switch (fmt) {
            case ZIP:
                return new ZipSink(target, password, level);
            case SEVENZ:
                return new SevenZSink(target, level);
            default:
                return new TarSink(fmt, target, level);
        }
    }

    private static final class ZipSink implements Sink {
        private final ZipOutputStream zos;
        private final boolean encrypt;
        private final CompressionLevel lvl;

        ZipSink(File target, String password, int level) throws IOException {
            OutputStream fos = new BufferedOutputStream(new FileOutputStream(target), BUF);
            encrypt = password != null;
            zos = encrypt ? new ZipOutputStream(fos, password.toCharArray()) : new ZipOutputStream(fos);
            lvl = level <= 2 ? CompressionLevel.FASTEST : level >= 8 ? CompressionLevel.ULTRA : CompressionLevel.NORMAL;
        }

        private ZipParameters params(String name, long mtime, boolean forDir) {
            ZipParameters p = new ZipParameters();
            p.setCompressionMethod(CompressionMethod.DEFLATE);
            p.setCompressionLevel(lvl);
            p.setFileNameInZip(name);
            p.setLastModifiedFileTime(mtime);
            if (encrypt && !forDir) {
                p.setEncryptFiles(true);
                p.setEncryptionMethod(EncryptionMethod.AES);
                p.setAesKeyStrength(AesKeyStrength.KEY_STRENGTH_256);
            }
            return p;
        }

        @Override
        public void dir(String name, long mtime) throws IOException {
            zos.putNextEntry(params(name + "/", mtime, true));
            zos.closeEntry();
        }

        @Override
        public void begin(String name, File f) throws IOException {
            ZipParameters p = params(name, f.lastModified(), false);
            p.setEntrySize(f.length());
            zos.putNextEntry(p);
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            zos.write(b, off, len);
        }

        @Override
        public void end() throws IOException {
            zos.closeEntry();
        }

        @Override
        public void close() throws IOException {
            zos.close();
        }
    }

    private static final class SevenZSink implements Sink {
        private final SevenZOutputFile out;

        SevenZSink(File target, int level) throws IOException {
            out = new SevenZOutputFile(target);
            int preset = level <= 2 ? 1 : level >= 8 ? 9 : 6;
            // Integer в SevenZMethodConfiguration означает размер словаря, а не уровень, поэтому передаём LZMA2Options
            out.setContentMethods(Collections.singletonList(new SevenZMethodConfiguration(SevenZMethod.LZMA2, new LZMA2Options(preset))));
        }

        private SevenZArchiveEntry entry(String name, boolean dir, boolean hasStream, long mtime) {
            SevenZArchiveEntry e = new SevenZArchiveEntry();
            e.setName(name);
            e.setDirectory(dir);
            e.setHasStream(hasStream);
            e.setLastModifiedDate(new Date(mtime));
            return e;
        }

        @Override
        public void dir(String name, long mtime) throws IOException {
            out.putArchiveEntry(entry(name, true, false, mtime));
            out.closeArchiveEntry();
        }

        @Override
        public void begin(String name, File f) throws IOException {
            out.putArchiveEntry(entry(name, false, f.length() > 0, f.lastModified()));
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            out.write(b, off, len);
        }

        @Override
        public void end() throws IOException {
            out.closeArchiveEntry();
        }

        @Override
        public void close() throws IOException {
            out.close();
        }
    }

    private static final class TarSink implements Sink {
        private final TarArchiveOutputStream tar;

        TarSink(Fmt fmt, File target, int level) throws IOException {
            OutputStream raw = new BufferedOutputStream(new FileOutputStream(target), BUF);
            OutputStream comp;
            try {
                switch (fmt) {
                    case TGZ: {
                        GzipParameters gp = new GzipParameters();
                        gp.setCompressionLevel(level <= 2 ? 1 : level >= 8 ? 9 : 6);
                        comp = new GzipCompressorOutputStream(raw, gp);
                        break;
                    }
                    case TBZ2:
                        comp = new BZip2CompressorOutputStream(raw, level <= 2 ? 1 : 9);
                        break;
                    case TXZ:
                        comp = new XZCompressorOutputStream(raw, level <= 2 ? 1 : level >= 8 ? 9 : 6);
                        break;
                    default:
                        comp = raw;
                        break;
                }
            } catch (IOException e) {
                raw.close();
                throw e;
            }
            tar = new TarArchiveOutputStream(comp, "UTF-8");
            tar.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX);
            tar.setBigNumberMode(TarArchiveOutputStream.BIGNUMBER_POSIX);
        }

        @Override
        public void dir(String name, long mtime) throws IOException {
            TarArchiveEntry e = new TarArchiveEntry(name + "/");
            e.setModTime(mtime);
            tar.putArchiveEntry(e);
            tar.closeArchiveEntry();
        }

        @Override
        public void begin(String name, File f) throws IOException {
            TarArchiveEntry e = new TarArchiveEntry(name);
            e.setSize(f.length());
            e.setModTime(f.lastModified());
            tar.putArchiveEntry(e);
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            tar.write(b, off, len);
        }

        @Override
        public void end() throws IOException {
            tar.closeArchiveEntry();
        }

        @Override
        public void close() throws IOException {
            tar.close();
        }
    }

    // =====================================================================================
    //  ЧТЕНИЕ: просмотр и распаковка
    // =====================================================================================

    static final class Ent {
        String name;
        boolean dir;
        long size;
        long mtime;
        boolean link;
    }

    /** Подсчёт прочитанных байт сжатых данных — для прогресса потоковых форматов. */
    private static final class CountingIn extends InputStream {
        private final InputStream in;
        long count;

        CountingIn(InputStream in) {
            this.in = in;
        }

        @Override
        public int read() throws IOException {
            int b = in.read();
            if (b >= 0) count++;
            return b;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            int n = in.read(b, off, len);
            if (n > 0) count += n;
            return n;
        }

        @Override
        public void close() throws IOException {
            in.close();
        }
    }

    private abstract static class Source implements java.io.Closeable {
        abstract Ent next() throws IOException;

        abstract InputStream stream();

        abstract double fraction();
    }

    private Source openSource(File f, Fmt fmt, String password) throws IOException {
        switch (fmt) {
            case ZIP:
                return new ZipSource(f, password);
            case SEVENZ:
                return new SevenZSource(f, password);
            case TAR:
            case TGZ:
            case TBZ2:
            case TXZ:
                return new TarSource(f, fmt);
            case GZ:
            case BZ2:
            case XZ:
                return new SingleSource(f, fmt);
            default:
                throw new ArchiveError("FORMAT", "Этот формат не поддерживается");
        }
    }

    private static final class ZipSource extends Source {
        private final CountingIn cin;
        private final ZipInputStream zis;
        private final long length;
        private final boolean hasPassword;

        ZipSource(File f, String password) throws IOException {
            length = Math.max(1, f.length());
            cin = new CountingIn(new BufferedInputStream(new FileInputStream(f), BUF));
            hasPassword = password != null && !password.isEmpty();
            zis = hasPassword ? new ZipInputStream(cin, password.toCharArray()) : new ZipInputStream(cin);
        }

        @Override
        Ent next() throws IOException {
            LocalFileHeader h;
            try {
                h = zis.getNextEntry();
            } catch (ZipException e) {
                throw mapZip(e, hasPassword);
            }
            if (h == null) return null;
            if (h.isEncrypted() && !hasPassword) throw new ArchiveError("PASSWORD_REQUIRED", "Архив защищён паролем");
            Ent e = new Ent();
            e.name = h.getFileName();
            e.dir = h.isDirectory();
            e.size = h.getUncompressedSize();
            e.mtime = h.getLastModifiedTimeEpoch();
            return e;
        }

        @Override
        InputStream stream() {
            return new InputStream() {
                @Override
                public int read() throws IOException {
                    try {
                        return zis.read();
                    } catch (ZipException e) {
                        throw mapZip(e, hasPassword);
                    }
                }

                @Override
                public int read(byte[] b, int off, int len) throws IOException {
                    try {
                        return zis.read(b, off, len);
                    } catch (ZipException e) {
                        throw mapZip(e, hasPassword);
                    }
                }
            };
        }

        @Override
        double fraction() {
            return (double) cin.count / length;
        }

        @Override
        public void close() throws IOException {
            zis.close();
        }
    }

    private static IOException mapZip(ZipException e, boolean hadPassword) {
        if (e.getType() == ZipException.Type.WRONG_PASSWORD) {
            return hadPassword
                ? new ArchiveError("WRONG_PASSWORD", "Неверный пароль")
                : new ArchiveError("PASSWORD_REQUIRED", "Архив защищён паролем");
        }
        return e;
    }

    private static final class SevenZSource extends Source {
        private final SevenZFile sz;
        private final long total;
        private long read;

        SevenZSource(File f, String password) throws IOException {
            try {
                sz = password != null && !password.isEmpty() ? new SevenZFile(f, password.toCharArray()) : new SevenZFile(f);
            } catch (PasswordRequiredException e) {
                throw new ArchiveError("PASSWORD_REQUIRED", "Архив защищён паролем");
            }
            long t = 0;
            for (SevenZArchiveEntry e : sz.getEntries()) {
                if (!e.isDirectory()) t += Math.max(0, e.getSize());
            }
            total = Math.max(1, t);
        }

        @Override
        Ent next() throws IOException {
            SevenZArchiveEntry e;
            try {
                e = sz.getNextEntry();
            } catch (PasswordRequiredException ex) {
                throw new ArchiveError("PASSWORD_REQUIRED", "Архив защищён паролем");
            }
            if (e == null) return null;
            Ent ent = new Ent();
            ent.name = e.getName();
            ent.dir = e.isDirectory();
            ent.size = e.getSize();
            ent.mtime = e.getHasLastModifiedDate() ? e.getLastModifiedDate().getTime() : 0;
            return ent;
        }

        @Override
        InputStream stream() {
            return new InputStream() {
                private final byte[] one = new byte[1];

                @Override
                public int read() throws IOException {
                    int n = read(one, 0, 1);
                    return n <= 0 ? -1 : one[0] & 0xFF;
                }

                @Override
                public int read(byte[] b, int off, int len) throws IOException {
                    try {
                        int n = sz.read(b, off, len);
                        if (n > 0) read += n;
                        return n;
                    } catch (PasswordRequiredException ex) {
                        throw new ArchiveError("PASSWORD_REQUIRED", "Архив защищён паролем");
                    } catch (IOException ex) {
                        String m = String.valueOf(ex.getMessage()).toLowerCase(Locale.ROOT);
                        if (m.contains("password") || m.contains("padding") || m.contains("pad block") || m.contains("given final block")) {
                            throw new ArchiveError("WRONG_PASSWORD", "Неверный пароль");
                        }
                        throw ex;
                    }
                }
            };
        }

        @Override
        double fraction() {
            return (double) read / total;
        }

        @Override
        public void close() throws IOException {
            sz.close();
        }
    }

    private static final class TarSource extends Source {
        private final CountingIn cin;
        private final TarArchiveInputStream tin;
        private final long length;

        TarSource(File f, Fmt fmt) throws IOException {
            length = Math.max(1, f.length());
            cin = new CountingIn(new BufferedInputStream(new FileInputStream(f), BUF));
            InputStream comp;
            switch (fmt) {
                case TGZ:
                    comp = new GzipCompressorInputStream(cin);
                    break;
                case TBZ2:
                    comp = new BZip2CompressorInputStream(cin);
                    break;
                case TXZ:
                    comp = new XZCompressorInputStream(cin);
                    break;
                default:
                    comp = cin;
                    break;
            }
            tin = new TarArchiveInputStream(comp);
        }

        @Override
        Ent next() throws IOException {
            TarArchiveEntry e = (TarArchiveEntry) tin.getNextEntry();
            if (e == null) return null;
            Ent ent = new Ent();
            ent.name = e.getName();
            ent.dir = e.isDirectory();
            ent.size = e.getSize();
            ent.mtime = e.getModTime() == null ? 0 : e.getModTime().getTime();
            ent.link = e.isSymbolicLink() || e.isLink();
            return ent;
        }

        @Override
        InputStream stream() {
            return tin;
        }

        @Override
        double fraction() {
            return (double) cin.count / length;
        }

        @Override
        public void close() throws IOException {
            tin.close();
        }
    }

    /** Одиночный сжатый файл: .gz / .bz2 / .xz. */
    private static final class SingleSource extends Source {
        private final CountingIn cin;
        private final InputStream comp;
        private final String name;
        private final long length;
        private boolean given;

        SingleSource(File f, Fmt fmt) throws IOException {
            length = Math.max(1, f.length());
            cin = new CountingIn(new BufferedInputStream(new FileInputStream(f), BUF));
            switch (fmt) {
                case GZ:
                    comp = new GzipCompressorInputStream(cin);
                    break;
                case BZ2:
                    comp = new BZip2CompressorInputStream(cin);
                    break;
                default:
                    comp = new XZCompressorInputStream(cin);
                    break;
            }
            String n = f.getName();
            int dot = n.lastIndexOf('.');
            name = dot > 0 ? n.substring(0, dot) : n + ".out";
        }

        @Override
        Ent next() {
            if (given) return null;
            given = true;
            Ent e = new Ent();
            e.name = name;
            e.size = -1;
            return e;
        }

        @Override
        InputStream stream() {
            return comp;
        }

        @Override
        double fraction() {
            return (double) cin.count / length;
        }

        @Override
        public void close() throws IOException {
            comp.close();
        }
    }

    // ------------------------------------------------------------------ list

    @PluginMethod
    public void list(final PluginCall call) {
        final String path = call.getString("path");
        final String password = call.getString("password", "");
        run(call, () -> {
            if (path == null) throw new ArchiveError("PATH", "Не указан архив");
            File f = new File(path);
            if (!f.isFile()) throw new ArchiveError("PATH", "Архив не найден");
            Fmt fmt = detect(f);
            if (fmt == Fmt.UNKNOWN) throw new ArchiveError("FORMAT", "Формат архива не распознан");

            JSArray arr = new JSArray();
            long totalSize = 0;
            long count = 0;
            boolean encrypted = false;
            boolean listing = true;

            if (fmt == Fmt.ZIP) {
                try (ZipFile zf = new ZipFile(f)) {
                    encrypted = zf.isEncrypted();
                    for (FileHeader h : zf.getFileHeaders()) {
                        checkCancel();
                        count++;
                        if (!h.isDirectory()) totalSize += Math.max(0, h.getUncompressedSize());
                        if (arr.length() < MAX_LIST) arr.put(entryJson(h.getFileName(), h.isDirectory(), h.getUncompressedSize(), h.getLastModifiedTimeEpoch()));
                    }
                } catch (ZipException e) {
                    throw new ArchiveError("BROKEN", "Архив повреждён или это не ZIP");
                }
            } else if (fmt == Fmt.RAR) {
                listing = false;
            } else {
                try (Source src = openSource(f, fmt, password)) {
                    Ent e;
                    while ((e = src.next()) != null) {
                        checkCancel();
                        count++;
                        if (!e.dir && e.size > 0) totalSize += e.size;
                        if (arr.length() < MAX_LIST) arr.put(entryJson(e.name, e.dir, e.size, e.mtime));
                        emit("list", src.fraction(), e.name, false);
                    }
                }
            }
            JSObject ret = new JSObject();
            ret.put("format", fmt.name());
            ret.put("entries", arr);
            ret.put("count", count);
            ret.put("bytes", totalSize);
            ret.put("encrypted", encrypted);
            ret.put("listing", listing);
            ret.put("truncated", count > arr.length());
            return ret;
        });
    }

    private static JSObject entryJson(String name, boolean dir, long size, long mtime) {
        JSObject o = new JSObject();
        String clean = name.replace('\\', '/');
        while (clean.endsWith("/") && clean.length() > 1) clean = clean.substring(0, clean.length() - 1);
        o.put("name", clean);
        o.put("dir", dir);
        o.put("size", Math.max(0, size));
        o.put("mtime", mtime);
        return o;
    }

    // ------------------------------------------------------------------ extract

    @PluginMethod
    public void extract(final PluginCall call) {
        final String path = call.getString("path");
        final String destDir = call.getString("dest");
        final String password = call.getString("password", "");
        final String mode = call.getString("mode", "folder"); // folder | here
        final Set<String> only = new HashSet<>(strings(call.getArray("entries")));
        run(call, () -> {
            if (path == null || destDir == null) throw new ArchiveError("PATH", "Не указан архив или папка");
            File f = new File(path);
            File dest = new File(destDir);
            if (!f.isFile()) throw new ArchiveError("PATH", "Архив не найден");
            if (!dest.isDirectory()) throw new ArchiveError("DEST", "Папка назначения недоступна");
            Fmt fmt = detect(f);
            if (fmt == Fmt.UNKNOWN) throw new ArchiveError("FORMAT", "Формат архива не распознан");

            String base = stripArchiveExt(f.getName());
            boolean here = "here".equals(mode) && fmt != Fmt.RAR;
            File root = here ? dest : FsUtil.uniqueTarget(dest, base);
            boolean createdRoot = false;
            if (!here) {
                if (!root.mkdirs()) throw new ArchiveError("DEST", "Не удалось создать папку для распаковки");
                createdRoot = true;
            }
            Map<String, String> topMap = new HashMap<>();
            List<File> created = new ArrayList<>(); // верхнеуровневые результаты для отката в режиме here
            long count = 0;
            try {
                if (fmt == Fmt.RAR) {
                    emit("extract", 0, f.getName(), true);
                    extractRar(f, root, password);
                    count = countFiles(root);
                } else {
                    String rootCanon = root.getCanonicalPath();
                    try (Source src = openSource(f, fmt, password)) {
                        byte[] buf = new byte[BUF];
                        Ent e;
                        while ((e = src.next()) != null) {
                            checkCancel();
                            if (e.link) continue;
                            String name = cleanName(e.name);
                            if (name == null || !wanted(name, only)) continue;
                            File out = mapTarget(root, name, here, topMap, created);
                            String canon = out.getCanonicalPath();
                            if (!canon.equals(rootCanon) && !canon.startsWith(rootCanon + File.separator)) continue; // Zip Slip
                            if (e.dir) {
                                //noinspection ResultOfMethodCallIgnored
                                out.mkdirs();
                                continue;
                            }
                            File parent = out.getParentFile();
                            if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
                                throw new ArchiveError("DEST", "Не удалось создать папку " + parent.getName());
                            }
                            try (OutputStream os = new BufferedOutputStream(new FileOutputStream(out), BUF)) {
                                InputStream in = src.stream();
                                int n;
                                while ((n = in.read(buf)) > 0) {
                                    os.write(buf, 0, n);
                                    checkCancel();
                                    emit("extract", src.fraction(), name, false);
                                }
                            }
                            if (e.mtime > 0) {
                                //noinspection ResultOfMethodCallIgnored
                                out.setLastModified(e.mtime);
                            }
                            count++;
                        }
                    }
                }
            } catch (Throwable t) {
                if (createdRoot) FsUtil.deleteRecursive(root);
                else for (File c : created) FsUtil.deleteRecursive(c);
                throw t;
            }
            if (createdRoot && count == 0 && only.isEmpty() && fmt != Fmt.RAR && countFiles(root) == 0) {
                // пустой архив — оставляем пустую папку, но сообщаем об этом
                emit("extract", 1, null, true);
            }
            emit("extract", 1, null, true);
            JSObject ret = new JSObject();
            ret.put("path", root.getAbsolutePath());
            ret.put("count", count);
            return ret;
        });
    }

    private static long countFiles(File dir) {
        long n = 0;
        File[] ch = dir.listFiles();
        if (ch == null) return 0;
        for (File c : ch) n += c.isDirectory() ? countFiles(c) : 1;
        return n;
    }

    /** Приводит имя записи к безопасному относительному виду; null — запись нужно пропустить. */
    static String cleanName(String raw) {
        if (raw == null) return null;
        String n = raw.replace('\\', '/');
        while (n.startsWith("/")) n = n.substring(1);
        while (n.endsWith("/") && n.length() > 1) n = n.substring(0, n.length() - 1);
        if (n.isEmpty() || n.equals("/")) return null;
        for (String part : n.split("/")) {
            if (part.equals("..")) return null;
        }
        return n;
    }

    private static boolean wanted(String name, Set<String> only) {
        if (only.isEmpty()) return true;
        for (String o : only) {
            if (name.equals(o) || name.startsWith(o + "/")) return true;
            // родительские папки выбранных файлов создаются автоматически при записи
        }
        return false;
    }

    private File mapTarget(File root, String name, boolean here, Map<String, String> topMap, List<File> created) {
        if (!here) return new File(root, name);
        int slash = name.indexOf('/');
        String top = slash < 0 ? name : name.substring(0, slash);
        String rest = slash < 0 ? "" : name.substring(slash);
        String mapped = topMap.get(top);
        if (mapped == null) {
            File candidate = new File(root, top);
            mapped = candidate.exists() ? FsUtil.uniqueTarget(root, top).getName() : top;
            topMap.put(top, mapped);
            created.add(new File(root, mapped));
        }
        return new File(root, mapped + rest);
    }

    /** RAR распаковывается библиотекой junrar; вызываем через рефлексию, чтобы сборка не зависела от её версии. */
    private void extractRar(File rar, File destDir, String password) throws IOException {
        try {
            Class<?> c = Class.forName("com.github.junrar.Junrar");
            if (password == null || password.isEmpty()) {
                c.getMethod("extract", File.class, File.class).invoke(null, rar, destDir);
            } else {
                c.getMethod("extract", File.class, File.class, String.class).invoke(null, rar, destDir, password);
            }
        } catch (InvocationTargetException ite) {
            Throwable cause = ite.getCause() == null ? ite : ite.getCause();
            String cn = cause.getClass().getSimpleName().toLowerCase(Locale.ROOT);
            String msg = String.valueOf(cause.getMessage()).toLowerCase(Locale.ROOT);
            if (cn.contains("v5") || msg.contains("rar5") || msg.contains("v5")) {
                throw new ArchiveError("RAR5", "Формат RAR5 пока не поддерживается (RAR 4 и старше — работает)");
            }
            if (cn.contains("password") || msg.contains("password") || cn.contains("crc") || msg.contains("crc")) {
                throw new ArchiveError(password == null || password.isEmpty() ? "PASSWORD_REQUIRED" : "WRONG_PASSWORD",
                    password == null || password.isEmpty() ? "Архив защищён паролем" : "Неверный пароль");
            }
            throw new IOException("Не удалось распаковать RAR: " + cause.getMessage());
        } catch (ReflectiveOperationException e) {
            throw new ArchiveError("FORMAT", "Поддержка RAR недоступна в этой сборке");
        }
    }

    private static List<String> strings(JSArray arr) {
        List<String> out = new ArrayList<>();
        if (arr == null) return out;
        for (int i = 0; i < arr.length(); i++) {
            String s = arr.optString(i, null);
            if (s != null && !s.isEmpty()) out.add(s);
        }
        return out;
    }
}
