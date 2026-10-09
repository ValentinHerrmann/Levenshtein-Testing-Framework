# Ares 2 Setup in Detail

[README](../README.md) · [Quick Start](quick-start.md) · [Writing Tests](writing-tests.md) · **Ares 2 Setup** · [Matching](matching.md) · [Architecture](architecture.md) · [Migration](migration.md) · [FAQ](faq.md)

The templates [`example-gradle/build.gradle`](../example-gradle/build.gradle) (Gradle) and
[`example/pom.xml`](../example/pom.xml) (Maven), together with the `test/` folder, already contain everything
below. This page explains it, so you know what you may not remove. Both builds do the same four things.
Gradle needs Gradle 9.1+ to run on JDK 25 (Gradle 8.14 cannot even read the build script on JDK 25); the
template ships a wrapper for 9.2.

**1. Dependencies.** Ares must be visible to the student sources (the weaver needs it there) *and* to the
tests. In Maven that is the scope `provided`, *not* `test`: with test scope AspectJ silently weaves nothing.

<details open>
<summary><b>Gradle</b></summary>

```groovy
repositories {
    mavenLocal()      // only for a SNAPSHOT of the framework
    mavenCentral()
}

configurations {
    aresAgent { transitive = false }                // just the agent JAR, see 2.
    testImplementation.extendsFrom compileOnly      // Maven's "provided": Ares is also on the test classpath
}

dependencies {
    compileOnly "de.tum.cit.ase:ares:2.2.1"
    aspect "de.tum.cit.ase:ares:2.2.1"              // the aspect library the weaver applies to the student classes
    implementation "org.aspectj:aspectjrt:1.9.25.1"
    aresAgent "de.tum.cit.ase:ares:2.2.1:agent"

    testImplementation "io.github.valentinherrmann:levenshtein-testing-framework:2000.0.0"
    testImplementation platform("org.junit:junit-bom:6.1.3")
    testImplementation 'org.junit.jupiter:junit-jupiter'
    testImplementation 'org.junit.platform:junit-platform-testkit'   // only the example's TimeoutControlTest needs it
    testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
}
```
</details>

<details>
<summary><b>Maven</b></summary>

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
</details>

**2. Ares 2 build wiring.** Three things: weave the Ares aspects into the compiled student classes, put the
Ares agent where the test JVM can find it, and start the tests with `-javaagent` plus the module flags.

* **Gradle:** the plugin `io.freefair.aspectj.post-compile-weaving` (weaves; it uses the `aspect`
  dependency above), a `Copy` task for the agent and `aspectjrt`, and `jvmArgs` plus `forkEvery = 1` on the
  `test` task. Copy these blocks from [`example-gradle/build.gradle`](../example-gradle/build.gradle). Two
  details that are easy to miss:
  * `forkEvery = 1` is Gradle's counterpart of Surefire's `reuseForks=false` (see
    [below](#ares-2-behaviour-worth-knowing)).
  * `exclude '**/*$*'` keeps nested classes out of the test run. Surefire skips them by default, Gradle does
    not, and the example's `TimeoutControlTest$TimeoutProbe` deliberately halts its JVM.
* **Maven:** `aspectj-maven-plugin` (weaves), `maven-dependency-plugin` (copies the agent) and Surefire. Copy
  the plugin blocks from [`example/pom.xml`](../example/pom.xml); they follow the Ares guide
  "[transform an Ares 1 protected project into an Ares 2 protected project](https://ls1intum.github.io/Ares2/instructor/transform-ares-1-into-ares-2/)"
  (Postcompile, Maven).

**3. Reserved-package guard: mandatory.** Student classes in the build output come *before* every dependency
JAR on the test classpath. A student who submits `io/github/valentinherrmann/levenshtein/StructuralLevenshtein.java`
would replace the framework, and with it every structural test. The guard therefore lists the Ares prefixes
**plus** `io/github/valentinherrmann/levenshtein/**`, the instructor test package, `org/junit/**`,
`org/assertj/**` and `org/opentest4j/**`, and fails the build if the student output contains any of them.

* **Gradle:** the task `verifyAresReservedPackages` in `build.gradle`. It runs right after the student code
  is compiled and before the tests are compiled, and fails with the offending class files.
* **Maven:** the antrun execution `verify-ares-reserved-packages-v2` in `example/pom.xml`.

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
* `SecurityPolicy.yaml`: see [`example-gradle/test/SecurityPolicy.yaml`](../example-gradle/test/SecurityPolicy.yaml)
  (Gradle) or [`example/test/SecurityPolicy.yaml`](../example/test/SecurityPolicy.yaml) (Maven). The two files
  differ in one line only.
  - Use `JAVA_USING_GRADLE_ARCHUNIT_AND_ASPECTJ` with Gradle and `JAVA_USING_MAVEN_ARCHUNIT_AND_ASPECTJ`
    with Maven. Ares uses it to find the build output. With the Gradle setting Ares reads the `build.gradle`
    of the **working directory** of the test JVM (the project directory, which is Gradle's default); if it
    is not there, every test fails with "Gradle was selected but no Gradle descriptor is present".
  - `withinPath` is `classes/<student/package/path>` for **both** build tools. A path like
    `build/classes/java/main/...` is rejected ("should start with classes or test-classes").
  - `theFollowingClassesAreTestClasses` must list the **exact fully qualified names** of every instructor
    test, helper, constant and wrapper class. Never use a package name there, and never a class students
    can edit.
  - For an exam, normally keep all six permission lists empty.
* Use a student package that is not a prefix of your test or wrapper packages.

## Ares 2 behaviour worth knowing

* **Endless loops end the JVM.** On a timeout, Ares 2 interrupts the student code. Student code cannot
  react to that interrupt (all thread operations, including `Thread.sleep` and `Object.wait`, are blocked),
  so Ares halts the test JVM with exit code 124. Start a fresh JVM per test class (Surefire
  `reuseForks=false`, Gradle `forkEvery = 1`, as in the templates), so only the tests of the affected class
  are lost, and keep tests that might loop in their own classes.
* **Static analysis looks at the whole submission.** A single forbidden call anywhere in the student code
  (e.g. `Thread.sleep`, file access) fails *every* supervised test, not only the one that runs the code.
  (If the policy permits some file access, as the example's does for `allowed.txt`, the static analysis
  leaves file access to the runtime layer; with empty permission lists, as in an exam, it rejects up front.)
* **No negative package rules.** Ares 1's `@BlacklistPackage("java.util.stream.*")` has no Ares 2
  counterpart (`java.*` is always permitted). If an exam must forbid streams, write a dedicated
  structural/ArchUnit test for it.
* **`*_INSTRUMENTATION` modes are not usable with Ares 2.2.1**: the agent fails to transform student
  classes that declare records.
* The examples contain self-checks that fail if the sandbox is not active (for example when the weaving
  is missing):
  [`SecurityControlTest`](../example/test/io/github/valentinherrmann/example/tests/SecurityControlTest.java)
  (permitted/forbidden file read) and
  [`TimeoutControlTest`](../example/test/io/github/valentinherrmann/example/tests/TimeoutControlTest.java)
  (deadline). They need the `SandboxControl` class in the student sources, so do not copy them into a
  real exam. Use them to validate your setup once.
