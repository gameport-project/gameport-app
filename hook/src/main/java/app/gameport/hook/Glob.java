package app.gameport.hook;

import java.util.regex.Pattern;

/** Steam's simple file patterns: `*` and `?` wildcards, case-insensitive. */
final class Glob {
    private Glob() {}

    static boolean matches(String pattern, String name) {
        StringBuilder regex = new StringBuilder("^");
        for (char c : pattern.toCharArray()) {
            if (c == '*') regex.append(".*");
            else if (c == '?') regex.append('.');
            else regex.append(Pattern.quote(String.valueOf(c)));
        }
        regex.append('$');
        return Pattern.compile(regex.toString(), Pattern.CASE_INSENSITIVE).matcher(name).matches();
    }
}
