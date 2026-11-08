# FAQ & Troubleshooting

Answers to common questions and fixes for typical problems, grouped by topic. Each question is a heading, so you
can link to it directly.

## Setup and build

### Does it work with Gradle?

Yes, and Gradle is the primary path of the [Quick Start](quick-start.md). The template is
[`example-gradle/`](https://github.com/ValentinHerrmann/Levenshtein-Testing-Framework/tree/main/example-gradle)
and CI runs it next to the Maven build. The framework itself is built and published with Maven, but it is an
ordinary Maven Central artifact that Gradle consumes like any other. See [Ares 2 Setup in Detail](ares-setup.md)
for the Gradle specifics.

### Which framework version do I get?

The templates take the newest release from Maven Central, release candidates included (`levenshteinVersion` =
`latest.release` in `build.gradle`, `levenshtein.version` = `[2000.0.0,)` in `pom.xml`). Set a fixed version there
to keep an exam on one release. These docs describe the newest release; changes between versions are listed in
[Versions and Migrating](migration.md).

### Gradle: "Gradle was selected but no Gradle descriptor is present"

The policy says `JAVA_USING_GRADLE_...`, but the test JVM's working directory has no `build.gradle`. Use the
project directory (Gradle's default) as working directory.

### Gradle: the test JVM exits with code 124 right at the start

Nested classes are run as stand-alone tests (Gradle, unlike Surefire, does not skip them). Add
`exclude '**/*$*'` to the `test` task.

## Ares and the sandbox

### The policy seems to have no effect

Check that the class carries `@LevenshteinTest` (or another Ares test-type annotation), that `withinPath` is
`classes/<student/package/path>`, that Ares is `provided` (Maven) or on `compileOnly` and `aspect` (Gradle), and
that `SecurityControlTest` of the example passes in your setup.

### All tests fail with "... was blocked by Ares"

The static analysis found a forbidden call somewhere in the student code (see the message). This is intended. If
the exercise legitimately needs the operation, grant exactly that in the policy.

### The test JVM crashed with exit code 124

A test timed out in student code. Ares halts the JVM in that case, see
[Ares 2 behaviour worth knowing](ares-setup.md#ares-2-behaviour-worth-knowing).

## Matching and structural tests

### A similar name is not found

Work through the checklist in [Debugging a non-match](matching.md#debugging-a-non-match): the name percentage,
the parameters of a method (same number and order), claiming by other wrappers, and inherited vs. declared
methods.

### A structural test fails although the behavioural tests pass

Intended: the element `DEVIATES`. The student loses the structural point, not the behavioural ones.

### A structural test fails with "unexpected modifier: ..."

The student's element has a modifier your wrapper does not list, e.g. `static` on an attribute or `final` on a
class. Every modifier that is neither expected, optional nor implicit is a deviation. If the extra modifier is
harmless, mark it as optional with a `?` suffix (`"public", "final?"`). See [Modifiers](matching.md#modifiers).

### Extra members in the student code?

They are ignored. Only an additional *interface* and additional *modifiers* are reported as `DEVIATES`.

### There is no `Attributes[...]` test for one of my classes

A group without wrappers gets no structural test, because it would always pass. `structuralTestFactory(level,
false, wrappers...)` still generates such tests. See [Structural tests](writing-tests.md#structural-tests).

### Generic classes?

Type parameters are not part of the specification; use the erased types.

## Behavioural tests

### The feedback is in German

That is the default. Call `LevenshteinSettings.setLanguage(LevenshteinSettings.Language.ENGLISH)`, see
[Configuration](writing-tests.md#configuration).

### A setting has no effect

Set `LevenshteinSettings` in a static initializer or `@BeforeAll`, not in a test method or `@BeforeEach`:
`@LevenshteinTest` looks up the attribute wrappers before each test, so a later change does not reach them.

### An array argument arrives as several arguments

`invoke`, `getObj(forceNew, constructorWrapper, args...)` and the other `Object...` methods are varargs. Pass a
single array argument as `(Object) array`, see [Arrays as arguments](writing-tests.md#arrays-as-arguments).

### A getter/setter test fails for an array attribute although the content is right

Arrays are compared by content. If the test fails, the content differs; the message shows the elements. If you
want to require the same object instead, set `LevenshteinSettings.setGettersSettersRequireSameObject(true)`.

### How do my own checks get the same failure format?

Throw `Failure.of(methodWrapper)...error()` instead of an AssertJ assertion, see
[Reporting failures](writing-tests.md#reporting-failures).

### Results depend on the order of the tests

Usually a student attribute that is `static` although an instance attribute is expected. `@LevenshteinTest`
resets such attributes before each test, but not `static final` ones and not the *content* of a list or map; see
[Modifiers](matching.md#modifiers).

## Limitations

* Thresholds are global per element kind, not per wrapper.
* Records and enums are checked structurally like classes (their implicit superclass is ignored). An enum cannot
  be instantiated, so the `getObj` of its wrapper returns `null` or one of its constants. Record components,
  enum constants, `sealed`/`permits`, generic type parameters and annotations are not verified.
* **JavaFX / EOS exercises are untested.** The framework needs Ares 2 on the test classpath:
  `@LevenshteinTest` composes Ares 2 annotations, and the wrappers call student code through Ares 2's
  `ReflectionTestUtils`. EOS 1.1.0 bundles a fork of Ares 1, and whether it works together with Ares 2 has not
  been checked.
* Method parameter types must match (up to primitive ⇄ wrapper or numeric type); reordered parameters are
  `MISSING`.
* Mocks (`mock()`, `newObjOverriding`) cannot replace `final` classes or methods, records, enums, sealed
  classes, `static` or `private` methods, or constructors, and an attribute read directly by student code
  bypasses every override. See [Independent Tests and Mocks](independent-tests.md#limits).
