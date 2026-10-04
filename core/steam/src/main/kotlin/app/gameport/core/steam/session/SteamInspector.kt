package app.gameport.core.steam.session

import `in`.dragonbra.javasteam.steam.cdn.Client
import `in`.dragonbra.javasteam.steam.handlers.steamapps.PICSRequest
import `in`.dragonbra.javasteam.steam.handlers.steamcontent.SteamContent
import `in`.dragonbra.javasteam.types.KeyValue
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.future.await

/**
 * Reads what Steam publishes for a game, as the signed-in account sees it, to find out what a download should hold. Read only, for the
 * debug receiver: it asks the same questions the library and the downloader ask, and changes nothing on the account.
 */
object SteamInspector {
    /** The raw description of the app (its depots, their branches and settings, its launch options), as text. */
    suspend fun describe(session: SteamSession, appId: Int): String {
        val tokens = session.apps.picsGetAccessTokens(listOf(appId), emptyList()).await().appTokens
        val info = session.apps.picsGetProductInfo(listOf(PICSRequest(appId, tokens[appId] ?: 0L)), emptyList()).await()
        val app = info.results.flatMap { it.apps.values }.firstOrNull { it.id == appId } ?: return "Steam gave nothing for app $appId"
        return buildString {
            val root = app.keyValues
            appendLine("app $appId")
            appendLine("[common]")
            root["common"].children.filter { it.children.isEmpty() }.forEach { appendLine("  ${it.name} = ${it.value}") }
            appendLine("[config]")
            root["config"].dump(this, 1)
            appendLine("[depots]")
            root["depots"].dump(this, 1)
        }
    }

    /** The files of [manifestId] of [depotId]: name, size and SHA-1 of each, or why they could not be read. */
    suspend fun manifest(session: SteamSession, appId: Int, depotId: Int, manifestId: Long, branch: String = "public"): String = coroutineScope {
        val content = session.client.getHandler(SteamContent::class.java) ?: return@coroutineScope "no content handler"
        val key = session.apps.getDepotDecryptionKey(depotId, appId).await().depotKey
        val servers = content.getServersForSteamPipe(null, 8, this).await()
        val code = content.getManifestRequestCode(depotId, appId, manifestId, branch, null, this).await()
        // Not closed: the client shares the session's HTTP client, and closing it would stop every request GamePort makes afterwards.
        Client(session.client).let { cdn ->
            var failure: Throwable? = null
            for (server in servers) {
                try {
                    val manifest = cdn.downloadManifestFuture(depotId, manifestId, code, server, key).await()
                    return@coroutineScope buildString {
                        appendLine("depot $depotId manifest $manifestId: ${manifest.files.size} file(s), ${manifest.files.sumOf { it.totalSize }} bytes")
                        manifest.files.sortedBy { it.fileName }.forEach { file ->
                            val sha = file.fileHash.joinToString("") { "%02x".format(it) }
                            appendLine("${file.totalSize}  $sha  ${file.fileName}")
                        }
                    }
                } catch (e: Exception) {
                    failure = e
                }
            }
            "could not read the manifest from ${servers.size} server(s): ${failure?.message}"
        }
    }

    private fun KeyValue.dump(out: StringBuilder, depth: Int) {
        for (child in children) {
            out.append("  ".repeat(depth)).append(child.name)
            if (child.children.isEmpty()) out.append(" = ").appendLine(child.value) else {
                out.appendLine()
                child.dump(out, depth + 1)
            }
        }
    }
}
