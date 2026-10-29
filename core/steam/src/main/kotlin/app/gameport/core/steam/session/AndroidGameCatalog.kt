package app.gameport.core.steam.session

import app.gameport.core.model.Artwork
import kotlinx.coroutines.flow.flow
import android.util.Log
import app.gameport.core.model.AgePolicy
import app.gameport.core.model.AndroidApp
import app.gameport.core.model.AndroidBuild
import app.gameport.core.model.AppKind
import app.gameport.core.model.AndroidDepot
import app.gameport.core.model.DlcContent
import app.gameport.core.model.SaveRule
import app.gameport.core.model.Game
import app.gameport.core.model.Library
import app.gameport.core.model.Ownership
import app.gameport.core.model.UpdateChannel
import app.gameport.core.model.UpdateChannels
import app.gameport.core.steam.cache.CACHE_VERSION
import app.gameport.core.steam.cache.CachedDepot
import app.gameport.core.steam.cache.CachedDlcApp
import app.gameport.core.steam.cache.CachedGame
import app.gameport.core.steam.cache.CachedLibrary
import app.gameport.core.steam.cache.CachedChannel
import app.gameport.core.steam.cache.CachedSaveRule
import app.gameport.core.steam.cache.LibraryCacheStore
import app.gameport.core.steam.cache.SaveRulesChange
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
    val dlcApps = HashMap<Int, CachedDlcApp>()
    cached?.dlcApps?.forEach { dlcApps[it.appId] = it }
    // The save rules of the games as an earlier version read them (a cache of another shape is not shown, but it still says what was known): a game whose rules are read
    // differently now is noted, for the sync to compare its two sides afresh instead of trusting what it agreed on under the old rules.
    val knownRules: Map<Int, List<CachedSaveRule>> = if (cached == null) cacheStore.loadPrevious(me)?.games?.associate { it.appId to it.saveRules }.orEmpty() else emptyMap()
    val rulesChanged = HashSet<Int>(cached?.rulesChanged.orEmpty())

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
        return libraryOf(games.values.filter { it.appId in visible }, owned, visible, dlcNames, scanning, dlcApps.values)
    }

    fun persist() = cacheStore.save(
        me,
        CachedLibrary(CACHE_VERSION, now(), packageApps.toMap(), checkedApps.toSet(), games.values.toList(), dlcNames.toMap(), dlcApps.values.toList(), rulesChanged.toSet()),
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
                            app.keyValues.toAndroidGame(app.id)?.let { game ->
                                if (SaveRulesChange.differ(games[app.id]?.saveRules ?: knownRules[app.id], game.saveRules)) rulesChanged += app.id
                                games[app.id] = game
                            }
                            app.keyValues.toAndroidDlc(app.id)?.let { dlc -> dlcApps[app.id] = dlc; dlcNames[app.id] = dlc.name }
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
internal fun libraryOf(
    shown: Collection<CachedGame>,
    owned: Set<Int>,
    visible: Set<Int>,
    dlcNames: Map<Int, String>,
    scanning: Boolean,
    dlcApps: Collection<CachedDlcApp> = emptyList(),
): Library {
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
            isAdult = game.adult,
            androidBuild = AndroidBuild(
                packageName = null,
                isVr = game.isVr,
                baseDepots = depots.filter { it.dlcAppId == null },
                saveRules = game.saveRules.map { SaveRule(it.localDir, it.pattern, it.recursive, it.cloudPrefix, it.pcCloudPrefix) },
                channels = channelsOf(game.channels),
                // The DLC the game's own depots name, and the DLC apps that carry their depots themselves (a game that sells its content as DLC).
                dlc = (
                    depots.filter { it.dlcAppId != null }.groupBy { it.dlcAppId!! }.map { (dlcAppId, dlcDepots) ->
                        DlcContent(dlcAppId, dlcNames[dlcAppId] ?: "DLC $dlcAppId", owned = dlcAppId in visible, depots = dlcDepots)
                    } + dlcApps.filter { it.parent == game.appId }.map { app ->
                        DlcContent(
                            app.appId, app.name, owned = app.appId in visible,
                            depots = app.depots.map { AndroidDepot(it.id, app.appId, it.installBytes, it.downloadBytes, it.manifestId, declaredBy = app.appId) },
                        )
                    }
                    ).distinctBy { it.appId }.sortedBy { it.name.lowercase() },
            ),
        )
    }.sortedBy { it.name.lowercase() }
    return Library(list, scanning, ownedAppIds = visible)
}

/** The channels of a game as the screens see them: whether each one carries the very Android build of the normal channel is told by the text that names the build. */
internal fun channelsOf(cached: List<CachedChannel>): List<UpdateChannel> {
    val normalKey = cached.firstOrNull { it.name == UpdateChannels.NORMAL }?.androidKey
    return UpdateChannels.ordered(
        cached.map { channel ->
            UpdateChannel(
                name = channel.name,
                description = channel.description,
                updatedSeconds = channel.updated,
                androidBytes = channel.androidBytes,
                sameAsNormal = channel.name != UpdateChannels.NORMAL && normalKey != null && channel.androidKey == normalKey,
                passwordRequired = channel.passwordRequired,
            )
        },
    )
}

/** The library from the last scan, for when Steam is not reachable: every cached game counts as owned. */
internal fun offlineLibrary(cacheStore: LibraryCacheStore, accountId: Long): Flow<Library> = flow {
    val cached = cacheStore.load(accountId)
    val everything = cached?.games.orEmpty()
    val ids = everything.map { it.appId }.toSet()
    emit(libraryOf(everything, ids, ids, cached?.dlcNames.orEmpty(), scanning = false, dlcApps = cached?.dlcApps.orEmpty()))
}

/**
 * A DLC app that carries Android depots of its own: type DLC with a parent game, and a depot tagged Android with a public build. Null for any other app, and for a DLC
 * without such a depot (the usual one, whose content is in the game's own depots).
 */
internal fun KeyValue.toAndroidDlc(appId: Int): CachedDlcApp? {
    val common = this["common"]
    if (common["type"].value?.lowercase() != "dlc") return null
    val parent = common["parent"].asInteger(0).takeIf { it > 0 } ?: return null
    val depots = this["depots"].children.filter { depot ->
        depot.name?.toIntOrNull() != null &&
            depot["config"]["oslist"].value.orEmpty().split(',').any { it.trim() == "android" } &&
            depot["manifests"]["public"] != KeyValue.INVALID
    }.map { depot ->
        CachedDepot(
            id = depot.name!!.toInt(),
            dlcAppId = depot["dlcappid"].asInteger(appId).takeIf { it > 0 } ?: appId,
            installBytes = depot["manifests"]["public"]["size"].asLong(),
            downloadBytes = depot["manifests"]["public"]["download"].asLong(),
            manifestId = depot["manifests"]["public"]["gid"].asLong(),
        )
    }
    if (depots.isEmpty()) return null
    return CachedDlcApp(appId, parent, common["name"].value ?: return null, depots)
}

internal fun KeyValue.toAndroidGame(appId: Int): CachedGame? {
    val common = this["common"]
    // Games, and also demos and betas (playtests): separate Steam apps that can carry an Android build of their own.
    val kind = common["type"].value?.lowercase()?.takeIf { it in LISTED_TYPES } ?: return null
    // A depot tagged Android is only usable once a build is published on the public branch;
    // Steam lists some ahead of release, and those have nothing to download yet.
    val publicDepots = this["depots"].children.filter { depot ->
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
    val channels = ChannelsParser.parse(this["depots"])
    // Without a base-game depot there is nothing to play, whatever DLC depots exist. When the normal channel has none, a test channel that asks no password may have one (an Android
    // version published for testing only): the game is listed with what that channel holds, and is installed from a channel (see AndroidBuild.onlyOnOtherChannels).
    val depots = if (publicDepots.any { it.dlcAppId == null }) {
        publicDepots
    } else {
        val fallback = ChannelsParser.fallbackChannel(channels) ?: return null
        ChannelsParser.depotsOf(this["depots"], fallback.name).takeIf { list -> list.any { it.dlcAppId == null } } ?: return null
    }
    return CachedGame(
        appId = appId,
        name = common["name"].value ?: return null,
        isVr = isVr(),
        adult = isAdult(),
        depots = depots,
        saveRules = SaveRulesParser.parse(this["ufs"]),
        channels = channels,
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

// Adult content: the store tags for nudity and sexual content, an age of 18 or more, or a content descriptor for sexual content (3, 4).
private fun KeyValue.isAdult(): Boolean {
    val common = this["common"]
    val tags = common["store_tags"].children.map { it.asInteger(-1) }
    val descriptors = common["content_descriptors"].children.map { it.asInteger(-1) }
    return tags.any { it in AndroidApp.ADULT_TAGS } || common["required_age"].asInteger(0) >= AgePolicy.ADULT_AGE || descriptors.any { it == 3 || it == 4 }
}

/** The Steam app types listed in the library. */
private val LISTED_TYPES = setOf("game", "demo", "beta")

private const val TAG = "GPLibrary"
private const val PICS_CHUNK = 200
private const val PARALLEL_REQUESTS = 6
private const val VR_TAG = 21978
private const val CACHE_TTL_MS = 24L * 60 * 60 * 1000
