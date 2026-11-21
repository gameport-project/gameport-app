package app.gameport.hook;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class EncryptedTicketRequestTest {
    @Test
    public void aRequestHasAnIdAndTheDataTheGameAsked() {
        EncryptedTicketRequest request = EncryptedTicketRequest.of("1700-42", "00ff10aB");
        assertNotNull(request);
        assertEquals("1700-42", request.id);
        assertArrayEquals(new byte[] {0x00, (byte) 0xff, 0x10, (byte) 0xab}, request.userdata);
    }

    @Test
    public void aRequestWithNoDataAsksForNone() {
        assertArrayEquals(new byte[0], EncryptedTicketRequest.of("1-1", "").userdata);
        assertArrayEquals(new byte[0], EncryptedTicketRequest.of("1-1", null).userdata);
        assertArrayEquals(new byte[0], EncryptedTicketRequest.of("1-1", "  ").userdata);
    }

    @Test
    public void theIdIsTrimmed() {
        assertEquals("7-7", EncryptedTicketRequest.of("  7-7 \r", "").id);
    }

    @Test
    public void aRequestWithNoIdIsNotOne() {
        assertNull(EncryptedTicketRequest.of(null, "00"));
        assertNull(EncryptedTicketRequest.of("", "00"));
        assertNull(EncryptedTicketRequest.of("   ", "00"));
    }

    @Test
    public void dataThatIsNotHexadecimalIsRefused() {
        assertNull(EncryptedTicketRequest.of("1-1", "0g"));
        assertNull(EncryptedTicketRequest.of("1-1", "abc"));
        assertNull(EncryptedTicketRequest.of("1-1", "zz"));
        assertNull(EncryptedTicketRequest.of("1-1", "0x10"));
    }

    @Test
    public void dataLongerThanSteamTakesIsRefused() {
        StringBuilder ok = new StringBuilder();
        for (int i = 0; i < EncryptedTicketRequest.MAX_USERDATA; i++) ok.append("ab");
        assertNotNull(EncryptedTicketRequest.of("1-1", ok.toString()));
        assertNull(EncryptedTicketRequest.of("1-1", ok + "ab"));
    }

    @Test
    public void everyByteSurvivesTheRoundTrip() {
        StringBuilder hex = new StringBuilder();
        byte[] all = new byte[256];
        for (int i = 0; i < 256; i++) {
            all[i] = (byte) i;
            hex.append(String.format("%02x", i));
        }
        assertArrayEquals(all, EncryptedTicketRequest.fromHex(hex.toString()));
    }
}
