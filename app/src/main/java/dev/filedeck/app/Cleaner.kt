package dev.filedeck.app

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile
import java.security.MessageDigest
import java.util.Locale
import java.util.PriorityQueue

// ======================================================================== сканирование

/** Что сканеру нужно от окружения: проверка отмены и отправка прогресса. */
internal interface ScanHost {
    /** Бросает Cancelled, если пользователь отменил сканирование. */
    fun checkCancel()

    fun emit(phase: String, done: Long, total: Long, current: String?, force: Boolean)
}

/** Какие проверки включены. */
data class ScanConfig(
    val duplicates: Boolean = true,
    val similar: Boolean = false,
    val junk: Boolean = true,
    val apks: Boolean = true,
    val largest: Boolean = true,
    val emptyFolders: Boolean = true,
    val zeroFiles: Boolean = true,
    val appFolders: Boolean = false,
    val minDuplicateSize: Long = 1024L,
)

internal class ScanFile(val path: String, val size: Long, val mtime: Long)

/** Служба глубокой очистки: один проход по накопителям. */
class CleanerService(private val ctx: Context) {
    @Volatile
    private var cancelFlag = false

    @Volatile
    var isScanning = false
        private set

    fun cancel() {
        cancelFlag = true
    }

    /** Возвращает null, если сканирование отменено. */
    suspend fun scan(
        roots: List<String>,
        excludes: List<String>,
        config: ScanConfig,
        onProgress: (ScanProgress) -> Unit,
    ): ScanResult? {
        if (isScanning) throw IllegalStateException("Сканирование уже идёт")
        isScanning = true
        cancelFlag = false
        return try {
            withContext(Dispatchers.IO) {
                val scanner = DeepScanner(ctx, { if (cancelFlag) throw Cancelled() }, onProgress, excludes, config)
                try {
                    scanner.run(roots)
                } catch (ignored: Cancelled) {
                    null
                }
            }
        } finally {
            isScanning = false
        }
    }
}

internal class DeepScanner(
    private val ctx: Context,
    private val cancelCheck: () -> Unit,
    private val sink: (ScanProgress) -> Unit,
    excludes: List<String>,
    private val cfg: ScanConfig,
) : ScanHost {
    private companion object {
        const val MB = 1024L * 1024L
        const val MAX_IMAGES = 30000
        const val MAX_LIST = 5000
        const val TOP_LARGEST = 100
        val KIND_KEYS = arrayOf("images", "video", "audio", "docs", "archives", "apk", "other")
    }

    private val excludePaths = HashSet<String>()
    private val minDup = maxOf(1L, cfg.minDuplicateSize)

    private var files = 0L
    private var folders = 0L
    private var bytes = 0L
    private var lastEmit = 0L

    private val sizeOnce = HashMap<Long, String>()
    private val sizeMany = HashMap<Long, ArrayList<String>>()
    private val emptyDirs = ArrayList<String>()
    private val zeroFiles = ArrayList<String>()
    private val largest = PriorityQueue<ScanFile>(128, Comparator { a, b -> a.size.compareTo(b.size) })
    private val apks = ArrayList<ScanFile>()
    private val images = ArrayList<ScanFile>()
    private val junkItems = ArrayList<JunkItem>()
    private val usageBytes = LongArray(KIND_KEYS.size)
    private val usageCount = LongArray(KIND_KEYS.size)

    init {
        for (e in excludes) excludePaths.add(stripSlash(e))
    }

    fun run(roots: List<String>): ScanResult {
        val t0 = System.nanoTime() / 1_000_000
        for (r in roots) {
            val rf = File(r)
            if (!rf.isDirectory || isExcluded(rf.absolutePath)) continue
            walk(rf, true, 0)
        }
        emit("walk", 0, 0, null, true)

        val dupMembers = HashSet<String>()
        val duplicates = if (cfg.duplicates) DuplicateFinder(this).find(sizeMany, dupMembers) else emptyList()
        val similar = if (cfg.similar) SimilarImageFinder(this).find(images, dupMembers) else emptyList()

        val usage = LinkedHashMap<String, UsageStat>()
        for (i in KIND_KEYS.indices) usage[KIND_KEYS[i]] = UsageStat(usageCount[i], usageBytes[i])

        return ScanResult(
            duplicates = duplicates,
            similar = similar,
            junk = junkItems,
            apks = apkList(),
            largest = largestList(),
            emptyFolders = emptyDirs,
            zeroFiles = zeroFiles,
            usage = usage,
            stats = ScanStats(files, folders, bytes, System.nanoTime() / 1_000_000 - t0),
        )
    }

    // ------------------------------------------------------------ обход

    private fun isExcluded(path: String): Boolean {
        for (e in excludePaths) {
            if (path == e || path.startsWith("$e/")) return true
        }
        return false
    }

    private fun stripSlash(p: String): String {
        var s = p
        while (s.length > 1 && s.endsWith("/")) s = s.substring(0, s.length - 1)
        return s
    }

    /** Возвращает true, если в папке нет ни одного файла (только пустые папки). */
    private fun walk(dir: File, isScanRoot: Boolean, depth: Int): Boolean {
        checkCancel()
        val children = dir.listFiles() ?: return false
        folders++
        val emptyChildren = ArrayList<File>()

        for (f in children) {
            val name = f.name
            if (f.isDirectory) {
                if (FsUtil.isSymlink(f)) continue
                if (name == TrashStore.TRASH_DIR || isExcluded(f.absolutePath)) continue
                if (isScanRoot && name == "Android") {
                    // чужие каталоги data/obb недоступны; в media — только если включено явно
                    val media = File(f, "media")
                    if (cfg.appFolders && media.isDirectory) walk(media, false, depth + 1)
                    continue
                }
                if (cfg.junk) {
                    val kind = FileKinds.junkDirKind(name.lowercase(Locale.ROOT))
                    if (kind != null) {
                        val size = FsUtil.sizeOf(f)
                        if (size > 0) {
                            addJunk(f.absolutePath, size, true, kind)
                            continue
                        }
                    }
                }
                if (depth >= 60) continue
                if (walk(f, false, depth + 1)) emptyChildren.add(f)
            } else {
                handleFile(f, name)
            }
        }

        // Пустое дерево: нет файлов и все вложенные папки пусты. Тогда сообщаем родителю,
        // а не перечисляем каждую вложенную папку по отдельности.
        val onlyEmptyDirs = emptyChildren.size == countDirs(children)
        val noFiles = !hasFiles(children)
        if (noFiles && onlyEmptyDirs && !isScanRoot) return true

        if (cfg.emptyFolders) {
            for (c in emptyChildren) {
                if (isScanRoot && FileKinds.PROTECTED_DIRS.contains(c.name.lowercase(Locale.ROOT))) continue
                if (emptyDirs.size < MAX_LIST) emptyDirs.add(c.absolutePath)
            }
        }
        return false
    }

    private fun countDirs(children: Array<File>): Int {
        var n = 0
        for (c in children) {
            if (c.isDirectory && c.name != TrashStore.TRASH_DIR && !FsUtil.isSymlink(c)) n++
        }
        return n
    }

    private fun hasFiles(children: Array<File>): Boolean {
        for (c in children) if (!c.isDirectory) return true
        return false
    }

    private fun handleFile(f: File, name: String) {
        files++
        val len = f.length()
        bytes += len
        if ((files and 0x3F) == 0L) emit("walk", 0, 0, f.parent, false)

        if (len == 0L) {
            if (cfg.zeroFiles && name != ".nomedia" && zeroFiles.size < MAX_LIST) zeroFiles.add(f.absolutePath)
            return
        }
        val lower = name.lowercase(Locale.ROOT)
        val ext = FileKinds.extOf(lower)
        val kind = FileKinds.kindIndex(ext)
        usageBytes[kind] += len
        usageCount[kind]++

        if (cfg.junk) {
            val junk = FileKinds.junkFileKind(lower, ext)
            if (junk != null) {
                addJunk(f.absolutePath, len, false, junk)
                return
            }
        }
        if (cfg.apks && FileKinds.APK_EXT.contains(ext)) apks.add(ScanFile(f.absolutePath, len, f.lastModified()))
        if (cfg.largest && len >= MB) pushLargest(ScanFile(f.absolutePath, len, f.lastModified()))
        if (cfg.similar && kind == 0 && len >= 20 * 1024 && images.size < MAX_IMAGES) {
            images.add(ScanFile(f.absolutePath, len, 0))
        }
        if (cfg.duplicates && len >= minDup) addBySize(len, f.absolutePath)
    }

    private fun pushLargest(fi: ScanFile) {
        if (largest.size < TOP_LARGEST) {
            largest.add(fi)
        } else {
            val smallest = largest.peek()
            if (smallest != null && fi.size > smallest.size) {
                largest.poll()
                largest.add(fi)
            }
        }
    }

    private fun addBySize(size: Long, path: String) {
        val many = sizeMany[size]
        if (many != null) {
            many.add(path)
            return
        }
        val first = sizeOnce[size]
        if (first == null) {
            sizeOnce[size] = path
        } else {
            val list = ArrayList<String>(4)
            list.add(first)
            list.add(path)
            sizeMany[size] = list
            sizeOnce.remove(size)
        }
    }

    private fun addJunk(path: String, size: Long, dir: Boolean, kind: String) {
        if (junkItems.size >= MAX_LIST) return
        junkItems.add(JunkItem(path, size, dir, kind))
    }

    // ------------------------------------------------------------ результаты

    private fun largestList(): List<BigFile> =
        largest.sortedByDescending { it.size }.map { BigFile(it.path, it.size, it.mtime) }

    private fun apkList(): List<ApkItem> {
        val sorted = apks.sortedByDescending { it.size }
        val pm = ctx.packageManager
        val out = ArrayList<ApkItem>()
        var parsed = 0
        for (fi in sorted) {
            if (out.size >= MAX_LIST) break
            var pkg: String? = null
            var version: String? = null
            var label: String? = null
            if (parsed < 150 && fi.path.lowercase(Locale.ROOT).endsWith(".apk")) {
                parsed++
                try {
                    val pi = pm.getPackageArchiveInfo(fi.path, 0)
                    if (pi != null) {
                        pkg = pi.packageName
                        version = pi.versionName ?: ""
                        val ai = pi.applicationInfo
                        if (ai != null) {
                            ai.sourceDir = fi.path
                            ai.publicSourceDir = fi.path
                            label = ai.loadLabel(pm).toString()
                        }
                    }
                } catch (ignored: Exception) {
                    // не удалось прочитать — покажем просто файл
                }
            }
            out.add(ApkItem(fi.path, fi.size, fi.mtime, pkg, version, label))
        }
        return out
    }

    override fun checkCancel() = cancelCheck()

    override fun emit(phase: String, done: Long, total: Long, current: String?, force: Boolean) {
        val now = System.nanoTime() / 1_000_000
        if (!force && now - lastEmit < 200) return
        lastEmit = now
        sink(ScanProgress(phase, files, folders, bytes, done, total, current))
    }
}

// ======================================================================== дубликаты

/** Поиск одинаковых файлов: группы по размеру → быстрый хеш краёв → полный хеш. */
internal class DuplicateFinder(private val host: ScanHost) {
    private companion object {
        const val PARTIAL = 64 * 1024
        const val BUF = 128 * 1024
        const val MAX_DUP_GROUPS = 3000
        const val MAX_DUP_PER_GROUP = 40
    }

    private var hashDone = 0L
    private var hashTotal = 0L

    fun find(sizeMany: Map<Long, ArrayList<String>>, members: MutableSet<String>): List<DupGroup> {
        hashTotal = 0
        hashDone = 0
        for ((size, list) in sizeMany) hashTotal += size * list.size

        val found = ArrayList<DupGroup>()
        for ((size, paths) in sizeMany) {
            host.checkCancel()
            val byPartial = HashMap<String, ArrayList<String>>()
            for (p in paths) {
                val h = hashFile(File(p), size, true) ?: continue
                byPartial.getOrPut(h) { ArrayList(2) }.add(p)
            }
            val completeAlready = size <= 2L * PARTIAL
            for (sub in byPartial.values) {
                if (sub.size < 2) continue
                if (completeAlready) {
                    addGroup(found, size, sub, members)
                    continue
                }
                val byFull = HashMap<String, ArrayList<String>>()
                for (p in sub) {
                    val h = hashFile(File(p), size, false) ?: continue
                    byFull.getOrPut(h) { ArrayList(2) }.add(p)
                }
                for (same in byFull.values) {
                    if (same.size >= 2) addGroup(found, size, same, members)
                }
            }
        }
        host.emit("hash", hashTotal, hashTotal, null, true)
        // самые «дорогие» группы сверху: размер × число лишних копий
        return found.sortedByDescending { it.size * (it.count - 1) }.take(MAX_DUP_GROUPS)
    }

    private fun addGroup(found: MutableList<DupGroup>, size: Long, paths: ArrayList<String>, members: MutableSet<String>) {
        paths.sort()
        val files = ArrayList<DupFile>()
        for (i in 0 until minOf(paths.size, MAX_DUP_PER_GROUP)) {
            files.add(DupFile(paths[i], File(paths[i]).lastModified()))
            if (i > 0) members.add(paths[i])
        }
        found.add(DupGroup(size, paths.size, files))
    }

    private fun hashFile(f: File, size: Long, partial: Boolean): String? {
        return try {
            RandomAccessFile(f, "r").use { raf ->
                val md = MessageDigest.getInstance("SHA-1")
                val buf = ByteArray(BUF)
                if (partial && size > 2L * PARTIAL) {
                    feed(raf, md, buf, PARTIAL.toLong())
                    raf.seek(size - PARTIAL)
                    feed(raf, md, buf, PARTIAL.toLong())
                } else {
                    feed(raf, md, buf, Long.MAX_VALUE)
                }
                hex(md.digest())
            }
        } catch (c: Cancelled) {
            throw c
        } catch (e: Exception) {
            null
        }
    }

    private fun feed(raf: RandomAccessFile, md: MessageDigest, buf: ByteArray, limit: Long) {
        var left = limit
        while (left > 0) {
            val n = raf.read(buf, 0, minOf(buf.size.toLong(), left).toInt())
            if (n <= 0) break
            md.update(buf, 0, n)
            left -= n
            hashDone += n
            host.checkCancel()
            host.emit("hash", hashDone, hashTotal, null, false)
        }
    }

    private fun hex(bytes: ByteArray): String {
        val digits = "0123456789abcdef"
        val sb = StringBuilder(bytes.size * 2)
        for (b in bytes) {
            sb.append(digits[(b.toInt() shr 4) and 0xF])
            sb.append(digits[b.toInt() and 0xF])
        }
        return sb.toString()
    }
}

// ======================================================================== похожие фото

/** Поиск похожих фото по отпечатку яркости (dHash) и объединению близких кадров. */
internal class SimilarImageFinder(private val host: ScanHost) {
    private companion object {
        const val MAX_SIMILAR_GROUPS = 500
    }

    fun find(images: List<ScanFile>, skip: Set<String>): List<SimGroup> {
        val list = images.filter { it.path !in skip }
        val n = list.size
        val hashes = LongArray(n)
        val widths = IntArray(n)
        val heights = IntArray(n)
        val ok = BooleanArray(n)
        val dims = IntArray(2)
        for (i in 0 until n) {
            host.checkCancel()
            dims[0] = 0
            dims[1] = 0
            val h = dHash(list[i].path, dims)
            if (dims[0] > 0 && h != 0L && h != -1L) {
                hashes[i] = h
                widths[i] = dims[0]
                heights[i] = dims[1]
                ok[i] = true
            }
            if ((i and 7) == 0) host.emit("images", i.toLong(), n.toLong(), null, false)
        }
        host.emit("images", n.toLong(), n.toLong(), null, true)

        val parent = IntArray(n) { it }
        val buckets = ArrayList<HashMap<Int, ArrayList<Int>>>()
        for (c in 0 until 8) buckets.add(HashMap())
        for (i in 0 until n) {
            if (!ok[i]) continue
            host.checkCancel()
            for (c in 0 until 8) {
                val key = ((hashes[i] ushr (8 * c)) and 0xFF).toInt()
                val bucket = buckets[c].getOrPut(key) { ArrayList() }
                if (bucket.size <= 300) {
                    for (j in bucket) {
                        if (java.lang.Long.bitCount(hashes[i] xor hashes[j]) <= 6) union(parent, i, j)
                    }
                }
                bucket.add(i)
            }
        }

        val clusters = HashMap<Int, ArrayList<Int>>()
        for (i in 0 until n) {
            if (!ok[i]) continue
            clusters.getOrPut(find(parent, i)) { ArrayList() }.add(i)
        }
        val groups = ArrayList<Pair<SimGroup, Long>>()
        for (members in clusters.values) {
            if (members.size < 2 || members.size > 60) continue
            var total = 0L
            val files = ArrayList<SimFile>()
            for (idx in members) {
                val fi = list[idx]
                files.add(SimFile(fi.path, fi.size, File(fi.path).lastModified(), widths[idx], heights[idx]))
                total += fi.size
            }
            groups.add(SimGroup(files) to total)
        }
        return groups.sortedByDescending { it.second }.take(MAX_SIMILAR_GROUPS).map { it.first }
    }

    private fun find(parent: IntArray, x0: Int): Int {
        var x = x0
        var r = x
        while (parent[r] != r) r = parent[r]
        while (parent[x] != r) {
            val next = parent[x]
            parent[x] = r
            x = next
        }
        return r
    }

    private fun union(parent: IntArray, a: Int, b: Int) {
        val ra = find(parent, a)
        val rb = find(parent, b)
        if (ra != rb) parent[ra] = rb
    }

    /** dHash 8×8: сравнение яркости соседних пикселей уменьшенного изображения. */
    private fun dHash(path: String, dims: IntArray): Long {
        var bmp: Bitmap? = null
        var small: Bitmap? = null
        try {
            val bounds = BitmapFactory.Options()
            bounds.inJustDecodeBounds = true
            BitmapFactory.decodeFile(path, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return 0L
            dims[0] = bounds.outWidth
            dims[1] = bounds.outHeight
            var sample = 1
            val longest = maxOf(bounds.outWidth, bounds.outHeight)
            while (longest / (sample * 2) >= 128) sample *= 2
            val opts = BitmapFactory.Options()
            opts.inSampleSize = sample
            opts.inPreferredConfig = Bitmap.Config.RGB_565
            val decoded = BitmapFactory.decodeFile(path, opts)
            bmp = decoded
            if (decoded == null) {
                dims[0] = 0
                return 0L
            }
            val scaled = Bitmap.createScaledBitmap(decoded, 9, 8, true)
            small = scaled
            var hash = 0L
            var bit = 0
            for (y in 0 until 8) {
                for (x in 0 until 8) {
                    if (luma(scaled.getPixel(x, y)) > luma(scaled.getPixel(x + 1, y))) hash = hash or (1L shl bit)
                    bit++
                }
            }
            return hash
        } catch (e: Exception) {
            dims[0] = 0
            return 0L
        } catch (e: OutOfMemoryError) {
            dims[0] = 0
            return 0L
        } finally {
            if (small != null && small !== bmp) small.recycle()
            bmp?.recycle()
        }
    }

    private fun luma(p: Int): Int = (299 * Color.red(p) + 587 * Color.green(p) + 114 * Color.blue(p)) / 1000
}

// ======================================================================== категории и выбор

enum class CleanCat(val title: String, val hint: String, val group: Boolean, val safe: Boolean) {
    Duplicates("Дубликаты", "Одинаковые файлы по содержимому, даже с разными именами", true, true),
    Similar("Похожие фото", "Серии кадров и копии разного размера · сканирование дольше", true, false),
    Junk("Мусор и кэш", "Временные файлы, логи, миниатюры, недокачанное", false, true),
    Apks("Установщики APK", "Файлы приложений, которые уже не нужны", false, false),
    Largest("Самые большие файлы", "Что занимает больше всего места", false, false),
    EmptyFolders("Пустые папки", "Папки без единого файла", false, true),
    ZeroFiles("Пустые файлы", "Файлы размером 0 байт", false, true),
}

val JUNK_LABEL: Map<String, String> = mapOf(
    "cache" to "Кэш", "temp" to "Временные", "thumbnails" to "Миниатюры", "logs" to "Логи",
    "trashed" to "Из системной корзины", "system" to "Служебные", "trash" to "Корзина приложения",
    "messenger" to "Статусы мессенджера", "partial" to "Недокачанное",
)

val USAGE_LABEL: List<Pair<String, String>> = listOf(
    "images" to "Фото", "video" to "Видео", "audio" to "Музыка", "docs" to "Документы",
    "archives" to "Архивы", "apk" to "APK", "other" to "Другое",
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
            if (f.w > 0 && f.h > 0) s -= f.w.toDouble() * f.h / 1e8 // у фото лучше версия с большим разрешением
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
        safeCats.flatMap { c -> rowsOf(r, c).filter { sel[c]?.contains(it.path) == true }.map { it.path } }

    fun safeBytes(r: ScanResult?, sel: CleanSelection): Long = safeCats.sumOf { selBytes(r, sel, it) }
}
