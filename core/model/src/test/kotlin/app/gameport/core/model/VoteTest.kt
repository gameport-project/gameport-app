package app.gameport.core.model

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VoteTest {
    private val day1 = "2026-10-08"
    private val day2 = "2026-10-09"

    @Test
    fun `the message carries exactly the fields the relay accepts`() {
        val message = VoteMessage(1125240, Verdict.WORKS, "0.7.2", "quest", "0123456789abcdef0123456789abcdef")
        val json = Json.parseToJsonElement(message.toJson()).jsonObject
        assertEquals(setOf("v", "appId", "verdict", "app", "device", "voter"), json.keys)
        assertEquals(1, json.getValue("v").jsonPrimitive.int)
        assertEquals(1125240, json.getValue("appId").jsonPrimitive.int)
        assertEquals("works", json.getValue("verdict").jsonPrimitive.content)
        assertEquals("0.7.2", json.getValue("app").jsonPrimitive.content)
        assertEquals("quest", json.getValue("device").jsonPrimitive.content)
    }

    @Test
    fun `the game build is sent only when there is one`() {
        val with = VoteMessage(1, Verdict.FAILS, "0.7.2", "pico", "0123456789abcdef0123456789abcdef", "b-1.2")
        assertEquals("b-1.2", Json.parseToJsonElement(with.toJson()).jsonObject.getValue("game").jsonPrimitive.content)
        assertEquals("fails", Json.parseToJsonElement(with.toJson()).jsonObject.getValue("verdict").jsonPrimitive.content)
    }

    @Test
    fun `the device is told in the few words the relay knows`() {
        assertEquals("quest", VoteDevice.of("meta", tablet = false))
        assertEquals("pico", VoteDevice.of("pico", tablet = false))
        assertEquals("other", VoteDevice.of("openxr", tablet = false))
        assertEquals("tablet", VoteDevice.of(null, tablet = true))
        assertEquals("phone", VoteDevice.of(null, tablet = false))
    }

    @Test
    fun `a game is asked about the first time it closes`() {
        assertTrue(VerdictBook().shouldAsk(1, "0.7.2", day1))
        assertEquals(setOf(1), VerdictBook().closed(1, "0.7.2", day1).pending)
    }

    @Test
    fun `an answer is not asked again for the same version, the same day or later`() {
        val book = VerdictBook().answered(1, Verdict.WORKS, "0.7.2", day1, send = true)
        assertFalse(book.shouldAsk(1, "0.7.2", day1))
        assertFalse(book.shouldAsk(1, "0.7.2", day2))
        assertTrue(book.closed(1, "0.7.2", day2).pending.isEmpty())
    }

    @Test
    fun `a new version of GamePort asks again`() {
        val book = VerdictBook().answered(1, Verdict.WORKS, "0.7.2", day1, send = true)
        assertTrue(book.shouldAsk(1, "0.7.3", day1))
    }

    @Test
    fun `after it did not work the game is asked about once a day`() {
        val book = VerdictBook().answered(1, Verdict.FAILS, "0.7.2", day1, send = true)
        assertFalse(book.shouldAsk(1, "0.7.2", day1))
        assertTrue(book.shouldAsk(1, "0.7.2", day2))
    }

    @Test
    fun `closing the question without answering does not ask again for this version`() {
        val book = VerdictBook().closed(1, "0.7.2", day1).dismissed(1, "0.7.2", day1)
        assertTrue(book.pending.isEmpty())
        assertFalse(book.shouldAsk(1, "0.7.2", day2))
        assertNull(book.games.getValue(1).verdict)
        assertTrue(book.shouldAsk(1, "0.7.3", day2))
    }

    @Test
    fun `dismissing keeps the last answer`() {
        val book = VerdictBook().answered(1, Verdict.FAILS, "0.7.2", day1, send = true).dismissed(1, "0.7.3", day2)
        assertEquals("fails", book.games.getValue(1).verdict)
        assertEquals("0.7.3", book.games.getValue(1).askedFor)
    }

    @Test
    fun `an answer waits to be sent, and is marked once the relay has it`() {
        val book = VerdictBook().answered(1, Verdict.WORKS, "0.7.2", day1, send = true)
        assertEquals(listOf(Answer(1, Verdict.WORKS, false)), book.unsent())
        assertTrue(book.sent(1, Verdict.WORKS).unsent().isEmpty())
    }

    @Test
    fun `an answer changed while it was being sent is not marked as sent`() {
        val first = VerdictBook().answered(1, Verdict.WORKS, "0.7.2", day1, send = true)
        val changed = first.answered(1, Verdict.FAILS, "0.7.2", day1, send = true)
        assertEquals(listOf(Answer(1, Verdict.FAILS, false)), changed.sent(1, Verdict.WORKS).unsent())
    }

    @Test
    fun `an answer is kept on the device and never queued when the player does not share`() {
        val book = VerdictBook().answered(1, Verdict.WORKS, "0.7.2", day1, send = false)
        assertTrue(book.unsent().isEmpty())
        assertEquals("works", book.games.getValue(1).verdict)
    }

    @Test
    fun `the book survives being written and read, and an unreadable one is empty`() {
        val book = VerdictBook().closed(2, "0.7.2", day1).answered(1, Verdict.FAILS, "0.7.2", day1, send = true)
        assertEquals(book, VerdictBook.fromJson(book.toJson()))
        assertEquals(VerdictBook(), VerdictBook.fromJson("not json"))
        assertEquals(VerdictBook(), VerdictBook.fromJson(null))
    }

    @Test
    fun `the offline mode is told only for an answer that says it worked, after a run that proves it`() {
        val proven = VerdictBook().closed(1, "0.7.2", day1, offlineRun = true)
        assertEquals(setOf(1), proven.offlineRuns)
        assertEquals(listOf(Answer(1, Verdict.WORKS, true)), proven.answered(1, Verdict.WORKS, "0.7.2", day1, send = true).unsent())
        assertEquals(listOf(Answer(1, Verdict.FAILS, false)), proven.answered(1, Verdict.FAILS, "0.7.2", day1, send = true).unsent())
    }

    @Test
    fun `a run that does not prove the offline mode removes an earlier proof`() {
        val book = VerdictBook().closed(1, "0.7.2", day1, offlineRun = true).dismissed(1, "0.7.2", day1).closed(1, "0.7.3", day2, offlineRun = false)
        assertTrue(book.offlineRuns.isEmpty())
        assertEquals(listOf(Answer(1, Verdict.WORKS, false)), book.answered(1, Verdict.WORKS, "0.7.3", day2, send = true).unsent())
    }

    @Test
    fun `the proof is used once`() {
        val book = VerdictBook().closed(1, "0.7.2", day1, offlineRun = true).answered(1, Verdict.WORKS, "0.7.2", day1, send = true)
        assertTrue(book.offlineRuns.isEmpty())
    }

    @Test
    fun `the answer offline only is sent as such, and never carries the offline flag`() {
        val message = VoteMessage(1, Verdict.OFFLINE_ONLY, "0.7.2", "quest", "0123456789abcdef0123456789abcdef")
        assertEquals("offline_only", Json.parseToJsonElement(message.toJson()).jsonObject.getValue("verdict").jsonPrimitive.content)
        assertEquals(Verdict.OFFLINE_ONLY, Verdict.fromWire("offline_only"))
        val proven = VerdictBook().closed(1, "0.7.2", day1, offlineRun = true).answered(1, Verdict.OFFLINE_ONLY, "0.7.2", day1, send = true)
        assertEquals(listOf(Answer(1, Verdict.OFFLINE_ONLY, false)), proven.unsent())
        assertFalse(proven.shouldAsk(1, "0.7.2", day2))
    }

    @Test
    fun `the offline flag goes in the message only when it is true`() {
        val without = Json.parseToJsonElement(VoteMessage(1, Verdict.WORKS, "0.7.2", "quest", "0123456789abcdef0123456789abcdef").toJson()).jsonObject
        assertFalse("offline" in without.keys)
        val with = Json.parseToJsonElement(VoteMessage(1, Verdict.WORKS, "0.7.2", "quest", "0123456789abcdef0123456789abcdef", offline = true).toJson()).jsonObject
        assertEquals("true", with.getValue("offline").jsonPrimitive.content)
    }

    @Test
    fun `only a chosen offline mode on a device with a network and without a window about another device is a proof`() {
        assertTrue(OfflineEvidence.qualifies(SteamConnection.OFFLINE_MODE, networkAvailable = true, otherDeviceAsked = false))
        assertFalse(OfflineEvidence.qualifies(SteamConnection.OFFLINE_MODE, networkAvailable = false, otherDeviceAsked = false))
        assertFalse(OfflineEvidence.qualifies(SteamConnection.OFFLINE_MODE, networkAvailable = true, otherDeviceAsked = true))
        for (connection in listOf(SteamConnection.ONLINE, SteamConnection.CONNECTING, SteamConnection.UNREACHABLE)) {
            assertFalse("$connection", OfflineEvidence.qualifies(connection, networkAvailable = true, otherDeviceAsked = false))
        }
    }
}
