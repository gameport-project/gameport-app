package app.gameport.core.patch

import app.gameport.core.patch.patches.StorageTargetPatch
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StorageTargetPatchTest {
    @Test
    fun `a game for Android 13 or later that declares the permission and has expansion files is lowered`() {
        assertTrue(StorageTargetPatch.shouldLower(declaresPermission = true, targetSdk = 36, hasExpansionFiles = true))
        assertTrue(StorageTargetPatch.shouldLower(declaresPermission = true, targetSdk = 33, hasExpansionFiles = true))
    }

    @Test
    fun `a game that does not need it is left alone`() {
        assertFalse(StorageTargetPatch.shouldLower(declaresPermission = false, targetSdk = 36, hasExpansionFiles = true))
        assertFalse(StorageTargetPatch.shouldLower(declaresPermission = true, targetSdk = 36, hasExpansionFiles = false))
        assertFalse(StorageTargetPatch.shouldLower(declaresPermission = true, targetSdk = 32, hasExpansionFiles = true))
        assertFalse(StorageTargetPatch.shouldLower(declaresPermission = true, targetSdk = 29, hasExpansionFiles = true))
        assertFalse(StorageTargetPatch.shouldLower(declaresPermission = true, targetSdk = null, hasExpansionFiles = true))
    }
}
