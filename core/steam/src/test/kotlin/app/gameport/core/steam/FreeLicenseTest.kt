package app.gameport.core.steam

import `in`.dragonbra.javasteam.enums.EResult
import org.junit.Assert.assertEquals
import org.junit.Test

class FreeLicenseTest {
    private fun outcome(result: EResult, apps: List<Int> = emptyList(), packages: List<Int> = emptyList(), appId: Int = 7) = freeLicenseOutcome(result, apps, packages, appId)

    @Test
    fun `an app Steam says it granted is added`() {
        assertEquals(FreeLicenseOutcome.Added, outcome(EResult.OK, apps = listOf(7), packages = listOf(70)))
    }

    @Test
    fun `an app granted with other ones is still added`() {
        assertEquals(FreeLicenseOutcome.Added, outcome(EResult.OK, apps = listOf(3, 7)))
    }

    @Test
    fun `nothing granted with an ok answer is an app the account already has`() {
        assertEquals(FreeLicenseOutcome.AlreadyOwned, outcome(EResult.OK))
    }

    @Test
    fun `a package granted without the app is told as added, as the account has what Steam gave`() {
        assertEquals(FreeLicenseOutcome.Added, outcome(EResult.OK, apps = listOf(3), packages = listOf(70)))
    }

    @Test
    fun `apps granted but not this one, and no package, is not what was asked`() {
        assertEquals(FreeLicenseOutcome.NotFree, outcome(EResult.OK, apps = listOf(3)))
    }

    @Test
    fun `a refusal from Steam is an app that is not free from here`() {
        for (result in listOf(EResult.Fail, EResult.AccessDenied, EResult.InvalidParam, EResult.Blocked, EResult.NoMatch)) {
            assertEquals(result.name, FreeLicenseOutcome.NotFree, outcome(result))
        }
    }

    @Test
    fun `a timeout and a lost connection are told apart from a refusal`() {
        assertEquals(FreeLicenseOutcome.Failed, outcome(EResult.Timeout))
        assertEquals(FreeLicenseOutcome.NotConnected, outcome(EResult.NoConnection))
    }
}
