package it.peppedess.salotto.remote

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.media.AudioManager
import android.net.ConnectivityManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import it.peppedess.salotto.MainActivity
import it.peppedess.salotto.service.TvAccessibilityService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.lang.ref.WeakReference
import java.net.Inet4Address
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder

/**
 * Telecomando dal browser: un piccolo server HTTP sulla rete locale.
 * Ogni avvio genera un codice di 4 cifre incluso nel QR, così solo chi lo inquadra può comandare.
 */
object RemoteServer {
    const val PORT = 8765

    @Volatile private var server: ServerSocket? = null
    @Volatile private var token: String = ""
    private val main = Handler(Looper.getMainLooper())

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    fun start(context: Context) {
        if (server != null) return
        val app = context.applicationContext
        token = (1000..9999).random().toString()
        Thread({
            val socket = runCatching { ServerSocket(PORT).apply { reuseAddress = true } }.getOrNull()
            if (socket == null) {
                _running.value = false
                return@Thread
            }
            server = socket
            _running.value = true
            while (!socket.isClosed) {
                val client = runCatching { socket.accept() }.getOrNull() ?: break
                Thread({ handle(app, client) }, "salotto-remote-client").apply { isDaemon = true }.start()
            }
            _running.value = false
        }, "salotto-remote").apply { isDaemon = true }.start()
    }

    fun stop() {
        runCatching { server?.close() }
        server = null
        _running.value = false
    }

    /** Indirizzo da aprire sull'altro telefono, con il codice. */
    fun address(context: Context): String? {
        val ip = localIp(context) ?: return null
        return "http://$ip:$PORT/?t=$token"
    }

    private fun localIp(context: Context): String? = runCatching {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        val props = cm.getLinkProperties(cm.activeNetwork) ?: return null
        props.linkAddresses.map { it.address }
            .firstOrNull { it is Inet4Address && !it.isLoopbackAddress }
            ?.hostAddress
    }.getOrNull()

    fun qr(text: String, size: Int = 512): Bitmap {
        val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, mapOf(EncodeHintType.MARGIN to 1))
        val pixels = IntArray(size * size) { i -> if (matrix.get(i % size, i / size)) Color.BLACK else Color.WHITE }
        return Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
    }

    private fun handle(context: Context, client: Socket) {
        client.use { socket ->
            socket.soTimeout = 5_000
            val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))
            val requestLine = reader.readLine() ?: return
            val parts = requestLine.split(" ")
            if (parts.size < 2) return
            val method = parts[0]
            val target = parts[1]
            var length = 0
            while (true) {
                val line = reader.readLine() ?: break
                if (line.isEmpty()) break
                if (line.startsWith("Content-Length:", ignoreCase = true)) {
                    length = line.substringAfter(":").trim().toIntOrNull() ?: 0
                }
            }
            val path = target.substringBefore("?")
            val query = target.substringAfter("?", "").split("&").mapNotNull {
                val k = it.substringBefore("=", "")
                if (k.isEmpty()) null else k to URLDecoder.decode(it.substringAfter("=", ""), "UTF-8")
            }.toMap()
            val out = socket.getOutputStream()
            val authorized = query["t"] == token && token.isNotEmpty()

            when {
                method == "GET" && (path == "/" || path == "/index.html") -> {
                    if (authorized) respond(out, 200, "text/html; charset=utf-8", RemotePage.html(token))
                    else respond(out, 403, "text/html; charset=utf-8", RemotePage.denied)
                }
                method == "POST" && path == "/k" && authorized -> {
                    val key = query["k"].orEmpty()
                    main.post { RemoteControl.key(context, key) }
                    respond(out, 204, "text/plain", "")
                }
                method == "POST" && path == "/text" && authorized -> {
                    val chars = CharArray(length.coerceIn(0, 2_000))
                    var read = 0
                    while (read < chars.size) {
                        val n = reader.read(chars, read, chars.size - read)
                        if (n < 0) break
                        read += n
                    }
                    val text = String(chars, 0, read).trim()
                    if (text.isNotEmpty()) main.post { RemoteControl.text(context, text) }
                    respond(out, 204, "text/plain", "")
                }
                !authorized && path != "/favicon.ico" -> respond(out, 403, "text/plain", "Codice non valido")
                else -> respond(out, 404, "text/plain", "")
            }
        }
    }

    private fun respond(out: OutputStream, code: Int, type: String, body: String) {
        val bytes = body.toByteArray(Charsets.UTF_8)
        val status = when (code) {
            200 -> "OK"
            204 -> "No Content"
            403 -> "Forbidden"
            else -> "Not Found"
        }
        val head = "HTTP/1.1 $code $status\r\n" +
            "Content-Type: $type\r\n" +
            "Content-Length: ${bytes.size}\r\n" +
            "Cache-Control: no-store\r\n" +
            "Connection: close\r\n\r\n"
        out.write(head.toByteArray(Charsets.UTF_8))
        out.write(bytes)
        out.flush()
    }
}

/** Esegue i comandi del telecomando: dentro Salotto come tasti veri, fuori tramite il Servizio TV. */
object RemoteControl {
    @Volatile var activity: WeakReference<MainActivity>? = null
    @Volatile var resumed: Boolean = false

    private val keyCodes = mapOf(
        "up" to KeyEvent.KEYCODE_DPAD_UP,
        "down" to KeyEvent.KEYCODE_DPAD_DOWN,
        "left" to KeyEvent.KEYCODE_DPAD_LEFT,
        "right" to KeyEvent.KEYCODE_DPAD_RIGHT,
        "ok" to KeyEvent.KEYCODE_DPAD_CENTER,
        "back" to KeyEvent.KEYCODE_BACK,
        "menu" to KeyEvent.KEYCODE_MENU,
    )

    fun key(context: Context, key: String) {
        val act = activity?.get()?.takeIf { resumed && !it.isFinishing }
        val service = TvAccessibilityService.instance?.get()
        when (key) {
            in keyCodes -> {
                val code = keyCodes.getValue(key)
                if (act != null) act.injectKey(code) else service?.remoteKey(key)
            }
            "home" -> {
                if (service?.remoteKey("home") != true) {
                    runCatching {
                        context.startActivity(
                            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                        )
                    }
                }
            }
            "play" -> mediaKey(context, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
            "volup", "voldown", "mute" -> {
                val dir = when (key) {
                    "volup" -> 1
                    "voldown" -> -1
                    else -> 0
                }
                when {
                    service != null -> service.volumeStep(dir)
                    act != null -> act.remoteVolume(dir)
                    else -> {
                        val audio = context.getSystemService(AudioManager::class.java)
                        val adjust = if (dir > 0) AudioManager.ADJUST_RAISE else if (dir < 0) AudioManager.ADJUST_LOWER else AudioManager.ADJUST_TOGGLE_MUTE
                        runCatching { audio?.adjustStreamVolume(AudioManager.STREAM_MUSIC, adjust, AudioManager.FLAG_SHOW_UI) }
                    }
                }
            }
        }
    }

    fun text(context: Context, text: String) {
        val act = activity?.get()?.takeIf { !it.isFinishing }
        if (act != null && resumed) {
            act.remoteSearch(text)
        } else {
            runCatching {
                context.startActivity(
                    Intent(context, MainActivity::class.java)
                        .putExtra(MainActivity.EXTRA_REMOTE_QUERY, text)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                )
            }
        }
    }

    private fun mediaKey(context: Context, code: Int) {
        val audio = context.getSystemService(AudioManager::class.java) ?: return
        val now = SystemClock.uptimeMillis()
        runCatching {
            audio.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, code, 0))
            audio.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, code, 0))
        }
    }
}
