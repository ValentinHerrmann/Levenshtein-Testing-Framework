# Levenshtein Name Deviation Testing Framework

**Version Notice:** V0260.9.0 was published accidentially (instead of 0.9.0). To get back to well-structure versioning V1000.0.0 acts as V1.0.0.

## Overview

This pattern provides a robust framework for testing student code that tolerates minor naming deviations (typos, small alterations) while maintaining structural integrity verification. It uses **Levenshtein distance** for fuzzy name matching and **Java Reflection** for dynamic structure analysis, making it ideal for educational exercises and exams.

### Key Features

✅ **Fuzzy Name Matching** - Tolerates typos in class, method, attribute, and constructor names  
✅ **Structural Verification** - Validates class hierarchies, interfaces, modifiers, and types  
✅ **ByteBuddy Integration** - Tests abstract classes via dynamic subclass generation  
✅ **Exercise Variants Compatible** - Works seamlessly with parameterized test patterns  
✅ **Detailed Feedback** - Provides clear distinction between EXACT, DEVIATES, and MISSING states  
✅ **Security-Aware** - Handles restricted access via getter fallbacks and ByteBuddy proxies

---

## Problem Statement

Testing student code with exact name matching can lead to issues when students make minor naming deviations. Such deviations may result in test failures even if the underlying logic is correct.

In learning exercises where students implement specific classes or methods, small naming deviations are common:
* **Increased frustration:** Correct logic marked incorrect due to naming issues
* **Reduced learning effectiveness:** Focus shifts from concepts to exact names
* **Exam penalties:** Unfair penalization for minor mistakes
* **Instructor workload:** Manual review of complaints and re-evaluation

### Rationale

This pattern creates a forgiving testing environment while maintaining assessment integrity:
* **Fully compatible with Exercise Variants Pattern:** Uses Strings for finding names and Types via Reflection ([see details](../exercise_variants/README.md))
* **Improves student experience:** Focus on learning concepts rather than exact names
* **Reduces instructor workload:** Minimizes manual reviews due to naming issues
* **Maintains rigor:** Still validates structural correctness and type safety

---

## Quick Start

### Try it out
Run tests using:
```bash
./mvnw clean test
```

### Load package as dependency in your project
**Maven:** pom.xml
```xml
<!-- Include Levenshtein Testing Framework -->
<dependency>
    <groupId>io.github.valentinherrmann</groupId>
    <artifactId>levenshtein-testing-framework</artifactId>
    <version>1000.0.0</version> <!-- insert desired version or use dynamic versioning: [1000.0.0,) -->]
    <scope>test</scope>
</dependency>
```

**Gradle:** build.gradle
```gradle
// Include Levenshtein Testing Framework
testImplementation 'io.github.valentinherrmann:levenshtein-testing-framework:1000.0.0'
``` 



### Basic Usage Example

```java
// 1. Create wrapper for expected class structure
public class CarWrapper<T> extends ClassWrapper<T> {
    private final AttributeWrapper<T, Double> price;
    private final MethodWrapper<T, Void> start;
    
    public CarWrapper() {
        super("Car", "io.github.valentinherrmann", "public");
        
        price = new AttributeWrapper<>(this, "price", double.class, "private");
        start = new MethodWrapper<>(this, "start", void.class, "public");
    }
    
    public Object getObj(boolean forceNew, boolean useByteBuddy) {
        return getObj(forceNew, useByteBuddy, constructor_full, "BMW", 2023, 30000.0);
    }
}

// 2. Use in tests
@TestFactory
List<DynamicTest> structuralTests() {
    CarWrapper<?> carWrapper = new CarWrapper<>();
    return StructuralLevenshtein.structuralTestFactory(
        DetailLevel.ONE_PER_CLASS, 
        carWrapper
    );
}

@Test
void testCarBehavior() {
    CarWrapper<?> car = new CarWrapper<>();
    car.startMethod().invoke();  // Calls start() on actual student class
    double speed = car.getSpeed().invoke();
    assertThat(speed).isEqualTo(10.0);
}
```

---

## Architecture Overview

![ARCHITECTURE](http://www.plantuml.com/plantuml/proxy?cache=no&src=https://raw.githubusercontent.com/ValentinHerrmann/Levenshtein-Testing-Framework/refs/heads/main/puml/ARCHITECTURE.puml)


### Package Structure

**`io.github.valentinherrmann.levenshtein`** - Core framework (reusable)
* `Wrapper<T>` - Abstract base for all wrappers
* `ClassWrapper<T>` - Wraps classes (abstract/concrete/interface)
* `AttributeWrapper<T,V>` - Wraps fields/attributes
* `MethodWrapper<T,R>` - Wraps methods
* `ConstructorWrapper<T>` - Wraps constructors
* `GenericClassWrapper<T>` - Wraps existing classes (e.g., for inheritance checks)
* `WrapperProperty<T>` - Tracks expected vs. actual values and existence state
* `StructuralLevenshtein` - Test factory for generating JUnit DynamicTests
* `Utils` - Levenshtein distance and type compatibility utilities

**`io.github.valentinherrmann.wrappers`** - Test-specific implementations
* One wrapper class per class under test (e.g., `CarWrapper`, `AbstrWrapper`, `DrivableWrapper`)
* Defines expected structure (attributes, methods, constructors)
* Provides access methods for test code

**`io.github.valentinherrmann`** - Test execution
* `TestManager` - Contains @TestFactory and @Test methods
* `TestSettings` - Configuration (package, deviation thresholds)
* `Constants` - Centralized test data (names, types)

---

## How It Works

### 1. Wrapper Definition Phase

Define expected structure using wrapper classes:

```java
public class CarWrapper<T> extends ClassWrapper<T> {
    private final AttributeWrapper<T, Double> price;
    private final MethodWrapper<T, Double> calculateCost;
    
    public CarWrapper(ClassWrapper<?> superClass, ClassWrapper<?>... interfaces) {
        super("Car", "io.github.valentinherrmann", superClass, interfaces, "public");
        
        // Define expected attributes
        price = new AttributeWrapper<>(this, "price", double.class, "private");
        
        // Define expected methods
        calculateCost = new MethodWrapper<>(this, "calculateCost", double.class, "public");
    }
}
```

### 2. Fuzzy Matching Phase

Wrappers automatically search for elements using Levenshtein distance:

```java
protected void findWithDeviation() {
    try {
        // Try exact match first
        field = clazz.getDeclaredField(name.expected);
        name.existence = EXACT;
    } catch (NoSuchFieldException e) {
        // Try fuzzy match within threshold
        for (Field f : clazz.getDeclaredFields()) {
            if (isNameWithinDeviation(name.expected, f.getName(), THRESHOLD)) {
                name.existence = DEVIATES;
                field = f;
            }
        }
    }
}
```

### 3. Existence States

Each wrapper element tracks its existence:
* **`UNCHECKED`** - Not yet verified
* **`EXACT`** - Perfect match (name, type, modifiers)
* **`DEVIATES`** - Found with minor differences (fuzzy match or compatible type)
* **`MISSING`** - Not found or incompatible

### 4. Test Generation

Generate structural tests automatically:

```java
@TestFactory
List<DynamicTest> structuralTests() {
    return StructuralLevenshtein.structuralTestFactory(
        DetailLevel.ONE_PER_MEMBER_CATEGORY,  // Group by constructors/attributes/methods
        new CarWrapper<>(new AbstrWrapper<>(), new DrivableWrapper<>())
    );
}
```

**Detail Levels:**
* `ONE_FOR_EVERYTHING` - Single test for all elements
* `ONE_PER_CLASS` - One test per class
* `ONE_PER_MEMBER_CATEGORY` - Separate tests for constructors/attributes/methods
* `ONE_PER_MEMBER` - Individual test for each element (not yet implemented)

### 5. ByteBuddy Integration

Test abstract classes by creating dynamic subclasses:

```java
public Object getDynamicSubclassObj(Class<?>[] constructorParamTypes, Object... args) {
    Class<?> dynamicType = new ByteBuddy()
        .subclass(getClazz())
        .make()
        .load(getClazz().getClassLoader(), ClassLoadingStrategy.Default.WRAPPER)
        .getLoaded();
    
    return dynamicType.getConstructor(constructorParamTypes).newInstance(args);
}
```

**Benefits:**
* Tests abstract classes without concrete implementations
* Verifies constructors and inherited members
* Enables behavioral testing of partial implementations

---

## Configuration

### TestSettings.java

```java
public class TestSettings {
    public static final String BASE_PACKAGE = "io.github.valentinherrmann";
    
    // Deviation thresholds (0-100, percentage of max string length)
    public static final int CLASS_NAME_DEVIATION_THRESHOLD = 20;
    public static final int METHOD_NAME_DEVIATION_THRESHOLD = 20;
    public static final int ATTRIBUTE_NAME_DEVIATION_THRESHOLD = 20;
}
```

### Constants.java (Exercise Variants Pattern)

Centralize expected names and types for parameterization:

```java
public class Constants {
    public static String concreteClass() { return "Car"; }
    public static String abstractClass() { return "AbstractVehicle"; }
    public static Class<?> priceType() { return double.class; }
    // ... more constants
}
```

---

## Complete Testing Workflow

### Step-by-Step Guide

#### 1. Define Wrapper Classes

Create a wrapper for each class you want to test. The wrapper defines the expected structure:

```java
public class CarWrapper<T> extends ClassWrapper<T> {
    // Define expected attributes
    private final AttributeWrapper<T, Double> price;
    private final AttributeWrapper<T, Double> speed;
    
    // Define expected constructors
    private final ConstructorWrapper<T> constructor_full;
    private final ConstructorWrapper<T> constructor_default;
    
    // Define expected methods
    private final MethodWrapper<T, Void> start;
    private final MethodWrapper<T, Double> getSpeed;
    private final MethodWrapper<T, Double> calculateCost;
    
    public CarWrapper(ClassWrapper<?> superClass, ClassWrapper<?>... interfaces) {
        super("Car", "io.github.valentinherrmann", superClass, interfaces, "public");
        
        // Initialize attributes with expected name, type, and modifiers
        price = new AttributeWrapper<>(this, "price", double.class, "private");
        speed = new AttributeWrapper<>(this, "speed", double.class, "private");
        
        // Initialize constructors with parameter types
        constructor_full = new ConstructorWrapper<>(this, 
            new Class<?>[]{String.class, int.class, double.class}, "public");
        constructor_default = new ConstructorWrapper<>(this, 
            new Class<?>[]{String.class, int.class}, "public");
        
        // Initialize methods with return types and parameter types
        start = new MethodWrapper<>(this, "start", void.class, "public");
        getSpeed = new MethodWrapper<>(this, "getSpeed", double.class, "public");
        calculateCost = new MethodWrapper<>(this, "calculateCost", double.class, "public");
    }
    
    // Provide getter methods for convenient access in tests
    public AttributeWrapper<T, Double> price() { return price; }
    public MethodWrapper<T, Void> startMethod() { return start; }
    public MethodWrapper<T, Double> getSpeed() { return getSpeed; }
    
    // Implement abstract getObj method for instance creation
    @Override
    public Object getObj(boolean forceNew, boolean useByteBuddy) {
        return getObj(forceNew, useByteBuddy, constructor_full, "BMW", 2023, 30000.0);
    }
}
```

#### 2. Generate Structural Tests

Use `StructuralLevenshtein.structuralTestFactory()` to automatically generate tests:

```java
@TestFactory
List<DynamicTest> structuralTests() {
    // Create wrappers for all classes
    DrivableWrapper<?> drivable = new DrivableWrapper<>();
    AbstrWrapper<?> abstractVehicle = new AbstrWrapper<>();
    CarWrapper<?> car = new CarWrapper<>(abstractVehicle, drivable);
    
    // Generate tests with desired detail level
    return StructuralLevenshtein.structuralTestFactory(
        DetailLevel.ONE_PER_MEMBER_CATEGORY,
        drivable, abstractVehicle, car
    );
}
```

This generates separate test methods for:
- **Class structure** - Name, modifiers, superclass, interfaces
- **Constructors** - All constructors with parameter types
- **Attributes** - All attributes with types and modifiers
- **Methods** - All methods with return types, parameters, and modifiers

#### 3. Write Behavioral Tests

Use wrapper methods to invoke student code:

```java
@Test
void testCarStartsCorrectly() {
    CarWrapper<?> car = new CarWrapper<>(new AbstrWrapper<>(), new DrivableWrapper<>());
    
    // Invoke start() method on actual student implementation
    car.startMethod().invoke();
    
    // Check that speed was set correctly
    double speed = car.getSpeed().invoke();
    assertThat(speed).isEqualTo(10.0);
}

@Test
void testCalculateCostWithYears() {
    CarWrapper<?> car = new CarWrapper<>(new AbstrWrapper<>(), new DrivableWrapper<>());
    
    // Invoke overloaded method with parameter
    double cost = car.calculateCostYears().invoke(5);
    assertThat(cost).isEqualTo(15000.0);  // 5 years * 10% * 30000
}
```

#### 4. Test Attribute-Getter Consistency

Verify that getters return the actual attribute values:

```java
@Test
void testGettersReturnAttributeValues() {
    CarWrapper<?> car = new CarWrapper<>(new AbstrWrapper<>(), new DrivableWrapper<>());
    
    // This internally compares attribute value with getter return value
    car.testGetter(car.price(), car.getPrice());
}
```

### Understanding Existence States

The framework tracks four states for each element:

| State | Meaning | Example |
|-------|---------|---------|
| `UNCHECKED` | Not yet verified | Initial state before `findWithDeviation()` is called |
| `EXACT` | Perfect match | Expected: `price`, Actual: `price` (same name, type, modifiers) |
| `DEVIATES` | Minor differences | Expected: `price`, Actual: `pric` (typo) OR `int` vs `long` (compatible type) |
| `MISSING` | Not found or incompatible | Expected attribute not found OR incompatible type like `String` vs `int` |

**How it works:**

1. **Name matching**: Uses Levenshtein distance to find similar names
   ```java
   // Threshold: 20% of max string length
   "price" vs "pric"   → DEVIATES (1 char difference, 20% of 5 = 1 allowed)
   "price" vs "cost"   → MISSING (4 char difference, exceeds threshold)
   ```

2. **Type matching**: Checks exact match, assignability, and primitive widening
   ```java
   // Exact match
   int vs int          → EXACT
   
   // Assignable (subclass)
   Vehicle vs Car      → DEVIATES (if Car extends Vehicle)
   
   // Primitive widening
   int vs long         → DEVIATES (int can be widened to long)
   float vs double     → DEVIATES (float can be widened to double)
   
   // Incompatible
   String vs int       → MISSING
   ```

3. **Modifier matching**: Checks critical vs non-critical modifiers
   ```java
   // Critical: static, abstract, interface
   Expected: "public static"
   Actual:   "public"          → MISSING (static is missing)
   
   // Non-critical: public/protected/private
   Expected: "private"
   Actual:   "protected"       → DEVIATES (visibility different but not critical)
   ```

### Dynamic Test Generation Detail Levels

Choose the appropriate detail level based on your needs:

#### ONE_FOR_EVERYTHING
```java
// Generates 1 test that checks all classes and all their members
structuralTests()  // Single test, single failure point
```
**Use when**: Quick overview, development phase

#### ONE_PER_CLASS
```java
// Generates 1 test per class (including all its members)
structuralTests[Car]            // All Car elements
structuralTests[AbstractVehicle] // All AbstractVehicle elements
structuralTests[Driveable]      // All Driveable elements
```
**Use when**: Moderate granularity, most common choice

#### ONE_PER_MEMBER_CATEGORY
```java
// Generates separate tests for each category per class
structuralClass[Car]            // Only class structure
structuralConstructors[Car]     // All Car constructors
structuralAttributes[Car]       // All Car attributes
structuralMethods[Car]          // All Car methods
```
**Use when**: Detailed feedback, easier to identify which category has issues

### ByteBuddy Integration Details

ByteBuddy is used to test abstract classes and private members:

#### Testing Abstract Classes

```java
public class AbstrWrapper<T> extends ClassWrapper<T> {
    public AbstrWrapper() {
        super("AbstractVehicle", "io.github.valentinherrmann", "public", "abstract");
        // ... define members ...
    }
    
    @Override
    public Object getObj(boolean forceNew, boolean useByteBuddy) {
        // For abstract classes, useByteBuddy should be true
        return getObj(forceNew, true, constructor, "BMW", 2023);
    }
}

// In tests
@Test
void testAbstractClassConstructor() {
    AbstrWrapper<?> abstractVehicle = new AbstrWrapper<>();
    
    // ByteBuddy creates a concrete subclass at runtime
    Object instance = abstractVehicle.getObj(true);  // useByteBuddy=true
    
    // Now you can test inherited methods
    String manufacturer = abstractVehicle.getManufacturer().invoke();
    assertThat(manufacturer).isEqualTo("BMW");
}
```

**How it works internally:**
1. ByteBuddy creates a dynamic subclass that extends the abstract class
2. Abstract methods get default implementations (no-op or default returns)
3. Constructor and concrete methods work normally
4. You can test all non-abstract functionality

#### Private Member Access

For private members, there are two approaches:

**Approach 1: Use ByteBuddy (Recommended for non-private elements)**
```java
// useByteBuddy=true allows access through ByteBuddy proxy
Object instance = wrapper.getObj(true);  // ByteBuddy instance
```

**Approach 2: Use Reflection (Required for private elements)**
```java
// useByteBuddy=false forces reflection-based access
Object instance = wrapper.getObj(false);  // Pure reflection

// For private fields, AttributeWrapper automatically:
// 1. Tries field.setAccessible(true) and direct access
// 2. Falls back to public getter method if SecurityException occurs
double price = car.price().getValue();  // Handles private field
```

**Security Manager Fallback:**
```java
// If SecurityException prevents field.setAccessible(true):
// AttributeWrapper.getValue() automatically tries:
String getterName = "get" + capitalize(fieldName);  // "getPrice"
return ReflectionTestUtils.invokeMethod(obj, getterName);
```

### Combining with Exercise Variants Pattern

Use Constants for parameterization:

```java
// Constants.java
public class Constants {
    public static String concreteClass() { return "Car"; }
    public static String abstractClass() { return "AbstractVehicle"; }
    public static String interfaceName() { return "Driveable"; }
    
    public static String concreteClassAttribute() { return "price"; }
    public static Class<?> priceType() { return double.class; }
    
    public static String calculateCostMethod() { return "calculateCost"; }
    // ... more constants
}

// CarWrapper.java
public CarWrapper() {
    super(
        concreteClass(),      // From Constants
        BASE_PACKAGE,
        "public"
    );
    
    price = new AttributeWrapper<>(
        this,
        concreteClassAttribute(),  // From Constants
        priceType(),               // From Constants
        "private"
    );
}
```

This allows parameterization for exercise variants while maintaining type safety and fuzzy matching!

---

## Advanced Features

### Security Manager Compatibility

Handles restricted access gracefully:

```java
public V getValue(Object obj) {
    try {
        field.setAccessible(true);
        return field.get(obj);
    } catch (SecurityException e) {
        // Fallback to public getter method
        String getter = "get" + capitalize(name.expected);
        return ReflectionTestUtils.invokeMethod(obj, getter);
    }
}
```

### Type Compatibility Checking

Allows compatible type deviations:

```java
// int can be widened to long
// float can be widened to double
if (canContain(actualType, expectedType)) {
    typeWrapperProperty.existence = DEVIATES;
}
```

### Inheritance Verification

```java
CarWrapper car = new CarWrapper<>(
    new AbstrWrapper<>(),           // superClassWrapper
    new DrivableWrapper<>()         // interfaceWrappers
);

car.verifySuperClass();   // Checks extends AbstractVehicle
car.verifyInterfaces();   // Checks implements Driveable
```

---

## Framework Class Diagrams

### Core Framework (levenshtein package)

![Levenshtein Framework](http://www.plantuml.com/plantuml/proxy?cache=no&src=https://raw.githubusercontent.com/ValentinHerrmann/Levenshtein-Testing-Framework/refs/heads/main/puml/io.github.valentinherrmann.levenshtein.puml)


### Test Implementation (wrappers package)
![Wrappers](http://www.plantuml.com/plantuml/proxy?cache=no&src=https://raw.githubusercontent.com/ValentinHerrmann/Levenshtein-Testing-Framework/refs/heads/main/puml/io.github.valentinherrmann.wrappers.puml)


---

## Best Practices

### 1. Wrapper Organization
* One wrapper class per class under test
* Use descriptive getter methods (e.g., `price()`, `startMethod()`)
* Group related elements (constructors, attributes, methods)

### 2. Test Structure
```java
@TestFactory  // Structural verification
List<DynamicTest> structure() { ... }

@Test         // Behavioral testing
void testCalculateCost() { ... }

@Test         // Integration testing
void testGetterMatchesAttribute() {
    carWrapper.testGetter(carWrapper.price(), carWrapper.getPrice());
}
```

### 3. ByteBuddy Usage
* Use `useByteBuddy=true` for abstract classes and non-private members
* Use `useByteBuddy=false` for private member access (uses reflection)
* Cache instances when possible via `getObj(forceNew=false, ...)`

### 4. Error Messages
Wrappers provide detailed feedback:
```
!! DEVIATION !!
Expect: public double price
Actual: public double pric  (typo detected)
```

---

## Limitations & Future Work

### Current Limitations
* `ONE_PER_MEMBER` detail level not yet implemented
* Levenshtein threshold is fixed per element type (not per test)
* No support for generic type parameters in wrapper definitions

### Planned Enhancements
* Configurable thresholds per wrapper instance
* Enhanced feedback with suggestions for fixes
* Support for enum verification
* Annotation verification

---

## FAQ & Troubleshooting

### Common Questions

**Q: How do I set the Levenshtein distance threshold?**

A: Configure thresholds in `TestSettings.java`:
```java
public class TestSettings {
    // Values are percentages (0-100) of the maximum string length
    public static final int CLASS_NAME_DEVIATION_THRESHOLD = 20;
    public static final int METHOD_NAME_DEVIATION_THRESHOLD = 20;
    public static final int ATTRIBUTE_NAME_DEVIATION_THRESHOLD = 20;
}
```
Example: With 20% threshold, "price" (5 chars) allows 1 char deviation (20% of 5 = 1).

**Q: When should I use `useByteBuddy=true` vs `useByteBuddy=false`?**

A: 
- `useByteBuddy=true`: For abstract classes and testing non-private members
- `useByteBuddy=false`: When accessing private fields/methods or when ByteBuddy causes issues

**Q: How do I test interface constants?**

A: Use `AttributeWrapper` with appropriate modifiers:
```java
maxSpeed = new AttributeWrapper<>(
    this,
    "MAX_SPEED",
    double.class,
    "public", "static", "final"  // Interface constant modifiers
);
```

**Q: Can I test generic classes?**

A: Partially. The current implementation handles type parameters at runtime through reflection, but wrapper definitions don't support explicit generic type parameters. Use `Object` or specific types in wrappers.

**Q: What if student code has additional methods/attributes I didn't expect?**

A: The framework only verifies elements you define in wrappers. Additional elements are ignored. This allows students to add helper methods without failing tests.

### Troubleshooting

**Issue: `ExceptionInInitializerError` with ByteBuddy**

Possible causes:
1. ByteBuddy version incompatibility
2. ClassLoader issues
3. Security manager restrictions

Solutions:
- Use `useByteBuddy=false` for the affected elements
- Check ByteBuddy version in `build.gradle`
- Ensure proper ClassLoader strategy: `ClassLoadingStrategy.Default.WRAPPER`

**Issue: `SecurityException` when accessing private members**

This is expected with security managers. The framework automatically falls back to getter methods:
- Ensure student code has public getters for private attributes
- If no getter exists, test will fail with clear message

**Issue: Tests pass but shouldn't (false positive)**

Check that:
1. Wrapper definitions match expected structure exactly
2. Deviation thresholds aren't too permissive
3. Type compatibility checking isn't too lenient

**Issue: Tests fail but shouldn't (false negative)**

Check that:
1. Wrapper modifiers match actual implementation (e.g., "public abstract" for interfaces)
2. Expected types match actual types (including primitive vs wrapper classes)
3. Deviation thresholds allow for the actual difference
4. Method parameter types are in correct order

**Issue: `findWithDeviation()` not finding similar names**

Debug steps:
1. Calculate Levenshtein distance manually
2. Check threshold: `(distance * 100) / maxLength <= threshold`
3. Verify case sensitivity (matching is case-sensitive)
4. Check for extra whitespace in names

### Performance Considerations

**Reflection overhead**: Each wrapper uses reflection to find elements. For large test suites:
- Cache wrapper instances when possible
- Use `forceNew=false` to reuse objects
- Consider `ONE_PER_CLASS` detail level instead of `ONE_PER_MEMBER_CATEGORY`

**ByteBuddy overhead**: Dynamic subclass generation is relatively expensive:
- Cache instances with `getObj(forceNew=false, ...)`
- Reuse wrapper instances across multiple tests
- Consider lazy initialization for wrappers

---

## Example: Complete Test Flow

```java
// 1. Define wrapper
CarWrapper<?> car = new CarWrapper<>(new AbstrWrapper<>(), new DrivableWrapper<>());

// 2. Structural verification (automatic)
@TestFactory
List<DynamicTest> structuralTests() {
    return StructuralLevenshtein.structuralTestFactory(
        DetailLevel.ONE_PER_CLASS, car
    );
}

// 3. Behavioral testing (manual)
@Test
void testCarStartIncreasesSpeed() {
    car.startMethod().invoke();
    double speed = car.getSpeed().invoke();
    assertThat(speed).isGreaterThan(0.0);
}

// 4. Getter/Attribute consistency
@Test
void testPriceGetter() {
    car.testGetter(car.price(), car.getPrice());
}
```

---

## References

* **Exercise Variants Pattern**: [../exercise_variants/README.md](../exercise_variants/README.md)
* **ByteBuddy Documentation**: https://bytebuddy.net/
* **Levenshtein Distance**: https://en.wikipedia.org/wiki/Levenshtein_distance

---

## License & Contributing

This pattern is part of the Artemis Testing Patterns repository. Contributions and feedback are welcome!


