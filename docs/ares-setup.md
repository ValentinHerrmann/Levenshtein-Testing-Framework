# Ares 2 Setup in Detail

[README](../README.md) · [Quick Start](quick-start.md) · [Writing Tests](writing-tests.md) · **Ares 2 Setup** · [Matching](matching.md) · [Architecture](architecture.md) · [Migration](migration.md) · [FAQ](faq.md)

The example's [`pom.xml`](../example/pom.xml) and [`test/`](../example/test) already contain everything below.
This section explains it, so you know what you may not remove.

**1. Dependencies.** Ares must be `provided`, *not* `test`: with test scope AspectJ silently weaves nothing.

```xml
<dependency>
    <groupId>de.tum.cit.ase</groupId>
    <artifactId>ares</artifactId>
    <version>2.2.1</version>
    <scope>provided</scope>
</dependency>
<dependency>
    <groupId>org.aspectj</groupId>
    <artifactId>aspectjrt</artifactId>
    <version>1.9.25.1</version>
</dependency>
<dependency>
    <groupId>io.github.valentinherrmann</groupId>
    <artifactId>levenshtein-testing-framework</artifactId>
    <version>2000.0.0</version>
    <scope>test</scope>
</dependency>
```

**2. Ares 2 build wiring.** Use `aspectj-maven-plugin` (weaves the student classes), `maven-dependency-plugin`
(copies the Ares agent) and Surefire with `-javaagent` plus the module flags. Copy the plugin blocks from
[`example/pom.xml`](../example/pom.xml); they follow the Ares guide
"[transform an Ares 1 protected project into an Ares 2 protected project](https://ls1intum.github.io/Ares2/instructor/transform-ares-1-into-ares-2/)"
(Postcompile, Maven).

**3. Reserved-package guard: mandatory.** Student classes in `target/classes` come *before* every
dependency JAR on the test classpath. A student who submits `io/github/valentinherrmann/levenshtein/StructuralLevenshtein.java`
would replace the framework, and with it every structural test. The antrun execution
`verify-ares-reserved-packages-v2` in `example/pom.xml` therefore lists the Ares prefixes **plus**
`io/github/valentinherrmann/levenshtein/**`, the instructor test package, `org/junit/**`, `org/assertj/**`
and `org/opentest4j/**`, and fails the build otherwise.

**4. Security policy and annotations.**

```java
@LevenshteinTest   // = @Public + @StrictTimeout(5) + @MirrorOutput
@Policy(value = "test/SecurityPolicy.yaml", withinPath = "classes/org/example/exam")
class ExamTest {
    // @Test, @TestFactory ... (plain JUnit annotations)
}
```

* `@LevenshteinTest` (or `@HiddenLevenshteinTest` plus `@Deadline`) **activates** Ares. A class with
  `@Policy` but without an Ares test-type annotation runs **completely unsupervised**, silently.
* `@Policy` is exam specific and therefore not part of `@LevenshteinTest`. The nearest `@Policy` wins;
  policies are never merged.
* `SecurityPolicy.yaml`: see [`example/test/SecurityPolicy.yaml`](../example/test/SecurityPolicy.yaml).
  - Use `JAVA_USING_MAVEN_ARCHUNIT_AND_ASPECTJ`.
  - `theFollowingClassesAreTestClasses` must list the **exact fully qualified names** of every instructor
    test, helper, constant and wrapper class. Never use a package name there, and never a class students
    can edit.
  - For an exam, normally keep all six permission lists empty.
* Use a student package that is not a prefix of your test or wrapper packages.

## Ares 2 behaviour worth knowing

* **Endless loops end the JVM.** On a timeout, Ares 2 interrupts the student code. Student code cannot
  react to that interrupt (all thread operations, including `Thread.sleep` and `Object.wait`, are blocked),
  so Ares halts the test JVM with exit code 124. Configure Surefire with `reuseForks=false` (as in the
  example), so only the tests of the affected class are lost, and keep tests that might loop in their own
  classes.
* **Static analysis looks at the whole submission.** A single forbidden call anywhere in the student code
  (e.g. `Thread.sleep`, file access) fails *every* supervised test, not only the one that runs the code.
* **No negative package rules.** Ares 1's `@BlacklistPackage("java.util.stream.*")` has no Ares 2
  counterpart (`java.*` is always permitted). If an exam must forbid streams, write a dedicated
  structural/ArchUnit test for it.
* **`*_INSTRUMENTATION` modes are not usable with Ares 2.2.1**: the agent fails to transform student
  classes that declare records.
* The example contains self-checks that fail if the sandbox is not active:
  [`SecurityControlTest`](../example/test/io/github/valentinherrmann/example/tests/SecurityControlTest.java)
  (permitted/forbidden file read) and
  [`TimeoutControlTest`](../example/test/io/github/valentinherrmann/example/tests/TimeoutControlTest.java)
  (deadline). They need the `SandboxControl` class in the student sources, so do not copy them into a
  real exam. Use them to validate your setup once.
