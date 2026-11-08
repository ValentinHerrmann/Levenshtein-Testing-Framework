package io.github.valentinherrmann.levenshtein;

import static io.github.valentinherrmann.levenshtein.WrapperProperty.Existence.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.valentinherrmann.levenshtein.ClassWrapperTest.W;
import io.github.valentinherrmann.levenshtein.fixtures.ClassFixtures;
import io.github.valentinherrmann.levenshtein.fixtures.Fixtures;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests of {@link ConstructorWrapper}: lookup, invocation, expected exceptions and reports.
 */
class ConstructorWrapperTest {

    private static W<Object> classFixture(String simpleName, String... modifiers) {
        return ClassWrapperTest.classFixture(simpleName, modifiers);
    }

    private static ConstructorWrapper<Object> ctor(W<Object> parent, String modifiers, Class<?>... paramTypes) {
        return new ConstructorWrapper<>(parent, paramTypes, modifiers);
    }

    @BeforeEach
    void english() {
        LevenshteinSettings.setLanguage(LevenshteinSettings.Language.ENGLISH);
    }

    @AfterEach
    void resetSettings() {
        LevenshteinSettings.reset();
    }

    @Nested
    class Lookup {

        @Test
        void nullParameterTypesMeanNoParameters() {
            W<Object> ctors = ClassWrapperTest.fixture("Ctors", "public static");
            ConstructorWrapper<Object> c = new ConstructorWrapper<>(ctors, (Class<?>[]) null, "private");
            assertThat(c.getOverallExistence()).isEqualTo(EXACT);
            assertThat(c.getParamTypes()).isEmpty();
            assertThat(((Fixtures.Ctors) c.invoke()).how).isEqualTo("private");
        }

        @Test
        void constructorOfMissingClassIsMissing() {
            W<Object> ghost = classFixture("DoesNotExist", "public");
            ConstructorWrapper<Object> c = ctor(ghost, "public", int.class);
            assertThatCode(c::getOverallExistence).doesNotThrowAnyException();
            assertThat(c.getOverallExistence()).isEqualTo(MISSING);
            assertThat(c.actualToString()).isEqualTo("<missing>");
            assertThat(c.getActualParamTypes()).containsExactly(int.class);
            assertThatThrownBy(() -> c.invoke(1))
                    .isInstanceOf(AssertionError.class)
                    .hasMessageContaining("Constructor public ClassFixtures$DoesNotExist(int) in class ClassFixtures$DoesNotExist is not implemented");
        }

        @Test
        void primitiveWrapperPairIsCloserThanNumericDeviations() {
            ConstructorWrapper<Object> c = ctor(classFixture("Overloads", "public static"), "public", int.class);
            assertThat(c.getOverallExistence()).isEqualTo(DEVIATES);
            assertThat(c.getActualParamTypes()).containsExactly(Integer.class);
            assertThat(((ClassFixtures.Overloads) c.invoke(7)).chosen).isEqualTo("Integer");
        }

        @Test
        void exactConstructorIsPreferredAndNumericDeviationsAreFound() {
            W<Object> garage = classFixture("Garage", "public static");
            ConstructorWrapper<Object> exact = ctor(garage, "public", int.class, String.class);
            assertThat(exact.getOverallExistence()).isEqualTo(EXACT);
            assertThat(((ClassFixtures.Garage) exact.invoke(3, "G")).chosen).isEqualTo("G3");

            ConstructorWrapper<Object> wrapper = ctor(garage, "public", Integer.class, String.class);
            assertThat(wrapper.getOverallExistence()).isEqualTo(DEVIATES);
            assertThat(wrapper.getActualParamTypes()).containsExactly(int.class, String.class);

            ConstructorWrapper<Object> numeric = ctor(garage, "protected", long.class);
            assertThat(numeric.getOverallExistence()).isEqualTo(DEVIATES);
            assertThat(numeric.getActualParamTypes()).containsExactly(Integer.class);
            assertThat(((ClassFixtures.Garage) numeric.invoke(5L)).chosen).isEqualTo("Integer 5");

            assertThat(ctor(garage, "public", String.class, int.class).getOverallExistence()).isEqualTo(MISSING);
            assertThat(ctor(garage, "public", boolean.class).getOverallExistence()).isEqualTo(MISSING);
        }

        @Test
        void actualParameterTypesTriggerTheLookup() {
            ConstructorWrapper<Object> c = ctor(classFixture("Overloads", "public static"), "public", int.class);
            assertThat(c.getActualParamTypes()).containsExactly(Integer.class); // first call on a fresh wrapper
        }

        @Test
        void expectedParameterTypesAreACopy() {
            Class<?>[] types = {int.class};
            ConstructorWrapper<Object> c = ctor(classFixture("Overloads", "public static"), "public", types);
            types[0] = String.class;
            assertThat(c.getParamTypes()).containsExactly(int.class);
            c.getParamTypes()[0] = String.class;
            assertThat(c.getParamTypes()).containsExactly(int.class);
        }
    }

    @Nested
    class Invocation {

        private final W<Object> critical = classFixture("CriticalCtors", "public static");

        @Test
        void assertionErrorOfTheStudentGetsTheArguments() {
            assertThatThrownBy(() -> ctor(critical, "public", String.class).invoke("x"))
                    .isInstanceOf(AssertionError.class)
                    .hasMessageContaining("student assertion x")
                    .hasMessageContaining("Arguments: (\"x\")")
                    .hasCauseInstanceOf(AssertionError.class);
        }

        @Test
        void criticalErrorsAreNotTurnedIntoTestFailures() {
            assertThatThrownBy(() -> ctor(critical, "public", int.class).invoke(3))
                    .isInstanceOf(StackOverflowError.class)
                    .hasMessage("too deep 3");
            assertThatThrownBy(() -> ctor(critical, "public", int.class).invokeExpectingException(IllegalStateException.class, 3))
                    .isInstanceOf(StackOverflowError.class);
        }

        @Test
        void otherExceptionTypeThanExpectedFails() {
            W<Object> calls = ClassWrapperTest.fixture("Calls", "public static");
            ConstructorWrapper<Object> c = ctor(calls, "public", String[].class);
            assertThatThrownBy(() -> c.invokeExpectingException(IllegalStateException.class, (Object) new String[0]))
                    .isInstanceOf(AssertionError.class)
                    .hasMessageContaining("should throw IllegalStateException")
                    .hasMessageContaining("IllegalArgumentException")
                    .hasMessageContaining("no names");
            assertThat(c.invokeExpectingException(RuntimeException.class, (Object) new String[0]))
                    .isInstanceOf(IllegalArgumentException.class); // subclasses are accepted
            assertThatThrownBy(() -> c.invokeExpectingException(IllegalArgumentException.class, (Object) new String[]{"a"}))
                    .isInstanceOf(AssertionError.class)
                    .hasMessageContaining("no exception was thrown\nArguments: ([\"a\"])"); // no generated subclass
        }

        @Test
        void finalClassIsCalledDirectly() {
            W<Object> finalCar = ClassWrapperTest.fixture("FinalCar", "public static final");
            ConstructorWrapper<Object> c = ctor(finalCar, "public", String.class);
            assertThatThrownBy(() -> c.invokeExpectingException(IllegalArgumentException.class, "BMW"))
                    .isInstanceOf(AssertionError.class)
                    .hasMessageContaining("should throw IllegalArgumentException, but: no exception was thrown");
        }

        @Test
        void expectedExceptionOfAbstractClassConstructorIsReturned() {
            W<Object> validating = classFixture("AbstractValidating", "public static abstract");
            ConstructorWrapper<Object> c = ctor(validating, "protected", int.class);
            IllegalArgumentException e = c.invokeExpectingException(IllegalArgumentException.class, -1);
            assertThat(e).hasMessage("negative: -1");
            assertThatThrownBy(() -> c.invokeExpectingException(IllegalArgumentException.class, 4))
                    .isInstanceOf(AssertionError.class)
                    .hasMessageContaining("no exception was thrown (")
                    .hasMessageContaining("Arguments: (4)");
            // invoke still reports the exception as a failure
            assertThatThrownBy(() -> c.invoke(-1))
                    .isInstanceOf(AssertionError.class)
                    .hasMessageContaining("negative: -1");
            assertThat(c.invoke(4)).isInstanceOf(ClassFixtures.AbstractValidating.class);
        }
    }

    @Nested
    class Reports {

        @Test
        void expectedAndActualSignatures() {
            ConstructorWrapper<Object> c = ctor(classFixture("Overloads", "public static"), "public", int.class);
            assertThat(c.expectedToString()).isEqualTo("public ClassFixtures$Overloads(int)");
            assertThat(c.actualToString()).isEqualTo("public ClassFixtures$Overloads(Integer)");
            assertThat(c.toString()).contains("public ClassFixtures$Overloads(int)").contains("public ClassFixtures$Overloads(Integer)");

            ConstructorWrapper<Object> twoParams = ctor(classFixture("Garage", "public static"), "public", int.class, String.class);
            assertThat(twoParams.toString()).isEqualTo("public ClassFixtures$Garage(int, String)");
        }

        @Test
        void noModifiersAndNoParameters() {
            ConstructorWrapper<Object> c = new ConstructorWrapper<>(classFixture("Gear", "public static"), "");
            assertThat(c.expectedToString()).isEqualTo("ClassFixtures$Gear()");
            assertThat(c.actualToString()).isEqualTo("public ClassFixtures$Gear()");
        }
    }
}
