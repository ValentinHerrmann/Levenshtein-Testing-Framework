package io.github.valentinherrmann.levenshtein;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.valentinherrmann.levenshtein.fixtures.BaseFixtures;
import io.github.valentinherrmann.levenshtein.fixtures.Fixtures;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests of {@link GettersSettersTest}: sample values, argument checks and the subtests.
 */
class GettersSettersTestTest {

    private final TestClassWrapper<Object> w = TestClassWrapper.base("GettersSetters", "public", "static");

    private <V> AttributeWrapper<Object, V> attribute(String name, Class<V> type) {
        return new AttributeWrapper<>(w, name, type, "private");
    }

    private MethodWrapper<Object, ?> getter(String name, Class<?> type) {
        return new MethodWrapper<>(w, name, type, "public");
    }

    private MethodWrapper<Object, Void> setter(String name, Class<?> type) {
        return new MethodWrapper<>(w, name, void.class, new Class<?>[]{type}, "public");
    }

    private String failure(Runnable test) {
        LevenshteinSettings.setLanguage(LevenshteinSettings.Language.ENGLISH);
        AssertionError e = org.junit.jupiter.api.Assertions.assertThrows(AssertionError.class, test::run);
        return e.getMessage();
    }

    @BeforeEach
    @AfterEach
    void reset() {
        LevenshteinSettings.reset();
        BaseFixtures.GettersSetters.SET.clear();
    }

    @Nested
    class GeneratedSamples {

        @Test
        void fiveValuesOfTheTypeFirstNotTheDefault() {
            assertThat(GettersSettersTest.generateSamples(int.class)).containsExactly(7, -3, 42, 1, 0);
            assertThat(GettersSettersTest.generateSamples(long.class)).containsExactly(7L, -3L, 42L, 1L, 0L);
            assertThat(GettersSettersTest.generateSamples(short.class))
                    .containsExactly((short) 7, (short) -3, (short) 42, (short) 1, (short) 0);
            assertThat(GettersSettersTest.generateSamples(byte.class))
                    .containsExactly((byte) 7, (byte) -3, (byte) 42, (byte) 1, (byte) 0);
            assertThat(GettersSettersTest.generateSamples(double.class)).containsExactly(2.5, -1.25, 42.0, 0.5, 0.0);
            assertThat(GettersSettersTest.generateSamples(float.class)).containsExactly(2.5f, -1.25f, 42.0f, 0.5f, 0.0f);
            assertThat(GettersSettersTest.generateSamples(char.class)).containsExactly('a', 'Z', '7', 'x', '_');
            assertThat(GettersSettersTest.generateSamples(boolean.class)).containsExactly(true, false, true, false, true);
        }

        @Test
        void referenceTypesAdditionallyGetNull() {
            assertThat(GettersSettersTest.generateSamples(Integer.class)).containsExactly(7, -3, 42, 1, 0, null);
            assertThat(GettersSettersTest.generateSamples(Boolean.class)).hasSize(6).endsWith((Object) null);
            assertThat(GettersSettersTest.generateSamples(String.class))
                    .containsExactly("Alpha", "beta", "", "Gamma 42", "x", null);
        }

        @Test
        void enumConstantsAreCycled() {
            assertThat(GettersSettersTest.generateSamples(Fixtures.Color.class)).containsExactly(
                    Fixtures.Color.RED, Fixtures.Color.GREEN, Fixtures.Color.RED, Fixtures.Color.GREEN, Fixtures.Color.RED, null);
            List<Object> all = new ArrayList<>(Arrays.asList(BaseFixtures.Weekday.values()));
            all.add(null);
            assertThat(GettersSettersTest.generateSamples(BaseFixtures.Weekday.class)).containsExactlyElementsOf(all);
        }

        @Test
        void otherTypesGetNoSamples() {
            assertThat(GettersSettersTest.generateSamples(Object.class)).isNull();
            assertThat(GettersSettersTest.generateSamples(int[].class)).isNull();
            assertThat(GettersSettersTest.generateSamples(BaseFixtures.Empty.class)).isNull();
            assertThat(GettersSettersTest.generateSamples(null)).isNull();
        }

        @Test
        void samplesOfAnotherStudentTypeAreUsedIfTheExpectedOnesDoNotFit() {
            // expected int; field float, getter and setter char: -3 does not fit into a char, 2.5f not into an int
            assertThatCode(() -> w.testGettersSetters(attribute("level", int.class),
                    getter("getLevel", int.class), setter("setLevel", int.class))).doesNotThrowAnyException();
            assertThat(BaseFixtures.GettersSetters.SET).containsOnly('a', 'Z', '7', 'x', '_');
        }

        @Test
        void nullIsUsedForReferenceTypesWithNumericDeviation() {
            // expected Integer, student Long: null fits as well
            assertThatCode(() -> w.testGettersSetters(attribute("total", Integer.class),
                    getter("getTotal", Integer.class), setter("setTotal", Integer.class))).doesNotThrowAnyException();
            assertThat(BaseFixtures.GettersSetters.SET).containsOnly(7L, -3L, 42L, 1L, 0L, null).contains((Object) null);
        }

        @Test
        void deviatingGetterOrSetterTypeIsConsidered() {
            assertThatCode(() -> w.testGettersSetters(attribute("code", int.class),
                    getter("getCode", int.class), setter("setCode", int.class))).doesNotThrowAnyException();
            assertThatCode(() -> w.testGettersSetters(attribute("key", int.class),
                    getter("getKey", int.class), setter("setKey", int.class))).doesNotThrowAnyException();
        }

        @Test
        void nullIsNotUsedIfOneOfTheStudentTypesIsPrimitive() {
            assertThatCode(() -> w.testGettersSetters(attribute("count", Integer.class),
                    getter("getCount", Integer.class), setter("setCount", Integer.class))).doesNotThrowAnyException();
            assertThatCode(() -> w.testGettersSetters(attribute("amount", Integer.class),
                    getter("getAmount", Integer.class), setter("setAmount", Integer.class))).doesNotThrowAnyException();
            assertThatCode(() -> w.testGettersSetters(attribute("number", Integer.class),
                    getter("getNumber", Integer.class), setter("setNumber", Integer.class))).doesNotThrowAnyException();
        }
    }

    @Nested
    class Misuse {

        private final AttributeWrapper<Object, Integer> plain = attribute("plain", int.class);

        @Test
        void attributeIsRequired() {
            assertThatThrownBy(() -> w.testGettersSetters(null, getter("getPlain", int.class)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("needs an attribute wrapper");
        }

        @Test
        void getterOrSetterIsRequired() {
            assertThatThrownBy(() -> w.testGettersSetters(plain, (MethodWrapper<?, ?>[]) null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("needs a getter (no parameters) and/or a setter");
        }

        @Test
        void nullWrapperIsRejected() {
            assertThatThrownBy(() -> w.testGettersSetters(plain, getter("getPlain", int.class), null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageEndingWith("but got null");
        }

        @Test
        void surplusWrapperIsNamed() {
            MethodWrapper<Object, Void> twoParams = new MethodWrapper<>(w, "setPlain", void.class,
                    new Class<?>[]{int.class, int.class}, "public");
            assertThatThrownBy(() -> w.testGettersSetters(plain, twoParams))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageEndingWith("but got " + twoParams.expectedToString());
            MethodWrapper<Object, Void> secondSetter = setter("setPlain", int.class);
            assertThatThrownBy(() -> w.testGettersSetters(plain, setter("setPlain", int.class), secondSetter))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageEndingWith("but got " + secondSetter.expectedToString());
        }

        @Test
        void usableSampleValuesAreRequired() {
            assertThatThrownBy(() -> w.testGettersSetters(plain, List.of(), setter("setPlain", int.class)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("at least one (usable) sample value");
            List<Object> onlyNull = new ArrayList<>();
            onlyNull.add(null);
            assertThatThrownBy(() -> w.testGettersSetters(plain, onlyNull, setter("setPlain", int.class)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("at least one (usable) sample value");
        }

        @Test
        void nullSampleIsDroppedForPrimitives() {
            List<Object> withNull = new ArrayList<>(List.of(3, 4));
            withNull.add(null);
            assertThatCode(() -> w.testGettersSetters(plain, withNull, getter("getPlain", int.class), setter("setPlain", int.class)))
                    .doesNotThrowAnyException();
        }

        @Test
        void unconvertibleSampleValueIsReportedAsSuch() {
            String message = failure(() -> w.testGettersSetters(plain, List.of(2.5), setter("setPlain", int.class)));
            assertThat(message)
                    .contains("cannot be converted to the expected type")
                    .doesNotContain("threw an exception");
        }
    }

    @Nested
    class Subtests {

        @Test
        void exceptionOfAValidSetterCallIsReported() {
            String message = failure(() -> w.testGettersSetters(attribute("broken", int.class),
                    getter("getBroken", int.class), setter("setBroken", int.class)));
            assertThat(message).isEqualTo("""
                    Method:   setBroken(int)
                    Scenario: setter -> attribute, attribute broken
                    Input:    setBroken(7)
                    Expected: 7
                    Actual:   IllegalStateException: nope
                    (10 of 15 cases failed)""");
        }

        @Test
        void exceptionOfTheGetterIsReportedInGermanWithoutCause() {
            LevenshteinSettings.setLanguage(LevenshteinSettings.Language.DEUTSCH);
            AssertionError e = org.junit.jupiter.api.Assertions.assertThrows(AssertionError.class,
                    () -> w.testGettersSetters(attribute("fragile", int.class),
                            getter("getFragile", int.class), setter("setFragile", int.class)));
            // setter -> attribute passes (5 cases), attribute -> getter fails 5 times, setter -> getter stops after 1
            assertThat(e).hasNoCause().hasMessage("""
                    Methode:     getFragile()
                    Szenario:    Attribut -> Getter, Attribut fragile
                    Eingabe:     fragile = 7
                    Erwartet:    7
                    Tatsächlich: UnsupportedOperationException: later
                    (6 von 11 Fällen fehlgeschlagen)""");
        }

        @Test
        void setterMayStoreAnInvalidValueIfItThrows() { // "keep the value unchanged or throw an exception"
            assertThatCode(() -> w.testGettersSetters(attribute("positive", int.class), (Integer p) -> p >= 0,
                    getter("getPositive", int.class), setter("setPositive", int.class))).doesNotThrowAnyException();
        }

        @Test
        void throwingConditionMarksTheValueInvalid() {
            // the condition throws a NullPointerException for null; setLabel rejects null
            assertThatCode(() -> w.testGettersSetters(attribute("label", String.class), (String s) -> s.length() >= 0,
                    getter("getLabel", String.class), setter("setLabel", String.class))).doesNotThrowAnyException();
        }

        @Test
        void actualNameOfADeviatingGetterIsShown() {
            String message = failure(() -> w.testGettersSetters(attribute("size", int.class), getter("getSize", int.class)));
            assertThat(message)
                    .startsWith("Method:   getSize()\nScenario: attribute -> getter, attribute size\nInput:    size = 7")
                    .contains("Expected: 7\nActual:   42")
                    .endsWith("(4 of 5 cases failed)");
        }

        @Test
        void getterOrSetterAloneCanBeTestedForReferenceTypes() {
            assertThatCode(() -> w.testGettersSetters(attribute("total", Integer.class), getter("getTotal", Integer.class)))
                    .doesNotThrowAnyException();
            assertThatCode(() -> w.testGettersSetters(attribute("total", Integer.class), setter("setTotal", Integer.class)))
                    .doesNotThrowAnyException();
        }

        @Test
        void getterOfAFinalAttributeIsChecked() {
            String message = failure(() -> w.testGettersSetters(attribute("limit", int.class), getter("getLimit", int.class)));
            assertThat(message).isEqualTo("""
                    Method:   getLimit()
                    Scenario: attribute -> getter, attribute limit
                    Input:    limit = 5
                    Expected: 5
                    Actual:   6""");
        }

        @Test
        void missingAttributeOrGetterIsReportedNotThrown() {
            // reference types, so the student's types are inspected for primitives
            String missingAttribute = failure(() -> w.testGettersSetters(attribute("ghostAttribute", Integer.class),
                    getter("getTotal", Integer.class), setter("setTotal", Integer.class)));
            assertThat(missingAttribute).contains("Attribute ghostAttribute in class BaseFixtures$GettersSetters is not implemented");

            String missingGetter = failure(() -> w.testGettersSetters(attribute("total", Integer.class),
                    getter("getGhostAttribute", Integer.class), setter("setTotal", Integer.class)));
            // a missing getter is a structural failure: short form, no block
            assertThat(missingGetter)
                    .contains("getGhostAttribute")
                    .contains("is not implemented as expected")
                    .doesNotContain("Actual:");
        }

        @Test
        void sameObjectIsNotRequiredForNumbersAndCharacters() {
            LevenshteinSettings.setGettersSettersRequireSameObject(true);
            assertThatCode(() -> w.testGettersSetters(attribute("price", double.class),
                    getter("getPrice", double.class), setter("setPrice", double.class)))
                    .doesNotThrowAnyException();
            // characters above 127 are not cached, so every boxing creates a new object
            assertThatCode(() -> w.testGettersSetters(attribute("initial", char.class), List.of('é', 'ж', 'ß'),
                    getter("getInitial", char.class), setter("setInitial", char.class)))
                    .doesNotThrowAnyException();
        }
    }
}
