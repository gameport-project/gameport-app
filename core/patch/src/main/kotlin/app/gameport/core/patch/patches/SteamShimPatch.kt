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
    const val ACHIEVEMENTS_PATH = "assets/gameport/achievements.json"
    const val ACHIEVEMENTS_EARNED_PATH = "assets/gameport/achievements_earned.json"

    override fun apply(session: PatchSession, context: PatchContext, assets: PatchAssets) {
        session.addFile(SHIM_PATH, assets.shim.open().use { it.readBytes() })

        // Public account data only. The shim reads this stored entry straight from the APK and
        // turns it into the settings files Steamworks expects, at every launch.
        val config = "appid=${context.steamAppId}\nsteamid=${context.steamId}\nname=${context.personaName.oneLine()}\n"
        session.addFile(CONFIG_PATH, config.toByteArray())

        // The achievements are a bonus: a shim that does not know them ignores these files, and a failure here never fails the patch.
        runCatching {
            context.achievementDefinitions?.takeIf { it.isNotEmpty() }?.let { definitions ->
                session.addFile(ACHIEVEMENTS_PATH, definitions.toByteArray())
                context.achievementsEarned?.takeIf { it.isNotEmpty() }?.let { session.addFile(ACHIEVEMENTS_EARNED_PATH, it.toByteArray()) }
            }
        }
    }
}

private fun String.oneLine() = replace('\n', ' ').replace('\r', ' ').trim()
