package id.cognify.notch

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner

/**
 * Satu foreground service yang memegang satu jendela overlay. Saat tertutup hanya pil kecil statis:
 * tidak ada timer, animasi, atau polling yang berjalan.
 */
class OverlayService : Service() {
    private lateinit var wm: WindowManager
    private var view: ComposeView? = null
    private val owner = Owner()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        ScreenInfo.update(this)
        startInForeground()
        owner.start()
        val v = ComposeView(this).apply {
            setViewTreeLifecycleOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setViewTreeViewModelStoreOwner(owner)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent { NotchOverlay(topInset = statusBarDp(), onOpenChange = ::setOpen, onClose = { stopSelf() }) }
        }
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL }
        wm.addView(v, lp)
        view = v
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        ScreenInfo.update(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int) = START_NOT_STICKY

    override fun onDestroy() {
        view?.let { runCatching { wm.removeView(it) } }
        view = null
        owner.stop()
        super.onDestroy()
    }

    /**
     * Jendela WRAP_CONTENT dibatasi sistem ke lebar dialog bawaan (sekitar 311 dp di HP), dan Compose tidak melaporkan
     * bahwa isinya terpotong. Jadi saat panel terbuka lebarnya diatur eksplisit.
     */
    private fun setOpen(open: Boolean) {
        val v = view ?: return
        val lp = v.layoutParams as WindowManager.LayoutParams
        lp.width = if (open) (panelWidthDp() * resources.displayMetrics.density).toInt() else WindowManager.LayoutParams.WRAP_CONTENT
        runCatching { wm.updateViewLayout(v, lp) }
    }

    /** Status bar selalu di atas overlay dan mengambil sentuhan di sana, jadi pil dibuat setinggi itu lebih panjang. */
    private fun statusBarDp(): Float {
        val id = resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (id > 0) resources.getDimension(id) / resources.displayMetrics.density else 24f
    }

    private fun startInForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("notch", getString(R.string.channel), NotificationManager.IMPORTANCE_MIN))
        val n = Notification.Builder(this, "notch")
            .setSmallIcon(R.drawable.ic_notch)
            .setContentText(getString(R.string.notif_text))
            .setOngoing(true)
            .build()
        // Tipe foreground service baru ada di Android 10 (diwajibkan jenis specialUse mulai Android 14).
        when {
            Build.VERSION.SDK_INT >= 34 -> startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            else -> startForeground(1, n)
        }
    }

    private class Owner : LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {
        private val registry = LifecycleRegistry(this)
        private val saved = SavedStateRegistryController.create(this)
        override val lifecycle: Lifecycle get() = registry
        override val savedStateRegistry: SavedStateRegistry get() = saved.savedStateRegistry
        override val viewModelStore = ViewModelStore()

        fun start() {
            saved.performAttach()
            saved.performRestore(null)
            registry.currentState = Lifecycle.State.RESUMED
        }

        fun stop() {
            registry.currentState = Lifecycle.State.DESTROYED
            viewModelStore.clear()
        }
    }
}
