package app.gameport.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    private val groupedNotes = """
        ## GamePort 0.8.2

        ### What's new in this version

        #### New features
        - **A free game.** Added in one tap.
        - **More filters.** Free or paid.

        #### Fixes
        - A game with expansion files keeps its version.

        ---

        ### Install
        - Not this one.

        ---

        ## GamePort 0.8.2

        ### Les nouveautés de cette version

        #### Nouvelles fonctionnalités
        - **Un jeu gratuit.** Ajouté en un geste.

        #### Corrections
        - Un jeu avec des fichiers d'extension garde sa version.

        ---
    """.trimIndent()

    @Test
    fun `bullets grouped under deeper headings are part of the list, and what follows the rule is not`() {
        assertEquals(
            "• A free game. Added in one tap.\n• More filters. Free or paid.\n• A game with expansion files keeps its version.",
            ReleaseNotes.whatsNew(groupedNotes, french = false),
        )
        assertEquals(
            "• Un jeu gratuit. Ajouté en un geste.\n• Un jeu avec des fichiers d'extension garde sa version.",
            ReleaseNotes.whatsNew(groupedNotes, french = true),
        )
        val blocks = ReleaseNotes.whatsNewBlocks(groupedNotes, french = false)
        assertEquals(3, blocks.size)
        assertEquals("• A free game. Added in one tap.", (blocks.first() as NoteBlock.Line).text)
    }

    @Test
    fun `every release notes file of the project shows its list`() {
        val folder = java.io.File("../../docs/releases")
        val files = folder.listFiles { file -> file.name.matches(Regex("v\\d+\\.\\d+\\.\\d+\\.md")) }.orEmpty()
        assertTrue("the notes files were not found at ${folder.absolutePath}", files.isNotEmpty())
        for (file in files.sortedBy { it.name }) {
            val text = file.readText()
            assertNotNull("${file.name}: no list in English", ReleaseNotes.whatsNew(text, french = false))
            assertNotNull("${file.name}: no list in French", ReleaseNotes.whatsNew(text, french = true))
        }
    }

    @Test
    fun `a file without a list gives nothing`() {
        assertNull(ReleaseNotes.whatsNew("just words", french = false))
    }

    @Test
    fun `release notes give their lines and the project images in order`() {
        val notes = """
            ## GamePort 1.0.0

            ### What's new in this version
            - **One.** First.

            <img src="../screenshots/menu.png" alt="A menu" width="360">

            - **Two.** Second.
            ![Elsewhere](https://example.com/x.png)
            ![Plain](../screenshots/plain.png)

            ---

            ### Install
            - not shown
        """.trimIndent()
        val blocks = ReleaseNotes.whatsNewBlocks(notes, french = false, imageBase = "https://raw.githubusercontent.com/gameport-project/gameport-app/v1.0.0")
        assertEquals(
            listOf(
                NoteBlock.Line("• One. First."),
                NoteBlock.Image("https://raw.githubusercontent.com/gameport-project/gameport-app/v1.0.0/docs/screenshots/menu.png", "A menu"),
                NoteBlock.Line("• Two. Second."),
                NoteBlock.Image("https://raw.githubusercontent.com/gameport-project/gameport-app/v1.0.0/docs/screenshots/plain.png", "Plain"),
            ),
            blocks,
        )
    }
}
