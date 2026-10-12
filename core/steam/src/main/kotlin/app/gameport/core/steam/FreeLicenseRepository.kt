package app.gameport.core.steam

import app.gameport.core.steam.session.SteamSessionHolder
import app.gameport.core.steam.session.requestFreeLicense
import javax.inject.Inject
import javax.inject.Singleton

/** What came of asking Steam to add a free app to the account. */
sealed interface FreeLicenseOutcome {
    /** Steam added it: the account has it now. */
    data object Added : FreeLicenseOutcome

    /** Steam says the account already has it. */
    data object AlreadyOwned : FreeLicenseOutcome

    /** Steam did not add it: it is not one it gives for free from here (a promotion, a code, a page of its store). */
    data object NotFree : FreeLicenseOutcome

    /** Nobody is signed in to Steam, or the connection is lost. */
    data object NotConnected : FreeLicenseOutcome

    /** Steam did not answer in time, or answered something GamePort does not know. */
    data object Failed : FreeLicenseOutcome
}

/** Adds a free app or demo to the library of the signed-in account, the way Steam's own client does when the player presses "Play" on it. Never takes a payment. */
interface FreeLicenseRepository {
    suspend fun add(appId: Int): FreeLicenseOutcome
}

/** The apps and packages Steam says it granted, and its answer, read as an outcome for [appId]. */
internal fun freeLicenseOutcome(result: `in`.dragonbra.javasteam.enums.EResult, grantedApps: List<Int>, grantedPackages: List<Int>, appId: Int): FreeLicenseOutcome = when {
    result == `in`.dragonbra.javasteam.enums.EResult.OK && appId in grantedApps -> FreeLicenseOutcome.Added
    // Steam answers OK with nothing granted for an app the account already has, as the client does.
    result == `in`.dragonbra.javasteam.enums.EResult.OK && grantedApps.isEmpty() && grantedPackages.isEmpty() -> FreeLicenseOutcome.AlreadyOwned
    result == `in`.dragonbra.javasteam.enums.EResult.OK -> FreeLicenseOutcome.Added.takeIf { grantedPackages.isNotEmpty() } ?: FreeLicenseOutcome.NotFree
    result == `in`.dragonbra.javasteam.enums.EResult.Timeout -> FreeLicenseOutcome.Failed
    result == `in`.dragonbra.javasteam.enums.EResult.NoConnection -> FreeLicenseOutcome.NotConnected
    else -> FreeLicenseOutcome.NotFree
}

@Singleton
class JavaSteamFreeLicenseRepository @Inject constructor(
    private val sessions: SteamSessionHolder,
    private val library: JavaSteamLibraryRepository,
) : FreeLicenseRepository {
    override suspend fun add(appId: Int): FreeLicenseOutcome {
        val session = sessions.current.value ?: return FreeLicenseOutcome.NotConnected
        val outcome = runCatching { session.requestFreeLicense(appId) }.getOrElse { FreeLicenseOutcome.Failed }
        // The library is read again once the account has the new license, so the page shows it as owned.
        if (outcome == FreeLicenseOutcome.Added) library.rescan()
        return outcome
    }
}
