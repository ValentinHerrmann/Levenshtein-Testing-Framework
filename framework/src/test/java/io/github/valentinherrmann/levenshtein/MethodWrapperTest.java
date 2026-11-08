package io.github.valentinherrmann.levenshtein;

import static io.github.valentinherrmann.levenshtein.WrapperProperty.Existence.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.valentinherrmann.levenshtein.fixtures.CallFixtures;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Lookup and invocation of methods by {@link MethodWrapper}.
 */
class MethodWrapperTest {

    static final String PKG = "io.github.valentinherrmann.levenshtein.fixtures";

    /** Minimal wrapper for a fixture class. */
    static class W<T> extends ClassWrapper<T> {
        W(String name, String... modifiers) {
            super(name, PKG, modifiers);
        }

        @Override
        public Object getObj(boolean forceNew) {
            return getObj(forceNew, null);
        }
    }

    private final W<Object> lookup = new W<>("CallFixtures$Lookup", "public", "static");
    private final W<Object> outcomes = new W<>("CallFixtures$Outcomes", "public", "static");

    @AfterEach
    void resetSettings() {
        LevenshteinSettings.reset();
    }

    private static String failure(Runnable call) {
        LevenshteinSettings.setLanguage(LevenshteinSettings.Language.ENGLISH);
        return org.junit.jupiter.api.Assertions.assertThrows(AssertionError.class, call::run).getMessage();
    }

    private MethodWrapper<Object, Integer> lookupMethod(String name, Class<?>... params) {
        return new MethodWrapper<>(lookup, name, int.class, params, "public");
    }

    @Test
    void signatureKeyListsTheBinaryParameterNames() {
        assertThat(MethodWrapper.signatureKey("m", new Class<?>[0])).isEqualTo("m()");
        assertThat(MethodWrapper.signatureKey("m", new Class<?>[]{int.class})).isEqualTo("m(int)");
        assertThat(MethodWrapper.signatureKey("m", new Class<?>[]{int.class, String.class, int[].class}))
                .isEqualTo("m(int,java.lang.String,[I)");
    }

    @Test
    void closestNameWins() {
        MethodWrapper<Object, Integer> w = lookupMethod("computeTotalz");
        assertThat(w.getActualName()).isEqualTo("computeTotal");
        assertThat(w.getOverallExistence()).isEqualTo(DEVIATES);
    }

    @Test
    void closerParameterTypesWinForEqualDistance() {
        // identical parameter types are closer than boxed ones
        assertThat(lookupMethod("calculate", int.class).getActualName()).isEqualTo("calculateb");
        // boxed parameter types are closer than other numeric types
        assertThat(lookupMethod("measure", int.class).getActualName()).isEqualTo("measureb");
    }

    @Test
    void closerNameWithOtherParametersDoesNotHideAMatch() {
        MethodWrapper<Object, Double> cost = new MethodWrapper<>(lookup, "calculateCost", double.class, "public");
        assertThat(cost.getActualName()).isEqualTo("calculateCots");
        assertThat(cost.getActualParamTypes()).isEmpty();
        assertThat(cost.getOverallExistence()).isEqualTo(DEVIATES);
    }

    @Test
    void namesBeyondTheThresholdAreNotMatched() {
        MethodWrapper<Object, Integer> w = lookupMethod("xyz");
        assertThat(w.getOverallExistence()).isEqualTo(MISSING);
        assertThat(w.getActualName()).isNull();
        assertThat(w.actualToString()).isEqualTo("<missing>");
    }

    @Test
    void incompatibleParametersAreNotMatched() {
        W<Object> typos = new W<>("Fixtures$Typos", "public", "static"); // has int add(Integer, Integer)
        assertThat(new MethodWrapper<>(typos, "add", int.class, new Class<?>[]{String.class, String.class}, "public")
                .getOverallExistence()).isEqualTo(MISSING);
        assertThat(new MethodWrapper<>(typos, "add", int.class, new Class<?>[]{Integer.class}, "public")
                .getOverallExistence()).isEqualTo(MISSING);
        assertThat(new MethodWrapper<>(typos, "add", int.class,
                new Class<?>[]{Integer.class, Integer.class, Integer.class}, "public").getOverallExistence()).isEqualTo(MISSING);
    }

    @Test
    void exactNameWithDeviatingParametersKeepsTheNameExact() {
        W<Object> typos = new W<>("Fixtures$Typos", "public", "static");
        MethodWrapper<Object, Integer> add =
                new MethodWrapper<>(typos, "add", int.class, new Class<?>[]{int.class, int.class}, "public");
        assertThat(add.getOverallExistence()).isEqualTo(DEVIATES);
        assertThat(add.name.existence).isEqualTo(EXACT);
        assertThat(add.getActualParamTypes()).containsExactly(Integer.class, Integer.class);

        MethodWrapper<Object, Integer> total = new MethodWrapper<>(typos, "totl", int.class, "public");
        assertThat(total.getActualName()).isEqualTo("total");
        assertThat(total.name.existence).isEqualTo(DEVIATES);
    }

    @Test
    void bridgeMethodsAreNoCandidates() {
        W<Object> ranked = new W<>("CallFixtures$Ranked", "public", "static");
        // only the synthetic bridge compareTo(Object) has these parameters
        MethodWrapper<Object, Integer> w =
                new MethodWrapper<>(ranked, "compareTO", int.class, new Class<?>[]{Object.class}, "public");
        assertThat(w.getOverallExistence()).isEqualTo(MISSING);
    }

    @Test
    void unexpectedStaticDeviates() {
        MethodWrapper<Object, Integer> answer = new MethodWrapper<>(outcomes, "answer", int.class, "public");
        assertThat(answer.getOverallExistence()).isEqualTo(DEVIATES);
        assertThat(new MethodWrapper<>(outcomes, "answer", int.class, "public", "static").getOverallExistence())
                .isEqualTo(EXACT);
    }

    @Test
    void methodOfMissingClassIsMissing() {
        W<Object> ghost = new W<>("CallFixtures$Ghost", "public", "static");
        MethodWrapper<Object, Integer> w = new MethodWrapper<>(ghost, "value", int.class, "public");
        assertThat(w.getOverallExistence()).isEqualTo(MISSING);
        assertThat(w.actualMethod()).isNull();
        assertThat(w.actualReturnType()).isNull();
        assertThat(w.getActualParamTypes()).isEmpty();
    }

    @Test
    void actualAccessorsRunTheLookup() {
        assertThat(lookupMethod("computeTotalz").actualMethod().getName()).isEqualTo("computeTotal");
        assertThat(new MethodWrapper<>(outcomes, "big", int.class, "public").actualReturnType()).isEqualTo(long.class);
        assertThat(lookupMethod("measure", int.class).getActualParamTypes()).containsExactly(Integer.class);
        // missing: the expected parameter types
        assertThat(lookupMethod("xyz", int.class).getActualParamTypes()).containsExactly(int.class);
    }

    @Test
    void expectedToStringWithoutReturnType() {
        MethodWrapper<Object, Object> w = new MethodWrapper<>(lookup, "computeTotal", null, new Class<?>[]{int.class}, "public");
        assertThat(w.expectedToString()).isEqualTo("public <missing> computeTotal(int)");
        assertThat(lookupMethod("computeTotal").expectedToString()).isEqualTo("public int computeTotal()");
    }

    @Test
    void staticMethodNeedsNoObject() { // the constructor of Outcomes throws
        MethodWrapper<Object, Integer> answer = new MethodWrapper<>(outcomes, "answer", int.class, "public", "static");
        assertThat(answer.invoke()).isEqualTo(42);
    }

    @Test
    void resultIsConvertedToTheExpectedType() {
        MethodWrapper<Object, Integer> big = new MethodWrapper<>(outcomes, "big", int.class, "public"); // student: long
        Object obj = outcomes.newObjWithoutConstructor();
        Object result = big.invokeOnSpecificObject(obj);
        assertThat(result).isInstanceOf(Integer.class).isEqualTo(5);
        assertThat((Object) big.invokeOnSpecificObjectWithContext(() -> "unused", obj)).isInstanceOf(Integer.class);
    }

    @Test
    void contextVariantsReturnTheResult() {
        Object obj = outcomes.newObjWithoutConstructor();
        MethodWrapper<Object, Integer> pair =
                new MethodWrapper<>(outcomes, "pair", int.class, new Class<?>[]{int.class, int.class}, "public");
        assertThat(pair.invokeOnSpecificObjectWithContext(() -> "unused", obj, 2, 3)).isEqualTo(5);
        assertThat(pair.invokeOnSpecificObject(obj, 2, 3)).isEqualTo(5);
    }

    @Test
    void criticalThrowablesOfStudentCodeArePassedOn() {
        Object obj = outcomes.newObjWithoutConstructor();
        MethodWrapper<Object, Void> forbidden = new MethodWrapper<>(outcomes, "forbidden", void.class, "public");
        MethodWrapper<Object, Void> outOfMemory = new MethodWrapper<>(outcomes, "outOfMemory", void.class, "public");
        MethodWrapper<Object, Void> failAssertion = new MethodWrapper<>(outcomes, "failAssertion", void.class, "public");

        assertThatThrownBy(() -> forbidden.invokeOnSpecificObject(obj))
                .isExactlyInstanceOf(SecurityException.class).hasMessage("forbidden by the sandbox");
        assertThatThrownBy(() -> outOfMemory.invokeOnSpecificObject(obj))
                .isExactlyInstanceOf(OutOfMemoryError.class).hasMessage("too much");
        assertThatThrownBy(() -> failAssertion.invokeOnSpecificObject(obj))
                .isExactlyInstanceOf(AssertionError.class).hasMessage("checked by Ares");

        // also when another exception is expected
        assertThatThrownBy(() -> forbidden.invokeExpectingException(IllegalStateException.class, obj))
                .isExactlyInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> outOfMemory.invokeExpectingException(IllegalStateException.class, obj))
                .isExactlyInstanceOf(OutOfMemoryError.class);
        // unless it is the expected one
        assertThat(forbidden.invokeExpectingException(SecurityException.class, obj)).hasMessage("forbidden by the sandbox");
    }

    @Test
    void otherExceptionThanExpectedFails() {
        Object obj = outcomes.newObjWithoutConstructor();
        MethodWrapper<Object, Void> boom = new MethodWrapper<>(outcomes, "boom", void.class, "public");
        assertThat(failure(() -> boom.invokeExpectingException(ArithmeticException.class, obj)))
                .contains("ArithmeticException")
                .contains("IllegalStateException: boom");
        assertThat(boom.invokeExpectingException(RuntimeException.class, obj)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void nullAsArgumentArray() {
        Object obj = outcomes.newObjWithoutConstructor();
        MethodWrapper<Object, Integer> none = new MethodWrapper<>(outcomes, "none", int.class, "public");
        MethodWrapper<Object, Integer> count =
                new MethodWrapper<>(outcomes, "countOrMinusOne", int.class, new Class<?>[]{int[].class}, "public");
        MethodWrapper<Object, Integer> pair =
                new MethodWrapper<>(outcomes, "pair", int.class, new Class<?>[]{int.class, int.class}, "public");
        assertThat(none.invokeOnSpecificObject(obj, (Object[]) null)).isEqualTo(7);
        assertThat(count.invokeOnSpecificObject(obj, (Object[]) null)).isEqualTo(-1);
        assertThat(count.invokeOnSpecificObject(obj, (Object) null)).isEqualTo(-1);
        assertThatThrownBy(() -> pair.invokeOnSpecificObject(obj, (Object[]) null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("expects 2 argument(s), but 0 were passed");
    }

    @Test
    void characterArgumentIsConvertedToANumber() {
        W<Object> typos = new W<>("Fixtures$Typos", "public", "static"); // long scale(int factor)
        MethodWrapper<Object, Long> scale = new MethodWrapper<>(typos, "scale", long.class, new Class<?>[]{int.class}, "public");
        assertThat(scale.invokeOnSpecificObject(typos.newObjWithoutConstructor(), 'A')).isEqualTo(65L);
    }

    @Test
    void argumentsAreShownAsPassedAlsoForCollections() {
        Object obj = outcomes.newObjWithoutConstructor();
        MethodWrapper<Object, Void> clear =
                new MethodWrapper<>(outcomes, "clearAndFail", void.class, new Class<?>[]{java.util.Collection.class}, "public");
        List<Integer> values = new ArrayList<>(List.of(1, 2, 3));
        assertThat(failure(() -> clear.invokeOnSpecificObject(obj, values)))
                .contains("IllegalStateException: cleared")
                .endsWith("Arguments: ([1, 2, 3])");
        assertThat(values).isEmpty();

        CallFixtures.StudentCollection own = new CallFixtures.StudentCollection(new ArrayList<>(List.of(4, 5)));
        assertThat(failure(() -> clear.invokeOnSpecificObject(obj, own))).endsWith("Arguments: ([])");
    }
}
