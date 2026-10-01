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
}

object PicoPlatform : VrPlatform {
    override val id = "pico"

    override fun matches(device: DeviceInfo) =
        device.manufacturer.equals("Pico", ignoreCase = true) || device.brand.equals("Pico", ignoreCase = true)

    override val immersiveLauncherCategories = listOf("com.yvr.intent.category.VR")
}

object VrPlatforms {
    /** Vendors first, so a Quest is reported as a Quest and not as a generic OpenXR device. */
    val all: List<VrPlatform> = listOf(MetaQuestPlatform, PicoPlatform, OpenXrPlatform)

    /** The platform this device belongs to, or null when it is not a headset. */
    fun detect(device: DeviceInfo): VrPlatform? = all.firstOrNull { it.matches(device) }

    /**
     * Categories a patched game gets so it starts as a VR app on any supported headset, not only
     * the one it was patched on.
     */
    val allImmersiveLauncherCategories: List<String> =
        all.flatMap { it.immersiveLauncherCategories }.distinct()
}
