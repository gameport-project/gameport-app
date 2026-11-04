package app.gameport.hook;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * What the whole log of a run says, counted on every line as it goes by and not on the few thousand lines a report can keep: the exceptions and how many times each
 * came, the lines that repeat the most, the sign-in problems, the states the OpenXR session went through, the frame rate and when the main steps happened.
 *
 * It also tells which lines are worth keeping. A line that repeats the same words hundreds of times (an exception printed eight lines long, a runtime that tells the
 * same thing each frame) filled the kept lines in a few seconds and hid the rest of the run: after a few copies the others are only counted.
 *
 * Plain Java, so it can be tested on a computer.
 */
final class StreamDigest {
    /** How many copies of one line (numbers set aside) are kept before the next ones are only counted. */
    static final int KEPT_COPIES = 40;
    private static final int MAX_LINE_KINDS = 4000;
    private static final int MAX_EXCEPTION_KINDS = 60;
    private static final int MAX_AUTH_LINES = 6;
    private static final int MAX_SAMPLE = 220;
    private static final int TOP_REPEATED = 15;
    private static final int TOP_EXCEPTIONS = 25;
    private static final int MAX_MEMORY_LINES = 200;
    private static final long MEMORY_STEP_KB = 50 * 1024;
    private static final long MEMORY_EVERY_MS = 60_000;

    /** Tags whose lines say what GamePort does or how the process ended: never held back, however often they come. */
    private static final String[] ALWAYS_KEPT_TAGS = {"GPXR", "GPHook", "GPVerdict", "DEBUG", "libc", "AndroidRuntime"};

    private static final Pattern EXCEPTION = Pattern.compile("\\b((?:[a-z][\\w]*\\.)+[A-Z][\\w$]*(?:Exception|Error)|[A-Z][A-Za-z0-9]*(?:Exception|Error))\\b");
    private static final Pattern SESSION_STATE = Pattern.compile("session state -> (\\d)");
    private static final Pattern FPS = Pattern.compile("FPS=(\\d+)/(\\d+)");
    private static final Pattern STALE = Pattern.compile("Stale=(\\d+)");
    /** A second counts for the stability only after this many seconds with the session in focus: the loading and the start of the menus come first. */
    static final int STABILITY_WARMUP_SECONDS = 60;
    /** Fewer counted seconds than this say nothing about a game, and no number is given. */
    static final int STABILITY_MIN_SECONDS = 180;
    private static final int FOCUSED = 5;
    private static final Pattern TIME = Pattern.compile("^(\\d\\d)-(\\d\\d) (\\d\\d):(\\d\\d):(\\d\\d)\\.(\\d\\d\\d)");
    private static final String[] SESSION_STATE_NAMES = {"", "idle", "ready", "synchronized", "visible", "focused", "stopping", "loss pending", "exiting"};

    private static final class Kind {
        int count;
        String first = "";
        String sample = "";
        String tag = "";
    }

    private final Map<String, Kind> lines = new HashMap<>();
    private final Map<String, Kind> exceptions = new LinkedHashMap<>();
    private final ArrayList<String> authLines = new ArrayList<>();
    private final TreeSet<Integer> sessionStates = new TreeSet<>();
    private final Map<String, String> milestones = new LinkedHashMap<>();
    private final int[] fpsHistogram = new int[1024];
    private int fpsSamples;
    private int fpsTarget;
    private int sessionState;
    private int focusedSeconds;
    private int countedSeconds;
    private int heldSeconds;
    private final ArrayList<String> memory = new ArrayList<>();

    private long seen;
    private long kept;
    private long engineLines;
    private long authTotal;
    private long firstMillis = -1;
    private long previousClock = -1;
    private long dayOffset;
    private String firstTime = "";
    private String lastTime = "";
    private long lastMemoryAt = -1;
    private long lastMemoryKb = -1;

    /** Looks at one line of the log (the "threadtime" format) and says whether it is worth keeping among the kept lines. */
    synchronized boolean scan(String line) {
        if (line == null || line.isEmpty()) return false;
        seen++;
        long millis = timeOf(line);
        int body = bodyStart(line);
        if (body < 0) {
            kept++;
            return true;
        }
        if (millis >= 0) {
            if (firstMillis < 0) {
                firstMillis = millis;
                firstTime = timeText(line);
                milestone("first line of the log", millis);
            }
            lastTime = timeText(line);
        }
        String tag = tagOf(line, body);
        if (tag.equals("UE") || tag.equals("UE4") || tag.equals("Unity")) engineLines++;
        watchSteps(line, millis);
        watchSessionState(line, millis);
        watchSignIn(line, millis);
        watchException(line, millis);
        boolean keep = countRepeat(line, body, tag);
        if (keep) kept++;
        return keep;
    }

    /** The frame rate lines the runtime prints once a second (read apart from the log, which leaves them out). */
    synchronized void frameRate(String line) {
        Matcher m = FPS.matcher(line);
        if (!m.find()) return;
        int fps = Math.min(Integer.parseInt(m.group(1)), fpsHistogram.length - 1);
        fpsHistogram[fps]++;
        fpsSamples++;
        fpsTarget = Integer.parseInt(m.group(2));
        countStability(fps, line);
    }

    /**
     * The seconds that tell how steady a game runs: those with the session in focus (the game has the view: not the loading before it, the system menu, a headset put down) and after
     * the first minute of it. A second is held when the game handed over nearly all the frames the screen asked for and the system had to show an old frame again only a few times
     * (the "Stale" count of the same line: the screen was filled in, which the frame rate alone does not tell).
     */
    private void countStability(int fps, String line) {
        if (sessionState != FOCUSED || fpsTarget <= 0) return;
        if (focusedSeconds++ < STABILITY_WARMUP_SECONDS) return;
        Matcher stale = STALE.matcher(line);
        int shownAgain = stale.find() ? Integer.parseInt(stale.group(1)) : 0;
        countedSeconds++;
        if (fps * 100 >= fpsTarget * 95 && shownAgain * 100 <= fpsTarget * 5) heldSeconds++;
    }

    /** The seconds that count for the stability (see [countStability]). */
    synchronized int stabilitySeconds() {
        return countedSeconds;
    }

    /** How many of them held the target. */
    synchronized int stabilityHeldSeconds() {
        return heldSeconds;
    }

    /** The frame rate the screen asked for, 0 when no line was seen. */
    synchronized int stabilityTarget() {
        return fpsTarget;
    }

    /** A memory reading of the game (resident set, and what the device still has free), kept when it moved or a minute went by. */
    synchronized boolean memory(long nowMillis, long startMillis, long residentKb, long availableKb) {
        if (lastMemoryAt >= 0 && Math.abs(residentKb - lastMemoryKb) < MEMORY_STEP_KB && nowMillis - lastMemoryAt < MEMORY_EVERY_MS) return false;
        if (memory.size() >= MAX_MEMORY_LINES) return false;
        lastMemoryAt = nowMillis;
        lastMemoryKb = residentKb;
        memory.add(((nowMillis - startMillis) / 1000) + " s: the game holds " + (residentKb / 1024) + " MB, the device has " + (availableKb / 1024) + " MB free");
        return true;
    }

    private void watchSteps(String line, long millis) {
        if (line.indexOf("GPHook") >= 0) {
            if (line.contains("Steam session ticket ready")) milestone("Steam ticket ready for the game", millis);
            else if (line.contains("launch sync finished")) milestone("GamePort answered the game's start", millis);
        } else if (line.indexOf("GPSteam") >= 0) {
            if (line.contains("launcher config read")) milestone("Steam stand-in started in the game", millis);
        } else if (line.indexOf("GPXR") >= 0) {
            if (line.contains("frame check at")) milestone("first frame handed to the runtime", millis);
            else if (line.contains("GamePort OpenXR layer active")) milestone("OpenXR layer active", millis);
        } else if (line.contains("Fatal signal ")) {
            milestone("native crash", millis);
        }
    }

    private void watchSessionState(String line, long millis) {
        if (line.indexOf("session state ->") < 0) return;
        Matcher m = SESSION_STATE.matcher(line);
        if (!m.find()) return;
        int state = Integer.parseInt(m.group(1));
        sessionState = state;
        if (sessionStates.add(state) && state >= 1 && state < SESSION_STATE_NAMES.length) milestone("OpenXR session " + SESSION_STATE_NAMES[state], millis);
    }

    private void watchSignIn(String line, long millis) {
        if (line.length() > 700) return;
        // Cheap test first: this sees every line of the game's log.
        if (!(line.contains("uth") || line.contains("oken") || line.contains("icket") || line.contains("401") || line.contains("403")
                || line.contains("ogin") || line.contains("ign-in") || line.contains("ignin") || line.contains("DENIED"))) return;
        String lower = line.toLowerCase(java.util.Locale.ROOT);
        boolean problem = lower.contains("invalid token") || lower.contains("invalid ticket") || lower.contains("customauthenticationfailed")
                || lower.contains("unauthorized") || lower.contains("permission_denied") || lower.contains("sign-in failed") || lower.contains("signin failed")
                || lower.contains("authentication failed") || lower.contains("login failed") || lower.contains("auth failed")
                || lower.contains("error response from steam") || lower.contains("status 401") || lower.contains("http 401") || lower.contains("code 401")
                || lower.contains(" 401 ") || lower.contains(" 403 ");
        if (!problem) return;
        authTotal++;
        if (authLines.size() >= MAX_AUTH_LINES) return;
        String sample = clip(line);
        String words = sameWords(sample.length() > 19 ? sample.substring(19) : sample);
        for (String known : authLines) if (sameWords(known.length() > 19 ? known.substring(19) : known).equals(words)) return;
        authLines.add(sample);
        milestone("first sign-in problem in the log", millis);
    }

    private void watchException(String line, long millis) {
        if (line.indexOf("xception") < 0 && line.indexOf("Error") < 0) return;
        Matcher m = EXCEPTION.matcher(line);
        if (!m.find()) return;
        String name = m.group(1);
        // The exception and the beginning of what it says, numbers set aside: the same fault with another address is one kind.
        String after = line.substring(m.end(), Math.min(line.length(), m.end() + 90));
        String key = name + sameWords(after);
        Kind kind = exceptions.get(key);
        if (kind == null) {
            if (exceptions.size() >= MAX_EXCEPTION_KINDS) return;
            kind = new Kind();
            kind.first = timeText(line);
            kind.sample = clip(line);
            exceptions.put(key, kind);
            if (exceptions.size() == 1) milestone("first exception in the log", millis);
        }
        kind.count++;
    }

    private boolean countRepeat(String line, int body, String tag) {
        String key = sameWords(line.substring(body));
        if (key.length() > 160) key = key.substring(0, 160);
        Kind kind = lines.get(key);
        if (kind == null) {
            if (lines.size() >= MAX_LINE_KINDS) return true; // too many kinds to follow: keep as before
            kind = new Kind();
            kind.tag = tag;
            kind.sample = clip(line);
            kind.first = timeText(line);
            lines.put(key, kind);
        }
        kind.count++;
        if (kind.count <= KEPT_COPIES) return true;
        for (String always : ALWAYS_KEPT_TAGS) if (always.equals(tag)) return true;
        return false;
    }

    private void milestone(String name, long millis) {
        if (milestones.containsKey(name) || firstMillis < 0 || millis < 0) return;
        milestones.put(name, String.format(java.util.Locale.ROOT, "+%.1f s", (millis - firstMillis) / 1000.0));
    }

    /** The text of a line with every run of digits replaced by one "#", so that the same words with other numbers count as one. */
    static String sameWords(String text) {
        StringBuilder out = new StringBuilder(text.length());
        boolean digits = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c >= '0' && c <= '9') {
                if (!digits) out.append('#');
                digits = true;
            } else {
                digits = false;
                out.append(c);
            }
        }
        return out.toString();
    }

    /** Where the message starts in a "threadtime" line: after the date, the time, the process and the thread. -1 when the line has no such start. */
    static int bodyStart(String line) {
        int at = 0;
        for (int field = 0; field < 4; field++) {
            while (at < line.length() && line.charAt(at) == ' ') at++;
            while (at < line.length() && line.charAt(at) != ' ') at++;
            if (at >= line.length()) return -1;
        }
        while (at < line.length() && line.charAt(at) == ' ') at++;
        return at + 2 < line.length() && line.charAt(at + 1) == ' ' ? at : -1;
    }

    private static String tagOf(String line, int body) {
        int colon = line.indexOf(':', body + 2);
        return colon < 0 ? "" : line.substring(body + 2, colon).trim();
    }

    private static String timeText(String line) {
        return line.length() >= 18 && TIME.matcher(line).find() ? line.substring(6, 18) : "";
    }

    private static String clip(String line) {
        return line.length() > MAX_SAMPLE ? line.substring(0, MAX_SAMPLE) + "..." : line;
    }

    /** The log's own clock in milliseconds; a new day is told by the clock going back. */
    private long timeOf(String line) {
        if (line.length() < 18) return -1;
        Matcher m = TIME.matcher(line);
        if (!m.find()) return -1;
        long clock = (((Long.parseLong(m.group(3)) * 60 + Long.parseLong(m.group(4))) * 60 + Long.parseLong(m.group(5))) * 1000) + Long.parseLong(m.group(6));
        if (previousClock >= 0 && clock < previousClock - 12L * 3600 * 1000) dayOffset += 24L * 3600 * 1000;
        previousClock = clock;
        return clock + dayOffset;
    }

    /** What was found, as text for a report; empty when no line was seen. */
    synchronized String report() {
        if (seen == 0) return "";
        StringBuilder out = new StringBuilder();
        out.append("Digest of the whole log of this run: the counts cover every line the game wrote, not only the ones kept in the other log files.\n");
        out.append("A line repeated more than ").append(KEPT_COPIES).append(" times is kept ").append(KEPT_COPIES).append(" times and only counted after that (GamePort's own lines are always kept).\n\n");
        out.append("lines seen: ").append(seen).append(", kept: ").append(kept).append(", lines of the engine's own log tag (UE, UE4, Unity): ").append(engineLines).append('\n');
        if (!firstTime.isEmpty()) out.append("from ").append(firstTime).append(" to ").append(lastTime).append('\n');

        out.append("\nSteps (seconds after the first line):\n");
        if (milestones.isEmpty()) out.append("  (none seen)\n");
        for (Map.Entry<String, String> step : milestones.entrySet()) out.append("  ").append(step.getValue()).append("  ").append(step.getKey()).append('\n');

        out.append("\nOpenXR session states reached: ");
        if (sessionStates.isEmpty()) out.append("none (no state line in the log)\n");
        else {
            StringBuilder names = new StringBuilder();
            for (int state : sessionStates) {
                if (names.length() > 0) names.append(", ");
                names.append(state >= 1 && state < SESSION_STATE_NAMES.length ? SESSION_STATE_NAMES[state] : String.valueOf(state));
            }
            out.append(names).append('\n');
        }
        out.append("focused reached: ").append(sessionStates.contains(5) ? "yes" : "no").append('\n');

        out.append("\nFrame rate (the runtime's line, once a second): ");
        if (fpsSamples == 0) out.append("no line seen\n");
        else out.append("median ").append(percentile(50)).append(", lowest tenth ").append(percentile(10)).append(", over ").append(fpsSamples).append(" s, target ").append(fpsTarget).append('\n');
        if (countedSeconds >= STABILITY_MIN_SECONDS) {
            out.append("Seconds that held the target: ").append(heldSeconds).append(" of ").append(countedSeconds).append(" (").append(heldSeconds * 100 / countedSeconds)
                    .append(" %), counted with the session in focus and after its first minute\n");
        } else {
            out.append("Seconds that held the target: not enough play to say (").append(countedSeconds).append(" s counted, ").append(STABILITY_MIN_SECONDS).append(" needed)\n");
        }

        out.append("\nSign-in problems: ").append(authTotal == 0 ? "none seen\n" : authTotal + " line(s), the first different ones:\n");
        for (String sample : authLines) out.append("  ").append(sample).append('\n');

        out.append("\nExceptions and errors named in the log, most frequent first:\n");
        if (exceptions.isEmpty()) out.append("  (none)\n");
        ArrayList<Kind> byCount = new ArrayList<>(exceptions.values());
        Collections.sort(byCount, (a, b) -> b.count - a.count);
        for (int i = 0; i < Math.min(TOP_EXCEPTIONS, byCount.size()); i++) {
            Kind kind = byCount.get(i);
            out.append("  ").append(kind.count).append("x  (first at ").append(kind.first).append(")  ").append(kind.sample).append('\n');
        }

        out.append("\nLines repeated the most (numbers set aside):\n");
        ArrayList<Kind> repeated = new ArrayList<>(lines.values());
        Collections.sort(repeated, (a, b) -> b.count - a.count);
        int shown = 0;
        for (Kind kind : repeated) {
            if (kind.count < 20 || shown >= TOP_REPEATED) break;
            out.append("  ").append(kind.count).append("x  [").append(kind.tag).append("]  ").append(kind.sample).append('\n');
            shown++;
        }
        if (shown == 0) out.append("  (nothing repeats 20 times)\n");

        if (!memory.isEmpty()) {
            out.append("\nMemory of the game over time:\n");
            for (String line : memory) out.append("  ").append(line).append('\n');
        }
        return out.toString();
    }

    private int percentile(int percent) {
        long target = (long) Math.ceil(fpsSamples * percent / 100.0);
        long seenSoFar = 0;
        for (int fps = 0; fps < fpsHistogram.length; fps++) {
            seenSoFar += fpsHistogram[fps];
            if (seenSoFar >= target) return fps;
        }
        return 0;
    }
}
