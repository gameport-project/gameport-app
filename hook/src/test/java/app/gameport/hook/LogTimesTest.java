package app.gameport.hook;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.GregorianCalendar;
import java.util.TimeZone;
import org.junit.Test;

public class LogTimesTest {
    private static final TimeZone UTC = TimeZone.getTimeZone("UTC");

    private static long utc(int year, int month, int day, int hour, int minute, int second, int millis) {
        GregorianCalendar c = new GregorianCalendar(UTC);
        c.clear();
        c.set(year, month - 1, day, hour, minute, second);
        return c.getTimeInMillis() + millis;
    }

    @Test
    public void a_line_of_the_log_gives_the_time_it_was_written() {
        LogTimes times = new LogTimes(utc(2026, 11, 4, 22, 50, 0, 0), UTC);
        assertEquals(utc(2026, 11, 4, 22, 47, 8, 314), times.of("11-04 22:47:08.314 22467 22519 I Unity   : [XR] started"));
        assertEquals(utc(2026, 11, 4, 0, 0, 0, 1), times.of("11-04 00:00:00.001  1 2 W Tag: x"));
    }

    @Test
    public void a_line_that_began_the_day_before_new_year_belongs_to_the_year_before() {
        LogTimes times = new LogTimes(utc(2027, 1, 1, 0, 10, 0, 0), UTC);
        assertEquals(utc(2026, 12, 31, 23, 59, 58, 500), times.of("12-31 23:59:58.500 1 2 I Tag: x"));
        assertEquals(utc(2027, 1, 1, 0, 5, 0, 0), times.of("01-01 00:05:00.000 1 2 I Tag: x"));
    }

    @Test
    public void a_line_without_a_time_gives_nothing() {
        LogTimes times = new LogTimes(utc(2026, 11, 4, 22, 50, 0, 0), UTC);
        assertEquals(-1, times.of("--------- beginning of main"));
        assertEquals(-1, times.of(""));
        assertEquals(-1, times.of("11-04 22:47:08 missing milliseconds"));
        assertEquals(-1, times.of("13-04 22:47:08.314 1 2 I Tag: x"));
        assertEquals(-1, times.of("ab-04 22:47:08.314 1 2 I Tag: x"));
    }

    @Test
    public void lines_of_an_earlier_run_come_before_lines_of_this_one() {
        LogTimes times = new LogTimes(utc(2026, 11, 4, 22, 50, 0, 0), UTC);
        long start = utc(2026, 11, 4, 22, 47, 5, 0);
        assertTrue(times.of("11-04 22:46:13.427 21902 22089 I GPXR: earlier run") < start);
        assertTrue(times.of("11-04 22:47:06.001 22467 22519 I GPXR: this run") > start);
    }
}
