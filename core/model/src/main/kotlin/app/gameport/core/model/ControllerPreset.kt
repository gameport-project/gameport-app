package app.gameport.core.model

/**
 * A ready-made way to put the Steam Frame's controls on the two or four buttons a device's controllers have, for the cases that come back from game to game:
 * a game that uses the four face buttons, or one that uses the D-pad instead. A preset only sends the controls it names; every other control keeps what it
 * has. It is applied as the player's own choice, so it can be undone and touched up by hand afterwards.
 */
enum class ControllerPreset(val key: String, val links: Map<ControlRef, List<ControlRef>>) {
    /** The four face buttons of the Steam Frame, one for one: A and B on A and B, X and Y on the left controller's X and Y. */
    FACE_BUTTONS(
        "faces",
        linkMap(
            "right:a" to "right:a",
            "right:b" to "right:b",
            "right:x" to "left:x",
            "right:y" to "left:y",
        ),
    ),

    /** The D-pad's up and down on the two buttons: up on Y, down on X. */
    DPAD_VERTICAL(
        "dpad_vertical",
        linkMap(
            "left:dpad_up" to "left:y",
            "left:dpad_down" to "left:x",
        ),
    ),

    /** The D-pad's left and right on the two buttons: left on X, right on Y. */
    DPAD_HORIZONTAL(
        "dpad_horizontal",
        linkMap(
            "left:dpad_left" to "left:x",
            "left:dpad_right" to "left:y",
        ),
    ),
    ;

    /** What this preset sends, for the controls of it the game uses ([used]): nothing for the ones it does not. */
    fun changesFor(used: Collection<ControlRef>): Map<ControlRef, List<ControlRef>> = links.filterKeys { it in used }

    /** Whether [allocation] already sends every control of this preset that the game uses where the preset does, and the game uses at least one of them. */
    fun matches(allocation: Map<ControlRef, List<ControlRef>>): Boolean {
        val present = links.filterKeys { it in allocation }
        return present.isNotEmpty() && present.all { (source, targets) -> allocation[source] == targets }
    }

    companion object {
        /** The preset that [allocation] corresponds to, or null when none does. */
        fun matching(allocation: Map<ControlRef, List<ControlRef>>): ControllerPreset? = entries.firstOrNull { it.matches(allocation) }
    }
}

private fun linkMap(vararg pairs: Pair<String, String>): Map<ControlRef, List<ControlRef>> =
    pairs.associate { (from, to) -> checkNotNull(ControlRef.parse(from)) to listOf(checkNotNull(ControlRef.parse(to))) }
