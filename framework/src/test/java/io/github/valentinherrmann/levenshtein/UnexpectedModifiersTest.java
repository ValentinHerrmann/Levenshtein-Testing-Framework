package io.github.valentinherrmann.levenshtein;

import static io.github.valentinherrmann.levenshtein.WrapperProperty.Existence.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.valentinherrmann.levenshtein.LevenshteinSettings.UnexpectedModifiers;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Unexpected, missing, optional and implicit modifiers (#35).
 */
class UnexpectedModifiersTest {

    static final String PKG = "io.github.valentinherrmann.levenshtein.fixtures";

    static class W<T> extends ClassWrapper<T> {
        W(String simpleName, String... modifiers) {
            super("ModifierFixtures$" + simpleName, PKG, modifiers);
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

    private static <V> AttributeWrapper<Object, V> attribute(String cls, String clsMods, String name, Class<V> type,
                                                            String... mods) {
        return new AttributeWrapper<>(new W<>(cls, clsMods), name, type, mods);
    }

    private static <R> MethodWrapper<Object, R> method(String cls, String clsMods, String name, Class<R> type,
                                                      Class<?>[] params, String... mods) {
        return new MethodWrapper<>(new W<>(cls, clsMods), name, type, params, mods);
    }

    @Nested
    class Unexpected {

        @Test
        void sharedSpotlightsAreReported() { // SE01E03
            AttributeWrapper<Object, boolean[]> spotlights =
                    attribute("FestivalStage", "public static", "spotlights", boolean[].class, "private");
            assertThat(spotlights.getOverallExistence()).isEqualTo(DEVIATES);
            assertThat(spotlights.getUnexpectedModifiers()).containsExactly("static");
            assertThat(spotlights.toString()).contains("unexpected modifier: static");
        }

        @Test
        void everyExtraModifierDeviates() {
            assertThat(attribute("Extras", "public static", "finalField", int.class, "public")
                    .getUnexpectedModifiers()).containsExactly("final");
            assertThat(attribute("Extras", "public static", "transientField", int.class, "public")
                    .getUnexpectedModifiers()).containsExactly("transient");
            assertThat(attribute("Extras", "public static", "volatileField", int.class, "public")
                    .getUnexpectedModifiers()).containsExactly("volatile");
            assertThat(method("Extras", "public static", "sync", void.class, new Class<?>[0], "public")
                    .getUnexpectedModifiers()).containsExactly("synchronized");
            assertThat(method("Extras", "public static", "staticMethod", int.class, new Class<?>[0], "public")
                    .getUnexpectedModifiers()).containsExactly("static");
            assertThat(method("Extras", "public static", "finalMethod", void.class, new Class<?>[0], "public")
                    .getOverallExistence()).isEqualTo(DEVIATES);
            W<Object> abstractClass = new W<>("AbstractButShouldNotBe", "public static");
            assertThat(abstractClass.getOverallExistence()).isEqualTo(DEVIATES);
            assertThat(abstractClass.getUnexpectedModifiers()).containsExactly("abstract");
            W<Object> finalClass = new W<>("SetlistTools", "public static");
            assertThat(finalClass.getUnexpectedModifiers()).containsExactly("final");
        }

        @Test
        void interfaceWhereClassExpectedDeviates() {
            W<Object> api = new W<>("Api", "public static");
            assertThat(api.getUnexpectedModifiers()).containsExactly("interface");
        }

        @Test
        void extraAbstractClassIsStillUsable() {
            W<Object> abstractClass = new W<>("AbstractButShouldNotBe", "public static");
            MethodWrapper<Object, Integer> value =
                    new MethodWrapper<>(abstractClass, "value", int.class, new Class<?>[0], "public");
            assertThat(value.invoke()).isEqualTo(7); // on a generated subclass
        }

        @Test
        void extraFinalAttributeCannotBeSet() {
            AttributeWrapper<Object, Integer> field =
                    attribute("Extras", "public static", "finalField", int.class, "public");
            assertThat(field.getValue()).isEqualTo(1);
            assertThatThrownBy(() -> field.setValue(2)).isInstanceOf(AssertionError.class);
        }

        @Test
        void varargsIsNotReportedAsTransient() {
            MethodWrapper<Object, Void> varargs =
                    method("Extras", "public static", "varargs", void.class, new Class<?>[]{int[].class}, "public");
            assertThat(varargs.getOverallExistence()).isEqualTo(EXACT);
            assertThat(varargs.actualToString()).doesNotContain("transient");
        }
    }

    @Nested
    class Missing {

        @Test
        void missingStaticMethodDeviatesAndRunsOnDefaultObject() {
            MethodWrapper<Object, Integer> m = method("Extras", "public static", "instanceMethod", int.class,
                    new Class<?>[0], "public", "static");
            assertThat(m.getOverallExistence()).isEqualTo(DEVIATES);
            assertThat(m.getMissingModifiers()).containsExactly("static");
            assertThat(m.toString()).contains("missing modifier: static");
            assertThat(m.invoke()).isEqualTo(4);
        }

        @Test
        void missingStaticAttributeUsesDefaultObject() {
            AttributeWrapper<Object, Integer> a =
                    attribute("Extras", "public static", "instanceField", int.class, "public", "static");
            assertThat(a.getOverallExistence()).isEqualTo(DEVIATES);
            assertThat(a.getValue()).isEqualTo(5);
        }
    }

    @Nested
    class Optional {

        @Test
        void optionalFinalMayBePresentOrAbsent() { // SE01E02
            assertThat(new W<>("SetlistTools", "public", "static", "final?").getOverallExistence()).isEqualTo(EXACT);
            assertThat(new W<>("SetlistToolsNotFinal", "public static final?").getOverallExistence()).isEqualTo(EXACT);
        }

        @Test
        void optionalModifiersAloneLeaveTheVisibilityUnchecked() {
            assertThat(new W<>("SetlistTools", "static", "final?").getOverallExistence()).isEqualTo(EXACT);
            assertThat(new W<>("SetlistToolsNotFinal", "static final?").getOverallExistence()).isEqualTo(EXACT);
            W<Object> onlyOptional = new W<>("SetlistToolsNotFinal", "final?");
            assertThat(onlyOptional.getUnexpectedModifiers()).containsExactly("static");
            assertThat(onlyOptional.getMissingModifiers()).isEmpty(); // public is not reported
        }

        @Test
        void optionalModifierIsNotShownAsExpected() {
            assertThat(new W<>("SetlistTools", "public", "static", "final?").expectedToString())
                    .isEqualTo("public static class ModifierFixtures$SetlistTools");
        }

        @Test
        void invalidSpecificationsAreRejected() {
            assertThatThrownBy(() -> new W<>("SetlistTools", "fnial?")).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new W<>("SetlistTools", "public?")).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new W<>("SetlistTools", "final", "final?"))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    class Implicit {

        @Test
        void interfaceMembersAreNotReported() {
            assertThat(new W<>("Api", "public static interface").getOverallExistence()).isEqualTo(EXACT);
            assertThat(method("Api", "public static interface", "run", void.class, new Class<?>[0], "public")
                    .getOverallExistence()).isEqualTo(EXACT);
            assertThat(method("Api", "public static interface", "twice", int.class, new Class<?>[0], "public")
                    .getOverallExistence()).isEqualTo(EXACT);
            assertThat(attribute("Api", "public static interface", "LIMIT", int.class, "public")
                    .getOverallExistence()).isEqualTo(EXACT);
            assertThat(new W<>("Api$InInterface", "public").getOverallExistence()).isEqualTo(EXACT);
        }

        @Test
        void staticInterfaceMethodMustBeExpectedStatic() {
            assertThat(method("Api", "public static interface", "one", int.class, new Class<?>[0], "public")
                    .getUnexpectedModifiers()).containsExactly("static");
        }

        @Test
        void enumsAndRecordsAreNotReported() {
            assertThat(new W<>("Plain", "public").getOverallExistence()).isEqualTo(EXACT);
            assertThat(new W<>("WithBodies", "public").getOverallExistence()).isEqualTo(EXACT);
            assertThat(new W<>("Point", "public").getOverallExistence()).isEqualTo(EXACT);
            assertThat(attribute("Plain", "public", "A", Object.class, "public").getUnexpectedModifiers()).isEmpty();
            assertThat(attribute("Point", "public", "x", int.class, "private").getOverallExistence()).isEqualTo(EXACT);
        }
    }

    @Nested
    class NestedClasses {

        @Test
        void innerClassWhereStaticExpectedDeviatesButIsUsable() {
            W<Object> inner = new W<>("Inner", "public static");
            assertThat(inner.getOverallExistence()).isEqualTo(DEVIATES);
            assertThat(inner.getMissingModifiers()).containsExactly("static");
            ConstructorWrapper<Object> ctor = new ConstructorWrapper<>(inner, new Class<?>[]{int.class}, "public");
            assertThat(ctor.getOverallExistence()).isEqualTo(EXACT);
            assertThat(ctor.getActualParamTypes()).containsExactly(int.class);
            Object obj = ctor.invoke(42);
            MethodWrapper<Object, Integer> v = new MethodWrapper<>(inner, "v", int.class, new Class<?>[0], "public");
            assertThat(v.invokeOnSpecificObject(obj)).isEqualTo(42);
        }

        @Test
        void innerObjectsShareOneOuterObject() {
            W<Object> inner = new W<>("Inner", "public static");
            ConstructorWrapper<Object> ctor = new ConstructorWrapper<>(inner, new Class<?>[]{int.class}, "public");
            MethodWrapper<Object, Object> outer =
                    new MethodWrapper<>(inner, "outer", Object.class, new Class<?>[0], "public");
            Object first = outer.invokeOnSpecificObject(ctor.invoke(1));
            Object second = outer.invokeOnSpecificObject(ctor.invoke(2));
            assertThat(first).isNotNull().isSameAs(second).isSameAs(inner.outerObj());
        }

        @Test
        void outerWithoutNoArgConstructorIsCreatedWithoutConstructor() {
            W<Object> part = new W<>("OuterWithArgs$Part", "public static");
            assertThat(part.getMissingModifiers()).containsExactly("static");
            MethodWrapper<Object, Integer> outerId =
                    new MethodWrapper<>(part, "outerId", int.class, new Class<?>[0], "public");
            assertThat(outerId.invoke()).isZero(); // the outer constructor did not run
        }

        @Test
        void innerClassDefaultObject() {
            W<Object> inner = new W<>("InnerNoArg", "public static");
            MethodWrapper<Object, Integer> v = new MethodWrapper<>(inner, "v", int.class, new Class<?>[0], "public");
            assertThat(v.invoke()).isEqualTo(9);
            W<Object> abstractInner = new W<>("AbstractInner", "public static abstract");
            MethodWrapper<Object, Integer> v2 =
                    new MethodWrapper<>(abstractInner, "v", int.class, new Class<?>[0], "public");
            assertThat(v2.invoke()).isEqualTo(11);
        }

        @Test
        void innerClassConstructorExpectingException() {
            W<Object> inner = new W<>("Inner", "public static");
            ConstructorWrapper<Object> ctor = new ConstructorWrapper<>(inner, new Class<?>[]{int.class}, "public");
            assertThatThrownBy(() -> ctor.invokeExpectingException(IllegalStateException.class, 1))
                    .isInstanceOf(AssertionError.class);
        }
    }

    @Nested
    class StaticReset {

        @Test
        void unexpectedStaticAttributeIsResetToItsInitialValue() {
            W<Object> stage = new W<>("FestivalStage", "public static");
            AttributeWrapper<Object, boolean[]> spotlights =
                    new AttributeWrapper<>(stage, "spotlights", boolean[].class, "private");
            AttributeWrapper<Object, int[][]> grid = new AttributeWrapper<>(stage, "grid", int[][].class, "private");
            MethodWrapper<Object, Void> switchOn =
                    new MethodWrapper<>(stage, "switchOn", void.class, new Class<?>[]{int.class}, "public");
            MethodWrapper<Object, Boolean> isOn =
                    new MethodWrapper<>(stage, "isOn", boolean.class, new Class<?>[]{int.class}, "public");

            StaticStateReset.resetAll(); // as before the first test: records the initial values
            Object first = stage.newObj();
            switchOn.invokeOnSpecificObject(first, 1);
            Object second = stage.newObj();
            assertThat(isOn.invokeOnSpecificObject(second, 1)).isTrue(); // shared within a test: the wrong behaviour stays visible

            StaticStateReset.resetAll(); // as before the next test
            assertThat(isOn.invokeOnSpecificObject(stage.newObj(), 1)).isFalse();
            assertThat(grid.getValue()[0][1]).isZero();
            assertThat(spotlights.getValue()).containsExactly(false, false, false);

            switchOn.invokeOnSpecificObject(stage.newObj(), 0);
            StaticStateReset.resetAll(); // the snapshot itself was not changed by the earlier reset
            assertThat(spotlights.getValue()).containsExactly(false, false, false);
            assertThat(grid.getValue()[0]).containsExactly(0, 0);
        }

        @Test
        void attributeExpectedStaticByAnyLiveWrapperIsNotReset() {
            W<Object> counter = new W<>("SharedCounter", "public static");
            AttributeWrapper<Object, int[]> asInstance =
                    new AttributeWrapper<>(counter, "count", int[].class, "private");
            AttributeWrapper<Object, int[]> asStatic =
                    new AttributeWrapper<>(counter, "count", int[].class, "private", "static");
            MethodWrapper<Object, Void> increment =
                    new MethodWrapper<>(counter, "increment", void.class, new Class<?>[0], "public");
            StaticStateReset.resetAll();
            increment.invoke();
            StaticStateReset.resetAll();
            assertThat(asInstance.hasUnexpectedModifier("static")).isTrue();
            assertThat(asStatic.getOverallExistence()).isEqualTo(EXACT);
            assertThat(asInstance.getValue()[0]).isEqualTo(1); // not reset: another wrapper expects static
        }

        @Test
        void finalAndBrokenAttributesAreSkipped() {
            AttributeWrapper<Object, int[]> fixed =
                    attribute("SharedCounter", "public static", "FIXED", int[].class, "public", "final");
            assertThat(fixed.getUnexpectedModifiers()).containsExactly("static");
            AttributeWrapper<Object, int[]> broken =
                    attribute("BrokenInit", "public static", "values", int[].class, "private");
            assertThat(broken.getUnexpectedModifiers()).containsExactly("static");
            org.assertj.core.api.Assertions.assertThatCode(StaticStateReset::resetAll).doesNotThrowAnyException();
            assertThat(fixed.getValue()).containsExactly(1);
        }

        @Test
        void missingElementHasNoModifierDeviations() {
            AttributeWrapper<Object, Integer> ghost =
                    attribute("Extras", "public static", "doesNotExistAtAll", int.class, "private", "static");
            assertThat(ghost.getOverallExistence()).isEqualTo(MISSING);
            assertThat(ghost.getMissingModifiers()).isEmpty();
            assertThat(ghost.getUnexpectedModifiers()).isEmpty();
        }

        @Test
        void deepCopyCopiesNestedArraysOnly() {
            int[][] original = {{1}, {2}};
            int[][] copy = (int[][]) StaticStateReset.deepCopy(original);
            assertThat(copy).isNotSameAs(original).isDeepEqualTo(original);
            assertThat(copy[0]).isNotSameAs(original[0]);
            Object other = new Object();
            assertThat(StaticStateReset.deepCopy(other)).isSameAs(other);
            assertThat(StaticStateReset.deepCopy(null)).isNull();
        }

        @Test
        void aresIsRegisteredBeforeTheResetSoTheResetRunsSupervised() {
            assertThat(indexOf(LevenshteinTest.class, de.tum.cit.ase.ares.api.jupiter.Public.class))
                    .isLessThan(indexOf(LevenshteinTest.class, ExtendWith.class));
            assertThat(indexOf(HiddenLevenshteinTest.class, de.tum.cit.ase.ares.api.jupiter.Hidden.class))
                    .isLessThan(indexOf(HiddenLevenshteinTest.class, ExtendWith.class));
        }

        private static int indexOf(Class<?> annotated, Class<? extends java.lang.annotation.Annotation> type) {
            java.lang.annotation.Annotation[] annotations = annotated.getDeclaredAnnotations();
            for (int i = 0; i < annotations.length; i++) {
                if (annotations[i].annotationType() == type) {
                    return i;
                }
            }
            throw new AssertionError(type + " missing on " + annotated);
        }

        @Test
        void extensionIsPartOfTheComposedAnnotations() {
            assertThat(LevenshteinTest.class.getAnnotation(org.junit.jupiter.api.extension.ExtendWith.class).value())
                    .containsExactly(StaticStateReset.class);
            assertThat(HiddenLevenshteinTest.class.getAnnotation(org.junit.jupiter.api.extension.ExtendWith.class).value())
                    .containsExactly(StaticStateReset.class);
        }
    }

    /** The extension as JUnit runs it: once per test, before the first {@code @BeforeEach} method. */
    @Nested
    @ExtendWith(StaticStateReset.class)
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class ResetBetweenTests {
        static final W<Object> STAGE = new W<>("ResetStage", "public static");
        static final AttributeWrapper<Object, int[]> HITS = new AttributeWrapper<>(STAGE, "hits", int[].class, "private");
        static final MethodWrapper<Object, Void> HIT = new MethodWrapper<>(STAGE, "hit", void.class, new Class<?>[0], "public");
        static final MethodWrapper<Object, Integer> COUNT =
                new MethodWrapper<>(STAGE, "hits", int.class, new Class<?>[0], "public");

        @BeforeEach
        void hitOnceInSetUp() {
            HIT.invoke();
        }

        @BeforeEach
        void secondSetUpMethodDoesNotResetAgain() {
            HIT.invoke();
        }

        @Test
        @Order(1)
        void firstTestChangesTheStaticState() {
            HIT.invoke();
            assertThat(COUNT.invoke()).isEqualTo(3);
        }

        @Test
        @Order(2)
        void secondTestStartsFromTheInitialValue() {
            assertThat(HITS.getOverallExistence()).isEqualTo(DEVIATES);
            assertThat(COUNT.invoke()).isEqualTo(2);
        }
    }

    @Nested
    class Legacy {

        @Test
        void ignoreRestoresThePreviousBehaviour() {
            LevenshteinSettings.setUnexpectedModifiers(UnexpectedModifiers.IGNORE);
            assertThat(attribute("Extras", "public static", "finalField", int.class, "public")
                    .getOverallExistence()).isEqualTo(EXACT);
            assertThat(method("Extras", "public static", "instanceMethod", int.class, new Class<?>[0],
                    "public", "static").getOverallExistence()).isEqualTo(MISSING);
            assertThat(attribute("FestivalStage", "public static", "spotlights", boolean[].class, "private")
                    .getOverallExistence()).isEqualTo(DEVIATES);
            assertThat(new W<>("AbstractButShouldNotBe", "public static").getOverallExistence()).isEqualTo(EXACT);
        }

        @Test
        void legacyIgnoresUnexpectedStaticOnClasses() {
            LevenshteinSettings.setUnexpectedModifiers(UnexpectedModifiers.IGNORE);
            assertThat(new W<>("SetlistToolsNotFinal", "public").getOverallExistence()).isEqualTo(EXACT);
            assertThat(attribute("Api", "public static interface", "LIMIT", int.class, "public")
                    .getOverallExistence()).isEqualTo(EXACT);
        }

        @Test
        void germanMessages() {
            LevenshteinSettings.setLanguage(LevenshteinSettings.Language.DEUTSCH);
            MethodWrapper<Object, Integer> m = method("Extras", "public static", "instanceMethod", int.class,
                    new Class<?>[0], "public", "static");
            assertThat(m.toString()).contains("fehlender Modifikator: static");
            assertThat(attribute("Extras", "public static", "finalField", int.class, "public").toString())
                    .contains("unerwarteter Modifikator: final");
        }

        @Test
        void settingDefaultsAndValidation() {
            assertThat(LevenshteinSettings.getUnexpectedModifiers()).isEqualTo(UnexpectedModifiers.DEVIATE);
            LevenshteinSettings.setUnexpectedModifiers(UnexpectedModifiers.IGNORE);
            LevenshteinSettings.reset();
            assertThat(LevenshteinSettings.getUnexpectedModifiers()).isEqualTo(UnexpectedModifiers.DEVIATE);
            assertThatThrownBy(() -> LevenshteinSettings.setUnexpectedModifiers(null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
