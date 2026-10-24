package dev.filedeck.app

import android.content.Context
import android.os.Environment
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException

/** Общие файловые утилиты ядра FileDeck. */
internal object FsUtil {
    private const val BUFFER = 64 * 1024

    fun isSymlink(f: File): Boolean = try {
        val parent = f.parentFile
        val probe = if (parent == null) f else File(parent.canonicalFile, f.name)
        probe.canonicalFile != probe.absoluteFile
    } catch (e: IOException) {
        false
    }

    /** progress получает размер и имя каждого удалённого файла — для полосы прогресса. */
    fun deleteRecursive(f: File, progress: ((Long, String) -> Unit)? = null): Boolean {
        var ok = true
        if (f.isDirectory && !isSymlink(f)) {
            f.listFiles()?.forEach { ok = deleteRecursive(it, progress) && ok }
            return f.delete() && ok
        }
        val len = f.length()
        val deleted = f.delete()
        if (deleted && progress != null) progress(len, f.name)
        return deleted && ok
    }

    @Throws(IOException::class)
    fun copyRecursive(src: File, dst: File) {
        if (src.isDirectory) {
            if (!dst.mkdirs() && !dst.isDirectory) throw IOException("Cannot create " + dst.name)
            if (isSymlink(src)) return
            src.listFiles()?.forEach { copyRecursive(it, File(dst, it.name)) }
        } else {
            FileInputStream(src).use { input ->
                FileOutputStream(dst).use { out -> input.copyTo(out, BUFFER) }
            }
            dst.setLastModified(src.lastModified())
        }
    }

    /** Свободное имя в папке: «имя (1).ext», «имя (2).ext»… */
    fun uniqueTarget(dir: File, name: String): File {
        var t = File(dir, name)
        if (!t.exists()) return t
        var base = name
        var ext = ""
        val dot = name.lastIndexOf('.')
        if (dot > 0) {
            base = name.substring(0, dot)
            ext = name.substring(dot)
        }
        for (i in 1 until 10000) {
            t = File(dir, "$base ($i)$ext")
            if (!t.exists()) return t
        }
        return t
    }

    /** Размер файла или суммарный размер папки. */
    fun sizeOf(f: File): Long {
        if (!f.isDirectory) return f.length()
        if (isSymlink(f)) return 0
        var sum = 0L
        f.listFiles()?.forEach { sum += sizeOf(it) }
        return sum
    }

    /** Переносит файл или папку: сначала rename, при неудаче — копирование и удаление. */
    fun moveTo(src: File, dst: File): Boolean {
        if (src.renameTo(dst)) return true
        return try {
            copyRecursive(src, dst)
            if (deleteRecursive(src)) {
                true
            } else {
                deleteRecursive(dst)
                false
            }
        } catch (e: IOException) {
            deleteRecursive(dst)
            false
        }
    }

    /** Корни накопителей: внутренняя память и SD-карты. */
    fun storageRoots(ctx: Context): List<File> {
        val out = ArrayList<File>()
        val seen = LinkedHashSet<String>()
        @Suppress("DEPRECATION")
        val primary = Environment.getExternalStorageDirectory()
        out.add(primary)
        seen.add(primary.absolutePath)
        for (f in ctx.getExternalFilesDirs(null)) {
            if (f == null) continue
            val p = f.absolutePath
            val i = p.indexOf("/Android/data")
            if (i <= 0) continue
            val rootPath = p.substring(0, i)
            if (seen.add(rootPath)) {
                val root = File(rootPath)
                if (root.exists() && root.canRead()) out.add(root)
            }
        }
        return out
    }
}
