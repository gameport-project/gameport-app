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

/**
 * Whether a game keeps the foveation that follows the eyes (the extension `XR_META_foveation_eye_tracked`): automatic keeps it where the device has eye tracking and
 * leaves it out where it has none, on keeps it always, off leaves it out always. Some runtimes crash when a game uses it on a device without eye tracking.
 */
enum class EyeFoveationMode { AUTO, ON, OFF }

/** The strengths offered for the controllers' vibration in a game, as a percentage of what the game asks for. The first is the game's own. */
object HapticsStrength {
    const val NORMAL = 100
    val CHOICES = listOf(NORMAL, 50, 150, 0)

    /** A stored value that is not one of the choices means no choice was made. */
    fun normalize(value: Int): Int = if (value in CHOICES) value else NORMAL
}

/** Whether a game declares that it uses the headset's passthrough: by itself for the games known to need it, always, or never. */
enum class PassthroughMode { AUTO, ON, OFF }

/**
 * Whether a game is installed under a package name of GamePort's own instead of the one its APK declares: automatic installs it that way on the headsets whose
 * store refuses a re-signed game ([VrPlatform.refusesReSignedGames]), always does it everywhere, never keeps the game's own name.
 */
enum class PackageNameMode { AUTO, ON, OFF }

/**
 * The name a game takes when it is not installed under the one its APK declares.
 *
 * A headset can keep its own store's package names associated with the signature it signed them with, and refuse a game that carries another one: a Pico asks
 * its servers about every third-party application it sees, with the package name and the signature of the installed APK, and force-stops the ones whose
 * signature is not the one registered for that name. A game sold on the Pico Store and re-signed by GamePort therefore never starts, whatever it does itself.
 * Under a name nobody knows there is nothing to compare, and the check has nothing to say.
 *
 * The name is built from the app id alone, so it is the same at every install and every patch: a game patched again keeps its folder, its expansion files and
 * its saves.
 */
object RenamedPackages {
    /** The name a game is installed under: unique, stable, and never one a store could know. */
    fun nameFor(appId: Int): String = "$PREFIX$appId"

    /** The name [appId] must be installed under, or null to keep the one its APK declares. */
    fun chosen(mode: PackageNameMode, appId: Int, refusesReSigned: Boolean): String? = when (mode) {
        PackageNameMode.ON -> nameFor(appId)
        PackageNameMode.OFF -> null
        PackageNameMode.AUTO -> if (refusesReSigned) nameFor(appId) else null
    }

    // A part of a package name cannot begin with a digit, so the app id follows a letter.
    private const val PREFIX = "com.gameport.g"
}

/**
 * The games whose VR build leaves out the declaration that lets the headset show the real world behind them, though they have a setting for it (Cubism, since an
 * update of the game). Steam does not say which games use the passthrough, and nothing the game does at run time tells it apart, so this is a list kept by hand: the
 * developer adds a game to it in the relay's console as players report one whose passthrough does not work, and the app reads it ([GameDefaultsList.passthrough], passed
 * here as [told]). The games below are the ones the app has always known, kept for when the relay cannot be read.
 */
object PassthroughGames {
    private val APP_IDS = setOf(804530)

    /**
     * The headsets whose graphics stack does not take the declaration for one game, by game: on a Pico, Cubism (804530) dies in the Adreno Vulkan driver before
     * its first frame once the extension that carries the real world is enabled in its instance, while it uses the passthrough normally on a Quest. The
     * declaration is what lets the game's own passthrough setting work, so it is kept everywhere else.
     */
    private val NOT_ON: Map<Int, Set<String>> = mapOf(804530 to setOf(app.gameport.core.model.vr.PicoPlatform.id))

    /**
     * [told]: the games the console says declare the passthrough, on top of the ones built in. [deviceFamily]: the headset the game is installed on
     * (`pico`, `meta`, `openxr`), null when it is not known.
     */
    fun wanted(appId: Int, deviceFamily: String? = null, told: Set<Int> = emptySet()): Boolean =
        (appId in APP_IDS || appId in told) && deviceFamily !in NOT_ON[appId].orEmpty()

    /** Whether the declaration is given to [appId]: the player's choice, or the list when they left it automatic. */
    fun declared(mode: PassthroughMode, appId: Int, deviceFamily: String? = null, told: Set<Int> = emptySet()): Boolean = when (mode) {
        PassthroughMode.ON -> true
        PassthroughMode.OFF -> false
        PassthroughMode.AUTO -> wanted(appId, deviceFamily, told)
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
    /** The foveation that follows the eyes: kept where the device can do it (automatic), always, or never. */
    val eyeFoveation: EyeFoveationMode = EyeFoveationMode.AUTO,
    /** How strongly the controllers vibrate in this game, as a percentage of what the game asks for ([HapticsStrength]); 100 leaves it as it is. */
    val hapticsPercent: Int = HapticsStrength.NORMAL,
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
    /**
     * The game is installed under a package name of GamePort's own ([RenamedPackages]) instead of the one its APK declares, which is what gets a game past a
     * headset that refuses a re-signed one. A change needs the game to be installed again, under the other name.
     */
    val packageName: PackageNameMode = PackageNameMode.AUTO,
    /** The channel (branch) of Steam this game is installed and updated from; null is the normal one ([UpdateChannels.NORMAL]). Its Android build is what an install or an update fetches. */
    val channel: String? = null,
) {
    /** The channel to fetch from: the one picked, or the normal one. */
    val channelName: String get() = channel ?: UpdateChannels.NORMAL

    val followsDefaults: Boolean get() = heightCm == null

    fun effectiveHeightCm(defaults: PlayerDefaults): Int = heightCm ?: defaults.heightCm

    /** Standing eye height: the eyes sit about 12 cm below the top of the head. */
    fun eyeHeightCm(defaults: PlayerDefaults): Int = effectiveHeightCm(defaults) - EYE_BELOW_TOP_OF_HEAD_CM

    private companion object {
        const val EYE_BELOW_TOP_OF_HEAD_CM = 12
    }
}
