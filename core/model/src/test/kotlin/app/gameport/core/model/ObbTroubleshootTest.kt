package app.gameport.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ObbTroubleshootTest {
    private val pkg = "com.gameport.g2081470"
    private val original = "com.studio.game"

    private fun renames(files: Map<String, Long>, unreal: Boolean = true, installed: Long? = 75, came: Long? = 75, originalName: String? = original) =
        ObbTroubleshoot.renames(files, pkg, originalName, installed, came, unreal)

    @Test
    fun `a patch an earlier GamePort named after the version code goes back to patch 0`() {
        val files = mapOf("main.75.$pkg.obb" to 2_779L, "overflow1.$pkg.obb" to 2_922L, "patch.75.$pkg.obb" to 3_220L)
        assertEquals(listOf(ObbTroubleshoot.Rename("patch.75.$pkg.obb", "patch.0.$pkg.obb")), renames(files))
    }

    @Test
    fun `the same goes when the file still carries the other package name`() {
        val files = mapOf("main.75.$pkg.obb" to 1L, "patch.75.$original.obb" to 1L)
        assertEquals(listOf(ObbTroubleshoot.Rename("patch.75.$original.obb", "patch.0.$pkg.obb")), renames(files))
    }

    @Test
    fun `nothing changes when a patch 0 is already there, and a file is never overwritten`() {
        assertTrue(renames(mapOf("main.75.$pkg.obb" to 1L, "patch.0.$pkg.obb" to 1L)).isEmpty())
        assertTrue(renames(mapOf("main.75.$pkg.obb" to 1L, "patch.0.$pkg.obb" to 1L, "patch.75.$pkg.obb" to 1L)).isEmpty())
    }

    @Test
    fun `run again on the result it finds nothing`() {
        val files = mapOf("main.75.$pkg.obb" to 1L, "patch.75.$pkg.obb" to 1L)
        val first = renames(files).single()
        val after = files - first.from + (first.to to 1L)
        assertTrue(renames(after).isEmpty())
    }

    @Test
    fun `a patch whose number is not one an earlier GamePort could have written is the game's own and stays`() {
        assertTrue(renames(mapOf("main.75.$pkg.obb" to 1L, "patch.5.$pkg.obb" to 1L)).isEmpty())
    }

    @Test
    fun `two patches are a doubt, and nothing is renamed`() {
        assertTrue(renames(mapOf("main.75.$pkg.obb" to 1L, "patch.75.$pkg.obb" to 1L, "patch.12.$pkg.obb" to 1L)).isEmpty())
    }

    @Test
    fun `a game that is not Unreal, or whose first version code is not known, is left as it is`() {
        val files = mapOf("main.75.$pkg.obb" to 1L, "patch.75.$pkg.obb" to 1L)
        assertTrue(renames(files, unreal = false).isEmpty())
        assertTrue(renames(files, came = null).isEmpty())
    }

    @Test
    fun `a main file named after the raised version goes back to the one the game came with`() {
        val files = mapOf("main.80.$pkg.obb" to 1L)
        assertEquals(listOf(ObbTroubleshoot.Rename("main.80.$pkg.obb", "main.75.$pkg.obb")), renames(files, installed = 80, came = 75))
    }

    @Test
    fun `a main file that is already right, or whose number is another one, stays`() {
        assertTrue(renames(mapOf("main.75.$pkg.obb" to 1L), installed = 80, came = 75).isEmpty())
        assertTrue(renames(mapOf("main.60.$pkg.obb" to 1L), installed = 80, came = 75).isEmpty())
    }

    @Test
    fun `an Unreal game with expansion files and no main file has to be given them again`() {
        assertEquals(ObbTroubleshoot.ReinstallReason.NO_MAIN_FILE, ObbTroubleshoot.reinstallReason(mapOf("vrpreferences.json" to 172L), pkg, original, unreal = true, expectsExpansion = true))
        assertEquals(ObbTroubleshoot.ReinstallReason.NO_MAIN_FILE, ObbTroubleshoot.reinstallReason(emptyMap(), pkg, original, unreal = true, expectsExpansion = true))
    }

    @Test
    fun `an empty expansion file calls for a new copy`() {
        assertEquals(
            ObbTroubleshoot.ReinstallReason.EMPTY_FILE,
            ObbTroubleshoot.reinstallReason(mapOf("main.75.$pkg.obb" to 10L, "patch.0.$pkg.obb" to 0L), pkg, original, unreal = true, expectsExpansion = true),
        )
    }

    @Test
    fun `no reason when the files are there, when the game has none, or when it is not Unreal`() {
        val files = mapOf("main.75.$pkg.obb" to 10L)
        assertNull(ObbTroubleshoot.reinstallReason(files, pkg, original, unreal = true, expectsExpansion = true))
        assertNull(ObbTroubleshoot.reinstallReason(emptyMap(), pkg, original, unreal = true, expectsExpansion = false))
        assertNull(ObbTroubleshoot.reinstallReason(emptyMap(), pkg, original, unreal = false, expectsExpansion = true))
    }

    @Test
    fun `the outcome puts the reinstallation first, then what was fixed`() {
        assertEquals(TroubleshootOutcome.NOTHING_FOUND, Troubleshooting().outcome)
        assertEquals(TroubleshootOutcome.NOTHING_FOUND, Troubleshooting(advice = listOf(TroubleshootAdvice.PATCH_OUTDATED)).outcome)
        val fixed = listOf(ObbTroubleshoot.Rename("a", "b"))
        assertEquals(TroubleshootOutcome.FIXED, Troubleshooting(fixed = fixed).outcome)
        assertEquals(TroubleshootOutcome.NEEDS_REINSTALL, Troubleshooting(fixed = fixed, reinstall = ObbTroubleshoot.ReinstallReason.NO_MAIN_FILE).outcome)
    }
}
