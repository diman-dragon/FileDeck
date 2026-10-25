package dev.filedeck.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
                meta(d.format, "${d.count} ${Str.p(R.plurals.word_item, d.count)}", formatSize(d.bytes),
                    if (d.encrypted) Str.s(R.string.archive_password) else null),
                color = fd.muted, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                c.loading -> Spinner(Modifier.fillMaxSize())
                d != null && !d.listing -> StateView(Icons2.archive, Str.s(R.string.s_spisok_nedostupen),
                    Str.s(R.string.s_soderzhimoe_rar_arhivov_pokazat_nelzya))
                else -> Column(Modifier.fillMaxSize()) {
                    val parts = c.cwd.split("/").filter { it.isNotEmpty() }
                    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp).horizontalScroll(rememberScrollState()),
                        verticalAlignment = Alignment.CenterVertically) {
                        Pill(Str.s(R.string.kind_archive), parts.isEmpty()) { c.cwd = "" }
                        var acc = ""
                        parts.forEachIndexed { i, p ->
                            acc = if (acc.isEmpty()) p else "$acc/$p"
                            val target = acc
                            Pill(p, i == parts.size - 1) { c.cwd = target }
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
                                ItemRow(
                                    title = k.name,
                                    lines = listOf(
                                        if (k.dir) meta("${k.count} ${Str.p(R.plurals.word_file, k.count)}", formatSize(k.size))
                                        else meta(formatSize(k.size), k.mtime.takeIf { it > 0 }?.let { formatDate(it) }),
                                    ),
                                    selected = on,
                                    onClick = { c.open(k) },
                                    lead = { KindBadge(kind) },
                                    end = { Box(Modifier.clickable { c.toggle(k.path) }) { SelectCheck(on) } },
                                )
                            }
                        }
                        if (d?.truncated == true) Text(Str.s(R.string.s_pokazany_pervye_20_000_zapisey), color = fd.muted,
                            fontSize = 12.sp, modifier = Modifier.padding(12.dp))
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
