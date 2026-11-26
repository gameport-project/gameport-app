package app.gameport.feature.game

import app.gameport.core.model.ControllerMapping
import app.gameport.core.model.ControllerProfile
import app.gameport.core.model.PresentedControllers

/**
 * What the section about the controllers offered to a game shows, from what the game reported and the device. Pure, so each state is tested.
 *
 * [choosable] false is the page as it was before the game reported its profiles: the single switch for the Steam Frame's controls, and, on a device that
 * has VR, [waitingForLaunch] says the profiles come after a first start with a patch that reports them.
 */
internal data class ControllerChoiceState(
    val choosable: Boolean,
    val waitingForLaunch: Boolean = false,
    /** What the game says it handles, one kind once, in the order it reported them: shown for information. */
    val listed: List<ControllerProfile.Kind> = emptyList(),
    /** The choices, automatic first. Only the Steam Frame's and the Touch's can be chosen. */
    val offered: List<PresentedControllers> = listOf(PresentedControllers.AUTO),
    /** The game only offers one kind of controllers (or nothing else can be put forward): nothing to choose. */
    val nothingToChoose: Boolean = false,
    /** The table used at the last start: `frame`, `touch`, `native` or `none`; null when the layer did not say. */
    val used: String? = null,
    /** The choice could not be applied at the last start and automatic was used. */
    val fallback: Boolean = false,
    /** The choice has no meaning for this game on this device and automatic was used: told in grey. */
    val ignored: Boolean = false,
    /** The Steam Frame's controls are asked for but the game ran with no control for them: forcing them changes nothing. */
    val noFrameControlsWarning: Boolean = false,
    /** Which controllers are read and through which profile, from the table used at the last start; null while it is not known. Shown after "Automatic". */
    val usedTable: UsedTable? = null,
) {
    /** The kind of controllers the game read at its last start and the profile it did so through (without the common start of its path). */
    data class UsedTable(val kind: ControllerProfile.Kind)

    companion object {
        /** The table the game used at its last start, from [ControllerMapping.used] and the profiles it gave; null when the layer did not say, or when no profile matches. */
        internal fun usedTableOf(mapping: ControllerMapping, deviceFamily: String?): UsedTable? {
            val profiles = mapping.profiles
            val chosen = when (mapping.used) {
                "frame" -> profiles.firstOrNull { it.kind == ControllerProfile.Kind.FRAME }
                "touch" -> profiles.firstOrNull { it.kind == ControllerProfile.Kind.TOUCH }
                // The game's own table for this device: the profile of the device's controllers (the richest one when there are several).
                "native" -> {
                    val own = when (deviceFamily) {
                        "pico" -> setOf(ControllerProfile.Kind.PICO)
                        "meta" -> setOf(ControllerProfile.Kind.TOUCH)
                        else -> setOf(ControllerProfile.Kind.PICO, ControllerProfile.Kind.TOUCH, ControllerProfile.Kind.INDEX, ControllerProfile.Kind.VIVE, ControllerProfile.Kind.OTHER)
                    }
                    profiles.filter { it.kind in own }.maxByOrNull { it.bindings }
                }
                else -> null
            }
            return chosen?.let { UsedTable(it.kind) }
        }

        /** [deviceFamily] is the VR platform of the device (`pico`, `meta`, `openxr`), null on a device with no VR. */
        fun of(mapping: ControllerMapping, deviceFamily: String?): ControllerChoiceState {
            if (deviceFamily == null) return ControllerChoiceState(choosable = false)
            // Nothing known yet: the page waits for a start of the game. A choice kept from before (no profile known any more) still shows its card, so it can be taken back.
            if (mapping.profiles.isEmpty() && mapping.presented == PresentedControllers.AUTO) return ControllerChoiceState(choosable = false, waitingForLaunch = true)
            val kinds = mapping.profiles.map { it.kind }.distinct()
            val hasFrame = ControllerProfile.Kind.FRAME in kinds
            // A table of its own for this device: the Quest's or the device's own. Only with both it and the Steam Frame's is there something to choose between.
            val ownKinds = when (deviceFamily) {
                "pico" -> listOf(ControllerProfile.Kind.PICO, ControllerProfile.Kind.TOUCH)
                "meta" -> listOf(ControllerProfile.Kind.TOUCH)
                else -> kinds.filter { it != ControllerProfile.Kind.FRAME && it != ControllerProfile.Kind.GAZE && it != ControllerProfile.Kind.SIMPLE }
            }
            val ownKind = ownKinds.firstOrNull { it in kinds }
            val offered = buildList {
                add(PresentedControllers.AUTO)
                if (hasFrame && ownKind != null) {
                    add(PresentedControllers.FRAME)
                    // The Touch's table is put forward only where it is not already native: on a Pico, and not when the game has a Pico profile of its own (the layer ignores it then).
                    if (deviceFamily == "pico" && ControllerProfile.Kind.TOUCH in kinds && ControllerProfile.Kind.PICO !in kinds) add(PresentedControllers.TOUCH)
                }
                // A choice made before, or kept from an older version, stays visible so it can be taken back.
                if (mapping.presented !in this) add(mapping.presented)
            }
            return ControllerChoiceState(
                choosable = true,
                listed = kinds,
                offered = offered,
                nothingToChoose = mapping.presented == PresentedControllers.AUTO && (kinds.size <= 1 || offered.size == 1),
                used = mapping.used.takeIf { it.isNotEmpty() },
                fallback = mapping.presented != PresentedControllers.AUTO && mapping.presentedResult == "fallback",
                ignored = mapping.presented != PresentedControllers.AUTO && mapping.presentedResult == "ignored",
                noFrameControlsWarning = mapping.presented == PresentedControllers.FRAME && mapping.noFrameControls,
                usedTable = usedTableOf(mapping, deviceFamily),
            )
        }
    }
}
