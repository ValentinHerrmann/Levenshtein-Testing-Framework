package io.github.valentinherrmann.levenshtein;

import static io.github.valentinherrmann.levenshtein.WrapperProperty.Existence.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Tests of {@link LevenshteinSettings}, {@link WrapperProperty} and {@link Messages}.
 */
class SettingsAndMessagesTest {

    @AfterEach
    void resetSettings() {
        LevenshteinSettings.reset();
    }

    @Test
    void thresholdsMustBePercentages() {
        List<IntConsumer> setters = List.of(LevenshteinSettings::setClassNameDeviationThreshold,
                LevenshteinSettings::setMethodNameDeviationThreshold, LevenshteinSettings::setAttributeNameDeviationThreshold);
        List<IntSupplier> getters = List.of(LevenshteinSettings::getClassNameDeviationThreshold,
                LevenshteinSettings::getMethodNameDeviationThreshold, LevenshteinSettings::getAttributeNameDeviationThreshold);
        for (int i = 0; i < setters.size(); i++) {
            IntConsumer setter = setters.get(i);
            IntSupplier getter = getters.get(i);
            assertThat(getter.getAsInt()).isEqualTo(LevenshteinSettings.DEFAULT_THRESHOLD);
            setter.accept(0);
            assertThat(getter.getAsInt()).isZero();
            setter.accept(100);
            assertThat(getter.getAsInt()).isEqualTo(100);
            assertThatThrownBy(() -> setter.accept(-1)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> setter.accept(101)).isInstanceOf(IllegalArgumentException.class);
            assertThat(getter.getAsInt()).isEqualTo(100);
        }
        LevenshteinSettings.reset();
        for (IntSupplier getter : getters) {
            assertThat(getter.getAsInt()).isEqualTo(LevenshteinSettings.DEFAULT_THRESHOLD);
        }
    }

    @Test
    void languageCanBeChangedButNotToNull() {
        assertThat(LevenshteinSettings.getLanguage()).isEqualTo(LevenshteinSettings.Language.DEUTSCH);
        LevenshteinSettings.setLanguage(LevenshteinSettings.Language.ENGLISH);
        assertThat(LevenshteinSettings.getLanguage()).isEqualTo(LevenshteinSettings.Language.ENGLISH);
        assertThatThrownBy(() -> LevenshteinSettings.setLanguage(null)).isInstanceOf(IllegalArgumentException.class);
        assertThat(LevenshteinSettings.getLanguage()).isEqualTo(LevenshteinSettings.Language.ENGLISH);
    }

    @Test
    void messagesFollowTheLanguage() {
        assertThat(Messages.GETTERS_SETTERS_PASSED.get()).isEqualTo("bestanden");
        assertThat(Messages.GETTERS_SETTERS_PASSED).hasToString("bestanden");
        LevenshteinSettings.setLanguage(LevenshteinSettings.Language.ENGLISH);
        assertThat(Messages.GETTERS_SETTERS_PASSED.get()).isEqualTo("passed");
        assertThat(Messages.GETTERS_SETTERS_PASSED).hasToString("passed");
        assertThat(Messages.GETTERS_SETTERS_ATTRIBUTE.format("price")).isEqualTo("attribute price");
    }

    @Test
    void newPropertyIsUnchecked() {
        WrapperProperty<String> property = new WrapperProperty<>("price");
        assertThat(property.getExpected()).isEqualTo("price");
        assertThat(property.getActual()).isNull();
        assertThat(property.getExistence()).isEqualTo(UNCHECKED);
        assertThat(property).hasToString("Expected: price, Actual: null, Existence: UNCHECKED");
        property.actual = "prize";
        property.existence = DEVIATES;
        assertThat(property.getActual()).isEqualTo("prize");
        assertThat(property.getExistence()).isEqualTo(DEVIATES);
        assertThat(property).hasToString("Expected: price, Actual: prize, Existence: DEVIATES");
    }

    @Test
    void severityOrdersTheStates() {
        assertThat(UNCHECKED.severity()).isEqualTo(-1);
        assertThat(EXACT.severity()).isZero();
        assertThat(DEVIATES.severity()).isEqualTo(1);
        assertThat(MISSING.severity()).isEqualTo(2);
        assertThat(WrapperProperty.Existence.worst(EXACT, MISSING)).isEqualTo(MISSING);
        assertThat(WrapperProperty.Existence.worst(DEVIATES, EXACT)).isEqualTo(DEVIATES);
        assertThat(WrapperProperty.Existence.worst(null, EXACT)).isEqualTo(EXACT);
        assertThat(WrapperProperty.Existence.worst(EXACT, null)).isEqualTo(EXACT);
        assertThat(WrapperProperty.Existence.worst(null, null)).isEqualTo(UNCHECKED);
    }
}
