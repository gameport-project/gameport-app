package app.gameport.core.patch.patches

import app.gameport.core.patch.ApkPatch
import app.gameport.core.patch.PatchAssets
import app.gameport.core.patch.PatchContext
import app.gameport.core.patch.PatchSession

/**
 * Lets a game that loads Meta's OpenXR loader run on a device that is not a Quest. Some engine builds always load
 * `libopenxr_loader_meta.so`, a small stub that hands over to the loader of the Quest's system. On any other headset that
 * system loader does not exist, the runtime is not found and the game stays black. The stub keeps its name (the game loads
 * it by name) and gets the bytes of Khronos' own loader, which finds the runtime of the device. A Quest is left alone.
 */
object MetaLoaderStubPatch : ApkPatch {
    override val id = "meta_loader_stub"
    override val recommended = true
    override val locked = false

    const val LIBRARY_PATH = "lib/arm64-v8a/libopenxr_loader_meta.so"

    override fun apply(session: PatchSession, context: PatchContext, assets: PatchAssets) {
        if (!wanted(context)) return
        val shipped = session.readFile(LIBRARY_PATH) ?: return
        // A stub that already is a complete loader needs nothing.
        if (XrLoaderPatch.isRecent(shipped)) return
        val replacement = runCatching { assets.xrLoader?.open()?.use { it.readBytes() } }.getOrNull() ?: return
        if (!XrLoaderPatch.isRecent(replacement)) return
        session.addFile(LIBRARY_PATH, replacement)
    }

    /** Only on a headset that is not a Quest, for a game that is not flat. */
    internal fun wanted(context: PatchContext): Boolean =
        context.isVr != false && context.deviceFamily != null && context.deviceFamily != "meta"
}
