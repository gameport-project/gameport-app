package app.gameport.hook;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class NativeGapsTest {
    private static final String MISSING_LIBRARY =
            "10-25 18:09:27.104  3498  3836 E Unity   : DllNotFoundException: Unable to load DLL 'OVRPlugin'. Tried the load the following dynamic libraries: Unable to load dynamic library";
    private static final String MISSING_FUNCTION =
            "10-25 18:09:28.001  3498  3836 E Unity   : EntryPointNotFoundException: Unable to find an entry point named 'ovrp_GetVersion' in 'OVRPlugin'.";

    @Test
    public void nothing_is_reported_for_a_log_that_says_nothing_of_the_kind() {
        NativeGaps gaps = new NativeGaps();
        gaps.scan("10-25 18:09:27.104  3498  3836 I Unity   : Saving file Savegame.save with a long enough line");
        gaps.scan("10-25 18:09:27.104  3498  3836 W VrRuntimeClient: Failed to call getLaunchId at some length");
        assertEquals("", gaps.report());
    }

    @Test
    public void a_missing_library_is_kept_once_with_how_many_times() {
        NativeGaps gaps = new NativeGaps();
        for (int i = 0; i < 3019; i++) gaps.scan(MISSING_LIBRARY);
        String report = gaps.report();
        assertTrue(report, report.contains("3019x  library OVRPlugin"));
        assertTrue(report, report.contains("first at 10-25 18:09:27.104"));
        assertEquals(1, report.split("3019x").length - 1);
    }

    @Test
    public void a_missing_function_names_the_function_and_its_library() {
        NativeGaps gaps = new NativeGaps();
        gaps.scan(MISSING_FUNCTION);
        gaps.scan(MISSING_FUNCTION);
        assertTrue(gaps.report(), gaps.report().contains("2x  function ovrp_GetVersion in library OVRPlugin"));
    }

    @Test
    public void the_most_frequent_comes_first() {
        NativeGaps gaps = new NativeGaps();
        gaps.scan(MISSING_FUNCTION);
        gaps.scan(MISSING_LIBRARY);
        gaps.scan(MISSING_LIBRARY);
        String report = gaps.report();
        assertTrue(report.indexOf("2x  library OVRPlugin") < report.indexOf("1x  function ovrp_GetVersion"));
    }

    @Test
    public void linker_and_jni_failures_are_kept() {
        NativeGaps gaps = new NativeGaps();
        gaps.scan("10-25 18:09:27.104  3498  3836 E linker  : cannot locate symbol \"foo_bar\" referenced by \"/data/app/x/lib/arm64/libgame.so\"...");
        gaps.scan("10-25 18:09:27.104  3498  3836 E AndroidRuntime: java.lang.UnsatisfiedLinkError: dlopen failed: library \"libsomething.so\" not found");
        gaps.scan("10-25 18:09:27.104  3498  3836 E AndroidRuntime: UnsatisfiedLinkError: No implementation found for void com.x.Y.z() (tried Java_com_x_Y_z)");
        String report = gaps.report();
        assertTrue(report, report.contains("symbol foo_bar referenced by /data/app/x/lib/arm64/libgame.so"));
        assertTrue(report, report.contains("library libsomething.so (not found by the linker)"));
        assertTrue(report, report.contains("native method void"));
    }

    @Test
    public void the_number_of_distinct_things_kept_is_bounded() {
        NativeGaps gaps = new NativeGaps();
        for (int i = 0; i < 1000; i++) gaps.scan("10-25 18:09:27.104  3498  3836 E Unity   : Unable to find an entry point named 'f" + i + "' in 'Lib'.");
        assertFalse(gaps.report().contains("f999"));
        assertTrue(gaps.report().contains("f0 "));
    }
}
