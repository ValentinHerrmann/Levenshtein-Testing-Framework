# Plan: Issue #33 – accept a `ClassWrapper` as member type (lazy type resolution)

> Delivery of this plan: once approved, commit this file as `docs/plans/issue-33-lazy-member-types.md`
> on branch `claude/eloquent-carson-1zn056`, push, and open a **draft PR** ("Plan for #33") so the
> implementation can continue later or be handed to another agent.

## Context

`AttributeWrapper`, `MethodWrapper` and `ConstructorWrapper` take only `Class<?>` for attribute / return /
parameter types. When the type is a student class, the test author must resolve it while building the
wrapper. That breaks when the student class is missing/misnamed (no `Class<?>` to pass) and for mutual
references (Guitarist ↔ Drummer in SE01E04). SE01E04 works around it with a copied `TypeWrapper.resolve()`
+ Byte Buddy placeholder. Goal: additive overloads taking `ClassWrapper<?>` (or a mix with `Class<?>`),
resolved lazily on first verification; a missing type class only marks the *type* property `MISSING`.

## Key facts from the code (framework/src/main/java/io/github/valentinherrmann/levenshtein/)

- `Wrapper.ensureChecked()` (Wrapper.java:148) runs `findWithDeviation()` + `parseExistence()` once and
  sets `checked = true` *first* → natural hook for lazy resolution; recursion between wrappers is already
  guarded.
- `ClassWrapper.getClazz()` (ClassWrapper.java:160) = `ensureChecked(); return clazz;` → returns `null`
  when missing, deviation-tolerant lookup already built in. Expected simple name: `name.expected`.
- `Utils.verifyType(WrapperProperty<Class<?>>, Class<?>)` (Utils.java:226) already yields `MISSING` when
  `expected == null` → reuse as-is.
- `AttributeWrapper`: `WrapperProperty<Class<?>> type` set in ctor (:49); used in `findWithDeviation`
  (:226-247), `getValue` → `safeCast(val, type.expected, …)` (:105), `getExpectedType()` (:144),
  `expectedToString()` (:282 – `getSimpleName()`).
- `MethodWrapper`: `Class<?>[] paramTypes` + `params`/`returnType` properties set in ctor (:54-59);
  used by `getDeclaredMethod(name, paramTypes)` (:119), `expectedSignatureKey()` (:87, used by *other*
  wrappers' `findClosestCandidate` → may be called before this wrapper is checked!), `parameterDeviation`
  (:185), `expectedToString()` (:398), `getParamTypes()` (:365, used by `Mock.java:208,334` and
  `GettersSettersTest.java:54`).
- `ConstructorWrapper`: same pattern (`paramTypes` :26, ctor :46, `getDeclaredConstructor(paramTypes)`
  :81, `parameterDeviation` :112, `typeNames` :226, `getParamTypes()` :259).
- `GettersSettersTest` calls `attribute.getExpectedType().getSimpleName()` (:81) etc. → must tolerate an
  unresolved (null) type.

## Design

### 1. New package-private helper `TypeRef` (new file `TypeRef.java`)
```java
sealed interface TypeRef permits Fixed, Lazy {
    Class<?> resolve();          // Fixed → the class; Lazy → classWrapper.getClazz() (may be null)
    String expectedSimpleName(); // Fixed → getSimpleName(); Lazy → simple part of classWrapper.name.expected
    static TypeRef of(Object o)  // Class<?> → Fixed, ClassWrapper<?> → Lazy, else IllegalArgumentException
                                 // (message names index + actual type; null → IAE too)
    static TypeRef[] ofAll(Object[] types)
}
```
Lazy must **not** call `getClazz()` in its constructor / `expectedSimpleName()`.

### 2. Wrappers store `TypeRef`s, resolve in `findWithDeviation()`
- Keep `WrapperProperty<Class<?>>` fields; fill `expected` from `ref.resolve()` at the start of
  `findWithDeviation()` (before the parent-class lookup, so the type is resolved even when the member is
  missing). For `Fixed` the ctor can still set `expected` immediately (keeps existing behaviour/tests).
- Add a private `resolvedParamTypes()` in Method/ConstructorWrapper; replace direct `paramTypes` uses.
- **Unresolved parameter type** (`null`): skip the exact `getDeclared…` lookup (it NPEs/misses), and in
  `parameterDeviation` treat a `null` slot as a wildcard matching any **non-primitive** actual type with
  a penalty (e.g. +3), and force `params.existence = MISSING` afterwards. Name/modifiers/return type are
  still checked → "only its type reports missing".
- **Unresolved return/attribute type**: `verifyType` already gives `MISSING`.
- `expectedSignatureKey()`: build from `TypeRef.expectedSimpleName()`-safe form — use resolved binary
  name if resolved, else the expected name; must not throw and must not force a premature check that
  breaks recursion (calling `getClazz()` is fine because of the `checked` guard, but prefer resolving
  only if the wrapper is already checked… simplest: call `resolve()`; verify with the mutual test).
- `expectedToString()` / `typeNames`: use `TypeRef.expectedSimpleName()` (→ "Drummer" even when missing).
  Never resolve here (it's used in failure messages, possibly mid-check).
- Public getters: `getExpectedType()` / `getParamTypes()` return resolved classes (may contain `null`);
  document it. Guard `GettersSettersTest` (:54, :81, :138, :182, :207, :349) and `Mock` (:208, :334)
  against null: a null type → treat member as not testable / report the existing "not implemented"
  message instead of NPE.
- `AttributeWrapper.getValue`: if `type.expected == null` after check, `verifyExistence(true)` should
  already fail (type MISSING ⇒ overall MISSING?) — check `parseExistence` semantics; if overall is only
  DEVIATES, skip `safeCast` when expected is null.

### 3. New public constructors (additive; existing ones untouched)
```java
// AttributeWrapper
public <V> AttributeWrapper(ClassWrapper<T> parent, String name, ClassWrapper<V> type, String... mods)
// MethodWrapper
public MethodWrapper(ClassWrapper<T> parent, String name, ClassWrapper<R> ret, String... mods)
public MethodWrapper(ClassWrapper<T> parent, String name, ClassWrapper<R> ret, Object[] params, String... mods)
public MethodWrapper(ClassWrapper<T> parent, String name, Class<R> ret,        Object[] params, String... mods)
// ConstructorWrapper
public ConstructorWrapper(ClassWrapper<T> parent, Object[] params, String... mods)
```
(`AttributeWrapper` is `<T,V>` at class level — the overload takes `ClassWrapper<V>`.)
Overload check: `Class<?>[]` stays more specific than `Object[]`, so existing calls bind unchanged.
**Risk:** a literal `null` for the param array becomes ambiguous — grep tests/examples/docs for
`, null,` usages and fix/document (`(Class<?>[]) null`). All ctors delegate to one private ctor taking
`TypeRef`/`TypeRef[]`.

### 4. Tests (framework/src/test/java/io/github/valentinherrmann/levenshtein/)
Add fixtures in `fixtures/` (follow `ClassFixtures.java` / `Bus.java` style): `Guitarist` ↔ `Drummer`
with mutual attribute, getter, setter, ctor `(double, Guitarist)`. Add `LazyTypeWrapperTest`:
1. Mutual references: build Guitarist wrappers first, then Drummer (and reverse) → all `EXACT`.
2. Missing Drummer (`ClassWrapper` with non-existent name, e.g. `"Drumer2000xyz"`): `Class[Drummer]`
   missing; Guitarist `drummer` attribute → name EXACT, modifiers EXACT, type MISSING; message names
   "Drummer"; nothing throws during construction or `getOverallExistence()`.
3. Misnamed within threshold (fixture class `Drumer`, wrapper expects `Drummer`) → type resolves to the
   student class, member ≤ `DEVIATES`.
4. Method with unresolved param type still found by name; only params MISSING.
5. Overload binding regression: existing `Class<?>[]` calls compile/behave unchanged; `TypeRef.of`
   rejects non-Class/ClassWrapper entries with a clear IAE.
6. `GettersSettersTest` / `Mock` with an unresolved type: no NPE.
Also update `WrapperRegressionTest` if any message format changes (it shouldn't).

### 5. Docs
- `docs/writing-tests.md`: section "Student classes as member types" with the Guitarist/Drummer example.
- `docs/migration.md`: note replacing `TypeWrapper.resolve` / Byte Buddy helpers with the new overloads.
- README API overview if it lists constructors.

## Open points for the implementer
- Confirm `ClassWrapper.findWithDeviation()` does not eagerly check member wrappers (would interact with
  lazy resolution recursion). If it does, rely on the `checked` guard and add the both-orders test.
- Decide if `getExpectedType()` returning `null` is acceptable API or should throw a clear
  `AssertionError` (prefer: null + javadoc, internal callers guard).
- Acceptance criterion "SE01E04 builds without `TypeWrapper`" lives in the external exercise repo:
  verify there after releasing a snapshot (bump in `framework/pom.xml` per `docs/releasing.md`).

## Verification
```bash
cd /home/user/Levenshtein-Testing-Framework && ./mvnw -q -pl framework verify
```
(includes PIT gate at 90 % – new code paths need tests). Then build both examples
(`example-maven`: `./mvnw test`; `example-gradle`: `./gradlew test`) to confirm no overload ambiguity.

## Addendum: decisions made during implementation

- **Mutual references need a reference that exists before the full wrapper**, because member wrappers are
  built in the `ClassWrapper` subclass constructor. Both are supported:
  `ClassWrapper.reference(name, package)` is a member-less wrapper found by the normal lookup and `equals` to the
  full wrapper; a `Supplier<? extends ClassWrapper<?>>` is evaluated when the member is checked (a `null` result
  throws `IllegalStateException`, and messages show `<unknown>`).
- `TypeRef` has three variants: `Fixed`, `Wrapped` and `Supplied`. The new constructors take
  `ClassWrapper<? extends V>` / `Supplier<? extends ClassWrapper<? extends V>>`, so wildcard wrappers bind with
  the diamond operator. `MethodWrapper.getExpectedReturnType()` is new.
- Resolved open points: `ClassWrapper.findWithDeviation()` never checks members, so resolving types inside a
  member's check cannot recurse. A `MISSING` type makes the member `MISSING`, so `getValue` / `invoke` fail with
  the usual "not implemented" message before any cast. The `Mock` argument conversion needs no guard: a method
  with an unresolved parameter is `MISSING` and is rejected first. `GettersSettersTest` fails with the
  attribute's "not implemented" message.
- Compatibility: a literal `null` return type is now ambiguous (one test changed to `(Class<Object>) null`), and
  a `null` entry in a parameter type array throws `IllegalArgumentException`. Both are in `docs/migration.md`.
