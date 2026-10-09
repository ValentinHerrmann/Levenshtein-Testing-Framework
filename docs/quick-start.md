# Quick Start

[README](../README.md) · **Quick Start** · [Writing Tests](writing-tests.md) · [Ares 2 Setup](ares-setup.md) · [Matching](matching.md) · [Architecture](architecture.md) · [Migration](migration.md) · [FAQ](faq.md)

**Requirements:** JDK 25 and either **Gradle** (the template ships a 9.8.0 wrapper, so just `./gradlew`;
9.1+ works) or Maven 3.9+. Ares 2.2.1, JUnit 6, AssertJ and Byte Buddy come in through the example's build file.

## Step 1: Run the example

```bash
git clone https://github.com/ValentinHerrmann/Levenshtein-Testing-Framework.git
cd Levenshtein-Testing-Framework/example-gradle
./gradlew test   # the example exercise under Ares 2
```

The example takes the framework `2000.0.0` from Maven Central. `./mvnw -B verify` in the repository root
builds the framework of the checkout and the Maven example instead.

The repository contains three modules:

| Module | Content |
|---|---|
| [`framework/`](../framework) | The published library `io.github.valentinherrmann:levenshtein-testing-framework` (built with Maven) |
| [`example-gradle/`](../example-gradle) | The example exercise as a standalone **Gradle** project: your exam template |
| [`example-maven/`](../example-maven) | The same exercise as a standalone **Maven** project: your exam template |

Both examples use the Artemis layout: `assignment/src` = student code, `test/` = instructor tests and policy.

## Step 2: Create your exam repository from the template

Copy the whole [`example-gradle/`](../example-gradle) (or [`example-maven/`](../example-maven)) folder as
your exam repository. It runs as it is, so you can check your machine first with `./gradlew test`
(`./mvnw -B verify`).

In this walkthrough the student package is `org.example.exam` and the test package is `org.example.tests`.
The student package must not be a prefix of the test package (`org.example.exam.tests` would be wrong).
Then adapt:

| Where | What to change |
|---|---|
| `assignment/src/`, `test/io/`, `test/sandbox/` | Delete the example's student code, tests and sandbox file, including the self-checks `SecurityControlTest`, `TimeoutControlTest` and `SandboxControl`. |
| Dependencies: `build.gradle` / `pom.xml` | Remove `junit-platform-testkit`: only `TimeoutControlTest` needs it. Add only libraries your course allows. |
| Reserved-package guard: `build.gradle` → `verifyAresReservedPackages`, `pom.xml` → antrun `verify-ares-reserved-packages-v2` | Replace `io/github/valentinherrmann/example/tests/**` with your test package, e.g. `org/example/tests/**`. |
| `test/SecurityPolicy.yaml` | `theSupervisedCodeUsesTheFollowingPackage: "org.example.exam"`, `theMainClassInsideThisPackageIs`, the list `theFollowingClassesAreTestClasses` (the **exact** names of all your test and wrapper classes), and `regardingFileSystemInteractions: [ ]`. Keep the programming language configuration. |
| Your test classes | `@Policy(value = "test/SecurityPolicy.yaml", withinPath = "classes/org/example/exam")` |

The framework version (`levenshteinVersion` in `build.gradle`, `levenshtein.version` in `pom.xml`) is the
release `2000.0.0` from Maven Central, so the copy builds on Artemis as it is.

> [!IMPORTANT]
> The reasons for every setting, for both build tools, are in [Ares 2 Setup in Detail](ares-setup.md).
> Do not remove the reserved-package guard or the weaving: without them the sandbox is silently inactive
> or can be replaced by the student.

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

/** Expected: public class Car in package org.example.exam. */
public class CarWrapper<T> extends ClassWrapper<T> {
    // One field per expected member (found via reflection).
    private final AttributeWrapper<T, Double> price;
    private final AttributeWrapper<T, Double> speed;
    private final ConstructorWrapper<T> constructor;
    private final MethodWrapper<T, Void> start;
    private final MethodWrapper<T, Double> getPrice;

    public CarWrapper() {
        super("Car", "org.example.exam", "public");
        price = new AttributeWrapper<>(this, "price", double.class, "private");
        speed = new AttributeWrapper<>(this, "speed", double.class, "private");
        constructor = new ConstructorWrapper<>(this,
                new Class<?>[]{double.class}, "public");
        start = new MethodWrapper<>(this, "start", void.class, "public");
        getPrice = new MethodWrapper<>(this, "getPrice", double.class,
                "public");
    }

    // Accessors for the behavioural tests
    public AttributeWrapper<T, Double> price() { return price; }
    public AttributeWrapper<T, Double> speed() { return speed; }
    public MethodWrapper<T, Void> start() { return start; }
    public MethodWrapper<T, Double> getPrice() { return getPrice; }

    // The default instance used by newObj(), getObj() and invoke()
    @Override
    public Object getObj(boolean forceNew) {
        return getObj(forceNew, constructor, 30000.0);
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
@Policy(value = "test/SecurityPolicy.yaml",
        withinPath = "classes/org/example/exam")
class CarTest {

    static {
        // feedback language, default: DEUTSCH
        LevenshteinSettings.setLanguage(LevenshteinSettings.Language.ENGLISH);
    }

    static final CarWrapper<?> car = new CarWrapper<>();

    // Structure: Class[Car], Constructors[Car], Attributes[Car], Methods[Car]
    @TestFactory
    List<DynamicTest> structure() {
        return StructuralLevenshtein.structuralTestFactory(
                DetailLevel.ONE_PER_MEMBER_CATEGORY, car);
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
./gradlew test        # or, with Maven: ./mvnw -B verify
```

With the solution above, all 6 tests pass. Every expected element ends up in one of three states:

| State | Example | Structural test | Behavioural tests |
|---|---|---|---|
| `EXACT` ✅ | `price` | passes | run |
| `DEVIATES` ⚠️ | `prize` for `price` (≤ 20 % of the name may differ) | fails | **still run** on `prize` |
| `MISSING` ❌ | `strat()` for `start()` (a swap is 2 edits = 40 %) | fails | fail: "... is not implemented as expected" |

The exact rules are in [How Matching Works](matching.md).

That's it. Next: describe more classes ([Writing Tests](writing-tests.md)) and look at the full example in
[`example-maven/test`](../example-maven/test/io/github/valentinherrmann/example/tests), which covers an interface, an
abstract class, inheritance, overloading and Exercise Variants.
