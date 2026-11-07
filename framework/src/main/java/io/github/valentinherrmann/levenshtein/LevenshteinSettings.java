package io.github.valentinherrmann.levenshtein;

/**
 * Runtime configuration of the Levenshtein Testing Framework.
 *
 * <p>All values are read when they are needed, never inlined at compile time, so an exam repository
 * configures the framework from its own test code, typically in a static initializer or a
 * {@code @BeforeAll} method of the test class:</p>
 *
 * <pre>{@code
 * static {
 *     LevenshteinSettings.setLanguage(LevenshteinSettings.Language.ENGLISH);
 *     LevenshteinSettings.setMethodNameDeviationThreshold(15);
 * }
 * }</pre>
 *
 * <h2>Levenshtein distance thresholds</h2>
 * <p>Thresholds are percentages (0-100) of the length of the longer name. A name is accepted as a
 * deviation when {@code distance * 100 / max(expected.length(), actual.length()) <= threshold}.</p>
 * <pre>{@code
 * // threshold 20:
 * "price"          vs "pric"           -> 1 * 100 / 5  = 20.0 %  -> DEVIATES
 * "calculateCost"  vs "calculateCots"  -> 2 * 100 / 13 = 15.4 %  -> DEVIATES
 * "Car"            vs "Cra"            -> 2 * 100 / 3  = 66.7 %  -> MISSING
 * }</pre>
 * <ul>
 *   <li><b>0</b> - no tolerance, exact names required</li>
 *   <li><b>10</b> - strict, single typos in long names only</li>
 *   <li><b>20</b> - moderate (default)</li>
 *   <li><b>30+</b> - lenient, may bind unrelated members</li>
 * </ul>
 *
 * <h2>Timeouts</h2>
 * <p>Timeouts are not configured here. {@link LevenshteinTest} carries a default
 * {@code @StrictTimeout}; put an explicit {@code @StrictTimeout} on the test class or method to override it.</p>
 */
public final class LevenshteinSettings {

    /**
     * Supported languages for assertion and feedback messages.
     */
    public enum Language { DEUTSCH, ENGLISH }

    /** Default threshold (percent) for all name deviations. */
    public static final int DEFAULT_THRESHOLD = 20;

    private static volatile Language language = Language.DEUTSCH;
    private static volatile int classNameDeviationThreshold = DEFAULT_THRESHOLD;
    private static volatile int methodNameDeviationThreshold = DEFAULT_THRESHOLD;
    private static volatile int attributeNameDeviationThreshold = DEFAULT_THRESHOLD;
    private static volatile boolean gettersSettersRequireSameObject = false;

    private LevenshteinSettings() {
    }

    /** @return the language of messages shown to students (default {@link Language#DEUTSCH}) */
    public static Language getLanguage() {
        return language;
    }

    /** @param newLanguage the language of messages shown to students */
    public static void setLanguage(Language newLanguage) {
        if (newLanguage == null) {
            throw new IllegalArgumentException("language must not be null");
        }
        language = newLanguage;
    }

    /** @return maximum deviation (percent) accepted for class names */
    public static int getClassNameDeviationThreshold() {
        return classNameDeviationThreshold;
    }

    /** @param threshold maximum deviation (percent, 0-100) accepted for class names */
    public static void setClassNameDeviationThreshold(int threshold) {
        classNameDeviationThreshold = checkThreshold(threshold);
    }

    /** @return maximum deviation (percent) accepted for method names */
    public static int getMethodNameDeviationThreshold() {
        return methodNameDeviationThreshold;
    }

    /** @param threshold maximum deviation (percent, 0-100) accepted for method names */
    public static void setMethodNameDeviationThreshold(int threshold) {
        methodNameDeviationThreshold = checkThreshold(threshold);
    }

    /** @return maximum deviation (percent) accepted for attribute names */
    public static int getAttributeNameDeviationThreshold() {
        return attributeNameDeviationThreshold;
    }

    /** @param threshold maximum deviation (percent, 0-100) accepted for attribute names */
    public static void setAttributeNameDeviationThreshold(int threshold) {
        attributeNameDeviationThreshold = checkThreshold(threshold);
    }

    /**
     * @return whether {@code testGettersSetters} requires the getter/setter to keep the same object for
     *         reference values (default {@code false}: equal content is enough)
     * @see #setGettersSettersRequireSameObject(boolean)
     */
    public static boolean isGettersSettersRequireSameObject() {
        return gettersSettersRequireSameObject;
    }

    /**
     * Chooses how {@code testGettersSetters} compares the value it set with the value it reads back.
     * {@code false} (default): equal content is enough ({@link java.util.Objects#deepEquals(Object, Object)}),
     * so a getter or setter may store or return a copy, e.g. of an array.
     * {@code true}: for reference values the very same object is required ({@code ==}), so a copy fails.
     * Primitives, their wrappers, strings and enums are always compared by value.
     *
     * @param requireSameObject whether the same object is required for reference values
     */
    public static void setGettersSettersRequireSameObject(boolean requireSameObject) {
        gettersSettersRequireSameObject = requireSameObject;
    }

    /**
     * Restores all defaults (German messages, 20 % for every threshold, getter/setter values compared by content).
     */
    public static void reset() {
        language = Language.DEUTSCH;
        classNameDeviationThreshold = DEFAULT_THRESHOLD;
        methodNameDeviationThreshold = DEFAULT_THRESHOLD;
        attributeNameDeviationThreshold = DEFAULT_THRESHOLD;
        gettersSettersRequireSameObject = false;
    }

    private static int checkThreshold(int threshold) {
        if (threshold < 0 || threshold > 100) {
            throw new IllegalArgumentException("threshold must be between 0 and 100 (percent), was " + threshold);
        }
        return threshold;
    }
}
