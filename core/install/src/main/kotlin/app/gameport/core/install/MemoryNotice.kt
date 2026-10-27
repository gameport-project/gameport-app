package app.gameport.core.install

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.text.format.Formatter
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/** Tells, in a short alert at the top that goes away by itself, how much memory closing the apps in the background gave back. */
@Singleton
class MemoryNotice @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val waiting = java.util.concurrent.ConcurrentHashMap<String, Pair<Long, Long>>()

    /**
     * What closing the apps in the background gave back, for the game [packageName] that is starting. It is announced when the game reaches the
     * screen: an alert posted while it is still starting is gone before it can be drawn over it, as the achievements show (they come from a game
     * already on screen).
     */
    fun report(packageName: String, freedBytes: Long) {
        waiting[packageName] = freedBytes to System.currentTimeMillis()
    }

    /** The game came to the screen: what [report] kept is announced, once. */
    fun gameShown(packageName: String) {
        val (freed, at) = waiting.remove(packageName) ?: return
        if (System.currentTimeMillis() - at > WAITING_MS) return
        announce(freed)
    }

    private fun announce(freedBytes: Long) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (!manager.areNotificationsEnabled()) return
        // A sound and a vibration, with the priority, are what get an alert drawn over a game on a headset (the achievements need the same).
        manager.deleteNotificationChannel(OLD_CHANNEL_ID)
        manager.deleteNotificationChannel("memory_freed")
        manager.deleteNotificationChannel("memory_freed_quiet")
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, context.getString(R.string.memory_channel_name), NotificationManager.IMPORTANCE_HIGH).apply {
                setShowBadge(false)
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 200)
            },
        )
        // The same alert, shown again without a sound: a headset draws one for a second or two only, and the alert is meant to be read.
        manager.createNotificationChannel(
            NotificationChannel(QUIET_CHANNEL_ID, context.getString(R.string.memory_channel_name), NotificationManager.IMPORTANCE_HIGH).apply {
                setShowBadge(false)
                enableVibration(false)
                setSound(null, null)
            },
        )
        val text = if (freedBytes > 0) context.getString(R.string.memory_freed, Formatter.formatShortFileSize(context, freedBytes))
        else context.getString(R.string.memory_nothing_to_free)
        manager.notify(NOTIFICATION_ID, build(CHANNEL_ID, text))
        scope.launch {
            repeat(REPEATS) {
                delay(REPEAT_EVERY_MS)
                manager.notify(NOTIFICATION_ID, build(QUIET_CHANNEL_ID, text))
            }
            // Nothing is left in the list afterwards.
            delay(REPEAT_EVERY_MS)
            manager.cancel(NOTIFICATION_ID)
        }
    }

    private fun build(channel: String, text: String): Notification {
        // Like the achievements, which are drawn over a game: a headset reads the category and the priority of the notification itself to decide
        // whether it is an alert. A status line is put in the list without a word. Touching it opens GamePort.
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val builder = Notification.Builder(context, channel)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setCategory(Notification.CATEGORY_MESSAGE)
            .setPriority(Notification.PRIORITY_MAX)
            .setContentTitle(context.getString(R.string.install_notification_title))
            .setContentText(text)
            .setSubText(context.getString(R.string.memory_channel_name))
            .setAutoCancel(true)
        launch?.let { builder.setContentIntent(PendingIntent.getActivity(context, NOTIFICATION_ID, it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)) }
        return builder.build()
    }

    private companion object {
        const val CHANNEL_ID = "memory_freed_alert_2"
        const val QUIET_CHANNEL_ID = "memory_freed_quiet_2"
        const val OLD_CHANNEL_ID = "memory_freed_alert"
        const val REPEATS = 3
        const val REPEAT_EVERY_MS = 1_800L
        const val NOTIFICATION_ID = 0x6d656d
        /** A result is announced only if the game reaches the screen within this time of the start. */
        const val WAITING_MS = 90_000L
    }
}
