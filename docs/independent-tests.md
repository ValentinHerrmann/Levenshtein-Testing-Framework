# Independent Tests and Mocks

A mistake in one student member should fail only the tests of that member. This page shows how to create objects
without the student's constructor, replace single student methods with test code, and check that a method was
called, so failures do not cascade from one member to the next.

## Why tests cascade

Two situations make failures cascade. In the first, the method under test calls another student method: if that method is wrong, the
test fails too. In the second, the test needs an object of a student class only as a value, but
`newObj()` runs the student's constructor. Three tools remove these dependencies. All three run **no
constructor** of the student class, so a missing or broken constructor cannot fail the test.

| Call | Result |
|---|---|
| `newObjWithoutConstructor()` | an object of the class whose attributes keep their default values (`0`, `false`, `null`; field initialisers do not run either). Concrete classes, also `final` ones, keep their exact class. Abstract classes and interfaces get a generated subclass. |
| `newObjOverriding(Map.of(method, args -> result, ...))` | an object of a generated subclass in which each given method returns the result of its function |
| `mock()` | a `Mock`: overrides like `newObjOverriding`, plus a record of every call |

## A method under test calls another student method

Example (SE01E03): `isShowReady()` calls `getNumberOfSpotlights()`. Replace the called method, so that only `isShowReady` is tested:

```java
Object stage = stageWrapper.newObjOverriding(Map.of(stageWrapper.getNumberOfSpotlights(), args -> 3));
assertThat(stageWrapper.isShowReady().invokeOnSpecificObject(stage)).isTrue();
```

The student's `isShowReady()` runs unchanged. Its call `this.getNumberOfSpotlights()` (or
`getNumberOfSpotlights()`) goes to the test's function.

## The method under test must call a method of another object

Example (SE01E04): `Drummer.calculateLoudness(volume, crowdNoise)` must use
`guitarist.calculateLoudness(volume)`. Give the Guitarist a return value the student
cannot produce by inlining `volume * 10`, and check the call:

```java
Mock<?> guitarist = guitaristWrapper.mock()
        .override(guitaristWrapper.calculateLoudness(), args -> (int) args[0] * 7);
Object drummer = drummerWrapper.newObjWithoutConstructor();
drummerWrapper.guitarist().setValue(drummer, guitarist.obj());

assertThat(drummerWrapper.calculateLoudness().invokeOnSpecificObject(drummer, 5, 3)).isEqualTo(38);
guitarist.assertCalled(guitaristWrapper.calculateLoudness(), 1,
        () -> "Drummer.calculateLoudness(5, 3) must use calculateLoudness of its guitarist.");
```

## An object is needed only as a value

The Drummer's accessor tests need *some* Guitarist, but not a correct constructor of it:

```java
Object guitarist = guitaristWrapper.newObjWithoutConstructor();  // no Guitarist constructor runs
Object drummer = drummerWrapper.newObj();
drummerWrapper.setGuitarist().invokeOnSpecificObject(drummer, guitarist);
```

## `Mock` in detail

| Call | Effect |
|---|---|
| `override(method, args -> result)` | `method` runs the function. |
| `returning(method, value)` | `method` always returns `value`. |
| `othersReturnDefaults()` | Methods without an override return `0`, `false` or `null` instead of running the student's code (a full mock). `equals`, `hashCode` and `toString` without an override behave like those of `Object` (identity), so the object still works in lists, sets and messages. |
| `obj()` | Returns the object. It is created on the first call and stays the same; overrides added later apply to it as well. |
| `callCount(method)` | Returns the number of calls. |
| `calls(method)` | Returns the arguments of the recorded calls, in call order: the first 1000 calls per method (`Mock.MAX_RECORDED_CALLS`). Arrays are copied when the call is recorded; other objects are the ones passed. |
| `assertCalled(method, times[, context])`, `assertNotCalled(method)` | Fails with the expected and actual number of calls and the recorded arguments, e.g. `Calls: (5), (7)`. |
| `resetCalls()` | Forgets all recorded calls, e.g. after preparing the object. |

* Methods are matched by the method the wrapper **actually found**, so a misspelt but tolerated name
  (`calculateLoudnes`) is still overridden. A `MethodWrapper` of a superclass or interface wrapper overrides
  the inherited method. Of an overloaded method, only the matching overload is overridden. Package-private
  methods can be overridden, too: the subclass is defined in the student's own package and class loader.
* The function gets the arguments in the parameter types the wrapper **expects**, e.g. an `Integer` for
  `int`, even if the student declared `long`. Its result is converted to the student's return type (numeric
  types are converted, e.g. `args -> 7` for a `double` method). If the function throws, or returns `null`
  for a primitive return type, the call fails like a call that throws.
* Methods without an override run the student's code, supervised by Ares. An abstract method
  without an override fails with "The abstract method ... was called, but the test does not override it."
* The functions are test code, so Ares does not restrict them. They are called from student code, though,
  so keep them to plain computations.
* A missing class or method fails with the usual `MISSING` message. A class or method that cannot be
  overridden fails with a readable message, e.g. `Guitarist: cannot be overridden (final)`.
  `calls`, `callCount`, `assertCalled` and `assertNotCalled` check the method in the same way, because calls
  of a `final`, `static` or `private` method cannot be counted.
* Only `mock()` records calls. The objects of `newObjOverriding` and `newObjWithoutConstructor` do not, since
  nobody can ask for them. A clone of a mock object (`super.clone()`) shares the mock: its calls are
  recorded there, and methods without an override run on the clone.

## Limits

These cannot be replaced, and the call fails readably instead:

* `final` classes and methods, records, enums, sealed classes;
* `static` and `private` methods;
* constructors.

Two more limits concern code that bypasses the methods:

* An attribute that student code reads directly is not affected by any override. Set it with
  `attribute.setValue(obj, value)`.
* A generated subclass is a different class, so a student's `getClass()`-based `equals` returns `false`
  against a real object.

There are no argument matchers and no call-order checks. A function and `calls(method)` cover these.
