import de.zfzfg.core.config.CoreConfigManager;
import de.zfzfg.core.monitoring.debug.DebugManager;
import de.zfzfg.core.reward.ActiveWagerJournal;
import de.zfzfg.core.reward.PendingPayoutStore;
import de.zfzfg.core.service.ConfigurationService;
import de.zfzfg.core.web.WebApiHandler;
import de.zfzfg.core.web.WebAuthManager;
import de.zfzfg.core.web.WebConfigManager;
import de.zfzfg.eventplugin.EventPlugin;
import de.zfzfg.pvpwager.gui.livetrade.*;
import de.zfzfg.pvpwager.managers.*;
import de.zfzfg.pvpwager.models.*;
import de.zfzfg.test.mocks.MockEconomy;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Server;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.lang.reflect.Field;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.logging.*;
import static org.mockito.Mockito.*;

/** Diagnostic assertions deliberately describe the current defects, not desired behavior. */
public class PlanAuditProbe {
    static final Logger LOG = Logger.getLogger("PlanAuditProbe");
    static int confirmed;
    static { LOG.setLevel(Level.OFF); }
    interface Probe { void run() throws Exception; }
    static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
    static void probe(String id, Probe body) throws Exception {
        body.run();
        confirmed++;
        System.out.println("CONFIRMED " + id);
    }

    static class Fixture implements AutoCloseable {
        final ServerMock server = MockBukkit.mock();
        final PlayerMock first = server.addPlayer("AuditAlice");
        final PlayerMock second = server.addPlayer("AuditBob");
        final EventPlugin plugin = mock(EventPlugin.class);
        final Path folder;
        final MatchManager matches;
        final ArenaManager arenas = mock(ArenaManager.class);
        final EquipmentManager equipment = mock(EquipmentManager.class);
        final Arena arena = mock(Arena.class);
        final EquipmentSet kit = mock(EquipmentSet.class);
        Fixture() throws Exception {
            folder = Files.createTempDirectory(Path.of("target/cocoindex-plan-audit"), "case-");
            when(plugin.getDataFolder()).thenReturn(folder.toFile());
            when(plugin.getLogger()).thenReturn(LOG);
            when(plugin.getName()).thenReturn("PlanAuditProbe");
            when(plugin.isEnabled()).thenReturn(true);
            when(plugin.getServer()).thenReturn(server);
            CoreConfigManager core = mock(CoreConfigManager.class);
            when(plugin.getCoreConfigManager()).thenReturn(core);
            when(core.getMessages()).thenReturn(new YamlConfiguration());
            when(plugin.getDebugManager()).thenReturn(mock(DebugManager.class));
            when(plugin.getPvpConfigManager()).thenReturn(mock(de.zfzfg.pvpwager.managers.ConfigManager.class));
            when(plugin.getPvpConfigManager().getConfig()).thenReturn(new YamlConfiguration());
            when(plugin.getArenaManager()).thenReturn(arenas);
            when(plugin.getEquipmentManager()).thenReturn(equipment);
            when(arenas.getArenaOptional("audit-arena")).thenReturn(Optional.of(arena));
            when(arena.getId()).thenReturn("audit-arena");
            when(arena.getDisplayName()).thenReturn("Audit Arena");
            when(arena.getArenaWorld()).thenReturn("audit-world");
            when(equipment.getEquipmentSet("audit-kit")).thenReturn(kit);
            when(kit.getId()).thenReturn("audit-kit");
            when(kit.getDisplayName()).thenReturn("Audit Kit");
            matches = new MatchManager(plugin);
            when(plugin.getMatchManager()).thenReturn(matches);
            ActiveWagerJournal journal = new ActiveWagerJournal(plugin);
            journal.load();
            when(plugin.getActiveWagers()).thenReturn(journal);
        }
        CommandRequest request(List<ItemStack> items, double firstMoney, double secondMoney) {
            CommandRequest r = new CommandRequest(first, second);
            r.setWagerItems(items);
            r.setMoney(firstMoney);
            r.setTargetWagerMoney(secondMoney);
            r.setFinalArenaId("audit-arena");
            r.setFinalEquipmentId("audit-kit");
            return r;
        }
        LiveTradeSession session() { return new LiveTradeSession(plugin, first, second); }
        PendingPayoutStore payouts() {
            PendingPayoutStore store = new PendingPayoutStore(plugin);
            store.load();
            return store;
        }
        void failedEconomy() {
            Economy economy = mock(Economy.class);
            when(plugin.hasEconomy()).thenReturn(true);
            when(plugin.getEconomy()).thenReturn(economy);
            when(economy.depositPlayer(any(Player.class), anyDouble())).thenReturn(
                    new EconomyResponse(0, 0, EconomyResponse.ResponseType.FAILURE, "injected failure"));
        }
        WebConfigManager webConfig() throws Exception {
            Files.writeString(folder.resolve("web-config.yml"), "web:\n  enabled: false\n");
            return new WebConfigManager(plugin);
        }
        public void close() { MockBukkit.unmock(); }
    }

    static int diamonds(Player player) {
        return Arrays.stream(player.getInventory().getStorageContents())
                .filter(Objects::nonNull).filter(i -> i.getType() == Material.DIAMOND)
                .mapToInt(ItemStack::getAmount).sum();
    }

    public static void main(String[] args) throws Exception {
        Files.createDirectories(Path.of("target/cocoindex-plan-audit"));
        probe("01_gui_items_deducted_twice", () -> {
            try (Fixture f = new Fixture()) {
                f.first.getInventory().setItem(0, new ItemStack(Material.DIAMOND, 8));
                f.first.getInventory().setItem(1, new ItemStack(Material.DIAMOND, 8));
                LiveTradeSession session = f.session();
                LiveTradeGui gui = new LiveTradeGui(session, session.getPlayer1());
                InventoryClickEvent click = mock(InventoryClickEvent.class);
                when(click.getRawSlot()).thenReturn(54);
                when(click.isShiftClick()).thenReturn(true);
                when(click.getCurrentItem()).thenReturn(f.first.getInventory().getItem(0));
                doAnswer(a -> { f.first.getInventory().setItem(0, a.getArgument(0)); return null; })
                        .when(click).setCurrentItem(any());
                gui.handleClick(click);
                check(diamonds(f.first) == 8, "GUI must have removed only offered stack");
                f.matches.startMatchFromCommand(f.request(session.getPlayer1().getWagerItems(), 0, 0));
                check(diamonds(f.first) == 0, "unexpected behavior: spare stack not deducted");
                check(f.matches.isPlayerInMatch(f.first), "match preparation did not proceed");
            }
        });
        probe("02_request_prefill_duplicates_items_on_abort", () -> {
            try (Fixture f = new Fixture()) {
                f.first.getInventory().addItem(new ItemStack(Material.DIAMOND, 8));
                LiveTradeSession session = spy(f.session());
                doNothing().when(session).start(); // Suppress opening visuals, keep real ownership/abort logic.
                LiveTradeManager manager = mock(LiveTradeManager.class);
                when(manager.createSession(f.first, f.second)).thenReturn(session);
                when(f.plugin.getLiveTradeManager()).thenReturn(manager);
                when(f.plugin.getCommandRequestManager()).thenReturn(mock(CommandRequestManager.class));
                CommandRequest request = new CommandRequest(f.first, f.second,
                        List.of(new ItemStack(Material.DIAMOND, 8)), 0, null, null);
                check(new LiveTradeBridge(f.plugin).startSessionFromRequest(request), "bridge did not start");
                check(diamonds(f.first) == 8, "request prefill unexpectedly removed originals");
                session.abort();
                check(diamonds(f.first) == 16, "prefill copies were not handed out on abort");
            }
        });
        probe("03_silent_start_failure_orphans_gui_stake", () -> {
            try (Fixture f = new Fixture()) {
                LiveTradeSession session = f.session();
                session.getPlayer1().addWagerItem(new ItemStack(Material.DIAMOND, 8));
                Arena missing = mock(Arena.class);
                when(missing.getId()).thenReturn("missing");
                session.setSelectedArena(missing);
                session.setSelectedEquipment(f.kit);
                var completion = LiveTradeSession.class.getDeclaredMethod("completeAndStartMatch");
                completion.setAccessible(true);
                completion.invoke(session); // Exact callback used after countdown completion.
                f.server.getScheduler().performTicks(2);
                check(session.isEnded(), "session not ended");
                check(!f.matches.isPlayerInMatch(f.first), "unexpected match exists");
                check(diamonds(f.first) == 0 && session.getPlayer1().getWagerItemCount() == 1,
                        "stake was unexpectedly returned or transferred");
            }
        });
        probe("04_rejected_second_withdraw_still_starts_match", () -> {
            try (Fixture f = new Fixture()) {
                MockEconomy economy = spy(new MockEconomy());
                economy.setBalance(f.first.getUniqueId(), 1000);
                economy.setBalance(f.second.getUniqueId(), 1000);
                when(f.plugin.hasEconomy()).thenReturn(true);
                when(f.plugin.getEconomy()).thenReturn(economy);
                doReturn(new EconomyResponse(0, 1000, EconomyResponse.ResponseType.FAILURE, "injected rejection"))
                        .when(economy).withdrawPlayer(f.second, 50);
                f.matches.startMatchFromCommand(f.request(List.of(), 100, 50));
                check(economy.getBalance(f.first) == 900 && economy.getBalance(f.second) == 1000,
                        "withdrawal setup wrong");
                check(f.matches.isPlayerInMatch(f.first), "match did not proceed despite failed withdrawal");
                check(f.plugin.getActiveWagers().openCount() == 2, "journal did not include failed stake");
            }
        });
        probe("05_rejected_deposit_reported_as_delivered", () -> {
            try (Fixture f = new Fixture()) {
                f.failedEconomy();
                PendingPayoutStore store = f.payouts();
                check(store.deliverOrQueue(f.first, List.of(), 20, "audit", true), "delivery not reported successful");
                verify(f.plugin.getEconomy()).depositPlayer(f.first, 20);
                check(!store.hasPending(f.first.getUniqueId()), "rejected money unexpectedly retained");
            }
        });
        probe("06_delivery_exception_deletes_untouched_rewards", () -> {
            try (Fixture f = new Fixture()) {
                f.failedEconomy();
                when(f.plugin.getEconomy().depositPlayer(f.first, 10)).thenThrow(new IllegalStateException("injected"));
                PendingPayoutStore store = f.payouts();
                store.queue(f.first.getUniqueId(), List.of(), 10, "first");
                store.queue(f.first.getUniqueId(), List.of(), 20, "untouched");
                try { store.deliverAll(f.first); throw new AssertionError("expected injected exception"); }
                catch (IllegalStateException expected) { }
                PendingPayoutStore reloaded = f.payouts();
                check(!reloaded.hasPending(f.first.getUniqueId()), "unprocessed rewards survived unexpectedly");
                verify(f.plugin.getEconomy(), never()).depositPlayer(f.first, 20);
            }
        });
        probe("07_failed_payout_save_erases_crash_refund_journal", () -> {
            try (Fixture f = new Fixture()) {
                UUID id = f.first.getUniqueId();
                UUID matchId = UUID.randomUUID();
                check(f.plugin.getActiveWagers().record(matchId,
                        new ActiveWagerJournal.Stake(id, matchId, List.of(), 30)), "could not record stake");
                Files.createDirectory(f.folder.resolve("pending-payouts.yml"));
                f.plugin.getActiveWagers().refundOpen(f.payouts());
                ActiveWagerJournal restarted = new ActiveWagerJournal(f.plugin);
                restarted.load();
                check(restarted.openCount() == 0, "journal unexpectedly retained failed transfer");
                check(!f.payouts().hasPending(id), "refund unexpectedly survived restart");
            }
        });
        probe("08_full_inventory_destroys_returned_items", () -> {
            try (Fixture f = new Fixture()) {
                for (int slot = 0; slot < 36; slot++)
                    f.first.getInventory().setItem(slot, new ItemStack(Material.COBBLESTONE, 64));
                LiveTradeSession session = f.session();
                session.getPlayer1().addWagerItem(new ItemStack(Material.DIAMOND, 8));
                session.getPlayer1().returnItems();
                check(diamonds(f.first) == 0 && session.getPlayer1().getWagerItemCount() == 0,
                        "return did not lose item");
                check(f.first.getWorld().getEntities().stream().noneMatch(e -> e instanceof org.bukkit.entity.Item),
                        "leftovers unexpectedly dropped");
            }
        });
        probe("09_parallel_login_consumes_one_token_twice", () -> {
            JavaPlugin plugin = mock(JavaPlugin.class);
            Server server = mock(Server.class);
            when(plugin.getServer()).thenReturn(server);
            when(plugin.getLogger()).thenReturn(LOG);
            when(server.getScheduler()).thenReturn(mock(BukkitScheduler.class));
            WebAuthManager auth = new WebAuthManager(plugin, "audit.web");
            Player player = mock(Player.class);
            when(player.isOp()).thenReturn(true);
            when(player.getUniqueId()).thenReturn(UUID.randomUUID());
            when(player.getName()).thenReturn("AuditAdmin");
            String token = auth.generateToken(player);
            Field field = WebAuthManager.class.getDeclaredField("activeTokens");
            field.setAccessible(true);
            @SuppressWarnings("unchecked") Map<String, Object> original = (Map<String, Object>)field.get(auth);
            CyclicBarrier bothReads = new CyclicBarrier(2);
            var gated = new ConcurrentHashMap<String, Object>() {
                @Override public Object get(Object key) {
                    Object value = super.get(key);
                    if (token.equals(key) && value != null) {
                        try { bothReads.await(5, TimeUnit.SECONDS); }
                        catch (Exception e) { throw new RuntimeException(e); }
                    }
                    return value;
                }
            };
            gated.putAll(original);
            field.set(auth, gated); // Force the valid interleaving: both get() calls precede either remove().
            try (ExecutorService threads = Executors.newFixedThreadPool(2)) {
                Future<String> a = threads.submit(() -> auth.validateTokenAndCreateSession(token, "127.0.0.1"));
                Future<String> b = threads.submit(() -> auth.validateTokenAndCreateSession(token, "127.0.0.1"));
                check(a.get(6, TimeUnit.SECONDS) != null && b.get(6, TimeUnit.SECONDS) != null,
                        "duplicate session not reproduced");
                check(auth.getActiveSessionCount() == 2, "expected two sessions");
            }
        });
        probe("10_web_world_save_reports_success_after_io_failure", () -> {
            try (Fixture f = new Fixture()) {
                WebConfigManager config = f.webConfig();
                Files.createDirectory(f.folder.resolve("worlds.yml"));
                Map<String, Object> response = new WebApiHandler(f.plugin, config)
                        .saveWorlds(Map.of("data", Map.of("worlds", Map.of("audit", Map.of("enabled", true)))));
                check(Boolean.TRUE.equals(response.get("success")), "API did not report false success");
                check(Files.isDirectory(f.folder.resolve("worlds.yml")), "failure injection wrong");
            }
        });
        probe("11_web_save_mutates_config_before_sync_dispatch", () -> {
            try (Fixture f = new Fixture()) {
                YamlConfiguration active = new YamlConfiguration();
                active.set("language", "en");
                AtomicBoolean dispatched = new AtomicBoolean();
                AtomicReference<String> configThread = new AtomicReference<>();
                AtomicBoolean readBeforeDispatch = new AtomicBoolean();
                when(f.plugin.getConfig()).thenAnswer(a -> {
                    configThread.set(Thread.currentThread().getName());
                    readBeforeDispatch.set(!dispatched.get());
                    return active;
                });
                Server mockServer = mock(Server.class);
                BukkitScheduler scheduler = mock(BukkitScheduler.class);
                when(mockServer.getScheduler()).thenReturn(scheduler);
                when(f.plugin.getServer()).thenReturn(mockServer);
                when(f.plugin.getConfigurationService()).thenReturn(mock(ConfigurationService.class));
                when(f.plugin.hasLiveGameplay()).thenReturn(true);
                doAnswer(a -> {
                    dispatched.set(true);
                    return CompletableFuture.completedFuture(((Callable<?>)a.getArgument(1)).call());
                }).when(scheduler).callSyncMethod(eq(f.plugin), any());
                WebApiHandler api = new WebApiHandler(f.plugin, f.webConfig());
                try (ExecutorService threads = Executors.newSingleThreadExecutor(r -> new Thread(r, "audit-http"))) {
                    Map<String, Object> response = threads.submit(() -> api.saveConfig(
                            Map.of("data", Map.of("language", "de")))).get(5, TimeUnit.SECONDS);
                    check(Boolean.TRUE.equals(response.get("reloadDeferred")), "deferred branch not exercised");
                }
                check(readBeforeDispatch.get() && "audit-http".equals(configThread.get()), "save was dispatched first");
                check("de".equals(active.getString("language")), "active plugin config not changed");
                verify(f.plugin).noteDeferredConfigReload();
                verify(f.plugin.getConfigurationService(), never()).reloadAll();
            }
        });
        probe("12_command_items_can_be_missing_but_full_stake_recorded", () -> {
            try (Fixture f = new Fixture()) {
                f.first.getInventory().addItem(new ItemStack(Material.DIAMOND, 8));
                f.matches.startMatchFromCommand(f.request(List.of(
                        new ItemStack(Material.DIAMOND, 8), new ItemStack(Material.DIAMOND, 8)), 0, 0));
                check(diamonds(f.first) == 0 && f.matches.isPlayerInMatch(f.first), "preparation did not proceed");
                PendingPayoutStore payouts = f.payouts();
                f.plugin.getActiveWagers().refundOpen(payouts);
                int refunded = payouts.snapshot().get(f.first.getUniqueId()).stream()
                        .flatMap(p -> p.items().stream()).mapToInt(ItemStack::getAmount).sum();
                check(refunded == 16, "full offered quantity was not recorded despite missing half");
            }
        });
        probe("13_missing_economy_discards_queued_money", () -> {
            try (Fixture f = new Fixture()) {
                PendingPayoutStore payouts = f.payouts();
                payouts.queue(f.first.getUniqueId(), List.of(), 25, "economy-unavailable");
                check(payouts.deliverAll(f.first) == 1, "delivery not reported successful");
                check(!f.payouts().hasPending(f.first.getUniqueId()), "money survived unexpectedly");
            }
        });
        System.out.println("DIAGNOSTIC_CASES_CONFIRMED=" + confirmed);
    }
}
