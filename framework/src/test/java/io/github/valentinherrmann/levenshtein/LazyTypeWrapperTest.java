package io.github.valentinherrmann.levenshtein;

import static io.github.valentinherrmann.levenshtein.WrapperProperty.Existence.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.function.Supplier;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests of student classes as attribute, return and parameter types (issue #33): the types are given as
 * {@link ClassWrapper}s (directly, via {@link ClassWrapper#reference(String, String)} or a {@link Supplier}) and are
 * only looked up when the member is checked.
 */
class LazyTypeWrapperTest {

    private static final String BAND = "io.github.valentinherrmann.levenshtein.fixtures.band";
    private static final String MISNAMED = "io.github.valentinherrmann.levenshtein.fixtures.misnamed";

    @AfterEach
    void resetSettings() {
        LevenshteinSettings.reset();
    }

    /** Wrapper of {@code Guitarist}, its type {@code Drummer} given as a wrapper or as a supplier of one. */
    static class GuitaristWrapper<T> extends ClassWrapper<T> {
        final AttributeWrapper<T, ?> drummer;
        final AttributeWrapper<T, ?> bassist;
        final MethodWrapper<T, ?> getDrummer;
        final MethodWrapper<T, Void> setDrummer;
        final MethodWrapper<T, ?> getBassist;
        final MethodWrapper<T, Void> setBassist;
        final ConstructorWrapper<T> constructor;

        GuitaristWrapper(String pkg, ClassWrapper<?> drummerType) {
            super("Guitarist", pkg, "public");
            ClassWrapper<?> bassistType = ClassWrapper.reference("Bassist", pkg);
            drummer = new AttributeWrapper<>(this, "drummer", drummerType, "private");
            bassist = new AttributeWrapper<>(this, "bassist", bassistType, "private");
            getDrummer = new MethodWrapper<>(this, "getDrummer", drummerType, "public");
            setDrummer = new MethodWrapper<>(this, "setDrummer", void.class, new Object[]{drummerType}, "public");
            getBassist = new MethodWrapper<>(this, "getBassist", bassistType, "public");
            setBassist = new MethodWrapper<>(this, "setBassist", void.class, new Object[]{bassistType}, "public");
            constructor = new ConstructorWrapper<>(this, new Object[]{double.class, drummerType}, "public");
        }

        GuitaristWrapper(String pkg, Supplier<ClassWrapper<?>> drummerType) {
            super("Guitarist", pkg, "public");
            drummer = new AttributeWrapper<>(this, "drummer", drummerType, "private");
            getDrummer = new MethodWrapper<>(this, "getDrummer", drummerType, "public");
            setDrummer = new MethodWrapper<>(this, "setDrummer", void.class, new Object[]{drummerType}, "public");
            constructor = new ConstructorWrapper<>(this, new Object[]{double.class, drummerType}, "public");
            bassist = null;
            getBassist = null;
            setBassist = null;
        }

        @Override
        public Object getObj(boolean forceNew) {
            return getObj(forceNew, constructor, 1.0, null);
        }
    }

    /** Wrapper of {@code Drummer}, its type {@code Guitarist} given as a wrapper or as a supplier of one. */
    static class DrummerWrapper<T> extends ClassWrapper<T> {
        final AttributeWrapper<T, ?> guitarist;
        final MethodWrapper<T, ?> getGuitarist;
        final MethodWrapper<T, Void> setGuitarist;
        final ConstructorWrapper<T> constructor;

        DrummerWrapper(String pkg, ClassWrapper<?> guitaristType) {
            super("Drummer", pkg, "public");
            guitarist = new AttributeWrapper<>(this, "guitarist", guitaristType, "private");
            getGuitarist = new MethodWrapper<>(this, "getGuitarist", guitaristType, new Object[0], "public");
            setGuitarist = new MethodWrapper<>(this, "setGuitarist", void.class, new Object[]{guitaristType}, "public");
            constructor = new ConstructorWrapper<>(this, new Object[]{double.class, guitaristType}, "public");
        }

        DrummerWrapper(String pkg, Supplier<ClassWrapper<?>> guitaristType) {
            super("Drummer", pkg, "public");
            guitarist = new AttributeWrapper<>(this, "guitarist", guitaristType, "private");
            getGuitarist = new MethodWrapper<>(this, "getGuitarist", guitaristType, new Object[0], "public");
            setGuitarist = new MethodWrapper<>(this, "setGuitarist", void.class, new Object[]{guitaristType}, "public");
            constructor = new ConstructorWrapper<>(this, new Object[]{double.class, guitaristType}, "public");
        }

        @Override
        public Object getObj(boolean forceNew) {
            return getObj(forceNew, null);
        }
    }

    /** Holds wrappers that refer to each other via suppliers. */
    static class Band {
        GuitaristWrapper<Object> guitarist;
        DrummerWrapper<Object> drummer;
    }

    private static void assertAllExact(GuitaristWrapper<?> g) {
        assertThat(List.of(g.drummer, g.getDrummer, g.setDrummer, g.constructor))
                .allSatisfy(w -> assertThat(w.getOverallExistence()).as(w.expectedToString()).isEqualTo(EXACT));
    }

    private static void assertAllExact(DrummerWrapper<?> d) {
        assertThat(List.of(d.guitarist, d.getGuitarist, d.setGuitarist, d.constructor))
                .allSatisfy(w -> assertThat(w.getOverallExistence()).as(w.expectedToString()).isEqualTo(EXACT));
    }

    @Nested
    class MutualReferences {

        @Test
        void viaReferencesGuitaristCheckedFirst() {
            GuitaristWrapper<Object> g = new GuitaristWrapper<>(BAND, ClassWrapper.reference("Drummer", BAND));
            DrummerWrapper<Object> d = new DrummerWrapper<>(BAND, ClassWrapper.reference("Guitarist", BAND));
            assertAllExact(g);
            assertAllExact(d);
        }

        @Test
        void viaReferencesDrummerCheckedFirst() {
            DrummerWrapper<Object> d = new DrummerWrapper<>(BAND, ClassWrapper.reference("Guitarist", BAND));
            GuitaristWrapper<Object> g = new GuitaristWrapper<>(BAND, ClassWrapper.reference("Drummer", BAND));
            assertAllExact(d);
            assertAllExact(g);
        }

        @Test
        void viaSuppliersInEitherOrder() {
            Band band = new Band();
            band.guitarist = new GuitaristWrapper<>(BAND, () -> band.drummer);
            band.drummer = new DrummerWrapper<>(BAND, () -> band.guitarist);
            assertAllExact(band.drummer);
            assertAllExact(band.guitarist);

            Band reversed = new Band();
            reversed.drummer = new DrummerWrapper<>(BAND, () -> reversed.guitarist);
            reversed.guitarist = new GuitaristWrapper<>(BAND, () -> reversed.drummer);
            assertAllExact(reversed.guitarist);
            assertAllExact(reversed.drummer);
        }

        @Test
        void fullWrapperAsTypeOfTheOther() {
            Band band = new Band();
            band.guitarist = new GuitaristWrapper<>(BAND, () -> band.drummer);
            band.drummer = new DrummerWrapper<>(BAND, band.guitarist);
            assertAllExact(band.drummer);
            assertAllExact(band.guitarist);
        }

        @Test
        void membersCanBeUsed() {
            Band band = new Band();
            band.guitarist = new GuitaristWrapper<>(BAND, () -> band.drummer);
            band.drummer = new DrummerWrapper<>(BAND, () -> band.guitarist);
            Object guitarist = band.guitarist.newObj();
            Object drummer = band.drummer.constructor.invoke(120.0, guitarist);
            band.guitarist.setDrummer.invokeOnSpecificObject(guitarist, drummer);
            assertThat(band.guitarist.getDrummer.invokeOnSpecificObject(guitarist)).isSameAs(drummer);
            assertThat(band.guitarist.drummer.getValue(guitarist)).isSameAs(drummer);
            assertThat(band.drummer.getGuitarist.invokeOnSpecificObject(drummer)).isSameAs(guitarist);
        }

        @Test
        void typesAreResolvedForTheGetters() throws Exception {
            Class<?> drummerClass = Class.forName(BAND + ".Drummer");
            GuitaristWrapper<Object> g = new GuitaristWrapper<>(BAND, ClassWrapper.reference("Drummer", BAND));
            assertThat(g.drummer.getExpectedType()).isEqualTo(drummerClass);
            assertThat(g.getDrummer.getExpectedReturnType()).isEqualTo(drummerClass);
            assertThat(g.setDrummer.getParamTypes()).containsExactly(drummerClass);
            assertThat(g.constructor.getParamTypes()).containsExactly(double.class, drummerClass);
        }

        @Test
        void messagesUseTheExpectedSimpleName() {
            GuitaristWrapper<Object> g = new GuitaristWrapper<>(BAND, ClassWrapper.reference("Drummer", BAND));
            assertThat(g.drummer.expectedToString()).isEqualTo("private Drummer drummer");
            assertThat(g.getDrummer.expectedToString()).isEqualTo("public Drummer getDrummer()");
            assertThat(g.setDrummer.expectedToString()).isEqualTo("public void setDrummer(Drummer)");
            assertThat(g.constructor.expectedToString()).isEqualTo("public Guitarist(double, Drummer)");
        }

        @Test
        void nestedClassesAreShownByTheirSimpleName() {
            TestClassWrapper<Object> parent = TestClassWrapper.base("Modifiers", "public", "static");
            AttributeWrapper<Object, ?> box = new AttributeWrapper<>(parent, "box",
                    ClassWrapper.reference("Outer$Inner", BAND), "private");
            assertThat(box.expectedToString()).isEqualTo("private Inner box");
        }
    }

    @Nested
    class MissingTypeClass {

        @Test
        void onlyTheTypeIsMissing() {
            GuitaristWrapper<Object> g = new GuitaristWrapper<>(BAND, ClassWrapper.reference("Drummer", BAND));
            assertThat(g.bassist.getOverallExistence()).isEqualTo(MISSING);
            assertThat(g.bassist.name.existence).isEqualTo(EXACT);
            assertThat(g.bassist.modifiers.existence).isEqualTo(EXACT);
            assertThat(g.bassist.getExpectedType()).isNull();
            assertThat(g.bassist.expectedToString()).isEqualTo("private Bassist bassist");
            assertThat(g.bassist.toString()).contains("Bassist");
        }

        @Test
        void methodIsFoundButItsTypesAreMissing() {
            GuitaristWrapper<Object> g = new GuitaristWrapper<>(BAND, ClassWrapper.reference("Drummer", BAND));
            assertThat(g.getBassist.getOverallExistence()).isEqualTo(MISSING);
            assertThat(g.getBassist.getActualName()).isEqualTo("getBassist");
            assertThat(g.getBassist.getExpectedReturnType()).isNull();

            assertThat(g.setBassist.getOverallExistence()).isEqualTo(MISSING);
            assertThat(g.setBassist.getActualName()).isEqualTo("setBassist");
            assertThat(g.setBassist.getActualParamTypes()).containsExactly(Object.class);
            assertThat(g.setBassist.getParamTypes()).containsExactly((Class<?>) null);
            assertThat(g.setBassist.expectedToString()).isEqualTo("public void setBassist(Bassist)");
        }

        @Test
        void missingDrummerClass() {
            ClassWrapper<Object> drummerType = ClassWrapper.reference("Drumxyz2000", BAND);
            GuitaristWrapper<Object>[] holder = new GuitaristWrapper[1];
            assertThatCode(() -> holder[0] = new GuitaristWrapper<>(BAND, drummerType)).doesNotThrowAnyException();
            GuitaristWrapper<Object> g = holder[0];
            assertThat(drummerType.getOverallExistence()).isEqualTo(MISSING);

            assertThat(g.drummer.getOverallExistence()).isEqualTo(MISSING);
            assertThat(g.drummer.name.existence).isEqualTo(EXACT);
            assertThat(g.getDrummer.getActualName()).isEqualTo("getDrummer");
            assertThat(g.getDrummer.getOverallExistence()).isEqualTo(MISSING);
            assertThat(g.setDrummer.getActualName()).isEqualTo("setDrummer");
            assertThat(g.constructor.getOverallExistence()).isEqualTo(MISSING);
            assertThat(g.constructor.getActualParamTypes()).hasSize(2);
            assertThat(g.constructor.modifiers.existence).isEqualTo(EXACT);
            assertThat(g.getOverallExistence()).isEqualTo(EXACT);
        }

        @Test
        void unresolvedParameterDoesNotMatchAPrimitive() {
            DrummerWrapper<Object> d = new DrummerWrapper<>(BAND, ClassWrapper.reference("Guitarist", BAND));
            // Drummer(double, Guitarist): the missing class cannot stand for the primitive double
            ConstructorWrapper<Object> c = new ConstructorWrapper<>(d,
                    new Object[]{ClassWrapper.reference("Nothing", BAND), ClassWrapper.reference("Guitarist", BAND)},
                    "public");
            assertThat(c.getOverallExistence()).isEqualTo(MISSING);
            assertThat(c.getActualParamTypes()).hasSize(2).startsWith((Class<?>) null);
        }

        @Test
        void behavioralUseFailsWithTheUsualMessage() {
            LevenshteinSettings.setLanguage(LevenshteinSettings.Language.ENGLISH);
            GuitaristWrapper<Object> g = new GuitaristWrapper<>(BAND, ClassWrapper.reference("Drummer", BAND));
            Object guitarist = g.newObj();
            assertThatThrownBy(() -> g.bassist.getValue(guitarist))
                    .isInstanceOf(AssertionError.class)
                    .hasMessageContaining("bassist");
            assertThatThrownBy(() -> g.getBassist.invokeOnSpecificObject(guitarist))
                    .isInstanceOf(AssertionError.class)
                    .hasMessageContaining("Bassist getBassist()");
        }

        @Test
        void gettersSettersTestFailsWithoutException() {
            GuitaristWrapper<Object> g = new GuitaristWrapper<>(BAND, ClassWrapper.reference("Drummer", BAND));
            assertThatThrownBy(() -> g.testGettersSetters(g.bassist, g.getBassist, g.setBassist))
                    .isInstanceOf(AssertionError.class)
                    .hasMessageContaining("bassist");
        }

        @Test
        void supplierReturningNull() {
            Band band = new Band();
            band.guitarist = new GuitaristWrapper<>(BAND, () -> band.drummer);
            assertThat(band.guitarist.drummer.expectedToString()).isEqualTo("private <unknown> drummer");
            assertThatThrownBy(() -> band.guitarist.drummer.getOverallExistence())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("attribute drummer");
            assertThatThrownBy(() -> band.guitarist.setDrummer.getParamTypes())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("parameter 0");
        }
    }

    @Nested
    class MisnamedTypeClass {

        @Test
        void typeResolvesToTheStudentClass() throws Exception {
            ClassWrapper<Object> drummerType = ClassWrapper.reference("Drummer", MISNAMED);
            GuitaristWrapper<Object> g = new GuitaristWrapper<>(MISNAMED, drummerType);
            assertThat(drummerType.getOverallExistence()).isEqualTo(DEVIATES);
            assertThat(g.drummer.getExpectedType()).isEqualTo(Class.forName(MISNAMED + ".Drumer"));
            assertThat(List.of(g.drummer, g.getDrummer, g.setDrummer, g.constructor))
                    .allSatisfy(w -> assertThat(w.getOverallExistence().severity())
                            .as(w.expectedToString()).isLessThanOrEqualTo(DEVIATES.severity()));
            // messages still show the expected name
            assertThat(g.drummer.expectedToString()).isEqualTo("private Drummer drummer");
        }
    }

    @Nested
    class Specification {

        private final TestClassWrapper<Object> parent = TestClassWrapper.base("Modifiers", "public", "static");

        @Test
        void referenceEqualsTheFullWrapper() {
            ClassWrapper<Object> reference = ClassWrapper.reference("Drummer", BAND);
            DrummerWrapper<Object> full = new DrummerWrapper<>(BAND, ClassWrapper.reference("Guitarist", BAND));
            assertThat(reference).isEqualTo(full).hasSameHashCodeAs(full);
            assertThat(reference.getObj(true)).isNull();
            assertThat(reference.getAttributeWrappers()).isEmpty();
        }

        @Test
        void classArrayCallsStillBindToTheOldConstructors() {
            MethodWrapper<Object, String> describe = new MethodWrapper<>(parent, "describe", String.class,
                    new Class<?>[0], "public");
            assertThat(describe.getOverallExistence()).isEqualTo(EXACT);
            assertThat(new ConstructorWrapper<>(parent, new Class<?>[0], "public").getOverallExistence()).isEqualTo(EXACT);
        }

        @Test
        void mixedParameterTypes() {
            MethodWrapper<Object, String> describe = new MethodWrapper<>(parent, "describe", String.class,
                    new Object[0], "public");
            assertThat(describe.getOverallExistence()).isEqualTo(EXACT);
            assertThat(new ConstructorWrapper<>(parent, (Object[]) null, "public").getParamTypes()).isEmpty();
        }

        @Test
        void invalidParameterTypesAreRejected() {
            assertThatThrownBy(() -> new MethodWrapper<>(parent, "describe", String.class, new Object[]{"int"}, "public"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("parameter 0")
                    .hasMessageContaining("java.lang.String");
            assertThatThrownBy(() -> new ConstructorWrapper<>(parent, new Object[]{int.class, null}, "public"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("parameter 1")
                    .hasMessageContaining("null");
            assertThatThrownBy(() -> new ConstructorWrapper<>(parent, new Class<?>[]{null}, "public"))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void nullWrapperOrSupplierIsRejected() {
            assertThatThrownBy(() -> new AttributeWrapper<>(parent, "box", (ClassWrapper<Object>) null, "private"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("attribute box");
            assertThatThrownBy(() -> new AttributeWrapper<>(parent, "box", (Supplier<ClassWrapper<Object>>) null, "private"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("attribute box");
            assertThatThrownBy(() -> new MethodWrapper<>(parent, "describe", (ClassWrapper<Object>) null, "public"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("return type of describe");
            assertThatThrownBy(() -> new MethodWrapper<>(parent, "describe", (Supplier<ClassWrapper<Object>>) null, "public"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("return type of describe");
        }

        @Test
        void wrapperOfAFixedClassAsType() {
            AttributeWrapper<Object, ?> text = new AttributeWrapper<>(parent, "text",
                    new GenericClassWrapper<>(String.class), "private");
            assertThat(text.getOverallExistence()).isEqualTo(EXACT);
            MethodWrapper<Object, ?> describe = new MethodWrapper<>(parent, "describe",
                    () -> new GenericClassWrapper<>(String.class), "public");
            assertThat(describe.getOverallExistence()).isEqualTo(EXACT);
            assertThat(describe.invoke()).isEqualTo("start");
        }
    }
}
