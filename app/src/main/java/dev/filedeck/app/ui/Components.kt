package dev.filedeck.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import dev.filedeck.app.FileKind
import dev.filedeck.app.ThemeMode

// ======================================================================== иконки

object Icons2 {
    val back = Icons.AutoMirrored.Filled.ArrowBack
    val check = Icons.Filled.Check
    val folderFilled = Icons.Filled.Folder
    val folder = Icons.Outlined.Folder
    val file = Icons.Outlined.Description
    val image = Icons.Outlined.Image
    val video = Icons.Outlined.Movie
    val audio = Icons.Outlined.MusicNote
    val archive = Icons.Outlined.FolderZip
    val pdf = Icons.Outlined.PictureAsPdf
    val apk = Icons.Outlined.Android
    val code = Icons.Outlined.Code
    val search = Icons.Outlined.Search
    val settings = Icons.Outlined.Settings
    val close = Icons.Outlined.Close
    val selectAll = Icons.Outlined.SelectAll
    val copy = Icons.Outlined.ContentCopy
    val cut = Icons.Outlined.ContentCut
    val share = Icons.Outlined.Share
    val delete = Icons.Outlined.Delete
    val more = Icons.Outlined.MoreVert
    val add = Icons.Outlined.Add
    val folderPlus = Icons.Outlined.CreateNewFolder
    val filePlus = Icons.Outlined.NoteAdd
    val edit = Icons.Outlined.Edit
    val info = Icons.Outlined.Info
    val external = Icons.AutoMirrored.Outlined.OpenInNew
    val grid = Icons.Outlined.GridView
    val list = Icons.Outlined.ViewAgenda
    val drive = Icons.Outlined.Storage
    val sd = Icons.Outlined.SdCard
    val sparkle = Icons.Outlined.AutoAwesome
    val download = Icons.Outlined.Download
    val camera = Icons.Outlined.CameraAlt
    val eye = Icons.Outlined.Visibility
    val eyeOff = Icons.Outlined.VisibilityOff
    val unzip = Icons.Outlined.Unarchive
    val layers = Icons.Outlined.Layers
    val restore = Icons.Outlined.Restore
    val chevron = Icons.Outlined.ChevronRight
    val lock = Icons.Outlined.Lock
    val refresh = Icons.Outlined.Refresh
}

fun kindIcon(kind: FileKind): ImageVector = when (kind) {
    FileKind.Folder -> Icons2.folder
    FileKind.Image -> Icons2.image
    FileKind.Video -> Icons2.video
    FileKind.Audio -> Icons2.audio
    FileKind.Archive -> Icons2.archive
    FileKind.Pdf -> Icons2.pdf
    FileKind.Apk -> Icons2.apk
    FileKind.Doc, FileKind.Text -> Icons2.file
    FileKind.Code -> Icons2.code
    FileKind.File -> Icons2.file
}

// ======================================================================== тема

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

// ======================================================================== общие элементы

@Composable
fun FdIconButton(icon: ImageVector, desc: String, onClick: () -> Unit, tint: Color = fd.text, enabled: Boolean = true) {
    IconButton(onClick = onClick, enabled = enabled) { Icon(icon, contentDescription = desc, tint = tint) }
}

/** Шапка полноэкранного слоя: стрелка назад, заголовок, действия справа. */
@Composable
fun FdBar(title: String, onBack: () -> Unit, actions: @Composable () -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().background(fd.chrome).statusBarsPadding().height(56.dp).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FdIconButton(Icons2.back, "Назад", onBack)
        Text(
            title, color = fd.text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
        )
        actions()
    }
}

@Composable
fun BottomAction(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().background(fd.chrome).navigationBarsPadding().padding(12.dp),
    ) { content() }
}

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, danger: Boolean = false) {
    androidx.compose.material3.Button(
        onClick = onClick, enabled = enabled, modifier = modifier.height(50.dp),
        shape = RoundedCornerShape(14.dp),
        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
            containerColor = if (danger) fd.danger else fd.manila,
            contentColor = if (danger) Color.White else fd.onManila,
            disabledContainerColor = fd.surface3, disabledContentColor = fd.muted,
        ),
    ) { Text(text, fontWeight = FontWeight.SemiBold) }
}

@Composable
fun GhostButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    androidx.compose.material3.OutlinedButton(
        onClick = onClick, enabled = enabled, modifier = modifier.height(50.dp),
        shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, fd.line),
        colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(contentColor = fd.text, disabledContentColor = fd.muted),
    ) { Text(text) }
}

@Composable
fun StateView(icon: ImageVector, title: String, text: String? = null, action: (@Composable () -> Unit)? = null) {
    Column(
        Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, null, tint = fd.muted, modifier = Modifier.size(48.dp))
        Spacer(Modifier.height(12.dp))
        Text(title, color = fd.text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        if (text != null) {
            Spacer(Modifier.height(6.dp))
            Text(text, color = fd.muted, fontSize = 14.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
        if (action != null) {
            Spacer(Modifier.height(16.dp))
            action()
        }
    }
}

@Composable
fun Spinner(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = fd.manila)
    }
}

@Composable
fun SectionTitle(text: String) {
    Text(
        text, color = fd.muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 20.dp, top = 18.dp, bottom = 8.dp),
    )
}

@Composable
fun Group(content: @Composable () -> Unit) {
    Column(
        Modifier.padding(horizontal = 12.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(fd.surface2),
    ) { content() }
}

@Composable
private fun RowText(label: String, hint: String?, modifier: Modifier, danger: Boolean = false) {
    Column(modifier) {
        Text(label, color = if (danger) fd.danger else fd.text, fontSize = 16.sp)
        if (!hint.isNullOrEmpty()) Text(hint, color = fd.muted, fontSize = 13.sp)
    }
}

@Composable
fun SwitchRow(label: String, hint: String?, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(enabled = enabled) { onChange(!checked) }.padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RowText(label, hint, Modifier.weight(1f))
        Switch(
            checked = checked, onCheckedChange = null, enabled = enabled,
            colors = SwitchDefaults.colors(checkedTrackColor = fd.manila, checkedThumbColor = fd.onManila),
        )
    }
}

@Composable
fun ChoiceRow(label: String, hint: String?, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RowText(label, hint, Modifier.weight(1f))
        if (selected) Icon(Icons2.check, null, tint = fd.manila)
    }
}

@Composable
fun LinkRow(label: String, hint: String? = null, danger: Boolean = false, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RowText(label, hint, Modifier.weight(1f), danger)
        if (!danger) Icon(Icons2.chevron, null, tint = fd.muted)
    }
}

@Composable
fun RowDivider() = HorizontalDivider(color = fd.line, modifier = Modifier.padding(horizontal = 16.dp))

/** Круглая отметка выбора. */
@Composable
fun SelectCheck(on: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier.size(24.dp).clip(CircleShape)
            .background(if (on) fd.manila else Color.Transparent)
            .then(if (on) Modifier else Modifier.background(fd.surface3.copy(alpha = 0.6f), CircleShape)),
        contentAlignment = Alignment.Center,
    ) {
        if (on) Icon(Icons2.check, null, tint = fd.onManila, modifier = Modifier.size(16.dp))
    }
}

/** Значок типа файла в цветной подложке. */
@Composable
fun KindBadge(kind: FileKind, size: Int = 44) {
    val c = fd.kind(kind)
    Box(
        Modifier.size(size.dp).clip(RoundedCornerShape(12.dp)).background(c.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(if (kind == FileKind.Folder) Icons2.folderFilled else kindIcon(kind), null, tint = c, modifier = Modifier.size((size * 0.55f).dp))
    }
}

// ======================================================================== диалоги

@Composable
private fun DialogShell(
    title: String,
    onDismiss: () -> Unit,
    confirm: @Composable () -> Unit,
    dismiss: @Composable (() -> Unit)? = null,
    body: @Composable () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = fd.surface2,
        titleContentColor = fd.text,
        textContentColor = fd.muted,
        title = { Text(title, fontWeight = FontWeight.SemiBold) },
        text = { Column(Modifier.verticalScroll(rememberScrollState())) { body() } },
        confirmButton = confirm,
        dismissButton = dismiss,
    )
}

@Composable
fun ConfirmDialogUi(d: Dlg.Confirm) {
    DialogShell(
        d.title, onDismiss = { d.result.complete(false) },
        confirm = {
            TextButton(onClick = { d.result.complete(true) }) {
                Text(d.okText, color = if (d.danger) fd.danger else fd.manila, fontWeight = FontWeight.SemiBold)
            }
        },
        dismiss = { TextButton(onClick = { d.result.complete(false) }) { Text("Отмена", color = fd.muted) } },
    ) { Text(d.text) }
}

@Composable
fun PromptDialogUi(d: Dlg.Prompt) {
    val initial = remember(d) {
        val dot = d.value.lastIndexOf('.')
        val end = if (d.selectBase && dot > 0) dot else d.value.length
        TextFieldValue(d.value, TextRange(0, end))
    }
    var tf by remember(d) { mutableStateOf(initial) }
    var show by remember(d) { mutableStateOf(false) }
    val focus = remember { androidx.compose.ui.focus.FocusRequester() }
    androidx.compose.runtime.LaunchedEffect(d) { focus.requestFocus() }
    DialogShell(
        d.title, onDismiss = { d.result.complete(null) },
        confirm = { TextButton(onClick = { d.result.complete(tf.text) }) { Text(d.okText, color = fd.manila, fontWeight = FontWeight.SemiBold) } },
        dismiss = { TextButton(onClick = { d.result.complete(null) }) { Text("Отмена", color = fd.muted) } },
    ) {
        if (d.text != null) {
            Text(d.text)
            Spacer(Modifier.height(8.dp))
        }
        OutlinedTextField(
            value = tf, onValueChange = { tf = it }, singleLine = true,
            placeholder = { Text(d.placeholder) },
            visualTransformation = if (d.password && !show) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(keyboardType = if (d.password) KeyboardType.Password else KeyboardType.Text),
            trailingIcon = if (d.password) {
                { IconButton(onClick = { show = !show }) { Icon(if (show) Icons2.eyeOff else Icons2.eye, "Показать", tint = fd.muted) } }
            } else null,
            isError = d.error.isNotEmpty(),
            modifier = Modifier.fillMaxWidth().focusRequester(focus),
        )
        if (d.error.isNotEmpty()) Text(d.error, color = fd.danger, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
fun InfoDialogUi(st: InfoState, onClose: () -> Unit) {
    DialogShell(
        st.title, onDismiss = onClose,
        confirm = { TextButton(onClick = onClose) { Text("Закрыть", color = fd.manila) } },
    ) {
        st.rows.forEach { (k, v) ->
            Column(Modifier.padding(vertical = 5.dp)) {
                Text(k, color = fd.muted, fontSize = 12.sp)
                Text(v, color = fd.text, fontSize = 15.sp)
            }
        }
    }
}

@Composable
fun ProgressDialogUi(p: ProgressState) {
    AlertDialog(
        onDismissRequest = { p.cancel?.invoke() },
        containerColor = fd.surface2,
        title = { Text(p.title, color = fd.text, fontWeight = FontWeight.SemiBold) },
        text = {
            Column {
                if (p.fraction != null) {
                    LinearProgressIndicator(progress = { p.fraction.coerceIn(0f, 1f) }, color = fd.manila, trackColor = fd.surface3, modifier = Modifier.fillMaxWidth())
                } else {
                    LinearProgressIndicator(color = fd.manila, trackColor = fd.surface3, modifier = Modifier.fillMaxWidth())
                }
                if (!p.current.isNullOrEmpty()) {
                    Text(p.current, color = fd.muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 10.dp))
                }
            }
        },
        confirmButton = {},
        dismissButton = if (p.cancel != null) {
            { TextButton(onClick = { p.cancel.invoke() }) { Text("Отмена", color = fd.muted) } }
        } else null,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SheetUi(d: Dlg.Sheet, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = fd.surface2) {
        Column(Modifier.navigationBarsPadding().verticalScroll(rememberScrollState())) {
            Text(d.title, color = fd.text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 20.dp))
            if (d.subtitle != null) Text(d.subtitle, color = fd.muted, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 20.dp))
            Spacer(Modifier.height(8.dp))
            d.items.forEach { item ->
                if (item.divider) {
                    RowDivider()
                } else {
                    Row(
                        Modifier.fillMaxWidth().clickable { onDismiss(); item.onClick() }.padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (item.icon != null) {
                            Icon(item.icon, null, tint = if (item.danger) fd.danger else fd.text)
                            Spacer(Modifier.width(16.dp))
                        }
                        RowText(item.label, item.hint, Modifier.weight(1f), item.danger)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

private val ARCHIVE_FORMATS = listOf(
    Triple("zip", "ZIP", "Подходит всем, есть пароль AES-256"),
    Triple("7z", "7z", "Самое сильное сжатие"),
    Triple("tar.gz", "TAR.GZ", "Для Linux и серверов"),
    Triple("tar.xz", "TAR.XZ", "Сильное сжатие, медленнее"),
    Triple("tar.bz2", "TAR.BZ2", "Среднее сжатие"),
    Triple("tar", "TAR", "Без сжатия"),
)
private val ARCHIVE_LEVELS = listOf(2 to "Быстро", 5 to "Обычно", 9 to "Максимум")

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun CreateArchiveDialogUi(d: Dlg.CreateArchive) {
    var name by remember(d) { mutableStateOf(d.defaultName) }
    var format by remember(d) { mutableStateOf("zip") }
    var level by remember(d) { mutableStateOf(5) }
    var pw by remember(d) { mutableStateOf("") }
    var show by remember(d) { mutableStateOf(false) }
    var err by remember(d) { mutableStateOf("") }
    val zip = format == "zip"
    DialogShell(
        "Создать архив", onDismiss = { d.result.complete(null) },
        confirm = {
            TextButton(onClick = {
                val n = name.trim()
                if (n.isEmpty()) err = "Введите имя архива"
                else if (n.contains("/")) err = "В имени нельзя использовать «/»"
                else d.result.complete(ArchiveOptions(n, format, level, if (zip) pw else ""))
            }) { Text("Создать", color = fd.manila, fontWeight = FontWeight.SemiBold) }
        },
        dismiss = { TextButton(onClick = { d.result.complete(null) }) { Text("Отмена", color = fd.muted) } },
    ) {
        Text(if (d.count == 1) "Один объект" else "Объектов: ${d.count}")
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(value = name, onValueChange = { name = it; err = "" }, singleLine = true, label = { Text("Имя") }, isError = err.isNotEmpty(), modifier = Modifier.fillMaxWidth())
        if (err.isNotEmpty()) Text(err, color = fd.danger, fontSize = 13.sp)
        Spacer(Modifier.height(10.dp))
        Text("Формат", fontSize = 13.sp)
        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ARCHIVE_FORMATS.forEach { (v, l, _) ->
                androidx.compose.material3.FilterChip(selected = format == v, onClick = { format = v; if (v != "zip") pw = "" }, label = { Text(l) })
            }
        }
        Text(ARCHIVE_FORMATS.first { it.first == format }.third, fontSize = 13.sp)
        Spacer(Modifier.height(10.dp))
        Text("Сжатие", fontSize = 13.sp)
        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ARCHIVE_LEVELS.forEach { (v, l) ->
                androidx.compose.material3.FilterChip(selected = level == v, onClick = { level = v }, label = { Text(l) })
            }
        }
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = pw, onValueChange = { pw = it }, singleLine = true, enabled = zip,
            label = { Text(if (zip) "Пароль (необязательно)" else "Пароль есть только у ZIP") },
            visualTransformation = if (show) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = { IconButton(onClick = { show = !show }) { Icon(if (show) Icons2.eyeOff else Icons2.eye, "Показать пароль", tint = fd.muted) } },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Показывает текущий диалог и окно прогресса поверх всего. */
@Composable
fun DialogHost(vm: AppViewModel) {
    when (val d = vm.dialog) {
        is Dlg.Confirm -> ConfirmDialogUi(d)
        is Dlg.Prompt -> PromptDialogUi(d)
        is Dlg.Info -> InfoDialogUi(d.state) { vm.dialog = null }
        is Dlg.Sheet -> SheetUi(d) { vm.dialog = null }
        is Dlg.CreateArchive -> CreateArchiveDialogUi(d)
        null -> {}
    }
    vm.progress?.let { ProgressDialogUi(it) }
}
