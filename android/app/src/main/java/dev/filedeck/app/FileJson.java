package dev.filedeck.app;

import android.os.StatFs;

import com.getcapacitor.JSObject;

import java.io.File;

/** Описание файлов и накопителей в виде JSON для интерфейса. */
final class FileJson {
    private FileJson() {}

    /** Файл или папка: имя, путь, размер, дата; для папки — число элементов (если withCount). */
    static JSObject describe(File f, boolean withCount) {
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
            o.put("mime", MimeTypes.of(f.getName()));
        }
        return o;
    }

    /** Накопитель: имя, путь, всего и свободно байт. */
    static JSObject root(String name, File root, boolean primary) {
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
}
