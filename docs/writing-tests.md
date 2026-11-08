# Writing Tests

## Wrapper cheat sheet

| Wrapper | Constructor | Describes |
|---|---|---|
| `ClassWrapper<T>` | `(name, package, superClassWrapper, interfaceWrappers, modifiers...)`<br>`(name, package, modifiers...)` | A class, abstract class or interface. `superClassWrapper == null` means "extends `Object`". |
| `AttributeWrapper<T, V>` | `(this, name, type, modifiers...)` | A field, e.g. `(this, "price", double.class, "private")` |
| `MethodWrapper<T, R>` | `(this, name, returnType, modifiers...)`<br>`(this, name, returnType, new Class<?>[]{paramTypes}, modifiers...)` | A method, e.g. `(this, "calculateCost", double.class, new Class<?>[]{int.class}, "public")` |
| `ConstructorWrapper<T>` | `(this, new Class<?>[]{paramTypes}, modifiers...)` | A constructor |

Where a type is expected, you can also pass the `ClassWrapper` of a student class (or a mix of `Class<?>` and
`ClassWrapper`s in an `Object[]`), see [Student classes as member types](#student-classes-as-member-types).

* **Modifiers** are plain strings: `"public"`, `"private"`, `"protected"`, `"static"`, `"final"`, `"abstract"`, ...
  A typo throws `IllegalArgumentException` as soon as the wrapper is created.
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

| Call | Target |
|---|---|
| `invoke(args...)`, `getValue()`, `setValue(value)` | the cached default instance (`getObj()`), or `null` for static members |
| `invokeOnSpecificObject(obj, args...)`, `getValue(obj)`, `setValue(obj, value)` | `obj` |
| `getObj()` | the cached default instance. The cache is only reused for the same constructor and arguments. |
| `newObj()` / `getObj(true)` / `getObj(true, constructorWrapper, args...)` | always a new instance |
| `setCachedObj(obj)` | pins an object you created yourself as the default instance |

* If the student's element is `MISSING`, the call fails with "... is not implemented as expected. See
  structural Tests for details".
* An exception thrown by student code fails the test with its type, its message and the argument values
  (arrays and collections with more than ten elements shortened, e.g. `Arguments: ([0, 1, 2, 3, 4, 5, 6, 7, 8, 9, … (+90)])`); the original
  exception is attached as the cause. Ares security violations are passed through unchanged, also when a
  student's `toString()` causes one while the message is built; an endlessly recursing `toString()` is shown
  as `(toString() threw StackOverflowError)`.
* Many generated inputs? `invokeWithContext(() -> "Scenario " + i + ", seed " + seed, args...)` and
  `invokeOnSpecificObjectWithContext(context, obj, args...)` put the text in front of the failure message.
  The supplier is only evaluated if the call fails.
* **Arrays as arguments:** `invoke`, `invokeOnSpecificObject`, `invokeExpectingException`, `getObj(forceNew,
  constructorWrapper, args...)` and `ConstructorWrapper.invoke` take `Object... args`. Java spreads an object
  array (`String[]`, `Integer[]`, ...) passed there into several arguments; a primitive array (`boolean[]`)
  is passed as one. Pass a single array argument as `(Object) array`:
  `stage.constructor().invoke((Object) spotlights)`. A wrong number of arguments is reported as
  `IllegalArgumentException` with this hint.
* Numbers are converted to the student's numeric type: arguments (`invoke(2.5)` for a student's `float`
  parameter), `setValue` and results. A value that does not fit (`2.5` or `300` for a `byte`, `1e300` for a
  `float`) fails with a readable message instead of being changed; see [Matching](matching.md#types).
* `Utils.safeCast(value, type)` converts numeric results in the same way (e.g. `int` → `double`). It fails with
  a readable message instead of producing a `ClassCastException` later.
* The `Arguments:` line shows arrays and collections as they were passed, even if the student's code changed
  them. A failure inside the call (from Ares, a mock or a conversion) also gets the arguments and the text of
  `invokeWithContext`.
* Static members: pass `null` as the object, or use `invoke(...)` / `getValue()`.

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
  was not given are reported as "not tested". The cached default instance (`getObj()`) is not changed.
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
* If a subtest fails, the test fails with one message about all three subtests:

```text
Getter/setter test for attribute price in class Car:
✅ setter -> attribute: passed
❌ attribute -> getter: attribute price has the value 2.5, but getPrice() returns 30000.0
❌ setter -> getter: after setPrice(2.5), getPrice() yields 30000.0, expected: 2.5
```

* Wrong use (two getters, a method with two parameters, a condition without setter, no sample values for an
  unsupported type) throws an `IllegalArgumentException`.

## Abstract classes and interfaces (Byte Buddy)

Abstract classes and interfaces are instantiated through a Byte Buddy subclass, using the actual
constructor (including `protected` ones). Concrete classes are **always** created with their own
constructor, so `final` classes and `getClass()`-based `equals` work. Calling an abstract method on such
an instance throws `AbstractMethodError`; test interface behaviour through an implementing class.

## Independent tests

A mistake in one student member should fail only the tests of that member. Two situations make failures
cascade. In the first, the method under test calls another student method: if that method is wrong, the
test fails too. In the second, the test needs an object of a student class only as a value, but
`newObj()` runs the student's constructor. Three tools remove these dependencies. All three run **no
constructor** of the student class, so a missing or broken constructor cannot fail the test.

| Call | Result |
|---|---|
| `newObjWithoutConstructor()` | an object of the class whose attributes keep their default values (`0`, `false`, `null`; field initialisers do not run either). Concrete classes, also `final` ones, keep their exact class. Abstract classes and interfaces get a generated subclass. |
| `newObjOverriding(Map.of(method, args -> result, ...))` | an object of a generated subclass in which each given method returns the result of its function |
| `mock()` | a `Mock`: overrides like `newObjOverriding`, plus a record of every call |

**A method under test calls another student method** (SE01E03: `isShowReady()` calls
`getNumberOfSpotlights()`). Replace the called method, so that only `isShowReady` is tested:

```java
Object stage = stageWrapper.newObjOverriding(Map.of(stageWrapper.getNumberOfSpotlights(), args -> 3));
assertThat(stageWrapper.isShowReady().invokeOnSpecificObject(stage)).isTrue();
```

The student's `isShowReady()` runs unchanged. Its call `this.getNumberOfSpotlights()` (or
`getNumberOfSpotlights()`) goes to the test's function.

**The method under test must call a method of another object** (SE01E04: `Drummer.calculateLoudness(volume,
crowdNoise)` must use `guitarist.calculateLoudness(volume)`). Give the Guitarist a return value the student
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

**An object is needed only as a value** (the Drummer's accessor tests need *some* Guitarist):
`guitaristWrapper.newObjWithoutConstructor()`.

`Mock` in detail:

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
* Methods without an override run the student's code, supervised by Ares as before. An abstract method
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

**Limits.** These cannot be replaced, and the call fails readably instead:
* `final` classes and methods, records, enums, sealed classes;
* `static` and `private` methods;
* constructors.

Two more limits concern code that bypasses the methods:
* An attribute that student code reads directly is not affected by any override. Set it with
  `attribute.setValue(obj, value)`.
* A generated subclass is a different class, so a student's `getClass()`-based `equals` returns `false`
  against a real object.

There are no argument matchers and no call-order checks. A function and `calls(method)` cover these.

## Configuration

Configure at runtime, e.g. in a static initializer of your test class:

```java
static {
    // default: DEUTSCH
    LevenshteinSettings.setLanguage(LevenshteinSettings.Language.ENGLISH);
    // in percent, default: 20 each
    LevenshteinSettings.setClassNameDeviationThreshold(10);
    LevenshteinSettings.setMethodNameDeviationThreshold(20);
    LevenshteinSettings.setAttributeNameDeviationThreshold(20);
    // testGettersSetters: equal content (false, default) or the same object (true) for reference values
    LevenshteinSettings.setGettersSettersRequireSameObject(false);
}
```

`LevenshteinSettings.reset()` restores the defaults. Timeouts: `@LevenshteinTest` carries
`@StrictTimeout(5)`. A `@StrictTimeout` on the class or a method overrides it. (`regardingTimeouts` in the
policy is not enforced by Ares 2.2.1.)

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
