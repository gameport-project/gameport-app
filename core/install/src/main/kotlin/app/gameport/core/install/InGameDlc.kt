package app.gameport.core.install

import app.gameport.core.steam.SteamLibraryRepository
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The extra contents a game asks to install itself (Steam's "install this DLC" call, relayed by the hook, see the `dlc` call of the provider). It does what the game's
 * settings page does: [GameInstallRepository.installDlc], and the state of the work is handed back for the game to show.
 */
@Singleton
class InGameDlc @Inject constructor(
    private val installed: InstalledGames,
    private val installer: GameInstallRepository,
    private val library: SteamLibraryRepository,
    private val events: GameEventLog,
) {
    /** What the game is told about one extra content: [phase] is QUEUED, DOWNLOADING, INSTALLED or FAILED. */
    data class Line(val id: Int, val phase: String, val fraction: Float)

    private val tried = ConcurrentHashMap<String, Long>()

    /** The extra contents of the game that have files of their own (a separate depot): only those can be "not installed"; the others are unlocks and keep being answered as before. */
    suspend fun withContent(packageName: String): Set<Int> = withTimeoutOrNull(3_000) {
        installed.all().entries.filter { it.value == packageName }.flatMap { entry ->
            library.observeGame(entry.key).first { it != null }?.androidBuild?.dlc.orEmpty().filter { it.depots.isNotEmpty() }.map { it.appId }
        }.toSet()
    }.orEmpty()

    /** Every app the account can use, for the game to be told the truth about any extra content it asks about; empty while Steam has not answered. */
    suspend fun ownedApps(): Set<Int> = withTimeoutOrNull(3_000) { library.observeLibrary().first { it.ownedAppIds.isNotEmpty() }.ownedAppIds }.orEmpty()

    suspend fun request(packageName: String, ids: List<Int>): List<Line> = ids.distinct().take(MAX_ITEMS).map { id -> line(packageName, id) }

    private suspend fun line(packageName: String, dlcId: Int): Line {
        val game = withTimeoutOrNull(5_000) {
            // Several records can point at one package (a demo and its game): the one whose build lists this extra content is the game.
            val candidates = installed.all().entries.filter { it.value == packageName }.map { it.key }
            candidates.firstNotNullOfOrNull { appId ->
                library.observeGame(appId).first { it != null }?.takeIf { game -> game.androidBuild?.dlc?.any { it.appId == dlcId } == true }
            }
        } ?: return Line(dlcId, "FAILED", 0f)
        val content = game.androidBuild?.dlc?.firstOrNull { it.appId == dlcId }
        if (content == null || !content.owned || content.depots.isEmpty()) return Line(dlcId, "FAILED", 0f)

        if (dlcId in installer.observeInstalledDlc(game.appId).first().first) return Line(dlcId, "INSTALLED", 1f)
        val work = installer.observeDlcWork(game.appId).first()
        if (work != null && work.dlcAppId == dlcId && !work.removing) {
            return if (work.error != null) Line(dlcId, "FAILED", 0f) else Line(dlcId, "DOWNLOADING", work.progress)
        }
        // Not there and nothing under way: it starts, but not again at every poll of the game after a failure.
        val key = "${game.appId}/$dlcId"
        val last = tried[key]
        if (last == null || System.currentTimeMillis() - last > RETRY_AFTER_MS) {
            tried[key] = System.currentTimeMillis()
            events.note(game.appId, "extra content $dlcId asked for by the game: installing it")
            installer.installDlc(game, dlcId)
        }
        return Line(dlcId, "QUEUED", 0f)
    }

    private companion object {
        const val MAX_ITEMS = 50
        const val RETRY_AFTER_MS = 60_000L
    }
}
