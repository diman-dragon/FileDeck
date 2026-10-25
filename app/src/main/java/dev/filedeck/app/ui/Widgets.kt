package dev.filedeck.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.filedeck.app.FileKind
import dev.filedeck.app.R
import dev.filedeck.app.Str

@Composable
fun FdIconButton(icon: ImageVector, desc: String, onClick: () -> Unit, tint: Color = fd.text, enabled: Boolean = true) {
    IconButton(onClick = onClick, enabled = enabled) { Icon(icon, contentDescription = desc, tint = tint) }
}

@Composable
fun FdBar(title: String, onBack: () -> Unit, actions: @Composable () -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().background(fd.chrome).statusBarsPadding().height(56.dp).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FdIconButton(Icons2.back, Str.s(R.string.s_nazad), onBack)
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
    Button(
        onClick = onClick, enabled = enabled, modifier = modifier.height(50.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (danger) fd.danger else fd.manila,
            contentColor = if (danger) Color.White else fd.onManila,
            disabledContainerColor = fd.surface3, disabledContentColor = fd.muted,
        ),
    ) { Text(text, fontWeight = FontWeight.SemiBold) }
}

@Composable
fun GhostButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    OutlinedButton(
        onClick = onClick, enabled = enabled, modifier = modifier.height(50.dp),
        shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, fd.line),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = fd.text, disabledContentColor = fd.muted),
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
            Text(text, color = fd.muted, fontSize = 14.sp, textAlign = TextAlign.Center)
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

@Composable
fun Pill(label: String, current: Boolean, onClick: () -> Unit) {
    Text(
        label, color = if (current) fd.onManila else fd.text, fontSize = 14.sp, maxLines = 1,
        modifier = Modifier.padding(end = 6.dp).clip(RoundedCornerShape(14.dp))
            .background(if (current) fd.manila else fd.surface3)
            .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

/** Строка списка: [start] [lead] заголовок + подписи [end]. Общая для файлов, очистки, корзины и архива. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ItemRow(
    title: String,
    lines: List<String>,
    selected: Boolean,
    onClick: () -> Unit,
    lead: @Composable () -> Unit,
    onLongClick: (() -> Unit)? = null,
    titleSize: Int = 15,
    start: (@Composable () -> Unit)? = null,
    titleExtra: (@Composable () -> Unit)? = null,
    end: (@Composable () -> Unit)? = null,
) {
    Row(
        Modifier.fillMaxWidth()
            .background(if (selected) fd.manila.copy(alpha = 0.12f) else Color.Transparent)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (start != null) {
            start()
            Spacer(Modifier.width(10.dp))
        }
        lead()
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, color = fd.text, fontSize = titleSize.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                titleExtra?.invoke()
            }
            lines.forEach { Text(it, color = fd.muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
        end?.invoke()
    }
}
