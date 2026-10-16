package dev.filedeck.app;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;

import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

/** Поиск похожих фото по отпечатку яркости (dHash) и объединению близких кадров. */
final class SimilarImageFinder {
    private static final int MAX_SIMILAR_GROUPS = 500;

    private final ScanHost host;

    SimilarImageFinder(ScanHost host) {
        this.host = host;
    }

    JSArray find(List<FileInfo> images, Set<String> skip) {
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
            host.checkCancel();
            dims[0] = 0;
            dims[1] = 0;
            long h = dHash(list.get(i).path, dims);
            if (dims[0] > 0 && h != 0L && h != -1L) {
                hashes[i] = h;
                widths[i] = dims[0];
                heights[i] = dims[1];
                ok[i] = true;
            }
            if ((i & 7) == 0) host.emit("images", i, n, null, false);
        }
        host.emit("images", n, n, null, true);

        int[] parent = new int[n];
        for (int i = 0; i < n; i++) parent[i] = i;
        ArrayList<HashMap<Integer, ArrayList<Integer>>> buckets = new ArrayList<>();
        for (int c = 0; c < 8; c++) buckets.add(new HashMap<Integer, ArrayList<Integer>>());
        for (int i = 0; i < n; i++) {
            if (!ok[i]) continue;
            host.checkCancel();
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
}
