package app.gameport.core.model

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidCatalogTest {
    private val today = LocalDate.of(2026, 10, 10)

    private fun app(id: Int, since: String?, installable: Boolean = true, vr: Boolean = true, name: String = "App $id") =
        AndroidApp(appid = id, name = name, vr = vr, installable = installable, android = if (installable) "ready" else "unreleased", androidSince = since)

    @Test
    fun `the week slides with the day`() {
        val catalog = AndroidCatalog(apps = listOf(app(1, "2026-10-10"), app(2, "2026-10-03"), app(3, "2026-10-02"), app(4, null)))
        assertEquals(listOf(1, 2), catalog.arrivals(today).map { it.appid })
        // The next day the window has moved: what came on the 3rd is out of it, what came on the 10th stays for a week.
        assertEquals(listOf(1), catalog.arrivals(today.plusDays(1)).map { it.appid })
        assertEquals(listOf(1), catalog.arrivals(today.plusDays(7)).map { it.appid })
        assertTrue(catalog.arrivals(today.plusDays(8)).isEmpty())
    }

    @Test
    fun `an app without a date of arrival is never new`() {
        assertTrue(AndroidCatalog(apps = listOf(app(1, null), app(2, "not a date"))).arrivals(today).isEmpty())
    }

    @Test
    fun `only apps that can be installed are new, and the date in the future is not`() {
        val catalog = AndroidCatalog(apps = listOf(app(1, "2026-10-09", installable = false), app(2, "2026-10-12")))
        assertTrue(catalog.arrivals(today).isEmpty())
    }

    @Test
    fun `the newest come first, then by name`() {
        val catalog = AndroidCatalog(apps = listOf(app(1, "2026-10-08", name = "B"), app(2, "2026-10-09", name = "Z"), app(3, "2026-10-08", name = "A")))
        assertEquals(listOf(2, 3, 1), catalog.arrivals(today).map { it.appid })
    }

    @Test
    fun `the arrivals can be limited to VR or to flat games`() {
        val catalog = AndroidCatalog(apps = listOf(app(1, "2026-10-09", vr = true), app(2, "2026-10-09", vr = false)))
        assertEquals(listOf(1), catalog.arrivals(today, vr = true).map { it.appid })
        assertEquals(listOf(2), catalog.arrivals(today, vr = false).map { it.appid })
        assertEquals(2, catalog.arrivals(today).size)
    }

    @Test
    fun `a file is read as the repository writes it, and an unknown field does no harm`() {
        val text = """{"v":1,"count":1,"future":true,"apps":[{"appid":5,"name":"A","android":"ready","installable":true,"androidSince":"2026-10-09","vrMode":"only","extra":[1]}]}"""
        val catalog = AndroidCatalog.parse(text)!!
        assertEquals("A", catalog.of(5)!!.name)
        assertEquals(LocalDate.of(2026, 10, 9), catalog.of(5)!!.arrival)
    }

    @Test
    fun `a file that is not a catalog is refused`() {
        assertNull(AndroidCatalog.parse(null))
        assertNull(AndroidCatalog.parse("not json"))
        assertNull(AndroidCatalog.parse("""{"v":2,"apps":[{"appid":5,"name":"A"}]}"""))
        assertNull(AndroidCatalog.parse("""{"v":1,"apps":[]}"""))
    }
}
