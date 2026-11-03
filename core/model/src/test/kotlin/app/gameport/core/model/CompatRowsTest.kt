package app.gameport.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CompatRowsTest {
    private fun report(model: String?, name: String?, verdict: String, version: String?, n: Int, device: String = "quest") =
        CompatReport(device = device, model = model, name = name, verdict = verdict, version = version, n = n)

    @Test
    fun `the lines come from the reports, with the name a person says and the version of GamePort`() {
        val rows = CompatRows.of(
            reports = listOf(
                report("Quest 3S", "Meta Quest 3S", "works", "0.9.2", 1),
                report("Quest 3", "Meta Quest 3", "works", "0.8.2", 3),
                report("Quest 3", "Meta Quest 3", "fails", "0.7.0", 1),
            ),
            counts = null, deviceKind = "pico", deviceName = null,
        )
        assertEquals(listOf("Quest 3S", "Quest 3", "Quest 3"), rows.map { it.name })
        assertEquals(listOf("0.9.2", "0.8.2", "0.7.0"), rows.map { it.version })
        assertEquals(listOf(Verdict.WORKS, Verdict.WORKS, Verdict.FAILS), rows.map { it.verdict })
        assertEquals(listOf(1, 3, 1), rows.map { it.count })
    }

    @Test
    fun `the newest version comes first, and in the same version the device in hand`() {
        val rows = CompatRows.of(
            reports = listOf(
                report("Quest 3S", "Meta Quest 3S", "works", "0.9.2", 5),
                report("Quest 3", "Meta Quest 3", "works", "0.8.2", 3),
                report("A8110", "Pico 4", "fails", "0.8.2", 2, device = "pico"),
            ),
            counts = null, deviceKind = "quest", deviceName = "Quest 3",
        )
        assertEquals(listOf("Quest 3S", "Quest 3", "Pico 4"), rows.map { it.name })
        assertEquals(listOf(false, true, false), rows.map { it.mine })
    }

    @Test
    fun `a device whose name is not known counts as the device in hand when its kind is the same`() {
        val rows = CompatRows.of(listOf(report(null, null, "works", "0.9.2", 2, device = "phone")), null, deviceKind = "phone", deviceName = null)
        assertTrue(rows.single().mine)
        assertNull(rows.single().name)
    }

    @Test
    fun `a line with an answer that is not known, or nobody behind it, is left out`() {
        val rows = CompatRows.of(
            listOf(report("Quest 3", "Meta Quest 3", "maybe", "0.9.2", 4), report("Quest 3", "Meta Quest 3", "works", "0.9.2", 0), report("Quest 3", "Meta Quest 3", "works", "0.9.2", 1)),
            null, "quest", null,
        )
        assertEquals(1, rows.size)
        assertEquals(1, rows.single().count)
    }

    @Test
    fun `without the reports the lines come from the totals of each model, without a version`() {
        val counts = CompatCounts(
            appId = 1, works = 3, fails = 1,
            devices = mapOf(
                "quest" to DeviceCounts(
                    works = 3, fails = 1,
                    models = mapOf("Quest 3" to ModelCounts(works = 2, fails = 1, name = "Meta Quest 3"), "Quest 3S" to ModelCounts(works = 1, name = "Meta Quest 3S")),
                ),
            ),
        )
        val rows = CompatRows.of(null, counts, "quest", "Quest 3S")
        assertTrue(rows.all { it.version == null })
        assertEquals(listOf("Quest 3S", "Quest 3", "Quest 3"), rows.map { it.name })
        assertEquals(listOf(Verdict.WORKS, Verdict.WORKS, Verdict.FAILS), rows.map { it.verdict })
        assertTrue(rows.first().mine)
        assertFalse(rows.last().mine)
    }

    @Test
    fun `without the reports or the models the lines come from the totals of each kind of device`() {
        val counts = CompatCounts(appId = 1, works = 2, fails = 1, offlineOnly = 1, devices = mapOf("pico" to DeviceCounts(works = 2, fails = 1, offlineOnly = 1)))
        val rows = CompatRows.of(null, counts, "quest", null)
        assertEquals(listOf(Verdict.WORKS, Verdict.OFFLINE_ONLY, Verdict.FAILS), rows.map { it.verdict }.sortedBy { it.ordinal })
        assertTrue(rows.all { it.kind == "pico" && it.name == null && !it.mine })
    }

    @Test
    fun `nothing at all gives no line`() {
        assertTrue(CompatRows.of(null, null, "quest", null).isEmpty())
        assertTrue(CompatRows.of(emptyList(), null, "quest", null).isEmpty())
    }

    @Test
    fun `the relay's answer is read, and anything else is not`() {
        val read = CompatReports.parse("""{"v":1,"appId":7,"reports":[{"device":"quest","model":"Quest 3","name":"Meta Quest 3","verdict":"works","version":"0.8.2","n":3,"extra":1}]}""")
        assertEquals(3, read?.reports?.single()?.n)
        assertNull(CompatReports.parse("""{"v":2,"reports":[]}"""))
        assertNull(CompatReports.parse("not json"))
        assertNull(CompatReports.parse(null))
    }

    @Test
    fun `a line that does not work carries what its players ticked and the version from which it may be solved`() {
        val rows = CompatRows.of(
            reports = listOf(
                CompatReport("quest", "Quest 3", "Meta Quest 3", "fails", "0.7.0", 2, issues = mapOf("slow" to 1, "no_sound" to 2), fixedSince = "0.9.0"),
                CompatReport("quest", "Quest 3", "Meta Quest 3", "works", "0.9.2", 3, fixedSince = "0.9.0"),
            ),
            counts = null, deviceKind = "quest", deviceName = "Quest 3",
        )
        val failing = rows.first { it.verdict == Verdict.FAILS }
        assertEquals(listOf(Symptom.NO_SOUND, Symptom.SLOW), failing.symptoms)
        assertEquals("0.9.0", failing.fixedSince)
        // A line that works has nothing to be solved, whatever the relay says.
        assertNull(rows.first { it.verdict == Verdict.WORKS }.fixedSince)
    }

    @Test
    fun `without the reports what was ticked for a model goes with its lines that do not work`() {
        val counts = CompatCounts(
            appId = 1, works = 1, fails = 1,
            devices = mapOf("quest" to DeviceCounts(works = 1, fails = 1, models = mapOf("Quest 3" to ModelCounts(works = 1, fails = 1, name = "Meta Quest 3", issues = mapOf("closes" to 1))))),
        )
        val rows = CompatRows.of(null, counts, "quest", "Quest 3")
        assertTrue(rows.first { it.verdict == Verdict.WORKS }.symptoms.isEmpty())
        assertEquals(listOf(Symptom.CLOSES), rows.first { it.verdict == Verdict.FAILS }.symptoms)
    }

    @Test
    fun `the turns and the ticks of the relay's answer are read`() {
        val read = CompatReports.parse(
            """{"v":1,"appId":7,"reports":[{"device":"quest","verdict":"fails","version":"0.7.0","n":1,"issues":{"no_sound":1},"fixedSince":"0.9.0"}],"turns":{"quest":{"fixedIn":"0.9.0","failedIn":"0.8.2","stillIn":"0.9.3"}}}""",
        )
        assertEquals("0.9.0", read?.reports?.single()?.fixedSince)
        assertEquals(1, read?.reports?.single()?.issues?.get("no_sound"))
        assertEquals(CompatTurn(fixedIn = "0.9.0", failedIn = "0.8.2", stillIn = "0.9.3"), read?.turns?.get("quest"))
    }

    @Test
    fun `versions are ordered as numbers, and a line without a version comes last`() {
        val rows = CompatRows.of(
            reports = listOf(
                report("Quest 3", "Meta Quest 3", "works", null, 9),
                report("Quest 3", "Meta Quest 3", "works", "0.9.9", 1),
                report("Quest 3", "Meta Quest 3", "works", "0.9.10", 1),
                report("Quest 3", "Meta Quest 3", "works", "0.9.10-alpha.2", 1),
            ),
            counts = null, deviceKind = "quest", deviceName = "Quest 3",
        )
        assertEquals(listOf("0.9.10", "0.9.10-alpha.2", "0.9.9", null), rows.map { it.version })
    }
}
