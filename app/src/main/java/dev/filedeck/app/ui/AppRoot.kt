package dev.filedeck.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import dev.filedeck.app.Settings

@Composable
fun AppRoot(vm: AppViewModel) {
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        vm.toasts.collect { t ->
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showSnackbar(t.text, duration = SnackbarDuration.Short)
        }
    }
    BackHandler(enabled = true) { vm.onBack() }
    Box(Modifier.fillMaxSize().background(fd.surface)) {
        MainScreen(vm)
        val top = vm.overlays.lastOrNull()
        if (top != null) {
            Box(Modifier.fillMaxSize().background(fd.surface).pointerInput(Unit) {}) {
                when (top) {
                    Overlay.Settings -> SettingsScreen(vm)
                    is Overlay.Cleaner -> CleanerScreen(top.c)
                    is Overlay.Trash -> TrashScreen(top.c)
                    is Overlay.ArchiveView -> ArchiveScreen(top.c)
                    is Overlay.Image -> ImageScreen(vm, top)
                    is Overlay.Editor -> EditorScreen(top.c)
                }
            }
        }
        SnackbarHost(
            snackbar, modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 72.dp),
        )
        DialogHost(vm)
    }
}
