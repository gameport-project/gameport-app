package app.gameport.core.steam.session

import app.gameport.core.model.Artwork
import kotlinx.coroutines.flow.flow
import android.util.Log
import app.gameport.core.model.AndroidBuild
import app.gameport.core.model.AppKind
import app.gameport.core.model.AndroidDepot
import app.gameport.core.model.DlcContent
import app.gameport.core.model.SaveRule
import app.gameport.core.model.Game
import app.gameport.core.model.Library
import app.gameport.core.model.Ownership
import app.gameport.core.steam.cache.CACHE_VERSION
import app.gameport.core.steam.cache.CachedDepot
import app.gameport.core.steam.cache.CachedGame
import app.gameport.core.steam.cache.CachedLibrary
import app.gameport.core.steam.cache.LibraryCacheStore
import `in`.dragonbra.javasteam.steam.handlers.steamapps.PICSRequest
import `in`.dragonbra.javasteam.types.KeyValue
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.future.await
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit

/**
 * Finds the games of the signed-in account that ship a native Android build (a depot tagged
 * `oslist=android`, the Steam Frame / Lepton builds), following Steam's PICS chain:
 * licenses -> packages -> app ids -> app info.
 *
 * Speed comes from three things: a disk cache (known packages and inspected apps are not asked
 * again, and the cached list is shown at once), batched requests, and chunks running in parallel.
 */
internal fun SteamSession.androidGames(
    cacheStore: LibraryCacheStore,
    now: () -> Long = System::currentTimeMillis,
): Flow<Library> = channelFlow {
    val startedAt = now()
    val licenses = licenses.filterNotNull().first()
    Log.d(TAG, "licenses: ${licenses.size} after ${now() - startedAt} ms")
    val me = accountId
    val cached = cacheStore.load(me)
    val cacheIsFresh = cached != null && now() - cached.savedAt < CACHE_TTL_MS

    val packageApps = HashMap<Int, List<Int>>(cached?.packageApps.orEmpty())
    val checkedApps = HashSet<Int>(if (cacheIsFresh) cached!!.checkedApps else emptySet())
    val games = HashMap<Int, CachedGame>()
    cached?.games?.forEach { games[it.appId] = it }
    val dlcNames = HashMap<Int, String>(cached?.dlcNames.orEmpty())

    val ownedPackages = licenses.filter { it.ownerAccountID.toLong() == me }.map { it.packageID }.toSet()
    val packageIds = licenses.map { it.packageID }.toSet()
    val lock = Mutex()

    // Rebuilt from the licenses on every emission, so a lost family share disappears at once.
    fun snapshot(scanning: Boolean): Library {
        val owned = HashSet<Int>()
        val visible = HashSet<Int>()
        for (pkg in packageIds) {
            val apps = packageApps[pkg] ?: continue
            visible += apps
            if (pkg in ownedPackages) owned += apps
        }
        return libraryOf(games.values.filter { it.appId in visible }, owned, visible, dlcNames, scanning)
    }

    fun persist() = cacheStore.save(
        me,
        CachedLibrary(CACHE_VERSION, now(), packageApps.toMap(), checkedApps.toSet(), games.values.toList(), dlcNames.toMap()),
    )

    val unknownPackages = packageIds.filter { it !in packageApps }
    if (cached != null) send(snapshot(scanning = unknownPackages.isNotEmpty() || !cacheIsFresh))

    val semaphore = Semaphore(PARALLEL_REQUESTS)

    // 1. Packages we have not seen: which apps do they grant?
    val requests = licenses.distinctBy { it.packageID }.filter { it.packageID in unknownPackages }
        .map { PICSRequest(it.packageID, it.accessToken) }
    requests.chunked(PICS_CHUNK).map { chunk ->
        async {
            semaphore.withPermit {
                val result = apps.picsGetProductInfo(emptyList(), chunk).await()
                lock.withLock {
                    for (callback in result.results) {
                        for (pkg in callback.packages.values) {
                            packageApps[pkg.id] = pkg.keyValues["appids"].children.map { it.asInteger() }
                        }
                    }
                }
            }
        }
    }.awaitAll()

    Log.d(TAG, "packages resolved: ${requests.size} asked, ${packageApps.size} known, at ${now() - startedAt} ms")

    // 2. Apps we have not inspected yet: is there an Android depot?
    val toInspect = packageApps.entries.filter { it.key in packageIds }.flatMap { it.value }.distinct()
        .filter { it !in checkedApps }
    toInspect.chunked(PICS_CHUNK).map { chunk ->
        async {
            semaphore.withPermit {
                val tokens = apps.picsGetAccessTokens(chunk, emptyList()).await().appTokens
                val chunkStart = now()
                val result = apps.picsGetProductInfo(chunk.map { PICSRequest(it, tokens[it] ?: 0L) }, emptyList()).await()
                Log.d(TAG, "app chunk of ${chunk.size}: ${now() - chunkStart} ms, ${result.results.sumOf { it.apps.size }} apps, ${result.results.sumOf { it.unknownApps.size }} unknown")
                lock.withLock {
                    for (callback in result.results) {
                        for (app in callback.apps.values) {
                            checkedApps += app.id
                            app.keyValues.toAndroidGame(app.id)?.let { games[app.id] = it }
                        }
                    }
                    send(snapshot(scanning = true))
                }
            }
        }
    }.awaitAll()

    // 3. Names of the DLC that games' depots belong to, so they can be listed.
    val unnamedDlc = games.values.flatMap { g -> g.depots.mapNotNull { it.dlcAppId } }.distinct().filter { it !in dlcNames }
    unnamedDlc.chunked(PICS_CHUNK).forEach { chunk ->
        val tokens = apps.picsGetAccessTokens(chunk, emptyList()).await().appTokens
        val result = apps.picsGetProductInfo(chunk.map { PICSRequest(it, tokens[it] ?: 0L) }, emptyList()).await()
        for (callback in result.results) {
            for (app in callback.apps.values) {
                app.keyValues["common"]["name"].value?.let { dlcNames[app.id] = it }
            }
        }
    }

    Log.d(TAG, "apps inspected: ${toInspect.size}, android games: ${games.size}, done at ${now() - startedAt} ms")
    lock.withLock {
        persist()
        send(snapshot(scanning = false))
    }
    awaitClose()
}

/** The library as the screens see it, from the cached games. [owned] and [visible] are app ids. */
internal fun libraryOf(shown: Collection<CachedGame>, owned: Set<Int>, visible: Set<Int>, dlcNames: Map<Int, String>, scanning: Boolean): Library {
    val list = shown.map { game ->
        val depots = game.depots.map { AndroidDepot(it.id, it.dlcAppId, it.installBytes, it.downloadBytes, it.manifestId) }
        Game(
            appId = game.appId,
            name = game.name,
            ownership = if (game.appId in owned) Ownership.OWNED else Ownership.FAMILY_SHARED,
            kind = when (game.kind) {
                "demo" -> AppKind.DEMO
                "beta" -> AppKind.BETA
                else -> AppKind.GAME
            },
            artwork = Artwork(game.capsule, game.hero, game.header),
            parentAppId = game.parent,
            androidBuild = AndroidBuild(
                packageName = null,
                isVr = game.isVr,
                baseDepots = depots.filter { it.dlcAppId == null },
                saveRules = game.saveRules.map { SaveRule(it.localDir, it.pattern, it.recursive, it.cloudPrefix) },
                dlc = depots.filter { it.dlcAppId != null }.groupBy { it.dlcAppId!! }.map { (dlcAppId, dlcDepots) ->
                    DlcContent(dlcAppId, dlcNames[dlcAppId] ?: "DLC $dlcAppId", owned = dlcAppId in visible, depots = dlcDepots)
                }.sortedBy { it.name.lowercase() },
            ),
        )
    }.sortedBy { it.name.lowercase() }
    return Library(list, scanning)
}

/** The library from the last scan, for when Steam is not reachable: every cached game counts as owned. */
internal fun offlineLibrary(cacheStore: LibraryCacheStore, accountId: Long): Flow<Library> = flow {
    val cached = cacheStore.load(accountId)
    val everything = cached?.games.orEmpty()
    val ids = everything.map { it.appId }.toSet()
    emit(libraryOf(everything, ids, ids, cached?.dlcNames.orEmpty(), scanning = false))
}

private fun KeyValue.toAndroidGame(appId: Int): CachedGame? {
    val common = this["common"]
    // Games, and also demos and betas (playtests): separate Steam apps that can carry an Android build of their own.
    val kind = common["type"].value?.lowercase()?.takeIf { it in LISTED_TYPES } ?: return null
    // A depot tagged Android is only usable once a build is published on the public branch;
    // Steam lists some ahead of release, and those have nothing to download yet.
    val depots = this["depots"].children.filter { depot ->
        depot.name?.toIntOrNull() != null &&
            depot["config"]["oslist"].value.orEmpty().split(',').any { it.trim() == "android" } &&
            depot["manifests"]["public"] != KeyValue.INVALID
    }.map { depot ->
        CachedDepot(
            id = depot.name!!.toInt(),
            dlcAppId = depot["dlcappid"].asInteger(0).takeIf { it > 0 },
            installBytes = depot["manifests"]["public"]["size"].asLong(),
            downloadBytes = depot["manifests"]["public"]["download"].asLong(),
            manifestId = depot["manifests"]["public"]["gid"].asLong(),
        )
    }
    // Without a base-game depot there is nothing to play, whatever DLC depots exist.
    if (depots.none { it.dlcAppId == null }) return null
    return CachedGame(
        appId = appId,
        name = common["name"].value ?: return null,
        isVr = isVr(),
        depots = depots,
        saveRules = SaveRulesParser.parse(this["ufs"]),
        kind = kind,
        capsule = common["library_assets_full"]["library_capsule"]["image"]["english"].value?.takeIf { it.isNotBlank() },
        hero = common["library_assets_full"]["library_hero"]["image"]["english"].value?.takeIf { it.isNotBlank() },
        header = (common["header_image"]["english"].value ?: common["library_assets_full"]["library_header"]["image"]["english"].value)?.takeIf { it.isNotBlank() },
        parent = if (kind != "game") common["parent"].asInteger(0).takeIf { it > 0 } else null,
    )
}

// category_53 / category_54 are Valve's "VR Supported" / "VR Only" flags; store tag 21978 is "VR".
private fun KeyValue.isVr(): Boolean {
    val common = this["common"]
    val categories = common["category"].children.mapNotNull { it.name }
    val tags = common["store_tags"].children.map { it.asInteger(-1) }
    return "category_53" in categories || "category_54" in categories || VR_TAG in tags
}

/** The Steam app types listed in the library. */
private val LISTED_TYPES = setOf("game", "demo", "beta")

private const val TAG = "GPLibrary"
private const val PICS_CHUNK = 200
private const val PARALLEL_REQUESTS = 6
private const val VR_TAG = 21978
private const val CACHE_TTL_MS = 24L * 60 * 60 * 1000
