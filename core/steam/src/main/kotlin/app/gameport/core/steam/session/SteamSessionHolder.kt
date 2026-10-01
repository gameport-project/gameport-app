package app.gameport.core.steam.session

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Shares the signed-in [SteamSession] between the auth and library repositories. */
@Singleton
class SteamSessionHolder @Inject constructor() {
    private val _current = MutableStateFlow<SteamSession?>(null)
    val current: StateFlow<SteamSession?> = _current.asStateFlow()

    fun set(session: SteamSession?) {
        _current.value = session
    }
}
