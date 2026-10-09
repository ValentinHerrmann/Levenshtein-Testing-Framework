# How Matching Works

[README](../README.md) · [Quick Start](quick-start.md) · [Writing Tests](writing-tests.md) · [Ares 2 Setup](ares-setup.md) · **Matching** · [Architecture](architecture.md) · [Migration](migration.md) · [FAQ](faq.md)

## 1. Lookup with deviation

Each wrapper looks up its element **once**, on first use:

1. **Exact match** by name (and parameter types).
2. Otherwise the **closest candidate** within the threshold, by Levenshtein distance with ties broken
   by name. Synthetic members and members that *another* wrapper of the same class expects exactly are
   skipped, so a missing `getMaxSpeed()` cannot take `getMinSpeed()`.
3. Classes are found by scanning the compiled classes of the expected package, so case slips such as
   `myCar` for `MyCar` are found too (within the threshold: `car` for `Car` is 33 % and therefore `MISSING`
   at the default of 20). Classes are loaded **without initialisation**, so student static initialisers do
   not run during the structural check.

## 2. Existence states

| State | Meaning | Examples |
|---|---|---|
| `EXACT` | Matches the specification | `price` / `price` |
| `DEVIATES` | Found with small differences; the structural test fails, behavioural tests use the actual element | `calculateCost` / `calculateCots`, `int` / `Integer`, `protected` instead of `public`, inherited indirectly, additional interface, unexpected `static` |
| `MISSING` | Not found or not usable as specified; the structural test and every behavioural test that needs it fail | name too different, `String` vs. `int`, missing `static` |
| `UNCHECKED` | Internal "not looked up yet"; never wins an aggregation and is never shown | |

Name thresholds are percentages of the longer name:
`distance * 100 / max(expected.length(), actual.length()) <= threshold`.

```
threshold 20:  "price" vs "pric"                   -> 20.0 %  DEVIATES
               "calculateCost" vs "calculateCots"  -> 15.4 %  DEVIATES
               "start" vs "strat"                  -> 40.0 %  MISSING
               "Car" vs "Cra"                      -> 66.7 %  MISSING
```

Short names tolerate fewer typos: at the default of 20, a name of 4 characters or less tolerates no edit
at all. Lower the threshold for an element kind with [`LevenshteinSettings`](writing-tests.md#configuration) if 20 % is too
generous for long names.

Types: exact → `EXACT`. Primitive ⇄ wrapper, a wider declared type (`Object` for `String`), or a wider
numeric type (`long` for `int`) → `DEVIATES`. Anything else → `MISSING`.

Modifiers: a missing `static` → `MISSING`. A different visibility, a missing `final`/`abstract`, or an
unexpected `static` on an attribute or method → `DEVIATES`. An unknown modifier in the specification (a
typo) throws `IllegalArgumentException` when the wrapper is created.
