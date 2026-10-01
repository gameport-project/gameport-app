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
import `in`.dragonbra.javasteam.steam.handlers.ClientMsgHandler
import android.util.Log
import com.google.protobuf.ByteString
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.TimeUnit
import java.util.zip.CRC32
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.future.await
import kotlinx.coroutines.withContext

private const val TAG = "GPSteamTicket"

/**
 * Keeps the game connect tokens Steam sends after log on. A token is used up by every session
 * ticket, and Steam sends a handful at a time.
 */
internal class GameConnectTokens : ClientMsgHandler() {
    private val tokens = LinkedBlockingDeque<ByteArray>()

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
            EMsg.ClientAuthListAck -> {
                val ack = ClientMsgProtobuf<CMsgClientAuthListAck.Builder>(CMsgClientAuthListAck::class.java, packetMsg)
                authSequenceFromServer = ack.body.messageSequence
                Log.i(TAG, "Steam acknowledged the ticket list (sequence ${ack.body.messageSequence}, ${ack.body.ticketCrcCount} ticket(s))")
            }
            EMsg.ClientTicketAuthComplete -> {
                val done = ClientMsgProtobuf<CMsgClientTicketAuthComplete.Builder>(CMsgClientTicketAuthComplete::class.java, packetMsg)
                Log.i(TAG, "Steam validated a ticket: session response ${done.body.eauthSessionResponse}, state ${done.body.estate}")
            }
            else -> Unit
        }
    }

    val available: Int get() = tokens.size

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
    private const val SESSION_HEADER_SIZE = 24
    private val nonce = java.security.SecureRandom()

    suspend fun build(session: SteamSession, appId: Int): ByteArray = withContext(Dispatchers.IO) {
        val ownership = session.apps.getAppOwnershipTicket(appId).await()
        check(ownership.result == EResult.OK && ownership.ticket.isNotEmpty()) { "Steam gave no ownership ticket for $appId (${ownership.result})" }
        val token = checkNotNull(session.connectTokens.take(TOKEN_WAIT_MS)) { "Steam sent no game connect token" }
        val ticket = assemble(
            token = token,
            ownershipTicket = ownership.ticket,
            headerNonce = session.connectTokens.clientInstanceId.takeIf { it != 0L } ?: nonce.nextLong(),
            millisSinceLogOn = (System.currentTimeMillis() - session.loggedOnAtMillis).coerceAtLeast(0),
            connectionCount = session.nextConnectionCount(),
        )
        activate(session, appId, ticket)
        ticket
    }

    /**
     * Tells Steam the ticket exists, as the Steam client does when it hands one to a game: a game's
     * server asks Steam to check the ticket, and Steam only knows the ones that were declared.
     */
    private fun activate(session: SteamSession, appId: Int, ticket: ByteArray) {
        val steamId = session.client.steamID?.convertToUInt64() ?: return
        val crc = CRC32().apply { update(ticket) }.value.toInt()
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
                .setTicket(ByteString.copyFrom(ticket)),
        )
        message.body.addAppIds(appId)
        message.body.setMessageSequence(session.nextAuthSequence())
        session.client.send(message)
        Log.i(TAG, "declared a session ticket for $appId to Steam (crc $crc, ${ticket.size} bytes)")
    }

    internal fun assemble(token: ByteArray, ownershipTicket: ByteArray, headerNonce: Long, millisSinceLogOn: Long, connectionCount: Int): ByteArray {
        val buffer = ByteBuffer.allocate(4 + token.size + 4 + SESSION_HEADER_SIZE + 4 + ownershipTicket.size).order(ByteOrder.LITTLE_ENDIAN)
        buffer.putInt(token.size).put(token)
        buffer.putInt(SESSION_HEADER_SIZE)
        buffer.putInt(1) // constants the Steam client writes here
        buffer.putInt(2)
        // 8 bytes the Steam client fills with the connection's client instance id (the real tickets we compared vary per session).
        buffer.putLong(headerNonce)
        buffer.putInt(millisSinceLogOn.toInt())
        buffer.putInt(connectionCount)
        buffer.putInt(ownershipTicket.size).put(ownershipTicket)
        return buffer.array()
    }
}
