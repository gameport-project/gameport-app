package app.gameport.core.sync

import org.junit.Assert.assertFalse
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

    @Test
    fun `a game that was started and never answered is said so, with the checks of the maker`() {
        val checks = ReportDiagnosis.vendorChecks(
            permissions = listOf("android.permission.INTERNET", "com.pvr.tobactivate.permission.AUTH_CHECK"),
            metaKeys = listOf("unity.splash-mode", "pvr.app.id"),
            apkEntries = listOf("lib/arm64-v8a/libpxrplatformloader.so", "lib/arm64-v8a/libil2cpp.so"),
        )
        assertTrue(checks.toString(), checks.size == 3 && checks.all { it.startsWith("Pico") })
        val text = ReportDiagnosis.of("", "", "", null, launch = ReportDiagnosis.Launch(launchedAtMillis = 1_000_000, dataAtMillis = null, nowMillis = 1_060_000), vendorChecks = checks)
        assertTrue(text, text.contains("last launch from GamePort: 60 s before this report"))
        assertTrue(text, text.contains("its hook handed something over): no"))
        assertTrue(text, text.contains("The checks below may explain it"))
        assertTrue(text, text.contains("pvr.app.id"))
    }

    @Test
    fun `data older than the launch is not an answer, newer data is`() {
        fun say(data: Long?) = ReportDiagnosis.of("", "", "", null, launch = ReportDiagnosis.Launch(launchedAtMillis = 5_000, dataAtMillis = data, nowMillis = 9_000))
        assertTrue(say(1_000).contains("handed something over): no"))
        assertTrue(say(6_000).contains("handed something over): yes"))
        assertTrue(!say(6_000).contains("nothing came back"))
    }

    @Test
    fun `no launch recorded is said, and no maker check is said when there is none`() {
        val text = ReportDiagnosis.of("", "", "", null, launch = ReportDiagnosis.Launch(null, null, 1_000))
        assertTrue(text, text.contains("launch from GamePort: none recorded"))
        assertTrue(text, !text.contains("checks built into the game"))
        assertTrue(ReportDiagnosis.vendorChecks(listOf("android.permission.INTERNET"), listOf("unity.splash-mode"), listOf("lib/arm64-v8a/libunity.so")).isEmpty())
    }

    @Test
    fun `on a Pico a game that never answered gets the line about the illegal signature even without a marker`() {
        val text = ReportDiagnosis.of("", "", "", null, launch = ReportDiagnosis.Launch(1_000, null, 2_000, onPico = true))
        assertTrue(text, text.contains("Verification failed: illegal signature"))
        assertTrue(text, text.contains("no Pico marker"))
        assertTrue(text, !ReportDiagnosis.of("", "", "", null, launch = ReportDiagnosis.Launch(1_000, null, 2_000)).contains("Verification failed"))
    }

    @Test
    fun `the report says whether GamePort has the access to all files and can write the obb folder`() {
        val text = ReportDiagnosis.of("", "", "", null, ReportDiagnosis.Expansion(declared = true, folder = ReportDiagnosis.Expansion.Folder.ABSENT, files = 0, allFilesAccess = false, obbFolderWritable = false))
        assertTrue(text, text.contains("access to all files given to GamePort: no"))
        assertTrue(text, text.contains("Android/obb folder can be written by GamePort: no"))
        val given = ReportDiagnosis.of("", "", "", null, ReportDiagnosis.Expansion(declared = true, folder = ReportDiagnosis.Expansion.Folder.READABLE, files = 3, allFilesAccess = true, obbFolderWritable = true))
        assertTrue(given, given.contains("access to all files given to GamePort: yes"))
    }

    @Test
    fun `the controls left out are said by how much they matter, and an essential one is a warning in front`() {
        val dispensableOnly = "10-15 19:48:25.603 24833 24937 I GPXR    : controls left out of /interaction_profiles/oculus/touch_controller: essential [ ] | essential plus [ ] | dispensable [ left/input/thumbrest/force ]\n" +
            "10-15 19:48:25.603 24833 24937 I GPXR    : xrSuggestInteractionProfileBindings(/interaction_profiles/oculus/touch_controller, 43 bindings, 43 actions) -> 0"
        val text = ReportDiagnosis.of("", dispensableOnly, "", null)
        assertTrue(text, text.contains("essential: none; essential plus: none; dispensable: left/input/thumbrest/force"))
        assertTrue(text, !text.contains("WARNING"))
        val worse = "10-15 19:48:25.603 24833 24937 I GPXR    : controls left out of /interaction_profiles/oculus/touch_controller: essential [ right/input/a/click ] | essential plus [ left/input/trigger/curl_meta ] | dispensable [ ]"
        val bad = ReportDiagnosis.of("", worse, "", null)
        assertTrue(bad, bad.startsWith("WARNING: the runtime refused ESSENTIAL controls of the game for /interaction_profiles/oculus/touch_controller: right/input/a/click"))
        assertTrue(bad, bad.contains("essential plus: left/input/trigger/curl_meta"))
    }

    @Test
    fun `a layer that the log of the start shows is said loaded even when the list of libraries no longer holds it`() {
        val text = ReportDiagnosis.of(
            libraries = "/data/app/x/lib/arm64/libopenxr_loader.so\n",
            startLog = "I GPHook : plan: NONE\nI OpenXR-Loader: ApiLayerInterface::LoadApiLayers succeeded loading layer XR_APILAYER_GAMEPORT_compat\nI GPXR : GamePort OpenXR layer active; seated mode off",
            previousExit = "",
            dataAgeSeconds = 4,
        )
        assertTrue(text, text.contains("OpenXR layer of GamePort loaded: yes, at the start of the run"))
    }

    @Test
    fun `a layer in neither the list nor the log is said not loaded`() {
        val text = ReportDiagnosis.of("/data/app/x/lib/arm64/libunity.so\n", "I GPHook : plan: NONE", "", 4)
        assertTrue(text, text.contains("OpenXR layer of GamePort loaded: no"))
    }

    @Test
    fun `a crash of long ago is dated, and said not to explain a run that started after it`() {
        val crashAt = 1_000_000_000_000L
        val now = crashAt + 28 * 3_600_000L
        val text = ReportDiagnosis.of(
            libraries = "", startLog = "", dataAgeSeconds = 4,
            previousExit = "time=$crashAt reason=CRASH_NATIVE status=0 importance=1000\n",
            launch = ReportDiagnosis.Launch(launchedAtMillis = now - 30_000, dataAtMillis = now - 4_000, nowMillis = now),
        )
        assertTrue(text, text.contains("ended on ${ReportDiagnosis.stamp(crashAt)}, 28 h before this report (made ${ReportDiagnosis.stamp(now)}"))
        assertTrue(text, text.contains("started again since"))
    }

    @Test
    fun `a crash just before the launch of the report is not said to be old news`() {
        val crashAt = 1_000_000_000_000L
        val text = ReportDiagnosis.of(
            libraries = "", startLog = "", dataAgeSeconds = 4,
            previousExit = "time=$crashAt reason=CRASH_NATIVE status=0 importance=1000\n",
            launch = ReportDiagnosis.Launch(launchedAtMillis = crashAt - 60_000, dataAtMillis = crashAt - 50_000, nowMillis = crashAt + 10_000),
        )
        assertTrue(text, text.contains("ended on ${ReportDiagnosis.stamp(crashAt)}, 10 s before this report"))
        assertTrue(text, !text.contains("started again since"))
    }

    @Test
    fun `stretches of time are given in the unit a reader weighs them in`() {
        assertTrue(ReportDiagnosis.ago(45_000) == "45 s")
        assertTrue(ReportDiagnosis.ago(10 * 60_000L) == "10 min")
        assertTrue(ReportDiagnosis.ago(28 * 3_600_000L) == "28 h")
        assertTrue(ReportDiagnosis.ago(5 * 86_400_000L) == "5 days")
    }

    @Test
    fun `the digest of the log gives its facts at once`() {
        val digest = """
            lines seen: 900, kept: 800, lines of the engine's own log tag (UE, UE4, Unity): 0

            OpenXR session states reached: idle, ready, synchronized, visible
            focused reached: no

            Frame rate (the runtime's line, once a second): median 5, lowest tenth 1, over 40 s, target 72

            Sign-in problems: 3 line(s), the first different ones:
              11-04 13:41:30.000 E Unity : Steam sign-in failed (invalid token)

            Exceptions and errors named in the log, most frequent first:
              300x  (first at 13:41:31.061)  W System.err: java.lang.NoClassDefFoundError: Failed resolution of: Lx;
        """.trimIndent()
        val text = ReportDiagnosis.of("/data/app/lib/arm64/libUnreal.so\n", "", "", null, digest = digest)
        assertTrue(text, text.contains("OpenXR session reached focus: no (states seen: idle, ready, synchronized, visible)"))
        assertTrue(text, text.contains("frame rate: median 5 of 72, lowest tenth 1, over 40 s (far under the target)"))
        assertTrue(text, text.contains("sign-in problems in the log: 3 line(s)"))
        assertTrue(text, text.contains("its engine wrote no line to the system log"))
        assertTrue(text, text.contains("exception named most often in the log: 300x"))
    }

    @Test
    fun `no digest adds nothing`() {
        val text = ReportDiagnosis.of("", "", "", null)
        assertFalse(text, text.contains("frame rate:"))
        assertFalse(text, text.contains("OpenXR session reached focus"))
    }

    @Test
    fun `a game that never asked Steam for a ticket is told so`() {
        val calls = """
            Calls the game made to Steam through the stand-in of GamePort, in the order each was first made. The counts cover the whole run.

                  1x  +   0.1 s  +   0.1 s  Steam_Client::CreateSteamPipe
                 12x  +   0.4 s  +  45.2 s  Steam_Apps::BIsSubscribed
        """.trimIndent()
        val text = ReportDiagnosis.of("", "", "", null, steamCalls = calls)
        assertTrue(text, text.contains("Steam tickets asked by the game: none"))
    }

    @Test
    fun `the tickets the game asked for and was given are counted with their services`() {
        val calls = """
                  3x  +   0.4 s  +  20.0 s  Steam_User::GetAuthSessionTicket %i
                  3x  +   0.4 s  +  20.0 s  session ticket read from GamePort  [3 timed, average 2.0 ms, longest 3.1 ms]
                  2x  +   1.0 s  +   9.0 s  web ticket asked for service: epiconlineservices
                  1x  +   1.0 s  +   1.0 s  web ticket NOT given by GamePort (the request fails)  [1 timed, average 0.1 ms, longest 0.1 ms]
        """.trimIndent()
        val text = ReportDiagnosis.of("", "", "", null, steamCalls = calls)
        assertTrue(text, text.contains("session ticket x3, web ticket for \"epiconlineservices\" x2; given to it: 3, NOT given: 1"))
    }
}
