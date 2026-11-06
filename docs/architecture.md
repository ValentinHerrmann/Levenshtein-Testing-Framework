# Architecture

[README](../README.md) · [Quick Start](quick-start.md) · [Writing Tests](writing-tests.md) · [Ares 2 Setup](ares-setup.md) · [Matching](matching.md) · **Architecture** · [Migration](migration.md) · [FAQ](faq.md)

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
        U["Utils · LevenshteinSettings<br/>Messages"]
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

Every expected class of the student code has a wrapper (a `ClassWrapper` subclass) that describes it and
finds the student's actual class and members, even with small naming deviations. `TestManager` generates
the structural tests from the wrappers and uses them for the behavioural tests, under Ares 2 supervision
(`@LevenshteinTest` + `@Policy`). Detailed class diagrams of the framework and of the example wrappers follow
[below](#class-diagrams).

**`io.github.valentinherrmann.levenshtein`** (framework, published)
* `Wrapper<T>`: base of all wrappers (name, modifiers, existence, messages)
* `ClassWrapper<T>`: classes, abstract classes and interfaces, including superclass, interfaces and instantiation
* `AttributeWrapper<T,V>`, `MethodWrapper<T,R>`, `ConstructorWrapper<T>`: members
* `GenericClassWrapper<T>`: wraps an already loaded class (actual superclass and interfaces)
* `WrapperProperty<T>`: expected vs. actual value plus existence
* `StructuralLevenshtein`: JUnit `DynamicTest` factory
* `LevenshteinTest` / `HiddenLevenshteinTest`: composed Ares 2 annotations
* `LevenshteinSettings`: runtime configuration (thresholds, language)
* `Messages`: German/English feedback
* `Utils`: Levenshtein distance, type compatibility, `safeCast`

**`example-maven/`** and **`example-gradle/`** (not published): the same example exercise as two standalone
projects, one per build tool. Their `assignment/` and `test/` folders are identical (CI checks this) except
for one line in `SecurityPolicy.yaml`.
* `assignment/src/.../vehicles`: the "student" solution (`Car`, `AbstractVehicle`, `Driveable`), explained
  in [`SIMPLIFIED_CONCEPTS.md`](../example-maven/SIMPLIFIED_CONCEPTS.md)
* `test/.../tests`: `TestManager` (tests), `TestAbstr`/`TestImpl`/`TestInterface` (test logic),
  `Constants` (Exercise Variants), `wrappers/*`, `SecurityPolicy.yaml`

## Class diagrams

The diagrams are [Mermaid](https://mermaid.js.org/) and render directly on GitHub; keep them in sync
with the code when public signatures change.

### Framework: wrappers (`io.github.valentinherrmann.levenshtein`)

Every wrapper describes one expected element of the student code and looks it up once, on first use.
`WrapperProperty` stores the expected and the actual value of each part (name, modifiers, types, ...) and
its `Existence`; the worst part decides the overall state.

```mermaid
classDiagram
    direction TB
    class Wrapper~T~ {
        <<abstract>>
        #WrapperProperty~String~ name
        #WrapperProperty~String~ modifiers
        #Existence existence
        -ClassWrapper~T~ parentClassWrapper
        +verifyExistence(boolean throwAssertion)*
        +getOverallExistence() Existence
        +verifyModifiers(int modifierBitmask)
        +getParentClassWrapper() ClassWrapper~T~
        +getExpectedName() String
        +getActualName() String
        +getActualModifiers() String
        +expectedToString()* String
        +actualToString()* String
        #findWithDeviation()*
        #parseExistence()*
        #ensureChecked()
        #usability() Existence
    }
    class ClassWrapper~T~ {
        <<abstract>>
        -String expectedPackage
        -Class~T~ clazz
        #T obj
        ~WrapperProperty superClassWrapper
        ~WrapperProperty interfaceWrappers
        +getClazz() Class~T~
        +getExpectedPackage() String
        +getObj(boolean forceNew)* Object
        +getObj() Object
        +newObj() Object
        +getObj(boolean forceNew, ConstructorWrapper ctor, Object... args) T
        +setCachedObj(Object obj)
        +getDynamicSubclassObj(ConstructorWrapper ctor, Object... args) Object
        +verifySuperClass()
        +verifyInterfaces()
        +getAttributeWrappers() List~Wrapper~T~~
        +getMethodWrappers() List~Wrapper~T~~
        +getConstructorWrappers() List~Wrapper~T~~
        +testGetter(AttributeWrapper attribute, MethodWrapper getter)
    }
    class GenericClassWrapper~T~ {
        +GenericClassWrapper(Class~T~ clz)
        +getObj(boolean forceNew) Object
    }
    class AttributeWrapper["AttributeWrapper&lt;T, V&gt;"] {
        -Field field
        -WrapperProperty type
        +getValue() V
        +getValue(Object obj) V
        +setValue(Object value)
        +setValue(Object obj, Object value)
    }
    class MethodWrapper["MethodWrapper&lt;T, R&gt;"] {
        -Class[] paramTypes
        -WrapperProperty params
        -WrapperProperty returnType
        -Method method
        +invoke(Object... params) R
        +invokeOnSpecificObject(Object obj, Object... params) R
        +invokeExpectingException(Class~X~ expected, Object obj, Object... params) X
        +getParamTypes() Class[]
    }
    class ConstructorWrapper~T~ {
        ~Class[] paramTypes
        -WrapperProperty params
        ~Constructor~T~ constructor
        +invoke(Object... args) T
        +invokeExpectingException(Class~X~ expected, Object... args) X
        +getParamTypes() Class[]
        +getActualParamTypes() Class[]
    }
    class WrapperProperty~T~ {
        ~T expected
        ~T actual
        ~Existence existence
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
        +severity() int
        +worst(Existence a, Existence b)$ Existence
    }

    ClassWrapper --|> Wrapper
    GenericClassWrapper --|> ClassWrapper
    AttributeWrapper --|> Wrapper
    MethodWrapper --|> Wrapper
    ConstructorWrapper --|> Wrapper

    Wrapper *-- WrapperProperty : name, modifiers
    ClassWrapper *-- WrapperProperty : superClassWrapper, interfaceWrappers
    AttributeWrapper *-- WrapperProperty : type
    MethodWrapper *-- WrapperProperty : params, returnType
    ConstructorWrapper *-- WrapperProperty : params
    WrapperProperty --> Existence : existence
    Wrapper --> ClassWrapper : parentClassWrapper
```

### Framework: test generation, configuration and annotations

```mermaid
classDiagram
    direction LR
    class StructuralLevenshtein {
        <<final>>
        +structuralTestFactory(DetailLevel level, ClassWrapper... classWrappers)$ List~DynamicTest~
        +structuralTestTemplate(List wrappers)$
    }
    class DetailLevel {
        <<enumeration>>
        ONE_FOR_EVERYTHING
        ONE_PER_CLASS
        ONE_PER_MEMBER_CATEGORY
        ONE_PER_MEMBER
    }
    class Utils {
        <<final>>
        +levenshteinDistance(String s1, String s2)$ int
        +levenshteinDistancePercent(String expected, String actual)$ double
        +isNameWithinDeviation(String expected, String actual, int threshold)$ boolean
        +safeCast(Object val, Class castTo)$ Object
        +verifyType(WrapperProperty typeProperty, Class actualType)$
        +isNumericDeviation(Class expected, Class actual)$ boolean
        +canContain(Class actualType, Class expectedType)$ boolean
        +unwrapPrimitive(Class type)$ Class
        +toWrapperType(Class type)$ Class
    }
    class LevenshteinSettings {
        <<final>>
        +getLanguage()$ Language
        +setLanguage(Language language)$
        +setClassNameDeviationThreshold(int percent)$
        +setMethodNameDeviationThreshold(int percent)$
        +setAttributeNameDeviationThreshold(int percent)$
        +reset()$
    }
    class Language {
        <<enumeration>>
        DEUTSCH
        ENGLISH
    }
    class Messages {
        <<enumeration>>
        WRAPPER_DEVIATION
        CLASS_NOT_IMPLEMENTED
        ...
        +get() String
        +format(Object... args) String
    }
    class Invocations {
        <<package-private>>
        ~invoke(Method method, Object target, Object... args)$ Object
        ~newInstance(Constructor ctor, Object... args)$ Object
        ~rethrowIfCritical(Throwable t)$
        ~describe(Throwable t)$ String
    }
    class LevenshteinTest {
        <<annotation>>
    }
    class HiddenLevenshteinTest {
        <<annotation>>
    }
    class ReflectionTestUtils {
        <<Ares 2>>
    }

    StructuralLevenshtein ..> DetailLevel
    LevenshteinSettings ..> Language
    Messages ..> LevenshteinSettings : reads language
    Invocations ..> ReflectionTestUtils : rethrowing invocations

    note for LevenshteinTest "= @Public + @StrictTimeout(5) + @MirrorOutput"
    note for HiddenLevenshteinTest "= @Hidden + @StrictTimeout(5) + @MirrorOutput"
```

### Example exercise: wrappers (`io.github.valentinherrmann.example.tests.wrappers`)

One wrapper per expected class of the student code. Each wrapper declares its members as
`AttributeWrapper`, `MethodWrapper` and `ConstructorWrapper` fields (found by `ClassWrapper` via
reflection for the structural tests) and exposes them through accessors for the behavioural tests.
Expected names and types come from `Constants` (Exercise Variants pattern).

```mermaid
classDiagram
    direction BT
    class ClassWrapper~T~ {
        <<abstract, framework>>
        +getObj(boolean forceNew)* Object
    }
    class DrivableWrapper~T~ {
        -AttributeWrapper maxSpeed
        -MethodWrapper startMethod
        -MethodWrapper getSpeedMethod
        +DrivableWrapper()
        +maxSpeed() AttributeWrapper
        +startMethod() MethodWrapper
        +getSpeedMethod() MethodWrapper
        +getObj(boolean forceNew) Object
    }
    class AbstrWrapper~T~ {
        -AttributeWrapper manufacturer
        -AttributeWrapper year
        -ConstructorWrapper constructor
        -MethodWrapper getManufacturer
        -MethodWrapper getYear
        -MethodWrapper calculateCost
        -MethodWrapper getInfo
        +AbstrWrapper()
        +manufacturer() AttributeWrapper
        +year() AttributeWrapper
        +constructor() ConstructorWrapper
        +getManufacturer() MethodWrapper
        +getYear() MethodWrapper
        +calculateCost() MethodWrapper
        +getInfo() MethodWrapper
        +getObj(boolean forceNew) T
        +setObj(Object obj, boolean force)
    }
    class CarWrapper~T~ {
        -AttributeWrapper price
        -AttributeWrapper speed
        -ConstructorWrapper constructor_full
        -ConstructorWrapper constructor_default
        -MethodWrapper getPrice
        -MethodWrapper start
        -MethodWrapper getSpeed
        -MethodWrapper calculateCost
        -MethodWrapper calculateCostYears
        -MethodWrapper getInfo
        +CarWrapper(ClassWrapper superClassWrapper, ClassWrapper... interfaceWrappers)
        +price() AttributeWrapper
        +speed() AttributeWrapper
        +constructor_full() ConstructorWrapper
        +constructor_default() ConstructorWrapper
        +getPrice() MethodWrapper
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

    note for DrivableWrapper "Wraps the interface Driveable:\nMAX_SPEED, start(), getSpeed()"
    note for AbstrWrapper "Wraps the abstract class AbstractVehicle:\nmanufacturer, year, abstract calculateCost(), getInfo()"
    note for CarWrapper "Wraps Car (extends AbstractVehicle, implements Driveable):\nconstructor and method overloading, overriding"
```

Each wrapper verifies existence, names, types and modifiers of its class with Levenshtein distance
tolerance; see [How Matching Works](matching.md).
