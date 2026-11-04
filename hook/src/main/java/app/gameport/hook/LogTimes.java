package app.gameport.hook;

import java.util.GregorianCalendar;
import java.util.TimeZone;

/**
 * When a line of the system log was written, read from the start of the line (the "threadtime" form: month-day hours:minutes:seconds.milliseconds, with no year). The system log
 * keeps what the earlier runs of the same app wrote and the app can read all of it, so a run tells its own lines from the ones before by their time.
 *
 * Not for several threads at once: each reader of the log makes its own. Plain Java, so it can be tested on a computer.
 */
final class LogTimes {
    private static final long DAY_MS = 86_400_000L;

    private final long nowMs;
    private final TimeZone zone;
    private int cachedDay = -1;
    private long cachedDayStart;

    /** [nowMs] settles the year the lines belong to: the latest one that does not put a line in the future. */
    LogTimes(long nowMs, TimeZone zone) {
        this.nowMs = nowMs;
        this.zone = zone;
    }

    /** The time of the line in milliseconds since 1970, or -1 when the line does not begin with a time. */
    long of(String line) {
        if (line.length() < 18 || line.charAt(2) != '-' || line.charAt(5) != ' ' || line.charAt(8) != ':' || line.charAt(11) != ':' || line.charAt(14) != '.') return -1;
        int month = number(line, 0, 2);
        int day = number(line, 3, 2);
        int hour = number(line, 6, 2);
        int minute = number(line, 9, 2);
        int second = number(line, 12, 2);
        int millis = number(line, 15, 3);
        if (month < 1 || month > 12 || day < 1 || day > 31 || hour < 0 || hour > 23 || minute < 0 || minute > 59 || second < 0 || second > 60 || millis < 0) return -1;
        int key = month * 100 + day;
        if (key != cachedDay) {
            cachedDayStart = dayStart(month, day);
            cachedDay = key;
        }
        return cachedDayStart + hour * 3_600_000L + minute * 60_000L + second * 1_000L + millis;
    }

    private static int number(String line, int from, int length) {
        int value = 0;
        for (int i = from; i < from + length; i++) {
            char c = line.charAt(i);
            if (c < '0' || c > '9') return -1;
            value = value * 10 + (c - '0');
        }
        return value;
    }

    private long dayStart(int month, int day) {
        GregorianCalendar now = new GregorianCalendar(zone);
        now.setTimeInMillis(nowMs);
        int year = now.get(java.util.Calendar.YEAR);
        long start = startOf(year, month, day);
        return start > nowMs + DAY_MS ? startOf(year - 1, month, day) : start;
    }

    private long startOf(int year, int month, int day) {
        GregorianCalendar day0 = new GregorianCalendar(zone);
        day0.clear();
        day0.set(year, month - 1, day, 0, 0, 0);
        return day0.getTimeInMillis();
    }
}
