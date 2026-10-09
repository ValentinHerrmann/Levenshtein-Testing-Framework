# FAQ & Troubleshooting

[README](../README.md) · [Quick Start](quick-start.md) · [Writing Tests](writing-tests.md) · [Ares 2 Setup](ares-setup.md) · [Matching](matching.md) · [Architecture](architecture.md) · [Migration](migration.md) · **FAQ**

**The feedback is in German.** That is the default. Call
`LevenshteinSettings.setLanguage(LevenshteinSettings.Language.ENGLISH)`, see [Configuration](writing-tests.md#configuration).

**The policy seems to have no effect.** Check that the class carries `@LevenshteinTest`
(or another Ares test-type annotation), that `withinPath` is `classes/<student/package/path>`, that Ares is
`provided`, and that `SecurityControlTest` of the example passes in your setup.

**All tests fail with "... was blocked by Ares".** The static analysis found a forbidden call somewhere in
the student code (see the message). This is intended. If the exercise legitimately needs the operation,
grant exactly that in the policy.

**The test JVM crashed with exit code 124.** A test timed out in student code. Ares halts the JVM in that
case, see [Ares 2 behaviour worth knowing](ares-setup.md#ares-2-behaviour-worth-knowing).

**A similar name is not found.** Compute `distance * 100 / maxLength` and compare it with the threshold
(see [Existence states](matching.md#2-existence-states)). Method lookup also requires the same number of parameters
(types may differ only primitive ⇄ wrapper).

**A structural test fails although the behavioural tests pass.** Intended: the element `DEVIATES`. The
student loses the structural point, not the behavioural ones.

**Extra members in the student code?** They are ignored. Only an additional *interface* is reported as
`DEVIATES`.

**Generic classes?** Type parameters are not part of the specification; use the erased types.

## Limitations

* Thresholds are global per element kind, not per wrapper.
* No verification of generic type parameters, enums, record components or annotations.
* Method parameter types must match (up to primitive ⇄ wrapper); reordered parameters are `MISSING`.
