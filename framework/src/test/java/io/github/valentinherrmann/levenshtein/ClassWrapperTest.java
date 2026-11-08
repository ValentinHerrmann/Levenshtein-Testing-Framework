package io.github.valentinherrmann.levenshtein;

import static io.github.valentinherrmann.levenshtein.WrapperProperty.Existence.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.valentinherrmann.levenshtein.fixtures.ClassFixtures;
import io.github.valentinherrmann.levenshtein.fixtures.Fixtures;

import java.net.URL;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests of {@link ClassWrapper}: lookup, superclass and interfaces, instantiation, wrapper fields and reports.
 */
class ClassWrapperTest {

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

    static <T> W<T> fixture(String simpleName, String... modifiers) {
        return new W<>("Fixtures$" + simpleName, modifiers);
    }

    static <T> W<T> classFixture(String simpleName, String... modifiers) {
        return new W<>("ClassFixtures$" + simpleName, modifiers);
    }

    static ClassWrapper<?>[] interfaces(ClassWrapper<?>... wrappers) {
        return wrappers;
    }

    /** The verdict of the superclass part (after the lookup). */
    static WrapperProperty.Existence superClassState(ClassWrapper<?> w) {
        w.getOverallExistence();
        return w.superClassWrapper.existence;
    }

    /** The verdict of the interfaces part (after the lookup). */
    static WrapperProperty.Existence interfacesState(ClassWrapper<?> w) {
        w.getOverallExistence();
        return w.interfaceWrappers.existence;
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
        void exactNameIsFound() {
            W<Object> w = classFixture("Gear", "public", "static");
            assertThat(w.getOverallExistence()).isEqualTo(EXACT);
            assertThat(w.getClazz()).isEqualTo(ClassFixtures.Gear.class);
            assertThat(w.getActualName()).isEqualTo("ClassFixtures$Gear");
            assertThat(w.getExpectedPackage()).isEqualTo(PKG);
        }

        @Test
        void typoIsFoundByScanningThePackage() {
            // a replaced character is no generated spelling: only the scan of the package finds it
            W<Object> w = classFixture("Gexr", "public", "static");
            assertThat(w.getOverallExistence()).isEqualTo(DEVIATES);
            assertThat(w.getClazz()).isEqualTo(ClassFixtures.Gear.class);
            assertThat(w.getActualName()).isEqualTo("ClassFixtures$Gear");
        }

        @Test
        void closestNameWinsOverAlphabeticalOrder() {
            // "Seat" is one edit away, "Sbat" two (and "Gear" two), although both sort before "Seat"
            W<Object> w = classFixture("Seax", "public", "static");
            assertThat(w.getOverallExistence()).isEqualTo(DEVIATES);
            assertThat(w.getClazz()).isEqualTo(ClassFixtures.Seat.class);
        }

        @Test
        void nestedClassesAreOnlyFoundIfANestedClassIsExpected() {
            // "ClassFixturesXGear" is one edit away from the nested "ClassFixtures$Gear"
            assertThat(new W<>("ClassFixturesXGear", "public").getOverallExistence()).isEqualTo(MISSING);
            assertThat(classFixture("Gexr", "public", "static").getClazz()).isEqualTo(ClassFixtures.Gear.class);
        }

        @Test
        void missingClassFailsBehaviouralUseWithReadableMessage() {
            W<Object> w = classFixture("DoesNotExist", "public");
            assertThat(w.getClazz()).isNull();
            assertThat(w.actualToString()).isEqualTo("<missing>");
            assertThatThrownBy(w::newObj)
                    .isInstanceOf(AssertionError.class)
                    .hasMessageContaining("Class ClassFixtures$DoesNotExist in package " + PKG + " is not implemented");
            assertThatThrownBy(w::newObjWithoutConstructor)
                    .isInstanceOf(AssertionError.class)
                    .hasMessageContaining("Class ClassFixtures$DoesNotExist in package " + PKG + " is not implemented");
        }

        /** Runs the lookup with a context class loader that lists the package roots differently. */
        private Class<?> findWithRoots(String expectedName, java.util.function.Function<List<URL>, List<URL>> roots) {
            Thread thread = Thread.currentThread();
            ClassLoader original = thread.getContextClassLoader();
            ClassLoader parent = original != null ? original : ClassWrapperTest.class.getClassLoader();
            thread.setContextClassLoader(new ClassLoader(parent) {
                @Override
                public Enumeration<URL> getResources(String name) throws java.io.IOException {
                    return Collections.enumeration(roots.apply(Collections.list(parent.getResources(name))));
                }
            });
            try {
                return new W<>(expectedName, "public").getClazz();
            }
            finally {
                thread.setContextClassLoader(original);
            }
        }

        /** Simulates a package that cannot be scanned (e.g. inside a JAR): only generated spellings are tried. */
        private Class<?> foundWithoutScan(String expectedName) {
            return findWithRoots(expectedName, roots -> List.of());
        }

        @Test
        void directoriesAreScannedEvenIfThePackageIsAlsoInAJar() throws Exception {
            URL jar = java.net.URI.create("jar:file:/does/not/exist.jar!/" + PKG.replace('.', '/')).toURL();
            Class<?> found = findWithRoots("ClassFixtures$Gexr", roots -> {
                List<URL> withJar = new java.util.ArrayList<>(List.of(jar));
                withJar.addAll(roots);
                return withJar;
            });
            assertThat(found).isEqualTo(ClassFixtures.Gear.class);
        }

        @Test
        void generatedSpellingsAreFoundWithoutPackageScan() {
            assertThat(foundWithoutScan("ClassFixtures$Gears")).isEqualTo(ClassFixtures.Gear.class); // plural
            assertThat(foundWithoutScan("ClassFixtures$Wheel")).isEqualTo(ClassFixtures.Wheels.class); // singular
            assertThat(foundWithoutScan("classFixtures$Gear")).isEqualTo(ClassFixtures.Gear.class); // first letter case
            assertThat(foundWithoutScan("ClassFixtures$Boxes")).isEqualTo(ClassFixtures.Box.class); // "es" plural
            assertThat(foundWithoutScan("ClassFixtures$Geaar")).isEqualTo(ClassFixtures.Gear.class); // one too many
            assertThat(foundWithoutScan("ClassFixtures$Gaer")).isEqualTo(ClassFixtures.Gear.class); // swapped
            assertThat(foundWithoutScan("MyCar").getSimpleName()).isEqualTo("myCar"); // first letter case
            LevenshteinSettings.setClassNameDeviationThreshold(25);
            assertThat(foundWithoutScan("Buss").getSimpleName()).isEqualTo("Bus"); // singular
        }

        @Test
        void otherTyposNeedThePackageScan() {
            assertThat(foundWithoutScan("ClassFixtures$Gexr")).isNull();
        }
    }

    @Nested
    class SuperClass {

        @Test
        void directSuperclassIsExact() {
            W<Object> w = new W<>("Fixtures$Derived", fixture("Base", "public static"), null, "public", "static");
            assertThat(superClassState(w)).isEqualTo(EXACT);
            assertThat(w.getOverallExistence()).isEqualTo(EXACT);
            assertThat(w.expectedToString()).isEqualTo("public static class Fixtures$Derived extends Fixtures$Base");
            assertThat(w.actualToString()).isEqualTo("public static class Fixtures$Derived extends Base");
        }

        @Test
        void unrelatedSuperclassIsMissing() {
            W<Object> w = new W<>("Fixtures$Derived", fixture("NoSuper", "public static"), null, "public", "static");
            assertThat(superClassState(w)).isEqualTo(MISSING);
        }

        @Test
        void unexpectedSuperclassDeviates() {
            W<Object> w = fixture("Derived", "public static");
            assertThat(superClassState(w)).isEqualTo(DEVIATES);
            assertThat(superClassState(fixture("Base", "public static"))).isEqualTo(EXACT);
        }

        @Test
        void classWithoutSuperclassIsMissingWhenOneIsExpected() {
            W<Object> w = new W<>("Fixtures$NoSuper", fixture("Base", "public static"), null, "public", "static");
            assertThat(superClassState(w)).isEqualTo(MISSING);
        }

        @Test
        void missingExpectedSuperclassMakesItMissing() {
            W<Object> w = new W<>("Fixtures$Derived", fixture("GhostBase", "public static"), null, "public", "static");
            assertThat(superClassState(w)).isEqualTo(MISSING);
        }
    }

    @Nested
    class Interfaces {

        private final W<Object> drivable = fixture("Drivable", "public static interface");
        private final W<Object> honkable = fixture("Honkable", "public static interface");

        @Test
        void exactlyTheExpectedInterfacesAreExact() {
            W<Object> w = new W<>("Fixtures$TwoInterfaces", null, interfaces(drivable, honkable), "public", "static");
            assertThat(interfacesState(w)).isEqualTo(EXACT);
        }

        @Test
        void additionalInterfaceDeviates() {
            W<Object> w = new W<>("Fixtures$TwoInterfaces", null, interfaces(drivable), "public", "static");
            assertThat(interfacesState(w)).isEqualTo(DEVIATES);
        }

        @Test
        void interfaceInheritedThroughTheSuperclassDeviates() {
            W<Object> w = new W<>("ClassFixtures$InheritsInterface", null, interfaces(drivable), "public", "static");
            assertThat(interfacesState(w)).isEqualTo(DEVIATES);
        }

        @Test
        void interfaceNotImplementedIsMissing() {
            W<Object> w = new W<>("Fixtures$NoSuper", null, interfaces(drivable), "public", "static");
            assertThat(interfacesState(w)).isEqualTo(MISSING);
            assertThat(w.getOverallExistence()).isEqualTo(MISSING);
        }
    }

    @Nested
    class Instantiation {

        @Test
        void nullArgumentsMeanNoArguments() {
            W<Object> w = fixture("FinalCar", "public static final");
            Object car = w.getObj(false, null, (Object[]) null);
            assertThat(((Fixtures.FinalCar) car).getBrand()).isEqualTo("default");
            assertThat(w.getObj(false, null)).isSameAs(car);
        }

        @Test
        void defaultConstructorIsNotServedFromTheCacheOfAnotherConstructor() {
            W<Object> w = fixture("FinalCar", "public static final");
            ConstructorWrapper<Object> ctor = new ConstructorWrapper<>(w, new Class<?>[]{String.class}, "public");
            Object bmw = w.getObj(false, ctor, "BMW");
            Object byDefault = w.getObj(false, null);
            assertThat(byDefault).isNotSameAs(bmw);
            assertThat(((Fixtures.FinalCar) byDefault).getBrand()).isEqualTo("default");
        }

        @Test
        void cacheDistinguishesConstructorsWithEqualArguments() {
            W<Object> w = classFixture("Overloads", "public static");
            ConstructorWrapper<Object> withLong = new ConstructorWrapper<>(w, new Class<?>[]{long.class}, "public");
            ConstructorWrapper<Object> withInteger = new ConstructorWrapper<>(w, new Class<?>[]{Integer.class}, "public");
            Object first = w.getObj(false, withLong, 7);
            Object second = w.getObj(false, withInteger, 7);
            assertThat(((ClassFixtures.Overloads) first).chosen).isEqualTo("long");
            assertThat(((ClassFixtures.Overloads) second).chosen).isEqualTo("Integer");
            assertThat(w.getObj(false, withInteger, 7)).isSameAs(second);
        }

        @Test
        void argumentsWithoutConstructorAreRejected() {
            // the no-argument constructor takes no arguments, also for concrete classes (like for abstract ones)
            W<Object> concrete = fixture("FinalCar", "public static final");
            assertThatThrownBy(() -> concrete.getObj(true, null, "BMW"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("expects 0 argument(s), but 1 were passed");
            W<Object> abstr = classFixture("AbstractWithDefaultCtor", "public static abstract");
            assertThatThrownBy(() -> abstr.getObj(true, null, "BMW"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("expects 0 argument(s), but 1 were passed");
        }

        @Test
        void argumentsWithoutConstructorAreRejectedAlsoForAPinnedObject() {
            W<Object> concrete = fixture("FinalCar", "public static final");
            Object pinned = concrete.getObj(true, null);
            concrete.setCachedObj(pinned);
            assertThatThrownBy(() -> concrete.getObj(false, null, 42))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("expects 0 argument(s), but 1 were passed");
            assertThat(concrete.getObj(false, null)).isSameAs(pinned);
        }

        @Test
        void pinnedObjectIsReturnedForEveryRequest() {
            W<Object> w = fixture("FinalCar", "public static final");
            ConstructorWrapper<Object> ctor = new ConstructorWrapper<>(w, new Class<?>[]{String.class}, "public");
            Fixtures.FinalCar pinned = new Fixtures.FinalCar("pinned");
            w.setCachedObj(pinned);
            assertThat(w.getObj()).isSameAs(pinned);
            assertThat(w.getObj(false, ctor, "BMW")).isSameAs(pinned);
            Object fresh = w.getObj(true, ctor, "BMW");
            assertThat(fresh).isNotSameAs(pinned);
            assertThat(w.getObj(false, null)).isNotSameAs(fresh).isNotSameAs(pinned); // no longer pinned
            w.setCachedObj(null);
            assertThat(w.getObj()).isNotNull();
        }

        @Test
        void abstractClassIsInstantiatedWithItsNoArgumentConstructor() {
            W<Object> w = classFixture("AbstractWithDefaultCtor", "public static abstract");
            Object first = w.getObj(true);
            Object second = w.newObj();
            assertThat(first).isInstanceOf(ClassFixtures.AbstractWithDefaultCtor.class).isNotSameAs(second);
            assertThat(((ClassFixtures.AbstractWithDefaultCtor) first).origin).isEqualTo("default");
            assertThat(first.getClass()).isNotEqualTo(ClassFixtures.AbstractWithDefaultCtor.class)
                    .isSameAs(second.getClass()); // the generated subclass is created only once
            assertThatThrownBy(((ClassFixtures.AbstractWithDefaultCtor) first)::wheels)
                    .isInstanceOf(AbstractMethodError.class);
        }

        @Test
        void abstractClassWithConstructorWrapper() {
            W<Object> w = classFixture("AbstractWithDefaultCtor", "public static abstract");
            ConstructorWrapper<Object> ctor = new ConstructorWrapper<>(w, new Class<?>[]{String.class}, "protected");
            Object obj = w.getObj(false, ctor, "given");
            assertThat(((ClassFixtures.AbstractWithDefaultCtor) obj).origin).isEqualTo("given");
            assertThat(w.getObj(false, ctor, "given")).isSameAs(obj);
        }

        @Test
        void missingNoArgumentConstructorFailsReadably() {
            W<Object> w = classFixture("NoDefaultCtor", "public static");
            assertThatThrownBy(w::newObj)
                    .isInstanceOf(AssertionError.class)
                    .hasMessageContaining("Creating instances of class ClassFixtures$NoDefaultCtor failed");
        }

        @Test
        void throwingNoArgumentConstructorFailsWithItsException() {
            W<Object> w = classFixture("ThrowingDefaultCtor", "public static");
            assertThatThrownBy(w::newObj)
                    .isInstanceOf(AssertionError.class)
                    .hasMessage("""
                            Method:   ClassFixtures$ThrowingDefaultCtor()
                            Actual:   IllegalStateException: not today""")
                    .hasNoCause();
        }

        @Test
        void assertionErrorOfTheStudentIsNotWrapped() {
            W<Object> w = classFixture("CriticalCtors", "public static");
            assertThatThrownBy(w::newObj)
                    .isInstanceOf(AssertionError.class)
                    .hasMessage("student assertion");
        }

        @Test
        void missingConstructorOfAbstractClassFailsAsMissingConstructor() {
            W<Object> w = classFixture("AbstractWithDefaultCtor", "public static abstract");
            ConstructorWrapper<Object> ctor = new ConstructorWrapper<>(w, new Class<?>[]{boolean.class}, "protected");
            assertThatThrownBy(() -> w.getDynamicSubclassObj(ctor, true))
                    .isInstanceOf(AssertionError.class)
                    .hasMessageContaining("Constructor protected ClassFixtures$AbstractWithDefaultCtor(boolean) in class "
                            + "ClassFixtures$AbstractWithDefaultCtor is not implemented");
        }

        @Test
        void wrongArgumentCountNamesTheConstructor() {
            W<Object> w = classFixture("AbstractWithDefaultCtor", "public static abstract");
            ConstructorWrapper<Object> ctor = new ConstructorWrapper<>(w, new Class<?>[]{String.class}, "protected");
            assertThatThrownBy(() -> w.getDynamicSubclassObj(ctor, "a", "b"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageStartingWith("protected ClassFixtures$AbstractWithDefaultCtor(String) expects 1 argument(s)");
            assertThatThrownBy(() -> w.getDynamicSubclassObj(null, "a"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageStartingWith("ClassFixtures$AbstractWithDefaultCtor() expects 0 argument(s)");
        }

        @Test
        void assertionErrorInAbstractConstructorGetsTheArguments() {
            W<Object> w = classFixture("AbstractCriticalCtors", "public static abstract");
            ConstructorWrapper<Object> ctor = new ConstructorWrapper<>(w, new Class<?>[]{String.class}, "protected");
            assertThatThrownBy(() -> w.getDynamicSubclassObj(ctor, "x"))
                    .isInstanceOf(AssertionError.class)
                    .hasMessageContaining("student assertion x")
                    .hasMessageContaining("Arguments: (\"x\")")
                    .hasNoCause();
        }

        @Test
        void stackOverflowIsReportedWithARecursionHint() {
            W<Object> abstr = classFixture("AbstractCriticalCtors", "public static abstract");
            ConstructorWrapper<Object> abstractCtor = new ConstructorWrapper<>(abstr, new Class<?>[]{int.class}, "protected");
            assertThatThrownBy(() -> abstr.getDynamicSubclassObj(abstractCtor, 3))
                    .isInstanceOf(AssertionError.class)
                    .hasNoCause()
                    .hasMessage("""
                            Method:   ClassFixtures$AbstractCriticalCtors(int)
                            Input:    3
                            Actual:   StackOverflowError: too deep 3
                            Hint: a StackOverflowError usually means endless recursion (a method keeps calling itself).""");
        }

        @Test
        void objectWithoutConstructorKeepsTheExactClass() {
            W<Object> w = classFixture("ThrowingDefaultCtor", "public static");
            Object obj = w.newObjWithoutConstructor();
            assertThat(obj.getClass()).isEqualTo(ClassFixtures.ThrowingDefaultCtor.class);
        }
    }

    /** A wrapper with wrapper fields of every kind, visibility and state. */
    static class Members extends W<Object> {
        static final W<Object> OTHER = fixture("Typos", "public static");
        static final MethodWrapper<Object, Integer> SHARED = new MethodWrapper<>(OTHER, "getMinSpeed", int.class, "public");

        final AttributeWrapper<Object, Integer> count = new AttributeWrapper<>(this, "count", int.class, "private");
        private final MethodWrapper<Object, Integer> add =
                new MethodWrapper<>(this, "add", int.class, new Class<?>[]{int.class, int.class}, "public");
        final ConstructorWrapper<Object> noArgs = new ConstructorWrapper<>(this, new Class<?>[0], "public");
        MethodWrapper<Object, Integer> notInitialised;

        Members() {
            super("Fixtures$Typos", "public", "static");
        }
    }

    @Nested
    class WrapperFields {

        private final Members members = new Members();

        @Test
        void attributeWrappers() {
            assertThat(members.getAttributeWrappers()).containsExactly(members.count);
        }

        @Test
        void methodWrappersIncludePrivateButNoStaticOrUnsetFields() {
            assertThat(members.getMethodWrappers()).containsExactly(members.add);
        }

        @Test
        void constructorWrappers() {
            assertThat(members.getConstructorWrappers()).containsExactly(members.noArgs);
        }
    }

    @Nested
    class Reports {

        private final W<Object> drivable = fixture("Drivable", "public static interface");
        private final W<Object> honkable = fixture("Honkable", "public static interface");

        @Test
        void classWithInterfaces() {
            W<Object> w = new W<>("Fixtures$TwoInterfaces", null, interfaces(drivable, honkable), "public", "static");
            assertThat(w.expectedToString())
                    .isEqualTo("public static class Fixtures$TwoInterfaces implements Fixtures$Drivable, Fixtures$Honkable");
            assertThat(w.actualToString())
                    .isEqualTo("public static class Fixtures$TwoInterfaces implements Drivable, Honkable");
            assertThat(w.toString()).startsWith(w.expectedToString() + " in package " + PKG);
        }

        @Test
        void interfaceExtendingInterfaces() {
            W<Object> w = new W<>("ClassFixtures$Steerable", null, interfaces(drivable, honkable), "public static interface");
            assertThat(w.getOverallExistence()).isEqualTo(EXACT);
            assertThat(w.expectedToString())
                    .isEqualTo("public static interface ClassFixtures$Steerable extends Fixtures$Drivable, Fixtures$Honkable");
            assertThat(w.actualToString())
                    .isEqualTo("public abstract static interface ClassFixtures$Steerable extends Drivable, Honkable");
        }

        @Test
        void classWithoutModifiersOrInterfaces() {
            W<Object> w = classFixture("Plain");
            assertThat(w.expectedToString()).isEqualTo("class ClassFixtures$Plain");
            assertThat(w.actualToString()).isEqualTo("static class ClassFixtures$Plain");
            // the fixture is a nested static class: the static that is not expected is a deviation (#35)
            assertThat(w.getOverallExistence()).isEqualTo(DEVIATES);
            assertThat(w.getUnexpectedModifiers()).containsExactly("static");
            assertThat(classFixture("Plain", "static").getOverallExistence()).isEqualTo(EXACT);
        }

        @Test
        void deviationShowsExpectedAndActual() {
            W<Object> w = classFixture("Gexr", "public", "static");
            assertThat(w.toString())
                    .startsWith(Messages.CLASS_DEVIATION.get())
                    .contains("Expect:\tpublic static class ClassFixtures$Gexr")
                    .contains("Actual:\tpublic static class ClassFixtures$Gear");
        }

        @Test
        void defaultPackageIsNamed() {
            ClassWrapper<Object> w = new ClassWrapper<>("DefaultPackageFixture", "", "public") {
                @Override
                public Object getObj(boolean forceNew) {
                    return null;
                }
            };
            assertThat(w.getExpectedPackage()).isEmpty();
            assertThat(w.toString()).contains(" in package " + Messages.DEFAULT_PACKAGE.get());
        }
    }

    @Nested
    class Equality {

        @Test
        void equalByExpectedNameAndPackage() {
            W<Object> base = fixture("Base");
            assertThat(base.equals(base)).isTrue();
            assertThat(base).isEqualTo(fixture("Base", "public")).hasSameHashCodeAs(fixture("Base", "public"));
            assertThat(base).isEqualTo(new GenericClassWrapper<>(Fixtures.Base.class));
            assertThat(base).isNotEqualTo(fixture("Derived"));
            assertThat(base.equals("Fixtures$Base")).isFalse();
            assertThat(base.equals(null)).isFalse();
            ClassWrapper<Object> otherPackage = new ClassWrapper<>("Fixtures$Base", "other") {
                @Override
                public Object getObj(boolean forceNew) {
                    return null;
                }
            };
            assertThat(base).isNotEqualTo(otherPackage);
        }
    }

    @Nested
    class GenericWrapper {

        @Test
        void wrapsAKnownClassExactly() {
            GenericClassWrapper<Fixtures.Derived> w = new GenericClassWrapper<>(Fixtures.Derived.class);
            assertThat(w.getOverallExistence()).isEqualTo(EXACT);
            assertThat(w.getClazz()).isEqualTo(Fixtures.Derived.class);
            assertThat(w.getExpectedName()).isEqualTo("Fixtures$Derived");
            assertThat(w.getExpectedPackage()).isEqualTo(PKG);
            assertThat(w.getObj(true)).isNull();
        }
    }

    @Nested
    class GettersSetters {

        private final W<Object> w = fixture("GettersSetters", "public static");

        @Test
        void testGetterFailsForHardCodedGetter() {
            AttributeWrapper<Object, Integer> count = new AttributeWrapper<>(w, "count", int.class, "private");
            MethodWrapper<Object, Integer> getCount = new MethodWrapper<>(w, "getCount", int.class, "public");
            assertThatThrownBy(() -> w.testGetter(count, getCount))
                    .isInstanceOf(AssertionError.class)
                    .hasMessageContaining("Method:   getCount()")
                    .hasMessageContaining("Expected: 7\nActual:   42");
        }

        @Test
        void ownValuesWithConditionFailForSetterStoringInvalidValues() {
            AttributeWrapper<Object, String> tag = new AttributeWrapper<>(w, "tag", String.class, "private");
            MethodWrapper<Object, Void> setTag = new MethodWrapper<>(w, "setTag", void.class, new Class<?>[]{String.class}, "public");
            assertThatThrownBy(() -> w.testGettersSetters(tag, List.of("a", "", "b"), (String s) -> !s.isEmpty(), setTag))
                    .isInstanceOf(AssertionError.class)
                    .hasMessage("""
                            Method:   setTag(String)
                            Scenario: setter -> attribute, attribute tag
                            Input:    setTag("")
                            Expected: value rejected (unchanged "a" or exception)
                            Actual:   ""
                            (1 of 3 cases failed)""");
            AttributeWrapper<Object, String> title = new AttributeWrapper<>(w, "title", String.class, "private");
            MethodWrapper<Object, Void> setTitle = new MethodWrapper<>(w, "setTitle", void.class, new Class<?>[]{String.class}, "public");
            assertThatCode(() -> w.testGettersSetters(title, java.util.Arrays.asList("a", null, "b"), (String s) -> s != null, setTitle))
                    .doesNotThrowAnyException();
        }
    }
}
