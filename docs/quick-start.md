# Quick Start

[README](../README.md) · **Quick Start** · [Writing Tests](writing-tests.md) · [Ares 2 Setup](ares-setup.md) · [Matching](matching.md) · [Architecture](architecture.md) · [Migration](migration.md) · [FAQ](faq.md)

**Requirements:** JDK 25 and Maven 3.9+. Ares 2.2.1, JUnit 6, AssertJ and Byte Buddy come in through the
example's `pom.xml`.

## Step 1: Run the example

```bash
git clone https://github.com/ValentinHerrmann/Levenshtein-Testing-Framework.git
cd Levenshtein-Testing-Framework
./mvnw -B verify        # builds the framework and runs the example exercise under Ares 2
```

The repository contains two modules:

| Module | Content |
|---|---|
| [`framework/`](../framework) | The published library `io.github.valentinherrmann:levenshtein-testing-framework` |
| [`example/`](../example) | A complete reference exercise in the Artemis layout (`assignment/src` = student code, `test/` = instructor tests) protected by Ares 2. **This is the template for your exam repository.** |

## Step 2: Create your exam repository from `example/`

Copy the contents of `example/` (`pom.xml`, `assignment/`, `test/`) into your exam repository. Copy
`mvnw`, `mvnw.cmd` and `.mvn/wrapper/` as well if you want the Maven wrapper. Then adapt it. In this
walkthrough the student package is `org.example.exam` and the test package is `org.example.tests`:

| Where | What to change |
|---|---|
| `pom.xml` → `levenshtein.version` | The framework version. `2000.0.0` is not on Maven Central yet: until it is, run `./mvnw -B install -pl framework` in this repository and keep `2000.0.0-SNAPSHOT`. |
| `pom.xml` → antrun `verify-ares-reserved-packages-v2` | Replace `io/github/valentinherrmann/example/tests/**` with your test package, e.g. `org/example/tests/**`. |
| `assignment/src/`, `test/io/`, `test/sandbox/` | Delete the example's student code, tests and sandbox file. |
| `test/SecurityPolicy.yaml` | `theSupervisedCodeUsesTheFollowingPackage: "org.example.exam"`, `theMainClassInsideThisPackageIs`, the list `theFollowingClassesAreTestClasses` (the **exact** names of all your test and wrapper classes), and `regardingFileSystemInteractions: [ ]`. |
| Your test classes | `@Policy(value = "test/SecurityPolicy.yaml", withinPath = "classes/org/example/exam")` |

> [!IMPORTANT]
> The student package must not be a prefix of the test package (`org.example.exam` and
> `org.example.exam.tests` would be wrong). The reasons for every setting are in
> [Ares 2 Setup in Detail](ares-setup.md).

## Step 3: Write your first wrapper and test

A **wrapper** describes one class you expect from the student. The framework finds the student's class and
members, even with small typos, and the tests work through the wrapper.

<details open>
<summary><b>Student code</b>: <code>assignment/src/org/example/exam/Car.java</code> (the expected solution)</summary>

```java
package org.example.exam;

public class Car {
    private double price;
    private double speed;

    public Car(double price) {
        this.price = price;
    }

    public void start() {
        speed = 10.0;
    }

    public double getPrice() {
        return price;
    }
}
```
</details>

**Wrapper**: `test/org/example/tests/CarWrapper.java`

```java
package org.example.tests;

import io.github.valentinherrmann.levenshtein.*;

/** Describes the expected class: public class Car in package org.example.exam. */
public class CarWrapper<T> extends ClassWrapper<T> {
    // One field per expected member; the structural tests find them via reflection.
    private final AttributeWrapper<T, Double> price;
    private final AttributeWrapper<T, Double> speed;
    private final ConstructorWrapper<T> constructor;
    private final MethodWrapper<T, Void> start;
    private final MethodWrapper<T, Double> getPrice;

    public CarWrapper() {
        super("Car", "org.example.exam", "public");
        price = new AttributeWrapper<>(this, "price", double.class, "private");
        speed = new AttributeWrapper<>(this, "speed", double.class, "private");
        constructor = new ConstructorWrapper<>(this, new Class<?>[]{double.class}, "public");
        start = new MethodWrapper<>(this, "start", void.class, "public");
        getPrice = new MethodWrapper<>(this, "getPrice", double.class, "public");
    }

    // Accessors for the behavioural tests
    public AttributeWrapper<T, Double> price() { return price; }
    public AttributeWrapper<T, Double> speed() { return speed; }
    public MethodWrapper<T, Void> start() { return start; }
    public MethodWrapper<T, Double> getPrice() { return getPrice; }

    // The default instance used by newObj(), getObj() and invoke()
    @Override
    public Object getObj(boolean forceNew, boolean useByteBuddy) {
        return getObj(forceNew, useByteBuddy, constructor, 30000.0);
    }
}
```

**Test**: `test/org/example/tests/CarTest.java`

```java
package org.example.tests;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import de.tum.cit.ase.ares.api.Policy;
import io.github.valentinherrmann.levenshtein.LevenshteinSettings;
import io.github.valentinherrmann.levenshtein.LevenshteinTest;
import io.github.valentinherrmann.levenshtein.StructuralLevenshtein;
import io.github.valentinherrmann.levenshtein.StructuralLevenshtein.DetailLevel;

@LevenshteinTest
@Policy(value = "test/SecurityPolicy.yaml", withinPath = "classes/org/example/exam")
class CarTest {

    static {
        LevenshteinSettings.setLanguage(LevenshteinSettings.Language.ENGLISH); // default: German
    }

    static final CarWrapper<?> car = new CarWrapper<>();

    // Structure: Class[Car], Constructors[Car], Attributes[Car], Methods[Car]
    @TestFactory
    List<DynamicTest> structure() {
        return StructuralLevenshtein.structuralTestFactory(DetailLevel.ONE_PER_MEMBER_CATEGORY, car);
    }

    // Behaviour: still runs if the student wrote "prize" instead of "price"
    @Test
    void startSetsSpeed() {
        Object c = car.newObj();
        car.start().invokeOnSpecificObject(c);
        assertThat(car.speed().getValue(c)).isEqualTo(10.0);
    }

    @Test
    void getPriceReturnsPrice() {
        car.testGetter(car.price(), car.getPrice());
    }
}
```

The matching `SecurityPolicy.yaml` lists the two test classes:

```yaml
  theSupervisedCodeUsesTheFollowingPackage: "org.example.exam"
  theMainClassInsideThisPackageIs: "Car"
  theFollowingClassesAreTestClasses:
    - "org.example.tests.CarTest"
    - "org.example.tests.CarWrapper"
```

## Step 4: Run it and read the results

```bash
./mvnw -B verify
```

With the solution above, all 6 tests pass. Every expected element ends up in one of three states:

* **`EXACT`**: matches the specification. ✅
* **`DEVIATES`**: found with small differences, e.g. `prize` for `price` (20 % of the name length is
  tolerated by default). The **structural test fails** with a `DEVIATION` message, but the **behavioural
  tests use the student's element** and can still pass.
* **`MISSING`**: not found, e.g. `strat()` for `start()` (a swap of two letters counts as 2 edits = 40 %).
  The structural test fails, and so does every behavioural test that needs the element:
  `Method public void start() in class Car is not implemented as expected.`

That's it. Next: describe more classes ([Writing Tests](writing-tests.md)) and look at the full example in
[`example/test`](../example/test/io/github/valentinherrmann/example/tests), which covers an interface, an
abstract class, inheritance, overloading and Exercise Variants.
