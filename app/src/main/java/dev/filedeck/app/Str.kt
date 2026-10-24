package dev.filedeck.app

import android.content.Context
import androidx.annotation.ArrayRes
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes

/** Строки из ресурсов в местах без Context. Инициализируется в FileDeckApp; до инициализации (юнит-тесты) возвращает id. */
object Str {
    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun s(@StringRes id: Int): String = appContext?.getString(id) ?: "string#$id"

    fun f(@StringRes id: Int, vararg args: Any?): String = appContext?.getString(id, *args) ?: "string#$id"

    fun p(@PluralsRes id: Int, n: Long): String =
        appContext?.resources?.getQuantityString(id, n.coerceIn(0, Int.MAX_VALUE.toLong()).toInt()) ?: "plurals#$id"

    fun p(@PluralsRes id: Int, n: Int): String = p(id, n.toLong())

    fun array(@ArrayRes id: Int): Array<String> = appContext?.resources?.getStringArray(id) ?: emptyArray()
}
