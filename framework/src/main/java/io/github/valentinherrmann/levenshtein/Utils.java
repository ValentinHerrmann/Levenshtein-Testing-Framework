package io.github.valentinherrmann.levenshtein;

import static io.github.valentinherrmann.levenshtein.WrapperProperty.Existence.*;

import java.math.BigDecimal;
import java.math.BigInteger;

import org.assertj.core.api.Assertions;

public final class Utils
{
    private Utils() {
    }

    /**
     * Calculates the Levenshtein distance between two strings.
     * @param s1 First string
     * @param s2 Second string
     * @return The edit distance between the two strings, or -1 if one of them is null
     */
    public static int levenshteinDistance(String s1, String s2) {
        if (s1 == null || s2 == null) {
            return -1;
        }

        int[][] dp = new int[s1.length() + 1][s2.length() + 1];

        for (int i = 0; i <= s1.length(); i++) {
            dp[i][0] = i;
        }
        for (int j = 0; j <= s2.length(); j++) {
            dp[0][j] = j;
        }

        for (int i = 1; i <= s1.length(); i++) {
            for (int j = 1; j <= s2.length(); j++) {
                if (s1.charAt(i - 1) == s2.charAt(j - 1)) {
                    dp[i][j] = dp[i - 1][j - 1];
                } else {
                    dp[i][j] = 1 + Math.min(dp[i - 1][j - 1], Math.min(dp[i - 1][j], dp[i][j - 1]));
                }
            }
        }
        return dp[s1.length()][s2.length()];
    }

    /**
     * Calculates the Levenshtein distance as percentage of the length of the longer string.
     *
     * @param expected the expected string
     * @param actual the actual string
     * @return the deviation in percent (0 for two empty strings), or -1 if one of them is null
     */
    public static double levenshteinDistancePercent(String expected, String actual) {
        if (expected == null || actual == null) {
            return -1;
        }
        int maxLength = Math.max(expected.length(), actual.length());
        if (maxLength == 0) {
            return 0;
        }
        return levenshteinDistance(expected, actual) * 100.0 / maxLength;
    }

    /**
     * Checks if names are within a specified deviation threshold.
     * @param expectedName The expected name
     * @param actualName The actual name
     * @param threshold The deviation threshold in percent (0-100)
     * @return true if the deviation is within the threshold, false otherwise
     */
    public static boolean isNameWithinDeviation(String expectedName, String actualName, int threshold) {
        if (expectedName == null || actualName == null) {
            return false;
        }
        if (expectedName.equals(actualName)) {
            return true;
        }
        return levenshteinDistancePercent(expectedName, actualName) <= threshold;
    }

    /**
     * Converts a value to the given type: returns the value itself if it already is an instance,
     * or converts between numeric types (e.g. an {@code int} result where a {@code double} is expected;
     * {@code char} counts as numeric type). A numeric conversion must not change the value beyond rounding:
     * an integral type ({@code int}, {@code long}, {@code short}, {@code byte}, {@code char}) only takes a whole
     * number within its range (2.5 or 300 do not fit into a {@code byte}, 65 becomes {@code 'A'}); a {@code float}
     * takes every {@code double} that does not overflow it.
     * Fails with a readable assertion message if neither is possible.
     *
     * @param val the value to convert (must not be null)
     * @param castTo the expected type
     * @return the converted value
     */
    public static Object safeCast(Object val, Class<?> castTo) {
        return safeCast(val, castTo, false);
    }

    /**
     * Like {@link #safeCast(Object, Class)}, but optionally permits {@code null}.
     *
     * @param val the value to convert
     * @param castTo the expected type
     * @param allowNull whether {@code null} is accepted (and returned)
     * @return the converted value
     */
    public static Object safeCast(Object val, Class<?> castTo, boolean allowNull) {
        if (val == null) {
            if (!allowNull) {
                throw new IllegalArgumentException(Messages.NULL_NOT_ALLOWED.get());
            }
            return null;
        }
        if (castTo == null || castTo == void.class || toWrapperType(castTo).isInstance(val)) {
            return val;
        }
        Object converted = convertNumber(val, castTo);
        if (converted != null) {
            return converted;
        }
        return Assertions.fail(Messages.CAST_FAILED.format(val, val.getClass().getSimpleName(), castTo.getSimpleName()));
    }

    /**
     * @param type a type
     * @return whether the type is a numeric primitive or wrapper type, or {@code char}/{@link Character}
     */
    static boolean isNumericOrChar(Class<?> type) {
        Class<?> t = toWrapperType(type);
        return t == Integer.class || t == Long.class || t == Short.class || t == Byte.class
                || t == Double.class || t == Float.class || t == Character.class;
    }

    /**
     * Converts a number or character to a numeric type (or {@code char}) as described in
     * {@link #safeCast(Object, Class)}.
     *
     * @param val the value
     * @param castTo the target type (primitive or wrapper)
     * @return the converted value, or {@code null} if the value is no number or does not fit
     */
    static Object convertNumber(Object val, Class<?> castTo) {
        Object numeric = val instanceof Character c ? Integer.valueOf(c) : val;
        if (!(numeric instanceof Number n)) {
            return null;
        }
        Class<?> target = toWrapperType(castTo);
        if (target == Double.class) {
            return n.doubleValue();
        }
        if (target == Float.class) {
            double d = n.doubleValue();
            return Double.isFinite(d) && Math.abs(d) > Float.MAX_VALUE ? null : (float) d;
        }
        long whole;
        if (n instanceof BigDecimal bd) {
            // Exact: going through doubleValue() would silently round integers beyond 2^53.
            if (bd.precision() - bd.scale() > 19) {
                return null; // more than 19 integer digits never fit a long; avoids building a huge BigInteger
            }
            BigInteger exact;
            try {
                exact = bd.toBigIntegerExact();
            }
            catch (ArithmeticException notWhole) {
                return null;
            }
            if (exact.bitLength() > 63) {
                return null;
            }
            whole = exact.longValue();
        }
        else if (n instanceof Double || n instanceof Float) {
            double d = n.doubleValue();
            if (!Double.isFinite(d) || d != Math.rint(d) || d < -0x1p63 || d >= 0x1p63) {
                return null;
            }
            whole = (long) d;
        }
        else if (n instanceof BigInteger b) {
            if (b.bitLength() > 63) {
                return null;
            }
            whole = b.longValue();
        }
        else {
            whole = n.longValue();
        }
        if (target == Long.class) return whole;
        if (target == Integer.class) return fits(whole, Integer.MIN_VALUE, Integer.MAX_VALUE) ? (int) whole : null;
        if (target == Short.class) return fits(whole, Short.MIN_VALUE, Short.MAX_VALUE) ? (short) whole : null;
        if (target == Byte.class) return fits(whole, Byte.MIN_VALUE, Byte.MAX_VALUE) ? (byte) whole : null;
        if (target == Character.class) return fits(whole, Character.MIN_VALUE, Character.MAX_VALUE) ? (char) whole : null;
        return null;
    }

    private static boolean fits(long value, long min, long max) {
        return value >= min && value <= max;
    }


    /**
     * Selects the worse existence state between two states, ignoring {@code UNCHECKED}.
     * Used to aggregate multiple property existence states into a single worst-case state.
     *
     * @param worst the current worst existence state
     * @param updated the new existence state to compare
     * @return the worse of the two existence states
     * @see WrapperProperty.Existence#worst(WrapperProperty.Existence, WrapperProperty.Existence)
     */
    public static WrapperProperty.Existence selectWorstExistence(WrapperProperty.Existence worst, WrapperProperty.Existence updated) {
        return WrapperProperty.Existence.worst(worst, updated);
    }



    /**
     * Verifies that the actual type matches or is compatible with the expected type.
     * Checks for exact match, primitive/wrapper equivalence (e.g. {@code int} vs. {@code Integer}),
     * assignability, or numeric type containment (e.g. long can contain int).
     * Updates the type property existence state accordingly.
     *
     * @param typeWrapperProperty the wrapper property containing the expected type
     * @param actualType the actual type found via reflection
     */
    public static void verifyType(WrapperProperty<Class<?>> typeWrapperProperty, Class<?> actualType) {
        typeWrapperProperty.actual = actualType;
        Class<?> expected = typeWrapperProperty.expected;
        if (expected == null || actualType == null) {
            typeWrapperProperty.existence = MISSING;
        }
        else if (expected.equals(actualType)) {
            typeWrapperProperty.existence = EXACT;
        }
        else if (toWrapperType(expected).equals(toWrapperType(actualType))) {
            typeWrapperProperty.existence = DEVIATES;
        }
        else if (actualType.isAssignableFrom(expected)) {
            typeWrapperProperty.existence = DEVIATES;
        }
        else if (isNumericDeviation(expected, actualType)) {
            typeWrapperProperty.existence = DEVIATES;
        }
        else {
            typeWrapperProperty.existence = MISSING;
        }
    }

    /**
     * Checks whether two types are different numeric types in either direction (e.g. {@code long} vs.
     * {@code int}, {@code Integer} vs. {@code double}). {@code boolean} and non-primitive types never qualify.
     *
     * @param expected the expected type
     * @param actual the actual type
     * @return true if both unwrap to numeric primitives (byte, short, char, int, long, float, double)
     */
    public static boolean isNumericDeviation(Class<?> expected, Class<?> actual) {
        Class<?> e = unwrapPrimitive(expected);
        Class<?> a = unwrapPrimitive(actual);
        return e.isPrimitive() && a.isPrimitive()
                && e != boolean.class && a != boolean.class
                && e != void.class && a != void.class;
    }


    /**
     * Checks if the actual primitive type can contain the expected primitive type, i.e. whether Java's widening
     * primitive conversion goes from the expected to the actual type.
     * For example, a long can contain an int, a double can contain a float, and every numeric type except
     * byte and short can contain a char.
     *
     * @param actualType the actual type to check
     * @param expectedType the expected type to verify containment for
     * @return true if actualType can contain values of expectedType, false otherwise
     */
    public static boolean canContain(Class<?> actualType, Class<?> expectedType) {
        // Unwrap primitive wrapper classes
        Class<?> actual = unwrapPrimitive(actualType);
        Class<?> expected = unwrapPrimitive(expectedType);

        if (!actual.isPrimitive() || !expected.isPrimitive()) {
            return false;
        }

        // Check if actual numeric type can contain expected numeric type
        return (actual == long.class && (expected == int.class || expected == short.class || expected == byte.class || expected == char.class)) ||
                (actual == int.class && (expected == short.class || expected == byte.class || expected == char.class)) ||
                (actual == short.class && expected == byte.class) ||
                (actual == double.class && (expected == float.class || expected == long.class || expected == int.class || expected == short.class || expected == byte.class || expected == char.class)) ||
                (actual == float.class && (expected == long.class || expected == int.class || expected == short.class || expected == byte.class || expected == char.class));
    }

    /**
     * Unwraps a primitive wrapper class to its corresponding primitive type.
     * For example, Integer.class is unwrapped to int.class.
     *
     * @param type the type to unwrap
     * @return the primitive type if type is a wrapper, otherwise the original type
     */
    public static Class<?> unwrapPrimitive(Class<?> type) {
        if (type == Integer.class) return int.class;
        if (type == Long.class) return long.class;
        if (type == Short.class) return short.class;
        if (type == Byte.class) return byte.class;
        if (type == Double.class) return double.class;
        if (type == Float.class) return float.class;
        if (type == Character.class) return char.class;
        if (type == Boolean.class) return boolean.class;
        return type;
    }

    /**
     * Converts a primitive type to its corresponding wrapper type.
     * For example, int.class is converted to Integer.class.
     *
     * @param type the type to convert
     * @return the wrapper type if type is primitive, otherwise the original type
     */
    public static Class<?> toWrapperType(Class<?> type) {
        if(type == int.class) return Integer.class;
        if(type == long.class) return Long.class;
        if(type == double.class) return Double.class;
        if(type == float.class) return Float.class;
        if(type == boolean.class) return Boolean.class;
        if(type == short.class) return Short.class;
        if(type == byte.class) return Byte.class;
        if(type == char.class) return Character.class;
        if(type == void.class) return Void.class;
        return type;
    }
}
