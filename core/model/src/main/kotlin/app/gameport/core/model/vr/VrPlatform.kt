package app.gameport.core.model.vr

/** What the Android layer can tell about the device; kept plain so the rules below stay testable. */
data class DeviceInfo(
    val manufacturer: String,
    val brand: String,
    val model: String,
    val systemFeatures: Set<String>,
)

/**
 * A VR headset family. Code that depends on the headset (detecting one, making a game start in an
 * immersive session, ...) goes through this interface, so no other code names a vendor. The
 * baseline is [OpenXrPlatform], which every OpenXR headset satisfies; vendors only add what
 * their system needs on top.
 */
interface VrPlatform {
    val id: String

    fun matches(device: DeviceInfo): Boolean

    /** Intent categories that make this system start an activity as an immersive VR app. */
    val immersiveLauncherCategories: List<String>

    /**
     * True when this system keeps the package names of its own store's games associated with the signature it signed them with, and refuses to start a game
     * that carries another one — before the game runs, and without a line in any log. A game installed under a name the store has never seen
     * ([app.gameport.core.model.RenamedPackages]) has nothing to be compared with, and starts.
     */
    val refusesReSignedGames: Boolean get() = false

    /**
     * True when a game that does not start on this system may be one the system refused because of its package name, so that installing it again under a name of GamePort's
     * own is worth offering. A system that refuses re-signed games ([refusesReSignedGames]) does it before the game runs, and so does Meta's safety check on a game
     * that is not from its store; the second is not made to rename every game by itself, only to offer it when a game did not start.
     */
    val renameMayHelp: Boolean get() = refusesReSignedGames

    /** The system setting that keeps the image size this system recommends to a game, as "1920*1920"; null when it keeps none. */
    val recommendedEyeSizeSetting: String? get() = null

    /** The image size for one eye (width, height) a game gets by default on this [model], when it is known without running a game; null when it is not. */
    fun defaultEyeSize(model: String): Pair<Int, Int>? = null

    /** The frame rates a player may ask for on this system, besides the game's own. */
    val refreshRates: List<Int> get() = listOf(72, 90)

    /** The apps that set the graphics of the games by themselves on this system: when one is installed, it takes over from GamePort's own choices. */
    val optimizerPackages: List<String> get() = emptyList()

    /** The system properties those apps (or a tool such as adb) set to change the graphics of a game: one that holds a value says something is setting them. */
    val optimizerProperties: List<String> get() = emptyList()
}

/** Vendor-neutral baseline: any device that reports head tracking or the OpenXR system API. */
object OpenXrPlatform : VrPlatform {
    override val id = "openxr"

    override fun matches(device: DeviceInfo) = device.systemFeatures.any { it in OPENXR_FEATURES }

    override val immersiveLauncherCategories = listOf("org.khronos.openxr.intent.category.IMMERSIVE_HMD")

    private val OPENXR_FEATURES = setOf("android.hardware.vr.headtracking", "android.software.xr.api.openxr")
}

object MetaQuestPlatform : VrPlatform {
    override val id = "meta"

    override fun matches(device: DeviceInfo) =
        device.manufacturer.equals("Oculus", ignoreCase = true) || device.manufacturer.equals("Meta", ignoreCase = true)

    override val immersiveLauncherCategories = listOf("com.oculus.intent.category.VR")

    // Horizon OS stops the launch of a game that is not from its store with a window saying the application is not approved (PhaAppLaunchInterceptor), and the game then
    // never begins. Under a name of GamePort's own it starts. Nothing renames a game by itself here: the card on its page offers it.
    override val renameMayHelp = true

    // The image size the runtime gives a game on a Quest 3 and a Quest 3S (seen in players' reports); the others are not known without running a game.
    override fun defaultEyeSize(model: String) = if (model.equals("Quest 3", ignoreCase = true) || model.equals("Quest 3S", ignoreCase = true)) 1680 to 1760 else null

    override val refreshRates = listOf(72, 80, 90, 120)

    // Quest Games Optimizer sets the size, the frame rate and the levels through the system properties of the runtime.
    override val optimizerPackages = listOf("com.anagan.qgo")

    override val optimizerProperties = listOf(
        "debug.oculus.textureWidth", "debug.oculus.textureHeight", "debug.oculus.cpuLevel", "debug.oculus.gpuLevel", "debug.oculus.refreshRate", "debug.oculus.dynResScaler",
    )
}

object PicoPlatform : VrPlatform {
    override val id = "pico"

    override fun matches(device: DeviceInfo) =
        device.manufacturer.equals("Pico", ignoreCase = true) || device.brand.equals("Pico", ignoreCase = true)

    override val immersiveLauncherCategories = listOf("com.yvr.intent.category.VR")

    override val recommendedEyeSizeSetting = "sys_set_vrshell_eyebuffer"

    /**
     * Pico's `com.bytedance.pico.matrix` asks Pico's servers about every third-party application on the headset, sending the package name and the signature of
     * the installed APK, and force-stops the ones whose signature is not the one registered for that name (the dialog says the entitlement check failed, and
     * offers the store). A game sold on the Pico Store and re-signed by GamePort is one of them.
     */
    override val refusesReSignedGames = true
}

object VrPlatforms {
    /** Vendors first, so a Quest is reported as a Quest and not as a generic OpenXR device. */
    val all: List<VrPlatform> = listOf(MetaQuestPlatform, PicoPlatform, OpenXrPlatform)

    /** The platform this device belongs to, or null when it is not a headset. */
    fun detect(device: DeviceInfo): VrPlatform? = all.firstOrNull { it.matches(device) }

    /** Whether a game that did not start is worth offering to install again under a name of GamePort's own on the headset family [id]. */
    fun renameMayHelp(id: String?): Boolean = all.firstOrNull { it.id == id }?.renameMayHelp == true

    /** The platform whose id a device profile reports, or null. */
    fun byId(id: String?): VrPlatform? = all.firstOrNull { it.id == id }

    /**
     * Whether the headset family [id] (as a device profile reports it) refuses a game it knows the package name of but not the signature of
     * ([VrPlatform.refusesReSignedGames]).
     */
    fun refusesReSignedGames(id: String?): Boolean = all.firstOrNull { it.id == id }?.refusesReSignedGames == true

    /**
     * Categories a patched game gets so it starts as a VR app on any supported headset, not only
     * the one it was patched on.
     */
    val allImmersiveLauncherCategories: List<String> =
        all.flatMap { it.immersiveLauncherCategories }.distinct()
}
