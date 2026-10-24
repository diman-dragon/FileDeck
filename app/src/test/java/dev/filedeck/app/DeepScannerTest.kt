package dev.filedeck.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class DeepScannerTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private fun scan(root: File, cfg: ScanConfig = ScanConfig()) =
        DeepScanner(null, {}, {}, emptyList(), cfg).run(listOf(root.path))

    @Test fun findsDuplicatesJunkEmptyFoldersAndZeroFiles() {
        val root = tmp.newFolder("s")
        File(root, "a.bin").writeBytes(ByteArray(5000) { 1 })
        File(root, "sub").mkdirs()
        File(root, "sub/b.bin").writeBytes(ByteArray(5000) { 1 })
        File(root, "x.tmp").writeText("junk")
        File(root, "empty").mkdirs()
        File(root, "zero.txt").createNewFile()

        val r = scan(root)

        assertEquals(1, r.duplicates.size)
        assertEquals(2, r.duplicates[0].count)
        assertTrue(r.junk.any { it.path.endsWith("x.tmp") })
        assertEquals(listOf(File(root, "empty").path), r.emptyFolders)
        assertEquals(1, r.zeroFiles.size)
    }

    @Test fun differentContentWithSameSizeIsNotDuplicate() {
        val root = tmp.newFolder("s2")
        File(root, "a.bin").writeBytes(ByteArray(5000) { 1 })
        File(root, "b.bin").writeBytes(ByteArray(5000) { 2 })
        assertTrue(scan(root).duplicates.isEmpty())
    }

    @Test fun nestedEmptyTreeIsReportedOnce() {
        val root = tmp.newFolder("s3")
        File(root, "a/b/c").mkdirs()
        File(root, "keep").mkdirs()
        File(root, "keep/f.txt").writeText("x")
        val r = scan(root)
        assertEquals(listOf(File(root, "a").path), r.emptyFolders)
    }

    @Test fun disabledChecksStayEmpty() {
        val root = tmp.newFolder("s4")
        File(root, "a.bin").writeBytes(ByteArray(5000) { 1 })
        File(root, "b.bin").writeBytes(ByteArray(5000) { 1 })
        val r = scan(root, ScanConfig(duplicates = false, junk = false, emptyFolders = false, zeroFiles = false))
        assertTrue(r.duplicates.isEmpty() && r.junk.isEmpty() && r.emptyFolders.isEmpty())
    }
}
