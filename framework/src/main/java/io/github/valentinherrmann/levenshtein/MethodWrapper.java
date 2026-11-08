package io.github.valentinherrmann.levenshtein;


import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static io.github.valentinherrmann.levenshtein.Utils.*;
import static io.github.valentinherrmann.levenshtein.WrapperProperty.Existence.*;
import static org.assertj.core.api.Assertions.fail;

/**
 * Wrapper for class methods that verifies their existence, name, return type, parameter types, and modifiers
 * using Levenshtein distance for fuzzy name matching.
 *
 * @param <T> the type of the class containing the method
 * @param <R> the expected return type of the method
 */
public class MethodWrapper<T, R> extends Wrapper<T>
{
    /**
     * The expected parameter types for the method.
     */
    private final Class<?>[] paramTypes;

    /**
     * Wrapper for the expected and actual parameter types.
     */
    private final WrapperProperty<Class<?>[]> params;

    /**
     * Wrapper for the expected and actual return type.
     */
    private final WrapperProperty<Class<?>> returnType;

    /**
     * The actual method found via reflection.
     */
    private Method method;

    /**
     * Constructs a new MethodWrapper for verifying a class method.
     *
     * @param parentClass the class wrapper containing this method
     * @param expectedName the expected name of the method
     * @param expectedReturnType the expected return type of the method
     * @param paramTypes the expected parameter types for the method
     * @param modifiers the expected modifiers (e.g., "public", "static")
     */
    public MethodWrapper(ClassWrapper<T> parentClass, String expectedName, Class<R> expectedReturnType, Class<?>[] paramTypes, String... modifiers) {
        super(parentClass, expectedName, modifiers);
        this.paramTypes = paramTypes == null ? new Class<?>[0] : paramTypes.clone();
        this.params = new WrapperProperty<>(this.paramTypes);
        this.returnType = new WrapperProperty<>(expectedReturnType);
    }

    /**
     * Constructs a new MethodWrapper for a method with no parameters.
     *
     * @param parentClass the class wrapper containing this method
     * @param expectedName the expected name of the method
     * @param expectedReturnType the expected return type of the method
     * @param modifiers the expected modifiers (e.g., "public", "static")
     */
    public MethodWrapper(ClassWrapper<T> parentClass, String expectedName, Class<R> expectedReturnType, String... modifiers) {
        this(parentClass, expectedName, expectedReturnType, new Class<?>[0], modifiers);
    }

    @Override
    public void verifyExistence(boolean throwAssertion)
    {
        super.verifyExistence(Messages.METHOD_NOT_IMPLEMENTED.format(this.expectedToString(), getParentClassWrapper().name.expected), throwAssertion);
    }

    @Override
    protected boolean legacyReportsUnexpectedStatic() {
        return true;
    }

    @Override
    protected int relevantModifierMask() {
        return Modifier.methodModifiers();
    }

    /**
     * Abstract methods of interfaces are implicitly {@code abstract}.
     */
    @Override
    protected int implicitModifiers() {
        Class<T> parent = getParentClassWrapper().getClazz();
        return super.implicitModifiers() | (parent != null && parent.isInterface() ? Modifier.ABSTRACT : 0);
    }

    /**
     * @return {@code "name(Type1,Type2)"} of the expected signature; used to keep other wrappers' members
     */
    String expectedSignatureKey() {
        return signatureKey(name.expected, paramTypes);
    }

    /**
     * @return {@code "name(Type1,Type2)"} of the method; identifies a method within a class hierarchy
     */
    static String signatureKey(Method m) {
        return signatureKey(m.getName(), m.getParameterTypes());
    }

    /**
     * @return {@code "name(Type1,Type2)"} with the binary names of the parameter types
     */
    static String signatureKey(String name, Class<?>[] parameterTypes) {
        StringBuilder key = new StringBuilder(name).append('(');
        for (int i = 0; i < parameterTypes.length; i++) {
            if (i > 0) {
                key.append(',');
            }
            key.append(parameterTypes[i].getName());
        }
        return key.append(')').toString();
    }

    @Override
    protected void findWithDeviation()
    {
        Class<T> clazz = getParentClassWrapper().getClazz();
        if(clazz != null) {
            try // First try exact match
            {
                method = clazz.getDeclaredMethod(name.expected, paramTypes);
                name.actual = method.getName();
                name.existence = EXACT;
                params.existence = EXACT;
            } catch (NoSuchMethodException | LinkageError e) {
                findClosestCandidate(clazz);
            }
        }
        if (this.method == null)
        {
            existence = name.existence = params.existence = returnType.existence = modifiers.existence = MISSING;
        }
        else
        {
            params.actual = method.getParameterTypes();
            verifyModifiers(method.getModifiers());
            verifyType(returnType, method.getReturnType());
        }
    }

    /**
     * Finds the closest method (by Levenshtein distance, ties broken by name) with matching parameters.
     * Synthetic and bridge methods are ignored, as are methods that another wrapper of the same class
     * expects exactly (so e.g. a missing {@code getMaxSpeed()} cannot take {@code getMinSpeed()}).
     */
    private void findClosestCandidate(Class<T> clazz) {
        Set<String> claimedByOthers = getParentClassWrapper().getMethodWrappers().stream()
                .filter(w -> w != this && w instanceof MethodWrapper<?, ?>)
                .map(w -> ((MethodWrapper<?, ?>) w).expectedSignatureKey())
                .collect(Collectors.toSet());
        Method[] declared;
        try {
            declared = clazz.getDeclaredMethods();
        }
        catch (LinkageError e) {
            return;
        }
        Method best = Arrays.stream(declared)
                .filter(m -> !m.isSynthetic() && !m.isBridge())
                .filter(m -> !claimedByOthers.contains(signatureKey(m)))
                .filter(m -> parametersMatch(m.getParameterTypes()) != MISSING)
                .filter(m -> isNameWithinDeviation(name.expected, m.getName(), LevenshteinSettings.getMethodNameDeviationThreshold()))
                .min(Comparator.<Method>comparingInt(m -> levenshteinDistance(name.expected, m.getName()))
                        .thenComparingInt(m -> parameterDeviation(m.getParameterTypes()))
                        .thenComparing(Method::getName))
                .orElse(null);
        if (best != null) {
            method = best;
            name.actual = best.getName();
            name.existence = best.getName().equals(name.expected) ? EXACT : DEVIATES;
            params.existence = parametersMatch(best.getParameterTypes());
        }
    }

    /**
     * @return EXACT for identical parameter types, DEVIATES if they only differ in primitive/wrapper types
     *         (e.g. {@code int} vs. {@code Integer}), MISSING otherwise
     */
    private WrapperProperty.Existence parametersMatch(Class<?>[] actualParams) {
        return parameterDeviation(actualParams) < 0 ? MISSING : parameterDeviation(actualParams) == 0 ? EXACT : DEVIATES;
    }

    /**
     * @return -1 if the parameters cannot match, otherwise a score where lower is closer: 0 for identical types,
     *         1 per primitive/wrapper difference and 2 per numeric difference (e.g. {@code long} vs. {@code int})
     */
    private int parameterDeviation(Class<?>[] actualParams) {
        if (actualParams.length != paramTypes.length) {
            return -1;
        }
        int score = 0;
        for (int i = 0; i < paramTypes.length; i++) {
            if (actualParams[i].equals(paramTypes[i])) {
                continue;
            }
            if (toWrapperType(actualParams[i]).equals(toWrapperType(paramTypes[i]))) {
                score += 1;
            }
            else if (isNumericDeviation(paramTypes[i], actualParams[i])) {
                score += 2;
            }
            else {
                return -1;
            }
        }
        return score;
    }

    @Override
    protected void parseExistence() {
        parseExistence(name, params, returnType, modifiers);
    }

    /**
     * Invokes the method on the class's default instance (or null for static methods).
     * An exception thrown by the student code fails the test with the exception type, its message and the
     * argument values.
     *
     * <p>Pass a single array argument as {@code (Object) array}: otherwise Java spreads an object array
     * (e.g. {@code String[]}) into several arguments. A wrong number of arguments is reported as
     * {@link IllegalArgumentException}.</p>
     *
     * @param params values for the method's parameters
     * @return the return value of the method, safely cast to R
     * @see #invokeWithContext(Supplier, Object...)
     */
    public R invoke(Object... params)
    {
        return invoke(null, null, params);
    }

    /**
     * Like {@link #invoke(Object...)}, but if the student code throws, the text of {@code context} is put in
     * front of the failure message, e.g. the scenario or the seed of generated inputs:
     * <pre>{@code
     * car.calculateCost().invokeWithContext(() -> "Scenario " + i + ", seed " + seed, years);
     * }</pre>
     * The supplier is only evaluated if the call fails.
     *
     * <p>Pass a single array argument as {@code (Object) array}: otherwise Java spreads an object array
     * into several arguments.</p>
     *
     * @param context supplies the text shown in front of the failure message (may be {@code null})
     * @param params values for the method's parameters
     * @return the return value of the method, safely cast to R
     */
    public R invokeWithContext(Supplier<String> context, Object... params)
    {
        return invoke(null, context, params);
    }

    /**
     * Invokes the method on a specific object instance.
     * An exception thrown by the student code fails the test with the exception type, its message and the
     * argument values; use {@link #invokeExpectingException(Class, Object, Object...)} if an exception is the
     * expected outcome.
     *
     * <p>Pass a single array argument as {@code (Object) array}: otherwise Java spreads an object array
     * (e.g. {@code String[]}) into several arguments. A wrong number of arguments is reported as
     * {@link IllegalArgumentException}.</p>
     *
     * @param obj the object to invoke the method on (null uses default instance or null for static methods)
     * @param params values for the method's parameters
     * @return the return value of the method, safely cast to R
     * @see #invokeOnSpecificObjectWithContext(Supplier, Object, Object...)
     */
    public R invokeOnSpecificObject(Object obj, Object... params)
    {
        return invoke(obj, null, params);
    }

    /**
     * Like {@link #invokeOnSpecificObject(Object, Object...)}, but if the student code throws, the text of
     * {@code context} is put in front of the failure message (e.g. the scenario or the seed of generated inputs).
     * The supplier is only evaluated if the call fails.
     *
     * <p>Pass a single array argument as {@code (Object) array}: otherwise Java spreads an object array
     * into several arguments.</p>
     *
     * @param context supplies the text shown in front of the failure message (may be {@code null})
     * @param obj the object to invoke the method on (null uses default instance or null for static methods)
     * @param params values for the method's parameters
     * @return the return value of the method, safely cast to R
     */
    public R invokeOnSpecificObjectWithContext(Supplier<String> context, Object obj, Object... params)
    {
        return invoke(obj, context, params);
    }

    @SuppressWarnings("unchecked")
    private R invoke(Object obj, Supplier<String> context, Object[] params)
    {
        verifyExistence(true);
        Object[] args = Invocations.checkArguments(method.getParameterTypes(), params, actualToString());
        Object[] shown = Invocations.snapshotArguments(args); // as passed, before the student code can change them
        Object val;
        try {
            val = Invocations.invoke(method, resolveTarget(obj), args);
        }
        catch (Throwable t) {
            if (t instanceof AssertionError e) {
                throw Invocations.withCallDetails(e, context, shown);
            }
            Invocations.rethrowIfCritical(t);
            String message = Messages.METHOD_INVOCATION_EXCEPTION.format(actualToString(),
                    getParentClassWrapper().name.expected, Invocations.describe(t));
            return fail(Invocations.withContext(context, Invocations.withArguments(message, shown)), t);
        }
        if (val == null || returnType.expected == null || returnType.expected == void.class) {
            return (R) val;
        }
        return (R) safeCast(val, returnType.expected, true);
    }

    /**
     * Invokes the method and expects the student code to throw an exception of the given type.
     *
     * <p>Pass a single array argument as {@code (Object) array}: otherwise Java spreads an object array
     * into several arguments.</p>
     *
     * @param expected the expected exception type (subclasses are accepted)
     * @param obj the object to invoke the method on (null uses default instance or null for static methods)
     * @param params values for the method's parameters
     * @param <X> the exception type
     * @return the thrown exception, e.g. for further assertions on its message
     */
    public <X extends Throwable> X invokeExpectingException(Class<X> expected, Object obj, Object... params) {
        verifyExistence(true);
        Object[] args = Invocations.checkArguments(method.getParameterTypes(), params, actualToString());
        Object[] shown = Invocations.snapshotArguments(args);
        Object target = resolveTarget(obj);
        String outcome;
        try {
            Invocations.invoke(method, target, args);
            outcome = Messages.NOTHING_THROWN.get();
        }
        catch (Throwable t) {
            if (expected.isInstance(t)) {
                return expected.cast(t);
            }
            Invocations.rethrowIfCritical(t);
            outcome = Invocations.describe(t);
        }
        return fail(Invocations.withArguments(Messages.EXPECTED_EXCEPTION_NOT_THROWN.format(actualToString(),
                getParentClassWrapper().name.expected, expected.getSimpleName(), outcome), shown));
    }

    /**
     * Invokes the method and passes an exception thrown by the student code on unchanged.
     * Fails with the usual message if the method is missing.
     */
    Object invokeRaw(Object obj, Object... params) throws Throwable {
        verifyExistence(true);
        return Invocations.invoke(method, resolveTarget(obj), params);
    }

    private Object resolveTarget(Object obj) {
        if (Modifier.isStatic(method.getModifiers())) {
            return null;
        }
        return obj != null ? obj : getParentClassWrapper().getObj();
    }

    /**
     * @return the expected parameter types
     */
    public Class<?>[] getParamTypes() {
        return paramTypes.clone();
    }

    /**
     * @return the parameter types of the student's method, or the expected ones if the method is missing
     */
    public Class<?>[] getActualParamTypes() {
        ensureChecked();
        return method == null ? paramTypes.clone() : method.getParameterTypes();
    }

    /**
     * @return the return type of the student's method, or {@code null} if the method is missing
     */
    Class<?> actualReturnType() {
        ensureChecked();
        return method == null ? null : method.getReturnType();
    }

    /**
     * @return the student's method, or {@code null} if it is missing
     */
    Method actualMethod() {
        ensureChecked();
        return method;
    }

    private static String typeNames(Class<?>[] types) {
        return Arrays.stream(types).map(Class::getSimpleName).collect(Collectors.joining(", "));
    }

    @Override
    public String expectedToString()
    {
        return String.format(
                "%s %s %s(%s)",
                modifiers.expected,
                returnType.expected == null ? "<missing>" : returnType.expected.getSimpleName(),
                name.expected,
                typeNames(paramTypes)
        ).trim();
    }

    @Override
    public String actualToString() {
        if(method == null) {
            return "<missing>";
        }
        return String.format(
                "%s %s %s(%s)",
                modifiers.actual,
                method.getReturnType().getSimpleName(),
                method.getName(),
                typeNames(method.getParameterTypes())
        ).trim();
    }
}
