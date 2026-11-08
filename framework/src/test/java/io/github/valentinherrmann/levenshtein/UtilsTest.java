package io.github.valentinherrmann.levenshtein;

import static io.github.valentinherrmann.levenshtein.WrapperProperty.Existence.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests of the helper methods in {@link Utils}.
 */
class UtilsTest {

    private static final List<Class<?>> NUMERIC_PRIMITIVES =
            List.of(byte.class, short.class, char.class, int.class, long.class, float.class, double.class);

    @AfterEach
    void resetSettings() {
        LevenshteinSettings.reset();
    }

    @Nested
    class Levenshtein {

        @Test
        void distanceCountsInsertsDeletesAndReplacements() {
            assertThat(Utils.levenshteinDistance("kitten", "sitting")).isEqualTo(3);
            assertThat(Utils.levenshteinDistance("price", "pric")).isEqualTo(1);
            assertThat(Utils.levenshteinDistance("pric", "price")).isEqualTo(1);
            assertThat(Utils.levenshteinDistance("calculateCost", "calculateCots")).isEqualTo(2);
            assertThat(Utils.levenshteinDistance("abc", "abc")).isZero();
            assertThat(Utils.levenshteinDistance("", "abc")).isEqualTo(3);
            assertThat(Utils.levenshteinDistance("abcd", "")).isEqualTo(4);
            assertThat(Utils.levenshteinDistance("", "")).isZero();
        }

        @Test
        void distanceOfNullIsMinusOne() {
            assertThat(Utils.levenshteinDistance(null, "abc")).isEqualTo(-1);
            assertThat(Utils.levenshteinDistance("abc", null)).isEqualTo(-1);
            assertThat(Utils.levenshteinDistance(null, null)).isEqualTo(-1);
        }

        @Test
        void percentIsRelativeToTheLongerString() {
            assertThat(Utils.levenshteinDistancePercent("price", "pric")).isEqualTo(20.0);
            assertThat(Utils.levenshteinDistancePercent("pric", "price")).isEqualTo(20.0);
            assertThat(Utils.levenshteinDistancePercent("Car", "Cra")).isCloseTo(66.67, within(0.01));
            assertThat(Utils.levenshteinDistancePercent("same", "same")).isZero();
            assertThat(Utils.levenshteinDistancePercent("", "")).isZero();
            assertThat(Utils.levenshteinDistancePercent("", "ab")).isEqualTo(100.0);
        }

        @Test
        void percentOfNullIsMinusOne() {
            assertThat(Utils.levenshteinDistancePercent(null, "abc")).isEqualTo(-1.0);
            assertThat(Utils.levenshteinDistancePercent("abc", null)).isEqualTo(-1.0);
        }

        @Test
        void nameWithinDeviationIncludesTheThreshold() {
            assertThat(Utils.isNameWithinDeviation("price", "pric", 20)).isTrue();  // exactly 20 %
            assertThat(Utils.isNameWithinDeviation("price", "pric", 19)).isFalse();
            assertThat(Utils.isNameWithinDeviation("Car", "Cra", 20)).isFalse();
            assertThat(Utils.isNameWithinDeviation("Car", "Cra", 100)).isTrue();
        }

        @Test
        void equalNamesAreAlwaysWithinDeviation() {
            assertThat(Utils.isNameWithinDeviation("price", "price", 0)).isTrue();
            assertThat(Utils.isNameWithinDeviation("", "", 0)).isTrue();
        }

        @Test
        void nullNamesAreNeverWithinDeviation() {
            assertThat(Utils.isNameWithinDeviation(null, "price", 100)).isFalse();
            assertThat(Utils.isNameWithinDeviation("price", null, 100)).isFalse();
            assertThat(Utils.isNameWithinDeviation(null, null, 100)).isFalse();
        }
    }

    @Nested
    class SafeCast {

        @Test
        void instancesAreReturnedAsTheyAre() {
            Object value = "text";
            assertThat(Utils.safeCast(value, String.class)).isSameAs(value);
            assertThat(Utils.safeCast(value, Object.class)).isSameAs(value);
            Integer boxed = 1234;
            assertThat(Utils.safeCast(boxed, int.class)).isSameAs(boxed);
            assertThat(Utils.safeCast(value, null)).isSameAs(value);
            assertThat(Utils.safeCast(value, void.class)).isSameAs(value);
        }

        @Test
        void nullIsOnlyAcceptedIfAllowed() {
            assertThatThrownBy(() -> Utils.safeCast(null, Integer.class)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> Utils.safeCast(null, Integer.class, false)).isInstanceOf(IllegalArgumentException.class);
            assertThat(Utils.safeCast(null, Integer.class, true)).isNull();
            assertThat(Utils.safeCast(null, int.class, true)).isNull();
        }

        @Test
        void numbersAreConvertedToEveryNumericType() {
            assertThat(Utils.safeCast(7, double.class)).isEqualTo(7.0);
            assertThat(Utils.safeCast(7, Double.class)).isEqualTo(7.0);
            assertThat(Utils.safeCast(7, float.class)).isEqualTo(7.0f);
            assertThat(Utils.safeCast(7, long.class)).isEqualTo(7L);
            assertThat(Utils.safeCast(7L, int.class)).isEqualTo(7);
            assertThat(Utils.safeCast(7, short.class)).isEqualTo((short) 7);
            assertThat(Utils.safeCast(7, byte.class)).isEqualTo((byte) 7);
            assertThat(Utils.safeCast(65, char.class)).isEqualTo('A');
            assertThat(Utils.safeCast('A', int.class)).isEqualTo(65);
            assertThat(Utils.safeCast('A', double.class)).isEqualTo(65.0);
            assertThat(Utils.safeCast(42.0, int.class)).isEqualTo(42);
            assertThat(Utils.safeCast(2.5f, double.class)).isEqualTo(2.5);
            assertThat(Utils.safeCast(new AtomicInteger(3), long.class)).isEqualTo(3L);
        }

        @Test
        void wholeNumbersOfEveryKindAreConverted() {
            assertThat(Utils.safeCast(3.0f, int.class)).isEqualTo(3);
            assertThat(Utils.safeCast(new BigDecimal("3"), int.class)).isEqualTo(3);
            assertThat(Utils.safeCast(BigInteger.valueOf(3), int.class)).isEqualTo(3);
        }

        @Test
        void fractionsDoNotFitIntoIntegralTypes() {
            for (Class<?> integral : List.of(int.class, long.class, short.class, byte.class, char.class)) {
                assertThatThrownBy(() -> Utils.safeCast(2.5, integral)).isInstanceOf(AssertionError.class);
            }
            assertThatThrownBy(() -> Utils.safeCast(2.5f, int.class)).isInstanceOf(AssertionError.class);
            assertThatThrownBy(() -> Utils.safeCast(new BigDecimal("2.5"), int.class)).isInstanceOf(AssertionError.class);
            assertThatThrownBy(() -> Utils.safeCast(Double.NaN, long.class)).isInstanceOf(AssertionError.class);
            assertThatThrownBy(() -> Utils.safeCast(Double.POSITIVE_INFINITY, long.class)).isInstanceOf(AssertionError.class);
        }

        @Test
        void integralRangesAreChecked() {
            assertThat(Utils.safeCast((long) Integer.MAX_VALUE, int.class)).isEqualTo(Integer.MAX_VALUE);
            assertThat(Utils.safeCast((long) Integer.MIN_VALUE, int.class)).isEqualTo(Integer.MIN_VALUE);
            assertThatThrownBy(() -> Utils.safeCast(Integer.MAX_VALUE + 1L, int.class)).isInstanceOf(AssertionError.class);
            assertThatThrownBy(() -> Utils.safeCast(Integer.MIN_VALUE - 1L, int.class)).isInstanceOf(AssertionError.class);

            assertThat(Utils.safeCast(32767, short.class)).isEqualTo(Short.MAX_VALUE);
            assertThat(Utils.safeCast(-32768, short.class)).isEqualTo(Short.MIN_VALUE);
            assertThatThrownBy(() -> Utils.safeCast(32768, short.class)).isInstanceOf(AssertionError.class);
            assertThatThrownBy(() -> Utils.safeCast(-32769, short.class)).isInstanceOf(AssertionError.class);

            assertThat(Utils.safeCast(127, byte.class)).isEqualTo(Byte.MAX_VALUE);
            assertThat(Utils.safeCast(-128, byte.class)).isEqualTo(Byte.MIN_VALUE);
            assertThatThrownBy(() -> Utils.safeCast(128, byte.class)).isInstanceOf(AssertionError.class);
            assertThatThrownBy(() -> Utils.safeCast(-129, byte.class)).isInstanceOf(AssertionError.class);

            assertThat(Utils.safeCast(0, char.class)).isEqualTo(Character.MIN_VALUE);
            assertThat(Utils.safeCast(65535, char.class)).isEqualTo(Character.MAX_VALUE);
            assertThatThrownBy(() -> Utils.safeCast(-1, char.class)).isInstanceOf(AssertionError.class);
            assertThatThrownBy(() -> Utils.safeCast(65536, char.class)).isInstanceOf(AssertionError.class);
        }

        @Test
        void longRangeIsCheckedForFloatingPointValues() {
            // -2^63 is Long.MIN_VALUE, 2^63 is one more than Long.MAX_VALUE
            assertThat(Utils.safeCast(-0x1p63, long.class)).isEqualTo(Long.MIN_VALUE);
            assertThatThrownBy(() -> Utils.safeCast(0x1p63, long.class)).isInstanceOf(AssertionError.class);
            assertThatThrownBy(() -> Utils.safeCast(-0x1p64, long.class)).isInstanceOf(AssertionError.class);
        }

        @Test
        void longRangeIsCheckedForBigIntegers() {
            assertThat(Utils.safeCast(BigInteger.valueOf(Long.MAX_VALUE), long.class)).isEqualTo(Long.MAX_VALUE);
            assertThat(Utils.safeCast(BigInteger.valueOf(Long.MIN_VALUE), long.class)).isEqualTo(Long.MIN_VALUE);
            assertThatThrownBy(() -> Utils.safeCast(BigInteger.ONE.shiftLeft(63), long.class))
                    .isInstanceOf(AssertionError.class);
        }

        @Test
        void bigDecimalsAreConvertedExactly() {
            // 2^53 + 1 cannot be represented as double; the conversion must not round it to 2^53.
            assertThat(Utils.safeCast(new BigDecimal("9007199254740993"), long.class)).isEqualTo(9007199254740993L);
            assertThat(Utils.safeCast(new BigDecimal("4.000"), int.class)).isEqualTo(4);
            assertThat(Utils.safeCast(new BigDecimal(Long.MIN_VALUE), long.class)).isEqualTo(Long.MIN_VALUE);
            assertThatThrownBy(() -> Utils.safeCast(new BigDecimal("9007199254740993.5"), long.class))
                    .isInstanceOf(AssertionError.class);
            assertThatThrownBy(() -> Utils.safeCast(new BigDecimal(BigInteger.ONE.shiftLeft(63)), long.class))
                    .isInstanceOf(AssertionError.class);
        }

        @Test
        void floatTakesEveryDoubleThatDoesNotOverflow() {
            assertThat(Utils.safeCast((double) Float.MAX_VALUE, float.class)).isEqualTo(Float.MAX_VALUE);
            assertThat(Utils.safeCast(-(double) Float.MAX_VALUE, float.class)).isEqualTo(-Float.MAX_VALUE);
            assertThat(Utils.safeCast(0.1, float.class)).isEqualTo(0.1f);
            assertThat(Utils.safeCast(Double.POSITIVE_INFINITY, float.class)).isEqualTo(Float.POSITIVE_INFINITY);
            assertThat((Float) Utils.safeCast(Double.NaN, float.class)).isNaN();
            assertThatThrownBy(() -> Utils.safeCast(1e39, float.class)).isInstanceOf(AssertionError.class);
            assertThatThrownBy(() -> Utils.safeCast(-1e39, float.class)).isInstanceOf(AssertionError.class);
        }

        @Test
        void otherTypesCannotBeConverted() {
            LevenshteinSettings.setLanguage(LevenshteinSettings.Language.ENGLISH);
            assertThatThrownBy(() -> Utils.safeCast("5", int.class))
                    .isInstanceOf(AssertionError.class)
                    .hasMessage("The value 5 of type String cannot be converted to the expected type int.");
            assertThatThrownBy(() -> Utils.safeCast(1, boolean.class)).isInstanceOf(AssertionError.class);
            assertThatThrownBy(() -> Utils.safeCast(true, int.class)).isInstanceOf(AssertionError.class);
            assertThatThrownBy(() -> Utils.safeCast(1, String.class)).isInstanceOf(AssertionError.class);
        }

        @Test
        void numericOrCharCoversExactlyTheNumericTypesAndChar() {
            for (Class<?> type : NUMERIC_PRIMITIVES) {
                assertThat(Utils.isNumericOrChar(type)).as(type.getName()).isTrue();
                assertThat(Utils.isNumericOrChar(Utils.toWrapperType(type))).as(type.getName()).isTrue();
            }
            for (Class<?> type : List.of(boolean.class, Boolean.class, void.class, String.class, Object.class, Number.class)) {
                assertThat(Utils.isNumericOrChar(type)).as(type.getName()).isFalse();
            }
        }
    }

    @Nested
    class Types {

        @Test
        void primitivesAndWrappersAreMappedBothWays() {
            List<Class<?>> primitives = List.of(int.class, long.class, double.class, float.class, boolean.class,
                    short.class, byte.class, char.class);
            List<Class<?>> wrappers = List.of(Integer.class, Long.class, Double.class, Float.class, Boolean.class,
                    Short.class, Byte.class, Character.class);
            for (int i = 0; i < primitives.size(); i++) {
                assertThat(Utils.toWrapperType(primitives.get(i))).isEqualTo(wrappers.get(i));
                assertThat(Utils.unwrapPrimitive(wrappers.get(i))).isEqualTo(primitives.get(i));
                assertThat(Utils.unwrapPrimitive(primitives.get(i))).isEqualTo(primitives.get(i));
                assertThat(Utils.toWrapperType(wrappers.get(i))).isEqualTo(wrappers.get(i));
            }
            assertThat(Utils.toWrapperType(void.class)).isEqualTo(Void.class);
            assertThat(Utils.toWrapperType(String.class)).isEqualTo(String.class);
            assertThat(Utils.unwrapPrimitive(String.class)).isEqualTo(String.class);
            assertThat(Utils.unwrapPrimitive(Void.class)).isEqualTo(Void.class);
        }

        @Test
        void canContainFollowsWideningPrimitiveConversions() {
            // JLS 5.1.2: the widening primitive conversions
            assertContains(short.class, byte.class);
            assertContains(int.class, byte.class, short.class, char.class);
            assertContains(long.class, byte.class, short.class, char.class, int.class);
            assertContains(float.class, byte.class, short.class, char.class, int.class, long.class);
            assertContains(double.class, byte.class, short.class, char.class, int.class, long.class, float.class);
            assertContains(byte.class);
            assertContains(char.class);
        }

        @Test
        void charFitsIntoFloatingPointTypes() { // every char value (0-65535) is exactly representable as float and double
            assertThat(Utils.canContain(double.class, char.class)).isTrue();
            assertThat(Utils.canContain(float.class, char.class)).isTrue();
            assertThat(Utils.canContain(Double.class, Character.class)).isTrue();
        }

        @Test
        void canContainUnwrapsWrappersAndRejectsOtherTypes() {
            assertThat(Utils.canContain(Long.class, Integer.class)).isTrue();
            assertThat(Utils.canContain(long.class, Integer.class)).isTrue();
            assertThat(Utils.canContain(boolean.class, boolean.class)).isFalse();
            assertThat(Utils.canContain(int.class, boolean.class)).isFalse();
            assertThat(Utils.canContain(Object.class, int.class)).isFalse();
            assertThat(Utils.canContain(int.class, String.class)).isFalse();
        }

        private void assertContains(Class<?> actual, Class<?>... contained) {
            List<Class<?>> containedList = List.of(contained);
            for (Class<?> expected : NUMERIC_PRIMITIVES) {
                assertThat(Utils.canContain(actual, expected))
                        .as("%s can contain %s", actual, expected)
                        .isEqualTo(containedList.contains(expected));
            }
        }

        @Test
        void numericDeviationIsAnyPairOfNumericTypes() {
            for (Class<?> a : NUMERIC_PRIMITIVES) {
                for (Class<?> b : NUMERIC_PRIMITIVES) {
                    assertThat(Utils.isNumericDeviation(a, b)).isTrue();
                    assertThat(Utils.isNumericDeviation(Utils.toWrapperType(a), b)).isTrue();
                }
            }
        }

        @Test
        void booleanVoidAndReferenceTypesAreNoNumericDeviation() {
            assertThat(Utils.isNumericDeviation(boolean.class, int.class)).isFalse();
            assertThat(Utils.isNumericDeviation(int.class, boolean.class)).isFalse();
            assertThat(Utils.isNumericDeviation(Boolean.class, Integer.class)).isFalse();
            assertThat(Utils.isNumericDeviation(void.class, int.class)).isFalse();
            assertThat(Utils.isNumericDeviation(int.class, void.class)).isFalse();
            assertThat(Utils.isNumericDeviation(String.class, int.class)).isFalse();
            assertThat(Utils.isNumericDeviation(int.class, Number.class)).isFalse();
        }

        private WrapperProperty<Class<?>> verify(Class<?> expected, Class<?> actual) {
            WrapperProperty<Class<?>> property = new WrapperProperty<>(expected);
            Utils.verifyType(property, actual);
            assertThat(property.getActual()).isEqualTo(actual);
            return property;
        }

        @Test
        void verifyTypeGradesAsDocumented() { // docs/matching.md, section "Types"
            assertThat(verify(double.class, double.class).getExistence()).isEqualTo(EXACT);
            assertThat(verify(String.class, String.class).getExistence()).isEqualTo(EXACT);
            assertThat(verify(int.class, Integer.class).getExistence()).isEqualTo(DEVIATES);
            assertThat(verify(Integer.class, int.class).getExistence()).isEqualTo(DEVIATES);
            assertThat(verify(boolean.class, Boolean.class).getExistence()).isEqualTo(DEVIATES);
            assertThat(verify(Boolean.class, boolean.class).getExistence()).isEqualTo(DEVIATES);
            assertThat(verify(String.class, Object.class).getExistence()).isEqualTo(DEVIATES);
            assertThat(verify(String.class, CharSequence.class).getExistence()).isEqualTo(DEVIATES);
            assertThat(verify(int.class, long.class).getExistence()).isEqualTo(DEVIATES);
            assertThat(verify(long.class, int.class).getExistence()).isEqualTo(DEVIATES);
            assertThat(verify(double.class, int.class).getExistence()).isEqualTo(DEVIATES);
            assertThat(verify(char.class, double.class).getExistence()).isEqualTo(DEVIATES);
            assertThat(verify(String.class, int.class).getExistence()).isEqualTo(MISSING);
            assertThat(verify(boolean.class, int.class).getExistence()).isEqualTo(MISSING);
            assertThat(verify(Object.class, String.class).getExistence()).isEqualTo(MISSING);
        }

        @Test
        void verifyTypeWithoutTypesIsMissing() {
            assertThat(verify(null, int.class).getExistence()).isEqualTo(MISSING);
            assertThat(verify(int.class, null).getExistence()).isEqualTo(MISSING);
        }
    }

    @Test
    void worstExistenceIgnoresUnchecked() {
        assertThat(Utils.selectWorstExistence(EXACT, DEVIATES)).isEqualTo(DEVIATES);
        assertThat(Utils.selectWorstExistence(MISSING, DEVIATES)).isEqualTo(MISSING);
        assertThat(Utils.selectWorstExistence(UNCHECKED, EXACT)).isEqualTo(EXACT);
        assertThat(Utils.selectWorstExistence(DEVIATES, UNCHECKED)).isEqualTo(DEVIATES);
        assertThat(Utils.selectWorstExistence(UNCHECKED, UNCHECKED)).isEqualTo(UNCHECKED);
    }
}
