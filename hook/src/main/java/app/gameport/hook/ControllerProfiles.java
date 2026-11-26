package app.gameport.hook;

import java.util.ArrayList;
import java.util.List;

/**
 * What GamePort's OpenXR layer leaves in xr_profiles.txt (one line per fact: {@code profile=<path>|<bindings>}, {@code present=}, {@code presented=}, {@code used=}) and the
 * player's choice as the hook receives it. Plain Java so it can be tested on a computer. Anything malformed is left out: an old file, no file or a damaged one changes nothing.
 */
final class ControllerProfiles {
    static final int MAX_PROFILES = 16;
    static final int MAX_ENTRY = 200;

    /** The optional extras of the controller report; a null or empty value is not sent. */
    static final class Report {
        /** One entry per profile, {@code <path>:<bindings>}, with no {@code |}. */
        final List<String> profiles = new ArrayList<>();
        String present;
        String presented;
        String used;
    }

    private ControllerProfiles() {}

    /** The player's choice of controllers to present: auto, frame or touch; anything else (or nothing) is auto. */
    static String normalizePresent(String value) {
        if (value == null) return "auto";
        String v = value.trim();
        return v.equals("frame") || v.equals("touch") ? v : "auto";
    }

    static Report parse(Iterable<String> lines) {
        Report report = new Report();
        if (lines == null) return report;
        for (String raw : lines) {
            if (raw == null) continue;
            String line = raw.trim();
            if (line.startsWith("profile=")) {
                String entry = profileEntry(line.substring("profile=".length()));
                if (entry != null && report.profiles.size() < MAX_PROFILES && !report.profiles.contains(entry)) report.profiles.add(entry);
            } else if (line.startsWith("present=")) {
                report.present = oneOf(line.substring("present=".length()), "auto", "frame", "touch");
            } else if (line.startsWith("presented=")) {
                report.presented = oneOf(line.substring("presented=".length()), "applied", "fallback", "ignored");
            } else if (line.startsWith("used=")) {
                report.used = oneOf(line.substring("used=".length()), "frame", "touch", "native", "none");
            }
        }
        return report;
    }

    private static String oneOf(String value, String... allowed) {
        String v = value.trim();
        for (String a : allowed) if (a.equals(v)) return v;
        return null;
    }

    /** {@code <path>|<bindings>} becomes {@code <path>:<bindings>}; null for a line that is not one (no path, no count, a character that cannot be in a path, too long). */
    private static String profileEntry(String value) {
        int bar = value.lastIndexOf('|');
        if (bar <= 0 || bar == value.length() - 1) return null;
        String path = value.substring(0, bar).trim();
        String count = value.substring(bar + 1).trim();
        if (path.isEmpty() || path.charAt(0) != '/' || count.isEmpty() || count.length() > 6) return null;
        for (int i = 0; i < path.length(); i++) {
            char c = path.charAt(i);
            if (c <= ' ' || c > '~' || c == '|' || c == ':') return null;
        }
        for (int i = 0; i < count.length(); i++) if (count.charAt(i) < '0' || count.charAt(i) > '9') return null;
        String entry = path + ":" + count;
        return entry.length() > MAX_ENTRY ? null : entry;
    }
}
