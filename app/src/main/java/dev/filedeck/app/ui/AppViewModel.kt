package dev.filedeck.app.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.Settings as AndroidSettings
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.filedeck.app.ArchiveService
import dev.filedeck.app.CleanerService
import dev.filedeck.app.FileEntry
import dev.filedeck.app.FileKind
import dev.filedeck.app.Fs
import dev.filedeck.app.R
import dev.filedeck.app.SearchState
import dev.filedeck.app.Settings
import dev.filedeck.app.SettingsStore
import dev.filedeck.app.StartScreen
import dev.filedeck.app.StorageRoot
import dev.filedeck.app.Str
import dev.filedeck.app.TrashService
import dev.filedeck.app.formatSize
import dev.filedeck.app.isArchive
import dev.filedeck.app.isEditable
import dev.filedeck.app.kind
import dev.filedeck.app.meta
import dev.filedeck.app.parentOf
import dev.filedeck.app.sortEntries
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class ClipMode { COPY, MOVE }

data class Clip(val mode: ClipMode, val paths: List<String>)

sealed interface Overlay {
    data object Settings : Overlay
    class Cleaner(val c: CleanerController) : Overlay
    class Trash(val c: TrashController) : Overlay
    class ArchiveView(val c: ArchiveBrowserController) : Overlay
    class Image(val images: List<FileEntry>, val start: Int) : Overlay
    class Editor(val c: EditorController) : Overlay
}

class AppViewModel(private val app: Application) : AndroidViewModel(app), UiHostApi by UiHost() {

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

    var perm by mutableStateOf<Boolean?>(null)
        private set
    var roots by mutableStateOf<List<StorageRoot>>(emptyList())
        private set

    var path by mutableStateOf<String?>(null)
        private set
    var entries by mutableStateOf<List<FileEntry>>(emptyList())
        private set
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var selection by mutableStateOf<Set<String>>(emptySet())
        internal set
    var clip by mutableStateOf<Clip?>(null)
        internal set
    var search by mutableStateOf<SearchState?>(null)
        internal set
    var trashCount by mutableStateOf(0)
        private set
    var trashBytes by mutableStateOf(0L)
        private set

    val overlays = mutableStateListOf<Overlay>()

    val scrollMemo = HashMap<String, Int>()

    private val history = ArrayList<String?>()
    private val lastIn = HashMap<String, String>()
    private var started = false
    private var loadJob: Job? = null
    private var searchJob: Job? = null

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

    private fun hasAccess(): Boolean = Environment.isExternalStorageManager()

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
                    }
                    refreshTrash()
                }
                val first = when (settings.start) {
                    StartScreen.PRIMARY -> primaryRoot()
                    StartScreen.SD -> roots.firstOrNull { !it.primary }
                    StartScreen.HOME -> null
                }
                if (first != null && path == null && !started) {
                    started = true
                    go(first.path, replace = true)
                    return@launch
                }
                started = true
            }
        }
    }

    fun requestPermission() {
        val flags = Intent.FLAG_ACTIVITY_NEW_TASK
        try {
            app.startActivity(
                Intent(AndroidSettings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:" + app.packageName))
                    .addFlags(flags),
            )
        } catch (e: Exception) {
            try {
                app.startActivity(Intent(AndroidSettings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION).addFlags(flags))
            } catch (e2: Exception) {
                toast(Str.s(R.string.s_ne_udalos_otkryt_nastroyki_dostupa), true)
            }
        }
    }

    fun onResume() {
        if (perm != true || !hasAccess()) {
            checkPermission()
            return
        }
        viewModelScope.launch { loadRoots() }
        if (path == null) refreshTrash() else refresh()
    }

    suspend fun loadRoots() {
        try {
            roots = withContext(Dispatchers.IO) { Fs.roots(app) }
        } catch (e: Exception) {
            toast(e.message ?: Str.s(R.string.s_ne_udalos_poluchit_spisok_nakopiteley), true)
        }
    }

    fun refreshTrash() {
        viewModelScope.launch {
            try {
                val r = trashSvc.list()
                trashCount = r.count
                trashBytes = r.bytes
            } catch (e: Exception) {
            }
        }
    }

    fun trashLabel(): String =
        if (trashCount > 0) meta(trashCount.toString(), formatSize(trashBytes)) else Str.s(R.string.s_pusto)

    private fun trackHistory(target: String?) {
        val top = history.lastOrNull()
        if (history.isNotEmpty() && top == target) {
            history.removeAt(history.size - 1)
        } else if (path != target) {
            history.add(path)
            if (history.size > 100) history.removeAt(0)
        }
    }

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
                if (path != target) return@launch
                entries = list
                if (search == null) {
                    val present = list.mapTo(HashSet()) { it.path }
                    selection = selection.filterTo(HashSet()) { it in present }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (path != target) return@launch
                entries = emptyList()
                error = e.message ?: Str.s(R.string.s_ne_udalos_otkryt_papku)
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
            toast(Str.s(R.string.s_snachala_vydayte_dostup_k_faylam), true)
            return
        }
        val root = if (sd) roots.firstOrNull { !it.primary } else primaryRoot()
        if (root == null) {
            toast(Str.s(R.string.s_sd_karta_ne_naydena_vstavte), true)
            return
        }
        val cur = path?.let { rootOf(it) }
        if (cur != null && cur.path == root.path && path != root.path && search == null) {
            go(root.path)
            return
        }
        go(lastIn[root.path] ?: root.path)
    }

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
                val res = withContext(Dispatchers.IO) { Fs.search(base, q.trim(), 400) { !isActive } }
                search = search?.copy(results = res.entries, truncated = res.truncated, loading = false)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                toast(e.message ?: Str.s(R.string.s_poisk_ne_udalsya), true)
                search = search?.copy(loading = false)
            }
        }
    }

    fun openExternal(e: FileEntry) {
        try {
            Fs.open(app, e.path)
        } catch (ex: Exception) {
            toast(ex.message ?: Str.s(R.string.s_ne_udalos_otkryt_fayl), true)
        }
    }

    fun openPathExternal(path: String) {
        try {
            Fs.open(app, path)
        } catch (ex: Exception) {
            toast(ex.message ?: Str.s(R.string.s_ne_udalos_otkryt_fayl), true)
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
                    SheetItem(Icons2.edit, Str.s(R.string.s_otkryt_v_redaktore)) { openEditor(e) },
                    SheetItem(Icons2.external, Str.s(R.string.s_otkryt_v_prilozhenii)) { openExternal(e) },
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
}
