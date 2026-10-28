package app.gameport.core.steam.session

import `in`.dragonbra.javasteam.types.KeyValue
import app.gameport.core.steam.cache.CachedDepot
import app.gameport.core.steam.cache.CachedDlcApp
import app.gameport.core.steam.cache.CachedGame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidDlcTest {
    private fun kv(name: String, value: String? = null, vararg children: KeyValue) =
        KeyValue(name, value).also { parent -> children.forEach { parent.children.add(it) } }

    private fun depot(id: String, os: String, gid: String = "4465632267227814212", dlc: String? = null) = kv(
        id, null,
        kv("config", null, kv("oslist", os)),
        kv("manifests", null, kv("public", null, kv("gid", gid), kv("size", "483868921"), kv("download", "480599280"))),
        *(dlc?.let { arrayOf(kv("dlcappid", it)) } ?: emptyArray()),
    )

    /** The record Steam publishes for a DLC of Puzzling Places: its Windows depot and its Android one, both named after the DLC. */
    private fun puzzlePack(type: String = "DLC", parent: String? = "3530820", androidOs: String = "android"): KeyValue = kv(
        "4684160", null,
        kv("common", null, kv("name", "Puzzling Places - Mars Desert Research Station"), kv("type", type), *(parent?.let { arrayOf(kv("parent", it)) } ?: emptyArray())),
        kv("depots", null, depot("4684160", "windows,linux", dlc = "4684160"), depot("4684161", androidOs, dlc = "4684160"), kv("branches", null, kv("public", null, kv("buildid", "1")))),
    )

    @Test
    fun `a DLC with an android depot of its own is read, with the game it belongs to`() {
        val dlc = puzzlePack().toAndroidDlc(4684160)!!
        assertEquals(3530820, dlc.parent)
        assertEquals("Puzzling Places - Mars Desert Research Station", dlc.name)
        assertEquals(listOf(4684161), dlc.depots.map { it.id })
        assertEquals(4684160, dlc.depots.single().dlcAppId)
        assertEquals(483868921L, dlc.depots.single().installBytes)
    }

    @Test
    fun `a DLC without an android depot, a game and a DLC without a parent are not read`() {
        assertNull(puzzlePack(androidOs = "windows").toAndroidDlc(4684160))
        assertNull(puzzlePack(type = "Game").toAndroidDlc(4684160))
        assertNull(puzzlePack(parent = null).toAndroidDlc(4684160))
    }

    @Test
    fun `the DLC apps of a game become its extra content, owned when the account has them`() {
        val game = CachedGame(3530820, "Puzzling Places", isVr = true, depots = listOf(CachedDepot(3530822, null, 2938460866, 2900083744, 1L)))
        val packs = listOf(
            CachedDlcApp(4684160, 3530820, "Mars Desert Research Station", listOf(CachedDepot(4684161, 4684160, 483868921, 480599280, 2L))),
            CachedDlcApp(5355010, 3530820, "The Lost Relic", listOf(CachedDepot(5355011, 5355010, 329362145, 326644976, 3L))),
            CachedDlcApp(999, 111, "Another game's DLC", listOf(CachedDepot(1000, 999, 1, 1, 4L))),
        )
        val library = libraryOf(listOf(game), owned = setOf(3530820), visible = setOf(3530820, 4684160), dlcNames = emptyMap(), scanning = false, dlcApps = packs)
        val dlc = library.games.single().androidBuild!!.dlc
        assertEquals(listOf("Mars Desert Research Station", "The Lost Relic"), dlc.map { it.name })
        assertEquals(listOf(true, false), dlc.map { it.owned })
        assertTrue(library.games.single().androidBuild!!.baseDepots.map { it.id } == listOf(3530822))
        assertEquals(listOf(4684161), dlc.first().depots.map { it.id })
        // Its depots are declared by the DLC app itself, which is the app to ask for their files.
        assertEquals(4684160, dlc.first().depots.single().declaredBy)
    }
}
