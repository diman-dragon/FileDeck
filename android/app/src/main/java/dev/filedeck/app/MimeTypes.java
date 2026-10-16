package dev.filedeck.app;

import android.webkit.MimeTypeMap;

import java.util.Locale;

/** MIME-тип по расширению файла. */
final class MimeTypes {
    private MimeTypes() {}

    static String of(String name) {
        int dot = name.lastIndexOf('.');
        if (dot >= 0 && dot < name.length() - 1) {
            String ext = name.substring(dot + 1).toLowerCase(Locale.ROOT);
            String mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);
            if (mime != null) return mime;
            switch (ext) {
                case "md":
                case "log":
                case "ini":
                case "conf":
                case "yml":
                case "yaml":
                    return "text/plain";
                case "json":
                    return "application/json";
                default:
                    break;
            }
        }
        return "application/octet-stream";
    }
}
