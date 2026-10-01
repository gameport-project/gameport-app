package app.gameport.core.model

/** One save file as one side (this device or Steam Cloud) holds it. */
data class SaveFileInfo(val size: Long, val modifiedMillis: Long, val sha1: String)

/** A save file and what each side has of it; a side that lacks the file is null. */
data class SaveFileState(val path: String, val local: SaveFileInfo?, val cloud: SaveFileInfo?) {
    val name: String get() = path.substringAfterLast('/')

    val identical: Boolean get() = local != null && cloud != null && local.sha1 == cloud.sha1
}

/** What the player asked for at the next launch of the game, once the games sync runs. */
enum class SaveDirection { RESTORE_FROM_CLOUD, SEND_TO_CLOUD }

/**
 * The state of a game's saves as GamePort last saw them: on this device (as of the last launch of
 * the game, the only moment GamePort can look inside the game's folders) and in Steam Cloud.
 */
data class SaveOverview(
    val files: List<SaveFileState>,
    /** When GamePort last saw the game's folder, or 0 if it never did. */
    val localSeenMillis: Long,
    /** When GamePort last looked at Steam Cloud, or 0 if it never did. */
    val cloudSeenMillis: Long,
    val pending: SaveDirection?,
) {
    val hasLocal: Boolean get() = files.any { it.local != null }
    val hasCloud: Boolean get() = files.any { it.cloud != null }
    val inSync: Boolean get() = files.isNotEmpty() && files.all { it.identical }
}
