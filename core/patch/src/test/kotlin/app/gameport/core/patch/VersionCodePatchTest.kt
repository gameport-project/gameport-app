package app.gameport.core.patch

import app.gameport.core.patch.patches.VersionCodePatch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VersionCodePatchTest {
    private val offset = VersionCodePatch.offset

    @Test
    fun `a game never patched gets the offset`() = assertEquals(328 + offset, VersionCodePatch.raisedCode(328, 328, null))

    @Test
    fun `a game patched before keeps its higher code`() = assertEquals(500, VersionCodePatch.raisedCode(328, 500, null))

    @Test
    fun `an update from Steam is never lower than the installed game`() {
        // Installed with a code of 500; Steam's next build is 329, which would only reach 329 + offset.
        assertEquals(500, VersionCodePatch.raisedCode(329, 329, 500L))
    }

    @Test
    fun `an update that goes beyond the installed code gets the offset`() = assertEquals(1000 + offset, VersionCodePatch.raisedCode(1000, 1000, 500L))

    @Test
    fun `a code that would not fit is left alone`() = assertNull(VersionCodePatch.raisedCode(Int.MAX_VALUE - 1, Int.MAX_VALUE - 1, null))
}
