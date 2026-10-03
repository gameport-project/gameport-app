package app.gameport.core.patch.patches

import app.gameport.core.patch.ApkPatch
import app.gameport.core.patch.PatchAssets
import app.gameport.core.patch.PatchContext
import app.gameport.core.patch.PatchSession

/**
 * Gives a game a recent OpenXR loader when the one it ships is too old to find the device's runtime. An OpenXR game
 * carries its own loader; an old one (seen in Moss 2) fails to reach the runtime of a Quest, so the game starts without
 * VR and the system's launch screen stays. Games that run here all carry a recent loader, and those are left untouched:
 * only a loader without the marker of the recent ones is replaced, with Khronos' own build.
 */
object XrLoaderPatch : ApkPatch {
    override val id = "xr_loader"
    override val recommended = true
    override val locked = false

    const val LIBRARY_PATH = "lib/arm64-v8a/libopenxr_loader.so"

    /** Text that every recent loader holds and the old ones lack. */
    private val RECENT_MARKER = "LoaderInitData not initialized".toByteArray()

    override fun apply(session: PatchSession, context: PatchContext, assets: PatchAssets) {
        if (context.isVr == false) return
        val shipped = session.readFile(LIBRARY_PATH) ?: return
        if (isRecent(shipped)) return
        val replacement = runCatching { assets.xrLoader?.open()?.use { it.readBytes() } }.getOrNull() ?: return
        // Never trade a loader for one that is not recent itself.
        if (!isRecent(replacement)) return
        session.addFile(LIBRARY_PATH, replacement)
    }

    internal fun isRecent(library: ByteArray): Boolean = indexOf(library, RECENT_MARKER) >= 0

    private fun indexOf(haystack: ByteArray, needle: ByteArray): Int {
        outer@ for (start in 0..haystack.size - needle.size) {
            for (i in needle.indices) if (haystack[start + i] != needle[i]) continue@outer
            return start
        }
        return -1
    }
}
