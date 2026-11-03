package id.cognify.notch

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.app.Notification
import kotlin.math.max

/** Pengingat lewat AlarmManager: tidak ada layanan yang terus hidup, sistem membangunkan app hanya saat waktunya tiba. */
object Reminders {
    private const val CHANNEL = "reminders"
    private const val LATE_WINDOW = 6 * 3600_000L // pengingat yang terlewat lebih dari 6 jam tidak dibunyikan

    private fun pending(ctx: Context, id: String) = PendingIntent.getBroadcast(
        ctx, id.hashCode(), Intent(ctx, ReminderReceiver::class.java).setAction("remind").putExtra("id", id),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    fun schedule(ctx: Context, t: Task, now: Long = System.currentTimeMillis()) {
        val due = t.due ?: return
        if (t.done || t.notified) return
        if (due < now && now - due > LATE_WINDOW) return
        val am = ctx.getSystemService(AlarmManager::class.java)
        // Tidak "exact": tidak perlu izin alarm presisi, dan sistem boleh menggabungkan bangun agar hemat baterai.
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, max(due, now + 1000), pending(ctx, t.id))
    }

    fun cancel(ctx: Context, id: String) {
        ctx.getSystemService(AlarmManager::class.java).cancel(pending(ctx, id))
    }

    fun scheduleAll(ctx: Context) {
        TaskStore.load(ctx)
        TaskStore.items.forEach { schedule(ctx, it) }
    }

    fun notify(ctx: Context, t: Task) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, ctx.getString(R.string.reminders), NotificationManager.IMPORTANCE_HIGH))
        val open = PendingIntent.getActivity(ctx, 0, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        nm.notify(t.id.hashCode(), Notification.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_notch)
            .setContentTitle(ctx.getString(R.string.app_name))
            .setContentText(t.text)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build())
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, i: Intent) {
        val id = i.getStringExtra("id") ?: return
        TaskStore.markNotified(ctx, id)?.let { Reminders.notify(ctx, it) }
    }
}

/** Alarm hilang saat perangkat dimatikan, jadi dipasang ulang setelah menyala. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, i: Intent) {
        if (i.action == Intent.ACTION_BOOT_COMPLETED) Reminders.scheduleAll(ctx)
    }
}
