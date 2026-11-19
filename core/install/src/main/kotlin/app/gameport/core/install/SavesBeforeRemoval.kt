package app.gameport.core.install

/**
 * Sends the saves of a game to Steam before its copy on the device is removed (when the game is installed again under another name): what the game kept in its own folder
 * goes with it. Provided by the part of GamePort that talks to Steam Cloud.
 */
interface SavesBeforeRemoval {
    /** Asks the game's copy installed as [packageName] to send its saves now, and waits a short while for it. Never fails: a game that cannot answer is left as it is. */
    suspend fun sendBeforeRemoval(packageName: String)
}
