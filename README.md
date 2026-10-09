# Levenshtein Name Deviation Testing Framework

Structural and behavioural tests for student code that **tolerate small naming deviations** (typos,
case slips, `int` vs. `Integer`, ...) while still verifying the structure, built on
[Ares 2](https://github.com/ls1intum/Ares2) for exams and exercises on Artemis.

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

### Key Features

✅ **Fuzzy name matching** for classes, methods and attributes; the closest candidate wins  
✅ **Structural verification** of modifiers, types, constructors, superclasses and interfaces  
✅ **Behavioural testing** through wrappers that call the student's (possibly misspelled) members  
✅ **Abstract classes and interfaces** instantiated through Byte Buddy subclasses  
✅ **Exercise Variants compatible**: names and types are plain strings and classes  
✅ **Secure by default**: `@LevenshteinTest` runs every test under Ares 2 supervision

### Why

Exact name matching fails correct solutions because of a typo. That frustrates students, distorts exam
results and creates manual re-grading work. This framework stays forgiving about names while keeping the
structural and behavioural checks strict.

## Get started

**Requirements:** JDK 25 and Maven 3.9+.

```bash
git clone https://github.com/ValentinHerrmann/Levenshtein-Testing-Framework.git
cd Levenshtein-Testing-Framework
./mvnw -B verify        # builds the framework and runs the example exercise under Ares 2
```

Then follow the **[Quick Start](docs/quick-start.md)**: copy [`example/`](example) into your exam
repository, write your first wrapper and test, and read the results. That takes four steps.

## Documentation

| Guide | What's inside |
|---|---|
| [Quick Start](docs/quick-start.md) | From zero to a running test in four steps |
| [Writing Tests](docs/writing-tests.md) | Wrapper cheat sheet, inheritance, structural and behavioural tests, configuration, Exercise Variants |
| [Ares 2 Setup in Detail](docs/ares-setup.md) | Dependencies, build wiring, reserved-package guard, security policy, Ares 2 pitfalls |
| [How Matching Works](docs/matching.md) | Lookup, existence states (`EXACT`/`DEVIATES`/`MISSING`), thresholds, types, modifiers |
| [Architecture](docs/architecture.md) | Module overview and class diagrams |
| [Versions and Migrating from 1000.x](docs/migration.md) | Version history and breaking changes in 2000.0.0 |
| [FAQ & Troubleshooting](docs/faq.md) | Common problems and known limitations |

The repository contains two modules:

| Module | Content |
|---|---|
| [`framework/`](framework) | The published library `io.github.valentinherrmann:levenshtein-testing-framework` |
| [`example/`](example) | A complete reference exercise in the Artemis layout, protected by Ares 2. **The template for your exam repository.** |

## Development

```bash
./mvnw -B verify                                   # framework unit tests + example under Ares 2
./mvnw -B install -pl framework                    # install the framework into ~/.m2 for your exam repository
./mvnw -B -P release -pl framework verify          # + sources, javadoc, GPG signature (needs a key)
```

Releases are published to Maven Central by the `Publish` workflow on a GitHub release
(`-P release -pl framework deploy`).

## References

* Ares 2: https://github.com/ls1intum/Ares2 and its migration guide "transform Ares 1 into Ares 2"
* Byte Buddy: https://bytebuddy.net/
* Levenshtein distance: https://en.wikipedia.org/wiki/Levenshtein_distance

## License

GNU General Public License v3.0, see [LICENSE](LICENSE). Contributions and feedback are welcome!
