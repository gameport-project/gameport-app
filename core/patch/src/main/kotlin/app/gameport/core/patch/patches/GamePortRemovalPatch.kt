package app.gameport.core.patch.patches

import app.gameport.core.patch.ApkPatch
import app.gameport.core.patch.PatchAssets
import app.gameport.core.patch.PatchContext
import app.gameport.core.patch.PatchSession

/**
 * Takes GamePort's additions back out of a patched game: the Steam shim and its config, the save
 * hook and the OpenXR layer. The game keeps starting as a VR app. Not part of the catalog: it is
 * only used when the player asks for the patch to be removed.
 */
object GamePortRemovalPatch : ApkPatch {
    override val id = "gameport_removal"
    override val recommended = false
    override val locked = false

    override fun apply(session: PatchSession, context: PatchContext, assets: PatchAssets) {
        listOf(SteamShimPatch.SHIM_PATH, SteamShimPatch.CONFIG_PATH, XrLayerPatch.LIBRARY_PATH, XrLayerPatch.MANIFEST_PATH)
            .forEach(session::removeFile)
        session.existingHookDex?.let(session::removeFile)
        CloudHookPatch.removeFrom(session)
        PatchVersionPatch.write(session, PatchVersionPatch.REMOVED)
    }
}
