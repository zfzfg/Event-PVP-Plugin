package de.zfzfg.eventplugin.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class UpdateCheckerTest {

    @ParameterizedTest
    @CsvSource({
            "1.0.0, 1.1.0, true",
            "1.0.0, 2.0.0, true",
            "1.0.9, 1.1.0, true",
            "1.1.0, 1.0.9, false",
            "1.1.0, 1.1.0, false",
            "1.2, 1.2.0, false",
            "1.2.0, 1.2, false",
            "1.1.0-Beta, 1.1.0, true",
            "1.0.0-alpha, 1.0.0, true",
            "1.0.0-RC1, 1.0.0, true",
            "1.0.0, 1.0.0-Beta, false"
    })
    @DisplayName("compareVersions determines if latest is newer than current")
    void testCompareVersions(String current, String latest, boolean expected) {
        assertThat(UpdateChecker.compareVersions(current, latest)).isEqualTo(expected);
    }
}
