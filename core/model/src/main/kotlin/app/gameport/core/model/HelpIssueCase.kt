package app.gameport.core.model

/** How a notice weighs: what needs attention, what only informs, a new version, and a question put to the player. */
enum class IssueTone { ATTENTION, INFO, UPDATE, QUESTION }

/**
 * Every kind of notice a game's page can show, for the legend in Settings > Help. [of] has no fallback branch: a new [GameIssue] does not build until
 * it is placed here, so the legend cannot miss a case.
 */
enum class HelpIssueCase(val tone: IssueTone) {
    UPDATE(IssueTone.UPDATE),
    PATCH_OUTDATED(IssueTone.ATTENTION),
    PROBLEM(IssueTone.ATTENTION),
    STORAGE(IssueTone.ATTENTION),
    SAVE_CONFLICT(IssueTone.ATTENTION),
    SAVE_SYNC_FAILED(IssueTone.ATTENTION),
    CONTROLLER_SILENT(IssueTone.INFO),
    CONTROLLER_MAPPING(IssueTone.INFO),
    REINSTALL(IssueTone.QUESTION),
    EXPANSION_UNCHECKED(IssueTone.INFO),
    EXPANSION_MISSING(IssueTone.ATTENTION),
    ;

    companion object {
        fun of(issue: GameIssue): HelpIssueCase = when (issue) {
            is GameIssue.ControllerMappingAvailable -> CONTROLLER_MAPPING
            is GameIssue.ControllerSilent -> CONTROLLER_SILENT
            GameIssue.UpdateAvailable -> UPDATE
            GameIssue.PatchOutdated -> PATCH_OUTDATED
            GameIssue.StoragePermissionMissing -> STORAGE
            GameIssue.SaveConflict -> SAVE_CONFLICT
            is GameIssue.ProblemSuspected -> PROBLEM
            GameIssue.ReinstallMayHelp -> REINSTALL
            GameIssue.ExpansionMayBeMissing -> EXPANSION_UNCHECKED
            is GameIssue.ExpansionMissing -> EXPANSION_MISSING
            is GameIssue.SaveSyncFailed -> SAVE_SYNC_FAILED
        }
    }
}
