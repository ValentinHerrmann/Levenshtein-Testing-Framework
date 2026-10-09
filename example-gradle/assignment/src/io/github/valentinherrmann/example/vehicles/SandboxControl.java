package io.github.valentinherrmann.example.vehicles;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * EXAMPLE ONLY - not part of an exercise. Stands in for student code that tries to do something it may or
 * may not do, so that {@code SecurityControlTest} can prove the Ares 2 sandbox is active. Operations must
 * happen in supervised (student) code: the test classes themselves are exempt from the policy.
 */
public final class SandboxControl {

    private SandboxControl() {
    }

    /** Positive control: the one file the policy permits. */
    public static String readPermittedFile() throws IOException {
        return Files.readString(Path.of("test/sandbox/allowed.txt")).trim();
    }

    /** Negative control: reading the (hidden) test configuration must be blocked. */
    public static String readTestConfiguration() throws IOException {
        return Files.readString(Path.of("test/SecurityPolicy.yaml"));
    }

    /**
     * Timeout control: a busy loop, bounded so that a broken timeout cannot hang the build.
     * Student code cannot react to Ares' interrupt (Thread.sleep, Object.wait, Thread.interrupted are all
     * blocked thread operations), so Ares halts the test JVM with exit code 124 when this times out.
     */
    public static long busyLoop(long millis) {
        long end = System.nanoTime() + millis * 1_000_000L;
        long counter = 0;
        while (System.nanoTime() < end) {
            counter++;
        }
        return counter;
    }
}
