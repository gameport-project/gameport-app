package app.gameport.core.model

import app.gameport.core.model.ExpansionVerification.Result
import org.junit.Assert.assertEquals
import org.junit.Test

class ExpansionVerificationTest {
    @Test
    fun `every expected size held is complete`() {
        assertEquals(Result.Complete, ExpansionVerification.compare(listOf(2_000, 300), listOf(300, 2_000)))
    }

    @Test
    fun `a folder with nothing in it is missing everything the depot lists`() {
        assertEquals(Result.Missing(missing = 2, expected = 2, missingBytes = 2_300), ExpansionVerification.compare(listOf(2_000, 300), emptyList()))
    }

    @Test
    fun `a file too small is missing, the others are found`() {
        assertEquals(Result.Missing(1, 2, 2_000), ExpansionVerification.compare(listOf(2_000, 300), listOf(1_999, 300)))
    }

    @Test
    fun `two expected files of the same size need two held files`() {
        assertEquals(Result.Missing(1, 2, 500), ExpansionVerification.compare(listOf(500, 500), listOf(500)))
        assertEquals(Result.Complete, ExpansionVerification.compare(listOf(500, 500), listOf(500, 500)))
    }

    @Test
    fun `a depot that lists no expansion has nothing to miss`() {
        assertEquals(Result.Complete, ExpansionVerification.compare(emptyList(), emptyList()))
    }

    @Test
    fun `extra files in the folder do no harm`() {
        assertEquals(Result.Complete, ExpansionVerification.compare(listOf(10), listOf(10, 99, 4)))
    }

    @Test
    fun `the paths of a depot are chosen as the files of a download are`() {
        val pkg = "com.example.game"
        val placed = ObbNames.selectPlaced(listOf("app.apk", "obb/main.5.$pkg.obb", "notes.txt", ".DepotDownloader/x"), pkg)
        assertEquals(listOf("obb/main.5.$pkg.obb" to "main.5.$pkg.obb"), placed)
    }
}
