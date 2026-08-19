package de.zfzfg.core.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class TimeTest {

    @Test
    @DisplayName("TICKS_PER_SECOND should be 20")
    void testTicksPerSecondConstant() {
        assertThat(Time.TICKS_PER_SECOND).isEqualTo(20L);
    }

    @ParameterizedTest
    @CsvSource({
            "0, 0",
            "1, 20",
            "5, 100",
            "60, 1200"
    })
    @DisplayName("seconds to ticks conversion")
    void testSeconds(long seconds, long expectedTicks) {
        assertThat(Time.seconds(seconds)).isEqualTo(expectedTicks);
    }

    @ParameterizedTest
    @CsvSource({
            "0, 0",
            "1, 1200",
            "5, 6000",
            "60, 72000"
    })
    @DisplayName("minutes to ticks conversion")
    void testMinutes(long minutes, long expectedTicks) {
        assertThat(Time.minutes(minutes)).isEqualTo(expectedTicks);
    }

    @ParameterizedTest
    @CsvSource({
            "0, 0",
            "1, 72000",
            "2, 144000",
            "24, 1728000"
    })
    @DisplayName("hours to ticks conversion")
    void testHours(long hours, long expectedTicks) {
        assertThat(Time.hours(hours)).isEqualTo(expectedTicks);
    }

    @Test
    @DisplayName("ticks pass-through")
    void testTicks() {
        assertThat(Time.ticks(42L)).isEqualTo(42L);
        assertThat(Time.ticks(0L)).isEqualTo(0L);
    }

    @ParameterizedTest
    @CsvSource({
            "0, 0",
            "1, 3600000",
            "2, 7200000",
            "24, 86400000"
    })
    @DisplayName("hours to milliseconds conversion")
    void testHoursToMillis(long hours, long expectedMillis) {
        assertThat(Time.hoursToMillis(hours)).isEqualTo(expectedMillis);
    }
}
