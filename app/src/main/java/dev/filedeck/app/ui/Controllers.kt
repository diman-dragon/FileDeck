package dev.filedeck.app.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewModelScope
import dev.filedeck.app.ArchiveExtracted
import dev.filedeck.app.ArchiveListing
import dev.filedeck.app.FileEntry
import dev.filedeck.app.Fs
import dev.filedeck.app.Outcome
import dev.filedeck.app.TrashItem
import dev.filedeck.app.archiveBase
import dev.filedeck.app.basename
import dev.filedeck.app.compareNames
import dev.filedeck.app.explainArchiveError
import dev.filedeck.app.filesWord
import dev.filedeck.app.formatSize
import dev.filedeck.app.isArchive
import dev.filedeck.app.isEditable
import dev.filedeck.app.parentOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ======================================================================== потоки архивов

private fun isPasswordError(code: String) = code == "PASSWORD_REQUIRED" || code == "WRONG_PASSWORD"

/** mode: "folder" — в новую папку с именем архива, "here" — прямо в dest. Возвращает результат и пароль. */
suspend fun AppViewModel.extractFlow(
    path: String,
    dest: String,
    mode: String,
    only: Set<String> = emptySet(),
    password: String = "",
): Pair<ArchiveExtracted, String>? {
    var pw = password
    while (true) {
        val res = withProgress("Распаковываем…", { archives.cancel() }) { update ->
            archives.extract(path, dest, pw, mode, only) { update(it.fraction, it.current) }
        }
        when (res) {
            is Outcome.Ok -> {
                toast("Распаковано: ${res.value.count} ${filesWord(res.value.count.toInt())}")
                return res.value to pw
            }
            Outcome.Aborted -> {
                toast("Отменено")
                return null
            }
            is Outcome.Err -> {
                if (isPasswordError(res.code)) {
                    val typed = prompt(
                        "Пароль архива", text = basename(path), password = true, okText = "Продолжить",
                        error = if (res.code == "WRONG_PASSWORD") "Неверный пароль. Попробуйте ещё раз." else "",
                    )
                    if (typed.isNullOrEmpty()) return null
                    pw = typed
                } else {
                    toast(explainArchiveError(res.code, res.message), true)
                    return null
                }
            }
        }
    }
}

suspend fun AppViewModel.createArchiveFlow(entries: List<FileEntry>, destDir: String): Boolean {
    val first = entries[0]
    val defaultName = if (entries.size == 1) {
        if (first.dir) first.name else first.name.replace(Regex("""\.[^.]+$"""), "")
    } else "Архив"
    val opts = archiveOptions(defaultName, entries.size) ?: return false
    val res = withProgress("Создаём архив…", { archives.cancel() }) { update ->
        archives.create(entries.map { it.path }, destDir, opts.name, opts.format, opts.password, opts.level) {
            update(it.fraction, it.current)
        }
    }
    return when (res) {
        is Outcome.Ok -> {
            toast("Архив создан: ${basename(res.value.path)} · ${formatSize(res.value.size)}")
            true
        }
        Outcome.Aborted -> {
            toast("Отменено")
            false
        }
        is Outcome.Err -> {
            toast(explainArchiveError(res.code, res.message), true)
            false
        }
    }
}

fun AppViewModel.zipSelected() {
    val sel = selectedEntries()
    if (sel.isEmpty()) return
    viewModelScope.launch {
        if (createArchiveFlow(sel, path ?: parentOf(sel[0].path))) {
            clearSelection()
            refresh()
        }
    }
}

fun AppViewModel.openArchiveSheet(e: FileEntry) {
    val dest = parentOf(e.path)
    val rar = e.name.endsWith(".rar", ignoreCase = true)
    val items = mutableListOf(
        SheetItem(
            Icons2.layers, "Просмотреть содержимое",
            if (rar) "Для RAR список недоступен — распаковка целиком" else "Выбрать, что распаковать",
        ) { overlays.add(Overlay.ArchiveView(ArchiveBrowserController(this, e))) },
        SheetItem(Icons2.unzip, "Распаковать в папку «${archiveBase(e.name)}»") {
            viewModelScope.launch { if (extractFlow(e.path, dest, "folder") != null) refresh() }
        },
    )
    if (!rar) {
        items.add(
            SheetItem(Icons2.download, "Распаковать сюда", "Файлы окажутся рядом с архивом") {
                viewModelScope.launch { if (extractFlow(e.path, dest, "here") != null) refresh() }
            },
        )
    }
    items.add(SheetItem(Icons2.external, "Открыть в другом приложении") { openExternal(e) })
    sheet(e.name, formatSize(e.size), items)
}

fun AppViewModel.moreForSelection() {
    val sel = selectedEntries()
    val one = if (sel.size == 1) sel[0] else null
    val items = mutableListOf<SheetItem>()
    if (one != null) items.add(SheetItem(Icons2.edit, "Переименовать") { renameEntry(one) })
    items.add(SheetItem(Icons2.archive, "Добавить в архив…", "ZIP, 7z, TAR · пароль, уровень сжатия") { zipSelected() })
    if (one != null && isArchive(one.name)) {
        items.add(SheetItem(Icons2.unzip, "Распаковать…", "Просмотр содержимого и выбор файлов") {
            clearSelection()
            openArchiveSheet(one)
        })
    }
    if (one != null && !one.dir) items.add(SheetItem(Icons2.external, "Открыть в приложении") { openExternal(one) })
    if (one != null && one.isEditable()) items.add(SheetItem(Icons2.file, "Открыть в редакторе") { openEditor(one) })
    if (one != null) items.add(SheetItem(Icons2.info, "Свойства") { showProps(one) })
    if (settings.useTrash) {
        items.add(SheetItem(divider = true))
        items.add(SheetItem(Icons2.delete, "Удалить навсегда", danger = true) { deletePaths(sel.map { it.path }, permanent = true) })
    }
    sheet(if (one != null) one.name else "Выбрано: ${sel.size}", null, items)
}

// ======================================================================== просмотр архива

class ArcNode(
    val name: String,
    val path: String,
    val dir: Boolean,
    var size: Long,
    var mtime: Long,
    var count: Int = 0,
    val children: LinkedHashMap<String, ArcNode>? = null,
)

/** Дерево папок и файлов внутри архива. */
fun buildArcTree(entries: List<dev.filedeck.app.ArchiveEntryInfo>): ArcNode {
    val root = ArcNode("", "", true, 0, 0, 0, LinkedHashMap())
    fun ensureDir(parts: List<String>): ArcNode {
        var node = root
        var acc = ""
        for (part in parts) {
            acc = if (acc.isEmpty()) part else "$acc/$part"
            var next = node.children!![part]
            if (next == null) {
                next = ArcNode(part, acc, true, 0, 0, 0, LinkedHashMap())
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
        parent.children!![parts.last()] = ArcNode(parts.last(), parts.joinToString("/"), false, e.size, e.mtime)
    }
    fun sum(n: ArcNode): Pair<Long, Int> {
        if (!n.dir) return n.size to 1
        var size = 0L
        var count = 0
        for (c in n.children!!.values) {
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
                            "Пароль архива", text = "Имена файлов зашифрованы", password = true, okText = "Открыть",
                            error = if (res.code == "WRONG_PASSWORD") "Неверный пароль." else "",
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
            if (only.isNotEmpty()) "Распаковать выбранное (${only.size})" else "Распаковать всё",
            null,
            buildList {
                add(SheetItem(Icons2.folder, "В папку «${archiveBase(entry.name)}»", "Рекомендуется") { run("folder", only) })
                if (d.format != "RAR") add(SheetItem(Icons2.download, "Сюда, рядом с архивом") { run("here", only) })
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

// ======================================================================== корзина

class TrashController(val vm: AppViewModel) {
    var items by mutableStateOf<List<TrashItem>>(emptyList())
    var bytes by mutableStateOf(0L)
    var loading by mutableStateOf(true)
    var selected by mutableStateOf<Set<String>>(emptySet())
    private var busy = false

    val days: Int get() = vm.settings.trashDays

    init {
        vm.viewModelScope.launch { load() }
    }

    suspend fun load() {
        try {
            val r = vm.trashSvc.list()
            items = r.items
            bytes = r.bytes
        } catch (e: Exception) {
            vm.toast(e.message ?: "Не удалось прочитать корзину", true)
        }
        loading = false
        selected = selected.filter { id -> items.any { it.id == id } }.toSet()
        vm.refreshTrash()
    }

    fun toggle(id: String) {
        selected = if (id in selected) selected - id else selected + id
    }

    fun toggleAll() {
        selected = if (selected.size == items.size) emptySet() else items.map { it.id }.toSet()
    }

    fun restore() {
        if (busy) return
        busy = true
        vm.viewModelScope.launch {
            try {
                val res = vm.withProgress("Восстанавливаем…", { vm.trashSvc.cancel() }) { update ->
                    vm.trashSvc.restore(selected) { update(it.fraction, it.current) }
                }
                val failed = res.failed.size
                vm.toast(
                    (if (res.cancelled) "Отменено. " else "") + "Восстановлено: ${res.done}" +
                        (if (failed > 0) ", не удалось: $failed" else ""),
                    failed > 0,
                )
                selected = emptySet()
                vm.refresh()
            } catch (e: Exception) {
                vm.toast(e.message ?: "Не удалось восстановить", true)
            }
            busy = false
            load()
        }
    }

    /** all = true — очистить всю корзину, иначе удалить только выбранное. */
    fun delete(all: Boolean) {
        vm.viewModelScope.launch {
            val ok = vm.confirm(
                if (all) "Очистить корзину?" else "Удалить навсегда (${selected.size})?",
                "Файлы будут удалены без возможности восстановления.",
                if (all) "Очистить" else "Удалить",
                danger = true,
            )
            if (!ok || busy) return@launch
            busy = true
            val expected = if (all) items.size else selected.size
            try {
                val res = vm.withProgress(if (all) "Очищаем корзину…" else "Удаляем…", { vm.trashSvc.cancel() }) { update ->
                    val cb: (dev.filedeck.app.TrashProgress) -> Unit = { update(it.fraction, it.current) }
                    if (all) vm.trashSvc.empty(cb) else vm.trashSvc.remove(selected, cb)
                }
                val failed = if (res.cancelled) 0 else maxOf(0, expected - res.done)
                val freed = "освобождено ${formatSize(res.bytes).ifEmpty { "0 Б" }}"
                when {
                    res.cancelled -> vm.toast("Отменено. Удалено: ${res.done} · $freed")
                    failed > 0 -> vm.toast("Удалено: ${res.done}, не удалось: $failed", true)
                    else -> vm.toast("Удалено: ${res.done} · $freed")
                }
                selected = emptySet()
            } catch (e: Exception) {
                vm.toast(e.message ?: "Не удалось удалить", true)
            }
            busy = false
            load()
        }
    }
}

// ======================================================================== редактор

class EditorController(val vm: AppViewModel, val entry: FileEntry) {
    var text by mutableStateOf("")
    var original = ""
    var loaded by mutableStateOf(false)
    val dirty: Boolean get() = loaded && text != original

    init {
        vm.viewModelScope.launch {
            try {
                val t = withContext(Dispatchers.IO) { Fs.readText(entry.path) }
                text = t
                original = t
                loaded = true
            } catch (e: Exception) {
                vm.toast(e.message ?: "Не удалось прочитать файл", true)
                close()
            }
        }
    }

    private fun close() {
        vm.overlays.removeAll { it is Overlay.Editor && it.c === this }
    }

    fun save() {
        if (!dirty) return
        val t = text
        vm.viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { Fs.writeText(entry.path, t) }
                original = t
                vm.toast("Сохранено")
                vm.refresh()
            } catch (e: Exception) {
                vm.toast(e.message ?: "Не удалось сохранить", true)
            }
        }
    }

    /** true — «Назад» обработан (показан вопрос о несохранённом). */
    fun onBack(): Boolean {
        if (!dirty) return false
        vm.viewModelScope.launch {
            val discard = vm.confirm("Закрыть без сохранения?", "Изменения в файле будут потеряны.", "Закрыть", danger = true)
            if (discard) close()
        }
        return true
    }
}
