package app.gameport.core.sync

/** A save file on the headset, with its path relative to the shared storage root. */
data class LocalFile(val rel: String, val sha1: String, val size: Long, val mtime: Long)

/** A save file in Steam Cloud. [name] is the cloud name, [rel] the path it maps to on the headset. */
data class CloudFile(val name: String, val rel: String, val sha1: String, val size: Long, val timestamp: Long)

/** What both sides looked like after the last successful sync. */
data class Baseline(val changeNumber: Long, val files: Map<String, String>)

/** Which side wins when the player settled a conflict. */
enum class Side { LOCAL, CLOUD }

/** Short description of one side of a conflict, for the question shown to the player. */
data class SideSummary(val fileCount: Int, val totalBytes: Long, val newestMillis: Long)

sealed interface SyncAction {
    /** Both sides agree, or neither changed. */
    data object None : SyncAction

    /** The cloud is ahead: fetch [files]; [deleteLocal] are paths the cloud no longer has. */
    data class Download(val files: List<CloudFile>, val deleteLocal: List<String>) : SyncAction

    /** The headset is ahead: send [files]; [deleteCloud] are cloud files the headset no longer has. */
    data class Upload(val files: List<LocalFile>, val deleteCloud: List<CloudFile>) : SyncAction

    /** Both changed since the last sync: the player must choose. Nothing is touched meanwhile. */
    data class Conflict(val local: SideSummary, val cloud: SideSummary) : SyncAction
}
