package app.gameport.feature.game

import app.gameport.core.model.ObbTroubleshoot
import app.gameport.core.model.TroubleshootAdvice
import app.gameport.core.model.Troubleshooting
import org.junit.Assert.assertEquals
import org.junit.Test

class TroubleshootDialogTest {
    private val fixed = listOf(ObbTroubleshoot.Rename("patch.75.pkg.obb", "patch.0.pkg.obb"))

    @Test
    fun `a game that said its files are missing is offered the repair`() {
        assertEquals(TroubleshootButton.REPAIR, troubleshootButton(Troubleshooting(reinstall = ObbTroubleshoot.ReinstallReason.REPORTED_MISSING)))
    }

    @Test
    fun `a game without its main file or with an empty one is offered the reinstallation, even when files were put right`() {
        assertEquals(TroubleshootButton.REINSTALL, troubleshootButton(Troubleshooting(reinstall = ObbTroubleshoot.ReinstallReason.NO_MAIN_FILE)))
        assertEquals(TroubleshootButton.REINSTALL, troubleshootButton(Troubleshooting(fixed = fixed, reinstall = ObbTroubleshoot.ReinstallReason.EMPTY_FILE)))
    }

    @Test
    fun `files put right lead to Play, and nothing found leads to no button`() {
        assertEquals(TroubleshootButton.PLAY, troubleshootButton(Troubleshooting(fixed = fixed)))
        assertEquals(TroubleshootButton.NONE, troubleshootButton(Troubleshooting()))
        assertEquals(TroubleshootButton.NONE, troubleshootButton(Troubleshooting(advice = listOf(TroubleshootAdvice.STORAGE_PERMISSION))))
    }
}
