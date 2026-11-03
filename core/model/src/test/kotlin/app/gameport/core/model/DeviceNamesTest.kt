package app.gameport.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeviceNamesTest {
    @Test
    fun `a Quest is named from its model`() {
        assertEquals("Quest 3", DeviceNames.friendly("Quest 3", ""))
        assertEquals("Quest 3S", DeviceNames.friendly("Quest 3S", ""))
        assertEquals("Quest 2", DeviceNames.friendly("Quest 2", ""))
        assertEquals("Quest Pro", DeviceNames.friendly("Quest Pro", ""))
    }

    @Test
    fun `a Quest is named from its codename when the model says nothing`() {
        assertEquals("Quest 3", DeviceNames.friendly("", "eureka"))
        assertEquals("Quest 3S", DeviceNames.friendly("Meta", "panther"))
    }

    @Test
    fun `a Pico is named from its model, the longer name first`() {
        assertEquals("Pico 4", DeviceNames.friendly("PICO 4", ""))
        assertEquals("Pico 4 Ultra", DeviceNames.friendly("PICO 4 Ultra", ""))
        assertEquals("Pico 4 Pro", DeviceNames.friendly("Pico 4 Pro", ""))
    }

    @Test
    fun `a device this does not know has no name`() {
        assertNull(DeviceNames.friendly("SM-X710", "gts9"))
        assertNull(DeviceNames.friendly("", ""))
        assertNull(DeviceNames.friendly(null, null))
    }
}
