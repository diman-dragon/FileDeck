package dev.filedeck.app;

import android.os.SystemClock;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Архиватор FileDeck.
 *
 * Создание: ZIP (в т. ч. с паролем AES-256), 7z, TAR, TAR.GZ, TAR.BZ2, TAR.XZ.
 * Распаковка и просмотр: ZIP (с паролем), 7z (с паролем), TAR/TGZ/TBZ2/TXZ, одиночные GZ/BZ2/XZ, RAR (v4, через junrar).
 *
 * Все операции потоковые, с прогрессом (событие archiveProgress) и отменой. Распаковка защищена от Zip Slip,
 * символические ссылки внутри архивов пропускаются, при ошибке недоделанный результат удаляется.
 */
@CapacitorPlugin(name = "Archive")
public class ArchivePlugin extends Plugin implements ArchiveHost {

    private final ExecutorService pool = Executors.newFixedThreadPool(2);
    private volatile boolean cancelRequested = false;
    private volatile boolean busy = false;
    private long lastEmit = 0;

    private interface Job {
        JSObject run() throws Exception;
    }

    protected void handleOnDestroy() {
        cancelRequested = true;
        pool.shutdownNow();
        super.handleOnDestroy();
    }

    @Override
    public void checkCancel() {
        if (cancelRequested) throw new Cancelled();
    }

    @PluginMethod
    public void cancel(PluginCall call) {
        cancelRequested = true;
        call.resolve();
    }

    private void run(final PluginCall call, final Job job) {
        if (busy) {
            call.reject("Другая операция с архивом ещё выполняется");
            return;
        }
        busy = true;
        cancelRequested = false;
        pool.execute(() -> {
            JSObject result = null;
            String errMsg = null;
            String errCode = null;
            try {
                result = job.run();
            } catch (Cancelled c) {
                result = new JSObject();
                result.put("cancelled", true);
            } catch (ArchiveError e) {
                errMsg = e.getMessage();
                errCode = e.code;
            } catch (Throwable t) {
                String msg = t.getMessage();
                errMsg = msg == null || msg.isEmpty() ? t.getClass().getSimpleName() : msg;
                errCode = "FAILED";
            }
            // флаг снимаем ДО ответа: следующий вызов из интерфейса приходит сразу после resolve
            busy = false;
            if (errMsg != null) call.reject(errMsg, errCode);
            else call.resolve(result);
        });
    }

    @Override
    public void emit(String op, double fraction, String current, boolean force) {
        long now = SystemClock.elapsedRealtime();
        if (!force && now - lastEmit < 120) return;
        lastEmit = now;
        JSObject o = new JSObject();
        o.put("op", op);
        o.put("fraction", Math.max(0, Math.min(1, fraction)));
        if (current != null) o.put("current", current);
        notifyListeners("archiveProgress", o);
    }

    @PluginMethod
    public void create(final PluginCall call) {
        final List<String> paths = JsArrays.strings(call.getArray("paths"));
        final String destDir = call.getString("dest");
        final String name = call.getString("name", "archive");
        final ArchiveFormat fmt = ArchiveFormat.byName(call.getString("format", "zip"));
        final String password = call.getString("password", "");
        final Integer lv = call.getInt("level", 5);
        final int level = lv == null ? 5 : lv;
        run(call, () -> ArchiveCreator.create(this, paths, destDir, name, fmt, password, level));
    }

    @PluginMethod
    public void list(final PluginCall call) {
        final String path = call.getString("path");
        final String password = call.getString("password", "");
        run(call, () -> ArchiveLister.list(this, path, password));
    }

    @PluginMethod
    public void extract(final PluginCall call) {
        final String path = call.getString("path");
        final String destDir = call.getString("dest");
        final String password = call.getString("password", "");
        final String mode = call.getString("mode", "folder"); // folder | here
        final Set<String> only = new HashSet<>(JsArrays.strings(call.getArray("entries")));
        run(call, () -> ArchiveExtractor.extract(this, path, destDir, password, mode, only));
    }
}
