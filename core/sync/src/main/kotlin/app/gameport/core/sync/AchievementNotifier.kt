package app.gameport.core.sync

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import app.gameport.core.install.InstalledGames
import app.gameport.core.model.AchievementArtwork
import app.gameport.core.model.AchievementNames
import app.gameport.core.steam.AchievementsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject

/**
 * Announces the achievements a game unlocked, with a notification that stays until the player removes it: the game and the
 * achievement, what it asks for, its picture, and the time it was unlocked. The game's hook tells GamePort when the shim recorded
 * new ones. Nothing is sent to Steam from here. The achievements of a game are gathered in a group under its name.
 *
 * A headset stops GamePort by force when a game is left, and Android then removes all its notifications. So the announced
 * achievements are kept, and posted again, silently, when GamePort starts again, until the player has removed or opened them.
 */
@Singleton
class AchievementNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
    private val installed: InstalledGames,
    private val achievements: AchievementsRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val store = File(context.filesDir, "pending-achievements.json")

    /** One announced achievement, with everything needed to show it again without asking anyone. */
    private data class Pending(
        val key: String,
        val appId: Int,
        val game: String,
        val title: String,
        val text: String,
        val icon: String?,
        /** When the shim recorded the unlock, in milliseconds; 0 when unknown. */
        val whenMillis: Long,
    ) {
        /** "Game - Achievement", so the game is known at a glance. */
        val heading: String get() = if (game.isEmpty()) title else "$game - $title"
    }

    /** [names] are the achievements [packageName] just unlocked, [times] when (seconds). Returns at once: the notification is made in the background. */
    fun unlocked(packageName: String, names: List<String>, times: LongArray? = null) {
        if (names.isEmpty()) return
        val appId = installed.all().entries.firstOrNull { it.value == packageName }?.key ?: return
        scope.launch {
            runCatching { announce(appId, names, times) }.onFailure { Log.w(TAG, "could not announce the achievements of $packageName", it) }
        }
    }

    /** Called when GamePort starts: what was announced and not yet removed comes back into the list of notifications, without a pop-up. */
    fun restore() {
        scope.launch {
            runCatching {
                val manager = context.getSystemService(NotificationManager::class.java) ?: return@launch
                // What was kept before the name of the game was kept with it gets its name back.
                val kept = load()
                val waiting = kept.map { if (it.game.isEmpty()) it.copy(game = labelOf(it.appId)) else it }
                if (waiting != kept) save(waiting)
                if (waiting.isEmpty() || !manager.areNotificationsEnabled()) return@launch
                createChannels(manager)
                val shown = manager.activeNotifications.map { it.id }.toSet()
                // In the order they were unlocked, then the summary of each game, last.
                for (pending in waiting.sortedBy { it.whenMillis }) {
                    if (pending.key.hashCode() in shown) continue
                    manager.notify(pending.key.hashCode(), build(LIST_CHANNEL_ID, pending, pending.icon?.let { loadPicture(pending.appId, it) }))
                }
                waiting.map { it.appId }.distinct().forEach { postSummary(manager, it) }
            }.onFailure { Log.w(TAG, "could not bring the achievements back", it) }
        }
    }

    /** The player removed the notification of [key]: it is not brought back any more. */
    fun forget(key: String) {
        save(load().filterNot { it.key == key })
    }

    /** The player removed the group of a game: none of its achievements is brought back. */
    fun forgetGame(appId: Int) {
        save(load().filterNot { it.appId == appId })
    }

    /** The player opened GamePort from the notification of [key]. */
    fun opened(key: String) {
        forget(key)
        context.getSystemService(NotificationManager::class.java)?.cancel(key.hashCode())
    }

    private suspend fun announce(appId: Int, names: List<String>, times: LongArray?) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (!manager.areNotificationsEnabled()) return
        createChannels(manager)
        // What was kept comes at once; without it Steam is asked, and the name of the achievement serves when neither answers.
        val list = withTimeoutOrNull(LOOKUP_TIMEOUT_MS) { achievements.observe(appId).firstOrNull() }
        val game = labelOf(appId)
        for ((index, name) in names.withIndex()) {
            val found = list?.items?.firstOrNull { it.name.equals(name, ignoreCase = true) }
            val pending = Pending(
                key = "$appId:$name",
                appId = appId,
                game = game,
                title = found?.title ?: AchievementNames.readable(name),
                text = found?.description?.takeIf { it.isNotEmpty() } ?: game,
                icon = found?.icon,
                whenMillis = times?.getOrNull(index)?.takeIf { it > 0 }?.times(1000L) ?: System.currentTimeMillis(),
            )
            save(load().filterNot { it.key == pending.key } + pending)
            val picture = pending.icon?.let { loadPicture(appId, it) }
            val id = pending.key.hashCode()
            manager.notify(id, build(CHANNEL_ID, pending, picture))
            postSummary(manager, appId)
            // A headset draws the alert for a second or two, and the system decides how long: the notification is posted again, silently,
            // so that the alert comes back, for as long as the player has not removed it.
            repeat(REPEATS) {
                delay(REPEAT_AFTER_MS)
                if (manager.activeNotifications.none { it.id == id }) return@repeat
                manager.notify(id, build(QUIET_CHANNEL_ID, pending, picture))
            }
            // Last, so that it is the newest of its group.
            postSummary(manager, appId)
        }
    }

    /**
     * The achievements of a game are gathered under its name: one line for the game, which opens to its achievements. The summary makes
     * no sound of its own, and removing it removes them all. Its time is a second after the newest achievement: a headset that picks
     * the head of a group by its time picks the summary, and not an achievement.
     */
    private fun postSummary(manager: NotificationManager, appId: Int) {
        val unlocked = load().filter { it.appId == appId }
        if (unlocked.isEmpty()) return
        val game = unlocked.maxByOrNull { it.whenMillis }?.game?.ifEmpty { labelOf(appId) }.orEmpty()
        val removed = Intent(context, AchievementDismissReceiver::class.java).putExtra(EXTRA_GAME, appId)
        val summary = Notification.Builder(context, LIST_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_achievement)
            .setPriority(Notification.PRIORITY_LOW)
            .setContentTitle(game.ifEmpty { context.getString(R.string.achievement_channel) })
            .setContentText(context.resources.getQuantityString(R.plurals.achievement_group_count, unlocked.size, unlocked.size))
            .setGroup(groupOf(appId))
            .setGroupSummary(true)
            .setGroupAlertBehavior(Notification.GROUP_ALERT_CHILDREN)
            .setWhen(unlocked.maxOf { it.whenMillis } + 1_000L)
            .setAutoCancel(true)
            .setDeleteIntent(PendingIntent.getBroadcast(context, summaryId(appId), removed, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
            .build()
        manager.notify(summaryId(appId), summary)
    }

    /** The name of the game, as Android shows it (the patched game carries its name on Steam). Empty when it cannot be found. */
    private fun labelOf(appId: Int): String {
        val packageName = installed.all()[appId] ?: return ""
        return runCatching { context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(packageName, 0)).toString() }.getOrDefault("")
    }

    private fun groupOf(appId: Int) = "achievements-$appId"

    private fun summaryId(appId: Int) = "summary:$appId".hashCode()

    private fun createChannels(manager: NotificationManager) {
        // The channel makes a sound and vibrates: that, with the priority, is what gets it drawn over a game on a headset.
        manager.deleteNotificationChannel(OLD_CHANNEL_ID)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, context.getString(R.string.achievement_channel), NotificationManager.IMPORTANCE_HIGH).apply {
                description = context.getString(R.string.achievement_channel_description)
                setShowBadge(false)
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 250, 120, 250)
            },
        )
        // The same alert shown again, without sound or vibration.
        manager.createNotificationChannel(
            NotificationChannel(QUIET_CHANNEL_ID, context.getString(R.string.achievement_channel_quiet), NotificationManager.IMPORTANCE_HIGH).apply {
                description = context.getString(R.string.achievement_channel_quiet_description)
                setShowBadge(false)
                enableVibration(false)
                setSound(null, null)
            },
        )
        // What is brought back when GamePort starts again: only in the list, with no pop-up.
        manager.createNotificationChannel(
            NotificationChannel(LIST_CHANNEL_ID, context.getString(R.string.achievement_channel_list), NotificationManager.IMPORTANCE_LOW).apply {
                description = context.getString(R.string.achievement_channel_list_description)
                setShowBadge(false)
            },
        )
    }

    private fun build(channel: String, pending: Pending, picture: Bitmap?): Notification {
        // Opening it opens GamePort, and tells it which one was opened, so it is not brought back; removing it does the same.
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)?.putExtra(EXTRA_OPENED, pending.key)
        val removed = Intent(context, AchievementDismissReceiver::class.java).putExtra(EXTRA_OPENED, pending.key)
        val builder = Notification.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_achievement)
            // Only the announcement while the game runs is an alert. What is brought back later is a plain entry in the list: a headset
            // reads the priority and the category of the notification itself, and would show it again as a pop-up.
            .also { if (channel != LIST_CHANNEL_ID) it.setCategory(Notification.CATEGORY_MESSAGE).setPriority(Notification.PRIORITY_MAX) else it.setPriority(Notification.PRIORITY_LOW) }
            .setContentTitle(pending.heading)
            .setContentText(pending.text)
            .setSubText(context.getString(R.string.achievement_unlocked))
            // The time it was unlocked, as the shim recorded it, and not the time the notification is posted again.
            .setShowWhen(pending.whenMillis > 0)
            .setWhen(pending.whenMillis)
            .setGroup(groupOf(pending.appId))
            .setAutoCancel(true)
            .setDeleteIntent(PendingIntent.getBroadcast(context, pending.key.hashCode(), removed, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
        launch?.let { builder.setContentIntent(PendingIntent.getActivity(context, pending.key.hashCode(), it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)) }
        picture?.let(builder::setLargeIcon)
        return builder.build()
    }

    @Synchronized
    private fun load(): List<Pending> = runCatching {
        val array = JSONArray(store.readText())
        (0 until array.length()).map { index ->
            val item = array.getJSONObject(index)
            Pending(
                item.getString("key"), item.getInt("appId"), item.optString("game"), item.getString("title"), item.getString("text"),
                item.optString("icon").takeIf { it.isNotEmpty() }, item.optLong("when", 0L),
            )
        }
    }.getOrDefault(emptyList())

    @Synchronized
    private fun save(pending: List<Pending>) {
        runCatching {
            // The newest are kept; nobody needs a list of hundreds.
            val array = JSONArray()
            pending.takeLast(MAX_PENDING).forEach {
                array.put(JSONObject().put("key", it.key).put("appId", it.appId).put("game", it.game).put("title", it.title).put("text", it.text).put("icon", it.icon.orEmpty()).put("when", it.whenMillis))
            }
            store.writeText(array.toString())
        }
    }

    /** The picture of the unlocked achievement, from the first of Steam's addresses that answers. Null when none does. */
    private fun loadPicture(appId: Int, file: String): Bitmap? = AchievementArtwork.urls(appId, file).firstNotNullOfOrNull { address ->
        runCatching {
            val connection = URL(address).openConnection().apply { connectTimeout = PICTURE_TIMEOUT_MS; readTimeout = PICTURE_TIMEOUT_MS }
            connection.getInputStream().use { BitmapFactory.decodeStream(it) }
        }.getOrNull()
    }

    companion object {
        /** Carried by the intents of a notification: the key (`appId:name`) of the achievement it is about. */
        const val EXTRA_OPENED = "app.gameport.extra.ACHIEVEMENT"

        /** Carried by the intent of the summary of a game: its app id. */
        const val EXTRA_GAME = "app.gameport.extra.ACHIEVEMENT_GAME"

        private const val TAG = "GPAchievements"
        private const val CHANNEL_ID = "achievements_alert"
        private const val QUIET_CHANNEL_ID = "achievements_quiet"
        private const val LIST_CHANNEL_ID = "achievements_list"
        private const val OLD_CHANNEL_ID = "achievements"
        private const val REPEATS = 2
        private const val REPEAT_AFTER_MS = 2_500L
        private const val MAX_PENDING = 50
        private const val LOOKUP_TIMEOUT_MS = 8_000L
        private const val PICTURE_TIMEOUT_MS = 4_000
    }
}
