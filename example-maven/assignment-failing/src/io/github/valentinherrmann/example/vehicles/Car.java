package io.github.valentinherrmann.example.vehicles;

/**
 * FAILING EXAMPLE of the Car class: every deliberate defect is marked with "FAILING EXAMPLE".
 */
/*  FAILING EXAMPLE: "implements Driveable" is missing -> class MISSING (interface)  */
public class Car extends AbstractVehicle {

    /*  FAILING EXAMPLE (combined): name typo prize and Double instead of double -> attribute DEVIATION  */
    private Double prize;
    private double speed;

    public Car(String manufacturer, int year, double prize) {
        super(manufacturer, year);
        this.prize = prize;
        this.speed = 0.0;
    }

    /*  FAILING EXAMPLE (combined): Integer instead of int and protected instead of public -> constructor DEVIATION  */
    protected Car(String manufacturer, Integer year) {
        this(manufacturer, year, 20000.0);
    }

    public double getPrice() {
        return prize;
    }

    /*  FAILING EXAMPLE: negative prices are stored -> getter/setter test reports the violated setter condition  */
    public void setPrice(double prize) {
        this.prize = prize;
    }

    @Override
    public double calculateCost() {
        return prize * 0.1;
    }

    /*  FAILING EXAMPLE: long instead of int parameter -> method DEVIATION (numeric), tests go on with it  */
    public double calculateCost(long years) {
        return prize * 0.1 * years;
    }

    /*  FAILING EXAMPLE: the method throws -> method invocation exception  */
    @Override
    public String getInfo() {
        throw new UnsupportedOperationException("getInfo not implemented yet");
    }

    /*  FAILING EXAMPLE: package-private instead of public -> method modifier DEVIATION  */
    void start() {
        this.speed = 10.0;
    }

    /*  FAILING EXAMPLE: returns a constant instead of the attribute -> getter/setter test reports the wrong value  */
    public double getSpeed() {
        return 0.0;
    }
}
