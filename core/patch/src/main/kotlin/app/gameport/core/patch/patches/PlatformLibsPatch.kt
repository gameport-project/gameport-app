package app.gameport.core.patch.patches

import app.gameport.core.patch.ApkPatch
import app.gameport.core.patch.PatchAssets
import app.gameport.core.patch.PatchContext
import app.gameport.core.patch.PatchSession

/** Source of platform libraries for the PlatformLibsPatch. */
fun interface PlatformLibsSource {
    /** Returns the bytes of a library by name, or null if not available. */
    fun getLibrary(name: String): ByteArray?
}

/**
 * Puts OVRPort's platform loaders in the game next to its own `libovrplatformloader.so`.
 * Only applies if the game already contains `libovrplatformloader.so`, and does nothing when
 * the source has no libraries.
 */
class PlatformLibsPatch(private val source: PlatformLibsSource = PlatformLibsSource { null }) : ApkPatch {
    override val id = "platform_libs"
    override val recommended = false
    override val locked = false

    override fun apply(session: PatchSession, context: PatchContext, assets: PatchAssets) {
        if (session.readFile(LIB_DIR + LOADER) == null) return

        var injected = false
        LIBRARIES.forEach { name ->
            source.getLibrary(name)?.let { 
                session.addFile(LIB_DIR + name, it)
                injected = true
            }
        }
        
        // Add the config file only if at least one library was injected
        if (injected) {
            session.addFile(LIB_DIR + "liboverport.config.so", CONFIG_JSON.toByteArray())
        }
    }

    private companion object {
        const val LIB_DIR = "lib/arm64-v8a/"
        const val LOADER = "libovrplatformloader.so"
        val LIBRARIES = listOf(
            LOADER,
            "libovrplatformloader_meta.so",
            "libovrplatformloader_meta_q1.so",
            "libpxrplatformloader.so",
        )
        const val CONFIG_JSON = "{\"version\":1,\"disable_controller_offset\":0,\"force_passthrough\":0,\"disable_space_warp\":0,\"verbose_logging\":0,\"suggested_texture_width\":0,\"suggested_texture_height\":0,\"suggested_cpu_level\":0,\"suggested_gpu_level\":0,\"vibration_multiplier\":1.0,\"vibration_frequency_multiplier\":1.0,\"database\":[],\"patched\":{\"by\":\"GamePort\",\"with\":\"3.3.0\",\"patches\":[\"platform_libs\"]}}"
    }
}
