package app.gameport.core.install

import android.app.ActivityManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import app.gameport.core.device.DeviceProfile
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** Talks to Android's package installer. Both install and uninstall may ask the user to confirm. */
@Singleton
class PackageGateway @Inject constructor(
    @ApplicationContext private val context: Context,
    private val device: DeviceProfile,
) {
    private val packageManager get() = context.packageManager

    /** False on a device without VR: VR patches make no sense there. */
    val isHeadset: Boolean get() = device.isHeadset

    /** The headset family of this device (`meta`, `pico`, `openxr`), null when it is not a headset. */
    val vrFamily: String? get() = device.vrPlatform?.id

    /**
     * Whether the system lets GamePort install anything at all. Without it, Android still takes a session and still asks, but it sends the player
     * to its own "install unknown apps" page instead of a confirmation, and that page answers nothing: the install would wait for ever.
     */
    fun canInstallPackages(): Boolean =
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) true else runCatching { packageManager.canRequestPackageInstalls() }.getOrDefault(true)

    fun packageNameOf(apk: File): String? = runCatching {
        packageManager.getPackageArchiveInfo(apk.absolutePath, 0)?.packageName
    }.getOrNull()

    /**
     * What the system says when it is asked to read [apk], for the log when [packageNameOf] gives nothing: the reason is otherwise
     * thrown away, and "the APK could not be read" alone says nothing about why.
     */
    fun unreadableReason(apk: File): String = runCatching {
        val info = packageManager.getPackageArchiveInfo(apk.absolutePath, 0)
        if (info == null) "the system read the file but found no package in it" else "read fine: ${info.packageName}"
    }.getOrElse { "${it.javaClass.simpleName}: ${it.message}" }

    /** The APK files of an installed package: the base and its splits. */
    fun apkFilesOf(packageName: String): List<File> = runCatching {
        val info = packageManager.getApplicationInfo(packageName, 0)
        (listOf(info.sourceDir) + info.splitSourceDirs.orEmpty()).map(::File)
    }.getOrDefault(emptyList())

    /** Whether the package's manifest metadata holds [key]. */
    fun hasMetaData(packageName: String, key: String): Boolean = runCatching {
        packageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA).metaData?.containsKey(key) == true
    }.getOrDefault(false)

    /** An integer from the package's manifest metadata, or null when it has none. */
    fun metaDataInt(packageName: String, key: String): Int? = runCatching {
        packageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA)
            .metaData?.takeIf { it.containsKey(key) }?.getInt(key)
    }.getOrNull()

    /**
     * The GamePort that patched [packageName], by its package name: the one written into the game, or the released one for a game patched before it was.
     * Null for a package GamePort did not patch.
     */
    fun ownerOf(packageName: String): String? = runCatching {
        val meta = packageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA).metaData
        meta?.getString("app.gameport.owner")?.takeIf { it.isNotEmpty() }
            ?: if (meta?.containsKey("app.gameport.patch_version") == true) "app.gameport" else null
    }.getOrNull()

    /** True when the package asks for [permission] in its manifest and has not been given it. */
    fun requestsButLacks(packageName: String, permission: String): Boolean = runCatching {
        val info = packageManager.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS)
        val index = info.requestedPermissions?.indexOf(permission) ?: -1
        index >= 0 && (info.requestedPermissionsFlags?.get(index) ?: 0) and android.content.pm.PackageInfo.REQUESTED_PERMISSION_GRANTED == 0
    }.getOrDefault(false)

    /** When the package was first installed (epoch millis), or null when it is not installed. */
    fun installTimeOf(packageName: String): Long? = runCatching { packageManager.getPackageInfo(packageName, 0).firstInstallTime }.getOrNull()

    /** The version code of the installed package, or null when it is not installed. */
    /** When the package was last installed or updated (a patch counts), in milliseconds, or null if it is not installed. */
    fun lastUpdateTimeOf(packageName: String): Long? = runCatching { packageManager.getPackageInfo(packageName, 0).lastUpdateTime }.getOrNull()

    fun versionCodeOf(packageName: String): Long? = runCatching { packageManager.getPackageInfo(packageName, 0).longVersionCode }.getOrNull()

    /** The version code the game had before GamePort raised it (written into the patched APK), or null for a package GamePort did not patch that way. */
    fun originalVersionCodeOf(packageName: String): Long? = metaDataInt(packageName, "app.gameport.original_version_code")?.toLong()

    /** True for an Unreal game: its manifest carries the engine's own entries (`com.epicgames.ue4.GameActivity.*`, `com.epicgames.unreal.GameActivity.*`). */
    fun isUnreal(packageName: String): Boolean = runCatching {
        packageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA).metaData?.keySet()?.any { it.startsWith("com.epicgames.") } == true
    }.getOrDefault(false)

    /**
     * True when Android updates this game without asking: GamePort installed it, and the system is Android 12 or later with a game that targets
     * Android 12 or later (the ones that target an older version still ask, and Android 11 always asks). Seen on a device, it is the system
     * that decides in the end, so this tells what to expect, not a promise.
     */
    fun updatesWithoutConfirmation(packageName: String): Boolean = runCatching {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return@runCatching false
        val target = packageManager.getApplicationInfo(packageName, 0).targetSdkVersion
        val installer = packageManager.getInstallSourceInfo(packageName).installingPackageName
        target >= Build.VERSION_CODES.S && installer == context.packageName
    }.getOrDefault(false)

    fun isInstalled(packageName: String): Boolean = runCatching {
        packageManager.getPackageInfo(packageName, 0)
        true
    }.getOrDefault(false)

    /**
     * The intent that starts the game. On a headset it carries the platform's immersive category,
     * which is what makes the system open the game as a VR app rather than in a flat window.
     */
    fun launchIntent(packageName: String, immersive: Boolean = true): Intent? =
        packageManager.getLaunchIntentForPackage(packageName)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (immersive) device.vrPlatform?.immersiveLauncherCategories?.forEach(::addCategory)
        }

    /** The memory the system can still give out, in bytes. */
    fun availableMemory(): Long {
        val manager = context.getSystemService(ActivityManager::class.java) ?: return 0
        return ActivityManager.MemoryInfo().also(manager::getMemoryInfo).availMem
    }

    /**
     * Asks the apps the user installed to close their background processes, except the ones in [keep] and GamePort: the memory they hold goes back
     * to the game about to start. Android only closes what is not on screen and not doing foreground work. Returns how many apps were asked.
     */
    fun trimBackground(keep: Set<String>): Int {
        val manager = context.getSystemService(ActivityManager::class.java) ?: return 0
        val system = ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP
        val candidates = runCatching { packageManager.getInstalledApplications(0) }.getOrDefault(emptyList())
            .map { BackgroundTrim.Candidate(it.packageName, it.flags and system != 0) }
        val targets = BackgroundTrim.targets(candidates, keep + context.packageName)
        targets.forEach { runCatching { manager.killBackgroundProcesses(it) } }
        return targets.size
    }

    /**
     * Installs one APK, or a base APK with its splits, in a single session. [note] is told the steps (the files handed to the system, the confirmation it asks for),
     * so that a report says where an installation that seems stuck is.
     *
     * The system is given as long as it needs to do its own work, but once it has put a confirmation in front of the player the answer is waited for
     * only [confirmTimeoutMs]: a window the player never saw, or dismissed without the system noticing, would otherwise leave the install waiting
     * for ever with nothing on screen to get out of it.
     */
    suspend fun install(apks: List<File>, confirmTimeoutMs: Long = CONFIRM_TIMEOUT_MS, note: (String) -> Unit = {}): Outcome {
        val installer = packageManager.packageInstaller
        val sessionId = withContext(Dispatchers.IO) {
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
            // From Android 12, an installer may update, without asking, an app it installed itself, when the system's conditions are met
            // (the app is not running, among others). If they are not, the system asks as before, which is also what Android 11 always does
            // (the Nvidia Shield): the confirmation is handled below either way.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            val id = installer.createSession(params)
            installer.openSession(id).use { session ->
                apks.forEach { apk ->
                    apk.inputStream().use { input ->
                        session.openWrite(apk.name, 0, apk.length()).use { output ->
                            input.copyTo(output)
                            session.fsync(output)
                        }
                    }
                }
            }
            id
        }
        note("install: ${apks.sumOf { it.length() } / (1024 * 1024)} MB handed to the system's installer, committing (the player may install unknown apps: ${canInstallPackages()})")
        val outcome = try {
            awaitStatus(sessionId, note, confirmTimeoutMs) { intentSender -> installer.openSession(sessionId).use { it.commit(intentSender) } }
        } catch (e: CancellationException) {
            note("install: given up on, the system is told to forget its session")
            abandon(sessionId)
            throw e
        }
        // A session the system still holds keeps the room of the whole game, and makes the next try fail; one that went through is already gone.
        if (outcome != Outcome.Success) abandon(sessionId)
        return outcome
    }

    /** Tells the system to forget a session it was not able to finish. Harmless on one it already closed. */
    private suspend fun abandon(sessionId: Int) {
        withContext(NonCancellable) { runCatching { packageManager.packageInstaller.abandonSession(sessionId) } }
    }

    /** What an APK says about itself, enough to tell the builds of one game apart. Null when it cannot be read. */
    fun inspect(apk: java.io.File): ApkInfo? {
        val info = packageManager.getPackageArchiveInfo(apk.absolutePath, PackageManager.GET_CONFIGURATIONS) ?: return null
        val features = info.reqFeatures.orEmpty().mapNotNull { it.name }
        return ApkInfo(
            file = apk,
            packageName = info.packageName,
            versionCode = info.longVersionCode,
            versionName = info.versionName,
            forWatch = "android.hardware.type.watch" in features,
            forHeadset = "android.hardware.vr.headtracking" in features,
        )
    }

    suspend fun uninstall(packageName: String, note: (String) -> Unit = {}): Outcome =
        awaitStatus(packageName.hashCode(), note, CONFIRM_TIMEOUT_MS) { intentSender -> packageManager.packageInstaller.uninstall(packageName, intentSender) }

    /**
     * Hands the work to the system and reads its answers as they come: it may first ask the player to confirm, and only then say how it went.
     * Null [confirmTimeoutMs] waits for the answer as long as it takes.
     */
    private suspend fun awaitStatus(
        token: Int,
        note: (String) -> Unit = {},
        confirmTimeoutMs: Long? = null,
        start: (android.content.IntentSender) -> Unit,
    ): Outcome {
        val action = "${context.packageName}.PACKAGE_RESULT.$token"
        val signals = Channel<Signal>(Channel.UNLIMITED)
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(receiverContext: Context, intent: Intent) {
                val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
                val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
                    @Suppress("DEPRECATION")
                    val confirmation = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                    if (confirmation == null) note("install: the system asks for a confirmation but gave no window to open")
                    else {
                        // Which window it is matters: a system that sends the player to its "install unknown apps" page instead of a
                        // confirmation never answers, and the action is what tells the two apart in a report.
                        note("install: the system asks for a confirmation, its window is opened (${confirmation.action ?: confirmation.component?.flattenToShortString()})")
                        runCatching { context.startActivity(confirmation.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                            .onFailure { note("install: the confirmation window could not be opened (${it.javaClass.simpleName}: ${it.message})") }
                    }
                    signals.trySend(Signal.Asked)
                    return
                }
                // The system's own number goes down as it is: it is what tells a refusal by the device apart from a file it would not take.
                note("install: the system answers with status $status" + (message?.let { " ($it)" } ?: ""))
                signals.trySend(Signal.Done(outcomeOf(status, message)))
            }
        }
        ContextCompat.registerReceiver(context, receiver, IntentFilter(action), ContextCompat.RECEIVER_NOT_EXPORTED)
        val pending = PendingIntent.getBroadcast(
            context,
            token,
            Intent(action).setPackage(context.packageName),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )
        return try {
            runCatching { start(pending.intentSender) }.getOrElse { return Outcome.Failure(it.message) }
            collect(signals, confirmTimeoutMs, note)
        } finally {
            runCatching { context.unregisterReceiver(receiver) }
        }
    }

    /** Waits for the system to say how it went: as long as it takes while it works, and only [confirmTimeoutMs] once it has asked the player. */
    private suspend fun collect(signals: ReceiveChannel<Signal>, confirmTimeoutMs: Long?, note: (String) -> Unit): Outcome {
        var waitMs: Long? = null
        while (true) {
            val signal = if (waitMs == null) signals.receive() else withTimeoutOrNull(waitMs) { signals.receive() }
            when (signal) {
                null -> {
                    note("install: the confirmation got no answer in ${waitMs?.div(1000)} s: given up on")
                    return Outcome.NotConfirmed
                }
                Signal.Asked -> waitMs = confirmTimeoutMs
                is Signal.Done -> return signal.outcome
            }
        }
    }

    private fun outcomeOf(status: Int, message: String?): Outcome = when (status) {
        PackageInstaller.STATUS_SUCCESS -> Outcome.Success
        PackageInstaller.STATUS_FAILURE_ABORTED -> Outcome.Cancelled
        PackageInstaller.STATUS_FAILURE_CONFLICT -> Outcome.Conflict
        PackageInstaller.STATUS_FAILURE_BLOCKED -> Outcome.Blocked(message)
        else -> Outcome.Failure(message)
    }

    /** What the system says while it works on a session, in the order it says it. */
    private sealed interface Signal {
        /** It has put a confirmation in front of the player and waits for the answer. */
        data object Asked : Signal

        data class Done(val outcome: Outcome) : Signal
    }

    sealed interface Outcome {
        data object Success : Outcome

        /** The user dismissed the confirmation. */
        data object Cancelled : Outcome

        /** Another version signed with a different key is installed. */
        data object Conflict : Outcome

        /**
         * The system asked the player to confirm and nothing came back in time: the window was never seen, or it was dismissed without
         * the system saying so. Told apart from a refusal, as there is nothing wrong with the game or the device.
         */
        data object NotConfirmed : Outcome

        /**
         * The device itself would not have it ([PackageInstaller.STATUS_FAILURE_BLOCKED]): a rule of the headset, or its own store holding
         * the game. Nothing about the download changes that, so it is not a failure of the file.
         */
        data class Blocked(val reason: String?) : Outcome

        data class Failure(val reason: String?) : Outcome
    }

    /** Emits whenever any package is added or removed on the device. */
    fun packageChanges(): kotlinx.coroutines.flow.Flow<Unit> = kotlinx.coroutines.flow.callbackFlow {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                trySend(Unit)
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addDataScheme("package")
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        trySend(Unit)
        awaitClose { context.unregisterReceiver(receiver) }
    }
}

/** The facts about an APK that decide which build of a game suits a device. */
data class ApkInfo(
    val file: java.io.File,
    val packageName: String,
    val versionCode: Long,
    val versionName: String?,
    val forWatch: Boolean,
    val forHeadset: Boolean,
)

/**
 * How long the answer to a confirmation the system asked for is waited for. Long enough to put a headset back on and read it, short enough
 * that an install nobody is going to answer gives up by itself instead of sitting on the screen for ever.
 */
private const val CONFIRM_TIMEOUT_MS = 5 * 60_000L
