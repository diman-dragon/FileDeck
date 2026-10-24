package dev.filedeck.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import dev.filedeck.app.BuildConfig
import dev.filedeck.app.FileKind
import dev.filedeck.app.Settings
import dev.filedeck.app.SortBy
import dev.filedeck.app.StartScreen
import dev.filedeck.app.ThemeMode
import dev.filedeck.app.basename
import dev.filedeck.app.formatDate
import dev.filedeck.app.formatSize
import dev.filedeck.app.itemsWord
import dev.filedeck.app.kindOf
import dev.filedeck.app.parentOf
import dev.filedeck.app.plural
import kotlinx.coroutines.launch
import java.io.File

// ======================================================================== настройки

@Composable
fun SettingsScreen(vm: AppViewModel) {
    val titles = listOf("Общие", "Файлы", "О нас")
    val pager = rememberPagerState(pageCount = { titles.size })
    val scope = rememberCoroutineScope()
    val s = vm.settings
    Column(Modifier.fillMaxSize().background(fd.surface)) {
        FdBar("Настройки", onBack = { vm.closeOverlay(Overlay.Settings) })
        PrimaryTabRow(selectedTabIndex = pager.currentPage, containerColor = fd.chrome, contentColor = fd.manila) {
            titles.forEachIndexed { i, t ->
                Tab(
                    selected = pager.currentPage == i, onClick = { scope.launch { pager.animateScrollToPage(i) } },
                    text = { Text(t, color = if (pager.currentPage == i) fd.manila else fd.muted) },
                )
            }
        }
        HorizontalPager(state = pager, modifier = Modifier.weight(1f)) { page ->
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding()) {
                when (page) {
                    0 -> GeneralPage(vm, s)
                    1 -> FilesPage(vm, s)
                    else -> AboutPage(vm)
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun GeneralPage(vm: AppViewModel, s: Settings) {
    SectionTitle("Тема")
    Group {
        ThemeMode.entries.forEachIndexed { i, m ->
            if (i > 0) RowDivider()
            ChoiceRow(m.label, m.hint, s.theme == m) { vm.updateSettings { it.copy(theme = m) } }
        }
    }
    SectionTitle("При запуске открывать")
    Group {
        StartScreen.entries.forEachIndexed { i, m ->
            if (i > 0) RowDivider()
            ChoiceRow(m.label, null, s.start == m) { vm.updateSettings { it.copy(start = m) } }
        }
    }
}

@Composable
private fun FilesPage(vm: AppViewModel, s: Settings) {
    SectionTitle("Сортировка")
    Group {
        SortBy.entries.forEach { m ->
            ChoiceRow(m.label, null, s.sortBy == m) { vm.updateSettings { it.copy(sortBy = m) } }
            RowDivider()
        }
        SwitchRow("В обратном порядке", "Новые и большие файлы сверху, Я → А", s.desc) { v -> vm.updateSettings { it.copy(desc = v) } }
    }
    SectionTitle("Поведение")
    Group {
        SwitchRow("Скрытые файлы", "Показывать файлы и папки, имена которых начинаются с точки", s.hidden) { v -> vm.updateSettings { it.copy(hidden = v) } }
        RowDivider()
        SwitchRow("Подтверждать удаление", "Спрашивать перед тем, как удалить файлы", s.confirmDelete) { v -> vm.updateSettings { it.copy(confirmDelete = v) } }
    }
    SectionTitle("Корзина и очистка")
    Group {
        SwitchRow("Использовать корзину", "Удалённые файлы можно восстановить в течение срока хранения", s.useTrash) { v -> vm.updateSettings { it.copy(useTrash = v) } }
        if (s.useTrash) {
            Settings.TRASH_DAYS.forEach { d ->
                RowDivider()
                ChoiceRow("Хранить $d ${plural(d, "день", "дня", "дней")}", null, s.trashDays == d) { vm.updateSettings { it.copy(trashDays = d) } }
            }
        }
    }
    Spacer(Modifier.height(12.dp))
    Group {
        LinkRow("Открыть корзину", vm.trashLabel().replaceFirstChar { it.uppercase() }) { vm.openTrash() }
        RowDivider()
        LinkRow("Глубокая очистка", "Дубликаты, похожие фото, мусор, пустые папки") { vm.openCleaner() }
    }
}

@Composable
private fun AboutPage(vm: AppViewModel) {
    val scope = rememberCoroutineScope()
    Column(Modifier.padding(12.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(fd.surface2).padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)).background(fd.manila), contentAlignment = Alignment.Center) {
                Text("D", color = fd.onManila, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
            }
            Spacer(Modifier.width(14.dp))
            Column {
                Text("FileDeck", color = fd.text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text("Файловый менеджер без лишнего", color = fd.muted, fontSize = 13.sp)
            }
        }
        Spacer(Modifier.height(14.dp))
        Text("Версия ${BuildConfig.VERSION_NAME} · сборка ${BuildConfig.VERSION_CODE}", color = fd.text, fontSize = 14.sp)
        Spacer(Modifier.height(10.dp))
        listOf("Сделано в" to "DataLaw", "Платформа" to "Android", "Основа" to "Kotlin · Jetpack Compose").forEach { (k, v) ->
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                Text(k, color = fd.muted, modifier = Modifier.weight(1f))
                Text(v, color = fd.text, fontWeight = FontWeight.Medium)
            }
        }
    }
    SectionTitle("Доступ к файлам")
    val granted = vm.hasAccessNow()
    Group {
        LinkRow(if (granted) "Доступ выдан" else "Доступ не выдан", if (granted) "FileDeck может читать и менять файлы на устройстве" else "Без доступа приложение не увидит файлы") {
            if (!granted) vm.requestPermission()
        }
    }
    Spacer(Modifier.height(12.dp))
    Group {
        LinkRow("Сбросить настройки", "Вернуть все настройки по умолчанию", danger = true) {
            scope.launch {
                if (vm.confirm("Сбросить настройки?", "Файлы не затрагиваются.", "Сбросить")) {
                    vm.resetSettings()
                    vm.toast("Настройки сброшены")
                }
            }
        }
    }
}

// ======================================================================== корзина

@Composable
fun TrashScreen(c: TrashController) {
    val vm = c.vm
    val n = c.selected.size
    Column(Modifier.fillMaxSize().background(fd.surface)) {
        FdBar("Корзина", onBack = { vm.overlays.removeAll { it is Overlay.Trash && it.c === c } }) {
            if (c.items.isNotEmpty()) TextButton(onClick = { c.toggleAll() }) { Text(if (n == c.items.size) "Снять всё" else "Выбрать всё", color = fd.manila) }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                c.loading -> Spinner(Modifier.fillMaxSize())
                c.items.isEmpty() -> StateView(Icons2.delete, "Корзина пуста", "Удалённые файлы хранятся здесь ${c.days} ${plural(c.days, "день", "дня", "дней")} и могут быть восстановлены.")
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    item {
                        Text(
                            "${c.items.size} ${itemsWord(c.items.size)} · ${formatSize(c.bytes).ifEmpty { "0 Б" }} · хранение ${c.days} ${plural(c.days, "день", "дня", "дней")}",
                            color = fd.muted, fontSize = 13.sp, modifier = Modifier.padding(16.dp),
                        )
                    }
                    items(c.items, key = { it.id }) { t ->
                        val on = t.id in c.selected
                        val kind = kindOf(t.name, t.dir)
                        val left = maxOf(0L, Math.ceil((t.time + c.days * 86_400_000.0 - System.currentTimeMillis()) / 86_400_000.0).toLong()).toInt()
                        Row(
                            Modifier.fillMaxWidth().clickable { c.toggle(t.id) }.background(if (on) fd.manila.copy(alpha = 0.10f) else Color.Transparent).padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            SelectCheck(on)
                            Spacer(Modifier.width(10.dp))
                            KindBadge(kind)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(t.name, color = fd.text, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("${formatSize(t.size).ifEmpty { "0 Б" }} · удалён ${formatDate(t.time)}", color = fd.muted, fontSize = 12.sp)
                                Text(
                                    "${shortLocation(parentOf(t.orig))} · осталось $left ${plural(left, "день", "дня", "дней")}",
                                    color = fd.muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        }
        if (n > 0) {
            BottomAction {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PrimaryButton("Восстановить · $n", { c.restore() }, Modifier.weight(1f))
                    PrimaryButton("Удалить", { c.delete(false) }, Modifier.weight(1f), danger = true)
                }
            }
        } else if (c.items.isNotEmpty()) {
            BottomAction { GhostButton("Очистить корзину", { c.delete(true) }, Modifier.fillMaxWidth()) }
        }
    }
}

fun shortLocation(dir: String): String =
    dir.replace(Regex("^/storage/(emulated/0|[^/]+)")) { if (it.groupValues[1] == "emulated/0") "Память" else "SD" }.ifEmpty { "/" }

// ======================================================================== просмотр архива

@Composable
fun ArchiveScreen(c: ArchiveBrowserController) {
    val d = c.data
    Column(Modifier.fillMaxSize().background(fd.surface)) {
        FdBar(c.entry.name, onBack = { if (!c.onBack()) c.vm.overlays.removeAll { it is Overlay.ArchiveView && it.c === c } })
        if (d != null) {
            Text(
                "${d.format} · ${d.count} ${itemsWord(d.count.toInt())} · ${formatSize(d.bytes)}" + if (d.encrypted) " · пароль" else "",
                color = fd.muted, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                c.loading -> Spinner(Modifier.fillMaxSize())
                d != null && !d.listing -> StateView(Icons2.archive, "Список недоступен", "Содержимое RAR-архивов показать нельзя, но архив можно распаковать целиком.")
                else -> Column(Modifier.fillMaxSize()) {
                    val parts = c.cwd.split("/").filter { it.isNotEmpty() }
                    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp).horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
                        Crumb("Архив", parts.isEmpty()) { c.cwd = "" }
                        var acc = ""
                        parts.forEachIndexed { i, p ->
                            acc = if (acc.isEmpty()) p else "$acc/$p"
                            val target = acc
                            Crumb(p, i == parts.size - 1) { c.cwd = target }
                        }
                    }
                    val kids = c.children()
                    if (kids.isEmpty()) {
                        StateView(Icons2.folder, "Папка пуста")
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
                                            if (k.dir) "${k.count} ${dev.filedeck.app.filesWord(k.count)} · ${formatSize(k.size)}"
                                            else formatSize(k.size) + (if (k.mtime > 0) " · ${formatDate(k.mtime)}" else ""),
                                            color = fd.muted, fontSize = 12.sp,
                                        )
                                    }
                                    Box(Modifier.clickable { c.toggle(k.path) }) { SelectCheck(on) }
                                }
                            }
                        }
                        if (d?.truncated == true) Text("Показаны первые 20 000 записей", color = fd.muted, fontSize = 12.sp, modifier = Modifier.padding(12.dp))
                    }
                }
            }
        }
        if (d != null) {
            BottomAction {
                val n = c.selected.size
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (d.listing) GhostButton("Распаковать всё", { c.extract(emptySet()) }, Modifier.weight(1f))
                    if (n > 0) PrimaryButton("Выбранное: $n", { c.extract(c.selected) }, Modifier.weight(1f))
                    else if (!d.listing) PrimaryButton("Распаковать архив", { c.extract(emptySet()) }, Modifier.weight(1f))
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

// ======================================================================== просмотр изображений

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
                Modifier.fillMaxWidth().background(Color(0x99000000)).statusBarsPadding().height(56.dp).padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FdIconButton(Icons2.back, "Назад", { vm.closeOverlay(o) }, tint = Color.White)
                Column(Modifier.weight(1f)) {
                    Text(cur.name, color = Color.White, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        (if (cur.size > 0) formatSize(cur.size) + " · " else "") + (if (cur.mtime > 0) formatDate(cur.mtime, true) else "") +
                            "  ${pager.currentPage + 1} / ${o.images.size}",
                        color = Color(0xFFCCCCCC), fontSize = 12.sp,
                    )
                }
                FdIconButton(Icons2.share, "Поделиться", { vm.sharePath(cur.path) }, tint = Color.White)
                FdIconButton(Icons2.external, "Открыть в приложении", { vm.openPathExternal(cur.path) }, tint = Color.White)
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

// ======================================================================== текстовый редактор

@Composable
fun EditorScreen(c: EditorController) {
    Column(Modifier.fillMaxSize().background(fd.surface).imePadding()) {
        FdBar(c.entry.name + if (c.dirty) " •" else "", onBack = { c.vm.onBack() }) {
            TextButton(onClick = { c.save() }, enabled = c.dirty) {
                Text("Сохранить", color = if (c.dirty) fd.manila else fd.muted, fontWeight = FontWeight.SemiBold)
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

// ======================================================================== корень приложения

@Composable
fun AppRoot(vm: AppViewModel) {
    val snackbar = remember { androidx.compose.material3.SnackbarHostState() }
    LaunchedEffect(Unit) {
        vm.toasts.collect { t ->
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showSnackbar(t.text, duration = androidx.compose.material3.SnackbarDuration.Short)
        }
    }
    androidx.activity.compose.BackHandler(enabled = true) { vm.onBack() }
    Box(Modifier.fillMaxSize().background(fd.surface)) {
        MainScreen(vm)
        val top = vm.overlays.lastOrNull()
        if (top != null) {
            Box(Modifier.fillMaxSize().background(fd.surface).pointerInput(Unit) {}) {
                when (top) {
                    Overlay.Settings -> SettingsScreen(vm)
                    is Overlay.Cleaner -> CleanerScreen(top.c)
                    is Overlay.Trash -> TrashScreen(top.c)
                    is Overlay.ArchiveView -> ArchiveScreen(top.c)
                    is Overlay.Image -> ImageScreen(vm, top)
                    is Overlay.Editor -> EditorScreen(top.c)
                }
            }
        }
        androidx.compose.material3.SnackbarHost(
            snackbar, modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 72.dp),
        )
        DialogHost(vm)
    }
}
