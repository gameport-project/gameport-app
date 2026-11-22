package app.gameport.hook;

import java.util.ArrayList;
import java.util.List;

/**
 * What every thread of the game was doing when the process began to end, written for the report. A game that quits by itself (System.exit, Process.killProcess, Runtime.halt) has one thread
 * inside that call, and the frames under it say who asked: the threads that are in such a call come first and are marked, so the answer is at the top of the file.
 */
final class ExitStacks {
    /** The calls that end a process from Java: a thread inside one of them is the one that asked. */
    private static final String[][] ENDING_CALLS = {
        {"java.lang.System", "exit"},
        {"java.lang.Runtime", "exit"},
        {"java.lang.Runtime", "halt"},
        {"android.os.Process", "killProcess"},
        {"android.os.Process", "sendSignal"},
    };

    private ExitStacks() { }

    /** Whether one of the frames is a call that ends the process. */
    static boolean endsTheProcess(StackTraceElement[] frames) {
        for (StackTraceElement frame : frames) {
            for (String[] call : ENDING_CALLS) {
                if (frame.getClassName().equals(call[0]) && frame.getMethodName().equals(call[1])) return true;
            }
        }
        return false;
    }

    /** One block per thread, the ones inside an ending call first (marked), the others after them, each with at most [limit] frames. */
    static String format(List<String> names, List<String> states, List<StackTraceElement[]> stacks, int limit) {
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < stacks.size(); i++) if (endsTheProcess(stacks.get(i))) order.add(i);
        int askers = order.size();
        for (int i = 0; i < stacks.size(); i++) if (!order.contains(i)) order.add(i);
        StringBuilder out = new StringBuilder();
        out.append(askers == 0 ? "no thread was inside a call that ends the process (the end came from native code or from outside the game)\n"
                : askers + " thread(s) inside a call that ends the process, first below\n");
        for (int n = 0; n < order.size(); n++) {
            int i = order.get(n);
            out.append('\n').append(names.get(i)).append(" (").append(states.get(i)).append(')');
            if (n < askers) out.append("  <-- asks for the end of the process");
            out.append(":\n");
            StackTraceElement[] frames = stacks.get(i);
            for (int f = 0; f < frames.length && f < limit; f++) out.append("    ").append(frames[f]).append('\n');
            if (frames.length == 0) out.append("    (no Java frame)\n");
            else if (frames.length > limit) out.append("    ... ").append(frames.length - limit).append(" more\n");
        }
        return out.toString();
    }
}
