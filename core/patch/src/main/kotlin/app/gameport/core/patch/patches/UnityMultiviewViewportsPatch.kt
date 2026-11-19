package app.gameport.core.patch.patches

import app.gameport.core.patch.ApkPatch
import app.gameport.core.patch.PatchAssets
import app.gameport.core.patch.PatchContext
import app.gameport.core.patch.PatchSession

/**
 * Turns off a Unity setting that a Pico's graphics driver cannot take: the per-view viewports of the multiview rendering (`xr-mvpvv-enabled` in the game's `boot.config`,
 * which Valve's render regions feature asks for in the builds made for the Steam Frame). With it on, Cubism recorded its frame with secondary command buffers and the Adreno
 * driver of a Pico 4 Ultra died with a null pointer inside `vkEndCommandBuffer`, a few seconds after the first image, every time. Nothing else of the game was at fault:
 * with the setting off and everything else as it was, the game runs.
 *
 * Only on a Pico, only for a VR game, and only when the game's file asks for it (a value above zero): a game without the line, or with it off, is not touched. The rest of
 * the file is kept as it is.
 */
object UnityMultiviewViewportsPatch : ApkPatch {
    override val id = "unity_multiview_viewports_off"
    override val recommended = true
    override val locked = false

    const val BOOT_CONFIG = "assets/bin/Data/boot.config"

    private val SETTING = Regex("(?m)^(xr-mvpvv-enabled=)([1-9]\\d*)\\s*$")

    internal fun wanted(context: PatchContext): Boolean = context.isVr != false && context.deviceFamily == PicoMetadataPatch.FAMILY

    /** The text of the file with the setting turned off, or null when it holds none that is on. */
    internal fun rewrite(text: String): String? {
        if (!SETTING.containsMatchIn(text)) return null
        return SETTING.replace(text) { it.groupValues[1] + "0" }
    }

    override fun apply(session: PatchSession, context: PatchContext, assets: PatchAssets) {
        if (!wanted(context)) return
        val current = session.readFile(BOOT_CONFIG)?.toString(Charsets.UTF_8) ?: return
        val changed = rewrite(current) ?: return
        session.addFile(BOOT_CONFIG, changed.toByteArray(Charsets.UTF_8))
    }
}
