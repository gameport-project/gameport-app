package app.gameport.hook;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Locale;

/**
 * Reads the table of the calls a game made to Steam, which the Steam stand-in (the shim) keeps in a file of the game's folder (steam-calls.bin) and writes into memory as the
 * game runs, so that it is on disk when the game is killed. The table holds only what the code of each call says and how many times, when and how long: never a value.
 *
 * Layout (little endian): a 24-byte header (the letters GPSC, version, slots, used, start of the run in milliseconds) and then slots of 104 bytes (the text of the call, 64 bytes
 * ending in zero; count; a reserved word; first and last time in milliseconds; total and longest time in microseconds for the timed ones; how many were timed).
 *
 * Plain Java, so it can be tested on a computer.
 */
final class SteamCalls {
    private static final int HEADER = 24;
    private static final int SLOT = 104;

    private static final class Call {
        String text;
        long count;
        long firstMs;
        long lastMs;
        long totalUs;
        long maxUs;
        long timed;
    }

    private SteamCalls() {
    }

    /** The table as text for a report, the calls in the order each was first made; empty when [data] is not a table. */
    static String render(byte[] data) {
        if (data == null || data.length < HEADER + SLOT) return "";
        ByteBuffer in = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        if (in.get(0) != 'G' || in.get(1) != 'P' || in.get(2) != 'S' || in.get(3) != 'C') return "";
        long used = in.getInt(12) & 0xFFFFFFFFL;
        long startedMs = in.getLong(16);
        long room = (data.length - HEADER) / SLOT;
        int count = (int) Math.min(Math.min(used, room), in.getInt(8) & 0xFFFFFFFFL);
        ArrayList<Call> calls = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            int at = HEADER + i * SLOT;
            Call call = new Call();
            int end = 0;
            while (end < 64 && data[at + end] != 0) end++;
            call.text = new String(data, at, end, java.nio.charset.StandardCharsets.UTF_8);
            if (call.text.isEmpty()) continue;
            call.count = in.getInt(at + 64) & 0xFFFFFFFFL;
            call.firstMs = in.getLong(at + 72);
            call.lastMs = in.getLong(at + 80);
            call.totalUs = in.getLong(at + 88);
            call.maxUs = in.getInt(at + 96) & 0xFFFFFFFFL;
            call.timed = in.getInt(at + 100) & 0xFFFFFFFFL;
            calls.add(call);
        }
        if (calls.isEmpty()) return "";
        Collections.sort(calls, (a, b) -> Long.compare(a.firstMs, b.firstMs));
        StringBuilder out = new StringBuilder();
        out.append("Calls the game made to Steam through the stand-in of GamePort, in the order each was first made. The counts cover the whole run.\n");
        out.append("Only what each call is and how often is kept: no value (account, name, ticket) is ever recorded. Times are seconds after the start of the run.\n\n");
        for (Call call : calls) {
            out.append(String.format(Locale.ROOT, "%7dx  +%6.1f s  +%6.1f s  %s", call.count, (call.firstMs - startedMs) / 1000.0, (call.lastMs - startedMs) / 1000.0, call.text));
            if (call.timed > 0) {
                out.append(String.format(Locale.ROOT, "  [%d timed, average %.1f ms, longest %.1f ms]", call.timed, call.totalUs / 1000.0 / call.timed, call.maxUs / 1000.0));
            }
            out.append('\n');
        }
        return out.toString();
    }
}
