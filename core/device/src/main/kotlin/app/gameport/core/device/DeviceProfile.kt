package app.gameport.core.device

import android.content.Context
import android.os.Build
import app.gameport.core.model.VoteDevice
import app.gameport.core.model.vr.DeviceInfo
import app.gameport.core.model.vr.VrPlatform
import app.gameport.core.model.vr.VrPlatforms
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** The device GamePort runs on, seen through [VrPlatforms]; the one place that reads `Build`. */
@Singleton
class DeviceProfile @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val info: DeviceInfo by lazy {
        DeviceInfo(
            manufacturer = Build.MANUFACTURER,
            brand = Build.BRAND,
            model = Build.MODEL,
            systemFeatures = context.packageManager.systemAvailableFeatures.mapNotNull { it.name }.toSet(),
        )
    }

    /** The headset family this device belongs to, or null on a phone or tablet. */
    val vrPlatform: VrPlatform? by lazy { VrPlatforms.detect(info) }

    val isHeadset: Boolean get() = vrPlatform != null

    /** The kind of device in the few words the project's totals use ("quest", "pico", "phone", "tablet", "other"): nothing finer than that leaves the device. */
    val kind: String by lazy { VoteDevice.of(vrPlatform?.id, context.resources.configuration.smallestScreenWidthDp >= TABLET_DP) }

    private companion object {
        const val TABLET_DP = 600
    }
}
