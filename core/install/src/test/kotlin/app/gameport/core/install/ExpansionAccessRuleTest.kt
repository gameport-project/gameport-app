package app.gameport.core.install

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExpansionAccessRuleTest {
    @Test
    fun `access is asked when the game has expansion files and GamePort lacks it, on a device that has the page`() {
        assertTrue(ExpansionAccessRule.mustAsk(hasExpansion = true, sdk = 34, hasAccess = false, settingsPageExists = true))
    }

    @Test
    fun `nothing is asked once the access is given, for a game without expansion files, before Android 11, or on a device without the page`() {
        assertFalse(ExpansionAccessRule.mustAsk(true, 34, hasAccess = true, settingsPageExists = true))
        assertFalse(ExpansionAccessRule.mustAsk(false, 34, hasAccess = false, settingsPageExists = true))
        assertFalse(ExpansionAccessRule.mustAsk(true, 29, hasAccess = false, settingsPageExists = true))
        assertFalse(ExpansionAccessRule.mustAsk(true, 34, hasAccess = false, settingsPageExists = false))
    }
}
