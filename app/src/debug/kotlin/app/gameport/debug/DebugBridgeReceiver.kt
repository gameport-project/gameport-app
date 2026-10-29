package app.gameport.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.util.Log
import app.gameport.core.install.GameEventLog
import app.gameport.core.install.GameInstallRepository
import app.gameport.core.install.InstalledGames
import app.gameport.core.install.PackageGateway
import app.gameport.core.model.AuthState
import app.gameport.core.patch.PatchCatalog
import app.gameport.core.patch.patches.XrLayerPatch
import app.gameport.core.model.Ownership
import app.gameport.core.steam.GameDownloader
import app.gameport.core.steam.SteamAuthRepository
import app.gameport.core.steam.SteamLibraryRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Lets a problem be looked into, and an install driven, from a cable. A test build only: the released GamePort has no such receiver, and
 * the debug build carries its own package name (`app.gameport.dev`), so the two never share one.
 *
 * It exists because the hard part of an install problem on a headset is not reading the code, it is seeing what the device did: which
 * state an install sat in, what the system answered, what the download holds. `adb logcat` shows what GamePort chose to print; this shows
 * what it knows, and starts or throws away an install without a headset being put on between every try.
 *
 * Every answer goes to logcat under [TAG]; what fits also comes back as the broadcast's result, which `adb` prints by itself.
 * Broadcasts named only by their action do not reach a receiver of the manifest (Android 8 and later), so the component is named:
 *
 *     adb shell am broadcast -n app.gameport.dev/app.gameport.debug.DebugBridgeReceiver --es cmd state
 *     adb logcat -s GamePortDebug
 *
 * The commands: `state` (where every install stands), `perm` (what the system lets GamePort do), `storage` (whether a game's expansion
 * folder can really be written), `games` (the account's games that have an Android build, with the package each installs under),
 * `log --ei appId N` (what was done with one game), `files --ei appId N` (what its download holds), `install --ei appId N`,
 * `cancel --ei appId N`, `discard --ei appId N`, `rename --ei appId N`, `nolayer --ei appId N`, `restore --ei appId N`.
 */
class DebugBridgeReceiver : BroadcastReceiver() {
    /** What the receiver needs of the app. Asked for by hand rather than injected: a receiver's `onReceive` cannot call its parent's. */
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Parts {
        fun installer(): GameInstallRepository

        fun library(): SteamLibraryRepository

        fun downloader(): GameDownloader

        fun events(): GameEventLog

        fun packages(): PackageGateway

        fun installed(): InstalledGames

        fun auth(): SteamAuthRepository
    }

    private lateinit var installer: GameInstallRepository
    private lateinit var library: SteamLibraryRepository
    private lateinit var downloader: GameDownloader
    private lateinit var events: GameEventLog
    private lateinit var packages: PackageGateway
    private lateinit var installed: InstalledGames
    private lateinit var auth: SteamAuthRepository

    override fun onReceive(context: Context, intent: Intent) {
        val parts = EntryPointAccessors.fromApplication(context.applicationContext, Parts::class.java)
        installer = parts.installer()
        library = parts.library()
        downloader = parts.downloader()
        events = parts.events()
        packages = parts.packages()
        installed = parts.installed()
        auth = parts.auth()
        val command = intent.getStringExtra("cmd")?.lowercase()
        // Nothing is read from the device on this thread: a receiver is given a few seconds, and reading an install's state waits on a flow.
        val pending = goAsync()
        scope.launch {
            val answer = runCatching { answer(context, command, intent.getIntExtra("appId", 0).takeIf { it != 0 }) }
                .getOrElse { "the command failed: ${it.javaClass.simpleName}: ${it.message}" }
            answer.lineSequence().forEach { Log.i(TAG, it) }
            // What `adb` prints by itself, for a command whose answer is one line.
            pending.setResultData(answer.lineSequence().firstOrNull())
            pending.finish()
        }
    }

    private suspend fun answer(context: Context, command: String?, appId: Int?): String = when (command) {
        "state" -> state()
        "perm" -> permissions(context)
        "storage" -> storage(context)
        "games" -> installable()
        "audit" -> audit(appId)
        "log" -> appId?.let { events.read(it).ifBlank { "nothing recorded for app $it" } } ?: NEEDS_APP_ID
        "files" -> appId?.let { installer.downloadReport(it) } ?: NEEDS_APP_ID
        "install" -> appId?.let { start(it) } ?: NEEDS_APP_ID
        "cancel" -> appId?.let { installer.cancel(it); "app $it: the running install was asked to stop" } ?: NEEDS_APP_ID
        "discard" -> appId?.let { installer.discard(it); "app $it: its download is being thrown away" } ?: NEEDS_APP_ID
        // Experimental, and here rather than on a screen: what a headset's licence check is keyed on is still being worked out, and
        // nothing should look like a feature until somebody has decided it is one.
        "rename" -> appId?.let {
            installer.renamePackage(it)
            "app $it: it will be installed under a name of GamePort's making; ask for the install next"
        } ?: NEEDS_APP_ID
        // Two games fail at the graphics, in two different engines, with GamePort's OpenXR layer in both: this leaves the layer out so
        // the layer can be told apart from the device. It works on the APK already on the device, so nothing is fetched.
        "nolayer" -> appId?.let {
            val without = PatchCatalog.recommended.map { patch -> patch.id }.toSet() - XrLayerPatch.id
            installer.repatchWith(it, without)
            "app $it: patched again without ${XrLayerPatch.id}; the system will ask to confirm"
        } ?: NEEDS_APP_ID
        "restore" -> appId?.let {
            installer.repatch(it)
            "app $it: patched again with everything GamePort recommends; the system will ask to confirm"
        } ?: NEEDS_APP_ID
        else -> "unknown command ${command ?: "(none)"}: one of state, perm, storage, games, audit, log, files, install, cancel, discard, rename, nolayer, restore"
    }

    /**
     * What each game's depots hold, read from the manifests Steam publishes and not from a download: the APK's name and weight, and what
     * sits beside it in expansion files. With no appId it looks at every game of the account that has an Android build.
     *
     * It cannot say which platform SDK a game carries, nor which Android version it asks for. Both of those live inside the APK, and a
     * depot's manifest names the depot's own files (the APK, the expansion bundles), never what is in them. Answering those needs the
     * APK itself, which is a fraction of a download rather than the whole of it: 583 MB of Hard Bullet's 9.7 GB.
     */
    private suspend fun audit(appId: Int?): String {
        val shelf = withTimeoutOrNull(LOOKUP_MS) { library.observeLibrary().first() }
            ?: return "the library did not answer in ${LOOKUP_MS / 1000} s"
        val games = shelf.games.filter { it.androidBuild != null }
            .let { all -> if (appId == null) all else all.filter { it.appId == appId } }
            .sortedBy { it.name.lowercase() }
        if (games.isEmpty()) return "no game of the account has an Android build" + (appId?.let { " under $it" } ?: "")
        Log.i(TAG, "reading the manifests of ${games.size} game(s); nothing is downloaded, and what is inside an APK cannot be seen this way")
        var answered = 0
        for (game in games) {
            val depots = game.androidBuild?.baseDepots.orEmpty()
            val files = mutableListOf<Pair<String, Long>>()
            var read = 0
            for (depot in depots) {
                // Steam hands a depot's manifest to the app that declares it, which is the game itself unless it says otherwise.
                val got = runCatching { downloader.manifestFiles(depot.declaredBy ?: game.appId, depot.id, depot.manifestId) }.getOrNull() ?: continue
                read++
                files += got
            }
            if (read > 0) answered++
            val verdict = if (read == 0) {
                "the manifests could not be read (${depots.size} depot(s))"
            } else {
                val apks = files.filter { it.first.endsWith(".apk", ignoreCase = true) }
                val beside = files.filterNot { it.first.endsWith(".apk", ignoreCase = true) }
                val apkPart = if (apks.isEmpty()) "no APK in the depots" else apks.joinToString { "${it.first} (${it.second / 1_000_000} MB)" }
                "$apkPart, and ${beside.size} file(s) beside it weighing ${beside.sumOf { it.second } / 1_000_000} MB"
            }
            // Logged as each game is answered, so a long audit is readable while it runs and loses nothing if it is cut short.
            Log.i(TAG, "${game.appId}  ${game.name}: $verdict")
        }
        return "looked at ${games.size} game(s), ${answered} answered; the lines above hold the result"
    }

    /**
     * The account's games that have an Android build, with the package name each would install under where it is known. A headset's own
     * store matches a game it did not install by that package name, so the package is what two catalogues have to be compared by, not
     * the title: the same game is named differently in each.
     */
    private suspend fun installable(): String {
        val shelf = withTimeoutOrNull(LOOKUP_MS) { library.observeLibrary().first() }
            ?: return "the library did not answer in ${LOOKUP_MS / 1000} s"
        val games = shelf.games.filter { it.androidBuild != null }.sortedBy { it.name.lowercase() }
        if (games.isEmpty()) return "no game of the account has an Android build"
        val known = games.count { it.androidBuild?.packageName != null }
        return "${games.size} game(s) of the account have an Android build, ${known} with the package already known\n" +
            games.joinToString("\n") { game ->
                val pkg = game.androidBuild?.packageName ?: "(package not known until it is installed)"
                val shared = if (game.ownership == Ownership.FAMILY_SHARED) "  [family shared]" else ""
                "${game.appId}  $pkg  ${game.name}$shared"
            }
    }

    /**
     * Whether a game's expansion folder can really be written, tried here rather than worked out from the permissions. The two do not
     * always agree: a permission can read as granted while the storage the process was given still refuses the write, and a shell with
     * the same user is given a different view of the storage again, so only the app itself can answer it.
     */
    private fun storage(context: Context): String = buildString {
        appendLine("Android ${Build.VERSION.SDK_INT}")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            appendLine("access to all files: ${Environment.isExternalStorageManager()}")
        } else {
            val granted = context.checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
            appendLine("permission to write shared storage: $granted")
        }
        val obb = File(Environment.getExternalStorageDirectory(), "Android/obb")
        appendLine("$obb exists=${obb.exists()} canWrite=${obb.canWrite()}")
        val probe = File(obb, "gameport.probe")
        runCatching { probe.delete() }
        val made = runCatching { probe.mkdirs() }.fold({ "$it" }, { "${it.javaClass.simpleName}: ${it.message}" })
        appendLine("creating a folder in it: $made (exists=${probe.exists()})")
        if (probe.exists()) {
            val inside = File(probe, "probe.txt")
            appendLine("writing a file inside it: " + runCatching { inside.writeText("probe"); "ok, ${inside.length()} bytes" }
                .getOrElse { "${it.javaClass.simpleName}: ${it.message}" })
            inside.delete()
            probe.delete()
            appendLine("taken back off: gone=${!probe.exists()}")
        }
    }.trimEnd()

    /** Where every install stands, which is what an install that seems stuck is looked at for. */
    private suspend fun state(): String {
        val states = withTimeoutOrNull(LOOKUP_MS) { installer.observeAll().first() }
            ?: return "the installs did not answer in ${LOOKUP_MS / 1000} s"
        if (states.isEmpty()) return "nothing is downloading and no game is installed"
        return states.entries.sortedBy { it.key }.joinToString("\n") { (id, state) ->
            "app $id: $state" + (installed.all()[id]?.let { " package=$it installed=${packages.isInstalled(it)}" } ?: "")
        }
    }

    /** What the system lets GamePort do, which decides whether an install can finish at all. */
    private fun permissions(context: Context): String = buildString {
        appendLine("device: ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        appendLine("headset: ${packages.isHeadset}, family: ${packages.vrFamily}")
        appendLine("may install unknown apps: ${packages.canInstallPackages()}")
        val allFiles = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Environment.isExternalStorageManager().toString() else "not asked below Android 11"
        appendLine("access to all files: $allFiles")
        appendLine("signed in to Steam: ${auth.authState.value is AuthState.SignedIn}, offline: ${auth.offline.value}")
    }.trimEnd()

    private suspend fun start(appId: Int): String {
        val game = withTimeoutOrNull(LOOKUP_MS) { library.observeGame(appId).filterNotNull().first() }
            ?: return "app $appId is not in the library (signed in? the library may still be loading)"
        installer.install(game, null)
        return "app $appId: the install of ${game.name} was asked for"
    }

    private companion object {
        const val TAG = "GamePortDebug"
        const val NEEDS_APP_ID = "this command needs a game: add --ei appId <steam app id>"

        /** How long the library and the installs are given to answer. */
        const val LOOKUP_MS = 10_000L

    }
}

/**
 * Outlives one broadcast on purpose: an answer is read from flows and the stores, which a receiver's own few seconds do not cover.
 * The work is held by `goAsync`, so the system keeps the process alive for it.
 */
private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
