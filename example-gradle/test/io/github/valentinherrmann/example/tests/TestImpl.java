package io.github.valentinherrmann.example.tests;

import static io.github.valentinherrmann.levenshtein.Utils.safeCast;
import static io.github.valentinherrmann.example.tests.TestManager.*;

import org.assertj.core.api.Assertions;

/**
 * Behavioral tests for the Car implementation class.
 *
 * <h2>Tutorial: Testing Concrete Implementation Classes</h2>
 *
 * <p>This class demonstrates how to test concrete classes that:</p>
 * <ul>
 *   <li>Extend abstract base classes ({@code AbstractVehicle})</li>
 *   <li>Implement interfaces ({@code Driveable})</li>
 *   <li>Demonstrate OOP concepts (overloading, overriding, inheritance)</li>
 * </ul>
 *
 * <h3>1. Key Differences from Testing Abstract Classes</h3>
 *
 * <table border="1">
 *   <tr>
 *     <th>Aspect</th>
 *     <th>Abstract Class (TestAbstr)</th>
 *     <th>Concrete Class (TestImpl)</th>
 *   </tr>
 *   <tr>
 *     <td><b>ByteBuddy</b></td>
 *     <td>Always (a Byte Buddy subclass is created automatically)</td>
 *     <td>Never (the class's own constructor is used)</td>
 *   </tr>
 *   <tr>
 *     <td><b>Instance Creation</b></td>
 *     <td>{@code constructor().invoke(...)}</td>
 *     <td>{@code constructor().invoke(...)} or {@code getObj()}</td>
 *   </tr>
 *   <tr>
 *     <td><b>Test Focus</b></td>
 *     <td>Constructors, concrete methods only</td>
 *     <td>Full functionality + inherited + interface methods</td>
 *   </tr>
 * </table>
 *
 * <h3>2. Test Coverage for Car Implementation</h3>
 *
 * <p>This class provides 8 tests covering all OOP concepts:</p>
 *
 * <h4>Constructor Testing (2 tests)</h4>
 * <ul>
 *   <li>{@link #testConstructorFull()} - Full constructor with all parameters</li>
 *   <li>{@link #testConstructorDefault()} - <b>Constructor overloading</b> demonstration</li>
 * </ul>
 *
 * <h4>Interface Implementation (2 tests)</h4>
 * <ul>
 *   <li>{@link #testStart()} - {@code start()} from Driveable interface</li>
 *   <li>{@link #testGetSpeed()} - {@code getSpeed()} from Driveable interface</li>
 * </ul>
 *
 * <h4>Method Overriding & Overloading (3 tests)</h4>
 * <ul>
 *   <li>{@link #testCalculateCost()} - Implements abstract method from AbstractVehicle</li>
 *   <li>{@link #testCalculateCostWithYears()} - <b>Method overloading</b> demonstration</li>
 *   <li>{@link #testGetInfo()} - <b>Method overriding</b> (overrides concrete method)</li>
 * </ul>
 *
 * <h4>Getter Testing (1 test)</h4>
 * <ul>
 *   <li>{@link #testGetPrice()} - Simple getter test using {@code testGetter()}</li>
 * </ul>
 *
 * <h3>3. Testing Constructor Overloading</h3>
 *
 * <p><b>Concept:</b> Multiple constructors with different parameter sets:</p>
 * <pre>{@code
 * // Constructor 1: Full parameters
 * public Car(String manufacturer, int year, double price) { ... }
 *
 * // Constructor 2: Default price (constructor overloading)
 * public Car(String manufacturer, int year) {
 *     this(manufacturer, year, 20000.0);  // Delegates to constructor 1
 * }
 * }</pre>
 *
 * <p><b>Testing approach:</b></p>
 * <pre>{@code
 * // Test 1: Full constructor
 * Object car1 = carImpl().constructor_full().invoke("BMW", 2023, 35000.0);
 * assertThat(carImpl().price().getValue(car1)).isEqualTo(35000.0);
 *
 * // Test 2: Default constructor
 * Object car2 = carImpl().constructor_default().invoke("BMW", 2023);
 * assertThat(carImpl().price().getValue(car2)).isEqualTo(20000.0);  // Default!
 * }</pre>
 *
 * <h3>4. Testing Method Overloading</h3>
 *
 * <p><b>Concept:</b> Same method name, different parameters:</p>
 * <pre>{@code
 * // Method 1: No parameters
 * public double calculateCost() {
 *     return price * 0.1;
 * }
 *
 * // Method 2: With years parameter (method overloading)
 * public double calculateCost(int years) {
 *     return price * 0.1 * years;
 * }
 * }</pre>
 *
 * <p><b>Testing approach:</b></p>
 * <pre>{@code
 * // Test both overloaded versions
 * double annual = carImpl().calculateCost().invoke();        // No params
 * double total = carImpl().calculateCostYears().invoke(5);   // With years
 *
 * assertThat(annual).isEqualTo(3000.0);   // 30000 * 0.1
 * assertThat(total).isEqualTo(15000.0);   // 30000 * 0.1 * 5
 * }</pre>
 *
 * <h3>5. Testing Method Overriding</h3>
 *
 * <p><b>Concept:</b> Overriding inherited concrete methods:</p>
 * <pre>{@code
 * // In AbstractVehicle:
 * public String getInfo() {
 *     return manufacturer + " (" + year + ")";
 * }
 *
 * // In Car (overriding):
 * &#64;Override
 * public String getInfo() {
 *     return super.getInfo() + " - $" + price;  // Extends parent behavior
 * }
 * }</pre>
 *
 * <p><b>Testing approach:</b></p>
 * <pre>{@code
 * String info = carImpl().getInfo().invoke();
 *
 * // Should contain BOTH parent info AND Car-specific info
 * assertThat(info).contains("BMW", "2023", "30000");
 * }</pre>
 *
 * <h3>6. Important Testing Patterns</h3>
 *
 * <h4>Pattern 1: Testing Inherited Attributes</h4>
 * <pre>{@code
 * // Car extends AbstractVehicle, so it has manufacturer and year
 * Object car = carImpl().constructor_full().invoke("BMW", 2023, 35000.0);
 *
 * // Access inherited attribute through parent wrapper
 * Object manufacturer = vehicleAbstr().manufacturer().getValue(car);
 * assertThat(manufacturer).isEqualTo("BMW");
 * }</pre>
 *
 * <h4>Pattern 2: Testing Interface Implementation</h4>
 * <pre>{@code
 * // Car implements Driveable, must provide start() and getSpeed()
 * Object car = carImpl().getObj(true);
 *
 * carImpl().startMethod().invokeOnSpecificObject(car);  // Call start()
 * double speed = carImpl().speed().getValue(car);       // Check speed was set
 * assertThat(speed).isEqualTo(10.0);
 * }</pre>
 *
 * <h4>Pattern 3: Using getObj() vs constructor().invoke()</h4>
 * <pre>{@code
 * // Option A: Use wrapper's default instance (convenience)
 * Object car = carImpl().getObj(true);  // Uses default values from wrapper
 *
 * // Option B: Create with specific values (control)
 * Object car = carImpl().constructor_full().invoke("Toyota", 2022, 25000.0);
 *
 * // Choose based on whether you need specific test values
 * }</pre>
 *
 * @see io.github.valentinherrmann.example.tests.wrappers.CarWrapper
 * @see io.github.valentinherrmann.example.tests.TestManager
 * @see TestAbstr
 */
public class TestImpl {

    /**
     * Tests the full Car constructor with all parameters.
     *
     * <p><b>Constructor signature:</b></p>
     * <pre>{@code
     * public Car(String manufacturer, int year, double price)
     * }</pre>
     *
     * <p><b>What this test verifies:</b></p>
     * <ol>
     *   <li><b>Inherited attributes:</b> manufacturer and year (from AbstractVehicle)</li>
     *   <li><b>Car-specific attribute:</b> price</li>
     *   <li><b>Super constructor call:</b> {@code super(manufacturer, year)} works correctly</li>
     * </ol>
     *
     * <p><b>Key Technique:</b> Accessing inherited attributes:</p>
     * <pre>{@code
     * Object car = carImpl().constructor_full().invoke("BMW", 2023, 35000.0);
     *
     * // Inherited attribute - access via PARENT wrapper
     * Object manufacturer = vehicleAbstr().manufacturer().getValue(car);
     * //                    ^^^^^^^^^^^^^^ - Use AbstrWrapper, not CarWrapper
     *
     * // Car-specific attribute - access via CAR wrapper
     * Object price = carImpl().price().getValue(car);
     * //             ^^^^^^^^^^ - Use CarWrapper
     * }</pre>
     *
     * <p><b>Why use parent wrapper for inherited attributes?</b></p>
     * <p>Because {@code manufacturer} is defined in AbstractVehicle, not Car.
     * The wrapper hierarchy mirrors the class hierarchy!</p>
     *
     * <p><b>Important Note:</b> Always store constructor result!</p>
     * <pre>{@code
     * // CORRECT: Store the result
     * Object car = carImpl().constructor_full().invoke(...);
     * carImpl().price().getValue(car);  // Use the specific instance
     *
     * // WRONG: Constructor result lost
     * carImpl().constructor_full().invoke(...);  // Result discarded!
     * carImpl().price().getValue();  // Gets from CACHED instance, not new one
     * }</pre>
     */
    public static void testConstructorFull() {
        try {
            /*
            A ClassWrapper's obj attribute can be overwritten anytime. When needing a
            specific Object store it yourself!
            */
            Object obj = carImpl().constructor_full().invoke("BMW", 2023, 35000.0);

            // Verify inherited attributes
            Object manufacturer = vehicleAbstr().manufacturer().getValue(obj);
            Assertions.assertThat(manufacturer)
                .withFailMessage("Inherited attribute %s must be initialized correctly.",
                               vehicleAbstr().manufacturer().getExpectedName())
                .isEqualTo("BMW");

            // Verify Car-specific attributes
            Object price = carImpl().price().getValue(obj);
            Assertions.assertThat((double)price)
                .withFailMessage("Attribute %s must be equal to constructor argument.",
                               carImpl().price().getExpectedName())
                .isEqualTo(35000.0, Assertions.within(0.001));
        }
        catch (Exception e) {
            Assertions.fail("Invoking constructor of %s caused an exception: %s",
                    carImpl().getExpectedName(), e.getMessage());
        }
    }
    
    /**
     * Tests the default Car constructor demonstrating constructor overloading.
     *
     * <p><b>OOP Concept:</b> Constructor Overloading</p>
     * <p>Two constructors with different parameter lists:</p>
     * <ul>
     *   <li>{@code Car(String, int, double)} - Full constructor</li>
     *   <li>{@code Car(String, int)} - Delegates to full with default price</li>
     * </ul>
     *
     * <p><b>Expected implementation:</b></p>
     * <pre>{@code
     * public Car(String manufacturer, int year) {
     *     this(manufacturer, year, 20000.0);  // Calls full constructor
     * }
     * }</pre>
     *
     * <p><b>Test verification:</b> Price should be set to default value 20000.0</p>
     */
    public static void testConstructorDefault() {
        Object car = carImpl().constructor_default().invoke("BMW", 2023);

        Assertions.assertThat(car)
            .withFailMessage("Constructor with default price should create valid object.")
            .isNotNull();

        // Price should be default value (20000.0)
        Object price = carImpl().price().getValue(car);
        Assertions.assertThat((double)price)
            .withFailMessage("Default constructor should set price to 20000.0")
            .isEqualTo(20000.0, Assertions.within(0.001));
    }

    /**
     * Tests the start() method from Driveable interface.
     *
     * <p><b>OOP Concept:</b> Interface Implementation</p>
     * <p>Car must implement {@code void start()} from Driveable.</p>
     *
     * <p><b>Expected behavior:</b> Calling start() sets speed to 10.0</p>
     *
     * <p><b>Pattern:</b> Test state change after method call:</p>
     * <pre>{@code
     * carImpl().startMethod().invoke();        // Call start()
     * double speed = carImpl().speed().getValue();  // Check side effect
     * assertThat(speed).isEqualTo(10.0);
     * }</pre>
     */
    public static void testStart() {
        Object obj = carImpl().getObj(true);

        carImpl().startMethod().invokeOnSpecificObject(obj);
        Object speed = carImpl().speed().getValue(obj);

        Assertions.assertThat((double)speed)
            .withFailMessage("Speed should be set after calling %s.",
                           carImpl().startMethod().getExpectedName())
            .isEqualTo(10.0, Assertions.within(0.001));
    }
    
    /**
     * Tests getSpeed() method - another interface implementation.
     * Uses {@code testGetter()} for getter-attribute consistency check.
     */
    public static void testGetSpeed() {
        carImpl().testGetter(carImpl().speed(), carImpl().getSpeed());
    }

    /**
     * Tests calculateCost() - implements abstract method from AbstractVehicle.
     *
     * <p><b>OOP Concept:</b> Abstract Method Implementation</p>
     * <p><b>Expected:</b> Returns 10% of price (30000 × 0.1 = 3000)</p>
     */
    public static void testCalculateCost() {
        Object obj = carImpl().getObj(true);
        double cost = (double)safeCast(carImpl().calculateCost().invokeOnSpecificObject(obj),double.class);
        double val = (double)safeCast(carImpl().price().getValue(carImpl().getObj(false)),double.class);
        
        // Cost should be 10% of price (35000 * 0.1 = 3500)
        Assertions.assertThat(cost)
            .withFailMessage("Cost should be 10%% of the price.")
            .isEqualTo(val*0.1, Assertions.within(0.001));
    }
    
    /**
     * Tests calculateCost(int years) - demonstrates method overloading.
     *
     * <p><b>OOP Concept:</b> Method Overloading</p>
     * <p>Same name, different parameters:</p>
     * <ul>
     *   <li>{@code calculateCost()} - annual cost</li>
     *   <li>{@code calculateCost(int years)} - total cost for multiple years</li>
     * </ul>
     *
     * <p><b>Expected:</b> price × 0.1 × years (30000 × 0.1 × 5 = 15000)</p>
     */
    public static void testCalculateCostWithYears() {
        Object obj = carImpl().getObj(true);
        Object cost = carImpl().calculateCostYears().invokeOnSpecificObject(obj, 5);
        Assertions.assertThat(cost)
            .withFailMessage("Method %s with years parameter should return a valid cost value.",
                           carImpl().calculateCostYears().getExpectedName())
            .isNotNull();
        
        // Cost should be 10% of price * years (35000 * 0.1 * 5 = 17500)
        Assertions.assertThat((double)cost)
            .withFailMessage("Cost for 5 years should be correct.")
            .isEqualTo(15000.0, Assertions.within(0.001));
    }
    
    /**
     * Tests getInfo() - demonstrates method overriding.
     *
     * <p><b>OOP Concept:</b> Method Overriding</p>
     * <p>Car overrides AbstractVehicle's concrete method:</p>
     * <pre>{@code
     * // AbstractVehicle:
     * public String getInfo() { return manufacturer + " (" + year + ")"; }
     *
     * // Car:
     * &#64;Override
     * public String getInfo() { return super.getInfo() + " - $" + price; }
     * }</pre>
     *
     * <p><b>Expected:</b> Should contain manufacturer, year, AND price</p>
     * <p><b>Example:</b> "BMW (2023) - $30000.0"</p>
     */
    public static void testGetInfo() {
        String info = carImpl().getInfo().invoke();
        Assertions.assertThat(info)
            .withFailMessage("Method %s should return vehicle information.",
                           carImpl().getInfo().getExpectedName())
            .isNotNull()
            .contains("BMW", "2023", "30000");
    }
    
    /**
     * Tests getPrice() method using the testGetter() utility.
     * Verifies getter returns the actual price attribute value.
     */
    public static void testGetPrice() {
        carImpl().testGetter(carImpl().price(), carImpl().getPrice());
    }
}

