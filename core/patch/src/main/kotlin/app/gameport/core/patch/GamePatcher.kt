package app.gameport.core.patch

import android.content.Context
import app.gameport.core.patch.patches.AppLabelPatch
import app.gameport.core.patch.patches.CloudHookPatch
import app.gameport.core.patch.patches.PatchVersionPatch
import app.gameport.core.patch.patches.VersionCodePatch
import app.gameport.core.patch.patches.SteamShimPatch
import app.gameport.core.patch.patches.XrLayerPatch
import app.gameport.core.patch.patches.StorageTargetPatch
import app.gameport.core.patch.patches.XrLoaderPatch
import app.gameport.core.patch.patches.VrLauncherPatch
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
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
            patches = patches,
            context = patchContext.copy(patchVersion = PatchVersioning.current(context)),
            assets = PatchAssets(
                shim = BinarySource { context.assets.open(SHIM_ASSET) },
                hookDex = BinarySource { context.assets.open(HOOK_ASSET) },
                xrLayer = BinarySource { context.assets.open(XR_LAYER_ASSET) },
                xrLoader = BinarySource { context.assets.open(XR_LOADER_ASSET) },
            ),
        )
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
    val all: List<ApkPatch> = listOf(VrLauncherPatch, SteamShimPatch, CloudHookPatch, XrLayerPatch, XrLoaderPatch, StorageTargetPatch, AppLabelPatch, VersionCodePatch, PatchVersionPatch)

    val recommended: List<ApkPatch> get() = all.filter { it.recommended }
}
