package dev.filedeck.app;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/** Типы файлов по расширению и признаки мусора: чистые функции без Android. */
final class FileKinds {
    private FileKinds() {}

    static final Set<String> IMG_EXT = set("jpg", "jpeg", "png", "webp", "heic", "heif", "bmp");
    static final Set<String> VIDEO_EXT = set("mp4", "mkv", "webm", "avi", "mov", "3gp", "m4v", "flv", "wmv", "ts");
    static final Set<String> AUDIO_EXT = set("mp3", "flac", "wav", "ogg", "m4a", "aac", "opus", "amr", "wma", "mid");
    static final Set<String> DOC_EXT = set("pdf", "doc", "docx", "odt", "rtf", "xls", "xlsx", "ods", "ppt", "pptx", "odp", "txt", "md", "csv", "epub", "fb2", "djvu");
    static final Set<String> ARCHIVE_EXT = set("zip", "rar", "7z", "tar", "gz", "bz2", "xz", "iso");
    static final Set<String> APK_EXT = set("apk", "xapk", "apks", "apkm", "aab");
    static final Set<String> TEMP_EXT = set("tmp", "temp", "crdownload", "part", "partial", "opdownload");
    static final Set<String> PROTECTED_DIRS = set(
        "dcim", "download", "downloads", "pictures", "music", "movies", "documents", "android", "alarms",
        "notifications", "ringtones", "podcasts", "audiobooks", "recordings", "screenshots", "dcim"
    );

    static Set<String> set(String... values) {
        return new HashSet<>(Arrays.asList(values));
    }

    static String extOf(String lowerName) {
        int dot = lowerName.lastIndexOf('.');
        return dot >= 0 && dot < lowerName.length() - 1 ? lowerName.substring(dot + 1) : "";
    }

    static int kindIndex(String ext) {
        if (IMG_EXT.contains(ext)) return 0;
        if (VIDEO_EXT.contains(ext)) return 1;
        if (AUDIO_EXT.contains(ext)) return 2;
        if (DOC_EXT.contains(ext)) return 3;
        if (ARCHIVE_EXT.contains(ext)) return 4;
        if (APK_EXT.contains(ext)) return 5;
        return 6;
    }

    static String junkFileKind(String lower, String ext) {
        if (lower.startsWith(".trashed-")) return "trashed";
        if (lower.startsWith(".thumbdata")) return "thumbnails";
        if (lower.equals("thumbs.db") || lower.equals(".ds_store") || lower.equals("desktop.ini")) return "system";
        if (TEMP_EXT.contains(ext) || lower.endsWith("~")) return "temp";
        if (ext.equals("log")) return "logs";
        return null;
    }

    static String junkDirKind(String lower) {
        switch (lower) {
            case ".thumbnails":
            case ".thumbcache":
                return "thumbnails";
            case ".cache":
            case "cache":
                return "cache";
            case ".tmp":
            case "tmp":
            case ".temp":
            case "temp":
                return "temp";
            case "lost.dir":
                return "system";
            case ".trash":
            case ".trashbin":
                return "trash";
            case ".statuses":
                return "messenger";
            default:
                return null;
        }
    }
}
