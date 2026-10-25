package dev.filedeck.app.ui

import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.filedeck.app.R
import dev.filedeck.app.Str
import kotlinx.coroutines.delay

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
        dismiss = { TextButton(onClick = { d.result.complete(false) }) { Text(Str.s(R.string.s_otmena), color = fd.muted) } },
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
    val focus = remember { FocusRequester() }
    LaunchedEffect(d) {
        delay(120)
        try { focus.requestFocus() } catch (e: Exception) { Log.w("FileDeck", "ignored") }
    }
    DialogShell(
        d.title, onDismiss = { d.result.complete(null) },
        confirm = { TextButton(onClick = { d.result.complete(tf.text) }) { Text(d.okText, color = fd.manila,
            fontWeight = FontWeight.SemiBold) } },
        dismiss = { TextButton(onClick = { d.result.complete(null) }) { Text(Str.s(R.string.s_otmena), color = fd.muted) } },
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
                { IconButton(onClick = { show = !show }) { Icon(if (show) Icons2.eyeOff else Icons2.eye, Str.s(R.string.s_pokazat),
                    tint = fd.muted) } }
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
        confirm = { TextButton(onClick = onClose) { Text(Str.s(R.string.action_close), color = fd.manila) } },
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
                    LinearProgressIndicator(progress = { p.fraction.coerceIn(0f, 1f) }, color = fd.manila, trackColor = fd.surface3,
                        modifier = Modifier.fillMaxWidth())
                } else {
                    LinearProgressIndicator(color = fd.manila, trackColor = fd.surface3, modifier = Modifier.fillMaxWidth())
                }
                if (!p.current.isNullOrEmpty()) {
                    MetaText(p.current, Modifier.padding(top = 10.dp), size = 13)
                }
            }
        },
        confirmButton = {},
        dismissButton = if (p.cancel != null) {
            { TextButton(onClick = { p.cancel.invoke() }) { Text(Str.s(R.string.s_otmena), color = fd.muted) } }
        } else null,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SheetUi(d: Dlg.Sheet, onClose: () -> Unit) {
    ModalBottomSheet(onDismissRequest = { onClose(); d.onCancel?.invoke() }, containerColor = fd.surface2) {
        Column(Modifier.navigationBarsPadding().verticalScroll(rememberScrollState())) {
            NameText(d.title, Modifier.padding(horizontal = 20.dp), size = 18, weight = FontWeight.SemiBold)
            if (d.subtitle != null) MetaText(d.subtitle, Modifier.padding(horizontal = 20.dp), size = 13)
            Spacer(Modifier.height(8.dp))
            d.items.forEach { item ->
                if (item.divider) {
                    RowDivider()
                } else {
                    Row(
                        Modifier.fillMaxWidth().clickable { onClose(); item.onClick() }.padding(horizontal = 20.dp, vertical = 12.dp),
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
    Triple("zip", "ZIP", Str.s(R.string.s_podhodit_vsem_est_parol_aes)),
    Triple("7z", "7z", Str.s(R.string.s_samoe_silnoe_szhatie)),
    Triple("tar.gz", "TAR.GZ", Str.s(R.string.s_dlya_linux_i_serverov)),
    Triple("tar.xz", "TAR.XZ", Str.s(R.string.s_silnoe_szhatie_medlennee)),
    Triple("tar.bz2", "TAR.BZ2", Str.s(R.string.s_srednee_szhatie)),
    Triple("tar", "TAR", Str.s(R.string.s_bez_szhatiya)),
)
private val ARCHIVE_LEVELS = listOf(2 to Str.s(R.string.s_bystro), 5 to Str.s(R.string.s_obychno), 9 to Str.s(R.string.s_maksimum))

@OptIn(ExperimentalLayoutApi::class)
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
        Str.s(R.string.s_sozdat_arhiv), onDismiss = { d.result.complete(null) },
        confirm = {
            TextButton(onClick = {
                val n = name.trim()
                if (n.isEmpty()) err = Str.s(R.string.s_vvedite_imya_arhiva)
                else if (n.contains("/")) err = Str.s(R.string.s_v_imeni_nelzya_ispolzovat)
                else d.result.complete(ArchiveOptions(n, format, level, if (zip) pw else ""))
            }) { Text(Str.s(R.string.s_sozdat), color = fd.manila, fontWeight = FontWeight.SemiBold) }
        },
        dismiss = { TextButton(onClick = { d.result.complete(null) }) { Text(Str.s(R.string.s_otmena), color = fd.muted) } },
    ) {
        Text(if (d.count == 1) Str.s(R.string.s_odin_obekt) else Str.f(R.string.s_obektov, d.count))
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(value = name, onValueChange = { name = it; err = "" }, singleLine = true,
            label = { Text(Str.s(R.string.s_imya)) }, isError = err.isNotEmpty(), modifier = Modifier.fillMaxWidth())
        if (err.isNotEmpty()) Text(err, color = fd.danger, fontSize = 13.sp)
        Spacer(Modifier.height(10.dp))
        Text(Str.s(R.string.s_format), fontSize = 13.sp)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ARCHIVE_FORMATS.forEach { (v, l, _) ->
                FilterChip(selected = format == v, onClick = { format = v; if (v != "zip") pw = "" }, label = { Text(l) })
            }
        }
        Text(ARCHIVE_FORMATS.first { it.first == format }.third, fontSize = 13.sp)
        Spacer(Modifier.height(10.dp))
        Text(Str.s(R.string.s_szhatie), fontSize = 13.sp)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ARCHIVE_LEVELS.forEach { (v, l) ->
                FilterChip(selected = level == v, onClick = { level = v }, label = { Text(l) })
            }
        }
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = pw, onValueChange = { pw = it }, singleLine = true, enabled = zip,
            label = { Text(if (zip) Str.s(R.string.s_parol_neobyazatelno) else Str.s(R.string.s_parol_est_tolko_u_zip)) },
            visualTransformation = if (show) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = { IconButton(onClick = { show = !show }) { Icon(if (show) Icons2.eyeOff else Icons2.eye,
                Str.s(R.string.s_pokazat_parol), tint = fd.muted) } },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

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
