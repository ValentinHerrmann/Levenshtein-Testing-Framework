package io.github.valentinherrmann.levenshtein;

import static io.github.valentinherrmann.levenshtein.WrapperProperty.Existence.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.valentinherrmann.levenshtein.fixtures.Fixtures;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

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
}
