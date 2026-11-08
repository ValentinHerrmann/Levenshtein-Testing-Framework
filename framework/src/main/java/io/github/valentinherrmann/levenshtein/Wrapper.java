package io.github.valentinherrmann.levenshtein;

import org.assertj.core.api.Assertions;

import static io.github.valentinherrmann.levenshtein.WrapperProperty.Existence;
import static io.github.valentinherrmann.levenshtein.WrapperProperty.Existence.*;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
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

    /** The visibility bits; a different visibility is reported as a missing expected modifier only. */
    private static final int VISIBILITY = Modifier.PUBLIC | Modifier.PROTECTED | Modifier.PRIVATE;

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
     * The expected (required) modifier keywords, parsed once at construction.
     */
    private final Set<String> expectedModifierSet;

    /**
     * The optional modifier keywords (written with a {@code ?} suffix, e.g. {@code "final?"}): they may be
     * present or absent without a deviation.
     */
    private final Set<String> optionalModifierSet;

    /**
     * Expected modifiers that the actual element lacks (filled by {@link #verifyModifiers(int)}).
     */
    private final List<String> missingModifiers = new ArrayList<>();

    /**
     * Modifiers of the actual element that are neither expected, optional nor implicit
     * (filled by {@link #verifyModifiers(int)}).
     */
    private final List<String> unexpectedModifiers = new ArrayList<>();

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
     *                          also contain several space separated modifiers. A {@code ?} suffix marks an
     *                          optional modifier that may be present or absent (e.g. {@code "final?"}).
     * @throws IllegalArgumentException if an expected modifier is unknown (a typo in the test specification),
     *                                  if a visibility is marked optional or if a modifier is both required and
     *                                  optional
     */
    public Wrapper(ClassWrapper<T> parentClass, String expectedName, String... expectedModifiers) {
        this.expectedModifierSet = parseModifiers(false, expectedModifiers);
        this.optionalModifierSet = parseModifiers(true, expectedModifiers);
        for (String optional : optionalModifierSet) {
            if (expectedModifierSet.contains(optional)) {
                throw new IllegalArgumentException("Modifier '" + optional + "' is both required and optional in "
                        + Arrays.toString(expectedModifiers));
            }
        }
        this.name = new WrapperProperty<>(expectedName);
        this.parentClassWrapper = parentClass;
        this.modifiers = new WrapperProperty<>(String.join(" ", expectedModifierSet));
        this.existence = UNCHECKED;
    }

    /**
     * Parses the required ({@code optional == false}) or the optional ({@code optional == true}) modifiers.
     */
    private static Set<String> parseModifiers(boolean optional, String... expectedModifiers) {
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
                boolean isOptional = token.endsWith("?");
                String keyword = isOptional ? token.substring(0, token.length() - 1) : token;
                if (!KNOWN_MODIFIERS.contains(keyword)) {
                    throw new IllegalArgumentException("Unknown modifier '" + token + "' in expected modifiers "
                            + Arrays.toString(expectedModifiers) + ". Known modifiers: " + KNOWN_MODIFIERS);
                }
                if (isOptional && (bit(keyword) & VISIBILITY) != 0) {
                    throw new IllegalArgumentException("A visibility cannot be optional: '" + token
                            + "' in expected modifiers " + Arrays.toString(expectedModifiers));
                }
                if (isOptional == optional) {
                    result.add(keyword);
                }
            }
        }
        return result;
    }

    /**
     * Maps a modifier keyword to its {@link Modifier} bit.
     *
     * @param keyword a keyword of {@link #KNOWN_MODIFIERS}
     * @return the bit, e.g. {@link Modifier#STATIC} for {@code "static"}
     */
    static int bit(String keyword) {
        return switch (keyword) {
            case "public" -> Modifier.PUBLIC;
            case "private" -> Modifier.PRIVATE;
            case "protected" -> Modifier.PROTECTED;
            case "static" -> Modifier.STATIC;
            case "final" -> Modifier.FINAL;
            case "abstract" -> Modifier.ABSTRACT;
            case "synchronized" -> Modifier.SYNCHRONIZED;
            case "native" -> Modifier.NATIVE;
            case "transient" -> Modifier.TRANSIENT;
            case "volatile" -> Modifier.VOLATILE;
            case "strictfp" -> Modifier.STRICT;
            case "interface" -> Modifier.INTERFACE;
            default -> throw new IllegalArgumentException("Unknown modifier: " + keyword);
        };
    }

    private static int bits(Set<String> keywords) {
        int result = 0;
        for (String keyword : keywords) {
            result |= bit(keyword);
        }
        return result;
    }

    /**
     * The keywords of all modifier bits set in the given mask, in the order of {@link #KNOWN_MODIFIERS}.
     */
    private static List<String> keywords(int mask) {
        List<String> result = new ArrayList<>();
        for (String keyword : KNOWN_MODIFIERS) {
            if ((mask & bit(keyword)) != 0) {
                result.add(keyword);
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
     * The modifier bits that are meaningful for this kind of element. Other bits of the bitmask are ignored:
     * for methods, the bits of {@code volatile} and {@code transient} mean bridge and varargs.
     * The default fits classes; methods, attributes and constructors override it.
     *
     * @return the mask of relevant modifier bits
     */
    protected int relevantModifierMask() {
        return Modifier.classModifiers() | Modifier.INTERFACE;
    }

    /**
     * The modifier bits that the actual element has implicitly, e.g. {@code abstract} for interfaces or
     * {@code static final} for constants of interfaces. They are never reported as unexpected.
     * Called after the element was found.
     *
     * @return the mask of implicit modifier bits
     */
    protected int implicitModifiers() {
        return Modifier.STRICT; // strictfp has no effect since Java 17 and is not emitted by javac any more
    }

    /**
     * Whether an unexpected {@code static} is reported with
     * {@link LevenshteinSettings.UnexpectedModifiers#IGNORE} (the behaviour before unexpected modifiers were
     * reported in general). Attributes and methods turn it on.
     *
     * @return true if an unexpected {@code static} is reported in the legacy mode
     */
    protected boolean legacyReportsUnexpectedStatic() {
        return false;
    }

    /**
     * Verifies that the actual modifiers match the expected modifiers.
     * <ul>
     *   <li>An expected modifier that is missing is a deviation ({@link Existence#DEVIATES}): behaviour tests go
     *       on with the actual element, e.g. a method that should be {@code static} is called on the default
     *       object.</li>
     *   <li>An actual modifier that is neither expected, optional ({@code "final?"}) nor implicit (see
     *       {@link #implicitModifiers()}) is a deviation as well. A different visibility is only reported as a
     *       missing expected visibility.</li>
     *   <li>If no modifier is specified at all (neither required nor optional), package-private access is
     *       expected. Otherwise a visibility is only checked if one is specified.</li>
     * </ul>
     * With {@link LevenshteinSettings.UnexpectedModifiers#IGNORE} the previous behaviour applies: a missing
     * {@code static} makes the element {@link Existence#MISSING} and only an unexpected {@code static} of
     * attributes and methods is reported.
     *
     * @param modifierBitmask the actual modifiers as a bitmask (from {@link java.lang.reflect.Member#getModifiers()})
     */
    public void verifyModifiers(int modifierBitmask) {
        int actual = modifierBitmask & relevantModifierMask();
        modifiers.actual = Modifier.toString(actual);
        missingModifiers.clear();
        unexpectedModifiers.clear();
        boolean legacy = LevenshteinSettings.getUnexpectedModifiers() == LevenshteinSettings.UnexpectedModifiers.IGNORE;

        Existence worst = EXACT; // the worst existence defines if the modifiers deviate or are wrong
        if (expectedModifierSet.isEmpty() && optionalModifierSet.isEmpty() && (actual & VISIBILITY) != 0) {
            worst = DEVIATES; // package-private expected; optional modifiers alone leave the visibility unchecked
        }
        for (String modifier : expectedModifierSet) {
            if ((actual & bit(modifier)) == 0) {
                missingModifiers.add(modifier);
                // legacy: a missing "static" made the element unusable; now it is used on the default object
                worst = selectWorstExistence(worst, legacy && modifier.equals("static") ? MISSING : DEVIATES);
            }
        }
        int unexpected = actual & ~VISIBILITY & ~bits(expectedModifierSet) & ~bits(optionalModifierSet)
                & ~implicitModifiers();
        if (legacy) {
            unexpected &= legacyReportsUnexpectedStatic() ? Modifier.STATIC : 0;
        }
        if (unexpected != 0) {
            unexpectedModifiers.addAll(keywords(unexpected));
            worst = selectWorstExistence(worst, DEVIATES);
        }
        modifiers.existence = worst;
    }

    /**
     * Returns whether the actual element has the given modifier although it is neither expected, optional nor
     * implicit. Only meaningful after the lookup ran.
     *
     * @param modifier the modifier keyword, e.g. {@code "static"}
     * @return true if the modifier was reported as unexpected
     */
    protected boolean hasUnexpectedModifier(String modifier) {
        return unexpectedModifiers.contains(modifier);
    }

    /**
     * @return the expected modifiers the actual element lacks (empty if the element is missing or matches)
     */
    public List<String> getMissingModifiers() {
        ensureChecked();
        return List.copyOf(missingModifiers);
    }

    /**
     * @return the modifiers of the actual element that are neither expected, optional nor implicit
     */
    public List<String> getUnexpectedModifiers() {
        ensureChecked();
        return List.copyOf(unexpectedModifiers);
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
            ) + modifierNotes();
            case MISSING -> Messages.WRAPPER_MISSING.format(
                    getParentClassWrapper().name.expected, expectedToString()
            );
            case UNCHECKED -> Messages.WRAPPER_UNCHECKED.format(
                    getParentClassWrapper().name.expected, expectedToString()
            );
        };
    }

    /**
     * @return one line per kind of modifier deviation (missing, unexpected), or an empty string
     */
    private String modifierNotes() {
        StringBuilder notes = new StringBuilder();
        if (!missingModifiers.isEmpty()) {
            notes.append(Messages.MISSING_MODIFIERS.format(String.join(" ", missingModifiers))).append('\n');
        }
        if (!unexpectedModifiers.isEmpty()) {
            notes.append(Messages.UNEXPECTED_MODIFIERS.format(String.join(" ", unexpectedModifiers))).append('\n');
        }
        return notes.toString();
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
