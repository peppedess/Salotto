package it.peppedess.salotto

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Registro dei crash: se Salotto si chiude da solo, al riavvio mostra l'errore
 * con i pulsanti per copiarlo o condividerlo. Usa solo View classiche, così funziona
 * anche quando il problema è nell'interfaccia Compose.
 */
object CrashLog {
    private const val FILE = "last_crash.txt"

    fun install(context: Context) {
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                val trace = StringWriter().also { error.printStackTrace(PrintWriter(it)) }.toString()
                val info = app.packageManager.getPackageInfo(app.packageName, 0)
                val header = buildString {
                    appendLine("Salotto ${info.versionName} · Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
                    appendLine("${Build.MANUFACTURER} ${Build.MODEL}")
                    appendLine(SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ITALIAN).format(Date()))
                    appendLine("Thread: ${thread.name}")
                    appendLine()
                }
                File(app.filesDir, FILE).writeText(header + trace)
            }
            previous?.uncaughtException(thread, error)
        }
    }

    fun pending(context: Context): String? {
        val f = File(context.filesDir, FILE)
        return if (f.exists()) runCatching { f.readText() }.getOrNull() else null
    }

    fun clear(context: Context) {
        File(context.filesDir, FILE).delete()
    }
}

class CrashActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val report = CrashLog.pending(this) ?: run {
            finish()
            return
        }
        val pad = (20 * resources.displayMetrics.density).toInt()

        val title = TextView(this).apply {
            text = "Salotto si è chiuso per un errore"
            textSize = 22f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.WHITE)
        }
        val hint = TextView(this).apply {
            text = "Copia il testo qui sotto e mandalo a chi sviluppa Salotto: serve a correggere il problema."
            textSize = 14f
            setTextColor(Color.argb(190, 255, 255, 255))
            setPadding(0, pad / 3, 0, pad / 2)
        }
        val body = TextView(this).apply {
            text = report
            textSize = 11f
            typeface = Typeface.MONOSPACE
            setTextColor(Color.argb(220, 255, 255, 255))
            setTextIsSelectable(true)
        }
        val scroll = ScrollView(this).apply {
            addView(body)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        }
        val copy = Button(this).apply {
            text = "Copia"
            setOnClickListener {
                getSystemService(ClipboardManager::class.java)
                    ?.setPrimaryClip(ClipData.newPlainText("Errore di Salotto", report))
                Toast.makeText(this@CrashActivity, "Copiato", Toast.LENGTH_SHORT).show()
            }
        }
        val share = Button(this).apply {
            text = "Condividi"
            setOnClickListener {
                val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, report)
                runCatching { startActivity(Intent.createChooser(send, "Condividi l'errore")) }
            }
        }
        val reopen = Button(this).apply {
            text = "Riapri Salotto"
            setOnClickListener {
                CrashLog.clear(this@CrashActivity)
                startActivity(Intent(this@CrashActivity, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                finish()
            }
        }
        val buttons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            setPadding(0, pad / 2, 0, 0)
            addView(copy)
            addView(share)
            addView(reopen)
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(15, 17, 21))
            setPadding(pad, pad, pad, pad)
            addView(title)
            addView(hint)
            addView(scroll)
            addView(buttons)
        }
        setContentView(root)
    }
}
