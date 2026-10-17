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

        LIBRARIES.forEach { name ->
            source.getLibrary(name)?.let { session.addFile(LIB_DIR + name, it) }
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
    }
}
