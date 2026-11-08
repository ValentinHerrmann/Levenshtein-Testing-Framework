package io.github.valentinherrmann.levenshtein;

import static io.github.valentinherrmann.levenshtein.WrapperProperty.Existence.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.tum.cit.ase.ares.api.MirrorOutput;
import de.tum.cit.ase.ares.api.StrictTimeout;
import de.tum.cit.ase.ares.api.jupiter.Hidden;
import de.tum.cit.ase.ares.api.jupiter.Public;
import io.github.valentinherrmann.levenshtein.fixtures.Fixtures;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.platform.commons.support.AnnotationSupport;

/**
 * Regression tests for the issues found in the review (GitHub issues #6-#15 and the additional findings).
 */
class WrapperRegressionTest {

    static final String PKG = "io.github.valentinherrmann.levenshtein.fixtures";

    /** Minimal wrapper for a fixture class. */
    static class W<T> extends ClassWrapper<T> {
        W(String name, String... modifiers) {
            super(name, PKG, modifiers);
        }

        W(String name, ClassWrapper<?> superClass, ClassWrapper<?>[] interfaces, String... modifiers) {
            super(name, PKG, superClass, interfaces, modifiers);
        }

        @Override
        public Object getObj(boolean forceNew) {
            return getObj(forceNew, null);
        }
    }

    static <T> W<T> nested(String simpleName, String... modifiers) {
        return new W<>("Fixtures$" + simpleName, modifiers);
    }

    /** Carriers of the composed annotations; without test methods, so they never run (and never start Ares). */
    @LevenshteinTest
    static class PublicProbe {
    }

    @HiddenLevenshteinTest
    static class HiddenProbe {
    }

    @AfterEach
    void resetSettings() {
        LevenshteinSettings.reset();
    }

    @Nested
    class Classes {

        @Test
        void twoInterfacesDoNotCrash() { // review H1
            W<Object> drivable = nested("Drivable", "public static abstract interface");
            W<Object> honkable = nested("Honkable", "public static abstract interface");
            W<Object> w = new W<>("Fixtures$TwoInterfaces", null, new ClassWrapper<?>[]{drivable, honkable}, "public", "static");
            assertThat(w.getOverallExistence()).isEqualTo(EXACT);
        }

        @Test
        void missingExpectedInterfaceIsReportedNotThrown() { // review H3
            W<Object> ghost = nested("GhostInterface", "public interface");
            W<Object> w = new W<>("Fixtures$NoSuper", null, new ClassWrapper<?>[]{ghost}, "public", "static");
            assertThat(w.getOverallExistence()).isEqualTo(MISSING);
            assertThatCode(w::toString).doesNotThrowAnyException();
            assertThat(w.toString()).contains("implements Fixtures$GhostInterface");
        }

        @Test
        void indirectInterfaceDeviates() { // review H9
            W<Object> drivable = nested("Drivable", "public static abstract interface");
            W<Object> w = new W<>("Fixtures$IndirectInterface", null, new ClassWrapper<?>[]{drivable}, "public", "static");
            assertThat(w.getOverallExistence()).isEqualTo(DEVIATES);
        }

        @Test
        void deviatingInterfaceNameStillMatchesImplementingClass() { // review H9
            W<Object> drivable = nested("Drivabel", "public static abstract interface"); // typo in the spec
            W<Object> w = new W<>("Fixtures$TwoInterfaces", null, new ClassWrapper<?>[]{drivable}, "public", "static");
            assertThat(drivable.getOverallExistence()).isEqualTo(DEVIATES);
            assertThat(w.interfaceWrappers.existence).isNotEqualTo(MISSING);
        }

        @Test
        void missingSuperclassIsReportedNotThrown() { // #7
            W<Object> base = nested("Base", "public static");
            W<Object> w = new W<>("Fixtures$NoSuper", base, null, "public", "static");
            assertThatCode(w::getOverallExistence).doesNotThrowAnyException();
            assertThat(w.getOverallExistence()).isEqualTo(MISSING);

            W<Object> ghost = nested("GhostBase", "public static");
            W<Object> w2 = new W<>("Fixtures$Derived", ghost, null, "public", "static");
            assertThat(w2.getOverallExistence()).isEqualTo(MISSING);
        }

        @Test
        void indirectSuperclassDeviatesAndActualIsShown() { // M3
            W<Object> base = nested("Base", "public static");
            W<Object> w = new W<>("Fixtures$DerivedTwice", base, null, "public", "static");
            assertThat(w.getOverallExistence()).isEqualTo(DEVIATES);
            assertThat(w.actualToString()).contains("extends Derived").doesNotContain("extends Fixtures$Base");
        }

        @Test
        void implicitSuperclassOfRecordsAndEnumsIsNoSuperclass() {
            W<Object> point = nested("Point", "public static");
            assertThat(point.getOverallExistence()).isEqualTo(EXACT);
            assertThat(point.actualToString()).doesNotContain("extends");

            W<Object> color = nested("Color", "public static");
            assertThat(color.getOverallExistence()).isEqualTo(EXACT);
            assertThat(color.actualToString()).doesNotContain("extends");

            W<Object> base = nested("Base", "public static");
            W<Object> pointWithSuper = new W<>("Fixtures$Point", base, null, "public", "static");
            assertThat(pointWithSuper.getOverallExistence()).isEqualTo(MISSING);
        }

        @Test
        void classReportIsLocalized() {
            W<Object> w = nested("DoesNotExist", "public");
            assertThat(w.toString()).contains("FEHLT").contains("im Paket " + PKG)
                    .contains("Erwartet:").contains("Tatsächlich:").doesNotContain("Expect:");
            LevenshteinSettings.setLanguage(LevenshteinSettings.Language.ENGLISH);
            assertThat(w.toString()).contains("MISSING").contains("in package " + PKG)
                    .contains("Expect:").contains("Actual:");
        }

        @Test
        void missingClassIsStillUsableForMessages() {
            W<Object> w = nested("DoesNotExist", "public");
            assertThat(w.getOverallExistence()).isEqualTo(MISSING);
            assertThatThrownBy(() -> w.verifyExistence(true)).isInstanceOf(AssertionError.class);
        }

        @Test
        void classNameThresholdIsAPercentage() { // #6: "Buss" vs "Bus" = 25 % > 20 %
            assertThat(new W<>("Buss", "public").getOverallExistence()).isEqualTo(MISSING);
            LevenshteinSettings.setClassNameDeviationThreshold(25);
            assertThat(new W<>("Buss", "public").getOverallExistence()).isEqualTo(DEVIATES);
        }

        @Test
        void wrongFirstLetterCaseIsFound() { // H7
            W<Object> w = new W<>("MyCar", "public");
            assertThat(w.getOverallExistence()).isEqualTo(DEVIATES);
            assertThat(w.getActualName()).isEqualTo("myCar");
        }

        @Test
        void staticInitializerDoesNotRunDuringLookup() { // H7
            W<Object> w = nested("ExplodingInit", "public static");
            assertThatCode(w::getOverallExistence).doesNotThrowAnyException();
            assertThat(w.getOverallExistence()).isEqualTo(EXACT);
        }

        @Test
        void defaultPackageDeviationsAreFound() { // #10.1
            ClassWrapper<Object> w = new ClassWrapper<>("DefaultPackageFixtur", "", "public") {
                @Override
                public Object getObj(boolean forceNew) {
                    return null;
                }
            };
            assertThat(w.getOverallExistence()).isEqualTo(DEVIATES);
            assertThat(w.getClazz().getName()).isEqualTo("DefaultPackageFixture");
        }

        @Test
        void finalConcreteClassIsInstantiatedWithoutByteBuddy() { // H8
            W<Object> w = nested("FinalCar", "public static final");
            Object car = w.getObj(false);
            assertThat(car).isInstanceOf(Fixtures.FinalCar.class);
            assertThat(car.getClass()).isEqualTo(Fixtures.FinalCar.class);
            assertThat(car).isEqualTo(new Fixtures.FinalCar());
        }

        @Test
        void forceNewCreatesANewDefaultInstance() { // #15.1: getObj(true) is a new instance, not the cached one
            W<Object> w = nested("FinalCar", "public static final");
            Object first = w.getObj(false);
            assertThat(w.getObj(false)).isSameAs(first);
            assertThat(w.getObj()).isSameAs(first);
            Object fresh = w.getObj(true);
            assertThat(fresh).isNotSameAs(first);
            assertThat(w.newObj()).isNotSameAs(fresh);
        }

        @Test
        void cacheRespectsConstructorAndArguments() { // #11.4
            W<Object> w = nested("FinalCar", "public static final");
            ConstructorWrapper<Object> ctor = new ConstructorWrapper<>(w, new Class<?>[]{String.class}, "public");
            Object bmw = w.getObj(false, ctor, "BMW");
            assertThat(w.getObj(false, ctor, "BMW")).isSameAs(bmw);
            Object audi = w.getObj(false, ctor, "Audi");
            assertThat(((Fixtures.FinalCar) audi).getBrand()).isEqualTo("Audi");
            assertThat(w.getObj(true, ctor, "Audi")).isNotSameAs(audi);
        }

        @Test
        void internalFieldsAreNoMemberWrappers() {
            W<Object> w = nested("FinalCar", "public static final");
            ConstructorWrapper<Object> ctor = new ConstructorWrapper<>(w, new Class<?>[]{String.class}, "public");
            w.getObj(false, ctor, "BMW"); // fills the internal cache key
            assertThat(w.getConstructorWrappers()).isEmpty();
        }

        @Test
        void equalsAndHashCodeAreConsistent() { // M4
            assertThat(nested("Base")).isEqualTo(nested("Base")).hasSameHashCodeAs(nested("Base"));
        }

        @Test
        void unknownModifierIsRejectedAtConstruction() { // #15.4
            assertThatThrownBy(() -> nested("Base", "pubilc")).isInstanceOf(IllegalArgumentException.class);
            assertThat(nested("Base", "public  static ").getOverallExistence()).isEqualTo(EXACT);
        }
    }

    @Nested
    class Members {

        /** Wrapper with two method wrappers, to check that one cannot take the other's method. */
        class SpeedWrapper extends W<Object> {
            final MethodWrapper<Object, Integer> getMaxSpeed = new MethodWrapper<>(this, "getMaxSpeed", int.class, "public");
            final MethodWrapper<Object, Integer> getMinSpeed = new MethodWrapper<>(this, "getMinSpeed", int.class, "public");

            SpeedWrapper() {
                super("Fixtures$Typos", "public", "static");
            }
        }

        final W<Object> typos = nested("Typos", "public static");

        @Test
        void methodNameDeviationIsReported() { // review H2
            MethodWrapper<Object, Double> m = new MethodWrapper<>(typos, "calculateCost", double.class, "public");
            assertThat(m.getOverallExistence()).isEqualTo(DEVIATES);
            assertThat(m.toString()).contains("calculateCost()").contains("calculateCots()");
        }

        @Test
        void expectedMemberIsNotTakenByAnotherWrapper() { // review H6
            SpeedWrapper w = new SpeedWrapper();
            assertThat(w.getMinSpeed.getOverallExistence()).isEqualTo(EXACT);
            assertThat(w.getMaxSpeed.getOverallExistence()).isEqualTo(MISSING);
        }

        @Test
        void closestAttributeWins() { // review H6
            AttributeWrapper<Object, Double> a = new AttributeWrapper<>(typos, "manufacturer", double.class, "private");
            assertThat(a.getOverallExistence()).isEqualTo(DEVIATES);
            assertThat(a.getActualName()).isEqualTo("manufacturr");
        }

        @Test
        void primitiveAndWrapperTypesDeviate() { // review H4
            AttributeWrapper<Object, Integer> a = new AttributeWrapper<>(typos, "count", int.class, "private");
            assertThat(a.getOverallExistence()).isEqualTo(DEVIATES);
            assertThat(a.getValue(new Fixtures.Typos())).isEqualTo(5);
        }

        @Test
        void wrapperParameterTypesDeviateAndActualSignatureIsShown() { // #11.3
            MethodWrapper<Object, Integer> m = new MethodWrapper<>(typos, "add", int.class, new Class<?>[]{int.class, int.class}, "public");
            assertThat(m.getOverallExistence()).isEqualTo(DEVIATES);
            assertThat(m.actualToString()).contains("add(Integer, Integer)");
            assertThat(m.expectedToString()).contains("add(int, int)");
            assertThat(m.invokeOnSpecificObject(new Fixtures.Typos(), 2, 3)).isEqualTo(5);
        }

        @Test
        void narrowerActualReturnTypeDeviates() {
            MethodWrapper<Object, Long> m = new MethodWrapper<>(typos, "total", long.class, new Class<?>[]{}, "public");
            assertThat(m.getOverallExistence()).isEqualTo(DEVIATES);
        }

        @Test
        void widerActualReturnTypeDeviates() {
            MethodWrapper<Object, Integer> m = new MethodWrapper<>(typos, "scale", int.class, new Class<?>[]{int.class}, "public");
            assertThat(m.getOverallExistence()).isEqualTo(DEVIATES);
        }

        @Test
        void booleanIsNotANumericDeviation() {
            MethodWrapper<Object, Integer> m = new MethodWrapper<>(typos, "flag", int.class, new Class<?>[]{}, "public");
            assertThat(m.getOverallExistence()).isEqualTo(MISSING);
        }

        @Test
        void numericParameterTypesDeviate() {
            MethodWrapper<Object, Long> m = new MethodWrapper<>(typos, "scale", long.class, new Class<?>[]{long.class}, "public");
            assertThat(m.getOverallExistence()).isEqualTo(DEVIATES);
            assertThat(m.actualToString()).contains("scale(int)");
        }

        @Test
        void unexpectedStaticIsReported() { // M2
            AttributeWrapper<Object, Integer> a = new AttributeWrapper<>(typos, "counter", int.class, "private");
            assertThat(a.getOverallExistence()).isEqualTo(DEVIATES);
        }

        @Test
        void studentExceptionIsPreservedInFailure() { // M1
            MethodWrapper<Object, Integer> m = new MethodWrapper<>(typos, "divide", int.class, new Class<?>[]{int.class, int.class}, "public");
            assertThatThrownBy(() -> m.invokeOnSpecificObject(new Fixtures.Typos(), 1, 0))
                    .isInstanceOf(AssertionError.class)
                    .hasMessageContaining("ArithmeticException: / by zero")
                    .hasNoCause(); // no stack trace of the student code in the report
        }

        @Test
        void expectedExceptionCanBeAsserted() { // M1
            MethodWrapper<Object, Void> m = new MethodWrapper<>(typos, "fail", void.class, new Class<?>[]{String.class}, "public");
            IllegalArgumentException e = m.invokeExpectingException(IllegalArgumentException.class, new Fixtures.Typos(), "why");
            assertThat(e).hasMessage("why");
            MethodWrapper<Object, Integer> divide = new MethodWrapper<>(typos, "divide", int.class, new Class<?>[]{int.class, int.class}, "public");
            assertThatThrownBy(() -> divide.invokeExpectingException(IllegalStateException.class, new Fixtures.Typos(), 4, 2))
                    .isInstanceOf(AssertionError.class);
        }

        @Test
        void getterTestHandlesNullValues() { // #11.2
            W<Object> w = nested("Typos", "public static");
            AttributeWrapper<Object, String> field = new AttributeWrapper<>(w, "nothing", String.class, "private");
            MethodWrapper<Object, String> getter = new MethodWrapper<>(w, "getNothing", String.class, "public");
            assertThatCode(() -> w.testGetter(field, getter)).doesNotThrowAnyException();
        }
    }

    @Nested
    class GettersSetters {

        private final W<Object> w = nested("GettersSetters", "public static");

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

        @Test
        void correctGetterAndSetterPass() {
            AttributeWrapper<Object, Double> price = attribute("price", double.class);
            assertThatCode(() -> w.testGettersSetters(price, getter("getPrice", double.class), setter("setPrice", double.class)))
                    .doesNotThrowAnyException();
            assertThatCode(() -> w.testGettersSetters(price, setter("setPrice", double.class), getter("getPrice", double.class)))
                    .doesNotThrowAnyException();
            assertThatCode(() -> w.testGettersSetters(price, getter("getPrice", double.class))).doesNotThrowAnyException();
            assertThatCode(() -> w.testGetter(price, getter("getPrice", double.class))).doesNotThrowAnyException();
        }

        @Test
        void hardCodedGetterFails() {
            String message = failure(() -> w.testGettersSetters(attribute("count", int.class),
                    getter("getCount", int.class), setter("setCount", int.class)));
            // setter -> attribute passes; the first failing case is the first one of attribute -> getter
            assertThat(message).isEqualTo("""
                    Method:   getCount()
                    Scenario: attribute -> getter, attribute count
                    Input:    count = 7
                    Expected: 7
                    Actual:   42
                    (8 of 15 cases failed)""");
        }

        @Test
        void setterIgnoringItsArgumentFails() {
            String message = failure(() -> w.testGettersSetters(attribute("name", String.class),
                    getter("getName", String.class), setter("setName", String.class)));
            assertThat(message).isEqualTo("""
                    Method:   setName(String)
                    Scenario: setter -> attribute, attribute name
                    Input:    setName("Alpha")
                    Expected: "Alpha"
                    Actual:   null
                    (10 of 18 cases failed)""");
        }

        @Test
        void getterOfAnotherAttributeFails() {
            String message = failure(() -> w.testGettersSetters(attribute("other", int.class), getter("getWrong", int.class)));
            assertThat(message)
                    .contains("Scenario: attribute -> getter, attribute other")
                    .contains("Expected: 7\nActual:   0")
                    .endsWith("(4 of 5 cases failed)"); // only attribute -> getter ran
        }

        @Test
        void onlyGivenSubtestsRun() {
            String message = failure(() -> w.testGettersSetters(attribute("name", String.class), setter("setName", String.class)));
            assertThat(message)
                    .contains("Scenario: setter -> attribute, attribute name")
                    .doesNotContain("getter")
                    .endsWith("(5 of 6 cases failed)"); // only setter -> attribute ran
        }

        @Test
        void setterConditionAcceptsRejectingSetters() {
            assertThatCode(() -> w.testGettersSetters(attribute("label", String.class), v -> v != null,
                    getter("getLabel", String.class), setter("setLabel", String.class))).doesNotThrowAnyException();
            assertThatCode(() -> w.testGettersSetters(attribute("title", String.class), v -> v != null,
                    getter("getTitle", String.class), setter("setTitle", String.class))).doesNotThrowAnyException();
        }

        @Test
        void setterConditionFailsSettersStoringInvalidValues() {
            String message = failure(() -> w.testGettersSetters(attribute("tag", String.class), (String v) -> v != null,
                    getter("getTag", String.class), setter("setTag", String.class)));
            assertThat(message).isEqualTo("""
                    Method:   setTag(String)
                    Scenario: setter -> attribute, attribute tag
                    Input:    setTag(null)
                    Expected: value rejected (unchanged "x" or exception)
                    Actual:   null
                    (2 of 18 cases failed)""");

            String negative = failure(() -> w.testGettersSetters(attribute("price", double.class), (Double p) -> p >= 0,
                    setter("setPrice", double.class)));
            assertThat(negative)
                    .contains("Input:    setPrice(-1.25)")
                    .contains("Expected: value rejected (unchanged 2.50 or exception)");
        }

        @Test
        void conditionForAnotherTypeIsATestError() {
            assertThatThrownBy(() -> w.testGettersSetters(attribute("price", double.class), (String s) -> !s.isEmpty(),
                    setter("setPrice", double.class)))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void typeDeviationsAndFinalAttributesWork() {
            assertThatCode(() -> w.testGettersSetters(attribute("amount", int.class),
                    getter("getAmount", int.class), setter("setAmount", int.class))).doesNotThrowAnyException();
            assertThatCode(() -> w.testGettersSetters(attribute("fixed", int.class), getter("getFixed", int.class)))
                    .doesNotThrowAnyException();
        }

        @Test
        void ownSampleValuesAreNeededForOtherTypes() {
            AttributeWrapper<Object, Object> box = attribute("box", Object.class);
            assertThatCode(() -> w.testGettersSetters(box, getter("getBox", Object.class))).doesNotThrowAnyException();
            assertThatThrownBy(() -> w.testGettersSetters(box, setter("setBox", Object.class)))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatCode(() -> w.testGettersSetters(box, List.of(new Object(), "text", 4, new Object(), 2.5),
                    getter("getBox", Object.class), setter("setBox", Object.class))).doesNotThrowAnyException();
        }

        @Test
        void misuseIsATestError() {
            AttributeWrapper<Object, Double> price = attribute("price", double.class);
            MethodWrapper<Object, ?> getPrice = getter("getPrice", double.class);
            MethodWrapper<Object, Double> twoParams = new MethodWrapper<>(w, "setPrice", double.class,
                    new Class<?>[]{double.class, double.class}, "public");
            assertThatThrownBy(() -> w.testGettersSetters(price)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> w.testGettersSetters(price, getPrice, getPrice)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> w.testGettersSetters(price, twoParams)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> w.testGettersSetters(price, v -> true, getPrice)).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void defaultInstanceIsKept() {
            Fixtures.GettersSetters pinned = new Fixtures.GettersSetters();
            pinned.setPrice(99.0);
            w.setCachedObj(pinned);
            w.testGettersSetters(attribute("price", double.class), getter("getPrice", double.class), setter("setPrice", double.class));
            assertThat(w.getObj()).isSameAs(pinned);
            assertThat(pinned.getPrice()).isEqualTo(99.0);
        }
    }

    @Nested
    class NumericDeviations { // #22

        private final W<Object> w = nested("NumericDeviations", "public static");

        private <V> AttributeWrapper<Object, V> attribute(String name, Class<V> type) {
            return new AttributeWrapper<>(w, name, type, "private");
        }

        private MethodWrapper<Object, ?> getter(String name, Class<?> type) {
            return new MethodWrapper<>(w, name, type, "public");
        }

        private MethodWrapper<Object, Void> setter(String name, Class<?> type) {
            return new MethodWrapper<>(w, name, void.class, new Class<?>[]{type}, "public");
        }

        @Test
        void primitiveFieldOfAnotherNumericTypeCanBeSet() {
            Object obj = w.newObj();
            AttributeWrapper<Object, Double> price = attribute("price", double.class);
            assertThat(price.getOverallExistence()).isEqualTo(DEVIATES);
            price.setValue(obj, 2.5);
            assertThat(price.getValue(obj)).isEqualTo(2.5);
            assertThat(((Fixtures.NumericDeviations) obj).getPrice()).isEqualTo(2.5f);

            AttributeWrapper<Object, Integer> count = attribute("count", int.class);
            count.setValue(obj, 42);
            assertThat(count.getValue(obj)).isEqualTo(42);

            AttributeWrapper<Object, Integer> small = attribute("small", int.class);
            small.setValue(obj, -3);
            assertThat(small.getValue(obj)).isEqualTo(-3);

            AttributeWrapper<Object, Double> whole = attribute("whole", double.class);
            whole.setValue(obj, 42.0);
            assertThat(whole.getValue(obj)).isEqualTo(42.0);

            AttributeWrapper<Object, Integer> letter = attribute("letter", int.class);
            letter.setValue(obj, 97);
            assertThat(((Fixtures.NumericDeviations) obj).getLetter()).isEqualTo('a');
        }

        @Test
        void getterSetterTestPassesForNumericDeviations() {
            assertThatCode(() -> w.testGettersSetters(attribute("price", double.class),
                    getter("getPrice", double.class), setter("setPrice", double.class))).doesNotThrowAnyException();
            assertThatCode(() -> w.testGettersSetters(attribute("count", int.class),
                    getter("getCount", int.class), setter("setCount", int.class))).doesNotThrowAnyException();
            assertThatCode(() -> w.testGettersSetters(attribute("small", int.class),
                    getter("getSmall", int.class), setter("setSmall", int.class))).doesNotThrowAnyException();
            // 2.5 does not fit into an int: whole numbers are used instead
            assertThatCode(() -> w.testGettersSetters(attribute("whole", double.class),
                    getter("getWhole", double.class), setter("setWhole", double.class))).doesNotThrowAnyException();
            // -3 does not fit into a char: character codes are used instead
            assertThatCode(() -> w.testGettersSetters(attribute("letter", int.class),
                    getter("getLetter", int.class), setter("setLetter", int.class))).doesNotThrowAnyException();
        }
    }

    @Nested
    class ArrayAttributes { // #25

        private final W<Object> w = nested("ArrayAttributes", "public static");

        private final List<boolean[]> flags = List.of(new boolean[]{true, false}, new boolean[]{false}, new boolean[0]);

        private <V> AttributeWrapper<Object, V> attribute(String name, Class<V> type) {
            return new AttributeWrapper<>(w, name, type, "private");
        }

        private MethodWrapper<Object, ?> getter(String name, Class<?> type) {
            return new MethodWrapper<>(w, name, type, "public");
        }

        private MethodWrapper<Object, Void> setter(String name, Class<?> type) {
            return new MethodWrapper<>(w, name, void.class, new Class<?>[]{type}, "public");
        }

        @Test
        void copiesWithEqualContentPass() {
            assertThatCode(() -> w.testGettersSetters(attribute("spotlights", boolean[].class), flags,
                    getter("getSpotlights", boolean[].class), setter("setSpotlights", boolean[].class)))
                    .doesNotThrowAnyException();
            assertThatCode(() -> w.testGettersSetters(attribute("names", String[].class),
                    List.of(new String[]{"a", "b"}, new String[0]),
                    getter("getNames", String[].class), setter("setNames", String[].class)))
                    .doesNotThrowAnyException();
        }

        @Test
        void arraysArePrintedWithTheirElements() {
            LevenshteinSettings.setLanguage(LevenshteinSettings.Language.ENGLISH);
            AssertionError e = org.junit.jupiter.api.Assertions.assertThrows(AssertionError.class,
                    () -> w.testGettersSetters(attribute("broken", boolean[].class), flags,
                            getter("getBroken", boolean[].class), setter("setBroken", boolean[].class)));
            assertThat(e.getMessage()).contains("[true, false]").contains("[false]").doesNotContain("[Z@");
        }

        @Test
        void sameObjectSettingIsResetAndKeepsValueComparisonForStrings() {
            LevenshteinSettings.setGettersSettersRequireSameObject(true);
            assertThat(LevenshteinSettings.isGettersSettersRequireSameObject()).isTrue();
            // the getter returns new String(text): strings are still compared by value
            assertThatCode(() -> w.testGettersSetters(attribute("text", String.class),
                    getter("getText", String.class), setter("setText", String.class)))
                    .doesNotThrowAnyException();
            LevenshteinSettings.reset();
            assertThat(LevenshteinSettings.isGettersSettersRequireSameObject()).isFalse();
        }

        @Test
        void sameObjectSettingWorksWithSetterCondition() {
            LevenshteinSettings.setGettersSettersRequireSameObject(true);
            // the setter keeps the old array for an empty one: same object before and after
            assertThatCode(() -> w.testGettersSetters(attribute("levels", int[].class),
                    List.of(new int[]{1, 2}, new int[0], new int[]{3}), (int[] l) -> l.length > 0,
                    getter("getLevels", int[].class), setter("setLevels", int[].class)))
                    .doesNotThrowAnyException();
        }

        @Test
        void sameObjectCanBeRequired() {
            LevenshteinSettings.setGettersSettersRequireSameObject(true);
            assertThatThrownBy(() -> w.testGettersSetters(attribute("spotlights", boolean[].class), flags,
                    getter("getSpotlights", boolean[].class), setter("setSpotlights", boolean[].class)))
                    .isInstanceOf(AssertionError.class);
            assertThatCode(() -> w.testGettersSetters(attribute("names", String[].class),
                    List.of(new String[]{"a", "b"}, new String[0]),
                    getter("getNames", String[].class), setter("setNames", String[].class)))
                    .doesNotThrowAnyException();
        }
    }

    @Nested
    class Calls { // #27, #28

        private final W<Object> w = nested("Calls", "public static");

        private final MethodWrapper<Object, Double> divide =
                new MethodWrapper<>(w, "divide", double.class, new Class<?>[]{double.class, double.class}, "public");

        private final MethodWrapper<Object, Integer> count =
                new MethodWrapper<>(w, "count", int.class, new Class<?>[]{String[].class}, "public");

        private String failure(Runnable call) {
            LevenshteinSettings.setLanguage(LevenshteinSettings.Language.ENGLISH);
            return org.junit.jupiter.api.Assertions.assertThrows(AssertionError.class, call::run).getMessage();
        }

        @Test
        void exceptionMessageShowsTheArguments() {
            assertThat(failure(() -> divide.invoke(2.5, 0.0)))
                    .isEqualTo("""
                            Method:   divide(double, double)
                            Input:    2.50, 0.00
                            Actual:   ArithmeticException: division by zero""");
        }

        @Test
        void contextIsPutInFrontOfTheMessage() {
            assertThat(failure(() -> divide.invokeWithContext(() -> "Scenario 3, seed 42", 2.5, 0.0)))
                    .isEqualTo("""
                            Method:   divide(double, double)
                            Scenario: Scenario 3, seed 42
                            Input:    2.50, 0.00
                            Actual:   ArithmeticException: division by zero""");
            Object obj = w.newObj();
            assertThat(failure(() -> divide.invokeOnSpecificObjectWithContext(() -> "case 7", obj, 1.0, 0.0)))
                    .contains("\nScenario: case 7\n");
            assertThat(failure(() -> divide.invokeWithContext(() -> { throw new IllegalStateException(); }, 1.0, 0.0)))
                    .doesNotContain("Scenario:");
            assertThat(divide.invokeWithContext(() -> "unused", 3.0, 2.0)).isEqualTo(1.5);
        }

        @Test
        void longArgumentsAreShortened() {
            MethodWrapper<Object, Integer> sum =
                    new MethodWrapper<>(w, "sum", int.class, new Class<?>[]{List.class}, "public");
            List<Integer> values = java.util.stream.IntStream.range(0, 100).boxed().toList();
            assertThat(failure(() -> sum.invoke(values))).contains("Input:    [0, 1, 2, 3, 4, 5, 6, 7, 8, 9, … (+90)]");
        }

        @Test
        void constructorExceptionShowsTheArguments() {
            ConstructorWrapper<Object> ctor = new ConstructorWrapper<>(w, new Class<?>[]{String[].class}, "public");
            assertThat(failure(() -> ctor.invoke((Object) new String[0]))).contains("Input:    []");
            assertThat(ctor.invoke((Object) new String[]{"x"})).isInstanceOf(Fixtures.Calls.class);
        }

        @Test
        void spreadObjectArrayIsReportedWithHint() {
            assertThat(count.invoke((Object) new String[]{"a", "b"})).isEqualTo(2);
            assertThatThrownBy(() -> count.invoke((Object[]) new String[]{"a", "b"}))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("expects 1 argument(s), but 2 were passed")
                    .hasMessageContaining("(Object) array");
            assertThatThrownBy(() -> count.invoke((Object[]) new String[]{"a"}))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("(Object) array");
            ConstructorWrapper<Object> ctor = new ConstructorWrapper<>(w, new Class<?>[]{String[].class}, "public");
            assertThatThrownBy(() -> ctor.invoke((Object[]) new String[]{"a", "b"}))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("(Object) array");
            assertThatThrownBy(() -> w.getObj(true, ctor, (Object[]) new String[]{"a", "b"}))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void primitiveArrayIsPassedAsOneArgument() {
            MethodWrapper<Object, Integer> countFlags =
                    new MethodWrapper<>(w, "countFlags", int.class, new Class<?>[]{boolean[].class}, "public");
            assertThat(countFlags.invoke(new boolean[]{true, false, true})).isEqualTo(3);
        }

        @Test
        void nullAsOnlyArgumentIsOneNullArgument() {
            MethodWrapper<Object, Boolean> isNull =
                    new MethodWrapper<>(w, "isNull", boolean.class, new Class<?>[]{String.class}, "public");
            assertThat(isNull.invoke((Object[]) null)).isTrue();
            assertThat(isNull.invoke((Object) null)).isTrue();
        }

        @Test
        void expectingExceptionChecksArgumentsAndShowsThem() {
            assertThatThrownBy(() -> count.invokeExpectingException(IllegalStateException.class, null,
                    (Object[]) new String[]{"a", "b"}))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("(Object) array");
            ConstructorWrapper<Object> ctor = new ConstructorWrapper<>(w, new Class<?>[]{String[].class}, "public");
            // the IllegalArgumentException of the check is not mistaken for the expected one of the student code
            assertThatThrownBy(() -> ctor.invokeExpectingException(IllegalArgumentException.class,
                    (Object[]) new String[]{"a", "b"}))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("(Object) array");
            assertThat(ctor.invokeExpectingException(IllegalArgumentException.class, (Object) new String[0]))
                    .hasMessage("no names");
            assertThat(failure(() -> divide.invokeExpectingException(ArithmeticException.class, null, 4.0, 2.0)))
                    .isEqualTo("""
                            Method:   divide(double, double)
                            Input:    4.00, 2.00
                            Expected: ArithmeticException is thrown
                            Actual:   no exception was thrown""");
        }

        @Test
        void callWithoutArgumentsHasNoArgumentLine() {
            MethodWrapper<Object, Void> boom = new MethodWrapper<>(w, "boom", void.class, "public");
            assertThat(failure(boom::invoke)).contains("IllegalStateException: boom").doesNotContain("Arguments:");
        }

        @Test
        void abstractClassChecksAndShowsArguments() {
            W<Object> abstr = nested("AbstractWithProtectedCtor", "public static abstract");
            ConstructorWrapper<Object> c = new ConstructorWrapper<>(abstr, new Class<?>[]{String.class}, "protected");
            assertThatThrownBy(() -> c.invoke("a", "b"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("expects 1 argument(s), but 2 were passed");
            assertThatThrownBy(() -> abstr.getDynamicSubclassObj(c))
                    .isInstanceOf(IllegalArgumentException.class);

            W<Object> throwing = nested("AbstractThrowingCtor", "public static abstract");
            ConstructorWrapper<Object> broken = new ConstructorWrapper<>(throwing, new Class<?>[]{String.class}, "protected");
            assertThat(failure(() -> broken.invoke("Toyota")))
                    .contains("Actual:   IllegalStateException: broken Toyota")
                    .contains("Input:    \"Toyota\"");
        }

        @Test
        void emptyContextAddsNoLineAndIsOnlyEvaluatedOnFailure() {
            assertThat(failure(() -> divide.invokeWithContext(() -> null, 1.0, 0.0))).doesNotContain("Scenario:");
            assertThat(failure(() -> divide.invokeWithContext(() -> "  ", 1.0, 0.0))).doesNotContain("Scenario:");
            assertThat(failure(() -> divide.invokeWithContext(null, 1.0, 0.0))).doesNotContain("Scenario:");
            java.util.concurrent.atomic.AtomicInteger evaluated = new java.util.concurrent.atomic.AtomicInteger();
            divide.invokeWithContext(() -> "n=" + evaluated.incrementAndGet(), 1.0, 2.0);
            divide.invokeOnSpecificObjectWithContext(() -> "n=" + evaluated.incrementAndGet(), w.newObj(), 1.0, 2.0);
            assertThat(evaluated).hasValue(0);
        }

        @Test
        void wrongArgumentCountIsReported() {
            assertThatThrownBy(() -> divide.invoke(1.0))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("expects 2 argument(s), but 1 were passed")
                    .hasMessageNotContaining("(Object) array");
        }
    }

    @Nested
    class ValueFormatting { // #25, #27

        static class BadToString {
            @Override
            public String toString() {
                throw new IllegalStateException("broken");
            }
        }

        @Test
        void simpleValues() {
            assertThat(Invocations.describeValue(null)).isEqualTo("null");
            assertThat(Invocations.describeValue("text")).isEqualTo("\"text\"");
            assertThat(Invocations.describeValue('c')).isEqualTo("'c'");
            assertThat(Invocations.describeValue(2.5)).isEqualTo("2.50");
            assertThat(Invocations.describeValue(2.5f)).isEqualTo("2.50");
            assertThat(Invocations.describeValue(7)).isEqualTo("7");
            assertThat(Invocations.describeValue(Fixtures.Color.RED)).isEqualTo("RED");
        }

        @Test
        void arraysAndCollectionsShowTheirElements() {
            assertThat(Invocations.describeValue(new int[]{1, 2, 3})).isEqualTo("[1, 2, 3]");
            assertThat(Invocations.describeValue(new boolean[]{true, false})).isEqualTo("[true, false]");
            assertThat(Invocations.describeValue(new String[][]{{"a"}, {"b", "c"}})).isEqualTo("[[\"a\"], [\"b\", \"c\"]]");
            assertThat(Invocations.describeValue(new int[0])).isEqualTo("[]");
            assertThat(Invocations.describeValue(List.of('x', 'y'))).isEqualTo("['x', 'y']");
            assertThat(Invocations.describeValue(new int[12])).isEqualTo("[0, 0, 0, 0, 0, 0, 0, 0, 0, 0, … (+2)]");
        }

        @Test
        void longValuesAreCut() {
            String described = Invocations.describeValue("x".repeat(100));
            assertThat(described).isEqualTo("\"" + "x".repeat(Invocations.MAX_VALUE_LENGTH) + "…\"");
        }

        @Test
        void deepAndSelfContainingArraysEnd() {
            Object[] self = new Object[1];
            self[0] = self;
            assertThat(Invocations.describeValue(self)).isEqualTo("[[[[…]]]]");
            assertThat(Invocations.describeValue(new int[][][][][]{{{{{1}}}}})).isEqualTo("[[[[…]]]]");
        }

        @Test
        void failingToStringIsReported() {
            assertThat(Invocations.describeValue(new BadToString()))
                    .isEqualTo("BadToString (toString() threw IllegalStateException)");
        }

        @Test
        void argumentListIsJoinedAndCut() {
            assertThat(Invocations.describeArguments(null)).isEqualTo("null");
            assertThat(Invocations.describeArguments(new Object[0])).isEmpty();
            assertThat(Invocations.describeArguments(new Object[]{2.5, "a", null})).isEqualTo("2.50, \"a\", null");
            Object[] many = new Object[10];
            java.util.Arrays.fill(many, "y".repeat(50));
            String described = Invocations.describeArguments(many);
            assertThat(described).hasSize(Invocations.MAX_ARGUMENTS_LENGTH + 1).endsWith("…");
        }
    }

    @Nested
    class Constructors {

        final W<Object> ctors = nested("Ctors", "public static");

        @Test
        void protectedConstructorIsFound() { // review H5
            ConstructorWrapper<Object> c = new ConstructorWrapper<>(ctors, new Class<?>[]{String.class}, "protected");
            assertThat(c.getOverallExistence()).isEqualTo(EXACT);
            assertThat(((Fixtures.Ctors) c.invoke("protected")).how).isEqualTo("protected");
        }

        @Test
        void wrongConstructorModifierIsReported() { // #8
            ConstructorWrapper<Object> c = new ConstructorWrapper<>(ctors, new Class<?>[0], "public");
            assertThat(c.getOverallExistence()).isEqualTo(DEVIATES);
        }

        @Test
        void missingConstructorIsMissingNotThrown() { // review H5
            ConstructorWrapper<Object> c = new ConstructorWrapper<>(ctors, new Class<?>[]{boolean.class}, "public");
            assertThatCode(c::getOverallExistence).doesNotThrowAnyException();
            assertThat(c.getOverallExistence()).isEqualTo(MISSING);
        }

        @Test
        void wrapperParameterConstructorDeviates() { // review H5 (former dead fallback)
            ConstructorWrapper<Object> c = new ConstructorWrapper<>(ctors, new Class<?>[]{int.class}, "public");
            assertThat(c.getOverallExistence()).isEqualTo(DEVIATES);
            assertThat(((Fixtures.Ctors) c.invoke(7)).how).isEqualTo("integer 7");
        }

        @Test
        void abstractClassWithProtectedConstructorIsInstantiated() { // H8 / #9
            W<Object> abstr = nested("AbstractWithProtectedCtor", "public static abstract");
            ConstructorWrapper<Object> c = new ConstructorWrapper<>(abstr, new Class<?>[]{String.class}, "protected");
            Object instance = c.invoke("Toyota");
            assertThat(instance).isInstanceOf(Fixtures.AbstractWithProtectedCtor.class);
            assertThat(((Fixtures.AbstractWithProtectedCtor) instance).getName()).isEqualTo("Toyota");
        }

        @Test
        void interfaceInstanceCanBeCreated() { // #9 part 2
            W<Object> drivable = nested("Drivable", "public static abstract interface");
            assertThat(drivable.getObj(true)).isInstanceOf(Fixtures.Drivable.class);
        }
    }

    @Nested
    class Structural {

        @Test
        void oneTestPerMemberWithUniqueNamesInOrder() { // #15.3
            Members members = new Members();
            Members.SpeedWrapper w = members.new SpeedWrapper();
            List<DynamicTest> tests = StructuralLevenshtein.structuralTestFactory(StructuralLevenshtein.DetailLevel.ONE_PER_MEMBER, w, w);
            assertThat(tests).extracting(DynamicTest::getDisplayName).doesNotHaveDuplicates()
                    .startsWith("Class[Fixtures$Typos]")
                    .anyMatch(n -> n.startsWith("Method[Fixtures$Typos.public int getMaxSpeed()"))
                    .anyMatch(n -> n.endsWith(" #2"));
            assertThat(tests).extracting(DynamicTest::getDisplayName).noneMatch(n -> n.startsWith("structStructural"));
        }

        @Test
        void emptyGroupsGetNoTest() { // #26
            W<Object> noMembers = nested("NoOwnMembers", "public static");
            assertThat(StructuralLevenshtein.structuralTestFactory(StructuralLevenshtein.DetailLevel.ONE_PER_MEMBER_CATEGORY, noMembers))
                    .extracting(DynamicTest::getDisplayName)
                    .containsExactly("Class[Fixtures$NoOwnMembers]");
            assertThat(StructuralLevenshtein.structuralTestFactory(StructuralLevenshtein.DetailLevel.ONE_PER_MEMBER_CATEGORY, false, noMembers))
                    .extracting(DynamicTest::getDisplayName)
                    .containsExactly("Class[Fixtures$NoOwnMembers]", "Constructors[Fixtures$NoOwnMembers]",
                            "Attributes[Fixtures$NoOwnMembers]", "Methods[Fixtures$NoOwnMembers]");
            assertThat(StructuralLevenshtein.structuralTestFactory(StructuralLevenshtein.DetailLevel.ONE_FOR_EVERYTHING))
                    .isEmpty();
            assertThat(StructuralLevenshtein.structuralTestFactory(StructuralLevenshtein.DetailLevel.ONE_PER_CLASS, noMembers))
                    .extracting(DynamicTest::getDisplayName)
                    .containsExactly("Structural[Fixtures$NoOwnMembers]");
            assertThat(StructuralLevenshtein.structuralTestFactory(StructuralLevenshtein.DetailLevel.ONE_PER_MEMBER, noMembers))
                    .extracting(DynamicTest::getDisplayName)
                    .containsExactly("Class[Fixtures$NoOwnMembers]");
        }

        @Test
        void structuralTemplateReportsMissingMembers() {
            Members members = new Members();
            Members.SpeedWrapper w = members.new SpeedWrapper();
            assertThatThrownBy(() -> StructuralLevenshtein.structuralTestTemplate(w.getMethodWrappers()))
                    .isInstanceOf(AssertionError.class)
                    .hasMessageContaining("getMaxSpeed");
        }
    }

    @Nested
    class UtilsAndSettings {

        @Test
        void percentOfEmptyStringsIsZero() { // #10.2
            assertThat(Utils.levenshteinDistancePercent("", "")).isZero();
            assertThat(Utils.levenshteinDistancePercent("abc", "abd")).isCloseTo(33.33, org.assertj.core.data.Offset.offset(0.01));
        }

        @Test
        void safeCastFailsClearly() { // #15.5
            assertThat(Utils.safeCast(3, double.class)).isEqualTo(3.0);
            assertThat(Utils.safeCast('a', int.class)).isEqualTo(97);
            assertThat(Utils.safeCast(97, char.class)).isEqualTo('a'); // #22
            assertThat(Utils.safeCast(97L, Character.class)).isEqualTo('a');
            assertThatThrownBy(() -> Utils.safeCast("text", Integer.class))
                    .isInstanceOf(AssertionError.class).hasMessageContaining("String");
        }

        @Test
        void languageIsResolvedAtRuntime() { // #14.3
            LevenshteinSettings.setLanguage(LevenshteinSettings.Language.ENGLISH);
            assertThat(Messages.CLASS_MISSING.get()).contains("MISSING");
            LevenshteinSettings.setLanguage(LevenshteinSettings.Language.DEUTSCH);
            assertThat(Messages.CLASS_MISSING.get()).contains("FEHLT");
        }

        @Test
        void thresholdsAreValidated() {
            assertThatThrownBy(() -> LevenshteinSettings.setMethodNameDeviationThreshold(101))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void uncheckedNeverWinsAggregation() { // #14.1
            assertThat(WrapperProperty.Existence.worst(UNCHECKED, EXACT)).isEqualTo(EXACT);
            assertThat(WrapperProperty.Existence.worst(DEVIATES, UNCHECKED)).isEqualTo(DEVIATES);
            assertThat(WrapperProperty.Existence.worst(DEVIATES, MISSING)).isEqualTo(MISSING);
            assertThat(WrapperProperty.Existence.worst(UNCHECKED, UNCHECKED)).isEqualTo(UNCHECKED);
        }
    }

    @Nested
    class ComposedAnnotations {

        @Test
        void levenshteinTestIsAPublicAresTest() {
            assertThat(AnnotationSupport.findAnnotation(PublicProbe.class, Public.class)).isPresent();
            assertThat(AnnotationSupport.findAnnotation(PublicProbe.class, Hidden.class)).isEmpty();
            assertThat(AnnotationSupport.findAnnotation(PublicProbe.class, StrictTimeout.class))
                    .hasValueSatisfying(timeout -> assertThat(timeout.value()).isEqualTo(5));
            assertThat(AnnotationSupport.findAnnotation(PublicProbe.class, MirrorOutput.class)).isPresent();
        }

        @Test
        void hiddenLevenshteinTestIsAHiddenAresTest() {
            assertThat(AnnotationSupport.findAnnotation(HiddenProbe.class, Hidden.class)).isPresent();
            assertThat(AnnotationSupport.findAnnotation(HiddenProbe.class, Public.class)).isEmpty();
            assertThat(AnnotationSupport.findAnnotation(HiddenProbe.class, StrictTimeout.class))
                    .hasValueSatisfying(timeout -> assertThat(timeout.value()).isEqualTo(5));
            assertThat(AnnotationSupport.findAnnotation(HiddenProbe.class, MirrorOutput.class)).isPresent();
        }
    }

    @Nested
    class Mocks { // #30

        private final W<Object> guitarist = nested("Guitarist", "public static");
        private final MethodWrapper<Object, Integer> loudness =
                new MethodWrapper<>(guitarist, "calculateLoudness", int.class, new Class<?>[]{int.class});
        private final MethodWrapper<Object, String> getName = new MethodWrapper<>(guitarist, "getName", String.class, "public");
        private final MethodWrapper<Object, Integer> getFactor = new MethodWrapper<>(guitarist, "getFactor", int.class, "public");
        private final MethodWrapper<Object, Double> calcInt =
                new MethodWrapper<>(guitarist, "calc", double.class, new Class<?>[]{int.class}, "public");
        private final MethodWrapper<Object, Double> calcDouble =
                new MethodWrapper<>(guitarist, "calc", double.class, new Class<?>[]{double.class}, "public");

        private final W<Object> drummer = nested("Drummer", "public static");
        private final MethodWrapper<Object, Integer> drummerLoudness =
                new MethodWrapper<>(drummer, "calculateLoudness", int.class, new Class<?>[]{int.class, int.class}, "public");
        private final MethodWrapper<Object, Void> setGuitarist =
                new MethodWrapper<>(drummer, "setGuitarist", void.class, new Class<?>[]{Fixtures.Guitarist.class}, "public");

        private String failure(Runnable call) {
            LevenshteinSettings.setLanguage(LevenshteinSettings.Language.ENGLISH);
            return org.junit.jupiter.api.Assertions.assertThrows(AssertionError.class, call::run).getMessage();
        }

        @Test
        void noConstructorRuns() {
            Fixtures.Guitarist.constructed = 0;
            Object g = guitarist.newObjWithoutConstructor();
            assertThat(g).isExactlyInstanceOf(Fixtures.Guitarist.class);
            assertThat(getFactor.invokeOnSpecificObject(g)).isZero(); // field initializers do not run either
            Object mocked = guitarist.newObjOverriding(Map.of(getName, args -> "mock"));
            assertThat(mocked).isInstanceOf(Fixtures.Guitarist.class);
            assertThat(getName.invokeOnSpecificObject(mocked)).isEqualTo("mock");
            assertThat(Fixtures.Guitarist.constructed).isZero();
        }

        @Test
        void overrideIsCalledByOtherStudentClass() { // SE01E04, package-private method
            Mock<Object> g = guitarist.mock().override(loudness, args -> (int) args[0] * 7);
            Object d = drummer.newObjWithoutConstructor();
            setGuitarist.invokeOnSpecificObject(d, g.obj());
            assertThat(drummerLoudness.invokeOnSpecificObject(d, 5, 3)).isEqualTo(38);
            g.assertCalled(loudness, 1);
            assertThat(g.calls(loudness)).singleElement().satisfies(args -> assertThat(args).containsExactly(5));
        }

        @Test
        void selfCallIsIntercepted() { // SE01E03
            W<Object> stage = nested("Stage", "public static");
            MethodWrapper<Object, Integer> count = new MethodWrapper<>(stage, "getNumberOfSpotlights", int.class, "public");
            MethodWrapper<Object, Boolean> ready = new MethodWrapper<>(stage, "isShowReady", boolean.class, "public");
            Object s = stage.newObjOverriding(Map.of(count, args -> 3));
            assertThat(ready.invokeOnSpecificObject(s)).isTrue();
            Object t = stage.newObjOverriding(Map.of(count, args -> 1));
            assertThat(ready.invokeOnSpecificObject(t)).isFalse();
        }

        @Test
        void inheritedMethodIsOverridden() {
            W<Object> musician = nested("Musician", "public static");
            W<Object> bassist = new W<>("Fixtures$Bassist", musician, null, "public", "static");
            MethodWrapper<Object, String> instrument = new MethodWrapper<>(musician, "instrument", String.class, "public");
            MethodWrapper<Object, String> greet = new MethodWrapper<>(musician, "greet", String.class, "public");
            Mock<Object> b = bassist.mock().returning(instrument, "bass");
            assertThat(b.obj()).isInstanceOf(Fixtures.Bassist.class);
            assertThat(greet.invokeOnSpecificObject(b.obj())).isEqualTo("I play bass");
            b.assertCalled(greet, 1);
            b.assertCalled(instrument, 1);
        }

        @Test
        void onlyTheMatchingOverloadIsOverridden() {
            Object g = guitarist.newObjOverriding(Map.of(calcInt, args -> 7));
            assertThat(calcInt.invokeOnSpecificObject(g, 1)).isEqualTo(7.0); // Integer result converted to double
            assertThat(calcDouble.invokeOnSpecificObject(g, 1.5)).isEqualTo(3.0);
        }

        @Test
        void methodFoundUnderDeviatingNameIsOverridden() {
            MethodWrapper<Object, Integer> volume = new MethodWrapper<>(guitarist, "calculateVolume", int.class, "public");
            assertThat(volume.getActualName()).isEqualTo("calculateVolumme");
            Object g = guitarist.newObjOverriding(Map.of(volume, args -> 99));
            assertThat(volume.invokeOnSpecificObject(g)).isEqualTo(99);
        }

        @Test
        void argumentsAndResultAreConverted() {
            MethodWrapper<Object, Integer> louder =
                    new MethodWrapper<>(guitarist, "louder", int.class, new Class<?>[]{int.class}, "public");
            Mock<Object> g = guitarist.mock().override(louder, args -> (Integer) args[0] + 1); // student: long louder(long)
            assertThat(louder.invokeOnSpecificObject(g.obj(), 4)).isEqualTo(5);
            assertThat(g.calls(louder).get(0)).containsExactly(4);
        }

        @Test
        void notOverridableIsReportedReadably() {
            MethodWrapper<Object, Integer> tune = new MethodWrapper<>(guitarist, "tune", int.class, "public", "final");
            assertThat(failure(() -> guitarist.mock().override(tune, args -> 1)))
                    .isEqualTo("public final int tune() in class Fixtures$Guitarist: cannot be overridden (final)");
            MethodWrapper<Object, Integer> count = new MethodWrapper<>(guitarist, "count", int.class, "public", "static");
            assertThat(failure(() -> guitarist.mock().override(count, args -> 1))).endsWith("cannot be overridden (static)");
            assertThat(failure(() -> nested("FinalCar", "public static final").mock()))
                    .isEqualTo("Fixtures$FinalCar: cannot be overridden (final)");
            assertThat(failure(() -> nested("Point", "public static final").mock())).endsWith("(record)");
            assertThat(failure(() -> nested("Color", "public static final").mock())).endsWith("(enum)");
            assertThat(failure(() -> nested("Color", "public static final").newObjWithoutConstructor()))
                    .contains("objects of an enum cannot be created");
            assertThat(failure(() -> drummer.mock().override(getName, args -> "x")))
                    .contains("does not belong to class Fixtures$Drummer");
        }

        @Test
        void missingMethodFailsWithTheUsualMessage() {
            MethodWrapper<Object, Integer> ghost = new MethodWrapper<>(guitarist, "playSolo", int.class, "public");
            assertThat(failure(() -> guitarist.mock().override(ghost, args -> 1))).contains("is not implemented as expected");
        }

        @Test
        void assertCalledShowsTheRecordedCalls() {
            Mock<Object> g = guitarist.mock().override(loudness, args -> 0);
            loudness.invokeOnSpecificObject(g.obj(), 5);
            assertThat(failure(() -> g.assertCalled(loudness, 2, () -> "Drummer.calculateLoudness(5, 3)")))
                    .isEqualTo("Drummer.calculateLoudness(5, 3)\n"
                            + "int calculateLoudness(int) in class Fixtures$Guitarist should be called 2 time(s),"
                            + " but was called 1 time(s).\nCalls: (5)");
            assertThat(failure(() -> g.assertNotCalled(loudness))).contains("should be called 0 time(s)");
            g.resetCalls();
            g.assertNotCalled(loudness);
        }

        @Test
        void othersReturnDefaults() {
            Mock<Object> g = guitarist.mock().othersReturnDefaults().returning(calcInt, 1.5);
            assertThat(getName.invokeOnSpecificObject(g.obj())).isNull();
            assertThat(calcDouble.invokeOnSpecificObject(g.obj(), 4.0)).isEqualTo(0.0);
            assertThat(calcInt.invokeOnSpecificObject(g.obj(), 4)).isEqualTo(1.5);
            g.assertCalled(getName, 1);
        }

        @Test
        void eachMockHasItsOwnBehaviourOnOneGeneratedClass() {
            Object a = guitarist.newObjOverriding(Map.of(getName, args -> "a"));
            Object b = guitarist.newObjOverriding(Map.of(getName, args -> "b"));
            assertThat(a.getClass()).isSameAs(b.getClass());
            assertThat(getName.invokeOnSpecificObject(a)).isEqualTo("a");
            assertThat(getName.invokeOnSpecificObject(b)).isEqualTo("b");
        }

        @Test
        void studentExceptionsAreReportedAsUsual() {
            MethodWrapper<Object, Void> fail = new MethodWrapper<>(guitarist, "fail", void.class, "public");
            Object g = guitarist.mock().obj();
            assertThat(failure(() -> fail.invokeOnSpecificObject(g))).contains("IllegalStateException: student bug");
        }

        @Test
        void nullForAPrimitiveResultIsATestError() {
            Object g = guitarist.newObjOverriding(Map.of(getFactor, args -> null));
            // thrown inside the call, so reported like any other exception of the call, with the reason
            assertThat(failure(() -> getFactor.invokeOnSpecificObject(g)))
                    .contains("IllegalStateException: The override of public int getFactor() returned null,"
                            + " but the method returns int.");
        }

        @Test
        void studentMethodRunsOnTheCloneThatWasCalled() { // review #1
            W<Object> counter = nested("Counter", "public static");
            MethodWrapper<Object, Integer> get = new MethodWrapper<>(counter, "get", int.class, "public");
            MethodWrapper<Object, Fixtures.Counter> copy = new MethodWrapper<>(counter, "copy", Fixtures.Counter.class, "public");
            AttributeWrapper<Object, Integer> value = new AttributeWrapper<>(counter, "value", int.class, "private");
            for (boolean cloneFirst : new boolean[]{true, false}) {
                Mock<Object> mock = counter.mock();
                value.setValue(mock.obj(), 1);
                Object clone = copy.invokeOnSpecificObject(mock.obj());
                value.setValue(clone, 99);
                if (cloneFirst) {
                    assertThat(get.invokeOnSpecificObject(clone)).isEqualTo(99);
                    assertThat(get.invokeOnSpecificObject(mock.obj())).isEqualTo(1);
                }
                else {
                    assertThat(get.invokeOnSpecificObject(mock.obj())).isEqualTo(1);
                    assertThat(get.invokeOnSpecificObject(clone)).isEqualTo(99);
                }
                mock.assertCalled(get, 2); // the clone shares the recording
            }
        }

        @Test
        void recordingKeepsTheFirstCallsAndCountsAll() { // review #3
            Mock<Object> g = guitarist.mock();
            for (int i = 0; i < Mock.MAX_RECORDED_CALLS + 500; i++) {
                getName.invokeOnSpecificObject(g.obj());
            }
            assertThat(g.callCount(getName)).isEqualTo(Mock.MAX_RECORDED_CALLS + 500L);
            assertThat(g.calls(getName)).hasSize(Mock.MAX_RECORDED_CALLS);
            assertThat(failure(() -> g.assertCalled(getName, 1)))
                    .contains("but was called 1500 time(s)")
                    .endsWith("(), … (+1490)");
        }

        @Test
        void callChecksRejectMethodsThatCannotBeIntercepted() { // review #4
            MethodWrapper<Object, Integer> tune = new MethodWrapper<>(guitarist, "tune", int.class, "public", "final");
            MethodWrapper<Object, Integer> count = new MethodWrapper<>(guitarist, "count", int.class, "public", "static");
            Mock<Object> g = guitarist.mock();
            assertThat(failure(() -> g.assertNotCalled(tune))).endsWith("cannot be overridden (final)");
            assertThat(failure(() -> g.assertCalled(count, 1))).endsWith("cannot be overridden (static)");
            assertThat(failure(() -> g.calls(tune))).endsWith("cannot be overridden (final)");
            assertThat(failure(() -> g.callCount(drummerLoudness))).contains("does not belong to class Fixtures$Guitarist");
        }

        @Test
        void recordedArraysAreCopies() { // review #5
            MethodWrapper<Object, Integer> sum =
                    new MethodWrapper<>(guitarist, "sumAndClear", int.class, new Class<?>[]{int[].class}, "public");
            Mock<Object> g = guitarist.mock();
            int[] values = {3, 1, 2};
            assertThat(sum.invokeOnSpecificObject(g.obj(), (Object) values)).isEqualTo(6);
            assertThat(values).containsExactly(0, 0, 0);
            assertThat((int[]) g.calls(sum).get(0)[0]).containsExactly(3, 1, 2);
        }

        @Test
        void fullMockKeepsIdentityForEqualsHashCodeAndToString() { // review #8
            W<Object> eq = nested("Eq", "public static");
            Object o = eq.mock().othersReturnDefaults().obj();
            Object other = eq.mock().othersReturnDefaults().obj();
            assertThat(o.equals(o)).isTrue();
            assertThat(o.equals(other)).isFalse();
            assertThat(o.hashCode()).isEqualTo(System.identityHashCode(o));
            assertThat(o.toString()).startsWith("Eq@");
            assertThat(new java.util.ArrayList<>(List.of(o))).contains(o);
            MethodWrapper<Object, Boolean> equals =
                    new MethodWrapper<>(eq, "equals", boolean.class, new Class<?>[]{Object.class}, "public");
            Object alwaysEqual = eq.mock().othersReturnDefaults().returning(equals, true).obj();
            assertThat(alwaysEqual.equals("anything")).isTrue();
            assertThat(eq.mock().obj().equals(other)).isTrue(); // partial mock: the student's equals
        }

        @Test
        void failingStaticInitializerIsReportedReadably() { // review #9
            W<Object> broken = nested("BrokenStaticInit", "public static");
            assertThat(failure(() -> broken.mock().obj()))
                    .contains("Creating instances of a subclass of Fixtures$BrokenStaticInit failed")
                    .contains("ExceptionInInitializerError: IllegalStateException: broken static");
            assertThat(failure(() -> broken.mock().obj()))
                    .contains("Creating instances of a subclass of Fixtures$BrokenStaticInit failed");
        }

        @Test
        void withoutConstructorForFinalAbstractAndInterface() {
            W<Object> finalCar = nested("FinalCar", "public static final");
            Object car = finalCar.newObjWithoutConstructor();
            assertThat(car).isExactlyInstanceOf(Fixtures.FinalCar.class);
            assertThat(new MethodWrapper<>(finalCar, "getBrand", String.class, "public").invokeOnSpecificObject(car)).isNull();

            W<Object> abstr = nested("AbstractWithProtectedCtor", "public static abstract");
            MethodWrapper<Object, String> name = new MethodWrapper<>(abstr, "getName", String.class, "public");
            MethodWrapper<Object, Double> cost = new MethodWrapper<>(abstr, "cost", double.class, "public", "abstract");
            Object a = abstr.newObjWithoutConstructor();
            assertThat(a).isInstanceOf(Fixtures.AbstractWithProtectedCtor.class);
            assertThat(name.invokeOnSpecificObject(a)).isNull();
            assertThat(failure(() -> cost.invokeOnSpecificObject(a)))
                    .contains("The abstract method cost in class Fixtures$AbstractWithProtectedCtor was called,"
                            + " but the test does not override it.");
            Object withCost = abstr.newObjOverriding(Map.of(cost, args -> 3.5));
            assertThat(cost.invokeOnSpecificObject(withCost)).isEqualTo(3.5);

            W<Object> drivable = nested("Drivable", "public static abstract interface");
            assertThat(drivable.newObjWithoutConstructor()).isInstanceOf(Fixtures.Drivable.class);
        }
    }

    @Nested
    class ReviewFindings { // code review of PR #31: shared invocation code

        private final W<Object> calls = nested("Calls", "public static");
        private final MethodWrapper<Object, Void> take =
                new MethodWrapper<>(calls, "take", void.class, new Class<?>[]{Object.class}, "public");

        private String failure(Runnable call) {
            LevenshteinSettings.setLanguage(LevenshteinSettings.Language.ENGLISH);
            return org.junit.jupiter.api.Assertions.assertThrows(AssertionError.class, call::run).getMessage();
        }

        @Test
        void recursiveToStringDoesNotHideTheFailure() { // #2
            assertThat(failure(() -> take.invoke(new Fixtures.SelfRef())))
                    .contains("IllegalStateException: taken")
                    .contains("Input:    SelfRef (toString() threw StackOverflowError)")
                    .doesNotContain("Hint:"); // the student method threw no StackOverflowError
        }

        @Test
        void securityViolationInToStringIsNotSwallowed() { // #7
            assertThatThrownBy(() -> take.invoke(new Fixtures.ForbiddenToString()))
                    .isInstanceOf(SecurityException.class)
                    .hasMessage("forbidden by the sandbox");
            assertThatThrownBy(() -> Invocations.describeValue(List.of(new Fixtures.ForbiddenToString())))
                    .isInstanceOf(SecurityException.class);
        }

        @Test
        void argumentsAreShownAsPassed() { // #5
            MethodWrapper<Object, Void> sort =
                    new MethodWrapper<>(calls, "sortAndFail", void.class, new Class<?>[]{int[].class}, "public");
            assertThat(failure(() -> sort.invoke((Object) new int[]{3, 1, 2}))).contains("Input:    [3, 1, 2]");
            List<Integer> values = java.util.stream.IntStream.range(0, 100).boxed().toList();
            assertThat(Invocations.describeArguments(Invocations.snapshotArguments(new Object[]{values})))
                    .isEqualTo("[0, 1, 2, 3, 4, 5, 6, 7, 8, 9, … (+90)]");
        }

        @Test
        void failureInsideTheCallKeepsContextAndArguments() { // #10
            W<Object> abstr = nested("AbstractCaller", "public static abstract");
            MethodWrapper<Object, Double> times =
                    new MethodWrapper<>(abstr, "times", double.class, new Class<?>[]{int.class}, "public");
            Object obj = abstr.newObjWithoutConstructor();
            assertThat(failure(() -> times.invokeOnSpecificObjectWithContext(() -> "Scenario 3", obj, 4)))
                    .startsWith("Scenario 3\nThe abstract method cost in class Fixtures$AbstractCaller was called")
                    .endsWith("Arguments: (4)");
        }

        @Test
        void nullAsArgumentArrayInGetObj() { // #11
            W<Object> holder = nested("Holder", "public static");
            ConstructorWrapper<Object> ctor = new ConstructorWrapper<>(holder, new Class<?>[]{String.class}, "public");
            assertThat(((Fixtures.Holder) ctor.invoke((Object[]) null)).value).isNull();
            assertThat(((Fixtures.Holder) holder.getObj(true, ctor, (Object[]) null)).value).isNull();
        }

        @Test
        void numericArgumentsAreConvertedToTheStudentsType() { // #6
            W<Object> fp = nested("FloatParams", "public static");
            MethodWrapper<Object, Double> half =
                    new MethodWrapper<>(fp, "half", double.class, new Class<?>[]{double.class}, "public");
            ConstructorWrapper<Object> ctor = new ConstructorWrapper<>(fp, new Class<?>[]{double.class}, "public");
            assertThat(half.getOverallExistence()).isEqualTo(DEVIATES);
            Object obj = ctor.invoke(2.5);
            assertThat(((Fixtures.FloatParams) obj).price).isEqualTo(2.5f);
            assertThat(half.invokeOnSpecificObject(obj, 2.5)).isEqualTo(1.25);
            assertThat(half.invokeOnSpecificObject(obj, 3)).isEqualTo(1.5);
            assertThat(failure(() -> half.invokeOnSpecificObject(obj, 1e300)))
                    .isEqualTo("Argument 1 (1.0E300) does not fit the parameter type float of public float half(float).");
        }

        @Test
        void narrowingConversionsFailInsteadOfChangingTheValue() { // #12
            assertThat(Utils.safeCast(2.0, int.class)).isEqualTo(2);
            assertThat(Utils.safeCast(0.1, float.class)).isEqualTo(0.1f);
            assertThat(Utils.safeCast(65, char.class)).isEqualTo('A');
            assertThat(Utils.safeCast(5L, int.class)).isEqualTo(5);
            assertThat(failure(() -> Utils.safeCast(2.5, int.class)))
                    .isEqualTo("The value 2.5 of type Double cannot be converted to the expected type int.");
            assertThat(failure(() -> Utils.safeCast(1e40, float.class))).contains("expected type float");
            assertThat(failure(() -> Utils.safeCast(Long.MAX_VALUE, int.class))).contains("expected type int");

            W<Object> narrow = nested("Narrow", "public static");
            Object obj = narrow.newObj();
            AttributeWrapper<Object, Double> count = new AttributeWrapper<>(narrow, "count", double.class, "private");
            AttributeWrapper<Object, Integer> tiny = new AttributeWrapper<>(narrow, "tiny", int.class, "private");
            AttributeWrapper<Object, Integer> letter = new AttributeWrapper<>(narrow, "letter", int.class, "private");
            count.setValue(obj, 3.0);
            assertThat(((Fixtures.Narrow) obj).getCount()).isEqualTo(3);
            assertThat(failure(() -> count.setValue(obj, 2.5))).contains("2.5").contains("int");
            tiny.setValue(obj, -3);
            assertThat(failure(() -> tiny.setValue(obj, 200))).contains("200").contains("byte");
            letter.setValue(obj, 65);
            assertThat(((Fixtures.Narrow) obj).getLetter()).isEqualTo('A');
            assertThat(failure(() -> letter.setValue(obj, -1))).contains("char");

            // own sample values that do not fit the student's int: the value is blamed, not the getter
            MethodWrapper<Object, Double> getCount = new MethodWrapper<>(narrow, "getCount", double.class, "public");
            assertThat(failure(() -> narrow.testGettersSetters(count, List.of(2.5, 3.0), getCount)))
                    .contains("2.5")
                    .doesNotContain("getCount() returns 2");
        }
    }
}
