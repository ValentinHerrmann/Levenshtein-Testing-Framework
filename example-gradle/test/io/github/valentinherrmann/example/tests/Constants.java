package io.github.valentinherrmann.example.tests;

import java.io.FileWriter;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

/**
 * Names and types of the expected student code, per exercise variant ("Exercise Variants" pattern).
 * Wrappers read every expected name and type from here, so a new variant only needs new values in this class.
 */
public final class Constants {

    private Constants() {
    }

    public enum Variant {
        DEFAULT
    }

    /**
     * The variant of the exercise being tested.
     */
    public static Variant variant = Variant.DEFAULT;

    /**
     * The package of the student code (the supervised package of SecurityPolicy.yaml).
     */
    public static final String BASE_PACKAGE = "io.github.valentinherrmann.example.vehicles";

    public static String abstractClass(){
        return switch (variant) {
            case DEFAULT -> "AbstractVehicle";
        };
    }

    public static String concreteClass(){
        return switch (variant) {
            case DEFAULT -> "Car";
        };
    }

    public static String concreteClassAttribute() {
        return switch (variant) {
            case DEFAULT -> "price"; // Original: "numberOfDoors"
        };
    }

    public static String interfaceName(){
        return switch (variant) {
            case DEFAULT -> "Driveable";
        };
    }

    public static String overwrittenMethod() {
        return switch (variant) {
            case DEFAULT -> "calculateCost"; // Original: "calculateFuelConsumption"
        };
    }

    public static String abstractClassAttribute() {
        return switch (variant) {
            case DEFAULT -> "manufacturer";
        };
    }

    public static String interfaceMethod() {
        return switch (variant) {
            case DEFAULT -> "start"; // Original: "startEngine"
        };
    }

    // ============================================================================
    // Attribute Name Constants
    // ============================================================================

    public static String yearAttribute() {
        return switch (variant) {
            case DEFAULT -> "year";
        };
    }

    public static String speedAttribute() {
        return switch (variant) {
            case DEFAULT -> "speed";
        };
    }

    public static String maxSpeedConstant() {
        return switch (variant) {
            case DEFAULT -> "MAX_SPEED";
        };
    }

    // ============================================================================
    // Method Name Constants
    // ============================================================================

    public static String getManufacturerMethodName() {
        return switch (variant) {
            case DEFAULT -> "getManufacturer";
        };
    }

    public static String getYearMethodName() {
        return switch (variant) {
            case DEFAULT -> "getYear";
        };
    }

    public static String getPriceMethodName() {
        return switch (variant) {
            case DEFAULT -> "getPrice";
        };
    }

    public static String getSpeedMethodName() {
        return switch (variant) {
            case DEFAULT -> "getSpeed";
        };
    }

    // ============================================================================
    // Type Constants
    // ============================================================================
    public static Class<?> yearType() {
        return switch (variant) {
            case DEFAULT -> int.class; // Used in: AbstractVehicle.year, Constructor parameter
        };
    }

    public static Class<?> speedType() {
        return switch (variant) {
            case DEFAULT -> double.class; // Used in: Car.speed, Driveable.getSpeed(), Car.getSpeed()
        };
    }

    public static Class<?> priceType() {
        return switch (variant) {
            case DEFAULT -> double.class; // Used in: Car.speed, Driveable.getSpeed(), Car.getSpeed()
        };
    }

    public static Class<?> manufacturerType() {
        return switch (variant) {
            case DEFAULT -> String.class; // Used in: AbstractVehicle.manufacturer, AbstractVehicle.getManufacturer()
        };
    }

    public static Class<?> startRetType() {
        return switch (variant) {
            case DEFAULT -> void.class; // Used in: Driveable.start()
        };
    }

    public static Class<?> calcReturnType() {
        return switch (variant) {
            case DEFAULT -> double.class; // Used in: AbstractVehicle.calculateCost(), Car.calculateCost()
        };
    }



    /**
     * Developer tool: writes an overview of all variants to {@code VariantOverview.csv} in the working directory.
     * Not used by the tests.
     *
     * @param args ignored
     */
    public static void main(String[] args)  {
        try {
            String filename = "VariantOverview.csv";

            List<String> methodNames = new ArrayList<>();
            List<List<String>> rows = new ArrayList<>();

            // Collect all static methods that return a String or Class<?>
            for (Method method : Constants.class.getDeclaredMethods()) {
                if (Modifier.isStatic(method.getModifiers())
                        && method.getParameterCount() == 0
                        && !method.getName().equals("exportVariantsToCSV")
                        && (method.getReturnType() == String.class || method.getReturnType() == Class.class)) {
                    methodNames.add(method.getName());
                }
            }


            // Collect the values of each variant
            for (Variant v : Variant.values()) {
                List<String> row = new ArrayList<>();
                row.add(v.name());

                for (String methodName : methodNames) {
                    try {
                        Method method = Constants.class.getDeclaredMethod(methodName);
                        Object result = method.invoke(null);
                        row.add(result.toString());
                    } catch (NoSuchMethodException e) {
                        row.add("N/A");
                    }
                }
                rows.add(row);
            }

            // Write the CSV file
            try (FileWriter writer = new FileWriter(filename)) {
                writer.write("Variant;" + String.join(";", methodNames) + "\n");
                for (List<String> row : rows) {
                    writer.write(String.join(";", row) + "\n");
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
