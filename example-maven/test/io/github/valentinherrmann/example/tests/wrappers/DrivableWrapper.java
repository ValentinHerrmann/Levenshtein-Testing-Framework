package io.github.valentinherrmann.example.tests.wrappers;


import io.github.valentinherrmann.levenshtein.*;

import static io.github.valentinherrmann.example.tests.Constants.*;

/**
 * Wrapper for the Driveable interface.
 * Simplified version matching the new example structure.
 */
public class DrivableWrapper<T> extends ClassWrapper<T> {

    private final AttributeWrapper<T, ?> maxSpeed;
    private final MethodWrapper<T, ?> startMethod;
    private final MethodWrapper<T, ?> getSpeedMethod;

    public AttributeWrapper<T, ?> maxSpeed() {
        return maxSpeed;
    }

    public MethodWrapper<T, ?> startMethod() {
        return startMethod;
    }

    public MethodWrapper<T, ?> getSpeedMethod() {
        return getSpeedMethod;
    }

    public DrivableWrapper() {
        super(interfaceName(),
              BASE_PACKAGE,
              "public", "abstract", "interface"
        );

        // Interface constant
        maxSpeed = new AttributeWrapper<>(
                this,
                maxSpeedConstant(), // "MAX_SPEED"
                speedType(),        // double.class,
                "public", "static"
        );

        // Interface methods
        startMethod = new MethodWrapper<>(
                this,
                interfaceMethod(),  // "start"
                startRetType(),     // void.class,
                "public", "abstract"
        );

        getSpeedMethod = new MethodWrapper<>(
                this,
                getSpeedMethodName(), // "getSpeed"
                speedType(), // double.class
                "public", "abstract"
        );
    }

    /**
     * Returns an instance of a Byte Buddy implementation of the interface. Only its {@code default} methods and
     * static members are usable; calling an abstract interface method throws {@link AbstractMethodError}.
     * Test interface behavior through an implementing class (e.g. {@code CarWrapper}) instead.
     *
     * @param forceNew force to create a new object even if member already holds one.
     * @return an instance implementing the interface
     */
    @Override
    public Object getObj(boolean forceNew) {
        return getObj(forceNew, null);
    }
}
