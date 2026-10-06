package app.gameport.core.model

sealed interface InstallState {
    data object NotInstalled : InstallState

    /** Waiting for a free download slot behind other games. */
    data object Queued : InstallState

    /**
     * [progress] is 0..1 of the download. [verifying] is true while it moves on by checking files already on disk
     * (a resumed download) rather than by fetching anything.
     */
    data class Downloading(val progress: Float, val bytesPerSecond: Long = 0L, val verifying: Boolean = false) : InstallState

    /** A download was started earlier and left files behind; it can be resumed or discarded. */
    data object Interrupted : InstallState

    /** The download holds several builds of the game and none fits this device clearly: the player picks one. */
    data class ChoosingVersion(val options: List<VersionOption>) : InstallState

    /**
     * A copy of the game that GamePort did not install (the Meta store's, for instance) is already on the device. It is signed with another
     * key, so the two cannot be installed together: the player chooses between keeping it and replacing it with this one.
     */
    /** [otherGamePort] is the package name of another GamePort that patched the copy on the device, or null when it comes from elsewhere. */
    data class ChoosingDuplicate(val packageName: String, val otherGamePort: String? = null) : InstallState

    /** The downloaded APK is being prepared: Steam shim, VR entry, signature. */
    data object Patching : InstallState

    /** The APK is handed to Android; the user may have to confirm on screen. */
    data object Installing : InstallState

    /** Android installed the game; GamePort now puts its expansion files in place and registers it. The game is not ready to play yet. */
    data object Finishing : InstallState

    /** [otherGamePort]: the package name of another GamePort on the device that patched the game, which then is the one that starts and updates it. */
    data class Installed(val packageName: String, val otherGamePort: String? = null) : InstallState

    data class Failed(val error: InstallError) : InstallState
}

/** Why an install stopped; the UI turns these into messages in the user's language. */
sealed interface InstallError {
    data class NotEnoughSpace(val neededBytes: Long, val freeBytes: Long) : InstallError

    data object NotSignedIn : InstallError

    /** Offline mode, or Steam cannot be reached: nothing can be downloaded. */
    data object Offline : InstallError

    /** GamePort is replacing itself: nothing else is started until it is done. */
    data object AppUpdating : InstallError

    /** Steam's Android build held no APK, so nothing could be installed. */
    data object NoApk : InstallError

    data object UnreadableApk : InstallError

    /** Another version of the game, signed differently, is already installed. */
    data object VersionConflict : InstallError

    data class Other(val message: String?) : InstallError
}

/** One build of a game found in its download. [id] is the file name. */
data class VersionOption(val id: String, val versionName: String?, val versionCode: Long, val forHeadset: Boolean)

/**
 * Whether a failed install is worth a problem report. Not for what the player can put right at once: not enough
 * space (the message says how much), no Steam sign-in, no connection, or GamePort updating itself.
 */
val InstallError.reportable: Boolean
    get() = this !is InstallError.NotEnoughSpace && this !is InstallError.NotSignedIn && this !is InstallError.Offline && this !is InstallError.AppUpdating

/**
 * True while a game is on its way to being installed: waiting its turn, downloading, patching, installing, or waiting for the player's
 * choice. Not for an install that was cut short, that failed, or that is done.
 */
val InstallState.inProgress: Boolean
    get() = this is InstallState.Queued || this is InstallState.Downloading || this is InstallState.Patching || this is InstallState.Installing || this is InstallState.Finishing ||
        this is InstallState.ChoosingVersion || this is InstallState.ChoosingDuplicate

/** Where "patch all" stands: [done] of [total] games handled, [current] being patched, and the games that could not be patched. */
data class PatchAllState(
    val total: Int,
    val done: Int,
    val current: Int?,
    val failed: List<Int>,
    val finished: Boolean,
    /** Every game of the run, in the order they are patched. */
    val ids: List<Int> = emptyList(),
)

/** How many installed games were patched by an older patcher, and where "patch all" stands, for the buttons that start it. */
data class PatchAllInfo(val behind: Int, val progress: PatchAllState?) {
    val running: Boolean get() = progress != null && !progress.finished
}

/** The steps a game goes through to be installed, in order; the same on the game's page and on the downloads page. */
enum class InstallStage { DOWNLOAD, PATCH, INSTALL, FINISH }

/** The step this state is in, or null when it is not one of them (waiting for a choice, interrupted, failed, installed). */
val InstallState.stage: InstallStage?
    get() = when (this) {
        InstallState.Queued, is InstallState.Downloading -> InstallStage.DOWNLOAD
        InstallState.Patching -> InstallStage.PATCH
        InstallState.Installing -> InstallStage.INSTALL
        InstallState.Finishing -> InstallStage.FINISH
        else -> null
    }
