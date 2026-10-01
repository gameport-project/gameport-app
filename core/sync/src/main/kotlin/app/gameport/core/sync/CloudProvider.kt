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
 * - `end` -> the game is done
 * - `resumed`, `alive`, `paused` -> the game is on screen, still there, gone: its playing time
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
                // Whether to open GamePort again when this game closes.
                putBoolean("returnToGamePort", entryPoint.userSettings().returnToGamePort.value)
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
        // Sent regularly by the running game so GamePort stays connected to Steam (see `ticket`).
        "warm" -> {
            runBlocking { coordinator.warmUp() }
            Bundle().apply { putString(STATUS, OK) }
        }
        // A fresh Steam session ticket, for games that log in to their own online services.
        "ticket" -> runBlocking { coordinator.authTicket(packageName) }
            ?.let { Bundle().apply { putByteArray("ticket", it) } }
            ?: Bundle().apply { putString(STATUS, ERROR) }
        "begin" -> when (val result = runBlocking { noteLaunch(packageName); coordinator.begin(packageName) }) {
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
    }

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
