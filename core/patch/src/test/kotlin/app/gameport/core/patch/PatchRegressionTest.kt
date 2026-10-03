package app.gameport.core.patch

import com.android.apksig.ApkVerifier
import com.reandroid.apk.ApkModule
import com.reandroid.json.JSONArray
import com.reandroid.json.JSONObject
import java.io.File
import java.util.zip.ZipEntry
import org.apache.commons.compress.archivers.zip.ZipFile
import org.junit.Assert.fail
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Patches the skeleton of every game GamePort installed and compares what comes out with what came out the last time
 * the games were known to work. A difference is not necessarily a bug, but it names the game to try again on the device.
 *
 * Needs GAMEPORT_REGRESSION_DIR=<tools/patch-regression>, which holds `skeletons/` (made by make_skeletons.py) and `golden/`.
 * GAMEPORT_REGRESSION_BLESS=1 records the current results as the new references.
 */
class PatchRegressionTest {
    @get:Rule
    val temp = TemporaryFolder()

    private val assets = File("src/main/assets")

    private fun patchAssets() = PatchAssets(
        shim = BinarySource { File(assets, "shim/arm64-v8a/libsteamclient.so").inputStream() },
        hookDex = BinarySource { File(assets, "hook/classes.dex").inputStream() },
        xrLayer = BinarySource { File(assets, "xrlayer/arm64-v8a/libXrApiLayer_gameport.so").inputStream() },
        xrLoader = BinarySource { File(assets, "xrloader/arm64-v8a/libopenxr_loader.so").inputStream() },
    )

    /** What a patched APK is made of, for comparing: the manifest, one line per entry, and what is wrong with it on its own. */
    private class Summary(val manifest: List<String>, val entries: Map<String, String>, val problems: List<String>)

    private fun patch(input: File, appId: Int, name: String): File {
        val output = temp.newFile()
        ApkPatchEngine(SigningKeyStore(temp.newFolder().resolve("key.p12"))).patch(
            input = input,
            output = output,
            patches = PatchCatalog.recommended,
            context = PatchContext(steamAppId = appId, steamId = 1, personaName = "test", gameName = name),
            assets = patchAssets(),
        )
        return output
    }

    private fun summarize(apk: File): Summary {
        val problems = mutableListOf<String>()
        // The devices GamePort targets run Android 10 or later, where the v2 and v3 signatures are enough.
        val verified = ApkVerifier.Builder(apk).setMinCheckedPlatformVersion(MIN_PLATFORM).build().verify()
        if (!verified.isVerified) problems += "signature: " + verified.errors.joinToString()
        val manifest = runCatching {
            ApkModule.loadApkFile(apk).androidManifest.toJson().toString(2).lines()
        }.getOrElse { problems += "manifest unreadable: ${it.message}"; emptyList() }
        val entries = sortedMapOf<String, String>()
        ZipFile.builder().setFile(apk).get().use { zip ->
            for (entry in zip.entries) {
                entries[entry.name] = "method=${entry.method} size=${entry.size} crc=${java.lang.Long.toHexString(entry.crc)}"
                // A library must be stored and sit on a page boundary, or Android cannot map it.
                if (entry.name.startsWith("lib/") && entry.name.endsWith(".so")) {
                    if (entry.method != ZipEntry.STORED) problems += "${entry.name} is compressed"
                    else if (entry.dataOffset % LIBRARY_ALIGNMENT != 0L) problems += "${entry.name} is not aligned"
                }
            }
        }
        return Summary(manifest, entries, problems)
    }

    private fun toJson(summary: Summary) = JSONObject().apply {
        put("manifest", JSONArray().also { array -> summary.manifest.forEach { array.put(it) } })
        put("entries", JSONObject().also { json -> summary.entries.forEach { (name, line) -> json.put(name, line) } })
    }

    private fun fromJson(file: File): Summary {
        val json = JSONObject(file.readText())
        val manifest = json.getJSONArray("manifest").let { array -> (0 until array.length()).map { array.getString(it) } }
        val entries = json.getJSONObject("entries").let { node -> node.keySet().sorted().associateWith { node.getString(it) } }
        return Summary(manifest, entries, emptyList())
    }

    /** What changed between the reference and now, in a few words; empty when nothing did. */
    private fun differences(before: Summary, now: Summary): List<String> {
        val result = mutableListOf<String>()
        if (before.manifest != now.manifest) {
            val nowLines = now.manifest.toSet()
            val beforeLines = before.manifest.toSet()
            val removed = before.manifest.filter { it !in nowLines }
            val added = now.manifest.filter { it !in beforeLines }
            result += "manifest: ${removed.size} line(s) gone, ${added.size} new" +
                (removed.take(3).map { " - ${it.trim()}" } + added.take(3).map { " + ${it.trim()}" }).joinToString("")
        }
        val gone = before.entries.keys - now.entries.keys
        val new = now.entries.keys - before.entries.keys
        val changed = before.entries.keys.intersect(now.entries.keys).filter { before.entries[it] != now.entries[it] }
        if (gone.isNotEmpty()) result += "files gone: ${gone.take(5).joinToString()}${more(gone.size)}"
        if (new.isNotEmpty()) result += "files new: ${new.take(5).joinToString()}${more(new.size)}"
        if (changed.isNotEmpty()) result += "files changed: ${changed.take(5).joinToString()}${more(changed.size)}"
        return result
    }

    private fun more(size: Int) = if (size > 5) " and ${size - 5} more" else ""

    @Test
    fun everyGameIsPatchedAsBefore() {
        val root = System.getenv("GAMEPORT_REGRESSION_DIR")?.let(::File)
        assumeTrue("set GAMEPORT_REGRESSION_DIR to run", root?.isDirectory == true)
        val bless = System.getenv("GAMEPORT_REGRESSION_BLESS") == "1"
        val skeletons = File(root, "skeletons").listFiles { f -> f.extension == "apk" }.orEmpty().sortedBy { it.name }
        assumeTrue("no skeleton in $root/skeletons: run make_skeletons.py", skeletons.isNotEmpty())
        val golden = File(root, "golden").apply { mkdirs() }

        val report = mutableListOf<String>()
        var failures = 0
        for (skeleton in skeletons) {
            val packageName = skeleton.nameWithoutExtension
            val appId = runCatching { JSONObject(File(skeleton.parentFile, "$packageName.json").readText()).getInt("appId") }.getOrDefault(1)
            val line = try {
                val once = patch(skeleton, appId, packageName)
                val summary = summarize(once)
                // Patching what is already patched must change nothing more.
                val twice = summarize(patch(once, appId, packageName))
                val problems = summary.problems + twice.problems.filter { it !in summary.problems } +
                    differences(summary, twice).map { "patching twice is not stable: $it" }
                val reference = File(golden, "$packageName.json")
                when {
                    bless -> {
                        reference.writeText(toJson(summary).toString(2))
                        if (problems.isEmpty()) "BLESSED $packageName" else "BLESSED $packageName, but: ${problems.joinToString("; ")}"
                    }
                    !reference.isFile -> "NO REFERENCE $packageName: run with GAMEPORT_REGRESSION_BLESS=1 once the games are known to work"
                    else -> (problems + differences(fromJson(reference), summary)).let { found ->
                        if (found.isEmpty()) "OK   $packageName" else "DIFF $packageName: ${found.joinToString(" | ")}"
                    }
                }
            } catch (e: Throwable) {
                "FAIL $packageName: ${e::class.simpleName}: ${e.message}"
            }
            if (!line.startsWith("OK") && !line.startsWith("BLESSED $packageName") || line.contains(", but:")) failures++
            report += line
        }
        File(root, "last-report.txt").writeText(report.joinToString("\n") + "\n")
        println(report.joinToString("\n", prefix = "\nPATCH REGRESSION\n"))
        if (failures > 0) fail("$failures of ${skeletons.size} game(s) differ or fail:\n" + report.filter { !it.startsWith("OK") }.joinToString("\n"))
    }

    private companion object {
        const val LIBRARY_ALIGNMENT = 16 * 1024L
        const val MIN_PLATFORM = 29
    }
}
