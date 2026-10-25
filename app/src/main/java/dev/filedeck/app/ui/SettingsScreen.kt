package dev.filedeck.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.filedeck.app.BuildConfig
import dev.filedeck.app.R
import dev.filedeck.app.Settings
import dev.filedeck.app.SortBy
import dev.filedeck.app.StartScreen
import dev.filedeck.app.Str
import dev.filedeck.app.ThemeMode
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(vm: AppViewModel) {
    val titles = listOf(Str.s(R.string.s_obschie), Str.s(R.string.s_fayly), Str.s(R.string.s_o_nas))
    val pager = rememberPagerState(pageCount = { titles.size })
    val scope = rememberCoroutineScope()
    val s = vm.settings
    Column(Modifier.fillMaxSize().background(fd.surface)) {
        FdBar(Str.s(R.string.s_nastroyki), onBack = { vm.closeOverlay(Overlay.Settings) })
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
    SectionTitle(Str.s(R.string.s_tema))
    Group {
        ThemeMode.entries.forEachIndexed { i, m ->
            if (i > 0) RowDivider()
            ChoiceRow(m.label, m.hint, s.theme == m) { vm.updateSettings { it.copy(theme = m) } }
        }
    }
    SectionTitle(Str.s(R.string.s_pri_zapuske_otkryvat))
    Group {
        StartScreen.entries.forEachIndexed { i, m ->
            if (i > 0) RowDivider()
            ChoiceRow(m.label, null, s.start == m) { vm.updateSettings { it.copy(start = m) } }
        }
    }
}

@Composable
private fun FilesPage(vm: AppViewModel, s: Settings) {
    SectionTitle(Str.s(R.string.s_sortirovka))
    Group {
        SortBy.entries.forEach { m ->
            ChoiceRow(m.label, null, s.sortBy == m) { vm.updateSettings { it.copy(sortBy = m) } }
            RowDivider()
        }
        SwitchRow(Str.s(R.string.s_v_obratnom_poryadke), Str.s(R.string.s_novye_i_bolshie_fayly_sverhu),
            s.desc) { v -> vm.updateSettings { it.copy(desc = v) } }
    }
    SectionTitle(Str.s(R.string.s_povedenie))
    Group {
        SwitchRow(Str.s(R.string.s_skrytye_fayly), Str.s(R.string.s_pokazyvat_fayly_i_papki_imena),
            s.hidden) { v -> vm.updateSettings { it.copy(hidden = v) } }
        RowDivider()
        SwitchRow(Str.s(R.string.s_podtverzhdat_udalenie), Str.s(R.string.s_sprashivat_pered_tem_kak_udalit),
            s.confirmDelete) { v -> vm.updateSettings { it.copy(confirmDelete = v) } }
    }
    SectionTitle(Str.s(R.string.s_korzina_i_ochistka))
    Group {
        SwitchRow(Str.s(R.string.s_ispolzovat_korzinu), Str.s(R.string.s_udalennye_fayly_mozhno_vosstanovit_v),
            s.useTrash) { v -> vm.updateSettings { it.copy(useTrash = v) } }
        if (s.useTrash) {
            Settings.TRASH_DAYS.forEach { d ->
                RowDivider()
                ChoiceRow(Str.f(R.string.settings_keep_days, d, Str.p(R.plurals.word_day, d)), null,
                    s.trashDays == d) { vm.updateSettings { it.copy(trashDays = d) } }
            }
        }
    }
    Spacer(Modifier.height(12.dp))
    Group {
        LinkRow(Str.s(R.string.s_otkryt_korzinu), vm.trashLabel().replaceFirstChar { it.uppercase() }) { vm.openTrash() }
        RowDivider()
        LinkRow(Str.s(R.string.s_glubokaya_ochistka), Str.s(R.string.s_dublikaty_pohozhie_foto_musor_pustye)) { vm.openCleaner() }
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
                Text(Str.s(R.string.s_faylovyy_menedzher_bez_lishnego), color = fd.muted, fontSize = 13.sp)
            }
        }
        Spacer(Modifier.height(14.dp))
        Text(Str.f(R.string.s_versiya_sborka, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE), color = fd.text, fontSize = 14.sp)
        Spacer(Modifier.height(10.dp))
        listOf(Str.s(R.string.s_sdelano_v) to "DataLaw", Str.s(R.string.s_platforma) to "Android",
            Str.s(R.string.s_osnova) to "Kotlin · Jetpack Compose").forEach { (k, v) ->
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                Text(k, color = fd.muted, modifier = Modifier.weight(1f))
                Text(v, color = fd.text, fontWeight = FontWeight.Medium)
            }
        }
    }
    SectionTitle(Str.s(R.string.s_dostup_k_faylam))
    val granted = vm.hasAccess()
    Group {
        LinkRow(if (granted) Str.s(R.string.s_dostup_vydan) else Str.s(R.string.s_dostup_ne_vydan),
            if (granted) Str.s(R.string.s_filedeck_mozhet_chitat_i_menyat) else Str.s(R.string.s_bez_dostupa_prilozhenie_ne_uvidit)) {
            if (!granted) vm.requestPermission()
        }
    }
    Spacer(Modifier.height(12.dp))
    Group {
        LinkRow(Str.s(R.string.s_sbrosit_nastroyki), Str.s(R.string.s_vernut_vse_nastroyki_po_umolchaniyu), danger = true) {
            scope.launch {
                if (vm.confirm(Str.s(R.string.s_sbrosit_nastroyki_2), Str.s(R.string.s_fayly_ne_zatragivayutsya),
                    Str.s(R.string.s_sbrosit))) {
                    vm.resetSettings()
                    vm.toast(Str.s(R.string.s_nastroyki_sbrosheny))
                }
            }
        }
    }
}
