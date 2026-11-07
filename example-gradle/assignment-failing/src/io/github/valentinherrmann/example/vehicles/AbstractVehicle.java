package io.github.valentinherrmann.example.vehicles;

/**
 * FAILING EXAMPLE of the abstract base class: every deliberate defect is marked with "FAILING EXAMPLE".
 */
public abstract class AbstractVehicle {

    /*  FAILING EXAMPLE: public instead of protected -> attribute modifier DEVIATION  */
    public String manufacturer;

    /*  FAILING EXAMPLE: Integer instead of int -> attribute type DEVIATION (primitive/wrapper)  */
    protected Integer year;

    public AbstractVehicle(String manufacturer, int year) {
        this.manufacturer = manufacturer;
        this.year = year;
    }

    public String getManufacturer() {
        return manufacturer;
    }

    /*  FAILING EXAMPLE (combined): long instead of int and protected instead of public -> method DEVIATION  */
    protected long getYear() {
        return year;
    }

    public abstract double calculateCost();

    public String getInfo() {
        return manufacturer + " (" + year + ")";
    }
}
