package dev.filedeck.app;

import android.os.SystemClock;

import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.CapacitorPlugin;

import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Корзина FileDeck: перенос в скрытую папку на том же накопителе (мгновенно), список, восстановление,
 * удаление навсегда с прогрессом и автоочистка по сроку. Хранилище — TrashStore.
 */
@CapacitorPlugin(name = "Trash")
public class TrashPlugin extends Plugin {

    private final JobRunner jobs = new JobRunner(3);
    private TrashStore store;
    private volatile boolean cancelRequested = false;
    private long lastEmit = 0;

    private TrashStore store() {
        if (store == null) store = new TrashStore(getContext());
        return store;
    }

    @Override
    protected void handleOnDestroy() {
        cancelRequested = true;
        jobs.shutdown();
        super.handleOnDestroy();
    }

    /** Прогресс операций корзины: fraction 0..1 и имя текущего файла. */
    private void emit(double fraction, String current, boolean force) {
        long now = SystemClock.elapsedRealtime();
        if (!force && now - lastEmit < 80) return;
        lastEmit = now;
        JSObject o = new JSObject();
        o.put("fraction", Math.max(0, Math.min(1, fraction)));
        if (current != null) o.put("current", current);
        notifyListeners("progress", o);
    }

    @PluginMethod
    public void cancel(PluginCall call) {
        cancelRequested = true;
        call.resolve();
    }

    /** Удаляет записи корзины с прогрессом по байтам. Возвращает {done, bytes, cancelled}. */
    private JSObject deleteEntries(List<TrashStore.TrashEntry> targets, boolean sweepOrphans) {
        cancelRequested = false;
        long totalBytes = 0;
        for (TrashStore.TrashEntry t : targets) totalBytes += t.meta.optLong("size");
        final long total = totalBytes;
        final long[] doneBytes = { 0 };
        final int count = targets.size();
        int done = 0;
        long freed = 0;
        boolean cancelled = false;
        for (TrashStore.TrashEntry t : targets) {
            if (cancelRequested) { cancelled = true; break; }
            final int index = done;
            long size = t.meta.optLong("size");
            boolean ok = FsUtil.deleteRecursive(t.content, (bytes, name) -> {
                doneBytes[0] += bytes;
                emit(total > 0 ? (double) doneBytes[0] / total : (double) index / Math.max(1, count), name, false);
            });
            if (ok) {
                //noinspection ResultOfMethodCallIgnored
                t.metaFile.delete();
                done++;
                freed += size;
            }
            if (total == 0) emit((double) done / Math.max(1, count), t.meta.optString("name"), false);
        }
        if (sweepOrphans && !cancelled) {
            // осиротевшие файлы без метаданных
            for (File root : FsUtil.storageRoots(getContext())) {
                File[] rest = new File(root, TrashStore.TRASH_DIR).listFiles();
                if (rest == null) continue;
                for (File f : rest) {
                    if (f.getName().equals(".nomedia")) continue;
                    FsUtil.deleteRecursive(f);
                }
            }
        }
        emit(1, null, true);
        JSObject ret = new JSObject();
        ret.put("done", done);
        ret.put("bytes", freed);
        ret.put("cancelled", cancelled);
        return ret;
    }

    @PluginMethod
    public void move(final PluginCall call) {
        final List<String> paths = JsArrays.strings(call.getArray("paths"));
        jobs.async(call, () -> {
            JSArray failed = new JSArray();
            int done = 0;
            long moved = 0;
            for (String p : paths) {
                File src = new File(p);
                try {
                    if (!src.exists()) throw new IOException("нет файла");
                    File root = store().rootFor(src);
                    if (root == null) throw new IOException("неизвестный накопитель");
                    if (src.getAbsolutePath().equals(root.getAbsolutePath())) throw new IOException("корень");
                    File td = store().trashDir(root, true);
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
                    TrashStore.writeUtf8(metaFile, meta.toString());
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
    public void list(final PluginCall call) {
        jobs.async(call, () -> {
            List<TrashStore.TrashEntry> entries = store().readTrash();
            Collections.sort(entries, new Comparator<TrashStore.TrashEntry>() {
                @Override
                public int compare(TrashStore.TrashEntry a, TrashStore.TrashEntry b) {
                    return Long.compare(b.meta.optLong("time"), a.meta.optLong("time"));
                }
            });
            JSArray items = new JSArray();
            long total = 0;
            for (TrashStore.TrashEntry t : entries) {
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
    public void restore(final PluginCall call) {
        final Set<String> ids = new HashSet<>(JsArrays.strings(call.getArray("ids")));
        jobs.async(call, () -> {
            JSArray failed = new JSArray();
            JSArray restored = new JSArray();
            List<TrashStore.TrashEntry> targets = new ArrayList<>();
            for (TrashStore.TrashEntry t : store().readTrash()) if (ids.contains(t.id())) targets.add(t);
            cancelRequested = false;
            boolean cancelled = false;
            int done = 0;
            int step = 0;
            for (TrashStore.TrashEntry t : targets) {
                if (cancelRequested) { cancelled = true; break; }
                emit((double) step / Math.max(1, targets.size()), t.meta.optString("name"), false);
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
            emit(1, null, true);
            JSObject ret = new JSObject();
            ret.put("done", done);
            ret.put("failed", failed);
            ret.put("restored", restored);
            ret.put("cancelled", cancelled);
            call.resolve(ret);
        });
    }

    @PluginMethod
    public void remove(final PluginCall call) {
        final Set<String> ids = new HashSet<>(JsArrays.strings(call.getArray("ids")));
        jobs.async(call, () -> {
            List<TrashStore.TrashEntry> targets = new ArrayList<>();
            for (TrashStore.TrashEntry t : store().readTrash()) if (ids.contains(t.id())) targets.add(t);
            call.resolve(deleteEntries(targets, false));
        });
    }

    @PluginMethod
    public void empty(final PluginCall call) {
        jobs.async(call, () -> call.resolve(deleteEntries(store().readTrash(), true)));
    }

    @PluginMethod
    public void purge(final PluginCall call) {
        final int days = call.getInt("days", 30) == null ? 30 : call.getInt("days", 30);
        jobs.async(call, () -> {
            long limit = System.currentTimeMillis() - days * 86400000L;
            int done = 0;
            long freed = 0;
            for (TrashStore.TrashEntry t : store().readTrash()) {
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
