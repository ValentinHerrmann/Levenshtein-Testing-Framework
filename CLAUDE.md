# CLAUDE.md

Guidance for Claude Code (and other agents) working in this repository.

## What this is

A Java 25 test framework (`io.github.valentinherrmann:levenshtein-testing-framework`, on Maven Central) for
structural and behavioural tests of student code that tolerate small naming deviations, built on Ares 2 for
Artemis exams.

| Path | Content |
|---|---|
| `framework/` | The published library, package `io.github.valentinherrmann.levenshtein` (Maven only) |
| `example-maven/`, `example-gradle/` | The same example exercise as exam templates; `assignment/src` = student code, `assignment-failing/src` = defective solution, `test/` = tests, wrappers, `SecurityPolicy.yaml`. Their `assignment*/` and `test/` folders must stay identical except for one line in `SecurityPolicy.yaml` (CI checks this) |
| `docs/` | The docs site (Zensical, `zensical.toml`), published to GitHub Pages with the Javadoc at `/api/` |
| `docs/plans/` | Internal implementation plans; excluded from the site |

## Commands

```bash
./mvnw -B verify                                   # framework tests + Maven example under Ares 2
(cd example-gradle && ./gradlew test)              # Gradle example
./mvnw -B -f framework/pom.xml -P mutation verify  # PIT mutation testing (CI gate: mutationThreshold)
pip install zensical==0.1.0 && zensical build --clean --strict   # docs; must pass, as in .github/workflows/docs.yml
```

## Keep the docs up to date

Every change users can notice (public API, student-facing messages, settings, behaviour, build templates) updates
the docs **in the same change**:

* the guide that explains it (`docs/writing-tests.md`, `docs/matching.md`, `docs/independent-tests.md`,
  `docs/ares-setup.md`, `docs/faq.md`, ...), written as the current behaviour: no "since version X" or "before
  version Y" in the guides;
* `docs/migration.md`: what changed in the upcoming release and how to update existing tests;
* the class diagrams and class table in `docs/architecture.md` when public classes or methods change;
* `docs/index.md` and `README.md` (Key features, guide table) for notable features;
* `nav` and the `llmstxt` sections in `zensical.toml` for new pages;
* example output in the docs when message texts change (check against `Messages.java`).

Docs conventions: one `# Title` per page followed by a one-sentence purpose line; real headings (not bold
paragraphs) so every topic has an anchor; code blocks with a language; every diagram explained in the text.
Run `zensical build --clean --strict` before pushing doc changes.

## Code conventions

* Student-facing texts live in `Messages` (German and English); behaviour failures use `Failure`.
* Student code runs only inside supervised tests (Ares); wrappers look up their element lazily on first use.
* Never lower `mutationThreshold`; add tests instead.
* The framework version lives only in `framework/pom.xml` (`-SNAPSHOT` of the next release); see
  `docs/releasing.md`.
