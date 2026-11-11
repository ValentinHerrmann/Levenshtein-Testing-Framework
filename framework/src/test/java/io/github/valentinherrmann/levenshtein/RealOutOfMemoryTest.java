package io.github.valentinherrmann.levenshtein;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

/**
 * Student code that really runs out of heap (issue #44). Runs in its own surefire execution with a small heap
 * ({@code -Xmx64m}, see {@code framework/pom.xml}); excluded from the other tests and from mutation testing.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class RealOutOfMemoryTest {

    private static final List<String> TEN_SONGS = List.of("a", "b", "c", "d", "e", "f", "g", "h", "i", "j");

    private final MethodWrapperTest.W<Object> medley = new MethodWrapperTest.W<>("CallFixtures$Medley", "public", "static");
    private final MethodWrapper<Object, String> createMedley =
            new MethodWrapper<>(medley, "createMedley", String.class, new Class<?>[]{List.class}, "public", "static");

    @AfterEach
    void resetSettings() {
        LevenshteinSettings.reset();
    }

    @Test
    @Order(1)
    void outOfMemoryIsReportedWithTheCallAndAHint() {
        LevenshteinSettings.setLanguage(LevenshteinSettings.Language.ENGLISH);
        assertThatThrownBy(() -> createMedley.invokeWithContext(() -> "standard cases", TEN_SONGS))
                .isExactlyInstanceOf(AssertionError.class)
                .hasNoCause()
                .hasMessageStartingWith("""
                        Method:   createMedley(List)
                        Scenario: standard cases
                        Input:    ["a", "b", "c", "d", "e", "f", "g", "h", "i", "j"]
                        Actual:   OutOfMemoryError""")
                .hasMessageEndingWith("\nHint: an OutOfMemoryError usually means that a loop keeps making a String"
                        + " or a list bigger and never ends.");
    }

    @Test
    @Order(2)
    void laterCallsRunNormally() {
        assertThat(createMedley.invoke(List.of("a", "b"))).isEqualTo("aabb");
    }
}
