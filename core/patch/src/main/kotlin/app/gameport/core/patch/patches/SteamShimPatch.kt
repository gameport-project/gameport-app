package app.gameport.core.patch.patches

import app.gameport.core.patch.ApkPatch
import app.gameport.core.patch.PatchContext
import app.gameport.core.patch.PatchSession
import app.gameport.core.patch.PatchAssets

/**
 * Puts GamePort's Steamworks shim in the game as `libsteamclient.so`. The game's own
 * `libsteam_api.so` loads that library by name, so from then on Steam calls reach the shim, which
 * answers with the signed-in account instead of a desktop Steam client.
 */
object SteamShimPatch : ApkPatch {
    override val id = "steam_shim"
    override val recommended = true
    override val locked = true

    const val SHIM_PATH = "lib/arm64-v8a/libsteamclient.so"
    const val CONFIG_PATH = "assets/gameport/steam.cfg"

    override fun apply(session: PatchSession, context: PatchContext, assets: PatchAssets) {
        session.addFile(SHIM_PATH, assets.shim.open().use { it.readBytes() })

        // Public account data only. The shim reads this stored entry straight from the APK and
        // turns it into the settings files Steamworks expects, at every launch.
        val config = "appid=${context.steamAppId}\nsteamid=${context.steamId}\nname=${context.personaName.oneLine()}\n"
        session.addFile(CONFIG_PATH, config.toByteArray())
    }
}

private fun String.oneLine() = replace('\n', ' ').replace('\r', ' ').trim()
