package io.github.valentinherrmann.example.tests.wrappers;

import static io.github.valentinherrmann.example.tests.Constants.*;


import io.github.valentinherrmann.levenshtein.*;

/**
 * Wrapper for the Car concrete class.
 * Simplified version matching the new example structure.
 */
public class CarWrapper<T> extends ClassWrapper<T> {
    
    // Car-specific attributes
    private final AttributeWrapper<T, ?> price;
    private final AttributeWrapper<T, ?> speed;

    // Constructors (demonstrates constructor overloading)
    private final ConstructorWrapper<T> constructor_full;
    private final ConstructorWrapper<T> constructor_default;

    // Car-specific methods
    private final MethodWrapper<T, ?> getPrice;

    // Interface methods (from Driveable)
    private final MethodWrapper<T, ?> start;
    private final MethodWrapper<T, ?> getSpeed;

    // Overridden methods
    private final MethodWrapper<T, ?> calculateCost;
    private final MethodWrapper<T, ?> calculateCostYears; // Method overloading
    private final MethodWrapper<T, String> getInfo; // DEMO of generic type usage

    // Getters for attributes
    public AttributeWrapper<T, ?> price() {
        return price;
    }

    public AttributeWrapper<T, ?> speed() {
        return speed;
    }

    // Getters for constructors
    public ConstructorWrapper<T> constructor_full() {
        return constructor_full;
    }

    public ConstructorWrapper<T> constructor_default() {
        return constructor_default;
    }

    // Getters for Car-specific methods
    public MethodWrapper<T, ?> getPrice() {
        return getPrice;
    }

    // Getters for interface methods
    public MethodWrapper<T, ?> startMethod() {
        return start;
    }

    public MethodWrapper<T, ?> getSpeed() {
        return getSpeed;
    }

    // Getters for overridden methods
    public MethodWrapper<T, ?> calculateCost() {
        return calculateCost;
    }

    public MethodWrapper<T, ?> calculateCostYears() {
        return calculateCostYears;
    }

    public MethodWrapper<T, String> getInfo() { // DEMO of generic type usage
        return getInfo;
    }

    // Getters for overridden abstract methods

    public CarWrapper(ClassWrapper<?> superClassWrapper, ClassWrapper<?>... interfaceWrappers) {
        super(concreteClass(),
              BASE_PACKAGE,
              superClassWrapper,
              interfaceWrappers,
              "public");

        // Initialize attributes
        price = new AttributeWrapper<>(
                this,
                concreteClassAttribute(),  // "price"
                priceType(),                            // double.class,
                "private");

        speed = new AttributeWrapper<>(
                this,
                speedAttribute(),   // "speed"
                speedType(),        // double.class
                "private");

        // Initialize constructors (constructor overloading)
        constructor_full = new ConstructorWrapper<>(
                this,
                new Class<?>[]{
                        manufacturerType(),// String.class
                        yearType(),        // int.class
                        priceType()        // double.class
                },
                "public"
        );

        constructor_default = new ConstructorWrapper<>(
                this,
                new Class<?>[]{
                    manufacturerType(), // String.class
                    yearType()          // int.class
                },
                "public"
        );

        // Initialize Car-specific methods
        getPrice = new MethodWrapper<>(
                this,
                getPriceMethodName(),   // "getPrice"
                priceType(),                        // double.class,
                "public"
        );

        start = new MethodWrapper<>(
                this,
                interfaceMethod(), // "start"
                startRetType(),                 // void.class,
                "public"
        );

        getSpeed = new MethodWrapper<>(
                this,
                getSpeedMethodName(),   // "getSpeed"
                speedType(),                        // double.class
                "public"
        );

        // Initialize overridden methods
        calculateCost = new MethodWrapper<>(
                this,
                overwrittenMethod(),    // "calculateCost"
                calcReturnType(),                   // double.class
                "public"
        );

        // Method overloading - same name, different parameters
        calculateCostYears = new MethodWrapper<>(
                this,
                overwrittenMethod(),    // "calculateCost"
                calcReturnType(),                   // double.class
                new Class<?>[]{yearType()},         // int.class
                "public"
        );

        // DEMO of usage without Variant Pattern
        getInfo = new MethodWrapper<>(
                this,
                "getInfo",
                String.class,
                "public"
        );
    }

    @Override
    public Object getObj(boolean forceNew) {
        return getObj(forceNew, constructor_full, "BMW", 2023, 30000.0);
    }
}

