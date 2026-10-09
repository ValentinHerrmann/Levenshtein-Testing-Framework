# Levenshtein Name Deviation Testing Framework

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

## Key Features

✅ **Fuzzy name matching** for classes, methods and attributes; the closest candidate wins  
✅ **Structural verification** of modifiers, types, constructors, superclasses and interfaces  
✅ **Behavioural testing** through wrappers that call the student's (possibly misspelled) members  
✅ **Abstract classes and interfaces** instantiated through Byte Buddy subclasses  
✅ **Exercise Variants compatible**: names and types are plain strings and classes  
✅ **Secure by default**: `@LevenshteinTest` runs every test under Ares 2 supervision

## Get started

**Requirements:** JDK 25, and Gradle 9.1+ (wrapper included) or Maven 3.9+.

```bash
git clone https://github.com/ValentinHerrmann/Levenshtein-Testing-Framework.git
cd Levenshtein-Testing-Framework
./mvnw -B install -pl framework       # until 2000.0.0 is on Maven Central
cd example-gradle && ./gradlew test   # the example exercise under Ares 2
```

Then follow the **[Quick Start](docs/quick-start.md)**: copy the [Gradle](example-gradle) (or
[Maven](example-maven)) template into your exam repository, write your first wrapper and test, and read the
results.

## Documentation

| Guide | What's inside |
|---|---|
| [Quick Start](docs/quick-start.md) | From zero to a running test in four steps |
| [Writing Tests](docs/writing-tests.md) | Wrapper cheat sheet, structural and behavioural tests, configuration |
| [Ares 2 Setup in Detail](docs/ares-setup.md) | Dependencies, build wiring, reserved-package guard, security policy, Ares 2 pitfalls |
| [How Matching Works](docs/matching.md) | How the student's elements are found and graded as `EXACT`, `DEVIATES` or `MISSING` |
| [Architecture](docs/architecture.md) | Module overview and class diagrams |
| [Versions and Migrating from 1000.x](docs/migration.md) | Version history and breaking changes in 2000.0.0 |
| [FAQ & Troubleshooting](docs/faq.md) | Common problems and known limitations |

## Development

```bash
# framework unit tests + Maven example under Ares 2
./mvnw -B verify

# install the framework into ~/.m2 (needed by the Gradle example and your exam)
./mvnw -B install -pl framework

# Gradle example under Ares 2
(cd example-gradle && ./gradlew test)

# + sources, javadoc, GPG signature (needs a key)
./mvnw -B -P release -pl framework verify
```

The framework is built and published with Maven only (publishing with Gradle is more trouble than it is worth).
The Gradle module is a consumer of it, like an exam repository.

Releases are published to Maven Central by the `Publish` workflow on a GitHub release
(`-P release -pl framework deploy`).

## References

* Ares 2: https://github.com/ls1intum/Ares2 and its migration guide "transform Ares 1 into Ares 2"
* Byte Buddy: https://bytebuddy.net/
* Levenshtein distance: https://en.wikipedia.org/wiki/Levenshtein_distance

## License

GNU General Public License v3.0, see [LICENSE](LICENSE). Contributions and feedback are welcome!
