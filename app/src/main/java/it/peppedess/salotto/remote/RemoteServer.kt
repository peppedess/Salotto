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
import java.io.InputStream
import java.io.OutputStream
import java.lang.ref.WeakReference
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder

/**
 * Telecomando dal browser: un piccolo server HTTP sulla rete locale.
 * Ogni avvio genera un codice di 6 cifre incluso nel QR, così solo chi lo inquadra può comandare;
 * dopo troppi tentativi sbagliati il server smette di accettare codici.
 */
object RemoteServer {
    const val PORT = 8765

    @Volatile private var server: ServerSocket? = null
    @Volatile private var token: String = ""
    private val starting = java.util.concurrent.atomic.AtomicBoolean(false)
    private val failures = java.util.concurrent.atomic.AtomicInteger(0)
    private val main = Handler(Looper.getMainLooper())

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    fun start(context: Context) {
        // Una sola accensione alla volta: due avvii ravvicinati non si contendono la porta.
        if (server != null || !starting.compareAndSet(false, true)) return
        val app = context.applicationContext
        token = (100000..999999).random().toString()
        failures.set(0)
        Thread({
            val socket = runCatching { ServerSocket().apply { reuseAddress = true; bind(InetSocketAddress(PORT)) } }.getOrNull()
            starting.set(false)
            if (socket == null) {
                _running.value = false
                return@Thread
            }
            server = socket
            _running.value = true
            while (!socket.isClosed) {
                val client = runCatching { socket.accept() }.getOrNull() ?: break
                Thread({
                    // Un client che si disconnette o manda dati strani non deve mai far chiudere Salotto.
                    runCatching { handle(app, client) }
                    runCatching { client.close() }
                }, "salotto-remote-client").apply { isDaemon = true }.start()
            }
            // Solo il server ancora attuale aggiorna lo stato: uno nuovo potrebbe essere già partito.
            if (server === socket || server == null) {
                server = null
                _running.value = false
            }
        }, "salotto-remote").apply { isDaemon = true }.start()
    }

    fun stop() {
        val s = server
        server = null
        runCatching { s?.close() }
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

    /** Legge una riga dell'intestazione HTTP byte per byte, con un limite di lunghezza. */
    private fun readLine(input: InputStream): String? {
        val buf = java.io.ByteArrayOutputStream()
        while (true) {
            val b = input.read()
            if (b < 0) return if (buf.size() == 0) null else buf.toString("UTF-8")
            if (b == '\n'.code) break
            if (b != '\r'.code) buf.write(b)
            if (buf.size() > 8_192) return null
        }
        return buf.toString("UTF-8")
    }

    private fun handle(context: Context, socket: Socket) {
        socket.soTimeout = 5_000
        val input = java.io.BufferedInputStream(socket.getInputStream())
        val requestLine = readLine(input) ?: return
        val parts = requestLine.split(" ")
        if (parts.size < 2) return
        val method = parts[0]
        val target = parts[1]
        var length = 0
        var headers = 0
        while (headers++ < 100) {
            val line = readLine(input) ?: break
            if (line.isEmpty()) break
            if (line.startsWith("Content-Length:", ignoreCase = true)) {
                length = line.substringAfter(":").trim().toIntOrNull() ?: 0
            }
        }
        val path = target.substringBefore("?")
        val query = target.substringAfter("?", "").split("&").mapNotNull {
            val k = it.substringBefore("=", "")
            if (k.isEmpty()) null else k to runCatching { URLDecoder.decode(it.substringAfter("=", ""), "UTF-8") }.getOrDefault("")
        }.toMap()
        val out = socket.getOutputStream()

        // Dopo troppi codici sbagliati il telecomando si blocca fino al prossimo avvio.
        val locked = failures.get() >= MAX_FAILURES
        val authorized = !locked && token.isNotEmpty() && query["t"] == token
        if (!authorized && query.containsKey("t")) failures.incrementAndGet()

        when {
            method == "GET" && (path == "/" || path == "/index.html") -> {
                if (authorized) respond(out, 200, "text/html; charset=utf-8", RemotePage.html(token))
                else respond(out, 403, "text/html; charset=utf-8", RemotePage.denied)
            }
            method == "POST" && path == "/k" && authorized -> {
                val key = query["k"].orEmpty()
                val ok = onMain { RemoteControl.key(context, key) } != false
                respond(out, if (ok) 204 else 409, "text/plain", "")
            }
            method == "POST" && (path == "/m" || path == "/s") && authorized -> {
                // Touchpad: spostamento del puntatore (/m) o scorrimento a due dita (/s).
                val dx = query["dx"]?.toFloatOrNull()?.coerceIn(-400f, 400f) ?: 0f
                val dy = query["dy"]?.toFloatOrNull()?.coerceIn(-400f, 400f) ?: 0f
                val ok = onMain { if (path == "/m") RemoteControl.move(dx, dy) else RemoteControl.scroll(dx, dy) } == true
                respond(out, if (ok) 204 else 409, "text/plain", "")
            }
            method == "POST" && path == "/type" && authorized -> {
                // Tastiera: il testo sostituisce quello del campo selezionato nell'app aperta.
                val text = readBody(input, length)
                val ok = onMain { RemoteControl.type(context, text) } == true
                respond(out, if (ok) 204 else 409, "text/plain", "")
            }
            method == "POST" && path == "/text" && authorized -> {
                val text = readBody(input, length).trim().take(200)
                if (text.isNotEmpty()) main.post { runCatching { RemoteControl.text(context, text) } }
                respond(out, 204, "text/plain", "")
            }
            !authorized && path != "/favicon.ico" -> respond(out, 403, "text/plain; charset=utf-8", "Codice non valido")
            else -> respond(out, 404, "text/plain", "")
        }
    }

    /** Corpo della richiesta letto in byte: le lettere accentate occupano più di un byte. */
    private fun readBody(input: InputStream, length: Int): String {
        val bytes = ByteArray(length.coerceIn(0, 4_000))
        var read = 0
        while (read < bytes.size) {
            val n = input.read(bytes, read, bytes.size - read)
            if (n < 0) break
            read += n
        }
        return String(bytes, 0, read, Charsets.UTF_8).take(500)
    }

    /** Esegue sul thread principale e aspetta l'esito (al massimo un secondo e mezzo). */
    private fun <T> onMain(block: () -> T): T? {
        val task = java.util.concurrent.FutureTask { runCatching(block).getOrNull() }
        main.post(task)
        return runCatching { task.get(1_500, java.util.concurrent.TimeUnit.MILLISECONDS) }.getOrNull()
    }

    private fun respond(out: OutputStream, code: Int, type: String, body: String) {
        val bytes = body.toByteArray(Charsets.UTF_8)
        val status = when (code) {
            200 -> "OK"
            204 -> "No Content"
            403 -> "Forbidden"
            409 -> "Conflict"
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

    private const val MAX_FAILURES = 30
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

    /** Restituisce false se il comando non si può eseguire (es. serve il Servizio TV). */
    fun key(context: Context, key: String): Boolean {
        val act = activity?.get()?.takeIf { resumed && !it.isFinishing }
        val service = TvAccessibilityService.instance?.get()
        when (key) {
            in keyCodes -> {
                val code = keyCodes.getValue(key)
                return if (act != null) {
                    act.injectKey(code)
                    true
                } else {
                    service?.remoteKey(key) == true
                }
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
            "tap", "longpress" -> return service?.tapCursor(long = key == "longpress") == true
            "enter" -> {
                if (act != null) {
                    act.injectKey(KeyEvent.KEYCODE_ENTER)
                    return true
                }
                return service?.imeEnter() == true
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
        return true
    }

    /** Touchpad: serve il Servizio TV (disegna il puntatore e tocca al posto tuo). */
    fun move(dx: Float, dy: Float): Boolean {
        val service = TvAccessibilityService.instance?.get() ?: return false
        service.moveCursor(dx, dy)
        return true
    }

    fun scroll(dx: Float, dy: Float): Boolean =
        TvAccessibilityService.instance?.get()?.scrollAtCursor(dx, dy) == true

    /**
     * Tastiera: in Salotto il testo va nella ricerca (che si aggiorna mentre scrivi),
     * nelle altre app nel campo di testo selezionato.
     */
    fun type(context: Context, text: String): Boolean {
        val act = activity?.get()?.takeIf { resumed && !it.isFinishing }
        if (act != null) {
            if (text.isNotBlank()) act.remoteSearch(text.take(200))
            return true
        }
        return TvAccessibilityService.instance?.get()?.typeText(text) == true
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
