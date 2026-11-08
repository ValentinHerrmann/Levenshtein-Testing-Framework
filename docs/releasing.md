# Development and Releasing

For maintainers of the framework: how the repository is built and tested, and how a release reaches Maven
Central. Exam authors do not need this page.

## Building and testing

```bash
# framework unit tests + Maven example under Ares 2, against the framework source of this checkout
./mvnw -B verify

# Gradle example under Ares 2, against the framework source of this checkout
(cd example-gradle && ./gradlew test)

# the failing example solution (fails on purpose; shows the students' messages)
./mvnw -B verify -Dassignment.dir=assignment-failing
(cd example-gradle && ./gradlew test -PassignmentDir=assignment-failing)

# + sources, javadoc, GPG signature (needs a key)
./mvnw -B -P release -pl framework verify

# mutation testing of the framework with PIT (report: framework/target/pit-reports/index.html)
./mvnw -B -f framework/pom.xml -P mutation verify
```

Inside this repository both examples compile the framework source (`../framework/src/main/java`) together with
the tests, so the framework can be debugged directly. The examples are exam templates: a copy without
`../framework` uses the newest framework release from Maven Central, release candidates included
(`levenshteinVersion` in `build.gradle`, `levenshtein.version` in `pom.xml`, both overridable). CI installs the
SNAPSHOT and passes it to standalone copies of both examples.

The framework is built and published with Maven only (publishing with Gradle is more trouble than it is worth).
The Gradle module is a consumer of it, like an exam repository.

### Mutation testing (PIT)

The framework's own tests are checked with [PIT](https://pitest.org) mutation testing: PIT changes the framework
code in small ways (e.g. `<` to `<=`, a condition to `true`) and reruns the tests; a change no test notices
("surviving mutant") points to a missing test or a bug. Only framework classes are mutated, never the examples.
CI's `mutation` job fails below `mutationThreshold` in the `mutation` profile of `framework/pom.xml` and uploads
the HTML report as the `pit-report` artifact. Raise the threshold when new tests kill more mutants; never lower it
to make a change pass.

## Releases

A version on Maven Central can never be changed or removed, so every release goes through a release candidate first.
The `Publish` workflow builds and deploys the framework when a GitHub release is published; the version comes from
the tag (`2026.11.1` or `2026.11.1-RC1`, as with the existing tags; a leading `v` would be stripped). `framework/pom.xml` is the only place that
holds the framework version.

### The version number

The only version in the code is `<version>` in `framework/pom.xml`. It always holds a `-SNAPSHOT` of the **next**
release (e.g. `2026.11.1-SNAPSHOT` for the release `2026.11.1`).

* **Before a release: change nothing.** Do not remove `-SNAPSHOT` and do not commit the release version. `Publish`
  takes the version from the tag and sets it in the build only; CI's `release-dry-run` job tests the SNAPSHOT
  without the suffix. Choose the tag so that it equals the SNAPSHOT without `-SNAPSHOT` (or deliberately choose a
  different version, then the next step sets it).
* **After the final release: set `<version>` to the next `-SNAPSHOT`** (e.g. `2026.11.1` released, then
  `2026.12.0-SNAPSHOT`) and commit it to `main`. This is the last step of the checklist. Do not bump after an RC:
  the RC belongs to the same SNAPSHOT.
* **Never change:** the version ranges in the examples (`levenshtein.version` = `[2000.0.0,)` in
  `example-maven/pom.xml`, `levenshteinVersion` = `latest.release` in `example-gradle/build.gradle`), and the
  `0-SNAPSHOT` of the root `pom.xml` and `example-maven/pom.xml`. They always take the newest release from Maven
  Central.
* **Only on a new major line** (a breaking change like 2000.0.0): raise the lower bound `2000.0.0` of the example
  ranges in `example-maven/pom.xml` and `docs/ares-setup.md`, and write the breaking changes into
  [Migration](migration.md).

### Checklist

1. [ ] `main` is green: CI passes, including the `release-dry-run` job (it builds sources, javadoc and the version
   change like `publish.yml`, without GPG and without the upload).
2. [ ] Docs match the code: [Versions and Migrating](migration.md) lists the new version and its changes; the
   guides, the [README](https://github.com/ValentinHerrmann/Levenshtein-Testing-Framework/blob/main/README.md)
   and the examples show current usage (see "Keeping the docs up to date" below).
3. [ ] Check that the secrets `GPG_PRIVATE_KEY`, `GPG_PASSPHRASE`, `OSSRH_USER` and `OSSRH_TOKEN` are set and
   still valid.
4. [ ] Create a **pre-release** on GitHub with the tag `<version>-RC1` on `main`. This starts `Publish`.
5. [ ] Wait for `Publish` to succeed and for the artifact to appear on Maven Central (this can take some minutes).
6. [ ] Run the `Smoke test` workflow with the version `<version>-RC1`: it runs both example templates (Gradle and
   Maven) against Maven Central.
7. [ ] If a problem shows up: fix it on `main` and publish `-RC2` (go to step 4). Never re-use a tag.
8. [ ] Create the **release** on GitHub with the tag `<version>` on the commit that passed the smoke test.
9. [ ] Wait for `Publish` and run `Smoke test` once more without an input (it takes the newest version on Maven
   Central).
10. [ ] Set `<version>` in `framework/pom.xml` to the next `-SNAPSHOT` and commit it to `main`. This is the only
    commit that changes the version.

## Documentation site

The docs site (`docs/` and the Javadoc of `framework/src/main`) is rebuilt and deployed to GitHub Pages by the
`Docs` workflow on every push to `main` that touches them; nothing to do for a release. It shows the state
of `main` (or of the branch of the last manual run: *Actions > Docs > Run workflow* deploys any branch, the next
push to `main` restores it), so merge doc changes for a new version together with the code. Preview it locally with
`pip install zensical && zensical serve`.

### Keeping the docs up to date

Every change that users can notice (public API, messages, settings, behaviour, build templates) updates the docs in
the same pull request:

* the guide that explains the feature (`docs/*.md`), written as the current behaviour, without "since version
  X" or "before version Y";
* [Versions and Migrating](migration.md): what changed and how to update existing tests;
* the class diagrams in [Architecture](architecture.md) if public classes or methods change;
* new pages in `nav` of `zensical.toml`.

`zensical build --clean --strict` must pass (the `Docs` workflow runs it for every pull request). `docs/plans/`
holds internal implementation plans and is not published.
