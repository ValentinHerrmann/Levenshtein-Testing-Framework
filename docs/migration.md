# Versions and Migrating from 1000.x

[README](../README.md) · [Quick Start](quick-start.md) · [Writing Tests](writing-tests.md) · [Ares 2 Setup](ares-setup.md) · [Matching](matching.md) · [Architecture](architecture.md) · **Migration** · [FAQ](faq.md) · [Releasing](releasing.md)

`0260.9.0` was published accidentally (instead of `0.9.0`); `1000.0.0` acts as `1.0.0`. **`2000.0.0`** is
the first release of the Ares 2 / Java 25 line and contains the breaking changes below.

| 1000.x | 2000.0.0 |
|---|---|
| Ares 1 (`de.tum.in.ase:artemis-java-test-sandbox`) | Ares 2 (`de.tum.cit.ase:ares`, provided), Java 25 |
| `@LevenshteinTest` = Ares 1 security annotations; **the sandbox was never active** because no `@Public`/`@Hidden` was included | `@LevenshteinTest` = `@Public @StrictTimeout(5) @MirrorOutput`; plus `@Policy` + `SecurityPolicy.yaml` in the exam |
| `io.github.valentinherrmann.test.TestSettings` constants (inlined at compile time, not changeable) | `io.github.valentinherrmann.levenshtein.LevenshteinSettings` setters |
| `io.github.valentinherrmann.test.Messages.X` (String) | `io.github.valentinherrmann.levenshtein.Messages.X.get()` / `.format(...)` |
| `TestSettings.BASE_PACKAGE`, `variant` | in your own `Constants` |
| `getObj(boolean forceNew, boolean useByteBuddy)`, `useByteBuddy=false` for private members | `getObj(boolean forceNew)`; wrappers delegate to `getObj(forceNew, constructorWrapper, args...)`. Abstract classes and interfaces always use Byte Buddy, concrete classes never |
| `getObj(boolean useByteBuddy)` (returned the **cached** instance) | `getObj()` (cached) or `newObj()` / `getObj(true)` (new) |
| `getDynamicSubclassObj(Class<?>[] paramTypes, args...)` | `getDynamicSubclassObj(constructorWrapper, args...)` |
| `Utils.saveCast(...)` | `Utils.safeCast(...)` |
| `getMethodsWrappers()` | `getMethodWrappers()` |
| `setObj` writes `obj` directly | `setCachedObj(obj)` |
| Exceptions from student code: generic failure | type and message in the failure, `invokeExpectingException(...)` |

**Gradle or Maven?** Both work with 2000.0.0. The coordinates are unchanged
(`io.github.valentinherrmann:levenshtein-testing-framework`), but a 1000.x exam repository also needs the
Ares 2 build wiring (weaving, agent, reserved-package guard). Start from the
[Gradle](../example-gradle/build.gradle) or [Maven](../example-maven/pom.xml) template and see
[Ares 2 Setup in Detail](ares-setup.md).

## Changes in 2026.11.1

* **Unexpected modifiers are reported (#35).** A modifier of the student's element that is neither expected,
  optional nor implicit (e.g. `private static boolean[] spotlights` where `private` is expected, an extra
  `abstract`, `final` or `synchronized`) is now a deviation: the structural test fails with "unexpected
  modifier: ...". Existing structural tests can turn red on student code with extra modifiers. Mark harmless
  extras as optional with a `?` suffix, e.g. `"public", "final?"` for a utility class. See
  [Modifiers](matching.md#modifiers).
* Nested classes: a wrapper of a `static` nested class must now expect `static`.
* **A missing expected `static` is a deviation instead of `MISSING`:** behavioural tests go on and call the
  method or read the attribute on the default object.
* `@LevenshteinTest` and `@HiddenLevenshteinTest` reset attributes that are unexpectedly `static` before each
  test (`StaticStateReset`).
* An inner class where a `static` nested class is expected can still be instantiated (the outer instance is
  created automatically).
* `LevenshteinSettings.setUnexpectedModifiers(UnexpectedModifiers.IGNORE)` restores the previous behaviour.

## Changes in 2026.11.0

* `structuralTestFactory` no longer generates tests for empty groups (e.g. `Attributes[X]` for a class
  without attributes, `Constructors[X]` for an interface). Such tests always passed, also for an untouched
  template. If your Artemis exercise gives points for one of them, remove it there, or keep the old tests
  with `structuralTestFactory(level, false, wrappers...)`.
* A wrong number of arguments for `invoke(...)`, `ConstructorWrapper.invoke(...)` or `getObj(forceNew,
  constructorWrapper, args...)` is now an `IllegalArgumentException` (an error in the test) instead of a
  failed test. Pass a single array argument as `(Object) array`.
* Numeric conversions no longer change values silently. `Utils.safeCast`, `setValue` and arguments for a deviating
  numeric parameter type fail with a readable message if the value does not fit the student's type
  (e.g. `2.5` → `int`, `200` → `byte`). Before, `safeCast` and `setValue` truncated or wrapped such values.
  Arguments are now converted to a deviating numeric parameter type (`2.5` → `float`); before, the call failed.
* An `AssertionError` from inside a call (from Ares, a mock or a conversion) now gets the arguments and the
  text of `invokeWithContext` as well.
* New: `ClassWrapper.newObjWithoutConstructor()`, `newObjOverriding(...)` and `mock()` keep tests
  independent of other student members (#30). See [Independent tests](writing-tests.md#independent-tests).
