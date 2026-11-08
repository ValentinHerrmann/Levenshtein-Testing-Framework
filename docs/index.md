# Levenshtein Testing Framework

Structural and behavioural tests for student code that **tolerate small naming deviations** (typos,
case slips, `int` vs. `Integer`, ...) while still verifying the structure, built on
[Ares 2](https://github.com/ls1intum/Ares2) for exams and exercises on Artemis. Exact name matching fails
correct solutions because of a typo; this framework stays forgiving about names and strict about the rest.

A student who writes `prize` instead of `price` loses the structural point for that attribute, but every
behavioural test still runs against their `prize`. They are not punished twice for one typo. What the
student sees:

```text
⚠️ DEVIATION in Car ⚠️
If possible actual will be used for further testing.
Expect:	private double price
Actual:	private double prize

❌ MISSING in Car ❌
Expect:	public void start()
```

## Key features

* **Fuzzy name matching** for classes, methods and attributes; the closest candidate wins
* **Structural verification** of modifiers, types, constructors, superclasses and interfaces
* **Behavioural testing** through wrappers that call the student's (possibly misspelled) members
* **Abstract classes and interfaces** instantiated through Byte Buddy subclasses
* **Independent tests**: objects without running a student constructor, test-owned method overrides and call checks (`mock()`)
* **Exercise Variants compatible**: names and types are plain strings and classes
* **Secure by default**: `@LevenshteinTest` runs every test under Ares 2 supervision

## Get started

**Requirements:** JDK 25, and Gradle (the template ships a 9.8.0 wrapper; 9.1+ works) or Maven 3.9+.

```bash
git clone https://github.com/ValentinHerrmann/Levenshtein-Testing-Framework.git
cd Levenshtein-Testing-Framework/example-gradle
./gradlew test   # the example exercise under Ares 2, newest framework release from Maven Central
```

Then follow the **[Quick Start](quick-start.md)**: copy the
[Gradle](https://github.com/ValentinHerrmann/Levenshtein-Testing-Framework/tree/main/example-gradle) (or
[Maven](https://github.com/ValentinHerrmann/Levenshtein-Testing-Framework/tree/main/example-maven)) template into
your exam repository, write your first wrapper and test, and read the results.

## Guides

| Guide | What's inside |
|---|---|
| [Quick Start](quick-start.md) | From zero to a running test in four steps |
| [Writing Tests](writing-tests.md) | Wrapper cheat sheet, structural and behavioural tests, independent tests (mocks), configuration |
| [Ares 2 Setup in Detail](ares-setup.md) | Dependencies, build wiring, reserved-package guard, security policy, Ares 2 pitfalls |
| [How Matching Works](matching.md) | How the student's elements are found and graded as `EXACT`, `DEVIATES` or `MISSING` |
| [Architecture](architecture.md) | Module overview and class diagrams |
| [API Reference (Javadoc)](https://valentinherrmann.github.io/Levenshtein-Testing-Framework/api/) | Every public class and method of the framework |
| [Versions and Migrating from 1000.x](migration.md) | Version history and breaking changes in 2000.0.0 |
| [FAQ & Troubleshooting](faq.md) | Common problems and known limitations |
| [Releasing](releasing.md) | Step checklist for publishing a release to Maven Central |

The framework is published on Maven Central as `io.github.valentinherrmann:levenshtein-testing-framework`; the
source is on [GitHub](https://github.com/ValentinHerrmann/Levenshtein-Testing-Framework).
