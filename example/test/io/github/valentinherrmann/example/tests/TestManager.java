package io.github.valentinherrmann.example.tests;

import static io.github.valentinherrmann.levenshtein.StructuralLevenshtein.structuralTestFactory;
import static io.github.valentinherrmann.levenshtein.StructuralLevenshtein.DetailLevel.*;
import static org.assertj.core.api.Assertions.*;

import net.bytebuddy.ByteBuddy;
import net.bytebuddy.implementation.FixedValue;
import net.bytebuddy.matcher.ElementMatchers;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import de.tum.cit.ase.ares.api.Policy;
import io.github.valentinherrmann.levenshtein.LevenshteinTest;
import io.github.valentinherrmann.example.tests.wrappers.*;

import java.util.List;

/**
 * Main test orchestrator for the Levenshtein Name Deviation Testing Pattern.
 *
 * <h2>Tutorial: Understanding the Test Manager</h2>
 *
 * <p>This class is the central hub for all tests. It demonstrates how to:</p>
 * <ul>
 *   <li>Initialize wrapper classes for student code</li>
 *   <li>Generate structural tests automatically</li>
 *   <li>Organize behavioral tests by category</li>
 *   <li>Run everything under Ares 2 supervision ({@code @LevenshteinTest} + {@code @Policy})</li>
 * </ul>
 *
 * <h3>1. Wrapper Initialization Pattern</h3>
 * <p>Before running any tests, we need to create wrapper instances that represent
 * the expected structure of student classes. This happens in {@link #beforeAll()}.</p>
 *
 * <pre>{@code
 * // Step 1: Create wrappers in dependency order
 * driveableInterface = new DrivableWrapper<>();              // No dependencies
 * vehicleAbstr = new AbstrWrapper<>();                       // No dependencies
 * carImpl = new CarWrapper<>(vehicleAbstr, driveableInterface); // Depends on both
 *
 * // Why this order matters:
 * // - DrivableWrapper and AbstrWrapper are independent (interface and base class)
 * // - CarWrapper MUST be created after its dependencies because it references them
 * //   via superClassWrapper and interfaceWrappers parameters
 * }</pre>
 *
 * <h3>2. Static Accessor Pattern</h3>
 * <p>Static accessor methods ({@link #vehicleAbstr()}, {@link #carImpl()}, etc.) allow
 * test classes in the {@code test} package to access wrappers easily:</p>
 *
 * <pre>{@code
 * // In TestAbstr.java:
 * import static io.github.valentinherrmann.example.tests.TestManager.*;
 *
 * public static void testGetManufacturer() {
 *     vehicleAbstr().testGetter(
 *         vehicleAbstr().manufacturer(),
 *         vehicleAbstr().getManufacturer()
 *     );
 * }
 * }</pre>
 *
 * <h3>3. Test Organization Strategy</h3>
 * <p>Tests are organized in three layers:</p>
 *
 * <h4>Layer 1: Structural Tests (Automatic)</h4>
 * <p>The {@link #structFactory()} method generates tests automatically:</p>
 * <pre>{@code
 * &#64;TestFactory
 * List<DynamicTest> structFactory() {
 *     return structuralTestFactory(
 *         ONE_PER_MEMBER_CATEGORY,  // Detail level
 *         driveableInterface,        // Classes to verify
 *         vehicleAbstr,
 *         carImpl
 *     );
 * }
 *
 * // This generates tests like:
 * // - structClass[Driveable]
 * // - structAttributes[Driveable]
 * // - structMethods[Driveable]
 * // - structClass[AbstractVehicle]
 * // - structConstructors[AbstractVehicle]
 * // ... and so on
 * }</pre>
 *
 * <h4>Layer 2: Behavioral Test Methods (in test package)</h4>
 * <p>Actual test logic lives in {@code TestAbstr}, {@code TestImpl}, {@code TestInterface}:</p>
 * <pre>{@code
 * // In TestAbstr.java:
 * public static void testConstructorManufacturer() {
 *     Object obj = vehicleAbstr().constructor().invoke("Toyota", 2022);
 *     var manufacturer = vehicleAbstr().getManufacturer().invokeOnSpecificObject(obj);
 *     assertThat(manufacturer).isEqualTo("Toyota");
 * }
 * }</pre>
 *
 * <h4>Layer 3: Test Manager Delegation (this class)</h4>
 * <p>TestManager methods delegate to the test classes. Assertion failures propagate unchanged, so the
 * student sees the original message and the stack trace keeps its cause:</p>
 * <pre>{@code
 * &#64;Test
 * void testAbstrConstructorManufacturer() {
 *     TestAbstr.testConstructorManufacturer();  // Delegate
 * }
 * }</pre>
 *
 * <h3>4. Why This Three-Layer Architecture?</h3>
 * <ul>
 *   <li><b>Separation of Concerns:</b> Test logic (Layer 2) is separate from test execution (Layer 3)</li>
 *   <li><b>Reusability:</b> Test methods can be called from multiple places</li>
 *   <li><b>Security:</b> only this class carries the Ares annotations ({@code @LevenshteinTest}, {@code @Policy});
 *       every helper class is listed in {@code theFollowingClassesAreTestClasses} of {@code SecurityPolicy.yaml}</li>
 *   <li><b>IDE Support:</b> Each test shows up separately in test runners</li>
 * </ul>
 *
 * <h3>5. Test Categories</h3>
 * <p>Tests are grouped by the class they test:</p>
 *
 * <h4>AbstractVehicle Tests (5 tests)</h4>
 * <ul>
 *   <li>{@link #testAbstrConstructorManufacturer()} - Constructor initialization</li>
 *   <li>{@link #testAbstrConstructorYear()} - Constructor initialization</li>
 *   <li>{@link #testAbstrGetManufacturer()} - Getter method</li>
 *   <li>{@link #testAbstrGetYear()} - Getter method</li>
 *   <li>{@link #testAbstrGetInfo()} - Business logic method</li>
 * </ul>
 *
 * <h4>Car Implementation Tests (8 tests)</h4>
 * <ul>
 *   <li>{@link #testImplConstructorFull()} - Full constructor</li>
 *   <li>{@link #testImplConstructorDefault()} - Constructor overloading</li>
 *   <li>{@link #testImplStart()} - Interface method implementation</li>
 *   <li>{@link #testImplGetSpeed()} - Interface method implementation</li>
 *   <li>{@link #testImplCalculateCost()} - Abstract method implementation</li>
 *   <li>{@link #testImplCalculateCostWithYears()} - Method overloading</li>
 *   <li>{@link #testImplGetInfo()} - Method overriding</li>
 *   <li>{@link #testImplGetPrice()} - Getter method</li>
 * </ul>
 *
 * <h4>Interface Tests (1 test)</h4>
 * <ul>
 *   <li>{@link #testInterfaceMaxSpeedConstant()} - Interface constant</li>
 * </ul>
 *
 * <h3>6. ByteBuddy Integration Test</h3>
 * <p>The {@link #testBytebuddy()} method verifies ByteBuddy is working correctly.
 * This is important because the framework uses ByteBuddy to test abstract classes:</p>
 *
 * <pre>{@code
 * &#64;Test
 * void testBytebuddy() {
 *     // Creates a dynamic subclass of Object
 *     // Verifies ByteBuddy can generate classes at runtime
 *     // If this fails, abstract class testing won't work
 * }
 * }</pre>
 *
 * <h3>7. Usage Example: Adding a New Test</h3>
 *
 * <p><b>Step 1:</b> Add test logic to appropriate test class (e.g., TestImpl.java):</p>
 * <pre>{@code
 * public static void testNewFeature() {
 *     Object car = carImpl().getObj(true);
 *     // ... test logic ...
 *     assertThat(result).isEqualTo(expected);
 * }
 * }</pre>
 *
 * <p><b>Step 2:</b> Add delegation method to TestManager:</p>
 * <pre>{@code
 * &#64;Test
 * void testImplNewFeature() {
 *     TestImpl.testNewFeature();
 * }
 * }</pre>
 *
 * <h3>8. Common Patterns</h3>
 *
 * <h4>Testing Constructors:</h4>
 * <pre>{@code
 * Object obj = carImpl().constructor_full().invoke("BMW", 2023, 35000.0);
 * Object price = carImpl().price().getValue(obj);
 * assertThat(price).isEqualTo(35000.0);
 * }</pre>
 *
 * <h4>Testing Methods:</h4>
 * <pre>{@code
 * double cost = carImpl().calculateCost().invoke();
 * assertThat(cost).isEqualTo(expectedCost);
 * }</pre>
 *
 * <h4>Testing Getters:</h4>
 * <pre>{@code
 * carImpl().testGetter(carImpl().price(), carImpl().getPrice());
 * }</pre>
 *
 * @see io.github.valentinherrmann.example.tests.wrappers.CarWrapper
 * @see io.github.valentinherrmann.example.tests.wrappers.AbstrWrapper
 * @see io.github.valentinherrmann.example.tests.wrappers.DrivableWrapper
 * @see io.github.valentinherrmann.levenshtein.StructuralLevenshtein
 */
@LevenshteinTest
@Policy(value = "test/SecurityPolicy.yaml", withinPath = "classes/io/github/valentinherrmann/example/vehicles")
public class TestManager {

    /**
     * Wrapper for the AbstractVehicle abstract class.
     * Initialized in {@link #beforeAll()} and accessed via {@link #vehicleAbstr()}.
     */
    static AbstrWrapper<?> vehicleAbstr;

    /**
     * Wrapper for the Car concrete class.
     * Initialized in {@link #beforeAll()} and accessed via {@link #carImpl()}.
     */
    static CarWrapper<?> carImpl;

    /**
     * Wrapper for the Driveable interface.
     * Initialized in {@link #beforeAll()} and accessed via {@link #driveableInterface()}.
     */
    static DrivableWrapper<?> driveableInterface;

    /**
     * Static accessor for the AbstractVehicle wrapper.
     * Allows test classes to access the wrapper using static imports.
     *
     * <p><b>Usage:</b></p>
     * <pre>{@code
     * import static io.github.valentinherrmann.example.tests.TestManager.*;
     *
     * vehicleAbstr().manufacturer().getValue();
     * }</pre>
     *
     * @return the AbstractVehicle wrapper instance
     */
    public static AbstrWrapper<?> vehicleAbstr() {
        return vehicleAbstr;
    }

    /**
     * Static accessor for the Car wrapper.
     * Allows test classes to access the wrapper using static imports.
     *
     * @return the Car wrapper instance
     */
    public static CarWrapper<?> carImpl() {
        return carImpl;
    }

    /**
     * Static accessor for the Driveable wrapper.
     * Allows test classes to access the wrapper using static imports.
     *
     * @return the Driveable wrapper instance
     */
    public static DrivableWrapper<?> driveableInterface() {
        return driveableInterface;
    }

    /**
     * Initializes all wrapper instances before any tests run.
     *
     * <p><b>Initialization Order:</b> Wrappers must be created in dependency order:</p>
     * <ol>
     *   <li>Interface wrappers (no dependencies)</li>
     *   <li>Base class wrappers (no dependencies)</li>
     *   <li>Implementation wrappers (depend on interface and base class)</li>
     * </ol>
     *
     * <p><b>Example:</b></p>
     * <pre>{@code
     * driveableInterface = new DrivableWrapper<>();  // Step 1: Interface
     * vehicleAbstr = new AbstrWrapper<>();           // Step 2: Base class
     * carImpl = new CarWrapper<>(                    // Step 3: Implementation
     *     vehicleAbstr,         // superClassWrapper
     *     driveableInterface    // interfaceWrappers
     * );
     * }</pre>
     */
    @BeforeAll
    static void beforeAll() {
        driveableInterface = new DrivableWrapper<>();
        vehicleAbstr = new AbstrWrapper<>();
        carImpl = new CarWrapper<>(vehicleAbstr, driveableInterface);
    }

    /**
     * Compilation and setup verification test.
     *
     * <p>This test ensures that:</p>
     * <ul>
     *   <li>All wrapper classes are properly initialized</li>
     *   <li>No compilation errors occurred</li>
     *   <li>Wrapper instances are of correct types</li>
     * </ul>
     *
     * <p><b>Why this test matters:</b> If this test fails, it indicates a fundamental
     * problem with the test setup itself, not the student code.</p>
     */
    @Test
    void testCompilationAndSetup() {
        // This test will always pass if the code compiles successfully
        // and all wrappers are initialized correctly.
        assertThat(vehicleAbstr).isNotNull();
        assertThat(vehicleAbstr).isInstanceOf(AbstrWrapper.class);
        assertThat(carImpl).isNotNull();
        assertThat(carImpl).isInstanceOf(CarWrapper.class);
        assertThat(driveableInterface).isNotNull();
        assertThat(driveableInterface).isInstanceOf(DrivableWrapper.class);

    }

    /**
     * ByteBuddy integration verification test.
     *
     * <p>Verifies that ByteBuddy is working correctly by creating a simple dynamic class.
     * This is crucial because the framework uses ByteBuddy to test abstract classes.</p>
     *
     * <p><b>What this test does:</b></p>
     * <pre>{@code
     * // 1. Creates a dynamic subclass of Object
     * // 2. Overrides toString() to return "Hello World!"
     * // 3. Instantiates the dynamic class
     * // 4. Verifies toString() returns expected value
     * }</pre>
     *
     * <p><b>If this test fails:</b> ByteBuddy is not working, which means testing
     * abstract classes (like AbstractVehicle) will fail. Check:</p>
     * <ul>
     *   <li>Byte Buddy version in the pom.xml (it must support the Java version of the class files)</li>
     *   <li>Java version compatibility</li>
     *   <li>ClassLoader permissions</li>
     * </ul>
     *
     * @throws InstantiationException if dynamic class cannot be instantiated
     * @throws IllegalAccessException if constructor is not accessible
     */
    @Test
    void testBytebuddy() throws ReflectiveOperationException {
        Class<?> dynamicType = new ByteBuddy()
                .subclass(Object.class)
                .method(ElementMatchers.named("toString"))
                .intercept(FixedValue.value("Hello World!"))
                .make()
                .load(getClass().getClassLoader())
                .getLoaded();
        assertThat(dynamicType.getDeclaredConstructor().newInstance().toString()).isEqualTo("Hello World!");
    }

    /**
     * Structural test factory using JUnit 5 DynamicTest feature.
     *
     * <p>This method automatically generates structural verification tests for all wrapped classes.
     * Tests are organized by {@code ONE_PER_MEMBER_CATEGORY}, which creates separate test methods for:</p>
     * <ul>
     *   <li><b>Class structure</b> - Verifies class name, modifiers, superclass, interfaces</li>
     *   <li><b>Constructors</b> - Verifies all constructors with correct parameter types</li>
     *   <li><b>Attributes</b> - Verifies all fields with correct types and modifiers</li>
     *   <li><b>Methods</b> - Verifies all methods with correct signatures</li>
     * </ul>
     *
     * <p><b>Example output in test runner:</b></p>
     * <pre>
     * ✓ structClass[Driveable]
     * ✓ structAttributes[Driveable]
     * ✓ structMethods[Driveable]
     * ✓ structClass[AbstractVehicle]
     * ✓ structConstructors[AbstractVehicle]
     * ✓ structAttributes[AbstractVehicle]
     * ✓ structMethods[AbstractVehicle]
     * ✗ structAttributes[Car] - "price" expected but found "pric" (DEVIATES)
     * </pre>
     *
     * <p><b>Detail Level Options:</b></p>
     * <ul>
     *   <li>{@code ONE_FOR_EVERYTHING} - Single test for all classes (less detail)</li>
     *   <li>{@code ONE_PER_CLASS} - One test per class (moderate detail)</li>
     *   <li>{@code ONE_PER_MEMBER_CATEGORY} - Separate tests for constructors/attributes/methods (high detail, recommended)</li>
     *   <li>{@code ONE_PER_MEMBER} - Individual test per member (very high detail, not yet implemented)</li>
     * </ul>
     *
     * @return list of dynamically generated JUnit tests
     * @see io.github.valentinherrmann.levenshtein.StructuralLevenshtein#structuralTestFactory
     */
    @TestFactory
    List<DynamicTest> structFactory() {
        return structuralTestFactory(
            ONE_PER_MEMBER_CATEGORY,
            driveableInterface,
            vehicleAbstr,
            carImpl
        );
    }

    // ============================================================================
    // AbstractVehicle Tests (5 tests)
    // ============================================================================

    /**
     * Tests that the AbstractVehicle constructor correctly initializes the manufacturer attribute.
     *
     * <p><b>What this tests:</b></p>
     * <ul>
     *   <li>Constructor accepts manufacturer parameter</li>
     *   <li>Manufacturer value is stored in the attribute</li>
     *   <li>Value can be retrieved via getter method</li>
     * </ul>
     *
     * <p><b>Example:</b> {@code new AbstractVehicle("Toyota", 2022)} should set manufacturer to "Toyota".</p>
     *
     * @see TestAbstr#testConstructorManufacturer()
     */
    @Test
    void testAbstrConstructorManufacturer() {
        TestAbstr.testConstructorManufacturer();
    }

    /**
     * Tests that the AbstractVehicle constructor correctly initializes the year attribute.
     *
     * <p><b>What this tests:</b></p>
     * <ul>
     *   <li>Constructor accepts year parameter</li>
     *   <li>Year value is stored in the attribute</li>
     *   <li>Value matches constructor argument</li>
     * </ul>
     *
     * @see TestAbstr#testConstructorYear()
     */
    @Test
    void testAbstrConstructorYear() {
        TestAbstr.testConstructorYear();
    }

    /**
     * Tests that the getManufacturer() method returns the actual manufacturer attribute value.
     *
     * <p>Uses {@link io.github.valentinherrmann.levenshtein.ClassWrapper#testGetter} to verify
     * getter-attribute consistency.</p>
     *
     * @see TestAbstr#testGetManufacturer()
     */
    @Test
    void testAbstrGetManufacturer() {
        TestAbstr.testGetManufacturer();
    }

    /**
     * Tests that the getYear() method returns the actual year attribute value.
     *
     * <p>Uses {@link io.github.valentinherrmann.levenshtein.ClassWrapper#testGetter} to verify
     * getter-attribute consistency.</p>
     *
     * @see TestAbstr#testGetYear()
     */
    @Test
    void testAbstrGetYear() {
        TestAbstr.testGetYear();
    }

    /**
     * Tests that the getInfo() method returns properly formatted vehicle information.
     *
     * <p><b>Expected format:</b> Should contain both manufacturer and year.</p>
     * <p><b>Example:</b> "BMW (2023)"</p>
     *
     * @see TestAbstr#testGetInfo()
     */
    @Test
    void testAbstrGetInfo() {
        TestAbstr.testGetInfo();
    }

    // ============================================================================
    // Car Implementation Tests (8 tests)
    // ============================================================================

    /**
     * Tests the full Car constructor with all parameters.
     *
     * <p><b>Constructor signature:</b> {@code Car(String manufacturer, int year, double price)}</p>
     *
     * <p><b>What this tests:</b></p>
     * <ul>
     *   <li>All three parameters are accepted</li>
     *   <li>Inherited attributes (manufacturer, year) are initialized via super()</li>
     *   <li>Car-specific attribute (price) is initialized</li>
     * </ul>
     *
     * @see TestImpl#testConstructorFull()
     */
    @Test
    void testImplConstructorFull() {
        TestImpl.testConstructorFull();
    }

    /**
     * Tests the default Car constructor demonstrating constructor overloading.
     *
     * <p><b>Constructor signature:</b> {@code Car(String manufacturer, int year)}</p>
     * <p><b>Expected behavior:</b> Should call {@code this(manufacturer, year, 20000.0)}</p>
     *
     * <p>This tests the OOP concept of <b>constructor overloading</b>.</p>
     *
     * @see TestImpl#testConstructorDefault()
     */
    @Test
    void testImplConstructorDefault() {
        TestImpl.testConstructorDefault();
    }

    /**
     * Tests the start() method from the Driveable interface.
     *
     * <p><b>Expected behavior:</b> Calling start() should set speed to 10.0</p>
     *
     * <p>This tests interface method implementation.</p>
     *
     * @see TestImpl#testStart()
     */
    @Test
    void testImplStart() {
        TestImpl.testStart();
    }

    /**
     * Tests the getSpeed() method from the Driveable interface.
     *
     * <p>Verifies that the getter returns the actual speed attribute value.</p>
     *
     * @see TestImpl#testGetSpeed()
     */
    @Test
    void testImplGetSpeed() {
        TestImpl.testGetSpeed();
    }

    /**
     * Tests the calculateCost() method (no parameters).
     *
     * <p><b>Expected behavior:</b> Should return 10% of the price</p>
     * <p><b>Example:</b> Price 30000.0 → Cost 3000.0</p>
     *
     * <p>This tests implementation of the abstract method from AbstractVehicle.</p>
     *
     * @see TestImpl#testCalculateCost()
     */
    @Test
    void testImplCalculateCost() {
        TestImpl.testCalculateCost();
    }

    /**
     * Tests the calculateCost(int years) method demonstrating method overloading.
     *
     * <p><b>Method signature:</b> {@code double calculateCost(int years)}</p>
     * <p><b>Expected behavior:</b> Should return 10% of price × years</p>
     * <p><b>Example:</b> Price 30000.0, years 5 → Cost 15000.0</p>
     *
     * <p>This tests the OOP concept of <b>method overloading</b> (same name, different parameters).</p>
     *
     * @see TestImpl#testCalculateCostWithYears()
     */
    @Test
    void testImplCalculateCostWithYears() {
        TestImpl.testCalculateCostWithYears();
    }

    /**
     * Tests the getInfo() method demonstrating method overriding.
     *
     * <p><b>Expected behavior:</b> Should call {@code super.getInfo()} and append price information</p>
     * <p><b>Example format:</b> "BMW (2023) - $30000.0"</p>
     *
     * <p>This tests the OOP concept of <b>method overriding</b> (overriding inherited concrete method).</p>
     *
     * @see TestImpl#testGetInfo()
     */
    @Test
    void testImplGetInfo() {
        TestImpl.testGetInfo();
    }

    /**
     * Tests the getPrice() method.
     *
     * <p>Verifies that the getter returns the actual price attribute value.</p>
     *
     * @see TestImpl#testGetPrice()
     */
    @Test
    void testImplGetPrice() {
        TestImpl.testGetPrice();
    }

    // ============================================================================
    // Interface Tests (1 test)
    // ============================================================================

    /**
     * Tests the MAX_SPEED interface constant from Driveable.
     *
     * <p><b>Expected value:</b> 200.0</p>
     * <p><b>Access:</b> {@code Driveable.MAX_SPEED}</p>
     *
     * <p>This tests the OOP concept of <b>interface constants</b> (public static final by default).</p>
     *
     * @see TestInterface#testMaxSpeedConstant()
     */
    @Test
    void testInterfaceMaxSpeedConstant() {
        TestInterface.testMaxSpeedConstant();
    }
}

