package app.gameport.core.sync

import app.gameport.core.install.GameEventLog
import app.gameport.core.install.GameInstallRepository
import app.gameport.core.install.InstallReportStore
import app.gameport.core.model.reportable
import app.gameport.core.steam.SteamLibraryRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Saves a report of an install that failed, the moment it fails, and tells where it went. What explains a failure (the download's files, the
 * lines the patcher wrote, the system's own log) is thrown away or written over soon after, so waiting for the player to ask for a report
 * loses it. The file lands in Downloads, where it can be copied off over USB.
 *
 * Only failures worth a report are saved: a permission to give or a confirmation to answer says in its own message what to do, and saving a
 * file for each of those would fill Downloads with nothing.
 */
@Singleton
class InstallReports @Inject constructor(
    private val installer: GameInstallRepository,
    private val library: SteamLibraryRepository,
    private val reporter: ProblemReporter,
    private val events: GameEventLog,
    private val store: InstallReportStore,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun start() {
        scope.launch {
            installer.failures.collect { failure ->
                if (!failure.error.reportable) return@collect
                // The library is what holds a game's name; without it there is nothing to build a report about. It may still be
                // loading when a download that was taken up again fails early, so it is given a moment to answer.
                val game = withTimeoutOrNull(LOOKUP_MS) { library.observeGame(failure.appId).filterNotNull().first() } ?: return@collect
                val report = runCatching { reporter.save(game, installFailure = failure.error.toString()) }.getOrNull()
                if (report == null) {
                    events.note(failure.appId, "a report of the failure could not be saved")
                    return@collect
                }
                events.note(failure.appId, "a report of the failure was saved in Downloads as ${report.fileName}")
                store.put(failure.appId, report.fileName)
            }
        }
    }

    private companion object {
        /** How long the library is given to answer with the game. */
        const val LOOKUP_MS = 10_000L
    }
}
