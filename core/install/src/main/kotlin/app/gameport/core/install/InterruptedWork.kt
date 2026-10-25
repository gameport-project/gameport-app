package app.gameport.core.install

import app.gameport.core.model.InstallState

/**
 * What to do with the files a stopped download left behind, and whether an installed game's update is going on. An update cut short (GamePort closed, the
 * system stopped it) goes on by itself like any other download; one the player paused, or that failed for a reason of its own, does not.
 */
internal object InterruptedWork {
    enum class Resume { NO, DOWNLOAD, UPDATE }

    /** [hasUpdateMarker]: the download folder carries the mark an update leaves while it runs. */
    fun resume(installed: Boolean, hasUpdateMarker: Boolean, paused: Boolean, failed: Boolean, running: Boolean): Resume = when {
        paused || failed || running -> Resume.NO
        // What is left next to an installed game is something else, unless it is the update that was running.
        installed -> if (hasUpdateMarker) Resume.UPDATE else Resume.NO
        else -> Resume.DOWNLOAD
    }

    /** The update of an installed game is going on (or about to go on again): its cover shows a spinner, not the "update available" mark. */
    fun updating(operation: InstallState?, hasUpdateMarker: Boolean, paused: Boolean, failed: Boolean): Boolean {
        if (!hasUpdateMarker) return false
        if (operation != null) return operation !is InstallState.Failed && operation !is InstallState.Installed && operation !is InstallState.NotInstalled
        return !paused && !failed
    }
}
