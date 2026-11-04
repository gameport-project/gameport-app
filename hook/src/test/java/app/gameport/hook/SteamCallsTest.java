package app.gameport.hook;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import org.junit.Test;

public class SteamCallsTest {
    private static ByteBuffer table(int slots, long startedMs) {
        ByteBuffer out = ByteBuffer.allocate(24 + slots * 104).order(ByteOrder.LITTLE_ENDIAN);
        out.put(new byte[] {'G', 'P', 'S', 'C'});
        out.putInt(1);
        out.putInt(slots);
        out.putInt(slots);
        out.putLong(startedMs);
        return out;
    }

    private static void slot(ByteBuffer out, String text, int count, long firstMs, long lastMs, long totalUs, int maxUs, int timed) {
        byte[] bytes = new byte[64];
        byte[] name = text.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        System.arraycopy(name, 0, bytes, 0, Math.min(63, name.length));
        out.put(bytes);
        out.putInt(count);
        out.putInt(0);
        out.putLong(firstMs);
        out.putLong(lastMs);
        out.putLong(totalUs);
        out.putInt(maxUs);
        out.putInt(timed);
    }

    @Test
    public void the_calls_come_in_the_order_each_was_first_made_with_their_counts() {
        ByteBuffer data = table(2, 1_000_000);
        slot(data, "Steam_Apps::BIsSubscribed", 12, 1_030_000, 1_045_000, 0, 0, 0);
        slot(data, "Steam_User::GetAuthSessionTicket %i", 3, 1_000_400, 1_020_000, 6_000, 3_100, 3);
        String text = SteamCalls.render(data.array());
        assertTrue(text, text.indexOf("GetAuthSessionTicket") < text.indexOf("BIsSubscribed"));
        assertTrue(text, text.contains("      3x  +   0.4 s  +  20.0 s  Steam_User::GetAuthSessionTicket %i  [3 timed, average 2.0 ms, longest 3.1 ms]"));
        assertTrue(text, text.contains("     12x  +  30.0 s  +  45.0 s  Steam_Apps::BIsSubscribed"));
    }

    @Test
    public void a_file_that_is_not_a_table_gives_nothing() {
        assertEquals("", SteamCalls.render(null));
        assertEquals("", SteamCalls.render(new byte[10]));
        byte[] wrong = table(1, 0).array();
        wrong[0] = 'X';
        assertEquals("", SteamCalls.render(wrong));
    }

    @Test
    public void the_start_of_the_run_that_wrote_a_table_is_told_and_a_file_that_is_not_a_table_has_none() {
        assertEquals(1_793_000_123_456L, SteamCalls.startedMs(table(1, 1_793_000_123_456L).array()));
        assertEquals(-1, SteamCalls.startedMs(null));
        assertEquals(-1, SteamCalls.startedMs(new byte[10]));
        byte[] wrong = table(1, 5).array();
        wrong[1] = 'X';
        assertEquals(-1, SteamCalls.startedMs(wrong));
    }

    @Test
    public void a_table_cut_short_by_a_kill_is_read_as_far_as_it_goes() {
        ByteBuffer data = table(2, 0);
        slot(data, "first call", 1, 100, 100, 0, 0, 0);
        byte[] cut = java.util.Arrays.copyOf(data.array(), 24 + 104 + 30);
        assertTrue(SteamCalls.render(cut).contains("first call"));
    }
}
