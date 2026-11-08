package io.github.valentinherrmann.levenshtein;


import org.assertj.core.api.Assertions;

import static io.github.valentinherrmann.levenshtein.Utils.toWrapperType;
import static io.github.valentinherrmann.levenshtein.Utils.isNumericDeviation;
import static io.github.valentinherrmann.levenshtein.WrapperProperty.Existence.*;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Wrapper for class constructors that verifies their existence, parameter types, and modifiers.
 * Provides methods to invoke constructors and create instances.
 *
 * @param <T> the type of the class whose constructor is being wrapped
 */
public class ConstructorWrapper<T> extends Wrapper<T>
{
    /**
     * The expected parameter types as specified; student classes are only resolved when the constructor is checked.
     */
    private final TypeRef[] paramRefs;

    /**
     * The resolved expected parameter types; a missing student class is {@code null}. Set when the constructor is
     * checked (immediately if all types are fixed classes).
     */
    private Class<?>[] paramTypes;

    /**
     * Expected and actual parameter types and their existence (EXACT, DEVIATES for primitive/wrapper
     * differences, MISSING).
     */
    private final WrapperProperty<Class<?>[]> params;

    /**
     * The actual constructor found via reflection.
     */
    Constructor<T> constructor;

    /**
     * Constructs a new ConstructorWrapper for verifying a class constructor.
     *
     * @param parentClass the class wrapper containing this constructor
     * @param paramTypes the expected parameter types for the constructor
     * @param modifiers the expected modifiers (e.g., "public", "protected")
     */
    public ConstructorWrapper(ClassWrapper<T> parentClass, Class<?>[] paramTypes, String... modifiers) {
        this(parentClass, TypeRef.ofAll(paramTypes), modifiers);
    }

    /**
     * Constructs a new ConstructorWrapper whose parameter types may include student classes. Each parameter type
     * is a {@link Class}, a {@link ClassWrapper} (e.g. {@code ClassWrapper.reference("Guitarist", "band")}) or a
     * {@link java.util.function.Supplier} of a class wrapper (needs a cast in an array:
     * {@code (Supplier<ClassWrapper<?>>) () -> x}). Student classes are only looked up when the constructor is
     * checked; if one is missing, only the parameters count as missing.
     *
     * @param parentClass the class wrapper containing this constructor
     * @param paramTypes the expected parameter types for the constructor
     * @param modifiers the expected modifiers (e.g., "public", "protected")
     * @throws IllegalArgumentException if a parameter type is {@code null} or of another kind
     */
    public ConstructorWrapper(ClassWrapper<T> parentClass, Object[] paramTypes, String... modifiers) {
        this(parentClass, TypeRef.ofAll(paramTypes), modifiers);
    }

    private ConstructorWrapper(ClassWrapper<T> parentClass, TypeRef[] paramRefs, String... modifiers) {
        super(parentClass, "", modifiers);
        this.paramRefs = paramRefs;
        boolean fixed = Arrays.stream(paramRefs).allMatch(TypeRef::isFixed);
        this.paramTypes = fixed ? TypeRef.resolveAll(paramRefs) : null;
        this.params = new WrapperProperty<>(this.paramTypes);
    }

    /**
     * Constructs a new ConstructorWrapper for a no-argument constructor.
     *
     * @param parentClass the class wrapper containing this constructor
     * @param modifiers the expected modifiers (e.g., "public")
     */
    public ConstructorWrapper(ClassWrapper<T> parentClass, String modifiers) {
        this(parentClass, new Class<?>[] {}, modifiers);
    }

    @Override
    public void verifyExistence(boolean throwAssertion) {
        super.verifyExistence(Messages.CONSTRUCTOR_NOT_IMPLEMENTED.format(
                this.expectedToString(), getParentClassWrapper().name.expected), throwAssertion);
    }

    /**
     * Looks up the declared constructor (any visibility). If no constructor with exactly the expected parameter
     * types exists, a constructor whose parameters only differ in primitive/wrapper types counts as a deviation.
     */
    @Override
    @SuppressWarnings("unchecked")
    protected void findWithDeviation()
    {
        paramTypes = getParamTypes();
        params.expected = paramTypes;
        boolean unresolved = Arrays.asList(paramTypes).contains(null);
        Class<T> clazz = getParentClassWrapper().getClazz();
        if(clazz != null) {
            try {
                if (unresolved) {
                    throw new NoSuchMethodException(); // not with the exact types: look for the closest candidate
                }
                constructor = clazz.getDeclaredConstructor(paramTypes);
                params.existence = EXACT;
            }
            catch (NoSuchMethodException | LinkageError e) {
                try {
                    int bestScore = Integer.MAX_VALUE;
                    for (Constructor<?> candidate : clazz.getDeclaredConstructors()) {
                        int score = candidate.isSynthetic() ? -1 : parameterDeviation(candidate.getParameterTypes());
                        if (score >= 0 && score < bestScore) {
                            bestScore = score;
                            constructor = (Constructor<T>) candidate;
                            // a parameter whose student class is missing cannot match
                            params.existence = unresolved ? MISSING : DEVIATES;
                        }
                    }
                }
                catch (LinkageError ignored) {
                    // treated as missing
                }
            }
        }
        if(constructor != null) {
            params.actual = constructor.getParameterTypes();
            verifyModifiers(constructor.getModifiers());
        }
        else {
            existence = params.existence = modifiers.existence = MISSING;
        }
    }

    /**
     * @return -1 if the parameters cannot match, otherwise a score where lower is closer: 0 for identical types,
     *         1 per primitive/wrapper difference, 2 per numeric difference (e.g. {@code long} vs. {@code int}) and
     *         {@value Utils#UNRESOLVED_PARAMETER_SCORE} per reference type in place of a missing student class
     */
    private int parameterDeviation(Class<?>[] actual) {
        if (actual.length != paramTypes.length) {
            return -1;
        }
        int score = 0;
        for (int i = 0; i < actual.length; i++) {
            if (paramTypes[i] == null) {
                if (actual[i].isPrimitive()) {
                    return -1;
                }
                score += Utils.UNRESOLVED_PARAMETER_SCORE;
                continue;
            }
            if (actual[i].equals(paramTypes[i])) {
                continue;
            }
            if (toWrapperType(actual[i]).equals(toWrapperType(paramTypes[i]))) {
                score += 1;
            }
            else if (isNumericDeviation(paramTypes[i], actual[i])) {
                score += 2;
            }
            else {
                return -1;
            }
        }
        return score;
    }



    /**
     * Invokes the constructor with the specified arguments to create a new instance.
     * For abstract classes, uses ByteBuddy to create a dynamic subclass instance.
     * An exception thrown by the student code fails the test with the exception type, its message and the
     * argument values; use {@link #invokeExpectingException(Class, Object...)} if an exception is the expected
     * outcome.
     *
     * <p>Pass a single array argument as {@code (Object) array}: otherwise Java spreads an object array
     * (e.g. {@code String[]}) into several arguments. A wrong number of arguments is reported as
     * {@link IllegalArgumentException}.</p>
     *
     * @param args the arguments to pass to the constructor
     * @return a new instance of the class
     */
    @SuppressWarnings("unchecked")
    public T invoke(Object... args)
    {
        verifyExistence(true);
        if (isParentAbstract()) {
            return (T) getParentClassWrapper().getDynamicSubclassObj(this, args);
        }
        Object[] checked = Invocations.checkArguments(constructor.getParameterTypes(), args, actualToString());
        Object[] shown = Invocations.snapshotArguments(checked); // as passed, before the student code can change them
        try {
            return (T) Invocations.newInstance(constructor, checked);
        }
        catch (Throwable t) {
            if (t instanceof AssertionError e) {
                throw Invocations.withCallDetails(e, null, shown);
            }
            Invocations.rethrowIfCritical(t);
            return Assertions.fail(Invocations.withArguments(Messages.CONSTRUCTOR_INVOCATION_FAILED.format(
                    this.expectedToString(), getParentClassWrapper().name.expected, Invocations.describe(t)), shown), t);
        }
    }

    /**
     * Invokes the constructor and expects the student code to throw an exception of the given type.
     *
     * <p>Pass a single array argument as {@code (Object) array}: otherwise Java spreads an object array
     * into several arguments.</p>
     *
     * @param expected the expected exception type (subclasses are accepted)
     * @param args the arguments to pass to the constructor
     * @param <X> the exception type
     * @return the thrown exception, e.g. for further assertions on its message
     */
    public <X extends Throwable> X invokeExpectingException(Class<X> expected, Object... args) {
        verifyExistence(true);
        Object[] checked = Invocations.checkArguments(constructor.getParameterTypes(), args, actualToString());
        Object[] shown = Invocations.snapshotArguments(checked);
        boolean dynamic = isParentAbstract();
        // abstract classes: the generated subclass calls the student's constructor, whose exception is not wrapped
        Constructor<?> target = constructor;
        if (dynamic) {
            try {
                target = getParentClassWrapper().dynamicSubclassConstructor(constructor.getParameterTypes());
            }
            catch (AssertionError failure) {
                // generating the subclass failed: show the arguments, as getDynamicSubclassObj does
                throw Invocations.withCallDetails(failure, null, shown);
            }
        }
        String outcome;
        try {
            Invocations.newInstance(target, checked);
            outcome = Messages.NOTHING_THROWN.get()
                    + (dynamic ? " (" + target.getDeclaringClass().getSimpleName() + ")" : "");
        }
        catch (Throwable t) {
            if (expected.isInstance(t)) {
                return expected.cast(t);
            }
            Invocations.rethrowIfCritical(t);
            outcome = Invocations.describe(t);
        }
        return Assertions.fail(Invocations.withArguments(Messages.EXPECTED_EXCEPTION_NOT_THROWN.format(expectedToString(),
                getParentClassWrapper().name.expected, expected.getSimpleName(), outcome), shown));
    }

    private boolean isParentAbstract() {
        Class<T> parent = getParentClassWrapper().getClazz();
        return Modifier.isAbstract(parent.getModifiers()) || parent.isInterface();
    }

    @Override
    protected void parseExistence() {
        parseExistence(params, modifiers);
    }

    private static String typeNames(Class<?>[] types) {
        return Arrays.stream(types).map(Class::getSimpleName).collect(Collectors.joining(", "));
    }

    @Override
    public String expectedToString() {
        return String.format(
                "%s %s(%s)",
                modifiers.expected,
                getParentClassWrapper().name.expected,
                TypeRef.simpleNames(paramRefs)
        ).trim();
    }

    @Override
    public String actualToString() {
        ensureChecked();
        if(constructor == null) {
            return "<missing>";
        }
        return String.format(
                "%s %s(%s)",
                modifiers.actual,
                getParentClassWrapper().name.actual,
                typeNames(constructor.getParameterTypes())
        ).trim();
    }

    /**
     * Retrieves the parameter types expected for this constructor, resolving student classes given as types.
     *
     * @return an array of parameter types; a student class that is missing is {@code null}
     */
    public Class<?>[] getParamTypes() {
        return paramTypes != null ? paramTypes.clone() : TypeRef.resolveAll(paramRefs);
    }

    /**
     * Retrieves the parameter types of the constructor actually found (they may differ in primitive/wrapper types).
     *
     * @return the actual parameter types, or the expected ones if the constructor is missing
     */
    public Class<?>[] getActualParamTypes() {
        ensureChecked();
        return constructor == null ? paramTypes.clone() : constructor.getParameterTypes();
    }
}
