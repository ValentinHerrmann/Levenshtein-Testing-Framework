# Architecture

How the framework, an exam's tests and the student code fit together, which class does what, and how a wrapper
gets from "expected" to "found". For the exact signatures of every public class and method see the
[API Reference (Javadoc)](https://valentinherrmann.github.io/Levenshtein-Testing-Framework/api/); the diagrams on
this page show the parts you work with, not every method.

## Overview

```mermaid
flowchart TB
    subgraph EX["Example exercise (test/)"]
        direction LR
        TM["TestManager<br/>@LevenshteinTest + @Policy"]
        POL[("SecurityPolicy.yaml")]
        subgraph WR["wrappers"]
            direction TB
            DW[DrivableWrapper]
            AW[AbstrWrapper]
            CRW[CarWrapper]
        end
        TM -- uses --> WR
        TM -. "@Policy" .-> POL
    end

    subgraph FW["Framework (framework/, published)"]
        direction LR
        LT["@LevenshteinTest<br/>@HiddenLevenshteinTest"]
        SL[StructuralLevenshtein]
        CW["ClassWrapper&lt;T&gt;"]
        MW["AttributeWrapper<br/>MethodWrapper<br/>ConstructorWrapper"]
        W["Wrapper&lt;T&gt;"]
        WP["WrapperProperty&lt;T&gt;"]
        U["Failure · Mock<br/>LevenshteinSettings · Messages · Utils"]
        CW --> W
        MW --> W
        W --> WP
    end

    subgraph ST["Student code (assignment/src), supervised by Ares 2"]
        direction LR
        D["«interface»<br/>Driveable"]
        AV["«abstract»<br/>AbstractVehicle"]
        C[Car]
        C -- extends --> AV
        C -. implements .-> D
    end

    TM -. annotated with .-> LT
    TM -- generates tests --> SL
    WR -- extend --> CW
    WR == "verify (fuzzy)" ==> ST

    classDef fw fill:#2C3E50,stroke:#1a252f,color:#ffffff
    classDef ex fill:#27AE60,stroke:#1e8449,color:#ffffff
    classDef tests fill:#F39C12,stroke:#b9770e,color:#000000
    classDef student fill:#E74C3C,stroke:#a93226,color:#ffffff
    class LT,SL,W,CW,MW,WP,U fw
    class DW,AW,CRW ex
    class TM,POL tests
    class D,AV,C student
```

Every expected class of the student code has a wrapper (a `ClassWrapper` subclass) that describes it. The wrapper
finds the student's actual class and members, even with small naming deviations. `TestManager` generates the
structural tests from the wrappers and uses the wrappers for the behavioural tests. Every test runs under Ares 2
supervision (`@LevenshteinTest` + `@Policy`).

## What happens in a test run

1. **Building the wrappers** (static field or `@BeforeAll`): each wrapper stores only what is *expected*: names,
   types, modifiers. Nothing is looked up yet, and no student code runs.
2. **First use, inside a supervised test**: the wrapper looks up its element once
   ([How Matching Works](matching.md)) and stores each part as a `WrapperProperty` (expected, actual,
   `EXACT`/`DEVIATES`/`MISSING`). The worst part is the wrapper's verdict.
3. **Structural tests** (`StructuralLevenshtein.structuralTestFactory`) fail for every wrapper that is not
   `EXACT` and print its `DEVIATION`/`MISSING` message.
4. **Behavioural tests** call the student's element through the wrapper (`invoke`, `getValue`, `newObj`, ...).
   A `DEVIATES` element is used as it is; a `MISSING` one fails the call. Exceptions of student code become a
   [failure block](writing-tests.md#reporting-failures) (`Failure`).
5. **Between tests**, `StaticStateReset` (part of `@LevenshteinTest`) resets attributes that are `static`
   although the test expects instance attributes, so tests do not influence each other.

## Packages and classes

**`io.github.valentinherrmann.levenshtein`** (framework, published)

| Class | Role |
|---|---|
| `Wrapper<T>` | Base of all wrappers: expected name and modifiers, verdict, messages, missing/unexpected modifiers |
| `ClassWrapper<T>` | A class, abstract class or interface: superclass, interfaces, instantiation (`newObj`, `newObjWithoutConstructor`, `newObjOverriding`, `mock`), `testGettersSetters`, `ClassWrapper.reference(name, package)` |
| `AttributeWrapper<T,V>`, `MethodWrapper<T,R>`, `ConstructorWrapper<T>` | Members: lookup, `getValue`/`setValue`, `invoke`, `invokeExpectingException` |
| `GenericClassWrapper<T>` | Wraps a class that is already loaded; used for the student's *actual* superclass and interfaces, not for expected classes |
| `WrapperProperty<T>` | Expected vs. actual value of one part plus its `Existence` |
| `StructuralLevenshtein` | JUnit `DynamicTest` factory for the structural tests (`DetailLevel`) |
| `Failure` | The structured failure block (`Method:`, `Scenario:`, `Input:`, `Expected:`, `Actual:`), also for your own checks |
| `Mock<T>` | Test-owned overrides and call records on an object of a student class |
| `LevenshteinTest` / `HiddenLevenshteinTest` | Composed annotations: `@Public`/`@Hidden` + `@StrictTimeout(5)` + `@MirrorOutput` + `@ExtendWith(StaticStateReset.class)` |
| `StaticStateReset` | JUnit extension that resets unexpectedly `static` attributes before each test |
| `LevenshteinSettings` | Runtime configuration: language, thresholds, decimals, getter/setter comparison, unexpected modifiers |
| `Messages` | German/English feedback texts |
| `Utils` | Levenshtein distance, type compatibility, `safeCast` |
| `TypeRef`, `Invocations`, `GettersSettersTest`, `MockClasses` (package-private) | Member types given as `ClassWrapper`, calling student code, getter/setter test, generated subclasses |

**`example-maven/`** and **`example-gradle/`** (not published): the same example exercise as two standalone
projects, one per build tool. Their `assignment/` and `test/` folders are identical (CI checks this) except
for one line in `SecurityPolicy.yaml`.

* `assignment/src/.../vehicles`: the "student" solution (`Car`, `AbstractVehicle`, `Driveable`), explained
  in [`SIMPLIFIED_CONCEPTS.md`](https://github.com/ValentinHerrmann/Levenshtein-Testing-Framework/blob/main/example-maven/SIMPLIFIED_CONCEPTS.md)
* `assignment-failing/src`: a solution with deliberate defects that shows the framework's messages
* `test/.../tests`: `TestManager` (tests), `TestAbstr`/`TestImpl`/`TestInterface` (test logic),
  `Constants` (Exercise Variants), `wrappers/*`, `SecurityPolicy.yaml`

## Class diagrams

The diagrams are [Mermaid](https://mermaid.js.org/) and render on GitHub and on the docs site. They show the
public API you use in tests; keep them in sync when it changes.

### Wrappers

Every wrapper describes one expected element of the student code. `ClassWrapper` holds the member wrappers of its
class; each member wrapper knows its parent `ClassWrapper`.

```mermaid
classDiagram
    direction TB
    class Wrapper~T~ {
        <<abstract>>
        +getOverallExistence() Existence
        +getExpectedName() String
        +getActualName() String
        +getActualModifiers() String
        +getMissingModifiers() List~String~
        +getUnexpectedModifiers() List~String~
        +getParentClassWrapper() ClassWrapper~T~
        +verifyExistence(boolean throwAssertion)
    }
    class ClassWrapper~T~ {
        <<abstract>>
        +reference(String name, String package)$ ClassWrapper
        +getClazz() Class~T~
        +getObj(boolean forceNew)* Object
        +getObj() Object
        +newObj() Object
        +getObj(boolean forceNew, ConstructorWrapper ctor, Object... args) T
        +setCachedObj(Object obj)
        +newObjWithoutConstructor() Object
        +newObjOverriding(Map overrides) Object
        +mock() Mock~T~
        +testGettersSetters(AttributeWrapper attribute, ...)
    }
    class GenericClassWrapper~T~ {
        +GenericClassWrapper(Class~T~ clz)
    }
    class AttributeWrapper["AttributeWrapper&lt;T, V&gt;"] {
        +getExpectedType() Class
        +getValue() V
        +getValue(Object obj) V
        +setValue(Object value)
        +setValue(Object obj, Object value)
    }
    class MethodWrapper["MethodWrapper&lt;T, R&gt;"] {
        +invoke(Object... params) R
        +invokeOnSpecificObject(Object obj, Object... params) R
        +invokeWithContext(Supplier context, Object... params) R
        +invokeExpectingException(Class expected, Object obj, Object... params) X
        +getParamTypes() Class[]
        +getActualParamTypes() Class[]
        +getExpectedReturnType() Class
    }
    class ConstructorWrapper~T~ {
        +invoke(Object... args) T
        +invokeExpectingException(Class expected, Object... args) X
        +getParamTypes() Class[]
        +getActualParamTypes() Class[]
    }

    ClassWrapper --|> Wrapper
    GenericClassWrapper --|> ClassWrapper
    AttributeWrapper --|> Wrapper
    MethodWrapper --|> Wrapper
    ConstructorWrapper --|> Wrapper
    ClassWrapper o-- AttributeWrapper : fields of the subclass
    ClassWrapper o-- MethodWrapper
    ClassWrapper o-- ConstructorWrapper
```

### Verdicts and member types

Each part of a wrapper (name, modifiers, type, parameters, superclass, ...) is a `WrapperProperty` with its own
`Existence`; the worst one is the wrapper's verdict. A member type that is a student class is a `TypeRef`, resolved
through its `ClassWrapper` when the member is checked.

```mermaid
classDiagram
    direction LR
    class Wrapper~T~ {
        <<abstract>>
    }
    class WrapperProperty~T~ {
        +getExpected() T
        +getActual() T
        +getExistence() Existence
    }
    class Existence {
        <<enumeration>>
        UNCHECKED
        EXACT
        DEVIATES
        MISSING
        +worst(Existence a, Existence b)$ Existence
    }
    class TypeRef {
        <<interface, package-private>>
        +resolve() Class
        +expectedSimpleName() String
    }
    class ClassWrapper~T~ {
        <<abstract>>
    }

    Wrapper *-- WrapperProperty : name, modifiers, type, params, ...
    WrapperProperty --> Existence
    Wrapper *-- TypeRef : attribute, return and parameter types
    TypeRef --> ClassWrapper : a fixed Class or a student class
```

### Failures, mocks and settings

```mermaid
classDiagram
    direction LR
    class Failure {
        <<final>>
        +of(MethodWrapper method)$ Failure
        +of(ConstructorWrapper ctor)$ Failure
        +of(String member)$ Failure
        +scenario(String text) Failure
        +input(String text) Failure
        +arguments(Object... args) Failure
        +expected(Object value) Failure
        +actual(Object value) Failure
        +thrown(Throwable t) Failure
        +cases(int failed, int total) Failure
        +error() AssertionError
        +format(Object value)$ String
        +call(ThrowingSupplier code)$ R
    }
    class Mock~T~ {
        <<final>>
        +override(MethodWrapper method, Function function) Mock~T~
        +returning(MethodWrapper method, Object value) Mock~T~
        +othersReturnDefaults() Mock~T~
        +obj() Object
        +callCount(MethodWrapper method) long
        +calls(MethodWrapper method) List~Object[]~
        +assertCalled(MethodWrapper method, int times)
        +assertNotCalled(MethodWrapper method)
        +resetCalls()
    }
    class LevenshteinSettings {
        <<final>>
        +setLanguage(Language language)$
        +setClassNameDeviationThreshold(int percent)$
        +setMethodNameDeviationThreshold(int percent)$
        +setAttributeNameDeviationThreshold(int percent)$
        +setDecimals(int decimals)$
        +setGettersSettersRequireSameObject(boolean same)$
        +setUnexpectedModifiers(UnexpectedModifiers mode)$
        +reset()$
    }
    class Language {
        <<enumeration>>
        DEUTSCH
        ENGLISH
    }
    class UnexpectedModifiers {
        <<enumeration>>
        DEVIATE
        IGNORE
    }
    class ClassWrapper~T~ {
        <<abstract>>
        +mock() Mock~T~
    }
    class MethodWrapper["MethodWrapper&lt;T, R&gt;"]

    ClassWrapper ..> Mock : creates
    Mock --> MethodWrapper : overrides, records calls
    Failure ..> MethodWrapper : Method line
    Failure ..> LevenshteinSettings : language, decimals
    LevenshteinSettings ..> Language
    LevenshteinSettings ..> UnexpectedModifiers
```

### Test generation and annotations

```mermaid
classDiagram
    direction LR
    class StructuralLevenshtein {
        <<final>>
        +structuralTestFactory(DetailLevel level, ClassWrapper... wrappers)$ List~DynamicTest~
        +structuralTestFactory(DetailLevel level, boolean skipEmpty, ClassWrapper... wrappers)$ List~DynamicTest~
    }
    class DetailLevel {
        <<enumeration>>
        ONE_FOR_EVERYTHING
        ONE_PER_CLASS
        ONE_PER_MEMBER_CATEGORY
        ONE_PER_MEMBER
    }
    class LevenshteinTest {
        <<annotation>>
    }
    class HiddenLevenshteinTest {
        <<annotation>>
    }
    class StaticStateReset {
        <<JUnit extension>>
    }

    StructuralLevenshtein ..> DetailLevel
    LevenshteinTest ..> StaticStateReset : @ExtendWith
    HiddenLevenshteinTest ..> StaticStateReset : @ExtendWith

    note for LevenshteinTest "= @Public + @StrictTimeout(5) + @MirrorOutput\n+ @ExtendWith(StaticStateReset.class)"
    note for HiddenLevenshteinTest "= @Hidden + @StrictTimeout(5) + @MirrorOutput\n+ @ExtendWith(StaticStateReset.class)"
```

### Example exercise: wrappers

One wrapper per expected class of the student code
(`io.github.valentinherrmann.example.tests.wrappers`). Each wrapper declares its members as `AttributeWrapper`,
`MethodWrapper` and `ConstructorWrapper` fields (found by `ClassWrapper` via reflection for the structural tests)
and exposes them through accessors for the behavioural tests. Expected names and types come from `Constants`
(Exercise Variants pattern).

```mermaid
classDiagram
    direction BT
    class ClassWrapper~T~ {
        <<abstract, framework>>
        +getObj(boolean forceNew)* Object
    }
    class DrivableWrapper~T~ {
        +maxSpeed() AttributeWrapper
        +startMethod() MethodWrapper
        +getSpeedMethod() MethodWrapper
        +getObj(boolean forceNew) Object
    }
    class AbstrWrapper~T~ {
        +manufacturer() AttributeWrapper
        +year() AttributeWrapper
        +constructor() ConstructorWrapper
        +getManufacturer() MethodWrapper
        +getYear() MethodWrapper
        +calculateCost() MethodWrapper
        +getInfo() MethodWrapper
        +getObj(boolean forceNew) T
    }
    class CarWrapper~T~ {
        +CarWrapper(ClassWrapper superClass, ClassWrapper... interfaces)
        +price() AttributeWrapper
        +speed() AttributeWrapper
        +constructor_full() ConstructorWrapper
        +constructor_default() ConstructorWrapper
        +getPrice() MethodWrapper
        +setPrice() MethodWrapper
        +startMethod() MethodWrapper
        +getSpeed() MethodWrapper
        +calculateCost() MethodWrapper
        +calculateCostYears() MethodWrapper
        +getInfo() MethodWrapper
        +getObj(boolean forceNew) Object
    }

    DrivableWrapper --|> ClassWrapper
    AbstrWrapper --|> ClassWrapper
    CarWrapper --|> ClassWrapper
    CarWrapper ..> AbstrWrapper : superClassWrapper
    CarWrapper ..> DrivableWrapper : interfaceWrappers

    note for DrivableWrapper "interface Driveable:\nMAX_SPEED, start(), getSpeed()"
    note for AbstrWrapper "abstract class AbstractVehicle:\nmanufacturer, year, abstract calculateCost(), getInfo()"
    note for CarWrapper "Car extends AbstractVehicle implements Driveable:\noverloaded constructors and calculateCost"
```
