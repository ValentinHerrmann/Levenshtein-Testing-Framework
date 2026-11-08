package io.github.valentinherrmann.levenshtein;

import de.tum.cit.ase.ares.api.util.ReflectionTestUtils;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Invocation of student code through Ares 2's {@link ReflectionTestUtils}.
 *
 * <p>The rethrowing variants are used, so an exception thrown by student code reaches the framework
 * unchanged (instead of being wrapped into a generic failure). Ares' own failures for wrong arguments
 * or inaccessible members arrive as {@link AssertionError}.</p>
 */
final class Invocations {

    /** Elements of an array or collection shown before the rest is summarised as {@code … (+n)}. */
    static final int MAX_ELEMENTS = 10;

    /** Characters of a single value (e.g. a long string or {@code toString()}) shown before it is cut. */
    static final int MAX_VALUE_LENGTH = 80;

    /** Characters of a whole argument list shown before it is cut. */
    static final int MAX_ARGUMENTS_LENGTH = 300;

    /** Nesting depth of arrays and collections shown (also guards against an array that contains itself). */
    private static final int MAX_DEPTH = 3;

    private static final String ELLIPSIS = "…";

    private Invocations() {
    }

    static Object invoke(Method method, Object target, Object... args) throws Throwable {
        return ReflectionTestUtils.invokeNonPublicMethodRethrowing(target, method, args);
    }

    static Object newInstance(Constructor<?> constructor, Object... args) throws Throwable {
        return ReflectionTestUtils.newInstanceFromNonPublicConstructorRethrowing(constructor, args);
    }

    /**
     * Rethrows throwables that must never be turned into an ordinary test failure:
     * assertion failures (they already are failures, often from Ares), security violations reported by
     * Ares, and virtual machine errors.
     *
     * @param t the throwable caught while running student code
     */
    static void rethrowIfCritical(Throwable t) {
        if (t instanceof AssertionError e) {
            throw e;
        }
        if (t instanceof SecurityException e) {
            throw e;
        }
        if (t instanceof VirtualMachineError e) {
            throw e;
        }
    }

    /**
     * @param t a throwable
     * @return a short, student-readable description like {@code IllegalArgumentException: year < 0}
     */
    static String describe(Throwable t) {
        if (t == null) {
            return "<none>";
        }
        if (t instanceof ExceptionInInitializerError && t.getCause() != null) {
            // its own message is null; the cause is the exception of the static initializer
            return t.getClass().getSimpleName() + ": " + describe(t.getCause());
        }
        String message = t.getMessage();
        return t.getClass().getSimpleName() + (message == null || message.isBlank() ? "" : ": " + message);
    }

    /**
     * Copies the arguments of a call, with arrays (also nested ones, up to {@value #MAX_DEPTH} levels) copied, so
     * that a later change of an array does not change the copy. Other objects are kept as they are.
     *
     * @param args the arguments
     * @return the copy
     */
    static Object[] copyArrays(Object[] args) {
        Object[] copy = args.clone();
        for (int i = 0; i < copy.length; i++) {
            copy[i] = copyArray(copy[i], 1);
        }
        return copy;
    }

    private static Object copyArray(Object value, int depth) {
        if (value == null || !value.getClass().isArray()) {
            return value;
        }
        if (value instanceof Object[] objects) {
            Object[] copy = objects.clone();
            if (depth < MAX_DEPTH) {
                for (int i = 0; i < copy.length; i++) {
                    copy[i] = copyArray(copy[i], depth + 1);
                }
            }
            return copy;
        }
        int length = Array.getLength(value);
        Object copy = Array.newInstance(value.getClass().getComponentType(), length);
        System.arraycopy(value, 0, copy, 0, length);
        return copy;
    }

    /**
     * Checks the arguments for a constructor or method before it is called and normalises them.
     * A wrong number of arguments is an error of the test, not of the student, so it is reported as
     * {@link IllegalArgumentException} with a hint on the most common cause: Java spreads an object array
     * passed to an {@code Object...} parameter into several arguments ({@code (Object) array} prevents that).
     *
     * @param parameterTypes the parameter types of the student's constructor or method
     * @param args the arguments as passed to the {@code Object...} parameter (may be {@code null})
     * @param signature the signature for the message
     * @return the arguments to pass on; {@code null} becomes one {@code null} argument for a single parameter
     *         and no arguments otherwise
     * @throws IllegalArgumentException if the arguments cannot fit the parameters
     */
    static Object[] checkArguments(Class<?>[] parameterTypes, Object[] args, String signature) {
        Object[] actual = args != null ? args : parameterTypes.length == 1 ? new Object[] {null} : new Object[0];
        boolean arrayParameter = Arrays.stream(parameterTypes).anyMatch(t -> t.isArray() && !t.getComponentType().isPrimitive());
        String hint = arrayParameter
                ? " Note: Java spreads an object array passed to an Object... parameter into several arguments;"
                  + " pass a single array argument as (Object) array."
                : "";
        if (actual.length != parameterTypes.length) {
            throw new IllegalArgumentException(signature + " expects " + parameterTypes.length + " argument(s), but "
                    + actual.length + " were passed: (" + describeArguments(actual) + ")." + hint);
        }
        for (int i = 0; i < actual.length; i++) {
            Class<?> type = parameterTypes[i];
            if (actual[i] != null && type.isArray() && !type.isInstance(actual[i]) && !actual[i].getClass().isArray()) {
                throw new IllegalArgumentException(signature + " expects an array of type " + type.getSimpleName()
                        + " as argument " + (i + 1) + ", but got " + describeValue(actual[i]) + " ("
                        + actual[i].getClass().getSimpleName() + ")." + hint);
            }
        }
        return actual;
    }

    /**
     * @param message a failure message
     * @param args the arguments of the failed call
     * @return the message, followed by a line with the argument values if there are any
     */
    static String withArguments(String message, Object[] args) {
        if (args == null || args.length == 0) {
            return message;
        }
        return message + "\n" + Messages.INVOCATION_ARGUMENTS.format(describeArguments(args));
    }

    /**
     * @param context supplies a text for the failure message; may be {@code null}, return {@code null} or throw
     * @param message a failure message
     * @return the message with the text of {@code context} in a line in front of it, if there is one
     */
    static String withContext(Supplier<String> context, String message) {
        if (context == null) {
            return message;
        }
        String text;
        try {
            text = context.get();
        }
        catch (RuntimeException e) {
            text = null; // a broken context must not hide the actual failure
        }
        return text == null || text.isBlank() ? message : text.strip() + "\n" + message;
    }

    /**
     * @param args argument values
     * @return the values separated by commas, e.g. {@code 2.5, "text", [1, 2, 3]}, cut if very long
     * @see #describeValue(Object)
     */
    static String describeArguments(Object[] args) {
        if (args == null) {
            return "null";
        }
        String joined = Arrays.stream(args).map(Invocations::describeValue).collect(Collectors.joining(", "));
        return cut(joined, MAX_ARGUMENTS_LENGTH);
    }

    /**
     * A student-readable representation of a value: strings in double quotes, characters in single quotes,
     * arrays and collections with their elements (at most {@value #MAX_ELEMENTS}), long values cut.
     *
     * @param value any value
     * @return the representation
     */
    static String describeValue(Object value) {
        return describeValue(value, 0);
    }

    private static String describeValue(Object value, int depth) {
        if (value == null) {
            return "null";
        }
        if (value instanceof String s) {
            return "\"" + cut(s, MAX_VALUE_LENGTH) + "\"";
        }
        if (value instanceof Character c) {
            return "'" + c + "'";
        }
        boolean container = value.getClass().isArray() || value instanceof Collection<?>;
        if (container && depth >= MAX_DEPTH) {
            return "[" + ELLIPSIS + "]";
        }
        if (value.getClass().isArray()) {
            int length = Array.getLength(value);
            List<Object> elements = new ArrayList<>();
            for (int i = 0; i < Math.min(length, MAX_ELEMENTS); i++) {
                elements.add(Array.get(value, i));
            }
            return describeElements(elements, length, depth);
        }
        if (value instanceof Collection<?> collection) {
            try {
                List<Object> elements = collection.stream().limit(MAX_ELEMENTS).collect(Collectors.toList());
                return describeElements(elements, collection.size(), depth);
            }
            catch (RuntimeException e) {
                // fall back to toString()
            }
        }
        try {
            return cut(String.valueOf(value), MAX_VALUE_LENGTH);
        }
        catch (RuntimeException e) {
            return value.getClass().getSimpleName() + " (toString() threw " + e.getClass().getSimpleName() + ")";
        }
    }

    private static String describeElements(List<Object> elements, int size, int depth) {
        String shown = elements.stream().map(e -> describeValue(e, depth + 1)).collect(Collectors.joining(", "));
        String more = size > elements.size() ? ", " + ELLIPSIS + " (+" + (size - elements.size()) + ")" : "";
        return "[" + shown + more + "]";
    }

    private static String cut(String text, int maxLength) {
        return text.length() <= maxLength ? text : text.substring(0, maxLength) + ELLIPSIS;
    }
}
