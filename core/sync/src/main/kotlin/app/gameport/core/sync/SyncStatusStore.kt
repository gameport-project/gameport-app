package app.gameport.core.sync

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** How the last save sync of a game went. [PENDING]: the saves on this device differ from the cloud's and were not sent yet. */
enum class SyncStatus { OK, OFFLINE, FAILED, PENDING }

/** Remembers, per Steam app, the outcome of the last save sync so the game's page can report a problem. */
@Singleton
class SyncStatusStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences("gameport_sync_status", Context.MODE_PRIVATE)
    private val _statuses = MutableStateFlow(read())

    val statuses: StateFlow<Map<Int, SyncStatus>> = _statuses.asStateFlow()

    fun record(appId: Int, status: SyncStatus) {
        if (_statuses.value[appId] == status) return
        prefs.edit().putString(appId.toString(), status.name).apply()
        _statuses.value = _statuses.value + (appId to status)
    }

    fun clear() {
        prefs.edit().clear().apply()
        _statuses.value = emptyMap()
    }

    private fun read(): Map<Int, SyncStatus> = prefs.all.mapNotNull { (key, value) ->
        val appId = key.toIntOrNull() ?: return@mapNotNull null
        val status = runCatching { SyncStatus.valueOf(value.toString()) }.getOrNull() ?: return@mapNotNull null
        appId to status
    }.toMap()
}
