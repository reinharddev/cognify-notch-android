package id.cognify.notch

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

data class Task(val id: String, val text: String, val due: Long?, val done: Boolean, val doneAt: Long?, val notified: Boolean)

/** Daftar tugas, disimpan sebagai satu berkas JSON kecil di penyimpanan app. Hanya ditulis saat ada perubahan. */
object TaskStore {
    val items = mutableStateListOf<Task>()
    private var loaded = false

    private fun file(ctx: Context) = File(ctx.applicationContext.filesDir, "tasks.json")

    @Synchronized
    fun load(ctx: Context) {
        if (loaded) return
        loaded = true
        val f = file(ctx)
        if (!f.exists()) return
        runCatching {
            val a = JSONArray(f.readText())
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                items += Task(
                    o.getString("id"), o.getString("text"),
                    if (o.isNull("due")) null else o.getLong("due"),
                    o.optBoolean("done"), if (o.isNull("doneAt")) null else o.getLong("doneAt"), o.optBoolean("notified"),
                )
            }
        }
    }

    private fun save(ctx: Context) {
        val a = JSONArray()
        for (t in items) a.put(JSONObject().put("id", t.id).put("text", t.text).put("due", t.due).put("done", t.done).put("doneAt", t.doneAt).put("notified", t.notified))
        file(ctx).writeText(a.toString())
    }

    fun add(ctx: Context, text: String, now: Long = System.currentTimeMillis()) {
        load(ctx)
        val clean = text.trim()
        if (clean.isEmpty()) return
        val t = Task(UUID.randomUUID().toString(), clean, DueParser.parseDue(clean), false, null, false)
        items += t
        save(ctx)
        Reminders.schedule(ctx, t, now)
    }

    fun toggle(ctx: Context, id: String) {
        load(ctx)
        val i = items.indexOfFirst { it.id == id }.takeIf { it >= 0 } ?: return
        val t = items[i]
        val done = !t.done
        items[i] = t.copy(done = done, doneAt = if (done) System.currentTimeMillis() else null)
        save(ctx)
        if (done) Reminders.cancel(ctx, id) else Reminders.schedule(ctx, items[i])
    }

    fun remove(ctx: Context, id: String) {
        load(ctx)
        items.removeAll { it.id == id }
        save(ctx)
        Reminders.cancel(ctx, id)
    }

    /** Dipakai penerima pengingat: tandai sudah diingatkan, kembalikan tugasnya bila memang perlu diingatkan. */
    fun markNotified(ctx: Context, id: String): Task? {
        load(ctx)
        val i = items.indexOfFirst { it.id == id }.takeIf { it >= 0 } ?: return null
        val t = items[i]
        if (t.done || t.notified) return null
        items[i] = t.copy(notified = true)
        save(ctx)
        return t
    }
}
