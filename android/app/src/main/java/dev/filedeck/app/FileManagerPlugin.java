package dev.filedeck.app;

import android.Manifest;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.Settings;

import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.PermissionState;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;
import com.getcapacitor.annotation.PermissionCallback;

import java.io.File;
import java.util.List;

/**
 * Мост между JavaScript и файловой системой: принимает вызовы интерфейса и передаёт их классам
 * FileBrowser, FileOps, TextFiles, FileOpener. Работает с обычными путями (/storage/emulated/0/...),
 * поэтому требует «Доступ ко всем файлам» (MANAGE_EXTERNAL_STORAGE) на Android 11+.
 */
@CapacitorPlugin(
    name = "FileManager",
    permissions = {
        @Permission(
            alias = "storage",
            strings = { Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE }
        )
    }
)
public class FileManagerPlugin extends Plugin {

    private final JobRunner jobs = new JobRunner(4);

    @Override
    protected void handleOnDestroy() {
        jobs.shutdown();
        super.handleOnDestroy();
    }

    // ---------------------------------------------------------------- разрешения

    private boolean hasAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return Environment.isExternalStorageManager();
        }
        return getPermissionState("storage") == PermissionState.GRANTED;
    }

    @PluginMethod
    public void checkPermission(PluginCall call) {
        JSObject ret = new JSObject();
        ret.put("granted", hasAccess());
        call.resolve(ret);
    }

    @PluginMethod
    public void requestPermission(PluginCall call) {
        if (hasAccess()) {
            JSObject ret = new JSObject();
            ret.put("granted", true);
            call.resolve(ret);
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Открываем системный экран «Доступ ко всем файлам». Результат приложение
            // перепроверяет само, когда пользователь возвращается.
            try {
                Intent intent = new Intent(
                    Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                    Uri.parse("package:" + getContext().getPackageName())
                );
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                getContext().startActivity(intent);
            } catch (Exception e) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                getContext().startActivity(intent);
            }
            JSObject ret = new JSObject();
            ret.put("granted", false);
            call.resolve(ret);
        } else {
            requestPermissionForAlias("storage", call, "storagePermissionCallback");
        }
    }

    @PermissionCallback
    private void storagePermissionCallback(PluginCall call) {
        JSObject ret = new JSObject();
        ret.put("granted", getPermissionState("storage") == PermissionState.GRANTED);
        call.resolve(ret);
    }

    // ---------------------------------------------------------------- хранилища

    @PluginMethod
    public void getRoots(PluginCall call) {
        jobs.async(call, () -> {
            JSArray roots = new JSArray();
            boolean primary = true;
            for (File root : FsUtil.storageRoots(getContext())) {
                roots.put(FileJson.root(primary ? "Внутренняя память" : "SD-карта", root, primary));
                primary = false;
            }
            JSObject ret = new JSObject();
            ret.put("roots", roots);
            call.resolve(ret);
        });
    }

    // ---------------------------------------------------------------- чтение каталогов

    @PluginMethod
    public void list(PluginCall call) {
        final String path = call.getString("path");
        jobs.async(call, () -> call.resolve(FileBrowser.list(path)));
    }

    @PluginMethod
    public void search(PluginCall call) {
        final String path = call.getString("path");
        final String query = call.getString("query", "");
        final int limit = call.getInt("limit", 400);
        jobs.async(call, () -> call.resolve(FileBrowser.search(path, query, limit)));
    }

    @PluginMethod
    public void size(PluginCall call) {
        final String path = call.getString("path");
        jobs.async(call, () -> call.resolve(FileBrowser.size(path)));
    }

    // ---------------------------------------------------------------- создание / переименование / удаление

    @PluginMethod
    public void mkdir(PluginCall call) {
        final String path = call.getString("path");
        jobs.async(call, () -> call.resolve(FileOps.mkdir(path)));
    }

    @PluginMethod
    public void createFile(PluginCall call) {
        final String path = call.getString("path");
        jobs.async(call, () -> call.resolve(FileOps.createFile(path)));
    }

    @PluginMethod
    public void rename(PluginCall call) {
        final String path = call.getString("path");
        final String name = call.getString("name");
        jobs.async(call, () -> call.resolve(FileOps.rename(path, name)));
    }

    @PluginMethod
    public void remove(PluginCall call) {
        final List<String> paths = JsArrays.strings(call.getArray("paths"));
        jobs.async(call, () -> call.resolve(FileOps.remove(paths)));
    }

    // ---------------------------------------------------------------- копирование / перемещение

    @PluginMethod
    public void copy(PluginCall call) {
        transfer(call, false);
    }

    @PluginMethod
    public void move(PluginCall call) {
        transfer(call, true);
    }

    private void transfer(PluginCall call, boolean move) {
        final List<String> paths = JsArrays.strings(call.getArray("paths"));
        final String dest = call.getString("dest");
        jobs.async(call, () -> call.resolve(FileOps.transfer(paths, dest, move)));
    }

    // ---------------------------------------------------------------- текст

    @PluginMethod
    public void readText(PluginCall call) {
        final String path = call.getString("path");
        jobs.async(call, () -> call.resolve(TextFiles.read(path)));
    }

    @PluginMethod
    public void writeText(PluginCall call) {
        final String path = call.getString("path");
        final String text = call.getString("text", "");
        jobs.async(call, () -> {
            TextFiles.write(path, text);
            call.resolve();
        });
    }

    // ---------------------------------------------------------------- открыть / поделиться

    @PluginMethod
    public void open(PluginCall call) {
        try {
            FileOpener.open(getContext(), call.getString("path"));
            call.resolve();
        } catch (Exception e) {
            call.reject(e.getMessage());
        }
    }

    @PluginMethod
    public void share(PluginCall call) {
        try {
            FileOpener.share(getContext(), JsArrays.strings(call.getArray("paths")));
            call.resolve();
        } catch (Exception e) {
            call.reject(e.getMessage());
        }
    }
}
