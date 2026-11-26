package app.gameport.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ControllerFeedbackTest {
    private fun ref(key: String) = checkNotNull(ControlRef.parse(key))

    private val detected = listOf("left:dpad_down", "left:dpad_up", "right:a").map(::ref)

    private fun mapping(enabled: Boolean, overrides: Map<String, String> = emptyMap()) =
        ControllerMapping(detected = detected, source = "valve", enabled = enabled, overrides = overrides)

    @Test
    fun `the canonical text lists every control the game uses, sorted, with the controls it goes to`() {
        val fingerprint = ControllerFingerprint.of(mapping(enabled = false))
        assertEquals("left:dpad_down>left:x;left:dpad_up>left:y;right:a>right:a", fingerprint.mapping)
        assertFalse(fingerprint.customised)
        assertNull(fingerprint.preset)
    }

    @Test
    fun `a control that goes nowhere is written none and several targets are joined and sorted`() {
        val allocation = mapOf(ref("right:y") to listOf(ref("left:y"), ref("left:x")), ref("left:view") to emptyList())
        assertEquals("left:view>none;right:y>left:x+left:y", ControllerFingerprint.canonical(allocation))
    }

    @Test
    fun `customised means turned on and different from the automatic mapping`() {
        val swapped = mapOf("left:dpad_up" to "left:x", "left:dpad_down" to "left:y")
        val custom = ControllerFingerprint.of(mapping(enabled = true, overrides = swapped))
        assertTrue(custom.customised)
        assertEquals("left:dpad_down>left:y;left:dpad_up>left:x;right:a>right:a", custom.mapping)
        // Turned on but with the choices the automatic mapping makes anyway: nothing of the player's own.
        assertFalse(ControllerFingerprint.of(mapping(enabled = true, overrides = mapOf("left:dpad_up" to "left:y"))).customised)
        // Choices kept but customising turned off: the automatic mapping applies.
        assertFalse(ControllerFingerprint.of(mapping(enabled = false, overrides = swapped)).customised)
    }

    @Test
    fun `a custom mapping that is a preset says which one, and one that is none says nothing`() {
        val none = ControllerFingerprint.of(mapping(enabled = true, overrides = mapOf("left:dpad_up" to "left:x", "left:dpad_down" to "left:y")).copy(detected = listOf(ref("left:dpad_down"), ref("left:dpad_up"))))
        assertTrue(none.customised)
        assertNull(none.preset)
        // Every face link the game uses is on the preset's target, and one differs from the automatic mapping (X would share Y's button by itself).
        val faces = ControllerFingerprint.of(
            ControllerMapping(
                detected = listOf(ref("right:x"), ref("right:y"), ref("left:x")), source = "valve", enabled = true,
                overrides = mapOf("right:x" to "left:x", "right:y" to "left:y"),
            ),
        )
        assertTrue(faces.customised)
        assertEquals(ControllerPreset.FACE_BUTTONS, faces.preset)
        assertEquals("faces", faces.wirePreset)
        // A choice that only repeats what the automatic mapping does names no preset (and is not customised at all).
        val same = ControllerFingerprint.of(
            ControllerMapping(detected = listOf(ref("right:a"), ref("right:x"), ref("left:dpad_up")), source = "valve", enabled = true, overrides = mapOf("right:x" to "left:x", "left:dpad_up" to "left:y")),
        )
        assertFalse(same.customised)
        assertEquals("auto", same.wirePreset)
        // A customised mapping that is no preset sends no key.
        assertNull(none.wirePreset)
        assertNull(ControllerFingerprint.of(mapping(enabled = false)).preset)
        assertEquals("auto", ControllerFingerprint.of(mapping(enabled = false)).wirePreset)
    }

    @Test
    fun `the canonical text respects the limits of the relay`() {
        val many = (0 until 80).associate { i -> ControlRef(Hand.LEFT, "c_" + ('a' + i % 26) + ('a' + i / 26)) to listOf(ref("left:x")) }
        val text = ControllerFingerprint.canonical(many)
        assertTrue(text.length <= ControllerFingerprint.MAX_LENGTH)
        assertTrue(text.split(';').size <= ControllerFingerprint.MAX_BINDINGS)
        // A control with a digit in its name is not something the relay reads: it is left out.
        assertEquals("", ControllerFingerprint.canonical(mapOf(ControlRef(Hand.LEFT, "button1") to listOf(ref("left:x")))))
    }

    @Test
    fun `a canonical text is read back, and what is not one is refused`() {
        val back = checkNotNull(ControllerFingerprint.parse("left:dpad_down>left:x;left:view>none;right:y>left:x+left:y"))
        assertEquals(listOf(ref("left:x")), back.getValue(ref("left:dpad_down")))
        assertEquals(emptyList<ControlRef>(), back.getValue(ref("left:view")))
        assertEquals(listOf(ref("left:x"), ref("left:y")), back.getValue(ref("right:y")))
        assertNull(ControllerFingerprint.parse(""))
        assertNull(ControllerFingerprint.parse("left:x"))
        assertNull(ControllerFingerprint.parse("left:x>middle:y"))
        assertNull(ControllerFingerprint.parse("left:x>left:y;oops"))
    }

    @Test
    fun `auto is a preset value the message and the community list carry`() {
        assertTrue(ControllerFeedbackMessage(1, "quest", "v", "a", ControllerAnswer.WORKS, "left:x>left:y", "auto").toJson().contains("\"preset\":\"auto\""))
        val read = CommunityMappings.parse("""{"mappings":[{"mapping":"left:x>left:y","preset":"auto","works":2}]}""").single()
        assertTrue(read.automatic)
        assertNull(read.preset)
    }

    @Test
    fun `the message carries the contract's fields and nothing else`() {
        val json = ControllerFeedbackMessage(1234, "quest", "0123456789abcdef0123456789abcdef", "0.9.5", ControllerAnswer.PARTIAL, "left:x>left:y", "faces").toJson()
        assertEquals(
            """{"v":1,"appId":1234,"device":"quest","voter":"0123456789abcdef0123456789abcdef","app":"0.9.5","answer":"partial","mapping":"left:x>left:y","preset":"faces"}""",
            json,
        )
        // No preset, or one that is not known: the field is absent.
        assertFalse(ControllerFeedbackMessage(1, "pico", "v", "a", ControllerAnswer.WORKS, "left:x>left:y").toJson().contains("preset"))
        assertFalse(ControllerFeedbackMessage(1, "pico", "v", "a", ControllerAnswer.WORKS, "left:x>left:y", "whatever").toJson().contains("preset"))
        assertTrue(json.length < 2048)
    }

    @Test
    fun `the relay's list of mappings is decoded and what is not as expected is left out`() {
        val text = """{"v":1,"mappings":[
            {"mapping":"left:dpad_up>left:y;left:dpad_down>left:x","preset":"dpad_vertical","works":5,"partial":1,"fails":0},
            {"mapping":"right:a>right:a","preset":null,"works":2,"partial":0,"fails":1},
            {"mapping":"not a mapping","works":9},
            {"works":3},
            "junk"
        ]}"""
        val read = CommunityMappings.parse(text)
        assertEquals(2, read.size)
        assertEquals(ControllerPreset.DPAD_VERTICAL, read[0].preset)
        assertEquals(5, read[0].works)
        assertEquals(1, read[0].partial)
        assertNull(read[1].preset)
        assertEquals(1, read[1].fails)
        assertEquals(emptyList<CommunityMapping>(), CommunityMappings.parse(null))
        assertEquals(emptyList<CommunityMapping>(), CommunityMappings.parse("<html>"))
        assertEquals(emptyList<CommunityMapping>(), CommunityMappings.parse("""{"v":1}"""))
    }

    @Test
    fun `at most ten mappings are kept`() {
        val entries = (1..15).joinToString(",") { """{"mapping":"left:x>left:y","works":$it}""" }
        assertEquals(10, CommunityMappings.parse("""{"mappings":[$entries]}""").size)
    }

    @Test
    fun `a received mapping only applies the controls the game uses, to controls the device has`() {
        val received = CommunityMapping("left:dpad_up>left:y;left:view>left:menu;right:x>right:thumbrest;right:a>right:a", null, 3, 0, 0)
        val changes = received.changesFor(listOf(ref("left:dpad_up"), ref("right:x"), ref("right:b")))
        assertEquals(setOf(ref("left:dpad_up"), ref("right:x")), changes.keys)
        assertEquals(listOf(ref("left:y")), changes.getValue(ref("left:dpad_up")))
        // The thumbrest is no target the player can choose: it is dropped, the control then goes nowhere.
        assertEquals(emptyList<ControlRef>(), changes.getValue(ref("right:x")))
    }

    // The rules of the card.

    private fun custom(text: String) = ControllerFingerprint(text, null, customised = true)
    private val mine = custom("left:x>left:y")

    @Test
    fun `GamePort's own mapping is asked about like the player's, once a run ended after its first sight`() {
        val auto = ControllerFingerprint("left:x>left:y", null, customised = false)
        var book = ControllerFeedbackBook().noted(1, auto.mapping, 100)
        assertFalse(book.shouldAsk(1, auto, 150))
        book = book.sessionEnded(1, 200)
        assertTrue(book.shouldAsk(1, auto, 300))
        book = book.answered(1, auto, ControllerAnswer.WORKS)
        assertFalse(book.shouldAsk(1, auto, 300))
        assertEquals(listOf(PendingControllerAnswer(1, "left:x>left:y", "auto", ControllerAnswer.WORKS)), book.unsent())
        // Switching to a choice that gives the same distribution is no change: nothing is asked again.
        assertFalse(book.noted(1, auto.mapping, 400).sessionEnded(1, 500).shouldAsk(1, custom(auto.mapping), 600))
    }

    @Test
    fun `nothing is asked when the game uses no control`() {
        val book = ControllerFeedbackBook().noted(1, "left:x>left:y", 100).sessionEnded(1, 200)
        assertFalse(book.shouldAsk(1, ControllerFingerprint("", null, customised = true), 300))
    }

    @Test
    fun `the card waits for a run that ended after the mapping changed`() {
        var book = ControllerFeedbackBook().noted(1, mine.mapping, 100)
        assertFalse(book.shouldAsk(1, mine, 150))
        // A run that ended before the change says nothing about it.
        book = book.sessionEnded(1, 90)
        assertFalse(book.shouldAsk(1, mine, 150))
        book = book.sessionEnded(1, 120)
        assertTrue(book.shouldAsk(1, mine, 150))
        // A change after that run starts the wait again.
        book = book.noted(1, "left:x>left:x", 130).noted(1, mine.mapping, 140)
        assertFalse(book.shouldAsk(1, mine, 150))
    }

    @Test
    fun `a mapping not looked at yet is taken as changed just now`() {
        val book = ControllerFeedbackBook().sessionEnded(1, 120)
        assertFalse(book.shouldAsk(1, mine, 150))
    }

    @Test
    fun `an answered mapping is not asked again until it changes`() {
        var book = ControllerFeedbackBook().noted(1, mine.mapping, 100).sessionEnded(1, 120)
        book = book.answered(1, mine, ControllerAnswer.WORKS)
        assertFalse(book.shouldAsk(1, mine, 150))
        assertEquals(ControllerAnswer.WORKS, book.answerFor(1, mine.mapping))
        assertNull(book.answerFor(1, "left:x>left:x"))
        val other = custom("left:x>left:x")
        book = book.noted(1, other.mapping, 160).sessionEnded(1, 170)
        assertTrue(book.shouldAsk(1, other, 180))
    }

    @Test
    fun `later hides the card for the run it was said in and brings it back after the next one`() {
        var book = ControllerFeedbackBook().noted(1, mine.mapping, 100).sessionEnded(1, 120)
        book = book.later(1, mine)
        assertFalse(book.shouldAsk(1, mine, 150))
        book = book.sessionEnded(1, 200)
        assertTrue(book.shouldAsk(1, mine, 210))
    }

    @Test
    fun `later is forgotten when the mapping changes`() {
        var book = ControllerFeedbackBook().noted(1, mine.mapping, 100).sessionEnded(1, 120).later(1, mine)
        val other = custom("left:x>left:x")
        book = book.noted(1, other.mapping, 130).sessionEnded(1, 140)
        assertTrue(book.shouldAsk(1, other, 150))
    }

    @Test
    fun `the last answer replaces the one before and waits to be sent`() {
        var book = ControllerFeedbackBook().answered(7, ControllerFingerprint("left:x>left:y", ControllerPreset.FACE_BUTTONS, customised = true), ControllerAnswer.FAILS)
        assertEquals(listOf(PendingControllerAnswer(7, "left:x>left:y", "faces", ControllerAnswer.FAILS)), book.unsent())
        book = book.answered(7, mine, ControllerAnswer.WORKS)
        assertEquals(listOf(PendingControllerAnswer(7, "left:x>left:y", "faces", ControllerAnswer.WORKS).copy(preset = "")), book.unsent())
        book = book.sent(7, "left:x>left:y", ControllerAnswer.FAILS)
        // The relay has the first answer, which is no longer the player's: the second still waits.
        assertEquals(1, book.unsent().size)
        book = book.sent(7, "left:x>left:y", ControllerAnswer.WORKS)
        assertTrue(book.unsent().isEmpty())
    }

    @Test
    fun `nothing is answered for a game that uses no control`() {
        assertTrue(ControllerFeedbackBook().answered(1, ControllerFingerprint("", null, customised = false), ControllerAnswer.WORKS).unsent().isEmpty())
    }

    @Test
    fun `the book survives being kept as text`() {
        val book = ControllerFeedbackBook().noted(1, mine.mapping, 100).sessionEnded(1, 120).answered(1, mine, ControllerAnswer.PARTIAL).later(2, mine)
        assertEquals(book, ControllerFeedbackBook.fromJson(book.toJson()))
        assertEquals(ControllerFeedbackBook(), ControllerFeedbackBook.fromJson("garbage"))
        assertEquals(ControllerFeedbackBook(), ControllerFeedbackBook.fromJson(null))
    }

    // The vote: "the game works, but not the controls".

    @Test
    fun `works with no controls is a verdict that works carrying the one code`() {
        val book = VerdictBook().answered(5, Verdict.WORKS, "0.9.5", "2026-11-26", send = true, noControls = true)
        val unsent = book.unsent().single()
        assertEquals(Verdict.WORKS, unsent.verdict)
        assertEquals(listOf("no_controls"), unsent.issues)
        val json = VoteMessage(5, unsent.verdict, "0.9.5", "quest", "v", issues = unsent.issues).toJson()
        assertTrue(json.contains("\"verdict\":\"works\"") && json.contains("\"issues\":[\"no_controls\"]"))
    }

    @Test
    fun `a plain works answer carries no code, and no code is kept for another verdict`() {
        assertTrue(VerdictBook().answered(5, Verdict.WORKS, "0.9.5", "2026-11-26", send = true).unsent().single().issues.isEmpty())
        assertTrue(VerdictBook().answered(5, Verdict.FAILS, "0.9.5", "2026-11-26", send = true, noControls = true).unsent().single().issues.isEmpty())
        // A later plain answer replaces the one that carried the code.
        val replaced = VerdictBook().answered(5, Verdict.WORKS, "0.9.5", "d", send = true, noControls = true).answered(5, Verdict.WORKS, "0.9.5", "d", send = true)
        assertTrue(replaced.unsent().single().issues.isEmpty())
    }
}
