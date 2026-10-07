package io.github.valentinherrmann.levenshtein;

/**
 * Central message catalog for all assertion and feedback messages shown to students.
 *
 * <h2>Language Switching</h2>
 * <p>All messages are available in German and English. The active language is read from
 * {@link LevenshteinSettings#getLanguage()} every time a message is requested, so it can be
 * switched at runtime, e.g. in a static initializer or {@code @BeforeAll} of the exam's test class:</p>
 * <pre>{@code
 * LevenshteinSettings.setLanguage(LevenshteinSettings.Language.ENGLISH);
 * }</pre>
 *
 * <h2>Usage</h2>
 * <p>All entries are format templates:</p>
 * <pre>{@code
 * fail(Messages.CLASS_NOT_IMPLEMENTED.format(name, pkg));
 * assertThat(x).withFailMessage(Messages.GETTER_WRONG_VALUE.get(), getter, expected, actual).isEqualTo(actual);
 * }</pre>
 *
 * @see LevenshteinSettings#getLanguage()
 */
public enum Messages {

    // -------------------------------------------------------------------------
    // Wrapper – general existence states (used in Wrapper.toString())
    // -------------------------------------------------------------------------

    /**
     * Shown when a structural element deviates from the expected signature.
     * Placeholders: (1) parent class name, (2) expected toString, (3) actual toString.
     */
    WRAPPER_DEVIATION(
            """
              ⚠️ ABWEICHUNG in %s ⚠️
              Falls möglich wird der tatsächliche Wert für weitere Tests verwendet.
              Erwartet:\t%s
              Tatsächlich:\t%s
              """,
            """
              ⚠️ DEVIATION in %s ⚠️
              If possible actual will be used for further testing.
              Expect:\t%s
              Actual:\t%s
              """),

    /**
     * Shown when a structural element is completely missing.
     * Placeholders: (1) parent class name, (2) expected toString.
     */
    WRAPPER_MISSING(
            """
              ❌ FEHLT in %s ❌
              Erwartet:\t%s
              """,
            """
              ❌ MISSING in %s ❌
              Expect:\t%s
              """),

    /**
     * Shown when a structural element has not been checked yet (debug / internal state).
     * Placeholders: (1) parent class name, (2) expected toString.
     */
    WRAPPER_UNCHECKED(
            """
              ? NICHT ÜBERPRÜFT in %s ?
              Erwartet:\t%s
              """,
            """
              ? UNCHECKED in %s ?
              Expect:\t%s
              """),

    // -------------------------------------------------------------------------
    // ClassWrapper – toString() intro lines
    // -------------------------------------------------------------------------

    /** Intro line for a deviating class in ClassWrapper.toString(). */
    CLASS_DEVIATION(
            "⚠️ ABWEICHUNG ⚠️\nFalls möglich wird der tatsächliche Wert für weitere Tests verwendet",
            "⚠️ DEVIATION ⚠️\nIf possible actual will be used for further testing"),

    /** Intro line for a missing class in ClassWrapper.toString(). */
    CLASS_MISSING(
            "❌ FEHLT ❌",
            "❌ MISSING ❌"),

    /** Intro line for an unchecked class in ClassWrapper.toString(). */
    CLASS_UNCHECKED(
            "Existenz nicht überprüft",
            "Existence unchecked"),

    // -------------------------------------------------------------------------
    // ClassWrapper – verifyExistence
    // -------------------------------------------------------------------------

    /**
     * Shown when the expected class is missing or deviates.
     * Placeholders: (1) class name, (2) package name.
     */
    CLASS_NOT_IMPLEMENTED(
            """
              Klasse %s im Paket %s ist nicht wie erwartet implementiert.
              --> Weitere Details in den Struktur-Tests.
              --> Dies kann dazu führen, dass nachfolgende Tests fehlschlagen.
              """,
            """
              Class %s in package %s is not implemented as expected.
              --> See structural Tests for details about this.
              --> This may lead subsequent tests to fail.
              """),

    // -------------------------------------------------------------------------
    // ClassWrapper – getObj (instantiation failures)
    // -------------------------------------------------------------------------

    /**
     * Shown when trying to directly instantiate an abstract class.
     * Placeholder: (1) class name.
     */
    CLASS_CANNOT_INSTANTIATE_ABSTRACT(
            "Abstrakte Klasse %s kann nicht direkt instanziiert werden.\nDies kann dazu führen, dass nachfolgende Tests fehlschlagen.",
            "Cannot instantiate abstract class %s directly.\nThis may lead subsequent tests to fail."),

    /**
     * Shown when trying to directly instantiate an interface.
     * Placeholder: (1) interface name.
     */
    CLASS_CANNOT_INSTANTIATE_INTERFACE(
            "Interface %s kann nicht direkt instanziiert werden.\nDies kann dazu führen, dass nachfolgende Tests fehlschlagen.",
            "Cannot instantiate interface %s directly.\nThis may lead subsequent tests to fail."),

    /**
     * Shown when instantiating a class via its constructor fails.
     * Placeholder: (1) class name.
     */
    CLASS_INSTANTIATION_FAILED(
            "Das Erstellen von Instanzen der Klasse %s ist fehlgeschlagen. Der Konstruktor ist möglicherweise nicht korrekt implementiert.\nDies kann dazu führen, dass nachfolgende Tests fehlschlagen.",
            "Creating instances of class %s failed. Constructor may not be implemented correctly.\nThis may lead subsequent tests to fail."),

    /**
     * Shown when creating a ByteBuddy dynamic subclass instance fails.
     * Placeholders: (1) class name, (2) error message.
     */
    CLASS_SUBCLASS_INSTANTIATION_FAILED(
            "Das Erstellen von Instanzen einer Unterklasse von %s ist fehlgeschlagen. Der Konstruktor ist möglicherweise nicht korrekt implementiert. Dies kann dazu führen, dass nachfolgende Tests fehlschlagen. Fehler: %s",
            "Creating instances of a subclass of %s failed. Constructor may not be implemented correctly. This might also cause subsequent tests to fail. Error: %s"),

    // -------------------------------------------------------------------------
    // ClassWrapper – testGetter
    // -------------------------------------------------------------------------

    /**
     * Shown when a getter method does not return the expected attribute value.
     * Placeholders: (1) getter signature, (2) expected value, (3) actual value.
     */
    GETTER_WRONG_VALUE(
            """
              Getter '%s' gibt nicht den Attributwert zurück.
              Erwartet: %s
              Tatsächlich: %s""",
            """
              Getter '%s' does not return the attribute's value.
              Expected: %s
              Actual: %s"""),

    // -------------------------------------------------------------------------
    // MethodWrapper
    // -------------------------------------------------------------------------

    /**
     * Shown when the expected method is missing or deviates.
     * Placeholders: (1) method signature, (2) class name.
     */
    METHOD_NOT_IMPLEMENTED(
            """
              Methode %s in Klasse %s ist nicht wie erwartet implementiert.
              --> Weitere Details in den Struktur-Tests.
              --> Dies kann dazu führen, dass nachfolgende Tests fehlschlagen.""",
            """
              Method %s in class %s is not implemented as expected.
              --> See structural Tests for details about this.
              --> This may lead subsequent tests to fail."""),

    /**
     * Shown when invoking a method throws an unexpected exception.
     * Placeholders: (1) method signature, (2) class name.
     */
    METHOD_INVOCATION_EXCEPTION(
            "Der Aufruf der Methode %s auf Klasse %s hat eine Ausnahme geworfen: %s",
            "Calling method %s on class %s threw an exception: %s"),

    // -------------------------------------------------------------------------
    // AttributeWrapper
    // -------------------------------------------------------------------------

    /**
     * Shown when the expected attribute is missing or deviates.
     * Placeholders: (1) attribute name, (2) class name.
     */
    ATTRIBUTE_NOT_IMPLEMENTED(
            """
              Attribut %s in Klasse %s ist nicht wie erwartet implementiert.
              --> Weitere Details in den Struktur-Tests.
              --> Dies kann dazu führen, dass nachfolgende Tests fehlschlagen.""",
            """
              Attribute %s in class %s is not implemented as expected.
              --> See structural Tests for details about this.
              --> This may lead subsequent tests to fail."""),

    /**
     * Shown when reading the value of an attribute fails.
     * Placeholders: (1) attribute name, (2) class name, (3) cause / exception message.
     */
    ATTRIBUTE_ACCESS_FAILED(
            "Zugriff auf den Wert des Attributs '%s' in %s nicht möglich. Ursache: %s",
            "Could not access value of attribute '%s' in %s. Cause: %s"),

    /**
     * Shown when trying to set the value of a final attribute.
     * Placeholders: (1) attribute name, (2) class name.
     */
    ATTRIBUTE_FINAL_CANNOT_SET(
            "Der Wert des Attributs %s in %s kann nicht gesetzt werden, da es als final deklariert ist (sollte es aber nicht sein).",
            "Cannot set value of attribute %s in %s because it is declared final (but should not be)."),

    /**
     * Shown when setting the value of an attribute fails for an unexpected reason.
     * Placeholders: (1) attribute name, (2) class name.
     */
    ATTRIBUTE_SET_FAILED(
            "Wert des Attributs '%s' in %s konnte nicht gesetzt werden.",
            "Could not set value of attribute '%s' in %s."),

    // -------------------------------------------------------------------------
    // ConstructorWrapper
    // -------------------------------------------------------------------------

    /**
     * Shown when the expected constructor is missing or deviates.
     * Placeholders: (1) constructor signature, (2) class name.
     */
    CONSTRUCTOR_NOT_IMPLEMENTED(
            """
              Konstruktor %s in Klasse %s ist nicht wie erwartet implementiert.
              --> Weitere Details in den Struktur-Tests.
              --> Dies kann dazu führen, dass nachfolgende Tests fehlschlagen.
              """,
            """
              Constructor %s in class %s is not implemented as expected.
              --> See structural Tests for details about this.
              --> This may lead subsequent tests to fail.
              """),

    /**
     * Shown when invoking a constructor throws an unexpected exception.
     * Placeholders: (1) constructor signature, (2) class name, (3) exception message.
     */
    CONSTRUCTOR_INVOCATION_FAILED(
            "Konstruktor '%s' in Klasse %s konnte nicht aufgerufen werden: %s",
            "Failed to invoke constructor '%s' in class %s: %s"),

    // -------------------------------------------------------------------------
    // Utils
    // -------------------------------------------------------------------------

    /**
     * Shown when student code was expected to throw an exception but did not, or threw a different one.
     * Placeholders: (1) member signature, (2) class name, (3) expected exception type, (4) actual outcome.
     */
    EXPECTED_EXCEPTION_NOT_THROWN(
            "%s in Klasse %s sollte eine %s werfen, aber: %s",
            "%s in class %s should throw %s, but: %s"),

    /** Used as the "actual outcome" of {@link #EXPECTED_EXCEPTION_NOT_THROWN} when nothing was thrown. */
    NOTHING_THROWN(
            "es wurde keine Ausnahme geworfen",
            "no exception was thrown"),

    /**
     * Shown when a value returned by student code cannot be converted to the expected type.
     * Placeholders: (1) value, (2) actual type, (3) expected type.
     */
    CAST_FAILED(
            "Der Wert %s vom Typ %s kann nicht in den erwarteten Typ %s umgewandelt werden.",
            "The value %s of type %s cannot be converted to the expected type %s."),

    /** Thrown internally when a value is null where null is not permitted. */
    NULL_NOT_ALLOWED(
            "Ein Wert war null, der nicht null sein darf",
            "A value was null that is not allowed to be null");


    private final String german;
    private final String english;

    Messages(String german, String english) {
        this.german = german;
        this.english = english;
    }

    /**
     * Returns the message in the language currently configured in {@link LevenshteinSettings#getLanguage()}.
     * The language is resolved on every call, so it can be changed at runtime (e.g. in {@code @BeforeAll}).
     *
     * @return the localized message template
     */
    public String get() {
        return LevenshteinSettings.getLanguage() == LevenshteinSettings.Language.ENGLISH ? english : german;
    }

    /**
     * Formats the localized message template with the given arguments.
     *
     * @param args the format arguments
     * @return the formatted, localized message
     */
    public String format(Object... args) {
        return String.format(get(), args);
    }

    @Override
    public String toString() {
        return get();
    }
}
