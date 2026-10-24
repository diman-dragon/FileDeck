package dev.filedeck.app.ui

import android.Manifest
import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings as AndroidSettings
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.filedeck.app.ArchiveService
import dev.filedeck.app.CleanerService
import dev.filedeck.app.FileEntry
import dev.filedeck.app.FileKind
import dev.filedeck.app.Fs
import dev.filedeck.app.OpResult
import dev.filedeck.app.SearchState
import dev.filedeck.app.Settings
import dev.filedeck.app.SettingsStore
import dev.filedeck.app.StorageRoot
import dev.filedeck.app.TrashService
import dev.filedeck.app.basename
import dev.filedeck.app.formatSize
import dev.filedeck.app.isArchive
import dev.filedeck.app.isEditable
import dev.filedeck.app.kind
import dev.filedeck.app.parentOf
import dev.filedeck.app.sortEntries
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ======================================================================== модели диалогов

class SheetItem(
    val icon: ImageVector? = null,
    val label: String = "",
    val hint: String? = null,
    val danger: Boolean = false,
    val divider: Boolean = false,
    val onClick: () -> Unit = {},
)

data class ArchiveOptions(val name: String, val format: String, val level: Int, val password: String)

class InfoState(val title: String, initial: List<Pair<String, String>>) {
    val rows = mutableStateListOf<Pair<String, String>>().apply { addAll(initial) }

    fun set(key: String, value: String) {
        val i = rows.indexOfFirst { it.first == key }
        if (i >= 0) rows[i] = key to value
    }
}

sealed interface Dlg {
    class Confirm(
        val title: String,
        val text: String,
        val okText: String,
        val danger: Boolean,
        val result: CompletableDeferred<Boolean>,
    ) : Dlg

    class Prompt(
        val title: String,
        val text: String?,
        val value: String,
        val placeholder: String,
        val okText: String,
        val selectBase: Boolean,
        val password: Boolean,
        val error: String,
        val result: CompletableDeferred<String?>,
    ) : Dlg

    class Info(val state: InfoState) : Dlg

    class Sheet(val title: String, val subtitle: String?, val items: List<SheetItem>) : Dlg

    class CreateArchive(
        val defaultName: String,
        val count: Int,
        val result: CompletableDeferred<ArchiveOptions?>,
    ) : Dlg
}

class ProgressState(
    val title: String,
    val fraction: Float?,
    val current: String?,
    val cancel: (() -> Unit)?,
)

data class ToastMsg(val text: String, val error: Boolean)

enum class ClipMode { COPY, MOVE }

data class Clip(val mode: ClipMode, val paths: List<String>)

/** Полноэкранные слои поверх главного экрана. */
sealed interface Overlay {
    data object Settings : Overlay
    class Cleaner(val c: CleanerController) : Overlay
    class Trash(val c: TrashController) : Overlay
    class ArchiveView(val c: ArchiveBrowserController) : Overlay
    class Image(val images: List<FileEntry>, val start: Int) : Overlay
    class Editor(val c: EditorController) : Overlay
}

// ======================================================================== модель приложения

class AppViewModel(private val app: Application) : AndroidViewModel(app) {

    private val store = SettingsStore(app)

    var settings by mutableStateOf(store.load())
        private set

    val archives = ArchiveService()
    val trashSvc = TrashService(app)
    val cleanerSvc = CleanerService(app)

    fun updateSettings(change: (Settings) -> Settings) {
        settings = change(settings)
        store.save(settings)
    }

    fun resetSettings() {
        store.reset()
        settings = Settings()
    }

    // ---------------------------------------------------------------- состояние экрана

    /** null — ещё проверяем. */
    var perm by mutableStateOf<Boolean?>(null)
        private set
    var roots by mutableStateOf<List<StorageRoot>>(emptyList())
        private set

    /** null — главный экран. */
    var path by mutableStateOf<String?>(null)
        private set
    var entries by mutableStateOf<List<FileEntry>>(emptyList())
        private set
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var selection by mutableStateOf<Set<String>>(emptySet())
        private set
    var clip by mutableStateOf<Clip?>(null)
        private set
    var search by mutableStateOf<SearchState?>(null)
        private set
    var trashCount by mutableStateOf(0)
        private set
    var trashBytes by mutableStateOf(0L)
        private set

    val overlays = mutableStateListOf<Overlay>()
    var dialog by mutableStateOf<Dlg?>(null)
    var progress by mutableStateOf<ProgressState?>(null)
        private set

    val toasts = MutableSharedFlow<ToastMsg>(extraBufferCapacity = 16)

    /** Положение прокрутки по папкам: первый видимый элемент. */
    val scrollMemo = HashMap<String, Int>()

    private val history = ArrayList<String?>()
    private val lastIn = HashMap<String, String>()
    private var started = false
    private var loadJob: Job? = null
    private var searchJob: Job? = null

    /** Для Android 10 и ниже: запрос разрешения на память делает Activity. */
    var requestLegacyPermission: (() -> Unit)? = null

    val visibleEntries: List<FileEntry> by derivedStateOf {
        val s = search
        if (s != null) {
            s.results
        } else {
            val shown = if (settings.hidden) entries else entries.filter { !it.name.startsWith(".") }
            sortEntries(shown, settings.sortBy, settings.desc)
        }
    }

    fun primaryRoot(): StorageRoot? = roots.firstOrNull { it.primary } ?: roots.firstOrNull()

    fun rootOf(p: String): StorageRoot? =
        roots.filter { p == it.path || p.startsWith(it.path + "/") }.maxByOrNull { it.path.length }

    fun toast(text: String, error: Boolean = false) {
        toasts.tryEmit(ToastMsg(text, error))
    }

    // ---------------------------------------------------------------- диалоги

    suspend fun confirm(title: String, text: String, okText: String, danger: Boolean = false): Boolean {
        val d = CompletableDeferred<Boolean>()
        dialog = Dlg.Confirm(title, text, okText, danger, d)
        try {
            return d.await()
        } finally {
            dialog = null
        }
    }

    /** Возвращает null, если пользователь отменил. */
    suspend fun prompt(
        title: String,
        value: String = "",
        okText: String = "OK",
        text: String? = null,
        placeholder: String = "",
        selectBase: Boolean = false,
        password: Boolean = false,
        error: String = "",
    ): String? {
        val d = CompletableDeferred<String?>()
        dialog = Dlg.Prompt(title, text, value, placeholder, okText, selectBase, password, error, d)
        try {
            return d.await()
        } finally {
            dialog = null
        }
    }

    fun sheet(title: String, subtitle: String? = null, items: List<SheetItem>) {
        dialog = Dlg.Sheet(title, subtitle, items)
    }

    suspend fun archiveOptions(defaultName: String, count: Int): ArchiveOptions? {
        val d = CompletableDeferred<ArchiveOptions?>()
        dialog = Dlg.CreateArchive(defaultName, count, d)
        try {
            return d.await()
        } finally {
            dialog = null
        }
    }

    /** Окно с полосой прогресса. update(fraction, текущий файл) можно вызывать из любого потока. */
    suspend fun <T> withProgress(
        title: String,
        cancel: (() -> Unit)? = null,
        block: suspend (update: (Double?, String?) -> Unit) -> T,
    ): T {
        progress = ProgressState(title, null, null, cancel)
        try {
            return block { f, c -> progress = ProgressState(title, f?.toFloat(), c, cancel) }
        } finally {
            progress = null
        }
    }

    // ---------------------------------------------------------------- доступ к файлам

    private fun hasAccess(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(app, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(app, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }

    fun hasAccessNow(): Boolean = hasAccess()

    fun checkPermission() {
        viewModelScope.launch {
            perm = hasAccess()
            if (perm == true) {
                loadRoots()
                launch {
                    try {
                        trashSvc.purge(settings.trashDays)
                    } catch (e: Exception) {
                        // корзины может ещё не быть
                    }
                    refreshTrash()
                }
                val first = when (settings.start) {
                    dev.filedeck.app.StartScreen.PRIMARY -> primaryRoot()
                    dev.filedeck.app.StartScreen.SD -> roots.firstOrNull { !it.primary }
                    dev.filedeck.app.StartScreen.HOME -> null
                }
                if (first != null && path == null && !started) {
                    started = true
                    go(first.path, replace = true) // стартовый экран — дно истории
                    return@launch
                }
                started = true
            }
        }
    }

    fun requestPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val i = Intent(
                    AndroidSettings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                    Uri.parse("package:" + app.packageName),
                )
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                app.startActivity(i)
            } catch (e: Exception) {
                try {
                    val i = Intent(AndroidSettings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    app.startActivity(i)
                } catch (e2: Exception) {
                    toast("Не удалось открыть настройки доступа", true)
                }
            }
        } else {
            requestLegacyPermission?.invoke()
        }
    }

    fun onResume() {
        if (perm != true) {
            checkPermission()
        } else {
            viewModelScope.launch { loadRoots() }
            refreshTrash()
        }
    }

    // ---------------------------------------------------------------- накопители и папки

    suspend fun loadRoots() {
        try {
            roots = withContext(Dispatchers.IO) { Fs.roots(app) }
        } catch (e: Exception) {
            toast(e.message ?: "Не удалось получить список накопителей", true)
        }
    }

    fun refreshTrash() {
        viewModelScope.launch {
            try {
                val r = trashSvc.list()
                trashCount = r.count
                trashBytes = r.bytes
            } catch (e: Exception) {
                // корзины может ещё не быть
            }
        }
    }

    fun trashLabel(): String =
        if (trashCount > 0) "$trashCount · ${formatSize(trashBytes).ifEmpty { "0 Б" }}" else "пусто"

    private fun trackHistory(target: String?) {
        val top = history.lastOrNull()
        if (history.isNotEmpty() && top == target) {
            history.removeAt(history.size - 1)
        } else if (path != target) {
            history.add(path)
            if (history.size > 100) history.removeAt(0)
        }
    }

    /** replace = true — не записывать в историю (стартовый экран и шаг назад). */
    fun go(target: String?, replace: Boolean = false) {
        if (!replace) trackHistory(target)
        searchJob?.cancel()
        search = null
        selection = emptySet()
        path = target
        entries = emptyList()
        error = null
        if (target != null) {
            rootOf(target)?.let { lastIn[it.path] = target }
        }
        if (target == null) {
            loadJob?.cancel()
            loading = false
            viewModelScope.launch { loadRoots() }
            return
        }
        load(target)
    }

    private fun load(target: String) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            loading = true
            error = null
            try {
                val list = withContext(Dispatchers.IO) { Fs.list(target) }
                if (path != target) return@launch // пользователь ушёл дальше
                entries = list
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (path != target) return@launch
                entries = emptyList()
                error = e.message ?: "Не удалось открыть папку"
            }
            loading = false
        }
    }

    fun refresh() {
        val p = path
        if (p != null) load(p) else viewModelScope.launch { loadRoots() }
    }

    fun up(): Boolean {
        val p = path ?: return false
        val root = rootOf(p)
        if (root == null || p == root.path) go(null) else go(parentOf(p))
        return true
    }

    fun switchRoot(sd: Boolean) {
        if (perm != true) {
            toast("Сначала выдайте доступ к файлам", true)
            return
        }
        val root = if (sd) roots.firstOrNull { !it.primary } else primaryRoot()
        if (root == null) {
            toast("SD-карта не найдена. Вставьте карту и вернитесь в приложение", true)
            return
        }
        val cur = path?.let { rootOf(it) }
        if (cur != null && cur.path == root.path && path != root.path && search == null) {
            go(root.path) // повторный тап — в корень
            return
        }
        go(lastIn[root.path] ?: root.path)
    }

    // ---------------------------------------------------------------- выделение, буфер

    fun toggleSelect(p: String) {
        selection = if (p in selection) selection - p else selection + p
    }

    fun clearSelection() {
        selection = emptySet()
    }

    fun selectAll() {
        selection = visibleEntries.map { it.path }.toSet()
    }

    fun selectedEntries(): List<FileEntry> = visibleEntries.filter { it.path in selection }

    fun toClipboard(mode: ClipMode) {
        val paths = selection.toList()
        clip = Clip(mode, paths)
        selection = emptySet()
        toast(
            (if (mode == ClipMode.MOVE) "Вырезано" else "Скопировано") +
                ": ${paths.size}. Откройте нужную папку и нажмите «Вставить сюда».",
        )
    }

    fun cancelPaste() {
        clip = null
    }

    fun paste() {
        val c = clip ?: return
        val dest = path ?: return
        viewModelScope.launch {
            val move = c.mode == ClipMode.MOVE
            try {
                val res = withProgress(if (move) "Переносим…" else "Копируем…") {
                    withContext(Dispatchers.IO) { Fs.transfer(c.paths, dest, move) }
                }
                clip = null
                report(res, if (move) "Перенесено" else "Скопировано")
            } catch (e: Exception) {
                toast(e.message ?: "Операция не удалась", true)
            }
            refresh()
        }
    }

    fun report(res: OpResult, verb: String) {
        val failed = res.failed.size
        if (failed > 0) {
            toast("$verb: ${res.done}, не удалось: $failed. Нельзя вставлять папку саму в себя.", true)
        } else {
            toast("$verb: ${res.done}")
        }
    }

    // ---------------------------------------------------------------- поиск

    fun startSearch() {
        search = SearchState()
    }

    fun exitSearch() {
        searchJob?.cancel()
        search = null
        selection = emptySet()
    }

    fun runSearch(q: String) {
        val cur = search ?: return
        search = cur.copy(q = q)
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(380)
            val base = path ?: primaryRoot()?.path
            if (q.isBlank() || base == null) {
                search = search?.copy(results = emptyList(), loading = false, truncated = false)
                return@launch
            }
            search = search?.copy(loading = true)
            try {
                val res = withContext(Dispatchers.IO) { Fs.search(base, q.trim(), 400) }
                search = search?.copy(results = res.entries, truncated = res.truncated, loading = false)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                toast(e.message ?: "Поиск не удался", true)
                search = search?.copy(loading = false)
            }
        }
    }

    // ---------------------------------------------------------------- открытие файлов

    fun openExternal(e: FileEntry) {
        try {
            Fs.open(app, e.path)
        } catch (ex: Exception) {
            toast(ex.message ?: "Не удалось открыть файл", true)
        }
    }

    fun openPathExternal(path: String) {
        try {
            Fs.open(app, path)
        } catch (ex: Exception) {
            toast(ex.message ?: "Не удалось открыть файл", true)
        }
    }

    fun openEntry(e: FileEntry) {
        if (e.dir) {
            go(e.path)
            return
        }
        val kind = e.kind()
        if (kind == FileKind.Image) {
            val images = visibleEntries.filter { it.kind() == FileKind.Image }
            overlays.add(Overlay.Image(images, maxOf(0, images.indexOfFirst { it.path == e.path })))
            return
        }
        if (isArchive(e.name)) {
            openArchiveSheet(e)
            return
        }
        if (e.isEditable()) {
            sheet(
                e.name,
                formatSize(e.size),
                listOf(
                    SheetItem(Icons2.edit, "Открыть в редакторе") { openEditor(e) },
                    SheetItem(Icons2.external, "Открыть в приложении") { openExternal(e) },
                ),
            )
            return
        }
        openExternal(e)
    }

    fun openEditor(e: FileEntry) {
        overlays.add(Overlay.Editor(EditorController(this, e)))
    }

    fun openSettings() {
        overlays.add(Overlay.Settings)
    }

    fun openTrash() {
        overlays.add(Overlay.Trash(TrashController(this)))
    }

    fun openCleaner() {
        overlays.add(Overlay.Cleaner(CleanerController(this)))
    }

    fun closeOverlay(o: Overlay) {
        overlays.remove(o)
    }

    /** Системная кнопка «Назад». Приложение по ней не закрывается. */
    fun onBack() {
        val p = progress
        if (p != null) {
            p.cancel?.invoke()
            return
        }
        val top = overlays.lastOrNull()
        if (top != null) {
            val consumed = when (top) {
                is Overlay.Cleaner -> top.c.onBack()
                is Overlay.ArchiveView -> top.c.onBack()
                is Overlay.Editor -> top.c.onBack()
                else -> false
            }
            if (!consumed) closeOverlay(top)
            return
        }
        if (selection.isNotEmpty()) {
            selection = emptySet()
            return
        }
        if (search != null) {
            exitSearch()
            return
        }
        if (history.isNotEmpty()) go(history.removeAt(history.size - 1), replace = true)
    }

    // ---------------------------------------------------------------- действия над файлами

    fun deletePaths(paths: List<String>, permanent: Boolean = false) {
        viewModelScope.launch {
            val toTrash = settings.useTrash && !permanent
            val what = if (paths.size == 1) "«${basename(paths[0])}»" else "выбранное (${paths.size})"
            val ok = if (!settings.confirmDelete && toTrash) {
                true
            } else {
                confirm(
                    title = if (toTrash) "Переместить $what в корзину?" else "Удалить $what навсегда?",
                    text = if (toTrash) {
                        "Файлы хранятся в корзине ${settings.trashDays} дн., их можно восстановить."
                    } else {
                        "Файлы будут удалены без возможности восстановления."
                    },
                    okText = if (toTrash) "В корзину" else "Удалить",
                    danger = !toTrash,
                )
            }
            if (!ok) return@launch
            try {
                val res = withProgress(if (toTrash) "Переносим в корзину…" else "Удаляем…") {
                    if (toTrash) {
                        val r = trashSvc.move(paths)
                        OpResult(r.done, r.failed)
                    } else {
                        withContext(Dispatchers.IO) { Fs.remove(paths) }
                    }
                }
                selection = emptySet()
                clip?.let { c ->
                    val left = c.paths.filter { it !in paths }
                    clip = if (left.isEmpty()) null else c.copy(paths = left)
                }
                report(res, if (toTrash) "В корзине" else "Удалено")
                if (toTrash) refreshTrash()
            } catch (e: Exception) {
                toast(e.message ?: "Не удалось удалить", true)
            }
            search = search?.let { s -> s.copy(results = s.results.filter { it.path !in paths }) }
            refresh()
        }
    }

    fun renameEntry(e: FileEntry) {
        viewModelScope.launch {
            val name = prompt("Переименовать", value = e.name, okText = "Переименовать", selectBase = !e.dir)
            if (name == null || name.trim().isEmpty() || name == e.name) return@launch
            try {
                withContext(Dispatchers.IO) { Fs.rename(e.path, name) }
                selection = emptySet()
                toast("Переименовано")
            } catch (ex: Exception) {
                toast(ex.message ?: "Не удалось переименовать", true)
            }
            refresh()
        }
    }

    fun createItem(isDir: Boolean) {
        val base = path ?: return
        viewModelScope.launch {
            val name = prompt(
                title = if (isDir) "Новая папка" else "Новый файл",
                value = if (isDir) "" else "Новый файл.txt",
                placeholder = if (isDir) "Название папки" else "заметка.txt",
                okText = "Создать",
                selectBase = !isDir,
            )
            if (name == null || name.trim().isEmpty()) return@launch
            if (name.contains("/")) {
                toast("В имени нельзя использовать «/»", true)
                return@launch
            }
            try {
                val target = dev.filedeck.app.joinPath(base, name.trim())
                withContext(Dispatchers.IO) { if (isDir) Fs.mkdir(target) else Fs.createFile(target) }
                toast(if (isDir) "Папка создана" else "Файл создан")
            } catch (ex: Exception) {
                toast(ex.message ?: "Не удалось создать", true)
            }
            refresh()
        }
    }

    fun shareSelected() {
        val paths = selectedEntries().filter { !it.dir }.map { it.path }
        if (paths.isEmpty()) {
            toast("Поделиться можно только файлами, не папками", true)
            return
        }
        try {
            Fs.share(app, paths)
        } catch (e: Exception) {
            toast(e.message ?: "Не удалось поделиться", true)
        }
    }

    fun sharePath(p: String) {
        try {
            Fs.share(app, listOf(p))
        } catch (e: Exception) {
            toast(e.message ?: "Не удалось поделиться", true)
        }
    }

    fun showProps(e: FileEntry) {
        val kind = e.kind()
        val ext = dev.filedeck.app.extOf(e.name)
        val rows = mutableListOf(
            "Тип" to (kind.label + if (e.dir || ext.isEmpty()) "" else " (.$ext)"),
            "Путь" to parentOf(e.path),
            "Размер" to if (e.dir) "считаем…" else "${formatSize(e.size)} (${"%,d".format(e.size).replace(',', ' ')} Б)",
        )
        if (e.dir) rows.add("Содержимое" to "считаем…")
        rows.add("Изменён" to dev.filedeck.app.formatDate(e.mtime, true))
        val st = InfoState(e.name, rows)
        dialog = Dlg.Info(st)
        if (e.dir) {
            viewModelScope.launch {
                try {
                    val r = withContext(Dispatchers.IO) { Fs.size(e.path) }
                    st.set("Размер", formatSize(r.size))
                    st.set(
                        "Содержимое",
                        "${r.files} ${dev.filedeck.app.filesWord(r.files.toInt())}, " +
                            "${r.folders} ${dev.filedeck.app.plural(r.folders, "папка", "папки", "папок")}",
                    )
                } catch (ex: Exception) {
                    st.set("Размер", "не удалось посчитать")
                    st.set("Содержимое", "—")
                }
            }
        }
    }

    fun fabMenu() {
        sheet(
            "Создать",
            null,
            listOf(
                SheetItem(Icons2.folderPlus, "Новая папка") { createItem(true) },
                SheetItem(Icons2.filePlus, "Новый текстовый файл") { createItem(false) },
            ),
        )
    }
}
