package dev.filedeck.app.ui

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewModelScope
import dev.filedeck.app.CleanCat
import dev.filedeck.app.CleanSelection
import dev.filedeck.app.CleanerLogic
import dev.filedeck.app.CleanerOptions
import dev.filedeck.app.FileKind
import dev.filedeck.app.Fs
import dev.filedeck.app.R
import dev.filedeck.app.ScanConfig
import dev.filedeck.app.ScanProgress
import dev.filedeck.app.ScanResult
import dev.filedeck.app.Str
import dev.filedeck.app.basename
import dev.filedeck.app.formatSize
import dev.filedeck.app.kindOf
import dev.filedeck.app.meta
import dev.filedeck.app.parentOf
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class CleanView { Setup, Scanning, Summary, Detail }

class CleanerController(val vm: AppViewModel) {
    var view by mutableStateOf(CleanView.Setup)
    var cat by mutableStateOf(CleanCat.Duplicates)
    var result by mutableStateOf<ScanResult?>(null)
    var sel by mutableStateOf<CleanSelection>(emptyMap())
    var progress by mutableStateOf<ScanProgress?>(null)
    var busy by mutableStateOf(false)

    val opts get() = vm.settings.cleaner
    val primary get() = vm.roots.firstOrNull { it.primary } ?: vm.roots.firstOrNull()
    val secondary get() = vm.roots.firstOrNull { !it.primary }

    fun onBack(): Boolean {
        when (view) {
            CleanView.Detail -> view = CleanView.Summary
            CleanView.Scanning -> vm.cleanerSvc.cancel()
            else -> return false
        }
        return true
    }

    fun start() {
        val roots = mutableListOf<String>()
        if (opts.primary) primary?.let { roots.add(it.path) }
        if (opts.sd) secondary?.let { roots.add(it.path) }
        if (roots.isEmpty()) return vm.toast(Str.s(R.string.s_vyberite_gde_iskat), true)
        val o = opts
        if (!(o.duplicates || o.similar || o.junk || o.apks || o.largest || o.emptyFolders || o.zeroFiles)) {
            return vm.toast(Str.s(R.string.s_vyberite_chto_iskat), true)
        }
        view = CleanView.Scanning
        progress = ScanProgress("walk", 0, 0, 0, 0, 0, null)
        vm.viewModelScope.launch {
            try {
                val cfg = ScanConfig(o.duplicates, o.similar, o.junk, o.apks, o.largest, o.emptyFolders, o.zeroFiles, o.appFolders)
                val res = vm.cleanerSvc.scan(roots, emptyList(), cfg) { progress = it }
                if (res == null) {
                    vm.toast(Str.s(R.string.s_skanirovanie_otmeneno))
                    view = CleanView.Setup
                } else {
                    result = res
                    sel = CleanerLogic.defaultSelection(res)
                    view = CleanView.Summary
                }
            } catch (e: Exception) {
                vm.toast(e.message ?: Str.s(R.string.s_ne_udalos_vypolnit_skanirovanie), true)
                view = CleanView.Setup
            }
        }
    }

    fun setOptions(change: (CleanerOptions) -> CleanerOptions) {
        vm.updateSettings { it.copy(cleaner = change(it.cleaner)) }
    }

    fun close() {
        vm.overlays.removeAll { it is Overlay.Cleaner && it.c === this }
    }

    fun toggleRow(path: String) {
        val cur = sel[cat] ?: emptySet()
        sel = sel + (cat to (if (path in cur) cur - path else cur + path))
    }

    fun toggleAll() {
        val rows = CleanerLogic.rowsOf(result, cat)
        val cur = sel[cat] ?: emptySet()
        sel = sel + (cat to (if (cur.size == rows.size) emptySet() else rows.map { it.path }.toSet()))
    }

    fun locate(path: String) {
        close()
        vm.go(parentOf(path))
    }

    fun view(path: String) {
        if (kindOf(basename(path), false) == FileKind.Image) {
            vm.overlays.add(Overlay.Image(listOf(Fs.describe(File(path), false)), 0))
        } else vm.openPathExternal(path)
    }

    private suspend fun removePaths(paths: List<String>, bytes: Long, label: String) {
        if (paths.isEmpty()) return
        val s = vm.settings
        val describe = meta("${paths.size} ${Str.p(R.plurals.word_item, paths.size)}", formatSize(bytes))
        val mode: String? = if (!s.useTrash) {
            if (vm.confirm(Str.s(R.string.s_udalit_navsegda_2), Str.f(R.string.s_vosstanovit_budet_nelzya, label, describe),
                Str.s(R.string.action_delete), true)) "permanent" else null
        } else {
            vm.choose(
                label, describe,
                listOf(
                    Choice("trash", Str.s(R.string.action_move_to_trash), Icons2.delete,
                        Str.f(R.string.cleaner_trash_hint, s.trashDays, Str.p(R.plurals.word_day, s.trashDays))),
                    Choice("permanent", Str.s(R.string.s_udalit_navsegda), Icons2.close,
                        Str.s(R.string.s_srazu_osvobodit_mesto_bez_vosstanovleniy), danger = true),
                ),
            )
        }
        if (mode == null) return
        busy = true
        try {
            val res = vm.withProgress(if (mode == "trash") Str.s(R.string.s_perenosim_v_korzinu) else Str.s(R.string.s_udalyaem)) {
                if (mode == "trash") {
                    val r = vm.trashSvc.move(paths)
                    r.done to r.failed
                } else {
                    val r = withContext(Dispatchers.IO) { Fs.remove(paths) }
                    r.done to r.failed
                }
            }
            val failed = res.second.size
            val failedSuffix = if (failed > 0) Str.f(R.string.failed_suffix, failed) else ""
            val ok = paths.filter { it !in res.second }
            result?.let { r ->
                val (nr, ns) = CleanerLogic.purge(r, sel, ok)
                result = nr
                sel = ns
            }
            vm.toast(
                if (mode == "trash") Str.f(R.string.cleaner_done_trash, res.first, failedSuffix)
                else Str.f(R.string.cleaner_done_deleted, res.first, failedSuffix, formatSize(bytes)),
                failed > 0,
            )
            vm.refreshTrash()
            vm.refresh()
        } catch (e: Exception) {
            vm.toast(e.message ?: Str.s(R.string.s_ne_udalos_udalit), true)
        }
        busy = false
    }

    fun deleteCategory() {
        vm.viewModelScope.launch {
            val rows = CleanerLogic.rowsOf(result, cat).filter { sel[cat]?.contains(it.path) == true }
            if (rows.isEmpty()) return@launch
            if (cat.group) {
                var full = 0
                for (list in CleanerLogic.groupRows(CleanerLogic.rowsOf(result, cat)).values) {
                    if (list.all { sel[cat]?.contains(it.path) == true }) full++
                }
                if (full > 0) {
                    val ok = vm.confirm(
                        Str.s(R.string.s_vybrany_vse_kopii),
                        Str.f(R.string.cleaner_all_copies, full, Str.p(R.plurals.word_group_in, full)),
                        Str.s(R.string.s_prodolzhit),
                    )
                    if (!ok) return@launch
                }
            }
            removePaths(CleanerLogic.topLevel(rows.map { it.path }), rows.sumOf { it.size }, cat.title)
        }
    }

    fun quickClean() {
        vm.viewModelScope.launch {
            val paths = CleanerLogic.safePaths(result, sel)
            if (paths.isEmpty()) return@launch vm.toast(Str.s(R.string.s_bezopasnoy_ochistki_ne_nashlos))
            removePaths(paths, CleanerLogic.safeBytes(result, sel), Str.s(R.string.s_bystraya_ochistka))
        }
    }
}
