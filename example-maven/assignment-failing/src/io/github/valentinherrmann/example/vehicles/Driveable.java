package io.github.valentinherrmann.example.vehicles;

/**
 * FAILING EXAMPLE of the Driveable interface: every deliberate defect is marked with "FAILING EXAMPLE".
 */
public interface Driveable {

    /*  FAILING EXAMPLE: int instead of double -> attribute type DEVIATION (numeric), tests go on with it  */
    int MAX_SPEED = 200;

    void start();

    double getSpeed();
}
