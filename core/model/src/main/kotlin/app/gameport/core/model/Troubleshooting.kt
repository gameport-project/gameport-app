package app.gameport.core.model

/** What the troubleshooting of an installed game came to, for the window that tells the player. */
enum class TroubleshootOutcome { NOTHING_FOUND, FIXED, NEEDS_REINSTALL }

/** What was found and is not put right by itself: each has a place of its own on the game's page. */
enum class TroubleshootAdvice { PATCH_OUTDATED, STORAGE_PERMISSION, EXPANSION_NOT_WRITABLE }

/**
 * The result of the checks run on an installed game: the files put right ([fixed]), what is left for the player ([advice]), and the reason the game has to be given its
 * files again when nothing else can help ([reinstall]). A game is never uninstalled by the checks: the reinstallation is only proposed.
 */
data class Troubleshooting(
    val fixed: List<ObbTroubleshoot.Rename> = emptyList(),
    val advice: List<TroubleshootAdvice> = emptyList(),
    val reinstall: ObbTroubleshoot.ReinstallReason? = null,
) {
    val outcome: TroubleshootOutcome
        get() = when {
            reinstall != null -> TroubleshootOutcome.NEEDS_REINSTALL
            fixed.isNotEmpty() -> TroubleshootOutcome.FIXED
            else -> TroubleshootOutcome.NOTHING_FOUND
        }
}
