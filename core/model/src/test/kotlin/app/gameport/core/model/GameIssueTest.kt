package app.gameport.core.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GameIssueTest {
    @Test
    fun `the questions and the notices never light the orange mark`() {
        assertFalse(GameIssue.ReinstallMayHelp.needsAttention)
        assertFalse(GameIssue.ExpansionMayBeMissing.needsAttention)
        assertFalse(GameIssue.UpdateAvailable.needsAttention)
        assertFalse(GameIssue.ControllerSilent(activated = false).needsAttention)
        assertFalse(GameIssue.ControllerMappingAvailable("touch").needsAttention)
    }

    @Test
    fun `a problem of the game lights the orange mark`() {
        assertTrue(GameIssue.PatchOutdated.needsAttention)
        assertTrue(GameIssue.SaveConflict.needsAttention)
        assertTrue(GameIssue.StoragePermissionMissing.needsAttention)
        assertTrue(GameIssue.ProblemSuspected(crash = false).needsAttention)
        assertTrue(GameIssue.ExpansionMissing(2).needsAttention)
        assertTrue(GameIssue.SaveSyncFailed(offline = true).needsAttention)
    }

    @Test
    fun `reinstall is a question in the help legend`() {
        assertTrue(HelpIssueCase.of(GameIssue.ReinstallMayHelp).tone == IssueTone.QUESTION)
    }
}
