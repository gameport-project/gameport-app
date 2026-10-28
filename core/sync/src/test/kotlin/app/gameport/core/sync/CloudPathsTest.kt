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

    /** Cubism: the name Steam describes is the Windows one; the saves an earlier GamePort sent are under the Android root, which Steam now refuses. */
    private val cubismDir = "Android/data/com.tvb.cubism/files"
    private val cubism = CloudPaths(
        listOf(
            SaveRule(cubismDir, "*.save", recursive = true, cloudPrefix = "%WinAppDataLocalLow%tvb/cubism"),
            SaveRule(cubismDir, "*.save", recursive = true, cloudPrefix = "%AndroidExternalData%com.tvb.cubism/files"),
        ),
        steamId64 = 1L,
        accountId = 2L,
    )
    private val legacy = "%AndroidExternalData%com.tvb.cubism/files/0_steam_full.save"
    private val declared = "%WinAppDataLocalLow%tvb/cubism/0_steam_full.save"

    @Test
    fun `the saves a game has in the cloud under the older android name are seen, none is lost from view`() {
        assertEquals("$cubismDir/0_steam_full.save", cubism.toLocal(legacy))
        assertEquals("$cubismDir/0_steam_custom.save", cubism.toLocal("%AndroidExternalData%com.tvb.cubism/files/0_steam_custom.save"))
    }

    @Test
    fun `the name Steam describes is read too`() {
        assertEquals("$cubismDir/0_steam_full.save", cubism.toLocal(declared))
    }

    @Test
    fun `the saves of the game are sent under the name Steam describes, not under the older name they have in the cloud`() {
        assertEquals(declared, cubism.toCloud("$cubismDir/0_steam_full.save"))
        assertEquals(listOf(declared, legacy), cubism.uploadNames("$cubismDir/0_steam_full.save", existing = legacy))
    }

    @Test
    fun `a file new to the cloud is offered the name Steam describes, then the older one`() {
        assertEquals(listOf(declared, legacy), cubism.uploadNames("$cubismDir/0_steam_full.save", existing = null))
    }

    @Test
    fun `a name that is the same as the one in the cloud is offered once`() {
        val single = CloudPaths(listOf(SaveRule(cubismDir, "*.save", true, "%WinAppDataLocalLow%tvb/cubism")), 1L, 2L)

        assertEquals(listOf(declared), single.uploadNames("$cubismDir/0_steam_full.save", existing = declared))
    }

    @Test
    fun `when the older and the new name both hold a save, the newest is the one kept`() {
        val old = CloudFile(legacy, "$cubismDir/0_steam_full.save", "aaa", 10, 1_000)
        val new = CloudFile(declared, "$cubismDir/0_steam_full.save", "bbb", 10, 2_000)

        assertEquals("bbb", cubism.byLocalPath(listOf(old, new)).getValue("$cubismDir/0_steam_full.save").sha1)
        assertEquals("bbb", cubism.byLocalPath(listOf(new, old)).getValue("$cubismDir/0_steam_full.save").sha1)
    }

    @Test
    fun `when both names hold the same date, the name Steam describes is the one kept`() {
        val old = CloudFile(legacy, "$cubismDir/0_steam_full.save", "aaa", 10, 1_000)
        val new = CloudFile(declared, "$cubismDir/0_steam_full.save", "bbb", 10, 1_000)

        assertEquals("bbb", cubism.byLocalPath(listOf(old, new)).getValue("$cubismDir/0_steam_full.save").sha1)
        assertEquals("bbb", cubism.byLocalPath(listOf(new, old)).getValue("$cubismDir/0_steam_full.save").sha1)
    }

    @Test
    fun `files of other paths are left alone`() {
        val a = CloudFile(declared, "$cubismDir/0_steam_full.save", "a", 1, 1)
        val b = CloudFile("%WinAppDataLocalLow%tvb/cubism/0_steam_custom.save", "$cubismDir/0_steam_custom.save", "b", 1, 1)

        assertEquals(setOf("$cubismDir/0_steam_full.save", "$cubismDir/0_steam_custom.save"), cubism.byLocalPath(listOf(a, b)).keys)
    }

    /** Quantum Void: separate Windows and Android rules, no override. Its names are what they were. */
    private val quantumDir = "@internal/com.tacticalnounours.quantumvoid/files/saves"
    private val quantumAndroid = "%AndroidInternalData%com.tacticalnounours.quantumvoid/files/saves"
    private val quantum = CloudPaths(listOf(SaveRule(quantumDir, "*", recursive = false, cloudPrefix = quantumAndroid, pcCloudPrefix = "%WinAppDataLocalLow%Tactical Nounours/Quantum Void/Saves")), 1L, 2L)

    /** The files Steam really holds for Quantum Void: the Android ones under the internal root, a PC copy under the Windows one that the Android pass does not read. */
    @Test
    fun `the real cloud files of Quantum Void map to one local file each, the Windows copies are not read by the android pass`() {
        val profile = "profile-a"
        val android = listOf("autosave", "DEBUT", "DEBUT1", "DEBUT2", "DEBUT2_ANDROID", "DEBUT3").map { "$quantumAndroid/$profile/$it" }
        val windows = listOf("autosave", "DEBUT", "DEBUT1", "DEBUT2", "DEBUT3").map { "%WinAppDataLocalLow%Tactical Nounours/Quantum Void/Saves/$profile/$it" }
        val rules = listOf(SaveRule(quantumDir, "*", recursive = true, cloudPrefix = quantumAndroid, pcCloudPrefix = "%WinAppDataLocalLow%Tactical Nounours/Quantum Void/Saves"))
        val paths = CloudPaths(rules, 1L, 2L)

        val mapped = (android + windows).mapNotNull { name -> paths.toLocal(name)?.let { CloudFile(name, it, "x", 1, 1) } }

        assertEquals(android.size, mapped.size)
        assertEquals(android.size, paths.byLocalPath(mapped).size)
        assertEquals("$quantumDir/$profile/DEBUT2_ANDROID", paths.toLocal("$quantumAndroid/$profile/DEBUT2_ANDROID"))
        assertEquals(listOf("$quantumAndroid/$profile/autosave"), paths.uploadNames("$quantumDir/$profile/autosave", existing = "$quantumAndroid/$profile/autosave"))
    }

    @Test
    fun `a game with separate rules keeps the name it has, in both directions`() {
        assertEquals("$quantumDir/autosave", quantum.toLocal("$quantumAndroid/autosave"))
        assertEquals("$quantumAndroid/autosave", quantum.toCloud("$quantumDir/autosave"))
        assertEquals(listOf("$quantumAndroid/autosave"), quantum.uploadNames("$quantumDir/autosave", existing = "$quantumAndroid/autosave"))
    }
}
