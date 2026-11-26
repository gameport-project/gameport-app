package app.gameport.core.sync

import app.gameport.core.model.ControllerAnswer
import app.gameport.core.model.ControllerFeedbackMessage
import app.gameport.core.model.PendingControllerAnswer
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ControllerFeedbackSenderTest {
    private val voter = "0123456789abcdef0123456789abcdef"
    private fun message(p: PendingControllerAnswer) = ControllerFeedbackMessage(p.appId, "quest", voter, "0.9.5", p.answer, p.mapping, p.preset.ifEmpty { null })
    private val first = PendingControllerAnswer(1, "left:x>left:y", "", ControllerAnswer.WORKS)
    private val second = PendingControllerAnswer(2, "left:x>left:x", "faces", ControllerAnswer.FAILS)

    @Test
    fun `every answer is sent in order and each one is told it got through`() = runBlocking {
        val sent = mutableListOf<String>()
        val told = mutableListOf<PendingControllerAnswer>()
        sendControllerAnswers(listOf(first, second), ::message, { sent += it; Delivery.SENT }) { told += it }
        assertEquals(2, sent.size)
        assertTrue(sent[0].contains("\"appId\":1") && !sent[0].contains("preset"))
        assertTrue(sent[1].contains("\"preset\":\"faces\""))
        assertEquals(listOf(first, second), told)
    }

    @Test
    fun `an answer that cannot be sent now waits with the ones after it`() = runBlocking {
        val told = mutableListOf<PendingControllerAnswer>()
        sendControllerAnswers(listOf(first, second), ::message, { Delivery.LATER }) { told += it }
        assertTrue(told.isEmpty())
    }

    @Test
    fun `an answer the relay refuses is given up so it does not block the others`() = runBlocking {
        val told = mutableListOf<PendingControllerAnswer>()
        var calls = 0
        sendControllerAnswers(listOf(first, second), ::message, { if (calls++ == 0) Delivery.REFUSED else Delivery.SENT }) { told += it }
        assertEquals(listOf(first, second), told)
    }
}
