package app.gameport.core.patch

import app.gameport.core.patch.patches.AppLabelPatch
import app.gameport.core.patch.patches.CloudHookPatch
import app.gameport.core.patch.patches.VersionCodePatch
import app.gameport.core.patch.patches.SteamShimPatch
import app.gameport.core.patch.patches.XrLayerPatch
import app.gameport.core.patch.patches.VrLauncherPatch
import com.android.apksig.ApkVerifier
import com.reandroid.apk.ApkModule
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Runs the real engine on an APK you provide: GAMEPORT_TEST_APK=/path/to/game.apk. */
class ApkPatchEngineTest {
    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun patchesSignsAndKeepsTheRestOfTheApk() {
        val source = System.getenv("GAMEPORT_TEST_APK")?.let(::File)
        assumeTrue("set GAMEPORT_TEST_APK to run", source?.isFile == true)
        val shimBytes = ByteArray(1024) { it.toByte() }
        val output = temp.newFile("patched.apk")
        val started = System.currentTimeMillis()

        ApkPatchEngine(SigningKeyStore(temp.newFolder().resolve("key.p12"))).patch(
            input = source!!,
            output = output,
            patches = listOf(VrLauncherPatch, SteamShimPatch, CloudHookPatch, XrLayerPatch, AppLabelPatch, VersionCodePatch),
            context = PatchContext(steamAppId = 1, steamId = 2, personaName = "test", gameName = "Real Game Name"),
            assets = PatchAssets(BinarySource { shimBytes.inputStream() }, BinarySource { ByteArray(64) { 1 }.inputStream() }, BinarySource { ByteArray(32) { 2 }.inputStream() }),
        )
        println("patched ${source.length() / 1_000_000} MB in ${System.currentTimeMillis() - started} ms")
        System.getenv("GAMEPORT_TEST_OUT")?.let { output.copyTo(File(it), overwrite = true) }

        val result = ApkVerifier.Builder(output).build().verify()
        assertTrue(result.errors.joinToString(), result.isVerified)

        ZipFile(output).use { zip ->
            assertEquals(shimBytes.size.toLong(), zip.getEntry("lib/arm64-v8a/libsteamclient.so").size)
            ZipFile(source).use { original ->
                val expected = original.entries().asSequence().filter { !it.name.startsWith("META-INF/") && it.name != "AndroidManifest.xml" }.count()
                val actual = zip.entries().asSequence().filter { !it.name.startsWith("META-INF/") && it.name != "AndroidManifest.xml" }.count()
                // Five entries are added (shim, its config, hook dex, OpenXR layer and its manifest); an existing shim would be replaced.
                val replaced = if (original.getEntry("lib/arm64-v8a/libsteamclient.so") != null) 1 else 0
                // An APK that GamePort already patched has those entries: they are replaced, not added.
                if (original.getEntry("assets/gameport/steam.cfg") == null) assertEquals(expected + 5 - replaced, actual)
                // Both must be stored: the shim reads its config from the APK and Android maps libraries.
                assertEquals(ZipEntry.STORED, zip.getEntry("assets/gameport/steam.cfg").method)
                assertEquals(ZipEntry.STORED, zip.getEntry("lib/arm64-v8a/libsteamclient.so").method)
            }
        }

        // The launcher patch only touches an activity that already declares a launcher entry.
        val before = ApkModule.loadApkFile(source).androidManifest.toJson().toString()
        val after = ApkModule.loadApkFile(output).androidManifest.toJson().toString()
        // Rewriting the manifest must replace it, not append a second copy of every element.
        assertEquals(Regex("\"name\":\"activity\"").findAll(before).count(), Regex("\"name\":\"activity\"").findAll(after).count())
        assertEquals(Regex("\"name\":\"uses-permission\"").findAll(before).count(), Regex("\"name\":\"uses-permission\"").findAll(after).count())
        if (before.contains("android.intent.category.LAUNCHER")) {
            assertTrue(after.contains("com.oculus.intent.category.VR"))
            assertTrue(after.contains("org.khronos.openxr.intent.category.IMMERSIVE_HMD"))
        }

        // The game gets its Steam name as its label, and a version code raised by the patch generation, once.
        assertTrue(after.contains("Real Game Name"))
        val code = { json: String -> Regex("\"name\":\"versionCode\"[^}]*?\"data\":(\\d+)").find(json)!!.groupValues[1].toInt() }
        // A game GamePort never patched gets the offset; one it already patched keeps its code (never lowered).
        if (before.contains(VersionCodePatch.META_ORIGINAL)) assertTrue(code(after) >= code(before)) else assertEquals(code(before) + VersionCodePatch.offset, code(after))

        // Patched again from the patched APK, the code does not grow a second time.
        val again = temp.newFile("patched-again.apk")
        ApkPatchEngine(SigningKeyStore(temp.newFolder().resolve("key.p12"))).patch(
            input = output,
            output = again,
            patches = listOf(AppLabelPatch, VersionCodePatch),
            context = PatchContext(steamAppId = 1, steamId = 2, personaName = "test", gameName = "Real Game Name"),
            assets = PatchAssets(BinarySource { ByteArray(1).inputStream() }, BinarySource { ByteArray(1).inputStream() }, BinarySource { ByteArray(1).inputStream() }),
        )
        assertEquals(code(after), code(ApkModule.loadApkFile(again).androidManifest.toJson().toString()))
    }
}
