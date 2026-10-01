package app.gameport.core.sync

import app.gameport.core.model.SaveRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CloudPathsTest {
    private val dir = "Android/data/de.erthu.ancientdungeonfull/files"
    private val prefix = "%WinAppDataLocalLow%ErThu/Ancient_Dungeon"
    private val paths = CloudPaths(
        listOf(
            SaveRule(dir, "*.es3", recursive = true, cloudPrefix = prefix),
            SaveRule(dir, "save_metadata.json", recursive = false, cloudPrefix = prefix),
        ),
        steamId64 = 1L,
        accountId = 2L,
    )

    @Test
    fun `cloud names map to the game's folder`() {
        assertEquals("$dir/Slot_0/AncientSave.es3", paths.toLocal("$prefix/Slot_0/AncientSave.es3"))
        assertEquals("$dir/save_metadata.json", paths.toLocal("$prefix/save_metadata.json"))
    }

    @Test
    fun `local save files map back to their cloud names`() {
        assertEquals("$prefix/Slot_0/AncientSave.es3", paths.toCloud("$dir/Slot_0/AncientSave.es3"))
        assertEquals("$prefix/save_metadata.json", paths.toCloud("$dir/save_metadata.json"))
    }

    @Test
    fun `files the rules do not cover are not synced`() {
        assertNull(paths.toCloud("$dir/il2cpp/unity.ver"))
        assertNull(paths.toCloud("$dir/Slot_0/notes.txt"))
        assertNull(paths.toCloud("$dir/Slot_0/save_metadata.json"))
        assertNull(paths.toLocal("%OtherRoot%file"))
    }

    @Test
    fun `account tokens are replaced`() {
        val tokens = CloudPaths(listOf(SaveRule("Android/data/x/{64BitSteamID}", "*", true, "%R%{Steam3AccountID}")), 76561198000000000L, 42L)
        assertEquals("Android/data/x/76561198000000000/a.sav", tokens.toLocal("%R%42/a.sav"))
        assertEquals("%R%42/a.sav", tokens.toCloud("Android/data/x/76561198000000000/a.sav"))
    }

    @Test
    fun `globs match wildcards without regard to case`() {
        assertEquals(true, Glob.matches("*.es3", "Save.ES3"))
        assertEquals(true, Glob.matches("slot?.dat", "slot1.dat"))
        assertEquals(false, Glob.matches("*.es3", "save.es3.bac"))
    }

    @Test
    fun `a rule without a prefix maps the plain names of the cloud api files`() {
        val remote = "Android/data/pkg/files/gameport/Goldberg SteamEmu Saves/2897700/remote"
        val withApi = CloudPaths(
            listOf(SaveRule(dir, "*.es3", recursive = true, cloudPrefix = prefix), SaveRule(remote, "*", recursive = true, cloudPrefix = "")),
            steamId64 = 1L,
            accountId = 2L,
        )

        assertEquals("$remote/ProgressSaveData.save", withApi.toLocal("ProgressSaveData.save"))
        assertEquals("ProgressSaveData.save", withApi.toCloud("$remote/ProgressSaveData.save"))
        // Auto-Cloud names keep going to their own rule.
        assertEquals("$dir/Slot_0/a.es3", withApi.toLocal("$prefix/Slot_0/a.es3"))
    }
}
