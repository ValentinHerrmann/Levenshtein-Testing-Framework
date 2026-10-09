# Versions and Migrating from 1000.x

[README](../README.md) · [Quick Start](quick-start.md) · [Writing Tests](writing-tests.md) · [Ares 2 Setup](ares-setup.md) · [Matching](matching.md) · [Architecture](architecture.md) · **Migration** · [FAQ](faq.md)

`0260.9.0` was published accidentally (instead of `0.9.0`); `1000.0.0` acts as `1.0.0`. **`2000.0.0`** is
the first release of the Ares 2 / Java 25 line and contains the breaking changes below.

| 1000.x | 2000.0.0 |
|---|---|
| Ares 1 (`de.tum.in.ase:artemis-java-test-sandbox`) | Ares 2 (`de.tum.cit.ase:ares`, provided), Java 25 |
| `@LevenshteinTest` = Ares 1 security annotations; **the sandbox was never active** because no `@Public`/`@Hidden` was included | `@LevenshteinTest` = `@Public @StrictTimeout(5) @MirrorOutput`; plus `@Policy` + `SecurityPolicy.yaml` in the exam |
| `io.github.valentinherrmann.test.TestSettings` constants (inlined at compile time, not changeable) | `io.github.valentinherrmann.levenshtein.LevenshteinSettings` setters |
| `io.github.valentinherrmann.test.Messages.X` (String) | `io.github.valentinherrmann.levenshtein.Messages.X.get()` / `.format(...)` |
| `TestSettings.BASE_PACKAGE`, `variant` | in your own `Constants` |
| `useByteBuddy=false` for private members | not needed; the parameter is ignored |
| `setObj` writes `obj` directly | `setCachedObj(obj)` |
| Exceptions from student code: generic failure | type and message in the failure, `invokeExpectingException(...)` |
