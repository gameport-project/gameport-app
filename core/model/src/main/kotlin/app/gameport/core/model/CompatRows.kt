package app.gameport.core.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * One line of what the relay lists for a game (`GET /reports/<appId>`, see the relay's README): how many players ([n]) gave the same answer ([verdict], the words of
 * [Verdict.wire]) with one model of device ([model], and its [name] when the relay knows it) and one version of GamePort ([version]).
 */
@Serializable
data class CompatReport(
    val device: String,
    val model: String? = null,
    val name: String? = null,
    val verdict: String,
    val version: String? = null,
    val n: Int = 0,
    /** What the players of this line ticked as going wrong, by code of [Symptom]. */
    val issues: Map<String, Int> = emptyMap(),
    /** For a line that does not work: the version from which it may be solved, since players say it works from there. */
    val fixedSince: String? = null,
)

@Serializable
data class CompatReports(val v: Int, val appId: Int = 0, val reports: List<CompatReport> = emptyList(), val turns: Map<String, CompatTurn> = emptyMap()) {
    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        /** The relay's answer, or null when it cannot be read: a bad answer never replaces a good one. */
        fun parse(text: String?): CompatReports? = runCatching { json.decodeFromString(serializer(), text.orEmpty()) }.getOrNull()?.takeIf { it.v == 1 }
    }
}

/**
 * A line of the details of a game, as it is shown: [count] players said [verdict] with [name] (null when no model is known: the kind of device is shown instead) and
 * [version] of GamePort (null when it is not known). [mine] is the device in hand.
 */
data class CompatRow(
    val count: Int,
    val kind: String,
    val name: String?,
    val verdict: Verdict,
    val version: String?,
    val mine: Boolean,
    /** What the players of the line ticked as going wrong, the most said first. */
    val symptoms: List<Symptom> = emptyList(),
    /** The version from which a line that does not work may be solved, when players say it works from there. */
    val fixedSince: String? = null,
)

object CompatRows {
    /** What the relay calls a device, in the few words a person says it: "Meta Quest 3" is a Quest 3. */
    fun shortName(name: String?): String? = name?.trim()?.removePrefix("Meta ")?.trim()?.takeIf { it.isNotEmpty() }

    /**
     * The lines to show, the newest version of GamePort first (and, in the same version, the device in hand). From [reports] when the relay gave them (with the version of GamePort). Without them, from what the
     * totals say of each model, and failing that of each kind of device: the same lines, without a version. A line with another answer than the three known, or nobody
     * behind it, is left out.
     */
    fun of(reports: List<CompatReport>?, counts: CompatCounts?, deviceKind: String, deviceName: String?): List<CompatRow> {
        val rows = if (!reports.isNullOrEmpty()) fromReports(reports) else fromCounts(counts)
        val own = rows.map { it.copy(mine = isMine(it, deviceKind, deviceName)) }
        // The newest version first, and in the same version the device in hand first; the rest keeps the order it came in (the most said first).
        return own.sortedWith(Comparator<CompatRow> { a, b -> newestFirst(a.version, b.version) }.thenByDescending { it.mine })
    }

    /** Negative when [a] is a newer version than [b]; a line without a version, or with one that is not one, comes after the others. */
    private fun newestFirst(a: String?, b: String?): Int {
        val order = if (a != null && b != null) VersionOrder.compare(a, b) else null
        return when {
            order != null -> -order
            else -> (if (a != null && VersionOrder.compare(a, a) != null) 0 else 1) - (if (b != null && VersionOrder.compare(b, b) != null) 0 else 1)
        }
    }

    private fun isMine(row: CompatRow, deviceKind: String, deviceName: String?): Boolean =
        row.kind == deviceKind && (deviceName == null || row.name == null || row.name.equals(shortName(deviceName), ignoreCase = true))

    private fun fromReports(reports: List<CompatReport>): List<CompatRow> = reports.mapNotNull { report ->
        val verdict = Verdict.fromWire(report.verdict) ?: return@mapNotNull null
        if (report.n <= 0) return@mapNotNull null
        CompatRow(
            report.n, report.device, shortName(report.name), verdict, report.version?.takeIf { it.isNotBlank() }, mine = false,
            symptoms = symptomsOf(report.issues), fixedSince = report.fixedSince?.takeIf { it.isNotBlank() && verdict != Verdict.WORKS },
        )
    }

    /** The symptoms players ticked, the most said first; a code this version does not know is left out. */
    private fun symptomsOf(issues: Map<String, Int>): List<Symptom> =
        issues.entries.sortedByDescending { it.value }.mapNotNull { Symptom.parse(listOf(it.key)).firstOrNull() }

    private fun fromCounts(counts: CompatCounts?): List<CompatRow> {
        val rows = mutableListOf<CompatRow>()
        for ((kind, device) in counts?.devices.orEmpty()) {
            if (device.models.isNotEmpty()) {
                device.models.values.forEach { model -> rows += linesOf(kind, shortName(model.name), model.works, model.offlineOnly, model.fails, symptomsOf(model.issues)) }
            } else {
                rows += linesOf(kind, null, device.works, device.offlineOnly, device.fails, symptomsOf(device.issues))
            }
        }
        // The most said first.
        return rows.sortedByDescending { it.count }
    }

    /** The totals of a model give no version, and say what was ticked for the model, not for each answer: it goes with the lines that do not work. */
    private fun linesOf(kind: String, name: String?, works: Int, offlineOnly: Int, fails: Int, symptoms: List<Symptom>): List<CompatRow> = listOf(
        Verdict.WORKS to works,
        Verdict.OFFLINE_ONLY to offlineOnly,
        Verdict.FAILS to fails,
    ).filter { it.second > 0 }.map { (verdict, count) ->
        CompatRow(count, kind, name, verdict, version = null, mine = false, symptoms = if (verdict == Verdict.WORKS) emptyList() else symptoms)
    }
}
