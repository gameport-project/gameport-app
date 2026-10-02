package app.gameport.core.install

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import android.os.Environment
import app.gameport.core.model.AndroidDepot
import app.gameport.core.model.Game
import app.gameport.core.model.InstallError
import app.gameport.core.model.InstallState
import app.gameport.core.model.VersionOption
import app.gameport.core.model.AuthState
import app.gameport.core.patch.GamePatcher
import app.gameport.core.patch.ApkPatch
import app.gameport.core.patch.PatchCatalog
import app.gameport.core.patch.PatchContext
import app.gameport.core.patch.PatchVersioning
import app.gameport.core.patch.patches.GamePortRemovalPatch
import app.gameport.core.patch.patches.PatchVersionPatch
import app.gameport.core.patch.patches.VrLauncherPatch
import app.gameport.core.steam.GameDownloader
import app.gameport.core.steam.SteamAuthRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/**
 * Download -> install -> forget the download. Downloads live in the app's own external folder and
 * are deleted as soon as the install succeeded; they are kept only when it failed or was
 * dismissed, so a retry does not start from zero, and removed on cancel or uninstall.
 */
@Singleton
class GameInstallRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val downloader: GameDownloader,
    private val auth: SteamAuthRepository,
    private val patcher: GamePatcher,
    private val packages: PackageGateway,
    private val installed: InstalledGames,
    private val builds: InstalledBuilds,
    private val updates: GameUpdatesRepository,
    private val library: app.gameport.core.steam.SteamLibraryRepository,
    private val playHistory: app.gameport.core.settings.PlayHistoryStore,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val operations = MutableStateFlow<Map<Int, InstallState>>(emptyMap())
    private val jobs = HashMap<Int, Job>()
    private val paused = java.util.Collections.synchronizedSet(HashSet<Int>())
    private val activeJobs = AtomicInteger()
    private val _isBusy = MutableStateFlow(false)

    /** True while a game is being downloaded, patched or installed. */
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()
    private val downloadSlots = Semaphore(MAX_CONCURRENT_DOWNLOADS)
    private val patchLock = Mutex()

    fun observe(appId: Int): Flow<InstallState> =
        combine(operations, packages.packageChanges()) { ops, _ -> stateOf(appId, ops) }

    /** Every game that is being installed, left half-installed, or installed: the downloads page. */
    fun observeAll(): Flow<Map<Int, InstallState>> =
        combine(operations, packages.packageChanges()) { ops, _ ->
            (ops.keys + installed.all().keys + leftoverIds()).associateWith { stateOf(it, ops) }
                .filterValues { it !is InstallState.NotInstalled }
        }

    private fun stateOf(appId: Int, ops: Map<Int, InstallState>): InstallState =
        ops[appId]
            ?: installed.all()[appId]?.takeIf(packages::isInstalled)?.let { InstallState.Installed(it) }
            ?: if (hasLeftovers(appId)) InstallState.Interrupted else InstallState.NotInstalled

    private fun leftoverIds(): List<Int> =
        downloadsRoot().listFiles { file -> file.isDirectory && file.list()?.isNotEmpty() == true }
            ?.mapNotNull { it.name.toIntOrNull() }.orEmpty()

    /** Stops everything and deletes all downloaded data. Installed games are left alone. */
    fun discardAll() {
        jobs.values.forEach { it.cancel() }
        downloadsRoot().deleteRecursively()
        operations.update { emptyMap() }
    }

    /**
     * Installs the base game plus the chosen [dlc] (only DLC the account owns is fetched). With
     * null, an interrupted install continues with the choice it started with.
     */
    fun install(game: Game, dlc: Set<Int>? = null) {
        if (jobs[game.appId]?.isActive == true) return
        val directory = downloadDirectory(game.appId)
        val chosen = dlc ?: readChosenDlc(directory)
        val depots = game.androidBuild?.depotsFor(chosen).orEmpty()
        notEnoughSpace(game.appId, depots)?.let { return fail(game.appId, it) }
        directory.mkdirs()
        writeChosenDlc(directory, chosen)
        keepAlive(start = true)
        val job = scope.launch(start = CoroutineStart.LAZY) {
            try {
                // A previous run may have got as far as the patched APK; then nothing is left to fetch.
                var apks = patchedApks(directory).let { patched -> if (patched.isEmpty()) patched else chooseBuild(game.appId, patched) ?: return@launch clearState(game.appId) }
                if (apks.isEmpty()) {
                    // Only a couple of games download at once, the rest wait their turn: several
                    // at a time would each keep buffers in memory and starve the app.
                    setState(game.appId, InstallState.Queued)
                    downloadSlots.withPermit {
                        setState(game.appId, InstallState.Downloading(0f))
                        val speed = SpeedMeter()
                        val check = VerifyingDetector()
                        downloader.download(game.appId, depots.map { it.id }, directory) { progress, received ->
                            setState(game.appId, InstallState.Downloading(progress, speed.update(received), check.update(received)))
                        }
                    }

                    val everything = findApks(directory)
                    if (everything.isEmpty()) {
                        // Nothing installable came down; do not leave gigabytes of it behind.
                        directory.deleteRecursively()
                        return@launch fail(game.appId, InstallError.NoApk)
                    }

                    val downloaded = chooseBuild(game.appId, everything) ?: return@launch clearState(game.appId).also { directory.deleteRecursively() }
                    val account = (auth.authState.value as? AuthState.SignedIn)?.account
                        ?: return@launch fail(game.appId, InstallError.NotSignedIn)
                    setState(game.appId, InstallState.Patching)
                    // Patching rewrites multi-gigabyte files, so one game at a time.
                    patchedDirectory(directory).deleteRecursively()
                    apks = patchLock.withLock { downloaded.map { original ->
                        val patched = File(patchedDirectory(directory), original.name)
                        patched.parentFile?.mkdirs()
                        // Written under another name and renamed when complete, so an interrupted
                        // patch is never mistaken for a finished one.
                        val partial = File(patched.parentFile, original.name + ".part")
                        patcher.patch(original, partial, PatchContext(game.appId, account.steamId, account.displayName, game.androidBuild?.isVr?.let { it && packages.isHeadset }, gameName = game.name, installedVersionCode = installed.all()[game.appId]?.let(packages::versionCodeOf)))
                        check(partial.renameTo(patched)) { "Could not finish the patched APK." }
                        // Keep only one copy of a multi-gigabyte game on disk.
                        original.delete()
                        patched
                    } }
                }
                val packageName = packages.packageNameOf(apks.first()) ?: return@launch fail(game.appId, InstallError.UnreadableApk)

                setState(game.appId, InstallState.Installing)
                when (val outcome = packages.install(apks)) {
                    PackageGateway.Outcome.Success -> {
                        placeObb(directory, packageName)
                        installed.put(game.appId, packageName)
                        // What this install came from, to recognise a newer build later.
                        builds.put(game.appId, InstalledBuild(depots.associate { it.id to it.manifestId }, chosen))
                        updates.markCurrent(game.appId)
                        directory.deleteRecursively()
                        clearState(game.appId)
                    }
                    PackageGateway.Outcome.Cancelled -> clearState(game.appId)
                    PackageGateway.Outcome.Conflict -> fail(game.appId, InstallError.VersionConflict)
                    is PackageGateway.Outcome.Failure -> fail(game.appId, InstallError.Other(outcome.reason))
                }
            } catch (e: CancellationException) {
                // A pause keeps what was downloaded; a cancel throws it away.
                if (paused.remove(game.appId)) {
                    clearState(game.appId)
                    throw e
                }
                directory.deleteRecursively()
                clearState(game.appId)
                throw e
            } catch (e: Exception) {
                fail(game.appId, InstallError.Other(e.message))
            }
        }
        job.invokeOnCompletion { keepAlive(start = false) }
        jobs[game.appId] = job
        job.start()
    }

    /** Installs Steam's newer build of an installed game over it, with the DLC it was installed with. Saves stay. */
    fun update(game: Game) = install(game, builds.get(game.appId)?.dlc.orEmpty())

    private fun keepAlive(start: Boolean) {
        val intent = Intent(context, InstallForegroundService::class.java)
        if (start) {
            if (activeJobs.getAndIncrement() == 0) runCatching { ContextCompat.startForegroundService(context, intent) }
        } else if (activeJobs.decrementAndGet() == 0) {
            context.stopService(intent)
        }
        _isBusy.value = activeJobs.get() > 0
    }

    /** The Android package of the game if it is installed on this device. */
    fun installedPackage(appId: Int): String? = installed.all()[appId]?.takeIf(packages::isInstalled)

    /** True when the installed game was patched by an older patcher than this GamePort has. */
    fun observePatchOutdated(appId: Int): Flow<Boolean> =
        packages.packageChanges().map { isPatchOutdated(appId) }

    /** Installed games whose patch is older than this GamePort's: they get an attention badge. */
    fun observeOutdatedPatches(): Flow<Set<Int>> =
        packages.packageChanges().map { installed.all().keys.filter(::isPatchOutdated).toSet() }

    private fun isPatchOutdated(appId: Int): Boolean {
        val packageName = installed.all()[appId]?.takeIf(packages::isInstalled) ?: return false
        val version = packages.metaDataInt(packageName, PatchVersionPatch.META_KEY) ?: 0
        // A player who took the patch off did so on purpose: nothing to update.
        return version != PatchVersionPatch.REMOVED && PatchVersioning.isOutdated(version, context)
    }

    /** True when the player took GamePort's patch off this installed game. */
    fun observePatchRemoved(appId: Int): Flow<Boolean> = packages.packageChanges().map {
        val packageName = installed.all()[appId]?.takeIf(packages::isInstalled)
        packageName != null && packages.metaDataInt(packageName, PatchVersionPatch.META_KEY) == PatchVersionPatch.REMOVED
    }

    /**
     * Patches an installed game again with the current patcher and installs the result over it.
     * It starts from the installed APK, so nothing is downloaded and the saves stay where they are.
     */
    fun repatch(appId: Int) = reinstall(appId, PatchCatalog.recommended)

    /**
     * Takes GamePort's patch off an installed game, from the installed APK as well. The game then
     * knows nothing of the Steam account, the cloud saves or the OpenXR layer.
     */
    fun removePatch(appId: Int) = reinstall(appId, listOf(VrLauncherPatch, GamePortRemovalPatch))

    /**
     * Development: takes GamePort's patch off first, then applies only the named patches, to find
     * which one a game does not get along with.
     */
    fun repatchWith(appId: Int, patchIds: Set<String>) = reinstall(
        appId,
        listOf(VrLauncherPatch, GamePortRemovalPatch) +
            PatchCatalog.all.filter { it.id in patchIds && it !== VrLauncherPatch && it !== PatchVersionPatch } +
            PatchVersionPatch,
    )

    private fun reinstall(appId: Int, patches: List<ApkPatch>) {
        if (jobs[appId]?.isActive == true) return
        val packageName = installed.all()[appId]?.takeIf(packages::isInstalled) ?: return
        val account = (auth.authState.value as? AuthState.SignedIn)?.account ?: return fail(appId, InstallError.NotSignedIn)
        val sources = packages.apkFilesOf(packageName).filter { it.isFile }
        if (sources.isEmpty()) return fail(appId, InstallError.UnreadableApk)
        val directory = downloadDirectory(appId).apply { mkdirs() }
        val needed = sources.sumOf { it.length() }
        if (needed > directory.usableSpace) {
            directory.deleteRecursively()
            return fail(appId, InstallError.NotEnoughSpace(neededBytes = needed, freeBytes = directory.usableSpace))
        }
        keepAlive(start = true)
        val job = scope.launch(start = CoroutineStart.LAZY) {
            try {
                setState(appId, InstallState.Patching)
                // Whether the game is VR decides which patches apply (a flat game gets none of the VR ones).
                val game = kotlinx.coroutines.withTimeoutOrNull(3_000) { library.observeGame(appId).first() }
                val isVr = game?.androidBuild?.isVr?.let { it && packages.isHeadset }
                val patched = patchLock.withLock {
                    patchedDirectory(directory).deleteRecursively()
                    sources.map { original ->
                        val result = File(patchedDirectory(directory), original.name)
                        result.parentFile?.mkdirs()
                        val partial = File(result.parentFile, original.name + ".part")
                        patcher.patch(original, partial, PatchContext(appId, account.steamId, account.displayName, isVr, gameName = game?.name, installedVersionCode = packageName.let(packages::versionCodeOf)), patches)
                        check(partial.renameTo(result)) { "Could not finish the patched APK." }
                        result
                    }
                }
                setState(appId, InstallState.Installing)
                when (val outcome = packages.install(patched)) {
                    PackageGateway.Outcome.Success, PackageGateway.Outcome.Cancelled -> clearState(appId)
                    PackageGateway.Outcome.Conflict -> fail(appId, InstallError.VersionConflict)
                    is PackageGateway.Outcome.Failure -> fail(appId, InstallError.Other(outcome.reason))
                }
            } catch (e: CancellationException) {
                clearState(appId)
                throw e
            } catch (e: Exception) {
                fail(appId, InstallError.Other(e.message))
            } finally {
                directory.deleteRecursively()
            }
        }
        job.invokeOnCompletion { keepAlive(start = false) }
        jobs[appId] = job
        job.start()
    }

    fun cancel(appId: Int) {
        jobs[appId]?.cancel()
    }

    /** Stops a download but keeps the files, so it can be resumed later (it then shows as interrupted). */
    fun pause(appId: Int) {
        val job = jobs[appId]?.takeIf { it.isActive } ?: return
        paused.add(appId)
        job.cancel()
    }

    /** Throws away an interrupted download. */
    fun discard(appId: Int) {
        scope.launch {
            downloadDirectory(appId).deleteRecursively()
            clearState(appId)
        }
    }

    fun uninstall(appId: Int) {
        scope.launch {
            val packageName = installed.all()[appId]
            if (packageName != null && packages.isInstalled(packageName)) {
                if (packages.uninstall(packageName) != PackageGateway.Outcome.Success) return@launch
            }
            installed.remove(appId)
            playHistory.forget(appId)
            builds.remove(appId)
            downloadDirectory(appId).deleteRecursively()
            clearState(appId)
        }
    }

    /**
     * True when the installed game asks for access to shared storage and has not been given it, and
     * the player has not been told yet. Some games ask again at every resume, and on a headset that
     * request takes the focus from the game until they crash (seen with Underdogs).
     */
    fun shouldExplainStoragePermission(appId: Int): Boolean {
        val packageName = installed.all()[appId]?.takeIf(packages::isInstalled) ?: return false
        if (prompts.getBoolean(appId.toString(), false)) return false
        return packages.requestsButLacks(packageName, STORAGE_PERMISSION)
    }

    fun markStoragePermissionExplained(appId: Int) {
        prompts.edit().putBoolean(appId.toString(), true).apply()
    }

    private val prompts by lazy { context.getSharedPreferences("gameport_storage_prompt", Context.MODE_PRIVATE) }

    /** The system page of the installed game's own settings, where its permissions can be granted. */
    fun appSettingsIntent(appId: Int): Intent? = installed.all()[appId]?.takeIf(packages::isInstalled)?.let { packageName ->
        Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:$packageName"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    /** The intent that starts the installed game, or null when it is not installed. */
    /** [isVr] false starts a flat game as a normal window, without the immersive categories of a headset. */
    fun launchIntent(appId: Int, isVr: Boolean? = null) = installed.all()[appId]?.let { packages.launchIntent(it, immersive = isVr != false) }
        // The patched game reads this mark to know GamePort started it, so it can open GamePort again when it closes.
        ?.also { it.putExtra("app.gameport.launched", true); playHistory.markPlayed(appId) }

    /**
     * Patching writes a second copy of the APK next to the download, so the worst case needs
     * about twice the download size. Games whose size Steam does not tell are not checked.
     */
    private fun notEnoughSpace(appId: Int, depots: List<AndroidDepot>): InstallError? {
        val size = depots.sumOf { it.installBytes }.takeIf { it > 0 } ?: return null
        val directory = downloadDirectory(appId).apply { mkdirs() }
        // What a resumed download already holds does not need to be found again.
        val alreadyThere = directory.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        val needed = size * 2 - alreadyThere
        val free = directory.usableSpace
        if (needed <= free) return null
        return InstallError.NotEnoughSpace(neededBytes = needed, freeBytes = free)
    }

    private fun hasLeftovers(appId: Int): Boolean = downloadDirectory(appId).list()?.isNotEmpty() == true

    private fun downloadsRoot(): File = context.getExternalFilesDir("downloads") ?: File(context.filesDir, "downloads")

    private fun downloadDirectory(appId: Int): File = File(downloadsRoot(), appId.toString())

    private fun readChosenDlc(directory: File): Set<Int> =
        runCatching { File(directory, CHOICE_FILE).readText().split(',').mapNotNull { it.trim().toIntOrNull() }.toSet() }
            .getOrDefault(emptySet())

    private fun writeChosenDlc(directory: File, dlc: Set<Int>) {
        runCatching { File(directory, CHOICE_FILE).writeText(dlc.joinToString(",")) }
    }

    private fun patchedDirectory(directory: File) = File(directory, PATCHED_DIR)

    private fun patchedApks(directory: File): List<File> =
        patchedDirectory(directory).listFiles { file -> file.extension.equals("apk", ignoreCase = true) }?.toList().orEmpty()

    /**
     * A download can hold several builds of one game (phone, headset, watch), all with the same package: Android
     * refuses to install them together. APKs of one package with different version codes are such builds; APKs
     * with the same version code are the parts of one build and go together. A watch build is never wanted. Among
     * the rest, the one made for this device wins; if that does not settle it, the player is asked.
     * Returns null when the player is asked and cancels.
     */
    private suspend fun chooseBuild(appId: Int, apks: List<File>): List<File>? {
        val infos = apks.map { file -> packages.inspect(file) ?: return apks }
        val builds = infos.filterNot { it.forWatch }.ifEmpty { infos }
            .groupBy { it.packageName }.values.flatMap { sameApp ->
                // One entry per build: the parts of a build share a version code.
                sameApp.groupBy { it.versionCode }.values
            }
        val byApp = builds.groupBy { it.first().packageName }
        val kept = byApp.values.flatMap { candidates ->
            if (candidates.size == 1) return@flatMap candidates.single()
            val suited = candidates.filter { build -> build.any { it.forHeadset } == packages.isHeadset }
            val pick = suited.singleOrNull() ?: askWhichBuild(appId, candidates) ?: return null
            pick
        }
        return kept.map { it.file }
    }

    private val choices = java.util.concurrent.ConcurrentHashMap<Int, kotlinx.coroutines.CompletableDeferred<String?>>()

    private suspend fun askWhichBuild(appId: Int, candidates: List<List<ApkInfo>>): List<ApkInfo>? {
        val deferred = kotlinx.coroutines.CompletableDeferred<String?>()
        choices[appId] = deferred
        setState(appId, InstallState.ChoosingVersion(candidates.map { build ->
            val main = build.first()
            VersionOption(main.file.name, main.versionName, main.versionCode, build.any { it.forHeadset })
        }))
        try {
            val chosen = deferred.await() ?: return null
            return candidates.firstOrNull { it.first().file.name == chosen }
        } finally {
            choices.remove(appId)
        }
    }

    /** The player's answer to [InstallState.ChoosingVersion]: the id of the build, or null to give up. */
    fun chooseVersion(appId: Int, optionId: String?) {
        choices[appId]?.complete(optionId)
    }

    /** APKs still to patch: the ones outside the folder that holds the patched results. */
    private fun findApks(directory: File): List<File> =
        directory.walkTopDown()
            .onEnter { it.name != PATCHED_DIR }
            .filter { it.isFile && it.extension.equals("apk", ignoreCase = true) }
            .toList()

    /**
     * Best effort: an OBB expansion belongs under Android/obb/<package>, which Android 11+ only
     * lets an app with "all files access" write to.
     */
    private suspend fun placeObb(directory: File, packageName: String) = withContext(Dispatchers.IO) {
        val obbs = directory.walkTopDown().filter { it.isFile && it.extension.equals("obb", ignoreCase = true) }.toList()
        if (obbs.isEmpty()) return@withContext
        runCatching {
            val target = File(Environment.getExternalStorageDirectory(), "Android/obb/$packageName").apply { mkdirs() }
            obbs.forEach { it.copyTo(File(target, it.name), overwrite = true) }
        }
    }

    private fun setState(appId: Int, state: InstallState) = operations.update { it + (appId to state) }

    private fun clearState(appId: Int) = operations.update { it - appId }

    private fun fail(appId: Int, error: InstallError) = setState(appId, InstallState.Failed(error))
}

private const val PATCHED_DIR = "patched"
private const val STORAGE_PERMISSION = "android.permission.READ_EXTERNAL_STORAGE"
private const val MAX_CONCURRENT_DOWNLOADS = 2

/**
 * Tells the check of files already on disk (progress moves, nothing comes from the network) from a download.
 * It only switches after a few seconds without data, and back after a real flow, so the label does not flicker
 * when two chunks happen to finish without a byte in between.
 */
internal class VerifyingDetector(private val now: () -> Long = System::currentTimeMillis) {
    private var lastReceived = 0L
    private var lastGrowth = now()
    private var fetchedSince = 0L
    private var verifying = false

    fun update(received: Long): Boolean {
        val delta = received - lastReceived
        lastReceived = received
        if (delta > 0) lastGrowth = now()
        if (!verifying) {
            if (now() - lastGrowth > IDLE_MS) {
                verifying = true
                fetchedSince = 0L
            }
        } else {
            fetchedSince += delta.coerceAtLeast(0)
            if (fetchedSince > BACK_TO_DOWNLOAD_BYTES) verifying = false
        }
        return verifying
    }

    private companion object {
        const val IDLE_MS = 4_000L
        const val BACK_TO_DOWNLOAD_BYTES = 512 * 1024L
    }
}
private const val CHOICE_FILE = ".gameport-dlc"

/** Smooths the network rate over a few seconds so the figure on screen does not jump around. */
internal class SpeedMeter(private val now: () -> Long = System::currentTimeMillis) {
    private var lastBytes = 0L
    private var lastTime = now()
    private var smoothed = 0.0

    /** Feeds the bytes received so far; returns bytes per second. */
    fun update(receivedBytes: Long): Long {
        val time = now()
        val elapsed = time - lastTime
        if (elapsed >= WINDOW_MS) {
            val instant = (receivedBytes - lastBytes).coerceAtLeast(0) * 1000.0 / elapsed
            smoothed = if (smoothed == 0.0) instant else smoothed * (1 - SMOOTHING) + instant * SMOOTHING
            lastBytes = receivedBytes
            lastTime = time
        }
        return smoothed.toLong()
    }

    private companion object {
        const val WINDOW_MS = 1_000L
        const val SMOOTHING = 0.3
    }
}
