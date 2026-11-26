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
        /** The profile of the table the game is read through when it is not the Steam Frame's (its own for the device, or the Touch's); null when none. */
        String table;
        /** The controls ({@code left:x}) that table binds, distinct, at most {@link #MAX_CONTROLS}. */
        final List<String> controls = new ArrayList<>();
    }

    static final int MAX_CONTROLS = 40;

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
            if (raw != null) readLine(report, raw.trim());
        }
        return report;
    }

    private static void readLine(Report report, String line) {
        if (line.startsWith("profile=")) {
            String entry = profileEntry(line.substring("profile=".length()));
            if (entry != null && report.profiles.size() < MAX_PROFILES && !report.profiles.contains(entry)) report.profiles.add(entry);
        } else if (line.startsWith("present=")) {
            report.present = oneOf(line.substring("present=".length()), "auto", "frame", "touch");
        } else if (line.startsWith("presented=")) {
            report.presented = oneOf(line.substring("presented=".length()), "applied", "fallback", "ignored");
        } else if (line.startsWith("used=")) {
            report.used = oneOf(line.substring("used=".length()), "frame", "touch", "native", "none");
        } else if (line.startsWith("table=")) {
            report.table = tablePath(line.substring("table=".length()));
        } else if (line.startsWith("control=")) {
            addControl(report, line.substring("control=".length()));
        }
    }

    private static void addControl(Report report, String value) {
        String control = controlKey(value);
        if (control != null && report.controls.size() < MAX_CONTROLS && !report.controls.contains(control)) report.controls.add(control);
    }

    /** A profile path ({@code /interaction_profiles/oculus/touch_controller}) with only printable characters and none the report uses as a separator; null otherwise. */
    private static String tablePath(String value) {
        String path = value.trim();
        if (path.isEmpty() || path.charAt(0) != '/' || path.length() > MAX_ENTRY) return null;
        for (int i = 0; i < path.length(); i++) {
            char c = path.charAt(i);
            if (c <= ' ' || c > '~' || c == '|' || c == ':' || c == ',' || c == '=') return null;
        }
        return path;
    }

    /** {@code left:x}: a hand, a colon and a lower-case name of letters, digits and underscores; null otherwise. */
    private static String controlKey(String value) {
        String key = value.trim();
        int colon = key.indexOf(':');
        if (colon < 0 || key.length() > 32) return null;
        String hand = key.substring(0, colon);
        String group = key.substring(colon + 1);
        if (!hand.equals("left") && !hand.equals("right")) return null;
        if (group.isEmpty()) return null;
        for (int i = 0; i < group.length(); i++) {
            char c = group.charAt(i);
            if (!((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_')) return null;
        }
        return key;
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
