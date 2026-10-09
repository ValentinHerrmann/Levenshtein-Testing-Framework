# Writing Tests

[README](../README.md) · [Quick Start](quick-start.md) · **Writing Tests** · [Ares 2 Setup](ares-setup.md) · [Matching](matching.md) · [Architecture](architecture.md) · [Migration](migration.md) · [FAQ](faq.md)

## Wrapper cheat sheet

| Wrapper | Constructor | Describes |
|---|---|---|
| `ClassWrapper<T>` | `(name, package, superClassWrapper, interfaceWrappers, modifiers...)`<br>`(name, package, modifiers...)` | A class, abstract class or interface. `superClassWrapper == null` means "extends `Object`". |
| `AttributeWrapper<T, V>` | `(this, name, type, modifiers...)` | A field, e.g. `(this, "price", double.class, "private")` |
| `MethodWrapper<T, R>` | `(this, name, returnType, modifiers...)`<br>`(this, name, returnType, new Class<?>[]{paramTypes}, modifiers...)` | A method, e.g. `(this, "calculateCost", double.class, new Class<?>[]{int.class}, "public")` |
| `ConstructorWrapper<T>` | `(this, new Class<?>[]{paramTypes}, modifiers...)` | A constructor |

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
[`TestManager`](../example-maven/test/io/github/valentinherrmann/example/tests/TestManager.java)):

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

// attribute == getter
carWrapper.testGetter(carWrapper.price(), carWrapper.getPrice());

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
* An exception thrown by student code fails the test with its type and message (the original exception is
  attached as the cause). Ares security violations and assertion failures are passed through unchanged.
* `Utils.safeCast(value, type)` converts numeric results (e.g. `int` → `double`). It fails with a readable
  message instead of producing a `ClassCastException` later.
* Static members: pass `null` as the object, or use `invoke(...)` / `getValue()`.

## Abstract classes and interfaces (Byte Buddy)

Abstract classes and interfaces are instantiated through a Byte Buddy subclass, using the actual
constructor (including `protected` ones). Concrete classes are **always** created with their own
constructor, so `final` classes and `getClass()`-based `equals` work. Calling an abstract method on such
an instance throws `AbstractMethodError`; test interface behaviour through an implementing class.

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
}
```

`LevenshteinSettings.reset()` restores the defaults. Timeouts: `@LevenshteinTest` carries
`@StrictTimeout(5)`. A `@StrictTimeout` on the class or a method overrides it. (`regardingTimeouts` in the
policy is not enforced by Ares 2.2.1.)

## Exercise Variants

Keep all expected names and types in one place and read them in the wrappers, as in
[`example-maven/.../Constants.java`](../example-maven/test/io/github/valentinherrmann/example/tests/Constants.java):

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
