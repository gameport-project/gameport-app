package app.gameport.core.steam

import android.content.Context
import app.gameport.core.model.SteamRegions
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The region Steam is asked to serve downloads from: 0 (automatic) or one of [SteamRegions]. It is given to Steam when GamePort connects,
 * so a change counts from the next connection ([SteamAuthRepository.reconnect] makes one at once).
 */
@Singleton
class DownloadRegion @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("gameport_download", Context.MODE_PRIVATE)
    private val _cellId = MutableStateFlow(prefs.getInt(KEY, SteamRegions.AUTOMATIC).takeIf { it == SteamRegions.AUTOMATIC || SteamRegions.nameOf(it) != null } ?: SteamRegions.AUTOMATIC)

    val cellId: StateFlow<Int> = _cellId.asStateFlow()

    fun select(cellId: Int) {
        if (cellId != SteamRegions.AUTOMATIC && SteamRegions.nameOf(cellId) == null) return
        _cellId.value = cellId
        prefs.edit().putInt(KEY, cellId).apply()
    }

    private companion object {
        const val KEY = "region"
    }
}
