package app.gameport.core.steam.session

import `in`.dragonbra.javasteam.types.KeyValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** What the Workshop in the application rests on: the cookie of the account, and knowing which games have a Workshop. */
class WorkshopSupportTest {
    private fun kv(name: String, value: String? = null, vararg children: KeyValue) =
        KeyValue(name, value).also { parent -> children.forEach { parent.children.add(it) } }

    private fun channel(name: String, gid: String) = kv(name, null, kv("gid", gid), kv("size", "10"), kv("download", "10"))

    private fun product(vararg categories: String): KeyValue = kv(
        "root", null,
        kv("common", null, kv("name", "A Game"), kv("type", "Game"), kv("category", null, *categories.map { kv(it, "1") }.toTypedArray())),
        kv("depots", null, kv("20", null, kv("config", null, kv("oslist", "android")), kv("manifests", null, channel("public", "5")))),
    )

    @Test
    fun `the cookie of the account is its number, two bars written as they are in a cookie, and the token`() {
        assertEquals("76561198000000001%7C%7Cabc.def.ghi", WebCookies(76561198000000001L, "abc.def.ghi").steamLoginSecure)
    }

    @Test
    fun `a game filed under Steam Workshop has a Workshop`() {
        assertTrue(product("category_2", "category_30").toAndroidGame(1)!!.workshop)
    }

    @Test
    fun `a game that is not filed under Steam Workshop has none`() {
        assertFalse(product("category_2", "category_53").toAndroidGame(1)!!.workshop)
        assertFalse(product().toAndroidGame(1)!!.workshop)
    }
}
