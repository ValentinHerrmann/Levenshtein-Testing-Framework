# Plan: Report unexpected modifiers (issue #35)

## Deliverable of this session
Commit this plan as `docs/plans/issue-35-unexpected-modifiers.md` on branch `claude/nice-ramanujan-o5f25f`, push it, and open a **draft PR** ("Plan: report unexpected modifiers (#35)") linking to the issue. No code changes yet. Another agent (or a later session) implements the plan below.

---

## Context
`Wrapper.verifyModifiers(int)` (`framework/src/main/java/io/github/valentinherrmann/levenshtein/Wrapper.java:196`) only checks that each **expected** modifier is present. The only extra it reports is `static`, through the hook `reportUnexpectedStatic()`. That hook is overridden in `AttributeWrapper.java:65` (only for parents that are not interfaces) and in `MethodWrapper.java:80`. Every other extra modifier (`abstract`, `final`, `synchronized`, …) passes silently. Issue #35 asks for:
1. any unexpected modifier → `DEVIATES`, with a message "unexpected modifier: X"
2. optional modifiers written with a `?` suffix (`"final?"`)
3. implicit modifiers (from interfaces, enums, records, `strictfp`) never count as unexpected
4. visibility stays as it is (a wrong visibility is already reported as a missing expected modifier)
5. optionally a global switch in `LevenshteinSettings`

**First step for the implementer: reproduce SE01E03.** The `static` check already exists on `main`, so `private static boolean[] spotlights` should already be `DEVIATES` for a class parent. Add a fixture with that field and check it. If it is already red, the report probably came from an older release; note this in the PR. If it is green, find out why. Suspects: the parent `ClassWrapper` not being resolved yet when `reportUnexpectedStatic()` runs (`getClazz()` returns null → the method returns true, so that case should be fine), or the `knownClass` short-cut in `ClassWrapper.findWithDeviation()` (`ClassWrapper.java:209`).

## Decisions (defaults; maintainer may override in PR review)
- An unexpected `static` stays `DEVIATES`, not `MISSING`. This keeps the current behaviour and `docs/matching.md:332`.
- Every other unexpected modifier is also `DEVIATES`.
- Visibility bits (`public`/`protected`/`private`) are **never** reported as unexpected (rule 4).
- An unexpected `interface` bit on a ClassWrapper (an interface found where a class is expected) also counts as unexpected → `DEVIATES`.
- Add the switch `LevenshteinSettings.setUnexpectedModifiers(UnexpectedModifiers.IGNORE | DEVIATE)`, defaulting to `DEVIATE`. `reset()` restores the default. `IGNORE` is exactly the pre-#35 behaviour: only the existing unexpected-`static` check for attributes and methods stays active.

## Implementation

### 1. `Wrapper.java`
- `parseModifiers`: accept the `?` suffix. Strip it, validate against `KNOWN_MODIFIERS` and put the result into a new `Set<String> optionalModifierSet`. Reject a modifier that is both required and optional (`IllegalArgumentException`). Reject `public?`/`private?`/`protected?`, because visibility cannot be optional (keeps rule 4 simple).
- `modifiers.expected` (the display string) stays **required modifiers only**. Optional ones are not printed. `isModifierExpected()` is unchanged (required only). The `ClassWrapper.java:719` "interface" check keeps working.
- Add a helper `static int bit(String keyword)` that maps a keyword to its `Modifier.X` constant. Reuse it in the existing switch in `verifyModifiers` so the switch is replaced by `(bitmask & bit(m)) != 0`.
- New overridable hooks:
  - `protected int relevantModifierMask()`: the default is `Modifier.classModifiers() | Modifier.INTERFACE`. Overrides: method → `Modifier.methodModifiers()`, field → `Modifier.fieldModifiers()`, constructor → `Modifier.constructorModifiers()`. **Important:** for methods, raw bits 0x40/0x80 are BRIDGE/VARARGS and would print as `volatile`/`transient`, so masking is mandatory.
  - `protected int implicitModifiers()`: the default is `Modifier.STRICT`. It is evaluated after the element is found, so it can inspect the actual parent class and element.
- In `verifyModifiers`, after the expected loop:
  ```java
  int unexpected = bitmask & relevantModifierMask()
          & ~(PUBLIC|PROTECTED|PRIVATE)
          & ~bitsOf(expectedModifierSet) & ~bitsOf(optionalModifierSet)
          & ~implicitModifiers();
  ```
  If `unexpected != 0` and the setting is `DEVIATE`: set `worst = worst(worst, DEVIATES)` and store `unexpectedModifiers = Modifier.toString(unexpected)` in a new field.
- Remove `reportUnexpectedStatic()` and its two overrides, because the generic logic replaces them. Keep the `IGNORE` path equivalent to the old behaviour, so under `IGNORE` the static-only check still runs, using the implicit mask.
- `modifiers.actual = Modifier.toString(bitmask & relevantModifierMask())`, so the display no longer shows phantom `volatile`/`transient` on methods.
- `toString()` DEVIATES case: if `unexpectedModifiers != null`, append a new message line `Messages.UNEXPECTED_MODIFIERS.format(unexpectedModifiers)`.

### 2. Implicit modifiers per wrapper (override `implicitModifiers()`)
- **ClassWrapper** (`clazz` = actual class):
  - interface or annotation → `ABSTRACT | INTERFACE`, plus `STATIC` if it is a member class
  - enum → `FINAL` (javac sets it when no constant has a body; enums with constant bodies are `ABSTRACT`/not final → also mask `ABSTRACT` for enums), plus `STATIC` if it is a member class
  - record → `FINAL`, plus `STATIC` if it is a member class
  - any member of an interface → `STATIC` (and `PUBLIC`, which is ignored anyway)
  - always `STRICT`
- **MethodWrapper**: parent is an interface → `ABSTRACT` (for abstract interface methods; default/static ones do not have it). Also parent is an enum and the method is abstract because of constant bodies → leave it. Always `STRICT`.
- **AttributeWrapper**: parent is an interface → `STATIC | FINAL`. `field.isEnumConstant()` → `STATIC | FINAL`. Record components (private final fields) → `FINAL` when `parent.isRecord()`.
- **ConstructorWrapper**: enum constructors are implicitly `private`, which is visibility and so already ignored. Nothing else.
- `GenericClassWrapper` inherits from ClassWrapper. Check that it calls `verifyModifiers` the same way.

### 3. `Messages.java`
Add `UNEXPECTED_MODIFIERS` with German and English text, following the existing enum pattern (`Messages.java:491` selects the language). DE: `"Unerwartete Modifikatoren: %s"`, EN: `"unexpected modifier(s): %s"`. The acceptance test asserts the substring `unexpected modifier` in English (and `static`).

### 4. `LevenshteinSettings.java`
Add a `volatile` field, a getter and a setter, plus the nested enum `UnexpectedModifiers { IGNORE, DEVIATE }`, following the existing threshold/flag pattern. Add it to `reset()`.

### 5. Docs
- `docs/matching.md`: update the table at line 35, the example at line 217 and the rule at line 332. Add a short section on "unexpected and optional modifiers (`final?`)" and on implicit modifiers.
- `docs/writing-tests.md`: show `super("SetlistTools", BASE_PACKAGE, "public", "final?")`.
- `docs/migration.md` or the release notes for 2026.11.1: add a behaviour-change note and mention the `IGNORE` switch. There is no CHANGELOG file in the repo, so the note goes in the release notes / `migration.md`.

## Tests (framework/src/test/java/io/github/valentinherrmann/levenshtein/)
Add fixtures to `fixtures/Fixtures.java`, which already has SE01-style fixtures used by `WrapperRegressionTest`. Add tests mainly to `WrapperRegressionTest`, `AttributeWrapperTest`, `MethodWrapperTest` and `ClassWrapperTest`:
- `private static boolean[] spotlights` with expected `"private"` → `DEVIATES`, and `toString()` contains `unexpected modifier` and `static` (set the language to ENGLISH and reset it in `@AfterEach`).
- Optional modifiers: `public final class SetlistTools` and `public class SetlistTools`, both with `"public", "final?"` → `EXACT`.
- Unexpected `abstract` on a class, `final` on a field, `synchronized` on a method, `static` on an instance method → `DEVIATES`.
- No false positives: interface type, interface method (abstract and default), interface constant, top-level enum, nested enum, enum with constant bodies, enum constant field, record and nested record, varargs method (the VARARGS bit must not show up as `transient`).
- Parser: `"final?"` is accepted, `"fnial?"` and `"public?"` throw, and `"final", "final?"` together throws.
- Settings: `IGNORE` restores the old result (an extra `final` → `EXACT`).
- The existing tests `MethodWrapperTest.unexpectedStaticDeviates`, `AttributeWrapperTest.unexpectedStaticDeviatesForClassesOnly` and `WrapperRegressionTest.unexpectedStaticIsReported` (M2) must stay green.
- Check the existing example projects: `example-maven/assignment` must stay at 100 %, and `assignment-failing` must have the same failures as before (or document new ones).

## Verification
```bash
./mvnw -q -pl framework verify          # unit tests + PIT gate (90 %)
./mvnw -q -f example-maven/pom.xml test # example assignment stays green
```
Acceptance criterion "SE01E02–E04 solutions still score 100 %": those solutions are **not in this repo**. The maintainer runs them locally with the snapshot (`./mvnw install`), or adds minimal copies of the relevant classes to `fixtures/`.

## Critical files
- `framework/src/main/java/io/github/valentinherrmann/levenshtein/Wrapper.java`: parsing, `verifyModifiers`, `toString`
- `ClassWrapper.java`, `MethodWrapper.java`, `AttributeWrapper.java`, `ConstructorWrapper.java`: masks and implicit modifiers
- `Messages.java`, `LevenshteinSettings.java`
- `docs/matching.md`, `docs/writing-tests.md`, `docs/migration.md`
- tests: `WrapperRegressionTest.java`, `fixtures/Fixtures.java` and the per-wrapper tests
