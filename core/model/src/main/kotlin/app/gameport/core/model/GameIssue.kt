package app.gameport.core.model

/** Something about an installed game that the player should know or act on. */
sealed interface GameIssue {
    /** The game was seen using the Steam Frame's controllers, whose mapping can be changed; a notice, not a problem. */
    data class ControllerMappingAvailable(val source: String) : GameIssue

    /** The game did not react to the controllers in use, and has the Steam Frame's controls as another way. */
    data class ControllerSilent(val activated: Boolean) : GameIssue

    /** Steam published a new version of the game. */
    data object UpdateAvailable : GameIssue

    /** The game was patched by an older GamePort patch than the current one. */
    data object PatchOutdated : GameIssue

    /** The game asks for access to shared storage and does not have it; on a headset it may keep closing. */
    data object StoragePermissionMissing : GameIssue

    /** The saves on the headset and in Steam Cloud differ and the player has not chosen yet. */
    data object SaveConflict : GameIssue

    /** The game closed within seconds of starting, or crashed: a problem report can help. [crash] tells which. */
    data class ProblemSuspected(val crash: Boolean) : GameIssue

    /**
     * The game has expansion files and its version was raised by an earlier patch, so its files and its code may disagree: if it does not start, installing it
     * again from scratch puts them right. A question, not a problem: the player is asked whether the game works (the cover carries a blue mark until they answer),
     * and the mark and the notice go once they say it does. It never lights the orange badge.
     */
    data object ReinstallMayHelp : GameIssue

    /**
     * Steam says the game weighs far more than its APK and nothing was written down about its expansion files, which GamePort cannot look for: the game may be missing
     * its data. A notice, not a problem: the page offers to repair it, and it never lights the cover badge.
     */
    data object ExpansionMayBeMissing : GameIssue

    /** The game itself said that [missing] of the files Steam lists for it are not in its folder: it cannot load its data. */
    data class ExpansionMissing(val missing: Int) : GameIssue

    /** The last save sync could not reach Steam ([offline]) or could not send the saves. */
    data class SaveSyncFailed(val offline: Boolean) : GameIssue

    /**
     * The studio keeps this game's Android saves apart from its PC ones, so a player who has played on a PC starts with none. A notice, not a
     * problem: the page of the saves offers to bring the PC saves here, and it never lights the cover badge.
     */
    data object SavesApart : GameIssue
}

/**
 * True for an issue that is a problem of the game: the orange mark on its cover. The notices, a new version, and the questions put to the player never
 * are (a question has its own blue mark, and goes away once answered).
 */
val GameIssue.needsAttention: Boolean
    get() = this !is GameIssue.ControllerMappingAvailable && this !is GameIssue.ControllerSilent && this !is GameIssue.UpdateAvailable &&
        this !is GameIssue.ReinstallMayHelp && this !is GameIssue.ExpansionMayBeMissing && this !is GameIssue.SavesApart
