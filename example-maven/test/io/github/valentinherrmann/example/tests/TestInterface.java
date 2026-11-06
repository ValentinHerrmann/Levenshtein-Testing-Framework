package io.github.valentinherrmann.example.tests;

import static io.github.valentinherrmann.levenshtein.Utils.safeCast;
import static io.github.valentinherrmann.example.tests.TestManager.*;

import org.assertj.core.api.Assertions;

/**
 * Behavioral tests for the Driveable interface.
 *
 * <h2>Tutorial: Testing Interfaces</h2>
 *
 * <p>This class demonstrates how to test Java interfaces using the Levenshtein pattern.
 * Interfaces present unique testing challenges because they cannot be instantiated and
 * typically contain only method signatures and constants.</p>
 *
 * <h3>1. What Can Be Tested in Interfaces?</h3>
 *
 * <table border="1">
 *   <tr>
 *     <th>Interface Element</th>
 *     <th>How to Test</th>
 *     <th>Example</th>
 *   </tr>
 *   <tr>
 *     <td><b>Constants</b></td>
 *     <td>Direct access (public static final)</td>
 *     <td>{@code Driveable.MAX_SPEED}</td>
 *   </tr>
 *   <tr>
 *     <td><b>Method Signatures</b></td>
 *     <td>Structural tests (via wrapper)</td>
 *     <td>Verify {@code start()} and {@code getSpeed()} exist</td>
 *   </tr>
 *   <tr>
 *     <td><b>Implementation</b></td>
 *     <td>Test via implementing class (Car)</td>
 *     <td>See {@link TestImpl}</td>
 *   </tr>
 * </table>
 *
 * <h3>2. Testing Interface Constants</h3>
 *
 * <p><b>Key Fact:</b> Interface fields are implicitly {@code public static final}:</p>
 * <pre>{@code
 * public interface Driveable {
 *     double MAX_SPEED = 200.0;  // Implicitly: public static final
 * }
 *
 * // Access via interface name (not instance):
 * double max = Driveable.MAX_SPEED;  // ✓ Correct
 * double max = car.MAX_SPEED;        // ✓ Also works (not recommended style)
 * }</pre>
 *
 * <p><b>Testing Pattern:</b></p>
 * <pre>{@code
 * // Step 1: Verify the constant exists (structural test handles this)
 * driveableInterface().maxSpeed().verifyExistence(true);
 *
 * // Step 2: Get the value (pass null - it's static!)
 * Object maxSpeed = driveableInterface().maxSpeed().getValue(null);
 * //                                                         ^^^^ null for static
 *
 * // Step 3: Verify the value
 * double value = (double) safeCast(maxSpeed, double.class);
 * assertThat(value).isEqualTo(200.0);
 * }</pre>
 *
 * <h3>3. Why Pass null to getValue()?</h3>
 *
 * <p>Interface constants are static, so they don't belong to any instance:</p>
 * <pre>{@code
 * // In regular Java:
 * double max = Driveable.MAX_SPEED;  // No instance needed
 *
 * // In wrapper framework:
 * Object max = driveableInterface().maxSpeed().getValue(null);
 * //                                                     ^^^^
 * // null because:
 * // 1. MAX_SPEED is static (belongs to interface, not instance)
 * // 2. getValue() needs an instance parameter for non-static fields
 * // 3. For static fields, we pass null to indicate "use static access"
 * }</pre>
 *
 * <h3>4. Testing Interface Methods (Indirectly)</h3>
 *
 * <p>Interface methods are abstract (no implementation), so we test them via implementing classes:</p>
 *
 * <table border="1">
 *   <tr>
 *     <th>Test Type</th>
 *     <th>What It Checks</th>
 *     <th>Where to Find</th>
 *   </tr>
 *   <tr>
 *     <td><b>Structural</b></td>
 *     <td>Methods exist with correct signatures</td>
 *     <td>Auto-generated in TestManager's &#64;TestFactory method</td>
 *   </tr>
 *   <tr>
 *     <td><b>Behavioral</b></td>
 *     <td>Methods work correctly when implemented</td>
 *     <td>{@link TestImpl#testStart()}, {@link TestImpl#testGetSpeed()}</td>
 *   </tr>
 * </table>
 *
 * <h3>5. Complete Test Coverage for Driveable</h3>
 *
 * <p>This class provides 1 test:</p>
 * <ul>
 *   <li>{@link #testMaxSpeedConstant()} - Verifies MAX_SPEED constant value</li>
 * </ul>
 *
 * <p>Additional coverage from other test classes:</p>
 * <ul>
 *   <li><b>Structural tests:</b> {@code structMethods[Driveable]} verifies start() and getSpeed() signatures</li>
 *   <li><b>Implementation tests:</b> {@link TestImpl} verifies Car implements interface correctly</li>
 * </ul>
 *
 * @see io.github.valentinherrmann.example.tests.wrappers.DrivableWrapper
 * @see TestImpl
 * @see io.github.valentinherrmann.example.tests.TestManager
 */
public class TestInterface {

    /**
     * Tests the MAX_SPEED interface constant.
     *
     * <p><b>What this test verifies:</b></p>
     * <ol>
     *   <li>The constant exists in the interface</li>
     *   <li>It's accessible (public static final)</li>
     *   <li>The value is exactly 200.0</li>
     * </ol>
     *
     * <p><b>Code Walkthrough:</b></p>
     * <pre>{@code
     * // Step 1: Verify existence (structural check)
     * driveableInterface().maxSpeed().verifyExistence(true);
     * //                              ^^^^^^^^^^^^^^^^
     * // This checks:
     * // - Field named "MAX_SPEED" (or similar within deviation) exists
     * // - Type is double (or compatible)
     * // - Modifiers include "public" and "static"
     *
     * // Step 2: Get the actual value
     * Object maxSpeed = driveableInterface().maxSpeed().getValue(null);
     * //                                                         ^^^^
     * // null because MAX_SPEED is static (no instance needed)
     *
     * // Step 3: Safe cast and verify
     * double dMaxSpeed = (double)safeCast(maxSpeed, double.class);
     * // safeCast() handles:
     * // - Null checking
     * // - Type compatibility (e.g., int → double widening)
     * // - Clear error messages
     *
     * // Step 4: Assert the value
     * Assertions.assertThat(dMaxSpeed)
     *     .withFailMessage("MAX_SPEED should be 200.0")
     *     .isEqualTo(200.0, Assertions.within(0.001));
     * }</pre>
     *
     * <p><b>Why 0.001 tolerance?</b> For floating-point comparisons, exact equality
     * can fail due to precision issues. Using {@code within(0.001)} allows tiny
     * rounding differences while catching real errors.</p>
     *
     * <p><b>Common Mistakes to Avoid:</b></p>
     * <pre>{@code
     * // WRONG: Trying to get value from an instance
     * Object car = carImpl().getObj(true);
     * Object maxSpeed = driveableInterface().maxSpeed().getValue(car);  // ✗
     * // This might work, but it's conceptually wrong - MAX_SPEED is static!
     *
     * // CORRECT: Use null for static fields
     * Object maxSpeed = driveableInterface().maxSpeed().getValue(null);  // ✓
     * }</pre>
     */
    public static void testMaxSpeedConstant() {
        driveableInterface().maxSpeed().verifyExistence(true);

        Object maxSpeed = driveableInterface().maxSpeed().getValue(null);
        double dMaxSpeed = (double)safeCast(maxSpeed, double.class);
        Assertions.assertThat(dMaxSpeed)
            .withFailMessage("Interface constant %s should be 200.0",
                           driveableInterface().maxSpeed().getExpectedName())
            .isEqualTo(200.0, Assertions.within(0.001));
    }
}

