package dev.filedeck.app

import java.io.File
import java.io.RandomAccessFile
import java.security.MessageDigest

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
