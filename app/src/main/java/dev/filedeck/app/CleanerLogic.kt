package dev.filedeck.app

import androidx.annotation.StringRes

enum class CleanCat(
    @StringRes private val titleRes: Int,
    @StringRes private val hintRes: Int,
    val group: Boolean,
    val safe: Boolean,
) {
    Duplicates(R.string.cat_duplicates, R.string.cat_duplicates_hint, true, true),
    Similar(R.string.cat_similar, R.string.cat_similar_hint, true, false),
    Junk(R.string.cat_junk, R.string.cat_junk_hint, false, true),
    Apks(R.string.cat_apks, R.string.cat_apks_hint, false, false),
    Largest(R.string.cat_largest, R.string.cat_largest_hint, false, false),
    EmptyFolders(R.string.cat_empty_folders, R.string.cat_empty_folders_hint, false, true),
    ZeroFiles(R.string.cat_zero_files, R.string.cat_zero_files_hint, false, true),
    ;

    val title: String get() = Str.s(titleRes)
    val hint: String get() = Str.s(hintRes)
}

val JUNK_LABEL: Map<String, String>
    get() = mapOf(
        "cache" to Str.s(R.string.junk_cache), "temp" to Str.s(R.string.junk_temp),
        "thumbnails" to Str.s(R.string.junk_thumbnails), "logs" to Str.s(R.string.junk_logs),
        "trashed" to Str.s(R.string.junk_trashed), "system" to Str.s(R.string.junk_system),
        "trash" to Str.s(R.string.junk_trash), "messenger" to Str.s(R.string.junk_messenger),
        "partial" to Str.s(R.string.junk_partial),
    )

val USAGE_LABEL: List<Pair<String, String>>
    get() = listOf(
        "images" to Str.s(R.string.usage_images), "video" to Str.s(R.string.kind_video),
        "audio" to Str.s(R.string.usage_audio), "docs" to Str.s(R.string.usage_docs),
        "archives" to Str.s(R.string.usage_archives), "apk" to Str.s(R.string.usage_apk),
        "other" to Str.s(R.string.usage_other),
    )

typealias CleanSelection = Map<CleanCat, Set<String>>

object CleanerLogic {
    private val RE_DOWNLOAD = Regex("/Download/", RegexOption.IGNORE_CASE)
    private val RE_NUMBERED = Regex(""" \(\d+\)(\.[^./]+)?$""")
    private val RE_COPY = Regex("(копия|copy|~)", RegexOption.IGNORE_CASE)
    private val RE_MESSENGER = Regex("/(Telegram|WhatsApp)|/Android/media", RegexOption.IGNORE_CASE)

    fun rowsOf(r: ScanResult?, cat: CleanCat): List<CleanRow> {
        if (r == null) return emptyList()
        return when (cat) {
            CleanCat.Duplicates -> r.duplicates.flatMapIndexed { gi, g ->
                g.files.map { CleanRow(it.path, g.size, it.mtime, group = gi) }
            }
            CleanCat.Similar -> r.similar.flatMapIndexed { gi, g ->
                g.files.map { CleanRow(it.path, it.size, it.mtime, w = it.w, h = it.h, group = gi) }
            }
            CleanCat.Junk -> r.junk.map { CleanRow(it.path, it.size, dir = it.dir, kind = it.kind) }
            CleanCat.Apks -> r.apks.map {
                CleanRow(it.path, it.size, it.mtime, label = it.label, pkg = it.pkg, version = it.version)
            }
            CleanCat.Largest -> r.largest.map { CleanRow(it.path, it.size, it.mtime) }
            CleanCat.EmptyFolders -> r.emptyFolders.map { CleanRow(it, 0, dir = true) }
            CleanCat.ZeroFiles -> r.zeroFiles.map { CleanRow(it, 0) }
        }
    }

    fun countOf(r: ScanResult?, cat: CleanCat): Int {
        if (r == null) return 0
        return when (cat) {
            CleanCat.Duplicates -> r.duplicates.size
            CleanCat.Similar -> r.similar.size
            else -> rowsOf(r, cat).size
        }
    }

    fun groupRows(rows: List<CleanRow>): Map<Int, List<CleanRow>> = rows.groupBy { it.group }

    /** «Лучший» экземпляр в группе: не из Загрузок, без « (1)» и «копия», в основной папке, старше. */
    fun keeper(list: List<CleanRow>): CleanRow {
        fun score(f: CleanRow): Double {
            val p = f.path
            var s = p.length / 1000.0
            if (RE_DOWNLOAD.containsMatchIn(p)) s += 3
            if (RE_NUMBERED.containsMatchIn(p)) s += 4
            if (RE_COPY.containsMatchIn(basename(p))) s += 3
            if (RE_MESSENGER.containsMatchIn(p)) s += 2
            if (f.w > 0 && f.h > 0) s -= f.w.toDouble() * f.h / 1e8
            s -= f.size / 1e12
            return s
        }
        return list.sortedWith(compareBy<CleanRow>({ score(it) }, { it.mtime })).first()
    }

    /** Сколько байт освободится, если оставить в каждой группе только «лучший» файл. */
    fun wasteOf(r: ScanResult?, cat: CleanCat): Long {
        val rows = rowsOf(r, cat)
        if (cat.group) {
            var sum = 0L
            for (list in groupRows(rows).values) {
                val keep = keeper(list)
                sum += list.filter { it !== keep }.sumOf { it.size }
            }
            return sum
        }
        return rows.sumOf { it.size }
    }

    /** Что отмечено сразу после сканирования: лишние копии, мусор, пустые папки и файлы. */
    fun defaultSelection(r: ScanResult): CleanSelection {
        val sel = LinkedHashMap<CleanCat, Set<String>>()
        for (c in CleanCat.entries) {
            val rows = rowsOf(r, c)
            val set = LinkedHashSet<String>()
            if (c.group) {
                for (list in groupRows(rows).values) {
                    val keep = keeper(list)
                    for (row in list) if (row !== keep) set.add(row.path)
                }
            } else if (c == CleanCat.Junk || c == CleanCat.EmptyFolders || c == CleanCat.ZeroFiles) {
                for (row in rows) set.add(row.path)
            }
            sel[c] = set
        }
        return sel
    }

    /** Убирает удалённое из всех списков (файл может числиться сразу в нескольких категориях). */
    fun purge(r: ScanResult, sel: CleanSelection, paths: Collection<String>): Pair<ScanResult, CleanSelection> {
        val gone = HashSet(paths)
        fun hit(p: String): Boolean = p in gone || gone.any { p.startsWith("$it/") }
        val next = r.copy(
            duplicates = r.duplicates
                .map { g -> g.copy(files = g.files.filter { !hit(it.path) }) }
                .filter { it.files.size > 1 },
            similar = r.similar
                .map { g -> g.copy(files = g.files.filter { !hit(it.path) }) }
                .filter { it.files.size > 1 },
            junk = r.junk.filter { !hit(it.path) },
            apks = r.apks.filter { !hit(it.path) },
            largest = r.largest.filter { !hit(it.path) },
            emptyFolders = r.emptyFolders.filter { !hit(it) },
            zeroFiles = r.zeroFiles.filter { !hit(it) },
        )
        val newSel = LinkedHashMap<CleanCat, Set<String>>()
        for (c in CleanCat.entries) {
            val valid = rowsOf(next, c).map { it.path }.toHashSet()
            newSel[c] = (sel[c] ?: emptySet()).filter { it in valid }.toSet()
        }
        return next to newSel
    }

    fun selBytes(r: ScanResult?, sel: CleanSelection, cat: CleanCat): Long =
        rowsOf(r, cat).filter { sel[cat]?.contains(it.path) == true }.sumOf { it.size }

    /** Категории, которые можно чистить без раздумий (похожие фото — только вручную). */
    val safeCats: List<CleanCat> = CleanCat.entries.filter { it.safe && it != CleanCat.Similar }

    fun safePaths(r: ScanResult?, sel: CleanSelection): List<String> =
        topLevel(safeCats.flatMap { c -> rowsOf(r, c).filter { sel[c]?.contains(it.path) == true }.map { it.path } })

    /** Оставляет только внешние пути: вложенные удалятся вместе с родителем. */
    fun topLevel(paths: Collection<String>): List<String> {
        val sorted = paths.distinct().sortedBy { it.length }
        val out = ArrayList<String>()
        for (p in sorted) if (out.none { p.startsWith("$it/") }) out.add(p)
        return out
    }

    fun safeBytes(r: ScanResult?, sel: CleanSelection): Long = safeCats.sumOf { selBytes(r, sel, it) }
}
