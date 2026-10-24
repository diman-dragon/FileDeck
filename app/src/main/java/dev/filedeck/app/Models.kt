package dev.filedeck.app

/** Файл или папка в списке. */
data class FileEntry(
    val name: String,
    val path: String,
    val dir: Boolean,
    val size: Long,
    val mtime: Long,
    /** Для папки — число элементов (если считали). */
    val count: Int = 0,
    val mime: String = "",
)

/** Накопитель: внутренняя память или SD-карта. */
data class StorageRoot(
    val name: String,
    val path: String,
    val primary: Boolean,
    val total: Long,
    val free: Long,
) {
    val used: Long get() = (total - free).coerceAtLeast(0)
    val percent: Int get() = if (total > 0) Math.round(used * 100.0 / total).toInt() else 0
}

data class SearchResult(val entries: List<FileEntry>, val truncated: Boolean)

data class SizeInfo(val size: Long, val files: Long, val folders: Long)

/** Итог пакетной операции: сколько сделано и какие пути не удались. */
data class OpResult(val done: Int, val failed: List<String>)

// ---------------------------------------------------------------- архивы

data class ArchiveProgress(val op: String, val fraction: Double, val current: String?)

data class ArchiveCreated(val path: String, val size: Long, val items: Int)

data class ArchiveEntryInfo(val name: String, val dir: Boolean, val size: Long, val mtime: Long)

data class ArchiveListing(
    val format: String,
    val entries: List<ArchiveEntryInfo>,
    val count: Long,
    val bytes: Long,
    val encrypted: Boolean,
    /** false — содержимое показать нельзя (RAR), но распаковать можно. */
    val listing: Boolean,
    val truncated: Boolean,
)

data class ArchiveExtracted(val path: String, val count: Long)

/** Результат длинной операции с архивом. */
sealed interface Outcome<out T> {
    data class Ok<T>(val value: T) : Outcome<T>
    data object Aborted : Outcome<Nothing>
    data class Err(val code: String, val message: String) : Outcome<Nothing>
}

// ---------------------------------------------------------------- корзина

data class TrashItem(
    val id: String,
    val name: String,
    val orig: String,
    val time: Long,
    val size: Long,
    val dir: Boolean,
)

data class TrashList(val items: List<TrashItem>, val count: Int, val bytes: Long)

data class TrashMoveResult(val done: Int, val bytes: Long, val failed: List<String>)

data class TrashRestoreResult(val done: Int, val failed: List<String>, val restored: List<String>, val cancelled: Boolean)

data class TrashDeleteResult(val done: Int, val bytes: Long, val cancelled: Boolean)

data class TrashProgress(val fraction: Double, val current: String?)

// ---------------------------------------------------------------- очистка

data class CleanerOptions(
    val primary: Boolean = true,
    val sd: Boolean = true,
    val duplicates: Boolean = true,
    val similar: Boolean = false,
    val junk: Boolean = true,
    val apks: Boolean = true,
    val largest: Boolean = true,
    val emptyFolders: Boolean = true,
    val zeroFiles: Boolean = true,
    val appFolders: Boolean = false,
)

data class DupFile(val path: String, val mtime: Long)
data class DupGroup(val size: Long, val count: Int, val files: List<DupFile>)
data class SimFile(val path: String, val size: Long, val mtime: Long, val w: Int, val h: Int)
data class SimGroup(val files: List<SimFile>)
data class JunkItem(val path: String, val size: Long, val dir: Boolean, val kind: String)
data class ApkItem(
    val path: String,
    val size: Long,
    val mtime: Long,
    val pkg: String? = null,
    val version: String? = null,
    val label: String? = null,
)
data class BigFile(val path: String, val size: Long, val mtime: Long)
data class UsageStat(val count: Long, val bytes: Long)
data class ScanStats(val files: Long, val folders: Long, val bytes: Long, val ms: Long)

data class ScanResult(
    val duplicates: List<DupGroup>,
    val similar: List<SimGroup>,
    val junk: List<JunkItem>,
    val apks: List<ApkItem>,
    val largest: List<BigFile>,
    val emptyFolders: List<String>,
    val zeroFiles: List<String>,
    /** Ключи по порядку: images, video, audio, docs, archives, apk, other. */
    val usage: Map<String, UsageStat>,
    val stats: ScanStats,
)

data class ScanProgress(
    val phase: String,
    val files: Long,
    val folders: Long,
    val bytes: Long,
    val done: Long,
    val total: Long,
    val current: String?,
)

/** Универсальная строка в списке результатов очистки. */
data class CleanRow(
    val path: String,
    val size: Long,
    val mtime: Long = 0,
    val dir: Boolean = false,
    val kind: String? = null,
    val group: Int = -1,
    val w: Int = 0,
    val h: Int = 0,
    val label: String? = null,
    val pkg: String? = null,
    val version: String? = null,
)

/** Состояние поиска по файлам. */
data class SearchState(
    val q: String = "",
    val results: List<FileEntry> = emptyList(),
    val loading: Boolean = false,
    val truncated: Boolean = false,
)
