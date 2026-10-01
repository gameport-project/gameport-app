package app.gameport.core.install

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import app.gameport.core.device.DeviceProfile
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/** Talks to Android's package installer. Both install and uninstall may ask the user to confirm. */
@Singleton
class PackageGateway @Inject constructor(
    @ApplicationContext private val context: Context,
    private val device: DeviceProfile,
) {
    private val packageManager get() = context.packageManager

    /** False on a device without VR: VR patches make no sense there. */
    val isHeadset: Boolean get() = device.isHeadset

    fun packageNameOf(apk: File): String? = runCatching {
        packageManager.getPackageArchiveInfo(apk.absolutePath, 0)?.packageName
    }.getOrNull()

    /** The APK files of an installed package: the base and its splits. */
    fun apkFilesOf(packageName: String): List<File> = runCatching {
        val info = packageManager.getApplicationInfo(packageName, 0)
        (listOf(info.sourceDir) + info.splitSourceDirs.orEmpty()).map(::File)
    }.getOrDefault(emptyList())

    /** An integer from the package's manifest metadata, or null when it has none. */
    fun metaDataInt(packageName: String, key: String): Int? = runCatching {
        packageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA)
            .metaData?.takeIf { it.containsKey(key) }?.getInt(key)
    }.getOrNull()

    /** True when the package asks for [permission] in its manifest and has not been given it. */
    fun requestsButLacks(packageName: String, permission: String): Boolean = runCatching {
        val info = packageManager.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS)
        val index = info.requestedPermissions?.indexOf(permission) ?: -1
        index >= 0 && (info.requestedPermissionsFlags?.get(index) ?: 0) and android.content.pm.PackageInfo.REQUESTED_PERMISSION_GRANTED == 0
    }.getOrDefault(false)

    /** When the package was first installed (epoch millis), or null when it is not installed. */
    fun installTimeOf(packageName: String): Long? = runCatching { packageManager.getPackageInfo(packageName, 0).firstInstallTime }.getOrNull()

    /** The version code of the installed package, or null when it is not installed. */
    fun versionCodeOf(packageName: String): Long? = runCatching { packageManager.getPackageInfo(packageName, 0).longVersionCode }.getOrNull()

    fun isInstalled(packageName: String): Boolean = runCatching {
        packageManager.getPackageInfo(packageName, 0)
        true
    }.getOrDefault(false)

    /**
     * The intent that starts the game. On a headset it carries the platform's immersive category,
     * which is what makes the system open the game as a VR app rather than in a flat window.
     */
    fun launchIntent(packageName: String, immersive: Boolean = true): Intent? =
        packageManager.getLaunchIntentForPackage(packageName)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (immersive) device.vrPlatform?.immersiveLauncherCategories?.forEach(::addCategory)
        }

    /** Installs one APK, or a base APK with its splits, in a single session. */
    suspend fun install(apks: List<File>): Outcome {
        val installer = packageManager.packageInstaller
        val sessionId = withContext(Dispatchers.IO) {
            val id = installer.createSession(PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL))
            installer.openSession(id).use { session ->
                apks.forEach { apk ->
                    apk.inputStream().use { input ->
                        session.openWrite(apk.name, 0, apk.length()).use { output ->
                            input.copyTo(output)
                            session.fsync(output)
                        }
                    }
                }
            }
            id
        }
        return awaitStatus(sessionId) { intentSender -> installer.openSession(sessionId).use { it.commit(intentSender) } }
    }

    suspend fun uninstall(packageName: String): Outcome =
        awaitStatus(packageName.hashCode()) { intentSender -> packageManager.packageInstaller.uninstall(packageName, intentSender) }

    private suspend fun awaitStatus(token: Int, start: (android.content.IntentSender) -> Unit): Outcome =
        suspendCancellableCoroutine { continuation ->
            val action = "${context.packageName}.PACKAGE_RESULT.$token"
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(receiverContext: Context, intent: Intent) {
                    when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
                        PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                            @Suppress("DEPRECATION")
                            val confirmation = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                            confirmation?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)?.let(context::startActivity)
                        }
                        else -> {
                            context.unregisterReceiver(this)
                            val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                            if (continuation.isActive) {
                                continuation.resume(
                                    when (status) {
                                        PackageInstaller.STATUS_SUCCESS -> Outcome.Success
                                        PackageInstaller.STATUS_FAILURE_ABORTED -> Outcome.Cancelled
                                        PackageInstaller.STATUS_FAILURE_CONFLICT -> Outcome.Conflict
                                        else -> Outcome.Failure(message)
                                    },
                                )
                            }
                        }
                    }
                }
            }
            ContextCompat.registerReceiver(context, receiver, IntentFilter(action), ContextCompat.RECEIVER_NOT_EXPORTED)
            continuation.invokeOnCancellation { runCatching { context.unregisterReceiver(receiver) } }

            val pending = PendingIntent.getBroadcast(
                context,
                token,
                Intent(action).setPackage(context.packageName),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
            )
            runCatching { start(pending.intentSender) }.onFailure {
                runCatching { context.unregisterReceiver(receiver) }
                if (continuation.isActive) continuation.resume(Outcome.Failure(it.message))
            }
        }

    sealed interface Outcome {
        data object Success : Outcome

        /** The user dismissed the confirmation. */
        data object Cancelled : Outcome

        /** Another version signed with a different key is installed. */
        data object Conflict : Outcome

        data class Failure(val reason: String?) : Outcome
    }

    /** Emits whenever any package is added or removed on the device. */
    fun packageChanges(): kotlinx.coroutines.flow.Flow<Unit> = kotlinx.coroutines.flow.callbackFlow {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                trySend(Unit)
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addDataScheme("package")
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        trySend(Unit)
        awaitClose { context.unregisterReceiver(receiver) }
    }
}
