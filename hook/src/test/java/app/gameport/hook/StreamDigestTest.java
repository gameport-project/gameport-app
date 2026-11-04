package app.gameport.hook;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class StreamDigestTest {
    private static String line(String time, String level, String tag, String message) {
        return "11-04 " + time + " 24638 24751 " + level + " " + tag + String.format("%-8s", "") + ": " + message;
    }

    @Test
    public void a_line_repeated_many_times_is_kept_a_few_times_and_counted_after() {
        StreamDigest digest = new StreamDigest();
        int kept = 0;
        for (int i = 0; i < 500; i++) {
            if (digest.scan(line("13:41:30." + String.format("%03d", i), "W", "OpenXR_CompositionLayers", "layer " + i + " is not valid"))) kept++;
        }
        assertEquals(StreamDigest.KEPT_COPIES, kept);
        assertTrue(digest.report(), digest.report().contains("500x  [OpenXR_CompositionLayers]"));
    }

    @Test
    public void the_lines_of_gameport_are_never_held_back() {
        StreamDigest digest = new StreamDigest();
        for (int i = 0; i < 300; i++) assertTrue(digest.scan(line("13:41:30.100", "I", "GPXR", "frame check at " + i + " s: 1 frames")));
    }

    @Test
    public void an_exception_is_counted_over_the_whole_log_with_its_first_time() {
        StreamDigest digest = new StreamDigest();
        for (int i = 0; i < 300; i++) {
            digest.scan(line("13:41:3" + (i % 10) + ".000", "W", "System.err", "java.lang.NoClassDefFoundError: Failed resolution of: Landroidx/browser/customtabs/CustomTabColorSchemeParams$Builder;"));
        }
        String report = digest.report();
        assertTrue(report, report.contains("300x  (first at 13:41:30.000)"));
        assertTrue(report, report.contains("NoClassDefFoundError"));
    }

    @Test
    public void the_session_states_tell_whether_focused_was_reached() {
        StreamDigest digest = new StreamDigest();
        digest.scan(line("13:41:30.675", "I", "GPXR", "session state -> 1 (1 idle, 2 ready, 3 synchronized, 4 visible, 5 focused)"));
        digest.scan(line("13:41:30.700", "I", "GPXR", "session state -> 4 (1 idle, 2 ready, 3 synchronized, 4 visible, 5 focused)"));
        assertTrue(digest.report(), digest.report().contains("focused reached: no"));
        digest.scan(line("13:41:31.675", "I", "GPXR", "session state -> 5 (1 idle, 2 ready, 3 synchronized, 4 visible, 5 focused)"));
        assertTrue(digest.report(), digest.report().contains("focused reached: yes"));
        assertTrue(digest.report(), digest.report().contains("+1.0 s  OpenXR session focused"));
    }

    @Test
    public void a_sign_in_problem_is_named_once_for_the_same_words() {
        StreamDigest digest = new StreamDigest();
        digest.scan(line("13:41:30.000", "E", "Unity", "Steam sign-in failed (invalid token), status 401"));
        digest.scan(line("13:41:31.000", "E", "Unity", "Steam sign-in failed (invalid token), status 401"));
        digest.scan(line("13:41:32.000", "I", "Unity", "Saved the game with a perfectly fine token count"));
        String report = digest.report();
        assertTrue(report, report.contains("Sign-in problems: 2 line(s)"));
        assertEquals(1, report.split("Steam sign-in failed").length - 1);
    }

    @Test
    public void the_frame_rate_is_summed_up_by_its_median_and_its_low_tenth() {
        StreamDigest digest = new StreamDigest();
        digest.scan(line("13:41:30.000", "I", "GPHook", "first"));
        for (int i = 0; i < 9; i++) digest.frameRate("11-04 13:41:3" + i + ".000 1 1 I VrApi   : FPS=72/72,Prd=0ms");
        digest.frameRate("11-04 13:41:39.000 1 1 I VrApi   : FPS=1/72,Prd=0ms");
        String report = digest.report();
        assertTrue(report, report.contains("median 72, lowest tenth 1, over 10 s, target 72"));
    }

    private static void seconds(StreamDigest digest, int count, int fps, int stale) {
        for (int i = 0; i < count; i++) digest.frameRate("11-04 13:41:30.000 1 1 I VrApi   : FPS=" + fps + "/72,Prd=0ms,Tear=0,Early=0,Stale=" + stale + ",Stale2/5/10/max=0/0/0/0,VSnc=1");
    }

    private static void focus(StreamDigest digest) {
        digest.scan(line("13:41:30.000", "I", "GPXR", "session state -> 5 (1 idle, 2 ready, 3 synchronized, 4 visible, 5 focused)"));
    }

    @Test
    public void the_stability_counts_only_seconds_in_focus_and_after_the_first_minute() {
        StreamDigest digest = new StreamDigest();
        seconds(digest, 40, 1, 0);
        focus(digest);
        seconds(digest, StreamDigest.STABILITY_WARMUP_SECONDS, 10, 70);
        assertEquals(0, digest.stabilitySeconds());
        seconds(digest, 200, 72, 0);
        assertEquals(200, digest.stabilitySeconds());
        assertEquals(200, digest.stabilityHeldSeconds());
        assertTrue(digest.report(), digest.report().contains("Seconds that held the target: 200 of 200 (100 %)"));
    }

    @Test
    public void a_second_with_few_frames_or_many_old_frames_shown_again_does_not_hold_the_target() {
        StreamDigest digest = new StreamDigest();
        focus(digest);
        seconds(digest, StreamDigest.STABILITY_WARMUP_SECONDS, 72, 0);
        seconds(digest, 100, 72, 0);
        seconds(digest, 40, 60, 0);
        seconds(digest, 40, 72, 30);
        seconds(digest, 20, 70, 2);
        assertEquals(200, digest.stabilitySeconds());
        assertEquals(120, digest.stabilityHeldSeconds());
        assertTrue(digest.report(), digest.report().contains("Seconds that held the target: 120 of 200 (60 %)"));
    }

    @Test
    public void seconds_with_the_session_out_of_focus_do_not_count_and_a_short_play_gives_no_number() {
        StreamDigest digest = new StreamDigest();
        focus(digest);
        seconds(digest, StreamDigest.STABILITY_WARMUP_SECONDS + 50, 72, 0);
        digest.scan(line("13:42:30.000", "I", "GPXR", "session state -> 4 (1 idle, 2 ready, 3 synchronized, 4 visible, 5 focused)"));
        seconds(digest, 500, 2, 0);
        assertEquals(50, digest.stabilitySeconds());
        assertTrue(digest.report(), digest.report().contains("not enough play to say (50 s counted, " + StreamDigest.STABILITY_MIN_SECONDS + " needed)"));
    }

    @Test
    public void a_line_with_no_threadtime_start_is_kept_and_not_counted_as_a_repeat() {
        StreamDigest digest = new StreamDigest();
        for (int i = 0; i < 100; i++) assertTrue(digest.scan("--------- beginning of main"));
        assertFalse(digest.report().contains("100x"));
    }

    @Test
    public void numbers_do_not_make_two_lines_different() {
        assertEquals("size # of # bytes", StreamDigest.sameWords("size 12 of 3456 bytes"));
    }

    @Test
    public void memory_is_kept_when_it_moves_or_a_minute_went_by() {
        StreamDigest digest = new StreamDigest();
        digest.scan(line("13:41:30.000", "I", "GPHook", "first"));
        digest.memory(1_000, 0, 1_000_000, 2_000_000);
        digest.memory(6_000, 0, 1_010_000, 2_000_000); // moved by 10 MB: left out
        digest.memory(11_000, 0, 1_200_000, 1_800_000); // moved by 190 MB: kept
        String report = digest.report();
        assertTrue(report, report.contains("1 s: the game holds 976 MB"));
        assertFalse(report, report.contains("6 s: the game holds"));
        assertTrue(report, report.contains("11 s: the game holds 1171 MB"));
    }
}
