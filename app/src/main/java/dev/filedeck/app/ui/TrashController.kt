package dev.filedeck.app.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewModelScope
import dev.filedeck.app.R
import dev.filedeck.app.Str
import dev.filedeck.app.TrashItem
import dev.filedeck.app.TrashProgress
import dev.filedeck.app.formatSize
import kotlinx.coroutines.launch

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
            vm.toast(e.message ?: Str.s(R.string.s_ne_udalos_prochitat_korzinu), true)
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
                val res = vm.withProgress(Str.s(R.string.s_vosstanavlivaem), { vm.trashSvc.cancel() }) { update ->
                    vm.trashSvc.restore(selected) { update(it.fraction, it.current) }
                }
                val failed = res.failed.size
                vm.toast(
                    (if (res.cancelled) Str.s(R.string.s_otmeneno_2) else "") + Str.f(R.string.s_vosstanovleno, res.done) +
                        (if (failed > 0) Str.f(R.string.failed_suffix, failed) else ""),
                    failed > 0,
                )
                selected = emptySet()
                vm.refresh()
            } catch (e: Exception) {
                vm.toast(e.message ?: Str.s(R.string.s_ne_udalos_vosstanovit), true)
            }
            busy = false
            load()
        }
    }

    fun delete(all: Boolean) {
        vm.viewModelScope.launch {
            val ok = vm.confirm(
                if (all) Str.s(R.string.s_ochistit_korzinu_2) else Str.f(R.string.s_udalit_navsegda_4, selected.size),
                Str.s(R.string.s_fayly_budut_udaleny_bez_vozmozhnosti),
                if (all) Str.s(R.string.s_ochistit) else Str.s(R.string.action_delete),
                danger = true,
            )
            if (!ok || busy) return@launch
            busy = true
            val expected = if (all) items.size else selected.size
            try {
                val res = vm.withProgress(if (all) Str.s(R.string.s_ochischaem_korzinu) else Str.s(R.string.s_udalyaem), { vm.trashSvc.cancel() }) { update ->
                    val cb: (TrashProgress) -> Unit = { update(it.fraction, it.current) }
                    if (all) vm.trashSvc.empty(cb) else vm.trashSvc.remove(selected, cb)
                }
                val failed = if (res.cancelled) 0 else maxOf(0, expected - res.done)
                val freed = Str.f(R.string.freed, formatSize(res.bytes))
                when {
                    res.cancelled -> vm.toast(Str.f(R.string.s_otmeneno_udaleno, res.done, freed))
                    failed > 0 -> vm.toast(Str.f(R.string.s_udaleno_ne_udalos, res.done, failed), true)
                    else -> vm.toast(Str.f(R.string.s_udaleno_2, res.done, freed))
                }
                selected = emptySet()
            } catch (e: Exception) {
                vm.toast(e.message ?: Str.s(R.string.s_ne_udalos_udalit), true)
            }
            busy = false
            load()
        }
    }
}
