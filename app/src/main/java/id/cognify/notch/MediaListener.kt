package id.cognify.notch

import android.content.ComponentName
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.service.notification.NotificationListenerService

/** Izin "Akses notifikasi" dipakai Android untuk membuka daftar sesi media; notifikasinya sendiri tidak dibaca. */
class MediaListener : NotificationListenerService() {
    private var manager: MediaSessionManager? = null
    private var current: MediaController? = null

    private val callback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: android.media.MediaMetadata?) = MediaState.update(current)
        override fun onPlaybackStateChanged(state: PlaybackState?) = MediaState.update(current)
        override fun onSessionDestroyed() = pick(manager?.getActiveSessions(component()))
    }
    private val sessions = MediaSessionManager.OnActiveSessionsChangedListener { pick(it) }

    private fun component() = ComponentName(this, MediaListener::class.java)

    override fun onListenerConnected() {
        val m = getSystemService(MediaSessionManager::class.java)
        manager = m
        m.addOnActiveSessionsChangedListener(sessions, component())
        pick(m.getActiveSessions(component()))
    }

    override fun onListenerDisconnected() {
        manager?.removeOnActiveSessionsChangedListener(sessions)
        current?.unregisterCallback(callback)
        current = null
        MediaState.update(null)
    }

    private fun pick(list: List<MediaController>?) {
        val next = list?.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING } ?: list?.firstOrNull()
        if (next?.sessionToken != current?.sessionToken) {
            current?.unregisterCallback(callback)
            current = next
            next?.registerCallback(callback)
        }
        MediaState.update(current)
    }
}
