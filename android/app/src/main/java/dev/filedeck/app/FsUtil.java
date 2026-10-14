package dev.filedeck.app;

import android.content.Context;
import android.os.Environment;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Общие файловые утилиты для плагинов FileDeck. */
final class FsUtil {
    private static final int BUFFER = 64 * 1024;

    private FsUtil() {}

    static boolean isSymlink(File f) {
        try {
            File parent = f.getParentFile();
            File probe = parent == null ? f : new File(parent.getCanonicalFile(), f.getName());
            return !probe.getCanonicalFile().equals(probe.getAbsoluteFile());
        } catch (IOException e) {
            return false;
        }
    }

    /** Получает размер каждого удалённого файла — для полосы прогресса. */
    interface DeleteProgress {
        void onDeleted(long bytes, String name);
    }

    static boolean deleteRecursive(File f) {
        return deleteRecursive(f, null);
    }

    static boolean deleteRecursive(File f, DeleteProgress progress) {
        boolean ok = true;
        if (f.isDirectory() && !isSymlink(f)) {
            File[] ch = f.listFiles();
            if (ch != null) {
                for (File c : ch) ok &= deleteRecursive(c, progress);
            }
            return f.delete() && ok;
        }
        long len = f.length();
        boolean deleted = f.delete();
        if (deleted && progress != null) progress.onDeleted(len, f.getName());
        return deleted && ok;
    }

    static void copyRecursive(File src, File dst) throws IOException {
        if (src.isDirectory()) {
            if (!dst.mkdirs() && !dst.isDirectory()) throw new IOException("Не удалось создать " + dst.getName());
            if (isSymlink(src)) return;
            File[] ch = src.listFiles();
            if (ch != null) {
                for (File c : ch) copyRecursive(c, new File(dst, c.getName()));
            }
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

    static File uniqueTarget(File dir, String name) {
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

    /** Размер файла или суммарный размер папки. */
    static long sizeOf(File f) {
        if (!f.isDirectory()) return f.length();
        if (isSymlink(f)) return 0;
        long sum = 0;
        File[] ch = f.listFiles();
        if (ch != null) {
            for (File c : ch) sum += sizeOf(c);
        }
        return sum;
    }

    /** Переносит файл или папку: сначала rename, при неудаче — копирование и удаление. */
    static boolean moveTo(File src, File dst) {
        if (src.renameTo(dst)) return true;
        try {
            copyRecursive(src, dst);
            if (deleteRecursive(src)) return true;
            deleteRecursive(dst);
            return false;
        } catch (IOException e) {
            deleteRecursive(dst);
            return false;
        }
    }

    /** Корни накопителей: внутренняя память и SD-карты. */
    static List<File> storageRoots(Context ctx) {
        List<File> out = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        File primary = Environment.getExternalStorageDirectory();
        out.add(primary);
        seen.add(primary.getAbsolutePath());
        File[] dirs = ctx.getExternalFilesDirs(null);
        if (dirs != null) {
            for (File f : dirs) {
                if (f == null) continue;
                String p = f.getAbsolutePath();
                int i = p.indexOf("/Android/data");
                if (i <= 0) continue;
                String rootPath = p.substring(0, i);
                if (seen.add(rootPath)) {
                    File root = new File(rootPath);
                    if (root.exists() && root.canRead()) out.add(root);
                }
            }
        }
        return out;
    }
}
