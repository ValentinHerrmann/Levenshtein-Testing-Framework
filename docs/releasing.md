# Releasing

[README](../README.md) · [Quick Start](quick-start.md) · [Writing Tests](writing-tests.md) · [Ares 2 Setup](ares-setup.md) · [Matching](matching.md) · [Architecture](architecture.md) · [Migration](migration.md) · [FAQ](faq.md) · **Releasing**

A version on Maven Central can never be changed or removed, so every release goes through a release candidate first.
The `Publish` workflow builds and deploys the framework when a GitHub release is published; the version comes from
the tag (`v2026.11.0` or `v2026.11.0-RC1`, the leading `v` is stripped). `framework/pom.xml` is the only place that
holds the framework version.

## Checklist

1. [ ] `main` is green: CI passes, including the `release-dry-run` job (it builds sources, javadoc and the version
   change like `publish.yml`, without GPG and without the upload).
2. [ ] Docs match the code: [Migration](migration.md) lists the new version and its breaking changes; the
   [README](../README.md) and the examples show current usage.
3. [ ] Check that the secrets `GPG_PRIVATE_KEY`, `GPG_PASSPHRASE`, `OSSRH_USER` and `OSSRH_TOKEN` are set and
   still valid.
4. [ ] Create a **pre-release** on GitHub with the tag `v<version>-RC1` on `main`. This starts `Publish`.
5. [ ] Wait for `Publish` to succeed and for the artifact to appear on Maven Central (this can take some minutes).
6. [ ] Run the `Smoke test` workflow with the version `<version>-RC1`: it runs both example templates (Gradle and
   Maven) against Maven Central.
7. [ ] If a problem shows up: fix it on `main` and publish `-RC2` (go to step 4). Never re-use a tag.
8. [ ] Create the **release** on GitHub with the tag `v<version>` on the commit that passed the smoke test.
9. [ ] Wait for `Publish` and run `Smoke test` once more without an input (it takes the newest version on Maven
   Central).
10. [ ] Bump `<version>` in `framework/pom.xml` to the next `-SNAPSHOT` and commit it to `main`.
