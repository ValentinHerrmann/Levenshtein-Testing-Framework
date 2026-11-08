package io.github.valentinherrmann.levenshtein;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.valentinherrmann.levenshtein.fixtures.CallFixtures;
import io.github.valentinherrmann.levenshtein.fixtures.Fixtures;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * {@link Mock}s and the generated subclasses of {@link MockClasses}.
 */
class MockTest {

    static final String PKG = "io.github.valentinherrmann.levenshtein.fixtures";

    /** Minimal wrapper for a class. */
    static class W<T> extends ClassWrapper<T> {
        W(String name, String pkg, String... modifiers) {
            super(name, pkg, modifiers);
        }

        @Override
        public Object getObj(boolean forceNew) {
            return getObj(forceNew, null);
        }
    }

    static <T> W<T> fixture(String simpleName) {
        return new W<>("CallFixtures$" + simpleName, PKG, "public", "static");
    }

    /** A subclass in another package than its superclass, whose package-private method it cannot override. */
    public static class ForeignChild extends CallFixtures.ForeignBase {
    }

    @AfterEach
    void resetSettings() {
        LevenshteinSettings.reset();
    }

    private static String failure(Runnable call) {
        LevenshteinSettings.setLanguage(LevenshteinSettings.Language.ENGLISH);
        return org.junit.jupiter.api.Assertions.assertThrows(AssertionError.class, call::run).getMessage();
    }

    private final W<Object> secretive = fixture("Secretive");
    private final MethodWrapper<Object, String> describe = new MethodWrapper<>(secretive, "describe", String.class, "public");
    private final MethodWrapper<Object, Void> log =
            new MethodWrapper<>(secretive, "log", void.class, new Class<?>[]{String.class}, "public");
    private final MethodWrapper<Object, String> echo =
            new MethodWrapper<>(secretive, "echo", String.class, new Class<?>[]{String.class}, "public");

    @Test
    void defaultValues() {
        assertThat(MockClasses.defaultValue(boolean.class)).isEqualTo(false);
        assertThat(MockClasses.defaultValue(char.class)).isEqualTo('\0');
        assertThat(MockClasses.defaultValue(byte.class)).isEqualTo((byte) 0);
        assertThat(MockClasses.defaultValue(short.class)).isEqualTo((short) 0);
        assertThat(MockClasses.defaultValue(int.class)).isEqualTo(0);
        assertThat(MockClasses.defaultValue(long.class)).isEqualTo(0L);
        assertThat(MockClasses.defaultValue(float.class)).isEqualTo(0f);
        assertThat(MockClasses.defaultValue(double.class)).isEqualTo(0d);
        assertThat(MockClasses.defaultValue(void.class)).isNull();
        assertThat(MockClasses.defaultValue(Integer.class)).isNull();
        assertThat(MockClasses.defaultValue(String.class)).isNull();
    }

    @Test
    void fullMockReturnsTheDefaultOfEachType() {
        CallFixtures.Defaults d = (CallFixtures.Defaults) fixture("Defaults").mock().othersReturnDefaults().obj();
        assertThat(d.flag()).isFalse();
        assertThat(d.letter()).isEqualTo('\0');
        assertThat(d.tiny()).isZero();
        assertThat(d.small()).isZero();
        assertThat(d.whole()).isZero();
        assertThat(d.large()).isZero();
        assertThat(d.single()).isZero();
        assertThat(d.precise()).isZero();
        assertThat(d.text()).isNull();
        d.nothing();
    }

    @Test
    void overloadsNamedLikeObjectMethodsAreOrdinaryMethods() {
        CallFixtures.Defaults d = (CallFixtures.Defaults) fixture("Defaults").mock().othersReturnDefaults().obj();
        assertThat(d.equals(d, d)).isFalse();
        assertThat(d.equals((Object) d)).isTrue(); // equals(Object) of Object
        assertThat(d.equals(d)).isFalse(); // the overload equals(Defaults)
        assertThat(d.hashCode(1)).isZero();
        assertThat(d.toString(16)).isNull();
    }

    @Test
    void defaultMethodsOfInterfacesRunAndAbstractOnesMustBeOverridden() {
        W<Object> greeter = fixture("Greeter");
        MethodWrapper<Object, String> name = new MethodWrapper<>(greeter, "name", String.class, "public", "abstract");
        for (String mocked : List.of("Greeter", "LoudGreeter", "PoliteGreeter")) {
            W<Object> w = fixture(mocked);
            CallFixtures.Greeter g = (CallFixtures.Greeter) w.mock().returning(name, "Ada").obj();
            assertThat(g.hello()).as(mocked).isEqualTo("hello Ada");

            CallFixtures.Greeter withoutName = (CallFixtures.Greeter) w.newObjWithoutConstructor();
            assertThat(failure(withoutName::hello)).as(mocked)
                    .isEqualTo("The abstract method name in class CallFixtures$" + mocked
                            + " was called, but the test does not override it.");
        }
        assertThat(MockClasses.hasImplementation(CallFixtures.PoliteGreeter.class, "hello", new Class<?>[0])).isTrue();
        assertThat(MockClasses.hasImplementation(CallFixtures.PoliteGreeter.class, "name", new Class<?>[0])).isFalse();
        assertThat(MockClasses.hasImplementation(CallFixtures.PoliteGreeter.class, "absent", new Class<?>[0])).isFalse();
        assertThat(MockClasses.hasImplementation(CallFixtures.Parent.class, "value", new Class<?>[0])).isTrue();
        assertThat(MockClasses.declaredInClassChain(CallFixtures.PoliteGreeter.class, "hello", new Class<?>[0])).isNull();
    }

    @Test
    void methodRedeclaredFinalInTheMockedClassCannotBeOverridden() {
        W<Object> parent = fixture("Parent");
        MethodWrapper<Object, Integer> value = new MethodWrapper<>(parent, "value", int.class, "public");
        assertThat(failure(() -> fixture("FinalChild").mock().override(value, args -> 3)))
                .isEqualTo("public int value() in class FinalChild: cannot be overridden (final)");
        assertThat(((CallFixtures.Parent) parent.mock().returning(value, 3).obj()).value()).isEqualTo(3);
    }

    @Test
    void privateMethodCannotBeOverridden() {
        MethodWrapper<Object, Integer> secret = new MethodWrapper<>(secretive, "secret", int.class, "private");
        assertThat(failure(() -> secretive.mock().override(secret, args -> 3)))
                .isEqualTo("private int secret() in class CallFixtures$Secretive: cannot be overridden (private)");
    }

    @Test
    void packagePrivateMethodOfAnotherPackageCannotBeOverridden() {
        W<Object> base = fixture("ForeignBase");
        MethodWrapper<Object, Integer> secret = new MethodWrapper<>(base, "secret", int.class);
        MethodWrapper<Object, Integer> open = new MethodWrapper<>(base, "open", int.class, "public");
        W<Object> child = new W<>("MockTest$ForeignChild", "io.github.valentinherrmann.levenshtein", "public", "static");
        assertThat(failure(() -> child.mock().override(secret, args -> 3)))
                .isEqualTo("int secret() in class CallFixtures$ForeignBase: cannot be overridden (package-private)");
        MethodWrapper<Object, Integer> guarded = new MethodWrapper<>(base, "guarded", int.class, "protected");
        Mock<Object> mock = child.mock().returning(guarded, 5); // protected methods still can
        assertThat(((CallFixtures.ForeignBase) mock.obj()).open()).isEqualTo(6);
        mock.returning(open, 9); // public ones as well
        assertThat(((CallFixtures.ForeignBase) mock.obj()).open()).isEqualTo(9);
        // in the same package, it can
        assertThat(((CallFixtures.ForeignBase) base.mock().returning(secret, 3).obj()).open()).isEqualTo(4);
    }

    @Test
    void classThatIsNotPublicCanBeMocked() {
        W<Object> hidden = new W<>("CallFixtures$Hidden", PKG, "static");
        MethodWrapper<Object, Integer> value = new MethodWrapper<>(hidden, "value", int.class, "public");
        Mock<Object> mock = hidden.mock().returning(value, 3);
        assertThat(value.invokeOnSpecificObject(mock.obj())).isEqualTo(3);
        mock.assertCalled(value, 1);
    }

    @Test
    void classOfAPackageThatIsNotOpenGetsASubclassElsewhere() {
        W<Object> list = new W<>("AbstractList", "java.util", "public", "abstract");
        MethodWrapper<Object, Object> get =
                new MethodWrapper<>(list, "get", Object.class, new Class<?>[]{int.class}, "public", "abstract");
        Mock<Object> mock = list.mock().override(get, args -> "#" + args[0]);
        assertThat(((List<?>) mock.obj()).get(4)).isEqualTo("#4");
        mock.assertCalled(get, 1);
        assertThat(list.mockSubclass().samePackage()).isFalse();

        // a package-private method can then not be overridden, even of the class itself
        W<Object> arrayList = new W<>("ArrayList", "java.util", "public");
        MethodWrapper<Object, Object> elementData =
                new MethodWrapper<>(arrayList, "elementData", Object.class, new Class<?>[]{int.class});
        assertThat(failure(() -> arrayList.mock().override(elementData, args -> null)))
                .endsWith("cannot be overridden (package-private)");
    }

    @Test
    void voidMethodIgnoresTheResultOfItsOverride() {
        Mock<Object> mock = secretive.mock().override(log, args -> null);
        log.invokeOnSpecificObject(mock.obj(), "first");
        mock.override(log, args -> "ignored");
        log.invokeOnSpecificObject(mock.obj(), "second");
        assertThat(mock.calls(log)).containsExactly(new Object[]{"first"}, new Object[]{"second"});
    }

    @Test
    void overrideMayReturnNullForAReferenceType() {
        Mock<Object> mock = secretive.mock().returning(describe, null);
        assertThat(describe.invokeOnSpecificObject(mock.obj())).isNull();
    }

    @Test
    void nullArgumentsReachTheOverrideAndTheRecord() {
        Mock<Object> mock = secretive.mock().override(echo, args -> args[0] == null ? "was null" : "not null");
        assertThat(echo.invokeOnSpecificObject(mock.obj(), (Object) null)).isEqualTo("was null");
        assertThat(mock.calls(echo)).singleElement().satisfies(args -> assertThat(args).containsExactly((Object) null));
    }

    @Test
    void methodThatWasNeverCalled() {
        Mock<Object> mock = secretive.mock();
        assertThat(mock.callCount(describe)).isZero();
        assertThat(mock.calls(describe)).isEmpty();
        mock.assertNotCalled(describe);
        assertThat(failure(() -> mock.assertCalled(describe, 1)))
                .isEqualTo("public String describe() in class CallFixtures$Secretive should be called 1 time(s),"
                        + " but was called 0 time(s).");
    }

    @Test
    void assertCalledReportsTheCountAlsoForArgumentsThatDoNotFitTheExpectedType() {
        W<Object> floats = new W<>("Fixtures$FloatParams", PKG, "public", "static");
        // the student declared float half(float); the test expects int half(int)
        MethodWrapper<Object, Integer> half = new MethodWrapper<>(floats, "half", int.class, new Class<?>[]{int.class}, "public");
        Mock<Object> mock = floats.mock();
        ((Fixtures.FloatParams) mock.obj()).half(2.5f);
        assertThat(failure(() -> mock.assertCalled(half, 2)))
                .isEqualTo("public float half(float) in class Fixtures$FloatParams should be called 2 time(s),"
                        + " but was called 1 time(s).\nCalls: (2.50)");
    }

    @Test
    void callsReturnsArgumentsThatDoNotFitTheExpectedTypeAsPassed() {
        W<Object> floats = new W<>("Fixtures$FloatParams", PKG, "public", "static");
        MethodWrapper<Object, Integer> half = new MethodWrapper<>(floats, "half", int.class, new Class<?>[]{int.class}, "public");
        Mock<Object> mock = floats.mock();
        ((Fixtures.FloatParams) mock.obj()).half(2.5f);
        ((Fixtures.FloatParams) mock.obj()).half(4f);
        // 4.0 fits int and is converted; 2.5 does not and is kept as passed instead of failing
        assertThat(mock.calls(half)).containsExactly(new Object[]{2.5f}, new Object[]{4});
    }

    @Test
    void objIsCreatedOnceAndOverridesAddedLaterApply() {
        Mock<Object> mock = secretive.mock();
        Object obj = mock.obj();
        assertThat(mock.obj()).isSameAs(obj).isInstanceOf(CallFixtures.Secretive.class);
        assertThat(describe.invokeOnSpecificObject(obj)).isEqualTo("real");
        mock.returning(describe, "mocked");
        assertThat(describe.invokeOnSpecificObject(obj)).isEqualTo("mocked");
        mock.assertCalled(describe, 2);
    }

    @Test
    void callsThroughAGenericInterfaceReachTheOverrideOnce() {
        W<Object> ranked = fixture("Ranked");
        MethodWrapper<Object, Integer> compareTo =
                new MethodWrapper<>(ranked, "compareTo", int.class, new Class<?>[]{CallFixtures.Ranked.class}, "public");
        Mock<Object> mock = ranked.mock().returning(compareTo, 7);
        @SuppressWarnings("unchecked")
        Comparable<Object> c = (Comparable<Object>) mock.obj();
        assertThat(c.compareTo(new CallFixtures.Ranked())).isEqualTo(7);
        mock.assertCalled(compareTo, 1);
    }

    @Test
    void newObjOverridingDoesNotNeedAMock() {
        Object obj = secretive.newObjOverriding(Map.of(describe, args -> "mocked"));
        assertThat(describe.invokeOnSpecificObject(obj)).isEqualTo("mocked");
    }
}
