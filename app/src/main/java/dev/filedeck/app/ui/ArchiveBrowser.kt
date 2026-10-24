package dev.filedeck.app.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewModelScope
import dev.filedeck.app.ArchiveEntryInfo
import dev.filedeck.app.ArchiveListing
import dev.filedeck.app.FileEntry
import dev.filedeck.app.Outcome
import dev.filedeck.app.R
import dev.filedeck.app.Str
import dev.filedeck.app.archiveBase
import dev.filedeck.app.compareNames
import dev.filedeck.app.explainArchiveError
import dev.filedeck.app.parentOf
import kotlinx.coroutines.launch

class ArcNode(
    val name: String,
    val path: String,
    val dir: Boolean,
    var size: Long,
    var mtime: Long,
    var count: Int = 0,
    val children: LinkedHashMap<String, ArcNode> = LinkedHashMap(),
)

fun buildArcTree(entries: List<ArchiveEntryInfo>): ArcNode {
    val root = ArcNode("", "", true, 0, 0)
    fun ensureDir(parts: List<String>): ArcNode {
        var node = root
        var acc = ""
        for (part in parts) {
            acc = if (acc.isEmpty()) part else "$acc/$part"
            var next = node.children[part]
            if (next == null) {
                next = ArcNode(part, acc, true, 0, 0)
                node.children[part] = next
            }
            node = next
        }
        return node
    }
    for (e in entries) {
        val parts = e.name.split("/").filter { it.isNotEmpty() }
        if (parts.isEmpty()) continue
        if (e.dir) {
            ensureDir(parts).mtime = e.mtime
            continue
        }
        val parent = ensureDir(parts.dropLast(1))
        parent.children[parts.last()] = ArcNode(parts.last(), parts.joinToString("/"), false, e.size, e.mtime)
    }
    fun sum(n: ArcNode): Pair<Long, Int> {
        if (!n.dir) return n.size to 1
        var size = 0L
        var count = 0
        for (c in n.children.values) {
            val r = sum(c)
            size += r.first
            count += r.second
        }
        n.size = size
        n.count = count
        return size to count
    }
    sum(root)
    return root
}

class ArchiveBrowserController(val vm: AppViewModel, val entry: FileEntry) {
    var data by mutableStateOf<ArchiveListing?>(null)
    var root by mutableStateOf<ArcNode?>(null)
    var cwd by mutableStateOf("")
    var loading by mutableStateOf(true)
    var selected by mutableStateOf<Set<String>>(emptySet())
    private var password = ""

    init {
        vm.viewModelScope.launch { load() }
    }

    private fun close() {
        vm.overlays.removeAll { it is Overlay.ArchiveView && it.c === this }
    }

    fun onBack(): Boolean {
        if (cwd.isNotEmpty()) {
            goUp()
            return true
        }
        return false
    }

    fun goUp() {
        cwd = if (cwd.contains('/')) cwd.substringBeforeLast('/') else ""
    }

    private suspend fun load() {
        while (true) {
            when (val res = vm.archives.list(entry.path, password)) {
                is Outcome.Ok -> {
                    data = res.value
                    root = if (res.value.listing) buildArcTree(res.value.entries) else null
                    loading = false
                    return
                }
                Outcome.Aborted -> {
                    close()
                    return
                }
                is Outcome.Err -> {
                    if (isPasswordError(res.code)) {
                        val typed = vm.prompt(
                            Str.s(R.string.s_parol_arhiva), text = Str.s(R.string.s_imena_faylov_zashifrovany), password = true, okText = Str.s(R.string.s_otkryt),
                            error = if (res.code == "WRONG_PASSWORD") Str.s(R.string.s_nevernyy_parol) else "",
                        )
                        if (typed.isNullOrEmpty()) {
                            close()
                            return
                        }
                        password = typed
                    } else {
                        vm.toast(explainArchiveError(res.code, res.message), true)
                        close()
                        return
                    }
                }
            }
        }
    }

    fun children(): List<ArcNode> {
        var n: ArcNode? = root
        for (part in cwd.split("/").filter { it.isNotEmpty() }) n = n?.children?.get(part)
        val kids = n?.children?.values?.toList() ?: emptyList()
        return kids.sortedWith { a, b -> if (a.dir != b.dir) (if (a.dir) -1 else 1) else compareNames(a.name, b.name) }
    }

    fun toggle(path: String) {
        selected = if (path in selected) selected - path else selected + path
    }

    fun open(node: ArcNode) {
        if (node.dir) cwd = node.path else toggle(node.path)
    }

    fun extract(only: Set<String>) {
        val d = data ?: return
        vm.sheet(
            if (only.isNotEmpty()) Str.f(R.string.s_raspakovat_vybrannoe, only.size) else Str.s(R.string.s_raspakovat_vse),
            null,
            buildList {
                add(SheetItem(Icons2.folder, Str.f(R.string.s_v_papku, archiveBase(entry.name)), Str.s(R.string.s_rekomenduetsya)) { run("folder", only) })
                if (d.format != "RAR") add(SheetItem(Icons2.download, Str.s(R.string.s_syuda_ryadom_s_arhivom)) { run("here", only) })
            },
        )
    }

    private fun run(mode: String, only: Set<String>) {
        vm.viewModelScope.launch {
            val res = vm.extractFlow(entry.path, parentOf(entry.path), mode, only, password)
            if (res != null) {
                password = res.second.ifEmpty { password }
                vm.refresh()
                selected = emptySet()
            }
        }
    }
}
