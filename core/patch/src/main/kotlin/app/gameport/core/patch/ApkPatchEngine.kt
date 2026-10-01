package app.gameport.core.patch

import com.android.apksig.ApkSigner
import com.reandroid.apk.ApkModule
import com.reandroid.archive.ArchiveFile
import com.reandroid.archive.io.ZipFileInput
import java.io.File

/**
 * Applies [ApkPatch]es to an APK and signs the result. Only the manifest is decoded; every other
 * entry (game data, dex, native libraries) is copied through untouched, which keeps multi-gigabyte
 * games manageable.
 */
class ApkPatchEngine(private val signingKeys: SigningKeyStore) {
    fun patch(
        input: File,
        output: File,
        patches: List<ApkPatch>,
        context: PatchContext,
        assets: PatchAssets,
        onStep: (String) -> Unit = {},
    ) {
        val unsigned = File(output.parentFile, output.name + ".unsigned")
        try {
            val archive = ArchiveFile(ZipFileInput(input))
            val apk = ApkModule("base", archive.createZipEntryMap()).apply { setCloseable(archive) }
            val session = PatchSession(apk, findHookDex(input))
            val manifest: ByteArray?
            try {
                patches.forEach { patch ->
                    onStep(patch.id)
                    patch.apply(session, context, assets)
                }
                manifest = session.manifestBytes()
            } finally {
                apk.close()
            }

            onStep("write")
            ZipRewriter.rewrite(
                input = input,
                output = unsigned,
                replaced = manifest?.let { mapOf(MANIFEST to it) }.orEmpty(),
                stored = session.additions,
                // The old signature is void once anything changed; the new one replaces it.
                dropIf = { (it.startsWith("META-INF/") && SIGNATURE_FILE.matches(it)) || it in session.removals },
            )

            onStep("sign")
            ApkSigner.Builder(listOf(signingKeys.signerConfig()))
                .setInputApk(unsigned)
                .setOutputApk(output)
                .setMinSdkVersion(MIN_SDK)
                // v2/v3 are enough from Android 7; the entries were aligned by ZipRewriter and
                // must stay where they are, or native libraries stop being mappable.
                .setV1SigningEnabled(false)
                .setAlignmentPreserved(true)
                .setLibraryPageAlignmentBytes(LIBRARY_PAGE_ALIGNMENT)
                .build()
                .sign()
        } finally {
            unsigned.delete()
        }
    }

    /**
     * The dex that holds GamePort's hook when the APK was patched before, so patching again
     * replaces it instead of adding a second copy of the same classes.
     */
    private fun findHookDex(input: File): String? =
        org.apache.commons.compress.archivers.zip.ZipFile.builder().setFile(input).get().use { zip ->
            zip.entries.toList()
                .filter { DEX_ENTRY.matches(it.name) && it.size in 1..MAX_HOOK_DEX_BYTES }
                .firstOrNull { entry -> zip.getInputStream(entry).use { it.readBytes() }.contains(HOOK_MARKER) }
                ?.name
        }

    private fun ByteArray.contains(needle: ByteArray): Boolean {
        outer@ for (start in 0..size - needle.size) {
            for (i in needle.indices) if (this[start + i] != needle[i]) continue@outer
            return true
        }
        return false
    }

    private companion object {
        val DEX_ENTRY = Regex("classes\\d*\\.dex")
        const val MAX_HOOK_DEX_BYTES = 2L * 1024 * 1024
        val HOOK_MARKER = "Lapp/gameport/hook/GamePortHookProvider;".toByteArray()
        const val MIN_SDK = 29
        const val LIBRARY_PAGE_ALIGNMENT = 16 * 1024
        const val MANIFEST = "AndroidManifest.xml"
        val SIGNATURE_FILE = Regex("META-INF/[^/]+\\.(SF|RSA|DSA|EC|MF)")
    }
}
