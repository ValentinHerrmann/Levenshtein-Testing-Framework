package io.github.valentinherrmann.levenshtein;

import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.InvocationInterceptor;
import org.junit.jupiter.api.extension.ReflectiveInvocationContext;

import java.lang.ref.WeakReference;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Keeps state from leaking from one test into the next: before each test it forgets the default instances of
 * the class wrappers and resets attributes that the student declared {@code static} although the test expects an
 * instance attribute (issue #35).
 *
 * <p><b>Default instances.</b> {@link ClassWrapper#getObj()} caches the default instance of a class, and
 * {@code invoke(...)}, {@code getValue()} and {@code setValue(...)} without an object use it. An object that one
 * test changed must not decide the result of the next, so every test starts with a new one. An instance set with
 * {@link ClassWrapper#setCachedObj(Object)} stays; {@link LevenshteinSettings#setNewDefaultObjectPerTest(boolean)}
 * switches this off.</p>
 *
 * <p><b>Static attributes.</b> Such an attribute is a deviation (the structural test fails), but behaviour tests go on with it. Its value
 * is shared by all objects and, because the class is never reloaded, by all tests, so results could depend on
 * the order of the tests. Before each test this extension writes the value back that the attribute had right
 * after the class was initialised.</p>
 *
 * <ul>
 *   <li>Only attributes that a live attribute wrapper reports with an unexpected {@code static} are reset.
 *       An attribute that any live wrapper expects to be {@code static} is never reset, even if another wrapper
 *       (e.g. of an earlier test class) reported it.</li>
 *   <li>Arrays (also nested ones) are copied deeply, so a test gets a fresh array with the initial content.
 *       Other objects (e.g. lists) are restored by reference: their content stays as an earlier test left it.</li>
 *   <li>{@code static final} attributes cannot be reset.</li>
 *   <li>Within one test the state stays shared on purpose: that is the wrong behaviour the student should see.</li>
 * </ul>
 *
 * <p>The extension is part of {@link LevenshteinTest} and {@link HiddenLevenshteinTest}. Once per test, before
 * the first {@code @BeforeEach} method or else before the test method, it first checks all attribute wrappers
 * created so far (their lookup runs once anyway), so the initial value is recorded before the first test can
 * change it, and then resets. Consequences:</p>
 * <ul>
 *   <li>Wrappers are looked up before the test runs, so {@link LevenshteinSettings} must be configured in a static
 *       initializer or {@code @BeforeAll}; a change inside a test method or {@code @BeforeEach} comes too late for
 *       the attribute wrappers that exist already.</li>
 *   <li>A wrapper whose lookup fails is skipped here; the test that uses it runs the lookup again and reports
 *       the failure. A static initializer of the student that fails (also with a {@link StackOverflowError})
 *       leaves the attribute without a recorded value and is reported by the behaviour tests.</li>
 *   <li>The initial value is the value when the first wrapper reported the attribute. If student code changed the
 *       attribute before (e.g. a test class without a wrapper for it ran first), that changed value is restored;
 *       the real initial value cannot be recovered without reloading the class.</li>
 * </ul>
 *
 * <p><b>Supervision:</b> Ares arms its security policy in its own invocation interceptor
 * ({@code JupiterSecurityExtension.interceptBeforeEachMethod}/{@code interceptTestMethod}). The composed
 * annotations declare {@code @Public}/{@code @Hidden} <em>before</em> {@code @ExtendWith(StaticStateReset.class)}, so
 * JUnit registers Ares first and its interceptor wraps this one: the lookups and class initialisations triggered
 * here run supervised, like every other lookup. Keep that order.</p>
 */
public class StaticStateReset implements InvocationInterceptor {

    private static final ExtensionContext.Namespace NAMESPACE = ExtensionContext.Namespace.create(StaticStateReset.class);

    /** All attribute wrappers created so far; weak, so wrappers of finished test classes can be collected. */
    private static final List<WeakReference<AttributeWrapper<?, ?>>> WRAPPERS = new ArrayList<>();

    /** All class wrappers created so far (not those of known classes, which have no default instance); weak. */
    private static final List<WeakReference<ClassWrapper<?>>> CLASS_WRAPPERS = new ArrayList<>();

    /** The initial value of each attribute with an unexpected {@code static}. */
    private static final Map<Field, Object> SNAPSHOTS = new LinkedHashMap<>();

    @Override
    public void interceptBeforeEachMethod(Invocation<Void> invocation, ReflectiveInvocationContext<Method> invocationContext,
                                          ExtensionContext extensionContext) throws Throwable {
        resetOncePerTest(extensionContext);
        invocation.proceed();
    }

    @Override
    public void interceptTestMethod(Invocation<Void> invocation, ReflectiveInvocationContext<Method> invocationContext,
                                    ExtensionContext extensionContext) throws Throwable {
        resetOncePerTest(extensionContext);
        invocation.proceed();
    }

    @Override
    public void interceptTestTemplateMethod(Invocation<Void> invocation,
                                            ReflectiveInvocationContext<Method> invocationContext,
                                            ExtensionContext extensionContext) throws Throwable {
        resetOncePerTest(extensionContext);
        invocation.proceed();
    }

    /**
     * Resets (see {@link #resetAll()}) unless it already happened for this test, e.g. before an earlier
     * {@code @BeforeEach} method of the same test.
     *
     * @param context the context of the test method
     */
    static void resetOncePerTest(ExtensionContext context) {
        ExtensionContext.Store store = context.getStore(NAMESPACE);
        if (store.get(context.getUniqueId()) == null) {
            store.put(context.getUniqueId(), Boolean.TRUE);
            resetAll();
        }
    }

    /**
     * Forgets the default instances of all registered class wrappers (unless
     * {@link LevenshteinSettings#isNewDefaultObjectPerTest()} is switched off), checks all registered attribute
     * wrappers (recording the initial values of attributes with an unexpected {@code static}) and writes the
     * recorded initial values back.
     */
    static void resetAll() {
        if (LevenshteinSettings.isNewDefaultObjectPerTest()) {
            for (ClassWrapper<?> wrapper : live(CLASS_WRAPPERS)) {
                wrapper.forgetDefaultObj();
            }
        }
        Set<Field> unexpectedStatic = new HashSet<>();
        Set<Field> expectedStatic = new HashSet<>();
        for (AttributeWrapper<?, ?> wrapper : live(WRAPPERS)) {
            try {
                wrapper.ensureChecked();
            }
            catch (Throwable t) {
                // not the business of this test: the test that uses the wrapper runs the lookup again and reports it
                Invocations.rethrowIfCritical(t);
                continue;
            }
            Field field = wrapper.foundField();
            if (field == null) {
                continue;
            }
            if (wrapper.hasUnexpectedModifier("static")) {
                unexpectedStatic.add(field);
            }
            if (wrapper.isModifierExpected("static")) {
                expectedStatic.add(field);
            }
        }
        unexpectedStatic.removeAll(expectedStatic);
        Map<Field, Object> snapshots;
        synchronized (SNAPSHOTS) {
            snapshots = new LinkedHashMap<>(SNAPSHOTS);
        }
        snapshots.keySet().retainAll(unexpectedStatic);
        for (Map.Entry<Field, Object> entry : snapshots.entrySet()) {
            try {
                entry.getKey().set(null, deepCopy(entry.getValue()));
            }
            catch (ReflectiveOperationException | RuntimeException e) {
                // cannot be reset: the behaviour tests run with the current value
            }
        }
    }

    /**
     * Registers an attribute wrapper; its attribute is recorded once its lookup reports an unexpected
     * {@code static}.
     *
     * @param wrapper the new attribute wrapper
     */
    static void register(AttributeWrapper<?, ?> wrapper) {
        add(WRAPPERS, wrapper);
    }

    /**
     * Registers a class wrapper; its default instance is forgotten before each test.
     *
     * @param wrapper the new class wrapper
     */
    static void register(ClassWrapper<?> wrapper) {
        add(CLASS_WRAPPERS, wrapper);
    }

    private static <W> void add(List<WeakReference<W>> wrappers, W wrapper) {
        synchronized (wrappers) {
            wrappers.removeIf(ref -> ref.get() == null);
            wrappers.add(new WeakReference<>(wrapper));
        }
    }

    private static <W> List<W> live(List<WeakReference<W>> wrappers) {
        List<W> live = new ArrayList<>();
        synchronized (wrappers) {
            for (WeakReference<W> ref : wrappers) {
                W wrapper = ref.get();
                if (wrapper != null) {
                    live.add(wrapper);
                }
            }
        }
        return live;
    }

    /**
     * Records the current value of a static attribute as its initial value (once per attribute). Reading the value
     * initialises the class if necessary. {@code final} attributes and attributes whose value cannot be read are
     * skipped.
     *
     * @param field the static attribute
     */
    static void snapshot(Field field) {
        if (!Modifier.isStatic(field.getModifiers()) || Modifier.isFinal(field.getModifiers())) {
            return;
        }
        synchronized (SNAPSHOTS) {
            if (SNAPSHOTS.containsKey(field)) {
                return;
            }
        }
        Object value;
        try {
            field.setAccessible(true);
            value = deepCopy(field.get(null));
        }
        catch (Throwable t) {
            // e.g. a static initializer that fails, also with a StackOverflowError or an OutOfMemoryError: there is
            // no initial value to restore, and the failure is reported by the behaviour tests that use the class
            return;
        }
        synchronized (SNAPSHOTS) {
            SNAPSHOTS.putIfAbsent(field, value);
        }
    }

    /**
     * @param value any value
     * @return a deep copy of an array (also of nested arrays); every other value unchanged
     */
    static Object deepCopy(Object value) {
        if (value == null || !value.getClass().isArray()) {
            return value;
        }
        int length = Array.getLength(value);
        Object copy = Array.newInstance(value.getClass().getComponentType(), length);
        for (int i = 0; i < length; i++) {
            Array.set(copy, i, deepCopy(Array.get(value, i)));
        }
        return copy;
    }
}
