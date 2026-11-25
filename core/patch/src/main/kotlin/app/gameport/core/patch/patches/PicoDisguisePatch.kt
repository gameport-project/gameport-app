package app.gameport.core.patch.patches

import app.gameport.core.patch.AndroidIconBadger
import app.gameport.core.patch.ApkPatch
import app.gameport.core.patch.IconBadger
import app.gameport.core.patch.PatchAssets
import app.gameport.core.patch.PatchContext
import app.gameport.core.patch.PatchSession

/**
 * Disguises a game's icon on a Pico so the store's local recognition model ([com.bytedance.pico.matrix]'s `tt_sim_v3.0.model`) does not take it for a title it
 * sells. The model compares the installed app's label and icon to the store's catalogue; a re-signed game that matches is blocked at launch with
 * « Verification failed: illegal signature » and a « Buy now » window, a false positive for a player who owns the game on Steam.
 *
 * The icon is kept, with GamePort's logo inset as a patch in its bottom-right corner, on every density the game ships. The label is handled on its own by
 * [AppLabelPatch], which adds GamePort to it under the same condition. The name is what the model weighs most, and both together push the game clear of its
 * store twin.
 *
 * Only on a Pico, only for a VR game, and only when the disguise is asked for ([PatchContext.picoStoreDisguise]); safe otherwise and when the icon or the logo
 * is missing (it changes nothing). Adaptive icons (a `.xml` drawable) are left as they are for now: only the raster densities are badged.
 */
object PicoDisguisePatch : ApkPatch {
    override val id = "pico_disguise"
    override val recommended = true
    override val locked = false

    /** The share of the icon's width the logo patch takes. */
    const val BADGE_FRACTION = 0.35f

    internal fun wanted(context: PatchContext): Boolean =
        context.picoStoreDisguise && context.isVr != false && context.deviceFamily == PicoMetadataPatch.FAMILY

    override fun apply(session: PatchSession, context: PatchContext, assets: PatchAssets) =
        apply(session, context, assets, AndroidIconBadger)

    internal fun apply(session: PatchSession, context: PatchContext, assets: PatchAssets, badger: IconBadger) {
        if (!wanted(context)) return
        val logo = runCatching { assets.gamePortIcon?.open()?.use { it.readBytes() } }.getOrNull() ?: return
        val files = session.iconResPaths().mapNotNull { path -> session.readFile(path)?.let { path to it } }
        badgeIcons(files, logo, badger).forEach { (path, bytes) -> session.addFile(path, bytes) }
    }

    /** Only the raster (`.png`) icon files are badged; an adaptive icon's `.xml` is skipped. */
    internal fun badgeablePaths(paths: List<String>): List<String> =
        paths.filter { it.endsWith(".png", ignoreCase = true) }

    /**
     * The icon files to rewrite, by path: each `.png` of [iconFiles] laid over with the [logo], the ones the badger could not read left out so nothing is
     * replaced by a broken file. A `.xml` (adaptive) icon is skipped.
     */
    internal fun badgeIcons(iconFiles: List<Pair<String, ByteArray>>, logo: ByteArray, badger: IconBadger): Map<String, ByteArray> {
        val badgeable = badgeablePaths(iconFiles.map { it.first }).toSet()
        val result = LinkedHashMap<String, ByteArray>()
        for ((path, bytes) in iconFiles) {
            if (path !in badgeable) continue
            badger.badge(bytes, logo, BADGE_FRACTION)?.let { result[path] = it }
        }
        return result
    }
}
