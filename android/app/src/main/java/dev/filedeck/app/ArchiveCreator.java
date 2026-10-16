package dev.filedeck.app;

import com.getcapacitor.JSObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/** Создание архива из выбранных файлов. */
final class ArchiveCreator {
    private ArchiveCreator() {}

    static JSObject create(ArchiveHost host, List<String> paths, String destDir, String name, ArchiveFormat fmt, String password, int level) throws IOException {
        if (paths.isEmpty()) throw new ArchiveError("EMPTY", "Нечего архивировать");
        if (fmt == ArchiveFormat.UNKNOWN) throw new ArchiveError("FORMAT", "Неизвестный формат архива");
        if (destDir == null) throw new ArchiveError("DEST", "Не указана папка");
        File dest = new File(destDir);
        if (!dest.isDirectory()) throw new ArchiveError("DEST", "Папка назначения недоступна");
        boolean usePassword = password != null && !password.isEmpty();
        if (usePassword && fmt != ArchiveFormat.ZIP) throw new ArchiveError("PASSWORD_UNSUPPORTED", "Пароль поддерживается только для ZIP");

        String base = ArchiveFormat.stripExtension(name.trim().isEmpty() ? "archive" : name.trim()).replace("/", "_");
        File target = FsUtil.uniqueTarget(dest, base + fmt.extension());
        String targetCanon = target.getCanonicalPath();

        List<ArchiveItem> items = new ArrayList<>();
        long total = 0;
        for (String p : paths) {
            File f = new File(p);
            if (!f.exists()) continue;
            total += collect(f, f.getName(), items, targetCanon);
        }
        if (items.isEmpty()) throw new ArchiveError("EMPTY", "Нечего архивировать");

        ArchiveSink sink = null;
        try {
            sink = ArchiveSink.open(fmt, target, usePassword ? password : null, level);
            long done = 0;
            byte[] buf = new byte[ArchiveIO.BUF];
            for (ArchiveItem it : items) {
                host.checkCancel();
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
                        host.checkCancel();
                        host.emit("create", total > 0 ? (double) done / total : 0, it.name, false);
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
        host.emit("create", 1, null, true);
        JSObject ret = new JSObject();
        ret.put("path", target.getAbsolutePath());
        ret.put("size", target.length());
        ret.put("items", items.size());
        return ret;
    }

    private static long collect(File f, String name, List<ArchiveItem> out, String skipCanon) throws IOException {
        if (f.getCanonicalPath().equals(skipCanon)) return 0;
        if (f.isDirectory()) {
            if (FsUtil.isSymlink(f)) return 0;
            out.add(new ArchiveItem(f, name, true));
            long sum = 0;
            File[] ch = f.listFiles();
            if (ch != null) {
                java.util.Arrays.sort(ch);
                for (File c : ch) sum += collect(c, name + "/" + c.getName(), out, skipCanon);
            }
            return sum;
        }
        if (!f.isFile()) return 0;
        out.add(new ArchiveItem(f, name, false));
        return f.length();
    }
}
