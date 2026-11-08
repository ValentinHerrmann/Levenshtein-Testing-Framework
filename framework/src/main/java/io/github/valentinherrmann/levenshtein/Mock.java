package io.github.valentinherrmann.levenshtein;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.fail;

/**
 * An object of a student class whose methods the test can replace, and which records every call to its methods.
 * Created with {@link ClassWrapper#mock()}; used to keep tests independent: a mistake in one student method then
 * only fails the tests of that method.
 *
 * <pre>{@code
 * Mock<?> guitarist = guitaristWrapper.mock()
 *         .override(guitaristWrapper.calculateLoudness, args -> (int) args[0] * 7);
 * Object drummer = drummerWrapper.newObjWithoutConstructor();
 * drummerWrapper.guitarist.setValue(drummer, guitarist.obj());
 * assertThat(drummerWrapper.calculateLoudness.invokeOnSpecificObject(drummer, 5, 3)).isEqualTo(38);
 * guitarist.assertCalled(guitaristWrapper.calculateLoudness, 1);
 * }</pre>
 *
 * <ul>
 *   <li>The object is an instance of a generated subclass. <b>No constructor</b> of the student class runs, so all
 *       attributes keep their default values ({@code 0}, {@code false}, {@code null}); set them with
 *       {@link AttributeWrapper#setValue(Object, Object)} if needed.</li>
 *   <li>An overridden method runs the test's function. Every other method runs the student's code (supervised by
 *       Ares as usual), or returns its default value after {@link #othersReturnDefaults()}.</li>
 *   <li>Methods are matched by the method the wrapper <b>actually found</b>, so a misspelt but tolerated name is
 *       still overridden. Calls from the student's own code ({@code this.getCount()}) are intercepted, too.</li>
 *   <li>Every call of a method that can be overridden is counted, and the arguments of the first
 *       {@value #MAX_RECORDED_CALLS} calls per method are recorded (arrays as copies, other objects as references),
 *       see {@link #callCount(MethodWrapper)}, {@link #calls(MethodWrapper)} and
 *       {@link #assertCalled(MethodWrapper, int)}.</li>
 *   <li>Not possible: {@code final} classes and methods, records, enums, sealed classes, {@code static} and
 *       {@code private} methods, constructors, and attributes read directly by student code.</li>
 * </ul>
 *
 * @param <T> the type of the class being mocked
 */
public final class Mock<T> {

    /** Calls per method whose arguments are kept; further calls are only counted. */
    public static final int MAX_RECORDED_CALLS = 1000;

    private final ClassWrapper<T> classWrapper;
    private final Class<T> base;
    private final boolean recording;
    private final Map<String, Replacement> overrides = new ConcurrentHashMap<>();
    private final Map<String, Recorded> recorded = new ConcurrentHashMap<>();
    private final InvocationHandler handler = this::handle;
    private volatile boolean othersReturnDefaults;
    private Object obj;

    /** A method replaced by a function of the test. */
    private record Replacement(MethodWrapper<?, ?> wrapper, Function<Object[], Object> function) {
    }

    /** The calls of one method: their number and the arguments of the first {@value #MAX_RECORDED_CALLS}. */
    private static final class Recorded {
        private long count;
        private final List<Object[]> first = new ArrayList<>();

        synchronized void add(Object[] args) {
            count++;
            if (first.size() < MAX_RECORDED_CALLS) {
                first.add(Invocations.copyArrays(args));
            }
        }

        synchronized long count() {
            return count;
        }

        synchronized List<Object[]> first() {
            return new ArrayList<>(first);
        }
    }

    Mock(ClassWrapper<T> classWrapper) {
        this(classWrapper, true);
    }

    /**
     * @param recording whether calls are recorded; off for objects whose mock nobody can ask
     *                  ({@link ClassWrapper#newObjOverriding(Map)}, {@link ClassWrapper#newObjWithoutConstructor()})
     */
    Mock(ClassWrapper<T> classWrapper, boolean recording) {
        this.classWrapper = classWrapper;
        this.recording = recording;
        classWrapper.verifyExistence(true);
        this.base = classWrapper.getClazz();
        String reason = base.isEnum() ? "enum"
                : base.isRecord() ? "record"
                : base.isSealed() ? "sealed"
                : !base.isInterface() && Modifier.isFinal(base.getModifiers()) ? "final"
                : null;
        if (reason != null) {
            fail(Messages.MOCK_CLASS_NOT_OVERRIDABLE.format(classWrapper.getExpectedName(), reason));
        }
    }

    /**
     * Replaces a method: every call of it runs {@code function} instead of the student's code.
     * The function gets the arguments converted to the parameter types the wrapper expects (e.g. an {@code int}
     * as {@link Integer}, also if the student declared {@code long}). Its result is converted to the student's
     * return type (numeric types are converted, e.g. an {@code Integer} for a {@code double} method) and ignored
     * for {@code void} methods. If the function throws (or returns {@code null} for a primitive return type),
     * the call fails like a call that throws. The function runs as test code: Ares does not restrict it, but it
     * is called from student code, so keep it to plain computations.
     *
     * <p>A method that is missing fails with the usual message. A method that cannot be overridden
     * ({@code final}, {@code static}, {@code private}, or one of another class) fails with a readable message.</p>
     *
     * @param method a method of the mocked class or of one of its superclasses or interfaces
     * @param function computes the return value from the arguments
     * @return this mock
     */
    public Mock<T> override(MethodWrapper<?, ?> method, Function<Object[], Object> function) {
        Objects.requireNonNull(function, "function");
        overrides.put(checkedKey(method), new Replacement(method, function));
        return this;
    }

    /**
     * Replaces a method by one that always returns {@code value}.
     *
     * @param method a method of the mocked class or of one of its superclasses or interfaces
     * @param value the value to return (converted like the result of {@link #override(MethodWrapper, Function)})
     * @return this mock
     */
    public Mock<T> returning(MethodWrapper<?, ?> method, Object value) {
        return override(method, args -> value);
    }

    /**
     * Makes this a full mock: methods without override return their default value ({@code 0}, {@code false},
     * {@code null}) instead of running the student's code. Their calls are still recorded.
     * {@code equals}, {@code hashCode} and {@code toString} without override behave like those of {@link Object}
     * (identity), so the object still works in lists, sets and messages.
     *
     * @return this mock
     */
    public Mock<T> othersReturnDefaults() {
        othersReturnDefaults = true;
        return this;
    }

    /**
     * The object of the mock, created on the first call (without running a constructor) and the same afterwards.
     * Overrides added later apply to it as well.
     *
     * @return the object, an instance of a generated subclass of the mocked class
     */
    public Object obj() {
        if (obj == null) {
            try {
                Class<?> type = classWrapper.mockSubclass().type();
                Object created = MockClasses.allocate(type);
                Field field = type.getField(MockClasses.HANDLER_FIELD);
                field.setAccessible(true);
                field.set(created, handler);
                obj = created;
            }
            catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
                return fail(Messages.CLASS_SUBCLASS_INSTANTIATION_FAILED.format(classWrapper.getExpectedName(),
                        Invocations.describe(e)), e);
            }
        }
        return obj;
    }

    /**
     * How often a method was called on {@link #obj()} (or on a clone of it).
     *
     * @param method a method of the mocked class or of one of its superclasses or interfaces
     * @return the number of calls
     */
    public long callCount(MethodWrapper<?, ?> method) {
        Recorded calls = recorded.get(checkedKey(method));
        return calls == null ? 0 : calls.count();
    }

    /**
     * The recorded calls of a method on {@link #obj()}, in call order: the first {@value #MAX_RECORDED_CALLS}
     * calls, see {@link #callCount(MethodWrapper)} for the number of all calls. Arrays were copied when the call
     * was recorded; other objects are the ones passed.
     *
     * @param method a method of the mocked class or of one of its superclasses or interfaces
     * @return the arguments of each call, converted to the parameter types the wrapper expects
     */
    public List<Object[]> calls(MethodWrapper<?, ?> method) {
        Recorded calls = recorded.get(checkedKey(method));
        if (calls == null) {
            return new ArrayList<>();
        }
        Class<?>[] expected = method.getParamTypes();
        List<Object[]> result = new ArrayList<>();
        for (Object[] args : calls.first()) {
            result.add(convertArguments(args, expected));
        }
        return result;
    }

    /**
     * Fails unless the method was called exactly {@code times} times on {@link #obj()}.
     * The message shows the arguments of the recorded calls.
     *
     * @param method a method of the mocked class or of one of its superclasses or interfaces
     * @param times the expected number of calls
     */
    public void assertCalled(MethodWrapper<?, ?> method, int times) {
        assertCalled(method, times, null);
    }

    /**
     * Like {@link #assertCalled(MethodWrapper, int)}, but the text of {@code context} is put in front of the
     * failure message, e.g. which call of the method under test should have called this method.
     *
     * @param method a method of the mocked class or of one of its superclasses or interfaces
     * @param times the expected number of calls
     * @param context supplies the text shown in front of the failure message (may be {@code null})
     */
    public void assertCalled(MethodWrapper<?, ?> method, int times, Supplier<String> context) {
        Recorded calls = recorded.get(checkedKey(method));
        long count = calls == null ? 0 : calls.count();
        if (count == times) {
            return;
        }
        String message = Messages.MOCK_CALL_COUNT.format(method.actualToString(),
                method.getParentClassWrapper().getExpectedName(), times, count);
        if (count > 0) {
            // the arguments as the student passed them: a value that does not fit the type the wrapper expects
            // (e.g. 2.5 for a student's double where int is expected) must not replace this failure
            String list = calls.first().stream()
                    .limit(Invocations.MAX_ELEMENTS)
                    .map(args -> "(" + Invocations.describeArguments(args) + ")")
                    .collect(Collectors.joining(", "));
            long shown = Math.min(count, Invocations.MAX_ELEMENTS);
            String more = count > shown ? ", … (+" + (count - shown) + ")" : "";
            message += "\n" + Messages.MOCK_RECORDED_CALLS.format(list + more);
        }
        fail(Invocations.withContext(context, message));
    }

    /**
     * Fails if the method was called on {@link #obj()}.
     *
     * @param method a method of the mocked class or of one of its superclasses or interfaces
     */
    public void assertNotCalled(MethodWrapper<?, ?> method) {
        assertCalled(method, 0, null);
    }

    /**
     * Forgets all recorded calls, e.g. after preparing the object.
     */
    public void resetCalls() {
        recorded.clear();
    }

    /**
     * Checks that a method belongs to the mocked class and can be intercepted (otherwise its calls could neither
     * be replaced nor recorded) and returns its key.
     */
    private String checkedKey(MethodWrapper<?, ?> method) {
        method.verifyExistence(true);
        Method actual = method.actualMethod();
        String signature = method.actualToString();
        String owner = method.getParentClassWrapper().getExpectedName();
        if (!actual.getDeclaringClass().isAssignableFrom(base)) {
            fail(Messages.MOCK_METHOD_NOT_IN_CLASS.format(signature, owner, classWrapper.getExpectedName()));
        }
        // the class itself (or a class between) may redeclare the method, e.g. as final
        Method effective = MockClasses.declaredInClassChain(base, actual.getName(), actual.getParameterTypes());
        for (Method m : effective == null ? List.of(actual) : List.of(actual, effective)) {
            String reason = notOverridableReason(m);
            if (reason != null) {
                fail(Messages.MOCK_METHOD_NOT_OVERRIDABLE.format(signature, m == actual ? owner
                        : m.getDeclaringClass().getSimpleName(), reason));
            }
        }
        return MethodWrapper.signatureKey(actual);
    }

    private String notOverridableReason(Method m) {
        int modifiers = m.getModifiers();
        if (Modifier.isStatic(modifiers)) return "static";
        if (Modifier.isPrivate(modifiers)) return "private";
        if (Modifier.isFinal(modifiers)) return "final";
        boolean packagePrivate = !Modifier.isPublic(modifiers) && !Modifier.isProtected(modifiers);
        if (packagePrivate && (!classWrapper.mockSubclass().samePackage()
                || !m.getDeclaringClass().getPackageName().equals(base.getPackageName()))) {
            return "package-private";
        }
        return null;
    }

    /** Called for every intercepted method of {@link #obj()} (and of clones of it). */
    private Object handle(Object self, Method method, Object[] args) throws Throwable {
        Object[] actualArgs = args == null ? new Object[0] : args;
        MockClasses.MethodInfo info = classWrapper.mockSubclass().info(method);
        if (recording) {
            recorded.computeIfAbsent(info.key(), k -> new Recorded()).add(actualArgs);
        }
        Replacement replacement = overrides.get(info.key());
        if (replacement != null) {
            return apply(replacement, method, actualArgs);
        }
        if (othersReturnDefaults) {
            return switch (info.objectMethod()) {
                case EQUALS -> self == actualArgs[0];
                case HASH_CODE -> System.identityHashCode(self);
                case TO_STRING -> base.getSimpleName() + "@" + Integer.toHexString(System.identityHashCode(self));
                case NONE -> MockClasses.defaultValue(method.getReturnType());
            };
        }
        if (!info.implemented()) {
            return fail(Messages.MOCK_ABSTRACT_NOT_OVERRIDDEN.format(method.getName(), classWrapper.getExpectedName()));
        }
        // the student's implementation, like super.method(...), on the object actually called
        return info.superCall().bindTo(self).invokeWithArguments(actualArgs);
    }

    private Object apply(Replacement replacement, Method method, Object[] args) {
        Object result = replacement.function().apply(convertArguments(args, replacement.wrapper().getParamTypes()));
        Class<?> returnType = method.getReturnType();
        if (returnType == void.class) {
            return null;
        }
        if (result == null && returnType.isPrimitive()) {
            throw new IllegalStateException("The override of " + replacement.wrapper().actualToString()
                    + " returned null, but the method returns " + returnType.getSimpleName() + ".");
        }
        return Utils.safeCast(result, returnType, true);
    }

    private static Object[] convertArguments(Object[] args, Class<?>[] expectedTypes) {
        Object[] converted = args.clone();
        if (expectedTypes.length != converted.length) {
            return converted;
        }
        for (int i = 0; i < converted.length; i++) {
            if (converted[i] != null) {
                converted[i] = Utils.safeCast(converted[i], expectedTypes[i], true);
            }
        }
        return converted;
    }
}
