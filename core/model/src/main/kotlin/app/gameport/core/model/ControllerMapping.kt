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
 * How a game that only knows the Steam Frame's controllers is mapped onto the controllers of the
 * device. The defaults mirror the OpenXR layer's own (keep the two in step); a player's overrides
 * replace them for one game when [enabled].
 */
object ControllerLayout {
    /** What the device's controllers offer to receive a Steam Frame control. */
    val targets: List<ControlRef> = listOf(
        ControlRef(Hand.LEFT, "trigger"), ControlRef(Hand.LEFT, "squeeze"), ControlRef(Hand.LEFT, "thumbstick"),
        ControlRef(Hand.LEFT, "x"), ControlRef(Hand.LEFT, "y"), ControlRef(Hand.LEFT, "menu"),
        ControlRef(Hand.RIGHT, "trigger"), ControlRef(Hand.RIGHT, "squeeze"), ControlRef(Hand.RIGHT, "thumbstick"),
        ControlRef(Hand.RIGHT, "a"), ControlRef(Hand.RIGHT, "b"),
    )

    /** Where the layer sends [source] with no override, or null when it has no equivalent. */
    fun defaultTarget(source: ControlRef): ControlRef? = when {
        source in targets || source.group == "thumbrest" -> source
        // The Steam Frame has its menu button on the right controller; the other side only has one on the left.
        source.hand == Hand.RIGHT && source.group == "menu" -> ControlRef(Hand.LEFT, "menu")
        // The Steam Frame's left D-pad: up goes to Y and down to X.
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
    /** Source control key to target key, or `none` for nothing. */
    val overrides: Map<String, String> = emptyMap(),
    /** The page was opened once: the notice that the mapping can be changed is not shown again. */
    val noticeSeen: Boolean = false,
) {
    /** Where [source] goes now: the player's choice when customising, else the default. */
    fun targetFor(source: ControlRef): ControlRef? {
        val chosen = if (enabled) overrides[source.key] else null
        return when (chosen) {
            null -> ControllerLayout.defaultTarget(source)
            "none" -> null
            else -> ControlRef.parse(chosen)
        }
    }
}
