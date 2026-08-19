package de.zfzfg.eventplugin.model;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

class EquipmentGroupTest {

    @Test
    @DisplayName("parse empty equipment group")
    void testEmptyEquipmentGroup() {
        YamlConfiguration yaml = new YamlConfiguration();
        EquipmentGroup group = new EquipmentGroup("empty_kit", yaml);

        assertThat(group.getId()).isEqualTo("empty_kit");
        assertThat(group.getArmor()).isNotNull();
        assertThat(group.getArmor().getHelmet()).isNull();
        assertThat(group.getArmor().getChestplate()).isNull();
        assertThat(group.getArmor().getLeggings()).isNull();
        assertThat(group.getArmor().getBoots()).isNull();
        assertThat(group.getArmor().getOffhand()).isNull();
        assertThat(group.getInventory()).isEmpty();
    }

    @Test
    @DisplayName("parse equipment group with empty sections")
    void testEmptySections() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.createSection("armor");
        yaml.createSection("inventory");

        EquipmentGroup group = new EquipmentGroup("custom_kit", yaml);

        assertThat(group.getId()).isEqualTo("custom_kit");
        assertThat(group.getArmor()).isNotNull();
        assertThat(group.getArmor().getHelmet()).isNull();
        assertThat(group.getInventory()).isEmpty();
    }
}
