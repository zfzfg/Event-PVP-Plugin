package de.zfzfg.eventplugin.util;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import de.zfzfg.eventplugin.EventPlugin;
import org.bukkit.Bukkit;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.logging.Level;

public class UpdateChecker {
    private final EventPlugin plugin;
    private final String currentVersion;
    private final String modrinthProjectId;
    
    private String latestVersion;
    private boolean updateAvailable;
    private boolean checked;
    private long lastCheckTime;
    
    public UpdateChecker(EventPlugin plugin, String currentVersion, String modrinthProjectId) {
        this.plugin = plugin;
        this.currentVersion = currentVersion;
        this.modrinthProjectId = modrinthProjectId;
        this.updateAvailable = false;
        this.checked = false;
    }
    
    /**
     * Startup-Variante: verzoegerter Abruf ohne Rueckmeldung.
     * Die Verzoegerung kommt aus settings.update-check.startup-delay-ticks.
     */
    public void checkForUpdates() {
        long delay = 20L;
        if (plugin.getConfigManager() != null) {
            delay = Math.max(0L, plugin.getConfigManager().getStartupDelayTicks());
        }
        checkForUpdates(delay, null);
    }

    /**
     * Sofortiger Abruf. {@code onDone} laeuft nach Abschluss im Main-Thread --
     * auch dann, wenn der Abruf fehlgeschlagen ist. Aufrufer pruefen danach
     * {@link #hasChecked()}, statt eine feste Wartezeit zu raten.
     */
    public void checkForUpdates(Runnable onDone) {
        checkForUpdates(0L, onDone);
    }

    public void checkForUpdates(long delayTicks, Runnable onDone) {
        Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, () -> {
            try {
                String apiUrl = "https://api.modrinth.com/v2/project/" + modrinthProjectId + "/version";
                URI uri = URI.create(apiUrl);
                URL url = uri.toURL();
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                
                // Pflicht-Header für Modrinth
                connection.setRequestMethod("GET");
                // Modrinth verlangt einen aussagekraeftigen User-Agent mit erreichbarem
                // Kontakt. Die Adresse kommt aus settings.update-check.contact.
                String contact = plugin.getConfigManager() != null
                        ? plugin.getConfigManager().getUpdateContact()
                        : "https://modrinth.com/plugin/" + modrinthProjectId;
                connection.setRequestProperty("User-Agent",  // i18n-ignore: HTTP-Header bzw. JSON-Feldname der Update-API
                        plugin.getDescription().getName() + "/" + currentVersion + " (" + contact + ")");
                connection.setRequestProperty("Accept", "application/json");
                connection.setConnectTimeout(10000);
                connection.setReadTimeout(10000);
                
                int responseCode = connection.getResponseCode();
                
                if (responseCode == 200) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                    reader.close();
                    
                    // Gson liegt Spigot/Paper bei -- spigot-api deklariert es als
                    // compile-Dependency. Es ist eine Bibliothek auf dem Klassenpfad,
                    // KEIN Bukkit-Service: eine Abfrage ueber den ServicesManager
                    // liefert immer null und darf nicht als Verfuegbarkeitspruefung
                    // dienen.
                    JsonArray versions = new Gson().fromJson(response.toString(), JsonArray.class);

                    // Hoechste Version suchen statt blind die erste zu nehmen --
                    // die Sortierung der API ist nicht zugesichert.
                    String highest = null;
                    if (versions != null) {
                        boolean stableOnly = plugin.getConfigManager() == null
                                || plugin.getConfigManager().isUpdateStableOnly();
                        for (JsonElement element : versions) {
                            if (element == null || !element.isJsonObject()) {
                                continue;
                            }
                            JsonObject version = element.getAsJsonObject();
                            if (stableOnly && version.has("version_type")
                                    && !"release".equals(version.get("version_type").getAsString())) {
                                continue;
                            }
                            if (!version.has("version_number")) {
                                continue;
                            }
                            String number = version.get("version_number").getAsString();
                            if (highest == null || compareVersions(highest, number)) {
                                highest = number;
                            }
                        }
                    }

                    if (highest != null) {
                        latestVersion = highest;
                        updateAvailable = compareVersions(currentVersion, latestVersion);
                        checked = true;
                        lastCheckTime = System.currentTimeMillis();

                        if (updateAvailable) {
                            plugin.getLogger().info("========================================");
                            plugin.getLogger().info(plugin.getConsoleMsg("update-available-banner"));
                            plugin.getLogger().info(plugin.getConsoleMsg("update-current-version", "version", currentVersion));
                            plugin.getLogger().info(plugin.getConsoleMsg("update-latest-version", "version", latestVersion));
                            plugin.getLogger().info(plugin.getConsoleMsg("update-download-url", "url", "https://modrinth.com/plugin/" + modrinthProjectId));
                            plugin.getLogger().info("========================================");
                        } else {
                            plugin.getLogger().info(plugin.getConsoleMsg("update-up-to-date", "version", currentVersion));
                        }
                    } else {
                        plugin.getLogger().warning(plugin.getConsoleMsg("update-invalid-response"));
                    }
                } else {
                    plugin.getLogger().warning(plugin.getConsoleMsg("update-check-failed-http", "code", String.valueOf(responseCode)));
                }
                
                connection.disconnect();
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, plugin.getConsoleMsg("update-check-failed", "error", e.getMessage()), e);
            } finally {
                // Rueckmeldung immer im Main-Thread -- die Bukkit-API ist nicht
                // thread-sicher. Laeuft auch nach einem Fehlschlag, damit der
                // Aufrufer nicht endlos auf ein Ergebnis wartet.
                if (onDone != null && plugin.isEnabled()) {
                    Bukkit.getScheduler().runTask(plugin, onDone);
                }
            }
        }, Math.max(0L, delayTicks));
    }
    
    /** true, wenn {@code latest} neuer ist als {@code current}. */
    static boolean compareVersions(String current, String latest) {
        int[] a = numericParts(current);
        int[] b = numericParts(latest);
        int length = Math.max(a.length, b.length);
        for (int i = 0; i < length; i++) {
            int left = i < a.length ? a[i] : 0;    // fehlendes Segment zaehlt als 0,
            int right = i < b.length ? b[i] : 0;   // damit 1.2 == 1.2.0 gilt
            if (right != left) {
                return right > left;
            }
        }
        // Zahlenteil gleich: eine Vorabversion ist AELTER als das fertige Release.
        return isPreRelease(current) && !isPreRelease(latest);
    }

    /**
     * Zerlegt "1.2.3-RC1" in [1, 2, 3]. Der Suffix wird abgeschnitten, bevor
     * die Ziffern gelesen werden -- sonst wuerde aus "1.0.0-RC1" die Zahlenfolge
     * 1.0.01 und die Vorabversion saehe neuer aus als ihr eigenes Release.
     */
    private static int[] numericParts(String version) {
        if (version == null) {
            return new int[0];
        }
        String core = version.split("[-+]", 2)[0];
        String[] segments = core.split("\\.");
        int[] out = new int[segments.length];
        for (int i = 0; i < segments.length; i++) {
            String digits = segments[i].replaceAll("[^0-9]", "");
            if (digits.isEmpty()) {
                out[i] = 0;
                continue;
            }
            try {
                out[i] = Integer.parseInt(digits);
            } catch (NumberFormatException e) {
                // Zu gross fuer int: als sehr gross werten, nicht als 0 -- sonst
                // meldet der Checker ein Update, das es nicht gibt.
                out[i] = Integer.MAX_VALUE;
            }
        }
        return out;
    }

    private static boolean isPreRelease(String version) {
        if (version == null) {
            return false;
        }
        String lower = version.toLowerCase(java.util.Locale.ROOT);
        return lower.contains("-rc") || lower.contains("-beta")
                || lower.contains("-alpha") || lower.contains("-pre")
                || lower.contains("-snapshot");
    }
    
    public boolean isUpdateAvailable() {
        return updateAvailable && checked;
    }
    
    public String getLatestVersion() {
        return latestVersion;
    }
    
    public String getCurrentVersion() {
        return currentVersion;
    }
    
    public boolean hasChecked() {
        return checked;
    }
    
    public long getLastCheckTime() {
        return lastCheckTime;
    }
}
