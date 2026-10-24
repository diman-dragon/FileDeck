package dev.filedeck.app.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableSharedFlow

class SheetItem(
    val icon: ImageVector? = null,
    val label: String = "",
    val hint: String? = null,
    val danger: Boolean = false,
    val divider: Boolean = false,
    val onClick: () -> Unit = {},
)

/** Вариант выбора для [UiHostApi.choose]: возвращает [value], если выбран. */
class Choice<T : Any>(
    val value: T,
    val label: String,
    val icon: ImageVector? = null,
    val hint: String? = null,
    val danger: Boolean = false,
)

data class ArchiveOptions(val name: String, val format: String, val level: Int, val password: String)

class InfoState(val title: String, initial: List<Pair<String, String>>) {
    val rows = mutableStateListOf<Pair<String, String>>().apply { addAll(initial) }

    fun set(key: String, value: String) {
        val i = rows.indexOfFirst { it.first == key }
        if (i >= 0) rows[i] = key to value
    }
}

sealed interface Dlg {
    class Confirm(
        val title: String,
        val text: String,
        val okText: String,
        val danger: Boolean,
        val result: CompletableDeferred<Boolean>,
    ) : Dlg

    class Prompt(
        val title: String,
        val text: String?,
        val value: String,
        val placeholder: String,
        val okText: String,
        val selectBase: Boolean,
        val password: Boolean,
        val error: String,
        val result: CompletableDeferred<String?>,
    ) : Dlg

    class Info(val state: InfoState) : Dlg

    /** onCancel вызывается, если лист закрыт без выбора. */
    class Sheet(
        val title: String,
        val subtitle: String?,
        val items: List<SheetItem>,
        val onCancel: (() -> Unit)? = null,
    ) : Dlg

    class CreateArchive(
        val defaultName: String,
        val count: Int,
        val result: CompletableDeferred<ArchiveOptions?>,
    ) : Dlg
}

class ProgressState(
    val title: String,
    val fraction: Float?,
    val current: String?,
    val cancel: (() -> Unit)?,
)

data class ToastMsg(val text: String, val error: Boolean)

/** Всё, что ViewModel умеет показать пользователю поверх экрана. */
interface UiHostApi {
    var dialog: Dlg?
    val progress: ProgressState?
    val toasts: MutableSharedFlow<ToastMsg>

    fun toast(text: String, error: Boolean = false)

    suspend fun confirm(title: String, text: String, okText: String, danger: Boolean = false): Boolean

    /** Возвращает null, если пользователь отменил. */
    suspend fun prompt(
        title: String,
        value: String = "",
        okText: String = "OK",
        text: String? = null,
        placeholder: String = "",
        selectBase: Boolean = false,
        password: Boolean = false,
        error: String = "",
    ): String?

    fun sheet(title: String, subtitle: String? = null, items: List<SheetItem>)

    /** Лист с вариантами; возвращает выбранное значение или null, если закрыт без выбора. */
    suspend fun <T : Any> choose(title: String, subtitle: String?, options: List<Choice<T>>): T?

    suspend fun archiveOptions(defaultName: String, count: Int): ArchiveOptions?

    /** Окно с полосой прогресса. update(доля, текущий файл) можно вызывать из любого потока. */
    suspend fun <T> withProgress(
        title: String,
        cancel: (() -> Unit)? = null,
        block: suspend (update: (Double?, String?) -> Unit) -> T,
    ): T
}

class UiHost : UiHostApi {
    override var dialog by mutableStateOf<Dlg?>(null)

    override var progress by mutableStateOf<ProgressState?>(null)

    override val toasts = MutableSharedFlow<ToastMsg>(extraBufferCapacity = 16)

    override fun toast(text: String, error: Boolean) {
        toasts.tryEmit(ToastMsg(text, error))
    }

    override suspend fun confirm(title: String, text: String, okText: String, danger: Boolean): Boolean {
        val d = CompletableDeferred<Boolean>()
        dialog = Dlg.Confirm(title, text, okText, danger, d)
        try {
            return d.await()
        } finally {
            dialog = null
        }
    }

    override suspend fun prompt(
        title: String,
        value: String,
        okText: String,
        text: String?,
        placeholder: String,
        selectBase: Boolean,
        password: Boolean,
        error: String,
    ): String? {
        val d = CompletableDeferred<String?>()
        dialog = Dlg.Prompt(title, text, value, placeholder, okText, selectBase, password, error, d)
        try {
            return d.await()
        } finally {
            dialog = null
        }
    }

    override fun sheet(title: String, subtitle: String?, items: List<SheetItem>) {
        dialog = Dlg.Sheet(title, subtitle, items)
    }

    override suspend fun <T : Any> choose(title: String, subtitle: String?, options: List<Choice<T>>): T? {
        val d = CompletableDeferred<T?>()
        dialog = Dlg.Sheet(
            title, subtitle,
            options.map { o -> SheetItem(o.icon, o.label, o.hint, o.danger) { d.complete(o.value) } },
            onCancel = { d.complete(null) },
        )
        try {
            return d.await()
        } finally {
            dialog = null
        }
    }

    override suspend fun archiveOptions(defaultName: String, count: Int): ArchiveOptions? {
        val d = CompletableDeferred<ArchiveOptions?>()
        dialog = Dlg.CreateArchive(defaultName, count, d)
        try {
            return d.await()
        } finally {
            dialog = null
        }
    }

    override suspend fun <T> withProgress(
        title: String,
        cancel: (() -> Unit)?,
        block: suspend (update: (Double?, String?) -> Unit) -> T,
    ): T {
        progress = ProgressState(title, null, null, cancel)
        try {
            return block { f, c -> progress = ProgressState(title, f?.toFloat(), c, cancel) }
        } finally {
            progress = null
        }
    }
}
