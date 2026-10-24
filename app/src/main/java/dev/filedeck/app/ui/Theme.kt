package dev.filedeck.app.ui

import androidx.compose.foundation.background
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import dev.filedeck.app.FileKind
import dev.filedeck.app.ThemeMode

@Immutable
data class FdColors(
    val chrome: Color,
    val surface: Color,
    val surface2: Color,
    val surface3: Color,
    val line: Color,
    val text: Color,
    val muted: Color,
    val manila: Color,
    val onManila: Color,
    val danger: Color,
    val dark: Boolean,
    private val kinds: Map<FileKind, Color>,
) {
    fun kind(k: FileKind): Color = kinds[k] ?: muted
}

private val DarkFd = FdColors(
    chrome = Color(0xFF000000), surface = Color(0xFF000000), surface2 = Color(0xFF101012), surface3 = Color(0xFF1B1B1F),
    line = Color(0xFF26262B), text = Color(0xFFECECEF), muted = Color(0xFF8F8F99), manila = Color(0xFFF0B84A),
    onManila = Color(0xFF241A05), danger = Color(0xFFFF6F61), dark = true,
    kinds = mapOf(
        FileKind.Image to Color(0xFFEC75AC), FileKind.Video to Color(0xFFA58AFF), FileKind.Audio to Color(0xFFFF9D5C),
        FileKind.Archive to Color(0xFF6FD3C0), FileKind.Pdf to Color(0xFFFF7366), FileKind.Apk to Color(0xFF94D96E),
        FileKind.Doc to Color(0xFF62ADFF), FileKind.Code to Color(0xFF6FE3AB), FileKind.Text to Color(0xFFB9C2CE),
        FileKind.File to Color(0xFF8F8F99), FileKind.Folder to Color(0xFFF0B84A),
    ),
)

private val LightFd = FdColors(
    chrome = Color(0xFFD8C3A6), surface = Color(0xFFF4EBDD), surface2 = Color(0xFFEBDFCB), surface3 = Color(0xFFDFD0B6),
    line = Color(0xFFD2C0A2), text = Color(0xFF2B2016), muted = Color(0xFF75624D), manila = Color(0xFFEAA93A),
    onManila = Color(0xFF2A1C05), danger = Color(0xFFC9402F), dark = false,
    kinds = mapOf(
        FileKind.Image to Color(0xFFC4467F), FileKind.Video to Color(0xFF7656E0), FileKind.Audio to Color(0xFFD9661A),
        FileKind.Archive to Color(0xFF1F968A), FileKind.Pdf to Color(0xFFD1432F), FileKind.Apk to Color(0xFF4F9A25),
        FileKind.Doc to Color(0xFF2A79D6), FileKind.Code to Color(0xFF1A9A62), FileKind.Text to Color(0xFF6F6254),
        FileKind.File to Color(0xFF75624D), FileKind.Folder to Color(0xFFEAA93A),
    ),
)

val LocalFd = staticCompositionLocalOf { DarkFd }

val fd: FdColors
    @Composable get() = LocalFd.current

@Composable
fun FileDeckTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.AUTO -> androidx.compose.foundation.isSystemInDarkTheme()
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }
    val c = if (dark) DarkFd else LightFd
    val scheme = if (dark) {
        darkColorScheme(
            primary = c.manila, onPrimary = c.onManila, background = c.surface, onBackground = c.text,
            surface = c.surface, onSurface = c.text, surfaceVariant = c.surface2, onSurfaceVariant = c.muted,
            outline = c.line, outlineVariant = c.line, error = c.danger,
            surfaceContainer = c.surface2, surfaceContainerHigh = c.surface3, surfaceContainerHighest = c.surface3,
            surfaceContainerLow = c.surface2, surfaceContainerLowest = c.surface,
        )
    } else {
        lightColorScheme(
            primary = c.manila, onPrimary = c.onManila, background = c.surface, onBackground = c.text,
            surface = c.surface, onSurface = c.text, surfaceVariant = c.surface2, onSurfaceVariant = c.muted,
            outline = c.line, outlineVariant = c.line, error = c.danger,
            surfaceContainer = c.surface2, surfaceContainerHigh = c.surface3, surfaceContainerHighest = c.surface3,
            surfaceContainerLow = c.surface2, surfaceContainerLowest = c.surface,
        )
    }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? android.app.Activity)?.window
            if (window != null) {
                val ctl = WindowCompat.getInsetsController(window, view)
                ctl.isAppearanceLightStatusBars = !dark
                ctl.isAppearanceLightNavigationBars = !dark
            }
        }
    }
    CompositionLocalProvider(LocalFd provides c) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
