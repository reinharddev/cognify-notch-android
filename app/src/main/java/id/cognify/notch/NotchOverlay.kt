package id.cognify.notch

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.delay
import kotlin.math.max
import kotlin.math.min

private val Ink = Color(0xFF000000)
private val Dim = Color(0x99FFFFFF)
private val Amber = Color(0xFFFF9F0A)

/** Pil hitam di tepi atas layar. Ketuk untuk melebar; ketuk garis kecil di panel untuk menciut. */
@Composable
fun NotchOverlay(topInset: Float, onClose: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    var tab by remember { mutableStateOf(0) }
    val shape = RoundedCornerShape(bottomStart = 22.dp, bottomEnd = 22.dp)
    Box(Modifier.animateContentSize().background(Ink, shape)) {
        Column {
            Box(Modifier.height(topInset.dp)) // di bawah status bar sistem: hanya hiasan, sentuhan di sini diambil sistem
            if (!open) Box(Modifier.width(120.dp).height(26.dp).clickable { open = true })
            else Panel(tab, { tab = it }, onCollapse = { open = false }, onClose = onClose)
        }
    }
}

@Composable
private fun Panel(tab: Int, onTab: (Int) -> Unit, onCollapse: () -> Unit, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val t = MediaState.track
    Column(Modifier.width(380.dp).padding(horizontal = 18.dp).padding(bottom = 12.dp)) {
        Box(Modifier.fillMaxWidth().height(24.dp).clickable(onClick = onCollapse), contentAlignment = Alignment.Center) {
            Box(Modifier.width(36.dp).height(4.dp).background(Color(0xFF444444), RoundedCornerShape(2.dp)))
        }
        Row(Modifier.padding(bottom = 10.dp), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(18.dp)) {
            T(stringResource(R.string.tab_music), if (tab == 0) Color.White else Color(0x66FFFFFF), 13.sp, Modifier.clickable { onTab(0) })
            T(stringResource(R.string.tab_tasks), if (tab == 1) Color.White else Color(0x66FFFFFF), 13.sp, Modifier.clickable { onTab(1) })
        }
        if (tab == 1) Tasks(ctx, onAdd = onCollapse) else if (t == null) Empty(ctx) else Now(t, ctx)
        T(stringResource(R.string.close), Dim, 12.sp, Modifier.padding(top = 10.dp).clickable { onClose() })
    }
}

@Composable
private fun Tasks(ctx: Context, onAdd: () -> Unit) {
    LaunchedEffect(Unit) { TaskStore.load(ctx) }
    val list = TaskStore.items.sortedWith(compareBy<Task>({ it.done }, { it.due ?: Long.MAX_VALUE }))
    Row(verticalAlignment = Alignment.CenterVertically) {
        T(stringResource(R.string.tasks_open, TaskStore.items.count { !it.done }), Dim, 12.sp, Modifier.weight(1f))
        T(
            "+", Color.Black, 18.sp,
            Modifier.background(Color.White, RoundedCornerShape(50)).clickable {
                onAdd() // overlay selalu di atas aplikasi lain: ciutkan dulu agar form terlihat
                ctx.startActivity(Intent(ctx, AddTaskActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }.padding(horizontal = 14.dp, vertical = 2.dp),
        )
    }
    Column(Modifier.fillMaxWidth().height(190.dp).padding(top = 8.dp).verticalScroll(rememberScrollState())) {
        if (list.isEmpty()) {
            Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) { T(stringResource(R.string.no_tasks), Dim, 13.sp) }
        }
        for (t in list) TaskRow(t, ctx)
    }
}

@Composable
private fun TaskRow(t: Task, ctx: Context) {
    val now = System.currentTimeMillis()
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(34.dp).clickable { TaskStore.toggle(ctx, t.id) }.padding(7.dp)) {
            val r = size.minDimension / 2
            if (t.done) drawCircle(Color.White, r) else drawCircle(Color(0x99FFFFFF), r, style = androidx.compose.ui.graphics.drawscope.Stroke(2f))
        }
        Column(Modifier.weight(1f).padding(horizontal = 6.dp)) {
            T(t.text, if (t.done) Color(0x66FFFFFF) else Color.White, 14.sp, maxLines = 2, strike = t.done)
            t.due?.let { T(dueLabel(ctx, it), if (!t.done && it < now) Color(0xFFFF6B6B) else Dim, 11.sp) }
        }
        T("×", Dim, 20.sp, Modifier.clickable { TaskStore.remove(ctx, t.id) }.padding(horizontal = 10.dp, vertical = 2.dp))
    }
}

/** "Hari ini 15.00", "Besok 09.00", atau "25 Nov 09.00". */
fun dueLabel(ctx: Context, ms: Long): String {
    val zone = java.time.ZoneId.systemDefault()
    val at = java.time.Instant.ofEpochMilli(ms).atZone(zone)
    val today = java.time.LocalDate.now(zone)
    val time = java.time.format.DateTimeFormatter.ofPattern("HH.mm").format(at)
    return when (at.toLocalDate()) {
        today -> ctx.getString(R.string.today) + " " + time
        today.plusDays(1) -> ctx.getString(R.string.tomorrow) + " " + time
        else -> java.time.format.DateTimeFormatter.ofPattern("d MMM HH.mm", java.util.Locale.getDefault()).format(at)
    }
}

@Composable
private fun Empty(ctx: Context) {
    val allowed = NotificationManagerCompat.getEnabledListenerPackages(ctx).contains(ctx.packageName)
    Column(Modifier.fillMaxWidth().padding(vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        T(stringResource(if (allowed) R.string.nothing_playing else R.string.need_music_access), Dim, 13.sp)
        if (!allowed) {
            T(
                stringResource(R.string.allow_music_access), Color.Black, 13.sp,
                Modifier.padding(top = 12.dp).background(Color.White, RoundedCornerShape(50)).clickable {
                    ctx.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }.padding(horizontal = 18.dp, vertical = 9.dp),
            )
        }
    }
}

@Composable
private fun Now(t: Track, ctx: Context) {
    // Lirik hanya diminta saat panel terbuka.
    LaunchedEffect(t.title, t.artist, t.album) { Lyrics.load(ctx, t) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        val art = t.artwork
        if (art != null) Image(art.asImageBitmap(), null, Modifier.size(56.dp).clip(RoundedCornerShape(10.dp)), contentScale = ContentScale.Crop)
        else Box(Modifier.size(56.dp).background(Color(0xFF222222), RoundedCornerShape(10.dp)))
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            T(t.title, Color.White, 16.sp, maxLines = 1)
            T(t.artist, Dim, 12.sp, maxLines = 1)
        }
        Glyph(Glyph.Prev) { MediaState.previous() }
        Glyph(if (MediaState.playing) Glyph.Pause else Glyph.Play) { MediaState.toggle() }
        Glyph(Glyph.Next) { MediaState.next() }
    }
    // Posisi dihitung ulang tiap 250 ms, hanya selagi panel terbuka dan lagu diputar.
    var pos by remember { mutableLongStateOf(MediaState.positionNow()) }
    LaunchedEffect(MediaState.playing, MediaState.anchor) {
        pos = MediaState.positionNow()
        while (MediaState.playing) {
            delay(250)
            pos = MediaState.positionNow()
        }
    }
    Progress(pos, t.durationMs)
    LyricsBlock(pos)
}

@Composable
private fun Progress(pos: Long, total: Long) {
    Canvas(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 10.dp).height(3.dp)) {
        drawRect(Color(0xFF333333), size = size)
        if (total > 0) drawRect(Color.White, size = Size(size.width * (pos.toFloat() / total).coerceIn(0f, 1f), size.height))
    }
}

@Composable
private fun LyricsBlock(pos: Long) {
    Box(Modifier.fillMaxWidth().height(112.dp)) {
        when (val s = Lyrics.state) {
            is Lyrics.S.Synced -> {
                val lines = s.lines
                val cur = lines.indexOfLast { it.ms <= pos + 250 }
                Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(5.dp)) {
                    for (i in max(0, cur - 1)..min(lines.lastIndex, max(0, cur) + 3)) {
                        val on = i == cur
                        T(
                            lines[i].text.ifEmpty { "♪" }, if (on) Amber else Dim, if (on) 16.sp else 13.sp, maxLines = 1,
                            modifier = Modifier.clickable { MediaState.seek(lines[i].ms) },
                        )
                    }
                }
            }
            is Lyrics.S.Plain -> Box(Modifier.verticalScroll(rememberScrollState())) { T(s.text, Dim, 13.sp) }
            Lyrics.S.Loading -> Msg(R.string.lyr_loading)
            Lyrics.S.Instrumental -> Msg(R.string.lyr_instrumental)
            Lyrics.S.Missing -> Msg(R.string.lyr_missing)
            Lyrics.S.Offline -> Msg(R.string.lyr_offline)
            Lyrics.S.Unavailable -> Msg(R.string.lyr_unavailable)
            Lyrics.S.Idle -> {}
        }
    }
}

@Composable
private fun Msg(id: Int) = Box(Modifier.fillMaxWidth().height(112.dp), contentAlignment = Alignment.Center) {
    T(stringResource(id), Dim, 12.sp)
}

private enum class Glyph { Prev, Play, Pause, Next }

@Composable
private fun Glyph(kind: Glyph, onClick: () -> Unit) {
    Canvas(Modifier.size(40.dp).clickable(onClick = onClick)) {
        val w = size.width
        val h = size.height
        fun tri(x1: Float, x2: Float) = Path().apply {
            moveTo(x1 * w, 0.28f * h); lineTo(x2 * w, 0.5f * h); lineTo(x1 * w, 0.72f * h); close()
        }
        when (kind) {
            Glyph.Play -> drawPath(tri(0.36f, 0.72f), Color.White)
            Glyph.Pause -> {
                drawRect(Color.White, Offset(0.34f * w, 0.28f * h), Size(0.11f * w, 0.44f * h))
                drawRect(Color.White, Offset(0.55f * w, 0.28f * h), Size(0.11f * w, 0.44f * h))
            }
            Glyph.Next -> {
                drawPath(tri(0.28f, 0.62f), Color.White)
                drawRect(Color.White, Offset(0.66f * w, 0.28f * h), Size(0.06f * w, 0.44f * h))
            }
            Glyph.Prev -> {
                drawPath(Path().apply { moveTo(0.72f * w, 0.28f * h); lineTo(0.38f * w, 0.5f * h); lineTo(0.72f * w, 0.72f * h); close() }, Color.White)
                drawRect(Color.White, Offset(0.28f * w, 0.28f * h), Size(0.06f * w, 0.44f * h))
            }
        }
    }
}

/** Teks tanpa pustaka material (APK tetap kecil). */
@Composable
fun T(text: String, color: Color, fontSize: TextUnit, modifier: Modifier = Modifier, maxLines: Int = Int.MAX_VALUE, strike: Boolean = false) =
    BasicText(
        text, modifier,
        TextStyle(color = color, fontSize = fontSize, textDecoration = if (strike) androidx.compose.ui.text.style.TextDecoration.LineThrough else null),
        maxLines = maxLines, overflow = TextOverflow.Ellipsis,
    )
