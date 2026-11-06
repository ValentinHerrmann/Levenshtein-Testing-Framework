package io.github.valentinherrmann.levenshtein;

import org.assertj.core.api.Assertions;

import static io.github.valentinherrmann.levenshtein.WrapperProperty.Existence;
import static io.github.valentinherrmann.levenshtein.WrapperProperty.Existence.*;

import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

 /**
 * Abstract base class for wrapping and verifying Java reflection elements (classes, methods, fields, constructors).
 * Provides name and modifier deviation detection using Levenshtein distance or similar fuzzy matching.
 * Tracks whether an element exists exactly, deviates (can be used even though not 100% matches the signature),
  * or is missing.
 *
 * <p>The (potentially expensive) lookup in the student code runs only once per wrapper, on first use.</p>
 *
 * @param <T> the type of the class the element (e.g. method) belongs to.
 */
public abstract class Wrapper<T>
{
    /** All modifier keywords accepted in expected modifier specifications. */
    private static final List<String> KNOWN_MODIFIERS = List.of(
            "public", "private", "protected", "static", "final", "abstract", "synchronized",
            "native", "transient", "volatile", "strictfp", "interface");

    /**
     * The expected and actual name of the wrapped element.
     */
    protected WrapperProperty<String> name;

    /**
     * The expected and actual modifiers (e.g., "public static final") of the wrapped element.
     */
    protected WrapperProperty<String> modifiers;

    /**
     * The overall existence (considering all parts state of the wrapped element (EXACT, DEVIATES, MISSING, or UNCHECKED).
     * DEVIATES means that the element was found but has some deviations from the expected signature, but can still be
     * used for further testing. UNCHECKED means that existence has not yet been verified and is a state which should
     * not be shown to users.
     */
    protected Existence existence;

    /**
     * Reference to the wrapper of the class that contains this element.
     */
    private final ClassWrapper<T> parentClassWrapper;

    /**
     * The expected modifier keywords, parsed once at construction.
     */
    private final Set<String> expectedModifierSet;

    /**
     * Whether the lookup in the student code already ran.
     */
    private boolean checked;


    /**
     * Constructs a new Wrapper for a reflection element with expected name and modifiers.
     *
     * @param parentClass the class wrapper containing this element
     * @param expectedName the expected name of the element
     * @param expectedModifiers the expected modifiers (e.g., "public", "static", "final"); a single string may
     *                          also contain several space separated modifiers
     * @throws IllegalArgumentException if an expected modifier is unknown (a typo in the test specification)
     */
    public Wrapper(ClassWrapper<T> parentClass, String expectedName, String... expectedModifiers) {
        this.expectedModifierSet = parseModifiers(expectedModifiers);
        this.name = new WrapperProperty<>(expectedName);
        this.parentClassWrapper = parentClass;
        this.modifiers = new WrapperProperty<>(String.join(" ", expectedModifierSet));
        this.existence = UNCHECKED;
    }

    private static Set<String> parseModifiers(String... expectedModifiers) {
        Set<String> result = new LinkedHashSet<>();
        if (expectedModifiers == null) {
            return result;
        }
        for (String part : expectedModifiers) {
            if (part == null) {
                continue;
            }
            for (String token : part.trim().split("\\s+")) {
                if (token.isEmpty()) {
                    continue;
                }
                if (!KNOWN_MODIFIERS.contains(token)) {
                    throw new IllegalArgumentException("Unknown modifier '" + token + "' in expected modifiers "
                            + Arrays.toString(expectedModifiers) + ". Known modifiers: " + KNOWN_MODIFIERS);
                }
                result.add(token);
            }
        }
        return result;
    }


    /**
     * Verifies that the wrapped element exists in the actual class.
     * Subclasses must implement this to perform element-specific existence checks.
     *
     * @param throwAssertion if true, throws an AssertJ assertion failure when the element is missing. Usually set to true
     *                       for behavioral tests, and false for structural tests to allow further deviation analysis.
     */
    public abstract void verifyExistence(boolean throwAssertion);

    /**
     * Verifies existence with a custom failure message.
     * Runs the lookup if it has not run yet, then throws an assertion if the element is not usable
     * and throwAssertion is true.
     *
     * @param failMessage the custom failure message to display if the element is missing
     * @param throwAssertion if true, throws an AssertJ assertion failure when the element is missing. Usually set to true
     *                       for behavioral tests, and false for structural tests to allow further deviation analysis.
     */
    protected void verifyExistence(String failMessage, boolean throwAssertion)
    {
        ensureChecked();
        if(throwAssertion) {
            Assertions.assertThat(usability())
                    .withFailMessage(failMessage)
                    .isNotEqualTo(MISSING);
        }
    }

    /**
     * Returns the state that decides whether the element can be used in behavioral tests.
     * By default this is the overall existence; subclasses may relax it (a class whose superclass deviates
     * can still be instantiated, for example).
     *
     * @return the existence state relevant for using the element
     */
    protected Existence usability() {
        return existence;
    }

    /**
     * Runs the lookup in the student code exactly once.
     */
    protected final void ensureChecked() {
        if (!checked) {
            // set first: wrappers may (indirectly) reference each other, e.g. via superclass wrappers
            checked = true;
            findWithDeviation();
            parseExistence();
        }
    }

    /**
     * Retrieves the overall existence state of this wrapped element.
     * Triggers deviation detection if not yet checked.
     * Worst part-wise existence determines the overall existence.
     *
     * @return the existence state (EXACT, DEVIATES or MISSING)
     */
    public Existence getOverallExistence() {
        ensureChecked();
        return existence;
    }

    /**
     * Searches for the wrapped element using deviation detection (e.g., Levenshtein distance).
     * Subclasses must implement this to perform element-specific searches and update the property states.
     * Called at most once per wrapper, see {@link #ensureChecked()}.
     */
    protected abstract void findWithDeviation();


    /**
     * Whether an unexpected {@code static} modifier on the actual element is reported as a deviation.
     * Off by default (classes, constructors); attributes and methods of classes turn it on.
     *
     * @return true if an unexpected {@code static} should be reported
     */
    protected boolean reportUnexpectedStatic() {
        return false;
    }

    /**
     * Verifies that the actual modifiers match the expected modifiers.
     * Compares the expected modifiers (e.g., "public", "static", "final") with the actual modifier bitmask.
     * Updates the modifiers property with EXACT if all match, DEVIATES for non-critical mismatches,
     * or MISSING for critical mismatches (e.g., missing "static").
     * If no modifier is expected at all, package-private access is expected.
     *
     * @param modifierBitmask the actual modifiers as a bitmask (from {@link java.lang.reflect.Member#getModifiers()})
     */
    public void verifyModifiers(int modifierBitmask) {
        modifiers.actual = Modifier.toString(modifierBitmask);

        Existence worst = EXACT; // the worst existence defines if the modifiers deviate or are wrong
        if (expectedModifierSet.isEmpty()) {
            boolean packagePrivate = !Modifier.isPublic(modifierBitmask)
                    && !Modifier.isPrivate(modifierBitmask)
                    && !Modifier.isProtected(modifierBitmask);
            worst = packagePrivate ? worst : selectWorstExistence(worst, DEVIATES);
        }
        for (String modifier : expectedModifierSet) {
            boolean present = switch (modifier) {
                case "public" -> Modifier.isPublic(modifierBitmask);
                case "private" -> Modifier.isPrivate(modifierBitmask);
                case "protected" -> Modifier.isProtected(modifierBitmask);
                case "static" -> Modifier.isStatic(modifierBitmask);
                case "final" -> Modifier.isFinal(modifierBitmask);
                case "abstract" -> Modifier.isAbstract(modifierBitmask);
                case "synchronized" -> Modifier.isSynchronized(modifierBitmask);
                case "native" -> Modifier.isNative(modifierBitmask);
                case "transient" -> Modifier.isTransient(modifierBitmask);
                case "volatile" -> Modifier.isVolatile(modifierBitmask);
                case "strictfp" -> Modifier.isStrict(modifierBitmask);
                case "interface" -> Modifier.isInterface(modifierBitmask);
                default -> throw new IllegalStateException("Unknown modifier: " + modifier); // rejected in constructor
            };
            if (!present) {
                // a missing "static" makes the element unusable as specified; everything else is a deviation
                worst = selectWorstExistence(worst, modifier.equals("static") ? MISSING : DEVIATES);
            }
        }
        if (reportUnexpectedStatic() && !expectedModifierSet.contains("static") && Modifier.isStatic(modifierBitmask)) {
            worst = selectWorstExistence(worst, DEVIATES);
        }
        modifiers.existence = worst;
    }

    /**
     * Returns whether the given modifier keyword is part of the expected modifiers.
     *
     * @param modifier the modifier keyword, e.g. {@code "abstract"}
     * @return true if it was expected
     */
    protected boolean isModifierExpected(String modifier) {
        return expectedModifierSet.contains(modifier);
    }


    /**
     * Retrieves the parent class wrapper containing this element.
     * If this wrapper is itself a ClassWrapper, returns itself; otherwise returns the parent.
     *
     * @return the parent ClassWrapper
     */
    public ClassWrapper<T> getParentClassWrapper() {
        return this instanceof ClassWrapper<T> t ? t : parentClassWrapper;
    }

    /**
     * <p>Returns a string representation of this wrapped element showing its existence state.
     * The returned string is formatted to be shown to users, indicating whether the element matches exactly,
     * deviates, is missing, or is unchecked. Reuses {@link #expectedToString()} and {@link #actualToString()}
     * for representing the two states.</p>
     * <p> For {@link Existence#EXACT} matches, shows expected (= actual) values.</p>
     * <p> For {@link Existence#DEVIATES}, shows both expected and actual values.</p>
     * <p> For {@link Existence#MISSING}, shows what was expected.</p>
     *
     * @return a formatted string describing the element's state
     */
    @Override
    public String toString()
    {
        return switch (getOverallExistence()) {
            case EXACT -> expectedToString();
            case DEVIATES -> Messages.WRAPPER_DEVIATION.format(
                    getParentClassWrapper().name.expected, expectedToString(), actualToString()
            );
            case MISSING -> Messages.WRAPPER_MISSING.format(
                    getParentClassWrapper().name.expected, expectedToString()
            );
            case UNCHECKED -> Messages.WRAPPER_UNCHECKED.format(
                    getParentClassWrapper().name.expected, expectedToString()
            );
        };
    }

    /**
     * Returns a string representation of the expected element signature.
     * Subclasses must implement this to provide element-specific formatting.
     *
     * @return a string showing the expected element details
     */
    public abstract String expectedToString();

    /**
     * Returns a string representation of the actual element signature found.
     * Subclasses must implement this to provide element-specific formatting.
     *
     * @return a string showing the actual element details
     */
    public abstract String actualToString();

    /**
     * Parses and updates the existence state based on all properties.
     * Subclasses must implement this to define how to aggregate property states.
     */
    protected abstract void parseExistence();

    /**
     * Helper method to parse existence state from multiple wrapper properties.
     * Selects the worst existence state among all provided properties; unchecked properties are ignored.
     *
     * @param properties the wrapper properties to check
     */
    protected void parseExistence(WrapperProperty<?> ... properties) {
        Existence result = UNCHECKED;
        for (WrapperProperty<?> property : properties) {
            result = Existence.worst(result, property.existence);
        }
        existence = result;
    }

    /**
     * Retrieves the expected name of this wrapped element instead of
     * exposing the {@link #name} property directly.
     *
     * @return the expected name as a string
     */
    public String getExpectedName() {
        return name.expected;
    }

    /**
     * Retrieves the actual name of the element found in the student code.
     *
     * @return the actual name, or {@code null} if the element is missing
     */
    public String getActualName() {
        ensureChecked();
        return name.actual;
    }

    /**
     * Retrieves the actual modifiers of the element found in the student code.
     *
     * @return the actual modifiers (e.g. {@code "public static"}), or {@code null} if the element is missing
     */
    public String getActualModifiers() {
        ensureChecked();
        return modifiers.actual;
    }

    private static Existence selectWorstExistence(Existence a, Existence b) {
        return Existence.worst(a, b);
    }
}
