package app.gameport.core.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** What a player says about the controls of a game, apart from whether the game itself works. [wire] is how the relay writes it. */
enum class ControllerAnswer(val wire: String) {
    WORKS("works"),
    PARTIAL("partial"),
    FAILS("fails"),
    ;

    companion object {
        fun fromWire(wire: String?): ControllerAnswer? = entries.firstOrNull { it.wire == wire }
    }
}

/**
 * The mapping of a game as the relay writes it (see its README): where every control the game uses goes on the device's controllers, in one text, sorted, so that
 * two players with the same mapping send the same text. [customised] says the player's choices make it differ from GamePort's own.
 */
data class ControllerFingerprint(
    /** `source>target[+target]` joined by `;`, a control that goes nowhere written `source>none`. Empty while the game uses no control. */
    val mapping: String,
    /** The ready-made mapping this one corresponds to, when it is customised and one does (see [presetFor]). */
    val preset: ControllerPreset?,
    /** The player turned customising on and the controls go elsewhere than GamePort's own mapping sends them. */
    val customised: Boolean,
) {
    /** There is something to tell the relay about. */
    val sendable: Boolean get() = mapping.isNotEmpty()

    /** What the relay is told in the `preset` field: [AUTO] for GamePort's own mapping, the preset's key, or nothing for a customised mapping that is none. */
    val wirePreset: String? get() = if (!customised) AUTO else preset?.key

    companion object {
        const val MAX_LENGTH = 700
        const val MAX_BINDINGS = 60
        /** The `preset` value for the mapping GamePort makes by itself, with no choice of the player. */
        const val AUTO = "auto"
        private val GROUP = Regex("[a-z_]{1,20}")

        private fun valid(ref: ControlRef) = GROUP.matches(ref.group)

        /** The canonical text of an [allocation]. A control the relay could not read, and what does not fit its limits, is left out. */
        fun canonical(allocation: Map<ControlRef, List<ControlRef>>): String {
            val parts = ArrayList<String>()
            var length = 0
            for ((source, targets) in allocation.entries.filter { valid(it.key) }.sortedBy { it.key.key }) {
                val goes = targets.filter(::valid).distinct().sortedBy { it.key }
                val part = "${source.key}>${if (goes.isEmpty()) "none" else goes.joinToString("+") { it.key }}"
                if (parts.size >= MAX_BINDINGS || length + part.length + (if (parts.isEmpty()) 0 else 1) > MAX_LENGTH) break
                length += part.length + (if (parts.isEmpty()) 0 else 1)
                parts += part
            }
            return parts.joinToString(";")
        }

        /** The mapping the game has now: what its controls do (the player's choices when customising, GamePort's own otherwise). */
        fun of(mapping: ControllerMapping): ControllerFingerprint {
            val allocation = mapping.allocation()
            val text = canonical(allocation)
            val customised = mapping.enabled && text != canonical(ControllerLayout.allocate(mapping.detected.toSet()))
            return ControllerFingerprint(text, if (customised) presetFor(allocation, ControllerLayout.allocate(mapping.detected.toSet())) else null, customised)
        }

        /**
         * The preset that [allocation] is: every link of the preset that the game uses goes where the preset sends it, and at least one of them differs from [automatic] (GamePort's own
         * mapping), so that a preset is only named when the player's choice made the difference.
         */
        fun presetFor(allocation: Map<ControlRef, List<ControlRef>>, automatic: Map<ControlRef, List<ControlRef>>): ControllerPreset? = ControllerPreset.entries.firstOrNull { preset ->
            preset.matches(allocation) && preset.links.keys.any { it in allocation && allocation[it].orEmpty().toSet() != automatic[it].orEmpty().toSet() }
        }

        /** The controls of a canonical [text], or null when it is not one (empty, malformed). */
        fun parse(text: String): Map<ControlRef, List<ControlRef>>? {
            if (text.isEmpty()) return null
            val result = LinkedHashMap<ControlRef, List<ControlRef>>()
            for (part in text.split(';')) {
                val sides = part.split('>')
                if (sides.size != 2) return null
                val source = ControlRef.parse(sides[0])?.takeIf(::valid) ?: return null
                val targets = if (sides[1] == "none") {
                    emptyList()
                } else {
                    sides[1].split('+').map { ControlRef.parse(it)?.takeIf(::valid) ?: return null }.distinct()
                }
                result[source] = targets
            }
            return result
        }
    }
}

/** The message sent to the relay for what a player says about the controls of a game: only these fields, never a name, a path or a serial number. */
data class ControllerFeedbackMessage(
    val appId: Int,
    val device: String,
    val voter: String,
    val appVersion: String,
    val answer: ControllerAnswer,
    val mapping: String,
    val preset: String? = null,
) {
    fun toJson(): String = buildJsonObject {
        put("v", 1)
        put("appId", appId)
        put("device", device)
        put("voter", voter)
        put("app", appVersion)
        put("answer", answer.wire)
        put("mapping", mapping)
        preset?.takeIf { p -> p == ControllerFingerprint.AUTO || ControllerPreset.entries.any { it.key == p } }?.let { put("preset", it) }
    }.toString()
}

/** One mapping the players say works, as the relay lists it: how many of them answered each way for this exact mapping. */
data class CommunityMapping(val mapping: String, val preset: ControllerPreset?, val works: Int, val partial: Int, val fails: Int, val automatic: Boolean = false) {
    /** Where each control goes, or null when the text is not a mapping. */
    val links: Map<ControlRef, List<ControlRef>>? by lazy { ControllerFingerprint.parse(mapping) }

    /**
     * What applying it changes for a game that uses [used]: only the controls the game uses, and only to controls the device has (a text from elsewhere is never trusted
     * further than that).
     */
    fun changesFor(used: Collection<ControlRef>): Map<ControlRef, List<ControlRef>> =
        links.orEmpty().filterKeys { it in used }.mapValues { (_, targets) -> targets.filter { it in ControllerLayout.targets } }
}

object CommunityMappings {
    const val MAX = 10

    /** The mappings of the relay's answer, in its order; what is not as expected is left out, and a text that is not an answer gives none. */
    fun parse(text: String?): List<CommunityMapping> = runCatching {
        val root = Json.parseToJsonElement(text.orEmpty()).jsonObject
        root["mappings"]?.jsonArray.orEmpty().mapNotNull { entry -> entry.entryOrNull() }.take(MAX)
    }.getOrDefault(emptyList())

    private fun kotlinx.serialization.json.JsonElement.entryOrNull(): CommunityMapping? = runCatching {
        val obj = this as? JsonObject ?: return null
        val mapping = obj["mapping"]?.jsonPrimitive?.contentOrNull?.takeIf { ControllerFingerprint.parse(it) != null } ?: return null
        val key = obj["preset"]?.takeIf { it is JsonPrimitive && it.isString }?.jsonPrimitive?.contentOrNull
        val preset = key?.let { k -> ControllerPreset.entries.firstOrNull { it.key == k } }
        CommunityMapping(
            mapping,
            preset,
            obj["works"]?.jsonPrimitive?.intOrNull?.coerceAtLeast(0) ?: 0,
            obj["partial"]?.jsonPrimitive?.intOrNull?.coerceAtLeast(0) ?: 0,
            obj["fails"]?.jsonPrimitive?.intOrNull?.coerceAtLeast(0) ?: 0,
            automatic = key == ControllerFingerprint.AUTO,
        )
    }.getOrNull()
}

/** What is kept for one game about its controllers' feedback. Times are epoch milliseconds. */
@Serializable
data class ControllerFeedbackRecord(
    /** The mapping last looked at, and when it changed to it. */
    val fingerprint: String = "",
    val changedAt: Long = 0,
    /** When a run of the game last ended. */
    val lastSessionEnd: Long = 0,
    /** The last answer, the mapping it was given for (and the preset of it), and whether the relay has it. */
    val answeredMapping: String = "",
    val answeredPreset: String = "",
    val answer: String? = null,
    val sent: Boolean = true,
    /** "Later" was said for this mapping, and the end of the run it was said during. */
    val laterMapping: String = "",
    val laterSessionEnd: Long = 0,
)

/** An answer that has not reached the relay yet. */
data class PendingControllerAnswer(val appId: Int, val mapping: String, val preset: String, val answer: ControllerAnswer)

/**
 * What the player said about the controls of each game, and when to ask, as plain data so the rules are tested without a device. A game is asked about on its page, once
 * a run has ended since the mapping last changed, when the mapping (GamePort's own or the player's) has not been answered yet, and not again for the run the player said "later" in.
 */
@Serializable
data class ControllerFeedbackBook(val games: Map<Int, ControllerFeedbackRecord> = emptyMap()) {
    private fun record(appId: Int) = games[appId] ?: ControllerFeedbackRecord()

    private fun with(appId: Int, record: ControllerFeedbackRecord) = copy(games = games + (appId to record))

    /** The mapping of the game was looked at, at [at]: when it is not the one kept, that is the time it changed. */
    fun noted(appId: Int, mapping: String, at: Long): ControllerFeedbackBook {
        val known = record(appId)
        return if (known.fingerprint == mapping) this else with(appId, known.copy(fingerprint = mapping, changedAt = at))
    }

    /** A run of the game ended at [at]. */
    fun sessionEnded(appId: Int, at: Long): ControllerFeedbackBook = with(appId, record(appId).copy(lastSessionEnd = at))

    /** Whether the card asking about the controls of the game is shown, with [current] the mapping the game has now, at [now]. */
    fun shouldAsk(appId: Int, current: ControllerFingerprint, now: Long): Boolean {
        if (!current.sendable) return false
        val known = record(appId)
        if (known.answeredMapping == current.mapping) return false
        // A mapping not looked at yet changed just now: no run can have ended after it.
        val changedAt = if (known.fingerprint == current.mapping) known.changedAt else now
        if (known.lastSessionEnd <= 0L || known.lastSessionEnd <= changedAt) return false
        if (known.laterMapping == current.mapping && known.laterSessionEnd >= known.lastSessionEnd) return false
        return true
    }

    /** The player answered for [current]: it goes to the relay, replacing an answer given before, and the card is gone until the mapping changes. */
    fun answered(appId: Int, current: ControllerFingerprint, answer: ControllerAnswer): ControllerFeedbackBook {
        if (!current.sendable) return this
        return with(appId, record(appId).copy(answeredMapping = current.mapping, answeredPreset = current.wirePreset.orEmpty(), answer = answer.wire, sent = false, laterMapping = "", laterSessionEnd = 0))
    }

    /** The player said "later" for [current]: the card comes back after the next run that ends, not before. */
    fun later(appId: Int, current: ControllerFingerprint): ControllerFeedbackBook =
        with(appId, record(appId).let { it.copy(laterMapping = current.mapping, laterSessionEnd = it.lastSessionEnd) })

    /** The answer the player gave for [mapping], when it is the last one they gave. */
    fun answerFor(appId: Int, mapping: String): ControllerAnswer? = record(appId).takeIf { it.answeredMapping == mapping }?.answer?.let(ControllerAnswer::fromWire)

    /** The answers the relay has not received yet. */
    fun unsent(): List<PendingControllerAnswer> = games.mapNotNull { (appId, known) ->
        val answer = ControllerAnswer.fromWire(known.answer)
        if (!known.sent && answer != null && known.answeredMapping.isNotEmpty()) PendingControllerAnswer(appId, known.answeredMapping, known.answeredPreset, answer) else null
    }

    /** The relay received this answer, unless the player gave another meanwhile. */
    fun sent(appId: Int, mapping: String, answer: ControllerAnswer): ControllerFeedbackBook {
        val known = record(appId)
        return if (known.answeredMapping == mapping && known.answer == answer.wire) with(appId, known.copy(sent = true)) else this
    }

    fun toJson(): String = json.encodeToString(serializer(), this)

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun fromJson(text: String?): ControllerFeedbackBook = runCatching { json.decodeFromString(serializer(), text.orEmpty()) }.getOrDefault(ControllerFeedbackBook())
    }
}
