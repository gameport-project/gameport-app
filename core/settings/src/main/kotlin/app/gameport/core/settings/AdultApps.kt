package app.gameport.core.settings

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update

/**
 * The adult apps seen so far, from the library and from the catalog. A list that is not built from those two (the compatibility page names games the
 * account does not have, a download is listed by number until its game is found) asks here which numbers to leave out while the player may not see
 * adult games.
 */
@Singleton
class AdultApps @Inject constructor(private val age: AgeStore) {
    private val known = MutableStateFlow<Set<Int>>(emptySet())

    fun learn(appIds: Collection<Int>) {
        if (appIds.isNotEmpty()) known.update { it + appIds }
    }

    /** The app ids to leave out of every list: the adult ones seen, as long as the player may not see them. */
    val hidden: Flow<Set<Int>> = combine(known, age.canSeeAdult) { ids, canSee -> if (canSee) emptySet() else ids }

    fun isHidden(appId: Int): Boolean = !age.canSeeAdult.value && appId in known.value
}
