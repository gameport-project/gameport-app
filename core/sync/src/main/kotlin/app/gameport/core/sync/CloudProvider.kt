package app.gameport.core.sync

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.util.Log
import app.gameport.core.install.InstalledGames
import app.gameport.core.settings.GameSettingsStore
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.io.FileNotFoundException
import kotlinx.coroutines.runBlocking

@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface CloudEntryPoint {
    fun coordinator(): CloudSyncCoordinator

    fun installedGames(): InstalledGames

    fun gameSettings(): GameSettingsStore

    fun controllerMappings(): app.gameport.core.settings.ControllerMappingStore

    fun deviceProfile(): app.gameport.core.device.DeviceProfile

    fun playHistory(): app.gameport.core.settings.PlayHistoryStore

    fun playtime(): PlaytimeTracker

    fun userSettings(): app.gameport.core.settings.UserSettings

    fun reports(): ReportStore

    fun catchUp(): SaveCatchUp

    fun achievementNotifier(): AchievementNotifier

    fun playDecisions(): PlayDecisions

    fun steamAchievements(): SteamAchievementSync
}

/**
 * The door patched games knock on. Android starts GamePort's process to answer, even when it was
 * closed or force-stopped. Every request is accepted only from the game it is about: the package
 * Android reports for the caller must be the package named in the request.
 *
 * Calls (all with the game's package name as the argument):
 * - `config` -> the player's settings for the game (seated mode, eye height)
 * - `ticket` -> a Steam session ticket for the game, made with the signed-in account
 * - `begin` -> status and the save rules to scan
 * - `plan` (local files, optional forced side) -> what to download, upload or ask the player
 * - `conflict` -> the player's answer once given
 * - `ack` (local files after a download) and `commit` (after uploading) -> finish a sync
 * - `local` (local files) -> what the saves are now, outside a sync
 * - `end` -> the game is done
 * - `resumed`, `alive`, `paused` -> the game is on screen, still there, gone: its playing time
 * - `log` (its last log lines, how its last runs ended) -> kept for a problem report
 * - `achievement` (the names) -> the game unlocked achievements: they are announced
 * - `closed` -> the game's process is ending
 *
 * Files travel through `openFile`: `fetch` reads a cloud file, `push` writes a file to upload.
 */
class CloudProvider : ContentProvider() {
    private val entryPoint: CloudEntryPoint by lazy {
        EntryPointAccessors.fromApplication(context!!.applicationContext, CloudEntryPoint::class.java)
    }
    private val coordinator: CloudSyncCoordinator get() = entryPoint.coordinator()

    override fun onCreate(): Boolean = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        val packageName = arg
        if (packageName == null || callingPackage != packageName) {
            Log.w(TAG, "refused $method from $callingPackage for $packageName")
            return null
        }
        // The game is there: GamePort stays connected for it (see [ConnectionKeeper]), except when it says it is leaving.
        if (method != "closed" && method != "end") entryPoint.playtime().gameCalled()
        return try {
            handle(method, packageName, extras ?: Bundle.EMPTY)
        } catch (e: Exception) {
            Log.w(TAG, "$method failed", e)
            Bundle().apply { putString(STATUS, ERROR) }
        }
    }

    private fun handle(method: String, packageName: String, extras: Bundle): Bundle = when (method) {
        // The player's settings for this game, read by the hook before the game starts.
        "config" -> {
            val appId = entryPoint.installedGames().all().entries.firstOrNull { it.value == packageName }?.key
            val settings = appId?.let { entryPoint.gameSettings().get(it) }
            Bundle().apply {
                putBoolean("seated", settings?.seated == true)
                putInt("eyeCm", appId?.let { entryPoint.gameSettings().eyeHeightCm(it) } ?: 0)
                // The player's controller mapping for the game, empty unless they customise it.
                putString("xrMap", appId?.let { entryPoint.controllerMappings().layerConfig(it) }.orEmpty())
                // The kind of headset (meta, pico, openxr), so the layer knows which controllers to translate onto.
                putString("xrFamily", entryPoint.deviceProfile().vrPlatform?.id.orEmpty())
                // For which games to open GamePort again when the game closes. Hooks from before the choice only know the first answer.
                val mode = entryPoint.userSettings().returnMode.value
                putString("returnMode", mode.id)
                putBoolean("returnToGamePort", mode == app.gameport.core.model.ReturnMode.APP || mode == app.gameport.core.model.ReturnMode.ALL)
            }
        }
        // The Steam Frame controls the game used the last time it ran (left by the OpenXR layer).
        "controller_profile" -> {
            val appId = entryPoint.installedGames().all().entries.firstOrNull { it.value == packageName }?.key
            val controls = extras.getStringArray("controls").orEmpty().mapNotNull(app.gameport.core.model.ControlRef::parse)
            if (appId != null && controls.isNotEmpty()) entryPoint.controllerMappings().setDetected(appId, controls, extras.getString("source").orEmpty())
            Bundle().apply { putString(STATUS, OK) }
        }
        // The game came to the screen, is still there, or left it: the time it is on screen is counted (see PlaytimeTracker).
        "resumed" -> okAfter { entryPoint.playtime().resumed(packageName) }
        "alive" -> okAfter { entryPoint.playtime().alive(packageName) }
        "paused" -> okAfter { entryPoint.playtime().paused(packageName) }
        // The game's process is ending (it quit by itself, or its last screen closed): how long it lasted shows a problem.
        // Saves it could not send are then sent by GamePort itself (see [SaveCatchUp]).
        "closed" -> okAfter {
            entryPoint.playDecisions().clear(packageName)
            entryPoint.reports().left(packageName)
            entryPoint.catchUp().afterClose()
        }
        // What the saves are now, whatever the connection: the page of the game's saves shows it, and what is not sent is remembered.
        "local" -> okAfter { coordinator.observeLocal(packageName, parseFiles(extras)) }
        // What the game wrote to the system log lately and how it last ended, for a problem report.
        "log" -> okAfter { entryPoint.reports().saveGameData(packageName, extras) }
        // The game unlocked achievements, which the shim recorded: they are announced with a notification.
        // They are also added to the Steam account when the player chose so (see [SteamAchievementSync]).
        "achievement" -> okAfter {
            val names = extras.getStringArray("names").orEmpty().toList()
            entryPoint.achievementNotifier().unlocked(packageName, names, extras.getLongArray("times"))
            entryPoint.steamAchievements().unlocked(packageName, names)
        }
        // The game is starting: its record of unlocked achievements, in return with what the Steam account has added to it.
        "earned" -> {
            val merged = runBlocking { entryPoint.steamAchievements().mergedRecord(packageName, extras.getString("current").orEmpty()) }
            Bundle().apply {
                putString(STATUS, OK)
                if (merged != null) putString("merged", merged)
            }
        }
        // Sent regularly by the running game so GamePort stays connected to Steam (see `ticket`).
        "warm" -> {
            runBlocking { coordinator.warmUp() }
            Bundle().apply { putString(STATUS, OK) }
        }
        // A fresh Steam session ticket, for games that log in to their own online services.
        "ticket" -> if (entryPoint.playDecisions().playsWithout(packageName)) {
            // The player chose to play this game without its ticket: it is not asked again.
            Bundle().apply { putString(STATUS, ERROR) }
        } else if (entryPoint.playDecisions().isSimulated(packageName) && !extras.getBoolean("takeOver", false) && !extras.getBoolean("quiet", false)) {
            // A test: the question is put as if another device played.
            entryPoint.playDecisions().ask(packageName, gameLabel(packageName))
            Bundle().apply { putString(STATUS, "BLOCKED"); putInt("otherApp", 0) }
        } else when (val outcome = runBlocking { coordinator.authTicket(packageName, extras.getBoolean("takeOver", false)) }) {
            is CloudSyncCoordinator.TicketOutcome.Given -> Bundle().apply { putByteArray("ticket", outcome.ticket) }
            // Another device plays with the account: at the start of the game the question is put to the player, in front of it.
            is CloudSyncCoordinator.TicketOutcome.Blocked -> if (extras.getBoolean("quiet", false)) {
                Bundle().apply { putString(STATUS, ERROR) }
            } else {
                entryPoint.playDecisions().ask(packageName, gameLabel(packageName))
                Bundle().apply { putString(STATUS, "BLOCKED"); putInt("otherApp", outcome.otherApp) }
            }
            CloudSyncCoordinator.TicketOutcome.None -> Bundle().apply { putString(STATUS, ERROR) }
        }
        // The player's answer to that question: PENDING while it is open, then QUIT, KICK or PLAY.
        "playchoice" -> Bundle().apply { putString(CHOICE, entryPoint.playDecisions().answerFor(packageName)) }
        // The game gave up waiting for the answer.
        "playgiveup" -> okAfter { entryPoint.playDecisions().clear(packageName) }
        // Did GamePort start this game's process only to send its saves? Then it is not played: nothing else is set up (no ticket, no time counted).
        "catchup" -> Bundle().apply {
            putString(STATUS, OK)
            putBoolean("catchup", coordinator.isCatchUp(packageName))
        }
        "catchup_done" -> okAfter { coordinator.finishCatchUp(packageName) }
        "begin" -> when (val result = runBlocking { if (!coordinator.isCatchUp(packageName)) noteLaunch(packageName); coordinator.begin(packageName) }) {
            is BeginResult.Ready -> Bundle().apply {
                putString(STATUS, READY)
                putStringArray(RULES, result.rules.map { listOf(it.localDir, it.pattern, if (it.recursive) "1" else "0", it.cloudPrefix).joinToString("\t") }.toTypedArray())
            }
            BeginResult.Offline -> Bundle().apply { putString(STATUS, OFFLINE) }
            BeginResult.Unsupported -> Bundle().apply { putString(STATUS, UNSUPPORTED) }
        }
        "plan" -> planBundle(coordinator.plan(packageName, parseFiles(extras), extras.getString(FORCE)?.let(::parseSide)))
        "conflict" -> Bundle().apply { putString(CHOICE, coordinator.answerFor(packageName)) }
        "ack" -> {
            coordinator.acknowledgeDownload(packageName, parseFiles(extras))
            Bundle().apply { putString(STATUS, OK) }
        }
        "commit" -> Bundle().apply { putString(STATUS, if (runBlocking { coordinator.commitUpload(packageName) }) OK else ERROR) }
        "end" -> {
            coordinator.end(packageName)
            Bundle().apply { putString(STATUS, OK) }
        }
        else -> Bundle().apply { putString(STATUS, ERROR) }
    }

    /** The game says it started: that is its launch, whether GamePort or the system's library started it. */
    private fun noteLaunch(packageName: String) {
        val appId = entryPoint.installedGames().all().entries.firstOrNull { it.value == packageName }?.key ?: return
        entryPoint.playHistory().markPlayed(appId)
        entryPoint.reports().launched(packageName)
    }

    private fun gameLabel(packageName: String): String = runCatching {
        val manager = context!!.packageManager
        manager.getApplicationInfo(packageName, 0).loadLabel(manager).toString()
    }.getOrDefault(packageName)

    private inline fun okAfter(action: () -> Unit): Bundle {
        action()
        return Bundle().apply { putString(STATUS, OK) }
    }

    private fun planBundle(action: SyncAction): Bundle = Bundle().apply {
        when (action) {
            SyncAction.None -> putString(ACTION, "NONE")
            is SyncAction.Download -> {
                putString(ACTION, "DOWNLOAD")
                putStringArray(DOWNLOAD, action.files.map { listOf(it.name, it.rel, it.sha1, it.size, it.timestamp).joinToString("\t") }.toTypedArray())
                putStringArray(DELETE_LOCAL, action.deleteLocal.toTypedArray())
            }
            is SyncAction.Upload -> {
                putString(ACTION, "UPLOAD")
                putStringArray(UPLOAD, action.files.map { it.rel }.toTypedArray())
            }
            is SyncAction.Conflict -> putString(ACTION, "CONFLICT")
        }
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        val packageName = uri.getQueryParameter("pkg")
        if (packageName == null || callingPackage != packageName) throw FileNotFoundException("not allowed")
        return when (uri.lastPathSegment) {
            "fetch" -> {
                val file = runBlocking { coordinator.fetch(packageName, uri.getQueryParameter("name").orEmpty()) }
                    ?: throw FileNotFoundException("cloud file unavailable")
                ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            }
            "push" -> {
                val file = coordinator.pushTarget(packageName, uri.getQueryParameter("rel").orEmpty())
                    ?: throw FileNotFoundException("upload not expected")
                ParcelFileDescriptor.open(
                    file,
                    ParcelFileDescriptor.MODE_WRITE_ONLY or ParcelFileDescriptor.MODE_CREATE or ParcelFileDescriptor.MODE_TRUNCATE,
                )
            }
            else -> throw FileNotFoundException("unknown path")
        }
    }

    private fun parseFiles(extras: Bundle): List<LocalFile> = extras.getStringArray(FILES).orEmpty().mapNotNull { line ->
        val parts = line.split('\t')
        if (parts.size < 4) null else LocalFile(parts[0], parts[1], parts[2].toLongOrNull() ?: 0L, parts[3].toLongOrNull() ?: 0L)
    }

    private fun parseSide(name: String): Side? = Side.entries.firstOrNull { it.name.equals(name, ignoreCase = true) }

    override fun query(uri: Uri, projection: Array<String>?, selection: String?, selectionArgs: Array<String>?, sortOrder: String?): Cursor? = null

    override fun getType(uri: Uri): String? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?): Int = 0

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<String>?): Int = 0

    private companion object {
        const val TAG = "GPSync"
        const val STATUS = "status"
        const val ACTION = "action"
        const val CHOICE = "choice"
        const val RULES = "rules"
        const val FILES = "files"
        const val FORCE = "force"
        const val DOWNLOAD = "download"
        const val DELETE_LOCAL = "deleteLocal"
        const val UPLOAD = "upload"
        const val READY = "READY"
        const val OFFLINE = "OFFLINE"
        const val UNSUPPORTED = "UNSUPPORTED"
        const val OK = "OK"
        const val ERROR = "ERROR"
    }
}
