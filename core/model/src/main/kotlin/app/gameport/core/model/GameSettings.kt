package app.gameport.core.model

/** Settings shared by every game unless a game has its own. */
data class PlayerDefaults(
    val heightCm: Int = DEFAULT_HEIGHT_CM,
) {
    companion object {
        const val DEFAULT_HEIGHT_CM = 170
        val HEIGHT_RANGE_CM = 120..220
    }
}

/** Whether a game gets the play space that the headset's recentering moves: by itself where the headset has no play area, always, or never. */
enum class RecenterMode { AUTO, ON, OFF }

/** Whether a game declares that it uses the headset's passthrough: by itself for the games known to need it, always, or never. */
enum class PassthroughMode { AUTO, ON, OFF }

/**
 * The games whose VR build leaves out the declaration that lets the headset show the real world behind them, though they have a setting for it (Cubism, since an
 * update of the game). Steam does not say which games use the passthrough, and nothing the game does at run time tells it apart, so this is a list kept by hand: the
 * developer adds a game to it in the relay's console as players report one whose passthrough does not work, and the app reads it ([GameDefaultsList.passthrough], passed
 * here as [told]). The games below are the ones the app has always known, kept for when the relay cannot be read.
 */
object PassthroughGames {
    private val APP_IDS = setOf(804530)

    /** [told]: the games the console says declare the passthrough, on top of the ones built in. */
    fun wanted(appId: Int, told: Set<Int> = emptySet()): Boolean = appId in APP_IDS || appId in told

    /** Whether the declaration is given to [appId]: the player's choice, or the list when they left it automatic. */
    fun declared(mode: PassthroughMode, appId: Int, told: Set<Int> = emptySet()): Boolean = when (mode) {
        PassthroughMode.ON -> true
        PassthroughMode.OFF -> false
        PassthroughMode.AUTO -> wanted(appId, told)
    }
}

/** What the player chose for one game. */
data class GameSettings(
    /** Play seated while the game believes the player stands. */
    val seated: Boolean = false,
    /** This game's own height; null means the game follows [PlayerDefaults]. */
    val heightCm: Int? = null,
    /** The game follows the headset's recentering: it gets the play space that recentering moves, in place of a fixed one. */
    val recenter: RecenterMode = RecenterMode.AUTO,
    /** The arguments the player picked to start the game with; null means none was picked and the ones meant for VR are used (see [LaunchOptions.chosen]). */
    val launchArguments: Set<String>? = null,
    /**
     * Some games start with a first screen that then opens the game itself, in a flat window. On (automatic), the patch makes the game itself open in VR too;
     * off keeps it from doing so, for a game that does not get along with it. A change needs the game to be patched again.
     */
    val openInVr: Boolean = true,
    /**
     * A game that carries Meta's mixed reality scripts without the native library they call stutters, as it raises an exception at every update: on (automatic), the patch
     * gives it a stand-in that answers "not initialized"; off keeps it from doing so. A change needs the game to be patched again.
     */
    val ovrPluginStandIn: Boolean = true,
    /** The game declares that it uses the headset's passthrough, so the headset lets it show the real world behind it. A change needs the game to be patched again. */
    val passthrough: PassthroughMode = PassthroughMode.AUTO,
) {
    val followsDefaults: Boolean get() = heightCm == null

    fun effectiveHeightCm(defaults: PlayerDefaults): Int = heightCm ?: defaults.heightCm

    /** Standing eye height: the eyes sit about 12 cm below the top of the head. */
    fun eyeHeightCm(defaults: PlayerDefaults): Int = effectiveHeightCm(defaults) - EYE_BELOW_TOP_OF_HEAD_CM

    private companion object {
        const val EYE_BELOW_TOP_OF_HEAD_CM = 12
    }
}
