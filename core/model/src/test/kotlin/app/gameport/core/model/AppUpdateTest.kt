package app.gameport.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppUpdateTest {
    @Test
    fun `a version becomes a code that keeps the order of the releases`() {
        assertEquals(500, AppVersion.codeOf("0.5.0"))
        assertEquals(501, AppVersion.codeOf("0.5.1"))
        assertEquals(10_203, AppVersion.codeOf("1.2.3"))
        assertEquals(true, AppVersion.codeOf("0.10.0")!! > AppVersion.codeOf("0.9.9")!!)
    }

    @Test
    fun `things that are not versions are refused`() {
        assertNull(AppVersion.codeOf("0.5"))
        assertNull(AppVersion.codeOf("a.b.c"))
        assertNull(AppVersion.codeOf("1.100.0"))
        assertNull(AppVersion.versionOfTag("latest"))
        assertEquals("0.5.1", AppVersion.versionOfTag("v0.5.1"))
    }

    private val notes = """
        ## GamePort 0.5.1

        ### What's new in this version
        - **Demos and betas.** Listed now.
        - **Filters.** A panel on the left.

        Everything else is still there.

        ---

        ### GamePort in short
        - Not this one.

        ---

        ## GamePort 0.5.1

        ### Les nouveautés de cette version
        - **Démos et bêtas.** Listées.

        ---
    """.trimIndent()

    @Test
    fun `the whats new list comes in the player's language`() {
        assertEquals("• Demos and betas. Listed now.\n• Filters. A panel on the left.", ReleaseNotes.whatsNew(notes, french = false))
        assertEquals("• Démos et bêtas. Listées.", ReleaseNotes.whatsNew(notes, french = true))
    }

    @Test
    fun `a file without a list gives nothing`() {
        assertNull(ReleaseNotes.whatsNew("just words", french = false))
    }
}
