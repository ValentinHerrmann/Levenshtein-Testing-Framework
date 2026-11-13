package io.github.valentinherrmann.levenshtein;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import java.util.function.Predicate;

import static io.github.valentinherrmann.levenshtein.Utils.isNumericDeviation;
import static io.github.valentinherrmann.levenshtein.Utils.safeCast;
import static io.github.valentinherrmann.levenshtein.Utils.toWrapperType;

/**
 * Tests the getter and/or setter of one attribute with several sample values in up to three subtests:
 * setter -> attribute, attribute -> getter and setter -> getter. Every applicable subtest runs with every sample
 * value; the first failing case is reported as {@link Failure} block, followed by the number of failed cases.
 *
 * <p>Values are compared by content ({@link Objects#deepEquals(Object, Object)}, so arrays work), or by identity
 * for reference values if {@link LevenshteinSettings#isGettersSettersRequireSameObject()} is set. Generated sample
 * values fit the student's numeric types (e.g. whole numbers if the student declared {@code int} instead of
 * {@code double}); sample values passed by the author are used as they are.</p>
 *
 * @see ClassWrapper#testGettersSetters(AttributeWrapper, MethodWrapper[])
 */
final class GettersSettersTest {

    /** Marks a value that cannot be converted. */
    private static final Object MISFIT = new Object();

    private final ClassWrapper<?> classWrapper;
    private final AttributeWrapper<?, ?> attribute;
    private final MethodWrapper<?, ?> getter;
    private final MethodWrapper<?, ?> setter;
    private final Predicate<Object> setterCondition;

    /**
     * The values to set; {@code null} if none can be generated for the attribute's type (then only the
     * current value is compared with the getter).
     */
    private final List<Object> samples;

    private Failure firstFailure;
    private int failedCases;
    private int totalCases;

    @SuppressWarnings("unchecked")
    GettersSettersTest(ClassWrapper<?> classWrapper, AttributeWrapper<?, ?> attribute, List<?> sampleValues,
                       Predicate<?> setterCondition, MethodWrapper<?, ?>... gettersSetters) {
        if (attribute == null) {
            throw new IllegalArgumentException("testGettersSetters needs an attribute wrapper");
        }
        MethodWrapper<?, ?> foundGetter = null;
        MethodWrapper<?, ?> foundSetter = null;
        for (MethodWrapper<?, ?> m : gettersSetters == null ? new MethodWrapper<?, ?>[0] : gettersSetters) {
            int paramCount = m == null ? -1 : m.getParamTypes().length;
            if (paramCount == 0 && foundGetter == null) {
                foundGetter = m;
            }
            else if (paramCount == 1 && foundSetter == null) {
                foundSetter = m;
            }
            else {
                throw new IllegalArgumentException("testGettersSetters takes at most one getter (no parameters) and one "
                        + "setter (one parameter), but got " + (m == null ? "null" : m.expectedToString()));
            }
        }
        if (foundGetter == null && foundSetter == null) {
            throw new IllegalArgumentException("testGettersSetters needs a getter (no parameters) and/or a setter (one parameter)");
        }
        if (setterCondition != null && foundSetter == null) {
            throw new IllegalArgumentException("testGettersSetters: a setter condition needs a setter");
        }
        if (attribute.getExpectedType() == null) {
            // the attribute's type is a student class that is missing: the attribute cannot be used
            attribute.verifyExistence(true);
        }
        this.classWrapper = classWrapper;
        this.attribute = attribute;
        this.getter = foundGetter;
        this.setter = foundSetter;
        this.setterCondition = (Predicate<Object>) setterCondition;

        List<Object> values = sampleValues != null ? new ArrayList<>(sampleValues) : generateFittingSamples();
        if (values == null && setter != null) {
            throw new IllegalArgumentException("testGettersSetters cannot generate sample values for the type "
                    + attribute.getExpectedType().getSimpleName() + " of attribute " + attribute.getExpectedName()
                    + "; pass them with testGettersSetters(attribute, List.of(...), ...)");
        }
        if (values != null) {
            if (rejectsNull()) {
                values.removeIf(Objects::isNull);
            }
            if (values.isEmpty()) {
                throw new IllegalArgumentException("testGettersSetters needs at least one (usable) sample value");
            }
        }
        this.samples = values;
    }

    /**
     * Five different values (the first one not the type's default) to catch hard-coded getters and setters
     * that ignore their argument; reference types additionally get {@code null}.
     *
     * @return the sample values, or {@code null} if the type is not supported
     */
    static List<Object> generateSamples(Class<?> type) {
        Class<?> t = toWrapperType(type);
        List<Object> values;
        if (t == Integer.class) values = List.of(7, -3, 42, 1, 0);
        else if (t == Long.class) values = List.of(7L, -3L, 42L, 1L, 0L);
        else if (t == Short.class) values = List.of((short) 7, (short) -3, (short) 42, (short) 1, (short) 0);
        else if (t == Byte.class) values = List.of((byte) 7, (byte) -3, (byte) 42, (byte) 1, (byte) 0);
        else if (t == Double.class) values = List.of(2.5, -1.25, 42.0, 0.5, 0.0);
        else if (t == Float.class) values = List.of(2.5f, -1.25f, 42.0f, 0.5f, 0.0f);
        else if (t == Character.class) values = List.of('a', 'Z', '7', 'x', '_');
        else if (t == Boolean.class) values = List.of(true, false, true, false, true);
        else if (t == String.class) values = List.of("Alpha", "beta", "", "Gamma 42", "x");
        else if (t != null && t.isEnum() && t.getEnumConstants().length > 0) {
            Object[] constants = t.getEnumConstants();
            List<Object> cycled = new ArrayList<>();
            for (int i = 0; i < Math.max(5, constants.length); i++) {
                cycled.add(constants[i % constants.length]);
            }
            values = cycled;
        }
        else return null;
        List<Object> result = new ArrayList<>(values);
        if (!type.isPrimitive()) {
            result.add(null);
        }
        return result;
    }

    /**
     * Generates sample values for the expected type that survive the student's numeric types unchanged, so a
     * deviating numeric type (e.g. {@code float} or {@code int} instead of {@code double}) only costs the structural
     * point. If the values for the expected type do not fit, the values for one of the student's types are used
     * (converted to the expected type); if none fit completely, the fitting values for the expected type remain.
     *
     * @return the sample values, or {@code null} if the type is not supported
     */
    private List<Object> generateFittingSamples() {
        Class<?> expected = attribute.getExpectedType();
        List<Object> generated = generateSamples(expected);
        if (generated == null) {
            return null;
        }
        List<Class<?>> numericDeviations = studentTypes().filter(t -> isNumericDeviation(expected, t)).toList();
        if (numericDeviations.isEmpty() || generated.stream().allMatch(v -> fits(v, numericDeviations))) {
            return generated;
        }
        for (Class<?> studentType : numericDeviations) {
            List<Object> candidate = generateSamples(studentType);
            if (candidate == null) {
                continue;
            }
            List<Object> converted = new ArrayList<>();
            for (Object v : candidate) {
                converted.add(convert(v, expected));
            }
            if (!converted.contains(MISFIT) && converted.stream().allMatch(v -> fits(v, numericDeviations))) {
                return converted;
            }
        }
        List<Object> fitting = new ArrayList<>(generated);
        fitting.removeIf(v -> !fits(v, numericDeviations));
        return fitting;
    }

    private static Object convert(Object value, Class<?> type) {
        try {
            return value == null ? null : safeCast(value, type, true);
        }
        catch (AssertionError e) {
            return MISFIT;
        }
    }

    /**
     * @return whether the value comes back unchanged after a conversion into each of the types
     */
    private boolean fits(Object value, List<Class<?>> types) {
        if (value == null) {
            return true;
        }
        for (Class<?> type : types) {
            Object back = convert(convert(value, type), attribute.getExpectedType());
            if (back == MISFIT || !Objects.equals(back, value)) {
                return false;
            }
        }
        return true;
    }

    /**
     * @return the types of the student's field, getter return value and setter parameter that exist
     */
    private Stream<Class<?>> studentTypes() {
        return Stream.of(attribute.actualFieldType(),
                        getter == null ? null : getter.actualReturnType(),
                        setter == null ? null : setter.getActualParamTypes()[0])
                .filter(Objects::nonNull);
    }

    /**
     * @return whether one of the student's types involved is primitive, so {@code null} cannot be used
     */
    private boolean rejectsNull() {
        Class<?> field = attribute.actualFieldType();
        Class<?> getterType = getter == null ? null : getter.actualReturnType();
        Class<?> setterType = setter == null ? null : setter.getActualParamTypes()[0];
        return attribute.getExpectedType().isPrimitive()
                || (field != null && field.isPrimitive())
                || (getterType != null && getterType.isPrimitive())
                || (setterType != null && setterType.isPrimitive());
    }

    /**
     * Runs all applicable subtests with all sample values and fails with the {@link Failure} block of the first
     * failing case (the first stack overflow or out of memory error if there is one, see
     * {@link Failure#isResourceError(Throwable)}), followed by the number of failed cases.
     */
    void run() {
        if (setter != null) {
            setterToAttribute();
        }
        if (getter != null) {
            attributeToGetter();
        }
        if (getter != null && setter != null) {
            setterToGetter();
        }
        if (firstFailure != null) {
            throw firstFailure.cases(failedCases, totalCases).error();
        }
    }

    /**
     * Counts a checked case and remembers the first failure, or the first stack overflow or out of memory error.
     *
     * @param failure the failure of the case, or {@code null} if it passed
     */
    private void record(Failure failure) {
        totalCases++;
        if (failure != null) {
            failedCases++;
            if (firstFailure == null || (!firstFailure.isResourceError() && failure.isResourceError())) {
                firstFailure = failure;
            }
        }
    }

    private String scenario(Messages label) {
        return Messages.GETTERS_SETTERS_SCENARIO.format(label.get(), attribute.getExpectedName());
    }

    private void setterToAttribute() {
        Object obj = classWrapper.getObj(true);
        String scenario = scenario(Messages.GETTERS_SETTERS_SETTER_TO_ATTRIBUTE);
        for (Object value : samples) {
            Object before = normalize(attribute.getValue(obj));
            Throwable thrown = callSetter(obj, value);
            record(checkSet(scenario, value, before, thrown, normalize(attribute.getValue(obj))));
        }
    }

    private void attributeToGetter() {
        Object obj = classWrapper.getObj(true);
        String scenario = scenario(Messages.GETTERS_SETTERS_ATTRIBUTE_TO_GETTER);
        if (samples == null || attribute.isFinal()) {
            // no values can be set: compare the current value only
            record(checkReturned(scenario, normalize(attribute.getValue(obj)), callGetter(obj)));
            return;
        }
        for (Object value : samples) {
            attribute.setValue(obj, value);
            record(checkReturned(scenario, normalize(value), callGetter(obj)));
        }
    }

    private void setterToGetter() {
        Object obj = classWrapper.getObj(true);
        String scenario = scenario(Messages.GETTERS_SETTERS_SETTER_TO_GETTER);
        for (Object value : samples) {
            GetterResult before = callGetter(obj);
            Throwable thrown = callSetter(obj, value);
            GetterResult after = callGetter(obj);
            Throwable getterThrown = before.thrown() != null ? before.thrown() : after.thrown();
            if (getterThrown != null) {
                // nothing to compare: the case fails with the exception of the getter
                record(Failure.of(getter).scenario(scenario).input(name(setter) + "(" + show(value) + ")")
                        .thrown(getterThrown));
                continue;
            }
            record(checkSet(scenario, value, normalize(before.value()), thrown, normalize(after.value())));
        }
    }

    /**
     * Checks the outcome of a setter call: a valid value must be stored, an invalid one must be rejected.
     *
     * @return the failure, or {@code null} if the case passed
     */
    private Failure checkSet(String scenario, Object value, Object before, Throwable thrown, Object after) {
        Failure failure = Failure.of(setter).scenario(scenario).input(name(setter) + "(" + show(value) + ")");
        if (isValid(value)) {
            if (thrown != null) {
                return failure.expected(value).thrown(thrown);
            }
            if (!same(after, normalize(value))) {
                return failure.expected(value).actual(after);
            }
        }
        else if (thrown == null && !same(after, before)) {
            return failure.expectedText(Messages.GETTERS_SETTERS_EXPECT_REJECTED.format(show(before))).actual(after);
        }
        return null;
    }

    /**
     * @return the failure, or {@code null} if the getter returned the expected value
     */
    private Failure checkReturned(String scenario, Object expected, GetterResult returned) {
        if (returned.thrown() != null) {
            return getterFailure(scenario, expected).thrown(returned.thrown());
        }
        Object actual = normalize(returned.value());
        if (!same(actual, expected)) {
            return getterFailure(scenario, expected).actual(actual);
        }
        return null;
    }

    private Failure getterFailure(String scenario, Object attributeValue) {
        return Failure.of(getter).scenario(scenario)
                .input(attribute.getExpectedName() + " = " + show(attributeValue)).expected(attributeValue);
    }

    /**
     * Calls the setter; an exception of the student code is returned instead of thrown.
     */
    private Throwable callSetter(Object obj, Object value) {
        setter.verifyExistence(true);
        Class<?> paramType = setter.getActualParamTypes()[0];
        Object argument = value == null ? null : safeCast(value, paramType, true); // a misfit fails the test
        try {
            setter.invokeRaw(obj, argument);
            return null;
        }
        catch (Throwable t) {
            rethrowIfCriticalExceptAssertion(t);
            return t;
        }
    }

    /**
     * Calls the getter; an exception of the student code is returned instead of thrown.
     */
    private GetterResult callGetter(Object obj) {
        getter.verifyExistence(true);
        try {
            return new GetterResult(getter.invokeRaw(obj), null);
        }
        catch (Throwable t) {
            rethrowIfCriticalExceptAssertion(t);
            return new GetterResult(null, t);
        }
    }

    /**
     * Like {@link Invocations#rethrowIfCritical(Throwable)}, but an {@link AssertionError} is kept: the getter or
     * setter exists (checked before the call), so it comes from the student code (e.g. {@code assert}) and is
     * reported as the outcome of the case instead of aborting all subtests.
     */
    private static void rethrowIfCriticalExceptAssertion(Throwable t) {
        if (!(t instanceof AssertionError)) {
            Invocations.rethrowIfCritical(t);
        }
    }

    /** The value returned by the getter, or the exception it threw. */
    private record GetterResult(Object value, Throwable thrown) {
    }

    /**
     * Evaluates the setter condition; a condition that throws (e.g. on {@code null}) marks the value invalid.
     */
    private boolean isValid(Object value) {
        if (setterCondition == null) {
            return true;
        }
        try {
            return setterCondition.test(value);
        }
        catch (ClassCastException e) {
            throw new IllegalArgumentException("testGettersSetters: the setter condition cannot handle the sample value "
                    + show(value) + " of type " + value.getClass().getSimpleName(), e);
        }
        catch (RuntimeException e) {
            return false;
        }
    }

    /**
     * Converts a value to the attribute's expected type, so e.g. {@code int} and {@code double} values compare equal.
     */
    private Object normalize(Object value) {
        return value == null ? null : safeCast(value, attribute.getExpectedType(), true);
    }

    private static String name(MethodWrapper<?, ?> m) {
        String actual = m.getActualName();
        return actual != null ? actual : m.getExpectedName();
    }

    /**
     * Compares by content (arrays element by element), or by identity for reference values if
     * {@link LevenshteinSettings#isGettersSettersRequireSameObject()} is set.
     */
    private static boolean same(Object a, Object b) {
        if (LevenshteinSettings.isGettersSettersRequireSameObject() && a != null && b != null
                && !isValueType(a) && !isValueType(b)) {
            return a == b;
        }
        return Objects.deepEquals(a, b);
    }

    /**
     * @return whether the value is always compared by value (primitive wrappers, strings, enums)
     */
    private static boolean isValueType(Object value) {
        return value instanceof Number || value instanceof Character || value instanceof Boolean
                || value instanceof String || value instanceof Enum<?>;
    }

    private static String show(Object value) {
        return Invocations.describeValue(value);
    }
}
