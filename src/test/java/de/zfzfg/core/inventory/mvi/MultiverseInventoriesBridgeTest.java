package de.zfzfg.core.inventory.mvi;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MultiverseInventoriesBridgeTest {

    @Test
    @DisplayName("MviConflictReport notInstalled returns uninstalled status")
    void testNotInstalledReport() {
        MviConflictReport report = MviConflictReport.notInstalled();

        assertThat(report.installed()).isFalse();
        assertThat(report.version()).isEmpty();
        assertThat(report.collisions()).isEmpty();
        assertThat(report.hasCollisions()).isFalse();
        assertThat(report.configUnreadable()).isFalse();
        assertThat(report.affectedGroups()).isEmpty();
    }

    @Test
    @DisplayName("MviConflictReport unreadable returns installed with configUnreadable=true")
    void testUnreadableReport() {
        MviConflictReport report = MviConflictReport.unreadable("3.0.0");

        assertThat(report.installed()).isTrue();
        assertThat(report.version()).isEqualTo("3.0.0");
        assertThat(report.collisions()).isEmpty();
        assertThat(report.hasCollisions()).isFalse();
        assertThat(report.configUnreadable()).isTrue();
    }

    @Test
    @DisplayName("MviConflictReport of with collisions")
    void testReportWithCollisions() {
        MviConflictReport.Collision c1 = new MviConflictReport.Collision(
                "arena_1", "default", List.of("survival", "creative"));
        MviConflictReport.Collision c2 = new MviConflictReport.Collision(
                "event_world", "events", List.of("hub"));

        MviConflictReport report = MviConflictReport.of("4.2.0", List.of(c1, c2));

        assertThat(report.installed()).isTrue();
        assertThat(report.version()).isEqualTo("4.2.0");
        assertThat(report.hasCollisions()).isTrue();
        assertThat(report.collisions()).hasSize(2);
        assertThat(report.affectedGroups()).containsExactly("default", "events");

        assertThat(c1.world()).isEqualTo("arena_1");
        assertThat(c1.group()).isEqualTo("default");
        assertThat(c1.partnerWorlds()).containsExactly("survival", "creative");
        assertThat(c1.fixCommand()).isEqualTo("/mvinv rmworld arena_1 default");
    }
}
