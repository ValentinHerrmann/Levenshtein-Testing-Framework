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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Resets attributes that the student declared {@code static} although the test expects an instance attribute
 * (issue #35), so that their state does not leak from one test into the next.
 *
 * <p>Such an attribute is a deviation (the structural test fails), but behaviour tests go on with it. Its value
 * is shared by all objects and, because the class is never reloaded, by all tests, so results could depend on
 * the order of the tests. Before each test this extension writes the value back that the attribute had right
 * after the class was initialised.</p>
 *
 * <ul>
 *   <li>Only attributes reported with an unexpected {@code static} are reset; intended static attributes are
 *       never touched.</li>
 *   <li>Arrays (also nested ones) are copied deeply, so a test gets a fresh array with the initial content.
 *       Other objects (e.g. lists) are restored by reference: their content stays as an earlier test left it.</li>
 *   <li>{@code static final} attributes cannot be reset.</li>
 *   <li>Within one test the state stays shared on purpose: that is the wrong behaviour the student should see.</li>
 * </ul>
 *
 * <p>The extension is part of {@link LevenshteinTest} and {@link HiddenLevenshteinTest}. Once per test, before
 * the first {@code @BeforeEach} method or else before the test method, it first checks all attribute wrappers
 * created so far (their lookup runs once anyway), so the initial value is recorded before the first test can
 * change it, and then resets. It is an interceptor registered after Ares' {@code @Public}/{@code @Hidden}, so
 * the lookups and class initialisations run inside the supervised invocation, like every other lookup.</p>
 */
public class StaticStateReset implements InvocationInterceptor {

    private static final ExtensionContext.Namespace NAMESPACE = ExtensionContext.Namespace.create(StaticStateReset.class);

    /** All attribute wrappers created so far; weak, so wrappers of finished test classes can be collected. */
    private static final List<WeakReference<AttributeWrapper<?, ?>>> WRAPPERS = new ArrayList<>();

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
     * Checks all registered attribute wrappers (recording the initial values of attributes with an unexpected
     * {@code static}) and writes the recorded initial values back.
     */
    static void resetAll() {
        for (AttributeWrapper<?, ?> wrapper : liveWrappers()) {
            wrapper.ensureChecked();
        }
        Map<Field, Object> snapshots;
        synchronized (SNAPSHOTS) {
            snapshots = new LinkedHashMap<>(SNAPSHOTS);
        }
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
        synchronized (WRAPPERS) {
            WRAPPERS.removeIf(ref -> ref.get() == null);
            WRAPPERS.add(new WeakReference<>(wrapper));
        }
    }

    private static List<AttributeWrapper<?, ?>> liveWrappers() {
        List<AttributeWrapper<?, ?>> live = new ArrayList<>();
        synchronized (WRAPPERS) {
            for (WeakReference<AttributeWrapper<?, ?>> ref : WRAPPERS) {
                AttributeWrapper<?, ?> wrapper = ref.get();
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
        catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            return; // e.g. a failing static initializer: reported by the behaviour tests
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
