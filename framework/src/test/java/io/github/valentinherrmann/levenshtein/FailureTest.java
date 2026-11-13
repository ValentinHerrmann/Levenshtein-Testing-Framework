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
    void outOfMemoryAddsAHint() {
        assertThat(Failure.of("f()").thrown(new OutOfMemoryError("Java heap space")).message()).isEqualTo("""
                Method:   f()
                Actual:   OutOfMemoryError: Java heap space
                Hint: an OutOfMemoryError usually means that a loop keeps making a String or a list bigger and never ends.""");
        // also as the cause of another exception
        assertThat(Failure.of("f()").thrown(new IllegalStateException("wrapped", new OutOfMemoryError())).message())
                .endsWith("a loop keeps making a String or a list bigger and never ends.");
        LevenshteinSettings.setLanguage(LevenshteinSettings.Language.DEUTSCH);
        assertThat(Failure.of("f()").thrown(new OutOfMemoryError()).message())
                .contains("Schleife hin, die einen String oder eine Liste immer weiter vergrößert");
    }

    @Test
    void hintIsFoundInTheFirstFiveExceptionsOfTheCauseChain() {
        assertThat(Failure.of("f()").thrown(wrapped(new OutOfMemoryError(), 4)).message()).contains("Hint:");
        assertThat(Failure.of("f()").thrown(wrapped(new OutOfMemoryError(), 5)).message()).doesNotContain("Hint:");
    }

    private static Throwable wrapped(Throwable t, int times) {
        for (int i = 0; i < times; i++) {
            t = new IllegalStateException("wrapper " + i, t);
        }
        return t;
    }

    @Test
    void otherErrorsOfTheVirtualMachineHaveNoHint() {
        assertThat(Failure.of("f()").thrown(new InternalError("broken")).message()).isEqualTo("""
                Method:   f()
                Actual:   InternalError: broken""");
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
    void callPassesFailuresAndSecurityViolationsOn() {
        AssertionError assertion = new AssertionError("from Ares");
        assertThat(catchThrowable(() -> Failure.call(() -> { throw assertion; }))).isSameAs(assertion);
        SecurityException security = new SecurityException("blocked");
        assertThat(catchThrowable(() -> Failure.call(() -> { throw security; }))).isSameAs(security);
    }

    @Test
    void callReportsAnOutOfMemoryErrorWithAHint() {
        Throwable memory = catchThrowable(() -> Failure.call(() -> { throw new OutOfMemoryError("full"); }));
        assertThat(memory).isInstanceOf(AssertionError.class).hasNoCause().hasMessage("""
                Actual:   OutOfMemoryError: full
                Hint: an OutOfMemoryError usually means that a loop keeps making a String or a list bigger and never ends.""");
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
    void resourceErrorsAreDetectedWithoutReadingTheMessage() {
        AssertionError outOfMemory = Failure.of("f()").thrown(new OutOfMemoryError("Java heap space")).error();
        AssertionError stackOverflow = Failure.of("f()").thrown(new StackOverflowError()).error();
        AssertionError asCause = Failure.of("f()").thrown(new RuntimeException(new OutOfMemoryError())).error();
        assertThat(List.of(outOfMemory, stackOverflow, asCause)).allSatisfy(e -> {
            assertThat(e).isExactlyInstanceOf(AssertionError.class).hasNoCause();
            assertThat(Failure.isResourceError(e)).isTrue();
        });
        // a block from Failure.call, too
        assertThat(Failure.isResourceError(catchThrowable(() -> Failure.call(FailureTest::recurse)))).isTrue();

        assertThat(Failure.isResourceError(Failure.of("f()").thrown(new IllegalStateException("x")).error())).isFalse();
        assertThat(Failure.isResourceError(Failure.of("f()").expected(1).actual(2).error())).isFalse();
        assertThat(Failure.isResourceError(Failure.of("f()").thrown(new InternalError("vm")).error())).isFalse();
        // the same text is not enough
        assertThat(Failure.isResourceError(new AssertionError(outOfMemory.getMessage()))).isFalse();
        assertThat(Failure.isResourceError(new AssertionError("x", new OutOfMemoryError()))).isTrue();
        assertThat(Failure.isResourceError(null)).isFalse();
        // the errors of the virtual machine themselves
        assertThat(Failure.isResourceError(new OutOfMemoryError())).isTrue();
        assertThat(Failure.isResourceError(new StackOverflowError())).isTrue();
        assertThat(Failure.isResourceError(new IllegalStateException())).isFalse();
    }

    @Test
    void laterChangesOfTheBuilderDoNotChangeAnError() {
        Failure failure = Failure.of("f()").thrown(new OutOfMemoryError());
        AssertionError first = failure.error();
        failure.thrown(new IllegalStateException());
        AssertionError second = failure.error();
        assertThat(Failure.isResourceError(first)).isTrue();
        assertThat(Failure.isResourceError(second)).isFalse();
    }

    @Test
    void forEachCasePassesIfAllCasesPass() {
        List<Integer> seen = new java.util.ArrayList<>();
        Failure.forEachCase(List.of(1, 2, 3), seen::add);
        assertThat(seen).containsExactly(1, 2, 3);
        Failure.forEachCase(List.of(), n -> {
            throw new AssertionError("never");
        });
    }

    @Test
    void forEachCaseReportsTheFirstFailureWithTheNumberOfFailedCases() {
        List<Integer> seen = new java.util.ArrayList<>();
        Throwable thrown = catchThrowable(() -> Failure.forEachCase(List.of(1, 2, 3, 4, 5), n -> {
            seen.add(n);
            if (n % 2 == 0) {
                throw Failure.of("square(int)").arguments(n).expected(n * n).actual(n).error();
            }
        }));
        assertThat(seen).containsExactly(1, 2, 3, 4, 5);
        assertThat(thrown).isExactlyInstanceOf(AssertionError.class).hasNoCause().hasMessage("""
                Method:   square(int)
                Input:    2
                Expected: 4
                Actual:   2
                (2 of 5 cases failed)""");
        assertThat(Failure.isResourceError(thrown)).isFalse();
    }

    @Test
    void forEachCasePrefersTheFirstResourceError() {
        Throwable thrown = catchThrowable(() -> Failure.forEachCase(List.of(1, 2, 3, 4), n -> {
            if (n == 1) {
                throw Failure.of("grow(int)").arguments(n).expected(1).actual(2).error();
            }
            if (n == 3) {
                throw Failure.of("grow(int)").arguments(n).thrown(new OutOfMemoryError("Java heap space")).error();
            }
            if (n == 4) {
                throw Failure.of("grow(int)").arguments(n).thrown(new StackOverflowError()).error();
            }
        }));
        assertThat(thrown).isExactlyInstanceOf(AssertionError.class).hasNoCause().hasMessage("""
                Method:   grow(int)
                Input:    3
                Actual:   OutOfMemoryError: Java heap space
                (3 of 4 cases failed)
                Hint: an OutOfMemoryError usually means that a loop keeps making a String or a list bigger and never ends.""");
        assertThat(Failure.isResourceError(thrown)).isTrue();
    }

    @Test
    void forEachCaseAddsTheNumberOfFailedCasesToOtherFailures() {
        Throwable thrown = catchThrowable(() -> Failure.forEachCase(List.of("a", "b", "c"), s -> {
            if (!s.equals("b")) {
                assertThat(s).isEqualTo("b");
            }
        }));
        assertThat(thrown).isExactlyInstanceOf(AssertionError.class).hasNoCause();
        assertThat(thrown.getMessage()).contains("\"a\"").endsWith("\n(2 of 3 cases failed)");

        // a single case is passed on unchanged
        AssertionError only = new AssertionError("only");
        assertThat(catchThrowable(() -> Failure.forEachCase(List.of(1), n -> {
            throw only;
        }))).isSameAs(only);
    }

    @Test
    void forEachCasePassesOnOtherExceptionsAtOnce() {
        List<Integer> seen = new java.util.ArrayList<>();
        IllegalStateException bug = new IllegalStateException("bug in the test");
        assertThat(catchThrowable(() -> Failure.forEachCase(List.of(1, 2, 3), n -> {
            seen.add(n);
            if (n == 2) {
                throw bug;
            }
        }))).isSameAs(bug);
        assertThat(seen).containsExactly(1, 2);
        assertThatThrownBy(() -> Failure.forEachCase(null, n -> { })).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> Failure.forEachCase(List.of(1), null)).isInstanceOf(NullPointerException.class);
    }

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
