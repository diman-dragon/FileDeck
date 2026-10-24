package dev.filedeck.app

import android.content.Context
import android.util.Log
import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** Хранилище корзины: на каждом накопителе скрытая папка .FileDeckTrash; у каждого элемента есть .json с метаданными. */
object TrashStore {
    const val TRASH_DIR = ".FileDeckTrash"
}

internal class TrashEntry(
    val id: String,
    val content: File,
    val metaFile: File,
    val name: String,
    val orig: String,
    val time: Long,
    val size: Long,
    val dir: Boolean,
) {
    fun toItem() = TrashItem(id, name, orig, time, size, dir)
}

/**
 * Корзина FileDeck: перенос в скрытую папку на том же накопителе (мгновенно), список, восстановление,
 * удаление навсегда с прогрессом и автоочистка по сроку.
 */
class TrashService(private val rootsProvider: () -> List<File>) {
    constructor(ctx: Context) : this({ FsUtil.storageRoots(ctx) })

    private val cancelFlag = AtomicBoolean(false)

    fun cancel() {
        cancelFlag.set(true)
    }

    private fun roots(): List<File> = rootsProvider()

    private fun rootFor(f: File): File? {
        var best: File? = null
        val path = f.absolutePath
        for (r in roots()) {
            val rp = r.absolutePath
            if ((path == rp || path.startsWith("$rp/")) && (best == null || rp.length > best.absolutePath.length)) {
                best = r
            }
        }
        return best
    }

    @Throws(IOException::class)
    private fun trashDir(root: File): File {
        val td = File(root, TrashStore.TRASH_DIR)
        if (!td.isDirectory && !td.mkdirs()) throw IOException("Cannot create trash directory")
        val nomedia = File(td, ".nomedia")
        if (!nomedia.exists()) nomedia.createNewFile()
        return td
    }

    private fun readTrash(): List<TrashEntry> {
        val out = ArrayList<TrashEntry>()
        for (root in roots()) {
            val td = File(root, TrashStore.TRASH_DIR)
            val metas = td.listFiles() ?: continue
            for (m in metas) {
                val n = m.name
                if (!n.endsWith(".json")) continue
                val id = n.substring(0, n.length - 5)
                val content = File(td, id)
                try {
                    val meta = JSONObject(m.readText(Charsets.UTF_8))
                    if (!content.exists()) {
                        m.delete()
                        continue
                    }
                    out.add(
                        TrashEntry(
                            id = id,
                            content = content,
                            metaFile = m,
                            name = meta.optString("name"),
                            orig = meta.optString("orig"),
                            time = meta.optLong("time"),
                            size = meta.optLong("size"),
                            dir = meta.optBoolean("dir"),
                        )
                    )
                } catch (ignored: Exception) {
                    Log.w("FileDeck", "Trash: ошибка проигнорирована", ignored)
                }
            }
        }
        return out
    }

    /** Переносит файлы в корзину своего накопителя. */
    suspend fun move(paths: List<String>): TrashMoveResult = withContext(Dispatchers.IO) {
        val failed = ArrayList<String>()
        var done = 0
        var moved = 0L
        for (p in paths) {
            val src = File(p)
            try {
                if (!src.exists()) throw IOException("source missing")
                val root = rootFor(src) ?: throw IOException("unknown storage")
                if (src.absolutePath == root.absolutePath) throw IOException("storage root")
                val td = trashDir(root)
                val tdPath = td.absolutePath
                if (src.absolutePath == tdPath || src.absolutePath.startsWith("$tdPath/")) {
                    throw IOException("already in trash")
                }
                val id = UUID.randomUUID().toString()
                val size = FsUtil.sizeOf(src)
                val meta = JSONObject()
                meta.put("name", src.name)
                meta.put("orig", src.absolutePath)
                meta.put("time", System.currentTimeMillis())
                meta.put("size", size)
                meta.put("dir", src.isDirectory)
                val metaFile = File(td, "$id.json")
                metaFile.writeText(meta.toString(), Charsets.UTF_8)
                if (!FsUtil.moveTo(src, File(td, id))) {
                    metaFile.delete()
                    throw IOException("move failed")
                }
                done++
                moved += size
            } catch (e: Exception) {
                failed.add(p)
            }
        }
        TrashMoveResult(done, moved, failed)
    }

    suspend fun list(): TrashList = withContext(Dispatchers.IO) {
        val entries = readTrash().sortedByDescending { it.time }
        TrashList(entries.map { it.toItem() }, entries.size, entries.sumOf { it.size })
    }

    suspend fun restore(ids: Set<String>, onProgress: (TrashProgress) -> Unit): TrashRestoreResult =
        withContext(Dispatchers.IO) {
            val failed = ArrayList<String>()
            val restored = ArrayList<String>()
            val targets = readTrash().filter { it.id in ids }
            cancelFlag.set(false)
            var cancelled = false
            var done = 0
            var step = 0
            for (t in targets) {
                if (cancelFlag.get()) {
                    cancelled = true
                    break
                }
                onProgress(TrashProgress(step.toDouble() / maxOf(1, targets.size), t.name))
                step++
                try {
                    val orig = File(t.orig)
                    val parent = orig.parentFile ?: throw IOException("no parent folder")
                    if (!parent.isDirectory && !parent.mkdirs()) throw IOException("no parent folder")
                    val target = FsUtil.uniqueTarget(parent, orig.name)
                    if (!FsUtil.moveTo(t.content, target)) throw IOException("restore failed")
                    t.metaFile.delete()
                    restored.add(target.absolutePath)
                    done++
                } catch (e: Exception) {
                    failed.add(t.id)
                }
            }
            onProgress(TrashProgress(1.0, null))
            TrashRestoreResult(done, failed, restored, cancelled)
        }

    /** Удаляет записи корзины с прогрессом по байтам. */
    private fun deleteEntries(
        targets: List<TrashEntry>,
        sweepOrphans: Boolean,
        onProgress: (TrashProgress) -> Unit,
    ): TrashDeleteResult {
        cancelFlag.set(false)
        val total = targets.sumOf { it.size }
        var doneBytes = 0L
        val count = targets.size
        var done = 0
        var freed = 0L
        var cancelled = false
        for (t in targets) {
            if (cancelFlag.get()) {
                cancelled = true
                break
            }
            val index = done
            val ok = FsUtil.deleteRecursive(t.content) { bytes, name ->
                doneBytes += bytes
                onProgress(
                    TrashProgress(
                        if (total > 0) doneBytes.toDouble() / total else index.toDouble() / maxOf(1, count),
                        name,
                    )
                )
            }
            if (ok) {
                t.metaFile.delete()
                done++
                freed += t.size
            }
            if (total == 0L) onProgress(TrashProgress(done.toDouble() / maxOf(1, count), t.name))
        }
        if (sweepOrphans && !cancelled) {
            for (root in roots()) {
                val rest = File(root, TrashStore.TRASH_DIR).listFiles() ?: continue
                for (f in rest) {
                    if (f.name == ".nomedia") continue
                    FsUtil.deleteRecursive(f)
                }
            }
        }
        onProgress(TrashProgress(1.0, null))
        return TrashDeleteResult(done, freed, cancelled)
    }

    suspend fun remove(ids: Set<String>, onProgress: (TrashProgress) -> Unit): TrashDeleteResult =
        withContext(Dispatchers.IO) {
            deleteEntries(readTrash().filter { it.id in ids }, false, onProgress)
        }

    suspend fun empty(onProgress: (TrashProgress) -> Unit): TrashDeleteResult =
        withContext(Dispatchers.IO) { deleteEntries(readTrash(), true, onProgress) }

    /** Автоочистка: удаляет всё, что лежит в корзине дольше days дней. */
    suspend fun purge(days: Int): TrashDeleteResult = withContext(Dispatchers.IO) {
        val limit = System.currentTimeMillis() - days * 86_400_000L
        var done = 0
        var freed = 0L
        for (t in readTrash()) {
            if (t.time > limit) continue
            if (FsUtil.deleteRecursive(t.content)) {
                t.metaFile.delete()
                done++
                freed += t.size
            }
        }
        TrashDeleteResult(done, freed, false)
    }
}
