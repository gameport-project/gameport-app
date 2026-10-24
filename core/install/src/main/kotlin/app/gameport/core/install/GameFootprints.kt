package app.gameport.core.install

import android.content.Context
import android.os.Build
import android.os.Environment
import app.gameport.core.model.GameFootprint
import app.gameport.core.model.InstallState
import app.gameport.core.model.UnfinishedDownload
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Measures what games take on the device, for the page that manages them. It reads folders, which can take a moment for games of several gigabytes, so it works
 * off the main thread and the page asks for it when it opens, not while it scrolls.
 */
@Singleton
class GameFootprints @Inject constructor(
    @ApplicationContext private val context: Context,
    private val installed: InstalledGames,
    private val packages: PackageGateway,
    private val handoff: ExpansionHandoff,
    private val installer: GameInstallRepository,
    private val builds: InstalledBuilds,
) {
    /** The footprint of every installed game, by Steam app id. A game whose package is gone from the device is left out. */
    suspend fun ofInstalled(): Map<Int, GameFootprint> = withContext(Dispatchers.IO) {
        installed.all().mapNotNull { (appId, packageName) ->
            if (packages.isInstalled(packageName)) appId to footprint(appId, packageName) else null
        }.toMap()
    }

    /** The downloads that stopped and left files behind: not the ones running, and not the ones of a game that is installed. */
    suspend fun unfinished(): List<UnfinishedDownload> = withContext(Dispatchers.IO) {
        installer.leftoverIds().mapNotNull { appId ->
            val state = installer.observe(appId).first()
            if (state is InstallState.Interrupted || state is InstallState.Failed) UnfinishedDownload(appId, treeBytes(installer.downloadDirectory(appId))) else null
        }
    }

    /** The free space where games are installed, in bytes. */
    fun freeBytes(): Long = (context.getExternalFilesDir(null) ?: context.filesDir).usableSpace

    private fun footprint(appId: Int, packageName: String): GameFootprint {
        val apks = packages.apkFilesOf(packageName)
        // The folder of the install holds the APKs, the libraries that were extracted and the compiled code: all of it is the game's. Where the folder cannot be listed,
        // the APK files alone are counted, so the figure is never lower than what is certain.
        val folder = apks.firstOrNull()?.parentFile
        val app = maxOf(folder?.let(::treeBytes) ?: 0L, apks.sumOf { it.length() })
        val kept = handoff.staged(packageName).sumOf { it.length() } + treeBytes(installer.downloadDirectory(appId))
        // Measured when the folder can be read; otherwise what was written down at the install, which is exact unless the files were removed since.
        val measured = expansionBytes(packageName)
        val recorded = if (measured == null) builds.get(appId)?.expansionBytes else null
        return GameFootprint(appBytes = app, expansionBytes = measured ?: recorded, keptBytes = kept, expansionRecorded = recorded != null)
    }

    private fun expansionBytes(packageName: String): Long? = ExpansionSize.of(Environment.getExternalStorageDirectory(), packageName, hasAllFilesAccess())

    private fun hasAllFilesAccess(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.R || Environment.isExternalStorageManager()

    private fun treeBytes(root: File): Long = ExpansionSize.treeBytes(root)
}

/** The size of a game's expansion files, told apart from not being able to look. */
internal object ExpansionSize {
    /**
     * The expansion files in Android/obb under [storageRoot], or null when this app cannot look there. Reading nothing is not the same as reading an empty folder: from Android
     * 11 on, other apps' folders there are hidden (the listing fails), and zero would then say the game has no expansion. So a size is only given when the game's folder, or the
     * one that holds it (with access to all files), can really be listed.
     */
    fun of(storageRoot: File, packageName: String, hasAllFilesAccess: Boolean): Long? {
        val folder = File(storageRoot, "Android/obb/$packageName")
        val readable = folder.list() != null || (hasAllFilesAccess && File(storageRoot, "Android/obb").list() != null)
        return if (readable) treeBytes(folder) else null
    }

    fun treeBytes(root: File): Long = root.takeIf { it.exists() }?.walkTopDown()?.filter { it.isFile }?.sumOf { it.length() } ?: 0L
}
