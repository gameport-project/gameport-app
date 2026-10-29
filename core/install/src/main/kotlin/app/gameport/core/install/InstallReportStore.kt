package app.gameport.core.install

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * The report saved by itself for each install that failed, by game. It is kept here, next to the installs, so every page that shows a
 * failure can say where the file went without knowing who writes it (`InstallReports` does, and is the only one that should).
 */
@Singleton
class InstallReportStore @Inject constructor() {
    private val _saved = MutableStateFlow<Map<Int, String>>(emptyMap())

    /** The file name of the report saved for each game whose install failed. */
    val saved: StateFlow<Map<Int, String>> = _saved.asStateFlow()

    fun put(appId: Int, fileName: String) = _saved.update { it + (appId to fileName) }

    /** The game is being tried again: the file of the try before is no longer what the player should be pointed at. */
    fun forget(appId: Int) = _saved.update { it - appId }
}
