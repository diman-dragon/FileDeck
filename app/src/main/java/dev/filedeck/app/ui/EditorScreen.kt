package dev.filedeck.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.filedeck.app.R
import dev.filedeck.app.Str

@Composable
fun EditorScreen(c: EditorController) {
    Column(Modifier.fillMaxSize().background(fd.surface).imePadding()) {
        FdBar(c.entry.name + if (c.dirty) " •" else "", onBack = { c.vm.onBack() }) {
            TextButton(onClick = { c.save() }, enabled = c.dirty) {
                Text(Str.s(R.string.s_sohranit), color = if (c.dirty) fd.manila else fd.muted, fontWeight = FontWeight.SemiBold)
            }
        }
        if (!c.loaded) {
            Spinner(Modifier.fillMaxSize())
        } else {
            BasicTextField(
                value = c.text, onValueChange = { c.text = it },
                textStyle = TextStyle(color = fd.text, fontSize = 14.sp, fontFamily = FontFamily.Monospace),
                cursorBrush = SolidColor(fd.manila),
                modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(12.dp),
            )
        }
    }
}
