package dev.filedeck.app

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.io.IOException

/** Открытие файла в другом приложении и «Поделиться». Ошибки несут готовый текст для пользователя. */
internal object FileOpener {
    @Throws(IOException::class)
    fun open(ctx: Context, path: String) {
        val f = File(path)
        try {
            val uri = uriFor(ctx, f)
            val intent = Intent(Intent.ACTION_VIEW)
            intent.setDataAndType(uri, MimeTypes.of(f.name))
            intent.clipData = ClipData.newRawUri("", uri)
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            ctx.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            throw IOException(Str.s(R.string.error_open_no_app))
        } catch (e: Exception) {
            throw IOException(Str.f(R.string.error_open_failed, e.message))
        }
    }

    @Throws(IOException::class)
    fun share(ctx: Context, paths: List<String>) {
        if (paths.isEmpty()) throw IOException(Str.s(R.string.error_share_nothing))
        val files = paths.map { File(it) }.filter { it.isFile }
        if (files.isEmpty()) throw IOException(Str.s(R.string.error_share_folders))
        try {
            val uris = ArrayList(files.map { uriFor(ctx, it) })
            val intent = if (uris.size == 1) {
                Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris[0]).setType(MimeTypes.of(files[0].name))
            } else {
                Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris).setType("*/*")
            }
            val clip = ClipData.newRawUri("", uris[0])
            for (i in 1 until uris.size) clip.addItem(ClipData.Item(uris[i]))
            intent.clipData = clip
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            val chooser = Intent.createChooser(intent, Str.s(R.string.action_share))
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ctx.startActivity(chooser)
        } catch (e: Exception) {
            throw IOException(Str.f(R.string.error_share_failed, e.message))
        }
    }

    private fun uriFor(ctx: Context, f: File): Uri =
        FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", f)
}
