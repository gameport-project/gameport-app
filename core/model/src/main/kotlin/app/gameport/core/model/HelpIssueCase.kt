package app.gameport.core.model

/** How a notice weighs: what needs attention, what only informs, and a new version. */
enum class IssueTone { ATTENTION, INFO, UPDATE }

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
    REINSTALL(IssueTone.INFO),
    EXPANSION_UNCHECKED(IssueTone.INFO),
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
            is GameIssue.SaveSyncFailed -> SAVE_SYNC_FAILED
        }
    }
}
