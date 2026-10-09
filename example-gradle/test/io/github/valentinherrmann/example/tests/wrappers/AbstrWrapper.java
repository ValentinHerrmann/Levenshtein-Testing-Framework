package io.github.valentinherrmann.example.tests.wrappers;

import io.github.valentinherrmann.levenshtein.*;

import static io.github.valentinherrmann.example.tests.Constants.*;

/**
 * Wrapper for the AbstractVehicle abstract class.
 * Simplified version matching the new example structure.
 */
public class AbstrWrapper<T> extends ClassWrapper<T> {
    
    private final AttributeWrapper<T, ?> manufacturer;
    private final AttributeWrapper<T, ?> year;

    private final ConstructorWrapper<T> constructor;
    
    private final MethodWrapper<T, ?> getManufacturer;
    private final MethodWrapper<T, ?> getYear;
    private final MethodWrapper<T, ?> calculateCost;
    private final MethodWrapper<T, String> getInfo; // DEMO of generic type usage

    // Getters for attributes
    public AttributeWrapper<T, ?> manufacturer() {
        return manufacturer;
    }

    public AttributeWrapper<T, ?> year() {
        return year;
    }

    // Getter for constructor
    public ConstructorWrapper<T> constructor() {
        return constructor;
    }

    // Getters for methods
    public MethodWrapper<T, ?> getManufacturer() {
        return getManufacturer;
    }

    public MethodWrapper<T, ?> getYear() {
        return getYear;
    }

    public MethodWrapper<T, ?> calculateCost() {
        return calculateCost;
    }

    public MethodWrapper<T, String> getInfo() {
        return getInfo;
    }

    public AbstrWrapper() {
        super(abstractClass(),  // "AbstractVehicle"
              BASE_PACKAGE,
              "public", "abstract"
        );

        // Initialize attributes
        manufacturer = new AttributeWrapper<>(
                this,
                abstractClassAttribute(),// "manufacturer"
                manufacturerType(),      // String.class
                "protected"
        );

        year = new AttributeWrapper<>(
                this,
                yearAttribute(),// "year"
                yearType(),     // int.class
                "protected"
        );

        // Initialize constructor
        constructor = new ConstructorWrapper<>(
                this,
                new Class<?>[]{
                    manufacturerType(), // String.class
                    yearType()          // int.class
                },
                "public"
        );

        getManufacturer = new MethodWrapper<>(
                this,
                getManufacturerMethodName(),    // "getManufacturer"
                manufacturerType(),                         // String.class
                "public"
        );

        getYear = new MethodWrapper<>(
                this,
                getYearMethodName(),   // "getYear"
                yearType(),            // int.class
                "public"
        );

        calculateCost = new MethodWrapper<>(
                this,
                overwrittenMethod(), // "calculateCost"
                calcReturnType(),    // double.class
                "public", "abstract"
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
    public T getObj(boolean forceNew) {
        return getObj(forceNew, constructor(), "BMW", 2023);
    }

    /**
     * Caches the given object as default instance (e.g. a Car created in a test), unless one is cached already.
     *
     * @param obj the object to cache
     * @param force overwrite an already cached object
     */
    public void setObj(Object obj, boolean force) {
        if(force || this.obj == null) {
            setCachedObj(obj);
        }
    }
}
