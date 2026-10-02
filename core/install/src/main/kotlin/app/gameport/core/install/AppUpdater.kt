package app.gameport.core.install

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import app.gameport.core.model.AppRelease
import app.gameport.core.model.AppUpdateFailure
import app.gameport.core.model.AppUpdateState
import app.gameport.core.model.AppVersion
import app.gameport.core.settings.UserSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Looks for a newer GamePort and installs it on request.
 *
 * - The check reads the public "latest release" address of the project, which redirects to the tag of the
 *   newest release: no GitHub API, so no request quota to run out of. Automatic checks happen when the app
 *   opens, at most as often as the player chose (4 hours by default); the settings page can check at any time.
 * - Nothing is downloaded or installed until the player asks. Android's own installer then asks to confirm.
 * - A release is only accepted if it is signed like the installed app, so a different build can never
 *   replace it; and a development build is never offered a release.
 * - It never runs together with work on games (see [UpdateGate]).
 */
@Singleton
class AppUpdater @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: UserSettings,
    private val packages: PackageGateway,
    private val gate: UpdateGate,
    private val auth: app.gameport.core.steam.SteamAuthRepository,
) {
    private val prefs = context.getSharedPreferences("gameport_app_update", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val checking = Mutex()
    private val lookup = OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
        .connectTimeout(10, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS).build()
    private val downloader = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).build()

    private val info = context.packageManager.getPackageInfo(context.packageName, 0)

    /** The version of GamePort that is running. */
    val installedVersion: String = info.versionName.orEmpty()
    private val installedCode: Long = info.longVersionCode

    /** A development build (signed with the shared debug key) is not offered releases: Android would refuse them. */
    val canUpdateInPlace: Boolean = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE == 0

    private val _state = MutableStateFlow(restore())
    val state: StateFlow<AppUpdateState> = _state.asStateFlow()

    /** Why an update cannot start now (a game is being installed, saves are syncing), or null. */
    val blocker: Flow<UpdateGate.Blocker?> = gate.blocker

    /** Called when the app opens: checks, unless the player turned it off or the last check is recent. */
    fun checkIfDue() {
        val hours = settings.updateCheckHours.value
        // Offline mode keeps GamePort from contacting anything on its own.
        if (hours <= 0 || auth.offline.value) return
        if (System.currentTimeMillis() - prefs.getLong(KEY_CHECKED, 0L) < hours * HOUR_MS) return
        check(manual = false)
    }

    /** Looks for a newer version now. [manual] shows the failure when there is no connection. */
    fun check(manual: Boolean = true) {
        scope.launch { checkNow(manual) }
    }

    private suspend fun checkNow(manual: Boolean) {
        if (_state.value is AppUpdateState.Downloading || _state.value is AppUpdateState.Installing) return
        if (!checking.tryLock()) return
        try {
            val before = _state.value
            if (manual) _state.value = AppUpdateState.Checking
            try {
                val release = latestRelease()
                val now = System.currentTimeMillis()
                prefs.edit().putLong(KEY_CHECKED, now).putString(KEY_TAG, release?.tag).putString(KEY_NOTES, release?.notes).apply()
                _state.value = stateFor(release, now)
            } catch (e: IOException) {
                _state.value = if (manual) AppUpdateState.Failed(null, AppUpdateFailure.OFFLINE) else before
            }
        } finally {
            checking.unlock()
        }
    }

    /** The newest release: its tag from the redirect of the "latest" address, then its notes. Null when there is none. */
    private fun latestRelease(): AppRelease? {
        val request = Request.Builder().url("$PROJECT/releases/latest").head().build()
        val tag = lookup.newCall(request).execute().use { response ->
            if (response.code !in 300..399) return null
            response.header("Location")?.substringAfterLast('/')
        } ?: return null
        val version = AppVersion.versionOfTag(tag) ?: return null
        val notes = runCatching {
            lookup.newCall(Request.Builder().url("$RAW/$tag/docs/releases/$tag.md").build()).execute().use { it.body?.string()?.takeIf { _ -> it.isSuccessful } }
        }.getOrNull()
        return AppRelease(version, tag, notes)
    }

    private fun stateFor(release: AppRelease?, checkedAt: Long): AppUpdateState {
        val code = release?.let { AppVersion.codeOf(it.version) }
        return if (release != null && code != null && code > installedCode) AppUpdateState.Available(release, checkedAt) else AppUpdateState.UpToDate(checkedAt)
    }

    private fun restore(): AppUpdateState {
        val checkedAt = prefs.getLong(KEY_CHECKED, 0L)
        if (checkedAt == 0L) return AppUpdateState.Idle
        val tag = prefs.getString(KEY_TAG, null)
        val release = tag?.let { t -> AppVersion.versionOfTag(t)?.let { AppRelease(it, t, prefs.getString(KEY_NOTES, null)) } }
        return stateFor(release, checkedAt)
    }

    /** Downloads the available release and hands it to Android to install over this app. */
    fun update() {
        val release = (_state.value as? AppUpdateState.Available)?.release ?: return
        if (!canUpdateInPlace || gate.currentBlocker() != null || gate.updating.value) return
        scope.launch { install(release) }
    }

    private suspend fun install(release: AppRelease) {
        gate.setUpdating(true)
        var file: File? = null
        try {
            _state.value = AppUpdateState.Downloading(release, 0f)
            file = download(release)
            if (!signedLikeThisApp(file)) throw UpdateFailure(AppUpdateFailure.DIFFERENT_SIGNATURE)
            _state.value = AppUpdateState.Installing(release)
            _state.value = when (val outcome = packages.install(listOf(file))) {
                PackageGateway.Outcome.Success -> AppUpdateState.UpToDate(System.currentTimeMillis())
                PackageGateway.Outcome.Cancelled -> AppUpdateState.Available(release, prefs.getLong(KEY_CHECKED, 0L))
                PackageGateway.Outcome.Conflict -> AppUpdateState.Failed(release, AppUpdateFailure.REFUSED)
                is PackageGateway.Outcome.Failure -> AppUpdateState.Failed(release, AppUpdateFailure.OTHER).also { android.util.Log.w(TAG, "update not installed: ${outcome.reason}") }
            }
        } catch (e: UpdateFailure) {
            _state.value = AppUpdateState.Failed(release, e.reason)
        } catch (e: IOException) {
            _state.value = AppUpdateState.Failed(release, AppUpdateFailure.DOWNLOAD)
        } finally {
            file?.delete()
            gate.setUpdating(false)
        }
    }

    private fun download(release: AppRelease): File {
        val directory = File(context.cacheDir, "app-update").apply { deleteRecursively(); mkdirs() }
        val file = File(directory, "GamePort-${release.tag}.apk")
        val request = Request.Builder().url("$PROJECT/releases/download/${release.tag}/GamePort-${release.tag}.apk").build()
        downloader.newCall(request).execute().use { response ->
            val body = response.body
            if (!response.isSuccessful || body == null) throw UpdateFailure(AppUpdateFailure.DOWNLOAD)
            val total = body.contentLength()
            if (total > 0 && directory.usableSpace < total * 2) throw UpdateFailure(AppUpdateFailure.NOT_ENOUGH_SPACE)
            var received = 0L
            var lastReported = 0f
            body.byteStream().use { input ->
                file.outputStream().use { output ->
                    val buffer = ByteArray(BUFFER)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        received += read
                        if (total > 0) {
                            val progress = received.toFloat() / total
                            if (progress - lastReported >= 0.01f) {
                                lastReported = progress
                                _state.value = AppUpdateState.Downloading(release, progress)
                            }
                        }
                    }
                }
            }
            if (total > 0 && received != total) throw UpdateFailure(AppUpdateFailure.DOWNLOAD)
        }
        return file
    }

    /** True when the downloaded file is signed with one of the keys of the installed app. */
    private fun signedLikeThisApp(apk: File): Boolean {
        val manager = context.packageManager
        fun digests(signers: Array<android.content.pm.Signature>?) = signers.orEmpty().map { MessageDigest.getInstance("SHA-256").digest(it.toByteArray()).toList() }.toSet()
        val own = digests(manager.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES).signingInfo?.apkContentsSigners)
        val downloaded = digests(manager.getPackageArchiveInfo(apk.absolutePath, PackageManager.GET_SIGNING_CERTIFICATES)?.signingInfo?.apkContentsSigners)
        return downloaded.isNotEmpty() && own.intersect(downloaded).isNotEmpty()
    }

    private class UpdateFailure(val reason: AppUpdateFailure) : Exception()

    private companion object {
        const val TAG = "GPUpdate"
        const val PROJECT = "https://github.com/gameport-project/gameport-app"
        const val RAW = "https://raw.githubusercontent.com/gameport-project/gameport-app"
        const val KEY_CHECKED = "checked_at"
        const val KEY_TAG = "latest_tag"
        const val KEY_NOTES = "latest_notes"
        const val BUFFER = 64 * 1024

        const val HOUR_MS = 60L * 60 * 1000
    }
}
