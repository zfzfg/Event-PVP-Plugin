package de.zfzfg.eventplugin.util;

import com.google.gson.JsonArray;
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
    
    public void checkForUpdates() {
        Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, () -> {
            try {
                String apiUrl = "https://api.modrinth.com/v2/project/" + modrinthProjectId + "/version";
                URI uri = URI.create(apiUrl);
                URL url = uri.toURL();
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                
                // Pflicht-Header für Modrinth
                connection.setRequestMethod("GET");
                connection.setRequestProperty("User-Agent", "EventPVPPlugin/" + currentVersion + " (kontakt@email.com)");
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
                    
                    // JSON-Parsing mit Gson (in Spigot/Paper enthalten)
                    JsonArray versions = plugin.getServer().getServicesManager().getRegistration(com.google.gson.Gson.class) != null 
                        ? new com.google.gson.Gson().fromJson(response.toString(), JsonArray.class)
                        : parseJsonManually(response.toString());
                    
                    if (versions != null && versions.size() > 0) {
                        JsonObject latest = versions.get(0).getAsJsonObject();
                        latestVersion = latest.get("version_number").getAsString();
                        
                        // Versionsvergleich
                        updateAvailable = compareVersions(currentVersion, latestVersion);
                        checked = true;
                        lastCheckTime = System.currentTimeMillis();
                        
                        if (updateAvailable) {
                            plugin.getLogger().info("========================================");
                            plugin.getLogger().info("UPDATE VERFÜGBAR!");
                            plugin.getLogger().info("Aktuelle Version: " + currentVersion);
                            plugin.getLogger().info("Neueste Version: " + latestVersion);
                            plugin.getLogger().info("Download: https://modrinth.com/plugin/" + modrinthProjectId);
                            plugin.getLogger().info("========================================");
                        } else {
                            plugin.getLogger().info("Plugin ist auf dem neuesten Stand (" + currentVersion + ")");
                        }
                    }
                } else {
                    plugin.getLogger().warning("Update-Prüfung fehlgeschlagen: HTTP " + responseCode);
                }
                
                connection.disconnect();
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Update-Prüfung fehlgeschlagen: " + e.getMessage(), e);
            }
        }, 20L); // 20 Ticks Delay (1 Sekunde)
    }
    
    private JsonArray parseJsonManually(String json) {
        // Fallback für manuelles Parsing wenn Gson nicht verfügbar
        try {
            // Einfache Extraktion der ersten version_number
            int versionIndex = json.indexOf("\"version_number\"");
            if (versionIndex != -1) {
                int colonIndex = json.indexOf(":", versionIndex);
                int quoteStart = json.indexOf("\"", colonIndex + 1);
                int quoteEnd = json.indexOf("\"", quoteStart + 1);
                latestVersion = json.substring(quoteStart + 1, quoteEnd);
                updateAvailable = compareVersions(currentVersion, latestVersion);
                checked = true;
                lastCheckTime = System.currentTimeMillis();
                return null;
            }
        } catch (Exception e) {
            plugin.getLogger().warning("JSON-Parsing fehlgeschlagen: " + e.getMessage());
        }
        return null;
    }
    
    private boolean compareVersions(String current, String latest) {
        try {
            // Extract numeric part from version (e.g., "1.0.6-Multilingual" -> "1.0.6")
            String currentNumeric = current.replaceAll("[^0-9.]", "");
            String latestNumeric = latest.replaceAll("[^0-9.]", "");
            
            String[] currentParts = currentNumeric.split("\\.");
            String[] latestParts = latestNumeric.split("\\.");
            
            for (int i = 0; i < Math.max(currentParts.length, latestParts.length); i++) {
                int currentNum = i < currentParts.length ? Integer.parseInt(currentParts[i]) : 0;
                int latestNum = i < latestParts.length ? Integer.parseInt(latestParts[i]) : 0;
                
                if (latestNum > currentNum) {
                    return true;
                } else if (latestNum < currentNum) {
                    return false;
                }
            }
            return false;
        } catch (NumberFormatException e) {
            plugin.getLogger().warning("Versionsvergleich fehlgeschlagen: " + e.getMessage());
            return false;
        }
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
