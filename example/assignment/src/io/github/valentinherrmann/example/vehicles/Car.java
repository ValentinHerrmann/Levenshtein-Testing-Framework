package io.github.valentinherrmann.example.vehicles;

/**
 * Concrete Car implementation.
 * Demonstrates:
 * - Inheritance from abstract class (AbstractVehicle)
 * - Interface implementation (Driveable)
 * - Method overriding (calculateCost, getInfo, start, getSpeed)
 * - Method overloading (calculateCost with different parameters)
 * - Constructor overloading (two constructors)
 */
public class Car extends AbstractVehicle implements Driveable {

    private double price;
    private double speed;

    /**
     * Constructor 1 - Constructor overloading demonstration.
     */
    public Car(String manufacturer, int year, double price) {
        super(manufacturer, year);
        this.price = price;
        this.speed = 0.0;
    }

    /**
     * Constructor 2 - Constructor overloading demonstration.
     */
    public Car(String manufacturer, int year) {
        this(manufacturer, year, 20000.0); // Calls other constructor
    }

    /**
     * Gets the price.
     */
    public double getPrice() {
        return price;
    }

    // Method overriding - implements abstract method from AbstractVehicle
    @Override
    public double calculateCost() {
        return price * 0.1; // Annual cost is 10% of price
    }

    // Method overloading - same name, different parameters
    public double calculateCost(int years) {
        return price * 0.1 * years;
    }

    // Method overriding - overrides concrete method from AbstractVehicle
    @Override
    public String getInfo() {
        return super.getInfo() + " - $" + price;
    }

    // Interface implementation - implements method from Driveable
    @Override
    public void start() {
        this.speed = 10.0;
    }

    // Interface implementation - implements method from Driveable
    @Override
    public double getSpeed() {
        return speed;
    }
}

