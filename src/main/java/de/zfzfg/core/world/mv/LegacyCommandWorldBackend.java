package de.zfzfg.core.world.mv;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Fallback-Backend fuer Server mit Multiverse-Core 4 -- oder ganz ohne Multiverse.
 *
 * <p>Setzt {@code mv ...}-Befehle ueber die Konsole ab, so wie es
 * {@link de.zfzfg.core.world.MultiverseHelper} seit jeher tut. Zwei bekannte Grenzen, die das
 * Panel dem Nutzer auch anzeigt:</p>
 * <ul>
 *   <li>Erfolg/Misserfolg laesst sich nur durch Nachschauen ermitteln (existiert die Welt jetzt?),
 *       weil die Konsolenausgabe nicht abgegriffen wird.</li>
 *   <li>{@code mv delete} verlangt bei MV5-Defaults ein {@code /mv confirm <OTP>}, dessen OTP von
 *       aussen nicht bekannt ist. Auf MV4 gibt es diese Huerde nicht; auf MV5 laeuft ohnehin
 *       {@link Mv5WorldBackend}. Bleibt der Sonderfall "MV5 vorhanden, API nicht ladbar" --
 *       dort greift die Ordner-Loeschung des {@link MvWorldService}.</li>
 * </ul>
 *
 * <p>Die Weltliste kommt bewusst ohne Multiverse aus: geladene Welten liefert Bukkit,
 * entladene findet der Ordner-Scan. Damit funktioniert die Weltauswahl im Panel auch ganz
 * ohne Multiverse.</p>
 */
class LegacyCommandWorldBackend implements MvWorldBackend {

    private final JavaPlugin plugin;

    LegacyCommandWorldBackend(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getBackendId() {
        return isAvailable() ? "LEGACY" : "NONE";
    }

    @Override
    public boolean isAvailable() {
        Plugin mv = Bukkit.getPluginManager().getPlugin("Multiverse-Core");  // i18n-ignore: Plugin-Name fuer den Bukkit-Lookup
        return mv != null && mv.isEnabled();
    }

    @Override
    public boolean supportsAdvancedCreateOptions() {
        // Biome und Generator-Settings gibt es erst in der MV5-Kommandozeile bzw. -API.
        return false;
    }

    @Override
    public List<MvWorldInfo> listWorlds() {
        List<MvWorldInfo> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        for (World world : Bukkit.getWorlds()) {
            if (!seen.add(world.getName().toLowerCase(Locale.ROOT))) continue;
            result.add(new MvWorldInfo(
                    world.getName(),
                    world.getEnvironment().name(),
                    null,
                    true,
                    isAvailable(),
                    true));
        }

        result.addAll(MvWorldService.scanServerWorlds(seen));
        result.sort(java.util.Comparator.comparing(MvWorldInfo::getName, String.CASE_INSENSITIVE_ORDER));
        return result;
    }

    @Override
    public MvResult create(MvCreateSpec spec) {
        if (!isAvailable()) {
            return MvResult.fail("mv.error.notInstalled");
        }
        if (Bukkit.getWorld(spec.getName()) != null) {
            return MvResult.ok();
        }

        // /mv create {NAME} {ENV} -s [SEED] -g [GENERATOR] -t [TYPE] [-n] -a [true|false]
        // -n = Spawn NICHT anpassen, -a = Strukturen generieren (MV4-Semantik).
        StringBuilder command = new StringBuilder("mv create ")  // i18n-ignore: nur Konsole (mv-Befehl), erreicht keinen Spieler
                .append(spec.getName()).append(' ').append(spec.getEnvironment());
        if (!spec.getSeed().isEmpty()) {
            command.append(" -s ").append(spec.getSeed());
        }
        if (!spec.getGenerator().isEmpty()) {
            command.append(" -g ").append(spec.getGenerator());
        }
        command.append(" -t ").append(spec.getWorldType());
        if (!spec.isAdjustSpawn()) {
            command.append(" -n");
        }
        command.append(" -a ").append(spec.isGenerateStructures());

        dispatch(command.toString());

        // Die Welterstellung laeuft im selben Tick durch, das Ergebnis ist also sofort sichtbar.
        if (Bukkit.getWorld(spec.getName()) != null) {
            return MvResult.ok();
        }
        return MvResult.fail("mv.error.createFailed");
    }

    @Override
    public MvResult load(String worldName) {
        if (Bukkit.getWorld(worldName) != null) {
            return MvResult.ok();
        }
        if (!isAvailable()) {
            return MvResult.fail("mv.error.notInstalled");
        }

        dispatch("mv load " + worldName);  // i18n-ignore: nur Konsole (mv-Befehl), erreicht keinen Spieler
        if (Bukkit.getWorld(worldName) != null) {
            return MvResult.ok();
        }

        // Ordner da, aber Multiverse kennt die Welt noch nicht -> importieren.
        if (MvWorldService.worldFolderExists(worldName)) {
            dispatch("mv import " + worldName + " " + guessEnvironment(worldName));  // i18n-ignore: nur Konsole (mv-Befehl), erreicht keinen Spieler
            if (Bukkit.getWorld(worldName) != null) {
                return MvResult.ok();
            }
        }
        return MvResult.fail("mv.error.loadFailed");
    }

    @Override
    public MvResult unload(String worldName) {
        if (Bukkit.getWorld(worldName) == null) {
            return MvResult.ok();
        }
        if (!isAvailable()) {
            return MvResult.fail("mv.error.notInstalled");
        }
        dispatch("mv unload " + worldName);  // i18n-ignore: nur Konsole (mv-Befehl), erreicht keinen Spieler
        if (Bukkit.getWorld(worldName) == null) {
            return MvResult.ok();
        }
        return MvResult.fail("mv.error.unloadFailed");
    }

    @Override
    public MvResult importWorld(String worldName) {
        if (Bukkit.getWorld(worldName) != null) {
            return MvResult.ok();
        }
        if (!isAvailable()) {
            return MvResult.fail("mv.error.notInstalled");
        }
        dispatch("mv import " + worldName + " " + guessEnvironment(worldName));  // i18n-ignore: nur Konsole (mv-Befehl), erreicht keinen Spieler
        if (Bukkit.getWorld(worldName) != null) {
            return MvResult.ok();
        }
        dispatch("mv load " + worldName);  // i18n-ignore: nur Konsole (mv-Befehl), erreicht keinen Spieler
        if (Bukkit.getWorld(worldName) != null) {
            return MvResult.ok();
        }
        return MvResult.fail("mv.error.restoreFailed");
    }

    @Override
    public java.io.File resolveWorldFolder(String worldName) {
        // MV4 hat keine Ordner-Aufloesung in der API; die Fallback-Ketten des Service greifen.
        return null;
    }

    @Override
    public MvResult delete(String worldName) {
        // Ueber die Konsole ist das Loeschen nicht zuverlaessig bestaetigbar (mv confirm/OTP).
        // Der Service raeumt den Ordner deshalb selbst weg; hier wird nur sichergestellt,
        // dass die Welt vorher entladen ist.
        if (Bukkit.getWorld(worldName) != null) {
            unload(worldName);
        }
        if (isAvailable()) {
            // Aufraeumen der Multiverse-eigenen worlds.yml, damit dort kein Eintrag zurueckbleibt.
            dispatch("mv remove " + worldName);  // i18n-ignore: nur Konsole (mv-Befehl), erreicht keinen Spieler
        }
        return MvResult.fail(MvWorldService.NOT_MANAGED_MARKER);
    }

    private void dispatch(String command) {
        plugin.getLogger().info("[Multiverse] /" + command);  // i18n-ignore: console-only mv command trace
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);  // i18n-ignore: console-only mv command
    }

    /** Entspricht {@code MultiverseHelper.guessEnv()} -- Environment aus dem Weltnamen raten. */
    private String guessEnvironment(String worldName) {
        String lower = worldName.toLowerCase(Locale.ROOT);
        if (lower.contains("nether")) return "NETHER";
        if (lower.contains("end")) return "THE_END";
        return "NORMAL";
    }
}
