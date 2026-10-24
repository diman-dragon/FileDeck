package dev.filedeck.app

import android.content.Context
import android.content.SharedPreferences
import androidx.annotation.StringRes

enum class ViewMode { LIST, GRID }

enum class ThemeMode(@StringRes private val labelRes: Int, @StringRes private val hintRes: Int) {
    AUTO(R.string.theme_auto, R.string.theme_auto_hint),
    DARK(R.string.theme_dark, R.string.theme_dark_hint),
    LIGHT(R.string.theme_light, R.string.theme_light_hint),
    ;

    val label: String get() = Str.s(labelRes)
    val hint: String get() = Str.s(hintRes)
}

enum class StartScreen(@StringRes private val labelRes: Int) {
    HOME(R.string.start_home),
    PRIMARY(R.string.start_primary),
    SD(R.string.start_sd),
    ;

    val label: String get() = Str.s(labelRes)
}

data class Settings(
    val view: ViewMode = ViewMode.LIST,
    val sortBy: SortBy = SortBy.NAME,
    val desc: Boolean = false,
    val hidden: Boolean = false,
    val theme: ThemeMode = ThemeMode.AUTO,
    val confirmDelete: Boolean = true,
    val start: StartScreen = StartScreen.HOME,
    val useTrash: Boolean = true,
    val trashDays: Int = 30,
    val cleaner: CleanerOptions = CleanerOptions(),
) {
    companion object {
        val TRASH_DAYS = listOf(7, 30, 90)
    }
}

class SettingsStore(context: Context) {
    private val sp: SharedPreferences = context.getSharedPreferences("filedeck.settings.v3", Context.MODE_PRIVATE)

    private inline fun <reified E : Enum<E>> SharedPreferences.readEnum(key: String, def: E): E {
        val name = getString(key, null) ?: return def
        return enumValues<E>().firstOrNull { it.name == name } ?: def
    }

    fun load(): Settings {
        val d = Settings()
        val dc = CleanerOptions()
        return Settings(
            view = sp.readEnum("view", d.view),
            sortBy = sp.readEnum("sortBy", d.sortBy),
            desc = sp.getBoolean("desc", d.desc),
            hidden = sp.getBoolean("hidden", d.hidden),
            theme = sp.readEnum("theme", d.theme),
            confirmDelete = sp.getBoolean("confirmDelete", d.confirmDelete),
            start = sp.readEnum("start", d.start),
            useTrash = sp.getBoolean("useTrash", d.useTrash),
            trashDays = sp.getInt("trashDays", d.trashDays),
            cleaner = CleanerOptions(
                primary = sp.getBoolean("c.primary", dc.primary),
                sd = sp.getBoolean("c.sd", dc.sd),
                duplicates = sp.getBoolean("c.duplicates", dc.duplicates),
                similar = sp.getBoolean("c.similar", dc.similar),
                junk = sp.getBoolean("c.junk", dc.junk),
                apks = sp.getBoolean("c.apks", dc.apks),
                largest = sp.getBoolean("c.largest", dc.largest),
                emptyFolders = sp.getBoolean("c.emptyFolders", dc.emptyFolders),
                zeroFiles = sp.getBoolean("c.zeroFiles", dc.zeroFiles),
                appFolders = sp.getBoolean("c.appFolders", dc.appFolders),
            ),
        )
    }

    fun save(s: Settings) {
        val c = s.cleaner
        sp.edit()
            .putString("view", s.view.name)
            .putString("sortBy", s.sortBy.name)
            .putBoolean("desc", s.desc)
            .putBoolean("hidden", s.hidden)
            .putString("theme", s.theme.name)
            .putBoolean("confirmDelete", s.confirmDelete)
            .putString("start", s.start.name)
            .putBoolean("useTrash", s.useTrash)
            .putInt("trashDays", s.trashDays)
            .putBoolean("c.primary", c.primary)
            .putBoolean("c.sd", c.sd)
            .putBoolean("c.duplicates", c.duplicates)
            .putBoolean("c.similar", c.similar)
            .putBoolean("c.junk", c.junk)
            .putBoolean("c.apks", c.apks)
            .putBoolean("c.largest", c.largest)
            .putBoolean("c.emptyFolders", c.emptyFolders)
            .putBoolean("c.zeroFiles", c.zeroFiles)
            .putBoolean("c.appFolders", c.appFolders)
            .apply()
    }

    fun reset() {
        sp.edit().clear().apply()
    }
}
