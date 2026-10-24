package dev.filedeck.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import java.io.File

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
