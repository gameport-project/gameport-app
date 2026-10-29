package app.gameport.core.steam.session

import app.gameport.core.steam.cache.CachedChannel
import `in`.dragonbra.javasteam.types.KeyValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** A game whose Android build is only on a test channel (the shape of Unification, trimmed): it is listed with that channel, and a game with nothing anywhere is not. */
class ChannelOnlyGameTest {
    private fun kv(name: String, value: String? = null, vararg children: KeyValue) =
        KeyValue(name, value).also { parent -> children.forEach { parent.children.add(it) } }

    private fun channel(name: String, gid: String, size: String) = kv(name, null, kv("gid", gid), kv("size", size), kv("download", size))

    private fun product(vararg depotsAndBranches: KeyValue): KeyValue = kv(
        "root", null,
        kv("common", null, kv("name", "A Test Game"), kv("type", "Game")),
        kv("depots", null, *depotsAndBranches),
    )

    private val androidDepotOnBeta = kv("20", null, kv("config", null, kv("oslist", "android")), kv("manifests", null, channel("beta", "900", "5000")))

    @Test
    fun `a game with an android build on a test channel only is listed with the depots of that channel`() {
        val game = product(androidDepotOnBeta, kv("branches", null, kv("beta", null, kv("buildid", "7"), kv("timeupdated", "1790000000")))).toAndroidGame(1)

        assertNotNull(game)
        assertEquals(listOf(20), game!!.depots.map { it.id })
        assertEquals(900L, game.depots.single().manifestId)
        assertEquals(5000L, game.depots.single().installBytes)
        assertEquals(listOf("beta"), game.channels.map { it.name })
    }

    @Test
    fun `the most recently updated channel that asks no password is the one it is listed with`() {
        val depot = kv(
            "20", null, kv("config", null, kv("oslist", "android")),
            kv("manifests", null, channel("old", "1", "100"), channel("fresh", "2", "200"), channel("locked", "3", "300")),
        )
        val branches = kv(
            "branches", null,
            kv("old", null, kv("timeupdated", "100")),
            kv("fresh", null, kv("timeupdated", "500")),
            kv("locked", null, kv("timeupdated", "900"), kv("pwdrequired", "1")),
        )
        val game = product(depot, branches).toAndroidGame(1)

        assertEquals(2L, game!!.depots.single().manifestId)
    }

    @Test
    fun `a channel that asks a password alone does not list the game`() {
        val branches = kv("branches", null, kv("beta", null, kv("timeupdated", "1"), kv("pwdrequired", "1")))
        assertNull(product(androidDepotOnBeta, branches).toAndroidGame(1))
    }

    @Test
    fun `a game with a build on the normal channel keeps the depots of the normal channel`() {
        val depot = kv("20", null, kv("config", null, kv("oslist", "android")), kv("manifests", null, channel("public", "11", "100"), channel("beta", "12", "200")))
        val game = product(depot).toAndroidGame(1)

        assertEquals(11L, game!!.depots.single().manifestId)
        assertEquals(setOf("public", "beta"), game.channels.map { it.name }.toSet())
    }

    @Test
    fun `an android depot with no manifest anywhere lists nothing, as for a game announced ahead of release`() {
        val empty = kv("20", null, kv("config", null, kv("oslist", "android")), kv("manifests"))
        assertNull(product(empty).toAndroidGame(1))
    }

    @Test
    fun `the fallback channel is never the normal one and never one that asks a password`() {
        val channels = listOf(
            CachedChannel("public", updated = 900),
            CachedChannel("beta", updated = 500),
            CachedChannel("alpha", updated = 600, passwordRequired = true),
        )
        assertEquals("beta", ChannelsParser.fallbackChannel(channels)?.name)
        assertNull(ChannelsParser.fallbackChannel(listOf(CachedChannel("public", updated = 1))))
    }
}
