package dev.filedeck.app

import android.webkit.MimeTypeMap
import java.util.Locale

internal object MimeTypes {
    private val FALLBACK = mapOf(
        "md" to "text/plain", "log" to "text/plain", "ini" to "text/plain", "conf" to "text/plain",
        "yml" to "text/plain", "yaml" to "text/plain", "json" to "application/json",
    )

    fun of(name: String): String {
        val dot = name.lastIndexOf('.')
        if (dot < 0 || dot >= name.length - 1) return "application/octet-stream"
        val ext = name.substring(dot + 1).lowercase(Locale.ROOT)
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: FALLBACK[ext] ?: "application/octet-stream"
    }
}
