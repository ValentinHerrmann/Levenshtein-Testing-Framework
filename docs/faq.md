# FAQ & Troubleshooting

[README](../README.md) · [Quick Start](quick-start.md) · [Writing Tests](writing-tests.md) · [Ares 2 Setup](ares-setup.md) · [Matching](matching.md) · [Architecture](architecture.md) · [Migration](migration.md) · **FAQ**

**Does it work with Gradle?** Yes, and Gradle is the primary path of the [Quick Start](quick-start.md). The
template is [`example-gradle/`](../example-gradle) and CI runs it next to the Maven build. The framework itself
is built and published with Maven, but it is an ordinary Maven Central artifact that Gradle consumes like any
other. See [Ares 2 Setup in Detail](ares-setup.md) for the Gradle specifics.

**The feedback is in German.** That is the default. Call
`LevenshteinSettings.setLanguage(LevenshteinSettings.Language.ENGLISH)`, see [Configuration](writing-tests.md#configuration).

**The policy seems to have no effect.** Check that the class carries `@LevenshteinTest`
(or another Ares test-type annotation), that `withinPath` is `classes/<student/package/path>`, that Ares is
`provided` (Maven) or on `compileOnly` and `aspect` (Gradle), and that `SecurityControlTest` of the example passes in your setup.

**All tests fail with "... was blocked by Ares".** The static analysis found a forbidden call somewhere in
the student code (see the message). This is intended. If the exercise legitimately needs the operation,
grant exactly that in the policy.

**Gradle: "Gradle was selected but no Gradle descriptor is present".** The policy says
`JAVA_USING_GRADLE_...`, but the test JVM's working directory has no `build.gradle`. Use the project
directory (Gradle's default) as working directory.

**Gradle: the test JVM exits with code 124 right at the start.** Nested classes are run as stand-alone tests
(Gradle, unlike Surefire, does not skip them). Add `exclude '**/*$*'` to the `test` task.

**The test JVM crashed with exit code 124.** A test timed out in student code. Ares halts the JVM in that
case, see [Ares 2 behaviour worth knowing](ares-setup.md#ares-2-behaviour-worth-knowing).

**A similar name is not found.** Work through the checklist in
[Debugging a non-match](matching.md#debugging-a-non-match): the name percentage, the parameters of a method
(same number and order), claiming by other wrappers, and inherited vs. declared methods.

**A structural test fails although the behavioural tests pass.** Intended: the element `DEVIATES`. The
student loses the structural point, not the behavioural ones.

**Extra members in the student code?** They are ignored. Only an additional *interface* is reported as
`DEVIATES`.

**Generic classes?** Type parameters are not part of the specification; use the erased types.

## Limitations

* Thresholds are global per element kind, not per wrapper.
* Records and enums can be wrapped like classes (their implicit superclass is ignored), but record components,
  enum constants, `sealed`/`permits`, generic type parameters and annotations are not verified.
* **JavaFX / EOS exercises are untested.** The framework needs Ares 2 on the test classpath:
  `@LevenshteinTest` composes Ares 2 annotations, and the wrappers call student code through Ares 2's
  `ReflectionTestUtils`. EOS 1.1.0 bundles a fork of Ares 1, and whether it works together with Ares 2 has not
  been checked.
* Method parameter types must match (up to primitive ⇄ wrapper or numeric type); reordered parameters are
  `MISSING`.
