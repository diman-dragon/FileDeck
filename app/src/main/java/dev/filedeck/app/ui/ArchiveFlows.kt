package dev.filedeck.app.ui

import androidx.lifecycle.viewModelScope
import dev.filedeck.app.ArchiveExtracted
import dev.filedeck.app.FileEntry
import dev.filedeck.app.Outcome
import dev.filedeck.app.R
import dev.filedeck.app.Str
import dev.filedeck.app.archiveBase
import dev.filedeck.app.basename
import dev.filedeck.app.explainArchiveError
import dev.filedeck.app.formatSize
import dev.filedeck.app.isArchive
import dev.filedeck.app.isEditable
import dev.filedeck.app.parentOf
import kotlinx.coroutines.launch

internal fun isPasswordError(code: String) = code == "PASSWORD_REQUIRED" || code == "WRONG_PASSWORD"

suspend fun AppViewModel.extractFlow(
    path: String,
    dest: String,
    mode: String,
    only: Set<String> = emptySet(),
    password: String = "",
): Pair<ArchiveExtracted, String>? {
    var pw = password
    while (true) {
        val res = withProgress(Str.s(R.string.s_raspakovyvaem), { archives.cancel() }) { update ->
            archives.extract(path, dest, pw, mode, only) { update(it.fraction, it.current) }
        }
        when (res) {
            is Outcome.Ok -> {
                toast(Str.f(R.string.s_raspakovano, res.value.count, Str.p(R.plurals.word_file, res.value.count.toInt())))
                return res.value to pw
            }
            Outcome.Aborted -> {
                toast(Str.s(R.string.s_otmeneno))
                return null
            }
            is Outcome.Err -> {
                if (isPasswordError(res.code)) {
                    val typed = prompt(
                        Str.s(R.string.s_parol_arhiva), text = basename(path), password = true, okText = Str.s(R.string.s_prodolzhit),
                        error = if (res.code == "WRONG_PASSWORD") Str.s(R.string.s_nevernyy_parol_poprobuyte_esche_raz) else "",
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
    } else Str.s(R.string.kind_archive)
    val opts = archiveOptions(defaultName, entries.size) ?: return false
    val res = withProgress(Str.s(R.string.s_sozdaem_arhiv), { archives.cancel() }) { update ->
        archives.create(entries.map { it.path }, destDir, opts.name, opts.format, opts.password, opts.level) {
            update(it.fraction, it.current)
        }
    }
    return when (res) {
        is Outcome.Ok -> {
            toast(Str.f(R.string.s_arhiv_sozdan, basename(res.value.path), formatSize(res.value.size)))
            true
        }
        Outcome.Aborted -> {
            toast(Str.s(R.string.s_otmeneno))
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
            Icons2.layers, Str.s(R.string.s_prosmotret_soderzhimoe),
            if (rar) Str.s(R.string.s_dlya_rar_spisok_nedostupen_raspakovka) else Str.s(R.string.s_vybrat_chto_raspakovat),
        ) { overlays.add(Overlay.ArchiveView(ArchiveBrowserController(this, e))) },
        SheetItem(Icons2.unzip, Str.f(R.string.s_raspakovat_v_papku, archiveBase(e.name))) {
            viewModelScope.launch { if (extractFlow(e.path, dest, "folder") != null) refresh() }
        },
    )
    if (!rar) {
        items.add(
            SheetItem(Icons2.download, Str.s(R.string.s_raspakovat_syuda), Str.s(R.string.s_fayly_okazhutsya_ryadom_s_arhivom)) {
                viewModelScope.launch { if (extractFlow(e.path, dest, "here") != null) refresh() }
            },
        )
    }
    items.add(SheetItem(Icons2.external, Str.s(R.string.s_otkryt_v_drugom_prilozhenii)) { openExternal(e) })
    sheet(e.name, formatSize(e.size), items)
}

fun AppViewModel.moreForSelection() {
    val sel = selectedEntries()
    val one = if (sel.size == 1) sel[0] else null
    val items = mutableListOf<SheetItem>()
    if (one != null) items.add(SheetItem(Icons2.edit, Str.s(R.string.s_pereimenovat)) { renameEntry(one) })
    items.add(SheetItem(Icons2.archive, Str.s(R.string.s_dobavit_v_arhiv), Str.s(R.string.s_zip_7z_tar_parol_uroven)) { zipSelected() })
    if (one != null && isArchive(one.name)) {
        items.add(SheetItem(Icons2.unzip, Str.s(R.string.s_raspakovat), Str.s(R.string.s_prosmotr_soderzhimogo_i_vybor_faylov)) {
            clearSelection()
            openArchiveSheet(one)
        })
    }
    if (one != null && !one.dir) items.add(SheetItem(Icons2.external, Str.s(R.string.s_otkryt_v_prilozhenii)) { openExternal(one) })
    if (one != null && one.isEditable()) items.add(SheetItem(Icons2.file, Str.s(R.string.s_otkryt_v_redaktore)) { openEditor(one) })
    if (one != null) items.add(SheetItem(Icons2.info, Str.s(R.string.s_svoystva)) { showProps(one) })
    if (settings.useTrash) {
        items.add(SheetItem(divider = true))
        items.add(SheetItem(Icons2.delete, Str.s(R.string.s_udalit_navsegda),
            danger = true) { deletePaths(sel.map { it.path }, permanent = true) })
    }
    sheet(if (one != null) one.name else Str.f(R.string.s_vybrano, sel.size), null, items)
}
