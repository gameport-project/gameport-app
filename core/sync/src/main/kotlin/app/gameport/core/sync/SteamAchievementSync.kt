package app.gameport.core.sync

import android.content.Context
import android.util.Log
import app.gameport.core.install.GameEventLog
import app.gameport.core.install.InstalledGames
import app.gameport.core.model.SteamConnection
import app.gameport.core.settings.UserSettings
import app.gameport.core.steam.AchievementUpload
import app.gameport.core.steam.AchievementUploader
import app.gameport.core.steam.AchievementsRepository
import app.gameport.core.steam.EarnedRecord
import app.gameport.core.steam.SteamAuthRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject

/**
 * Adds the achievements a game unlocked to the Steam account, when the player turned that on. The game's hook says what the shim
 * recorded; the names wait here, in a file, until Steam took them, so an achievement unlocked offline, or while GamePort was
 * stopped, is not lost. What Steam refuses is dropped after being noted in the game's events, since asking again would give the
 * same answer. Nothing is queued while the option is off, and nothing is sent for what was unlocked before it was turned on.
 */
@Singleton
class SteamAchievementSync @Inject constructor(
    @ApplicationContext context: Context,
    private val installed: InstalledGames,
    private val settings: UserSettings,
    private val uploader: AchievementUploader,
    private val auth: SteamAuthRepository,
    private val achievements: AchievementsRepository,
    private val events: GameEventLog,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val store = File(context.filesDir, "pending-steam-achievements.json")
    private val lock = Mutex()

    /** Sends what waits as soon as Steam can be reached, and again each time the connection comes back. */
    fun start() {
        scope.launch {
            auth.connection.collect { connection -> if (connection == SteamConnection.ONLINE) flush() }
        }
    }

    /** [names] are the achievements [packageName] just unlocked (the names the game uses). */
    fun unlocked(packageName: String, names: List<String>) {
        if (names.isEmpty() || !settings.sendAchievementsToSteam.value) return
        val appId = installed.all().entries.firstOrNull { it.value == packageName }?.key ?: return
        scope.launch {
            lock.withLock { save(load().toMutableMap().also { it[appId] = (it[appId].orEmpty() + names).distinct() }) }
            flush()
        }
    }

    /**
     * A game is starting: [current] is the record of its unlocked achievements as it has it. Returns that record with what the Steam account
     * has added (earned on another device, for example), or null when nothing is to be added or Steam cannot be asked in time. Nothing is
     * ever taken out of the record. Follows the same option as the sending.
     */
    suspend fun mergedRecord(packageName: String, current: String): String? {
        if (!settings.sendAchievementsToSteam.value) return null
        val appId = installed.all().entries.firstOrNull { it.value == packageName }?.key ?: return null
        val list = achievements.forSync(appId, PULL_TIMEOUT_MS) ?: return null
        val merged = EarnedRecord.merge(current, list.items)
        if (merged != null) events.note(appId, "achievements of the Steam account given to the game at start: ${list.items.count { it.unlocked }} unlocked on Steam")
        return merged
    }

    private suspend fun flush() = lock.withLock {
        if (!settings.sendAchievementsToSteam.value) return@withLock
        val waiting = load()
        if (waiting.isEmpty()) return@withLock
        val remaining = waiting.toMutableMap()
        for ((appId, names) in waiting) {
            val outcome = runCatching { uploader.upload(appId, names) }.getOrElse {
                events.failure(appId, "adding achievements to Steam", it)
                continue
            }
            when (outcome) {
                is AchievementUpload.Sent -> { events.note(appId, "added to Steam: ${outcome.names.joinToString()}"); remaining.remove(appId) }
                is AchievementUpload.NothingToSend -> {
                    events.note(appId, "nothing to add to Steam (already there: ${outcome.already.size}, unknown to the game: ${outcome.unknown.joinToString()})")
                    remaining.remove(appId)
                }
                is AchievementUpload.Refused -> { events.note(appId, "Steam refused the achievements ${names.joinToString()}: ${outcome.reason}"); remaining.remove(appId) }
                is AchievementUpload.Planned, AchievementUpload.Offline -> Log.i(TAG, "app $appId: waiting for Steam")
            }
        }
        if (remaining != waiting) save(remaining)
    }

    private fun load(): Map<Int, List<String>> = runCatching {
        val json = JSONObject(store.takeIf { it.isFile }?.readText() ?: return emptyMap())
        json.keys().asSequence().associate { key ->
            val array = json.getJSONArray(key)
            key.toInt() to (0 until array.length()).map { array.getString(it) }
        }
    }.getOrDefault(emptyMap())

    private fun save(waiting: Map<Int, List<String>>) {
        runCatching {
            if (waiting.isEmpty()) store.delete() else store.writeText(JSONObject().also { json -> waiting.forEach { (appId, names) -> json.put(appId.toString(), JSONArray(names)) } }.toString())
        }
    }

    private companion object {
        const val TAG = "GPAchievements"

        /** The game waits for this at its start, so Steam is given little time. */
        const val PULL_TIMEOUT_MS = 3_000L
    }
}
