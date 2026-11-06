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
     * The expected parameter types for the constructor.
     */
    Class<?>[] paramTypes;

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
        super(parentClass, "", modifiers);
        this.paramTypes = paramTypes == null ? new Class<?>[0] : paramTypes.clone();
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
        Class<T> clazz = getParentClassWrapper().getClazz();
        if(clazz != null) {
            try {
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
                            params.existence = DEVIATES;
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
     *         1 per primitive/wrapper difference and 2 per numeric difference (e.g. {@code long} vs. {@code int})
     */
    private int parameterDeviation(Class<?>[] actual) {
        if (actual.length != paramTypes.length) {
            return -1;
        }
        int score = 0;
        for (int i = 0; i < actual.length; i++) {
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
     * An exception thrown by the student code fails the test with the exception type and message;
     * use {@link #invokeExpectingException(Class, Object...)} if an exception is the expected outcome.
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
        try {
            return (T) Invocations.newInstance(constructor, args);
        }
        catch (Throwable t) {
            Invocations.rethrowIfCritical(t);
            return Assertions.fail(Messages.CONSTRUCTOR_INVOCATION_FAILED.format(this.expectedToString(),
                    getParentClassWrapper().name.expected, Invocations.describe(t)), t);
        }
    }

    /**
     * Invokes the constructor and expects the student code to throw an exception of the given type.
     *
     * @param expected the expected exception type (subclasses are accepted)
     * @param args the arguments to pass to the constructor
     * @param <X> the exception type
     * @return the thrown exception, e.g. for further assertions on its message
     */
    public <X extends Throwable> X invokeExpectingException(Class<X> expected, Object... args) {
        verifyExistence(true);
        String outcome;
        try {
            if (isParentAbstract()) {
                Class<?> dynamic = getParentClassWrapper().getDynamicSubclassObj(this, args).getClass();
                outcome = Messages.NOTHING_THROWN.get() + " (" + dynamic.getSimpleName() + ")";
            }
            else {
                Invocations.newInstance(constructor, args);
                outcome = Messages.NOTHING_THROWN.get();
            }
        }
        catch (Throwable t) {
            if (expected.isInstance(t)) {
                return expected.cast(t);
            }
            Invocations.rethrowIfCritical(t);
            outcome = Invocations.describe(t);
        }
        return Assertions.fail(Messages.EXPECTED_EXCEPTION_NOT_THROWN.format(expectedToString(),
                getParentClassWrapper().name.expected, expected.getSimpleName(), outcome));
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
                typeNames(paramTypes)
        ).trim();
    }

    @Override
    public String actualToString() {
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
     * Retrieves the parameter types expected for this constructor.
     *
     * @return an array of parameter types
     */
    public Class<?>[] getParamTypes() {
        return paramTypes.clone();
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
