package dev.filedeck.app

import androidx.annotation.StringRes
import java.util.Calendar
import java.util.Locale

fun formatSize(bytes: Long): String {
    if (bytes < 0) return ""
    if (bytes < 1024) return "$bytes ${Str.s(R.string.unit_b)}"
    val units = arrayOf(R.string.unit_kb, R.string.unit_mb, R.string.unit_gb, R.string.unit_tb)
    var v = bytes / 1024.0
    var i = 0
    while (v >= 1024 && i < units.size - 1) {
        v /= 1024
        i++
    }
    val s = when {
        v >= 100 -> String.format(Locale.ROOT, "%.0f", v)
        v >= 10 -> String.format(Locale.ROOT, "%.1f", v)
        else -> String.format(Locale.ROOT, "%.2f", v)
    }
    return s.replace('.', ',') + " " + Str.s(units[i])
}

fun formatDate(ms: Long, withTime: Boolean = false): String {
    if (ms <= 0) return ""
    val d = Calendar.getInstance().apply { timeInMillis = ms }
    val now = Calendar.getInstance()
    val sameYear = d.get(Calendar.YEAR) == now.get(Calendar.YEAR)
    val month = Str.array(R.array.months_short).getOrElse(d.get(Calendar.MONTH)) { (d.get(Calendar.MONTH) + 1).toString() }
    var s = "${d.get(Calendar.DAY_OF_MONTH)} $month" + (if (sameYear) "" else " ${d.get(Calendar.YEAR)}")
    if (withTime) {
        s += String.format(Locale.ROOT, ", %02d:%02d", d.get(Calendar.HOUR_OF_DAY), d.get(Calendar.MINUTE))
    }
    return s
}

fun meta(vararg parts: String?): String = parts.filter { !it.isNullOrEmpty() }.joinToString(" · ")

fun shortLocation(dir: String): String =
    dir.replace(Regex("^/storage/(emulated/0|[^/]+)")) {
        if (it.groupValues[1] == "emulated/0") Str.s(R.string.storage_short_internal) else Str.s(R.string.storage_short_sd)
    }.ifEmpty { "/" }

fun basename(path: String): String = path.trimEnd('/').substringAfterLast('/')

fun parentOf(path: String): String {
    val p = path.trimEnd('/')
    val i = p.lastIndexOf('/')
    return if (i <= 0) "/" else p.substring(0, i)
}

fun joinPath(base: String, name: String): String =
    if (base.endsWith("/")) base + name else "$base/$name"

fun extOf(name: String): String {
    val i = name.lastIndexOf('.')
    return if (i > 0 && i < name.length - 1) name.substring(i + 1).lowercase(Locale.ROOT) else ""
}

enum class FileKind(@StringRes private val labelRes: Int) {
    Folder(R.string.kind_folder),
    Image(R.string.kind_image),
    Video(R.string.kind_video),
    Audio(R.string.kind_audio),
    Archive(R.string.kind_archive),
    Pdf(R.string.kind_pdf),
    Apk(R.string.kind_apk),
    Doc(R.string.kind_doc),
    Code(R.string.kind_code),
    Text(R.string.kind_text),
    File(R.string.kind_file),
;

    val label: String get() = Str.s(labelRes)
}

private val KIND_EXTS: Map<FileKind, Set<String>> = mapOf(
    FileKind.Image to setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif", "avif", "svg"),
    FileKind.Video to setOf("mp4", "mkv", "webm", "avi", "mov", "3gp", "m4v", "flv", "wmv"),
    FileKind.Audio to setOf("mp3", "flac", "wav", "ogg", "m4a", "aac", "opus", "amr", "wma", "mid"),
    FileKind.Archive to setOf("zip", "rar", "7z", "tar", "gz", "bz2", "xz", "iso"),
    FileKind.Pdf to setOf("pdf"),
    FileKind.Apk to setOf("apk", "xapk", "apks", "apkm", "aab"),
    FileKind.Doc to setOf("doc", "docx", "odt", "rtf", "xls", "xlsx", "ods", "ppt", "pptx", "odp", "epub", "fb2", "csv", "djvu"),
    FileKind.Code to setOf(
        "js", "mjs", "ts", "jsx", "tsx", "json", "html", "htm", "css", "scss", "xml", "java", "kt", "py", "c", "h",
        "cpp", "cs", "go", "rs", "rb", "php", "sh", "bat", "sql", "yml", "yaml", "toml", "gradle", "lua", "swift",
    ),
    FileKind.Text to setOf("txt", "md", "log", "ini", "conf", "cfg", "properties", "env", "gitignore", "srt", "nfo"),
)

private val KIND_BY_EXT: Map<String, FileKind> =
    KIND_EXTS.flatMap { (k, exts) -> exts.map { it to k } }.toMap()

/** Тип по расширению без точки (нижний регистр) или null. */
internal fun kindOfExtension(ext: String): FileKind? = KIND_BY_EXT[ext]

internal val APK_EXTENSIONS: Set<String> get() = KIND_EXTS.getValue(FileKind.Apk)

fun kindOf(name: String, dir: Boolean): FileKind =
    if (dir) FileKind.Folder else KIND_BY_EXT[extOf(name)] ?: FileKind.File

fun FileEntry.kind(): FileKind = kindOf(name, dir)

const val MAX_EDIT_BYTES = 2L * 1024 * 1024

fun FileEntry.isEditable(): Boolean {
    if (dir || size > MAX_EDIT_BYTES) return false
    val k = kind()
    return k == FileKind.Text || k == FileKind.Code || mime.startsWith("text/")
}

private val ARCHIVE_RE = Regex("""\.(zip|7z|rar|tar|tgz|tbz2?|txz|gz|bz2|xz|jar|cbz)$""", RegexOption.IGNORE_CASE)
private val MULTI_ARCHIVE_RE = Regex("""\.tar\.(gz|bz2|xz)$""", RegexOption.IGNORE_CASE)

fun isArchive(name: String): Boolean = ARCHIVE_RE.containsMatchIn(name) || MULTI_ARCHIVE_RE.containsMatchIn(name)

fun archiveBase(name: String): String =
    name.replace(MULTI_ARCHIVE_RE, "").replace(Regex("""\.[^.]+$"""), "")

enum class SortBy(@StringRes private val labelRes: Int) {
    NAME(R.string.sort_name),
    DATE(R.string.sort_date),
    SIZE(R.string.sort_size),
    TYPE(R.string.sort_type),
;

    val label: String get() = Str.s(labelRes)
}

/** Естественное сравнение имён: «файл2» раньше «файл10», регистр не важен. */
fun compareNames(a: String, b: String): Int {
    var i = 0
    var j = 0
    while (i < a.length && j < b.length) {
        val ca = a[i]
        val cb = b[j]
        if (ca.isDigit() && cb.isDigit()) {
            var ei = i
            while (ei < a.length && a[ei].isDigit()) ei++
            var ej = j
            while (ej < b.length && b[ej].isDigit()) ej++
            val na = a.substring(i, ei).trimStart('0')
            val nb = b.substring(j, ej).trimStart('0')
            if (na.length != nb.length) return na.length - nb.length
            val c = na.compareTo(nb)
            if (c != 0) return c
            i = ei
            j = ej
        } else {
            val c = ca.lowercaseChar().compareTo(cb.lowercaseChar())
            if (c != 0) return c
            i++
            j++
        }
    }
    return (a.length - i) - (b.length - j)
}

/** Папки всегда сверху. */
fun sortEntries(list: List<FileEntry>, by: SortBy, desc: Boolean): List<FileEntry> {
    val dir = if (desc) -1 else 1
    val cmp: Comparator<FileEntry> = when (by) {
        SortBy.NAME -> Comparator { a, b -> compareNames(a.name, b.name) }
        SortBy.SIZE -> Comparator { a, b ->
            val c = if (a.dir && b.dir) a.count.compareTo(b.count) else a.size.compareTo(b.size)
            if (c != 0) c else compareNames(a.name, b.name)
        }
        SortBy.DATE -> Comparator { a, b ->
            val c = a.mtime.compareTo(b.mtime)
            if (c != 0) c else compareNames(a.name, b.name)
        }
        SortBy.TYPE -> Comparator { a, b ->
            val c = compareNames(extOf(a.name), extOf(b.name))
            if (c != 0) c else compareNames(a.name, b.name)
        }
    }
    return list.sortedWith { a, b ->
        if (a.dir != b.dir) (if (a.dir) -1 else 1) else cmp.compare(a, b) * dir
    }
}
