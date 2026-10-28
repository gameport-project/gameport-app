package app.gameport.core.install

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class WorkshopPlanTest {
    @get:Rule val folder = TemporaryFolder()

    private val entry = WorkshopPlan.Entry(size = 1000, updated = 50, creator = 7, title = "A puzzle")

    @Test
    fun `an item never downloaded is downloaded`() {
        assertEquals(WorkshopPlan.Source.DOWNLOAD, WorkshopPlan.decide(null, haveSize = 1000, ourCopy = true, remoteUpdated = 0))
    }

    @Test
    fun `an item the game holds whole is served from the game and GamePort keeps no copy`() {
        assertEquals(WorkshopPlan.Source.GAME_COPY, WorkshopPlan.decide(entry, haveSize = 1000, ourCopy = true, remoteUpdated = 50))
    }

    @Test
    fun `an item the game does not hold yet is served from GamePort's copy`() {
        assertEquals(WorkshopPlan.Source.OUR_COPY, WorkshopPlan.decide(entry, haveSize = null, ourCopy = true, remoteUpdated = 0))
        assertEquals(WorkshopPlan.Source.OUR_COPY, WorkshopPlan.decide(entry, haveSize = 400, ourCopy = true, remoteUpdated = 0))
    }

    @Test
    fun `an item whose copies are both gone is downloaded again`() {
        assertEquals(WorkshopPlan.Source.DOWNLOAD, WorkshopPlan.decide(entry, haveSize = null, ourCopy = false, remoteUpdated = 0))
        assertEquals(WorkshopPlan.Source.DOWNLOAD, WorkshopPlan.decide(entry, haveSize = 999, ourCopy = false, remoteUpdated = 0))
    }

    @Test
    fun `a version Steam has updated is downloaded again even when the game holds the old one`() {
        assertEquals(WorkshopPlan.Source.DOWNLOAD, WorkshopPlan.decide(entry, haveSize = 1000, ourCopy = false, remoteUpdated = 51))
    }

    @Test
    fun `an unknown date at Steam never forces a download`() {
        assertEquals(WorkshopPlan.Source.GAME_COPY, WorkshopPlan.decide(entry, haveSize = 1000, ourCopy = false, remoteUpdated = 0))
    }

    @Test
    fun `an empty record never matches an empty folder`() {
        val empty = entry.copy(size = 0)
        assertEquals(WorkshopPlan.Source.DOWNLOAD, WorkshopPlan.decide(empty, haveSize = 0, ourCopy = false, remoteUpdated = 0))
    }

    @Test
    fun `an item the account left is taken away, only when the subscriptions were read`() {
        assertEquals(setOf(3L), WorkshopPlan.toForget(held = setOf(1L, 2L, 3L), wanted = setOf(1L, 2L), subscriptionsKnown = true))
        assertEquals(emptySet<Long>(), WorkshopPlan.toForget(held = setOf(1L, 2L, 3L), wanted = emptySet(), subscriptionsKnown = false))
    }

    @Test
    fun `an item the game asks for is kept even when the account is not subscribed to it`() {
        assertEquals(emptySet<Long>(), WorkshopPlan.toForget(held = setOf(5L), wanted = setOf(5L), subscriptionsKnown = true))
    }

    @Test
    fun `the record is written and read back, a title with tabs and lines included`() {
        val file = File(folder.root, "9.ledger")
        WorkshopPlan.write(file, entry.copy(title = "Two\tparts\nof a title"))
        assertEquals(entry.copy(title = "Two parts of a title"), WorkshopPlan.read(file))
    }

    @Test
    fun `a missing or damaged record reads as nothing`() {
        assertNull(WorkshopPlan.read(File(folder.root, "none.ledger")))
        val damaged = File(folder.root, "x.ledger").apply { writeText("not a record") }
        assertNull(WorkshopPlan.read(damaged))
    }
}
