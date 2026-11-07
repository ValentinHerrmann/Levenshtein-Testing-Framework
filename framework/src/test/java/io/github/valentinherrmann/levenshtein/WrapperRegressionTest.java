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
                    .hasMessageContaining("ArithmeticException")
                    .hasCauseInstanceOf(ArithmeticException.class);
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
            assertThat(message)
                    .contains("Getter/setter test for attribute count in class Fixtures$GettersSetters")
                    .contains("✅ setter -> attribute: passed")
                    .contains("❌ attribute -> getter: attribute count has the value 7, but getCount() returns 42")
                    .contains("❌ setter -> getter: after setCount(7), getCount() yields 42, expected: 7");
        }

        @Test
        void setterIgnoringItsArgumentFails() {
            String message = failure(() -> w.testGettersSetters(attribute("name", String.class),
                    getter("getName", String.class), setter("setName", String.class)));
            assertThat(message)
                    .contains("❌ setter -> attribute: after setName(\"Alpha\"), attribute name yields null, expected: \"Alpha\"")
                    .contains("✅ attribute -> getter: passed")
                    .contains("❌ setter -> getter");
        }

        @Test
        void getterOfAnotherAttributeFails() {
            String message = failure(() -> w.testGettersSetters(attribute("other", int.class), getter("getWrong", int.class)));
            assertThat(message)
                    .contains("➖ setter -> attribute: not tested")
                    .contains("❌ attribute -> getter")
                    .contains("➖ setter -> getter: not tested");
        }

        @Test
        void onlyGivenSubtestsRun() {
            String message = failure(() -> w.testGettersSetters(attribute("name", String.class), setter("setName", String.class)));
            assertThat(message)
                    .contains("❌ setter -> attribute")
                    .contains("➖ attribute -> getter: not tested")
                    .contains("➖ setter -> getter: not tested");
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
            assertThat(message)
                    .contains("❌ setter -> attribute: setTag(null) must reject the invalid value")
                    .contains("✅ attribute -> getter: passed")
                    .contains("❌ setter -> getter: setTag(null) must reject the invalid value");

            String negative = failure(() -> w.testGettersSetters(attribute("price", double.class), (Double p) -> p >= 0,
                    setter("setPrice", double.class)));
            assertThat(negative).contains("setPrice(-1.25) must reject the invalid value");
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
                    .contains("ArithmeticException: division by zero")
                    .contains("Arguments: (2.5, 0.0)");
        }

        @Test
        void contextIsPutInFrontOfTheMessage() {
            assertThat(failure(() -> divide.invokeWithContext(() -> "Scenario 3, seed 42", 2.5, 0.0)))
                    .startsWith("Scenario 3, seed 42\n")
                    .contains("Arguments: (2.5, 0.0)");
            Object obj = w.newObj();
            assertThat(failure(() -> divide.invokeOnSpecificObjectWithContext(() -> "case 7", obj, 1.0, 0.0)))
                    .startsWith("case 7\n");
            assertThat(failure(() -> divide.invokeWithContext(() -> { throw new IllegalStateException(); }, 1.0, 0.0)))
                    .startsWith("Calling method");
            assertThat(divide.invokeWithContext(() -> "unused", 3.0, 2.0)).isEqualTo(1.5);
        }

        @Test
        void longArgumentsAreShortened() {
            MethodWrapper<Object, Integer> sum =
                    new MethodWrapper<>(w, "sum", int.class, new Class<?>[]{List.class}, "public");
            List<Integer> values = java.util.stream.IntStream.range(0, 100).boxed().toList();
            assertThat(failure(() -> sum.invoke(values))).contains("Arguments: ([0, 1, 2, 3, 4, 5, 6, 7, 8, 9, … (+90)])");
        }

        @Test
        void constructorExceptionShowsTheArguments() {
            ConstructorWrapper<Object> ctor = new ConstructorWrapper<>(w, new Class<?>[]{String[].class}, "public");
            assertThat(failure(() -> ctor.invoke((Object) new String[0]))).contains("Arguments: ([])");
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
        void wrongArgumentCountIsReported() {
            assertThatThrownBy(() -> divide.invoke(1.0))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("expects 2 argument(s), but 1 were passed")
                    .hasMessageNotContaining("(Object) array");
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
}
