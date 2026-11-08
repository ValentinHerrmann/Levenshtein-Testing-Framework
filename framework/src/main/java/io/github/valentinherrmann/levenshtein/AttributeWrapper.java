package io.github.valentinherrmann.levenshtein;

import org.assertj.core.api.Assertions;


import java.lang.reflect.Field;
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
     * The expected type as specified; a student class is only resolved when the attribute is checked.
     * {@code null} if no type was given.
     */
    private final TypeRef typeRef;

    /**
     * Constructs a new AttributeWrapper for verifying a class attribute.
     *
     * @param parentClass the class wrapper containing this attribute
     * @param expectedName the expected name of the attribute
     * @param expectedType the expected type of the attribute
     * @param modifiers the expected modifiers (e.g., "public", "static", "final")
     */
    public AttributeWrapper(ClassWrapper<T> parentClass, String expectedName, Class<V> expectedType, String... modifiers) {
        this(parentClass, expectedName, TypeRef.ofNullableClass(expectedType), modifiers);
    }

    /**
     * Constructs a new AttributeWrapper for an attribute whose type is a student class, e.g. an attribute
     * {@code drummer} of type {@code Drummer}. The type is only looked up when the attribute is checked, so the
     * wrappers can be built in any order. If the student class is missing, only the attribute's type counts as
     * missing.
     *
     * @param parentClass the class wrapper containing this attribute
     * @param expectedName the expected name of the attribute
     * @param expectedType the wrapper of the expected type, e.g. {@code ClassWrapper.reference("Drummer", "band")}
     * @param modifiers the expected modifiers (e.g., "public", "static", "final")
     */
    public AttributeWrapper(ClassWrapper<T> parentClass, String expectedName, ClassWrapper<? extends V> expectedType, String... modifiers) {
        this(parentClass, expectedName, TypeRef.ofWrapper(expectedType, "attribute " + expectedName), modifiers);
    }

    /**
     * Like {@link #AttributeWrapper(ClassWrapper, String, ClassWrapper, String...)}, but the wrapper of the type is
     * only taken from the supplier when the attribute is checked, e.g. {@code () -> TestManager.drummer} for a
     * wrapper that is created later.
     *
     * @param parentClass the class wrapper containing this attribute
     * @param expectedName the expected name of the attribute
     * @param expectedType supplies the wrapper of the expected type
     * @param modifiers the expected modifiers (e.g., "public", "static", "final")
     */
    public AttributeWrapper(ClassWrapper<T> parentClass, String expectedName,
                            Supplier<? extends ClassWrapper<? extends V>> expectedType, String... modifiers) {
        this(parentClass, expectedName, TypeRef.ofSupplier(expectedType, "attribute " + expectedName), modifiers);
    }

    private AttributeWrapper(ClassWrapper<T> parentClass, String expectedName, TypeRef typeRef, String... modifiers) {
        super(parentClass, expectedName, modifiers);
        this.typeRef = typeRef;
        this.type = new WrapperProperty<>(typeRef != null && typeRef.isFixed() ? typeRef.resolve() : null);
        StaticStateReset.register(this);
    }



    @Override
    public void verifyExistence(boolean throwAssertion)
    {
        super.verifyExistence(Messages.ATTRIBUTE_NOT_IMPLEMENTED.format(
                name.expected, getParentClassWrapper().name.expected), throwAssertion);
    }

    /**
     * Fields of interfaces are implicitly static, so the legacy mode only reports an unexpected {@code static}
     * for classes.
     */
    @Override
    protected boolean legacyReportsUnexpectedStatic() {
        Class<T> parent = getParentClassWrapper().getClazz();
        return parent == null || !parent.isInterface();
    }

    @Override
    protected int relevantModifierMask() {
        return Modifier.fieldModifiers();
    }

    /**
     * @return the student's field found by the lookup, or null if it is missing (does not trigger the lookup)
     */
    Field foundField() {
        return field;
    }

    /**
     * Constants of interfaces and enum constants are implicitly {@code static final}, the component fields of
     * records implicitly {@code final}.
     */
    @Override
    protected int implicitModifiers() {
        int implicit = super.implicitModifiers();
        Class<T> parent = getParentClassWrapper().getClazz();
        if (parent != null && parent.isInterface()) {
            implicit |= Modifier.STATIC | Modifier.FINAL;
        }
        if (field != null && field.isEnumConstant()) {
            implicit |= Modifier.STATIC | Modifier.FINAL;
        }
        if (parent != null && parent.isRecord() && field != null && !Modifier.isStatic(field.getModifiers())) {
            implicit |= Modifier.FINAL;
        }
        return implicit;
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
        return (V) safeCast(val, type.expected, true);
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

    /**
     * Resolves a student class given as type (see {@link #AttributeWrapper(ClassWrapper, String, ClassWrapper, String...)}).
     *
     * @return the expected type of the attribute, or {@code null} if it is a student class that is missing
     */
    public Class<?> getExpectedType() {
        return typeRef == null ? null : typeRef.resolve();
    }

    /**
     * @return the type of the student's field, or {@code null} if the attribute is missing
     */
    Class<?> actualFieldType() {
        ensureChecked();
        return field == null ? null : field.getType();
    }

    /**
     * @return whether the student's field exists and is {@code final}
     */
    boolean isFinal() {
        ensureChecked();
        return field != null && Modifier.isFinal(field.getModifiers());
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
     * A number is converted to the type of the student's field (e.g. a {@code double} value into a {@code float}
     * field); a narrowing conversion may lose precision.
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
            // numeric values are converted to the student's type (also primitives: reflection only widens)
            Object converted = value == null || !(value instanceof Number)
                    ? value : safeCast(value, field.getType());
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
        type.expected = getExpectedType();
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
            if (hasUnexpectedModifier("static")) {
                StaticStateReset.snapshot(field); // reset before each test, see StaticStateReset
            }
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
        String t = typeRef == null ? "<missing>" : typeRef.expectedSimpleName();
        return String.format("%s %s %s", modifiers.expected, t, name.expected).trim();
    }

    @Override
    public String actualToString() {
        ensureChecked();
        if(field == null) {
            return "<missing>";
        }
        String t = type.actual == null ? "<missing>" : type.actual.getSimpleName();
        return String.format("%s %s %s", modifiers.actual, t, name.actual).trim();
    }

}
