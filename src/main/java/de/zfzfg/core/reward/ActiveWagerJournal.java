package de.zfzfg.core.reward;

import de.zfzfg.eventplugin.EventPlugin;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Haelt abgebuchte Wetteinsaetze fest, solange das Match noch nicht ausgezahlt ist.
 *
 * <p>Items und Vault-Geld liegen sonst nur im {@code Match}-Objekt. Ein Absturz dazwischen
 * stellt das Restinventar ueber {@code inventory-guard.yml} wieder her und laesst den
 * Einsatz verschwinden. Dieses Journal wird synchron geschrieben, sobald der Einsatz
 * abgebucht ist, und erst geloescht, wenn die bestehende Auszahlung ihn uebernommen hat.</p>
 *
 * <p>Bleibt ein Eintrag ueber einen Neustart stehen, bekommt jeder Spieler seinen eigenen
 * Einsatz zurueck. Es gibt keinen Sieger: das Match ist nicht zu Ende gespielt worden.</p>
 */
public final class ActiveWagerJournal {

    /** Grund in {@code pending-payouts.yml}. Die Match-Id haengt daran, damit ein zweiter
     *  Wiederanlauf denselben Posten nicht noch einmal einreiht. */
    public static final String CRASH_REFUND_PREFIX = "wager-crash-refund:";

    private static final String FILE_NAME = "active-wagers.yml";

    private final EventPlugin plugin;
    private final Object fileLock = new Object();
    private final Map<UUID, Stake> stakes = new LinkedHashMap<>();
    private volatile boolean loaded;

    public ActiveWagerJournal(EventPlugin plugin) {
        this.plugin = plugin;
    }

    /** Ein abgebuchter Einsatz eines Spielers. */
    public static final class Stake {
        private final UUID playerId;
        private final UUID matchId;
        private final List<ItemStack> items;
        private final double money;
        private final long openedAt;

        public Stake(UUID playerId, UUID matchId, List<ItemStack> items, double money) {
            this(playerId, matchId, items, money, System.currentTimeMillis());
        }

        private Stake(UUID playerId, UUID matchId, List<ItemStack> items, double money, long openedAt) {
            this.playerId = playerId;
            this.matchId = matchId;
            this.items = copyItems(items);
            this.money = Math.max(0, money);
            this.openedAt = openedAt;
        }

        public UUID playerId() { return playerId; }
        public UUID matchId() { return matchId; }
        public List<ItemStack> items() { return items; }
        public double money() { return money; }
        public long openedAt() { return openedAt; }

        public boolean isEmpty() {
            return items.isEmpty() && money <= 0;
        }

        private static List<ItemStack> copyItems(List<ItemStack> items) {
            List<ItemStack> copies = new ArrayList<>();
            if (items == null) {
                return copies;
            }
            for (ItemStack item : items) {
                if (item != null && item.getType() != org.bukkit.Material.AIR) {
                    copies.add(item.clone());
                }
            }
            return copies;
        }
    }

    public void load() {
        synchronized (fileLock) {
            stakes.clear();
            File file = file();
            if (file.isFile()) {
                YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
                ConfigurationSection root = cfg.getConfigurationSection("stakes");
                if (root != null) {
                    for (String key : root.getKeys(false)) {
                        try {
                            UUID playerId = UUID.fromString(key);
                            ConfigurationSection sec = root.getConfigurationSection(key);
                            Stake stake = sec == null ? null : readStake(playerId, sec);
                            if (stake != null && !stake.isEmpty()) {
                                stakes.put(playerId, stake);
                            }
                        } catch (IllegalArgumentException e) {
                            plugin.getLogger().warning("[Wagers] Skipping entry with invalid UUID: " + key);  // i18n-ignore: technical wager journal log
                        }
                    }
                }
            }
            loaded = true;
        }
    }

    /**
     * Schreibt die Einsaetze eines Matches in einer Datei.
     *
     * @return {@code false}, wenn die Datei nicht geschrieben werden konnte. Der Aufrufer
     *         muss den bereits abgebuchten Einsatz dann selbst zurueckgeben und das Match
     *         nicht starten. Leere Einsaetze (kein Item, kein Geld) werden uebersprungen
     *         und zaehlen als Erfolg.
     */
    public boolean record(UUID matchId, Stake... incoming) {
        if (matchId == null) {
            return false;
        }
        synchronized (fileLock) {
            if (!loaded) {
                load();
            }
            List<UUID> added = new ArrayList<>();
            if (incoming != null) {
                for (Stake stake : incoming) {
                    if (stake == null || stake.isEmpty() || stake.playerId == null) {
                        continue;
                    }
                    stakes.put(stake.playerId, new Stake(stake.playerId, matchId, stake.items, stake.money, stake.openedAt));
                    added.add(stake.playerId);
                }
            }
            if (added.isEmpty()) {
                return true;
            }
            if (save()) {
                return true;
            }
            for (UUID playerId : added) {
                stakes.remove(playerId);
            }
            return false;
        }
    }

    /** Entfernt den Einsatz eines Spielers, nachdem er ausgezahlt oder zurueckgegeben wurde. */
    public void forget(UUID playerId) {
        if (playerId == null) {
            return;
        }
        synchronized (fileLock) {
            if (stakes.remove(playerId) != null) {
                save();
            }
        }
    }

    public int openCount() {
        synchronized (fileLock) {
            return stakes.size();
        }
    }

    /**
     * Reiht jeden noch offenen Einsatz als Rueckerstattung an denselben Spieler ein
     * und nimmt ihn danach aus dem Journal.
     *
     * <p>Steht derselbe Grund schon in der Payout-Datei (Wiederanlauf wurde beim letzten
     * Mal nach dem Einreihen und vor dem Loeschen unterbrochen), wird nicht ein zweites
     * Mal eingereiht.</p>
     *
     * @return Anzahl neu eingereihter Posten
     */
    public int refundOpen(PendingPayoutStore payouts) {
        if (payouts == null) {
            return 0;
        }
        synchronized (fileLock) {
            if (!loaded) {
                load();
            }
            int queued = 0;
            for (Stake stake : new ArrayList<>(stakes.values())) {
                String reason = CRASH_REFUND_PREFIX + stake.matchId;
                if (!payouts.hasReason(stake.playerId, reason)) {
                    payouts.queue(stake.playerId, stake.items, stake.money, reason);
                    queued++;
                }
                stakes.remove(stake.playerId);
                save();
            }
            if (queued > 0) {
                plugin.getLogger().info("[Wagers] Queued " + queued  // i18n-ignore: technical wager journal log
                        + " crashed wager stake(s) for refund");
            }
            return queued;
        }
    }

    @SuppressWarnings("unchecked")
    private Stake readStake(UUID playerId, ConfigurationSection sec) {
        String matchRaw = sec.getString("match-id");
        UUID matchId;
        try {
            matchId = matchRaw == null ? null : UUID.fromString(matchRaw);
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("[Wagers] Skipping stake with invalid match id: " + matchRaw);  // i18n-ignore: technical wager journal log
            return null;
        }
        if (matchId == null) {
            return null;
        }
        List<ItemStack> items = new ArrayList<>();
        for (Object element : sec.getList("items", new ArrayList<>())) {
            if (element instanceof ItemStack) {
                items.add((ItemStack) element);
            } else if (element instanceof Map<?, ?>) {
                try {
                    items.add(ItemStack.deserialize((Map<String, Object>) element));
                } catch (Exception ignored) {
                    plugin.getLogger().warning("[Wagers] Skipping unreadable item for " + playerId);  // i18n-ignore: technical wager journal log
                }
            }
        }
        double money = sec.getDouble("money", 0);
        long openedAt = sec.getLong("opened-at", System.currentTimeMillis());
        return new Stake(playerId, matchId, items, money, openedAt);
    }

    private boolean save() {
        try {
            YamlConfiguration cfg = new YamlConfiguration();
            cfg.set("version", 1);
            for (Stake stake : stakes.values()) {
                String key = "stakes." + stake.playerId;
                cfg.set(key + ".match-id", stake.matchId.toString());  // i18n-ignore: YAML path fragment in active-wagers.yml
                cfg.set(key + ".money", stake.money);  // i18n-ignore: YAML path fragment in active-wagers.yml
                cfg.set(key + ".opened-at", stake.openedAt);  // i18n-ignore: YAML path fragment in active-wagers.yml
                cfg.set(key + ".items", new ArrayList<>(stake.items));  // i18n-ignore: YAML path fragment in active-wagers.yml
            }
            File dir = plugin.getDataFolder();
            if (!dir.exists() && !dir.mkdirs()) {
                plugin.getLogger().severe("[Wagers] Could not create data folder for " + FILE_NAME);  // i18n-ignore: technical wager journal log
                return false;
            }
            saveAtomic(cfg, file());
            return true;
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "[Wagers] Could not save " + FILE_NAME  // i18n-ignore: technical wager journal log
                    + " - the match must not keep a stake that is only in memory", e);
            return false;
        }
    }

    private static void saveAtomic(YamlConfiguration cfg, File file) throws IOException {
        File tmp = new File(file.getParentFile(), file.getName() + ".tmp");
        cfg.save(tmp);
        try {
            Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (Exception atomicFailed) {
            try {
                Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } catch (Exception moveFailed) {
                cfg.save(file);
            }
        }
    }

    private File file() {
        return new File(plugin.getDataFolder(), FILE_NAME);
    }
}
