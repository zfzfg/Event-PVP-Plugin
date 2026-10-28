package de.zfzfg.core.reward;

import de.zfzfg.eventplugin.EventPlugin;
import de.zfzfg.core.util.AtomicFiles;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.logging.Level;

/** Durable handoff ledger. An interrupted external effect is held for review, never replayed blindly. */
public final class PendingPayoutStore {
    public enum PayoutResult { DELIVERED, QUEUED_DURABLE, FAILED, REVIEW_REQUIRED;
        public boolean accepted() { return this != FAILED; }
    }
    private enum State { PENDING, IN_FLIGHT, DONE, REVIEW_REQUIRED }
    private final EventPlugin plugin;
    private final File file;
    private final Map<String, Entry> entries = new LinkedHashMap<>();
    private final List<Object> quarantine = new ArrayList<>();
    private boolean writable = true;
    private long failedWrites;

    private static final class Entry {
        String id;
        UUID recipient;
        PendingPayout payout;
        List<ItemStack> remaining;
        List<String> sources = new ArrayList<>();
        List<String> commands = new ArrayList<>();
        List<State> commandStates = new ArrayList<>();
        State items = State.PENDING, money = State.PENDING;
        boolean durable;
        boolean done() { return items == State.DONE && money == State.DONE && commandStates.stream().allMatch(s -> s == State.DONE); }
        boolean review() { return items == State.REVIEW_REQUIRED || money == State.REVIEW_REQUIRED || commandStates.contains(State.REVIEW_REQUIRED); }
    }

    public PendingPayoutStore(EventPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "pending-payouts.yml");
    }

    public synchronized void load() {
        entries.clear(); quarantine.clear(); writable = true;
        if (!file.isFile()) return;
        try {
            YamlConfiguration cfg = new YamlConfiguration();
            cfg.load(file);
            if (cfg.getInt("version") == 2) {
                quarantine.addAll(cfg.getList("quarantine", List.of()));
                for (Map<?, ?> raw : cfg.getMapList("records")) {
                    try { Entry entry = readEntry(raw);
                        if (entry.id.isBlank() || entry.id.equals("null") || entries.containsKey(entry.id)) throw new IllegalArgumentException("Invalid or duplicate payout ID");
                        entries.put(entry.id, entry);
                    }
                    catch (Exception ex) { quarantine.add(raw); plugin.getLogger().warning("[Payouts] Unreadable record retained for review"); }
                }
            } else {
                if (!Files.exists(file.toPath().resolveSibling(file.getName() + ".v1-backup")))
                    Files.copy(file.toPath(), file.toPath().resolveSibling(file.getName() + ".v1-backup"), StandardCopyOption.COPY_ATTRIBUTES);
                for (String key : cfg.getKeys(false)) {
                    try {
                        UUID recipient = UUID.fromString(key);
                        for (Map<?, ?> raw : cfg.getMapList(key)) {
                            try { validateStoredPayload(raw); } catch (IllegalArgumentException ex) {
                                quarantine.add(Map.of("recipient", key, "record", raw)); continue;
                            }
                            PendingPayout payout = PendingPayout.fromMap(raw);
                            if (payout == null) { quarantine.add(Map.of("recipient", key, "record", raw)); continue; }
                            Entry entry = create(UUID.randomUUID().toString(), recipient, payout, List.of(), List.of());
                            entries.put(entry.id, entry);
                        }
                    } catch (Exception ex) { quarantine.add(Map.of("key", key, "value", String.valueOf(cfg.get(key)))); }
                }
            }
            for (Entry e : entries.values()) {
                String source = debitSource(e.id);
                if (source != null && ownsSource(source)) { e.items = State.DONE; e.money = State.DONE; }
            }
            if (!save()) throw new IOException("Could not persist payout recovery");
            if (!quarantine.isEmpty()) {
                writable = false;
                plugin.getLogger().severe("[Payouts] Quarantined records retained; resolve damaged ledger before new payouts");
            }
        } catch (Exception ex) {
            writable = false;
            plugin.getLogger().log(Level.SEVERE, "[Payouts] Ledger unavailable; refusing payout mutations", ex);
        }
    }

    private Entry create(String id, UUID recipient, PendingPayout payout, List<String> sources, List<String> commands) {
        Entry e = new Entry(); e.id = id; e.recipient = recipient; e.payout = payout;
        e.remaining = new ArrayList<>(payout.items()); e.sources.addAll(sources); e.commands.addAll(commands);
        commands.forEach(c -> e.commandStates.add(State.PENDING));
        if (e.remaining.isEmpty()) e.items = State.DONE;
        if (payout.money() <= 0) e.money = State.DONE;
        return e;
    }

    @SuppressWarnings("unchecked")
    private Entry readEntry(Map<?, ?> raw) {
        Map<?, ?> data = (Map<?, ?>) raw.get("payout");
        validateStoredPayload(data);
        PendingPayout payout = PendingPayout.fromMap(data);
        // Completed receipts and command-only records have an empty payload.
        if (payout == null) payout = PendingPayout.of(List.of(), 0, String.valueOf(data.get("reason")));
        Entry e = create(String.valueOf(raw.get("id")), UUID.fromString(String.valueOf(raw.get("recipient"))), payout,
                (List<String>) raw.get("sources"), (List<String>) raw.get("commands"));
        e.items = recovered(raw.get("itemsState")); e.money = recovered(raw.get("moneyState"));
        e.commandStates.clear();
        for (Object state : (List<?>) raw.get("commandStates")) e.commandStates.add(recovered(state));
        if (e.commands.size() != e.commandStates.size()) throw new IllegalArgumentException("command state count");
        e.durable = true;
        return e;
    }
    private static void validateStoredPayload(Map<?, ?> data) {
        if (data == null) throw new IllegalArgumentException("Missing payload");
        Object money = data.get("money");
        if (!(money instanceof Number n) || !Double.isFinite(n.doubleValue()) || n.doubleValue() < 0)
            throw new IllegalArgumentException("Invalid stored money");
        Object rawItems = data.get("items");
        if (!(rawItems instanceof List<?> items)) throw new IllegalArgumentException("Invalid stored items");
        PendingPayout decoded = PendingPayout.fromMap(data);
        if (!items.isEmpty() && (decoded == null || decoded.items().size() != items.size()))
            throw new IllegalArgumentException("Unreadable stored items");
    }

    private State recovered(Object raw) {
        State state = State.valueOf(String.valueOf(raw));
        return state == State.IN_FLIGHT ? State.REVIEW_REQUIRED : state;
    }

    private boolean save() {
        if (!writable) return false;
        try {
            YamlConfiguration cfg = new YamlConfiguration(); cfg.set("version", 2);
            List<Map<String, Object>> records = new ArrayList<>();
            for (Entry e : entries.values()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", e.id); row.put("recipient", e.recipient.toString());
                Map<String, Object> payload = e.payout.toMap(); payload.put("items", new ArrayList<>(e.remaining));
                row.put("payout", payload); row.put("sources", e.sources); row.put("commands", e.commands);
                row.put("itemsState", e.items.name()); row.put("moneyState", e.money.name());
                row.put("commandStates", e.commandStates.stream().map(Enum::name).toList()); records.add(row);
            }
            cfg.set("records", records); cfg.set("quarantine", quarantine);
            AtomicFiles.save(cfg, file.toPath());
            entries.values().forEach(e -> e.durable = true);
            return true;
        } catch (Exception ex) {
            failedWrites++;
            plugin.getLogger().log(Level.SEVERE, "[Payouts] Could not persist ledger; external effects stopped", ex);
            return false;
        }
    }

    /** Persist uncertainty BEFORE invoking Vault. A crash cannot erase the debit intent. */
    public synchronized boolean beginDebit(String id, UUID recipient, double money) {
        return beginDebit(id, recipient, List.of(), money);
    }
    public synchronized boolean beginDebit(String id, UUID recipient, List<ItemStack> items, double money) {
        if (entries.containsKey(id)) return false;
        Entry e = create(id, recipient, PendingPayout.of(items, money, "debit-review"), List.of(), List.of());
        if (money > 0) e.money = State.REVIEW_REQUIRED;
        if (!items.isEmpty()) e.items = State.REVIEW_REQUIRED;
        entries.put(id, e);
        if (save()) return true;
        entries.remove(id); return false;
    }
    public synchronized boolean completeDebit(String id) {
        Entry e = entries.get(id); if (e == null) return true;
        State previous = e.money, previousItems = e.items; e.money = State.DONE; e.items = State.DONE;
        if (save()) return true;
        e.money = previous; e.items = previousItems; return false;
    }

    public synchronized PayoutResult submit(String id, UUID recipient, Player player, List<ItemStack> items,
            double money, String reason, boolean inventoryReady, List<String> sources, List<String> commands) {
        if (!writable || id == null || recipient == null || !Double.isFinite(money) || money < 0) return PayoutResult.FAILED;
        Entry e = entries.get(id);
        boolean created = e == null;
        if (e == null) {
            // A source can fund exactly one receipt, including a completed receipt.
            if (sources.stream().anyMatch(this::ownsSource)) return PayoutResult.FAILED;
            e = create(id, recipient, PendingPayout.of(items, money, reason), sources, commands);
            entries.put(id, e);
        } else if (!e.recipient.equals(recipient)) return PayoutResult.FAILED;
        if (!save()) { if (created) entries.remove(id); return PayoutResult.FAILED; }
        if (player != null && player.isOnline() && inventoryReady) return deliver(e, player);
        return result(e);
    }

    private PayoutResult result(Entry e) {
        return e.review() ? PayoutResult.REVIEW_REQUIRED : e.done() ? PayoutResult.DELIVERED : PayoutResult.QUEUED_DURABLE;
    }
    private PayoutResult deliver(Entry e, Player player) {
        if (!writable || !e.durable) return PayoutResult.FAILED;
        if (e.items == State.PENDING) {
            e.items = State.IN_FLIGHT;
            if (!save()) { e.items = State.PENDING; return PayoutResult.FAILED; }
            try {
                Map<Integer, ItemStack> left = player.getInventory().addItem(e.remaining.stream().map(ItemStack::clone).toArray(ItemStack[]::new));
                e.remaining = new ArrayList<>(left.values());
                e.items = e.remaining.isEmpty() ? State.DONE : State.PENDING;
            } catch (Exception ex) { e.items = State.REVIEW_REQUIRED; }
            if (!save()) { e.items = State.REVIEW_REQUIRED; return PayoutResult.REVIEW_REQUIRED; }
        }
        if (e.money == State.PENDING && plugin.hasEconomy()) {
            e.money = State.IN_FLIGHT;
            if (!save()) { e.money = State.PENDING; return PayoutResult.FAILED; }
            try {
                net.milkbowl.vault.economy.EconomyResponse response = plugin.getEconomy().depositPlayer(player, e.payout.money());
                e.money = response != null && response.transactionSuccess() ? State.DONE : response == null ? State.REVIEW_REQUIRED : State.PENDING;
            } catch (Exception ex) { e.money = State.REVIEW_REQUIRED; }
            if (!save()) { e.money = State.REVIEW_REQUIRED; return PayoutResult.REVIEW_REQUIRED; }
        }
        for (int i = 0; i < e.commands.size(); i++) {
            if (e.commandStates.get(i) != State.PENDING) continue;
            e.commandStates.set(i, State.IN_FLIGHT);
            if (!save()) { e.commandStates.set(i, State.PENDING); return PayoutResult.FAILED; }
            try {
                boolean accepted = org.bukkit.Bukkit.dispatchCommand(org.bukkit.Bukkit.getConsoleSender(), e.commands.get(i));
                e.commandStates.set(i, accepted ? State.DONE : State.REVIEW_REQUIRED);
            } catch (Exception ex) { e.commandStates.set(i, State.REVIEW_REQUIRED); }
            if (!save()) { e.commandStates.set(i, State.REVIEW_REQUIRED); return PayoutResult.REVIEW_REQUIRED; }
        }
        return result(e);
    }

    /** Compatibility API: failure must propagate so callers cannot discard the source. */
    public boolean deliverOrQueue(Player player, List<ItemStack> items, double money, String reason, boolean ready) {
        PayoutResult result = submit(UUID.randomUUID().toString(), player == null ? null : player.getUniqueId(), player,
                items, money, reason, ready, List.of(), List.of());
        if (result == PayoutResult.FAILED) throw new IllegalStateException("Payout handoff failed");
        return result == PayoutResult.DELIVERED;
    }
    public void queue(UUID player, List<ItemStack> items, double money, String reason) {
        PayoutResult result = submit(UUID.randomUUID().toString(), player, null, items, money, reason, false, List.of(), List.of());
        if (result == PayoutResult.FAILED) throw new IllegalStateException("Payout handoff failed");
    }
    public synchronized int deliverAll(Player player) {
        if (player == null || !player.isOnline()) return 0;
        if (!save()) return 0;
        int delivered = 0;
        for (Entry e : entries.values()) {
            if (e.recipient.equals(player.getUniqueId()) && !e.done() && deliver(e, player) == PayoutResult.DELIVERED) delivered++;
        }
        return delivered;
    }
    public synchronized boolean hasPending(UUID player) { return entries.values().stream().anyMatch(e -> e.recipient.equals(player) && !e.done()); }
    public synchronized boolean hasReason(UUID player, String reason) { return entries.values().stream().anyMatch(e -> e.durable && e.recipient.equals(player) && e.payout.reason().equals(reason)); }
    public synchronized boolean ownsSource(String source) { return entries.values().stream().anyMatch(e -> e.durable && e.sources.contains(source)); }
    public synchronized Map<UUID, List<PendingPayout>> snapshot() {
        Map<UUID, List<PendingPayout>> copy = new LinkedHashMap<>();
        entries.values().stream().filter(e -> !e.done()).forEach(e -> copy.computeIfAbsent(e.recipient, k -> new ArrayList<>()).add(PendingPayout.of(e.remaining, e.money == State.DONE ? 0 : e.payout.money(), e.payout.reason())));
        copy.replaceAll((id, list) -> List.copyOf(list)); return Collections.unmodifiableMap(copy);
    }
    public synchronized List<String> describe(String id) {
        return entries.values().stream().filter(e -> id == null ? !e.done() : e.id.equals(id))
                .map(e -> e.id + " recipient=" + e.recipient + " items=" + e.items + " money=" + e.money + " commands=" + e.commandStates + " reason=" + e.payout.reason() + " amount=" + e.payout.money() + " stacks=" + e.remaining.size() + " sources=" + e.sources).toList();
    }
    private static String debitSource(String id) {
        if (id.startsWith("debit:")) return id.substring("debit:".length());
        if (id.startsWith("item-debit:")) return id.substring("item-debit:".length());
        return null;
    }
    public synchronized boolean resolve(String id, boolean delivered, String actor) {
        Entry e = entries.get(id); if (e == null || !e.review()) return false;
        String consumedSource = debitSource(id);
        if (consumedSource != null && ownsSource(consumedSource)) return completeDebit(id);
        State next = delivered ? State.DONE : State.PENDING;
        State oldItems = e.items, oldMoney = e.money;
        List<State> oldCommands = new ArrayList<>(e.commandStates);
        if (e.items == State.REVIEW_REQUIRED) e.items = next;
        if (e.money == State.REVIEW_REQUIRED) e.money = next;
        e.commandStates.replaceAll(s -> s == State.REVIEW_REQUIRED ? next : s);
        if (!save()) { e.items = oldItems; e.money = oldMoney; e.commandStates = oldCommands; return false; }
        plugin.getLogger().warning("[Payouts] " + actor + " resolved " + id + " as " + next);
        return true;
    }
    public synchronized Map<String, Object> status() {
        return Map.of("open", entries.values().stream().filter(e -> !e.done()).count(), "review", entries.values().stream().filter(Entry::review).count(), "quarantine", quarantine.size(), "failedWrites", failedWrites, "available", writable);
    }

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

}
