package dev.filedeck.app

import android.content.Context
import android.util.Log
import java.io.File
import java.util.Locale
import java.util.PriorityQueue
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
    private val cancelFlag = AtomicBoolean(false)
    private val scanning = AtomicBoolean(false)

    fun cancel() {
        cancelFlag.set(true)
    }

    /** Возвращает null, если сканирование отменено. */
    suspend fun scan(
        roots: List<String>,
        excludes: List<String>,
        config: ScanConfig,
        onProgress: (ScanProgress) -> Unit,
    ): ScanResult? {
        check(scanning.compareAndSet(false, true)) { "Scan already running" }
        cancelFlag.set(false)
        return try {
            withContext(Dispatchers.IO) {
                val scanner = DeepScanner(ctx, { if (cancelFlag.get()) throw Cancelled() }, onProgress, excludes, config)
                try {
                    scanner.run(roots)
                } catch (ignored: Cancelled) {
                    null
                }
            }
        } finally {
            scanning.set(false)
        }
    }
}

internal class DeepScanner(
    private val ctx: Context?,
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
                    val kind = FileRules.junkDirKind(name.lowercase(Locale.ROOT))
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
                if (isScanRoot && FileRules.PROTECTED_DIRS.contains(c.name.lowercase(Locale.ROOT))) continue
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
        val ext = FileRules.extension(lower)
        val kind = FileRules.usageIndex(ext)
        usageBytes[kind] += len
        usageCount[kind]++

        if (cfg.junk) {
            val junk = FileRules.junkFileKind(lower, ext)
            if (junk != null) {
                addJunk(f.absolutePath, len, false, junk)
                return
            }
        }
        if (cfg.apks && FileRules.APK_EXT.contains(ext)) apks.add(ScanFile(f.absolutePath, len, f.lastModified()))
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

    private fun largestList(): List<BigFile> =
        largest.sortedByDescending { it.size }.map { BigFile(it.path, it.size, it.mtime) }

    private fun apkList(): List<ApkItem> {
        val sorted = apks.sortedByDescending { it.size }
        val pm = ctx?.packageManager
        val out = ArrayList<ApkItem>()
        var parsed = 0
        for (fi in sorted) {
            if (out.size >= MAX_LIST) break
            var pkg: String? = null
            var version: String? = null
            var label: String? = null
            if (pm != null && parsed < 150 && fi.path.lowercase(Locale.ROOT).endsWith(".apk")) {
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
                    Log.w("FileDeck", "Cleaner: ошибка проигнорирована", ignored)
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
