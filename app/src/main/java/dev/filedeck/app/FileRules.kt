package dev.filedeck.app

internal object FileRules {
    val APK_EXT: Set<String> get() = APK_EXTENSIONS

    private val TEMP_EXT = setOf("tmp", "temp", "crdownload", "part", "partial", "opdownload")

    val PROTECTED_DIRS = setOf(
        "dcim", "download", "downloads", "pictures", "music", "movies", "documents", "android", "alarms",
        "notifications", "ringtones", "podcasts", "audiobooks", "recordings", "screenshots",
    )

    fun extension(lowerName: String): String {
        val dot = lowerName.lastIndexOf('.')
        return if (dot >= 0 && dot < lowerName.length - 1) lowerName.substring(dot + 1) else ""
    }

    fun usageIndex(ext: String): Int = when (kindOfExtension(ext)) {
        FileKind.Image -> 0
        FileKind.Video -> 1
        FileKind.Audio -> 2
        FileKind.Doc, FileKind.Pdf, FileKind.Text -> 3
        FileKind.Archive -> 4
        FileKind.Apk -> 5
        else -> 6
    }

    fun junkFileKind(lower: String, ext: String): String? = when {
        lower.startsWith(".trashed-") -> "trashed"
        lower.startsWith(".thumbdata") -> "thumbnails"
        lower == "thumbs.db" || lower == ".ds_store" || lower == "desktop.ini" -> "system"
        ext in TEMP_EXT || lower.endsWith("~") -> "temp"
        ext == "log" -> "logs"
        else -> null
    }

    fun junkDirKind(lower: String): String? = when (lower) {
        ".thumbnails", ".thumbcache" -> "thumbnails"
        ".cache", "cache" -> "cache"
        ".tmp", "tmp", ".temp", "temp" -> "temp"
        "lost.dir" -> "system"
        ".trash", ".trashbin" -> "trash"
        ".statuses" -> "messenger"
        else -> null
    }
}
