package app.gameport.core.settings

import android.content.Context
import app.gameport.core.model.EyeFoveationMode
import app.gameport.core.model.GameSettings
import app.gameport.core.model.PlayerDefaults
import app.gameport.core.model.PassthroughMode
import app.gameport.core.model.PackageNameMode
import app.gameport.core.model.RecenterMode
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map

/**
 * Settings kept on the device: what applies to every game ([defaults]) and what a game overrides.
 * A game only holds its own height once the player set one; changing the defaults never touches it.
 */
@Singleton
class GameSettingsStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val appContext = context
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val changes = MutableStateFlow(0)
    private val _defaults = MutableStateFlow(readDefaults())

    val defaults: StateFlow<PlayerDefaults> = _defaults.asStateFlow()

    fun setDefaults(value: PlayerDefaults) {
        val height = value.heightCm.coerceIn(PlayerDefaults.HEIGHT_RANGE_CM)
        prefs.edit().putInt(KEY_DEFAULT_HEIGHT, height).apply()
        _defaults.value = PlayerDefaults(height)
        changes.value += 1
    }

    fun get(appId: Int): GameSettings = GameSettings(
        seated = prefs.getBoolean("seated_$appId", false),
        heightCm = if (prefs.contains("height_$appId")) prefs.getInt("height_$appId", 0) else null,
        recenter = recenterOf(appId),
        eyeFoveation = eyeFoveationOf(appId),
        hapticsPercent = app.gameport.core.model.HapticsStrength.normalize(prefs.getInt("haptics_$appId", app.gameport.core.model.HapticsStrength.NORMAL)),
        resolutionPercent = app.gameport.core.model.PerformanceTuning.resolution(prefs.getInt("perf_res_screen_$appId", app.gameport.core.model.PerformanceTuning.DEFAULT_RESOLUTION)),
        refreshRate = app.gameport.core.model.PerformanceTuning.refreshRate(prefs.getInt("perf_refresh_$appId", 0)),
        cpuLevel = app.gameport.core.model.PerformanceTuning.level(prefs.getInt("perf_cpu_$appId", -1)),
        gpuLevel = app.gameport.core.model.PerformanceTuning.level(prefs.getInt("perf_gpu_$appId", -1)),
        performanceChangedAt = prefs.getLong("perf_changed_$appId", 0),
        openInVr = prefs.getBoolean("open_in_vr_$appId", true),
        ovrPluginStandIn = prefs.getBoolean("ovrplugin_stand_in_$appId", true),
        passthrough = passthroughOf(appId),
        packageName = packageNameOf(appId),
        launchArguments = if (prefs.contains("launchargs_$appId")) prefs.getStringSet("launchargs_$appId", emptySet()).orEmpty().toSet() else null,
        channel = prefs.getString("channel_$appId", null)?.takeIf { it.isNotBlank() },
    )

    fun observe(appId: Int): Flow<GameSettings> = changes.map { get(appId) }

    /**
     * What the device says about the image it shows, read from the system itself (no game has to run): the size of the screen for one eye, and the size the system
     * recommends to a game, which the platform keeps in a system setting ("1920*1920") or knows for the model. Null for what the device does not say.
     */
    fun eyeSides(platform: app.gameport.core.model.vr.VrPlatform?): EyeSides {
        val mode = (appContext.getSystemService(Context.DISPLAY_SERVICE) as? android.hardware.display.DisplayManager)?.getDisplay(android.view.Display.DEFAULT_DISPLAY)?.mode
        // The screen holds both eyes side by side: one eye is half of its width when that is about twice its height.
        val screen = mode?.let {
            if (it.physicalWidth >= it.physicalHeight * 1.8) (it.physicalWidth / 2) to it.physicalHeight else it.physicalWidth to it.physicalHeight
        }?.takeIf { it.first > 0 && it.second > 0 }
        val fromSystem = platform?.recommendedEyeSizeSetting?.let { name ->
            runCatching { android.provider.Settings.Global.getString(appContext.contentResolver, name) }.getOrNull()
                ?.split('*')?.mapNotNull { it.trim().toIntOrNull() }?.takeIf { it.size == 2 && it.all { side -> side > 0 } }?.let { it[0] to it[1] }
        }
        val recommended = fromSystem ?: platform?.defaultEyeSize(android.os.Build.MODEL)
        android.util.Log.i("GPPerf", "screen for one eye $screen, recommended $recommended (${if (fromSystem != null) "from the system" else "from the model"})")
        return EyeSides(screen?.first, screen?.second, recommended?.first, recommended?.second)
    }

    /**
     * What the screen exposes about its frame rates, read from the system (no game has to run): every whole rate its modes give, and for each the largest picture it comes
     * with, per eye. A rate that only comes with a smaller picture than the screen's own is the one the player is told about.
     */
    fun displayRates(): DisplayRates {
        val modes = runCatching {
            (appContext.getSystemService(Context.DISPLAY_SERVICE) as? android.hardware.display.DisplayManager)
                ?.getDisplay(android.view.Display.DEFAULT_DISPLAY)?.supportedModes?.toList().orEmpty()
        }.getOrDefault(emptyList())
        // One eye is half of the width when the screen holds both side by side.
        fun eye(mode: android.view.Display.Mode) =
            if (mode.physicalWidth >= mode.physicalHeight * 1.8) (mode.physicalWidth / 2) to mode.physicalHeight else mode.physicalWidth to mode.physicalHeight
        val at = HashMap<Int, Pair<Int, Int>>()
        for (mode in modes) {
            val rate = Math.round(mode.refreshRate)
            val size = eye(mode)
            val known = at[rate]
            if (known == null || size.first * size.second > known.first * known.second) at[rate] = size
        }
        val best = at.values.maxByOrNull { it.first * it.second }
        return DisplayRates(at.keys.sorted(), at, best)
    }

    /**
     * Whether something else sets the graphics of this game: an app that does it by itself, is running (its accessibility service, which tells it a game started, is on)
     * and knows the game by the package name it has, or system properties that hold a value (set by that app, by adb, by another tool, for every game).
     */
    fun optimizerState(platform: app.gameport.core.model.vr.VrPlatform?, gamePackage: String?): OptimizerState {
        val services = runCatching { android.provider.Settings.Secure.getString(appContext.contentResolver, "enabled_accessibility_services") }.getOrNull().orEmpty()
        // Such an app knows a game by its package name: a game installed under a name of GamePort's own is one it knows nothing of, and does not touch.
        val known = gamePackage == null || !app.gameport.core.model.RenamedPackages.isOurs(gamePackage)
        if (known && platform?.optimizerPackages.orEmpty().any { services.contains("$it/") }) return OptimizerState.APP
        if (platform?.optimizerProperties.orEmpty().any { readSystemProperty(it).isNotEmpty() }) return OptimizerState.PROPERTIES
        return OptimizerState.NONE
    }

    private fun readSystemProperty(name: String): String = runCatching {
        val process = ProcessBuilder("getprop", name).redirectErrorStream(true).start()
        process.inputStream.bufferedReader().use { it.readText().trim() }
    }.getOrDefault("")

    /** The layer found, the last time the game ran, that the headset has no play area and gave the game a space that follows the recentering. */
    fun stageFallback(appId: Int): Flow<Boolean> = changes.map { prefs.getBoolean("stagefallback_$appId", false) }

    /** The account of the family whose copy of the game is used, picked by the player; null while none was picked (the signed-in account's own copy is used when there is one). */
    fun ownerChoice(appId: Int): Long? = if (prefs.contains("owner_$appId")) prefs.getLong("owner_$appId", 0L) else null

    fun observeOwnerChoice(appId: Int): Flow<Long?> = changes.map { ownerChoice(appId) }

    /** Whether the game was last patched as used through the family; null when it never was. What decides it is the copy picked (see [ownerChoice]). */
    fun familyPatched(appId: Int): Boolean? = if (prefs.contains("family_patched_$appId")) prefs.getBoolean("family_patched_$appId", false) else null

    fun setFamilyPatched(appId: Int, shared: Boolean) {
        prefs.edit().putBoolean("family_patched_$appId", shared).apply()
    }

    fun setOwnerChoice(appId: Int, accountId: Long) {
        prefs.edit().putLong("owner_$appId", accountId).apply()
        changes.value += 1
    }

    fun setStageFallback(appId: Int, fallback: Boolean) {
        if (prefs.getBoolean("stagefallback_$appId", false) == fallback) return
        // A new situation (the layer adapted the game again, or no longer does) is news again: what the player had seen is forgotten.
        prefs.edit().putBoolean("stagefallback_$appId", fallback).remove("stagefallbackseen_$appId").apply()
        changes.value += 1
    }

    /** True while the game is adapted by the layer and the player has not looked at it yet: the page of the game marks it until it is opened. */
    fun stageNotice(appId: Int): Flow<Boolean> = changes.map { prefs.getBoolean("stagefallback_$appId", false) && !prefs.getBoolean("stagefallbackseen_$appId", false) }

    /** The player opened what the mark was about: it goes, until the layer adapts the game again. */
    fun setStageNoticeSeen(appId: Int) {
        if (prefs.getBoolean("stagefallbackseen_$appId", false)) return
        prefs.edit().putBoolean("stagefallbackseen_$appId", true).apply()
        changes.value += 1
    }

    /** The eye height to give a game: its own height if it has one, else the defaults'. */
    fun eyeHeightCm(appId: Int): Int = get(appId).eyeHeightCm(defaults.value)

    fun set(appId: Int, settings: GameSettings) {
        val before = get(appId)
        val editor = prefs.edit().putBoolean("seated_$appId", settings.seated)
        if (before.resolutionPercent != settings.resolutionPercent || before.refreshRate != settings.refreshRate || before.cpuLevel != settings.cpuLevel || before.gpuLevel != settings.gpuLevel) {
            editor.putLong("perf_changed_$appId", System.currentTimeMillis())
        }
        val height = settings.heightCm
        // Automatic is the absence of a choice.
        when (settings.recenter) {
            RecenterMode.AUTO -> editor.remove("recenter_$appId")
            RecenterMode.ON -> editor.putString("recenter_$appId", "on")
            RecenterMode.OFF -> editor.putString("recenter_$appId", "off")
        }
        // Automatic is the absence of a choice.
        when (settings.eyeFoveation) {
            EyeFoveationMode.AUTO -> editor.remove("eye_foveation_$appId")
            EyeFoveationMode.ON -> editor.putString("eye_foveation_$appId", "on")
            EyeFoveationMode.OFF -> editor.putString("eye_foveation_$appId", "off")
        }
        // The game's own strength is the absence of a choice.
        if (settings.hapticsPercent == app.gameport.core.model.HapticsStrength.NORMAL) editor.remove("haptics_$appId") else editor.putInt("haptics_$appId", settings.hapticsPercent)
        // What leaves the graphics as they are is the absence of a choice.
        if (settings.resolutionPercent == app.gameport.core.model.PerformanceTuning.DEFAULT_RESOLUTION) editor.remove("perf_res_screen_$appId") else editor.putInt("perf_res_screen_$appId", settings.resolutionPercent)
        if (settings.refreshRate == 0) editor.remove("perf_refresh_$appId") else editor.putInt("perf_refresh_$appId", settings.refreshRate)
        if (settings.cpuLevel == -1) editor.remove("perf_cpu_$appId") else editor.putInt("perf_cpu_$appId", settings.cpuLevel)
        if (settings.gpuLevel == -1) editor.remove("perf_gpu_$appId") else editor.putInt("perf_gpu_$appId", settings.gpuLevel)
        // No pick is the absence of a choice, an empty pick is a choice of none.
        settings.launchArguments?.let { editor.putStringSet("launchargs_$appId", it) } ?: editor.remove("launchargs_$appId")
        // Automatic is the absence of a choice: only a no is written.
        if (settings.openInVr) editor.remove("open_in_vr_$appId") else editor.putBoolean("open_in_vr_$appId", false)
        if (settings.ovrPluginStandIn) editor.remove("ovrplugin_stand_in_$appId") else editor.putBoolean("ovrplugin_stand_in_$appId", false)
        // Automatic is the absence of a choice.
        when (settings.passthrough) {
            PassthroughMode.AUTO -> editor.remove("passthrough_$appId")
            PassthroughMode.ON -> editor.putString("passthrough_$appId", "on")
            PassthroughMode.OFF -> editor.putString("passthrough_$appId", "off")
        }
        // Automatic is the absence of a choice.
        when (settings.packageName) {
            PackageNameMode.AUTO -> editor.remove("package_name_$appId")
            PackageNameMode.ON -> editor.putString("package_name_$appId", "on")
            PackageNameMode.OFF -> editor.putString("package_name_$appId", "off")
        }
        // The normal channel is the absence of a choice.
        settings.channel?.let { editor.putString("channel_$appId", it) } ?: editor.remove("channel_$appId")
        if (height == null) editor.remove("height_$appId") else editor.putInt("height_$appId", height.coerceIn(PlayerDefaults.HEIGHT_RANGE_CM))
        editor.apply()
        changes.value += 1
    }

    /** A choice written before the automatic mode was a yes only: an old "true" is still a yes, an old "false" was no choice. */
    private fun recenterOf(appId: Int): RecenterMode = when (prefs.all["recenter_$appId"]) {
        "on", true -> RecenterMode.ON
        "off" -> RecenterMode.OFF
        else -> RecenterMode.AUTO
    }

    private fun eyeFoveationOf(appId: Int): EyeFoveationMode = when (prefs.all["eye_foveation_$appId"]) {
        "on" -> EyeFoveationMode.ON
        "off" -> EyeFoveationMode.OFF
        else -> EyeFoveationMode.AUTO
    }

    /** A choice written when it was only a switch: an old "true" is a yes. */
    private fun passthroughOf(appId: Int): PassthroughMode = when (prefs.all["passthrough_$appId"]) {
        "on", true -> PassthroughMode.ON
        "off" -> PassthroughMode.OFF
        else -> PassthroughMode.AUTO
    }

    /** Automatic is the absence of a choice: the game is installed under GamePort's own name only where the headset refuses a re-signed one. */
    private fun packageNameOf(appId: Int): PackageNameMode = when (prefs.all["package_name_$appId"]) {
        "on" -> PackageNameMode.ON
        "off" -> PackageNameMode.OFF
        else -> PackageNameMode.AUTO
    }

    private fun readDefaults() = PlayerDefaults(prefs.getInt(KEY_DEFAULT_HEIGHT, PlayerDefaults.DEFAULT_HEIGHT_CM))

    private companion object {
        const val PREFS = "gameport_game_settings"
        const val KEY_DEFAULT_HEIGHT = "default_height"
    }
}

/** The size, in pixels, of the screen for one eye and of the image the system recommends to a game; null when the device does not say. */
data class EyeSides(val screenWidth: Int?, val screenHeight: Int?, val recommendedWidth: Int?, val recommendedHeight: Int?) {
    val screen: Pair<Int, Int>? get() = if (screenWidth != null && screenHeight != null) screenWidth to screenHeight else null
    val recommended: Pair<Int, Int>? get() = if (recommendedWidth != null && recommendedHeight != null) recommendedWidth to recommendedHeight else null
}

/** What sets the graphics of the games besides GamePort: nothing, an app that is running, or system properties that hold a value. */
enum class OptimizerState { NONE, APP, PROPERTIES }

/** The whole frame rates a screen exposes, the largest picture (per eye) each comes with, and the largest of all; empty when the device says nothing. */
data class DisplayRates(val rates: List<Int>, val resolutionAt: Map<Int, Pair<Int, Int>>, val best: Pair<Int, Int>?) {
    /** The picture a rate comes with when it is smaller than the best one, else null: what the player is told before choosing it. */
    fun lowerResolutionAt(rate: Int): Pair<Int, Int>? {
        val at = resolutionAt[rate] ?: return null
        val top = best ?: return null
        return at.takeIf { it.first * it.second < top.first * top.second }
    }
}
