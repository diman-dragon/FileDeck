package dev.filedeck.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import dev.filedeck.app.CleanCat
import dev.filedeck.app.CleanRow
import dev.filedeck.app.CleanerLogic
import dev.filedeck.app.FileKind
import dev.filedeck.app.JUNK_LABEL
import dev.filedeck.app.R
import dev.filedeck.app.ScanResult
import dev.filedeck.app.Str
import dev.filedeck.app.USAGE_LABEL
import dev.filedeck.app.basename
import dev.filedeck.app.formatDate
import dev.filedeck.app.formatSize
import dev.filedeck.app.isEnabled
import dev.filedeck.app.kindOf
import dev.filedeck.app.meta
import dev.filedeck.app.parentOf
import dev.filedeck.app.shortLocation
import dev.filedeck.app.with
import java.io.File

private fun catIcon(c: CleanCat) = when (c) {
    CleanCat.Duplicates -> Icons2.copy
    CleanCat.Similar -> Icons2.image
    CleanCat.Junk -> Icons2.delete
    CleanCat.Apks -> Icons2.apk
    CleanCat.Largest -> Icons2.drive
    CleanCat.EmptyFolders -> Icons2.folder
    CleanCat.ZeroFiles -> Icons2.file
}

private fun usageColor(key: String): Color = when (key) {
    "images" -> Color(0xFFEC75AC)
    "video" -> Color(0xFFA58AFF)
    "audio" -> Color(0xFFFF9D5C)
    "docs" -> Color(0xFF62ADFF)
    "archives" -> Color(0xFF6FD3C0)
    "apk" -> Color(0xFF94D96E)
    else -> Color(0xFF8F8F99)
}

@Composable
fun CleanerScreen(c: CleanerController) {
    Column(Modifier.fillMaxSize().background(fd.surface)) {
        when (c.view) {
            CleanView.Setup -> SetupView(c)
            CleanView.Scanning -> ScanningView(c)
            CleanView.Summary -> SummaryView(c)
            CleanView.Detail -> DetailView(c)
        }
    }
}

@Composable
private fun ColumnScope.SetupView(c: CleanerController) {
    FdBar(Str.s(R.string.s_ochistka), onBack = { c.close() })
    Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons2.sparkle, null, tint = fd.manila, modifier = Modifier.size(34.dp))
            Spacer(Modifier.width(14.dp))
            Text(Str.s(R.string.s_naydem_dublikaty_musor_pustye_papki), color = fd.muted, fontSize = 14.sp)
        }
        SectionTitle(Str.s(R.string.s_gde_iskat))
        SourcesGroup(c)
        SectionTitle(Str.s(R.string.s_chto_iskat))
        CategoriesGroup(c)
        SectionTitle(Str.s(R.string.s_dopolnitelno))
        Group {
            SwitchRow(
                Str.s(R.string.s_papki_prilozheniy),
                Str.s(R.string.s_android_media_media_messendzherov_udalen),
                c.opts.appFolders,
            ) { v -> c.setOptions { it.copy(appFolders = v) } }
        }
        Spacer(Modifier.height(20.dp))
    }
    BottomAction { PrimaryButton(Str.s(R.string.s_skanirovat), { c.start() }, Modifier.fillMaxWidth()) }
}

@Composable
private fun SourcesGroup(c: CleanerController) {
    val o = c.opts
    Group {
        val p = c.primary
        SwitchRow(
            Str.s(R.string.start_primary),
            p?.let { Str.f(R.string.s_svobodno_iz, formatSize(it.free), formatSize(it.total)) },
            o.primary,
            p != null,
        ) { v -> c.setOptions { it.copy(primary = v) } }
        c.secondary?.let { s ->
            RowDivider()
            SwitchRow(
                Str.s(R.string.storage_sd),
                Str.f(R.string.s_svobodno_iz, formatSize(s.free), formatSize(s.total)),
                o.sd,
            ) { v -> c.setOptions { it.copy(sd = v) } }
        }
    }
}

@Composable
private fun CategoriesGroup(c: CleanerController) {
    Group {
        CleanCat.entries.forEachIndexed { i, cat ->
            if (i > 0) RowDivider()
            SwitchRow(cat.title, cat.hint, c.opts.isEnabled(cat)) { v -> c.setOptions { it.with(cat, v) } }
        }
    }
}

@Composable
private fun ColumnScope.ScanningView(c: CleanerController) {
    val p = c.progress
    FdBar(Str.s(R.string.s_skanirovanie), onBack = { c.onBack() })
    Column(Modifier.weight(1f).fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center) {
        Icon(Icons2.sparkle, null, tint = fd.manila, modifier = Modifier.size(48.dp))
        Spacer(Modifier.height(16.dp))
        val phase = when (p?.phase) {
            "walk" -> Str.s(R.string.s_prosmatrivaem_fayly)
            "hash" -> Str.s(R.string.s_sravnivaem_soderzhimoe)
            "images" -> Str.s(R.string.s_analiziruem_fotografii)
            else -> Str.s(R.string.s_rabotaem)
        }
        Text(phase, color = fd.text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(16.dp))
        if (p != null && p.phase != "walk" && p.total > 0) {
            LinearProgressIndicator(progress = { (p.done.toFloat() / p.total).coerceIn(0f, 1f) }, color = fd.manila,
                trackColor = fd.surface3, modifier = Modifier.fillMaxWidth())
        } else {
            LinearProgressIndicator(color = fd.manila, trackColor = fd.surface3, modifier = Modifier.fillMaxWidth())
        }
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("%,d".format(p?.files ?: 0).replace(',', ' '), color = fd.text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text(Str.s(R.string.s_faylov), color = fd.muted, fontSize = 13.sp)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(formatSize(p?.bytes ?: 0).ifEmpty { Str.s(R.string.s_0_b) }, color = fd.text, fontSize = 22.sp,
                    fontWeight = FontWeight.Bold)
                Text(Str.s(R.string.s_prosmotreno), color = fd.muted, fontSize = 13.sp)
            }
        }
        val cur = p?.current
        if (!cur.isNullOrEmpty()) {
            Spacer(Modifier.height(16.dp))
            MetaText(cur, center = true)
        }
    }
    BottomAction { GhostButton(Str.s(R.string.s_otmena), { c.onBack() }, Modifier.fillMaxWidth()) }
}

@Composable
private fun ColumnScope.SummaryView(c: CleanerController) {
    val r = c.result ?: return
    FdBar(Str.s(R.string.s_rezultaty), onBack = { c.close() }) {
        FdIconButton(Icons2.refresh, Str.s(R.string.s_skanirovat_zanovo), { c.view = CleanView.Setup })
    }
    val safe = CleanerLogic.safeBytes(r, c.sel)
    Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
        Column(Modifier.padding(20.dp)) {
            Text(Str.s(R.string.s_mozhno_bezopasno_osvobodit), color = fd.muted, fontSize = 13.sp)
            Text(formatSize(safe), color = fd.manila, fontSize = 34.sp, fontWeight = FontWeight.Bold)
            Text(
                Str.f(
                    R.string.cleaner_scanned,
                    "%,d".format(r.stats.files),
                    Str.p(R.plurals.word_file, r.stats.files),
                    formatSize(r.stats.bytes),
                    "%.1f".format(r.stats.ms / 1000.0),
                ),
                color = fd.muted, fontSize = 13.sp,
            )
        }
        UsageBar(r)
        SectionTitle(Str.s(R.string.s_naydeno))
        FoundGroup(c, r)
        Spacer(Modifier.height(20.dp))
    }
    BottomAction {
        if (safe > 0) {
            PrimaryButton(Str.f(R.string.s_bystraya_ochistka_2, formatSize(safe)), { c.quickClean() }, Modifier.fillMaxWidth(),
                enabled = !c.busy)
        } else {
            GhostButton(Str.s(R.string.s_skanirovat_zanovo), { c.view = CleanView.Setup }, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun UsageBar(r: ScanResult) {
    val used = USAGE_LABEL.mapNotNull { (k, label) -> r.usage[k]?.bytes?.takeIf { it > 0 }?.let { Triple(k, label, it) } }
    if (used.isEmpty()) return
    Row(Modifier.padding(horizontal = 20.dp).fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp))) {
        used.forEach { (k, _, bytes) ->
            Box(Modifier.weight(bytes.toFloat().coerceAtLeast(1f)).fillMaxSize().background(usageColor(k)))
        }
    }
    Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
        used.sortedByDescending { it.third }.take(5).forEach { (k, l, bytes) ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                Box(Modifier.size(10.dp).clip(RoundedCornerShape(5.dp)).background(usageColor(k)))
                Spacer(Modifier.width(8.dp))
                Text("$l  ${formatSize(bytes)}", color = fd.muted, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun FoundGroup(c: CleanerController, r: ScanResult) {
    val enabled = CleanCat.entries.filter { c.opts.isEnabled(it) }
    if (enabled.isEmpty()) {
        Text(Str.s(R.string.s_vklyuchite_hotya_by_odnu_kategoriyu), color = fd.muted, modifier = Modifier.padding(20.dp))
    }
    Group {
        enabled.forEachIndexed { i, cat ->
            if (i > 0) RowDivider()
            val n = CleanerLogic.countOf(r, cat)
            val bytes = if (cat == CleanCat.Largest) CleanerLogic.rowsOf(r, cat).sumOf { it.size } else CleanerLogic.wasteOf(r, cat)
            val unit = if (cat.group) Str.p(R.plurals.word_group, n) else Str.p(R.plurals.word_item, n)
            val sub = if (n == 0) Str.s(R.string.s_nichego_ne_naydeno) else meta("$n $unit", if (bytes > 0) formatSize(bytes) else null)
            Row(
                Modifier.fillMaxWidth().clickable(enabled = n > 0) { c.cat = cat; c.view = CleanView.Detail }.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(if (n > 0) catIcon(cat) else Icons2.check, null, tint = if (n > 0) fd.manila else fd.muted)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(cat.title, color = fd.text, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                    Text(sub, color = fd.muted, fontSize = 13.sp)
                }
                if (n > 0) Icon(Icons2.chevron, null, tint = fd.muted)
            }
        }
    }
}

@Composable
private fun ColumnScope.DetailView(c: CleanerController) {
    val cat = c.cat
    val rows = CleanerLogic.rowsOf(c.result, cat)
    val selSet = c.sel[cat] ?: emptySet()
    val n = selSet.size
    val bytes = CleanerLogic.selBytes(c.result, c.sel, cat)
    val all = rows.isNotEmpty() && n == rows.size
    FdBar(cat.title, onBack = { c.onBack() }) {
        if (rows.isNotEmpty()) SelectAllButton(all) { c.toggleAll() }
    }
    if (rows.isEmpty()) {
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            StateView(Icons2.check, Str.s(R.string.s_vse_chisto), Str.s(R.string.s_v_etoy_kategorii_bolshe_nichego))
        }
    } else {
        val groups = if (cat.group) CleanerLogic.groupRows(rows) else emptyMap()
        LazyColumn(Modifier.weight(1f)) {
            item { Text(cat.hint, color = fd.muted, fontSize = 13.sp, modifier = Modifier.padding(16.dp)) }
            if (cat.group) {
                groups.values.forEach { list ->
                    val keep = CleanerLogic.keeper(list)
                    val waste = list.filter { it !== keep }.sumOf { it.size }
                    item {
                        val kindLabel = Str.s(if (cat == CleanCat.Similar) R.string.group_similar else R.string.group_same)
                        Row(Modifier.fillMaxWidth().background(fd.surface2).padding(horizontal = 16.dp, vertical = 8.dp)) {
                            Text(
                                meta(kindLabel, list.size.toString()),
                                color = fd.text, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f),
                            )
                            Text(Str.f(R.string.s_mozhno_osvobodit, formatSize(waste)), color = fd.muted, fontSize = 13.sp)
                        }
                    }
                    items(list, key = { it.path + "#" + it.group }) { r -> CleanRowView(c, r, r.path in selSet, keep.path) }
                }
            } else {
                items(rows, key = { it.path }) { r -> CleanRowView(c, r, r.path in selSet, null) }
            }
        }
    }
    BottomAction {
        if (n > 0) PrimaryButton(meta(Str.s(R.string.action_delete), n.toString(), formatSize(bytes)), { c.deleteCategory() },
            Modifier.fillMaxWidth(), enabled = !c.busy, danger = true)
        else GhostButton(Str.s(R.string.s_nichego_ne_vybrano), {}, Modifier.fillMaxWidth(), enabled = false)
    }
}

@Composable
private fun CleanRowView(c: CleanerController, r: CleanRow, on: Boolean, keepPath: String?) {
    val name = basename(r.path)
    val kind = kindOf(name, r.dir)
    val where = shortLocation(parentOf(r.path))
    val sub = when (c.cat) {
        CleanCat.Junk -> meta(JUNK_LABEL[r.kind] ?: Str.s(R.string.junk_other), formatSize(r.size))
        CleanCat.Apks -> meta(r.version?.takeIf { it.isNotEmpty() }?.let { "v$it" }, formatSize(r.size),
            r.mtime.takeIf { it > 0 }?.let { formatDate(it) })
        CleanCat.EmptyFolders -> Str.s(R.string.s_pustaya_papka)
        CleanCat.ZeroFiles -> Str.s(R.string.s_0_b)
        else -> meta(if (r.w > 0) "${r.w}×${r.h}" else null, formatSize(r.size), r.mtime.takeIf { it > 0 }?.let { formatDate(it) })
    }
    val title = if (c.cat == CleanCat.Apks && !r.label.isNullOrEmpty()) r.label else name
    ItemRow(
        title = title ?: name,
        lines = listOf(sub, where),
        selected = on,
        onClick = { c.toggleRow(r.path) },
        start = { SelectCheck(on) },
        lead = {
            if (kind == FileKind.Image) {
                AsyncImage(model = File(r.path), contentDescription = null, contentScale = ContentScale.Crop,
                    modifier = Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)))
            } else {
                KindBadge(kind)
            }
        },
        titleExtra = if (keepPath == r.path) {
            { Text(Str.s(R.string.s_ostavit), color = fd.manila, fontSize = 12.sp, modifier = Modifier.padding(start = 8.dp)) }
        } else null,
        end = {
            if (!r.dir) FdIconButton(Icons2.eye, Str.s(R.string.s_otkryt), { c.view(r.path) })
            FdIconButton(Icons2.folder, Str.s(R.string.s_pokazat_v_papke), { c.locate(r.path) })
        },
    )
}
