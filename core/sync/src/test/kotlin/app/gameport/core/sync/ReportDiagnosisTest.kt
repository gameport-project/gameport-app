package app.gameport.core.sync

import org.junit.Assert.assertTrue
import org.junit.Test

class ReportDiagnosisTest {
    @Test
    fun `what loaded and what did not is said, and how the last runs ended`() {
        val text = ReportDiagnosis.of(
            libraries = "/data/app/x/lib/arm64/libunity.so\n/data/app/x/lib/arm64/libopenxr_loader.so\n",
            startLog = "I GPHook : plan: NONE",
            previousExit = "time=1 reason=SIGNALED status=9 importance=100\ntime=2 reason=SIGNALED status=9 importance=100\ntime=3 reason=OTHER_13 status=0 importance=400\n",
            dataAgeSeconds = 300,
        )
        assertTrue(text, text.contains("hook of GamePort ran in the game: yes"))
        assertTrue(text, text.contains("OpenXR loader loaded: yes"))
        assertTrue(text, text.contains("OpenXR layer of GamePort loaded: no"))
        assertTrue(text, text.contains("SIGNALED/9 x2"))
        assertTrue(text, text.contains("OTHER_13 x1"))
        assertTrue(text, text.contains("may be from the start of the run"))
    }

    @Test
    fun `nothing handed over is not said as no`() {
        val text = ReportDiagnosis.of("(the game has not handed it over yet)", "", "", null)
        assertTrue(text, text.contains("loaded libraries: not handed over"))
        assertTrue(text, text.contains("unknown (no log handed over)"))
        assertTrue(text, text.contains("last exits: none recorded"))
    }

    @Test
    fun `expansion files announced and missing are said, and what to do`() {
        val text = ReportDiagnosis.of("", "", "", null, ReportDiagnosis.Expansion(declared = true, folder = ReportDiagnosis.Expansion.Folder.READABLE, files = 0))
        assertTrue(text, text.contains("declared by the game: yes; the Android/obb folder is empty"))
        assertTrue(text, text.contains("none are there"))
        assertTrue(text, text.contains("older than 0.7.0"))
    }

    @Test
    fun `expansion files that cannot be listed are not said missing`() {
        val text = ReportDiagnosis.of("", "", "", null, ReportDiagnosis.Expansion(declared = true, folder = ReportDiagnosis.Expansion.Folder.NOT_READABLE, files = 0))
        assertTrue(text, text.contains("cannot tell whether they are there"))
        assertTrue(text, !text.contains("none are there"))
    }

    @Test
    fun `a game that needs none and has none says no more`() {
        val text = ReportDiagnosis.of("", "", "", null, ReportDiagnosis.Expansion(declared = false, folder = ReportDiagnosis.Expansion.Folder.ABSENT, files = 0))
        assertTrue(text, text.contains("declared by the game: no; no Android/obb folder"))
        assertTrue(text, !text.contains("cannot load its data"))
    }

    @Test
    fun `expansion files that are there are counted`() {
        val text = ReportDiagnosis.of("", "", "", null, ReportDiagnosis.Expansion(declared = true, folder = ReportDiagnosis.Expansion.Folder.READABLE, files = 2))
        assertTrue(text, text.contains("2 file(s) in Android/obb"))
        assertTrue(text, !text.contains("cannot load its data"))
    }

    private val mossStart = """
        10-09 16:38:53.935 12220 12311 I GPXR    : xrSuggestInteractionProfileBindings(/interaction_profiles/khr/simple_controller, 6 bindings) -> 0
        10-09 16:38:53.935 12220 12311 I GPXR    : xrSuggestInteractionProfileBindings(/interaction_profiles/oculus/touch_controller, 33 bindings) -> 0
        10-09 16:38:53.935 12220 12311 I GPXR    : xrSuggestInteractionProfileBindings(, 44 bindings) -> -22
        10-09 16:38:54.134 12220 12311 I GPXR    : the game has its own bindings for this device's controllers (/interaction_profiles/oculus/touch_controller): nothing to translate
        10-09 16:38:54.265 12220 12311 I OpenXR_ClientState: [OATS]: IP changed: /user/hand/right/interaction_profiles/oculus/touch_controller, 7
        10-09 16:38:54.265 12220 12311 I OpenXR_ClientState: [OATS]: IP changed: /user/hand/left/interaction_profiles/oculus/touch_controller, 6
        10-09 16:39:55.736 12220 12311 I OpenXR_ClientState: [OATS]: IP changed: /user/hand/right/interaction_profiles/oculus/touch_controller, -1
    """.trimIndent()

    @Test
    fun `the profiles the game suggested, the active one and what the layer did are listed`() {
        val text = ReportDiagnosis.of("", mossStart, "", null)
        assertTrue(text, text.contains("/interaction_profiles/oculus/touch_controller: 33 controls, runtime answer 0"))
        assertTrue(text, text.contains("<unnamed profile>: 44 controls, runtime answer -22 (refused)"))
        assertTrue(text, text.contains("profile the system made active: right /interaction_profiles/oculus/touch_controller, left /interaction_profiles/oculus/touch_controller"))
        assertTrue(text, text.contains("what the layer did: the game has its own bindings for this device's controllers"))
    }

    @Test
    fun `the newer layer lines with the number of actions are read too`() {
        val line = "10-10 11:54:35.589 I GPXR    : xrSuggestInteractionProfileBindings(/interaction_profiles/valve/frame_controller_valve, 44 bindings, 44 actions) -> -22"
        val text = ReportDiagnosis.of("", line, "", null)
        assertTrue(text, text.contains("frame_controller_valve: 44 controls, 44 actions, runtime answer -22 (refused)"))
    }

    @Test
    fun `a log without a line from the layer says so, and no log says nothing about the controllers`() {
        assertTrue(ReportDiagnosis.of("", "I GPHook : plan: NONE", "", null).contains("no line from GamePort's OpenXR layer"))
        assertTrue(!ReportDiagnosis.of("", "", "", null).contains("controllers"))
        assertTrue(!ReportDiagnosis.of("", "(the game has not handed over its log yet)", "", null).contains("controllers"))
    }
}
