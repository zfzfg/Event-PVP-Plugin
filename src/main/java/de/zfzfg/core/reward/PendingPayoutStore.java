package de.zfzfg.core.reward;

import de.zfzfg.eventplugin.EventPlugin;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Haelt Gewinne und Belohnungen fest, die gerade nicht ausgegeben werden koennen.
 *
 * <h2>Warum es das braucht</h2>
 * <p>Nach einem Match oder Event gilt eine feste Reihenfolge: erst wird das Survival-Inventar
 * wiederhergestellt, dann kommt der Gewinn dazu. Andersherum wuerde die Wiederherstellung
 * ({@code clearBefore}) den gerade uebergebenen Gewinn im selben Tick wieder loeschen.</p>
 *
 * <p>Diese Reihenfolge war umgesetzt - aber nur fuer den Normalfall. Drei Wege endeten im
 * Nichts:</p>
 * <ul>
 *   <li>Der Spieler war beim Match-Ende <b>offline</b>. Die Wiederherstellung wurde fuer den
 *       naechsten Join eingereiht ({@code QUEUED_FOR_JOIN}), die Ausschuettung dagegen
 *       uebersprungen - beide Einsaetze waren weg.</li>
 *   <li>Die Wiederherstellung <b>schlug fehl</b> ({@code FAILED}, {@code NOT_FOUND},
 *       {@code CANCELLED}). Der Erfolgs-Callback lief nie, also auch die Ausschuettung nicht.</li>
 *   <li>Vorgemerkte Event-Belohnungen lagen nur im Arbeitsspeicher der Sitzung und
 *       ueberlebten weder einen Neustart noch das Ende des Events.</li>
 * </ul>
 *
 * <p>{@code InventoryUtil.giveItems} steigt bei einem Offline-Spieler still aus - dort
 * verschwanden die Items endgueltig, ohne Logzeile.</p>
 *
 * <h2>Wie es arbeitet</h2>
 * <p>Kann nicht sofort ausgegeben werden, landet der Posten in
 * {@code plugins/<Plugin>/pending-payouts.yml} und wird beim naechsten Join nachgereicht -
 * nach der Wiederherstellung, damit die Reihenfolge gewahrt bleibt. Geschrieben wird
 * sofort bei jedem Eintrag, damit auch ein harter Absturz nichts verliert.</p>
 */
public final class PendingPayoutStore {

    private static final String FILE_NAME = "pending-payouts.yml";

    private final EventPlugin plugin;
    private final File file;

    /** Spieler -> offene Posten. Spiegelt die Datei; die Datei bleibt die Wahrheit. */
    private final Map<UUID, List<PendingPayout>> pending = new ConcurrentHashMap<>();

    public PendingPayoutStore(EventPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), FILE_NAME);
    }

    // ============ Laden und Speichern ============

    public void load() {
        pending.clear();
        if (!file.isFile()) {
            return;
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        int restored = 0;
        for (String key : config.getKeys(false)) {
            UUID playerId;
            try {
                playerId = UUID.fromString(key);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("[Payouts] Skipping entry with invalid UUID: " + key);  // i18n-ignore: technical payout log
                continue;
            }

            List<PendingPayout> payouts = new ArrayList<>();
            for (Map<?, ?> raw : config.getMapList(key)) {
                PendingPayout payout = PendingPayout.fromMap(raw);
                if (payout != null) {
                    payouts.add(payout);
                }
            }
            if (!payouts.isEmpty()) {
                pending.put(playerId, payouts);
                restored += payouts.size();
            }
        }

        if (restored > 0) {
            plugin.getLogger().info("[Payouts] " + restored + " pending payout(s) for "  // i18n-ignore: technical payout log
                    + pending.size() + " player(s) are waiting to be handed out on join");
        }
    }

    private void save() {
        YamlConfiguration config = new YamlConfiguration();
        for (Map.Entry<UUID, List<PendingPayout>> entry : pending.entrySet()) {
            List<Map<String, Object>> serialized = new ArrayList<>();
            for (PendingPayout payout : entry.getValue()) {
                serialized.add(payout.toMap());
            }
            config.set(entry.getKey().toString(), serialized);
        }
        try {
            config.save(file);
        } catch (IOException e) {
            // Bewusst laut: geht das Schreiben schief, sind die Items nur noch im Speicher
            // und ein Neustart verliert sie - genau das soll diese Klasse verhindern.
            plugin.getLogger().log(Level.SEVERE, "[Payouts] Could not save " + FILE_NAME  // i18n-ignore: technical payout log
                    + " - queued items would be lost on restart!", e);
        }
    }

    // ============ Eintragen ============

    /**
     * Gibt einen Posten sofort aus oder merkt ihn fuer den naechsten Join vor.
     *
     * @param player          Zielspieler; darf offline sein
     * @param items           auszugebende Items (Kopien werden angelegt)
     * @param money           Betrag ueber Vault, {@code 0} wenn keiner
     * @param reason          kurzer Grund, erscheint im Log und in der Datei
     * @param inventoryReady  {@code true}, wenn das Survival-Inventar bereits wiederhergestellt
     *                        ist. Bei {@code false} wird immer vorgemerkt - sonst wuerde die
     *                        nachlaufende Wiederherstellung den Posten wieder loeschen.
     * @return {@code true}, wenn sofort ausgegeben wurde
     */
    public boolean deliverOrQueue(Player player, List<ItemStack> items, double money,
                                  String reason, boolean inventoryReady) {
        PendingPayout payout = PendingPayout.of(items, money, reason);
        if (payout.isEmpty()) {
            return true;  // nichts auszuzahlen
        }

        if (player != null && player.isOnline() && inventoryReady) {
            hand(player, payout);
            return true;
        }

        UUID playerId = player == null ? null : player.getUniqueId();
        if (playerId == null) {
            plugin.getLogger().warning("[Payouts] Cannot queue '" + reason + "' without a player reference");  // i18n-ignore: technical payout log
            return false;
        }

        queue(playerId, payout);
        plugin.getLogger().info("[Payouts] Queued '" + reason + "' for " + playerId  // i18n-ignore: technical payout log
                + " (" + payout.describe() + ") - handed out on next join");
        return false;
    }

    /** Merkt einen Posten unabhaengig vom Zustand des Spielers vor. */
    public void queue(UUID playerId, List<ItemStack> items, double money, String reason) {
        PendingPayout payout = PendingPayout.of(items, money, reason);
        if (!payout.isEmpty()) {
            queue(playerId, payout);
        }
    }

    private void queue(UUID playerId, PendingPayout payout) {
        pending.computeIfAbsent(playerId, id -> Collections.synchronizedList(new ArrayList<>())).add(payout);
        save();
    }

    // ============ Ausgeben ============

    /**
     * Reicht alle vorgemerkten Posten eines Spielers nach.
     *
     * <p>Wird beim Join aufgerufen - und zwar erst, nachdem die Wiederherstellung des
     * Inventars durch ist. Frueher aufgerufen wuerde der Restore die Posten ueberschreiben.</p>
     *
     * @return Anzahl der ausgegebenen Posten
     */
    public int deliverAll(Player player) {
        if (player == null || !player.isOnline()) {
            return 0;
        }
        List<PendingPayout> payouts = pending.remove(player.getUniqueId());
        if (payouts == null || payouts.isEmpty()) {
            return 0;
        }

        // Erst aus der Datei nehmen, dann ausgeben: bricht das Ausgeben mittendrin ab,
        // ist der Rest immer noch im Spieler-Inventar oder als Drop im Boden - eine
        // zweite Ausgabe waere dagegen eine Vervielfaeltigung.
        save();

        for (PendingPayout payout : payouts) {
            hand(player, payout);
        }
        plugin.getLogger().info("[Payouts] Handed out " + payouts.size() + " pending payout(s) to "  // i18n-ignore: technical payout log
                + player.getName());
        return payouts.size();
    }

    /** Ob fuer diesen Spieler etwas offen ist. */
    public boolean hasPending(UUID playerId) {
        List<PendingPayout> payouts = pending.get(playerId);
        return payouts != null && !payouts.isEmpty();
    }

    private void hand(Player player, PendingPayout payout) {
        if (!payout.items().isEmpty()) {
            // giveItems legt ab, was nicht ins Inventar passt, als Drop vor die Fuesse -
            // damit geht auch bei vollem Inventar nichts verloren.
            de.zfzfg.pvpwager.utils.InventoryUtil.giveItems(player, payout.items());
        }
        if (payout.money() > 0 && plugin.hasEconomy()) {
            plugin.getEconomy().depositPlayer(player, payout.money());
        }
    }

    // ============ Datenhaltung ============

    /** Ein einzelner offener Posten. */
    public static final class PendingPayout {

        private final List<ItemStack> items;
        private final double money;
        private final String reason;
        private final long createdAt;

        private PendingPayout(List<ItemStack> items, double money, String reason, long createdAt) {
            this.items = items;
            this.money = money;
            this.reason = reason;
            this.createdAt = createdAt;
        }

        static PendingPayout of(List<ItemStack> items, double money, String reason) {
            List<ItemStack> copies = new ArrayList<>();
            if (items != null) {
                for (ItemStack item : items) {
                    if (item != null && item.getType() != Material.AIR) {
                        // Kopie: der Aufrufer haelt oft noch eine Referenz auf dieselben
                        // Stacks (etwa die Einsatzliste des Matches).
                        copies.add(item.clone());
                    }
                }
            }
            return new PendingPayout(copies, Math.max(0, money),
                    reason == null ? "unknown" : reason, System.currentTimeMillis());
        }

        @SuppressWarnings("unchecked")
        static PendingPayout fromMap(Map<?, ?> raw) {
            if (raw == null) {
                return null;
            }
            List<ItemStack> items = new ArrayList<>();
            Object rawItems = raw.get("items");
            if (rawItems instanceof List<?>) {
                for (Object element : (List<Object>) rawItems) {
                    if (element instanceof ItemStack) {
                        items.add((ItemStack) element);
                    } else if (element instanceof Map<?, ?>) {
                        // YAML gibt ItemStacks je nach Ladeweg als Map zurueck.
                        try {
                            items.add(ItemStack.deserialize((Map<String, Object>) element));
                        } catch (Exception ignored) {
                            // Einzelnes unlesbares Item ueberspringen, statt den ganzen
                            // Posten zu verlieren.
                        }
                    }
                }
            }
            double money = raw.get("money") instanceof Number ? ((Number) raw.get("money")).doubleValue() : 0;
            String reason = raw.get("reason") == null ? "unknown" : String.valueOf(raw.get("reason"));
            long createdAt = raw.get("createdAt") instanceof Number
                    ? ((Number) raw.get("createdAt")).longValue() : System.currentTimeMillis();

            PendingPayout payout = new PendingPayout(items, money, reason, createdAt);
            return payout.isEmpty() ? null : payout;
        }

        Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("items", new ArrayList<>(items));
            map.put("money", money);
            map.put("reason", reason);
            map.put("createdAt", createdAt);
            return map;
        }

        boolean isEmpty() {
            return items.isEmpty() && money <= 0;
        }

        String describe() {
            StringBuilder text = new StringBuilder();
            text.append(items.size()).append(" item stack(s)");  // i18n-ignore: technical payout log, describe() only feeds log lines
            if (money > 0) {
                text.append(", ").append(String.format(java.util.Locale.ROOT, "%.2f", money)).append(" money");
            }
            return text.toString();
        }

        public List<ItemStack> items() { return items; }
        public double money() { return money; }
        public String reason() { return reason; }
        public long createdAt() { return createdAt; }
    }

    /** Nur fuer die Diagnose: alle offenen Posten. */
    public Map<UUID, List<PendingPayout>> snapshot() {
        return Collections.unmodifiableMap(pending);
    }
}
