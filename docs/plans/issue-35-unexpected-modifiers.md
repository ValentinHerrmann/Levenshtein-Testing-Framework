# Plan: Report unexpected modifiers (issue #35)

## Deliverable of this session
Update `docs/plans/issue-35-unexpected-modifiers.md` on branch `claude/nice-ramanujan-o5f25f` (draft PR #37) with this revised plan, then commit and push. No code changes yet.

---

## Context
`Wrapper.verifyModifiers(int)` (`framework/src/main/java/io/github/valentinherrmann/levenshtein/Wrapper.java:196`) only checks that each **expected** modifier is present. The only extra modifier it reports is `static`, through the hook `reportUnexpectedStatic()` (overridden in `AttributeWrapper.java:65` for parents that are not interfaces, and in `MethodWrapper.java:80`). Other extras (`abstract`, `final`, `synchronized`, …) pass silently. A missing expected `static` currently makes the element `MISSING`.

Semantics that drive every decision (see `docs/matching.md`):
- `DEVIATES`: the structural test fails. Behaviour tests **keep running on the detected element**.
- `MISSING`: the element counts as absent. Behaviour tests fail with "not implemented".

Guiding principle (maintainer): **every deviation from the exact intended signature makes the structural test fail. Behaviour tests keep working with the detected element wherever that is technically possible.** So modifier mismatches are `DEVIATES`, never `MISSING`. The framework adapts its runtime handling so the detected element stays usable.

First step for the implementer: **reproduce SE01E03.** `main` already reports an extra `static` on attributes, so `private static boolean[] spotlights` should already be red. Write a fixture and check. If it is green, find the cause first (suspect: the `knownClass` short-cut in `ClassWrapper.findWithDeviation()`, `ClassWrapper.java:209`).

## Decision matrix (agreed with the maintainer)

| Case | Structural | Behaviour tests |
|---|---|---|
| Any extra modifier (`static`, `abstract`, `final`, `synchronized`, `transient`, `volatile`, `native`, `interface`) | `DEVIATES`, message "unexpected modifier: X" | use the detected element |
| Extra `static` on an attribute | `DEVIATES` | run; **static state is reset before each test** (see §3) |
| Extra `static` on a method | `DEVIATES` | run; invoked with target `null` (`MethodWrapper.resolveTarget`, already works) |
| **Missing** expected `static` (attribute or method), *changed from `MISSING`* | `DEVIATES`, message "missing modifier: static" | run on `obj != null ? obj : getParentClassWrapper().getObj()` (the existing `resolveTarget` logic). Fails only if no default object can be created. |
| Extra `abstract` on a class that should be instantiable | `DEVIATES` | keep the current behaviour: `getObj()` builds a dynamic subclass (`ClassWrapper.java:461`) |
| Extra `final` on an attribute | `DEVIATES` | `setValue` fails with the existing `ATTRIBUTE_FINAL_CANNOT_SET` |
| Extra or missing `static` on a **nested class** | `DEVIATES` | constructors stay usable through a **bridge for the outer instance** (see §4) |
| Other missing expected modifiers (`final`, `abstract`, …) | `DEVIATES` (unchanged) | unchanged |
| Wrong visibility | unchanged: reported as a missing expected modifier → `DEVIATES` | unchanged |
| Optional modifier `"final?"` | present or absent → no deviation | – |
| Implicit modifiers (see §2) | never reported | – |

Limits that cannot be checked (document them, do not test them):
- javac ≥ 17 no longer emits `ACC_STRICT`, so `strictfp` cannot be detected and is ignored.
- Redundant explicit modifiers (e.g. writing `public abstract` on an interface method) produce the same bytecode as the implicit ones and cannot be told apart.

Global switch: `LevenshteinSettings.setUnexpectedModifiers(UnexpectedModifiers.IGNORE | DEVIATE)`, defaulting to `DEVIATE`, restored by `reset()`. `IGNORE` is exactly the pre-#35 behaviour: only the old extra-`static` check for attributes and methods stays active, and a missing `static` stays `MISSING`.

## Implementation

### 1. `Wrapper.java`: parsing and comparison
- `parseModifiers`: accept the `?` suffix, putting those modifiers into a new `optionalModifierSet`. Throw `IllegalArgumentException` for `public?`/`private?`/`protected?`, for an unknown keyword, or if the same modifier is both required and optional.
- `modifiers.expected` (the display string) shows only the required modifiers. `isModifierExpected()` stays limited to required modifiers.
- Add a helper `static int bit(String)` that maps a keyword to its `Modifier.X` constant, and use it to replace the existing `switch`.
- New hooks:
  - `protected int relevantModifierMask()`: class → `Modifier.classModifiers() | INTERFACE`, method → `methodModifiers()`, field → `fieldModifiers()`, constructor → `constructorModifiers()`. The mask is **mandatory**: for methods, raw bits 0x40/0x80 are BRIDGE/VARARGS and would show up as `volatile`/`transient`.
  - `protected int implicitModifiers()`: evaluated on the detected element.
- `verifyModifiers`:
  - `actual = bitmask & relevantModifierMask()`, and `modifiers.actual = Modifier.toString(actual)`.
  - missing = expected bits not present in `actual` → `DEVIATES`. The `static → MISSING` special case is removed when the setting is `DEVIATE`.
  - unexpected = `actual & ~VISIBILITY & ~expected & ~optional & ~implicitModifiers()` → `DEVIATES`.
  - Store the keyword lists `missingModifiers` and `unexpectedModifiers` in new fields.
- Remove `reportUnexpectedStatic()` and its two overrides. Implement the `IGNORE` path explicitly with the old logic.
- `toString()` DEVIATES case: append new message lines `Messages.UNEXPECTED_MODIFIERS` / `Messages.MISSING_MODIFIERS` (German and English, following the `Messages.java:491` pattern) when the lists are not empty.
- Accessor `boolean hasUnexpectedModifier(String)`, used by the static reset (§3).

### 2. Implicit modifiers (override `implicitModifiers()`)
- **ClassWrapper**:
  - interface or annotation → `ABSTRACT|INTERFACE`, plus `STATIC` for a member class
  - enum → `FINAL|ABSTRACT` (javac sets `ABSTRACT` instead of `FINAL` when constants have bodies), plus `STATIC` for a member class
  - record → `FINAL`, plus `STATIC` for a member class
  - a type declared inside an interface → `STATIC`
- **MethodWrapper**: parent is an interface → `ABSTRACT`.
- **AttributeWrapper**:
  - parent is an interface → `STATIC|FINAL`
  - `field.isEnumConstant()` → `STATIC|FINAL`
  - parent is a record → `FINAL`
- **ConstructorWrapper**: nothing. An enum constructor is implicitly `private`, which is visibility and therefore never reported.
- Check `GenericClassWrapper`, which inherits from ClassWrapper.

### 3. Reset extra static fields before each test
- **Hook:** a new JUnit 5 extension `StaticStateReset implements BeforeEachCallback`, registered through `@ExtendWith` on the meta-annotations `LevenshteinTest` and `HiddenLevenshteinTest`. Check that this works with Ares 2: Ares allows extensions, but verify the order relative to Ares's own extensions and the security policy (reflection on student classes happens inside the framework, as it does today).
- **Scope (decided): only fields that an AttributeWrapper flags as extra `static`.** Intended static fields are not touched.
- **Snapshot:** when `AttributeWrapper.findWithDeviation()` finds an extra `static`, it forces class initialisation (`Class.forName(name, true, loader)`), reads the value, and registers `(field, snapshot)` in a static registry inside the extension.
  - Values are snapshotted with a **deep copy of arrays** (recursively, including nested arrays). Other objects are kept by reference; document that a mutated collection stays mutated.
  - `final static` fields cannot be reset; skip them and note this in the Javadoc.
- **Reset:** `beforeEach` writes a fresh deep copy of the snapshot back into each registered field. A fresh copy each time is needed so the snapshot itself is never mutated.
- **Known gap (document it):** wrappers are checked lazily. If an earlier test already changed the static field before the AttributeWrapper was first checked, the snapshot holds the changed value. Mitigation: in `beforeEach`, the extension first calls `ensureChecked()` on all registered AttributeWrappers. Register every AttributeWrapper in its constructor, in a weak list.
- Note: sharing state between two instances *within* one test stays visible on purpose. That is the wrong behaviour the student should see.

### 4. Bridge for the outer instance of nested classes (ConstructorWrapper)
- If the parent class is an inner class (`isMemberClass() && !Modifier.isStatic`), its constructors have the outer instance as a hidden first parameter.
- In `ConstructorWrapper.findWithDeviation()`: if no match is found with the expected parameters and the class is an inner class, look up `[outer, ...expected]` again. When found, remember `needsOuter = true`. This alone is not a constructor deviation; the class wrapper reports the `static` mismatch.
- The reverse case (expected inner, actual static nested) must be handled too, if test authors specify the outer parameter explicitly. Check how existing wrappers express inner classes. Probably they never do; then only handle the case above.
- `invoke(args)`: when `needsOuter` is set, prepend the outer class's default object. Get the outer `ClassWrapper` from `clazz.getEnclosingClass()`, through a new `new ClassWrapper<>(enclosing)` (the `knownClass` constructor) or through a registered wrapper. Fall back to `MockClasses.allocate`. Also check `ClassWrapper.getDynamicSubclassObj` and `newObjWithoutConstructor` for inner classes.

### 5. `LevenshteinSettings.java`
Add the field, getter and setter, plus the enum `UnexpectedModifiers { IGNORE, DEVIATE }`, following the threshold pattern. Restore the default in `reset()`.

### 6. Docs
- `docs/matching.md`: update the table at line 35, the example at line 217 and the rule at line 332 ("missing static → MISSING" becomes DEVIATES). Add the decision matrix, `?` optional modifiers, implicit modifiers, the static reset and the limits (strictfp).
- `docs/writing-tests.md`: show `super("SetlistTools", BASE_PACKAGE, "public", "final?")`.
- `docs/migration.md` and the 2026.11.1 release notes: behaviour change (stricter structural tests, missing `static` now DEVIATES) and the `IGNORE` switch.

## Tests (`framework/src/test/java/io/github/valentinherrmann/levenshtein/`)
Put fixtures in `fixtures/Fixtures.java` and tests mainly in `WrapperRegressionTest` plus the per-wrapper tests.
- SE01E03: `private static boolean[] spotlights` with expected `"private"` → `DEVIATES`, and the message contains `unexpected modifier` and `static` (language ENGLISH, reset in `@AfterEach`).
- Static reset: test A sets `spotlights[0] = true`, test B sees `false` (snapshot + deep copy). A second instance in the same test still shares the state.
- Missing static: an instance method expected as `static` → `DEVIATES`, and `invoke(null, …)` runs on the default object. An instance attribute expected as `static` → `getValue(null)` reads the default object.
- `"public", "final?"`: passes both with and without `final`.
- Extra `abstract` (class) → `DEVIATES`, and `getObj()` still returns a subclass object. Extra `final` (attribute, method, class), `synchronized`, `transient`, `volatile` → `DEVIATES`.
- Nested class: expected `static class Inner`, actual inner class → class `DEVIATES`, and a constructor invoke works through the bridge.
- No false positives: interface type, abstract and default interface method, interface constant, enum (with and without constant bodies), nested enum, enum constant, record and nested record, varargs method.
- Parser: `"fnial?"`, `"public?"` and `"final","final?"` throw.
- `IGNORE`: the old results (extra `final` → `EXACT`, missing `static` → `MISSING`).
- Adapt the existing tests `MethodWrapperTest.unexpectedStaticDeviates`, `AttributeWrapperTest.unexpectedStaticDeviatesForClassesOnly` and `WrapperRegressionTest.unexpectedStaticIsReported` (M2), plus every test that expects `MISSING` for a missing `static` (grep for `static` + `MISSING`).

## Verification
```bash
./mvnw -q -pl framework verify          # unit tests + PIT gate (90 %)
./mvnw -q -f example-maven/pom.xml test # example assignment stays green, assignment-failing unchanged or documented
```
"SE01E02–E04 still score 100 %": those solutions are not in this repo. The maintainer checks locally against the snapshot (`./mvnw install`), or minimal copies go into `fixtures/`.

## Critical files
- `Wrapper.java`, `ClassWrapper.java`, `MethodWrapper.java`, `AttributeWrapper.java`, `ConstructorWrapper.java`
- new: `StaticStateReset.java` (extension), registered in `LevenshteinTest.java` and `HiddenLevenshteinTest.java`
- `Messages.java`, `LevenshteinSettings.java`
- `docs/matching.md`, `docs/writing-tests.md`, `docs/migration.md`
- tests: `WrapperRegressionTest.java`, `fixtures/Fixtures.java` and the per-wrapper tests
