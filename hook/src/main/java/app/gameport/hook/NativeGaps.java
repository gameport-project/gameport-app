package app.gameport.hook;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The native things a game asked for and did not find, as its log tells them: a function a library does not export, a library that is not there, a symbol the
 * linker cannot place, a method with no native implementation. A game that carries scripts for a library it does not ship (Meta's OVRPlugin, in a build made
 * for another headset) fails at each of those, and a stand-in that answers the function it asks for can be made from this list. Each distinct one is kept once
 * with how many times it came, when it first did, and the line that said it.
 *
 * Plain Java, so it can be tested on a computer.
 */
final class NativeGaps {
    private static final int MAX_KINDS = 300;
    private static final int MAX_SAMPLE = 300;

    private static final Pattern ENTRY_POINT = Pattern.compile("Unable to find an entry point named '([^']+)' in '([^']+)'");
    private static final Pattern UNITY_LIBRARY = Pattern.compile("Unable to load DLL '([^']+)'");
    private static final Pattern LINKER_SYMBOL = Pattern.compile("cannot locate symbol \"([^\"]+)\" referenced by \"([^\"]+)\"");
    private static final Pattern LINKER_LIBRARY = Pattern.compile("dlopen failed: library \"([^\"]+)\" not found");
    private static final Pattern UNDEFINED = Pattern.compile("undefined symbol: (\\S+)");
    private static final Pattern JNI = Pattern.compile("No implementation found for (\\S+)");

    /** The log time at the start of a threadtime line: "10-25 18:09:27.104". */
    private static final Pattern TIME = Pattern.compile("^(\\d\\d-\\d\\d \\d\\d:\\d\\d:\\d\\d\\.\\d+)");

    private static final class Gap {
        int count;
        String first = "";
        String sample = "";
    }

    private final Map<String, Gap> gaps = new LinkedHashMap<>();

    /** Looks at one line of the log. A line that says nothing of the kind is ignored at once. */
    synchronized void scan(String line) {
        if (line == null || line.length() < 20) return;
        // Cheap test first: this sees every line of the game's log.
        if (!(line.contains("Unable to") || line.contains("cannot locate symbol") || line.contains("dlopen failed") || line.contains("undefined symbol")
                || line.contains("No implementation found"))) return;
        String kind = kindOf(line);
        if (kind == null) return;
        Gap gap = gaps.get(kind);
        if (gap == null) {
            if (gaps.size() >= MAX_KINDS) return;
            gap = new Gap();
            Matcher time = TIME.matcher(line);
            gap.first = time.find() ? time.group(1) : "";
            gap.sample = line.length() > MAX_SAMPLE ? line.substring(0, MAX_SAMPLE) + "..." : line;
            gaps.put(kind, gap);
        }
        gap.count++;
    }

    private static String kindOf(String line) {
        Matcher m = ENTRY_POINT.matcher(line);
        if (m.find()) return "function " + m.group(1) + " in library " + m.group(2);
        m = UNITY_LIBRARY.matcher(line);
        if (m.find()) return "library " + m.group(1) + " (not found by the game's runtime)";
        m = LINKER_SYMBOL.matcher(line);
        if (m.find()) return "symbol " + m.group(1) + " referenced by " + m.group(2);
        m = LINKER_LIBRARY.matcher(line);
        if (m.find()) return "library " + m.group(1) + " (not found by the linker)";
        m = UNDEFINED.matcher(line);
        if (m.find()) return "symbol " + m.group(1);
        m = JNI.matcher(line);
        if (m.find()) return "native method " + m.group(1);
        return null;
    }

    /** What was found, most frequent first, as text for a report; empty when nothing was. */
    synchronized String report() {
        if (gaps.isEmpty()) return "";
        ArrayList<Map.Entry<String, Gap>> entries = new ArrayList<>(gaps.entrySet());
        Collections.sort(entries, (a, b) -> b.getValue().count - a.getValue().count);
        StringBuilder out = new StringBuilder();
        out.append("Native things the game asked for and did not find, each distinct one once, most frequent first.\n");
        out.append("A missing function or library listed here is one a stand-in can answer.\n\n");
        for (Map.Entry<String, Gap> entry : entries) {
            Gap gap = entry.getValue();
            out.append(gap.count).append("x  ").append(entry.getKey());
            if (!gap.first.isEmpty()) out.append("  (first at ").append(gap.first).append(")");
            out.append('\n').append("    ").append(gap.sample).append('\n');
        }
        return out.toString();
    }
}
