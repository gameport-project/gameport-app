package app.gameport.core.steam.session

import app.gameport.core.steam.FreeLicenseOutcome
import app.gameport.core.steam.freeLicenseOutcome
import `in`.dragonbra.javasteam.enums.EResult
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.future.await
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withTimeout

/** How long Steam has to answer a request for a free license. */
private const val ANSWER_TIMEOUT_MS = 20_000L

/** How long the account's list of licenses has to show the new one before the library is read again anyway. */
private const val LICENSE_TIMEOUT_MS = 10_000L

/**
 * Asks Steam to add the free app [appId] to the account, as its client does on "Play" for a free game or a demo. Steam answers with the apps and the packages it
 * granted; the call returns once the account's list of licenses holds the new package too, so the scan that follows sees it.
 */
internal suspend fun SteamSession.requestFreeLicense(appId: Int): FreeLicenseOutcome {
    val answer = try {
        withTimeout(ANSWER_TIMEOUT_MS) { apps.requestFreeLicense(appId).await() }
    } catch (_: TimeoutCancellationException) {
        return FreeLicenseOutcome.Failed
    }
    val outcome = freeLicenseOutcome(answer.result ?: EResult.Fail, answer.grantedApps, answer.grantedPackages, appId)
    if (outcome == FreeLicenseOutcome.Added && answer.grantedPackages.isNotEmpty()) {
        // Steam sends the new list of licenses on its own, around the time it answers: wait for the package to be in it, and go on without it after a while.
        withTimeoutOrNull(LICENSE_TIMEOUT_MS) { licenses.first { list -> list.orEmpty().any { it.packageID in answer.grantedPackages } } }
    }
    return outcome
}
