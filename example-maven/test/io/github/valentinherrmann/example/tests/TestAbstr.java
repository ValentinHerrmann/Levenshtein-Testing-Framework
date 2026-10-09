package io.github.valentinherrmann.example.tests;

import static io.github.valentinherrmann.levenshtein.Utils.*;
import static io.github.valentinherrmann.example.tests.Constants.*;
import static io.github.valentinherrmann.example.tests.TestManager.*;
import static org.assertj.core.api.Assertions.fail;

import org.assertj.core.api.Assertions;

/**
 * Behavioral tests for the AbstractVehicle abstract class.
 *
 * <h2>Tutorial: Testing Abstract Classes</h2>
 *
 * <p>This class demonstrates how to test abstract classes using the Levenshtein pattern.
 * Testing abstract classes requires special handling because they cannot be instantiated directly.</p>
 *
 * <h3>1. The Challenge: Abstract Classes</h3>
 * <p>Abstract classes pose a testing challenge:</p>
 * <pre>{@code
 * // This won't work:
 * AbstractVehicle vehicle = new AbstractVehicle("BMW", 2023); // ERROR: Cannot instantiate
 *
 * // We need a workaround to test the non-abstract parts!
 * }</pre>
 *
 * <h3>2. The Solution: ByteBuddy Dynamic Subclasses</h3>
 * <p>The framework uses ByteBuddy to create concrete subclasses at runtime:</p>
 * <pre>{@code
 * // Behind the scenes, when you call:
 * Object obj = vehicleAbstr().constructor().invoke("Toyota", 2022);
 *
 * // ByteBuddy creates something like:
 * class AbstractVehicle$Generated extends AbstractVehicle {
 *     // Provides default implementations for abstract methods
 *     &#64;Override
 *     public double calculateCost() { return 0.0; }  // Default stub
 * }
 *
 * // Then instantiates it:
 * AbstractVehicle$Generated obj = new AbstractVehicle$Generated("Toyota", 2022);
 * }</pre>
 *
 * <h3>3. Test Organization Pattern</h3>
 * <p>All test methods are {@code public static} to allow delegation from {@link io.github.valentinherrmann.example.tests.TestManager}:</p>
 * <pre>{@code
 * // In TestAbstr.java:
 * public static void testConstructorManufacturer() {
 *     // Test logic here
 * }
 *
 * // In TestManager.java:
 * &#64;Test
 * void testAbstrConstructorManufacturer() {
 *     try {
 *         TestAbstr.testConstructorManufacturer();  // Delegate to static method
 *     } catch (AssertionError e) {
 *         fail(e.getMessage());
 *     }
 * }
 * }</pre>
 *
 * <h3>4. Common Testing Patterns</h3>
 *
 * <h4>Pattern A: Testing Constructors with ByteBuddy</h4>
 * <pre>{@code
 * // Create instance using wrapper's constructor
 * Object obj = vehicleAbstr().constructor().invoke("Toyota", 2022);
 *
 * // Verify attribute was set correctly
 * var manufacturer = vehicleAbstr().getManufacturer().invokeOnSpecificObject(obj);
 * assertThat(manufacturer).isEqualTo("Toyota");
 * }</pre>
 *
 * <h4>Pattern B: Testing Getters with testGetter()</h4>
 * <pre>{@code
 * // This helper method verifies getter-attribute consistency:
 * vehicleAbstr().testGetter(
 *     vehicleAbstr().manufacturer(),      // AttributeWrapper
 *     vehicleAbstr().getManufacturer()    // MethodWrapper
 * );
 *
 * // Internally, it:
 * // 1. Gets attribute value directly
 * // 2. Calls getter method
 * // 3. Asserts they match
 * }</pre>
 *
 * <h4>Pattern C: Testing Business Logic Methods</h4>
 * <pre>{@code
 * // Create instance with specific values
 * Object obj = vehicleAbstr().getObj(true, true,
 *     vehicleAbstr().constructor(),
 *     "BMW", 2023
 * );
 *
 * // Invoke method and verify result
 * String info = vehicleAbstr().getInfo().invokeOnSpecificObject(obj);
 * assertThat(info).contains("BMW", "2023");
 * }</pre>
 *
 * <h3>5. The saveCast() Utility</h3>
 * <p>Used to safely cast reflection results to expected types:</p>
 * <pre>{@code
 * Object manufacturerObj = vehicleAbstr().manufacturer().getValue(obj);
 *
 * // Safe cast with type checking and primitive widening support:
 * String manufacturer = (String) saveCast(manufacturerObj, manufacturerType());
 *
 * // Why saveCast()?
 * // - Handles null values
 * // - Supports primitive widening (int → long, float → double)
 * // - Provides clear error messages
 * }</pre>
 *
 * <h3>6. Understanding getObj() Parameters</h3>
 * <pre>{@code
 * Object obj = vehicleAbstr().getObj(
 *     true,                            // forceNew: create new instance (don't reuse cached)
 *     true,                            // useByteBuddy: use ByteBuddy for abstract class
 *     vehicleAbstr().constructor(),    // which constructor to use
 *     "BMW", 2023                      // constructor arguments
 * );
 * }</pre>
 *
 * <h3>7. Test Coverage for AbstractVehicle</h3>
 * <p>This class provides 5 tests covering:</p>
 * <ul>
 *   <li>{@link #testConstructorManufacturer()} - Constructor initialization (manufacturer)</li>
 *   <li>{@link #testConstructorYear()} - Constructor initialization (year)</li>
 *   <li>{@link #testGetManufacturer()} - Getter method correctness</li>
 *   <li>{@link #testGetYear()} - Getter method correctness</li>
 *   <li>{@link #testGetInfo()} - Business logic method</li>
 * </ul>
 *
 * @see io.github.valentinherrmann.example.tests.wrappers.AbstrWrapper
 * @see io.github.valentinherrmann.example.tests.TestManager
 * @see io.github.valentinherrmann.levenshtein.ClassWrapper#getDynamicSubclassObj
 */
public class TestAbstr {
    
    /**
     * Tests that the AbstractVehicle constructor correctly initializes the manufacturer attribute.
     *
     * <p><b>Testing Approach:</b></p>
     * <ol>
     *   <li>Create instance using constructor wrapper with "Toyota" as manufacturer</li>
     *   <li>Invoke getManufacturer() method on that specific instance</li>
     *   <li>Verify returned value matches constructor argument</li>
     * </ol>
     *
     * <p><b>Key Learning:</b> When testing abstract classes, we must:</p>
     * <ul>
     *   <li>Use {@code constructor().invoke(...)} to create ByteBuddy instances</li>
     *   <li>Store the returned object reference for later method calls</li>
     *   <li>Use {@code invokeOnSpecificObject(obj)} to call methods on that specific instance</li>
     * </ul>
     *
     * <p><b>Code Walkthrough:</b></p>
     * <pre>{@code
     * // Step 1: Create instance via ByteBuddy
     * Object obj = vehicleAbstr().constructor().invoke("Toyota", 2022);
     *
     * // Step 2: Call getter on the specific instance
     * var manufacturer = vehicleAbstr()
     *     .getManufacturer()                    // Get MethodWrapper
     *     .invokeOnSpecificObject(obj);         // Invoke on our instance
     *
     * // Step 3: Cast and verify
     * var manufacturerStr = saveCast(manufacturer, manufacturerType());
     * assertThat(manufacturerStr).isEqualTo("Toyota");
     * }</pre>
     *
     * <p><b>Why invokeOnSpecificObject()?</b> Because we need to call the method on the
     * specific instance we just created, not a cached default instance.</p>
     *
     * @throws Exception if constructor invocation or method call fails
     */
    public static void testConstructorManufacturer() {
        try {
            Object obj = vehicleAbstr().constructor().invoke("Toyota", 2022);

            var manufacturer = saveCast(vehicleAbstr().getManufacturer().invokeOnSpecificObject(obj), manufacturerType());
            Assertions.assertThat(manufacturer)
                .withFailMessage("Value of attribute %s must be equal to the value passed in constructor.",
                               vehicleAbstr().manufacturer().getExpectedName())
                .isEqualTo("Toyota");
        }
        catch (Exception e) {
            fail("Invoking constructor of %s caused an exception: %s",
                    vehicleAbstr().getExpectedName(), e.getMessage());
        }
    }
    
    /**
     * Tests that the AbstractVehicle constructor correctly initializes the year attribute.
     *
     * <p><b>Simplified Testing Pattern:</b></p>
     * <p>This test uses a simpler approach - it gets the value from the wrapper's default instance:</p>
     * <pre>{@code
     * // The wrapper maintains a default instance (created in beforeAll)
     * var year = vehicleAbstr().year().getValue();  // Gets from default instance
     *
     * // This is equivalent to:
     * Object defaultInstance = vehicleAbstr().getObj(false);  // Get cached instance
     * var year = vehicleAbstr().year().getValue(defaultInstance);
     * }</pre>
     *
     * <p><b>When to use each pattern:</b></p>
     * <ul>
     *   <li><b>Specific instance</b> ({@code testConstructorManufacturer}):
     *       When you need to test with specific constructor arguments</li>
     *   <li><b>Default instance</b> (this test):
     *       When the wrapper's default values are sufficient</li>
     * </ul>
     *
     * <p><b>Default Instance Values:</b></p>
     * <p>The default instance is created in {@code AbstrWrapper.getObj()} with:
     * manufacturer="BMW", year=2023</p>
     */
    public static void testConstructorYear() {
        var year = saveCast(vehicleAbstr().year().getValue(), yearType());
        Assertions.assertThat(year)
            .withFailMessage("Value of attribute %s must be equal to the value passed in constructor.",
                           vehicleAbstr().year().getExpectedName())
            .isEqualTo(2023);
    }
    
    /**
     * Tests that the getManufacturer() method returns the same value as the manufacturer attribute.
     *
     * <p><b>The testGetter() Utility:</b></p>
     * <p>This test uses the {@link io.github.valentinherrmann.levenshtein.ClassWrapper#testGetter} helper method,
     * which is one of the most convenient features of the framework.</p>
     *
     * <p><b>What testGetter() does:</b></p>
     * <pre>{@code
     * vehicleAbstr().testGetter(
     *     vehicleAbstr().manufacturer(),      // AttributeWrapper - the field
     *     vehicleAbstr().getManufacturer()    // MethodWrapper - the getter
     * );
     *
     * // Internally performs:
     * // 1. Object instance = getObj();
     * // 2. Object attributeValue = manufacturer.getValue(instance);
     * // 3. Object getterValue = getManufacturer().invoke();
     * // 4. assertThat(attributeValue).isEqualTo(getterValue);
     * }</pre>
     *
     * <p><b>Why use testGetter():</b></p>
     * <ul>
     *   <li>Automatically creates and uses the same instance for both checks</li>
     *   <li>Handles type casting automatically</li>
     *   <li>Provides clear error messages if values don't match</li>
     *   <li>Reduces boilerplate code</li>
     * </ul>
     *
     * <p><b>Example error message:</b></p>
     * <pre>
     * Getter 'getManufacturer()' does not return the attribute's value.
     * Expected: BMW
     * Actual: toyota
     * </pre>
     *
     * <p><b>Best Practice:</b> Use {@code testGetter()} for all simple getter tests.
     * Only write custom tests when getters have additional logic beyond returning the attribute.</p>
     */
    public static void testGetManufacturer() {
        vehicleAbstr().testGetter(vehicleAbstr().manufacturer(), vehicleAbstr().getManufacturer());
    }
    
    /**
     * Tests that the getYear() method returns the same value as the year attribute.
     *
     * <p>Uses the {@link io.github.valentinherrmann.levenshtein.ClassWrapper#testGetter} utility
     * method for automatic getter-attribute consistency verification.</p>
     *
     * @see #testGetManufacturer() for detailed explanation of testGetter()
     */
    public static void testGetYear() {
        vehicleAbstr().testGetter(vehicleAbstr().year(), vehicleAbstr().getYear());
    }
    
    /**
     * Tests that the getInfo() method returns properly formatted vehicle information.
     *
     * <p><b>Advanced Testing Pattern:</b></p>
     * <p>This test demonstrates several advanced techniques:</p>
     *
     * <h4>1. Creating Instances with Specific Values</h4>
     * <pre>{@code
     * Object obj = vehicleAbstr().getObj(
     *     true,                            // forceNew: always create new instance
     *     true,                            // useByteBuddy: needed for abstract classes
     *     vehicleAbstr().constructor(),    // which constructor to use
     *     "BMW", 2023                      // constructor arguments
     * );
     * }</pre>
     *
     * <h4>2. Retrieving Attribute Values for Comparison</h4>
     * <pre>{@code
     * // Get manufacturer attribute value
     * var man = (String)saveCast(
     *     vehicleAbstr().manufacturer().getValue(obj),  // Get value
     *     manufacturerType()                            // Expected type
     * );
     *
     * // Why saveCast()?
     * // - getValue() returns Object
     * // - saveCast() safely converts to expected type
     * // - Handles primitive widening (int→long, float→double)
     * // - Provides clear errors if cast fails
     * }</pre>
     *
     * <h4>3. Using Generic Return Types</h4>
     * <pre>{@code
     * // Notice: No cast needed here!
     * String info = vehicleAbstr().getInfo().invokeOnSpecificObject(obj);
     *
     * // This works because MethodWrapper is defined with generic return type:
     * // MethodWrapper<T, String> getInfo = new MethodWrapper<>(..., String.class, ...);
     * //                    ^^^^^^ - This generic parameter eliminates the cast
     * }</pre>
     *
     * <h4>4. String Content Assertions</h4>
     * <pre>{@code
     * Assertions.assertThat(info)
     *     .isNotNull()                    // First check: not null
     *     .contains(man, ""+year);        // Then check: contains expected values
     *
     * // Expected format: "BMW (2023)"
     * // Both "BMW" and "2023" must be present
     * }</pre>
     *
     * <p><b>Why This Test Structure?</b></p>
     * <ul>
     *   <li>Tests business logic, not just getters</li>
     *   <li>Verifies formatting/composition of multiple attributes</li>
     *   <li>Uses actual attribute values for comparison (dynamic, not hardcoded)</li>
     *   <li>Demonstrates proper use of generics to avoid casts</li>
     * </ul>
     *
     * <p><b>Common Mistake to Avoid:</b></p>
     * <pre>{@code
     * // DON'T do this:
     * assertThat(info).isEqualTo("BMW (2023)");  // Too strict!
     *
     * // DO this instead:
     * assertThat(info).contains("BMW", "2023");  // Flexible format
     *
     * // Why? Student might format as:
     * // - "BMW (2023)"
     * // - "BMW - 2023"
     * // - "Manufacturer: BMW, Year: 2023"
     * // All valid as long as info is present!
     * }</pre>
     */
    public static void testGetInfo() {
        Object obj = vehicleAbstr().getObj(true, true,
                vehicleAbstr().constructor(),
                "BMW",2023);
        var man = (String)saveCast(vehicleAbstr().manufacturer().getValue(obj),manufacturerType());
        var year = saveCast(vehicleAbstr().year().getValue(obj),yearType());
        String info = vehicleAbstr().getInfo().invokeOnSpecificObject(obj); // DEMO of generic type usage, no need to cast anything
        Assertions.assertThat(info)
            .withFailMessage("Method %s should return vehicle information.",
                           vehicleAbstr().getInfo().getExpectedName())
            .isNotNull()
            .contains(man, ""+year);
    }
}

