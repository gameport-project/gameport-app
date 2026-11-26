package app.gameport.core.model

/**
 * The text a [ControllerMapping] is kept as, parts joined by `|`. New parts are only ever added at the end, so a record written by an older version is
 * shorter and reads with the defaults, and a part this version does not know is ignored.
 */
object ControllerMappingCodec {
    /** Marks a mapping written since the defaults changed (0.7.4); the ones before it have no mark. */
    const val VERSION = "v2"

    private val USED = setOf("frame", "touch", "native", "none")
    private val RESULTS = setOf("applied", "fallback", "ignored")
    private val PRESETS = ControllerPreset.entries.map { it.key }.toSet()

    fun encode(mapping: ControllerMapping): String = listOf(
        mapping.detected.joinToString(",") { it.key },
        if (mapping.enabled) "1" else "0",
        mapping.overrides.entries.joinToString(";") { "${it.key}=${it.value}" },
        if (mapping.noticeSeen) "1" else "0",
        mapping.source,
        VERSION,
        if (mapping.useFrame) "1" else "0",
        if (mapping.both) "1" else "0",
        if (mapping.silent) "1" else "0",
        // Parts added after the nine of 0.9.3 and before: 9 what the player presented, 10 the profiles the game proposed, 11 what was used, 12 what became of the choice.
        mapping.presented.key,
        ControllerProfile.cleanAll(mapping.profiles).joinToString(",") { "${it.path}=${it.bindings}" },
        mapping.used.takeIf { it in USED }.orEmpty(),
        mapping.presentedResult.takeIf { it in RESULTS }.orEmpty(),
        // 13 the ready-made preset the player chose (empty for automatic or a mix made by hand).
        mapping.preset.takeIf { it in PRESETS }.orEmpty(),
    ).joinToString("|")

    /** True when the record carries the current version mark: otherwise reading it freezes a customised mapping as it worked before. */
    fun isCurrent(text: String): Boolean = text.split('|').getOrNull(5) == VERSION

    /** A mapping written before the version was kept, and customised, is frozen as it worked then: the rules have changed since. */
    fun decode(text: String): ControllerMapping {
        val parts = text.split('|')
        val mapping = decodeParts(parts)
        return if (parts.getOrNull(5) == VERSION) mapping else mapping.frozenAsBefore()
    }

    private fun decodeParts(parts: List<String>): ControllerMapping = ControllerMapping(
        detected = parts.getOrNull(0).orEmpty().split(',').mapNotNull(ControlRef::parse),
        enabled = parts.getOrNull(1) == "1",
        overrides = parts.getOrNull(2).orEmpty().split(';').mapNotNull { entry ->
            entry.split('=', limit = 2).takeIf { it.size == 2 && it[0].isNotEmpty() }?.let { it[0] to it[1] }
        }.toMap(),
        noticeSeen = parts.getOrNull(3) == "1",
        source = parts.getOrNull(4).orEmpty(),
        both = parts.getOrNull(7) == "1",
        silent = parts.getOrNull(8) == "1",
        // A record from before the choice has no ninth part: what it kept of the Steam Frame's controls (the sixth) becomes the choice.
        presented = parts.getOrNull(9)?.let(PresentedControllers::of)
            ?: if (parts.getOrNull(6) == "1") PresentedControllers.FRAME else PresentedControllers.AUTO,
        profiles = ControllerProfile.cleanAll(parts.getOrNull(10).orEmpty().split(',').mapNotNull { entry ->
            entry.split('=', limit = 2).takeIf { it.size == 2 }?.let { (path, count) -> count.toIntOrNull()?.let { ControllerProfile.clean(path, it) } }
        }),
        used = parts.getOrNull(11).orEmpty().takeIf { it in USED }.orEmpty(),
        presentedResult = parts.getOrNull(12).orEmpty().takeIf { it in RESULTS }.orEmpty(),
        preset = parts.getOrNull(13).orEmpty().takeIf { it in PRESETS }.orEmpty(),
    )
}
