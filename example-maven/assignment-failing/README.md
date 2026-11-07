# Failing example solution

A student solution of the example exercise with deliberate defects. It runs against the same tests as
`assignment/src` and shows the messages the framework gives students. Every defect is marked with
`FAILING EXAMPLE` in the code.

```bash
./mvnw -B verify -Dassignment.dir=assignment-failing      # Maven, fails on purpose
./gradlew test -PassignmentDir=assignment-failing         # Gradle, fails on purpose
```

`DEVIATION` fails the structural test only: the behavioural tests run on the student's element and pass.
`MISSING` and the behavioural defects fail their tests.

| File | Expected | Student code | Shows | Framework reports |
|---|---|---|---|---|
| `Driveable` | `double MAX_SPEED` | `int MAX_SPEED` | attribute type (numeric) | `DEVIATION` |
| `AbstractVehicle` | `protected String manufacturer` | `public String manufacturer` | attribute modifier | `DEVIATION` |
| `AbstractVehicle` | `protected int year` | `protected Integer year` | attribute type (primitive/wrapper) | `DEVIATION` |
| `AbstractVehicle` | `public int getYear()` | `protected long getYear()` | **combined:** return type (numeric) + modifier | `DEVIATION` |
| `Car` | `extends AbstractVehicle implements Driveable` | `extends AbstractVehicle` | missing interface | `MISSING` (class) |
| `Car` | `public void start()` | `void start()` | method modifier (package-private) | `DEVIATION` |
| `Car` | `private double price` | `private Double prize` | **combined:** name typo + type (primitive/wrapper) | `DEVIATION` |
| `Car` | `public double calculateCost(int)` | `public double calculateCost(long)` | parameter type (numeric) | `DEVIATION` |
| `Car` | `public Car(String, int)` | `protected Car(String, Integer)` | **combined:** parameter type (primitive/wrapper) + modifier | `DEVIATION` |
| `Car` | `setPrice` rejects negative prices | stores them | getter/setter test with condition | `... muss den ungültigen Wert ablehnen ...` |
| `Car` | `getSpeed()` returns `speed` | returns `0.0` | getter/setter test | `... aber getSpeed() liefert 0.0` |
| `Car` | `getInfo()` returns the info | throws | exception in student code | `... hat eine Ausnahme geworfen ...` |

`SandboxControl` is unchanged: the security and timeout self-checks still pass.
