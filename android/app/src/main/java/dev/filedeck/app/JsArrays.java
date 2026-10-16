package dev.filedeck.app;

import com.getcapacitor.JSArray;

import java.util.ArrayList;
import java.util.List;

/** Разбор массивов, пришедших из JavaScript. */
final class JsArrays {
    private JsArrays() {}

    /** Непустые строки массива; null превращается в пустой список. */
    static List<String> strings(JSArray arr) {
        List<String> out = new ArrayList<>();
        if (arr == null) return out;
        for (int i = 0; i < arr.length(); i++) {
            String s = arr.optString(i, null);
            if (s != null && !s.isEmpty()) out.add(s);
        }
        return out;
    }
}
