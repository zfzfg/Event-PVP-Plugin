package de.zfzfg.core.inventory;

/**
 * Was von einem Backup zurueckgespielt wird.
 *
 * <p>{@link #all()} ist der Rollback-Fall (vorher leeren, alles zurueck) und damit der
 * Normalfall am Match- und Event-Ende. {@link #itemsOnly()} laesst Level und Erfahrung in
 * Ruhe.</p>
 */
public final class RestoreMode {

    private static final RestoreMode ALL = new RestoreMode(true, true, true, true, true, true, true);
    private static final RestoreMode ITEMS_ONLY = new RestoreMode(true, true, true, false, false, true, true);

    private final boolean contents;
    private final boolean armor;
    private final boolean offhand;
    private final boolean level;
    private final boolean exp;
    private final boolean clearBefore;
    private final boolean dropOverflow;

    private RestoreMode(boolean contents, boolean armor, boolean offhand, boolean level,
                        boolean exp, boolean clearBefore, boolean dropOverflow) {
        this.contents = contents;
        this.armor = armor;
        this.offhand = offhand;
        this.level = level;
        this.exp = exp;
        this.clearBefore = clearBefore;
        this.dropOverflow = dropOverflow;
    }

    public static RestoreMode all() { return ALL; }

    public static RestoreMode itemsOnly() { return ITEMS_ONLY; }

    public static Builder builder() { return new Builder(); }

    public boolean contents() { return contents; }
    public boolean armor() { return armor; }
    public boolean offhand() { return offhand; }
    public boolean level() { return level; }
    public boolean exp() { return exp; }
    public boolean clearBefore() { return clearBefore; }
    public boolean dropOverflow() { return dropOverflow; }

    public static final class Builder {
        private boolean contents = true;
        private boolean armor = true;
        private boolean offhand = true;
        private boolean level = true;
        private boolean exp = true;
        private boolean clearBefore = true;
        private boolean dropOverflow = true;

        public Builder contents(boolean v) { this.contents = v; return this; }
        public Builder armor(boolean v) { this.armor = v; return this; }
        public Builder offhand(boolean v) { this.offhand = v; return this; }
        public Builder level(boolean v) { this.level = v; return this; }
        public Builder exp(boolean v) { this.exp = v; return this; }
        public Builder clearBefore(boolean v) { this.clearBefore = v; return this; }
        public Builder dropOverflow(boolean v) { this.dropOverflow = v; return this; }

        public RestoreMode build() {
            return new RestoreMode(contents, armor, offhand, level, exp, clearBefore, dropOverflow);
        }
    }
}
