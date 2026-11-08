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
✅ **One failure format**: every behaviour failure (also your own checks via `Failure`) shows Method, Scenario, Input, Expected and Actual, without stack traces  
✅ **Independent tests**: objects without running a student constructor, test-owned method overrides and call checks (`mock()`)  
✅ **Exercise Variants compatible**: names and types are plain strings and classes  
✅ **Secure by default**: `@LevenshteinTest` runs every test under Ares 2 supervision

## Get started

**Requirements:** JDK 25, and Gradle (the template ships a 9.8.0 wrapper; 9.1+ works) or Maven 3.9+.

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
| [Writing Tests](https://valentinherrmann.github.io/Levenshtein-Testing-Framework/writing-tests/) | Wrapper cheat sheet, structural and behavioural tests, independent tests (mocks), configuration |
| [Ares 2 Setup in Detail](https://valentinherrmann.github.io/Levenshtein-Testing-Framework/ares-setup/) | Dependencies, build wiring, reserved-package guard, security policy, Ares 2 pitfalls |
| [How Matching Works](https://valentinherrmann.github.io/Levenshtein-Testing-Framework/matching/) | How the student's elements are found and graded as `EXACT`, `DEVIATES` or `MISSING` |
| [Architecture](https://valentinherrmann.github.io/Levenshtein-Testing-Framework/architecture/) | Module overview and class diagrams |
| [Versions and Migrating from 1000.x](https://valentinherrmann.github.io/Levenshtein-Testing-Framework/migration/) | Version history and breaking changes in 2000.0.0 |
| [FAQ & Troubleshooting](https://valentinherrmann.github.io/Levenshtein-Testing-Framework/faq/) | Common problems and known limitations |
| [API Reference (Javadoc)](https://valentinherrmann.github.io/Levenshtein-Testing-Framework/api/) | Every public class and method of the framework |
| [Releasing](https://valentinherrmann.github.io/Levenshtein-Testing-Framework/releasing/) | Step checklist for publishing a release to Maven Central |

## Development

```bash
# framework unit tests + Maven example under Ares 2, against the framework source of this checkout
./mvnw -B verify

# Gradle example under Ares 2, against the framework source of this checkout
(cd example-gradle && ./gradlew test)

# + sources, javadoc, GPG signature (needs a key)
./mvnw -B -P release -pl framework verify

# mutation testing of the framework with PIT (report: framework/target/pit-reports/index.html)
./mvnw -B -f framework/pom.xml -P mutation verify

# docs site with live reload on http://localhost:8000 (Zensical, configured in zensical.toml)
pip install zensical && zensical serve
```

Inside this repository both examples compile the framework source (`../framework/src/main/java`) together with
the tests, so the framework can be debugged directly. The examples are exam templates: a copy without
`../framework` uses the newest framework release from Maven Central, release candidates included
(`levenshteinVersion` in `build.gradle`, `levenshtein.version` in `pom.xml`, both overridable). CI installs the
SNAPSHOT and passes it to standalone copies of both examples.

The framework's own tests are checked with [PIT](https://pitest.org) mutation testing: PIT changes the framework
code in small ways (e.g. `<` to `<=`, a condition to `true`) and reruns the tests; a change no test notices
("surviving mutant") points to a missing test or a bug. Only framework classes are mutated, never the examples.
CI's `mutation` job fails below `mutationThreshold` in the `mutation` profile of `framework/pom.xml` and uploads
the HTML report as the `pit-report` artifact. Raise the threshold when new tests kill more mutants; never lower it
to make a change pass.

The framework is built and published with Maven only (publishing with Gradle is more trouble than it is worth).
The Gradle module is a consumer of it, like an exam repository.

Releases are published to Maven Central by the `Publish` workflow on a GitHub release
(`-P release -pl framework deploy`; the version comes from the tag). CI's `release-dry-run` job builds the
release artifacts for every change. A version on Maven Central can never be changed, so publish a release
candidate first (e.g. `2026.11.0-RC1` as a GitHub pre-release) and run the `Smoke test` workflow with that
version: it runs both templates against Maven Central. Afterwards, bump the `<version>` in `framework/pom.xml`
to the next SNAPSHOT; it is the only place that holds the framework version. The full step list is the
[release checklist](https://valentinherrmann.github.io/Levenshtein-Testing-Framework/releasing/).

## References

* Ares 2: https://github.com/ls1intum/Ares2 and its migration guide "transform Ares 1 into Ares 2"
* Byte Buddy: https://bytebuddy.net/
* Levenshtein distance: https://en.wikipedia.org/wiki/Levenshtein_distance

## License

GNU General Public License v3.0, see [LICENSE](LICENSE). Contributions and feedback are welcome!
