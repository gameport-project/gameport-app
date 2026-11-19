package app.gameport.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RenamedPackagesTest {
    @Test
    fun `automatic gives the game another name only where the store refuses a re-signed one`() {
        assertEquals("com.gameport.g804530", RenamedPackages.chosen(PackageNameMode.AUTO, 804530, refusesReSigned = true))
        assertNull(RenamedPackages.chosen(PackageNameMode.AUTO, 804530, refusesReSigned = false))
        assertEquals("com.gameport.g804530", RenamedPackages.chosen(PackageNameMode.ON, 804530, refusesReSigned = false))
        assertNull(RenamedPackages.chosen(PackageNameMode.OFF, 804530, refusesReSigned = true))
    }

    @Test
    fun `the name is built from the app id alone, so a game patched again keeps it`() {
        assertEquals(RenamedPackages.nameFor(804530), RenamedPackages.chosen(PackageNameMode.ON, 804530, false))
        assertNotEquals(RenamedPackages.nameFor(804530), RenamedPackages.nameFor(846470))
    }

    /** Android refuses a package name whose parts do not begin with a letter ("Invalid manifest package: bad character"): the id alone would not do. */
    @Test
    fun `every part of the name is a name Android accepts`() {
        val parts = RenamedPackages.nameFor(804530).split('.')
        assertTrue(RenamedPackages.nameFor(804530), parts.size >= 2 && parts.all { it.first().isLetter() && it.all { c -> c.isLetterOrDigit() || c == '_' } })
    }

    @Test
    fun `a save rule keeps its cloud side and takes the name the game is installed under`() {
        val rule = SaveRule("Android/data/com.cmgames.unloop/files", "*.sav", recursive = true, cloudPrefix = "%WinAppDataLocal%Unloop")
        val moved = rule.forInstalledPackage("com.gameport.g2672470", "com.cmgames.unloop")
        assertEquals("Android/data/com.gameport.g2672470/files", moved.localDir)
        assertEquals(rule.cloudPrefix, moved.cloudPrefix)
        assertEquals(rule.pattern, moved.pattern)
    }

    @Test
    fun `a rule is left as it is when the names are the same, and when it is another game's`() {
        val rule = SaveRule("Android/data/com.cmgames.unloop/files", "*.sav", recursive = true, cloudPrefix = "x")
        assertEquals(rule, rule.forInstalledPackage("com.cmgames.unloop", null))
        assertEquals(rule, rule.forInstalledPackage("com.cmgames.unloop", "com.cmgames.unloop"))
        assertEquals(rule, rule.forInstalledPackage("com.gameport.g2672470", "com.other.game"))
    }

    @Test
    fun `a save rule in the game's private data follows the name too`() {
        val rule = SaveRule("@internal/com.cmgames.unloop/saves", "*", recursive = true, cloudPrefix = "x")
        assertEquals("@internal/com.gameport.g2672470/saves", rule.forInstalledPackage("com.gameport.g2672470", "com.cmgames.unloop").localDir)
    }
}
