package id.cognify.notch

import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.PlaybackState
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class Track(val title: String, val artist: String, val album: String, val durationMs: Long, val artwork: Bitmap?)

/** Keadaan "sedang diputar" yang dibaca dari app musik mana pun. Diperbarui hanya oleh kejadian sistem, tanpa polling. */
object MediaState {
    var track: Track? by mutableStateOf(null); private set
    var playing by mutableStateOf(false); private set
    private var posMs = 0L
    private var posAt = 0L
    private var speed = 1f
    private var controller: MediaController? = null
    // Dibaca Compose agar posisi dihitung ulang setelah seek atau jeda.
    var anchor by mutableStateOf(0); private set

    fun positionNow(): Long =
        if (playing) posMs + ((SystemClock.elapsedRealtime() - posAt) * speed).toLong() else posMs

    fun update(c: MediaController?) {
        controller = c
        val m = c?.metadata
        val title = m?.getString(MediaMetadata.METADATA_KEY_TITLE)
        track = if (m == null || title.isNullOrBlank()) null else Track(
            title,
            m.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: m.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST) ?: "",
            m.getString(MediaMetadata.METADATA_KEY_ALBUM) ?: "",
            m.getLong(MediaMetadata.METADATA_KEY_DURATION),
            small(m.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART) ?: m.getBitmap(MediaMetadata.METADATA_KEY_ART)),
        )
        val s = c?.playbackState
        playing = s?.state == PlaybackState.STATE_PLAYING
        posMs = s?.position ?: 0L
        posAt = s?.lastPositionUpdateTime ?: SystemClock.elapsedRealtime()
        speed = s?.playbackSpeed ?: 1f
        anchor++
    }

    // Sampul diperkecil supaya RAM tetap kecil.
    private fun small(b: Bitmap?): Bitmap? = b?.let { Bitmap.createScaledBitmap(it, 160, 160, true) }

    fun toggle() = controller?.transportControls?.let { if (playing) it.pause() else it.play() }
    fun next() = controller?.transportControls?.skipToNext()
    fun previous() = controller?.transportControls?.skipToPrevious()
    fun seek(ms: Long) {
        controller?.transportControls?.seekTo(ms)
        posMs = ms
        posAt = SystemClock.elapsedRealtime()
        anchor++
    }
}
