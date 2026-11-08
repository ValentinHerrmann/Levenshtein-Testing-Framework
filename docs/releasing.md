# Releasing

A version on Maven Central can never be changed or removed, so every release goes through a release candidate first.
The `Publish` workflow builds and deploys the framework when a GitHub release is published; the version comes from
the tag (`2026.11.0` or `2026.11.0-RC1`, as with the existing tags; a leading `v` would be stripped). `framework/pom.xml` is the only place that
holds the framework version.

## The version number

The only version in the code is `<version>` in `framework/pom.xml`. It always holds a `-SNAPSHOT` of the **next**
release (now `2026.11.0-SNAPSHOT` for the release `2026.11.0`).

* **Before a release: change nothing.** Do not remove `-SNAPSHOT` and do not commit the release version. `Publish`
  takes the version from the tag and sets it in the build only; CI's `release-dry-run` job tests the SNAPSHOT
  without the suffix. Choose the tag so that it equals the SNAPSHOT without `-SNAPSHOT` (or deliberately choose a
  different version, then the next step sets it).
* **After the final release: set `<version>` to the next `-SNAPSHOT`** (e.g. `2026.11.0` released, then
  `2026.12.0-SNAPSHOT`) and commit it to `main`. This is the last step of the checklist. Do not bump after an RC:
  the RC belongs to the same SNAPSHOT.
* **Never change:** the version ranges in the examples (`levenshtein.version` = `[2000.0.0,)` in
  `example-maven/pom.xml`, `levenshteinVersion` = `latest.release` in `example-gradle/build.gradle`), and the
  `0-SNAPSHOT` of the root `pom.xml` and `example-maven/pom.xml`. They always take the newest release from Maven
  Central.
* **Only on a new major line** (a breaking change like 2000.0.0): raise the lower bound `2000.0.0` of the example
  ranges in `example-maven/pom.xml` and `docs/ares-setup.md`, and write the breaking changes into
  [Migration](migration.md).

## Checklist

1. [ ] `main` is green: CI passes, including the `release-dry-run` job (it builds sources, javadoc and the version
   change like `publish.yml`, without GPG and without the upload).
2. [ ] Docs match the code: [Migration](migration.md) lists the new version and its breaking changes; the
   [README](https://github.com/ValentinHerrmann/Levenshtein-Testing-Framework/blob/main/README.md) and the examples show current usage.
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
`Docs` workflow on every push to `main` that touches them; nothing to do for a release. It always shows the state
of `main`, so merge doc changes for a new version together with the code. Preview it locally with
`pip install zensical && zensical serve`.
