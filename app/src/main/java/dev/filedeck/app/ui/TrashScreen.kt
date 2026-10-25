package dev.filedeck.app.ui

import androidx.compose.foundation.background
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.filedeck.app.R
import dev.filedeck.app.Str
import dev.filedeck.app.formatDate
import dev.filedeck.app.formatSize
import dev.filedeck.app.kindOf
import dev.filedeck.app.meta
import dev.filedeck.app.parentOf
import dev.filedeck.app.shortLocation

@Composable
fun TrashScreen(c: TrashController) {
    val vm = c.vm
    val n = c.selected.size
    Column(Modifier.fillMaxSize().background(fd.surface)) {
        FdBar(Str.s(R.string.s_korzina), onBack = { vm.overlays.removeAll { it is Overlay.Trash && it.c === c } }) {
            if (c.items.isNotEmpty()) SelectAllButton(n == c.items.size) { c.toggleAll() }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                c.loading -> Spinner(Modifier.fillMaxSize())
                c.items.isEmpty() -> StateView(Icons2.delete, Str.s(R.string.trash_empty),
                    Str.f(R.string.trash_empty_hint, c.days, Str.p(R.plurals.word_day, c.days)))
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    item {
                        Text(
                            meta("${c.items.size} ${Str.p(R.plurals.word_item, c.items.size)}", formatSize(c.bytes),
                                Str.f(R.string.trash_keep, c.days, Str.p(R.plurals.word_day, c.days))),
                            color = fd.muted, fontSize = 13.sp, modifier = Modifier.padding(16.dp),
                        )
                    }
                    items(c.items, key = { it.id }) { t ->
                        val on = t.id in c.selected
                        val kind = kindOf(t.name, t.dir)
                        val left = maxOf(0L,
                            Math.ceil((t.time + c.days * 86_400_000.0 - System.currentTimeMillis()) / 86_400_000.0).toLong()).toInt()
                        ItemRow(
                            title = t.name,
                            lines = listOf(
                                meta(formatSize(t.size), Str.f(R.string.trash_deleted_on, formatDate(t.time))),
                                meta(shortLocation(parentOf(t.orig)), Str.f(R.string.trash_left, left, Str.p(R.plurals.word_day, left))),
                            ),
                            selected = on,
                            onClick = { c.toggle(t.id) },
                            start = { SelectCheck(on) },
                            lead = { KindBadge(kind) },
                        )
                    }
                }
            }
        }
        if (n > 0) {
            BottomAction {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PrimaryButton(Str.f(R.string.s_vosstanovit, n), { c.restore() }, Modifier.weight(1f))
                    PrimaryButton(Str.s(R.string.action_delete), { c.delete(false) }, Modifier.weight(1f), danger = true)
                }
            }
        } else if (c.items.isNotEmpty()) {
            BottomAction { GhostButton(Str.s(R.string.s_ochistit_korzinu), { c.delete(true) }, Modifier.fillMaxWidth()) }
        }
    }
}
