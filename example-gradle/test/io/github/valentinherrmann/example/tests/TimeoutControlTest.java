package io.github.valentinherrmann.example.tests;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;

import de.tum.cit.ase.ares.api.Policy;
import de.tum.cit.ase.ares.api.StrictTimeout;
import io.github.valentinherrmann.example.vehicles.SandboxControl;
import io.github.valentinherrmann.levenshtein.LevenshteinTest;

import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.platform.testkit.engine.EngineTestKit;

/**
 * Regression guard for deadlines: student code running too long must be stopped by {@code @StrictTimeout}.
 *
 * <p>Ares 2 interrupts timed-out student code. Student code cannot react to that interrupt (all thread
 * operations are blocked by the policy), so Ares then halts the complete test JVM with exit code 124
 * ("fail closed"). This test therefore starts {@link TimeoutProbe} in a separate JVM with the same agent and
 * module flags and expects exactly that exit code. It deliberately carries no Ares annotation itself.</p>
 *
 * <p>Consequence for exams: an endless loop in student code ends the JVM of its test class. The Surefire
 * configuration starts a fresh JVM per test class, so only that class is lost.</p>
 */
public class TimeoutControlTest {

    /** Exit code of {@code de.tum.cit.ase.ares.api.internal.TimeoutUtils} for unresponsive timed-out code. */
    private static final int ARES_TIMEOUT_EXIT_CODE = 124;

    @Test
    void strictTimeoutHaltsBusyStudentCode() throws Exception {
        List<String> command = new ArrayList<>();
        command.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
        command.addAll(ManagementFactory.getRuntimeMXBean().getInputArguments()); // -javaagent, --add-opens, ...
        command.add("-cp");
        command.add(System.getProperty("java.class.path"));
        command.add(ProbeLauncher.class.getName());

        Path log = Files.createTempFile("timeout-probe", ".log");
        long start = System.nanoTime();
        Process probe = new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(log.toFile()).start();
        boolean finished = probe.waitFor(60, TimeUnit.SECONDS);
        if (!finished) {
            probe.destroyForcibly();
        }
        long seconds = TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - start);

        assertThat(finished).as("probe JVM finished").isTrue();
        assertThat(probe.exitValue())
                .as("exit code of the probe JVM after %ss, output:%n%s", seconds, Files.readString(log))
                .isEqualTo(ARES_TIMEOUT_EXIT_CODE);
        Files.deleteIfExists(log);
    }

    /** Entry point of the probe JVM. Exit code 0: probe passed (timeout not enforced), 1: probe failed otherwise. */
    public static final class ProbeLauncher {
        public static void main(String[] args) {
            long failed = EngineTestKit.engine("junit-jupiter")
                    .selectors(selectClass(TimeoutProbe.class))
                    .execute()
                    .testEvents()
                    .failed()
                    .count();
            System.exit(failed == 0 ? 0 : 1);
        }
    }

    /**
     * Executed only in the probe JVM (Surefire skips nested classes).
     * The direct {@code @StrictTimeout(1)} overrides the default of {@link LevenshteinTest}.
     */
    @LevenshteinTest
    @Policy(value = "test/SecurityPolicy.yaml", withinPath = "classes/io/github/valentinherrmann/example/vehicles")
    @StrictTimeout(1)
    static class TimeoutProbe {
        @Test
        void runsLongerThanTheTimeout() {
            SandboxControl.busyLoop(20_000);
        }
    }
}
