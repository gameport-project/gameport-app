package app.gameport.core.patch.patches

import app.gameport.core.patch.ApkPatch
import app.gameport.core.patch.PatchAssets
import app.gameport.core.patch.PatchContext
import app.gameport.core.patch.PatchSession

/**
 * Adds GamePort's OpenXR layer to the game. The OpenXR loader finds it through the manifest placed
 * in the game's assets, and loads the library next to the game's own. The layer raises the world
 * for seated play and reports what the game asks of the runtime; without the library built into
 * GamePort the patch does nothing.
 */
object XrLayerPatch : ApkPatch {
    override val id = "xr_layer"
    override val recommended = true
    override val locked = false

    private const val LIBRARY = "libXrApiLayer_gameport.so"
    const val LIBRARY_PATH = "lib/arm64-v8a/$LIBRARY"
    const val MANIFEST_PATH = "assets/openxr/1/api_layers/implicit.d/XrApiLayer_gameport.json"

    private val MANIFEST = """
        {"file_format_version":"1.0.0","api_layer":{"name":"XR_APILAYER_GAMEPORT_compat","library_path":"$LIBRARY","api_version":"1.0","implementation_version":"1","description":"GamePort OpenXR compatibility layer","disable_environment":"DISABLE_GAMEPORT_XR_LAYER"}}
    """.trimIndent()

    override fun apply(session: PatchSession, context: PatchContext, assets: PatchAssets) {
        // The OpenXR layer only concerns VR games.
        if (context.isVr == false) return
        val library = runCatching { assets.xrLayer?.open()?.use { it.readBytes() } }.getOrNull() ?: return
        session.addFile(LIBRARY_PATH, library)
        session.addFile(MANIFEST_PATH, MANIFEST.toByteArray())
    }
}
