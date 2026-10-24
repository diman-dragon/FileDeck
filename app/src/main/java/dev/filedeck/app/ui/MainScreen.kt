package dev.filedeck.app.ui

import android.util.Log
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import dev.filedeck.app.FileEntry
import dev.filedeck.app.FileKind
import dev.filedeck.app.R
import dev.filedeck.app.StorageRoot
import dev.filedeck.app.Str
import dev.filedeck.app.ViewMode
import dev.filedeck.app.formatDate
import dev.filedeck.app.formatSize
import dev.filedeck.app.joinPath
import dev.filedeck.app.kind
import dev.filedeck.app.meta
import dev.filedeck.app.parentOf
import java.io.File
import kotlinx.coroutines.delay

@Composable
fun MainScreen(vm: AppViewModel) {
    val selecting = vm.selection.isNotEmpty()
    val pasteBar = vm.clip != null && vm.path != null && vm.perm == true && vm.search == null && !selecting
    val dockVisible = selecting || pasteBar
    Column(Modifier.fillMaxSize().background(fd.surface)) {
        TopBar(vm)
        Box(Modifier.weight(1f).fillMaxWidth()) {
            Content(vm)
            if (vm.perm == true && vm.path != null && vm.search == null && !selecting) {
                FloatingActionButton(
                    onClick = { vm.fabMenu() },
                    containerColor = fd.manila, contentColor = fd.onManila,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
                ) { Icon(Icons2.add, Str.s(R.string.s_sozdat)) }
            }
        }
        if (dockVisible) Dock(vm, selecting) else Spacer(Modifier.navigationBarsPadding())
    }
}

@Composable
private fun TopBar(vm: AppViewModel) {
    val n = vm.selection.size
    val searching = vm.search != null
    val path = vm.path
    Column(Modifier.fillMaxWidth().background(fd.chrome).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            when {
                searching -> FdIconButton(Icons2.back, Str.s(R.string.s_zakryt_poisk), { vm.exitSearch() })
                path != null && vm.perm == true -> FdIconButton(Icons2.back, Str.s(R.string.s_naverh), { vm.up() })
                else -> Box(Modifier.padding(start = 12.dp, end = 8.dp).size(28.dp).clip(RoundedCornerShape(8.dp)).background(fd.manila), contentAlignment = Alignment.Center) {
                    Text("D", color = fd.onManila, fontWeight = FontWeight.ExtraBold)
                }
            }
            Switcher(vm, Modifier.weight(1f))
            if (vm.perm == true) FdIconButton(Icons2.search, Str.s(R.string.s_poisk), { vm.startSearch() })
            FdIconButton(Icons2.settings, Str.s(R.string.s_nastroyki), { vm.openSettings() })
        }
        when {
            n > 0 -> Row(Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                FdIconButton(Icons2.close, Str.s(R.string.s_otmenit_vybor), { vm.clearSelection() })
                Text("$n ${Str.p(R.plurals.word_selected, n)}", color = fd.text, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                FdIconButton(Icons2.selectAll, Str.s(R.string.s_vybrat_vse), { vm.selectAll() })
            }
            searching -> SearchField(vm)
            path != null && vm.perm == true -> Row(Modifier.fillMaxWidth().height(44.dp), verticalAlignment = Alignment.CenterVertically) {
                Crumbs(vm, path, Modifier.weight(1f))
                FdIconButton(
                    if (vm.settings.view == ViewMode.LIST) Icons2.grid else Icons2.list, Str.s(R.string.s_smenit_vid),
                    { vm.updateSettings { it.copy(view = if (it.view == ViewMode.LIST) ViewMode.GRID else ViewMode.LIST) } },
                )
            }
        }
    }
}

@Composable
private fun Switcher(vm: AppViewModel, modifier: Modifier) {
    val cur = vm.path?.let { vm.rootOf(it) }
    val hasSd = vm.roots.any { !it.primary }
    Row(modifier.padding(horizontal = 4.dp).clip(RoundedCornerShape(20.dp)).background(fd.surface3).padding(3.dp)) {
        @Composable
        fun seg(label: String, icon: ImageVector, active: Boolean, dim: Boolean, onClick: () -> Unit) {
            Row(
                Modifier.weight(1f).clip(RoundedCornerShape(17.dp)).background(if (active) fd.manila else Color.Transparent)
                    .clickable(onClick = onClick).padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
            ) {
                val c = if (active) fd.onManila else if (dim) fd.muted.copy(alpha = 0.5f) else fd.text
                Icon(icon, null, tint = c, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(6.dp))
                Text(label, color = c, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            }
        }
        seg(Str.s(R.string.storage_short_internal), Icons2.drive, cur?.primary == true, false) { vm.switchRoot(false) }
        seg(Str.s(R.string.start_sd), Icons2.sd, cur != null && !cur.primary, vm.perm == true && !hasSd) { vm.switchRoot(true) }
    }
}

@Composable
private fun Crumbs(vm: AppViewModel, path: String, modifier: Modifier) {
    val root = vm.rootOf(path)
    val base = root?.path ?: ""
    val parts = path.removePrefix(base).split("/").filter { it.isNotEmpty() }
    val scroll = rememberScrollState()
    LaunchedEffect(path) { scroll.scrollTo(scroll.maxValue) }
    Row(modifier.horizontalScroll(scroll).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        fun tab(label: String, target: String, current: Boolean) = Pair(label, Pair(target, current))
        val tabs = mutableListOf(tab(if (root == null) "/" else if (root.primary) Str.s(R.string.storage_short_internal) else Str.s(R.string.start_sd), base.ifEmpty { "/" }, parts.isEmpty()))
        var acc = base
        parts.forEachIndexed { i, p ->
            acc = joinPath(acc, p)
            tabs.add(tab(p, acc, i == parts.size - 1))
        }
        tabs.forEach { (label, t) ->
            Text(
                label, color = if (t.second) fd.onManila else fd.text, fontSize = 14.sp, maxLines = 1,
                modifier = Modifier.padding(end = 6.dp).clip(RoundedCornerShape(14.dp))
                    .background(if (t.second) fd.manila else fd.surface3)
                    .clickable { vm.go(t.first) }.padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
private fun SearchField(vm: AppViewModel) {
    val s = vm.search ?: return
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(120)
        try { focus.requestFocus() } catch (e: Exception) { Log.w("FileDeck", "ignored") }
    }
    TextField(
        value = s.q, onValueChange = { vm.runSearch(it) }, singleLine = true,
        placeholder = { Text(if (vm.path != null) Str.s(R.string.s_poisk_v_etoy_papke) else Str.s(R.string.s_poisk_po_pamyati)) },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { vm.runSearch(s.q) }),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = fd.surface3, unfocusedContainerColor = fd.surface3,
            focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
            focusedTextColor = fd.text, unfocusedTextColor = fd.text, cursorColor = fd.manila,
        ),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp).focusRequester(focus),
    )
}

@Composable
private fun Content(vm: AppViewModel) {
    when {
        vm.perm == null -> Spinner(Modifier.fillMaxSize())
        vm.perm == false -> PermissionView(vm)
        vm.path == null && vm.search == null -> HomeView(vm)
        else -> FileListView(vm)
    }
}

@Composable
private fun PermissionView(vm: AppViewModel) {
    Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Box(Modifier.size(72.dp).clip(CircleShape).background(fd.manila.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
            Icon(Icons2.lock, null, tint = fd.manila, modifier = Modifier.size(34.dp))
        }
        Spacer(Modifier.height(20.dp))
        Text(Str.s(R.string.s_nuzhen_dostup_ko_vsem_faylam), color = fd.text, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            Str.s(R.string.s_filedeck_pokazyvaet_i_menyaet_fayly),
            color = fd.muted, fontSize = 14.sp, textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        PrimaryButton(Str.s(R.string.s_vydat_dostup), { vm.requestPermission() }, Modifier.fillMaxWidth())
    }
}

private val QUICK = listOf(
    Triple("Download", Str.s(R.string.s_zagruzki), Icons2.download),
    Triple("DCIM", Str.s(R.string.s_kamera), Icons2.camera),
    Triple("Pictures", Str.s(R.string.s_kartinki), Icons2.image),
    Triple("Music", Str.s(R.string.usage_audio), Icons2.audio),
    Triple("Movies", Str.s(R.string.kind_video), Icons2.video),
    Triple("Documents", Str.s(R.string.usage_docs), Icons2.file),
)

@Composable
private fun HomeView(vm: AppViewModel) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (vm.roots.isEmpty()) Text(Str.s(R.string.s_nakopiteli_ne_naydeny), color = fd.muted, modifier = Modifier.padding(16.dp))
            vm.roots.forEach { StorageCard(it) { vm.go(it.path) } }
        }
        Row(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ToolCard(Str.s(R.string.s_ochistka), Str.s(R.string.s_dublikaty_musor_pustye_papki), Icons2.sparkle, true, Modifier.weight(1f)) { vm.openCleaner() }
            ToolCard(Str.s(R.string.s_korzina), vm.trashLabel(), Icons2.delete, false, Modifier.weight(1f)) { vm.openTrash() }
        }
        SectionTitle(Str.s(R.string.s_bystryy_dostup))
        val root = vm.primaryRoot()
        if (root != null) {
            QUICK.chunked(3).forEach { rowItems ->
                Row(Modifier.padding(horizontal = 12.dp, vertical = 5.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    rowItems.forEach { (dir, label, icon) ->
                        Column(
                            Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).background(fd.surface2)
                                .clickable { vm.go(joinPath(root.path, dir)) }.padding(vertical = 16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Icon(icon, null, tint = fd.manila, modifier = Modifier.size(26.dp))
                            Spacer(Modifier.height(6.dp))
                            Text(label, color = fd.text, fontSize = 13.sp)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun StorageCard(r: StorageRoot, onClick: () -> Unit) {
    val low = r.percent > 90
    val accent = if (low) fd.danger else fd.manila
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(fd.surface2).clickable(onClick = onClick).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(if (r.primary) Icons2.drive else Icons2.sd, null, tint = accent, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(10.dp))
            Text(r.name, color = fd.text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text("${r.percent}%", color = accent, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.fillMaxWidth()) {
            for (i in 0 until 28) {
                Box(
                    Modifier.weight(1f).height(14.dp).clip(RoundedCornerShape(3.dp))
                        .background(if ((i + 1) / 28f <= r.percent / 100f) accent else fd.surface3),
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(Str.f(R.string.s_svobodno_iz, formatSize(r.free), formatSize(r.total)), color = fd.muted, fontSize = 13.sp)
    }
}

@Composable
private fun ToolCard(title: String, sub: String, icon: ImageVector, main: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val bg = if (main) fd.manila else fd.surface2
    val fg = if (main) fd.onManila else fd.text
    Column(modifier.clip(RoundedCornerShape(20.dp)).background(bg).clickable(onClick = onClick).padding(16.dp)) {
        Icon(icon, null, tint = if (main) fd.onManila else fd.manila, modifier = Modifier.size(28.dp))
        Spacer(Modifier.height(10.dp))
        Text(title, color = fg, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Text(sub, color = fg.copy(alpha = 0.7f), fontSize = 12.sp)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileListView(vm: AppViewModel) {
    val s = vm.search
    val list = vm.visibleEntries
    val path = vm.path
    if (s != null) {
        when {
            s.loading && list.isEmpty() -> { Spinner(Modifier.fillMaxSize()); return }
            s.q.isBlank() -> { StateView(Icons2.search, Str.s(R.string.s_vvedite_chast_imeni_fayla_ili)); return }
            list.isEmpty() -> { StateView(Icons2.search, Str.s(R.string.s_nichego_ne_naydeno), Str.f(R.string.s_po_zaprosu_faylov_net_proverte, s.q)); return }
        }
    } else {
        when {
            vm.loading && vm.entries.isEmpty() -> { Spinner(Modifier.fillMaxSize()); return }
            vm.error != null -> {
                StateView(Icons2.lock, Str.s(R.string.s_papka_nedostupna), vm.error) { GhostButton(Str.s(R.string.s_nazad), { vm.up() }) }
                return
            }
            list.isEmpty() -> {
                StateView(
                    Icons2.folderFilled, Str.s(R.string.s_zdes_pusto),
                    if (vm.entries.isNotEmpty()) Str.s(R.string.s_v_papke_tolko_skrytye_fayly)
                    else Str.s(R.string.s_sozdayte_papku_ili_fayl_knopkoy),
                )
                return
            }
        }
    }
    val grid = vm.settings.view == ViewMode.GRID && s == null
    val key = path ?: "search"
    val selecting = vm.selection.isNotEmpty()
    val onTap: (FileEntry) -> Unit = { e -> if (selecting) vm.toggleSelect(e.path) else vm.openEntry(e) }
    val onLong: (FileEntry) -> Unit = { e -> vm.toggleSelect(e.path) }

    Column(Modifier.fillMaxSize()) {
        if (s != null) {
            Text(
                meta("${list.size} ${Str.p(R.plurals.word_result, list.size)}", if (s.truncated) Str.s(R.string.search_truncated) else null),
                color = fd.muted, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        if (grid) {
            val state = remember(key) { LazyGridState(vm.scrollMemo[key] ?: 0) }
            DisposableEffect(key) { onDispose { vm.scrollMemo[key] = state.firstVisibleItemIndex } }
            LazyVerticalGrid(
                columns = GridCells.Adaptive(104.dp), state = state, modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 88.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                gridItems(list, key = { it.path }) { e -> GridItem(e, e.path in vm.selection, onTap, onLong) }
            }
        } else {
            val state = remember(key) { LazyListState(vm.scrollMemo[key] ?: 0) }
            DisposableEffect(key) { onDispose { vm.scrollMemo[key] = state.firstVisibleItemIndex } }
            LazyColumn(Modifier.weight(1f), state = state, contentPadding = PaddingValues(bottom = 88.dp)) {
                items(list, key = { it.path }) { e -> ListItem(e, e.path in vm.selection, selecting, onTap, onLong, showPath = s != null) }
            }
        }
    }
}

@Composable
private fun Lead(e: FileEntry, size: Int) {
    val kind = e.kind()
    if (kind == FileKind.Image && !e.dir) {
        AsyncImage(model = File(e.path), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(size.dp).clip(RoundedCornerShape(12.dp)))
    } else KindBadge(kind, size)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ListItem(e: FileEntry, selected: Boolean, selecting: Boolean, onTap: (FileEntry) -> Unit, onLong: (FileEntry) -> Unit, showPath: Boolean) {
    val sub = meta(
        if (e.dir) "${e.count} ${Str.p(R.plurals.word_item, e.count)}" else formatSize(e.size),
        e.mtime.takeIf { it > 0 }?.let { formatDate(it) },
    )
    Row(
        Modifier.fillMaxWidth().background(if (selected) fd.manila.copy(alpha = 0.12f) else Color.Transparent)
            .combinedClickable(onClick = { onTap(e) }, onLongClick = { onLong(e) })
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Lead(e, 46)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(e.name, color = fd.text, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(sub, color = fd.muted, fontSize = 13.sp, maxLines = 1)
            if (showPath) Text(parentOf(e.path), color = fd.muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (selecting) SelectCheck(selected)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GridItem(e: FileEntry, selected: Boolean, onTap: (FileEntry) -> Unit, onLong: (FileEntry) -> Unit) {
    Column(
        Modifier.clip(RoundedCornerShape(14.dp)).background(if (selected) fd.manila.copy(alpha = 0.18f) else fd.surface2)
            .combinedClickable(onClick = { onTap(e) }, onLongClick = { onLong(e) }).padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f), contentAlignment = Alignment.Center) {
            if (e.kind() == FileKind.Image && !e.dir) {
                AsyncImage(model = File(e.path), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(10.dp)))
            } else KindBadge(e.kind(), 56)
            if (selected) Box(Modifier.align(Alignment.TopEnd)) { SelectCheck(true) }
        }
        Text(e.name, color = fd.text, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp))
        Text(if (e.dir) "${e.count} ${Str.p(R.plurals.word_item, e.count)}" else formatSize(e.size), color = fd.muted, fontSize = 11.sp)
    }
}

@Composable
private fun Dock(vm: AppViewModel, selecting: Boolean) {
    Row(
        Modifier.fillMaxWidth().background(fd.chrome).navigationBarsPadding().padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selecting) {
            @Composable
            fun act(icon: ImageVector, label: String, danger: Boolean = false, onClick: () -> Unit) {
                Column(Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(icon, label, tint = if (danger) fd.danger else fd.text)
                    Text(label, color = if (danger) fd.danger else fd.muted, fontSize = 11.sp, maxLines = 1)
                }
            }
            act(Icons2.copy, Str.s(R.string.action_copy)) { vm.toClipboard(ClipMode.COPY) }
            act(Icons2.cut, Str.s(R.string.s_vyrezat)) { vm.toClipboard(ClipMode.MOVE) }
            act(Icons2.share, Str.s(R.string.action_share)) { vm.shareSelected() }
            act(Icons2.delete, Str.s(R.string.action_delete), true) { vm.deletePaths(vm.selection.toList()) }
            act(Icons2.more, Str.s(R.string.s_esche)) { vm.moreForSelection() }
        } else {
            val c = vm.clip ?: return
            Icon(if (c.mode == ClipMode.MOVE) Icons2.cut else Icons2.copy, null, tint = fd.muted, modifier = Modifier.padding(start = 8.dp))
            Text(
                (if (c.mode == ClipMode.MOVE) Str.s(R.string.s_perenesti) else Str.s(R.string.s_skopirovat)) + ": ${c.paths.size}",
                color = fd.text, modifier = Modifier.weight(1f).padding(start = 8.dp),
            )
            TextButton(onClick = { vm.cancelPaste() }) { Text(Str.s(R.string.s_otmena), color = fd.muted) }
            Button(
                onClick = { vm.paste() },
                colors = ButtonDefaults.buttonColors(containerColor = fd.manila, contentColor = fd.onManila),
            ) { Text(Str.s(R.string.s_vstavit_syuda)) }
        }
    }
}
