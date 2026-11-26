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
) {
    companion object {
        /** [deviceFamily] is the VR platform of the device (`pico`, `meta`, `openxr`), null on a device with no VR. */
        fun of(mapping: ControllerMapping, deviceFamily: String?): ControllerChoiceState {
            if (deviceFamily == null) return ControllerChoiceState(choosable = false)
            if (mapping.profiles.isEmpty()) return ControllerChoiceState(choosable = false, waitingForLaunch = true)
            val kinds = mapping.profiles.map { it.kind }.distinct()
            val offered = buildList {
                add(PresentedControllers.AUTO)
                if (ControllerProfile.Kind.FRAME in kinds) add(PresentedControllers.FRAME)
                // The Touch's table is put forward only where it is not already native: on a Pico, and not when the game has a Pico profile of its own (the layer ignores it then).
                if (deviceFamily == "pico" && ControllerProfile.Kind.TOUCH in kinds && ControllerProfile.Kind.PICO !in kinds) add(PresentedControllers.TOUCH)
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
            )
        }
    }
}
