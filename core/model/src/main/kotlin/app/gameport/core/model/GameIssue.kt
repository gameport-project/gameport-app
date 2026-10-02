package app.gameport.core.model

/** Something about an installed game that the player should know or act on. */
sealed interface GameIssue {
    /** The game was seen using the Steam Frame's controllers, whose mapping can be changed; a notice, not a problem. */
    data class ControllerMappingAvailable(val source: String) : GameIssue

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

    /** The last save sync could not reach Steam ([offline]) or could not send the saves. */
    data class SaveSyncFailed(val offline: Boolean) : GameIssue
}
