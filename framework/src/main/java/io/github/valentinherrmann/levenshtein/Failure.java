package io.github.valentinherrmann.levenshtein;

import java.util.ArrayList;
import java.util.List;

/**
 * The one format of every behaviour failure: a block of labelled lines, localized with {@link Messages} and
 * {@link LevenshteinSettings#getLanguage()}. The framework reports exceptions of student code,
 * {@code invokeExpectingException} and {@code testGettersSetters} with it, and test authors use it for their
 * own checks, so students see the same format everywhere:
 * <pre>
 * Method:   calculateLoudness(double, double)
 * Scenario: generated case 3, seed 2040003
 * Input:    volume = 12.50, crowdNoise = 3.00
 * Expected: 31.25
 * Actual:   27.00
 * </pre>
 * <pre>{@code
 * throw Failure.of(methodWrapper)
 *         .scenario("generated case 3, seed 2040003")
 *         .input("volume = 12.50, crowdNoise = 3.00")
 *         .expected(31.25).actual(actual)
 *         .error();
 * }</pre>
 * Lines that are not set are left out. The {@link AssertionError} of {@link #error()} never has a cause, so no
 * stack trace of the student code is shown.
 *
 * <p>Structural failures (a missing class, method, ...) keep their short form and do not use this block.</p>
 */
public final class Failure {

    private final String member;
    private String scenario;
    private String input;
    private String expected;
    private String actual;
    /** The raw values given to {@link #expected(Object)} / {@link #actual(Object)}, to detect rounding that hides a difference. */
    private Object expectedValue;
    private Object actualValue;
    private boolean rawValues;
    private int failedCases;
    private int totalCases;
    /** The hint shown below the block, e.g. on endless recursion; {@code null} for none. */
    private Messages hint;

    private Failure(String member) {
        this.member = member;
    }

    /**
     * @param method the method whose behaviour is wrong (the Method line shows its name and parameter types)
     * @return a new failure
     */
    public static Failure of(MethodWrapper<?, ?> method) {
        return new Failure(method == null ? null : method.signature());
    }

    /**
     * @param constructor the constructor whose behaviour is wrong
     * @return a new failure
     */
    public static Failure of(ConstructorWrapper<?> constructor) {
        return new Failure(constructor == null ? null : constructor.signature());
    }

    /**
     * @param member the text of the Method line, e.g. {@code "toString()"}; {@code null} leaves the line out
     * @return a new failure
     */
    public static Failure of(String member) {
        return new Failure(member);
    }

    /**
     * @param text what was checked, e.g. {@code "generated case 3, seed 2040003"}
     * @return this failure
     */
    public Failure scenario(String text) {
        this.scenario = Invocations.blankToNull(text);
        return this;
    }

    /**
     * @param text the input as it should be shown, e.g. {@code "volume = 12.50"}
     * @return this failure
     */
    public Failure input(String text) {
        this.input = Invocations.blankToNull(text);
        return this;
    }

    /**
     * Shows argument values in the Input line, formatted like {@link #format(Object)} and separated by commas.
     * No arguments leave the line out.
     *
     * @param args the argument values
     * @return this failure
     */
    public Failure arguments(Object... args) {
        this.input = args == null || args.length == 0 ? null : Invocations.describeArguments(args);
        return this;
    }

    /**
     * @param value the expected value, formatted with {@link #format(Object)}
     * @return this failure
     */
    public Failure expected(Object value) {
        this.expected = format(value);
        this.expectedValue = value;
        return this;
    }

    /**
     * @param value the actual value, formatted with {@link #format(Object)}
     * @return this failure
     */
    public Failure actual(Object value) {
        this.actual = format(value);
        this.actualValue = value;
        this.rawValues = true;
        return this;
    }

    /**
     * @param text the expected outcome as it should be shown (not formatted, strings are not quoted)
     * @return this failure
     */
    public Failure expectedText(String text) {
        this.expected = text;
        this.expectedValue = null;
        return this;
    }

    /**
     * @param text the actual outcome as it should be shown (not formatted, strings are not quoted)
     * @return this failure
     */
    public Failure actualText(String text) {
        this.actual = text;
        this.rawValues = false;
        return this;
    }

    /**
     * Shows an exception of the student code as actual outcome, e.g. {@code IllegalStateException: message}
     * (never with a stack trace). Errors of the virtual machine are shown the same way. A {@link StackOverflowError}
     * adds a hint on endless recursion, an {@link OutOfMemoryError} a hint on a loop that never stops growing.
     *
     * @param t the exception thrown by the student code
     * @return this failure
     */
    public Failure thrown(Throwable t) {
        this.actual = Invocations.describe(t);
        this.rawValues = false;
        this.hint = hintFor(t);
        return this;
    }

    /**
     * Adds the line "(n of m cases failed)" if more than one case was checked.
     *
     * @param failed the number of failed cases
     * @param total the number of all checked cases
     * @return this failure
     */
    public Failure cases(int failed, int total) {
        this.failedCases = failed;
        this.totalCases = total;
        return this;
    }

    /**
     * @return the block in the current language
     */
    public String message() {
        List<String[]> lines = new ArrayList<>();
        add(lines, Messages.FAILURE_METHOD, member);
        add(lines, Messages.FAILURE_SCENARIO, scenario);
        add(lines, Messages.FAILURE_INPUT, input);
        String shownExpected = expected;
        String shownActual = actual;
        if (rawValues && expected != null && expected.equals(actual)
                && expectedValue != null && !java.util.Objects.deepEquals(expectedValue, actualValue)) {
            // e.g. 2.5 and 2.4999 both show as 2.50: show them as they are
            shownExpected = Invocations.describeValueExactly(expectedValue);
            shownActual = Invocations.describeValueExactly(actualValue);
        }
        add(lines, Messages.FAILURE_EXPECTED, shownExpected);
        add(lines, Messages.FAILURE_ACTUAL, shownActual);
        int width = labelWidth();
        StringBuilder out = new StringBuilder();
        for (String[] line : lines) {
            if (!out.isEmpty()) {
                out.append('\n');
            }
            String label = line[0] + ":";
            String indent = " ".repeat(width + 1);
            out.append(label).append(" ".repeat(width + 1 - label.length()))
                    .append(line[1].strip().replace("\n", "\n" + indent));
        }
        if (totalCases > 1) {
            out.append('\n').append(Messages.FAILURE_CASES.format(failedCases, totalCases));
        }
        if (hint != null) {
            out.append('\n').append(hint.get());
        }
        return out.toString();
    }

    /**
     * @return a failure with the block as message and without a cause, ready to be thrown
     */
    public AssertionError error() {
        return new AssertionError(message());
    }

    @Override
    public String toString() {
        return message();
    }

    /**
     * Formats a value the same way in every failure message: {@code double} and {@code float} values with
     * {@link LevenshteinSettings#getDecimals()} decimals (dot as separator), strings in double quotes, characters
     * in single quotes, {@code null}, arrays and collections in brackets, very long values cut.
     *
     * @param value any value
     * @return the formatted value
     */
    public static String format(Object value) {
        return Invocations.describeValue(value);
    }

    /**
     * Runs student code and turns an exception or error it throws into a failure block (Actual: the exception),
     * also an error of the virtual machine such as {@link OutOfMemoryError}. Failures ({@link AssertionError}) and
     * security violations are passed on unchanged.
     *
     * @param code the code to run, e.g. {@code () -> wrapper.invokeRaw(...)} or a call of a student method
     * @param <R> the result type
     * @return the result of the code
     */
    public static <R> R call(ThrowingSupplier<R> code) {
        try {
            return code.get();
        }
        catch (Throwable t) {
            Invocations.rethrowIfCritical(t);
            throw new Failure(null).thrown(t).error();
        }
    }

    /**
     * Like {@link #call(ThrowingSupplier)} for code without a result.
     *
     * @param code the code to run
     */
    public static void call(ThrowingRunnable code) {
        call(() -> {
            code.run();
            return null;
        });
    }

    /**
     * Code that returns a value and may throw anything.
     *
     * @param <R> the result type
     */
    @FunctionalInterface
    public interface ThrowingSupplier<R> {
        /**
         * @return the result
         * @throws Throwable anything
         */
        R get() throws Throwable;
    }

    /**
     * Code without a result that may throw anything.
     */
    @FunctionalInterface
    public interface ThrowingRunnable {
        /**
         * @throws Throwable anything
         */
        void run() throws Throwable;
    }

    private static void add(List<String[]> lines, Messages label, String value) {
        if (value != null) {
            lines.add(new String[] {label.get(), value});
        }
    }

    /**
     * @return the length of the longest label (with colon) of the current language, so all values line up
     */
    private static int labelWidth() {
        int width = 0;
        for (Messages label : new Messages[] {Messages.FAILURE_METHOD, Messages.FAILURE_SCENARIO,
                Messages.FAILURE_INPUT, Messages.FAILURE_EXPECTED, Messages.FAILURE_ACTUAL}) {
            width = Math.max(width, label.get().length() + 1);
        }
        return width;
    }

    /**
     * @param types parameter types
     * @return their simple names separated by commas, e.g. {@code double, String[]}
     */
    static String simpleNames(Class<?>[] types) {
        return java.util.Arrays.stream(types).map(Class::getSimpleName)
                .collect(java.util.stream.Collectors.joining(", "));
    }

    /**
     * @param t the exception thrown by the student code
     * @return the hint for an error of the virtual machine in its cause chain, or {@code null} if there is none
     */
    private static Messages hintFor(Throwable t) {
        for (int i = 0; t != null && i < 5; i++, t = t.getCause()) {
            if (t instanceof StackOverflowError) {
                return Messages.FAILURE_STACK_OVERFLOW_HINT;
            }
            if (t instanceof OutOfMemoryError) {
                return Messages.FAILURE_OUT_OF_MEMORY_HINT;
            }
        }
        return null;
    }
}
