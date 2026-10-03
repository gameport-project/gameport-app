package app.gameport.core.install

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.IBinder
import android.os.PowerManager

/**
 * Keeps the process alive while a game is downloaded, patched and installed. It does no work
 * itself: [GameInstallRepository] starts it with the first operation and stops it after the last.
 */
class InstallForegroundService : Service() {
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.install_channel_name), NotificationManager.IMPORTANCE_LOW),
        )
        val notification = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(getString(R.string.install_notification_title))
            .setContentText(getString(R.string.install_notification_text))
            .setOngoing(true)
            .build()
        startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        holdLocks()
        return START_NOT_STICKY
    }

    /** The player closed GamePort (not just left it): the run of "patch all" stops with it. Downloads and single installs go on, as they are meant to. */
    override fun onTaskRemoved(rootIntent: Intent?) {
        onClosed?.invoke()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        wakeLock?.takeIf { it.isHeld }?.release()
        wifiLock?.takeIf { it.isHeld }?.release()
        super.onDestroy()
    }

    // The screen may go off (the headset sleeps when taken off); the work must not stop with it.
    // The timeout is a safety net in case the service is ever left running by mistake.
    private fun holdLocks() {
        if (wakeLock == null) {
            wakeLock = getSystemService(PowerManager::class.java)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "gameport:install")
                .apply { acquire(MAX_HOLD_MS) }
        }
        if (wifiLock == null) {
            wifiLock = applicationContext.getSystemService(WifiManager::class.java)
                .createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "gameport:install")
                .apply { acquire() }
        }
    }

    companion object {
        /** Set by [GameInstallRepository], which is what runs "patch all". */
        @Volatile internal var onClosed: (() -> Unit)? = null

        private const val CHANNEL_ID = "installs"
        private const val NOTIFICATION_ID = 1
        private const val MAX_HOLD_MS = 3L * 60 * 60 * 1000
    }
}
