package dev.filedeck.app

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.io.File

/** Простой экран на обычных View (работает, даже если сломался Compose). Живёт в отдельном процессе. */
class CrashActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val trace = intent.getStringExtra("trace")
            ?: File(filesDir, "crash.txt").takeIf { it.exists() }?.readText()
            ?: Str.s(R.string.crash_no_data)
        val d = resources.displayMetrics.density
        val pad = (16 * d).toInt()

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(pad, pad * 2, pad, pad)

        val title = TextView(this)
        title.text = Str.s(R.string.crash_title)
        title.textSize = 20f
        root.addView(title)

        val hint = TextView(this)
        hint.text = Str.s(R.string.crash_hint)
        hint.setPadding(0, pad / 2, 0, pad / 2)
        root.addView(hint)

        val buttons = LinearLayout(this)
        buttons.orientation = LinearLayout.HORIZONTAL
        buttons.gravity = Gravity.START

        val copy = Button(this)
        copy.text = Str.s(R.string.action_copy)
        copy.setOnClickListener {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("crash", trace))
            Toast.makeText(this, Str.s(R.string.action_copied), Toast.LENGTH_SHORT).show()
        }
        buttons.addView(copy)

        val share = Button(this)
        share.text = Str.s(R.string.action_share)
        share.setOnClickListener {
            val i = Intent(Intent.ACTION_SEND)
            i.type = "text/plain"
            i.putExtra(Intent.EXTRA_TEXT, trace)
            startActivity(Intent.createChooser(i, Str.s(R.string.crash_share_chooser)))
        }
        buttons.addView(share)

        val close = Button(this)
        close.text = Str.s(R.string.action_close)
        close.setOnClickListener { finishAndRemoveTask() }
        buttons.addView(close)
        root.addView(buttons)

        val body = TextView(this)
        body.text = trace
        body.textSize = 11f
        body.typeface = android.graphics.Typeface.MONOSPACE
        body.setTextIsSelectable(true)
        val scroll = ScrollView(this)
        scroll.addView(body)
        root.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        setContentView(root)
    }
}
