package dev.filedeck.app;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.SystemClock;

import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Глубокая очистка и корзина FileDeck.
 *
 * scan()      — один проход по накопителям: дубликаты, похожие фото, пустые папки и файлы,
 *               мусор и кэш, установщики APK, самые большие файлы, статистика по типам.
 * trash*()    — корзина: на каждом накопителе создаётся скрытая папка .FileDeckTrash,
 *               перенос внутри одного накопителя мгновенный, у каждого элемента есть .json с метаданными.
 */
@CapacitorPlugin(name = "Cleaner")
public class CleanerPlugin extends Plugin {

    static final String TRASH_DIR = ".FileDeckTrash";
    private static final int PARTIAL = 64 * 1024;
    private static final int BUF = 128 * 1024;
    private static final long MB = 1024L * 1024L;
    private static final int MAX_DUP_GROUPS = 3000;
    private static final int MAX_DUP_PER_GROUP = 40;
    private static final int MAX_SIMILAR_GROUPS = 500;
    private static final int MAX_IMAGES = 30000;
    private static final int MAX_LIST = 5000;
    private static final int TOP_LARGEST = 100;

    private static final String[] KIND_KEYS = { "images", "video", "audio", "docs", "archives", "apk", "other" };

    private static final Set<String> IMG_EXT = set("jpg", "jpeg", "png", "webp", "heic", "heif", "bmp");
    private static final Set<String> VIDEO_EXT = set("mp4", "mkv", "webm", "avi", "mov", "3gp", "m4v", "flv", "wmv", "ts");
    private static final Set<String> AUDIO_EXT = set("mp3", "flac", "wav", "ogg", "m4a", "aac", "opus", "amr", "wma", "mid");
    private static final Set<String> DOC_EXT = set("pdf", "doc", "docx", "odt", "rtf", "xls", "xlsx", "ods", "ppt", "pptx", "odp", "txt", "md", "csv", "epub", "fb2", "djvu");
    private static final Set<String> ARCHIVE_EXT = set("zip", "rar", "7z", "tar", "gz", "bz2", "xz", "iso");
    private static final Set<String> APK_EXT = set("apk", "xapk", "apks", "apkm", "aab");
    private static final Set<String> TEMP_EXT = set("tmp", "temp", "crdownload", "part", "partial", "opdownload");
    private static final Set<String> PROTECTED_DIRS = set(
        "dcim", "download", "downloads", "pictures", "music", "movies", "documents", "android", "alarms",
        "notifications", "ringtones", "podcasts", "audiobooks", "recordings", "screenshots", "dcim"
    );

    private final ExecutorService pool = Executors.newFixedThreadPool(3);
    private volatile boolean cancelRequested = false;
    private volatile boolean scanning = false;
    private volatile boolean trashCancel = false;
    private long lastTrashEmit = 0;

    private interface Job {
        void run() throws Exception;
    }

    private static final class Cancelled extends RuntimeException {
        Cancelled() {
            super("cancelled");
        }
    }

    private static final class FileInfo {
        final String path;
        final long size;
        final long mtime;

        FileInfo(String path, long size, long mtime) {
            this.path = path;
            this.size = size;
            this.mtime = mtime;
        }
    }

    private static Set<String> set(String... values) {
        return new HashSet<>(Arrays.asList(values));
    }

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
        cancelRequested = true;
        pool.shutdownNow();
        super.handleOnDestroy();
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

    private static boolean flag(PluginCall call, String name, boolean def) {
        Boolean b = call.getBoolean(name, def);
        return b == null ? def : b;
    }

    // =====================================================================================
    //  СКАНИРОВАНИЕ
    // =====================================================================================

    @PluginMethod
    public void scan(final PluginCall call) {
        if (scanning) {
            call.reject("Сканирование уже идёт");
            return;
        }
        final List<String> roots = strings(call.getArray("roots"));
        final List<String> excludes = strings(call.getArray("excludes"));
        if (roots.isEmpty()) {
            call.reject("Не выбрано, что сканировать");
            return;
        }
        final Scan scan = new Scan(
            excludes,
            flag(call, "duplicates", true),
            flag(call, "similar", false),
            flag(call, "junk", true),
            flag(call, "apks", true),
            flag(call, "largest", true),
            flag(call, "emptyFolders", true),
            flag(call, "zeroFiles", true),
            flag(call, "appFolders", false),
            call.getLong("minDuplicateSize", 1024L) == null ? 1024L : call.getLong("minDuplicateSize", 1024L)
        );
        scanning = true;
        cancelRequested = false;
        pool.execute(() -> {
            JSObject result = null;
            String error = null;
            try {
                result = scan.run(roots);
            } catch (Cancelled c) {
                result = new JSObject();
                result.put("cancelled", true);
            } catch (Throwable t) {
                String msg = t.getMessage();
                error = msg == null || msg.isEmpty() ? t.getClass().getSimpleName() : msg;
            }
            // флаг снимаем до ответа: следующий вызов приходит сразу после resolve
            scanning = false;
            if (error != null) call.reject(error);
            else call.resolve(result);
        });
    }

    @PluginMethod
    public void cancelScan(PluginCall call) {
        cancelRequested = true;
        call.resolve();
    }

    private void checkCancel() {
        if (cancelRequested) throw new Cancelled();
    }

    private final class Scan {
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

        Scan(List<String> excludes, boolean dup, boolean similar, boolean junk, boolean apk, boolean large,
             boolean empty, boolean zero, boolean apps, long minDup) {
            for (String e : excludes) excludePaths.add(stripSlash(e));
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
            JSArray duplicates = wantDup ? findDuplicates(dupMembers) : new JSArray();
            JSArray similar = wantSimilar ? findSimilar(dupMembers) : new JSArray();

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
                    if (name.equals(TRASH_DIR) || isExcluded(f.getAbsolutePath())) continue;
                    if (isScanRoot && name.equals("Android")) {
                        // чужие каталоги data/obb недоступны; в media — только если включено явно
                        File media = new File(f, "media");
                        if (includeApps && media.isDirectory()) walk(media, false, depth + 1);
                        continue;
                    }
                    if (wantJunk) {
                        String kind = junkDirKind(name.toLowerCase(Locale.ROOT));
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
                    if (isScanRoot && PROTECTED_DIRS.contains(c.getName().toLowerCase(Locale.ROOT))) continue;
                    if (emptyDirs.size() < MAX_LIST) emptyDirs.add(c.getAbsolutePath());
                }
            }
            return false;
        }

        private int countDirs(File[] children) {
            int n = 0;
            for (File c : children) {
                if (c.isDirectory() && !c.getName().equals(TRASH_DIR) && !FsUtil.isSymlink(c)) n++;
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
            String ext = extOf(lower);
            int kind = kindIndex(ext);
            usageBytes[kind] += len;
            usageCount[kind]++;

            if (wantJunk) {
                String junk = junkFileKind(lower, ext);
                if (junk != null) {
                    addJunk(f.getAbsolutePath(), len, false, junk);
                    return;
                }
            }
            if (wantApk && APK_EXT.contains(ext)) apks.add(new FileInfo(f.getAbsolutePath(), len, f.lastModified()));
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

        private JSArray findDuplicates(HashSet<String> members) {
            ArrayList<Map.Entry<Long, ArrayList<String>>> groups = new ArrayList<>(sizeMany.entrySet());
            hashTotal = 0;
            hashDone = 0;
            for (Map.Entry<Long, ArrayList<String>> e : groups) hashTotal += e.getKey() * e.getValue().size();

            ArrayList<JSObject> found = new ArrayList<>();
            ArrayList<Long> wasted = new ArrayList<>();
            for (Map.Entry<Long, ArrayList<String>> e : groups) {
                checkCancel();
                long size = e.getKey();
                HashMap<String, ArrayList<String>> byPartial = new HashMap<>();
                for (String p : e.getValue()) {
                    String h = hashFile(new File(p), size, true);
                    if (h == null) continue;
                    ArrayList<String> l = byPartial.get(h);
                    if (l == null) {
                        l = new ArrayList<>(2);
                        byPartial.put(h, l);
                    }
                    l.add(p);
                }
                boolean completeAlready = size <= 2L * PARTIAL;
                for (ArrayList<String> sub : byPartial.values()) {
                    if (sub.size() < 2) continue;
                    if (completeAlready) {
                        addGroup(found, wasted, size, sub, members);
                        continue;
                    }
                    HashMap<String, ArrayList<String>> byFull = new HashMap<>();
                    for (String p : sub) {
                        String h = hashFile(new File(p), size, false);
                        if (h == null) continue;
                        ArrayList<String> l = byFull.get(h);
                        if (l == null) {
                            l = new ArrayList<>(2);
                            byFull.put(h, l);
                        }
                        l.add(p);
                    }
                    for (ArrayList<String> same : byFull.values()) {
                        if (same.size() >= 2) addGroup(found, wasted, size, same, members);
                    }
                }
            }
            emit("hash", hashTotal, hashTotal, null, true);

            Integer[] order = new Integer[found.size()];
            for (int i = 0; i < order.length; i++) order[i] = i;
            final ArrayList<Long> w = wasted;
            Arrays.sort(order, new Comparator<Integer>() {
                @Override
                public int compare(Integer a, Integer b) {
                    return Long.compare(w.get(b), w.get(a));
                }
            });
            JSArray out = new JSArray();
            for (int i = 0; i < order.length && i < MAX_DUP_GROUPS; i++) out.put(found.get(order[i]));
            return out;
        }

        private void addGroup(ArrayList<JSObject> found, ArrayList<Long> wasted, long size, ArrayList<String> paths,
                              HashSet<String> members) {
            JSArray arr = new JSArray();
            Collections.sort(paths);
            for (int i = 0; i < paths.size() && i < MAX_DUP_PER_GROUP; i++) {
                JSObject o = new JSObject();
                o.put("path", paths.get(i));
                o.put("mtime", new File(paths.get(i)).lastModified());
                arr.put(o);
                if (i > 0) members.add(paths.get(i));
            }
            JSObject g = new JSObject();
            g.put("size", size);
            g.put("count", paths.size());
            g.put("files", arr);
            found.add(g);
            wasted.add(size * (paths.size() - 1));
        }

        private String hashFile(File f, long size, boolean partial) {
            try (RandomAccessFile raf = new RandomAccessFile(f, "r")) {
                MessageDigest md = MessageDigest.getInstance("SHA-1");
                byte[] buf = new byte[BUF];
                if (partial && size > 2L * PARTIAL) {
                    feed(raf, md, buf, PARTIAL);
                    raf.seek(size - PARTIAL);
                    feed(raf, md, buf, PARTIAL);
                } else {
                    feed(raf, md, buf, Long.MAX_VALUE);
                }
                return hex(md.digest());
            } catch (Cancelled c) {
                throw c;
            } catch (Exception e) {
                return null;
            }
        }

        private void feed(RandomAccessFile raf, MessageDigest md, byte[] buf, long limit) throws IOException {
            long left = limit;
            while (left > 0) {
                int n = raf.read(buf, 0, (int) Math.min(buf.length, left));
                if (n <= 0) break;
                md.update(buf, 0, n);
                left -= n;
                hashDone += n;
                checkCancel();
                emit("hash", hashDone, hashTotal, null, false);
            }
        }

        // ------------------------------------------------------------ похожие фото

        private JSArray findSimilar(HashSet<String> skip) {
            ArrayList<FileInfo> list = new ArrayList<>();
            for (FileInfo fi : images) {
                if (!skip.contains(fi.path)) list.add(fi);
            }
            int n = list.size();
            long[] hashes = new long[n];
            int[] widths = new int[n];
            int[] heights = new int[n];
            boolean[] ok = new boolean[n];
            int[] dims = new int[2];
            for (int i = 0; i < n; i++) {
                checkCancel();
                dims[0] = 0;
                dims[1] = 0;
                long h = dHash(list.get(i).path, dims);
                if (dims[0] > 0 && h != 0L && h != -1L) {
                    hashes[i] = h;
                    widths[i] = dims[0];
                    heights[i] = dims[1];
                    ok[i] = true;
                }
                if ((i & 7) == 0) emit("images", i, n, null, false);
            }
            emit("images", n, n, null, true);

            int[] parent = new int[n];
            for (int i = 0; i < n; i++) parent[i] = i;
            ArrayList<HashMap<Integer, ArrayList<Integer>>> buckets = new ArrayList<>();
            for (int c = 0; c < 8; c++) buckets.add(new HashMap<Integer, ArrayList<Integer>>());
            for (int i = 0; i < n; i++) {
                if (!ok[i]) continue;
                checkCancel();
                for (int c = 0; c < 8; c++) {
                    int key = (int) ((hashes[i] >>> (8 * c)) & 0xFF);
                    HashMap<Integer, ArrayList<Integer>> map = buckets.get(c);
                    ArrayList<Integer> bucket = map.get(key);
                    if (bucket == null) {
                        bucket = new ArrayList<>();
                        map.put(key, bucket);
                    }
                    if (bucket.size() <= 300) {
                        for (int j : bucket) {
                            if (Long.bitCount(hashes[i] ^ hashes[j]) <= 6) union(parent, i, j);
                        }
                    }
                    bucket.add(i);
                }
            }

            HashMap<Integer, ArrayList<Integer>> clusters = new HashMap<>();
            for (int i = 0; i < n; i++) {
                if (!ok[i]) continue;
                int r = find(parent, i);
                ArrayList<Integer> l = clusters.get(r);
                if (l == null) {
                    l = new ArrayList<>();
                    clusters.put(r, l);
                }
                l.add(i);
            }
            ArrayList<JSObject> out = new ArrayList<>();
            final ArrayList<Long> weight = new ArrayList<>();
            for (ArrayList<Integer> members : clusters.values()) {
                if (members.size() < 2 || members.size() > 60) continue;
                JSArray arr = new JSArray();
                long total = 0;
                for (int idx : members) {
                    FileInfo fi = list.get(idx);
                    JSObject o = new JSObject();
                    o.put("path", fi.path);
                    o.put("size", fi.size);
                    o.put("mtime", new File(fi.path).lastModified());
                    o.put("w", widths[idx]);
                    o.put("h", heights[idx]);
                    arr.put(o);
                    total += fi.size;
                }
                JSObject g = new JSObject();
                g.put("files", arr);
                out.add(g);
                weight.add(total);
            }
            Integer[] order = new Integer[out.size()];
            for (int i = 0; i < order.length; i++) order[i] = i;
            Arrays.sort(order, new Comparator<Integer>() {
                @Override
                public int compare(Integer a, Integer b) {
                    return Long.compare(weight.get(b), weight.get(a));
                }
            });
            JSArray res = new JSArray();
            for (int i = 0; i < order.length && i < MAX_SIMILAR_GROUPS; i++) res.put(out.get(order[i]));
            return res;
        }

        private int find(int[] parent, int x) {
            int r = x;
            while (parent[r] != r) r = parent[r];
            while (parent[x] != r) {
                int next = parent[x];
                parent[x] = r;
                x = next;
            }
            return r;
        }

        private void union(int[] parent, int a, int b) {
            int ra = find(parent, a);
            int rb = find(parent, b);
            if (ra != rb) parent[ra] = rb;
        }

        /** dHash 8×8: сравнение яркости соседних пикселей уменьшенного изображения. */
        private long dHash(String path, int[] dims) {
            Bitmap bmp = null;
            Bitmap small = null;
            try {
                BitmapFactory.Options bounds = new BitmapFactory.Options();
                bounds.inJustDecodeBounds = true;
                BitmapFactory.decodeFile(path, bounds);
                if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return 0L;
                dims[0] = bounds.outWidth;
                dims[1] = bounds.outHeight;
                int sample = 1;
                int longest = Math.max(bounds.outWidth, bounds.outHeight);
                while (longest / (sample * 2) >= 128) sample *= 2;
                BitmapFactory.Options opts = new BitmapFactory.Options();
                opts.inSampleSize = sample;
                opts.inPreferredConfig = Bitmap.Config.RGB_565;
                bmp = BitmapFactory.decodeFile(path, opts);
                if (bmp == null) {
                    dims[0] = 0;
                    return 0L;
                }
                small = Bitmap.createScaledBitmap(bmp, 9, 8, true);
                long hash = 0L;
                int bit = 0;
                for (int y = 0; y < 8; y++) {
                    for (int x = 0; x < 8; x++) {
                        if (luma(small.getPixel(x, y)) > luma(small.getPixel(x + 1, y))) hash |= (1L << bit);
                        bit++;
                    }
                }
                return hash;
            } catch (Exception | OutOfMemoryError e) {
                dims[0] = 0;
                return 0L;
            } finally {
                if (small != null && small != bmp) small.recycle();
                if (bmp != null) bmp.recycle();
            }
        }

        private int luma(int p) {
            return (299 * Color.red(p) + 587 * Color.green(p) + 114 * Color.blue(p)) / 1000;
        }

        // ------------------------------------------------------------ результаты

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
            PackageManager pm = getContext().getPackageManager();
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

        private void emit(String phase, long done, long total, String current, boolean force) {
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
            notifyListeners("scanProgress", o);
        }
    }

    private static String extOf(String lowerName) {
        int dot = lowerName.lastIndexOf('.');
        return dot >= 0 && dot < lowerName.length() - 1 ? lowerName.substring(dot + 1) : "";
    }

    private static int kindIndex(String ext) {
        if (IMG_EXT.contains(ext)) return 0;
        if (VIDEO_EXT.contains(ext)) return 1;
        if (AUDIO_EXT.contains(ext)) return 2;
        if (DOC_EXT.contains(ext)) return 3;
        if (ARCHIVE_EXT.contains(ext)) return 4;
        if (APK_EXT.contains(ext)) return 5;
        return 6;
    }

    private static String junkFileKind(String lower, String ext) {
        if (lower.startsWith(".trashed-")) return "trashed";
        if (lower.startsWith(".thumbdata")) return "thumbnails";
        if (lower.equals("thumbs.db") || lower.equals(".ds_store") || lower.equals("desktop.ini")) return "system";
        if (TEMP_EXT.contains(ext) || lower.endsWith("~")) return "temp";
        if (ext.equals("log")) return "logs";
        return null;
    }

    private static String junkDirKind(String lower) {
        switch (lower) {
            case ".thumbnails":
            case ".thumbcache":
                return "thumbnails";
            case ".cache":
            case "cache":
                return "cache";
            case ".tmp":
            case "tmp":
            case ".temp":
            case "temp":
                return "temp";
            case "lost.dir":
                return "system";
            case ".trash":
            case ".trashbin":
                return "trash";
            case ".statuses":
                return "messenger";
            default:
                return null;
        }
    }

    private static String hex(byte[] bytes) {
        char[] digits = "0123456789abcdef".toCharArray();
        char[] out = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            out[i * 2] = digits[(bytes[i] >> 4) & 0xF];
            out[i * 2 + 1] = digits[bytes[i] & 0xF];
        }
        return new String(out);
    }

    // =====================================================================================
    //  КОРЗИНА
    // =====================================================================================

    private static final class TrashEntry {
        final File dir;
        final File content;
        final File metaFile;
        final JSONObject meta;

        TrashEntry(File dir, File content, File metaFile, JSONObject meta) {
            this.dir = dir;
            this.content = content;
            this.metaFile = metaFile;
            this.meta = meta;
        }

        String id() {
            return meta.optString("id");
        }
    }

    private File rootFor(File f) {
        File best = null;
        String path = f.getAbsolutePath();
        for (File r : FsUtil.storageRoots(getContext())) {
            String rp = r.getAbsolutePath();
            if ((path.equals(rp) || path.startsWith(rp + "/")) && (best == null || rp.length() > best.getAbsolutePath().length())) {
                best = r;
            }
        }
        return best;
    }

    private File trashDir(File root, boolean create) throws IOException {
        File td = new File(root, TRASH_DIR);
        if (create) {
            if (!td.isDirectory() && !td.mkdirs()) throw new IOException("Не удалось создать корзину");
            File nomedia = new File(td, ".nomedia");
            if (!nomedia.exists()) {
                //noinspection ResultOfMethodCallIgnored
                nomedia.createNewFile();
            }
        }
        return td;
    }

    private List<TrashEntry> readTrash() {
        List<TrashEntry> out = new ArrayList<>();
        for (File root : FsUtil.storageRoots(getContext())) {
            File td = new File(root, TRASH_DIR);
            File[] metas = td.listFiles();
            if (metas == null) continue;
            for (File m : metas) {
                String n = m.getName();
                if (!n.endsWith(".json")) continue;
                String id = n.substring(0, n.length() - 5);
                File content = new File(td, id);
                try {
                    JSONObject meta = new JSONObject(readUtf8(m));
                    if (!content.exists()) {
                        //noinspection ResultOfMethodCallIgnored
                        m.delete();
                        continue;
                    }
                    meta.put("id", id);
                    out.add(new TrashEntry(td, content, m, meta));
                } catch (Exception e) {
                    // повреждённые метаданные — пропускаем
                }
            }
        }
        return out;
    }

    private static String readUtf8(File f) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (InputStream in = new FileInputStream(f)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
        }
        return new String(bos.toByteArray(), StandardCharsets.UTF_8);
    }

    private static void writeUtf8(File f, String text) throws IOException {
        try (OutputStream out = new FileOutputStream(f, false)) {
            out.write(text.getBytes(StandardCharsets.UTF_8));
        }
    }

    /** Прогресс операций корзины: fraction 0..1 и имя текущего файла. */
    private void emitTrash(double fraction, String current, boolean force) {
        long now = SystemClock.elapsedRealtime();
        if (!force && now - lastTrashEmit < 80) return;
        lastTrashEmit = now;
        JSObject o = new JSObject();
        o.put("fraction", Math.max(0, Math.min(1, fraction)));
        if (current != null) o.put("current", current);
        notifyListeners("trashProgress", o);
    }

    @PluginMethod
    public void trashCancel(PluginCall call) {
        trashCancel = true;
        call.resolve();
    }

    /** Удаляет записи корзины с прогрессом по байтам. Возвращает {done, bytes, cancelled}. */
    private JSObject deleteEntries(List<TrashEntry> targets, boolean sweepOrphans) {
        trashCancel = false;
        long totalBytes = 0;
        for (TrashEntry t : targets) totalBytes += t.meta.optLong("size");
        final long total = totalBytes;
        final long[] doneBytes = { 0 };
        final int count = targets.size();
        int done = 0;
        long freed = 0;
        boolean cancelled = false;
        for (TrashEntry t : targets) {
            if (trashCancel) { cancelled = true; break; }
            final int index = done;
            long size = t.meta.optLong("size");
            boolean ok = FsUtil.deleteRecursive(t.content, (bytes, name) -> {
                doneBytes[0] += bytes;
                emitTrash(total > 0 ? (double) doneBytes[0] / total : (double) index / Math.max(1, count), name, false);
            });
            if (ok) {
                //noinspection ResultOfMethodCallIgnored
                t.metaFile.delete();
                done++;
                freed += size;
            }
            if (total == 0) emitTrash((double) done / Math.max(1, count), t.meta.optString("name"), false);
        }
        if (sweepOrphans && !cancelled) {
            // осиротевшие файлы без метаданных
            for (File root : FsUtil.storageRoots(getContext())) {
                File[] rest = new File(root, TRASH_DIR).listFiles();
                if (rest == null) continue;
                for (File f : rest) {
                    if (f.getName().equals(".nomedia")) continue;
                    FsUtil.deleteRecursive(f);
                }
            }
        }
        emitTrash(1, null, true);
        JSObject ret = new JSObject();
        ret.put("done", done);
        ret.put("bytes", freed);
        ret.put("cancelled", cancelled);
        return ret;
    }

    @PluginMethod
    public void trashMove(final PluginCall call) {
        final List<String> paths = strings(call.getArray("paths"));
        async(call, () -> {
            JSArray failed = new JSArray();
            int done = 0;
            long moved = 0;
            for (String p : paths) {
                File src = new File(p);
                try {
                    if (!src.exists()) throw new IOException("нет файла");
                    File root = rootFor(src);
                    if (root == null) throw new IOException("неизвестный накопитель");
                    if (src.getAbsolutePath().equals(root.getAbsolutePath())) throw new IOException("корень");
                    File td = trashDir(root, true);
                    String tdPath = td.getAbsolutePath();
                    if (src.getAbsolutePath().equals(tdPath) || src.getAbsolutePath().startsWith(tdPath + "/")) {
                        throw new IOException("уже в корзине");
                    }
                    String id = UUID.randomUUID().toString();
                    long size = FsUtil.sizeOf(src);
                    JSONObject meta = new JSONObject();
                    meta.put("name", src.getName());
                    meta.put("orig", src.getAbsolutePath());
                    meta.put("time", System.currentTimeMillis());
                    meta.put("size", size);
                    meta.put("dir", src.isDirectory());
                    File metaFile = new File(td, id + ".json");
                    writeUtf8(metaFile, meta.toString());
                    if (!FsUtil.moveTo(src, new File(td, id))) {
                        //noinspection ResultOfMethodCallIgnored
                        metaFile.delete();
                        throw new IOException("не удалось переместить");
                    }
                    done++;
                    moved += size;
                } catch (Exception e) {
                    failed.put(p);
                }
            }
            JSObject ret = new JSObject();
            ret.put("done", done);
            ret.put("bytes", moved);
            ret.put("failed", failed);
            call.resolve(ret);
        });
    }

    @PluginMethod
    public void trashList(final PluginCall call) {
        async(call, () -> {
            List<TrashEntry> entries = readTrash();
            Collections.sort(entries, new Comparator<TrashEntry>() {
                @Override
                public int compare(TrashEntry a, TrashEntry b) {
                    return Long.compare(b.meta.optLong("time"), a.meta.optLong("time"));
                }
            });
            JSArray items = new JSArray();
            long total = 0;
            for (TrashEntry t : entries) {
                JSObject o = new JSObject();
                o.put("id", t.id());
                o.put("name", t.meta.optString("name"));
                o.put("orig", t.meta.optString("orig"));
                o.put("time", t.meta.optLong("time"));
                o.put("size", t.meta.optLong("size"));
                o.put("dir", t.meta.optBoolean("dir"));
                items.put(o);
                total += t.meta.optLong("size");
            }
            JSObject ret = new JSObject();
            ret.put("items", items);
            ret.put("count", entries.size());
            ret.put("bytes", total);
            call.resolve(ret);
        });
    }

    @PluginMethod
    public void trashRestore(final PluginCall call) {
        final Set<String> ids = new HashSet<>(strings(call.getArray("ids")));
        async(call, () -> {
            JSArray failed = new JSArray();
            JSArray restored = new JSArray();
            List<TrashEntry> targets = new ArrayList<>();
            for (TrashEntry t : readTrash()) if (ids.contains(t.id())) targets.add(t);
            trashCancel = false;
            boolean cancelled = false;
            int done = 0;
            int step = 0;
            for (TrashEntry t : targets) {
                if (trashCancel) { cancelled = true; break; }
                emitTrash((double) step / Math.max(1, targets.size()), t.meta.optString("name"), false);
                step++;
                try {
                    File orig = new File(t.meta.optString("orig"));
                    File parent = orig.getParentFile();
                    if (parent == null) throw new IOException("нет папки");
                    if (!parent.isDirectory() && !parent.mkdirs()) throw new IOException("нет папки");
                    File target = FsUtil.uniqueTarget(parent, orig.getName());
                    if (!FsUtil.moveTo(t.content, target)) throw new IOException("не удалось восстановить");
                    //noinspection ResultOfMethodCallIgnored
                    t.metaFile.delete();
                    restored.put(target.getAbsolutePath());
                    done++;
                } catch (Exception e) {
                    failed.put(t.id());
                }
            }
            emitTrash(1, null, true);
            JSObject ret = new JSObject();
            ret.put("done", done);
            ret.put("failed", failed);
            ret.put("restored", restored);
            ret.put("cancelled", cancelled);
            call.resolve(ret);
        });
    }

    @PluginMethod
    public void trashDelete(final PluginCall call) {
        final Set<String> ids = new HashSet<>(strings(call.getArray("ids")));
        async(call, () -> {
            List<TrashEntry> targets = new ArrayList<>();
            for (TrashEntry t : readTrash()) if (ids.contains(t.id())) targets.add(t);
            call.resolve(deleteEntries(targets, false));
        });
    }

    @PluginMethod
    public void trashEmpty(final PluginCall call) {
        async(call, () -> call.resolve(deleteEntries(readTrash(), true)));
    }

    @PluginMethod
    public void trashPurge(final PluginCall call) {
        final int days = call.getInt("days", 30) == null ? 30 : call.getInt("days", 30);
        async(call, () -> {
            long limit = System.currentTimeMillis() - days * 86400000L;
            int done = 0;
            long freed = 0;
            for (TrashEntry t : readTrash()) {
                if (t.meta.optLong("time") > limit) continue;
                long size = t.meta.optLong("size");
                if (FsUtil.deleteRecursive(t.content)) {
                    //noinspection ResultOfMethodCallIgnored
                    t.metaFile.delete();
                    done++;
                    freed += size;
                }
            }
            JSObject ret = new JSObject();
            ret.put("done", done);
            ret.put("bytes", freed);
            call.resolve(ret);
        });
    }
}
