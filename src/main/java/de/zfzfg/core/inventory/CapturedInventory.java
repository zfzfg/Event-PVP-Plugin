package de.zfzfg.core.inventory;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

/**
 * Ein im Speicher gehaltener Inventar-Abzug.
 *
 * <p>Der Abzug wird <b>synchron</b> genommen ({@link #of(Player)}), damit direkt danach
 * gecleart werden darf. Genau das ist die Invariante I2 des Integrationsplans: nie clearen,
 * ohne den Zustand vorher erfasst zu haben. Der Weg auf die Platte laeuft asynchron und darf
 * nachlaufen - dieses Objekt ist bis dahin der Rettungsanker (Fallback), falls das Schreiben
 * scheitert.</p>
 *
 * <p>Nicht enthalten und daher an anderer Stelle zu behandeln: Enderchest, Trankeffekte,
 * Leben/Hunger, Spielmodus und Position. Die zugrundeliegende InventoryBackup-API sichert
 * ebenfalls nur diese fuenf Bestandteile.</p>
 */
public final class CapturedInventory {

    private final ItemStack[] contents;
    private final ItemStack[] armor;
    private final ItemStack offhand;
    private final int level;
    private final float exp;

    public CapturedInventory(ItemStack[] contents, ItemStack[] armor, ItemStack offhand,
                             int level, float exp) {
        this.contents = contents == null ? new ItemStack[0] : contents;
        this.armor = armor == null ? new ItemStack[0] : armor;
        this.offhand = offhand;
        this.level = level;
        this.exp = exp;
    }

    /** Erfasst den aktuellen Zustand eines Spielers. Nur vom Haupt-Thread aufrufen. */
    public static CapturedInventory of(Player player) {
        PlayerInventory inv = player.getInventory();
        return new CapturedInventory(
                cloneAll(inv.getContents()),
                cloneAll(inv.getArmorContents()),
                inv.getItemInOffHand() == null ? null : inv.getItemInOffHand().clone(),
                player.getLevel(),
                player.getExp());
    }

    private static ItemStack[] cloneAll(ItemStack[] source) {
        if (source == null) {
            return new ItemStack[0];
        }
        ItemStack[] copy = new ItemStack[source.length];
        for (int i = 0; i < source.length; i++) {
            copy[i] = source[i] == null ? null : source[i].clone();
        }
        return copy;
    }

    public ItemStack[] contents() { return contents; }
    public ItemStack[] armor() { return armor; }
    public ItemStack offhand() { return offhand; }
    public int level() { return level; }
    public float exp() { return exp; }

    /**
     * Ein Fingerabdruck ueber Inhalt, Ruestung und Nebenhand.
     *
     * <p>Damit laesst sich billig pruefen, ob jemand anderes das Inventar zwischen zwei Ticks
     * angefasst hat - genau der Fall, wenn Multiverse-Inventories parallel laeuft und einen
     * gerade wiederhergestellten Zustand wieder ueberschreibt.</p>
     *
     * <p>Level und Erfahrung gehen bewusst <b>nicht</b> ein: die aendern sich im normalen
     * Spielverlauf staendig und wuerden den Vergleich zu einem Dauer-Falschalarm machen.</p>
     */
    public String fingerprint() {
        StringBuilder builder = new StringBuilder(256);
        appendAll(builder, contents);
        builder.append('|');
        appendAll(builder, armor);
        builder.append('|');
        appendItem(builder, offhand);
        return builder.toString();
    }

    private static void appendAll(StringBuilder builder, ItemStack[] items) {
        for (ItemStack item : items) {
            appendItem(builder, item);
            builder.append(',');
        }
    }

    private static void appendItem(StringBuilder builder, ItemStack item) {
        if (item == null) {
            builder.append('-');
            return;
        }
        // Typ und Menge reichen: ein Tausch durch ein anderes Plugin ersetzt praktisch immer
        // ganze Stapel. Ein vollstaendiger NBT-Vergleich waere pro Tick zu teuer.
        builder.append(item.getType().name()).append('x').append(item.getAmount());
    }

    /**
     * Spielt diesen Abzug direkt auf einen Spieler zurueck - der Notweg, wenn kein Provider
     * verfuegbar ist oder ein Restore fehlgeschlagen ist.
     *
     * @param dropOverflow was nicht ins Inventar passt, vor die Fuesse legen
     */
    public void applyTo(Player player, boolean dropOverflow) {
        PlayerInventory inv = player.getInventory();
        inv.clear();
        inv.setArmorContents(null);

        ItemStack[] target = new ItemStack[inv.getSize()];
        int overflowFrom = Math.min(contents.length, target.length);
        System.arraycopy(contents, 0, target, 0, overflowFrom);
        inv.setContents(target);

        if (armor.length > 0) {
            inv.setArmorContents(armor);
        }
        if (offhand != null) {
            inv.setItemInOffHand(offhand);
        }
        player.setLevel(level);
        player.setExp(exp);

        if (dropOverflow && contents.length > overflowFrom && player.getWorld() != null) {
            for (int i = overflowFrom; i < contents.length; i++) {
                if (contents[i] != null) {
                    player.getWorld().dropItemNaturally(player.getLocation(), contents[i]);
                }
            }
        }
        player.updateInventory();
    }
}
