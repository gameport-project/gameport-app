package app.gameport.core.model

import org.junit.Test
import org.junit.Assert.assertEquals

class HelpIssueCaseTest {
    private val everyIssue = listOf(
        GameIssue.ControllerMappingAvailable("source"),
        GameIssue.ControllerSilent(activated = false),
        GameIssue.UpdateAvailable,
        GameIssue.PatchOutdated,
        GameIssue.StoragePermissionMissing,
        GameIssue.SaveConflict,
        GameIssue.ProblemSuspected(crash = true),
        GameIssue.ReinstallMayHelp,
        GameIssue.ExpansionMayBeMissing,
        GameIssue.ExpansionMissing(missing = 2),
        GameIssue.SaveSyncFailed(offline = true),
        GameIssue.SavesApart,
    )

    @Test
    fun `every notice has a case in the help, and every case has a notice`() {
        assertEquals(HelpIssueCase.entries.toSet(), everyIssue.map(HelpIssueCase::of).toSet())
    }
}
