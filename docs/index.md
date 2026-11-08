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
* **Structural verification** of modifiers, types, constructors, superclasses and interfaces; every missing or
  unexpected modifier is reported, harmless ones can be marked optional (`"final?"`)
* **Behavioural testing** through wrappers that call the student's (possibly misspelled) members
* **One failure format**: every failure in student code, and your own checks via `Failure`, shows Method,
  Scenario, Input, Expected and Actual, without stack traces
* **Getter/setter tests in one line**: `testGettersSetters` generates values, checks setter conditions and
  compares arrays by content
* **Independent tests**: objects without running a student constructor, test-owned method overrides and call
  checks (`mock()`)
* **Student classes as member types**: attributes, return and parameter types may be other student classes,
  found with the same tolerance
* **Abstract classes and interfaces** instantiated through Byte Buddy subclasses
* **Exercise Variants compatible**: names and types are plain strings and classes
* **Secure by default**: `@LevenshteinTest` runs every test under Ares 2 supervision and resets static state
  between tests

A failing behavioural test reads like this:

```text
Method:   setPrice(double)
Scenario: setter -> attribute, attribute price
Input:    setPrice(-1.25)
Expected: value rejected (unchanged 2.50 or exception)
Actual:   -1.25
(2 of 15 cases failed)
```

## Get started

You need JDK 25 and Gradle 9.1+ or Maven 3.9+ (details in the [Quick Start](quick-start.md)).

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
| [Writing Tests](writing-tests.md) | Wrapper cheat sheet, structural and behavioural tests, getters and setters, failure format, configuration, annotations |
| [Independent Tests and Mocks](independent-tests.md) | Objects without constructor, method overrides and call checks, so one mistake fails one test |
| [Ares 2 Setup in Detail](ares-setup.md) | Dependencies, build wiring, reserved-package guard, security policy, Ares 2 pitfalls |
| [How Matching Works](matching.md) | How the student's elements are found and graded as `EXACT`, `DEVIATES` or `MISSING`; types and modifiers |
| [Architecture](architecture.md) | How the parts fit together, class overview and diagrams |
| [API Reference (Javadoc)](https://valentinherrmann.github.io/Levenshtein-Testing-Framework/api/) | Every public class and method of the framework |
| [FAQ & Troubleshooting](faq.md) | Common problems and known limitations |
| [Versions and Migrating](migration.md) | What changed in each release and how to update existing tests |
| [Development and Releasing](releasing.md) | Building and testing the framework, mutation testing, publishing a release |

For AI assistants and other tools, the whole documentation is also available as plain text:
[`llms.txt`](https://valentinherrmann.github.io/Levenshtein-Testing-Framework/llms.txt) (index) and
[`llms-full.txt`](https://valentinherrmann.github.io/Levenshtein-Testing-Framework/llms-full.txt) (all pages).

The framework is published on Maven Central as `io.github.valentinherrmann:levenshtein-testing-framework`; the
source is on [GitHub](https://github.com/ValentinHerrmann/Levenshtein-Testing-Framework).
