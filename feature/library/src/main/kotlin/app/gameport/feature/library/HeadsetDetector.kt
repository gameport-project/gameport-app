package app.gameport.feature.library

import app.gameport.core.device.DeviceProfile
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject

/** Tells whether the app runs on a VR headset, where the VR / Flat tabs make sense. */
fun interface HeadsetDetector {
    fun isHeadset(): Boolean
}

internal class DeviceHeadsetDetector @Inject constructor(
    private val device: DeviceProfile,
) : HeadsetDetector {
    override fun isHeadset(): Boolean = device.isHeadset
}

@Module
@InstallIn(SingletonComponent::class)
internal abstract class HeadsetModule {
    @Binds
    abstract fun bindHeadsetDetector(impl: DeviceHeadsetDetector): HeadsetDetector
}
