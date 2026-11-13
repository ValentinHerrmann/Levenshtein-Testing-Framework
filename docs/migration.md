# Versions and Migrating

What changed between releases and how to update existing tests. If you start a new exam, you do not need this
page: the other guides describe the current release.

| Version | Highlights |
|---|---|
| **2026.11.1** | One failure format for behaviour failures (`Failure`), unexpected modifiers reported, student classes as member types, objects without constructor and mocks, docs site |
| 2026.11.0 | `testGettersSetters` (getter/setter tests with setter conditions and own sample values), failing example solution |
| 2000.0.0 | First release on Ares 2 and Java 25 |
| 1000.0.0 | Last release on Ares 1 (`0260.9.0` was published by accident instead of `0.9.0`; `1000.0.0` acts as `1.0.0`) |

## Upgrading to 2026.11.1

The changes since 2000.0.0, for test suites written against 2000.0.0 or 2026.11.0. Coming from 1000.x, first
apply [the 2000.0.0 changes](#migrating-from-1000x-to-2000).

### What can turn existing tests red

* **Unexpected modifiers deviate** (#35). A modifier of the student's element that is neither expected,
  optional nor implicit (e.g. `private static boolean[] spotlights` where `private` is expected, an extra
  `abstract`, `final` or `synchronized`) fails the structural test with "unexpected modifier: ...". Mark
  harmless extras as optional with a `?` suffix, e.g. `"public", "final?"` for a utility class. See
  [Modifiers](matching.md#modifiers).
  * A wrapper of a `static` nested class must now expect `static`.
  * To keep the old behaviour, set `LevenshteinSettings.setUnexpectedModifiers(UnexpectedModifiers.IGNORE)`.
* **A missing expected `static` is a deviation instead of `MISSING`**: behavioural tests go on and call the
  method or read the attribute on the default object.
* **Messages of behaviour failures changed** (#34). Exceptions of student code (`invoke`, `newObj`,
  `ConstructorWrapper.invoke`), `invokeExpectingException` and `testGettersSetters` report with one block of
  `Method:` / `Scenario:` / `Input:` / `Expected:` / `Actual:` lines (German: `Methode:`, `Szenario:`,
  `Eingabe:`, `Erwartet:`, `Tatsächlich:`). Tests that assert on message texts need updating:
  * "Calling method ... threw an exception: ...", "... should throw X, but: ..." and "Getter/setter test for
    attribute ...: ✅/❌ ..." are gone. An exception thrown by a student constructor is reported in the block,
    too.
  * `Arguments: (...)` became the `Input:` line; the text of `invokeWithContext` is the `Scenario:` line.
  * `testGettersSetters` shows only the first failing case plus "(n of m cases failed)" instead of one line
    per subtest. A missing getter or setter fails with the short structural message.
  * `double`/`float` values are shown with two decimals (`2.50`, `LevenshteinSettings.setDecimals`).
  * The `AssertionError` no longer has the student's exception as cause (no stack trace, no `Caused by`).
    Read the exception from `invokeExpectingException` if a test needs it.
  * A `StackOverflowError`, an `OutOfMemoryError` or any other `VirtualMachineError` of student code is a
    normal failure (with a hint on endless recursion or on a loop that never stops growing) instead of being
    rethrown (#44). Tests that expected the bare error, e.g. from `Failure.call`, now get an `AssertionError`
    with the failure block; `invokeExpectingException(OutOfMemoryError.class, ...)` still returns it.
  * A student's `toString()` that throws an error of the virtual machine while a message is built is shown as
    `(toString() threw OutOfMemoryError)` instead of failing the test with that error.
  * The old message constants are kept but `@Deprecated`.
* **No structural tests for empty groups.** `structuralTestFactory` no longer generates tests such as
  `Attributes[X]` for a class without attribute wrappers or `Constructors[X]` for an interface. They always
  passed, also for an untouched template. If your Artemis exercise gives points for one of them, remove it
  there, or keep the old tests with `structuralTestFactory(level, false, wrappers...)`.
* **Numbers are no longer changed silently.** `Utils.safeCast`, `setValue` and arguments for a deviating numeric
  parameter type fail with a readable message if the value does not fit the student's type (e.g. `2.5` → `int`,
  `200` → `byte`). Before, `safeCast` and `setValue` truncated or wrapped such values. Arguments are now
  converted to a deviating numeric parameter type (`2.5` → `float`); before, the call failed.
* **A wrong number of arguments** for `invoke(...)`, `ConstructorWrapper.invoke(...)` or `getObj(forceNew,
  constructorWrapper, args...)` is an `IllegalArgumentException` (an error in the test) instead of a failed
  test. Pass a single array argument as `(Object) array`.
* **Getters and setters compare arrays by content** (`Objects.deepEquals`). To require the same object, set
  `LevenshteinSettings.setGettersSettersRequireSameObject(true)`.

### API changes

* A literal `null` as return type (`new MethodWrapper<>(this, "m", null, ...)`) is ambiguous now; write
  `(Class<Object>) null`.
* A `null` entry in a parameter type array throws `IllegalArgumentException` when the wrapper is built.
* With `@LevenshteinTest`, attribute wrappers are looked up before each test. Configure `LevenshteinSettings`
  in a static initializer or `@BeforeAll`; changes inside a test method come too late for them.

### New features

* **`Failure`** for your own checks, so students see the same format there. See
  [Reporting failures](writing-tests.md#reporting-failures).
* **`Failure.isResourceError(e)`** and **`Failure.forEachCase(cases, body)`** (#46): tell a block of a
  `StackOverflowError` or `OutOfMemoryError` from a wrong result without parsing the message, and run many cases
  reporting one block plus "(n of m cases failed)", preferring such an error. Replace loops that look for
  `"OutOfMemoryError"` in the message by these. See [Checking many cases](writing-tests.md#checking-many-cases).
  `testGettersSetters` shows a `StackOverflowError` or `OutOfMemoryError` of a later case instead of the first
  failing case, too.
* **Optional modifiers** with a `?` suffix, and `getMissingModifiers()` / `getUnexpectedModifiers()` on every
  wrapper.
* **`StaticStateReset`**: `@LevenshteinTest` and `@HiddenLevenshteinTest` reset attributes that are unexpectedly
  `static` before each test, so results do not depend on the order of the tests.
* **Inner instead of nested class**: an inner class where a `static` nested class is expected can still be
  instantiated (the outer instance is created automatically).
* **Student classes as member types** (#33). `AttributeWrapper`, `MethodWrapper` and `ConstructorWrapper` accept
  a `ClassWrapper` (e.g. `ClassWrapper.reference("Drummer", pkg)`) or a supplier of one as type; parameter lists
  can be an `Object[]` that mixes them with `Class<?>`. Replace helpers like a copied `TypeWrapper.resolve(name)`
  with Byte Buddy placeholder classes by these. See
  [Student classes as member types](writing-tests.md#student-classes-as-member-types).
* **Independent tests** (#30): `ClassWrapper.newObjWithoutConstructor()`, `newObjOverriding(...)` and `mock()`.
  See [Independent Tests and Mocks](independent-tests.md).
* An `AssertionError` from inside a call (from Ares, a mock or a conversion) gets the arguments and the text of
  `invokeWithContext` as well.
* **Docs site** with search and the Javadoc: <https://valentinherrmann.github.io/Levenshtein-Testing-Framework/>.

## Migrating from 1000.x to 2000

**`2000.0.0`** is the first release of the Ares 2 / Java 25 line and contains these breaking changes:

| 1000.x | 2000.0.0 |
|---|---|
| Ares 1 (`de.tum.in.ase:artemis-java-test-sandbox`) | Ares 2 (`de.tum.cit.ase:ares`, provided), Java 25 |
| `@LevenshteinTest` = Ares 1 security annotations; **the sandbox was never active** because no `@Public`/`@Hidden` was included | `@LevenshteinTest` = `@Public @StrictTimeout(5) @MirrorOutput` (2026.11.1 adds `StaticStateReset`); plus `@Policy` + `SecurityPolicy.yaml` in the exam |
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

**Gradle or Maven?** Both work. The coordinates are unchanged
(`io.github.valentinherrmann:levenshtein-testing-framework`), but a 1000.x exam repository also needs the
Ares 2 build wiring (weaving, agent, reserved-package guard). Start from the
[Gradle](https://github.com/ValentinHerrmann/Levenshtein-Testing-Framework/blob/main/example-gradle/build.gradle)
or [Maven](https://github.com/ValentinHerrmann/Levenshtein-Testing-Framework/blob/main/example-maven/pom.xml)
template and see [Ares 2 Setup in Detail](ares-setup.md).
