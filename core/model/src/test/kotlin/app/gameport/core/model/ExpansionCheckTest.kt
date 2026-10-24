package app.gameport.core.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExpansionCheckTest {
    private val mb = 1024L * 1024

    @Test
    fun `a game far heavier on Steam than its APK, with nothing noted, may be missing its files`() {
        assertTrue(ExpansionCheck.mayBeMissing(depotInstallBytes = 2_470 * mb, apkBytes = 83 * mb, noted = null))
    }

    @Test
    fun `a game noted as having no expansion, though Steam says it should have one, is suspected too`() {
        assertTrue(ExpansionCheck.mayBeMissing(depotInstallBytes = 2_470 * mb, apkBytes = 83 * mb, noted = 0))
    }

    @Test
    fun `a game whose expansion was noted as present is not suspected`() {
        assertFalse(ExpansionCheck.mayBeMissing(depotInstallBytes = 2_470 * mb, apkBytes = 83 * mb, noted = 2_300 * mb))
    }

    @Test
    fun `a game whose depot is about the size of its APK has no expansion to miss`() {
        assertFalse(ExpansionCheck.mayBeMissing(depotInstallBytes = 130 * mb, apkBytes = 120 * mb, noted = null))
    }

    @Test
    fun `a small difference is not an expansion`() {
        assertFalse(ExpansionCheck.mayBeMissing(depotInstallBytes = 299 * mb + 83 * mb, apkBytes = 83 * mb + 1, noted = null))
    }

    @Test
    fun `when Steam does not say how big the game is, nothing is suspected`() {
        assertFalse(ExpansionCheck.mayBeMissing(depotInstallBytes = null, apkBytes = 83 * mb, noted = null))
    }
}
