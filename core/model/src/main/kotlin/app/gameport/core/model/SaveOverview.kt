package app.gameport.core.model

/** One save file as one side (this device or Steam Cloud) holds it. */
data class SaveFileInfo(val size: Long, val modifiedMillis: Long, val sha1: String)

/** A save file and what each side has of it; a side that lacks the file is null. */
data class SaveFileState(val path: String, val local: SaveFileInfo?, val cloud: SaveFileInfo?) {
    val name: String get() = path.substringAfterLast('/')

    val identical: Boolean get() = local != null && cloud != null && local.sha1 == cloud.sha1
}

/**
 * The Windows copy of a game's saves, which Steam keeps apart from the Android one for some games. [files] set this device's files beside
 * the Windows ones (the "cloud" side of each is the Windows file). [usePc]: each start, they feed this device. [sendPc]: this device also
 * writes to them. [backupMillis] is when the clean copy of the Windows saves was made (0 when none), [backupFiles] how many files it holds.
 */
data class PcSaves(
    val files: List<SaveFileState>,
    val usePc: Boolean,
    val sendPc: Boolean,
    val backupMillis: Long,
    val backupFiles: Int,
) {
    val hasFiles: Boolean get() = files.any { it.cloud != null }
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
    /** The Windows copy of the saves, when the studio keeps it apart from the Android one; null when there is none to offer. */
    val pc: PcSaves? = null,
) {
    val hasLocal: Boolean get() = files.any { it.local != null }
    val hasCloud: Boolean get() = files.any { it.cloud != null }
    val inSync: Boolean get() = files.isNotEmpty() && files.all { it.identical }
}
