package dev.filedeck.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.filedeck.app.R
import dev.filedeck.app.Str
import dev.filedeck.app.formatDate
import dev.filedeck.app.formatSize
import dev.filedeck.app.kindOf
import dev.filedeck.app.meta

@Composable
fun ArchiveScreen(c: ArchiveBrowserController) {
    val d = c.data
    Column(Modifier.fillMaxSize().background(fd.surface)) {
        FdBar(c.entry.name, onBack = { if (!c.onBack()) c.vm.overlays.removeAll { it is Overlay.ArchiveView && it.c === c } })
        if (d != null) {
            Text(
                meta(d.format, "${d.count} ${Str.p(R.plurals.word_item, d.count)}", formatSize(d.bytes), if (d.encrypted) Str.s(R.string.archive_password) else null),
                color = fd.muted, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                c.loading -> Spinner(Modifier.fillMaxSize())
                d != null && !d.listing -> StateView(Icons2.archive, Str.s(R.string.s_spisok_nedostupen), Str.s(R.string.s_soderzhimoe_rar_arhivov_pokazat_nelzya))
                else -> Column(Modifier.fillMaxSize()) {
                    val parts = c.cwd.split("/").filter { it.isNotEmpty() }
                    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp).horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
                        Crumb(Str.s(R.string.kind_archive), parts.isEmpty()) { c.cwd = "" }
                        var acc = ""
                        parts.forEachIndexed { i, p ->
                            acc = if (acc.isEmpty()) p else "$acc/$p"
                            val target = acc
                            Crumb(p, i == parts.size - 1) { c.cwd = target }
                        }
                    }
                    val kids = c.children()
                    if (kids.isEmpty()) {
                        StateView(Icons2.folder, Str.s(R.string.s_papka_pusta))
                    } else {
                        LazyColumn(Modifier.weight(1f)) {
                            items(kids, key = { it.path }) { k ->
                                val on = k.path in c.selected
                                val kind = kindOf(k.name, k.dir)
                                Row(
                                    Modifier.fillMaxWidth().clickable { c.open(k) }.background(if (on) fd.manila.copy(alpha = 0.10f) else Color.Transparent).padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    KindBadge(kind)
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(k.name, color = fd.text, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(
                                            if (k.dir) meta("${k.count} ${Str.p(R.plurals.word_file, k.count)}", formatSize(k.size))
                                            else meta(formatSize(k.size), k.mtime.takeIf { it > 0 }?.let { formatDate(it) }),
                                            color = fd.muted, fontSize = 12.sp,
                                        )
                                    }
                                    Box(Modifier.clickable { c.toggle(k.path) }) { SelectCheck(on) }
                                }
                            }
                        }
                        if (d?.truncated == true) Text(Str.s(R.string.s_pokazany_pervye_20_000_zapisey), color = fd.muted, fontSize = 12.sp, modifier = Modifier.padding(12.dp))
                    }
                }
            }
        }
        if (d != null) {
            BottomAction {
                val n = c.selected.size
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (d.listing) GhostButton(Str.s(R.string.s_raspakovat_vse), { c.extract(emptySet()) }, Modifier.weight(1f))
                    if (n > 0) PrimaryButton(Str.f(R.string.s_vybrannoe_2, n), { c.extract(c.selected) }, Modifier.weight(1f))
                    else if (!d.listing) PrimaryButton(Str.s(R.string.s_raspakovat_arhiv), { c.extract(emptySet()) }, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun Crumb(label: String, current: Boolean, onClick: () -> Unit) {
    Text(
        label, color = if (current) fd.onManila else fd.text, fontSize = 14.sp, maxLines = 1,
        modifier = Modifier.padding(end = 6.dp).clip(RoundedCornerShape(14.dp)).background(if (current) fd.manila else fd.surface3)
            .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 6.dp),
    )
}
