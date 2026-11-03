package id.cognify.notch

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.SystemClock

/** am broadcast -n id.cognify.notch/.DemoReceiver --es title Yellow --es artist Coldplay --es album Parachutes --ei dur 269 --ei pos 40 */
class DemoReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        if (i.getStringExtra("cmd") == "stop") { session?.release(); session = null; return }
        session?.release()
        val s = MediaSession(c.applicationContext, "demo")
        session = s
        s.setMetadata(
            MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, i.getStringExtra("title") ?: "Yellow")
                .putString(MediaMetadata.METADATA_KEY_ARTIST, i.getStringExtra("artist") ?: "Coldplay")
                .putString(MediaMetadata.METADATA_KEY_ALBUM, i.getStringExtra("album") ?: "Parachutes")
                .putLong(MediaMetadata.METADATA_KEY_DURATION, i.getIntExtra("dur", 269) * 1000L)
                .build(),
        )
        var pos = i.getIntExtra("pos", 0) * 1000L
        var at = SystemClock.elapsedRealtime()
        var playing = true
        fun push() = s.setPlaybackState(
            PlaybackState.Builder().setActions(PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_SEEK_TO or PlaybackState.ACTION_SKIP_TO_NEXT)
                .setState(if (playing) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED, pos, 1f, SystemClock.elapsedRealtime()).build(),
        )
        fun now() = if (playing) pos + (SystemClock.elapsedRealtime() - at) else pos
        s.setCallback(object : MediaSession.Callback() {
            override fun onPlay() { at = SystemClock.elapsedRealtime(); playing = true; push() }
            override fun onPause() { pos = now(); playing = false; push() }
            override fun onSeekTo(p: Long) { pos = p; at = SystemClock.elapsedRealtime(); push() }
        })
        push()
        s.isActive = true
    }

    companion object { private var session: MediaSession? = null }
}
