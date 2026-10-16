package dev.filedeck.app;

import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/** Поиск одинаковых файлов: группы по размеру → быстрый хеш краёв → полный хеш. */
final class DuplicateFinder {
    private static final int PARTIAL = 64 * 1024;
    private static final int BUF = 128 * 1024;
    private static final int MAX_DUP_GROUPS = 3000;
    private static final int MAX_DUP_PER_GROUP = 40;

    private final ScanHost host;
    private long hashDone;
    private long hashTotal;

    DuplicateFinder(ScanHost host) {
        this.host = host;
    }

    JSArray find(Map<Long, ArrayList<String>> sizeMany, Set<String> members) {
        ArrayList<Map.Entry<Long, ArrayList<String>>> groups = new ArrayList<>(sizeMany.entrySet());
        hashTotal = 0;
        hashDone = 0;
        for (Map.Entry<Long, ArrayList<String>> e : groups) hashTotal += e.getKey() * e.getValue().size();

        ArrayList<JSObject> found = new ArrayList<>();
        ArrayList<Long> wasted = new ArrayList<>();
        for (Map.Entry<Long, ArrayList<String>> e : groups) {
            host.checkCancel();
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
        host.emit("hash", hashTotal, hashTotal, null, true);

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
                          Set<String> members) {
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
            host.checkCancel();
            host.emit("hash", hashDone, hashTotal, null, false);
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
}
