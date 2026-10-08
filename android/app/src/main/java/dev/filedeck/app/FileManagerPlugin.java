package dev.filedeck.app;

import android.Manifest;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.StatFs;
import android.provider.Settings;
import android.webkit.MimeTypeMap;

import androidx.core.content.FileProvider;

import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.PermissionState;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;
import com.getcapacitor.annotation.PermissionCallback;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Нативный плагин файловой системы для FileDeck.
 * Работает с обычными путями (/storage/emulated/0/...), поэтому требует
 * разрешение «Доступ ко всем файлам» (MANAGE_EXTERNAL_STORAGE) на Android 11+.
 */
@CapacitorPlugin(
    name = "FileManager",
    permissions = {
        @Permission(
            alias = "storage",
            strings = { Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE }
        )
    }
)
public class FileManagerPlugin extends Plugin {

    private static final long MAX_TEXT_BYTES = 2L * 1024 * 1024;
    private static final int BUFFER = 64 * 1024;

    private final ExecutorService pool = Executors.newFixedThreadPool(4);

    private interface Job {
        void run() throws Exception;
    }

    /** Выполняет работу в своём пуле, чтобы длинные операции не блокировали остальные вызовы. */
    private void async(final PluginCall call, final Job job) {
        pool.execute(() -> {
            try {
                job.run();
            } catch (Exception e) {
                String msg = e.getMessage();
                call.reject(msg == null || msg.isEmpty() ? e.getClass().getSimpleName() : msg);
            }
        });
    }

    @Override
    protected void handleOnDestroy() {
        pool.shutdownNow();
        super.handleOnDestroy();
    }

    // ---------------------------------------------------------------- разрешения

    private boolean hasAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return Environment.isExternalStorageManager();
        }
        return getPermissionState("storage") == PermissionState.GRANTED;
    }

    @PluginMethod
    public void checkPermission(PluginCall call) {
        JSObject ret = new JSObject();
        ret.put("granted", hasAccess());
        call.resolve(ret);
    }

    @PluginMethod
    public void requestPermission(PluginCall call) {
        if (hasAccess()) {
            JSObject ret = new JSObject();
            ret.put("granted", true);
            call.resolve(ret);
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Открываем системный экран «Доступ ко всем файлам». Результат приложение
            // перепроверяет само, когда пользователь возвращается.
            try {
                Intent intent = new Intent(
                    Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                    Uri.parse("package:" + getContext().getPackageName())
                );
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                getContext().startActivity(intent);
            } catch (Exception e) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                getContext().startActivity(intent);
            }
            JSObject ret = new JSObject();
            ret.put("granted", false);
            call.resolve(ret);
        } else {
            requestPermissionForAlias("storage", call, "storagePermissionCallback");
        }
    }

    @PermissionCallback
    private void storagePermissionCallback(PluginCall call) {
        JSObject ret = new JSObject();
        ret.put("granted", getPermissionState("storage") == PermissionState.GRANTED);
        call.resolve(ret);
    }

    // ---------------------------------------------------------------- хранилища

    @PluginMethod
    public void getRoots(PluginCall call) {
        async(call, () -> {
            JSArray roots = new JSArray();
            File primary = Environment.getExternalStorageDirectory();
            roots.put(describeRoot("Внутренняя память", primary, true));

            Set<String> seen = new LinkedHashSet<>();
            seen.add(primary.getAbsolutePath());
            File[] dirs = getContext().getExternalFilesDirs(null);
            if (dirs != null) {
                for (File f : dirs) {
                    if (f == null) continue;
                    String p = f.getAbsolutePath();
                    int i = p.indexOf("/Android/data");
                    if (i <= 0) continue;
                    String rootPath = p.substring(0, i);
                    if (seen.add(rootPath)) {
                        File root = new File(rootPath);
                        if (root.exists() && root.canRead()) {
                            roots.put(describeRoot("SD-карта", root, false));
                        }
                    }
                }
            }
            JSObject ret = new JSObject();
            ret.put("roots", roots);
            call.resolve(ret);
        });
    }

    private JSObject describeRoot(String name, File root, boolean primary) {
        JSObject o = new JSObject();
        o.put("name", name);
        o.put("path", root.getAbsolutePath());
        o.put("primary", primary);
        long total = 0;
        long free = 0;
        try {
            StatFs fs = new StatFs(root.getAbsolutePath());
            total = fs.getTotalBytes();
            free = fs.getAvailableBytes();
        } catch (Exception ignored) {
        }
        o.put("total", total);
        o.put("free", free);
        return o;
    }

    // ---------------------------------------------------------------- чтение каталогов

    @PluginMethod
    public void list(PluginCall call) {
        final String path = call.getString("path");
        async(call, () -> {
            if (path == null) throw new IOException("Не указан путь");
            File dir = new File(path);
            if (!dir.exists()) throw new IOException("Папка не существует");
            if (!dir.isDirectory()) throw new IOException("Это не папка");
            File[] files = dir.listFiles();
            if (files == null) throw new IOException("Нет доступа к папке");
            JSArray arr = new JSArray();
            for (File f : files) arr.put(describe(f, true));
            JSObject ret = new JSObject();
            ret.put("entries", arr);
            call.resolve(ret);
        });
    }

    @PluginMethod
    public void search(PluginCall call) {
        final String path = call.getString("path");
        final String query = call.getString("query", "");
        final int limit = call.getInt("limit", 400);
        async(call, () -> {
            if (path == null) throw new IOException("Не указан путь");
            final String q = query.toLowerCase(Locale.ROOT).trim();
            JSArray arr = new JSArray();
            boolean truncated = false;
            if (!q.isEmpty()) {
                ArrayDeque<File> queue = new ArrayDeque<>();
                queue.add(new File(path));
                int visited = 0;
                outer:
                while (!queue.isEmpty()) {
                    File dir = queue.poll();
                    File[] children = dir.listFiles();
                    if (children == null) continue;
                    for (File f : children) {
                        if (++visited > 300000) {
                            truncated = true;
                            break outer;
                        }
                        if (f.getName().toLowerCase(Locale.ROOT).contains(q)) {
                            arr.put(describe(f, false));
                            if (arr.length() >= limit) {
                                truncated = true;
                                break outer;
                            }
                        }
                        if (f.isDirectory() && !isSymlink(f)) queue.add(f);
                    }
                }
            }
            JSObject ret = new JSObject();
            ret.put("entries", arr);
            ret.put("truncated", truncated);
            call.resolve(ret);
        });
    }

    @PluginMethod
    public void size(PluginCall call) {
        final String path = call.getString("path");
        async(call, () -> {
            if (path == null) throw new IOException("Не указан путь");
            long[] acc = new long[3]; // bytes, files, folders
            measure(new File(path), acc, true);
            JSObject ret = new JSObject();
            ret.put("size", acc[0]);
            ret.put("files", acc[1]);
            ret.put("folders", acc[2]);
            call.resolve(ret);
        });
    }

    private void measure(File f, long[] acc, boolean isRoot) {
        if (f.isDirectory()) {
            if (!isRoot) acc[2]++;
            if (isSymlink(f)) return;
            File[] ch = f.listFiles();
            if (ch != null) for (File c : ch) measure(c, acc, false);
        } else {
            acc[0] += f.length();
            acc[1]++;
        }
    }

    // ---------------------------------------------------------------- создание / переименование / удаление

    @PluginMethod
    public void mkdir(PluginCall call) {
        final String path = call.getString("path");
        async(call, () -> {
            if (path == null) throw new IOException("Не указан путь");
            File f = new File(path);
            if (f.exists()) throw new IOException("Папка с таким именем уже есть");
            if (!f.mkdirs()) throw new IOException("Не удалось создать папку");
            JSObject ret = new JSObject();
            ret.put("path", f.getAbsolutePath());
            call.resolve(ret);
        });
    }

    @PluginMethod
    public void createFile(PluginCall call) {
        final String path = call.getString("path");
        async(call, () -> {
            if (path == null) throw new IOException("Не указан путь");
            File f = new File(path);
            if (f.exists()) throw new IOException("Файл с таким именем уже есть");
            if (!f.createNewFile()) throw new IOException("Не удалось создать файл");
            JSObject ret = new JSObject();
            ret.put("path", f.getAbsolutePath());
            call.resolve(ret);
        });
    }

    @PluginMethod
    public void rename(PluginCall call) {
        final String path = call.getString("path");
        final String name = call.getString("name");
        async(call, () -> {
            if (path == null || name == null) throw new IOException("Не указан путь или имя");
            String clean = name.trim();
            if (clean.isEmpty() || clean.contains("/") || clean.equals(".") || clean.equals("..")) {
                throw new IOException("Недопустимое имя");
            }
            File src = new File(path);
            File dst = new File(src.getParentFile(), clean);
            if (dst.exists()) throw new IOException("Элемент с таким именем уже существует");
            if (!src.renameTo(dst)) throw new IOException("Не удалось переименовать");
            JSObject ret = new JSObject();
            ret.put("path", dst.getAbsolutePath());
            call.resolve(ret);
        });
    }

    @PluginMethod
    public void remove(PluginCall call) {
        final List<String> paths = getPaths(call);
        async(call, () -> {
            JSArray failed = new JSArray();
            int done = 0;
            for (String p : paths) {
                if (deleteRecursive(new File(p))) done++;
                else failed.put(p);
            }
            JSObject ret = new JSObject();
            ret.put("done", done);
            ret.put("failed", failed);
            call.resolve(ret);
        });
    }

    private boolean deleteRecursive(File f) {
        boolean ok = true;
        if (f.isDirectory() && !isSymlink(f)) {
            File[] ch = f.listFiles();
            if (ch != null) for (File c : ch) ok &= deleteRecursive(c);
        }
        return f.delete() && ok;
    }

    // ---------------------------------------------------------------- копирование / перемещение

    @PluginMethod
    public void copy(PluginCall call) {
        transfer(call, false);
    }

    @PluginMethod
    public void move(PluginCall call) {
        transfer(call, true);
    }

    private void transfer(final PluginCall call, final boolean move) {
        final List<String> paths = getPaths(call);
        final String dest = call.getString("dest");
        async(call, () -> {
            if (dest == null) throw new IOException("Не указана папка назначения");
            File destDir = new File(dest);
            if (!destDir.isDirectory()) throw new IOException("Папка назначения недоступна");
            String destCanon = destDir.getCanonicalPath();

            JSArray failed = new JSArray();
            int done = 0;
            for (String p : paths) {
                File src = new File(p);
                try {
                    String srcCanon = src.getCanonicalPath();
                    // Нельзя копировать/переносить папку внутрь самой себя
                    if (src.isDirectory()
                        && (destCanon.equals(srcCanon) || destCanon.startsWith(srcCanon + File.separator))) {
                        failed.put(p);
                        continue;
                    }
                    // Перенос в ту же папку — ничего не делаем
                    if (move && src.getParentFile() != null
                        && src.getParentFile().getCanonicalPath().equals(destCanon)) {
                        done++;
                        continue;
                    }
                    File target = uniqueTarget(destDir, src.getName());
                    if (move) {
                        if (!src.renameTo(target)) {
                            copyRecursive(src, target);
                            if (!deleteRecursive(src)) {
                                failed.put(p);
                                continue;
                            }
                        }
                    } else {
                        copyRecursive(src, target);
                    }
                    done++;
                } catch (Exception e) {
                    failed.put(p);
                }
            }
            JSObject ret = new JSObject();
            ret.put("done", done);
            ret.put("failed", failed);
            call.resolve(ret);
        });
    }

    private File uniqueTarget(File dir, String name) {
        File t = new File(dir, name);
        if (!t.exists()) return t;
        String base = name;
        String ext = "";
        int dot = name.lastIndexOf('.');
        if (dot > 0) {
            base = name.substring(0, dot);
            ext = name.substring(dot);
        }
        for (int i = 1; i < 10000; i++) {
            t = new File(dir, base + " (" + i + ")" + ext);
            if (!t.exists()) return t;
        }
        return t;
    }

    private void copyRecursive(File src, File dst) throws IOException {
        if (src.isDirectory()) {
            if (!dst.mkdirs() && !dst.isDirectory()) throw new IOException("Не удалось создать " + dst.getName());
            if (isSymlink(src)) return;
            File[] ch = src.listFiles();
            if (ch != null) for (File c : ch) copyRecursive(c, new File(dst, c.getName()));
        } else {
            try (InputStream in = new FileInputStream(src); OutputStream out = new FileOutputStream(dst)) {
                byte[] buf = new byte[BUFFER];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            }
            //noinspection ResultOfMethodCallIgnored
            dst.setLastModified(src.lastModified());
        }
    }

    // ---------------------------------------------------------------- архивы

    @PluginMethod
    public void zip(PluginCall call) {
        final List<String> paths = getPaths(call);
        final String dest = call.getString("dest");
        final String name = call.getString("name", "archive.zip");
        async(call, () -> {
            if (dest == null || paths.isEmpty()) throw new IOException("Нечего архивировать");
            File target = uniqueTarget(new File(dest), name.endsWith(".zip") ? name : name + ".zip");
            try (ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(target)))) {
                for (String p : paths) {
                    File f = new File(p);
                    if (f.getCanonicalPath().equals(target.getCanonicalPath())) continue;
                    addToZip(zos, f, f.getName());
                }
            } catch (IOException e) {
                //noinspection ResultOfMethodCallIgnored
                target.delete();
                throw e;
            }
            JSObject ret = new JSObject();
            ret.put("path", target.getAbsolutePath());
            call.resolve(ret);
        });
    }

    private void addToZip(ZipOutputStream zos, File f, String entryName) throws IOException {
        if (f.isDirectory()) {
            zos.putNextEntry(new ZipEntry(entryName + "/"));
            zos.closeEntry();
            if (isSymlink(f)) return;
            File[] ch = f.listFiles();
            if (ch != null) for (File c : ch) addToZip(zos, c, entryName + "/" + c.getName());
        } else {
            ZipEntry e = new ZipEntry(entryName);
            e.setTime(f.lastModified());
            zos.putNextEntry(e);
            try (InputStream in = new BufferedInputStream(new FileInputStream(f), BUFFER)) {
                byte[] buf = new byte[BUFFER];
                int n;
                while ((n = in.read(buf)) > 0) zos.write(buf, 0, n);
            }
            zos.closeEntry();
        }
    }

    @PluginMethod
    public void unzip(PluginCall call) {
        final String path = call.getString("path");
        final String dest = call.getString("dest");
        async(call, () -> {
            if (path == null || dest == null) throw new IOException("Не указан архив или папка");
            File zipFile = new File(path);
            String base = zipFile.getName();
            int dot = base.lastIndexOf('.');
            if (dot > 0) base = base.substring(0, dot);
            File outDir = uniqueTarget(new File(dest), base);
            if (!outDir.mkdirs()) throw new IOException("Не удалось создать папку для распаковки");
            String outCanon = outDir.getCanonicalPath();

            try (ZipInputStream zis = new ZipInputStream(new BufferedInputStream(new FileInputStream(zipFile), BUFFER))) {
                ZipEntry e;
                byte[] buf = new byte[BUFFER];
                while ((e = zis.getNextEntry()) != null) {
                    File out = new File(outDir, e.getName());
                    // защита от Zip Slip
                    String canon = out.getCanonicalPath();
                    if (!canon.equals(outCanon) && !canon.startsWith(outCanon + File.separator)) {
                        throw new IOException("Архив содержит небезопасные пути");
                    }
                    if (e.isDirectory()) {
                        //noinspection ResultOfMethodCallIgnored
                        out.mkdirs();
                    } else {
                        File parent = out.getParentFile();
                        if (parent != null && !parent.exists()) {
                            //noinspection ResultOfMethodCallIgnored
                            parent.mkdirs();
                        }
                        try (OutputStream os = new BufferedOutputStream(new FileOutputStream(out), BUFFER)) {
                            int n;
                            while ((n = zis.read(buf)) > 0) os.write(buf, 0, n);
                        }
                        if (e.getTime() > 0) {
                            //noinspection ResultOfMethodCallIgnored
                            out.setLastModified(e.getTime());
                        }
                    }
                }
            } catch (IOException e) {
                deleteRecursive(outDir);
                throw e;
            }
            JSObject ret = new JSObject();
            ret.put("path", outDir.getAbsolutePath());
            call.resolve(ret);
        });
    }

    // ---------------------------------------------------------------- текст

    @PluginMethod
    public void readText(PluginCall call) {
        final String path = call.getString("path");
        async(call, () -> {
            if (path == null) throw new IOException("Не указан путь");
            File f = new File(path);
            if (!f.isFile()) throw new IOException("Файл не найден");
            if (f.length() > MAX_TEXT_BYTES) throw new IOException("Файл слишком большой для редактора (больше 2 МБ)");
            ByteArrayOutputStream bos = new ByteArrayOutputStream((int) Math.max(f.length(), 16));
            try (InputStream in = new FileInputStream(f)) {
                byte[] buf = new byte[BUFFER];
                int n;
                while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
            }
            JSObject ret = new JSObject();
            ret.put("text", new String(bos.toByteArray(), StandardCharsets.UTF_8));
            call.resolve(ret);
        });
    }

    @PluginMethod
    public void writeText(PluginCall call) {
        final String path = call.getString("path");
        final String text = call.getString("text", "");
        async(call, () -> {
            if (path == null) throw new IOException("Не указан путь");
            try (OutputStream out = new FileOutputStream(new File(path), false)) {
                out.write(text.getBytes(StandardCharsets.UTF_8));
            }
            call.resolve();
        });
    }

    // ---------------------------------------------------------------- открыть / поделиться

    @PluginMethod
    public void open(PluginCall call) {
        final String path = call.getString("path");
        if (path == null) {
            call.reject("Не указан путь");
            return;
        }
        File f = new File(path);
        try {
            Uri uri = uriFor(f);
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, mimeOf(f.getName()));
            intent.setClipData(ClipData.newRawUri("", uri));
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            getContext().startActivity(intent);
            call.resolve();
        } catch (ActivityNotFoundException e) {
            call.reject("Нет приложения для открытия этого файла");
        } catch (Exception e) {
            call.reject("Не удалось открыть файл: " + e.getMessage());
        }
    }

    @PluginMethod
    public void share(PluginCall call) {
        final List<String> paths = getPaths(call);
        if (paths.isEmpty()) {
            call.reject("Нечем делиться");
            return;
        }
        try {
            ArrayList<Uri> uris = new ArrayList<>();
            for (String p : paths) {
                File f = new File(p);
                if (f.isFile()) uris.add(uriFor(f));
            }
            if (uris.isEmpty()) {
                call.reject("Поделиться можно только файлами, не папками");
                return;
            }
            Intent intent;
            if (uris.size() == 1) {
                intent = new Intent(Intent.ACTION_SEND);
                intent.putExtra(Intent.EXTRA_STREAM, uris.get(0));
                intent.setType(mimeOf(new File(paths.get(0)).getName()));
            } else {
                intent = new Intent(Intent.ACTION_SEND_MULTIPLE);
                intent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);
                intent.setType("*/*");
            }
            ClipData clip = ClipData.newRawUri("", uris.get(0));
            for (int i = 1; i < uris.size(); i++) clip.addItem(new ClipData.Item(uris.get(i)));
            intent.setClipData(clip);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            Intent chooser = Intent.createChooser(intent, "Поделиться");
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            getContext().startActivity(chooser);
            call.resolve();
        } catch (Exception e) {
            call.reject("Не удалось поделиться: " + e.getMessage());
        }
    }

    private Uri uriFor(File f) {
        Context ctx = getContext();
        return FileProvider.getUriForFile(ctx, ctx.getPackageName() + ".fileprovider", f);
    }

    // ---------------------------------------------------------------- утилиты

    private List<String> getPaths(PluginCall call) {
        List<String> out = new ArrayList<>();
        JSArray arr = call.getArray("paths");
        if (arr == null) return out;
        for (int i = 0; i < arr.length(); i++) {
            String s = arr.optString(i, null);
            if (s != null && !s.isEmpty()) out.add(s);
        }
        return out;
    }

    private JSObject describe(File f, boolean withCount) {
        JSObject o = new JSObject();
        boolean dir = f.isDirectory();
        o.put("name", f.getName());
        o.put("path", f.getAbsolutePath());
        o.put("dir", dir);
        o.put("size", dir ? 0L : f.length());
        o.put("mtime", f.lastModified());
        if (dir) {
            if (withCount) {
                String[] l = f.list();
                o.put("count", l == null ? 0 : l.length);
            }
        } else {
            o.put("mime", mimeOf(f.getName()));
        }
        return o;
    }

    private static String mimeOf(String name) {
        int dot = name.lastIndexOf('.');
        if (dot >= 0 && dot < name.length() - 1) {
            String ext = name.substring(dot + 1).toLowerCase(Locale.ROOT);
            String mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);
            if (mime != null) return mime;
            switch (ext) {
                case "md":
                case "log":
                case "ini":
                case "conf":
                case "yml":
                case "yaml":
                    return "text/plain";
                case "json":
                    return "application/json";
                default:
                    break;
            }
        }
        return "application/octet-stream";
    }

    private static boolean isSymlink(File f) {
        try {
            File parent = f.getParentFile();
            File probe = parent == null ? f : new File(parent.getCanonicalFile(), f.getName());
            return !probe.getCanonicalFile().equals(probe.getAbsoluteFile());
        } catch (IOException e) {
            return false;
        }
    }
}
