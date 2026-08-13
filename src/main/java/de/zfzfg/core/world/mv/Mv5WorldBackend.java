package de.zfzfg.core.world.mv;

import org.bukkit.World;
import org.bukkit.WorldType;

import org.mvplugins.multiverse.core.MultiverseCoreApi;
import org.mvplugins.multiverse.core.world.LoadedMultiverseWorld;
import org.mvplugins.multiverse.core.world.MultiverseWorld;
import org.mvplugins.multiverse.core.world.WorldManager;
import org.mvplugins.multiverse.core.world.options.CreateWorldOptions;
import org.mvplugins.multiverse.core.world.options.DeleteWorldOptions;
import org.mvplugins.multiverse.core.world.options.LoadWorldOptions;
import org.mvplugins.multiverse.core.world.options.UnloadWorldOptions;
import org.mvplugins.multiverse.core.utils.result.Attempt;

import java.util.ArrayList;
import java.util.List;

/**
 * Multiverse-Core-5-Backend.
 *
 * <p><strong>Diese Klasse ist die einzige Stelle im Plugin, die
 * {@code org.mvplugins.multiverse.core.*} beruehrt.</strong> {@link MvWorldService} laedt sie
 * ausschliesslich nach erfolgreichem {@code Class.forName("...MultiverseCoreApi")}. Wird hier
 * ein MV5-Typ in eine Signatur gehoben, die {@code MvWorldService} direkt aufruft, faellt das
 * Plugin auf MV4-Servern beim Klassenladen mit {@code NoClassDefFoundError} um -- deshalb
 * kommuniziert die Klasse nach aussen nur ueber die neutralen DTOs des Pakets.</p>
 *
 * <p>Gegenueber dem Kommando-Weg loest die API zwei Probleme: es gibt kein
 * {@code /mv confirm}-OTP (Multiverse verlangt die Bestaetigung nur fuer Befehle) und
 * Fehlschlaege kommen als typisierter Grund zurueck statt nur in der Konsole zu landen.</p>
 */
public class Mv5WorldBackend implements MvWorldBackend {

    /** Oeffentlich, weil {@link MvWorldService} die Klasse reflektiv instanziiert. */
    public Mv5WorldBackend() {
        // Fruehe Probe: erzwingt das Linken der MV5-Typen jetzt (und damit einen sauberen
        // Fallback im Service), statt erst beim ersten Web-Request zu scheitern.
        MultiverseCoreApi.isLoaded();
    }

    @Override
    public String getBackendId() {
        return "MV5";
    }

    @Override
    public boolean isAvailable() {
        try {
            return MultiverseCoreApi.isLoaded();
        } catch (Throwable t) {
            return false;
        }
    }

    @Override
    public boolean supportsAdvancedCreateOptions() {
        return true;
    }

    private WorldManager worldManager() {
        return MultiverseCoreApi.get().getWorldManager();
    }

    @Override
    public List<MvWorldInfo> listWorlds() {
        List<MvWorldInfo> result = new ArrayList<>();
        java.util.Set<String> seen = new java.util.HashSet<>();

        // Vereinigung aus allen drei Sichten des WorldManagers. Seit dem WorldStore-Umbau in
        // MV 5.7 ist nicht garantiert, dass getWorlds() auch jede entladene Welt enthaelt --
        // auf einem 5.7.3-Server fiel eine frisch entladene Welt komplett aus der Liste und
        // das Panel hielt sie fuer einen Platzhalter ("Welt erstellen" fuer eine existierende
        // Welt). Geladene zuerst, damit der Ladezustand beim Dedupe gewinnt.
        List<MultiverseWorld> candidates = new ArrayList<>();
        candidates.addAll(worldManager().getLoadedWorlds());
        candidates.addAll(worldManager().getUnloadedWorlds());
        candidates.addAll(worldManager().getWorlds());

        for (MultiverseWorld world : candidates) {
            String name = world.getName();
            if (name == null || !seen.add(name.toLowerCase(java.util.Locale.ROOT))) continue;

            boolean loaded = world.isLoaded();
            String worldType = null;
            if (loaded) {
                LoadedMultiverseWorld loadedWorld = worldManager().getLoadedWorld(name).getOrNull();
                if (loadedWorld != null) {
                    WorldType type = loadedWorld.getWorldType().getOrNull();
                    if (type != null) worldType = type.name();
                }
            }

            String environment = "NORMAL";
            try {
                if (world.getEnvironment() != null) environment = world.getEnvironment().name();
            } catch (Throwable ignored) {
                // Eine Welt mit kaputtem Environment darf nicht die ganze Liste reissen.
            }

            result.add(new MvWorldInfo(
                    name,
                    environment,
                    worldType,
                    loaded,
                    true,
                    MvWorldService.looksLikeWorldFolder(resolveWorldFolder(name))
                            || MvWorldService.worldFolderExists(name)));
        }

        // Welten, die auf der Platte liegen bzw. von Bukkit geladen sind, ohne dass Multiverse
        // sie kennt (z.B. per Vanilla-Level oder anderem Plugin erzeugt). Sie sollen im Panel
        // sichtbar sein, damit man sie als Preset-Ziel auswaehlen kann.
        for (MvWorldInfo extra : MvWorldService.scanServerWorlds(seen)) {
            result.add(extra);
        }

        result.sort(java.util.Comparator.comparing(MvWorldInfo::getName, String.CASE_INSENSITIVE_ORDER));
        return result;
    }

    @Override
    public java.io.File resolveWorldFolder(String worldName) {
        MultiverseWorld world = worldManager().getWorld(worldName).getOrNull();
        if (world == null) return null;
        try {
            // Erst ab MV 5.7 vorhanden (WorldFolderResolver kennt auch das Dimensions-Layout
            // world/dimensions/<ns>/<name>). Auf 5.4-5.6 wirft der Aufruf NoSuchMethodError --
            // dann entscheiden die Fallback-Ketten des MvWorldService.
            return world.getOfflineWorldFolder();
        } catch (Throwable t) {
            return null;
        }
    }

    @Override
    public MvResult create(MvCreateSpec spec) {
        CreateWorldOptions options = CreateWorldOptions.worldName(spec.getName())
                .environment(parseEnvironment(spec.getEnvironment()))
                .worldType(parseWorldType(spec.getWorldType()))
                .generateStructures(spec.isGenerateStructures())
                .useSpawnAdjust(spec.isAdjustSpawn());

        if (!spec.getSeed().isEmpty()) {
            options = options.seed(spec.getSeed());
        }
        if (!spec.getGenerator().isEmpty()) {
            options = options.generator(spec.getGenerator());
        }
        if (!spec.getGeneratorSettings().isEmpty()) {
            options = options.generatorSettings(spec.getGeneratorSettings());
        }
        if (!spec.getBiome().isEmpty()) {
            options = options.biome(spec.getBiome());
        }

        return toResult(worldManager().createWorld(options));
    }

    @Override
    public MvResult load(String worldName) {
        MultiverseWorld world = worldManager().getWorld(worldName).getOrNull();
        if (world == null) {
            // Multiverse kennt die Welt nicht -- ohne Import ist hier nichts zu laden.
            return MvResult.fail("mv.error.notKnown", worldName);
        }
        if (world.isLoaded()) {
            return MvResult.ok();
        }
        return toResult(worldManager().loadWorld(LoadWorldOptions.world(world)));
    }

    @Override
    public MvResult unload(String worldName) {
        LoadedMultiverseWorld world = worldManager().getLoadedWorld(worldName).getOrNull();
        if (world == null) {
            return MvResult.ok();
        }
        return toResult(worldManager().unloadWorld(
                UnloadWorldOptions.world(world).saveBukkitWorld(true)));
    }

    @Override
    public MvResult importWorld(String worldName) {
        if (worldManager().getLoadedWorld(worldName).getOrNull() != null) {
            return MvResult.ok();
        }
        // Kennt MV die Welt schon (entladen), reicht ein Load statt eines Imports.
        MultiverseWorld known = worldManager().getWorld(worldName).getOrNull();
        if (known != null) {
            return toResult(worldManager().loadWorld(LoadWorldOptions.world(known)));
        }
        return toResult(worldManager().importWorld(
                org.mvplugins.multiverse.core.world.options.ImportWorldOptions.worldName(worldName)));
    }

    @Override
    public MvResult delete(String worldName) {
        MultiverseWorld world = worldManager().getWorld(worldName).getOrNull();
        if (world == null) {
            // Nicht von Multiverse verwaltet: der Service raeumt den Ordner selbst weg.
            return MvResult.fail(MvWorldService.NOT_MANAGED_MARKER);
        }
        return toResult(worldManager().deleteWorld(DeleteWorldOptions.world(world)));
    }

    /**
     * Uebersetzt ein Multiverse-{@code Attempt} in das neutrale {@link MvResult}. Der
     * Fehlertext kommt aus der Multiverse-Message, damit der Nutzer im Panel den echten
     * Grund sieht ("world folder already exists", "invalid generator", ...).
     */
    private MvResult toResult(Attempt<?, ?> attempt) {
        if (attempt.isSuccess()) {
            return MvResult.ok();
        }
        String reason;
        try {
            reason = attempt.getFailureMessage().formatted();
        } catch (Throwable t) {
            reason = String.valueOf(attempt.getFailureReason());
        }
        return MvResult.fail(MvResult.GENERIC_ERROR, reason);
    }

    private World.Environment parseEnvironment(String value) {
        try {
            return World.Environment.valueOf(value);
        } catch (IllegalArgumentException e) {
            return World.Environment.NORMAL;
        }
    }

    private WorldType parseWorldType(String value) {
        try {
            return WorldType.valueOf(value);
        } catch (IllegalArgumentException e) {
            return WorldType.NORMAL;
        }
    }
}
