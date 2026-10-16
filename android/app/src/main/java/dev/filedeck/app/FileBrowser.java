package dev.filedeck.app;

import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;

import java.io.File;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Locale;

/** Чтение каталогов: список, поиск по имени, размер папки. */
final class FileBrowser {
    private static final int SEARCH_VISIT_LIMIT = 300000;

    private FileBrowser() {}

    static JSObject list(String path) throws IOException {
        if (path == null) throw new IOException("Не указан путь");
        File dir = new File(path);
        if (!dir.exists()) throw new IOException("Папка не существует");
        if (!dir.isDirectory()) throw new IOException("Это не папка");
        File[] files = dir.listFiles();
        if (files == null) throw new IOException("Нет доступа к папке");
        JSArray arr = new JSArray();
        for (File f : files) arr.put(FileJson.describe(f, true));
        JSObject ret = new JSObject();
        ret.put("entries", arr);
        return ret;
    }

    static JSObject search(String path, String query, int limit) throws IOException {
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
                    if (++visited > SEARCH_VISIT_LIMIT) {
                        truncated = true;
                        break outer;
                    }
                    if (f.getName().toLowerCase(Locale.ROOT).contains(q)) {
                        arr.put(FileJson.describe(f, false));
                        if (arr.length() >= limit) {
                            truncated = true;
                            break outer;
                        }
                    }
                    if (f.isDirectory() && !FsUtil.isSymlink(f)) queue.add(f);
                }
            }
        }
        JSObject ret = new JSObject();
        ret.put("entries", arr);
        ret.put("truncated", truncated);
        return ret;
    }

    static JSObject size(String path) throws IOException {
        if (path == null) throw new IOException("Не указан путь");
        long[] acc = new long[3]; // bytes, files, folders
        measure(new File(path), acc, true);
        JSObject ret = new JSObject();
        ret.put("size", acc[0]);
        ret.put("files", acc[1]);
        ret.put("folders", acc[2]);
        return ret;
    }

    private static void measure(File f, long[] acc, boolean isRoot) {
        if (f.isDirectory()) {
            if (!isRoot) acc[2]++;
            if (FsUtil.isSymlink(f)) return;
            File[] ch = f.listFiles();
            if (ch != null) for (File c : ch) measure(c, acc, false);
        } else {
            acc[0] += f.length();
            acc[1]++;
        }
    }
}
