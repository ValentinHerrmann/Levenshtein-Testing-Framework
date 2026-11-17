package io.github.valentinherrmann.levenshtein;

import static io.github.valentinherrmann.levenshtein.WrapperProperty.Existence.DEVIATES;
import static io.github.valentinherrmann.levenshtein.WrapperProperty.Existence.EXACT;
import static io.github.valentinherrmann.levenshtein.WrapperProperty.Existence.UNCHECKED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.valentinherrmann.levenshtein.fixtures.RobustnessFixtures;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Student code must neither earn a pass it did not deserve nor lose or distort a result: expected exceptions,
 * lookups that fail, state carried from test to test, and student code that runs while a failure is reported.
 */
class RobustnessTest {

    static final String PKG = "io.github.valentinherrmann.levenshtein.fixtures";

    static class W<T> extends ClassWrapper<T> {
        W(String simpleName, String... modifiers) {
            super("RobustnessFixtures$" + simpleName, PKG, modifiers);
        }

        @Override
        public Object getObj(boolean forceNew) {
            return getObj(forceNew, null);
        }
    }

    @BeforeEach
    void english() {
        LevenshteinSettings.setLanguage(LevenshteinSettings.Language.ENGLISH);
    }

    @AfterEach
    void resetSettings() {
        LevenshteinSettings.reset();
    }

    private static W<Object> counter() {
        return new W<>("Counter", "public static");
    }

    /** {@code invokeExpectingException}: what Ares or the virtual machine reports is no expected supertype. */
    @Nested
    class ExpectedExceptions {

        private final W<Object> critical = new W<>("Critical", "public static");
        private final Object obj = new RobustnessFixtures.Critical();

        private MethodWrapper<Object, Void> method(String name) {
            return new MethodWrapper<>(critical, name, void.class, "public");
        }

        @Test
        void securityViolationIsNotAnExpectedSupertype() {
            List<Class<? extends Throwable>> supertypes =
                    List.of(RuntimeException.class, Exception.class, Throwable.class);
            for (Class<? extends Throwable> expected : supertypes) {
                assertThatThrownBy(() -> method("forbidden").invokeExpectingException(expected, obj))
                        .as(expected.getSimpleName())
                        .isExactlyInstanceOf(SecurityException.class).hasMessage("forbidden by the sandbox");
            }
            assertThat(method("forbidden").invokeExpectingException(SecurityException.class, obj))
                    .hasMessage("forbidden by the sandbox");
        }

        @Test
        void assertionFailureIsNotAnExpectedSupertype() {
            List<Class<? extends Throwable>> supertypes = List.of(Error.class, Throwable.class);
            for (Class<? extends Throwable> expected : supertypes) {
                assertThatThrownBy(() -> method("failAssertion").invokeExpectingException(expected, obj))
                        .as(expected.getSimpleName())
                        .isExactlyInstanceOf(AssertionError.class).hasMessage("checked by Ares");
            }
            assertThat(method("failAssertion").invokeExpectingException(AssertionError.class, obj))
                    .hasMessage("checked by Ares");
        }

        @Test
        void errorOfTheVirtualMachineIsNotAnExpectedSupertype() {
            assertThatThrownBy(() -> method("overflow").invokeExpectingException(Throwable.class, obj))
                    .isExactlyInstanceOf(AssertionError.class)
                    .hasMessageContaining("Expected: Throwable is thrown")
                    .hasMessageContaining("Actual:   StackOverflowError");
            assertThatThrownBy(() -> method("outOfMemory").invokeExpectingException(Error.class, obj))
                    .isExactlyInstanceOf(AssertionError.class)
                    .hasMessageContaining("Expected: Error is thrown")
                    .hasMessageContaining("Actual:   OutOfMemoryError: too much");
            // asked for exactly that kind, it is the expected outcome
            assertThat(method("overflow").invokeExpectingException(StackOverflowError.class, obj))
                    .isInstanceOf(StackOverflowError.class);
            assertThat(method("outOfMemory").invokeExpectingException(VirtualMachineError.class, obj))
                    .isInstanceOf(OutOfMemoryError.class);
        }

        @Test
        void ordinaryExceptionsStillMatchTheirSupertypes() {
            assertThat(method("boom").invokeExpectingException(RuntimeException.class, obj)).hasMessage("boom");
            assertThat(method("boom").invokeExpectingException(Throwable.class, obj))
                    .isInstanceOf(IllegalStateException.class);
            assertThatThrownBy(() -> method("boom").invokeExpectingException(IllegalArgumentException.class, obj))
                    .isExactlyInstanceOf(AssertionError.class)
                    .hasMessageContaining("Actual:   IllegalStateException: boom");
        }

        @Test
        void constructorsFollowTheSameRule() {
            ConstructorWrapper<Object> withText =
                    new ConstructorWrapper<>(critical, new Class<?>[]{String.class}, "public");
            assertThatThrownBy(() -> withText.invokeExpectingException(RuntimeException.class, "x"))
                    .isExactlyInstanceOf(SecurityException.class).hasMessage("forbidden by the sandbox");
            assertThat(withText.invokeExpectingException(SecurityException.class, "x"))
                    .hasMessage("forbidden by the sandbox");
            ConstructorWrapper<Object> withDepth =
                    new ConstructorWrapper<>(critical, new Class<?>[]{int.class}, "public");
            assertThatThrownBy(() -> withDepth.invokeExpectingException(Throwable.class, 3))
                    .isExactlyInstanceOf(AssertionError.class)
                    .hasMessageContaining("Expected: Throwable is thrown")
                    .hasMessageContaining("Actual:   StackOverflowError");
            assertThat(withDepth.invokeExpectingException(StackOverflowError.class, 3))
                    .isInstanceOf(StackOverflowError.class);
        }

        @Test
        void theRuleItself() {
            assertThat(Invocations.isExpected(RuntimeException.class, new IllegalStateException())).isTrue();
            assertThat(Invocations.isExpected(IllegalArgumentException.class, new IllegalStateException())).isFalse();
            assertThat(Invocations.isExpected(RuntimeException.class, new SecurityException())).isFalse();
            assertThat(Invocations.isExpected(SecurityException.class, new SecurityException())).isTrue();
            assertThat(Invocations.isExpected(Error.class, new AssertionError())).isFalse();
            assertThat(Invocations.isExpected(AssertionError.class, new AssertionError())).isTrue();
            assertThat(Invocations.isExpected(Error.class, new OutOfMemoryError())).isFalse();
            assertThat(Invocations.isExpected(VirtualMachineError.class, new InternalError())).isTrue();
            assertThat(Invocations.isExpected(OutOfMemoryError.class, new OutOfMemoryError())).isTrue();
            assertThat(Invocations.isExpected(Error.class, new LinkageError())).isTrue();
        }
    }

    /** A lookup that fails must neither pass a structural test nor leave a wrapper half checked. */
    @Nested
    class FailedLookups {

        @Test
        void failedLookupIsRunAgainAndReportedEveryTime() {
            W<Object> counter = counter();
            AtomicReference<ClassWrapper<Object>> type = new AtomicReference<>();
            Supplier<ClassWrapper<Object>> notYet = type::get;
            MethodWrapper<Object, Object> self = new MethodWrapper<>(counter, "self", notYet, "public");

            assertThatThrownBy(self::getOverallExistence)
                    .isInstanceOf(IllegalStateException.class).hasMessageContaining("returned null");
            // a behaviour test reports the same cause, not a NullPointerException of a half checked wrapper
            assertThatThrownBy(() -> self.invoke())
                    .isInstanceOf(IllegalStateException.class).hasMessageContaining("returned null");
            for (int run = 1; run <= 2; run++) { // it never turns into a pass
                assertThatThrownBy(() -> StructuralLevenshtein.structuralTestTemplate(List.of(self)))
                        .as("run " + run)
                        .isInstanceOf(AssertionError.class)
                        .hasMessageContaining("could not be checked: IllegalStateException")
                        .hasMessageContaining("returned null");
            }

            type.set(counter); // the cause is gone: the next use runs the lookup again
            assertThat(self.getOverallExistence()).isEqualTo(EXACT);
            assertThat(self.invoke()).isInstanceOf(RobustnessFixtures.Counter.class);
            assertThatCode(() -> StructuralLevenshtein.structuralTestTemplate(List.of(self)))
                    .doesNotThrowAnyException();
        }

        @Test
        void wrapperThatStaysUncheckedFailsTheStructuralTest() {
            Wrapper<Object> unchecked = new Wrapper<>(counter(), "ghost") {
                @Override
                public void verifyExistence(boolean throwAssertion) {
                }

                @Override
                protected void findWithDeviation() {
                }

                @Override
                public String expectedToString() {
                    return "ghost";
                }

                @Override
                public String actualToString() {
                    return "ghost";
                }

                @Override
                protected void parseExistence() {
                }
            };
            assertThat(unchecked.getOverallExistence()).isEqualTo(UNCHECKED);
            assertThatThrownBy(() -> StructuralLevenshtein.structuralTestTemplate(List.of(unchecked)))
                    .isInstanceOf(AssertionError.class)
                    .hasMessageContaining("UNCHECKED in RobustnessFixtures$Counter");
        }

        @Test
        void errorOfALookupIsReportedAndASecurityViolationIsPassedOn() {
            // fails on the first call only: the report asks the supplier again for the expected type name
            AtomicInteger calls = new AtomicInteger();
            W<Object> counter = counter();
            Supplier<ClassWrapper<Object>> overflowing = () -> {
                if (calls.getAndIncrement() == 0) {
                    throw new StackOverflowError();
                }
                return counter;
            };
            MethodWrapper<Object, Object> first = new MethodWrapper<>(counter, "self", overflowing, "public");
            assertThatThrownBy(() -> StructuralLevenshtein.structuralTestTemplate(List.of(first)))
                    .isInstanceOf(AssertionError.class)
                    .hasMessageContaining("could not be checked: StackOverflowError");

            // again only on the first call, so the violation can only come from the lookup, not from the report
            AtomicInteger forbiddenCalls = new AtomicInteger();
            Supplier<ClassWrapper<Object>> forbidden = () -> {
                if (forbiddenCalls.getAndIncrement() == 0) {
                    throw new SecurityException("forbidden by the sandbox");
                }
                return counter;
            };
            MethodWrapper<Object, Object> second = new MethodWrapper<>(counter, "self", forbidden, "public");
            assertThatThrownBy(() -> StructuralLevenshtein.structuralTestTemplate(List.of(second)))
                    .isExactlyInstanceOf(SecurityException.class).hasMessage("forbidden by the sandbox");
        }

        @Test
        void staticInitializerThatOverflowsLeavesADeviationNotAPass() {
            AttributeWrapper<Object, Integer> attribute = new AttributeWrapper<>(
                    new W<>("OverflowingInit", "public static"), "counter", int.class, "private");
            assertThatCode(StaticStateReset::resetAll).doesNotThrowAnyException(); // as before any test
            assertThat(attribute.getOverallExistence()).isEqualTo(DEVIATES);
            assertThat(attribute.getUnexpectedModifiers()).containsExactly("static");
            assertThatThrownBy(() -> StructuralLevenshtein.structuralTestTemplate(List.of(attribute)))
                    .isInstanceOf(AssertionError.class)
                    .hasMessageContaining("unexpected modifier: static");
        }

        @Test
        void resetBeforeATestSkipsAWrapperWhoseLookupFails() {
            Supplier<ClassWrapper<Object>> none = () -> null;
            AttributeWrapper<Object, Object> broken = new AttributeWrapper<>(counter(), "count", none, "private");
            assertThatCode(StaticStateReset::resetAll).doesNotThrowAnyException();
            // the test that uses the wrapper reports it
            assertThatThrownBy(broken::getOverallExistence).isInstanceOf(IllegalStateException.class);
        }

        @Test
        void resetBeforeATestPassesASecurityViolationOn() {
            // switched off again at the end: the wrapper stays registered until it is collected
            AtomicBoolean violating = new AtomicBoolean(true);
            W<Object> counter = counter();
            Supplier<ClassWrapper<Object>> forbidden = () -> {
                if (violating.get()) {
                    throw new SecurityException("forbidden by the sandbox");
                }
                return counter;
            };
            AttributeWrapper<Object, Object> attribute = new AttributeWrapper<>(counter, "count", forbidden, "private");
            try {
                assertThatThrownBy(StaticStateReset::resetAll).isExactlyInstanceOf(SecurityException.class);
            }
            finally {
                violating.set(false);
            }
            assertThatCode(StaticStateReset::resetAll).doesNotThrowAnyException();
            assertThat(attribute.getExpectedName()).isEqualTo("count");
        }
    }

    /** The default object of a class wrapper lasts for one test. */
    @Nested
    class DefaultObjects {

        private final W<Object> counter = counter();
        private final MethodWrapper<Object, Integer> increment =
                new MethodWrapper<>(counter, "increment", int.class, "public");

        @Test
        void defaultObjectIsNewAfterTheResetBeforeATest() {
            assertThat(increment.invoke()).isEqualTo(1);
            assertThat(increment.invoke()).isEqualTo(2); // one object within a test
            StaticStateReset.resetAll(); // as before the next test
            assertThat(increment.invoke()).isEqualTo(1);
        }

        @Test
        void objectCreatedWithAConstructorWrapperIsForgottenToo() {
            ConstructorWrapper<Object> withStart =
                    new ConstructorWrapper<>(counter, new Class<?>[]{int.class}, "public");
            Object first = counter.getObj(false, withStart, 5);
            assertThat(counter.getObj(false, withStart, 5)).isSameAs(first);
            StaticStateReset.resetAll();
            assertThat(counter.getObj(false, withStart, 5)).isNotSameAs(first);
        }

        @Test
        void objectSetByTheTestIsKept() {
            Object own = counter.newObj();
            counter.setCachedObj(own);
            StaticStateReset.resetAll();
            assertThat(counter.getObj()).isSameAs(own);
        }

        @Test
        void defaultObjectCanBeKeptAcrossTests() {
            assertThat(LevenshteinSettings.isNewDefaultObjectPerTest()).isTrue();
            LevenshteinSettings.setNewDefaultObjectPerTest(false);
            assertThat(LevenshteinSettings.isNewDefaultObjectPerTest()).isFalse();
            Object first = counter.getObj();
            StaticStateReset.resetAll();
            assertThat(counter.getObj()).isSameAs(first);
            LevenshteinSettings.reset();
            assertThat(LevenshteinSettings.isNewDefaultObjectPerTest()).isTrue();
        }

        @Test
        void sharedOuterObjectOfAnInnerClassIsNewAfterTheReset() {
            W<Object> inner = new W<>("Outer$Inner", "public static");
            MethodWrapper<Object, Integer> use = new MethodWrapper<>(inner, "use", int.class, "public");
            assertThat(use.invoke()).isEqualTo(1);
            Object outer = inner.outerObj();
            assertThat(use.invokeOnSpecificObject(inner.newObj())).isEqualTo(2); // shared within a test
            StaticStateReset.resetAll();
            assertThat(inner.outerObj()).isNotSameAs(outer);
            assertThat(use.invoke()).isEqualTo(1);
        }
    }

    /** The extension as JUnit runs it: every test gets its own default object. */
    @Nested
    @ExtendWith(StaticStateReset.class)
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class DefaultObjectPerTest {
        static final W<Object> COUNTER = counter();
        static final MethodWrapper<Object, Integer> INCREMENT =
                new MethodWrapper<>(COUNTER, "increment", int.class, "public");

        @BeforeEach
        void setUpUsesTheObjectOfTheTest() {
            assertThat(INCREMENT.invoke()).isEqualTo(1);
        }

        @Test
        @Order(1)
        void firstTestChangesTheDefaultObject() {
            assertThat(INCREMENT.invoke()).isEqualTo(2);
        }

        @Test
        @Order(2)
        void secondTestStartsWithANewDefaultObject() {
            assertThat(INCREMENT.invoke()).isEqualTo(2);
        }
    }

    /** Student code that runs while a failure is reported must not replace the report. */
    @Nested
    class StudentCodeInReports {

        private final W<Object> thrower = new W<>("Thrower", "public static");

        private MethodWrapper<Object, Void> method(String name) {
            return new MethodWrapper<>(thrower, name, void.class, "public");
        }

        @Test
        void exceptionWhoseMessageFailsIsStillReported() {
            assertThatThrownBy(() -> method("broken").invoke())
                    .isExactlyInstanceOf(AssertionError.class)
                    .hasMessageContaining("Actual:   BrokenMessage: (getMessage() threw IllegalStateException)");
        }

        @Test
        void securityViolationInGetMessageIsPassedOn() {
            assertThatThrownBy(() -> method("forbiddenMessage").invoke())
                    .isExactlyInstanceOf(SecurityException.class).hasMessage("forbidden by the sandbox");
        }

        @Test
        void veryLongMessageIsCut() {
            assertThatThrownBy(() -> method("longMessage").invoke())
                    .isExactlyInstanceOf(AssertionError.class)
                    .hasMessageContaining("IllegalStateException: " + "x".repeat(Invocations.MAX_MESSAGE_LENGTH) + "…")
                    .hasMessageNotContaining("x".repeat(Invocations.MAX_MESSAGE_LENGTH + 1));
            String atTheLimit = "y".repeat(Invocations.MAX_MESSAGE_LENGTH);
            assertThat(Invocations.describe(new IllegalStateException(atTheLimit)))
                    .isEqualTo("IllegalStateException: " + atTheLimit);
        }

        @Test
        void resultWhoseToStringFailsIsStillReported() {
            MethodWrapper<Object, String> weird = new MethodWrapper<>(thrower, "weird", String.class, "public");
            assertThatThrownBy(() -> weird.invoke())
                    .isExactlyInstanceOf(AssertionError.class)
                    .hasMessage("The value BrokenToString (toString() threw IllegalStateException) of type "
                            + "BrokenToString cannot be converted to the expected type String.");
        }

        @Test
        void argumentWhoseEqualsFailsGetsANewObject() {
            W<Object> takes = new W<>("TakesObject", "public static");
            ConstructorWrapper<Object> ctor =
                    new ConstructorWrapper<>(takes, new Class<?>[]{Object.class}, "public");
            Object first = takes.getObj(false, ctor, new RobustnessFixtures.BrokenEquals());
            Object second = takes.getObj(false, ctor, new RobustnessFixtures.BrokenEquals());
            assertThat(second).isNotNull().isNotSameAs(first);
        }

        @Test
        void comparingArguments() {
            Object[] one = {1, "a", new int[]{2}};
            assertThat(Invocations.sameArguments(one, new Object[]{1, "a", new int[]{2}})).isTrue();
            assertThat(Invocations.sameArguments(one, new Object[]{1, "b", new int[]{2}})).isFalse();
            assertThat(Invocations.sameArguments(null, one)).isFalse();
            assertThat(Invocations.sameArguments(new Object[]{new RobustnessFixtures.BrokenEquals()},
                    new Object[]{new RobustnessFixtures.BrokenEquals()})).isFalse();
            assertThatThrownBy(() -> Invocations.sameArguments(new Object[]{new RobustnessFixtures.ForbiddenEquals()},
                    new Object[]{new RobustnessFixtures.ForbiddenEquals()}))
                    .isExactlyInstanceOf(SecurityException.class);
        }

        @Test
        void staticAttributeOfAClassThatCannotBeInitialisedFailsReadably() {
            AttributeWrapper<Object, Integer> read = new AttributeWrapper<>(
                    new W<>("BrokenRead", "public static"), "value", int.class, "public", "static");
            assertThatThrownBy(() -> read.getValue())
                    .isExactlyInstanceOf(AssertionError.class)
                    .hasMessage("Could not access value of attribute 'value' in RobustnessFixtures$BrokenRead. "
                            + "Cause: ExceptionInInitializerError: IllegalStateException: broken static");
            AttributeWrapper<Object, Integer> write = new AttributeWrapper<>(
                    new W<>("BrokenWrite", "public static"), "value", int.class, "public", "static");
            assertThatThrownBy(() -> write.setValue(5))
                    .isExactlyInstanceOf(AssertionError.class)
                    .hasMessage("Could not set value of attribute 'value' in RobustnessFixtures$BrokenWrite.\n"
                            + "ExceptionInInitializerError: IllegalStateException: broken static");
        }
    }
}
