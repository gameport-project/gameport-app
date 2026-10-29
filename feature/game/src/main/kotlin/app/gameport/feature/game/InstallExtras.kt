package app.gameport.feature.game

import app.gameport.core.model.ExtraPlanItem
import app.gameport.core.model.InstallStage
import app.gameport.core.model.InstallState

/**
 * A patch or an update of a game that is already installed goes through the same steps as an install, and shows in the same card: this says which step of an install it is at.
 * Without a figure for the download yet, it counts as at the start of it.
 */
internal fun Repatch.Running.asInstallState(): InstallState = when (stage) {
    RepatchStage.QUEUED -> InstallState.Queued
    RepatchStage.DOWNLOADING -> InstallState.Downloading(progress = fraction ?: 0f, bytesPerSecond = bytesPerSecond)
    RepatchStage.PATCHING -> InstallState.Patching
    RepatchStage.INSTALLING -> InstallState.Installing
}

/** Where one line of the card of an install stands: not started, going on, or in place. */
internal enum class Stand { WAITING, ACTIVE, DONE }

/**
 * Where each extra content chosen for an install stands. The ones in the game's own download are in place as soon as that download is over (whatever step follows it). The others are
 * fetched on their own, one after the other, once the game is installed: [extraIndex] is the place, counting from 1 among those, of the one being fetched now (what the state of the
 * install says), or null while none is; the ones before it are in place, the ones after it wait. The order of [extras] is kept.
 */
internal fun extraStands(extras: List<ExtraPlanItem>, stage: InstallStage, extraIndex: Int?): List<Pair<ExtraPlanItem, Stand>> {
    val separate = extras.filter { !it.withGame }
    return extras.map { item ->
        item to when {
            item.withGame -> if (stage == InstallStage.DOWNLOAD) Stand.WAITING else Stand.DONE
            extraIndex == null -> Stand.WAITING
            else -> {
                val place = separate.indexOf(item) + 1
                when {
                    place < extraIndex -> Stand.DONE
                    place == extraIndex -> Stand.ACTIVE
                    else -> Stand.WAITING
                }
            }
        }
    }
}
