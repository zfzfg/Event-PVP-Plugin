package de.zfzfg.core.inventory.mvi;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Was die Diagnose in der Konfiguration von Multiverse-Inventories gefunden hat.
 *
 * <p>Reines Ergebnisobjekt: erstellt einmal beim Start und beim Reload, danach nur noch
 * gelesen - vom Konsolen-Log und vom Web-Panel. Ein leerer Report bedeutet nicht
 * zwangslaeufig, dass Multiverse-Inventories fehlt: es kann auch installiert sein, ohne
 * dass eine seiner Weltgruppen eine Arena- oder Event-Welt beruehrt.</p>
 */
public final class MviConflictReport {

    /**
     * Eine einzelne Kollision: eine vom Plugin verwaltete Welt liegt in einer
     * Multiverse-Inventories-Gruppe, die noch weitere Welten enthaelt.
     *
     * <p>Erst die Partnerwelt macht die Gruppe gefaehrlich. Eine Gruppe, in der nur die
     * Arena selbst steht, tauscht beim Betreten nichts aus - Multiverse-Inventories hat
     * dann keinen zweiten Zustand, zwischen dem es wechseln koennte.</p>
     */
    public static final class Collision {
        private final String world;
        private final String group;
        private final List<String> partnerWorlds;

        public Collision(String world, String group, List<String> partnerWorlds) {
            this.world = world;
            this.group = group;
            this.partnerWorlds = Collections.unmodifiableList(new ArrayList<>(partnerWorlds));
        }

        /** Die vom Plugin verwaltete Welt (Arena, Event-Welt oder Event-Lobby). */
        public String world() { return world; }

        /** Name der Multiverse-Inventories-Gruppe, in der sie steht. */
        public String group() { return group; }

        /** Die uebrigen Welten derselben Gruppe - mit ihnen wird getauscht. */
        public List<String> partnerWorlds() { return partnerWorlds; }

        /** Der Befehl, mit dem ein Admin die Welt aus der Gruppe loest. */
        public String fixCommand() {
            return "/mvinv rmworld " + world + " " + group;
        }
    }

    private static final MviConflictReport NOT_INSTALLED =
            new MviConflictReport(false, "", Collections.<Collision>emptyList(), false);

    private final boolean installed;
    private final String version;
    private final List<Collision> collisions;
    private final boolean configUnreadable;

    private MviConflictReport(boolean installed, String version, List<Collision> collisions,
                              boolean configUnreadable) {
        this.installed = installed;
        this.version = version;
        this.collisions = Collections.unmodifiableList(new ArrayList<>(collisions));
        this.configUnreadable = configUnreadable;
    }

    /** Multiverse-Inventories laeuft nicht - es gibt nichts zu melden. */
    public static MviConflictReport notInstalled() {
        return NOT_INSTALLED;
    }

    /** Multiverse-Inventories laeuft; {@code collisions} kann leer sein. */
    public static MviConflictReport of(String version, List<Collision> collisions) {
        return new MviConflictReport(true, version, collisions, false);
    }

    /**
     * Multiverse-Inventories laeuft, aber seine Gruppen-Datei war nicht lesbar.
     *
     * <p>Bewusst ein eigener Zustand statt "keine Kollisionen": nicht lesen koennen heisst
     * nicht, dass nichts kollidiert. Das Panel muss diesen Fall als Unsicherheit zeigen,
     * nicht als Entwarnung.</p>
     */
    public static MviConflictReport unreadable(String version) {
        return new MviConflictReport(true, version, Collections.<Collision>emptyList(), true);
    }

    public boolean installed() { return installed; }
    public String version() { return version; }
    public List<Collision> collisions() { return collisions; }
    public boolean configUnreadable() { return configUnreadable; }

    /** Ob mindestens eine verwaltete Welt in einer tauschenden Gruppe liegt. */
    public boolean hasCollisions() { return !collisions.isEmpty(); }

    /** Die betroffenen Gruppennamen, ohne Wiederholung, in Fundreihenfolge. */
    public Set<String> affectedGroups() {
        Set<String> groups = new LinkedHashSet<>();
        for (Collision collision : collisions) {
            groups.add(collision.group());
        }
        return groups;
    }
}
