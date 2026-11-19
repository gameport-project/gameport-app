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
}

object PicoPlatform : VrPlatform {
    override val id = "pico"

    override fun matches(device: DeviceInfo) =
        device.manufacturer.equals("Pico", ignoreCase = true) || device.brand.equals("Pico", ignoreCase = true)

    override val immersiveLauncherCategories = listOf("com.yvr.intent.category.VR")

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
