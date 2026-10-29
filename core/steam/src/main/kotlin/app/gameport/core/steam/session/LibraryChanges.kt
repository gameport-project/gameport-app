package app.gameport.core.steam.session

import kotlinx.coroutines.future.await

/** What Steam says changed since a change number: the apps whose record changed, and whether it is too long ago to say. */
internal data class ChangesAnswer(val currentNumber: Int, val changedApps: Set<Int>, val requiresFullUpdate: Boolean)

/**
 * Asks Steam which apps changed since [lastNumber], as its own client does to keep its library up to date: one request, whatever the number of games. The number of the
 * last answer is kept to ask from there the next time.
 */
internal suspend fun SteamSession.changesSince(lastNumber: Int): ChangesAnswer {
    val answer = apps.picsGetChangesSince(lastNumber, true, false).await()
    return ChangesAnswer(answer.currentChangeNumber, answer.appChanges.keys.toSet(), answer.isRequiresFullUpdate || answer.isRequiresFullAppUpdate)
}

/** What to do with the library after Steam's answer: the apps to read again, or all of them, and the number to ask from next time. */
internal data class LibraryChangePlan(val forget: Set<Int>, val forgetAll: Boolean, val number: Int) {
    val needsScan: Boolean get() = forgetAll || forget.isNotEmpty()
}

/**
 * Works out which apps of the library are to be inspected again, from what Steam says changed. Only the games the account has count: a change of any other app on Steam is of no interest.
 * A game that has just got an Android build, or a new one, is among them, as it is flagged as changed. When there was no number yet ([lastNumber] 0) the library has just been
 * read in full, so there is nothing to do but remember the number. When Steam says it is too long ago to tell what changed, everything is read again.
 */
internal fun planLibraryChanges(owned: Set<Int>, answer: ChangesAnswer, lastNumber: Int): LibraryChangePlan = when {
    lastNumber <= 0 -> LibraryChangePlan(emptySet(), false, answer.currentNumber)
    answer.requiresFullUpdate -> LibraryChangePlan(emptySet(), true, answer.currentNumber)
    else -> LibraryChangePlan(answer.changedApps.intersect(owned), false, answer.currentNumber)
}
