package io.github.valentinherrmann.levenshtein;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.util.Arrays;
import java.util.List;

import io.github.valentinherrmann.levenshtein.ClassWrapperTest.W;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests of {@link Failure}, the one block of every behaviour failure, and of its use by the framework.
 */
class FailureTest {

    @BeforeEach
    void english() {
        LevenshteinSettings.setLanguage(LevenshteinSettings.Language.ENGLISH);
    }

    @AfterEach
    void resetSettings() {
        LevenshteinSettings.reset();
    }

    @Test
    void allLinesInOrderAndAligned() {
        String message = Failure.of("calculateLoudness(double, double)")
                .scenario("generated case 3, seed 2040003")
                .input("volume = 12.50, crowdNoise = 3.00")
                .expected(31.25).actual(27.0)
                .cases(2, 10)
                .message();
        assertThat(message).isEqualTo("""
                Method:   calculateLoudness(double, double)
                Scenario: generated case 3, seed 2040003
                Input:    volume = 12.50, crowdNoise = 3.00
                Expected: 31.25
                Actual:   27.00
                (2 of 10 cases failed)""");
    }

    @Test
    void germanLabelsAreAlignedToTheLongestLabel() {
        LevenshteinSettings.setLanguage(LevenshteinSettings.Language.DEUTSCH);
        String message = Failure.of("f(int)").scenario("Fall 1").arguments(3).expected(1).actual(2).cases(1, 4).message();
        assertThat(message).isEqualTo("""
                Methode:     f(int)
                Szenario:    Fall 1
                Eingabe:     3
                Erwartet:    1
                Tatsächlich: 2
                (1 von 4 Fällen fehlgeschlagen)""");
    }

    @Test
    void linesThatAreNotSetAreLeftOut() {
        assertThat(Failure.of((String) null).actual("x").message()).isEqualTo("Actual:   \"x\"");
        assertThat(Failure.of("f()").scenario("  ").input(null).arguments().expectedText("ok").message())
                .isEqualTo("Method:   f()\nExpected: ok");
    }

    @Test
    void casesLineOnlyForMoreThanOneCase() {
        assertThat(Failure.of("f()").actual(1).cases(1, 1).message()).doesNotContain("cases");
        assertThat(Failure.of("f()").actual(1).cases(3, 7).message()).endsWith("\n(3 of 7 cases failed)");
    }

    @Test
    void multiLineValuesAreIndented() {
        assertThat(Failure.of("f()").actualText("line 1\nline 2").message())
                .isEqualTo("Method:   f()\nActual:   line 1\n          line 2");
    }

    @Test
    void valuesAreFormattedTheSameWayEverywhere() {
        assertThat(Failure.format(31.25)).isEqualTo("31.25");
        assertThat(Failure.format(2.0)).isEqualTo("2.00");
        assertThat(Failure.format(1.005f)).isEqualTo("1.00");
        assertThat(Failure.format(-0.125)).isEqualTo("-0.13");
        assertThat(Failure.format(Double.NaN)).isEqualTo("NaN");
        assertThat(Failure.format(Double.POSITIVE_INFINITY)).isEqualTo("Infinity");
        assertThat(Failure.format(1e300)).isEqualTo("1.0E300");
        assertThat(Failure.format(1e-5)).isEqualTo("1.0E-5"); // not shown as 0.00
        assertThat(Failure.format(0.0)).isEqualTo("0.00");
        assertThat(Failure.format(42)).isEqualTo("42");
        assertThat(Failure.format("text")).isEqualTo("\"text\"");
        assertThat(Failure.format('c')).isEqualTo("'c'");
        assertThat(Failure.format(null)).isEqualTo("null");
        assertThat(Failure.format(true)).isEqualTo("true");
        assertThat(Failure.format(new int[]{1, 2})).isEqualTo("[1, 2]");
        assertThat(Failure.format(new double[]{1.5})).isEqualTo("[1.50]");
        assertThat(Failure.format(List.of("a", 2.5))).isEqualTo("[\"a\", 2.50]");
        assertThat(Failure.format(new Object[]{new int[]{1}, Arrays.asList(null, 'x')})).isEqualTo("[[1], [null, 'x']]");
    }

    @Test
    void valuesThatOnlyLookEqualAfterRoundingAreShownExactly() {
        assertThat(Failure.of("f()").expected(2.5).actual(2.4999).message())
                .isEqualTo("Method:   f()\nExpected: 2.5\nActual:   2.4999");
        assertThat(Failure.of("f()").expected(new double[]{0.1}).actual(new double[]{0.1000001}).message())
                .endsWith("Expected: [0.1]\nActual:   [0.1000001]");
        // equal values or different texts keep the rounded form
        assertThat(Failure.of("f()").expected(2.5).actual(2.6).message()).endsWith("Expected: 2.50\nActual:   2.60");
        assertThat(Failure.of("f()").expected(2.5).actualText("2.50").message()).endsWith("Actual:   2.50");
    }

    @Test
    void decimalsCanBeConfigured() {
        LevenshteinSettings.setDecimals(4);
        assertThat(Failure.format(31.25)).isEqualTo("31.2500");
        LevenshteinSettings.setDecimals(0);
        assertThat(Failure.format(31.25)).isEqualTo("31");
        assertThatThrownBy(() -> LevenshteinSettings.setDecimals(-1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> LevenshteinSettings.setDecimals(16)).isInstanceOf(IllegalArgumentException.class);
        LevenshteinSettings.reset();
        assertThat(LevenshteinSettings.getDecimals()).isEqualTo(LevenshteinSettings.DEFAULT_DECIMALS);
    }

    @Test
    void errorHasNoCause() {
        AssertionError error = Failure.of("f()").thrown(new IllegalStateException("broken", new RuntimeException("inner")))
                .error();
        assertThat(error).hasNoCause().hasMessage("Method:   f()\nActual:   IllegalStateException: broken");
    }

    @Test
    void stackOverflowAddsAHint() {
        assertThat(Failure.of("f()").thrown(new StackOverflowError()).message()).isEqualTo("""
                Method:   f()
                Actual:   StackOverflowError
                Hint: a StackOverflowError usually means endless recursion (a method keeps calling itself).""");
        LevenshteinSettings.setLanguage(LevenshteinSettings.Language.DEUTSCH);
        assertThat(Failure.of("f()").thrown(new StackOverflowError()).message()).contains("endlose Rekursion");
    }

    @Test
    void callReturnsTheResultOrReportsTheException() {
        assertThat(Failure.call(() -> 42)).isEqualTo(42);
        Failure.call(() -> { });
        Throwable thrown = catchThrowable(() -> Failure.call(() -> { throw new IllegalArgumentException("bad"); }));
        assertThat(thrown).isInstanceOf(AssertionError.class).hasNoCause()
                .hasMessage("Actual:   IllegalArgumentException: bad");
        Throwable checked = catchThrowable(() -> Failure.call(() -> { throw new java.io.IOException("io"); }));
        assertThat(checked).isInstanceOf(AssertionError.class).hasMessage("Actual:   IOException: io");
        Throwable overflow = catchThrowable(() -> Failure.call(FailureTest::recurse));
        assertThat(overflow).isInstanceOf(AssertionError.class).hasNoCause()
                .hasMessageStartingWith("Actual:   StackOverflowError")
                .hasMessageContaining("Hint:");
    }

    @Test
    void callPassesFailuresAndCriticalErrorsOn() {
        AssertionError assertion = new AssertionError("from Ares");
        assertThat(catchThrowable(() -> Failure.call(() -> { throw assertion; }))).isSameAs(assertion);
        SecurityException security = new SecurityException("blocked");
        assertThat(catchThrowable(() -> Failure.call(() -> { throw security; }))).isSameAs(security);
        OutOfMemoryError memory = new OutOfMemoryError("full");
        assertThat(catchThrowable(() -> Failure.call(() -> { throw memory; }))).isSameAs(memory);
    }

    @Test
    void ofWrapperShowsNameAndParameterTypes() {
        W<Object> calls = ClassWrapperTest.fixture("Calls", "public static");
        MethodWrapper<Object, Double> divide =
                new MethodWrapper<>(calls, "divide", double.class, new Class<?>[]{double.class, double.class}, "public");
        ConstructorWrapper<Object> ctor = new ConstructorWrapper<>(calls, new Class<?>[]{String[].class}, "public");
        assertThat(Failure.of(divide).message()).isEqualTo("Method:   divide(double, double)");
        assertThat(Failure.of(ctor).message()).isEqualTo("Method:   Fixtures$Calls(String[])");
        // the student's actual name and types, not the expected ones
        W<Object> typos = ClassWrapperTest.fixture("Typos", "public static");
        MethodWrapper<Object, Integer> deviating =
                new MethodWrapper<>(typos, "devide", int.class, new Class<?>[]{int.class, int.class}, "public");
        assertThat(Failure.of(deviating).message()).isEqualTo("Method:   divide(int, int)");
        MethodWrapper<Object, Integer> missing =
                new MethodWrapper<>(typos, "somethingElseEntirely", int.class, new Class<?>[]{int.class}, "public");
        assertThat(Failure.of(missing).message()).isEqualTo("Method:   somethingElseEntirely(int)");
        assertThat(Failure.of((MethodWrapper<?, ?>) null).actual(1).message()).isEqualTo("Actual:   1");
    }

    /** No framework failure of a student exception contains a stack trace or a cause, in both languages. */
    @Test
    void frameworkFailuresHaveNoStackTraceInBothLanguages() {
        for (LevenshteinSettings.Language language : LevenshteinSettings.Language.values()) {
            LevenshteinSettings.setLanguage(language);
            W<Object> calls = ClassWrapperTest.fixture("Calls", "public static");
            MethodWrapper<Object, Double> divide =
                    new MethodWrapper<>(calls, "divide", double.class, new Class<?>[]{double.class, double.class}, "public");
            ConstructorWrapper<Object> ctor = new ConstructorWrapper<>(calls, new Class<?>[]{String[].class}, "public");
            W<Object> critical = ClassWrapperTest.classFixture("CriticalCtors", "public static");
            ConstructorWrapper<Object> recursive = new ConstructorWrapper<>(critical, new Class<?>[]{int.class}, "public");
            W<Object> throwingDefault = ClassWrapperTest.classFixture("ThrowingDefaultCtor", "public static");

            List<Throwable> failures = List.of(
                    catchThrowable(() -> divide.invoke(1.0, 0.0)),
                    catchThrowable(() -> divide.invokeExpectingException(IllegalStateException.class, null, 1.0, 0.0)),
                    catchThrowable(() -> divide.invokeExpectingException(ArithmeticException.class, null, 4.0, 2.0)),
                    catchThrowable(() -> ctor.invoke((Object) new String[0])),
                    catchThrowable(() -> ctor.invokeExpectingException(IllegalStateException.class, (Object) new String[0])),
                    catchThrowable(() -> recursive.invoke(3)),
                    catchThrowable(throwingDefault::newObj));
            String method = language == LevenshteinSettings.Language.ENGLISH ? "Method:" : "Methode:";
            String actual = language == LevenshteinSettings.Language.ENGLISH ? "Actual:" : "Tatsächlich:";
            for (Throwable failure : failures) {
                assertThat(failure).isInstanceOf(AssertionError.class).hasNoCause();
                assertThat(failure.getMessage())
                        .startsWith(method)
                        .contains("\n" + actual)
                        .doesNotContain("\tat ")
                        .doesNotContain("Caused by")
                        .doesNotContain("Exception in");
            }
            assertThat(failures.get(5).getMessage()).contains(language == LevenshteinSettings.Language.ENGLISH
                    ? "endless recursion" : "endlose Rekursion");
        }
    }

    private static Object recurse() {
        return recurse();
    }
}
