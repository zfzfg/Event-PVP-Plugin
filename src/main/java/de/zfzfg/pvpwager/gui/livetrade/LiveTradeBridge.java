package de.zfzfg.pvpwager.gui.livetrade;

import de.zfzfg.eventplugin.EventPlugin;
import de.zfzfg.pvpwager.models.Arena;
import de.zfzfg.pvpwager.models.CommandRequest;
import de.zfzfg.pvpwager.models.EquipmentSet;
import de.zfzfg.pvpwager.utils.MessageUtil;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Brücke zwischen eingehenden Chat-Herausforderungen (CommandRequest) und
 * dem modernen Echtzeit-LiveTrade-System.
 *
 * <p>Ermöglicht es, wenn ein Spieler auf eine Wager-Herausforderung im Chat
 * reagiert (z.B. per {@code /pvprespond gui}), direkt eine synchrone
 * {@link LiveTradeSession} zu eröffnen, die bereits mit den Wager-Items,
 * dem Geldbetrag, der Arena und dem Equipment des Herausforderers vorbefüllt ist.</p>
 */
public class LiveTradeBridge {

    private final EventPlugin plugin;

    public LiveTradeBridge(EventPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Erstellt und startet eine LiveTrade-Sitzung aus einer bestehenden Chat-Herausforderung.
     *
     * @param request die ausstehende Wette-Anfrage
     * @return true wenn die Session erfolgreich gestartet wurde
     */
    public boolean startSessionFromRequest(CommandRequest request) {
        if (request == null) return false;

        Player sender = request.getSender();
        Player target = request.getTarget();

        if (sender == null || !sender.isOnline() || target == null || !target.isOnline()) {
            return false;
        }

        LiveTradeManager manager = plugin.getLiveTradeManager();
        if (manager == null) return false;

        // Prüfe ob bereits in Session oder Match
        if (manager.isInSession(sender) || manager.isInSession(target)) {
            MessageUtil.sendMessage(target, getMsg("both-in-session"));
            return false;
        }

        if (plugin.getMatchManager().isPlayerInMatch(sender) || plugin.getMatchManager().isPlayerInMatch(target)) {
            MessageUtil.sendMessage(target, getMsg("both-in-match"));
            return false;
        }

        // Session erstellen
        LiveTradeSession session = manager.createSession(sender, target);
        if (session == null) {
            return false;
        }

        // Herausforderer-Einsätze vorbefüllen
        LiveTradePlayer tradePlayer1 = session.getPlayer1();
        try {
            if (request.getWagerItems() != null) {
                if (request.getWagerItems().size() > 12) { session.abort(); return false; }
                var taken = de.zfzfg.core.reward.StakeService.take(sender.getInventory(), request.getWagerItems());
                for (ItemStack item : taken) tradePlayer1.addWagerItem(item);
            }
        } catch (IllegalArgumentException ex) {
            session.abort();
            return false;
        }

        if (request.getMoney() > 0) {
            if (!tradePlayer1.setWagerMoney(request.getMoney())) { session.abort(); return false; }
        }

        // Arena & Equipment übernehmen
        if (request.getArenaId() != null) {
            Arena arena = plugin.getArenaManager().getArena(request.getArenaId());
            if (arena != null) {
                session.setSelectedArena(arena);
            }
        }

        if (request.getEquipmentId() != null) {
            EquipmentSet equip = plugin.getEquipmentManager().getEquipmentSet(request.getEquipmentId());
            if (equip != null) {
                session.setSelectedEquipment(equip);
            }
        }

        try {
            session.start();
            plugin.getCommandRequestManager().removeRequest(sender);
            return true;
        } catch (Exception ex) {
            session.abort();
            plugin.getLogger().log(java.util.logging.Level.WARNING, "Trade initialization failed", ex);
            return false;
        }
    }

    /**
     * Startet eine frische LiveTrade-Sitzung zwischen zwei Spielern.
     */
    public boolean startSession(Player challenger, Player target) {
        if (challenger == null || target == null) return false;
        LiveTradeManager manager = plugin.getLiveTradeManager();
        if (manager == null) return false;

        LiveTradeSession session = manager.createSession(challenger, target);
        if (session == null) return false;

        session.start();
        return true;
    }

    private String getMsg(String key) {
        String val = plugin.getCoreConfigManager().getMessages().getString("messages.livetrade." + key, null);
        if (val != null) return MessageUtil.color(val);
        return MessageUtil.color("&c[missing: " + key + "]");
    }
}
