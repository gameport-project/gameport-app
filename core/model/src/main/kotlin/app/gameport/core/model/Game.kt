package app.gameport.core.model

enum class Ownership {
    OWNED,
    FAMILY_SHARED,
}

/** One Steam depot of the Android build, with the space it takes. */
data class AndroidDepot(
    val id: Int,
    /** The DLC this depot belongs to, or null for the base game. */
    val dlcAppId: Int?,
    val installBytes: Long,
    val downloadBytes: Long,
    /** Id of the build Steam publishes for this depot; it changes when the game is updated (0 if unknown). */
    val manifestId: Long = 0,
)

/** Extra content of a game, sold separately; its depots are only fetched when chosen. */
data class DlcContent(
    val appId: Int,
    val name: String,
    /** The signed-in account (or its family) has this DLC. */
    val owned: Boolean,
    val depots: List<AndroidDepot>,
) {
    val installBytes: Long get() = depots.sumOf { it.installBytes }
    val downloadBytes: Long get() = depots.sumOf { it.downloadBytes }
}

/**
 * One place where a game keeps saves that Steam Cloud syncs. [localDir] is relative to the shared
 * storage root (for example `Android/data/<package>/files`); [cloudPrefix] is how Steam names the
 * same folder (for example `%WinAppDataLocalLow%Studio/Game`). Both may contain the tokens
 * `{64BitSteamID}` and `{Steam3AccountID}`, which stand for the signed-in account.
 */
data class SaveRule(
    val localDir: String,
    val pattern: String,
    val recursive: Boolean,
    val cloudPrefix: String,
)

data class AndroidBuild(
    val packageName: String?,
    val isVr: Boolean?,
    /** Where the game's synced saves live; empty when Steam has no Android mapping for them. */
    val saveRules: List<SaveRule> = emptyList(),
    /** Depots of the base game: always installed. */
    val baseDepots: List<AndroidDepot> = emptyList(),
    val dlc: List<DlcContent> = emptyList(),
) {
    /** Space the base game takes once installed, or null if Steam did not announce it. */
    val installBytes: Long? get() = baseDepots.sumOf { it.installBytes }.takeIf { it > 0 }

    val downloadBytes: Long? get() = baseDepots.sumOf { it.downloadBytes }.takeIf { it > 0 }

    /** The depots to fetch for the base game plus the chosen DLC (unowned DLC is ignored). */
    fun depotsFor(selectedDlc: Set<Int>): List<AndroidDepot> =
        baseDepots + dlc.filter { it.owned && it.appId in selectedDlc }.flatMap { it.depots }
}

/** What kind of Steam app it is: a full game, a demo, or a beta (a playtest or a beta app of its own). */
enum class AppKind { GAME, DEMO, BETA }

data class Game(
    val appId: Int,
    val name: String,
    val ownership: Ownership,
    val androidBuild: AndroidBuild?,
    val kind: AppKind = AppKind.GAME,
) {
    /** Portrait capsule, used in grids. */
    val capsuleUrl: String get() = SteamImages.asset(appId, "library_600x900.jpg")

    /** Wide hero artwork, used in the carousel and on the game page. */
    val heroUrl: String get() = SteamImages.asset(appId, "library_hero.jpg")

    /** Landscape header, a fallback when the other artwork is missing. */
    val headerUrl: String get() = SteamImages.asset(appId, "header.jpg")
}

/** Public artwork served by Steam's CDN. */
object SteamImages {
    private const val BASE = "https://shared.akamai.steamstatic.com/store_item_assets/steam/apps"

    fun asset(appId: Int, file: String): String = "$BASE/$appId/$file"
}

/** Snapshot of the library: [isScanning] stays true while Steam is still being queried. */
data class Library(
    val games: List<Game>,
    val isScanning: Boolean,
)
