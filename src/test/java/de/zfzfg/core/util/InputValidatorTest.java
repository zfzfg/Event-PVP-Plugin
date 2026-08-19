package de.zfzfg.core.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InputValidatorTest {

    @Nested
    @DisplayName("validateEventId")
    class ValidateEventIdTests {

        @ParameterizedTest
        @ValueSource(strings = {"event1", "pvp_match", "tournament-2026", "abc", "12345678901234567890123456789012"})
        void shouldAcceptValidEventIds(String validId) {
            String result = InputValidator.validateEventId(validId);
            assertThat(result).isEqualTo(validId.toLowerCase());
        }

        @Test
        void shouldRejectUppercaseEventIds() {
            assertThatThrownBy(() -> InputValidator.validateEventId("PVP_EVENT"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Invalid event ID format");
        }

        @ParameterizedTest
        @ValueSource(strings = {"ab", "a", "", "event with space", "event@123", "event!#$", "too_long_event_id_that_exceeds_the_maximum_limit_of_thirty_two_characters"})
        void shouldRejectInvalidEventIds(String invalidId) {
            assertThatThrownBy(() -> InputValidator.validateEventId(invalidId))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Invalid event ID format");
        }

        @Test
        void shouldRejectNullEventId() {
            assertThatThrownBy(() -> InputValidator.validateEventId(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Invalid event ID format");
        }
    }

    @Nested
    @DisplayName("validateMoney")
    class ValidateMoneyTests {

        @Test
        void shouldAcceptValidAmountWithinBounds() {
            double amount = InputValidator.validateMoney("50.0", 10.0, 100.0);
            assertThat(amount).isEqualTo(50.0);
        }

        @Test
        void shouldAcceptMinAndMaxBoundaryValues() {
            assertThat(InputValidator.validateMoney("10.0", 10.0, 100.0)).isEqualTo(10.0);
            assertThat(InputValidator.validateMoney("100.0", 10.0, 100.0)).isEqualTo(100.0);
        }

        @Test
        void shouldRejectAmountBelowMinimum() {
            assertThatThrownBy(() -> InputValidator.validateMoney("9.99", 10.0, 100.0))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Amount out of range");
        }

        @Test
        void shouldRejectAmountAboveMaximum() {
            assertThatThrownBy(() -> InputValidator.validateMoney("100.01", 10.0, 100.0))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Amount out of range");
        }

        @ParameterizedTest
        @ValueSource(strings = {"abc", "", "   ", "10,5", "12.34.56", "invalid"})
        void shouldRejectInvalidNumberFormats(String invalid) {
            assertThatThrownBy(() -> InputValidator.validateMoney(invalid, 10.0, 100.0))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Invalid number format");
        }

        @ParameterizedTest
        @ValueSource(strings = {"NaN", "Infinity", "-Infinity"})
        void shouldRejectSpecialFloatValues(String special) {
            assertThatThrownBy(() -> InputValidator.validateMoney(special, 10.0, 100.0))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Amount out of range");
        }
    }

    @Nested
    @DisplayName("validateOnlinePlayer")
    class ValidateOnlinePlayerTests {

        @Test
        void shouldRejectNullOrEmptyPlayerName() {
            assertThatThrownBy(() -> InputValidator.validateOnlinePlayer(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Player name is required");

            assertThatThrownBy(() -> InputValidator.validateOnlinePlayer(""))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Player name is required");
        }
    }
}
