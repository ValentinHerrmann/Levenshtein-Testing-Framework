# Writing Tests

How to describe the expected classes with wrappers, generate the structural tests, and write behavioural tests
that still run when the student deviates slightly. [Quick Start](quick-start.md) shows a complete first test;
this page is the reference for everything after it.

## Wrapper cheat sheet

| Wrapper | Constructors | Describes |
|---|---|---|
| `ClassWrapper<T>` | `(name, package, modifiers...)`<br>`(name, package, superClassWrapper, interfaceWrappers, modifiers...)` | A class, abstract class, interface, enum or record. `superClassWrapper == null` means "extends `Object`". |
| `AttributeWrapper<T, V>` | `(this, name, type, modifiers...)` | A field, e.g. `(this, "price", double.class, "private")` |
| `MethodWrapper<T, R>` | `(this, name, returnType, modifiers...)`<br>`(this, name, returnType, new Class<?>[]{paramTypes}, modifiers...)`<br>`(this, name, returnType, new Object[]{paramTypes}, modifiers...)` | A method, e.g. `(this, "calculateCost", double.class, new Class<?>[]{int.class}, "public")` |
| `ConstructorWrapper<T>` | `(this, "public")` (no parameters)<br>`(this, new Class<?>[]{paramTypes}, modifiers...)`<br>`(this, new Object[]{paramTypes}, modifiers...)` | A constructor, e.g. `(this, "public")` for `public Car()` |

Where a type is expected (`type`, `returnType`, an entry of `paramTypes`), you can pass a `Class<?>`, the
`ClassWrapper` of a student class, or a `Supplier<ClassWrapper<?>>`. A parameter list that contains a wrapper is an
`Object[]`; see [Student classes as member types](#student-classes-as-member-types).

* **Modifiers** are plain strings: `"public"`, `"private"`, `"protected"`, `"static"`, `"final"`, `"abstract"`, ...
  A typo throws `IllegalArgumentException` as soon as the wrapper is created. Every modifier of the student's
  element that you do not list is reported as unexpected; mark harmless extras as optional with a `?` suffix
  (`"public", "final?"`). See [Modifiers](matching.md#modifiers).
* **Member wrappers must be (non-static) fields** of your `ClassWrapper` subclass. That's how the
  structural tests find them.
* **Every `ClassWrapper` subclass implements `getObj(boolean forceNew)`**, which defines the default
  instance, usually by delegating to `getObj(forceNew, constructorWrapper, args...)`.
* Nothing is looked up when a wrapper is created. Each wrapper looks up its element **once**, on first use,
  inside a supervised test.

## Inheritance and interfaces

Pass the wrappers of the expected superclass and interfaces to the subclass wrapper, and create them in
dependency order (see `beforeAll()` in
[`TestManager`](https://github.com/ValentinHerrmann/Levenshtein-Testing-Framework/blob/main/example-maven/test/io/github/valentinherrmann/example/tests/TestManager.java)):

```java
// interface Driveable
driveable = new DrivableWrapper<>();
// abstract class AbstractVehicle
vehicle = new AbstrWrapper<>();
// class Car extends AbstractVehicle implements Driveable
car = new CarWrapper<>(vehicle, driveable);

// inside CarWrapper:
public CarWrapper(ClassWrapper<?> superClass,
                  ClassWrapper<?>... interfaces) {
    super("Car", "org.example.exam", superClass, interfaces, "public");
    ...
}
```

The superclass and interface checks compare the classes the wrappers *found*, so a deviating superclass
name still counts. Inheriting indirectly, or implementing an additional interface, is `DEVIATES`.

## Student classes as member types

If an attribute, return or parameter type is itself a student class, pass its `ClassWrapper` instead of a
`Class<?>`. The type is looked up only when the member is checked, so:

* a **missing** student class only makes that type (or the parameters) `MISSING`. The member's name and
  modifiers are still checked, and nothing throws while the wrappers are built;
* a **misnamed** student class within the threshold is found like any other class, and the member uses it;
* two classes that **refer to each other** work, whichever wrapper is built or checked first.

Messages show the type by its expected simple name (`Drummer`), also if the class is missing.

Because member wrappers are built in the wrapper's constructor, refer to a class whose wrapper does not exist
yet in one of two ways:

```java
// 1. by name: a light wrapper without members, found like any other class
ClassWrapper<?> drummerType = ClassWrapper.reference("Drummer", "org.example.band");

// 2. via a supplier of the full wrapper, evaluated when the member is checked
Supplier<ClassWrapper<?>> drummerType = () -> TestManager.drummer;
```

Inside `GuitaristWrapper`'s constructor:

```java
drummer     = new AttributeWrapper<>(this, "drummer", drummerType, "private");
getDrummer  = new MethodWrapper<>(this, "getDrummer", drummerType, "public");
setDrummer  = new MethodWrapper<>(this, "setDrummer", void.class, new Object[]{drummerType}, "public");
constructor = new ConstructorWrapper<>(this, new Object[]{double.class, drummerType}, "public");
```

Parameter lists are `Object[]` here, each entry a `Class<?>`, a `ClassWrapper` or a supplier. A lambda written
directly into the array needs a cast, `(Supplier<ClassWrapper<?>>) () -> TestManager.drummer`, so prefer a
variable or `ClassWrapper.reference(...)`. A supplier that still returns `null` when the member is checked throws
`IllegalStateException` (wrong initialisation order in the test). `getExpectedType()`, `getExpectedReturnType()`
and `getParamTypes()` return `null` for a student class that is missing.

## Structural tests

```java
@TestFactory
List<DynamicTest> structure() {
    return StructuralLevenshtein.structuralTestFactory(
            DetailLevel.ONE_PER_MEMBER_CATEGORY, driveable, vehicle, car);
}
```

| Detail level | Generated tests |
|---|---|
| `ONE_FOR_EVERYTHING` | `Structural[all]` |
| `ONE_PER_CLASS` | `Structural[Car]`, ... |
| `ONE_PER_MEMBER_CATEGORY` | `Class[Car]`, `Constructors[Car]`, `Attributes[Car]`, `Methods[Car]`, ... |
| `ONE_PER_MEMBER` | `Class[Car]`, `Constructor[public Car(String, int)]`, `Attribute[Car.price]`, `Method[Car.public void start()]`, ... |

Pick the level that matches your grading granularity: each generated test is its own test case (on
Artemis, too).

* The order is deterministic; duplicate names get a `#2` suffix instead of replacing each other.
* A group without wrappers gets no test (e.g. no `Attributes[OpenAirStage]` for a subclass without own
  attributes), because it would always pass, also for an untouched template. `structuralTestFactory(level,
  false, wrappers...)` still generates them.
* Student code only runs inside the generated tests, i.e. supervised by Ares.
* If one element cannot be checked, that element is reported and the rest of the test continues.

## Behavioural tests

```java
Object car = carWrapper.newObj();                  // new default instance
carWrapper.start().invokeOnSpecificObject(car);    // student's method
double speed = carWrapper.speed().getValue(car);   // (private) attribute
carWrapper.speed().setValue(car, 0.0);
double cost = carWrapper.calculateCost().invoke(); // on the default instance
Object other = carWrapper.constructor().invoke(25000.0);

// getter and setter (see "Getters and setters" below)
carWrapper.testGettersSetters(carWrapper.price(), carWrapper.getPrice(), carWrapper.setPrice());

// expected exception (a MethodWrapper for "void setYear(int)")
IllegalArgumentException e = carWrapper.setYear()
        .invokeExpectingException(IllegalArgumentException.class, car, -1);
```

Every call goes to the element the wrapper *found*, so a test written for `price` runs against the student's
`prize`.

| Call | Target |
|---|---|
| `invoke(args...)`, `getValue()`, `setValue(value)` | the cached default instance (`getObj()`), or `null` for static members |
| `invokeOnSpecificObject(obj, args...)`, `getValue(obj)`, `setValue(obj, value)` | `obj` |
| `getObj()` | the cached default instance. The cache is only reused for the same constructor and arguments. |
| `newObj()` / `getObj(true)` / `getObj(true, constructorWrapper, args...)` | always a new instance |
| `setCachedObj(obj)` | pins an object you created yourself as the default instance |

Static members: pass `null` as the object, or use `invoke(...)` / `getValue()`.

### When the student's element is missing or throws

* A `MISSING` element fails the call with "... is not implemented as expected. See structural Tests for details".
  A `DEVIATES` element (typo, other numeric type, extra modifier, ...) is used as it is.
* An exception thrown by student code fails the test with a [failure block](#reporting-failures): the method,
  the argument values and the exception's type and message as `Actual:`. No stack trace and no cause are
  attached. Errors of the virtual machine (`StackOverflowError`, `OutOfMemoryError`, ...) are mistakes of the
  student code, too, and are reported the same way. A `StackOverflowError` adds a hint on endless recursion, an
  `OutOfMemoryError` a hint on a loop that never stops growing. The tests after it run normally.

  ```text
  Method:   createMedley(List)
  Scenario: standard cases
  Input:    ["Intro", "Chorus", "Bridge", "Outro"]
  Actual:   OutOfMemoryError: Java heap space
  Hint: an OutOfMemoryError usually means that a loop keeps making a String or a list bigger and never ends.
  ```

* Arrays and collections with more than ten elements are shortened in the `Input:` line
  (`[0, 1, 2, 3, 4, 5, 6, 7, 8, 9, … (+90)]`). They are shown as they were passed, even if the student's code
  changed them.
* Ares security violations are passed through unchanged, also when a student's `toString()` causes one while
  the message is built. A `toString()` that throws anything else is shown with the type of what it threw, e.g.
  an endlessly recursing one as `(toString() threw StackOverflowError)`.
* A failure inside the call (from Ares, a mock or a number conversion) keeps its own message and gets an
  `Arguments:` line and the text of `invokeWithContext`.

### Expected exceptions

`invokeExpectingException(type, obj, args...)` (methods) and `invokeExpectingException(type, args...)`
(constructors) pass if the student's code throws `type` or a subclass, and return the exception for further
checks. If nothing or something else is thrown, the test fails with a failure block whose `Expected:` line names
the exception type.

```java
IllegalArgumentException e = carWrapper.constructor()
        .invokeExpectingException(IllegalArgumentException.class, -1.0);
assertThat(e.getMessage()).contains("price");
```

### Many generated inputs

`invokeWithContext(() -> "case " + i + ", seed " + seed, args...)` and
`invokeOnSpecificObjectWithContext(context, obj, args...)` show the text in the `Scenario:` line of the failure.
The supplier is only evaluated if the call fails. To check many cases and report one of them, run them with
[`Failure.forEachCase`](#checking-many-cases).

### Arrays as arguments

`invoke`, `invokeOnSpecificObject`, `invokeExpectingException`, `getObj(forceNew, constructorWrapper, args...)`
and `ConstructorWrapper.invoke` take `Object... args`. Java spreads an object array (`String[]`, `Integer[]`,
...) passed there into several arguments; a primitive array (`boolean[]`) is passed as one. Pass a single array
argument as `(Object) array`:

```java
stage.constructor().invoke((Object) spotlights);
```

A wrong number of arguments is reported as `IllegalArgumentException` (an error in the test) with this hint.

### Numbers

Numbers are converted to the student's numeric type: arguments (`invoke(2.5)` for a student's `float`
parameter), `setValue` and results. A value that does not fit (`2.5` or `300` for a `byte`, `1e300` for a
`float`) fails with a readable message instead of being changed; see [Matching](matching.md#types).
`Utils.safeCast(value, type)` converts numeric results in the same way (e.g. `int` → `double`) and fails with a
readable message instead of producing a `ClassCastException` later.

## Getters and setters

`testGettersSetters` tests the getter and/or setter of an attribute; you only pass the wrappers. Call it in
your own test method:

```java
@Test
void priceGettersSetters() {
    car.testGettersSetters(car.price(), car.getPrice(), car.setPrice());          // getter and setter
    car.testGettersSetters(car.price(), car.getPrice());                           // getter only (= testGetter)
    car.testGettersSetters(car.price(), car.setPrice());                           // setter only
    car.testGettersSetters(car.price(), (Double p) -> p >= 0, car.getPrice(), car.setPrice()); // setter condition
    car.testGettersSetters(car.price(), List.of(1.5, 99.0, 0.0), car.setPrice());  // own sample values
}
```

* A method wrapper without parameters is the getter, one with one parameter the setter (any order).
* Up to three subtests run, each on a new instance: **setter -> attribute** (the setter stores the value),
  **attribute -> getter** (a value written into the attribute via reflection is returned; for a `final`
  attribute only the current value is compared) and **setter -> getter**. Subtests whose getter or setter
  was not given are skipped. The cached default instance (`getObj()`) is not changed.
* Sample values are generated for primitives, their wrappers, `String` and enums: five different values (so
  hard-coded getters and setters ignoring their argument fail), plus `null` for reference types if no
  primitive is involved. Other types (arrays, own classes, ...) need own sample values.
* A student type with another numeric type (e.g. `float` or `int` instead of `double`) is `DEVIATES`, and
  the generated values fit it (whole numbers for `int`), so the deviation only costs the structural point.
  Own sample values are used as they are: a value that does not fit (e.g. `2.5` for an `int`) fails.
* Values are compared by content (`Objects.deepEquals`), so a getter or setter may store or return a copy of
  an array; failure messages show the elements (`[true, false]`). To require the very same object for
  reference values, set `LevenshteinSettings.setGettersSettersRequireSameObject(true)` (see
  [Configuration](#configuration)).
* **Setter condition** (`Predicate`, e.g. `v -> v != null`): values that satisfy it must be stored; for other
  values the setter must keep the old value or throw an exception. A condition that throws itself (e.g. a
  `NullPointerException` for `null`) marks the value invalid. Only setters are checked; more complex rules
  need a behavioural test of your own.
* Every subtest runs with every sample value. If a case fails, the test fails with the
  [failure block](#reporting-failures) of the first failing case and the number of failed cases. A
  `StackOverflowError` or `OutOfMemoryError` of a later case is shown instead, so students see its hint. The
  `Scenario:` line names the subtest:

```text
Method:   getPrice()
Scenario: attribute -> getter, attribute price
Input:    price = 2.50
Expected: 2.50
Actual:   30000.00
(10 of 15 cases failed)
```

* An exception of the getter or setter is shown as `Actual: IllegalStateException: message`. For an invalid
  value (setter condition) `Expected:` reads `value rejected (unchanged 2.50 or exception)`.
* A missing getter, setter or attribute fails with the usual short "... is not implemented as expected".

* Wrong use (two getters, a method with two parameters, a condition without setter, no sample values for an
  unsupported type) throws an `IllegalArgumentException`.

## Reporting failures

Behavioural failures share one format: exceptions of student code, `invokeExpectingException`,
`testGettersSetters`, and your own checks through `Failure`. The labels follow
`LevenshteinSettings.setLanguage(...)`; lines that are not set are left out:

```text
Method:   calculateLoudness(double, double)
Scenario: generated case 3, seed 2040003
Input:    volume = 12.50, crowdNoise = 3.00
Expected: 31.25
Actual:   27.00
```

Use `Failure` for your own checks, so students see the same format there:

```java
double actual = wrapper.calculateLoudness().invoke(12.5, 3.0);
if (Math.abs(actual - 31.25) > 1e-9) {
    throw Failure.of(wrapper.calculateLoudness())          // Method line: name and parameter types
            .scenario("generated case 3, seed 2040003")
            .input("volume = 12.50, crowdNoise = 3.00")    // or .arguments(12.5, 3.0)
            .expected(31.25).actual(actual)
            .cases(2, 10)                                 // adds "(2 of 10 cases failed)"
            .error();
}
```

* `Failure.of(methodWrapper)`, `Failure.of(constructorWrapper)` or `Failure.of("any text")` start a block.
* `.expected(value)` / `.actual(value)` format values the same way everywhere (`Failure.format`): `double` and
  `float` with two decimals (`LevenshteinSettings.setDecimals(n)` changes that; the separator is always a
  dot), strings in double quotes, characters in single quotes, `null`, arrays and lists in brackets.
  `.expectedText(...)` / `.actualText(...)` show a text as it is.
* `.thrown(exception)` shows an exception as `Actual: IllegalStateException: message`. A `StackOverflowError`
  or `OutOfMemoryError` (also as the cause of another exception) adds its hint below the block.
* `.error()` returns the `AssertionError` to throw. It never has a cause, so students see no stack trace.
* `Failure.call(() -> ...)` runs student code and turns any exception or error, also an `OutOfMemoryError`,
  into a block (`Actual:` only); failures and Ares security violations are passed on unchanged. Prefer the
  wrappers' `invoke...` methods, whose block also shows the method and the input.
* `.cases(failed, total)` adds "(n of m cases failed)". `Failure.forEachCase` (below) counts the cases for you.

Structural failures ("Class X in package Y is not implemented as expected.") keep their short form.

### Checking many cases

`Failure.forEachCase(cases, body)` runs a check for every case and fails with one block plus the number of
failed cases:

```java
Failure.forEachCase(List.of(0, 1, 5, 20), n -> {
    String actual = wrapper.repeat().invoke("ab", n);
    if (!actual.equals("ab".repeat(n))) {
        throw Failure.of(wrapper.repeat()).arguments("ab", n)
                .expected("ab".repeat(n)).actual(actual)
                .error();
    }
});
```

```text
Method:   repeat(String, int)
Input:    "ab", 20
Actual:   OutOfMemoryError: Java heap space
(2 of 4 cases failed)
Hint: an OutOfMemoryError usually means that a loop keeps making a String or a list bigger and never ends.
```

* All cases run. Only failures (`AssertionError`) are counted; any other exception passes on at once, so call
  student code through the wrappers or `Failure.call`, which turn its exceptions into blocks.
* The block shown is the first `StackOverflowError` or `OutOfMemoryError` of the student code, so students see
  its hint even if an earlier case only returned a wrong value. Without one, it is the first failure.
* A failure that is not a `Failure` block (e.g. from AssertJ) gets the line "(n of m cases failed)" appended.

### Detecting resource errors

`Failure.isResourceError(e)` tells whether a failure comes from a `StackOverflowError` or an `OutOfMemoryError`
of the student code (also as the cause of another exception), without reading the message. Use it for your own
loops over cases, e.g. to prefer such a block:

```java
AssertionError shown = null;
for (int n : inputs) {
    try {
        check(n);
    } catch (AssertionError e) {
        if (shown == null || (!Failure.isResourceError(shown) && Failure.isResourceError(e))) {
            shown = e;
        }
    }
}
```

It works for the blocks of the wrappers, `Failure.call`, `testGettersSetters` and your own `.error()`, which stay
plain `AssertionError`s without a cause. A block copied into a new error (`new AssertionError(e.getMessage())`)
is no longer recognized.

## Abstract classes and interfaces (Byte Buddy)

Abstract classes and interfaces are instantiated through a Byte Buddy subclass, using the actual
constructor (including `protected` ones). Concrete classes are **always** created with their own
constructor, so `final` classes and `getClass()`-based `equals` work. Calling an abstract method on such
an instance throws `AbstractMethodError`; test interface behaviour through an implementing class.

## Independent tests and mocks

A mistake in one student member should fail only the tests of that member. `newObjWithoutConstructor()`,
`newObjOverriding(...)` and `mock()` create objects without running a student constructor, replace single student
methods with test code and record calls. See [Independent Tests and Mocks](independent-tests.md).

## Configuration

Configure at runtime in a static initializer of your test class (or in `@BeforeAll`):

```java
static {
    // feedback language, default: DEUTSCH
    LevenshteinSettings.setLanguage(LevenshteinSettings.Language.ENGLISH);
    // name deviation in percent of the longer name, default: 20 each
    LevenshteinSettings.setClassNameDeviationThreshold(10);
    LevenshteinSettings.setMethodNameDeviationThreshold(20);
    LevenshteinSettings.setAttributeNameDeviationThreshold(20);
    // decimals of double/float values in failure messages, default: 2
    LevenshteinSettings.setDecimals(2);
    // testGettersSetters: equal content (false, default) or the same object (true) for reference values
    LevenshteinSettings.setGettersSettersRequireSameObject(false);
    // modifiers the specification does not list: DEVIATE (default) or IGNORE
    LevenshteinSettings.setUnexpectedModifiers(LevenshteinSettings.UnexpectedModifiers.DEVIATE);
}
```

| Setting | Default | Effect |
|---|---|---|
| `setLanguage` | `DEUTSCH` | Language of every message (`ENGLISH` or `DEUTSCH`) |
| `set...NameDeviationThreshold` | `20` | How much of a name may differ, see [Name matching](matching.md#name-matching) |
| `setDecimals` | `2` | Decimals of `double`/`float` values in failure blocks (the separator is always a dot) |
| `setGettersSettersRequireSameObject` | `false` | `testGettersSetters` compares by content, or requires the very same object |
| `setUnexpectedModifiers` | `DEVIATE` | `IGNORE` reports only an unexpected `static` of attributes and methods, and a missing expected `static` makes the element `MISSING`; see [Modifiers](matching.md#modifiers) |

!!! warning "Set them before the first test"
    Set the settings in a static initializer or `@BeforeAll`, not in a test method or `@BeforeEach`:
    `@LevenshteinTest` looks up the attribute wrappers before each test (to reset attributes that are
    unexpectedly `static`), so a later change does not reach them.

`LevenshteinSettings.reset()` restores the defaults.

## Test annotations and timeouts

| Annotation | Use for | Composed of |
|---|---|---|
| `@LevenshteinTest` | public tests (students see the result) | `@Public` `@StrictTimeout(5)` `@MirrorOutput` `@ExtendWith(StaticStateReset.class)` |
| `@HiddenLevenshteinTest` | hidden tests (results shown after the `@Deadline`) | `@Hidden` `@StrictTimeout(5)` `@MirrorOutput` `@ExtendWith(StaticStateReset.class)` |

Both need your own `@Policy(...)` on the class, and hidden tests also an Ares `@Deadline` (class or method).
Without an Ares test-type annotation a test runs unsupervised.
A `@StrictTimeout` on the class or a method overrides the 5 seconds. (`regardingTimeouts` in the policy is not
enforced by Ares 2.2.1.) A hidden test class looks like a public one:

```java
@HiddenLevenshteinTest
@Deadline("2026-12-01 23:59")
@Policy(value = "test/SecurityPolicy.yaml", withinPath = "classes/org/example/exam")
class CarHiddenTest {
    @Test
    @StrictTimeout(10)   // this test may take longer
    void manyRandomCars() { ... }
}
```

## Exercise Variants

Keep all expected names and types in one place and read them in the wrappers, as in
[`example-maven/.../Constants.java`](https://github.com/ValentinHerrmann/Levenshtein-Testing-Framework/blob/main/example-maven/test/io/github/valentinherrmann/example/tests/Constants.java):

```java
public static String concreteClass() {
    return switch (variant) {
        case DEFAULT -> "Car";
    };
}
```

## Organising larger test suites

The example splits the tests into three layers: `TestManager` carries the annotations and the
`@Test`/`@TestFactory` methods, `TestAbstr`/`TestImpl`/`TestInterface` hold the test logic as static
methods, and `wrappers/*` describe the classes. Only the annotated class needs `@LevenshteinTest` and
`@Policy`, but **every** helper class must be listed in `theFollowingClassesAreTestClasses`.
