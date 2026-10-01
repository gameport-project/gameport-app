package app.gameport.core.sync

/**
 * Folders GamePort creates inside a game's data (safety copies, shim data). They are never saves,
 * and an early version of the sync mistakenly sent some of them to Steam; those are ignored and
 * removed from the cloud.
 *
 * One exception: the files a game keeps through Steam's cloud API live in the shim's `remote`
 * folder, under GamePort's own data. Those are the game's saves.
 */
internal object OwnFiles {
    private const val BACKUPS = "/gameport-backup/"
    private const val OWN = "/gameport/"
    private val SHIM_REMOTE = Regex("/gameport/Goldberg SteamEmu Saves/[^/]+/remote/")

    fun isOwn(pathOrName: String): Boolean = when {
        pathOrName.contains(BACKUPS) -> true
        SHIM_REMOTE.containsMatchIn(pathOrName) -> false
        else -> pathOrName.contains(OWN)
    }
}
