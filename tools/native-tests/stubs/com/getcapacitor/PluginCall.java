package com.getcapacitor;
import java.util.concurrent.CompletableFuture;
/** Тестовая заглушка PluginCall с теми же сигнатурами, что и в Capacitor 8. */
public class PluginCall {
    private final JSObject data;
    public final CompletableFuture<JSObject> future = new CompletableFuture<>();
    public String errorMessage;
    public String errorCode;
    public PluginCall(JSObject data) { this.data = data; }
    public JSObject getData() { return data; }
    public String getString(String name) { return getString(name, null); }
    public String getString(String name, String def) { return data.has(name) && !data.isNull(name) ? data.optString(name) : def; }
    public Integer getInt(String name, Integer def) { return data.has(name) ? Integer.valueOf(data.optInt(name)) : def; }
    public Long getLong(String name, Long def) { return data.has(name) ? Long.valueOf(data.optLong(name)) : def; }
    public Boolean getBoolean(String name, Boolean def) { return data.has(name) ? Boolean.valueOf(data.optBoolean(name)) : def; }
    public JSObject getObject(String name) { return null; }
    public JSArray getArray(String name) {
        org.json.JSONArray a = data.optJSONArray(name);
        if (a == null) return null;
        try { return new JSArray(a.toString()); } catch (org.json.JSONException e) { return null; }
    }
    public void resolve(JSObject d) { future.complete(d); }
    public void resolve() { future.complete(new JSObject()); }
    public void reject(String msg) { reject(msg, null); }
    public void reject(String msg, String code) { errorMessage = msg; errorCode = code; future.complete(null); }
}
