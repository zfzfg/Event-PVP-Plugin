package de.zfzfg.test;

import de.zfzfg.eventplugin.EventPlugin;
import de.zfzfg.test.mocks.MockEconomy;
import de.zfzfg.test.mocks.MockInventoryBackupService;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.plugin.ServicePriority;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

/**
 * Basisklasse für alle In-Memory MockBukkit-Tests.
 * Initialisiert und terminiert ServerMock und registriert Standard-Services (Economy, Backup).
 */
public abstract class MockBukkitTestBase {

    protected ServerMock server;
    protected EventPlugin plugin;
    protected MockEconomy economy;
    protected MockInventoryBackupService backupService;

    @BeforeEach
    public void setUpMockBukkit() {
        if (!MockBukkit.isMocked()) {
            server = MockBukkit.mock();
        } else {
            server = MockBukkit.getMock();
        }

        economy = new MockEconomy();
        backupService = new MockInventoryBackupService();

        // Registriere Economy Service im Bukkit ServicesManager
        server.getServicesManager().register(Economy.class, economy,
                MockBukkit.createMockPlugin("Vault"), ServicePriority.Highest);
    }

    @AfterEach
    public void tearDownMockBukkit() {
        if (MockBukkit.isMocked()) {
            MockBukkit.unmock();
        }
    }

    /**
     * Erstellt einen regulären Spieler-Mock mit Standard-Guthaben.
     */
    protected PlayerMock createPlayer(String name) {
        PlayerMock player = server.addPlayer(name);
        economy.setBalance(player.getUniqueId(), 1000.0);
        return player;
    }

    /**
     * Erstellt einen Operator-Spieler-Mock.
     */
    protected PlayerMock createOpPlayer(String name) {
        PlayerMock player = server.addPlayer(name);
        player.setOp(true);
        economy.setBalance(player.getUniqueId(), 10000.0);
        return player;
    }

    /**
     * Erstellt eine simulierte Welt.
     */
    protected WorldMock createWorld(String name) {
        return server.addSimpleWorld(name);
    }

    /**
     * Führt eine bestimmte Anzahl an Scheduler-Ticks aus.
     */
    protected void tick(long ticks) {
        server.getScheduler().performTicks(ticks);
    }

    /**
     * Führt alle anstehenden asynchronen / synchronen Tasks für einen Tick aus.
     */
    protected void tick() {
        server.getScheduler().performOneTick();
    }

    /**
     * Erstellt ein EntityDamageEvent mit dem nicht-deprecateten Konstruktor (für Purpur / Paper 26.2).
     */
    @SuppressWarnings("deprecation")
    protected org.bukkit.event.entity.EntityDamageEvent createDamageEvent(org.bukkit.entity.Entity damagee,
                                                                          org.bukkit.event.entity.EntityDamageEvent.DamageCause cause,
                                                                          org.bukkit.damage.DamageSource damageSource,
                                                                          double damage) {
        java.util.Map<org.bukkit.event.entity.EntityDamageEvent.DamageModifier, Double> modifiers =
                new java.util.EnumMap<>(org.bukkit.event.entity.EntityDamageEvent.DamageModifier.class);
        modifiers.put(org.bukkit.event.entity.EntityDamageEvent.DamageModifier.BASE, damage);

        java.util.Map<org.bukkit.event.entity.EntityDamageEvent.DamageModifier, com.google.common.base.Function<? super Double, Double>> modifierFunctions =
                new java.util.EnumMap<>(org.bukkit.event.entity.EntityDamageEvent.DamageModifier.class);
        modifierFunctions.put(org.bukkit.event.entity.EntityDamageEvent.DamageModifier.BASE, com.google.common.base.Functions.constant(-0.0));

        return new org.bukkit.event.entity.EntityDamageEvent(damagee, cause, damageSource, modifiers, modifierFunctions);
    }

    /**
     * Erstellt ein EntityDamageByEntityEvent mit dem nicht-deprecateten Konstruktor (für Purpur / Paper 26.2).
     */
    @SuppressWarnings("deprecation")
    protected org.bukkit.event.entity.EntityDamageByEntityEvent createDamageByEntityEvent(org.bukkit.entity.Entity damager,
                                                                                          org.bukkit.entity.Entity damagee,
                                                                                          org.bukkit.event.entity.EntityDamageEvent.DamageCause cause,
                                                                                          org.bukkit.damage.DamageSource damageSource,
                                                                                          double damage) {
        java.util.Map<org.bukkit.event.entity.EntityDamageEvent.DamageModifier, Double> modifiers =
                new java.util.EnumMap<>(org.bukkit.event.entity.EntityDamageEvent.DamageModifier.class);
        modifiers.put(org.bukkit.event.entity.EntityDamageEvent.DamageModifier.BASE, damage);

        java.util.Map<org.bukkit.event.entity.EntityDamageEvent.DamageModifier, com.google.common.base.Function<? super Double, Double>> modifierFunctions =
                new java.util.EnumMap<>(org.bukkit.event.entity.EntityDamageEvent.DamageModifier.class);
        modifierFunctions.put(org.bukkit.event.entity.EntityDamageEvent.DamageModifier.BASE, com.google.common.base.Functions.constant(-0.0));

        return new org.bukkit.event.entity.EntityDamageByEntityEvent(damager, damagee, cause, damageSource, modifiers, modifierFunctions, false);
    }
}
