package app.gameport.hook;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

public class ControllerProfilesTest {
    @Test public void aFullFileIsRead() {
        ControllerProfiles.Report r = ControllerProfiles.parse(Arrays.asList(
                "profile=/interaction_profiles/oculus/touch_controller|33", "profile=/interaction_profiles/valve/frame_controller_valve|44",
                "present=touch", "presented=applied", "used=touch"));
        assertEquals(Arrays.asList("/interaction_profiles/oculus/touch_controller:33", "/interaction_profiles/valve/frame_controller_valve:44"), r.profiles);
        assertEquals("touch", r.present);
        assertEquals("applied", r.presented);
        assertEquals("touch", r.used);
    }

    @Test public void theTableInUseAndItsControlsAreRead() {
        ControllerProfiles.Report r = ControllerProfiles.parse(Arrays.asList(
                "used=native", "table=/interaction_profiles/bytedance/pico4_controller", "control=left:x", "control=right:a", "control=left:x", "control=left:thumbstick"));
        assertEquals("/interaction_profiles/bytedance/pico4_controller", r.table);
        assertEquals(Arrays.asList("left:x", "right:a", "left:thumbstick"), r.controls);
    }

    @Test public void aBadTableOrControlIsLeftOut() {
        ControllerProfiles.Report r = ControllerProfiles.parse(Arrays.asList(
                "table=interaction_profiles/x", "table=/a b", "table=/a|b", "control=up:x", "control=left:", "control=left:X", "control=left:a-b", "control=left", "control=", "control=right:y"));
        assertNull(r.table);
        assertEquals(Arrays.asList("right:y"), r.controls);
    }

    @Test public void noMoreThanTheCapOfControlsIsKept() {
        List<String> lines = new ArrayList<String>();
        for (int i = 0; i < 60; i++) lines.add("control=left:c" + i);
        assertEquals(ControllerProfiles.MAX_CONTROLS, ControllerProfiles.parse(lines).controls.size());
    }

    @Test public void noFileOrNothingChangesNothing() {
        ControllerProfiles.Report r = ControllerProfiles.parse(null);
        assertTrue(r.profiles.isEmpty());
        assertNull(r.present);
        assertNull(r.presented);
        assertNull(r.used);
        assertTrue(ControllerProfiles.parse(new ArrayList<String>()).profiles.isEmpty());
    }

    @Test public void malformedLinesAreIgnored() {
        ControllerProfiles.Report r = ControllerProfiles.parse(Arrays.asList(
                "profile=", "profile=|3", "profile=/a/b|", "profile=/a/b|x", "profile=a/b|3", "profile=/a:b|3", "profile=/a b|3", "profile=/a/b|1234567",
                "garbage", "present=index", "presented=maybe", "used=both", "=", "profile=/ok/one|7"));
        assertEquals(Arrays.asList("/ok/one:7"), r.profiles);
        assertNull(r.present);
        assertNull(r.presented);
        assertNull(r.used);
    }

    @Test public void profilesAreCappedAndDeduplicated() {
        List<String> lines = new ArrayList<>();
        for (int i = 0; i < 40; i++) lines.add("profile=/p/" + i + "|1");
        lines.add("profile=/p/0|1");
        assertEquals(ControllerProfiles.MAX_PROFILES, ControllerProfiles.parse(lines).profiles.size());
        assertEquals(1, ControllerProfiles.parse(Arrays.asList("profile=/p/0|1", "profile=/p/0|1")).profiles.size());
    }

    @Test public void anEntryOfMoreThanTheLimitIsDropped() {
        StringBuilder path = new StringBuilder("/");
        while (path.length() < ControllerProfiles.MAX_ENTRY) path.append('x');
        assertTrue(ControllerProfiles.parse(Arrays.asList("profile=" + path + "|1")).profiles.isEmpty());
        StringBuilder ok = new StringBuilder("/");
        while (ok.length() < ControllerProfiles.MAX_ENTRY - 3) ok.append('x');
        assertEquals(1, ControllerProfiles.parse(Arrays.asList("profile=" + ok + "|1")).profiles.size());
    }

    @Test public void noEntryHasABar() {
        for (String e : ControllerProfiles.parse(Arrays.asList("profile=/a|b|3", "profile=/a/b|3")).profiles) assertTrue(e.indexOf('|') < 0);
    }

    @Test public void thePlayersChoiceIsAutoUnlessUnderstood() {
        assertEquals("auto", ControllerProfiles.normalizePresent(null));
        assertEquals("auto", ControllerProfiles.normalizePresent(""));
        assertEquals("auto", ControllerProfiles.normalizePresent("index"));
        assertEquals("frame", ControllerProfiles.normalizePresent("frame"));
        assertEquals("touch", ControllerProfiles.normalizePresent(" touch "));
    }
}
