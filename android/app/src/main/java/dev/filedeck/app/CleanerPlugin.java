package dev.filedeck.app;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

import java.util.List;

/**
 * Глубокая очистка FileDeck.
 *
 * scan()      — один проход по накопителям: дубликаты, похожие фото, пустые папки и файлы,
 *               мусор и кэш, установщики APK, самые большие файлы, статистика по типам.
 */
@CapacitorPlugin(name = "Cleaner")
public class CleanerPlugin extends Plugin {




    private final JobRunner jobs = new JobRunner(3);
    private volatile boolean cancelRequested = false;
    private volatile boolean scanning = false;



    private void async(PluginCall call, JobRunner.Job job) {
        jobs.async(call, job);
    }

    @Override
    protected void handleOnDestroy() {
        cancelRequested = true;
        jobs.shutdown();
        super.handleOnDestroy();
    }

    private static boolean flag(PluginCall call, String name, boolean def) {
        Boolean b = call.getBoolean(name, def);
        return b == null ? def : b;
    }

    @PluginMethod
    public void scan(final PluginCall call) {
        if (scanning) {
            call.reject("Сканирование уже идёт");
            return;
        }
        final List<String> roots = JsArrays.strings(call.getArray("roots"));
        final List<String> excludes = JsArrays.strings(call.getArray("excludes"));
        if (roots.isEmpty()) {
            call.reject("Не выбрано, что сканировать");
            return;
        }
        final Scanner scan = new Scanner(
            getContext(),
            this::checkCancel,
            o -> notifyListeners("scanProgress", o),
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
        jobs.execute(() -> {
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






}
