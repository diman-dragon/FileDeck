package dev.filedeck.app

import android.content.Context
import android.os.StatFs
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.ArrayDeque
import java.util.Locale

/**
 * Файловые операции: каталоги, поиск, создание, переименование, удаление, копирование, текст.
 * Все функции блокирующие — вызывать из фонового потока (Dispatchers.IO).
 * Работает с обычными путями (/storage/emulated/0/...), поэтому нужен «Доступ ко всем файлам».
 */
object Fs {
    private const val SEARCH_VISIT_LIMIT = 300_000

    // ---------------------------------------------------------------- накопители

    fun roots(ctx: Context): List<StorageRoot> {
        var primary = true
        return FsUtil.storageRoots(ctx).map { root ->
            var total = 0L
            var free = 0L
            try {
                val fs = StatFs(root.absolutePath)
                total = fs.totalBytes
                free = fs.availableBytes
            } catch (ignored: Exception) {
            }
            val r = StorageRoot(
                name = if (primary) "Внутренняя память" else "SD-карта",
                path = root.absolutePath,
                primary = primary,
                total = total,
                free = free,
            )
            primary = false
            r
        }
    }

    // ---------------------------------------------------------------- чтение каталогов

    fun describe(f: File, withCount: Boolean): FileEntry {
        val dir = f.isDirectory
        var count = 0
        if (dir && withCount) count = f.list()?.size ?: 0
        return FileEntry(
            name = f.name,
            path = f.absolutePath,
            dir = dir,
            size = if (dir) 0L else f.length(),
            mtime = f.lastModified(),
            count = count,
            mime = if (dir) "" else MimeTypes.of(f.name),
        )
    }

    @Throws(IOException::class)
    fun list(path: String): List<FileEntry> {
        val dir = File(path)
        if (!dir.exists()) throw IOException("Папка не существует")
        if (!dir.isDirectory) throw IOException("Это не папка")
        val files = dir.listFiles() ?: throw IOException("Нет доступа к папке")
        return files.map { describe(it, true) }
    }

    @Throws(IOException::class)
    fun search(path: String, query: String, limit: Int = 400): SearchResult {
        val q = query.lowercase(Locale.ROOT).trim()
        val out = ArrayList<FileEntry>()
        var truncated = false
        if (q.isNotEmpty()) {
            val queue = ArrayDeque<File>()
            queue.add(File(path))
            var visited = 0
            outer@ while (queue.isNotEmpty()) {
                val dir = queue.poll() ?: break
                val children = dir.listFiles() ?: continue
                for (f in children) {
                    if (++visited > SEARCH_VISIT_LIMIT) {
                        truncated = true
                        break@outer
                    }
                    if (f.name.lowercase(Locale.ROOT).contains(q)) {
                        out.add(describe(f, false))
                        if (out.size >= limit) {
                            truncated = true
                            break@outer
                        }
                    }
                    if (f.isDirectory && !FsUtil.isSymlink(f)) queue.add(f)
                }
            }
        }
        return SearchResult(out, truncated)
    }

    fun size(path: String): SizeInfo {
        val acc = LongArray(3) // байты, файлы, папки
        measure(File(path), acc, true)
        return SizeInfo(acc[0], acc[1], acc[2])
    }

    private fun measure(f: File, acc: LongArray, isRoot: Boolean) {
        if (f.isDirectory) {
            if (!isRoot) acc[2]++
            if (FsUtil.isSymlink(f)) return
            f.listFiles()?.forEach { measure(it, acc, false) }
        } else {
            acc[0] += f.length()
            acc[1]++
        }
    }

    // ---------------------------------------------------------------- создание / переименование / удаление

    @Throws(IOException::class)
    fun mkdir(path: String): String {
        val f = File(path)
        if (f.exists()) throw IOException("Папка с таким именем уже есть")
        if (!f.mkdirs()) throw IOException("Не удалось создать папку")
        return f.absolutePath
    }

    @Throws(IOException::class)
    fun createFile(path: String): String {
        val f = File(path)
        if (f.exists()) throw IOException("Файл с таким именем уже есть")
        if (!f.createNewFile()) throw IOException("Не удалось создать файл")
        return f.absolutePath
    }

    @Throws(IOException::class)
    fun rename(path: String, name: String): String {
        val clean = name.trim()
        if (clean.isEmpty() || clean.contains("/") || clean == "." || clean == "..") {
            throw IOException("Недопустимое имя")
        }
        val src = File(path)
        val dst = File(src.parentFile, clean)
        if (dst.exists()) throw IOException("Элемент с таким именем уже существует")
        if (!src.renameTo(dst)) throw IOException("Не удалось переименовать")
        return dst.absolutePath
    }

    fun remove(paths: List<String>): OpResult {
        val failed = ArrayList<String>()
        var done = 0
        for (p in paths) {
            if (FsUtil.deleteRecursive(File(p))) done++ else failed.add(p)
        }
        return OpResult(done, failed)
    }

    /** Копирует или переносит paths в папку dest; конфликты имён разрешаются суффиксом « (1)». */
    @Throws(IOException::class)
    fun transfer(paths: List<String>, dest: String, move: Boolean): OpResult {
        val destDir = File(dest)
        if (!destDir.isDirectory) throw IOException("Папка назначения недоступна")
        val destCanon = destDir.canonicalPath
        val failed = ArrayList<String>()
        var done = 0
        for (p in paths) {
            val src = File(p)
            try {
                val srcCanon = src.canonicalPath
                // Нельзя копировать/переносить папку внутрь самой себя
                if (src.isDirectory && (destCanon == srcCanon || destCanon.startsWith(srcCanon + File.separator))) {
                    failed.add(p)
                    continue
                }
                // Перенос в ту же папку — ничего не делаем
                if (move && src.parentFile != null && src.parentFile!!.canonicalPath == destCanon) {
                    done++
                    continue
                }
                val target = FsUtil.uniqueTarget(destDir, src.name)
                if (move) {
                    if (!src.renameTo(target)) {
                        FsUtil.copyRecursive(src, target)
                        if (!FsUtil.deleteRecursive(src)) {
                            failed.add(p)
                            continue
                        }
                    }
                } else {
                    FsUtil.copyRecursive(src, target)
                }
                done++
            } catch (ignored: Exception) {
                failed.add(p)
            }
        }
        return OpResult(done, failed)
    }

    // ---------------------------------------------------------------- текст

    @Throws(IOException::class)
    fun readText(path: String): String {
        val f = File(path)
        if (!f.isFile) throw IOException("Файл не найден")
        if (f.length() > MAX_EDIT_BYTES) throw IOException("Файл слишком большой для редактора (больше 2 МБ)")
        val bos = ByteArrayOutputStream(maxOf(f.length().toInt(), 16))
        FileInputStream(f).use { it.copyTo(bos, 64 * 1024) }
        return String(bos.toByteArray(), Charsets.UTF_8)
    }

    @Throws(IOException::class)
    fun writeText(path: String, text: String) {
        FileOutputStream(File(path), false).use { it.write(text.toByteArray(Charsets.UTF_8)) }
    }

    // ---------------------------------------------------------------- открыть / поделиться

    @Throws(IOException::class)
    fun open(ctx: Context, path: String) = FileOpener.open(ctx, path)

    @Throws(IOException::class)
    fun share(ctx: Context, paths: List<String>) = FileOpener.share(ctx, paths)
}
