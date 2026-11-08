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
✅ **Structural verification** of modifiers, types, constructors, superclasses and interfaces; missing and unexpected modifiers are reported  
✅ **Behavioural testing** through wrappers that call the student's (possibly misspelled) members  
✅ **One failure format**: every failure in student code (also your own checks via `Failure`) shows Method, Scenario, Input, Expected and Actual, without stack traces  
✅ **Getter/setter tests in one line** with generated values and setter conditions  
✅ **Independent tests**: objects without running a student constructor, test-owned method overrides and call checks (`mock()`)  
✅ **Student classes as member types**, found with the same tolerance  
✅ **Abstract classes and interfaces** instantiated through Byte Buddy subclasses  
✅ **Exercise Variants compatible**: names and types are plain strings and classes  
✅ **Secure by default**: `@LevenshteinTest` runs every test under Ares 2 supervision

## Get started

**Requirements:** JDK 25 and Gradle 9.1+ (the template ships a wrapper) or Maven 3.9+.

```bash
git clone https://github.com/ValentinHerrmann/Levenshtein-Testing-Framework.git
cd Levenshtein-Testing-Framework/example-gradle
./gradlew test   # the example exercise under Ares 2, newest framework release from Maven Central
```

Then follow the **[Quick Start](https://valentinherrmann.github.io/Levenshtein-Testing-Framework/quick-start/)**: copy the [Gradle](example-gradle) (or
[Maven](example-maven)) template into your exam repository, write your first wrapper and test, and read the
results.

## Documentation

📖 **[https://valentinherrmann.github.io/Levenshtein-Testing-Framework/](https://valentinherrmann.github.io/Levenshtein-Testing-Framework/)**: searchable docs site with all guides and the API reference (Javadoc).
The sources are the Markdown files in [`docs/`](docs).

| Guide | What's inside |
|---|---|
| [Quick Start](https://valentinherrmann.github.io/Levenshtein-Testing-Framework/quick-start/) | From zero to a running test in four steps |
| [Writing Tests](https://valentinherrmann.github.io/Levenshtein-Testing-Framework/writing-tests/) | Wrapper cheat sheet, structural and behavioural tests, getters and setters, failure format, configuration |
| [Independent Tests and Mocks](https://valentinherrmann.github.io/Levenshtein-Testing-Framework/independent-tests/) | Objects without constructor, method overrides and call checks |
| [Ares 2 Setup in Detail](https://valentinherrmann.github.io/Levenshtein-Testing-Framework/ares-setup/) | Dependencies, build wiring, reserved-package guard, security policy, Ares 2 pitfalls |
| [How Matching Works](https://valentinherrmann.github.io/Levenshtein-Testing-Framework/matching/) | How the student's elements are found and graded as `EXACT`, `DEVIATES` or `MISSING` |
| [Architecture](https://valentinherrmann.github.io/Levenshtein-Testing-Framework/architecture/) | How the parts fit together, class overview and diagrams |
| [API Reference (Javadoc)](https://valentinherrmann.github.io/Levenshtein-Testing-Framework/api/) | Every public class and method of the framework |
| [FAQ & Troubleshooting](https://valentinherrmann.github.io/Levenshtein-Testing-Framework/faq/) | Common problems and known limitations |
| [Versions and Migrating](https://valentinherrmann.github.io/Levenshtein-Testing-Framework/migration/) | What changed in each release and how to update existing tests |
| [Development and Releasing](https://valentinherrmann.github.io/Levenshtein-Testing-Framework/releasing/) | Building and testing the framework, mutation testing, publishing a release |

## Development

```bash
# framework unit tests + Maven example under Ares 2, against the framework source of this checkout
./mvnw -B verify

# Gradle example under Ares 2, against the framework source of this checkout
(cd example-gradle && ./gradlew test)

# mutation testing of the framework with PIT (report: framework/target/pit-reports/index.html)
./mvnw -B -f framework/pom.xml -P mutation verify

# docs site with live reload on http://localhost:8000 (Zensical, configured in zensical.toml)
pip install zensical && zensical serve
```

How the repository is built, tested and released: [Development and Releasing](https://valentinherrmann.github.io/Levenshtein-Testing-Framework/releasing/).

## References

* Ares 2: https://github.com/ls1intum/Ares2 and its migration guide "transform Ares 1 into Ares 2"
* Byte Buddy: https://bytebuddy.net/
* Levenshtein distance: https://en.wikipedia.org/wiki/Levenshtein_distance

## License

GNU General Public License v3.0, see [LICENSE](LICENSE). Contributions and feedback are welcome!
