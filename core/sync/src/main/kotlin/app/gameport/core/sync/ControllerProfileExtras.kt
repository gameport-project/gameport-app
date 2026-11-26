package app.gameport.core.sync

import app.gameport.core.model.ControllerProfile
import app.gameport.core.model.PresentedControllers

/**
 * The optional extras a game's hook adds to the controller report, read apart from the ones that always came: the profiles the game proposed, which
 * table was used and what became of the player's choice. A hook that does not send them, or sends them badly, changes nothing.
 */
data class ControllerProfileExtras(
    val profiles: List<ControllerProfile>? = null,
    /** What the layer says it was asked: `auto`, `frame` or `touch`, or null. */
    val present: String? = null,
    /** What became of the choice: `applied`, `fallback` or `ignored`, or null. */
    val presented: String? = null,
    /** The table that was translated or used: `frame`, `touch`, `native` or `none`, or null. */
    val used: String? = null,
) {
    companion object {
        private val PRESENT = setOf("auto", "frame", "touch")
        private val PRESENTED = setOf("applied", "fallback", "ignored")
        private val USED = setOf("frame", "touch", "native", "none")

        /** From the raw values of the call: entries that are malformed are dropped, an unknown word is null, the list is capped. */
        fun of(profiles: Array<String>?, present: String?, presented: String?, used: String?) = ControllerProfileExtras(
            profiles = profiles?.mapNotNull(ControllerProfile::parseEntry)?.let(ControllerProfile::cleanAll)?.takeIf { it.isNotEmpty() },
            present = present?.trim()?.takeIf { it in PRESENT },
            presented = presented?.trim()?.takeIf { it in PRESENTED },
            used = used?.trim()?.takeIf { it in USED },
        )

        fun read(extras: android.os.Bundle) = of(extras.getStringArray("profiles"), extras.getString("present"), extras.getString("presented"), extras.getString("used"))
    }
}

/** What the hook is given in the config about the controllers to present: the choice, and the older boolean for hooks that only know that one. No game is automatic. */
data class ControllerPresentation(val present: String, val preferFrame: Boolean) {
    companion object {
        fun of(presented: PresentedControllers?): ControllerPresentation {
            val choice = presented ?: PresentedControllers.AUTO
            return ControllerPresentation(choice.key, choice == PresentedControllers.FRAME)
        }
    }
}
