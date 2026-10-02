package app.gameport.core.model

/** A published version of GamePort, with the text of its release notes when they could be read. */
data class AppRelease(
    val version: String,
    val tag: String,
    /** The whole notes file of the release; [ReleaseNotes.whatsNew] picks the part to show. */
    val notes: String? = null,
)

/** Why an update of GamePort did not go through. */
enum class AppUpdateFailure { OFFLINE, DOWNLOAD, NOT_ENOUGH_SPACE, DIFFERENT_SIGNATURE, REFUSED, OTHER }

sealed interface AppUpdateState {
    /** Nothing has been checked yet. */
    data object Idle : AppUpdateState

    data object Checking : AppUpdateState

    data class UpToDate(val checkedAtMillis: Long) : AppUpdateState

    data class Available(val release: AppRelease, val checkedAtMillis: Long) : AppUpdateState

    data class Downloading(val release: AppRelease, val progress: Float) : AppUpdateState

    /** The new version is handed to Android, which asks for confirmation on screen. */
    data class Installing(val release: AppRelease) : AppUpdateState

    data class Failed(val release: AppRelease?, val reason: AppUpdateFailure) : AppUpdateState
}

/** GamePort's version numbers: `1.2.3` is the version code 10203, so the order of the numbers is the order of the releases. */
object AppVersion {
    fun codeOf(version: String): Int? {
        val parts = version.trim().removePrefix("v").split('.')
        if (parts.size != 3) return null
        val numbers = parts.map { it.toIntOrNull() ?: return null }
        val (major, minor, patch) = numbers
        if (major < 0 || minor !in 0..99 || patch !in 0..99) return null
        return major * 10_000 + minor * 100 + patch
    }

    /** `v0.5.1` -> `0.5.1`, or null when the tag is not a version. */
    fun versionOfTag(tag: String): String? = tag.trim().removePrefix("v").takeIf { codeOf(it) != null }
}

/** Picks, in a release notes file, the short list of what is new, in the player's language. */
object ReleaseNotes {
    /**
     * The notes file has an English half and a French half, each with a "what's new" list under its first `###`
     * heading. Returns that list as bullet lines, or null when the file has none.
     */
    fun whatsNew(text: String, french: Boolean): String? {
        val halves = text.split(Regex("(?m)^## GamePort")).drop(1)
        val half = (if (french) halves.getOrNull(1) else null) ?: halves.firstOrNull() ?: return null
        val lines = half.lines()
        val start = lines.indexOfFirst { it.startsWith("### ") }
        if (start < 0) return null
        return lines.drop(start + 1)
            .takeWhile { !it.startsWith("#") && !it.startsWith("---") }
            .filter { it.trimStart().startsWith("- ") }
            .joinToString("\n") { "• " + it.trimStart().removePrefix("- ").replace("**", "") }
            .ifBlank { null }
    }
}
