package app.gameport.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class StorageAccessTest {
    @Test
    fun `a game that does not lack the permission has nothing to ask`() {
        assertEquals(StorageAccess.NOT_NEEDED, StorageAccess.of(lacksIt = false, targetSdk = 34, deviceSdk = 34))
        assertEquals(StorageAccess.NOT_NEEDED, StorageAccess.of(lacksIt = false, targetSdk = null, deviceSdk = 34))
    }

    @Test
    fun `a game built for Android 12 or older can be given it`() {
        assertEquals(StorageAccess.GRANTABLE, StorageAccess.of(lacksIt = true, targetSdk = 32, deviceSdk = 34))
        assertEquals(StorageAccess.GRANTABLE, StorageAccess.of(lacksIt = true, targetSdk = 29, deviceSdk = 34))
    }

    @Test
    fun `a game built for Android 13 on a device of Android 13 or later cannot be given it`() {
        assertEquals(StorageAccess.UNFIXABLE, StorageAccess.of(lacksIt = true, targetSdk = 33, deviceSdk = 34))
        assertEquals(StorageAccess.UNFIXABLE, StorageAccess.of(lacksIt = true, targetSdk = 34, deviceSdk = 33))
    }

    @Test
    fun `on a device older than Android 13 the permission is still the player's to give`() {
        assertEquals(StorageAccess.GRANTABLE, StorageAccess.of(lacksIt = true, targetSdk = 34, deviceSdk = 29))
    }

    @Test
    fun `Play is never shut for good, after three visits to the settings that gave nothing the permission is taken for one the device does not offer`() {
        assertEquals(StorageAccess.GRANTABLE, StorageAccess.of(lacksIt = true, targetSdk = 32, deviceSdk = 34, settingsOpened = StorageAccess.MAX_SETTINGS_VISITS - 1))
        assertEquals(StorageAccess.UNFIXABLE, StorageAccess.of(lacksIt = true, targetSdk = 32, deviceSdk = 34, settingsOpened = StorageAccess.MAX_SETTINGS_VISITS))
        // Once it is given, the visits do not matter.
        assertEquals(StorageAccess.NOT_NEEDED, StorageAccess.of(lacksIt = false, targetSdk = 32, deviceSdk = 34, settingsOpened = 9))
    }

    @Test
    fun `when what the game was built for is not known nothing is claimed`() {
        assertEquals(StorageAccess.UNFIXABLE, StorageAccess.of(lacksIt = true, targetSdk = null, deviceSdk = 34))
    }
}
