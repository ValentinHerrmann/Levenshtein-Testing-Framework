# Follow-up plan: fix code-review findings on #33 (PR #36, branch claude/eloquent-carson-1zn056)

## Context
Another agent implemented #33 (commits cbb6f59, ccce628, 462c9ca). A high-effort review produced 7 findings.
The main problem: a type lookup that throws inside `findWithDeviation()` leaves a wrapper stuck as UNCHECKED,
because `Wrapper.ensureChecked()` sets `checked = true` first. That makes `verifyExistence(true)` pass, and a later call hits an NPE.
First run `git pull origin claude/eloquent-carson-1zn056`, because the local branch is 3 commits behind.

## Fixes (in priority order)
1. **Stuck UNCHECKED state** (`Wrapper.java` `ensureChecked`, `TypeRef.java` `Supplied.resolve`):
   in `ensureChecked()`, wrap `findWithDeviation()`/`parseExistence()` in a try/catch that sets every
   property to MISSING, then rethrows (or reports it). Simpler fix: make `Supplied.resolve()` return `null` (= missing type)
   when the supplier returns null, and keep the explanation for `expectedSimpleName()`/the message.
2. **Signature key of another wrapper throws** (`MethodWrapper.expectedSignatureKey`): build the key without resolving
   unresolved refs. Use the resolved name for `Fixed` and `"?"`/the expected name for `Wrapped`/`Supplied`, and
   resolve only once the wrapper is checked. Never let it throw.
3. **Unchecked Supplier cast** (`TypeRef.of`/`Supplied.resolve`): check `instanceof ClassWrapper` on the supplied value.
   If it isn't one, give an IAE that names the position (or treat it as missing, consistent with fix 1).
4. **Ambiguous `null` type** (AttributeWrapper/MethodWrapper overloads): decide between keeping the documented break and
   dropping the Supplier overloads, which reduces the ambiguity. Recommended: drop the Supplier overloads (see 5). `null` stays
   ambiguous between Class and ClassWrapper, so keep the migration note.
5. **Three mechanisms** (altitude): remove `TypeRef.Supplied` and the Supplier constructors. `ClassWrapper.reference()` already
   covers any build order. Update docs/writing-tests.md, docs/migration.md and LazyTypeWrapperTest.
6. **Reference repeats the lookup** (efficiency): optional, can wait.
7. **Duplicated parameterDeviation** (reuse): move it to `Utils.parameterDeviation(Class<?>[] expected, Class<?>[] actual)`,
   including the null-slot wildcard, and use it in both wrappers.

## Tests to add (LazyTypeWrapperTest)
- A wrapper whose type resolution fails: the second `verifyExistence(true)` still fails, with no NPE.
- An unrelated sibling method is still found when another wrapper's type is unresolvable.
- `TypeRef.of` rejects a non-ClassWrapper supplier value with an IAE (only if Supplied is kept).

## Verification
```bash
./mvnw -q -pl framework verify
```
This includes the PIT gate at 90 %. Then build example-maven (`./mvnw test`) and example-gradle (`./gradlew test`).
Commit, push to claude/eloquent-carson-1zn056, and update PR #36.
