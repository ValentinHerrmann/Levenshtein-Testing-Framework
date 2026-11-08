# Plan: Issue #34 – One structured failure format for all framework helpers

> Handoff document. Written so another agent (or a later session) can implement it without re-doing the research.
> Issue: https://github.com/ValentinHerrmann/Levenshtein-Testing-Framework/issues/34

## Context

Failure messages look different depending on which code wrote them: `testGettersSetters` prints a ✅/❌ report
with one line per subtest, `invokeExpectingException` writes a sentence, exceptions from `invoke`/`newObj` write
"Calling method … threw an exception: …", and test authors use their own format. Exercise repos (SE01E02-E04) each
copy a `helpers/Check.format`, which still can't reformat the framework's own messages.

Goal: every **behaviour** failure is reported as the same localized block. Test authors get the same block through
a new public `Failure` builder. Structural failures ("… is not implemented as expected") keep their short form.

```
Method:   calculateLoudness(double, double)
Scenario: generated case 3, seed 2040003
Input:    12.50, 3.00
Expected: 31.25
Actual:   27.00
(2 of 10 cases failed)
```

## Findings from the code (current state)

All sources are in `framework/src/main/java/io/github/valentinherrmann/levenshtein/`.

- **Exception from student code in `invoke`**: `MethodWrapper.java:290-306` builds `METHOD_INVOCATION_EXCEPTION`
  plus `Invocations.withContext`/`withArguments`, then calls `fail(msg, t)`. **Passing `t` sets the cause**, and that
  cause is where the `Caused by`/stack trace comes from.
- **`invokeExpectingException`**: `MethodWrapper.java:325-343` and `ConstructorWrapper.java:183-213`
  (`EXPECTED_EXCEPTION_NOT_THROWN` + `NOTHING_THROWN`).
- **Constructor invocation**: `ConstructorWrapper.java:157-168` (`CONSTRUCTOR_INVOCATION_FAILED`, `fail(msg, t)`).
- **`ClassWrapper.newObj`/`getObj` paths**: `ClassWrapper.java:480-487, 523-535, 559, 622, 635`
  (`CLASS_INSTANTIATION_FAILED`, `CLASS_SUBCLASS_INSTANTIATION_FAILED`, all with `fail(msg, e)` → cause attached).
- **Getter via attribute**: `AttributeWrapper.java:117-126` (`ATTRIBUTE_ACCESS_FAILED`, cause attached).
- **Ares/mock assertion failures inside a call**: `Invocations.withCallDetails` (`Invocations.java:365-371`) wraps
  with `new AssertionError(msg, e)` → cause attached.
- **Getter/setter**: `GettersSettersTest.run()` (~line 216) joins three `subtest(...)` lines into
  `GETTERS_SETTERS_REPORT`. `checkSet`/`checkReturned` throw on the **first** failing sample of each subtest.
  `callGetter` attaches the cause.
- **StackOverflowError**: `Invocations.rethrowIfCritical` (`Invocations.java:58-68`) rethrows every
  `VirtualMachineError`. `StackOverflowError` is one of them, so today endless recursion in student code escapes as a
  raw SOE with a huge stack trace. It must be handled **before** `rethrowIfCritical` in every call path.
- **Reusable helpers**: `Invocations.describe(Throwable)` (gives `IllegalStateException: msg`, unwraps
  `ExceptionInInitializerError`), `Invocations.describeValue(Object)`/`describeArguments(Object[])` (arrays,
  collections, length caps, safe `toString`), `Invocations.snapshotArguments`, `withContext` (handles a supplier that
  throws or returns null). Reuse these and don't write new ones.
- **Settings**: `LevenshteinSettings` holds static volatile fields with getter/setter and `reset()`. Add the decimals
  setting there the same way.
- **Messages**: enum `Messages(german, english)` with `.get()`/`.format(...)`. Add new constants there and keep the
  old ones (`@Deprecated` when they are no longer used, as was done for `GETTER_WRONG_VALUE`).
- **Tests that assert on old texts** (must be updated): `MethodWrapperTest`, `ConstructorWrapperTest`,
  `ClassWrapperTest`, `GettersSettersTestTest`, `WrapperRegressionTest`, `InvocationsTest`
  (in `framework/src/test/java/io/github/valentinherrmann/levenshtein/`).
- No CHANGELOG file exists. `docs/migration.md` and `docs/writing-tests.md` are where the changes are documented.

## Design

### 1. New public class `Failure` (new file `Failure.java`)

A builder that produces the block. It is the single place that renders it: the framework uses it internally and test
authors use it in their own checks.

```java
public final class Failure {
    public static Failure of(MethodWrapper<?, ?> m);       // Method line = m.expectedToString()
    public static Failure of(ConstructorWrapper<?> c);     // Method line = constructor's expectedToString()
    public static Failure of(String member);               // free text, for author checks without a wrapper
    public Failure scenario(String text);
    public Failure input(String text);                      // as given by the author
    public Failure arguments(Object... args);               // framework use: Invocations.describeArguments(args)
    public Failure expected(Object value);                  // formatted with Failure.format
    public Failure actual(Object value);
    public Failure expectedText(String text);               // raw, unformatted (e.g. "IllegalArgumentException")
    public Failure actualText(String text);
    public Failure thrown(Throwable t);                      // Actual: Invocations.describe(t); SOE → adds hint
    public Failure cases(int failed, int total);             // adds "(n of m cases failed)" if total > 1
    public String message();
    public AssertionError error();                           // new AssertionError(message()), NEVER a cause
    public static String format(Object value);
    public static <R> R call(ThrowingSupplier<R> code);      // runs student code, any exception → block (no Method line)
    public static void call(ThrowingRunnable code);
    @FunctionalInterface public interface ThrowingSupplier<R> { R get() throws Throwable; }
    @FunctionalInterface public interface ThrowingRunnable  { void run() throws Throwable; }
}
```

Rendering rules:
- Lines that were not set are left out (e.g. no `Input:` for a call without arguments, no `Scenario:` without context).
- Labels come from `Messages` and are padded to the longest label **of the active language**, so values line up
  (German: `Methode:`, `Szenario:`, `Eingabe:`, `Erwartet:`, `Tatsächlich:`).
- The cases line and the StackOverflow hint go on their own lines below the block.
- `error()` never attaches a cause. That rule is the core of the "no stack trace / no Caused by" acceptance criterion.
- `Failure.call` must keep the critical-throwable semantics: let `AssertionError` and `SecurityException` through
  unchanged (Ares), handle `StackOverflowError` as a student failure, and rethrow other `VirtualMachineError`s.

`Failure.format(Object)`:
- `Double`/`Float` → `String.format(Locale.ROOT, "%." + LevenshteinSettings.getDecimals() + "f", v)`; also handle
  NaN/Infinity, which are printed as is.
- `String` → `"text"` (quoted), `Character` → `'c'`, `null` → `null`.
- Arrays/collections → brackets, elements formatted recursively by the same rules.
- Everything else → `Invocations.describeValue` (keeps the length caps and the safe `toString`).
- Implementation: change `Invocations.describeValue(Object, int)` so it delegates scalar formatting to one shared
  method. Then the `Input:` line (via `describeArguments`) and `Expected/Actual` use **identical** formatting. Watch
  out: this changes the existing `Arguments: (...)` output for doubles (`2.5` → `2.50`) and strings (now quoted), so
  update `InvocationsTest` to match.

### 2. Settings and Messages

- `LevenshteinSettings`: `getDecimals()`/`setDecimals(int)` (default 2, reject < 0), reset in `reset()`.
- New `Messages` constants (DE / EN):
  - `FAILURE_METHOD` "Methode" / "Method", `FAILURE_SCENARIO` "Szenario" / "Scenario", `FAILURE_INPUT` "Eingabe" /
    "Input", `FAILURE_EXPECTED` "Erwartet" / "Expected", `FAILURE_ACTUAL` "Tatsächlich" / "Actual"
  - `FAILURE_CASES` "(%d von %d Fällen fehlgeschlagen)" / "(%d of %d cases failed)"
  - `FAILURE_STACK_OVERFLOW_HINT` "Hinweis: StackOverflowError deutet meist auf eine endlose Rekursion hin." /
    "Hint: a StackOverflowError usually means endless recursion."
  - `FAILURE_NO_EXCEPTION`: reuse the existing `NOTHING_THROWN` as Actual text
  - `FAILURE_RETURNED` "Rückgabe %s" / "returned %s": Actual for `invokeExpectingException` when the call
    returned normally (optional, `NOTHING_THROWN` is enough)
  - getter/setter Expected texts: `GETTERS_SETTERS_EXPECT_REJECTED` "Wert abgelehnt (unverändert %s oder Ausnahme)" /
    "value rejected (unchanged %s or exception)"
- Mark as `@Deprecated` (keep them, no API removal): `METHOD_INVOCATION_EXCEPTION`, `CONSTRUCTOR_INVOCATION_FAILED`,
  `EXPECTED_EXCEPTION_NOT_THROWN`, `GETTERS_SETTERS_REPORT`, `GETTERS_SETTERS_PASSED`, `GETTERS_SETTERS_NOT_TESTED`,
  `GETTERS_SETTERS_VALUE_NOT_STORED`, `GETTERS_SETTERS_INVALID_VALUE_STORED`, `GETTERS_SETTERS_WRONG_VALUE_RETURNED`,
  `GETTERS_SETTERS_THREW`, `INVOCATION_ARGUMENTS` (only deprecate a constant once nothing uses it any more).

### 3. Switch the framework call sites to `Failure`

Same pattern at every site: `catch StackOverflowError` first → `Failure…thrown(e).error()`; then
`rethrowIfCritical(t)`; then `throw Failure.of(this).scenario(contextText).arguments(shown).thrown(t).error();`.

- `MethodWrapper.invoke*` (290-306): context supplier → `Scenario:` (reuse the logic of `Invocations.withContext`,
  extracted into a `static String contextText(Supplier<String>)` that returns null on failure).
- `MethodWrapper.invokeExpectingException` (325-343) and `ConstructorWrapper.invokeExpectingException` (183-213):
  `Expected: <ExceptionSimpleName>`, `Actual: no exception was thrown` or `Actual: <describe(t)>`.
- `ConstructorWrapper.newInstance` (157-168).
- `ClassWrapper` instantiation sites (480-487, 523-535, 559, 622, 635): for a student exception thrown by the
  constructor, use the block (Method = constructor/class name). Keep the short form for "cannot be instantiated
  (abstract/missing)" structural cases, but **remove the cause** there too.
- `AttributeWrapper` (117-126): getter exception → block; reflection access failure keeps its short text without
  the cause.
- `Invocations.withCallDetails` (365-371): keep the message (Ares/mock text + arguments) but build
  `new AssertionError(msg)` **without the cause**. Copy `e.getStackTrace()` only if Ares needs it; check whether
  any test relies on `getCause()`.
- Then grep the whole module for `fail(` with two arguments and `new AssertionError(..., t)` and remove every
  remaining cause on a student-facing failure.

### 4. `testGettersSetters` → block, first failing case + count

In `GettersSettersTest`:
- Replace the "throw on first failure per subtest" approach: `checkSet`/`checkReturned` return a `Failure` (or
  null). Each subtest iterates **all** samples, counts `failed`/`total` and remembers the first failure.
- `run()`: subtests run in the order setter→attribute, attribute→getter, setter→getter. The first failure across all
  subtests is reported, with `.cases(failedTotal, casesTotal)` counted across all applicable subtests × samples.
  No ✅/➖ lines any more.
- Block contents per case:
  - `Method:` the setter's (or getter's) `expectedToString()`
  - `Scenario:` the subtest label (`GETTERS_SETTERS_SETTER_TO_ATTRIBUTE` etc.) + `, ` + attribute name
  - `Input:` `setX(2.50)` for setter cases, the attribute value for attribute→getter
  - `Expected:` / `Actual:` the stored/returned value; for an invalid value use `GETTERS_SETTERS_EXPECT_REJECTED`
    as Expected text
  - a setter/getter exception → `Actual: IllegalStateException: msg`. Remove the cause in `callGetter`
    (line ~322). A setter exception for a valid value counts as a failed case. For an invalid value an exception
    is still a pass.
- Caveat: the setter→getter loop must keep working when `callGetter` throws mid-loop. Count it as a failed case and
  continue only if the object is still usable, otherwise stop the subtest.

### 5. Docs and changelog

- `docs/writing-tests.md`: a new section "Reporting failures" with the `Failure.of(...)` and `Failure.call(...)`
  examples from the issue and `LevenshteinSettings.setDecimals`.
- `docs/migration.md`: an "Unreleased" entry that lists the changed message texts (block format, getter/setter report
  no longer lists passed subtests, no cause on the `AssertionError`, `Arguments:` line replaced by `Input:`, doubles
  now with 2 decimals, strings quoted) for authors who assert on messages.
- `README.md` "Key Features": one bullet.

### 6. Out of scope for this PR (follow-up)

- SE01E02-E04 replacing `helpers/Check.format` with `Failure`. They live in other repositories that this session
  can't reach. This is done after a framework release that contains `Failure`. Open a follow-up issue or tick this
  box manually.

## Implementation order (each step compiles and has tests green)

1. `LevenshteinSettings.decimals` + `Messages` constants + `Failure` + `FailureTest`.
2. Shared value formatting in `Invocations.describeValue`; fix `InvocationsTest`.
3. `MethodWrapper` / `ConstructorWrapper` / `ClassWrapper` / `AttributeWrapper` / `withCallDetails` call sites,
   including SOE; fix `MethodWrapperTest`, `ConstructorWrapperTest`, `ClassWrapperTest`, `WrapperRegressionTest`.
4. `GettersSettersTest` rework; fix `GettersSettersTestTest`.
5. Docs/migration notes; deprecations.

## Tests to add

- `FailureTest`: line omission, label alignment DE+EN, `format` (double default 2 decimals, `setDecimals(4)`,
  NaN, string quoted, char, null, int[], List, nested), `cases` line only when total > 1, `error().getCause() == null`,
  `Failure.call` with a returning, throwing, SOE-throwing and AssertionError-throwing lambda.
- Regression (e.g. in `WrapperRegressionTest`): for `invoke`, `newObj`, both `invokeExpectingException` variants and
  `testGettersSetters`, using the fixtures in `framework/src/test/java/.../fixtures/` (add a recursive method and a
  throwing setter/getter fixture if missing). Assert, in **both languages**: the message has the 5 labels,
  `getCause() == null`, the message contains no `"\tat "`, `"Caused by"` or `"Exception in"`, and SOE adds the hint.
- Getter/setter: the first failing case is shown with the correct `(n of m cases failed)`. Passing subtests are no
  longer listed.

## Verification

```bash
./mvnw -q -pl framework verify
```
- Run the example against the failing assignment and read the output to check that the format is readable:
  `example-maven` (see `example-maven/README.md` for switching to `assignment-failing`) and the same for
  `example-gradle`.
- Grep check: `grep -rn "fail(.*, [a-z]*)\s*;" framework/src/main` should show no two-argument `fail` with a
  throwable left.
- CI: `.github/workflows/ci.yml` must be green on the PR.

## Decisions (confirmed by owner)

- Decimal separator: `Locale.ROOT` (`2.50`) in both languages.
- `Method:` line: types only, no parameter names (`calculateLoudness(double, double)`).

## Execution notes

- `origin/main` has moved since the plan was written (#35/#37). First, rebase `claude/great-galileo-gjsqbv` onto
  `origin/main`, then re-locate the call sites by grep (`fail(`, `new AssertionError(`, `Invocations.describe`).
  The line numbers above are approximate.
- Commit after each implementation step, push to `claude/great-galileo-gjsqbv` and update draft PR #38 (title/body).
