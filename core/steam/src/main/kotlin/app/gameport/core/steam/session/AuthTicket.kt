package app.gameport.core.steam.session

import `in`.dragonbra.javasteam.base.ClientMsgProtobuf
import `in`.dragonbra.javasteam.base.IPacketMsg
import `in`.dragonbra.javasteam.enums.EMsg
import `in`.dragonbra.javasteam.enums.EResult
import `in`.dragonbra.javasteam.protobufs.steamclient.SteammessagesBase.CMsgAuthTicket
import `in`.dragonbra.javasteam.protobufs.steamclient.SteammessagesClientserver.CMsgClientAuthList
import `in`.dragonbra.javasteam.protobufs.steamclient.SteammessagesClientserverLogin.CMsgClientLogonResponse
import `in`.dragonbra.javasteam.protobufs.steamclient.SteammessagesClientserver.CMsgClientAuthListAck
import `in`.dragonbra.javasteam.protobufs.steamclient.SteammessagesClientserver.CMsgClientGameConnectTokens
import `in`.dragonbra.javasteam.protobufs.steamclient.SteammessagesClientserver.CMsgClientTicketAuthComplete
import `in`.dragonbra.javasteam.protobufs.steamclient.SteammessagesClientserver2.CMsgClientKickPlayingSession
import `in`.dragonbra.javasteam.protobufs.steamclient.SteammessagesClientserver2.CMsgClientPlayingSessionState
import `in`.dragonbra.javasteam.steam.handlers.ClientMsgHandler
import android.util.Log
import com.google.protobuf.ByteString
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.TimeUnit
import java.util.zip.CRC32
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.future.await
import kotlinx.coroutines.withContext

private const val TAG = "GPSteamTicket"

/**
 * Steam lets one session play at a time on an account. This is what it said when another session plays: [appId] is the game it plays (0 when
 * not said), and [kicked] is true when it ended the playing session of this one.
 */
data class PlayingElsewhere(val appId: Int, val kicked: Boolean)

/** Steam refuses the playing session of this connection because another one plays: no ticket is made until the player says what to do. */
class PlayingBlockedException(val other: PlayingElsewhere) : Exception("another session of the account plays (app ${other.appId})")

/**
 * Keeps the game connect tokens Steam sends after log on. A token is used up by every session
 * ticket, and Steam sends a handful at a time.
 */
internal class GameConnectTokens : ClientMsgHandler() {
    private val tokens = LinkedBlockingDeque<ByteArray>()

    @Volatile private var traceUntil = 0L

    /** Writes down what Steam sends over the next [millis]. */
    fun traceFor(millis: Long) {
        traceUntil = android.os.SystemClock.elapsedRealtime() + millis
    }

    private val _playingElsewhere = MutableStateFlow<PlayingElsewhere?>(null)

    /** Steam dropped this connection just after the account was declared as playing: another session plays, and which one is not said. */
    fun refusedPlaying() {
        if (_playingElsewhere.value == null) _playingElsewhere.value = PlayingElsewhere(0, kicked = false)
    }

    /** Set while Steam says another session of the account plays; null when it says nobody does, or has said nothing. */
    val playingElsewhere: StateFlow<PlayingElsewhere?> = _playingElsewhere.asStateFlow()

    @Volatile var authSequenceFromServer = 0
        private set

    /** Identifies this connection to Steam; the ticket header carries it (0 until log on). */
    @Volatile var clientInstanceId: Long = 0L
        private set

    override fun handleMsg(packetMsg: IPacketMsg) {
        when (packetMsg.msgType) {
            EMsg.ClientLogOnResponse -> {
                val response = ClientMsgProtobuf<CMsgClientLogonResponse.Builder>(CMsgClientLogonResponse::class.java, packetMsg)
                clientInstanceId = response.body.clientInstanceId
                Log.i(TAG, "logon response: client instance id ${java.lang.Long.toHexString(clientInstanceId)}, token id ${java.lang.Long.toHexString(response.body.tokenId)}")
            }
            EMsg.ClientGameConnectTokens -> {
                val message = ClientMsgProtobuf<CMsgClientGameConnectTokens.Builder>(CMsgClientGameConnectTokens::class.java, packetMsg)
                message.body.tokensList.forEach { tokens.addLast(it.toByteArray()) }
            }
            EMsg.ClientPlayingSessionState -> {
                val state = ClientMsgProtobuf<CMsgClientPlayingSessionState.Builder>(CMsgClientPlayingSessionState::class.java, packetMsg).body
                Log.i(TAG, "Steam says playing is ${if (state.playingBlocked) "blocked: another session plays app ${state.playingApp}" else "free"}")
                _playingElsewhere.value = if (state.playingBlocked) PlayingElsewhere(state.playingApp, kicked = false) else null
            }
            EMsg.ClientKickPlayingSession -> {
                val kick = ClientMsgProtobuf<CMsgClientKickPlayingSession.Builder>(CMsgClientKickPlayingSession::class.java, packetMsg).body
                Log.i(TAG, "Steam ended the playing session of this connection (only the game: ${kick.onlyStopGame})")
                _playingElsewhere.value = PlayingElsewhere(_playingElsewhere.value?.appId ?: 0, kicked = true)
            }
            EMsg.ClientAuthListAck -> {
                val ack = ClientMsgProtobuf<CMsgClientAuthListAck.Builder>(CMsgClientAuthListAck::class.java, packetMsg)
                authSequenceFromServer = ack.body.messageSequence
                ack.body.ticketCrcList.forEach { acks.remove(it)?.countDown() }
                Log.i(TAG, "Steam acknowledged the ticket list (sequence ${ack.body.messageSequence}, ${ack.body.ticketCrcCount} ticket(s))")
            }
            EMsg.ClientTicketAuthComplete -> {
                val done = ClientMsgProtobuf<CMsgClientTicketAuthComplete.Builder>(CMsgClientTicketAuthComplete::class.java, packetMsg)
                Log.i(TAG, "Steam validated a ticket: session response ${done.body.eauthSessionResponse}, state ${done.body.estate}")
            }
            else -> if (android.os.SystemClock.elapsedRealtime() < traceUntil) Log.i(TAG, "Steam sent ${packetMsg.msgType}")
        }
    }

    val available: Int get() = tokens.size

    private val acks = java.util.concurrent.ConcurrentHashMap<Int, java.util.concurrent.CountDownLatch>()

    /** Called before a ticket list is sent: the latch opens when Steam's acknowledgement names the ticket with this CRC. */
    fun expectAck(crc: Int): java.util.concurrent.CountDownLatch = java.util.concurrent.CountDownLatch(1).also { acks[crc] = it }

    fun forgetAck(crc: Int) {
        acks.remove(crc)
    }

    /** The oldest token, waiting up to [timeoutMs] for Steam to send one. */
    fun take(timeoutMs: Long): ByteArray? = tokens.pollFirst(timeoutMs, TimeUnit.MILLISECONDS)
}

/**
 * Builds the ticket a game hands to its own servers to prove who is playing, the same one the
 * Steam client returns from `GetAuthSessionTicket`: the game connect token, a fixed-size session
 * header, and the app ownership ticket Steam signs. Every part is prefixed with its length, in
 * little-endian, so a server can check it with Valve's `AuthenticateUserTicket`.
 */
internal object AuthTicket {
    private const val TOKEN_WAIT_MS = 10_000L
    private const val ACK_WAIT_MS = 5_000L
    private const val SESSION_HEADER_SIZE = 24
    private const val SESSION_TYPE = 2
    private const val WEB_API_TYPE = 5
    private val nonce = java.security.SecureRandom()

    /**
     * The ticket for [appId]. With an [identity] it is the one `GetAuthTicketForWebApi` gives: the same pieces with another type in the
     * header (the Steam client gives the same length: the 2560 bytes of the callback are only its buffer), and Steam is told which service it is meant for, so that service can ask Steam to check it.
     */
    suspend fun build(session: SteamSession, appId: Int, identity: String? = null): ByteArray = withContext(Dispatchers.IO) {
        val ownership = session.apps.getAppOwnershipTicket(appId).await()
        check(ownership.result == EResult.OK && ownership.ticket.isNotEmpty()) { "Steam gave no ownership ticket for $appId (${ownership.result})" }
        val token = checkNotNull(session.connectTokens.take(TOKEN_WAIT_MS)) { "Steam sent no game connect token" }
        val ticket = assemble(
            token = token,
            ownershipTicket = ownership.ticket,
            headerNonce = session.connectTokens.clientInstanceId.takeIf { it != 0L } ?: nonce.nextLong(),
            millisSinceLogOn = (System.currentTimeMillis() - session.loggedOnAtMillis).coerceAtLeast(0),
            connectionCount = session.nextConnectionCount(),
            type = if (identity == null) SESSION_TYPE else WEB_API_TYPE,
        )
        // Steam is told of the token and the header only, as JavaSteam and SteamKit do for both kinds of ticket: it looks a ticket up by the CRC of those
        // bytes, so a game's server asking Steam to check the ticket finds it.
        val declared = ticket.copyOf(4 + token.size + 4 + SESSION_HEADER_SIZE)
        if (identity == null) {
            activate(session, appId, ticket, declared, serverSecret = null)
            ticket
        } else {
            activate(session, appId, ticket, declared, serverSecret(identity))
            ticket
        }
    }

    /** What Steam keeps next to a web ticket to know its service: the identity with Steam's own prefix and a terminating zero. */
    internal fun serverSecret(identity: String): ByteArray = "str:$identity\u0000".toByteArray(Charsets.UTF_8)

    /**
     * Tells Steam the ticket exists, as the Steam client does when it hands one to a game: a game's
     * server asks Steam to check the ticket, and Steam only knows the ones that were declared.
     */
    private fun activate(session: SteamSession, appId: Int, ticket: ByteArray, declared: ByteArray, serverSecret: ByteArray?) {
        val steamId = session.client.steamID?.convertToUInt64() ?: return
        val crc = CRC32().apply { update(declared) }.value.toInt()
        val message = ClientMsgProtobuf<CMsgClientAuthList.Builder>(CMsgClientAuthList::class.java, EMsg.ClientAuthList)
        message.body.setTokensLeft(session.connectTokens.available)
        message.body.setLastRequestSeq(session.authSequence)
        message.body.setLastRequestSeqFromServer(session.connectTokens.authSequenceFromServer)
        message.body.addTickets(
            CMsgAuthTicket.newBuilder()
                .setEstate(0)
                .setSteamid(steamId)
                .setGameid(appId.toLong())
                .setHSteamPipe(session.pipeHandle)
                .setTicketCrc(crc)
                .setTicket(ByteString.copyFrom(declared))
                .also { if (serverSecret != null) it.setServerSecret(ByteString.copyFrom(serverSecret)) },
        )
        message.body.addAppIds(appId)
        message.body.setMessageSequence(session.nextAuthSequence())
        // A game hands the ticket to its server at once, and the server asks Steam: the ticket is given back only when Steam has taken the list,
        // as JavaSteam and SteamKit do. Without the acknowledgement it goes on after a while, as the ticket may still be honoured.
        val acknowledged = session.connectTokens.expectAck(crc)
        session.client.send(message)
        val answered = acknowledged.await(ACK_WAIT_MS, TimeUnit.MILLISECONDS)
        if (!answered) session.connectTokens.forgetAck(crc)
        Log.i(TAG, "Steam ${if (answered) "took" else "did not acknowledge"} the ticket list for $appId")
        Log.i(TAG, "declared a session ticket for $appId to Steam (crc $crc, ${declared.size} bytes${if (serverSecret != null) ", for a web service" else ""})")
    }

    internal fun assemble(token: ByteArray, ownershipTicket: ByteArray, headerNonce: Long, millisSinceLogOn: Long, connectionCount: Int, type: Int = SESSION_TYPE): ByteArray {
        val buffer = ByteBuffer.allocate(4 + token.size + 4 + SESSION_HEADER_SIZE + 4 + ownershipTicket.size).order(ByteOrder.LITTLE_ENDIAN)
        buffer.putInt(token.size).put(token)
        buffer.putInt(SESSION_HEADER_SIZE)
        buffer.putInt(1) // constants the Steam client writes here
        buffer.putInt(type)
        // 8 bytes the Steam client fills with the connection's client instance id (the real tickets we compared vary per session).
        buffer.putLong(headerNonce)
        buffer.putInt(millisSinceLogOn.toInt())
        buffer.putInt(connectionCount)
        buffer.putInt(ownershipTicket.size).put(ownershipTicket)
        return buffer.array()
    }
}
