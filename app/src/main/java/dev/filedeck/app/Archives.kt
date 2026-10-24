package dev.filedeck.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.exception.ZipException
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.lang.reflect.InvocationTargetException
import java.util.Locale

/**
 * Архиватор FileDeck.
 *
 * Создание: ZIP (в т. ч. с паролем AES-256), 7z, TAR, TAR.GZ, TAR.BZ2, TAR.XZ.
 * Распаковка и просмотр: ZIP (с паролем), 7z (с паролем), TAR/TGZ/TBZ2/TXZ, одиночные GZ/BZ2/XZ, RAR (v4).
 *
 * Все операции потоковые, с прогрессом и отменой. Распаковка защищена от Zip Slip, символические ссылки
 * внутри архивов пропускаются, при ошибке недоделанный результат удаляется.
 * Форматы читают и пишут Java-классы ArchiveSource / ArchiveSink — они проверены тестами и не менялись.
 */
class ArchiveService {
    @Volatile
    private var cancelFlag = false

    @Volatile
    private var busy = false

    fun cancel() {
        cancelFlag = true
    }

    val isBusy: Boolean get() = busy

    private class Host(
        private val cancelled: () -> Boolean,
        private val sink: (ArchiveProgress) -> Unit,
    ) : ArchiveHost {
        private var last = 0L

        override fun checkCancel() {
            if (cancelled()) throw Cancelled()
        }

        override fun emit(op: String, fraction: Double, current: String?, force: Boolean) {
            val now = System.nanoTime() / 1_000_000
            if (!force && now - last < 120) return
            last = now
            sink(ArchiveProgress(op, fraction.coerceIn(0.0, 1.0), current))
        }
    }

    private suspend fun <T> guarded(onProgress: (ArchiveProgress) -> Unit, job: (ArchiveHost) -> T): Outcome<T> {
        if (busy) return Outcome.Err("BUSY", "Другая операция с архивом ещё выполняется")
        busy = true
        cancelFlag = false
        return try {
            withContext<Outcome<T>>(Dispatchers.IO) {
                val host = Host({ cancelFlag }, onProgress)
                try {
                    Outcome.Ok(job(host))
                } catch (ignored: Cancelled) {
                    Outcome.Aborted
                } catch (e: ArchiveError) {
                    Outcome.Err(e.code, e.message ?: "")
                } catch (t: Throwable) {
                    val msg = t.message
                    Outcome.Err("FAILED", if (msg.isNullOrEmpty()) t.javaClass.simpleName else msg)
                }
            }
        } finally {
            busy = false
        }
    }

    suspend fun create(
        paths: List<String>,
        destDir: String,
        name: String,
        format: String,
        password: String,
        level: Int,
        onProgress: (ArchiveProgress) -> Unit,
    ): Outcome<ArchiveCreated> = guarded(onProgress) { host ->
        createArchive(host, paths, destDir, name, ArchiveFormat.byName(format), password, level)
    }

    suspend fun list(path: String, password: String): Outcome<ArchiveListing> =
        guarded({}) { host -> listArchive(host, path, password) }

    suspend fun extract(
        path: String,
        destDir: String,
        password: String,
        mode: String,
        only: Set<String>,
        onProgress: (ArchiveProgress) -> Unit,
    ): Outcome<ArchiveExtracted> = guarded(onProgress) { host ->
        extractArchive(host, path, destDir, password, mode, only)
    }

    // ------------------------------------------------------------------ создание

    private fun createArchive(
        host: ArchiveHost,
        paths: List<String>,
        destDir: String,
        name: String,
        fmt: ArchiveFormat,
        password: String,
        level: Int,
    ): ArchiveCreated {
        if (paths.isEmpty()) throw ArchiveError("EMPTY", "Нечего архивировать")
        if (fmt == ArchiveFormat.UNKNOWN) throw ArchiveError("FORMAT", "Неизвестный формат архива")
        val dest = File(destDir)
        if (!dest.isDirectory) throw ArchiveError("DEST", "Папка назначения недоступна")
        val usePassword = password.isNotEmpty()
        if (usePassword && fmt != ArchiveFormat.ZIP) {
            throw ArchiveError("PASSWORD_UNSUPPORTED", "Пароль поддерживается только для ZIP")
        }

        val base = ArchiveFormat.stripExtension(name.trim().ifEmpty { "archive" }).replace("/", "_")
        val target = FsUtil.uniqueTarget(dest, base + fmt.extension())
        val targetCanon = target.canonicalPath

        val items = ArrayList<ArchiveItem>()
        var total = 0L
        for (p in paths) {
            val f = File(p)
            if (!f.exists()) continue
            total += collect(f, f.name, items, targetCanon)
        }
        if (items.isEmpty()) throw ArchiveError("EMPTY", "Нечего архивировать")

        var opened: ArchiveSink? = null
        try {
            val sink = ArchiveSink.open(fmt, target, if (usePassword) password else null, level)
            opened = sink
            var done = 0L
            val buf = ByteArray(ArchiveIO.BUF)
            for (item in items) {
                host.checkCancel()
                if (item.dir) {
                    sink.dir(item.name, item.file.lastModified())
                    continue
                }
                sink.begin(item.name, item.file)
                FileInputStream(item.file).use { input ->
                    while (true) {
                        val n = input.read(buf)
                        if (n <= 0) break
                        sink.write(buf, 0, n)
                        done += n
                        host.checkCancel()
                        host.emit("create", if (total > 0) done.toDouble() / total else 0.0, item.name, false)
                    }
                }
                sink.end()
            }
            sink.close()
            opened = null
        } catch (t: Throwable) {
            try {
                opened?.close()
            } catch (ignored: Throwable) {
                // уже в ошибке
            }
            target.delete()
            throw t
        }
        host.emit("create", 1.0, null, true)
        return ArchiveCreated(target.absolutePath, target.length(), items.size)
    }

    private fun collect(f: File, name: String, out: MutableList<ArchiveItem>, skipCanon: String): Long {
        if (f.canonicalPath == skipCanon) return 0
        if (f.isDirectory) {
            if (FsUtil.isSymlink(f)) return 0
            out.add(ArchiveItem(f, name, true))
            var sum = 0L
            val ch = f.listFiles()
            if (ch != null) {
                ch.sort()
                for (c in ch) sum += collect(c, name + "/" + c.name, out, skipCanon)
            }
            return sum
        }
        if (!f.isFile) return 0
        out.add(ArchiveItem(f, name, false))
        return f.length()
    }

    // ------------------------------------------------------------------ просмотр

    private fun listArchive(host: ArchiveHost, path: String, password: String): ArchiveListing {
        val f = File(path)
        if (!f.isFile) throw ArchiveError("PATH", "Архив не найден")
        val fmt = ArchiveFormat.detect(f)
        if (fmt == ArchiveFormat.UNKNOWN) throw ArchiveError("FORMAT", "Формат архива не распознан")

        val arr = ArrayList<ArchiveEntryInfo>()
        var totalSize = 0L
        var count = 0L
        var encrypted = false
        var listing = true

        if (fmt == ArchiveFormat.ZIP) {
            try {
                ZipFile(f).use { zf ->
                    encrypted = zf.isEncrypted
                    for (h in zf.fileHeaders) {
                        host.checkCancel()
                        count++
                        if (!h.isDirectory) totalSize += maxOf(0L, h.uncompressedSize)
                        if (arr.size < MAX_LIST) {
                            arr.add(entryInfo(h.fileName, h.isDirectory, h.uncompressedSize, h.lastModifiedTimeEpoch))
                        }
                    }
                }
            } catch (e: ZipException) {
                throw ArchiveError("BROKEN", "Архив повреждён или это не ZIP")
            }
        } else if (fmt == ArchiveFormat.RAR) {
            listing = false
        } else {
            ArchiveSource.open(f, fmt, password).use { src ->
                var e = src.next()
                while (e != null) {
                    host.checkCancel()
                    count++
                    if (!e.dir && e.size > 0) totalSize += e.size
                    if (arr.size < MAX_LIST) arr.add(entryInfo(e.name, e.dir, e.size, e.mtime))
                    host.emit("list", src.fraction(), e.name, false)
                    e = src.next()
                }
            }
        }
        return ArchiveListing(
            format = fmt.name,
            entries = arr,
            count = count,
            bytes = totalSize,
            encrypted = encrypted,
            listing = listing,
            truncated = count > arr.size,
        )
    }

    private fun entryInfo(name: String, dir: Boolean, size: Long, mtime: Long): ArchiveEntryInfo {
        var clean = name.replace('\\', '/')
        while (clean.endsWith("/") && clean.length > 1) clean = clean.substring(0, clean.length - 1)
        return ArchiveEntryInfo(clean, dir, maxOf(0L, size), mtime)
    }

    // ------------------------------------------------------------------ распаковка

    private fun extractArchive(
        host: ArchiveHost,
        path: String,
        destDir: String,
        password: String,
        mode: String,
        only: Set<String>,
    ): ArchiveExtracted {
        val f = File(path)
        val dest = File(destDir)
        if (!f.isFile) throw ArchiveError("PATH", "Архив не найден")
        if (!dest.isDirectory) throw ArchiveError("DEST", "Папка назначения недоступна")
        val fmt = ArchiveFormat.detect(f)
        if (fmt == ArchiveFormat.UNKNOWN) throw ArchiveError("FORMAT", "Формат архива не распознан")

        val base = ArchiveFormat.stripExtension(f.name)
        val here = mode == "here" && fmt != ArchiveFormat.RAR
        val root = if (here) dest else FsUtil.uniqueTarget(dest, base)
        var createdRoot = false
        if (!here) {
            if (!root.mkdirs()) throw ArchiveError("DEST", "Не удалось создать папку для распаковки")
            createdRoot = true
        }
        val topMap = HashMap<String, String>()
        val created = ArrayList<File>() // верхнеуровневые результаты для отката в режиме «сюда»
        var count = 0L
        try {
            if (fmt == ArchiveFormat.RAR) {
                host.emit("extract", 0.0, f.name, true)
                extractRar(f, root, password)
                count = countFiles(root)
            } else {
                val rootCanon = root.canonicalPath
                ArchiveSource.open(f, fmt, password).use { src ->
                    val buf = ByteArray(ArchiveIO.BUF)
                    var e = src.next()
                    while (e != null) {
                        host.checkCancel()
                        val name = cleanName(e.name)
                        if (!e.link && name != null && wanted(name, only)) {
                            val out = mapTarget(root, name, here, topMap, created)
                            val canon = out.canonicalPath
                            val inside = canon == rootCanon || canon.startsWith(rootCanon + File.separator)
                            if (inside) { // иначе — Zip Slip, пропускаем
                                if (e.dir) {
                                    out.mkdirs()
                                } else {
                                    val parent = out.parentFile
                                    if (parent != null && !parent.isDirectory && !parent.mkdirs()) {
                                        throw ArchiveError("DEST", "Не удалось создать папку " + parent.name)
                                    }
                                    BufferedOutputStream(FileOutputStream(out), ArchiveIO.BUF).use { os ->
                                        val input = src.stream()
                                        while (true) {
                                            val n = input.read(buf)
                                            if (n <= 0) break
                                            os.write(buf, 0, n)
                                            host.checkCancel()
                                            host.emit("extract", src.fraction(), name, false)
                                        }
                                    }
                                    if (e.mtime > 0) out.setLastModified(e.mtime)
                                    count++
                                }
                            }
                        }
                        e = src.next()
                    }
                }
            }
        } catch (t: Throwable) {
            if (createdRoot) FsUtil.deleteRecursive(root) else for (c in created) FsUtil.deleteRecursive(c)
            throw t
        }
        host.emit("extract", 1.0, null, true)
        return ArchiveExtracted(root.absolutePath, count)
    }

    private fun countFiles(dir: File): Long {
        var n = 0L
        val ch = dir.listFiles() ?: return 0
        for (c in ch) n += if (c.isDirectory) countFiles(c) else 1
        return n
    }

    private fun wanted(name: String, only: Set<String>): Boolean {
        if (only.isEmpty()) return true
        for (o in only) {
            if (name == o || name.startsWith("$o/")) return true
        }
        return false
    }

    private fun mapTarget(
        root: File,
        name: String,
        here: Boolean,
        topMap: MutableMap<String, String>,
        created: MutableList<File>,
    ): File {
        if (!here) return File(root, name)
        val slash = name.indexOf('/')
        val top = if (slash < 0) name else name.substring(0, slash)
        val rest = if (slash < 0) "" else name.substring(slash)
        var mapped = topMap[top]
        if (mapped == null) {
            val candidate = File(root, top)
            mapped = if (candidate.exists()) FsUtil.uniqueTarget(root, top).name else top
            topMap[top] = mapped
            created.add(File(root, mapped))
        }
        return File(root, mapped + rest)
    }

    @Throws(IOException::class)
    private fun extractRar(rar: File, destDir: File, password: String) {
        try {
            val c = Class.forName("com.github.junrar.Junrar")
            if (password.isEmpty()) {
                c.getMethod("extract", File::class.java, File::class.java).invoke(null, rar, destDir)
            } else {
                c.getMethod("extract", File::class.java, File::class.java, String::class.java)
                    .invoke(null, rar, destDir, password)
            }
        } catch (ite: InvocationTargetException) {
            val cause = ite.cause ?: ite
            val cn = cause.javaClass.simpleName.lowercase(Locale.ROOT)
            val msg = cause.message.toString().lowercase(Locale.ROOT)
            if (cn.contains("v5") || msg.contains("rar5") || msg.contains("v5")) {
                throw ArchiveError("RAR5", "Формат RAR5 пока не поддерживается (RAR 4 и старше — работает)")
            }
            if (cn.contains("password") || msg.contains("password") || cn.contains("crc") || msg.contains("crc")) {
                throw if (password.isEmpty()) {
                    ArchiveError("PASSWORD_REQUIRED", "Архив защищён паролем")
                } else {
                    ArchiveError("WRONG_PASSWORD", "Неверный пароль")
                }
            }
            throw IOException("Не удалось распаковать RAR: " + cause.message)
        } catch (e: ReflectiveOperationException) {
            throw ArchiveError("FORMAT", "Поддержка RAR недоступна в этой сборке")
        }
    }

    companion object {
        private const val MAX_LIST = 20000

        /** Нормализует имя записи: без ведущих слэшей, без «..» (защита от Zip Slip). */
        fun cleanName(raw: String?): String? {
            if (raw == null) return null
            var n = raw.replace('\\', '/')
            while (n.startsWith("/")) n = n.substring(1)
            while (n.endsWith("/") && n.length > 1) n = n.substring(0, n.length - 1)
            if (n.isEmpty() || n == "/") return null
            for (part in n.split("/")) {
                if (part == "..") return null
            }
            return n
        }
    }
}

/** Понятные тексты ошибок архиватора. */
fun explainArchiveError(code: String, message: String): String = when (code) {
    "RAR5" -> "Формат RAR5 пока не поддерживается. RAR 4 и старше открываются."
    "PASSWORD_UNSUPPORTED" -> "Пароль можно задать только для ZIP"
    else -> message.ifEmpty { "Операция не удалась" }
}
