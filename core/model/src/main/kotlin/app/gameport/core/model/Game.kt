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
    /**
     * The Steam app whose record declares this depot, when it is not the game itself: a game that sells its content as DLC leaves each DLC app to declare its own depots, and
     * Steam gives the key of a depot, and its files, only to the app that declares it. Null when the game declares it.
     */
    val declaredBy: Int? = null,
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
 * A [localDir] starting with `@internal/` is in the private data of the game (see the hook), not in the shared storage.
 * [pcCloudPrefix] is how Steam names the Windows copy of the same saves, when the studio keeps one apart from the Android one (no override links them).
 */
data class SaveRule(
    val localDir: String,
    val pattern: String,
    val recursive: Boolean,
    val cloudPrefix: String,
    val pcCloudPrefix: String? = null,
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

/**
 * The artwork file names Steam publishes for an app, relative to its folder on the CDN (`<hash>/library_capsule.jpg`).
 * Recent apps, demos and playtests among them, only have their artwork under such a hashed name.
 */
data class Artwork(val capsule: String? = null, val hero: String? = null, val header: String? = null)

data class Game(
    val appId: Int,
    val name: String,
    val ownership: Ownership,
    val androidBuild: AndroidBuild?,
    val kind: AppKind = AppKind.GAME,
    val artwork: Artwork = Artwork(),
    /** For a demo or a playtest, the full game: its artwork stands in when the app has none of its own. */
    val parentAppId: Int? = null,
    /** Steam files the game as adult content: nudity, sexual content or an age of 18 or more. Hidden from anyone who is not an adult (see [AgePolicy]). */
    val isAdult: Boolean = false,
) {
    /** Portrait capsule, used in grids. */
    val capsuleUrl: String get() = artwork.capsule?.let { SteamImages.asset(appId, it) } ?: SteamImages.asset(appId, "library_600x900.jpg")

    /** Wide hero artwork, used in the carousel and on the game page. */
    val heroUrl: String get() = artwork.hero?.let { SteamImages.asset(appId, it) } ?: SteamImages.asset(appId, "library_hero.jpg")

    /** Landscape header, a fallback when the other artwork is missing. */
    val headerUrl: String get() = artwork.header?.let { SteamImages.asset(appId, it) } ?: SteamImages.asset(appId, "header.jpg")

    /** The cover, with everything to try after it. The one way a cover is shown (see [Art]). */
    val capsuleArt: Art get() = Art(capsuleUrl, capsuleFallbacks)

    /** The wide hero picture, with everything to try after it. */
    val heroArt: Art get() = Art(heroUrl, heroFallbacks)

    /** What to try after [capsuleUrl], in order: the old file name, the header, then the full game's artwork. */
    val capsuleFallbacks: List<String>
        get() = listOf(SteamImages.asset(appId, "library_600x900.jpg"), headerUrl, SteamImages.asset(appId, "header.jpg")) + parentArtwork("library_600x900.jpg")

    /** What to try after [heroUrl], in order. */
    val heroFallbacks: List<String>
        get() = listOf(SteamImages.asset(appId, "library_hero.jpg"), headerUrl, SteamImages.asset(appId, "header.jpg")) + parentArtwork("library_hero.jpg")

    private fun parentArtwork(file: String): List<String> =
        parentAppId?.let { listOf(SteamImages.asset(it, file), SteamImages.asset(it, "header.jpg")) }.orEmpty()
}

/** Public artwork served by Steam's CDN. */
object SteamImages {
    private const val BASE = "https://shared.akamai.steamstatic.com/store_item_assets/steam/apps"

    fun asset(appId: Int, file: String): String = "$BASE/$appId/$file"

    /** A path under the folder of the apps, for the pictures named by something else than an app (the thumbnail of a trailer). */
    fun path(path: String): String = "$BASE/$path"
}

/** Snapshot of the library: [isScanning] stays true while Steam is still being queried. */
data class Library(
    val games: List<Game>,
    val isScanning: Boolean,
)
