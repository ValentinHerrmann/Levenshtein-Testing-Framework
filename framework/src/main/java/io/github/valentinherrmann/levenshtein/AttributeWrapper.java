package io.github.valentinherrmann.levenshtein;

import org.assertj.core.api.Assertions;


import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Set;
import java.util.stream.Collectors;

import static io.github.valentinherrmann.levenshtein.Utils.*;
import static io.github.valentinherrmann.levenshtein.WrapperProperty.Existence.*;
import static org.assertj.core.api.Assertions.fail;

/**
 * Wrapper for class attributes (fields) that verifies their existence, name, type, and modifiers
 * using Levenshtein distance for fuzzy matching.
 *
 * @param <T> the type of the class containing the attribute
 * @param <V> the expected type of the attribute value
 */
public class AttributeWrapper<T, V> extends Wrapper<T>
{

    /**
     * The field (Reflection object) representing the detected attribute in the class.
     */
    private Field field;

    /**
     * Stored expected and actual type of attribute and
     * information about its existence.
     */
    private final WrapperProperty<Class<?>> type;

    /**
     * Constructs a new AttributeWrapper for verifying a class attribute.
     *
     * @param parentClass the class wrapper containing this attribute
     * @param expectedName the expected name of the attribute
     * @param expectedType the expected type of the attribute
     * @param modifiers the expected modifiers (e.g., "public", "static", "final")
     */
    public AttributeWrapper(ClassWrapper<T> parentClass, String expectedName, Class<V> expectedType, String... modifiers) {
        super(parentClass, expectedName, modifiers);
        this.type = new WrapperProperty<>(expectedType);
    }



    @Override
    public void verifyExistence(boolean throwAssertion)
    {
        super.verifyExistence(Messages.ATTRIBUTE_NOT_IMPLEMENTED.format(
                name.expected, getParentClassWrapper().name.expected), throwAssertion);
    }

    /**
     * Fields of interfaces are implicitly static, so an unexpected {@code static} is only reported for classes.
     */
    @Override
    protected boolean reportUnexpectedStatic() {
        Class<T> parent = getParentClassWrapper().getClazz();
        return parent == null || !parent.isInterface();
    }

    /**
     * Retrieves the value of this attribute from a default instance.
     * Uses the parent class's default object instance.
     * To get the value from a specific instance, use {@link AttributeWrapper#getValue(Object)}.
     * Uses {@link Assertions#fail(String)} to report access failures.
     *
     * @return the attribute value
     */
    public V getValue()
    {
        return getValue(null);
    }

    /**
     * Retrieves the value of this attribute from a specific object instance.
     * If obj is null, creates or uses a default instance from the parent class.
     * Attempts direct field access, falling back to a getter ({@code get<ActualName>()} or {@code is<ActualName>()})
     * if the field cannot be accessed.
     * Uses {@link Assertions#fail(String)} to report access failures.
     *
     * @param obj the object instance to get the value from (null for static fields or default instance)
     * @return the attribute value, safely cast to type V
     */
    @SuppressWarnings("unchecked")
    public V getValue(Object obj) { // usually obj is type T, but could be a subclass
        verifyExistence(true);

        Object target = resolveTarget(obj);
        Object val;
        try {
            field.setAccessible(true);
            val = field.get(target);
        } catch (ReflectiveOperationException | RuntimeException e) {
            val = getValueViaGetter(target, e);
        }
        return (V) saveCast(val, type.expected, true);
    }

    private Object getValueViaGetter(Object target, Exception accessFailure) {
        String actualName = name.actual;
        String capitalized = Character.toUpperCase(actualName.charAt(0)) + actualName.substring(1);
        for (String getterName : new String[] {"get" + capitalized, "is" + capitalized}) {
            Method getter = findNoArgMethod(field.getDeclaringClass(), getterName);
            if (getter == null) {
                continue;
            }
            try {
                return Invocations.invoke(getter, Modifier.isStatic(getter.getModifiers()) ? null : target);
            }
            catch (Throwable t) {
                Invocations.rethrowIfCritical(t);
                return fail(Messages.ATTRIBUTE_ACCESS_FAILED.format(name.expected, getParentClassWrapper().name.expected,
                        Invocations.describe(t)), t);
            }
        }
        return fail(Messages.ATTRIBUTE_ACCESS_FAILED.format(name.expected, getParentClassWrapper().name.expected,
                Invocations.describe(accessFailure)), accessFailure);
    }

    private static Method findNoArgMethod(Class<?> start, String methodName) {
        for (Class<?> c = start; c != null; c = c.getSuperclass()) {
            try {
                return c.getDeclaredMethod(methodName);
            }
            catch (NoSuchMethodException | LinkageError ignored) {
                // continue with the superclass
            }
        }
        return null;
    }

    private Object resolveTarget(Object obj) {
        if (Modifier.isStatic(field.getModifiers())) {
            return null;
        }
        return obj != null ? obj : getParentClassWrapper().getObj();
    }

    /**
     * Sets the value of this attribute on a default instance.
     * Uses the parent class's default object instance. To set the value on a specific instance, use {@link AttributeWrapper#setValue(Object, Object)}.
     * Fails if the attribute is declared final.
     * Uses {@link Assertions#fail(String)} to report access failures.
     *
     * @param value the value to set
     */
    public void setValue(Object value)
    {
        setValue(null, value);
    }



    /**
     * Sets the value of this attribute on a specific object instance.
     * If obj is null, creates or uses a default instance from the parent class.
     * Fails if the attribute is declared final.
     * Uses {@link Assertions#fail(String)} to report access failures.
     *
     * @param obj the object instance to set the value on (null for static fields or default instance)
     * @param value the value to set
     */
    public void setValue(Object obj, Object value)
    {
        verifyExistence(true);

        if(Modifier.isFinal(field.getModifiers())) {
            fail(Messages.ATTRIBUTE_FINAL_CANNOT_SET.format(name.expected, getParentClassWrapper().name.expected));
        }

        Object target = resolveTarget(obj);
        try {
            field.setAccessible(true);
            Object converted = value == null || field.getType().isPrimitive() || !(value instanceof Number)
                    ? value : saveCast(value, field.getType());
            field.set(target, converted);
        }
        catch (ReflectiveOperationException | RuntimeException e) {
            Assertions.fail(Messages.ATTRIBUTE_SET_FAILED.format(name.expected, getParentClassWrapper().name.expected), e);
        }
    }



    /**
     * Attempts to find a field in a class, allowing for deviation in the attribute name.
     * The closest field (Levenshtein distance, ties broken by name) wins; synthetic fields and fields that
     * another wrapper of the same class expects exactly are ignored.
     */
    @Override
    protected void findWithDeviation()
    {
        Class<?> clazz = getParentClassWrapper().getClazz();
        if(clazz != null) {
            try // First try exact match
            {
                field = clazz.getDeclaredField(name.expected);
                name.actual = name.expected;
                name.existence = EXACT;
            } catch (NoSuchFieldException | LinkageError e) {
                findClosestCandidate(clazz);
            }
        }
        if (field == null)
        {
            existence = name.existence = type.existence = modifiers.existence = MISSING;
        }
        else
        {
            verifyModifiers(field.getModifiers());
            verifyType(type, field.getType());
        }
    }

    private void findClosestCandidate(Class<?> clazz) {
        Set<String> claimedByOthers = getParentClassWrapper().getAttributeWrappers().stream()
                .filter(w -> w != this)
                .map(Wrapper::getExpectedName)
                .collect(Collectors.toSet());
        Field[] declared;
        try {
            declared = clazz.getDeclaredFields();
        }
        catch (LinkageError e) {
            return;
        }
        Field best = Arrays.stream(declared)
                .filter(f -> !f.isSynthetic())
                .filter(f -> !claimedByOthers.contains(f.getName()))
                .filter(f -> isNameWithinDeviation(name.expected, f.getName(), LevenshteinSettings.getAttributeNameDeviationThreshold()))
                .min(Comparator.<Field>comparingInt(f -> levenshteinDistance(name.expected, f.getName()))
                        .thenComparing(Field::getName))
                .orElse(null);
        if (best != null) {
            field = best;
            name.actual = best.getName();
            name.existence = DEVIATES;
        }
    }

    @Override
    protected void parseExistence() {
        parseExistence(name, type, modifiers);
    }

    @Override
    public String expectedToString() {
        String t = type.expected == null ? "<missing>" : type.expected.getSimpleName();
        return String.format("%s %s %s", modifiers.expected, t, name.expected).trim();
    }

    @Override
    public String actualToString() {
        if(field == null) {
            return "<missing>";
        }
        String t = type.actual == null ? "<missing>" : type.actual.getSimpleName();
        return String.format("%s %s %s", modifiers.actual, t, name.actual).trim();
    }

}
