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
        session.addFile(CONFIG_PATH, configFor(context).toByteArray())

        // The achievements are a bonus: a shim that does not know them ignores these files, and a failure here never fails the patch.
        runCatching {
            context.achievementDefinitions?.takeIf { it.isNotEmpty() }?.let { definitions ->
                session.addFile(ACHIEVEMENTS_PATH, definitions.toByteArray())
                context.achievementsEarned?.takeIf { it.isNotEmpty() }?.let { session.addFile(ACHIEVEMENTS_EARNED_PATH, it.toByteArray()) }
            }
        }
    }

    /**
     * What the shim reads at every launch. The DLC lines are what the account has and what it does not (as far as the library knows: a DLC
     * that is on neither list keeps getting a yes); without them the shim keeps saying every DLC is there. Family Sharing is said as it is.
     */
    internal fun configFor(context: PatchContext): String = buildString {
        append("appid=${context.steamAppId}\nsteamid=${context.steamId}\nname=${context.personaName.oneLine()}\n")
        context.ownedDlc?.let {
            append("dlc=${it.distinct().sorted().joinToString(",")}\n")
            if (context.missingDlc.isNotEmpty()) append("dlcmissing=${context.missingDlc.distinct().sorted().joinToString(",")}\n")
        }
        if (context.familyShared) append("familysharing=1\n")
    }
}


private fun String.oneLine() = replace('\n', ' ').replace('\r', ' ').trim()
