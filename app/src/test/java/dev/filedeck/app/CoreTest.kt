package dev.filedeck.app

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class CoreTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private fun sample(): File {
        val dir = tmp.newFolder("src")
        File(dir, "a.txt").writeText("hello мир")
        File(dir, "sub").mkdirs()
        File(dir, "sub/b.bin").writeBytes(ByteArray(300_000) { (it % 251).toByte() })
        return dir
    }

    private fun roundTrip(format: String, password: String = "", ext: String) {
        val src = sample()
        val out = tmp.newFolder("out_" + format.replace('.', '_') + password.length)
        val svc = ArchiveService()
        val created = runBlocking { svc.create(listOf(src.path), out.path, "pack", format, password, 5) {} }
        assertTrue(created is Outcome.Ok)
        val archive = File((created as Outcome.Ok).value.path)
        assertTrue(archive.name.endsWith(ext))

        val listing = runBlocking { svc.list(archive.path, password) } as Outcome.Ok
        assertTrue(listing.value.entries.any { it.name.endsWith("a.txt") })

        val ex = runBlocking { svc.extract(archive.path, out.path, password, "folder", emptySet()) {} } as Outcome.Ok
        val root = File(ex.value.path)
        assertEquals("hello мир", File(root, "src/a.txt").readText())
        assertEquals(300_000L, File(root, "src/sub/b.bin").length())
    }

    @Test fun zip() = roundTrip("zip", ext = ".zip")
    @Test fun zipPassword() = roundTrip("zip", "secret", ".zip")
    @Test fun sevenZ() = roundTrip("7z", ext = ".7z")
    @Test fun tar() = roundTrip("tar", ext = ".tar")
    @Test fun tarGz() = roundTrip("tar.gz", ext = ".tar.gz")
    @Test fun tarXz() = roundTrip("tar.xz", ext = ".tar.xz")
    @Test fun tarBz2() = roundTrip("tar.bz2", ext = ".tar.bz2")

    @Test fun wrongPasswordIsReported() {
        val src = sample()
        val out = tmp.newFolder("o")
        val svc = ArchiveService()
        val created = runBlocking { svc.create(listOf(src.path), out.path, "p", "zip", "right", 5) {} } as Outcome.Ok
        val res = runBlocking { svc.extract(created.value.path, out.path, "wrong", "folder", emptySet()) {} }
        assertTrue(res is Outcome.Err)
        assertEquals("WRONG_PASSWORD", (res as Outcome.Err).code)
    }

    @Test fun zipSlipNamesAreRejected() {
        assertNull(ArchiveService.cleanName("../evil"))
        assertNull(ArchiveService.cleanName("a/../../evil"))
        assertEquals("a/b", ArchiveService.cleanName("/a/b/"))
    }

    @Test fun transferNeverOverwrites() {
        val a = tmp.newFolder("a")
        val b = tmp.newFolder("b")
        File(a, "f.txt").writeText("1")
        File(b, "f.txt").writeText("2")
        val r = Fs.transfer(listOf(File(a, "f.txt").path), b.path, false)
        assertEquals(1, r.done)
        assertEquals("2", File(b, "f.txt").readText())
        assertEquals("1", File(b, "f (1).txt").readText())
    }

    @Test fun folderCannotGoIntoItself() {
        val a = tmp.newFolder("self")
        val inner = File(a, "inner").apply { mkdirs() }
        val r = Fs.transfer(listOf(a.path), inner.path, false)
        assertEquals(1, r.failed.size)
    }

    @Test fun renameRejectsBadNames() {
        val f = tmp.newFile("x.txt")
        assertFalse(runCatching { Fs.rename(f.path, "a/b") }.isSuccess)
        assertTrue(runCatching { Fs.rename(f.path, "y.txt") }.isSuccess)
    }

    @Test fun formatting() {
        assertTrue(formatSize(512).startsWith("512 "))
        assertTrue(formatSize(1536).startsWith("1,50 "))
        assertTrue(compareNames("файл2", "файл10") < 0)
        assertEquals("archive", archiveBase("archive.tar.gz"))
        assertTrue(isArchive("a.TAR.XZ"))
        assertEquals("a · b", meta("a", null, "", "b"))
    }

    @Test fun keeperAvoidsDownloadsAndCopies() {
        val rows = listOf(
            CleanRow("/storage/emulated/0/Download/photo.jpg", 10, 1),
            CleanRow("/storage/emulated/0/DCIM/photo (1).jpg", 10, 2),
            CleanRow("/storage/emulated/0/DCIM/photo.jpg", 10, 3),
        )
        assertEquals("/storage/emulated/0/DCIM/photo.jpg", CleanerLogic.keeper(rows).path)
    }
}
