package app.gameport.core.sync

import org.junit.Assert.assertEquals
import org.junit.Test

class OwnFilesTest {
    @Test
    fun `recognises the folders GamePort keeps in a game`() {
        assertEquals(true, OwnFiles.isOwn("%R%Studio/Game/gameport-backup/2026/Slot_0/a.es3"))
        assertEquals(true, OwnFiles.isOwn("Android/data/pkg/files/gameport/settings/x.sav"))
    }

    @Test
    fun `leaves real saves alone`() {
        assertEquals(false, OwnFiles.isOwn("%R%Studio/Game/Slot_0/a.es3"))
        assertEquals(false, OwnFiles.isOwn("%R%Studio/Game/save_metadata.json"))
    }

    @Test
    fun `the files a game keeps through the cloud api are saves, the shim's settings are not`() {
        assertEquals(false, OwnFiles.isOwn("/Android/data/pkg/files/gameport/Goldberg SteamEmu Saves/2897700/remote/ProgressSaveData.save"))
        assertEquals(true, OwnFiles.isOwn("/Android/data/pkg/files/gameport/Goldberg SteamEmu Saves/settings/user_steam_id.txt"))
        assertEquals(true, OwnFiles.isOwn("/Android/data/pkg/files/gameport-backup/2026/remote/ProgressSaveData.save"))
    }
}
