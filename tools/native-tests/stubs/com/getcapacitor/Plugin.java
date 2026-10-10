package com.getcapacitor;
import android.app.Activity;
import android.content.Context;
import java.util.ArrayList;
import java.util.List;
/** Тестовая заглушка: повторяет публичные сигнатуры настоящего Plugin, которыми пользуется FileDeck. */
public class Plugin {
    public Context testContext;
    public final List<String> testEvents = new ArrayList<>();
    public Context getContext() { return testContext; }
    public Activity getActivity() { return null; }
    protected void handleOnDestroy() {}
    protected void notifyListeners(String eventName, JSObject data) { testEvents.add(eventName + " " + data); }
    public PermissionState getPermissionState(String alias) { return PermissionState.GRANTED; }
    protected void requestPermissionForAlias(String alias, PluginCall call, String callbackName) {}
}
