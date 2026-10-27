package app.gameport.core.steam.session

import `in`.dragonbra.javasteam.types.KeyValue
import org.junit.Assert.assertEquals
import org.junit.Test

class SaveRulesParserTest {
    private fun kv(name: String, value: String? = null, vararg children: KeyValue) =
        KeyValue(name, value).also { parent -> children.forEach { parent.children.add(it) } }

    /** The `ufs` section Steam publishes for Ancient Dungeon, trimmed to what matters. */
    private fun ancientDungeon(): KeyValue = kv(
        "ufs", null,
        kv(
            "savefiles", null,
            kv("0", null, kv("root", "WinAppDataLocalLow"), kv("path", "ErThu/Ancient_Dungeon"), kv("pattern", "*.es3"), kv("recursive", "1")),
            kv("2", null, kv("root", "WinAppDataLocalLow"), kv("path", "ErThu/Ancient_Dungeon"), kv("pattern", "save_metadata.json")),
        ),
        kv(
            "rootoverrides", null,
            kv(
                "0", null,
                kv("root", "WinAppDataLocalLow"), kv("os", "Android"), kv("useinstead", "AndroidExternalData"),
                kv("pathtransforms", null, kv("0", null, kv("find", "ErThu/Ancient_Dungeon"), kv("replace", "Android/data/de.erthu.ancientdungeonfull/files"))),
            ),
        ),
    )

    @Test
    fun `maps the windows root to the game's android folder`() {
        val rules = SaveRulesParser.parse(ancientDungeon())

        assertEquals(2, rules.size)
        assertEquals("Android/data/de.erthu.ancientdungeonfull/files", rules[0].localDir)
        assertEquals("*.es3", rules[0].pattern)
        assertEquals(true, rules[0].recursive)
        assertEquals("%WinAppDataLocalLow%ErThu/Ancient_Dungeon", rules[0].cloudPrefix)
        assertEquals(false, rules[1].recursive)
    }

    /** Underdogs names the Android root directly and writes the path from Android/data. */
    @Test
    fun `an android rule that names the android root directly is kept`() {
        val underdogs = kv(
            "ufs", null,
            kv(
                "savefiles", null,
                kv("0", null, kv("root", "WinAppDataLocalLow"), kv("path", "One Hamsa/UNDERDOGS/SaveGames"), kv("pattern", "*.sav"), kv("platforms", null, kv("1", "Windows"))),
                kv("1", null, kv("root", "AndroidExternalData"), kv("path", "com.onehamsa.underdogs/files/SaveGames"), kv("pattern", "*.sav"), kv("platforms", null, kv("1", "Android"))),
            ),
        )

        val rules = SaveRulesParser.parse(underdogs)

        assertEquals(1, rules.size)
        assertEquals("Android/data/com.onehamsa.underdogs/files/SaveGames", rules[0].localDir)
        assertEquals("*.sav", rules[0].pattern)
        assertEquals("%AndroidExternalData%com.onehamsa.underdogs/files/SaveGames", rules[0].cloudPrefix)
    }

    @Test
    fun `a game without an android override cannot be synced`() {
        val noOverride = kv(
            "ufs", null,
            kv("savefiles", null, kv("0", null, kv("root", "WinAppDataLocalLow"), kv("path", "x"), kv("pattern", "*"))),
        )
        assertEquals(emptyList<Any>(), SaveRulesParser.parse(noOverride))
    }

    @Test
    fun `rules restricted to other platforms are ignored`() {
        val windowsOnly = ancientDungeon().also {
            it["savefiles"]["0"].children.add(kv("platforms", null, kv("0", "Windows")))
        }
        assertEquals(1, SaveRulesParser.parse(windowsOnly).size)
    }

    /** Quantum Void: one rule for Windows with no Android mapping, one that names the private data of the game. */
    @Test
    fun `an android rule in the private data of the game is kept, marked as internal`() {
        val quantumVoid = kv(
            "ufs", null,
            kv(
                "savefiles", null,
                kv("0", null, kv("root", "WinAppDataLocalLow"), kv("path", "Tactical Nounours/Quantum Void/Saves"), kv("pattern", "*"), kv("platforms", null, kv("1", "Windows")), kv("recursive", "1")),
                kv("1", null, kv("root", "AndroidInternalData"), kv("path", "com.tacticalnounours.quantumvoid/files/saves"), kv("pattern", "*"), kv("platforms", null, kv("1", "Android")), kv("recursive", "1")),
            ),
        )

        val rules = SaveRulesParser.parse(quantumVoid)

        assertEquals(1, rules.size)
        assertEquals("@internal/com.tacticalnounours.quantumvoid/files/saves", rules[0].localDir)
        assertEquals("%AndroidInternalData%com.tacticalnounours.quantumvoid/files/saves", rules[0].cloudPrefix)
        assertEquals(true, rules[0].recursive)
        // The Windows rule kept apart is remembered, to offer the Windows saves.
        assertEquals("%WinAppDataLocalLow%Tactical Nounours/Quantum Void/Saves", rules[0].pcCloudPrefix)
    }

    @Test
    fun `a rule linked to android by an override has no separate windows copy`() {
        val rules = SaveRulesParser.parse(ancientDungeon())

        assertEquals(listOf<String?>(null, null), rules.map { it.pcCloudPrefix })
    }

    @Test
    fun `an android rule without a windows rule has no windows copy`() {
        val coral = kv(
            "ufs", null,
            kv("savefiles", null, kv("0", null, kv("root", "AndroidExternalData"), kv("path", "Android/data/com.x.coral/files/User"), kv("pattern", "*.dat"), kv("platforms", null, kv("1", "Android")))),
        )

        assertEquals(null, SaveRulesParser.parse(coral).single().pcCloudPrefix)
    }

    @Test
    fun `an override for every system but windows applies to android`() {
        val ufs = kv(
            "ufs", null,
            kv("savefiles", null, kv("0", null, kv("root", "WinAppDataLocal"), kv("path", "Studio/Game"), kv("pattern", "*"))),
            kv(
                "rootoverrides", null,
                kv("0", null, kv("root", "WinAppDataLocal"), kv("os", "Windows"), kv("oscompare", "!="), kv("useinstead", "AndroidExternalData"), kv("addpath", "Android/data/com.studio.game/files")),
            ),
        )

        assertEquals("Android/data/com.studio.game/files/Studio/Game", SaveRulesParser.parse(ufs).single().localDir)
    }
}
