package dev.filedeck.app

import android.content.Context
import android.os.StatFs
import android.util.Log
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
                Log.w("FileDeck", "Fs: ошибка проигнорирована", ignored)
            }
            val r = StorageRoot(
                name = if (primary) Str.s(R.string.storage_internal) else Str.s(R.string.start_sd),
                path = root.absolutePath,
                primary = primary,
                total = total,
                free = free,
            )
            primary = false
            r
        }
    }

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
        )
    }

    @Throws(IOException::class)
    fun list(path: String): List<FileEntry> {
        val dir = File(path)
        if (!dir.exists()) throw IOException(Str.s(R.string.error_folder_missing))
        if (!dir.isDirectory) throw IOException(Str.s(R.string.error_not_folder))
        val files = dir.listFiles() ?: throw IOException(Str.s(R.string.error_folder_no_access))
        return files.map { describe(it, true) }
    }

    @Throws(IOException::class)
    fun search(path: String, query: String, limit: Int = 400, cancelled: () -> Boolean = { false }): SearchResult {
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
                    if (cancelled()) break@outer
                    if (f.name == TrashStore.TRASH_DIR) continue
                    if (++visited > SEARCH_VISIT_LIMIT) {
                        truncated = true
                        break@outer
                    }
                    if (f.name.lowercase(Locale.ROOT).contains(q)) {
                        out.add(describe(f, f.isDirectory))
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
        val acc = LongArray(3)
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

    @Throws(IOException::class)
    fun mkdir(path: String): String {
        val f = File(path)
        if (f.exists()) throw IOException(Str.s(R.string.error_folder_exists))
        if (!f.mkdirs()) throw IOException(Str.s(R.string.error_create_folder))
        return f.absolutePath
    }

    @Throws(IOException::class)
    fun createFile(path: String): String {
        val f = File(path)
        if (f.exists()) throw IOException(Str.s(R.string.error_file_exists))
        if (!f.createNewFile()) throw IOException(Str.s(R.string.error_create_file))
        return f.absolutePath
    }

    @Throws(IOException::class)
    fun rename(path: String, name: String): String {
        val clean = name.trim()
        if (clean.isEmpty() || clean.contains("/") || clean == "." || clean == "..") {
            throw IOException(Str.s(R.string.error_bad_name))
        }
        val src = File(path)
        val dst = File(src.parentFile, clean)
        if (dst.exists()) throw IOException(Str.s(R.string.error_name_taken))
        if (!src.renameTo(dst)) throw IOException(Str.s(R.string.error_rename))
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
        if (!destDir.isDirectory) throw IOException(Str.s(R.string.error_dest_unavailable))
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
                if (move && src.parentFile?.canonicalPath == destCanon) {
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
                Log.w("FileDeck", "Fs: ошибка проигнорирована", ignored)
                failed.add(p)
            }
        }
        return OpResult(done, failed)
    }

    @Throws(IOException::class)
    fun readText(path: String): String {
        val f = File(path)
        if (!f.isFile) throw IOException(Str.s(R.string.error_file_not_found))
        if (f.length() > MAX_EDIT_BYTES) throw IOException(Str.s(R.string.error_file_too_large))
        val bos = ByteArrayOutputStream(maxOf(f.length().toInt(), 16))
        FileInputStream(f).use { it.copyTo(bos, 64 * 1024) }
        return String(bos.toByteArray(), Charsets.UTF_8)
    }

    @Throws(IOException::class)
    fun writeText(path: String, text: String) {
        FileOutputStream(File(path), false).use { it.write(text.toByteArray(Charsets.UTF_8)) }
    }

    @Throws(IOException::class)
    fun open(ctx: Context, path: String) = FileOpener.open(ctx, path)

    @Throws(IOException::class)
    fun share(ctx: Context, paths: List<String>) = FileOpener.share(ctx, paths)
}
