package app.gameport.core.steam.session

import `in`.dragonbra.javasteam.types.KeyValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChannelsParserTest {
    private fun kv(name: String, value: String? = null, vararg children: KeyValue) =
        KeyValue(name, value).also { parent -> children.forEach { parent.children.add(it) } }

    private fun channel(name: String, gid: String, size: String) = kv(name, null, kv("gid", gid), kv("size", size), kv("download", size))

    /** The shape of the product info of Iron Rebellion (trimmed): a Windows depot, an Android depot, four channels and Steam's own words for them. */
    private fun ironRebellion(): KeyValue = kv(
        "depots", null,
        kv("1192901", null, kv("config", null, kv("oslist", "windows")), kv("manifests", null, channel("public", "100", "10976161037"), channel("beta_branch", "101", "10335780690"))),
        kv(
            "1192902", null, kv("config", null, kv("oslist", "android")),
            kv(
                "manifests", null,
                channel("public", "3356460349393913942", "3689369749"),
                channel("android_branch", "2293030535267693131", "3690624583"),
                channel("beta_branch", "7738584439572401782", "3693760398"),
                channel("qol_branch", "8985211948127317845", "1355963004"),
            ),
        ),
        kv(
            "branches", null,
            kv("public", null, kv("buildid", "25444940"), kv("timeupdated", "1790106429")),
            kv("android_branch", null, kv("buildid", "25443165"), kv("description", "Android_branch"), kv("timeupdated", "1790015514")),
            kv("beta_branch", null, kv("buildid", "26173740"), kv("description", "New features tests"), kv("timeupdated", "1792800190")),
            kv("qol_branch", null, kv("buildid", "26281929"), kv("description", "Quality of Life Branch"), kv("timeupdated", "1793221580")),
        ),
    )

    @Test
    fun `every channel with an android build is read, with Steam's date and words`() {
        val channels = ChannelsParser.parse(ironRebellion()).associateBy { it.name }

        assertEquals(setOf("public", "android_branch", "beta_branch", "qol_branch"), channels.keys)
        assertEquals(1790106429L, channels.getValue("public").updated)
        assertNull(channels.getValue("public").description)
        assertEquals("New features tests", channels.getValue("beta_branch").description)
    }

    @Test
    fun `the size and the build are those of the android depot, not the windows one`() {
        val beta = ChannelsParser.parse(ironRebellion()).first { it.name == "beta_branch" }

        assertEquals(3693760398L, beta.androidBytes)
        assertEquals("1192902:7738584439572401782", beta.androidKey)
    }

    @Test
    fun `two channels with the same manifests have the same build text, two that differ do not`() {
        val depots = kv(
            "depots", null,
            kv("5", null, kv("config", null, kv("oslist", "android")), kv("manifests", null, channel("public", "7", "100"), channel("open-beta", "7", "100"), channel("previous", "8", "90"))),
            kv("branches", null, kv("public"), kv("open-beta"), kv("previous")),
        )

        val by = ChannelsParser.parse(depots).associateBy { it.name }

        assertEquals(by.getValue("public").androidKey, by.getValue("open-beta").androidKey)
        assertTrue(by.getValue("public").androidKey != by.getValue("previous").androidKey)
    }

    @Test
    fun `a channel that asks a password is said so`() {
        val depots = kv(
            "depots", null,
            kv("5", null, kv("config", null, kv("oslist", "android")), kv("manifests", null, channel("public", "7", "100"), channel("secret", "9", "100"))),
            kv("branches", null, kv("public"), kv("secret", null, kv("pwdrequired", "1"))),
        )

        val by = ChannelsParser.parse(depots).associateBy { it.name }

        assertEquals(false, by.getValue("public").passwordRequired)
        assertEquals(true, by.getValue("secret").passwordRequired)
    }

    @Test
    fun `the depots of an extra content and of another system are not the game`() {
        val depots = kv(
            "depots", null,
            kv("5", null, kv("config", null, kv("oslist", "android")), kv("dlcappid", "99"), kv("manifests", null, channel("public", "7", "100"), channel("only-dlc", "8", "100"))),
            kv("6", null, kv("config", null, kv("oslist", "windows")), kv("manifests", null, channel("public", "1", "5"))),
            kv("branches", null, kv("public"), kv("only-dlc")),
        )

        assertEquals(emptyList<Any>(), ChannelsParser.parse(depots))
    }

    @Test
    fun `a channel with the very build of the normal one is told so, the normal one never is, and the order is the one to show`() {
        val cached = ChannelsParser.parse(
            kv(
                "depots", null,
                kv("5", null, kv("config", null, kv("oslist", "android")), kv("manifests", null, channel("public", "7", "100"), channel("open-beta", "7", "100"), channel("previous", "8", "90"))),
                kv("branches", null, kv("public", null, kv("timeupdated", "300")), kv("open-beta", null, kv("timeupdated", "400")), kv("previous", null, kv("timeupdated", "500"))),
            ),
        )

        val shown = channelsOf(cached)

        assertEquals(listOf("public", "previous", "open-beta"), shown.map { it.name })
        assertEquals(false, shown.first { it.name == "public" }.sameAsNormal)
        assertEquals(true, shown.first { it.name == "open-beta" }.sameAsNormal)
        assertEquals(false, shown.first { it.name == "previous" }.sameAsNormal)
    }

    @Test
    fun `without a normal channel nothing is said to be the same as it`() {
        val shown = channelsOf(listOf(app.gameport.core.steam.cache.CachedChannel("beta", androidKey = "5:1"), app.gameport.core.steam.cache.CachedChannel("other", androidKey = "5:1")))

        assertEquals(listOf(false, false), shown.map { it.sameAsNormal })
    }

    @Test
    fun `a channel only named in a manifest still counts, and an app with no android depot has none`() {
        val named = kv(
            "depots", null,
            kv("5", null, kv("config", null, kv("oslist", "android")), kv("manifests", null, channel("public", "7", "100"), channel("unlisted", "8", "100"))),
        )
        assertEquals(setOf("public", "unlisted"), ChannelsParser.parse(named).map { it.name }.toSet())
        assertEquals(0L, ChannelsParser.parse(named).first { it.name == "unlisted" }.updated)

        val windowsOnly = kv("depots", null, kv("6", null, kv("config", null, kv("oslist", "windows")), kv("manifests", null, channel("public", "1", "5"))))
        assertEquals(emptyList<Any>(), ChannelsParser.parse(windowsOnly))
        assertEquals(emptyList<Any>(), ChannelsParser.parse(kv("depots")))
    }
}
