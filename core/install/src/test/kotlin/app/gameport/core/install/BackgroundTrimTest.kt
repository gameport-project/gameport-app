package app.gameport.core.install

import app.gameport.core.install.BackgroundTrim.Candidate
import org.junit.Assert.assertEquals
import org.junit.Test

class BackgroundTrimTest {
    private val installed = listOf(
        Candidate("com.example.game", isSystem = false),
        Candidate("com.example.other", isSystem = false),
        Candidate("com.android.settings", isSystem = true),
        Candidate("com.oculus.store", isSystem = false),
        Candidate("com.pico.launcher", isSystem = false),
        Candidate("app.gameport", isSystem = false),
        Candidate("app.gameport.dev", isSystem = false),
    )

    @Test
    fun `only user apps are asked, without the one about to start`() {
        assertEquals(listOf("com.example.other"), BackgroundTrim.targets(installed, keep = setOf("com.example.game")))
    }

    @Test
    fun `system apps, the headset's own apps and GamePort are never asked`() {
        val asked = BackgroundTrim.targets(installed, keep = emptySet())
        assertEquals(listOf("com.example.game", "com.example.other"), asked)
    }

    @Test
    fun `nothing installed means nothing asked`() {
        assertEquals(emptyList<String>(), BackgroundTrim.targets(emptyList(), keep = setOf("x")))
    }
}
