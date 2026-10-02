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

    /** The downloaded APK is being prepared: Steam shim, VR entry, signature. */
    data object Patching : InstallState

    /** The APK is handed to Android; the user may have to confirm on screen. */
    data object Installing : InstallState

    data class Installed(val packageName: String) : InstallState

    data class Failed(val error: InstallError) : InstallState
}

/** Why an install stopped; the UI turns these into messages in the user's language. */
sealed interface InstallError {
    data class NotEnoughSpace(val neededBytes: Long, val freeBytes: Long) : InstallError

    data object NotSignedIn : InstallError

    /** Steam's Android build held no APK, so nothing could be installed. */
    data object NoApk : InstallError

    data object UnreadableApk : InstallError

    /** Another version of the game, signed differently, is already installed. */
    data object VersionConflict : InstallError

    data class Other(val message: String?) : InstallError
}

/** One build of a game found in its download. [id] is the file name. */
data class VersionOption(val id: String, val versionName: String?, val versionCode: Long, val forHeadset: Boolean)
