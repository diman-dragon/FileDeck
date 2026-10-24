package dev.filedeck.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CleanerLogicTest {
    private fun result(
        dups: List<DupGroup> = emptyList(),
        junk: List<JunkItem> = emptyList(),
        empty: List<String> = emptyList(),
    ) = ScanResult(dups, emptyList(), junk, emptyList(), emptyList(), empty, emptyList(), emptyMap(), ScanStats(0, 0, 0, 0))

    private fun group(vararg paths: String) = DupGroup(100, paths.size, paths.map { DupFile(it, 1) })

    @Test fun defaultSelectionKeepsExactlyOneCopy() {
        val g = group("/s/Download/a.jpg", "/s/DCIM/a.jpg", "/s/DCIM/a (1).jpg")
        val sel = CleanerLogic.defaultSelection(result(dups = listOf(g)))
        assertEquals(setOf("/s/Download/a.jpg", "/s/DCIM/a (1).jpg"), sel[CleanCat.Duplicates])
    }

    @Test fun junkAndEmptyFoldersArePreselected() {
        val r = result(junk = listOf(JunkItem("/s/.cache", 5, true, "cache")), empty = listOf("/s/empty"))
        val sel = CleanerLogic.defaultSelection(r)
        assertEquals(setOf("/s/.cache"), sel[CleanCat.Junk])
        assertEquals(setOf("/s/empty"), sel[CleanCat.EmptyFolders])
        assertTrue(sel[CleanCat.Largest]!!.isEmpty())
    }

    @Test fun purgeDropsGroupsThatBecomeSingles() {
        val r = result(dups = listOf(group("/s/a", "/s/b")))
        val sel = CleanerLogic.defaultSelection(r)
        val (next, newSel) = CleanerLogic.purge(r, sel, listOf("/s/b"))
        assertTrue(next.duplicates.isEmpty())
        assertTrue(newSel[CleanCat.Duplicates]!!.isEmpty())
    }

    @Test fun purgeRemovesNestedPathsOfDeletedFolder() {
        val r = result(empty = listOf("/s/a/b", "/s/other"))
        val (next, _) = CleanerLogic.purge(r, CleanerLogic.defaultSelection(r), listOf("/s/a"))
        assertEquals(listOf("/s/other"), next.emptyFolders)
    }

    @Test fun wasteCountsOnlyExtraCopies() {
        val r = result(dups = listOf(group("/s/a", "/s/b", "/s/c")))
        assertEquals(200L, CleanerLogic.wasteOf(r, CleanCat.Duplicates))
    }

    @Test fun usageCategoriesComeFromSingleExtensionTable() {
        assertEquals(0, FileRules.usageIndex("jpg"))
        assertEquals(1, FileRules.usageIndex("mkv"))
        assertEquals(2, FileRules.usageIndex("mp3"))
        assertEquals(3, FileRules.usageIndex("pdf"))
        assertEquals(3, FileRules.usageIndex("txt"))
        assertEquals(4, FileRules.usageIndex("zip"))
        assertEquals(5, FileRules.usageIndex("apk"))
        assertEquals(6, FileRules.usageIndex("kt"))
        assertTrue(FileRules.APK_EXT.contains("xapk"))
        assertEquals(FileKind.Image, kindOf("x.HEIF".lowercase(), false))
    }

    @Test fun junkRules() {
        assertEquals("temp", FileRules.junkFileKind("a.tmp", "tmp"))
        assertEquals("logs", FileRules.junkFileKind("x.log", "log"))
        assertEquals("trashed", FileRules.junkFileKind(".trashed-1-x", "x"))
        assertNull(FileRules.junkFileKind("photo.jpg", "jpg"))
        assertEquals("cache", FileRules.junkDirKind(".cache"))
        assertNull(FileRules.junkDirKind("dcim"))
        assertTrue(FileRules.PROTECTED_DIRS.contains("dcim"))
    }

    @Test fun sortingPutsFoldersFirst() {
        val list = listOf(
            FileEntry("b.txt", "/b.txt", false, 10, 1),
            FileEntry("Z", "/Z", true, 0, 2),
            FileEntry("a10.txt", "/a10", false, 5, 3),
            FileEntry("a2.txt", "/a2", false, 7, 4),
        )
        val byName = sortEntries(list, SortBy.NAME, false).map { it.name }
        assertEquals(listOf("Z", "a2.txt", "a10.txt", "b.txt"), byName)
        assertFalse(sortEntries(list, SortBy.NAME, true).first().name != "Z")
    }
}
