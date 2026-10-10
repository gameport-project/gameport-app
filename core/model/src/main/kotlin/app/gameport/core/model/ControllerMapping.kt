package app.gameport.core.model

enum class Hand(val key: String) {
    LEFT("left"),
    RIGHT("right"),
    ;

    companion object {
        fun of(key: String): Hand? = entries.firstOrNull { it.key == key }
    }
}

/** A control of a controller, by hand and group: the joystick, the trigger, a button. */
data class ControlRef(val hand: Hand, val group: String) {
    /** The form the OpenXR layer reads: `left:thumbstick`. */
    val key: String get() = "${hand.key}:$group"

    companion object {
        fun parse(key: String): ControlRef? {
            val (handKey, group) = key.split(':', limit = 2).takeIf { it.size == 2 } ?: return null
            return Hand.of(handKey)?.let { ControlRef(it, group) }
        }
    }
}

/**
 * How a game that only knows the Steam Frame's controllers is mapped onto the controllers of the device. The Steam Frame has far more controls
 * than the device (a D-pad, X and Y on the right, bumpers, a view button), so several must share one, or go without: what the game uses decides,
 * only the controls it binds compete. The rules mirror the OpenXR layer's own (`xrlayer/controller_allocation.h`, with the same cases tested
 * there): keep the two in step. A player's choices replace them for one game when [ControllerMapping.enabled].
 */
object ControllerLayout {
    /** What the device's controllers offer to receive a Steam Frame control. */
    val targets: List<ControlRef> = listOf(
        ControlRef(Hand.LEFT, "trigger"), ControlRef(Hand.LEFT, "squeeze"), ControlRef(Hand.LEFT, "thumbstick"),
        ControlRef(Hand.LEFT, "x"), ControlRef(Hand.LEFT, "y"), ControlRef(Hand.LEFT, "menu"),
        ControlRef(Hand.RIGHT, "trigger"), ControlRef(Hand.RIGHT, "squeeze"), ControlRef(Hand.RIGHT, "thumbstick"),
        ControlRef(Hand.RIGHT, "a"), ControlRef(Hand.RIGHT, "b"),
    )

    private val deviceControls: Set<ControlRef> = targets.toSet() + ControlRef(Hand.LEFT, "thumbrest") + ControlRef(Hand.RIGHT, "thumbrest")

    private fun ref(key: String): ControlRef = checkNotNull(ControlRef.parse(key)) { key }

    /** The controls a Steam Frame control falls back on, in order of preference, when the device has no control of that name. */
    private val fallbacks: Map<ControlRef, List<ControlRef>> = mapOf(
        "right:x" to listOf("left:x", "left:y"),
        "right:y" to listOf("left:y", "left:x"),
        "right:menu" to listOf("left:menu"),
        // Valve relates the Touch's top button to the Frame's left, up and right and its bottom one to down; the other way round, by what the game uses.
        "left:dpad_up" to listOf("left:y", "left:x"),
        "left:dpad_down" to listOf("left:x", "left:y"),
        "left:dpad_left" to listOf("left:x", "left:y"),
        "left:dpad_right" to listOf("left:y", "left:x"),
        "left:bumper" to listOf("left:squeeze"),
        "right:bumper" to listOf("right:squeeze"),
    ).entries.associate { (source, list) -> ref(source) to list.map(::ref) }

    /** The order in which the fallbacks are given out: the face buttons first, then the D-pad, the bumpers and the rest. */
    private val fallbackOrder: List<ControlRef> = listOf(
        "right:x", "right:y", "left:dpad_up", "left:dpad_down", "left:dpad_left", "left:dpad_right", "right:menu", "left:bumper", "right:bumper",
    ).map(::ref)

    /**
     * For each control in [used] (the controls the game binds), where it goes. A control the player chose for ([overrides]) goes there. Else it keeps
     * its name when the device has it; else it takes the first of its fallbacks that no other control has taken, and when they are all taken it shares
     * the first one. A control with no fallback (view, the extra ones) goes nowhere.
     */
    fun allocate(used: Set<ControlRef>, overrides: Map<ControlRef, List<ControlRef>> = emptyMap()): Map<ControlRef, List<ControlRef>> {
        val result = LinkedHashMap<ControlRef, List<ControlRef>>()
        val taken = mutableSetOf<ControlRef>()
        for (source in used) overrides[source]?.let { result[source] = it; taken += it }
        for (source in used) if (source !in result && source in deviceControls) { result[source] = listOf(source); taken += source }
        for (source in fallbackOrder) {
            if (source !in used || source in result) continue
            val candidates = fallbacks.getValue(source)
            val free = candidates.firstOrNull { it !in taken }
            if (free != null) { result[source] = listOf(free); taken += free } else result[source] = listOf(candidates.first())
        }
        for (source in used) result.getOrPut(source) { emptyList() }
        return result
    }

    /** The device controls that receive more than one control of the game: pressing one fires every action bound to the others too. */
    fun shared(allocation: Map<ControlRef, List<ControlRef>>): Map<ControlRef, List<ControlRef>> =
        allocation.entries.flatMap { (source, targets) -> targets.map { it to source } }
            .groupBy({ it.first }, { it.second })
            .filterValues { it.size >= 2 }

    /** The controls of a player's choice, written `left:y+left:x`, or `none` for nowhere. */
    fun parseTargets(value: String): List<ControlRef> = if (value == "none") emptyList() else value.split('+').mapNotNull(ControlRef::parse).distinct()

    fun encodeTargets(targets: List<ControlRef>): String = if (targets.isEmpty()) "none" else targets.joinToString("+") { it.key }

    /**
     * Where the layer sent a control before the rules above were made (GamePort 0.7.3 and earlier): the controls the device has kept their name, the right
     * menu went to the left one, the D-pad's up and down to Y and X, and the rest nowhere. A mapping the player customised keeps working this way.
     */
    fun legacyTarget(source: ControlRef): ControlRef? = when {
        source in targets || source.group == "thumbrest" -> source
        source.hand == Hand.RIGHT && source.group == "menu" -> ControlRef(Hand.LEFT, "menu")
        source == ControlRef(Hand.LEFT, "dpad_up") -> ControlRef(Hand.LEFT, "y")
        source == ControlRef(Hand.LEFT, "dpad_down") -> ControlRef(Hand.LEFT, "x")
        else -> null
    }

    /** The `left:x=right:a;…` text the layer reads; a null target is written `none`. */
    fun encode(overrides: Map<String, String?>): String = overrides.entries.joinToString(";") { (source, target) -> "$source=${target ?: "none"}" }
}

/** What GamePort knows about the controllers of one game and what the player chose. */
data class ControllerMapping(
    /** The controls the game was seen using; empty until it ran once with the patch. */
    val detected: List<ControlRef> = emptyList(),
    /** Whose controllers those controls belong to: `valve` (Steam Frame) or `touch` (Meta Quest). */
    val source: String = "",
    /** Off by default: the layer's defaults apply. */
    val enabled: Boolean = false,
    /** Source control key to the target keys joined by `+`, or `none` for nothing. */
    val overrides: Map<String, String> = emptyMap(),
    /** The page was opened once: the notice that the mapping can be changed is not shown again. */
    val noticeSeen: Boolean = false,
    /** The game has controls for the device's controllers and for the Steam Frame's: by default it uses its own. */
    val both: Boolean = false,
    /** The player chose the Steam Frame's controls for a game that also has its own. Off by default. */
    val useFrame: Boolean = false,
    /** The last run asked for the game's actions for a minute and none was ever active: the game did not hear the controllers. */
    val silent: Boolean = false,
) {
    private fun withChoicesFor(fill: (ControlRef) -> List<ControlRef>): ControllerMapping =
        copy(overrides = overrides + detected.filter { it.key !in overrides }.associate { it.key to ControllerLayout.encodeTargets(fill(it)) })

    /**
     * A mapping the player customised, with a choice written for every control they did not choose, as the rules worked before they changed. What the player
     * chose is kept; what they left alone keeps doing what it did, whatever the defaults become. A mapping that is not customised follows the defaults.
     */
    fun frozenAsBefore(): ControllerMapping = if (!enabled) this else withChoicesFor { ControllerLayout.legacyTarget(it)?.let(::listOf).orEmpty() }

    /** The mapping the game has now, written as the player's own: customising starts from what the game does, and stays so when the defaults change. */
    fun frozenAsNow(): ControllerMapping = withChoicesFor { allocation()[it].orEmpty() }

    /** Where every control the game uses goes now: the player's choices when customising, else the defaults. */
    fun allocation(): Map<ControlRef, List<ControlRef>> {
        val chosen = if (enabled) overrides.mapNotNull { (key, value) -> ControlRef.parse(key)?.let { it to ControllerLayout.parseTargets(value) } }.toMap() else emptyMap()
        return ControllerLayout.allocate(detected.toSet(), chosen)
    }

    /** The Steam Frame's controls are put on this device's controllers because the player forced it. */
    val forced: Boolean get() = useFrame && !noFrameControls

    /** The game ran and has no controls for the Steam Frame's controllers at all: forcing them changes nothing. */
    val noFrameControls: Boolean get() = source == "none"

    /** The game only has the Steam Frame's controls, and GamePort puts them on this device's controllers by itself. */
    val automatic: Boolean get() = !useFrame && !both && source == "valve" && detected.isNotEmpty()

    /** The Steam Frame's controls are on this device's controllers, by themselves or by the player's choice: they can be remapped. */
    val translated: Boolean get() = forced || automatic

    /** Where [source] goes now. */
    fun targetsFor(source: ControlRef): List<ControlRef> = allocation()[source].orEmpty()

    /** The device controls that now take more than one control of the game. */
    fun sharedTargets(): Map<ControlRef, List<ControlRef>> = ControllerLayout.shared(allocation())
}
