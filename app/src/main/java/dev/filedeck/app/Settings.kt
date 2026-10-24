package dev.filedeck.app

import android.content.Context
import android.content.SharedPreferences

enum class ViewMode { LIST, GRID }

enum class ThemeMode(val label: String, val hint: String) {
    AUTO("Как в системе", "Тёмная ночью, светлая днём"),
    DARK("Тёмная", "Чистый чёрный — экономит заряд на OLED-экранах"),
    LIGHT("Светлая", "Тёплая светлая тема цвета какао с молоком"),
}

enum class StartScreen(val label: String) {
    HOME("Главный экран"),
    PRIMARY("Память телефона"),
    SD("SD-карта"),
}

/** Настройки пользователя. Неизменяемый объект: меняется через copy и сохраняется целиком. */
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
