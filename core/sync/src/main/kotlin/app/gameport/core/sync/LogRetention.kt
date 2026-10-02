package app.gameport.core.sync

import app.gameport.core.install.GameEventLog
import app.gameport.core.install.GameInstallRepository
import app.gameport.core.install.InstalledGames
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps what GamePort holds for problem reports from piling up: files older than a week are deleted when the app
 * starts, and a game's files go when it is uninstalled. Reports already saved in Downloads belong to the player and
 * are never touched.
 */
@Singleton
class LogRetention @Inject constructor(
    private val reports: ReportStore,
    private val events: GameEventLog,
    private val installer: GameInstallRepository,
    private val installed: InstalledGames,
) {
    /** Cleans once, then follows uninstalls for as long as it runs. */
    suspend fun run() {
        val cutoff = System.currentTimeMillis() - RETENTION_MS
        events.purgeOlderThan(cutoff)
        reports.purge(cutoff, installed.all().values.toSet())
        installer.uninstalled.collect { reports.forget(it) }
    }

    private companion object {
        const val RETENTION_MS = 7L * 24 * 60 * 60 * 1000
    }
}
