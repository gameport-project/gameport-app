package app.gameport.core.device

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Build
import app.gameport.core.model.VoteDevice
import app.gameport.core.model.vr.DeviceInfo
import app.gameport.core.model.vr.VrPlatform
import app.gameport.core.model.vr.VrPlatforms
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** The device GamePort runs on, seen through [VrPlatforms]; the one place that reads `Build`. */
@Singleton
class DeviceProfile @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val info: DeviceInfo by lazy {
        val real = DeviceInfo(
            manufacturer = Build.MANUFACTURER,
            brand = Build.BRAND,
            model = Build.MODEL,
            systemFeatures = context.packageManager.systemAvailableFeatures.mapNotNull { it.name }.toSet(),
        )
        if (takesItselfForAQuest()) real.copy(manufacturer = "Oculus", brand = "oculus", model = "Quest 3") else real
    }

    /**
     * Development only: a debug build takes itself for a Quest when its files folder holds a file `debug-device.txt` that says `quest`, so the emulator shows the
     * app as the headset does (the VR games, the tabs). A release build never reads it. From the Mac: `adb -s emulator-5554 shell run-as app.gameport.dev sh -c "echo quest > files/debug-device.txt"`.
     */
    private fun takesItselfForAQuest(): Boolean {
        if (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE == 0) return false
        return runCatching { File(context.filesDir, "debug-device.txt").readText().trim() == "quest" }.getOrDefault(false)
    }

    /** The headset family this device belongs to, or null on a phone or tablet. */
    val vrPlatform: VrPlatform? by lazy { VrPlatforms.detect(info) }

    val isHeadset: Boolean get() = vrPlatform != null

    /** What the device calls itself (`Build.MODEL`), sent with a vote next to the kind of device so the project can name the exact one. No serial number, no fingerprint. */
    val model: String get() = info.model

    /** The codename of the device (`Build.DEVICE`), same use as [model]. A debug build that takes itself for a Quest takes the codename of a Quest 3 too. */
    val codename: String by lazy { if (takesItselfForAQuest()) "eureka" else Build.DEVICE.orEmpty() }

    /** The kind of device in the few words the project's totals use ("quest", "pico", "phone", "tablet", "other"): nothing finer than that leaves the device. */
    val kind: String by lazy { VoteDevice.of(vrPlatform?.id, context.resources.configuration.smallestScreenWidthDp >= TABLET_DP) }

    private companion object {
        const val TABLET_DP = 600
    }
}
