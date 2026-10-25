package app.gameport.core.steam.session

import `in`.dragonbra.javasteam.protobufs.steamclient.SteammessagesClientserver.CMsgClientGamesPlayed
import org.junit.Assert.assertEquals
import org.junit.Test

class PlayingMessageTest {
    @Test
    fun `a game the account is in carries its process, its owner and how it was started`() {
        val body = CMsgClientGamesPlayed.newBuilder()
        fillPlaying(body, appId = 1125240, accountId = 4242, processId = 777)
        val game = body.gamesPlayedList.single()
        assertEquals(1125240L, game.gameId)
        assertEquals(777, game.processId)
        assertEquals(4242, game.ownerId)
        assertEquals(100, game.launchSource)
        assertEquals(`in`.dragonbra.javasteam.enums.EOSType.AndroidUnknown.code(), body.clientOsType)
    }

    @Test
    fun `the game's process, its parent and its build are told too`() {
        val body = CMsgClientGamesPlayed.newBuilder()
        fillPlaying(body, appId = 1125240, accountId = 4242, processId = 777, parentProcessId = 55, buildId = 9001)
        val game = body.gamesPlayedList.single()
        assertEquals(9001, game.gameBuildId)
        val process = game.processIdListList.single()
        assertEquals(777, process.processId)
        assertEquals(55, process.processIdParent)
        assertEquals(true, process.parentIsSteam)
    }

    @Test
    fun `a build that is not known is left out`() {
        val body = CMsgClientGamesPlayed.newBuilder()
        fillPlaying(body, appId = 1125240, accountId = 4242, processId = 777, buildId = 0)
        assertEquals(0, body.gamesPlayedList.single().gameBuildId)
    }

    @Test
    fun `no game at all says the account is in none`() {
        val body = CMsgClientGamesPlayed.newBuilder()
        fillPlaying(body, appId = 0, accountId = 4242, processId = 777)
        assertEquals(0, body.gamesPlayedCount)
    }
}
