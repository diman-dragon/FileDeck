package dev.filedeck.app;

import com.getcapacitor.JSObject;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Распаковка архива: целиком или выбранных записей, с защитой от Zip Slip. */
final class ArchiveExtractor {
    private ArchiveExtractor() {}

    static JSObject extract(ArchiveHost host, String path, String destDir, String password, String mode, Set<String> only) throws IOException {
        if (path == null || destDir == null) throw new ArchiveError("PATH", "Не указан архив или папка");
        File f = new File(path);
        File dest = new File(destDir);
        if (!f.isFile()) throw new ArchiveError("PATH", "Архив не найден");
        if (!dest.isDirectory()) throw new ArchiveError("DEST", "Папка назначения недоступна");
        ArchiveFormat fmt = ArchiveFormat.detect(f);
        if (fmt == ArchiveFormat.UNKNOWN) throw new ArchiveError("FORMAT", "Формат архива не распознан");

        String base = ArchiveFormat.stripExtension(f.getName());
        boolean here = "here".equals(mode) && fmt != ArchiveFormat.RAR;
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
            if (fmt == ArchiveFormat.RAR) {
                host.emit("extract", 0, f.getName(), true);
                extractRar(f, root, password);
                count = countFiles(root);
            } else {
                String rootCanon = root.getCanonicalPath();
                try (ArchiveSource src = ArchiveSource.open(f, fmt, password)) {
                    byte[] buf = new byte[ArchiveIO.BUF];
                    ArchiveEntry e;
                    while ((e = src.next()) != null) {
                        host.checkCancel();
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
                        try (OutputStream os = new BufferedOutputStream(new FileOutputStream(out), ArchiveIO.BUF)) {
                            InputStream in = src.stream();
                            int n;
                            while ((n = in.read(buf)) > 0) {
                                os.write(buf, 0, n);
                                host.checkCancel();
                                host.emit("extract", src.fraction(), name, false);
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
        if (createdRoot && count == 0 && only.isEmpty() && fmt != ArchiveFormat.RAR && countFiles(root) == 0) {
            // пустой архив — оставляем пустую папку, но сообщаем об этом
            host.emit("extract", 1, null, true);
        }
        host.emit("extract", 1, null, true);
        JSObject ret = new JSObject();
        ret.put("path", root.getAbsolutePath());
        ret.put("count", count);
        return ret;
    }

    private static long countFiles(File dir) {
        long n = 0;
        File[] ch = dir.listFiles();
        if (ch == null) return 0;
        for (File c : ch) n += c.isDirectory() ? countFiles(c) : 1;
        return n;
    }

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

    private static File mapTarget(File root, String name, boolean here, Map<String, String> topMap, List<File> created) {
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

    private static void extractRar(File rar, File destDir, String password) throws IOException {
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
}
