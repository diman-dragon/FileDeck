package dev.filedeck.app;

import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;

import java.io.File;
import java.io.IOException;
import java.util.List;

/** Создание, переименование, удаление, копирование и перемещение файлов. */
final class FileOps {
    private FileOps() {}

    static JSObject mkdir(String path) throws IOException {
        if (path == null) throw new IOException("Не указан путь");
        File f = new File(path);
        if (f.exists()) throw new IOException("Папка с таким именем уже есть");
        if (!f.mkdirs()) throw new IOException("Не удалось создать папку");
        JSObject ret = new JSObject();
        ret.put("path", f.getAbsolutePath());
        return ret;
    }

    static JSObject createFile(String path) throws IOException {
        if (path == null) throw new IOException("Не указан путь");
        File f = new File(path);
        if (f.exists()) throw new IOException("Файл с таким именем уже есть");
        if (!f.createNewFile()) throw new IOException("Не удалось создать файл");
        JSObject ret = new JSObject();
        ret.put("path", f.getAbsolutePath());
        return ret;
    }

    static JSObject rename(String path, String name) throws IOException {
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
        return ret;
    }

    static JSObject remove(List<String> paths) {
        JSArray failed = new JSArray();
        int done = 0;
        for (String p : paths) {
            if (FsUtil.deleteRecursive(new File(p))) done++;
            else failed.put(p);
        }
        JSObject ret = new JSObject();
        ret.put("done", done);
        ret.put("failed", failed);
        return ret;
    }

    /** Копирует или переносит paths в папку dest; конфликты имён разрешаются суффиксом « (1)». */
    static JSObject transfer(List<String> paths, String dest, boolean move) throws IOException {
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
                File target = FsUtil.uniqueTarget(destDir, src.getName());
                if (move) {
                    if (!src.renameTo(target)) {
                        FsUtil.copyRecursive(src, target);
                        if (!FsUtil.deleteRecursive(src)) {
                            failed.put(p);
                            continue;
                        }
                    }
                } else {
                    FsUtil.copyRecursive(src, target);
                }
                done++;
            } catch (Exception e) {
                failed.put(p);
            }
        }
        JSObject ret = new JSObject();
        ret.put("done", done);
        ret.put("failed", failed);
        return ret;
    }
}
