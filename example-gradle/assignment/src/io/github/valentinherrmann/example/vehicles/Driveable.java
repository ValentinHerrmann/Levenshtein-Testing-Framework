package io.github.valentinherrmann.example.vehicles;

/**
 * Interface for driveable vehicles.
 * Demonstrates: Interface constants and method declarations.
 */
public interface Driveable {
    
    // Interface constant (public static final by default)
    double MAX_SPEED = 200.0;

    /**
     * Starts the vehicle's engine.
     */
    void start();

    /**
     * Gets the current speed.
     */
    double getSpeed();
}

