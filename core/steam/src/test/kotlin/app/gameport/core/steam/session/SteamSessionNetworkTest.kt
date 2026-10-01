package app.gameport.core.steam.session

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Talks to the real Steam network; run with GAMEPORT_NETWORK_TESTS=1. */
class SteamSessionNetworkTest {
    @Test
    fun qrSignInProducesChallengeUrl() = runBlocking {
        assumeTrue(System.getenv("GAMEPORT_NETWORK_TESTS") == "1")
        val session = SteamSession()
        val url = CompletableDeferred<String>()
        val flow = async { session.authenticateWithQr("GamePort test") { url.complete(it) } }
        try {
            val challenge = withTimeout(30_000) { url.await() }
            assertTrue(challenge, challenge.startsWith("https://s.team/q/"))
        } finally {
            flow.cancelAndJoin()
            session.disconnect()
        }
    }
}
