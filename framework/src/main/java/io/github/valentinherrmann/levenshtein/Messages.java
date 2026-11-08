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
 * assertThat(x).withFailMessage(Messages.CAST_FAILED.get(), value, actualType, expectedType).isEqualTo(y);
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
     * Appended to a deviation when expected modifiers are missing.
     * Placeholders: (1) the missing modifiers, e.g. {@code "static"}.
     */
    MISSING_MODIFIERS(
            "fehlender Modifikator: %s",
            "missing modifier: %s"),

    /**
     * Appended to a deviation when the actual element has modifiers that are not expected.
     * Placeholders: (1) the unexpected modifiers, e.g. {@code "static"}.
     */
    UNEXPECTED_MODIFIERS(
            "unerwarteter Modifikator: %s",
            "unexpected modifier: %s"),

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

    /**
     * Report of a class in ClassWrapper.toString().
     * Placeholders: (1) intro line, (2) package name, (3) expected toString, (4) actual toString.
     */
    CLASS_REPORT(
            """
              %s im Paket %s
              Erwartet:\t%s
              Tatsächlich:\t%s
              """,
            """
              %s in package %s
              Expect:\t%s
              Actual:\t%s
              """),

    /** Shown instead of the package name for the default package. */
    DEFAULT_PACKAGE(
            "<Standardpaket>",
            "<default>"),

    /**
     * Shown in a structural test when checking one element failed unexpectedly.
     * Placeholders: (1) class name, (2) expected toString, (3) cause.
     */
    WRAPPER_CHECK_FAILED(
            "%s: %s konnte nicht überprüft werden: %s",
            "%s: %s could not be checked: %s"),

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
    // ClassWrapper – mocks (mock(), newObjOverriding, newObjWithoutConstructor)
    // -------------------------------------------------------------------------

    /**
     * Shown when a class cannot be subclassed for a mock.
     * Placeholders: (1) class name, (2) reason (e.g. {@code final}, {@code record}, {@code enum}, {@code sealed}).
     */
    MOCK_CLASS_NOT_OVERRIDABLE(
            "%s: kann nicht überschrieben werden (%s)",
            "%s: cannot be overridden (%s)"),

    /**
     * Shown when an enum should be created without a constructor.
     * Placeholder: (1) class name.
     */
    MOCK_ENUM_NOT_INSTANTIABLE(
            "%s: von einem enum können keine Objekte erzeugt werden",
            "%s: objects of an enum cannot be created"),

    /**
     * Shown when a method cannot be overridden in a mock.
     * Placeholders: (1) method signature, (2) class name, (3) reason (e.g. {@code final}, {@code static}).
     */
    MOCK_METHOD_NOT_OVERRIDABLE(
            "%s in Klasse %s: kann nicht überschrieben werden (%s)",
            "%s in class %s: cannot be overridden (%s)"),

    /**
     * Shown when a method wrapper of another class is used for a mock (e.g. the class does not extend it).
     * Placeholders: (1) method signature, (2) class of the method, (3) class of the mock.
     */
    MOCK_METHOD_NOT_IN_CLASS(
            "%s aus Klasse %s gehört nicht zur Klasse %s und kann dort nicht überschrieben werden",
            "%s of class %s does not belong to class %s and cannot be overridden there"),

    /**
     * Shown when student code calls an abstract method that the test did not override.
     * Placeholders: (1) method name, (2) class name.
     */
    MOCK_ABSTRACT_NOT_OVERRIDDEN(
            "Die abstrakte Methode %s in Klasse %s wurde aufgerufen, aber vom Test nicht überschrieben.",
            "The abstract method %s in class %s was called, but the test does not override it."),

    /**
     * Shown when a method was not called as often as expected.
     * Placeholders: (1) method signature, (2) class name, (3) expected count, (4) actual count.
     */
    MOCK_CALL_COUNT(
            "%s in Klasse %s sollte %d-mal aufgerufen werden, wurde aber %d-mal aufgerufen.",
            "%s in class %s should be called %d time(s), but was called %d time(s)."),

    /**
     * Appended on its own line to {@link #MOCK_CALL_COUNT} if there were calls.
     * Placeholder: (1) the arguments of each call, e.g. {@code (5), (7)}.
     */
    MOCK_RECORDED_CALLS(
            "Aufrufe: %s",
            "Calls: %s"),

    // -------------------------------------------------------------------------
    // Failure – the block of every behaviour failure
    // -------------------------------------------------------------------------

    /** Label of the line naming the method or constructor in a {@link Failure} block. */
    FAILURE_METHOD(
            "Methode",
            "Method"),

    /** Label of the line describing the scenario (e.g. a generated case) in a {@link Failure} block. */
    FAILURE_SCENARIO(
            "Szenario",
            "Scenario"),

    /** Label of the line with the input (e.g. the arguments) in a {@link Failure} block. */
    FAILURE_INPUT(
            "Eingabe",
            "Input"),

    /** Label of the line with the expected outcome in a {@link Failure} block. */
    FAILURE_EXPECTED(
            "Erwartet",
            "Expected"),

    /** Label of the line with the actual outcome in a {@link Failure} block. */
    FAILURE_ACTUAL(
            "Tatsächlich",
            "Actual"),

    /**
     * Appended below a {@link Failure} block when several cases were checked.
     * Placeholders: (1) failed cases, (2) all cases.
     */
    FAILURE_CASES(
            "(%d von %d Fällen fehlgeschlagen)",
            "(%d of %d cases failed)"),

    /** Appended below a {@link Failure} block whose actual outcome is a {@link StackOverflowError}. */
    FAILURE_STACK_OVERFLOW_HINT(
            "Hinweis: Ein StackOverflowError deutet meist auf eine endlose Rekursion hin (eine Methode ruft sich immer wieder selbst auf).",
            "Hint: a StackOverflowError usually means endless recursion (a method keeps calling itself)."),

    /**
     * Expected outcome of a call that should throw an exception.
     * Placeholder: (1) the exception type.
     */
    FAILURE_EXPECTED_EXCEPTION(
            "%s wird geworfen",
            "%s is thrown"),

    /**
     * Expected outcome of a setter call with an invalid value.
     * Placeholder: (1) the value before the call.
     */
    GETTERS_SETTERS_EXPECT_REJECTED(
            "Wert abgelehnt (unverändert %s oder Ausnahme)",
            "value rejected (unchanged %s or exception)"),

    /**
     * Scenario of a getter/setter case.
     * Placeholders: (1) subtest label, e.g. "setter -> attribute", (2) attribute name.
     */
    GETTERS_SETTERS_SCENARIO(
            "%s, Attribut %s",
            "%s, attribute %s"),

    // -------------------------------------------------------------------------
    // ClassWrapper – testGettersSetters
    // -------------------------------------------------------------------------

    /**
     * Report of a failed getter/setter test, one line per subtest.
     * Placeholders: (1) attribute name, (2) class name, (3) subtest lines.
     *
     * @deprecated no longer used, behaviour failures are reported as {@link Failure} block
     */
    @Deprecated
    GETTERS_SETTERS_REPORT(
            "Getter/Setter-Test für Attribut %s in Klasse %s:\n%s",
            "Getter/setter test for attribute %s in class %s:\n%s"),

    /** Subtest label: the setter stores the value in the attribute. */
    GETTERS_SETTERS_SETTER_TO_ATTRIBUTE(
            "Setter -> Attribut",
            "setter -> attribute"),

    /** Subtest label: the getter returns the value of the attribute. */
    GETTERS_SETTERS_ATTRIBUTE_TO_GETTER(
            "Attribut -> Getter",
            "attribute -> getter"),

    /** Subtest label: the getter returns what the setter set. */
    GETTERS_SETTERS_SETTER_TO_GETTER(
            "Setter -> Getter",
            "setter -> getter"),

    /** Result of a passed subtest.     *
     * @deprecated no longer used, behaviour failures are reported as {@link Failure} block
 */
    @Deprecated
    GETTERS_SETTERS_PASSED(
            "bestanden",
            "passed"),

    /** Result of a subtest whose getter or setter was not given.     *
     * @deprecated no longer used, behaviour failures are reported as {@link Failure} block
 */
    @Deprecated
    GETTERS_SETTERS_NOT_TESTED(
            "nicht getestet",
            "not tested"),

    /**
     * Names the attribute as the place a value is read from.
     * Placeholders: (1) attribute name.
     *
     * @deprecated no longer used, behaviour failures are reported as {@link Failure} block
     */
    @Deprecated
    GETTERS_SETTERS_ATTRIBUTE(
            "Attribut %s",
            "attribute %s"),

    /**
     * Shown when a valid value is not stored by the setter.
     * Placeholders: (1) setter name, (2) value, (3) where the value is read from, (4) actual, (5) expected.
     *
     * @deprecated no longer used, behaviour failures are reported as {@link Failure} block
     */
    @Deprecated
    GETTERS_SETTERS_VALUE_NOT_STORED(
            "nach %s(%s) liefert %s den Wert %s, erwartet: %s",
            "after %s(%s), %s yields %s, expected: %s"),

    /**
     * Shown when the setter stores a value that violates the setter condition.
     * Placeholders: (1) setter name, (2) value, (3) where the value is read from, (4) actual, (5) value before.
     *
     * @deprecated no longer used, behaviour failures are reported as {@link Failure} block
     */
    @Deprecated
    GETTERS_SETTERS_INVALID_VALUE_STORED(
            "%s(%s) muss den ungültigen Wert ablehnen (Wert unverändert lassen oder Ausnahme werfen), aber %s liefert jetzt %s statt %s",
            "%s(%s) must reject the invalid value (keep the value unchanged or throw an exception), but %s now yields %s instead of %s"),

    /**
     * Shown when the getter does not return the value of the attribute.
     * Placeholders: (1) attribute, (2) attribute value, (3) getter name, (4) returned value.
     *
     * @deprecated no longer used, behaviour failures are reported as {@link Failure} block
     */
    @Deprecated
    GETTERS_SETTERS_WRONG_VALUE_RETURNED(
            "%s hat den Wert %s, aber %s() liefert %s",
            "%s has the value %s, but %s() returns %s"),

    /**
     * Shown when a getter or setter throws an exception for a valid value.
     * Placeholders: (1) method name, (2) argument (empty for getters), (3) exception.
     *
     * @deprecated no longer used, behaviour failures are reported as {@link Failure} block
     */
    @Deprecated
    GETTERS_SETTERS_THREW(
            "%s(%s) hat eine Ausnahme geworfen: %s",
            "%s(%s) threw an exception: %s"),

    /**
     * Shown when a getter method does not return the expected attribute value.
     * Placeholders: (1) getter signature, (2) expected value, (3) actual value.
     *
     * @deprecated no longer used, {@code testGetter} reports with the {@code GETTERS_SETTERS_*} messages
     */
    @Deprecated
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
     *
     * @deprecated no longer used, behaviour failures are reported as {@link Failure} block
     */
    @Deprecated
    METHOD_INVOCATION_EXCEPTION(
            "Der Aufruf der Methode %s auf Klasse %s hat eine Ausnahme geworfen: %s",
            "Calling method %s on class %s threw an exception: %s"),

    /**
     * Appended on its own line to a failed constructor or method call with arguments.
     * Placeholder: (1) the argument values, e.g. {@code 2.5, "text"}.
     */
    INVOCATION_ARGUMENTS(
            "Argumente: (%s)",
            "Arguments: (%s)"),

    /**
     * Shown when an argument does not fit the (deviating numeric) parameter type of the student's method.
     * Placeholders: (1) argument position, (2) value, (3) parameter type, (4) signature.
     */
    ARGUMENT_DOES_NOT_FIT(
            "Argument %d (%s) passt nicht in den Parametertyp %s von %s.",
            "Argument %d (%s) does not fit the parameter type %s of %s."),

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
     *
     * @deprecated no longer used, behaviour failures are reported as {@link Failure} block
     */
    @Deprecated
    CONSTRUCTOR_INVOCATION_FAILED(
            "Konstruktor '%s' in Klasse %s konnte nicht aufgerufen werden: %s",
            "Failed to invoke constructor '%s' in class %s: %s"),

    // -------------------------------------------------------------------------
    // Utils
    // -------------------------------------------------------------------------

    /**
     * Shown when student code was expected to throw an exception but did not, or threw a different one.
     * Placeholders: (1) member signature, (2) class name, (3) expected exception type, (4) actual outcome.
     *
     * @deprecated no longer used, behaviour failures are reported as {@link Failure} block
     */
    @Deprecated
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
