package dev.filedeck.app.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Единые текстовые атомы. Любое имя (файла, папки, архива, пути) переносится на новые строки целиком:
 * без многоточия, без обрезки и без сокращений. Длинное слово без пробелов тоже переносится по символам.
 */
@Composable
fun NameText(
    text: String,
    modifier: Modifier = Modifier,
    size: Int = 15,
    color: Color = fd.text,
    weight: FontWeight? = null,
    center: Boolean = false,
) {
    Text(
        text,
        color = color,
        fontSize = size.sp,
        lineHeight = (size * 1.3f).sp,
        fontWeight = weight,
        textAlign = if (center) TextAlign.Center else TextAlign.Start,
        modifier = modifier,
    )
}

@Composable
fun MetaText(text: String, modifier: Modifier = Modifier, size: Int = 12, center: Boolean = false) {
    NameText(text, modifier, size, fd.muted, center = center)
}

/** Карточка плитки: значок или миниатюра сверху, название и подпись под ней — одинаково везде. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Tile(
    title: String,
    subtitle: String?,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    square: Boolean = true,
    lead: @Composable BoxScope.() -> Unit,
) {
    Column(
        modifier.clip(RoundedCornerShape(14.dp))
            .background(if (selected) fd.manila.copy(alpha = 0.18f) else fd.surface2)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.fillMaxWidth().then(if (square) Modifier.aspectRatio(1f) else Modifier.height(40.dp)),
            contentAlignment = Alignment.Center,
        ) {
            lead()
            if (selected) Box(Modifier.align(Alignment.TopEnd)) { SelectCheck(true) }
        }
        NameText(title, Modifier.padding(top = 6.dp), size = 13, center = true)
        if (subtitle != null) MetaText(subtitle, size = 11, center = true)
    }
}
