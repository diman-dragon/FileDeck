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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewModelScope
import coil3.compose.AsyncImage
import dev.filedeck.app.CleanCat
import dev.filedeck.app.CleanRow
import dev.filedeck.app.CleanSelection
import dev.filedeck.app.CleanerLogic
import dev.filedeck.app.FileEntry
import dev.filedeck.app.FileKind
import dev.filedeck.app.Fs
import dev.filedeck.app.JUNK_LABEL
import dev.filedeck.app.ScanConfig
import dev.filedeck.app.ScanProgress
import dev.filedeck.app.ScanResult
import dev.filedeck.app.USAGE_LABEL
import dev.filedeck.app.basename
import dev.filedeck.app.formatDate
import dev.filedeck.app.formatSize
import dev.filedeck.app.kindOf
import dev.filedeck.app.parentOf
import dev.filedeck.app.plural
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import java.io.File

enum class CleanView { Setup, Scanning, Summary, Detail }

class CleanerController(val vm: AppViewModel) {
    var view by mutableStateOf(CleanView.Setup)
    var cat by mutableStateOf(CleanCat.Duplicates)
    var result by mutableStateOf<ScanResult?>(null)
    var sel by mutableStateOf<CleanSelection>(emptyMap())
    var progress by mutableStateOf<ScanProgress?>(null)
    var busy by mutableStateOf(false)

    val opts get() = vm.settings.cleaner
    val primary get() = vm.roots.firstOrNull { it.primary } ?: vm.roots.firstOrNull()
    val secondary get() = vm.roots.firstOrNull { !it.primary }

    fun onBack(): Boolean {
        when (view) {
            CleanView.Detail -> view = CleanView.Summary
            CleanView.Scanning -> vm.cleanerSvc.cancel()
            else -> return false
        }
        return true
    }

    fun start() {
        val roots = mutableListOf<String>()
        if (opts.primary) primary?.let { roots.add(it.path) }
        if (opts.sd) secondary?.let { roots.add(it.path) }
        if (roots.isEmpty()) return vm.toast("Выберите, где искать", true)
        val o = opts
        if (!(o.duplicates || o.similar || o.junk || o.apks || o.largest || o.emptyFolders || o.zeroFiles)) {
            return vm.toast("Выберите, что искать", true)
        }
        view = CleanView.Scanning
        progress = ScanProgress("walk", 0, 0, 0, 0, 0, null)
        vm.viewModelScope.launch {
            try {
                val cfg = ScanConfig(o.duplicates, o.similar, o.junk, o.apks, o.largest, o.emptyFolders, o.zeroFiles, o.appFolders)
                val res = vm.cleanerSvc.scan(roots, emptyList(), cfg) { progress = it }
                if (res == null) {
                    vm.toast("Сканирование отменено")
                    view = CleanView.Setup
                } else {
                    result = res
                    sel = CleanerLogic.defaultSelection(res)
                    view = CleanView.Summary
                }
            } catch (e: Exception) {
                vm.toast(e.message ?: "Не удалось выполнить сканирование", true)
                view = CleanView.Setup
            }
        }
    }

    fun close() {
        vm.overlays.removeAll { it is Overlay.Cleaner && it.c === this }
    }

    fun toggleRow(path: String) {
        val cur = sel[cat] ?: emptySet()
        sel = sel + (cat to (if (path in cur) cur - path else cur + path))
    }

    fun toggleAll() {
        val rows = CleanerLogic.rowsOf(result, cat)
        val cur = sel[cat] ?: emptySet()
        sel = sel + (cat to (if (cur.size == rows.size) emptySet() else rows.map { it.path }.toSet()))
    }

    fun locate(path: String) {
        close()
        vm.go(parentOf(path))
    }

    fun view(path: String) {
        if (kindOf(basename(path), false) == FileKind.Image) {
            vm.overlays.add(Overlay.Image(listOf(Fs.describe(File(path), false)), 0))
        } else vm.openPathExternal(path)
    }

    private suspend fun removePaths(paths: List<String>, bytes: Long, label: String) {
        if (paths.isEmpty()) return
        val s = vm.settings
        val describe = "${paths.size} ${plural(paths.size, "объект", "объекта", "объектов")} · ${formatSize(bytes).ifEmpty { "0 Б" }}"
        val mode: String? = if (!s.useTrash) {
            if (vm.confirm("Удалить навсегда?", "$label: $describe. Восстановить будет нельзя.", "Удалить", true)) "permanent" else null
        } else {
            val d = CompletableDeferred<String?>()
            vm.sheet(
                label, describe,
                listOf(
                    SheetItem(Icons2.delete, "Переместить в корзину", "Хранится ${s.trashDays} ${plural(s.trashDays, "день", "дня", "дней")}, можно восстановить") { d.complete("trash") },
                    SheetItem(Icons2.close, "Удалить навсегда", "Сразу освободит место, без восстановления", danger = true) { d.complete("permanent") },
                ),
            )
            // закрытие листа без выбора
            vm.viewModelScope.launch {
                while (!d.isCompleted) {
                    kotlinx.coroutines.delay(150)
                    if (vm.dialog == null) d.complete(null)
                }
            }
            d.await()
        }
        if (mode == null) return
        busy = true
        try {
            val res = vm.withProgress(if (mode == "trash") "Переносим в корзину…" else "Удаляем…") {
                if (mode == "trash") {
                    val r = vm.trashSvc.move(paths)
                    r.done to r.failed
                } else {
                    val r = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { Fs.remove(paths) }
                    r.done to r.failed
                }
            }
            val failed = res.second.size
            val ok = paths.filter { it !in res.second }
            result?.let { r ->
                val (nr, ns) = CleanerLogic.purge(r, sel, ok)
                result = nr
                sel = ns
            }
            vm.toast(
                if (mode == "trash") "В корзине: ${res.first}${if (failed > 0) ", не удалось: $failed" else ""}. Место освободится после очистки корзины."
                else "Удалено: ${res.first}${if (failed > 0) ", не удалось: $failed" else ""} · освобождено ${formatSize(bytes)}",
                failed > 0,
            )
            vm.refreshTrash()
            vm.refresh()
        } catch (e: Exception) {
            vm.toast(e.message ?: "Не удалось удалить", true)
        }
        busy = false
    }

    fun deleteCategory() {
        vm.viewModelScope.launch {
            val rows = CleanerLogic.rowsOf(result, cat).filter { sel[cat]?.contains(it.path) == true }
            if (rows.isEmpty()) return@launch
            if (cat.group) {
                var full = 0
                for (list in CleanerLogic.groupRows(CleanerLogic.rowsOf(result, cat)).values) {
                    if (list.all { sel[cat]?.contains(it.path) == true }) full++
                }
                if (full > 0) {
                    val ok = vm.confirm(
                        "Выбраны все копии",
                        "В $full ${plural(full, "группе", "группах", "группах")} отмечены все экземпляры — файл исчезнет совсем. Продолжить?",
                        "Продолжить",
                    )
                    if (!ok) return@launch
                }
            }
            removePaths(rows.map { it.path }, rows.sumOf { it.size }, cat.title)
        }
    }

    fun quickClean() {
        vm.viewModelScope.launch {
            val paths = CleanerLogic.safePaths(result, sel)
            if (paths.isEmpty()) return@launch vm.toast("Безопасной очистки не нашлось")
            removePaths(paths, CleanerLogic.safeBytes(result, sel), "Быстрая очистка")
        }
    }
}

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
    val vm = c.vm
    val o = c.opts
    FdBar("Очистка", onBack = { c.close() })
    Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons2.sparkle, null, tint = fd.manila, modifier = Modifier.size(34.dp))
            Spacer(Modifier.width(14.dp))
            Text("Найдём дубликаты, мусор, пустые папки и самые тяжёлые файлы. Ничего не удаляется без вашего подтверждения.", color = fd.muted, fontSize = 14.sp)
        }
        SectionTitle("Где искать")
        Group {
            val p = c.primary
            SwitchRow("Память телефона", p?.let { "${formatSize(it.free)} свободно из ${formatSize(it.total)}" }, o.primary, p != null) { v -> vm.updateSettings { it.copy(cleaner = it.cleaner.copy(primary = v)) } }
            c.secondary?.let { s ->
                RowDivider()
                SwitchRow("SD-карта", "${formatSize(s.free)} свободно из ${formatSize(s.total)}", o.sd) { v -> vm.updateSettings { it.copy(cleaner = it.cleaner.copy(sd = v)) } }
            }
        }
        SectionTitle("Что искать")
        Group {
            CleanCat.entries.forEachIndexed { i, cat ->
                if (i > 0) RowDivider()
                val on = when (cat) {
                    CleanCat.Duplicates -> o.duplicates
                    CleanCat.Similar -> o.similar
                    CleanCat.Junk -> o.junk
                    CleanCat.Apks -> o.apks
                    CleanCat.Largest -> o.largest
                    CleanCat.EmptyFolders -> o.emptyFolders
                    CleanCat.ZeroFiles -> o.zeroFiles
                }
                SwitchRow(cat.title, cat.hint, on) { v ->
                    vm.updateSettings {
                        val cl = it.cleaner
                        it.copy(
                            cleaner = when (cat) {
                                CleanCat.Duplicates -> cl.copy(duplicates = v)
                                CleanCat.Similar -> cl.copy(similar = v)
                                CleanCat.Junk -> cl.copy(junk = v)
                                CleanCat.Apks -> cl.copy(apks = v)
                                CleanCat.Largest -> cl.copy(largest = v)
                                CleanCat.EmptyFolders -> cl.copy(emptyFolders = v)
                                CleanCat.ZeroFiles -> cl.copy(zeroFiles = v)
                            },
                        )
                    }
                }
            }
        }
        SectionTitle("Дополнительно")
        Group {
            SwitchRow("Папки приложений", "Android/media: медиа мессенджеров. Удаление может оставить пустые места в чатах", o.appFolders) { v ->
                vm.updateSettings { it.copy(cleaner = it.cleaner.copy(appFolders = v)) }
            }
        }
        Spacer(Modifier.height(20.dp))
    }
    BottomAction { PrimaryButton("Сканировать", { c.start() }, Modifier.fillMaxWidth()) }
}

@Composable
private fun ColumnScope.ScanningView(c: CleanerController) {
    val p = c.progress
    FdBar("Сканирование", onBack = { c.onBack() })
    Column(Modifier.weight(1f).fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(Icons2.sparkle, null, tint = fd.manila, modifier = Modifier.size(48.dp))
        Spacer(Modifier.height(16.dp))
        val phase = when (p?.phase) {
            "walk" -> "Просматриваем файлы…"
            "hash" -> "Сравниваем содержимое…"
            "images" -> "Анализируем фотографии…"
            else -> "Работаем…"
        }
        Text(phase, color = fd.text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(16.dp))
        if (p != null && p.phase != "walk" && p.total > 0) {
            LinearProgressIndicator(progress = { (p.done.toFloat() / p.total).coerceIn(0f, 1f) }, color = fd.manila, trackColor = fd.surface3, modifier = Modifier.fillMaxWidth())
        } else {
            LinearProgressIndicator(color = fd.manila, trackColor = fd.surface3, modifier = Modifier.fillMaxWidth())
        }
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("%,d".format(p?.files ?: 0).replace(',', ' '), color = fd.text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text("файлов", color = fd.muted, fontSize = 13.sp)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(formatSize(p?.bytes ?: 0).ifEmpty { "0 Б" }, color = fd.text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text("просмотрено", color = fd.muted, fontSize = 13.sp)
            }
        }
        val cur = p?.current
        if (!cur.isNullOrEmpty()) {
            Spacer(Modifier.height(16.dp))
            Text(if (cur.length > 52) "…" + cur.takeLast(50) else cur, color = fd.muted, fontSize = 12.sp, maxLines = 1)
        }
    }
    BottomAction { GhostButton("Отмена", { c.onBack() }, Modifier.fillMaxWidth()) }
}

@Composable
private fun ColumnScope.SummaryView(c: CleanerController) {
    val vm = c.vm
    val r = c.result ?: return
    val o = c.opts
    FdBar("Результаты", onBack = { c.close() }) {
        FdIconButton(Icons2.refresh, "Сканировать заново", { c.view = CleanView.Setup })
    }
    val safe = CleanerLogic.safeBytes(r, c.sel)
    Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
        Column(Modifier.padding(20.dp)) {
            Text("Можно безопасно освободить", color = fd.muted, fontSize = 13.sp)
            Text(formatSize(safe).ifEmpty { "0 Б" }, color = fd.manila, fontSize = 34.sp, fontWeight = FontWeight.Bold)
            Text(
                "Просмотрено ${"%,d".format(r.stats.files).replace(',', ' ')} ${plural(r.stats.files, "файл", "файла", "файлов")} · ${formatSize(r.stats.bytes)} · ${"%.1f".format(r.stats.ms / 1000.0)} с",
                color = fd.muted, fontSize = 13.sp,
            )
        }
        val used = USAGE_LABEL.filter { (r.usage[it.first]?.bytes ?: 0) > 0 }
        if (used.isNotEmpty()) {
            Row(Modifier.padding(horizontal = 20.dp).fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp))) {
                used.forEach { (k, _) ->
                    Box(Modifier.weight((r.usage[k]!!.bytes).toFloat().coerceAtLeast(1f)).fillMaxSize().background(usageColor(k)))
                }
            }
            Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                used.sortedByDescending { r.usage[it.first]!!.bytes }.take(5).forEach { (k, l) ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                        Box(Modifier.size(10.dp).clip(RoundedCornerShape(5.dp)).background(usageColor(k)))
                        Spacer(Modifier.width(8.dp))
                        Text("$l  ${formatSize(r.usage[k]!!.bytes)}", color = fd.muted, fontSize = 13.sp)
                    }
                }
            }
        }
        SectionTitle("Найдено")
        val enabled = CleanCat.entries.filter {
            when (it) {
                CleanCat.Duplicates -> o.duplicates
                CleanCat.Similar -> o.similar
                CleanCat.Junk -> o.junk
                CleanCat.Apks -> o.apks
                CleanCat.Largest -> o.largest
                CleanCat.EmptyFolders -> o.emptyFolders
                CleanCat.ZeroFiles -> o.zeroFiles
            }
        }
        if (enabled.isEmpty()) Text("Включите хотя бы одну категорию", color = fd.muted, modifier = Modifier.padding(20.dp))
        Group {
            enabled.forEachIndexed { i, cat ->
                if (i > 0) RowDivider()
                val n = CleanerLogic.countOf(r, cat)
                val bytes = if (cat == CleanCat.Largest) CleanerLogic.rowsOf(r, cat).sumOf { it.size } else CleanerLogic.wasteOf(r, cat)
                val sub = if (n == 0) "Ничего не найдено" else {
                    val unit = if (cat.group) plural(n, "группа", "группы", "групп") else plural(n, "объект", "объекта", "объектов")
                    "$n $unit" + if (bytes > 0) " · ${formatSize(bytes)}" else ""
                }
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
        Spacer(Modifier.height(20.dp))
    }
    BottomAction {
        if (safe > 0) PrimaryButton("Быстрая очистка · ${formatSize(safe)}", { c.quickClean() }, Modifier.fillMaxWidth(), enabled = !c.busy)
        else GhostButton("Сканировать заново", { c.view = CleanView.Setup }, Modifier.fillMaxWidth())
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
        if (rows.isNotEmpty()) TextButton(onClick = { c.toggleAll() }) { Text(if (all) "Снять всё" else "Выбрать всё", color = fd.manila) }
    }
    if (rows.isEmpty()) {
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            StateView(Icons2.check, "Всё чисто", "В этой категории больше ничего нет.")
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
                        Row(Modifier.fillMaxWidth().background(fd.surface2).padding(horizontal = 16.dp, vertical = 8.dp)) {
                            Text("${if (cat == CleanCat.Similar) "Похожие" else "Одинаковые"} · ${list.size}", color = fd.text, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            Text("можно освободить ${formatSize(waste)}", color = fd.muted, fontSize = 13.sp)
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
        if (n > 0) PrimaryButton("Удалить · $n · ${formatSize(bytes).ifEmpty { "0 Б" }}", { c.deleteCategory() }, Modifier.fillMaxWidth(), enabled = !c.busy, danger = true)
        else GhostButton("Ничего не выбрано", {}, Modifier.fillMaxWidth(), enabled = false)
    }
}

@Composable
private fun CleanRowView(c: CleanerController, r: CleanRow, on: Boolean, keepPath: String?) {
    val name = basename(r.path)
    val kind = kindOf(name, r.dir)
    val where = r.path.let { parentOf(it) }.replace(Regex("^/storage/(emulated/0|[^/]+)")) { if (it.groupValues[1] == "emulated/0") "Память" else "SD" }
    val sub = when (c.cat) {
        CleanCat.Junk -> "${JUNK_LABEL[r.kind] ?: "Мусор"} · ${formatSize(r.size)}"
        CleanCat.Apks -> (if (!r.version.isNullOrEmpty()) "v${r.version} · " else "") + formatSize(r.size) + (if (r.mtime > 0) " · ${formatDate(r.mtime)}" else "")
        CleanCat.EmptyFolders -> "пустая папка"
        CleanCat.ZeroFiles -> "0 Б"
        else -> (if (r.w > 0) "${r.w}×${r.h} · " else "") + formatSize(r.size) + (if (r.mtime > 0) " · ${formatDate(r.mtime)}" else "")
    }
    val title = if (c.cat == CleanCat.Apks && !r.label.isNullOrEmpty()) r.label else name
    Row(
        Modifier.fillMaxWidth().clickable { c.toggleRow(r.path) }.background(if (on) fd.manila.copy(alpha = 0.10f) else Color.Transparent).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SelectCheck(on)
        Spacer(Modifier.width(10.dp))
        if (kind == FileKind.Image) {
            AsyncImage(model = File(r.path), contentDescription = null, contentScale = androidx.compose.ui.layout.ContentScale.Crop, modifier = Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)))
        } else KindBadge(kind)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title ?: name, color = fd.text, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (keepPath == r.path) Text("  оставить", color = fd.manila, fontSize = 12.sp)
            }
            Text(sub, color = fd.muted, fontSize = 12.sp, maxLines = 1)
            Text(where, color = fd.muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (!r.dir) FdIconButton(Icons2.eye, "Открыть", { c.view(r.path) })
        FdIconButton(Icons2.folder, "Показать в папке", { c.locate(r.path) })
    }
}
