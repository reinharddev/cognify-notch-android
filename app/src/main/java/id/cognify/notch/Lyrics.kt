package id.cognify.notch

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.URL
import java.net.URLEncoder
import java.net.UnknownHostException
import java.security.MessageDigest
import kotlin.math.abs

/**
 * Lirik tersinkron dari LRCLIB (lrclib.net). Yang dikirim hanya judul, artis, album, dan durasi,
 * dan hanya saat panel musik dibuka. Hasilnya disimpan di cache app, jadi lagu yang sama tidak diminta dua kali.
 */
object Lyrics {
    class Line(val ms: Long, val text: String)
    sealed interface S {
        data object Idle : S
        data object Loading : S
        class Synced(val lines: List<Line>) : S
        class Plain(val text: String) : S
        data object Instrumental : S
        data object Missing : S     // memang belum ada di LRCLIB
        data object Offline : S     // tidak ada koneksi / timeout
        data object Unavailable : S // LRCLIB membalas error server
    }

    var state: S by mutableStateOf(S.Idle); private set
    private var key: String? = null
    private var job: Job? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun load(ctx: Context, t: Track) {
        val next = "${t.title}\u001F${t.artist}\u001F${t.album}"
        val failed = state is S.Offline || state is S.Unavailable
        if (next == key && !(failed && job?.isActive != true)) return // gagal sebelumnya: coba lagi saat dibuka
        key = next
        job?.cancel()
        state = S.Loading
        val dir = File(ctx.applicationContext.cacheDir, "lyrics").also { it.mkdirs() }
        job = scope.launch {
            // Gangguan sesaat: tampilkan hasil gagalnya langsung, lalu coba lagi diam-diam sampai 3 kali.
            var r = fetch(dir, t)
            publish(next, r)
            for (wait in longArrayOf(4, 12, 30)) {
                if (r !is S.Offline && r !is S.Unavailable) break
                delay(wait * 1000)
                r = fetch(dir, t)
                publish(next, r)
            }
        }
    }

    private suspend fun publish(forKey: String, r: S) {
        if (key == forKey) withContext(Dispatchers.Main) { state = r }
    }

    private fun fetch(dir: File, t: Track): S {
        val cache = File(dir, sha("${t.title}\u001F${t.artist}".lowercase()) + ".json")
        if (cache.exists()) {
            val text = cache.readText()
            if (text.isEmpty()) return S.Missing // sudah pernah dicari, tidak ada
            runCatching { return fromRecord(JSONObject(text)) }
        }
        // Pencocokan persis kadang dibalas error server oleh LRCLIB (503): lanjut ke pencarian.
        var exact: JSONObject? = null
        try {
            exact = request(url("get", t, true)) as? JSONObject
        } catch (e: IOException) {
            if (isOffline(e)) return S.Offline
        }
        if (exact != null && hasLyrics(exact)) {
            cache.writeText(exact.toString())
            return fromRecord(exact)
        }
        val found = try {
            request(url("search", t, false)) as? JSONArray
        } catch (e: IOException) {
            android.util.Log.d("Lyrics", "search gagal: $e")
            return if (isOffline(e)) S.Offline else S.Unavailable
        }
        val target = t.durationMs / 1000.0
        var best: JSONObject? = null
        var bestScore = Double.MAX_VALUE
        for (i in 0 until (found?.length() ?: 0)) {
            val o = found!!.optJSONObject(i) ?: continue
            if (!hasLyrics(o)) continue
            val score = abs(o.optDouble("duration", target) - target) + (if (str(o, "syncedLyrics") == null) 1000 else 0)
            if (score < bestScore) { best = o; bestScore = score }
        }
        if (best != null && (target <= 0 || abs(best.optDouble("duration", target) - target) < 8)) {
            cache.writeText(best.toString())
            return fromRecord(best)
        }
        cache.writeText("")
        return S.Missing
    }

    private fun url(kind: String, t: Track, withExtra: Boolean): URL {
        fun q(k: String, v: String) = "$k=" + URLEncoder.encode(v, "UTF-8")
        val parts = mutableListOf(q("track_name", t.title), q("artist_name", t.artist))
        if (withExtra) {
            if (t.album.isNotEmpty()) parts += q("album_name", t.album)
            if (t.durationMs > 0) parts += q("duration", Math.round(t.durationMs / 1000.0).toString())
        }
        return URL("https://lrclib.net/api/$kind?" + parts.joinToString("&"))
    }

    /** GET JSON; null bila 404; IOException bila error lain. */
    private fun request(url: URL): Any? {
        val c = url.openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 10_000
            c.readTimeout = 10_000
            c.setRequestProperty("User-Agent", "Cognify Notch Android (https://github.com/reinharddev/cognify-notch)")
            val code = c.responseCode
            if (code == 404) return null
            if (code !in 200..299) {
                android.util.Log.d("Lyrics", "HTTP $code ${url.path} ${url.query}")
                throw IOException("HTTP $code")
            }
            val body = c.inputStream.bufferedReader().readText().trim()
            return if (body.startsWith("[")) JSONArray(body) else JSONObject(body)
        } finally {
            c.disconnect()
        }
    }

    private fun isOffline(e: IOException) =
        e is UnknownHostException || e is SocketTimeoutException || e is ConnectException || e is NoRouteToHostException

    private fun str(o: JSONObject, k: String): String? = if (o.isNull(k)) null else o.optString(k).takeIf { it.isNotBlank() }

    private fun hasLyrics(o: JSONObject) = str(o, "syncedLyrics") != null || str(o, "plainLyrics") != null || o.optBoolean("instrumental", false)

    private fun fromRecord(o: JSONObject): S {
        if (o.optBoolean("instrumental", false)) return S.Instrumental
        str(o, "syncedLyrics")?.let { s -> parse(s).takeIf { it.isNotEmpty() }?.let { return S.Synced(it) } }
        str(o, "plainLyrics")?.let { return S.Plain(it.trim()) }
        return S.Missing
    }

    private val stamp = Regex("""\[(\d+):(\d+(?:\.\d+)?)]""")

    /** "[01:02.34] teks" (satu baris bisa punya beberapa waktu) → baris berurutan menurut waktu. */
    fun parse(lrc: String): List<Line> {
        val out = mutableListOf<Line>()
        for (row in lrc.lines()) {
            val ms = stamp.findAll(row).toList()
            val last = ms.lastOrNull() ?: continue
            val text = row.substring(last.range.last + 1).trim()
            for (m in ms) out += Line(((m.groupValues[1].toDouble() * 60 + m.groupValues[2].toDouble()) * 1000).toLong(), text)
        }
        return out.sortedBy { it.ms }
    }

    private fun sha(s: String) =
        MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }.take(32)
}
