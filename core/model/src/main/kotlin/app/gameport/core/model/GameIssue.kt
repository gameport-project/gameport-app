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
     * again from scratch puts them right. A notice, not a problem: it never lights the cover badge.
     */
    data object ReinstallMayHelp : GameIssue

    /**
     * Steam says the game weighs far more than its APK and nothing was written down about its expansion files, which GamePort cannot look for: the game may be missing
     * its data. A notice, not a problem: the page offers to repair it, and it never lights the cover badge.
     */
    data object ExpansionMayBeMissing : GameIssue

    /** The last save sync could not reach Steam ([offline]) or could not send the saves. */
    data class SaveSyncFailed(val offline: Boolean) : GameIssue
}
