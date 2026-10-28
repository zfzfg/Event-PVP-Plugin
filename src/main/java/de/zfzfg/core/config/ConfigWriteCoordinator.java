package de.zfzfg.core.config;

import de.zfzfg.core.util.AtomicFiles;
import org.bukkit.configuration.file.YamlConfiguration;
import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/** All writers merge patches into the latest confirmed file; web replacements use revisions. */
public final class ConfigWriteCoordinator {
    private static final Map<Path, Object> LOCKS = new ConcurrentHashMap<>();
    private ConfigWriteCoordinator() {}
    private static Object lock(Path path) { return LOCKS.computeIfAbsent(path.toAbsolutePath().normalize(), p -> new Object()); }
    public static String revision(Path path) {
        synchronized (lock(path)) {
            try {
                byte[] bytes = Files.exists(path) ? Files.readAllBytes(path) : new byte[0];
                return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
            } catch (IOException | NoSuchAlgorithmException ex) { throw new IllegalStateException("Could not read configuration revision", ex); }
        }
    }
    public static YamlConfiguration patch(Path path, Consumer<YamlConfiguration> patch) {
        synchronized (lock(path)) {
            try {
                YamlConfiguration latest = new YamlConfiguration();
                if (Files.exists(path)) latest.load(path.toFile());
                patch.accept(latest); AtomicFiles.save(latest, path); return latest;
            } catch (Exception ex) { throw new IllegalStateException("Configuration write failed", ex); }
        }
    }
    public static void replace(Path path, YamlConfiguration proposed, String expected) {
        synchronized (lock(path)) {
            if (expected != null && !expected.equals(revision(path))) throw new Conflict();
            try { AtomicFiles.save(proposed, path); }
            catch (IOException ex) { throw new IllegalStateException("Configuration write failed", ex); }
        }
    }
    public static final class Conflict extends IllegalArgumentException {
        public Conflict() { super("Configuration changed; reload before saving"); }
    }
}
