package io.github.valentinherrmann.levenshtein;


/**
 * A wrapper for properties of reflection elements that tracks expected vs. actual values
 * and their existence state (exact match, deviation, missing, or unchecked).
 * DEVIATES indicates a partial match or acceptable deviation which allows behavioral testing
 * using this component. For names the acceptable deviation is set via {@link LevenshteinSettings}; for types
 * it is based on assignability, numeric widening and primitive-wrapper equivalence.
 *
 * @param <T> the type of the property being wrapped
 */
public class WrapperProperty<T> {
    /**
     * Enumeration representing the existence state of a property.
     *
     * <p>{@link #UNCHECKED} means "unknown" and never takes part in "worst state wins" aggregation
     * (see {@link #worst(Existence, Existence)}). The remaining states are ordered by their explicit
     * {@link #severity()}, not by their declaration order.</p>
     */
    public enum Existence {
        /** Property has not yet been checked (unknown). */
        UNCHECKED(-1),
        /** Property matches exactly with expected value */
        EXACT(0),
        /** Property exists but deviates from expected value */
        DEVIATES(1),
        /** Property is missing or does not match at all */
        MISSING(2);

        private final int severity;

        Existence(int severity) {
            this.severity = severity;
        }

        /** @return the severity used for aggregation; {@code -1} for {@link #UNCHECKED} */
        public int severity() {
            return severity;
        }

        /**
         * Returns the worse of two states. {@link #UNCHECKED} is ignored: it only results if both
         * states are unchecked.
         *
         * @param a first state
         * @param b second state
         * @return the state with the higher severity, ignoring unchecked states
         */
        public static Existence worst(Existence a, Existence b) {
            if (a == null || a == UNCHECKED) {
                return b == null ? UNCHECKED : b;
            }
            if (b == null || b == UNCHECKED) {
                return a;
            }
            return a.severity >= b.severity ? a : b;
        }
    }

    /**
     * The expected value of this property.
     */
    T expected;

    /**
     * The actual value found for this property.
     */
    T actual;

    /**
     * The existence state of this property.
     */
    Existence existence;

    /**
     * Constructs a new WrapperProperty with an expected value.
     * The actual value is initialized to null and existence is set to UNCHECKED.
     *
     * @param expected the expected value of this property
     */
    public WrapperProperty(T expected) {
        this.expected = expected;
        this.actual = null;
        this.existence = Existence.UNCHECKED;
    }

    /** @return the expected value */
    public T getExpected() {
        return expected;
    }

    /** @return the actual value found via reflection, or {@code null} if not (yet) found */
    public T getActual() {
        return actual;
    }

    /** @return the existence state of this property */
    public Existence getExistence() {
        return existence;
    }

    /**
     * Returns a string representation showing expected, actual, and existence state.
     *
     * @return a formatted string with property details
     */
    @Override
    public String toString() {
        return String.format("Expected: %s, Actual: %s, Existence: %s", expected, actual, existence);
    }
}
