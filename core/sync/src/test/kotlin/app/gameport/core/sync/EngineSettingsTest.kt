package app.gameport.core.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EngineSettingsTest {
    @Test
    fun `the version of Unity is read from the start of its data file`() {
        val head = ByteArray(40) { 7 } + "6000.3.12f1".toByteArray() + ByteArray(20)
        assertEquals("6000.3.12f1", EngineSettings.unityVersion(head))
        assertEquals("2022.3.21f1", EngineSettings.unityVersion("junk\u0000 2022.3.21f1 \u0000".toByteArray()))
    }

    @Test
    fun `no version, or a number that only looks like one, is not taken`() {
        assertNull(EngineSettings.unityVersion(ByteArray(100)))
        assertNull(EngineSettings.unityVersion("1.2.3.4.5 and 6000.3.12".toByteArray()))
        assertNull(EngineSettings.unityVersion("x16000.3.12f1y".toByteArray()))
    }

    @Test
    fun `the boot config keeps its settings and drops blank lines and comments`() {
        val text = "gfx-enable-gfx-jobs=1\n\n# a comment\n  xr-mvpvv-enabled=2  \nnot a setting\r\nbuild-guid=abc\n"
        assertEquals(listOf("gfx-enable-gfx-jobs=1", "xr-mvpvv-enabled=2", "build-guid=abc"), EngineSettings.bootConfig(text))
    }

    @Test
    fun `a huge file is bounded in lines and in line length`() {
        val many = (1..500).joinToString("\n") { "k$it=v" }
        assertEquals(80, EngineSettings.bootConfig(many).size)
        assertEquals(160, EngineSettings.bootConfig("k=" + "x".repeat(1000)).single().length)
    }

    @Test
    fun `the text says the engine, its version and the settings`() {
        assertEquals("engine: Unity 6000.3.12f1\nboot.config:\n  a=1\n", EngineSettings.text("Unity", "6000.3.12f1", listOf("a=1")))
        assertEquals("engine: Unreal (version not found)\nboot.config: none\n", EngineSettings.text("Unreal", null, emptyList()))
        assertEquals("engine: not recognised (no Unity data, no Unreal library in the APK)\nboot.config: none\n", EngineSettings.text(null, null, emptyList()))
    }
}
