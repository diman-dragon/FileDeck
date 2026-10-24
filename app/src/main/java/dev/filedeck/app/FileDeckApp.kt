package dev.filedeck.app

import android.app.Application
import android.content.Intent
import android.os.Build
import android.os.Process
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import kotlin.system.exitProcess

/**
 * При любом необработанном исключении показывает отдельный экран с текстом ошибки
 * (его можно скопировать или отправить), вместо тихого закрытия приложения.
 */
class FileDeckApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                val sw = StringWriter()
                error.printStackTrace(PrintWriter(sw))
                val text = ("FileDeck ${BuildConfig.VERSION_NAME}\n" +
                    "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}), ${Build.MANUFACTURER} ${Build.MODEL}\n" +
                    "Поток: ${thread.name}\n\n$sw").take(60_000)
                try {
                    File(filesDir, "crash.txt").writeText(text)
                } catch (ignored: Throwable) {
                }
                val i = Intent(this, CrashActivity::class.java)
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                i.putExtra("trace", text)
                startActivity(i)
            } catch (ignored: Throwable) {
            }
            Process.killProcess(Process.myPid())
            exitProcess(10)
        }
    }
}
