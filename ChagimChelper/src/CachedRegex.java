import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * A class for caching regex patterns, so we don't call Pattern.compile() multiple times
 * for the same pattern (for example, if we call a method multiple times that contains a pattern).
 */
public class CachedRegex {
    private CachedRegex() {}

    /**
     * The key class used in our map of cached Regex patterns. Patterns are only considered
     * the same if they were created with the same string and the same flags.
     *
     * @param patternString  The string used to create the Regex pattern
     * @param flags          The integer flags used to create the Regex pattern
     */
    private record PatternKey(String patternString, Integer flags) {
        private Pattern toPattern() {
            if (this.flags == null) {
                return Pattern.compile(this.patternString);
            } else {
                return Pattern.compile(this.patternString, this.flags);
            }
        }
    }

    private static final Map<PatternKey, Pattern> map = new HashMap<>();

    /**
     * Either compiles the given string to a Pattern, or returns an already-compiled cached version
     * of that pattern.
     *
     * @param patternString  the pattern string to compile or get from cache
     * @param flags          additional flags to pass to Pattern.compile, or null for no flags
     * @return               the compiled pattern
     */
    private static Pattern patternImpl(String patternString, Integer flags) {
        return map.computeIfAbsent(new PatternKey(patternString, flags), PatternKey::toPattern);
    }

    /**
     * Either compiles the given string to a Pattern, or returns an already-compiled cached version
     * of that pattern.
     *
     * @param patternString  the pattern string to compile or get from cache
     * @return               the compiled pattern
     */
    public static Pattern pattern(String patternString) {
        return patternImpl(patternString, null);
    }

    /**
     * Either compiles the given string to a Pattern, or returns an already-compiled cached version
     * of that pattern.
     *
     * @param patternString  the pattern string to compile or get from cache
     * @param flags          additional flags to pass to Pattern.compile
     * @return               the compiled pattern
     */
    public static Pattern pattern(String patternString, int flags) {
        return patternImpl(patternString, flags);
    }
}
