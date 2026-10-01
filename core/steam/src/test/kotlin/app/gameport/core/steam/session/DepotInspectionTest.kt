package app.gameport.core.steam.session

import `in`.dragonbra.javasteam.steam.handlers.steamapps.PICSRequest
import `in`.dragonbra.javasteam.steam.handlers.steamapps.SteamApps
import `in`.dragonbra.javasteam.steam.handlers.steamuser.SteamUser
import `in`.dragonbra.javasteam.steam.handlers.steamuser.callback.LoggedOnCallback
import `in`.dragonbra.javasteam.steam.steamclient.SteamClient
import `in`.dragonbra.javasteam.steam.steamclient.callbackmgr.CallbackManager
import `in`.dragonbra.javasteam.steam.steamclient.callbacks.ConnectedCallback
import `in`.dragonbra.javasteam.steam.steamclient.configuration.SteamConfiguration
import `in`.dragonbra.javasteam.networking.steam3.ProtocolTypes
import java.util.EnumSet
import kotlinx.coroutines.future.await
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Prints the depots of an app, read anonymously (public data only). Opt-in:
 * GAMEPORT_NETWORK_TESTS=1 GAMEPORT_INSPECT_APP=<appId>.
 */
class DepotInspectionTest {
    @Test
    fun printDepots() = runBlocking {
        assumeTrue(System.getenv("GAMEPORT_NETWORK_TESTS") == "1")
        val appId = System.getenv("GAMEPORT_INSPECT_APP")?.toIntOrNull()
        assumeTrue("set GAMEPORT_INSPECT_APP", appId != null)

        val client = SteamClient(SteamConfiguration.create { it.withProtocolTypes(EnumSet.of(ProtocolTypes.WEB_SOCKET)) })
        val manager = CallbackManager(client)
        val user = client.getHandler(SteamUser::class.java)!!
        val apps = client.getHandler(SteamApps::class.java)!!
        var loggedOn = false
        manager.subscribe(ConnectedCallback::class.java) { user.logOnAnonymous() }
        manager.subscribe(LoggedOnCallback::class.java) { loggedOn = true }
        client.connect()
        val deadline = System.currentTimeMillis() + 30_000
        while (!loggedOn && System.currentTimeMillis() < deadline) manager.runWaitCallbacks(200)

        val tokens = apps.picsGetAccessTokens(listOf(appId!!), emptyList()).await().appTokens
        val result = apps.picsGetProductInfo(listOf(PICSRequest(appId, tokens[appId] ?: 0L)), emptyList()).await()
        val kv = result.results.first().apps.values.first().keyValues
        println("APP ${kv["common"]["name"].value}")
        for (depot in kv["depots"].children.filter { it.name?.toIntOrNull() != null }) {
            val config = depot["config"]
            println(
                "depot ${depot.name} os=${config["oslist"].value} dlcapp=${depot["dlcappid"].value} " +
                    "optionaldlc=${config["optionaldlc"].value} lang=${config["language"].value} " +
                    "size=${depot["manifests"]["public"]["size"].asLong()} download=${depot["manifests"]["public"]["download"].asLong()} " +
                    "sharedinstall=${depot["sharedinstall"].value}",
            )
        }
        println("UFS tree:")
        fun dump(node: `in`.dragonbra.javasteam.types.KeyValue, indent: String) {
            if (node.children.isEmpty()) println("$indent${node.name} = ${node.value}") else {
                println("$indent${node.name}")
                node.children.forEach { dump(it, "$indent  ") }
            }
        }
        dump(kv["ufs"], "  ")
        client.disconnect()
    }
}
