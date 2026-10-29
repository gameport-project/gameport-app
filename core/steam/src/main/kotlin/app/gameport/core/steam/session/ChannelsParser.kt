package app.gameport.core.steam.session

import app.gameport.core.model.UpdateChannels
import app.gameport.core.steam.cache.CachedChannel
import app.gameport.core.steam.cache.CachedDepot
import `in`.dragonbra.javasteam.types.KeyValue

/**
 * Reads the channels (branches) of an app that hold an Android build, from the `depots` section of its product info. A channel counts when it has a manifest for a depot of the base
 * game tagged Android (the depots of an extra content are not the game). Each carries what tells whether its Android build is the normal channel's: the depots and manifests,
 * written as one text, that is the same text for the same build.
 */
internal object ChannelsParser {
    /**
     * The channel a game that has no Android build on the normal channel is listed with: the most recently updated one that asks no password. A channel that asks a password
     * cannot be fetched, and the normal one is not an option here (it has nothing). Null when there is none.
     */
    fun fallbackChannel(channels: List<CachedChannel>): CachedChannel? =
        channels.filter { it.name != UpdateChannels.NORMAL && !it.passwordRequired }.maxWithOrNull(compareBy<CachedChannel>({ it.updated }, { it.name }))

    /** The Android depots that hold a manifest on [channel], with the sizes and the manifest of that channel. */
    fun depotsOf(depots: KeyValue, channel: String): List<CachedDepot> =
        depots.children.filter { depot ->
            depot.name?.toIntOrNull() != null &&
                depot["config"]["oslist"].value.orEmpty().split(',').any { it.trim() == "android" } &&
                depot["manifests"][channel] != KeyValue.INVALID
        }.map { depot ->
            val manifest = depot["manifests"][channel]
            CachedDepot(
                id = depot.name!!.toInt(),
                dlcAppId = depot["dlcappid"].asInteger(0).takeIf { it > 0 },
                installBytes = manifest["size"].asLong(),
                downloadBytes = manifest["download"].asLong(),
                manifestId = manifest["gid"].asLong(),
            )
        }

    fun parse(depots: KeyValue): List<CachedChannel> {
        val base = depots.children.filter { depot ->
            depot.name?.toIntOrNull() != null &&
                depot["config"]["oslist"].value.orEmpty().split(',').any { it.trim() == "android" } &&
                depot["dlcappid"].asInteger(0) <= 0
        }
        if (base.isEmpty()) return emptyList()
        val branches = depots["branches"].children
        // A channel Steam does not describe (it is only named in a manifest) still counts, with what is known of it.
        val names = (branches.mapNotNull { it.name } + base.flatMap { depot -> depot["manifests"].children.mapNotNull { it.name } }).distinct()
        return names.mapNotNull { name ->
            val manifests = base.mapNotNull { depot ->
                depot["manifests"][name].takeIf { it != KeyValue.INVALID }?.let { depot.name!! to it }
            }
            if (manifests.isEmpty()) return@mapNotNull null
            val branch = branches.firstOrNull { it.name == name }
            CachedChannel(
                name = name,
                description = branch?.get("description")?.value?.takeIf { it.isNotBlank() },
                updated = branch?.get("timeupdated")?.asLong() ?: 0L,
                androidBytes = manifests.sumOf { (_, manifest) -> manifest["size"].asLong() },
                androidKey = manifests.sortedBy { (depot, _) -> depot }.joinToString(";") { (depot, manifest) -> "$depot:${manifest["gid"].value.orEmpty()}" },
                passwordRequired = (branch?.get("pwdrequired")?.asInteger(0) ?: 0) != 0,
            )
        }
    }
}
