package io.github.valentinherrmann.example.tests;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.tum.cit.ase.ares.api.Policy;
import de.tum.cit.ase.ares.api.StrictTimeout;
import io.github.valentinherrmann.example.vehicles.SandboxControl;
import io.github.valentinherrmann.levenshtein.LevenshteinTest;

import org.junit.jupiter.api.Test;
import org.junit.platform.commons.support.AnnotationSupport;

/**
 * Regression guard for the sandbox itself: if Ares 2 is not active (missing weaving, missing test-type
 * annotation, wrong {@code withinPath}, ...), these tests fail instead of every other test silently running
 * unsupervised. See {@link TimeoutControlTest} for the deadline.
 */
@LevenshteinTest
@Policy(value = "test/SecurityPolicy.yaml", withinPath = "classes/io/github/valentinherrmann/example/vehicles")
public class SecurityControlTest {

    /** Positive control: what the policy permits must keep working. */
    @Test
    void permittedFileIsReadable() throws Exception {
        assertThat(SandboxControl.readPermittedFile()).isEqualTo("allowed");
    }

    /** Negative control: everything else in the same domain must be rejected. */
    @Test
    void testConfigurationIsNotReadableByStudentCode() {
        // assert on the file name, not the (localized) message text
        assertThatThrownBy(SandboxControl::readTestConfiguration)
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("SecurityPolicy.yaml");
    }

    /** The composed annotation must carry the Ares test type and a default deadline. */
    @Test
    void levenshteinTestComposesAresAnnotations() {
        assertThat(AnnotationSupport.findAnnotation(SecurityControlTest.class, de.tum.cit.ase.ares.api.jupiter.Public.class))
                .isPresent();
        assertThat(AnnotationSupport.findAnnotation(SecurityControlTest.class, StrictTimeout.class))
                .hasValueSatisfying(timeout -> assertThat(timeout.value()).isEqualTo(5));
    }
}
