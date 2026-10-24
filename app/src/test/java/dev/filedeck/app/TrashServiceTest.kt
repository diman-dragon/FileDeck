package dev.filedeck.app

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class TrashServiceTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private fun service(root: File) = TrashService { listOf(root) }

    @Test fun moveListRestoreRoundTrip() = runBlocking {
        val root = tmp.newFolder("root")
        val svc = service(root)
        val f = File(root, "a.txt").apply { writeText("hi") }

        val moved = svc.move(listOf(f.path))
        assertEquals(1, moved.done)
        assertFalse(f.exists())

        val list = svc.list()
        assertEquals(1, list.count)
        assertEquals("a.txt", list.items[0].name)

        val restored = svc.restore(setOf(list.items[0].id)) {}
        assertEquals(1, restored.done)
        assertEquals("hi", f.readText())
        assertEquals(0, svc.list().count)
    }

    @Test fun restoreNeverOverwritesExistingFile() = runBlocking {
        val root = tmp.newFolder("root2")
        val svc = service(root)
        val f = File(root, "a.txt").apply { writeText("old") }
        svc.move(listOf(f.path))
        File(root, "a.txt").writeText("new")

        svc.restore(svc.list().items.map { it.id }.toSet()) {}
        assertEquals("new", File(root, "a.txt").readText())
        assertEquals("old", File(root, "a (1).txt").readText())
    }

    @Test fun removeAndEmptyFreeSpace() = runBlocking {
        val root = tmp.newFolder("root3")
        val svc = service(root)
        val a = File(root, "a.bin").apply { writeBytes(ByteArray(100)) }
        val b = File(root, "b.bin").apply { writeBytes(ByteArray(50)) }
        svc.move(listOf(a.path, b.path))
        val ids = svc.list().items.associateBy { it.name }

        val removed = svc.remove(setOf(ids.getValue("a.bin").id)) {}
        assertEquals(1, removed.done)
        assertEquals(100L, removed.bytes)
        assertEquals(1, svc.list().count)

        val emptied = svc.empty {}
        assertEquals(1, emptied.done)
        assertTrue(svc.list().items.isEmpty())
    }

    @Test fun purgeDeletesOnlyExpired() = runBlocking {
        val root = tmp.newFolder("root4")
        val svc = service(root)
        val f = File(root, "x.txt").apply { writeText("x") }
        svc.move(listOf(f.path))
        assertEquals(0, svc.purge(30).done)
        assertEquals(1, svc.list().count)
    }

    @Test fun cannotTrashStorageRootOrTrashItself() = runBlocking {
        val root = tmp.newFolder("root5")
        val svc = service(root)
        val res = svc.move(listOf(root.path))
        assertEquals(0, res.done)
        assertEquals(1, res.failed.size)
    }
}
