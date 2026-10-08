package app.gameport.core.sync

import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import app.gameport.core.device.DeviceProfile
import app.gameport.core.install.InstalledGames
import app.gameport.core.install.PackageGateway
import app.gameport.core.model.AuthState
import app.gameport.core.model.Game
import app.gameport.core.steam.SteamAuthRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A report saved in the Downloads folder. */
data class SavedReport(val fileName: String, val uri: Uri)

/**
 * Builds the file a player attaches to a bug report: a zip with what explains a problem with one game (versions,
 * files, permissions, the game's and GamePort's log lines, how the last run ended). Nothing that identifies the
 * player goes in: see [ReportRedactor]. The zip is saved in Downloads, and nothing is sent anywhere by GamePort.
 *
 * The file name is plain ASCII whatever the language or the game's name, and the entries are UTF-8, so every
 * file manager reads it.
 */
@Singleton
class ProblemReporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val installed: InstalledGames,
    private val installer: app.gameport.core.install.GameInstallRepository,
    private val packages: PackageGateway,
    private val device: DeviceProfile,
    private val auth: SteamAuthRepository,
    private val store: ReportStore,
    private val events: app.gameport.core.install.GameEventLog,
    private val gameSettings: app.gameport.core.settings.GameSettingsStore,
    private val controllers: app.gameport.core.settings.ControllerMappingStore,
    private val syncStatus: SyncStatusStore,
    private val userSettings: app.gameport.core.settings.UserSettings,
) {
    /** Writes the report to the Downloads folder and returns where it is. */
    suspend fun save(game: Game, installFailure: String? = null): SavedReport? = withContext(Dispatchers.IO) {
        // A game whose install failed has no package yet: the report then holds the download and the events instead.
        val packageName = installed.all()[game.appId]?.takeIf(packages::isInstalled)
        if (packageName == null && installFailure == null) return@withContext null
        val name = "gameport-report-${game.appId}-${SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())}.zip"
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, name)
            put(MediaStore.Downloads.MIME_TYPE, "application/zip")
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return@withContext null
        try {
            // Written straight to the file as it is built: a report never sits whole in memory.
            resolver.openOutputStream(uri)?.use { raw -> writeZip(game, packageName, installFailure, raw.buffered()) } ?: return@withContext null
            resolver.update(uri, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null)
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            return@withContext null
        }
        SavedReport(name, uri)
    }

    /** The page that opens a new ticket on the project's tracker with the title and the facts filled in. */
    fun ticketUrl(game: Game, fileName: String?): Uri {
        val body = buildString {
            appendLine("**Game:** ${game.name} (Steam app ${game.appId})")
            appendLine("**GamePort:** ${gameportVersion()}")
            appendLine("**Device:** ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE}")
            appendLine()
            appendLine("**What happened:**")
            appendLine()
            appendLine("**What was expected:**")
            appendLine()
            if (fileName != null) appendLine("The report file `$fileName` (saved in Downloads) is attached below.")
            else appendLine("Please attach the report file saved in Downloads.")
        }
        return Uri.parse("https://github.com/gameport-project/gameport-app/issues/new").buildUpon()
            .appendQueryParameter("title", "[Game] ${game.name}: ")
            .appendQueryParameter("body", body)
            .build()
    }

    private fun writeZip(game: Game, packageName: String?, installFailure: String?, target: java.io.OutputStream) {
        val names = (auth.authState.value as? AuthState.SignedIn)?.account?.displayName?.let(::listOf).orEmpty()
        fun clean(text: String) = ReportRedactor.clean(text, names)
        ZipOutputStream(target, Charsets.UTF_8).use { zip ->
            fun entry(name: String, text: String) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(text.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            entry("README.txt", README)
            entry("report.json", clean(summary(game, packageName, installFailure)))
            entry("device.txt", clean(deviceFacts()))
            entry("settings.txt", clean(settingsFacts(game.appId)))
            if (packageName != null) {
                entry("performance.txt", clean(store.text(packageName, "performance.txt").ifBlank { "(no performance line received from the game: it has not run with the current patch, or the runtime printed none while it ran)" }))
                entry("files.txt", clean(files(packageName)))
                entry("apk-contents.txt", clean(apkContents(packageName)))
                entry("steam-interfaces.txt", clean(steamInterfaces(packageName)))
                entry("patch.txt", clean(patchFacts(packageName)))
                entry("manifest.txt", clean(manifest(packageName)))
                entry("components.txt", clean(components(packageName)))
                entry("launch.txt", clean(launchFacts(packageName)))
                entry("loaded-libraries.txt", clean(store.text(packageName, "libraries.txt").ifBlank { "(the game has not handed it over yet)" }))
                entry("game-log-start.txt", clean(store.text(packageName, "log-start.txt").ifBlank { "(the game has not handed over its log yet)" }))
                entry("game-log.txt", clean(store.hookLog(packageName).ifBlank { "(empty: a short run fits in game-log-start.txt, or the game has not handed over its log)" }))
                // The run before the last one, when the last one began after it ended: the one that explains how it ended.
                store.previousLogStart(packageName).takeIf { it.isNotBlank() }?.let { entry("previous-run-log-start.txt", clean(it)) }
                store.previousHookLog(packageName).takeIf { it.isNotBlank() }?.let { entry("previous-run-log.txt", clean(it)) }
                entry("engine-logs.txt", clean(store.text(packageName, "engine.log").ifBlank { "(no log file of the game's engine found)" }))
                entry("last-exits.txt", clean(ExitReasons.withTimes(store.previousExit(packageName)).ifBlank { "(not available)" }))
                entry("diagnosis.txt", ReportDiagnosis.of(store.text(packageName, "libraries.txt"), store.text(packageName, "log-start.txt"), store.previousExit(packageName), store.lastDataMillis(packageName)?.let { (System.currentTimeMillis() - it) / 1000 }))
                store.bytes(packageName, "tombstone.pb")?.let { trace ->
                    zip.putNextEntry(ZipEntry("crash-tombstone.pb"))
                    zip.write(trace)
                    zip.closeEntry()
                    entry("crash-tombstone-info.txt", store.text(packageName, "tombstone-info.txt"))
                }
            }
            if (installFailure != null) entry("install.txt", clean("Install error: $installFailure\n\n" + installer.downloadReport(game.appId)))
            entry("events.txt", clean(events.read(game.appId).ifBlank { "(nothing recorded for this game)" }))
            entry("gameport-log.txt", clean(ownLog(packageName ?: "")))
        }
    }

    private fun summary(game: Game, packageName: String?, installFailure: String?): String {
        fun q(value: Any?) = "\"" + value.toString().replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ") + "\""
        val info = packageName?.let { runCatching { context.packageManager.getPackageInfo(it, 0) }.getOrNull() }
        val patchVersion = packageName?.let { packages.metaDataInt(it, "app.gameport.patch_version") }
        val originalCode = packageName?.let { packages.metaDataInt(it, "app.gameport.original_version_code") }
        val facts = linkedMapOf<String, Any?>(
            "created" to SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.US).format(Date()),
            "gameport" to gameportVersion(),
            "game.name" to game.name,
            "game.steamAppId" to game.appId,
            "game.kind" to game.kind,
            "game.vr" to game.androidBuild?.isVr,
            "game.installed" to (packageName != null),
            "install.error" to installFailure,
            "game.package" to packageName,
            "game.versionName" to info?.versionName,
            "game.versionCode" to info?.longVersionCode,
            "game.originalVersionCode" to originalCode,
            "game.patchVersion" to patchVersion,
            "device.manufacturer" to Build.MANUFACTURER,
            "device.model" to Build.MODEL,
            "device.os" to Build.DISPLAY,
            "device.android" to Build.VERSION.SDK_INT,
            "device.fingerprint" to Build.FINGERPRINT,
            "device.vrPlatform" to device.vrPlatform?.id,
            "steam.connected" to (auth.connection.value.name),
            "suspicion" to packageName?.let { store.suspected.value[it]?.name },
            // How old what the game handed over is: a game that logs little leaves it as it was.
            "hook.lastDataSecondsAgo" to packageName?.let { store.lastDataMillis(it) }?.let { (System.currentTimeMillis() - it) / 1000 },
            "lastSession.seconds" to packageName?.let { store.lastSessionMillis(it) }?.let { it / 1000 },
        )
        return facts.entries.joinToString(",\n", "{\n", "\n}\n") { (key, value) -> "  ${q(key)}: ${if (value == null) "null" else q(value)}" }
    }

    private fun files(packageName: String): String = buildString {
        appendLine("APK files:")
        packages.apkFilesOf(packageName).forEach { appendLine("  ${it.name}  ${it.length()} bytes") }
        appendLine("Expansion files (Android/obb):")
        val obb = File(Environment.getExternalStorageDirectory(), "Android/obb/$packageName")
        obb.listFiles()?.sortedBy { it.name }?.forEach { appendLine("  ${it.name}  ${it.length()} bytes") }
            ?: appendLine("  (none, or not readable)")
    }

    private fun manifest(packageName: String): String = buildString {
        val manager = context.packageManager
        val info = runCatching { manager.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS or PackageManager.GET_META_DATA) }.getOrNull()
        val app = info?.applicationInfo
        appendLine("targetSdk=${app?.targetSdkVersion} minSdk=${app?.minSdkVersion}")
        appendLine("Permissions (granted?):")
        info?.requestedPermissions?.forEachIndexed { index, permission ->
            val granted = (info.requestedPermissionsFlags?.getOrNull(index) ?: 0) and android.content.pm.PackageInfo.REQUESTED_PERMISSION_GRANTED != 0
            appendLine("  $permission  $granted")
        }
        appendLine("Meta-data:")
        app?.metaData?.keySet()?.sorted()?.forEach { key -> appendLine("  $key = ${app.metaData.get(key)}") }
    }

    /** GamePort's own log lines (an app only sees its own): warnings and errors, and everything GamePort tags as its own. */
    private fun ownLog(packageName: String): String = runCatching {
        val process = ProcessBuilder("logcat", "-d", "-t", "4000", "-v", "threadtime").redirectErrorStream(true).start()
        val lines = process.inputStream.bufferedReader(Charsets.UTF_8).readLines()
        process.destroy()
        lines.filter { line ->
            val parts = line.trim().split(Regex("\\s+"), limit = 7)
            val level = parts.getOrNull(4)
            val tag = parts.getOrNull(5)?.removeSuffix(":").orEmpty()
            level == "W" || level == "E" || level == "F" || tag.startsWith("GP") || (packageName.isNotEmpty() && line.contains(packageName))
        }.takeLast(1500).joinToString("\n")
    }.getOrDefault("(GamePort's log is not available)")

    /** The player's choices that change how the game runs, and the sync state. Nothing about the account. */
    private fun settingsFacts(appId: Int): String = buildString {
        val settings = gameSettings.get(appId)
        appendLine("seated=${settings.seated} heightCm=${settings.heightCm ?: "default"} defaultHeightCm=${gameSettings.defaults.value.heightCm}")
        val mapping = controllers.get(appId)
        appendLine("controller mapping: $mapping")
        appendLine("layer config: ${controllers.layerConfig(appId).ifBlank { "(none)" }}")
        appendLine("save sync status: ${syncStatus.statuses.value[appId] ?: "unknown"}")
        appendLine("returnMode=${userSettings.returnMode.value.id} countPlaytimeOnSteam=${userSettings.countPlaytimeOnSteam.value} sendAchievementsToSteam=${userSettings.sendAchievementsToSteam.value}")
        appendLine("steam connection: ${auth.connection.value}")
    }

    private fun deviceFacts(): String = buildString {
        appendLine("manufacturer=${Build.MANUFACTURER} brand=${Build.BRAND} model=${Build.MODEL} device=${Build.DEVICE} product=${Build.PRODUCT}")
        appendLine("hardware=${Build.HARDWARE} board=${Build.BOARD} abis=${Build.SUPPORTED_ABIS.joinToString()}")
        appendLine("android=${Build.VERSION.RELEASE} sdk=${Build.VERSION.SDK_INT} securityPatch=${Build.VERSION.SECURITY_PATCH} incremental=${Build.VERSION.INCREMENTAL}")
        appendLine("display=${Build.DISPLAY}")
        appendLine("fingerprint=${Build.FINGERPRINT}")
        appendLine("type=${Build.TYPE} tags=${Build.TAGS}")
        appendLine("vrPlatform=${device.vrPlatform?.id} headset=${device.isHeadset}")
        appendLine("locale=${Locale.getDefault()} timezoneOffsetMinutes=${java.util.TimeZone.getDefault().rawOffset / 60000}")
        val external = Environment.getExternalStorageDirectory()
        appendLine("storage free=${external.usableSpace / MIB} MiB of ${external.totalSpace / MIB} MiB")
        appendLine("internal free=${context.filesDir.usableSpace / MIB} MiB")
        val memory = android.app.ActivityManager.MemoryInfo()
        (context.getSystemService(Context.ACTIVITY_SERVICE) as? android.app.ActivityManager)?.getMemoryInfo(memory)
        appendLine("memory total=${memory.totalMem / MIB} MiB available=${memory.availMem / MIB} MiB lowMemory=${memory.lowMemory}")
        (context.getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager)?.let { appendLine("thermalStatus=${it.currentThermalStatus}") }
        appendLine("features of interest:")
        val interesting = listOf("vr", "xr", "oculus", "pico", "meta", "tracking", "passthrough", "controller", "openxr", "vulkan", "opengl")
        context.packageManager.systemAvailableFeatures.mapNotNull { it.name }.filter { name -> interesting.any { name.contains(it, ignoreCase = true) } }.sorted()
            .forEach { appendLine("  $it") }
    }

    private fun apkContents(packageName: String): String = buildString {
        packages.apkFilesOf(packageName).forEach { apk ->
            appendLine("== ${apk.name} (${apk.length()} bytes)")
            runCatching {
                java.util.zip.ZipFile(apk).use { zip ->
                    zip.entries().asSequence().take(MAX_APK_ENTRIES).forEach { appendLine("  ${it.name}  ${it.size} (${it.compressedSize})") }
                }
            }.onFailure { appendLine("  (not readable: ${it.javaClass.simpleName})") }
        }
    }

    /**
     * The Steam interface versions the game's own libraries ask for, and which of them GamePort's Steam shim
     * (libsteamclient.so) does not offer: a version the shim lacks makes the game call the wrong functions.
     */
    private fun steamInterfaces(packageName: String): String = buildString {
        val found = linkedMapOf<String, List<String>>()
        packages.apkFilesOf(packageName).forEach { apk ->
            runCatching {
                java.util.zip.ZipFile(apk).use { zip ->
                    zip.entries().asSequence()
                        .filter { it.name.startsWith("lib/arm64-v8a/") && it.name.endsWith(".so") && it.size in 1..MAX_SCANNED_LIBRARY }
                        .filter { it.name.contains("steam", ignoreCase = true) }
                        .forEach { entry -> found[entry.name] = zip.getInputStream(entry).use { SteamInterfaces.find(it) } }
                }
            }
        }
        found.forEach { (name, versions) ->
            appendLine("$name:")
            if (versions.isEmpty()) appendLine("  (none found)") else versions.forEach { appendLine("  $it") }
        }
        val asked = found["lib/arm64-v8a/libsteam_api.so"]
        val offered = found["lib/arm64-v8a/libsteamclient.so"]
        if (asked != null && offered != null) {
            appendLine()
            appendLine("Asked by libsteam_api.so and not offered by libsteamclient.so (the shim):")
            val missing = asked.filter { it !in offered }
            if (missing.isEmpty()) appendLine("  (none)") else missing.forEach { appendLine("  $it") }
        }
        if (found.isEmpty()) appendLine("(no Steam library found in the APK)")
    }

    /** Which GamePort pieces the game carries (names and sizes only: nothing from the account file). */
    private fun patchFacts(packageName: String): String = buildString {
        val wanted = listOf(
            "lib/arm64-v8a/libsteamclient.so", "lib/arm64-v8a/libXrApiLayer_gameport.so",
            "assets/openxr/1/api_layers/implicit.d/XrApiLayer_gameport.json", "assets/gameport/steam.cfg",
        )
        packages.apkFilesOf(packageName).forEach { apk ->
            runCatching {
                java.util.zip.ZipFile(apk).use { zip ->
                    wanted.forEach { name -> appendLine("$name: ${zip.getEntry(name)?.size?.let { "$it bytes" } ?: "absent"}") }
                    val hook = zip.entries().asSequence().firstOrNull { it.name.matches(Regex("classes\\d*\\.dex")) && it.size < 3_000_000 &&
                        zip.getInputStream(it).use { s -> String(s.readBytes(), Charsets.ISO_8859_1).contains("Lapp/gameport/hook/GamePortHookProvider;") } }
                    appendLine("hook dex: ${hook?.name ?: "absent"}")
                }
            }
        }
        val meta = runCatching { context.packageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA).metaData }.getOrNull()
        meta?.keySet()?.filter { it.startsWith("app.gameport") }?.sorted()?.forEach { appendLine("$it = ${meta.get(it)}") }
    }

    private fun components(packageName: String): String = buildString {
        val flags = PackageManager.GET_ACTIVITIES or PackageManager.GET_SERVICES or PackageManager.GET_PROVIDERS or PackageManager.GET_RECEIVERS
        val info = runCatching { context.packageManager.getPackageInfo(packageName, flags) }.getOrNull() ?: return "(not readable)"
        appendLine("activities:"); info.activities?.forEach { appendLine("  ${it.name} exported=${it.exported} launchMode=${it.launchMode} orientation=${it.screenOrientation}") }
        appendLine("services:"); info.services?.forEach { appendLine("  ${it.name} exported=${it.exported}") }
        appendLine("providers:"); info.providers?.forEach { appendLine("  ${it.name} authority=${it.authority}") }
        appendLine("receivers:"); info.receivers?.forEach { appendLine("  ${it.name} exported=${it.exported}") }
    }

    private fun launchFacts(packageName: String): String = buildString {
        appendLine("How GamePort starts the game: ${packages.launchIntent(packageName, immersive = true)}")
        appendLine("How the game's first screen was started the last time it ran:")
        append(store.text(packageName, "launch.txt").ifBlank { "  (not recorded: the game has not run with the current patch)\n" })
    }

    private fun gameportVersion(): String = runCatching {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        "${info.versionName} (${info.longVersionCode})"
    }.getOrDefault("unknown")

    private companion object {
        const val MIB = 1024L * 1024L
        const val MAX_APK_ENTRIES = 6000
        const val MAX_SCANNED_LIBRARY = 200L * 1024 * 1024
        val README = """
            GamePort problem report
            -----------------------
            Made on the player's device to help find why one game does not work. Nothing was sent anywhere:
            the player chose to share this file.

            diagnosis.txt         what the files show at once (the hook ran, the OpenXR loader and layer loaded, how the last runs ended) and how old the data is
            report.json           versions of GamePort, the game and the patch; the connection to Steam; why a report is suggested
            device.txt            the device, its system, storage, memory, VR features
            settings.txt          the player's choices for this game (seated mode, height, controller mapping), the save sync state
            performance.txt       the runtime's frame rate, CPU and GPU lines from the last minutes of play
            files.txt             the game's APK and expansion files (names and sizes)
            apk-contents.txt      every file inside the game's APK (names and sizes)
            steam-interfaces.txt  the Steam interface versions the game's libraries ask for
            patch.txt             which GamePort pieces the game carries (shim, OpenXR layer, hook)
            manifest.txt          the permissions and metadata the game declares
            components.txt        the game's screens, services, providers, receivers
            launch.txt            how the game is started
            loaded-libraries.txt  the files the game process really loaded (which OpenXR runtime, the shim...)
            game-log-start.txt    the first lines of the game's system log (its start)
            game-log.txt          the latest lines of the game's system log
            previous-run-log-*.txt the same for the run before the last one, when there was one
            engine-logs.txt       log files the game's engine wrote in the game's folders, when there are some
            last-exits.txt        how the last runs ended, as Android recorded it
            crash-tombstone.pb    Android's crash report of the last native crash, when there was one (binary; it holds
                                  the call stack and technical memory data of the game process)
            install.txt           for a failed install: the error, the download folder and what its APKs say about themselves
            events.txt            what GamePort did for this game: download, patch, install and their errors
            gameport-log.txt      GamePort's own warnings, errors and messages about this game

            Account numbers, e-mail addresses, network addresses and the account's display name are removed from every
            text file. The crash report is a binary file and is not filtered.
        """.trimIndent() + "\n"
    }
}
