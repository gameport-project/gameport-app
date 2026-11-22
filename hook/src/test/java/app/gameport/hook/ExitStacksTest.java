package app.gameport.hook;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;

public class ExitStacksTest {
    private static StackTraceElement frame(String cls, String method) {
        return new StackTraceElement(cls, method, "File.java", 1);
    }

    @Test
    public void aThreadInsideSystemExitIsTheOneThatAsked() {
        assertTrue(ExitStacks.endsTheProcess(new StackTraceElement[] {frame("java.lang.Runtime", "exit"), frame("java.lang.System", "exit"), frame("com.game.Quit", "now")}));
        assertTrue(ExitStacks.endsTheProcess(new StackTraceElement[] {frame("android.os.Process", "killProcess")}));
        assertFalse(ExitStacks.endsTheProcess(new StackTraceElement[] {frame("android.os.Looper", "loop"), frame("com.game.Main", "run")}));
        assertFalse(ExitStacks.endsTheProcess(new StackTraceElement[0]));
    }

    @Test
    public void theAskingThreadComesFirstAndIsMarkedWhateverItsPlace() {
        String text = ExitStacks.format(
                Arrays.asList("main", "UE4 worker", "RenderThread"),
                Arrays.asList("RUNNABLE", "RUNNABLE", "WAITING"),
                Arrays.asList(
                        new StackTraceElement[] {frame("android.os.Looper", "loop")},
                        new StackTraceElement[] {frame("java.lang.Runtime", "exit"), frame("com.epicgames.ue4.GameActivity", "AndroidThunkJava_ForceQuit")},
                        new StackTraceElement[0]),
                10);
        assertTrue(text.startsWith("1 thread(s) inside a call that ends the process"));
        assertTrue(text.indexOf("UE4 worker") < text.indexOf("main"));
        assertTrue(text.contains("UE4 worker (RUNNABLE)  <-- asks for the end of the process"));
        assertTrue(text.contains("AndroidThunkJava_ForceQuit"));
        assertTrue(text.contains("(no Java frame)"));
    }

    @Test
    public void withNoAskingThreadTheFileSaysTheEndCameFromElsewhere() {
        String text = ExitStacks.format(Collections.singletonList("main"), Collections.singletonList("RUNNABLE"),
                Collections.singletonList(new StackTraceElement[] {frame("android.os.Looper", "loop")}), 10);
        assertTrue(text.startsWith("no thread was inside a call that ends the process"));
        assertFalse(text.contains("<--"));
    }

    @Test
    public void aLongStackIsCutAndSaysHowManyFramesWereLeftOut() {
        StackTraceElement[] frames = new StackTraceElement[5];
        for (int i = 0; i < frames.length; i++) frames[i] = frame("a.B", "m" + i);
        String text = ExitStacks.format(Collections.singletonList("t"), Collections.singletonList("RUNNABLE"), Collections.singletonList(frames), 3);
        assertTrue(text.contains("a.B.m2"));
        assertFalse(text.contains("a.B.m3"));
        assertTrue(text.contains("... 2 more"));
    }
}
