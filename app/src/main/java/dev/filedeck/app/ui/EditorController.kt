package dev.filedeck.app.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewModelScope
import dev.filedeck.app.FileEntry
import dev.filedeck.app.Fs
import dev.filedeck.app.R
import dev.filedeck.app.Str
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
                vm.toast(e.message ?: Str.s(R.string.s_ne_udalos_prochitat_fayl), true)
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
                vm.toast(Str.s(R.string.s_sohraneno))
                vm.refresh()
            } catch (e: Exception) {
                vm.toast(e.message ?: Str.s(R.string.s_ne_udalos_sohranit), true)
            }
        }
    }

    fun onBack(): Boolean {
        if (!dirty) return false
        vm.viewModelScope.launch {
            val discard = vm.confirm(Str.s(R.string.s_zakryt_bez_sohraneniya), Str.s(R.string.s_izmeneniya_v_fayle_budut_poteryany),
                Str.s(R.string.action_close), danger = true)
            if (discard) close()
        }
        return true
    }
}
