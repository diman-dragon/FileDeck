package dev.filedeck.app;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.SystemClock;

import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.PriorityQueue;
import java.util.Set;

/** Один проход по накопителям: дубликаты, похожие фото, мусор, APK, самые большие файлы, статистика. */
final class Scanner implements ScanHost {
    /** Куда отправлять прогресс сканирования. */
    interface Sink {
        void send(JSObject progress);
    }

    private static final long MB = 1024L * 1024L;
    private static final int MAX_IMAGES = 30000;
    private static final int MAX_LIST = 5000;
    private static final int TOP_LARGEST = 100;
    private static final String[] KIND_KEYS = { "images", "video", "audio", "docs", "archives", "apk", "other" };

    private final Context ctx;
    private final Runnable cancelCheck;
    private final Sink sink;

    final Set<String> excludePaths = new HashSet<>();
    final boolean wantDup;
    final boolean wantSimilar;
    final boolean wantJunk;
    final boolean wantApk;
    final boolean wantLarge;
    final boolean wantEmpty;
    final boolean wantZero;
    final boolean includeApps;
    final long minDup;

    long files;
    long folders;
    long bytes;
    long lastEmit;
    long hashDone;
    long hashTotal;

    final HashMap<Long, String> sizeOnce = new HashMap<>();
    final HashMap<Long, ArrayList<String>> sizeMany = new HashMap<>();
    final ArrayList<String> emptyDirs = new ArrayList<>();
    final ArrayList<String> zeroFiles = new ArrayList<>();
    final PriorityQueue<FileInfo> largest = new PriorityQueue<>(128, new Comparator<FileInfo>() {
        @Override
        public int compare(FileInfo a, FileInfo b) {
            return Long.compare(a.size, b.size);
        }
    });
    final ArrayList<FileInfo> apks = new ArrayList<>();
    final ArrayList<FileInfo> images = new ArrayList<>();
    final JSArray junkItems = new JSArray();
    final long[] usageBytes = new long[KIND_KEYS.length];
    final long[] usageCount = new long[KIND_KEYS.length];

    Scanner(Context ctx, Runnable cancelCheck, Sink sink, List<String> excludes, boolean dup, boolean similar, boolean junk, boolean apk, boolean large,
         boolean empty, boolean zero, boolean apps, long minDup) {
        for (String e : excludes) excludePaths.add(stripSlash(e));
        this.ctx = ctx;
        this.cancelCheck = cancelCheck;
        this.sink = sink;
        this.wantDup = dup;
        this.wantSimilar = similar;
        this.wantJunk = junk;
        this.wantApk = apk;
        this.wantLarge = large;
        this.wantEmpty = empty;
        this.wantZero = zero;
        this.includeApps = apps;
        this.minDup = Math.max(1, minDup);
    }

    JSObject run(List<String> roots) {
        long t0 = SystemClock.elapsedRealtime();
        for (String r : roots) {
            File rf = new File(r);
            if (!rf.isDirectory() || isExcluded(rf.getAbsolutePath())) continue;
            walk(rf, true, 0);
        }
        emit("walk", 0, 0, null, true);

        HashSet<String> dupMembers = new HashSet<>();
        JSArray duplicates = wantDup ? new DuplicateFinder(this).find(sizeMany, dupMembers) : new JSArray();
        JSArray similar = wantSimilar ? new SimilarImageFinder(this).find(images, dupMembers) : new JSArray();

        JSObject res = new JSObject();
        res.put("duplicates", duplicates);
        res.put("similar", similar);
        res.put("emptyFolders", toArray(emptyDirs));
        res.put("zeroFiles", toArray(zeroFiles));
        res.put("junk", junkItems);
        res.put("largest", largestArray());
        res.put("apks", apkArray());

        JSObject usage = new JSObject();
        for (int i = 0; i < KIND_KEYS.length; i++) {
            JSObject u = new JSObject();
            u.put("count", usageCount[i]);
            u.put("bytes", usageBytes[i]);
            usage.put(KIND_KEYS[i], u);
        }
        res.put("usage", usage);

        JSObject stats = new JSObject();
        stats.put("files", files);
        stats.put("folders", folders);
        stats.put("bytes", bytes);
        stats.put("ms", SystemClock.elapsedRealtime() - t0);
        res.put("stats", stats);
        return res;
    }

    // ------------------------------------------------------------ обход

    private boolean isExcluded(String path) {
        for (String e : excludePaths) {
            if (path.equals(e) || path.startsWith(e + "/")) return true;
        }
        return false;
    }

    private String stripSlash(String p) {
        String s = p;
        while (s.length() > 1 && s.endsWith("/")) s = s.substring(0, s.length() - 1);
        return s;
    }

    /** Возвращает true, если в папке нет ни одного файла (только пустые папки). */
    private boolean walk(File dir, boolean isScanRoot, int depth) {
        checkCancel();
        File[] children = dir.listFiles();
        if (children == null) return false;
        folders++;
        boolean empty = true;
        ArrayList<File> emptyChildren = new ArrayList<>();

        for (File f : children) {
            String name = f.getName();
            if (f.isDirectory()) {
                empty = false;
                if (FsUtil.isSymlink(f)) continue;
                if (name.equals(TrashStore.TRASH_DIR) || isExcluded(f.getAbsolutePath())) continue;
                if (isScanRoot && name.equals("Android")) {
                    // чужие каталоги data/obb недоступны; в media — только если включено явно
                    File media = new File(f, "media");
                    if (includeApps && media.isDirectory()) walk(media, false, depth + 1);
                    continue;
                }
                if (wantJunk) {
                    String kind = FileKinds.junkDirKind(name.toLowerCase(Locale.ROOT));
                    if (kind != null) {
                        long size = FsUtil.sizeOf(f);
                        if (size > 0) {
                            addJunk(f.getAbsolutePath(), size, true, kind);
                            continue;
                        }
                    }
                }
                if (depth >= 60) continue;
                boolean childEmpty = walk(f, false, depth + 1);
                if (childEmpty) {
                    emptyChildren.add(f);
                    empty = false; // решим ниже: пустое дерево целиком или нет
                }
            } else {
                empty = false;
                handleFile(f, name);
            }
        }

        // Пустое дерево: нет файлов и все вложенные папки пусты. Тогда сообщаем родителю,
        // а не перечисляем каждую вложенную папку по отдельности.
        boolean onlyEmptyDirs = emptyChildren.size() == countDirs(children);
        boolean noFiles = !hasFiles(children);
        if (noFiles && onlyEmptyDirs && !isScanRoot) return true;

        if (wantEmpty) {
            for (File c : emptyChildren) {
                if (isScanRoot && FileKinds.PROTECTED_DIRS.contains(c.getName().toLowerCase(Locale.ROOT))) continue;
                if (emptyDirs.size() < MAX_LIST) emptyDirs.add(c.getAbsolutePath());
            }
        }
        return false;
    }

    private int countDirs(File[] children) {
        int n = 0;
        for (File c : children) {
            if (c.isDirectory() && !c.getName().equals(TrashStore.TRASH_DIR) && !FsUtil.isSymlink(c)) n++;
        }
        return n;
    }

    private boolean hasFiles(File[] children) {
        for (File c : children) {
            if (!c.isDirectory()) return true;
        }
        return false;
    }

    private void handleFile(File f, String name) {
        files++;
        long len = f.length();
        bytes += len;
        if ((files & 0x3F) == 0) emit("walk", 0, 0, f.getParent(), false);

        if (len == 0) {
            if (wantZero && !name.equals(".nomedia") && zeroFiles.size() < MAX_LIST) zeroFiles.add(f.getAbsolutePath());
            return;
        }
        String lower = name.toLowerCase(Locale.ROOT);
        String ext = FileKinds.extOf(lower);
        int kind = FileKinds.kindIndex(ext);
        usageBytes[kind] += len;
        usageCount[kind]++;

        if (wantJunk) {
            String junk = FileKinds.junkFileKind(lower, ext);
            if (junk != null) {
                addJunk(f.getAbsolutePath(), len, false, junk);
                return;
            }
        }
        if (wantApk && FileKinds.APK_EXT.contains(ext)) apks.add(new FileInfo(f.getAbsolutePath(), len, f.lastModified()));
        if (wantLarge && len >= MB) pushLargest(new FileInfo(f.getAbsolutePath(), len, f.lastModified()));
        if (wantSimilar && kind == 0 && len >= 20 * 1024 && images.size() < MAX_IMAGES) {
            images.add(new FileInfo(f.getAbsolutePath(), len, 0));
        }
        if (wantDup && len >= minDup) addBySize(len, f.getAbsolutePath());
    }

    private void pushLargest(FileInfo fi) {
        if (largest.size() < TOP_LARGEST) {
            largest.add(fi);
        } else if (largest.peek() != null && fi.size > largest.peek().size) {
            largest.poll();
            largest.add(fi);
        }
    }

    private void addBySize(long size, String path) {
        ArrayList<String> many = sizeMany.get(size);
        if (many != null) {
            many.add(path);
            return;
        }
        String first = sizeOnce.get(size);
        if (first == null) {
            sizeOnce.put(size, path);
        } else {
            ArrayList<String> list = new ArrayList<>(4);
            list.add(first);
            list.add(path);
            sizeMany.put(size, list);
            sizeOnce.remove(size);
        }
    }

    private void addJunk(String path, long size, boolean dir, String kind) {
        if (junkItems.length() >= MAX_LIST) return;
        JSObject o = new JSObject();
        o.put("path", path);
        o.put("size", size);
        o.put("dir", dir);
        o.put("kind", kind);
        junkItems.put(o);
    }

    // ------------------------------------------------------------ дубликаты

    private JSArray largestArray() {
        ArrayList<FileInfo> l = new ArrayList<>(largest);
        Collections.sort(l, new Comparator<FileInfo>() {
            @Override
            public int compare(FileInfo a, FileInfo b) {
                return Long.compare(b.size, a.size);
            }
        });
        JSArray arr = new JSArray();
        for (FileInfo fi : l) arr.put(info(fi));
        return arr;
    }

    private JSArray apkArray() {
        Collections.sort(apks, new Comparator<FileInfo>() {
            @Override
            public int compare(FileInfo a, FileInfo b) {
                return Long.compare(b.size, a.size);
            }
        });
        PackageManager pm = ctx.getPackageManager();
        JSArray arr = new JSArray();
        int parsed = 0;
        for (FileInfo fi : apks) {
            if (arr.length() >= MAX_LIST) break;
            JSObject o = info(fi);
            if (parsed < 150 && fi.path.toLowerCase(Locale.ROOT).endsWith(".apk")) {
                parsed++;
                try {
                    PackageInfo pi = pm.getPackageArchiveInfo(fi.path, 0);
                    if (pi != null) {
                        o.put("pkg", pi.packageName);
                        o.put("version", pi.versionName == null ? "" : pi.versionName);
                        ApplicationInfo ai = pi.applicationInfo;
                        if (ai != null) {
                            ai.sourceDir = fi.path;
                            ai.publicSourceDir = fi.path;
                            CharSequence label = ai.loadLabel(pm);
                            if (label != null) o.put("label", label.toString());
                        }
                    }
                } catch (Exception ignored) {
                    // не удалось прочитать — покажем просто файл
                }
            }
            arr.put(o);
        }
        return arr;
    }

    private JSObject info(FileInfo fi) {
        JSObject o = new JSObject();
        o.put("path", fi.path);
        o.put("size", fi.size);
        o.put("mtime", fi.mtime);
        return o;
    }

    private JSArray toArray(List<String> list) {
        JSArray arr = new JSArray();
        for (String s : list) arr.put(s);
        return arr;
    }

    @Override
    public void checkCancel() {
        cancelCheck.run();
    }

    @Override
    public void emit(String phase, long done, long total, String current, boolean force) {
        long now = SystemClock.elapsedRealtime();
        if (!force && now - lastEmit < 200) return;
        lastEmit = now;
        JSObject o = new JSObject();
        o.put("phase", phase);
        o.put("files", files);
        o.put("folders", folders);
        o.put("bytes", bytes);
        o.put("done", done);
        o.put("total", total);
        if (current != null) o.put("current", current);
        sink.send(o);
    }
}
