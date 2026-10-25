package dev.filedeck.app.ui

import android.app.Application
import androidx.lifecycle.viewModelScope
import dev.filedeck.app.FileEntry
import dev.filedeck.app.Fs
import dev.filedeck.app.OpResult
import dev.filedeck.app.R
import dev.filedeck.app.Str
import dev.filedeck.app.basename
import dev.filedeck.app.extOf
import dev.filedeck.app.formatDate
import dev.filedeck.app.formatSize
import dev.filedeck.app.joinPath
import dev.filedeck.app.kind
import dev.filedeck.app.parentOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

fun AppViewModel.toClipboard(mode: ClipMode) {
    val paths = selection.toList()
    clip = Clip(mode, paths)
    selection = emptySet()
    toast(
        (if (mode == ClipMode.MOVE) Str.s(R.string.s_vyrezano) else Str.s(R.string.action_copied)) +
            Str.f(R.string.s_otkroyte_nuzhnuyu_papku_i_nazhmite, paths.size),
    )
}

fun AppViewModel.cancelPaste() {
    clip = null
}

fun AppViewModel.paste() {
    val c = clip ?: return
    val dest = path ?: return
    viewModelScope.launch {
        val move = c.mode == ClipMode.MOVE
        try {
            val res = withProgress(if (move) Str.s(R.string.s_perenosim) else Str.s(R.string.s_kopiruem)) {
                withContext(Dispatchers.IO) { Fs.transfer(c.paths, dest, move) }
            }
            clip = null
            report(res, if (move) Str.s(R.string.s_pereneseno) else Str.s(R.string.action_copied))
        } catch (e: Exception) {
            toast(e.message ?: Str.s(R.string.error_operation_failed), true)
        }
        refresh()
    }
}

fun AppViewModel.report(res: OpResult, verb: String) {
    val failed = res.failed.size
    if (failed > 0) {
        toast(Str.f(R.string.s_ne_udalos_nelzya_vstavlyat_papku, verb, res.done, failed), true)
    } else {
        toast("$verb: ${res.done}")
    }
}

fun AppViewModel.deletePaths(paths: List<String>, permanent: Boolean = false) {
    viewModelScope.launch {
        val toTrash = settings.useTrash && !permanent
        val what = if (paths.size == 1) Str.f(R.string.quoted_name, basename(paths[0])) else Str.f(R.string.s_vybrannoe, paths.size)
        val ok = if (!settings.confirmDelete && toTrash) {
            true
        } else {
            confirm(
                title = if (toTrash) Str.f(R.string.s_peremestit_v_korzinu, what) else Str.f(R.string.s_udalit_navsegda_3, what),
                text = if (toTrash) {
                    Str.f(R.string.s_fayly_hranyatsya_v_korzine_dn, settings.trashDays)
                } else {
                    Str.s(R.string.s_fayly_budut_udaleny_bez_vozmozhnosti)
                },
                okText = if (toTrash) Str.s(R.string.s_v_korzinu) else Str.s(R.string.action_delete),
                danger = !toTrash,
            )
        }
        if (!ok) return@launch
        try {
            val res = withProgress(if (toTrash) Str.s(R.string.s_perenosim_v_korzinu) else Str.s(R.string.s_udalyaem)) {
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
            report(res, if (toTrash) Str.s(R.string.s_v_korzine) else Str.s(R.string.s_udaleno))
            if (toTrash) refreshTrash()
        } catch (e: Exception) {
            toast(e.message ?: Str.s(R.string.s_ne_udalos_udalit), true)
        }
        search = search?.let { s -> s.copy(results = s.results.filter { it.path !in paths }) }
        refresh()
    }
}

fun AppViewModel.renameEntry(e: FileEntry) {
    viewModelScope.launch {
        val name = prompt(Str.s(R.string.s_pereimenovat), value = e.name, okText = Str.s(R.string.s_pereimenovat), selectBase = !e.dir)
        if (name == null || name.trim().isEmpty() || name == e.name) return@launch
        try {
            withContext(Dispatchers.IO) { Fs.rename(e.path, name) }
            selection = emptySet()
            toast(Str.s(R.string.s_pereimenovano))
        } catch (ex: Exception) {
            toast(ex.message ?: Str.s(R.string.error_rename), true)
        }
        refresh()
    }
}

fun AppViewModel.createItem(isDir: Boolean) {
    val base = path ?: return
    viewModelScope.launch {
        val name = prompt(
            title = if (isDir) Str.s(R.string.s_novaya_papka) else Str.s(R.string.s_novyy_fayl),
            value = if (isDir) "" else Str.s(R.string.s_novyy_fayl_txt),
            placeholder = if (isDir) Str.s(R.string.s_nazvanie_papki) else Str.s(R.string.s_zametka_txt),
            okText = Str.s(R.string.s_sozdat),
            selectBase = !isDir,
        )
        if (name == null || name.trim().isEmpty()) return@launch
        if (name.contains("/")) {
            toast(Str.s(R.string.s_v_imeni_nelzya_ispolzovat), true)
            return@launch
        }
        try {
            val target = joinPath(base, name.trim())
            withContext(Dispatchers.IO) { if (isDir) Fs.mkdir(target) else Fs.createFile(target) }
            toast(if (isDir) Str.s(R.string.s_papka_sozdana) else Str.s(R.string.s_fayl_sozdan))
        } catch (ex: Exception) {
            toast(ex.message ?: Str.s(R.string.s_ne_udalos_sozdat), true)
        }
        refresh()
    }
}

fun AppViewModel.shareSelected() {
    val paths = selectedEntries().filter { !it.dir }.map { it.path }
    if (paths.isEmpty()) {
        toast(Str.s(R.string.error_share_folders), true)
        return
    }
    try {
        Fs.share(getApplication<Application>(), paths)
    } catch (e: Exception) {
        toast(e.message ?: Str.s(R.string.s_ne_udalos_podelitsya), true)
    }
}

fun AppViewModel.sharePath(p: String) {
    try {
        Fs.share(getApplication<Application>(), listOf(p))
    } catch (e: Exception) {
        toast(e.message ?: Str.s(R.string.s_ne_udalos_podelitsya), true)
    }
}

fun AppViewModel.showProps(e: FileEntry) {
    val kind = e.kind()
    val ext = extOf(e.name)
    val rows = mutableListOf(
        Str.s(R.string.s_tip) to (kind.label + if (e.dir || ext.isEmpty()) "" else " (.$ext)"),
        Str.s(R.string.s_put) to parentOf(e.path),
        Str.s(R.string.s_razmer) to if (e.dir) Str.s(R.string.s_schitaem) else Str.f(R.string.size_exact, formatSize(e.size),
            "%,d".format(e.size)),
    )
    if (e.dir) rows.add(Str.s(R.string.s_soderzhimoe) to Str.s(R.string.s_schitaem))
    rows.add(Str.s(R.string.s_izmenen) to formatDate(e.mtime, true))
    val st = InfoState(e.name, rows)
    dialog = Dlg.Info(st)
    if (e.dir) {
        viewModelScope.launch {
            try {
                val r = withContext(Dispatchers.IO) { Fs.size(e.path) }
                st.set(Str.s(R.string.s_razmer), formatSize(r.size))
                st.set(
                    Str.s(R.string.s_soderzhimoe),
                    Str.f(R.string.props_contents, r.files, Str.p(R.plurals.word_file, r.files), r.folders,
                        Str.p(R.plurals.word_folder, r.folders)),
                )
            } catch (ex: Exception) {
                st.set(Str.s(R.string.s_razmer), Str.s(R.string.s_ne_udalos_poschitat))
                st.set(Str.s(R.string.s_soderzhimoe), "—")
            }
        }
    }
}

fun AppViewModel.fabMenu() {
    sheet(
        Str.s(R.string.s_sozdat),
        null,
        listOf(
            SheetItem(Icons2.folderPlus, Str.s(R.string.s_novaya_papka)) { createItem(true) },
            SheetItem(Icons2.filePlus, Str.s(R.string.s_novyy_tekstovyy_fayl)) { createItem(false) },
        ),
    )
}
