package io.github.valentinherrmann.example.vehicles;

/**
 * Abstract base class for all vehicles.
 * Demonstrates: Abstract class, abstract methods, inheritance.
 */
public abstract class AbstractVehicle {
    
    protected String manufacturer;
    protected int year;

    /**
     * Constructor for AbstractVehicle.
     */
    public AbstractVehicle(String manufacturer, int year) {
        this.manufacturer = manufacturer;
        this.year = year;
    }
    
    /**
     * Gets the manufacturer.
     */
    public String getManufacturer() {
        return manufacturer;
    }
    
    /**
     * Gets the year.
     */
    public int getYear() {
        return year;
    }
    
    /**
     * Abstract method to be implemented by subclasses.
     */
    public abstract double calculateCost();

    /**
     * Concrete method that can be overridden.
     */
    public String getInfo() {
        return manufacturer + " (" + year + ")";
    }
}

