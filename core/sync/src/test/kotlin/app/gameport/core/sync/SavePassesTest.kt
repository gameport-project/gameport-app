package app.gameport.core.sync

import app.gameport.core.sync.SavePass.ANDROID
import app.gameport.core.sync.SavePass.PC_DOWNLOAD
import app.gameport.core.sync.SavePass.PC_TWO_WAY
import org.junit.Assert.assertEquals
import org.junit.Test

class SavePassesTest {
    @Test
    fun `the android saves are the default`() {
        assertEquals(listOf(ANDROID), SavePasses.of(SaveSpaceMode.ANDROID, hasPc = true, launch = true, knowsPasses = true))
    }

    @Test
    fun `using the windows saves reads them first at launch, then the android ones`() {
        assertEquals(listOf(PC_DOWNLOAD, ANDROID), SavePasses.of(SaveSpaceMode.USE_PC, hasPc = true, launch = true, knowsPasses = true))
    }

    @Test
    fun `using the windows saves never looks at them again while the game is played`() {
        assertEquals(listOf(ANDROID), SavePasses.of(SaveSpaceMode.USE_PC, hasPc = true, launch = false, knowsPasses = true))
    }

    @Test
    fun `sending to the windows saves goes both ways at launch and while playing, then the android ones`() {
        assertEquals(listOf(PC_TWO_WAY, ANDROID), SavePasses.of(SaveSpaceMode.USE_PC_AND_SEND, hasPc = true, launch = true, knowsPasses = true))
        assertEquals(listOf(PC_TWO_WAY, ANDROID), SavePasses.of(SaveSpaceMode.USE_PC_AND_SEND, hasPc = true, launch = false, knowsPasses = true))
    }

    @Test
    fun `a game without a windows copy, or a hook that does one pass, only gets the android saves`() {
        assertEquals(listOf(ANDROID), SavePasses.of(SaveSpaceMode.USE_PC, hasPc = false, launch = true, knowsPasses = true))
        assertEquals(listOf(ANDROID), SavePasses.of(SaveSpaceMode.USE_PC_AND_SEND, hasPc = true, launch = true, knowsPasses = false))
    }

    @Test
    fun `only the windows passes are windows`() {
        assertEquals(listOf(false, true, true), listOf(ANDROID, PC_DOWNLOAD, PC_TWO_WAY).map { it.isPc })
    }

    private fun rule(local: String, prefix: String, pc: String? = null) = app.gameport.core.model.SaveRule(local, "*", true, prefix, pc)

    @Test
    fun `the signature does not depend on the order of the rules`() {
        val a = rule("a", "%X%a")
        val b = rule("b", "%X%b")

        assertEquals(SaveRulesSignature.of(listOf(a, b)), SaveRulesSignature.of(listOf(b, a)))
    }

    @Test
    fun `a studio that adds a root override changes the signature`() {
        val apart = listOf(rule("@internal/pkg/files/saves", "%AndroidInternalData%pkg/files/saves", pc = "%WinAppDataLocalLow%Studio/Game/Saves"))
        val linked = listOf(rule("Android/data/pkg/files/saves", "%WinAppDataLocalLow%Studio/Game/Saves"))

        assertEquals(false, SaveRulesSignature.of(apart) == SaveRulesSignature.of(linked))
    }

    @Test
    fun `the choice to use the pc saves goes when the studio links them itself, and stays while they are apart`() {
        assertEquals(SaveSpaceMode.ANDROID, SaveRulesSignature.modeAfterChange(SaveSpaceMode.USE_PC, stillApart = false))
        assertEquals(SaveSpaceMode.ANDROID, SaveRulesSignature.modeAfterChange(SaveSpaceMode.USE_PC_AND_SEND, stillApart = false))
        assertEquals(SaveSpaceMode.USE_PC, SaveRulesSignature.modeAfterChange(SaveSpaceMode.USE_PC, stillApart = true))
    }

    @Test
    fun `a remembered signature that differs always makes the agreement forgotten`() {
        assertEquals(true, SaveRulesSignature.mustForgetAgreement(before = "old", readDifferently = false))
        assertEquals(true, SaveRulesSignature.mustForgetAgreement(before = "old", readDifferently = true))
    }

    @Test
    fun `with no signature remembered, a scan that read the rules differently makes the agreement forgotten`() {
        assertEquals(true, SaveRulesSignature.mustForgetAgreement(before = null, readDifferently = true))
    }

    @Test
    fun `a game seen for the first time has nothing to forget`() {
        assertEquals(false, SaveRulesSignature.mustForgetAgreement(before = null, readDifferently = false))
    }
}
