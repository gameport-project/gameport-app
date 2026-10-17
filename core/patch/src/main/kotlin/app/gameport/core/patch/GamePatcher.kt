package app.gameport.core.patch

import android.content.Context
import app.gameport.core.patch.patches.AppLabelPatch
import app.gameport.core.patch.patches.CloudHookPatch
import app.gameport.core.patch.patches.OvrportLibsSource
import app.gameport.core.patch.patches.PatchVersionPatch
import app.gameport.core.patch.patches.PlatformLibsPatch
import app.gameport.core.patch.patches.VersionCodePatch
import app.gameport.core.patch.patches.SteamShimPatch
import app.gameport.core.patch.patches.XrLayerPatch
import app.gameport.core.patch.patches.StorageTargetPatch
import app.gameport.core.patch.patches.XrLoaderPatch
import app.gameport.core.patch.patches.VrLauncherPatch
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Patches a downloaded game so it runs as a Steam game under the signed-in account. */
@Singleton
class GamePatcher @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val engine = ApkPatchEngine(SigningKeyStore(File(context.filesDir, "patching/signing.p12")))

    suspend fun patch(
        input: File,
        output: File,
        patchContext: PatchContext,
        patches: List<ApkPatch> = PatchCatalog.recommended,
    ) = withContext(Dispatchers.IO) {
        engine.patch(
            input = input,
            output = output,
            patches = withPlatformLibs(patches),
            context = patchContext.copy(patchVersion = PatchVersioning.current(context)),
            assets = PatchAssets(
                shim = BinarySource { context.assets.open(SHIM_ASSET) },
                hookDex = BinarySource { context.assets.open(HOOK_ASSET) },
                xrLayer = BinarySource { context.assets.open(XR_LAYER_ASSET) },
                xrLoader = BinarySource { context.assets.open(XR_LOADER_ASSET) },
            ),
        )
    }

    /**
     * The platform-libs patch needs OVRPort's pinned archive. It is fetched and checked here (this
     * runs off the main thread) and the patch is rebuilt around it. When the user asked for the
     * patch and the archive cannot be had, patching stops: injecting nothing while reporting
     * success would be worse than an error.
     */
    private fun withPlatformLibs(patches: List<ApkPatch>): List<ApkPatch> {
        if (patches.none { it is PlatformLibsPatch }) return patches
        val source = OvrportLibsSource(File(context.filesDir, "patching/ovrport-libs"))
        if (!source.prepare()) throw IOException("The platform libraries could not be downloaded or verified")
        return patches.map { if (it is PlatformLibsPatch) PlatformLibsPatch(source) else it }
    }

    private companion object {
        const val SHIM_ASSET = "shim/arm64-v8a/libsteamclient.so"
        const val HOOK_ASSET = "hook/classes.dex"
        const val XR_LAYER_ASSET = "xrlayer/arm64-v8a/libXrApiLayer_gameport.so"
        const val XR_LOADER_ASSET = "xrloader/arm64-v8a/libopenxr_loader.so"
    }
}

/** Every patch GamePort knows, in the order they are applied. */
object PatchCatalog {
    val all: List<ApkPatch> = listOf(VrLauncherPatch, SteamShimPatch, CloudHookPatch, XrLayerPatch, XrLoaderPatch, StorageTargetPatch, AppLabelPatch, VersionCodePatch, PatchVersionPatch, PlatformLibsPatch())

    val recommended: List<ApkPatch> get() = all.filter { it.recommended }
}
