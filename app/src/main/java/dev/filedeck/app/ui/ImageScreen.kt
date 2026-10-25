package dev.filedeck.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import dev.filedeck.app.R
import dev.filedeck.app.Str
import dev.filedeck.app.formatDate
import dev.filedeck.app.formatSize
import dev.filedeck.app.meta
import java.io.File

@Composable
fun ImageScreen(vm: AppViewModel, o: Overlay.Image) {
    val pager = rememberPagerState(initialPage = o.start, pageCount = { o.images.size })
    var chrome by remember { mutableStateOf(true) }
    val cur = o.images.getOrNull(pager.currentPage)
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { page ->
            ZoomableImage(o.images[page].path) { chrome = !chrome }
        }
        if (chrome && cur != null) {
            Row(
                Modifier.fillMaxWidth().background(Color(0x99000000)).statusBarsPadding().heightIn(min = 56.dp)
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FdIconButton(Icons2.back, Str.s(R.string.s_nazad), { vm.closeOverlay(o) }, tint = Color.White)
                Column(Modifier.weight(1f)) {
                    NameText(cur.name, size = 16, color = Color.White)
                    Text(
                        meta(cur.size.takeIf { it > 0 }?.let { formatSize(it) },
                            cur.mtime.takeIf { it > 0 }?.let { formatDate(it, true) }) +
                            "  ${pager.currentPage + 1} / ${o.images.size}",
                        color = Color(0xFFCCCCCC), fontSize = 12.sp,
                    )
                }
                FdIconButton(Icons2.share, Str.s(R.string.action_share), { vm.sharePath(cur.path) }, tint = Color.White)
                FdIconButton(Icons2.external, Str.s(R.string.s_otkryt_v_prilozhenii), { vm.openPathExternal(cur.path) }, tint = Color.White)
            }
        }
    }
}

@Composable
private fun ZoomableImage(path: String, onTap: () -> Unit) {
    var scale by remember(path) { mutableFloatStateOf(1f) }
    var ox by remember(path) { mutableFloatStateOf(0f) }
    var oy by remember(path) { mutableFloatStateOf(0f) }
    val state = rememberTransformableState { zoom, pan, _ ->
        scale = (scale * zoom).coerceIn(1f, 6f)
        if (scale > 1f) {
            ox += pan.x
            oy += pan.y
        } else {
            ox = 0f
            oy = 0f
        }
    }
    Box(
        Modifier.fillMaxSize()
            .pointerInput(path) {
                detectTapGestures(
                    onTap = { onTap() },
                    onDoubleTap = { if (scale > 1f) { scale = 1f; ox = 0f; oy = 0f } else scale = 2.5f },
                )
            }
            .transformable(state),
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = File(path), contentDescription = null, contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize().graphicsLayer(scaleX = scale, scaleY = scale, translationX = ox, translationY = oy),
        )
    }
}
