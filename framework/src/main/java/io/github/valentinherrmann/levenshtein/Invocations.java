package io.github.valentinherrmann.levenshtein;

import de.tum.cit.ase.ares.api.util.ReflectionTestUtils;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.assertj.core.api.Assertions;

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
     * Ares, and virtual machine errors other than a {@link StackOverflowError}: endless recursion is a mistake of
     * the student code and is reported like an exception (with a hint, see {@link Failure#thrown(Throwable)}).
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
        if (t instanceof VirtualMachineError e && !(t instanceof StackOverflowError)) {
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
     * A number (or character) for a parameter of another numeric type, e.g. {@code 2.5} for the student's
     * {@code float}, is converted as in {@link Utils#safeCast(Object, Class)}; a value that does not fit fails
     * the test with a readable message.
     *
     * @param parameterTypes the parameter types of the student's constructor or method
     * @param args the arguments as passed to the {@code Object...} parameter (may be {@code null})
     * @param signature the signature for the message
     * @return the arguments to pass on (a new array); {@code null} becomes one {@code null} argument for a single
     *         parameter and no arguments otherwise
     * @throws IllegalArgumentException if the arguments cannot fit the parameters
     */
    static Object[] checkArguments(Class<?>[] parameterTypes, Object[] args, String signature) {
        Object[] actual = normalizeNull(parameterTypes, args).clone();
        if (actual.length != parameterTypes.length) {
            throw new IllegalArgumentException(signature + " expects " + parameterTypes.length + " argument(s), but "
                    + actual.length + " were passed: (" + describeArguments(actual) + ")." + arrayHint(parameterTypes));
        }
        for (int i = 0; i < actual.length; i++) {
            Class<?> type = parameterTypes[i];
            Object arg = actual[i];
            if (arg == null) {
                continue;
            }
            if (type.isArray() && !type.isInstance(arg) && !arg.getClass().isArray()) {
                throw new IllegalArgumentException(signature + " expects an array of type " + type.getSimpleName()
                        + " as argument " + (i + 1) + ", but got " + describeValue(arg) + " ("
                        + arg.getClass().getSimpleName() + ")." + arrayHint(parameterTypes));
            }
            if (Utils.isNumericOrChar(type) && !Utils.toWrapperType(type).isInstance(arg)
                    && (arg instanceof Number || arg instanceof Character)) {
                Object converted = Utils.convertNumber(arg, type);
                if (converted == null) {
                    Assertions.fail(Messages.ARGUMENT_DOES_NOT_FIT.format(i + 1, describeValueExactly(arg),
                            type.getSimpleName(), signature));
                }
                actual[i] = converted;
            }
        }
        return actual;
    }

    /**
     * @return the arguments, with {@code null} (no array) as one {@code null} argument for a single parameter
     *         and as no arguments otherwise
     */
    static Object[] normalizeNull(Class<?>[] parameterTypes, Object[] args) {
        return args != null ? args : parameterTypes.length == 1 ? new Object[] {null} : new Object[0];
    }

    private static String arrayHint(Class<?>[] parameterTypes) {
        boolean arrayParameter = Arrays.stream(parameterTypes).anyMatch(t -> t.isArray() && !t.getComponentType().isPrimitive());
        return arrayParameter
                ? " Note: Java spreads an object array passed to an Object... parameter into several arguments;"
                  + " pass a single array argument as (Object) array."
                : "";
    }

    /**
     * An array or JDK collection as it was before a call: its first {@value #MAX_ELEMENTS} elements and its size.
     * Shown like the array or collection itself.
     */
    record Snapshot(List<Object> head, int size) {
    }

    /**
     * Takes the arguments of a call as they are <b>before</b> the call, for the failure message: arrays and JDK
     * collections (also nested ones) become {@link Snapshot}s, so a change made by the student's code is not
     * shown as the passed value. Other objects (and collections of student classes, which are not iterated
     * before the call) are kept as they are. The cost does not depend on the size of an array or collection.
     *
     * @param args the arguments of a call
     * @return the snapshot of each argument
     */
    static Object[] snapshotArguments(Object[] args) {
        Object[] snapshot = args.clone();
        for (int i = 0; i < snapshot.length; i++) {
            snapshot[i] = snapshot(snapshot[i], 0);
        }
        return snapshot;
    }

    private static Object snapshot(Object value, int depth) {
        if (value == null || depth >= MAX_DEPTH) {
            return value;
        }
        try {
            if (value.getClass().isArray()) {
                int length = Array.getLength(value);
                List<Object> head = new ArrayList<>();
                for (int i = 0; i < Math.min(length, MAX_ELEMENTS); i++) {
                    head.add(snapshot(Array.get(value, i), depth + 1));
                }
                return new Snapshot(head, length);
            }
            if (value instanceof Collection<?> collection && value.getClass().getName().startsWith("java.")) {
                List<Object> head = new ArrayList<>();
                for (Object element : collection) {
                    if (head.size() == MAX_ELEMENTS) {
                        break;
                    }
                    head.add(snapshot(element, depth + 1));
                }
                return new Snapshot(head, collection.size());
            }
        }
        catch (RuntimeException e) {
            // e.g. a concurrently changed collection: show it as it is later
        }
        return value;
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
        String text = contextText(context);
        return text == null ? message : text + "\n" + message;
    }

    /**
     * @param context supplies a text for the failure message; may be {@code null}, return {@code null} or throw
     * @return the stripped text, or {@code null} if there is none
     */
    static String contextText(Supplier<String> context) {
        if (context == null) {
            return null;
        }
        try {
            return blankToNull(context.get());
        }
        catch (RuntimeException e) {
            return null; // a broken context must not hide the actual failure
        }
    }

    /**
     * @param text a text for a failure message (may be {@code null})
     * @return the stripped text, or {@code null} if it is {@code null} or blank
     */
    static String blankToNull(String text) {
        return text == null || text.isBlank() ? null : text.strip();
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
        return describeValue(value, 0, false);
    }

    /**
     * Like {@link #describeValue(Object)}, but {@code double} and {@code float} values with full precision (as in
     * Java, e.g. {@code 2.4999}), for values whose difference would be hidden by rounding.
     *
     * @param value any value
     * @return the representation
     */
    static String describeValueExactly(Object value) {
        return describeValue(value, 0, true);
    }

    private static String describeValue(Object value, int depth, boolean exact) {
        if (value == null) {
            return "null";
        }
        if (value instanceof String s) {
            return "\"" + cut(s, MAX_VALUE_LENGTH) + "\"";
        }
        if (value instanceof Character c) {
            return "'" + c + "'";
        }
        if (value instanceof Double || value instanceof Float) {
            return exact ? String.valueOf(value) : formatDecimal(((Number) value).doubleValue());
        }
        boolean container = value.getClass().isArray() || value instanceof Collection<?> || value instanceof Snapshot;
        if (container && depth >= MAX_DEPTH) {
            return "[" + ELLIPSIS + "]";
        }
        if (value instanceof Snapshot snapshot) {
            return describeElements(snapshot.head(), snapshot.size(), depth, exact);
        }
        if (value.getClass().isArray()) {
            int length = Array.getLength(value);
            List<Object> elements = new ArrayList<>();
            for (int i = 0; i < Math.min(length, MAX_ELEMENTS); i++) {
                elements.add(Array.get(value, i));
            }
            return describeElements(elements, length, depth, exact);
        }
        if (value instanceof Collection<?> collection) {
            try {
                List<Object> elements = collection.stream().limit(MAX_ELEMENTS).collect(Collectors.toList());
                return describeElements(elements, collection.size(), depth, exact);
            }
            catch (StackOverflowError e) {
                // fall back to toString()
            }
            catch (Throwable t) {
                rethrowIfMessageMustNotHideIt(t);
                // fall back to toString()
            }
        }
        try {
            return cut(String.valueOf(value), MAX_VALUE_LENGTH);
        }
        catch (StackOverflowError e) {
            // e.g. toString() of two objects that refer to each other
            return value.getClass().getSimpleName() + " (toString() threw StackOverflowError)";
        }
        catch (Throwable t) {
            rethrowIfMessageMustNotHideIt(t);
            return value.getClass().getSimpleName() + " (toString() threw " + t.getClass().getSimpleName() + ")";
        }
    }

    /**
     * Rethrows what must not become part of a failure message while student code ({@code toString()}, a
     * collection) is shown: security violations reported by Ares and virtual machine errors other than a
     * {@link StackOverflowError} (which the caller handles).
     */
    private static void rethrowIfMessageMustNotHideIt(Throwable t) {
        if (t instanceof SecurityException e) {
            throw e;
        }
        if (t instanceof VirtualMachineError e) {
            throw e;
        }
    }

    /**
     * Adds the context and the arguments of a call to an assertion failure from inside the call (a failure of
     * Ares, of a mock or of a conversion), which is otherwise passed on unchanged.
     *
     * @param e the failure
     * @param context supplies the text shown in front of the message (may be {@code null})
     * @param args the snapshot of the arguments, see {@link #snapshotArguments(Object[])}
     * @return the failure with context and arguments, or {@code e} itself if there are neither
     */
    static AssertionError withCallDetails(AssertionError e, Supplier<String> context, Object[] args) {
        if (context == null && (args == null || args.length == 0)) {
            return e;
        }
        String message = e.getMessage() != null ? e.getMessage() : describe(e);
        return new AssertionError(withContext(context, withArguments(message, args))); // no cause: no stack trace
    }

    /**
     * @param value a decimal number
     * @return the number with {@link LevenshteinSettings#getDecimals()} decimals and a dot as separator; very
     *         large numbers and numbers that would round to 0 as in Java (e.g. {@code 1.0E300}, {@code 1.0E-5})
     */
    static String formatDecimal(double value) {
        int decimals = LevenshteinSettings.getDecimals();
        double magnitude = Math.abs(value);
        if (Double.isNaN(value) || Double.isInfinite(value) || magnitude >= 1e15
                || (magnitude != 0 && magnitude < 0.5 * Math.pow(10, -decimals))) {
            // too large for fixed decimals, or a value that would be shown as 0: Java's own representation
            return String.valueOf(value);
        }
        return String.format(Locale.ROOT, "%." + decimals + "f", value);
    }

    private static String describeElements(List<Object> elements, int size, int depth, boolean exact) {
        String shown = elements.stream().map(e -> describeValue(e, depth + 1, exact)).collect(Collectors.joining(", "));
        String more = size > elements.size() ? ", " + ELLIPSIS + " (+" + (size - elements.size()) + ")" : "";
        return "[" + shown + more + "]";
    }

    private static String cut(String text, int maxLength) {
        return text.length() <= maxLength ? text : text.substring(0, maxLength) + ELLIPSIS;
    }
}
