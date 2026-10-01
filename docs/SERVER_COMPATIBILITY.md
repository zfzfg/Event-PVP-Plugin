# 🌐 Minecraft Server Kompatibilitäts- & Versionsbericht

Dieses Dokument enthält den verbindlichen technischen Leitfaden und die Kompatibilitätsmatrix für das **Event-PVP-Plugin (Version 1.1.1)**. Es beschreibt unterstützte Server-Engines, Minecraft-Versionen, Java-Laufzeitanforderungen sowie Abhängigkeiten im Detail.

---

## 📊 1. Kompatibilitätsmatrix

| Server-Software | Minecraft Version | Kompatibilität | Status & Technische Details |
|---|---|---|---|
| **Purpur** | **26.2** (Build 2618+) | 🟢 **100% Nativ** | ⭐ **Offizielle Referenzplattform** (Native Adventure RGB Components, Async-Teleports, Paper-Registry TagKeys). |
| **Purpur** | **26.3** (Build 2634+) | 🟢 **Unterstützt** | ✅ **Neu in 1.1.1:** Kompiliert ohne Warnungen gegen `purpur-api 26.3`, keine entfernten Materialien/Entities/Sounds; Start, Konfigurations-Reload und Web-Panel auf Purpur 26.3 (Build 2639) getestet. Dasselbe JAR läuft auf 26.2 und 26.3 (`api-version: 26.2`). |
| **Paper** | **26.2 / 26.3** | 🟢 **100% Nativ** | ✅ **Vollständig unterstützt** (Alle Paper- und Adventure-APIs nativ eingebunden; 26.3 wie oben). |
| **Pufferfish** | **26.2+** | 🟢 **Nativ** | ✅ Basiert auf Paper-API. |
| **Spigot (Vanilla Spigot)** | **26.2+** | 🟢 **Unterstützt** | ✅ **Neu in 1.1.0:** Dual-Platform-Architektur (Kyori Adventure 5.2.0 wird mitgeliefert; automatische Fallbacks für GUI-Titel, ItemMeta, Titles und synchrone Teleports). |
| **Paper / Purpur / Spigot** | **1.21.x und älter** | 🔴 **Inkompatibel** | ❌ `api-version: '26.2'` wird von älteren Servern abgelehnt („Unsupported API version“). Zusätzlich: Java-25-Bytecode und Adventure 5 (erst ab 26.2 im Server). |
| **Folia** | **alle** | 🔴 **Nicht unterstützt** | ❌ **Inkompatibel** (Folia deaktiviert den `BukkitScheduler` und verbietet synchrone Multi-Thread-Zugriffe; zudem ist Multiverse-Core nicht Folia-fähig). |

---

## ☕ 2. Java-Laufzeitumgebung (Java Runtime)

* **Mindestanforderung:** **Java 25 (LTS)** oder neuer (z. B. Eclipse Temurin 25, OpenJDK 25, GraalVM 25).
* **Bytecode-Zielversion:** Java 25 (Class File Version `69.0`).
* **Hintergrund:**
   * Minecraft 26.x-Server laufen auf Java 25; das Plugin wird mit `maven.compiler.release=25` gebaut.
   * Das Plugin nutzt moderne Java-Sprachfeatures (Pattern Matching für `switch` und `instanceof`, Records, Concurrency-Klassen) und wird für Java 25 kompiliert.
* **JVM-Empfehlungen:**
  * Für optimale Garbage Collection werden die standardmäßigen **Aikar's Flags** mit G1GC empfohlen:
    ```bash
    java -Xms4G -Xmx4G -XX:+UseG1GC -XX:+ParallelRefProcEnabled -XX:MaxGCPauseMillis=200 -XX:+UnlockExperimentalVMOptions -XX:+DisableExplicitGC -XX:+AlwaysPreTouch -XX:G1NewSizePercent=30 -XX:G1MaxNewSizePercent=40 -XX:G1ReservePercent=20 -XX:G1HeapWastePercent=5 -XX:G1MixedGCCountTarget=4 -XX:InitiatingHeapOccupancyPercent=15 -XX:G1MixedGCLiveThresholdPercent=90 -XX:G1RSetUpdatingPauseTimePercent=5 -XX:SurvivorRatio=32 -XX:+PerfDisableSharedMem -XX:MaxTenuringThreshold=1 -jar purpur-26.2.jar --nogui
    ```

---

## 🧩 3. Plugin-Abhängigkeiten

### 🔒 Pflicht-Abhängigkeiten (Hard Dependencies)
Ohne diese Plugins schaltet sich das Event-PVP-Plugin beim Serverstart sicherheitsbedingt ab:

1. **Multiverse-Core**
   * **Multiverse-Core 5 (Empfohlen):** Typsichere Java-API-Anbindung für verzögerungsfreie Welt-Resets, automatisches Klonen und Entladen von Arenen ohne Konsolen-Timeouts.
   * **Multiverse-Core 4:** Ebenfalls über automatische Rückfallpfade vollständig unterstützt.
2. **Vault**
   * Verarbeitet Wetteinsätze mit Spielgeld bei PvP-Kämpfen.
   * Erfordert ein kompatibles Economy-Plugin auf dem Server (z. B. *EssentialsX*, *Treasury*).
3. **InventoryBackup**
   * Sichert Spieler-Inventare vor Rundenbeginn und stellt sie bei Match-Ende, Respawn oder Server-Disconnects transaktionssicher wieder her.

---

### ✨ Optionale Erweiterungen (Soft Dependencies)

| Plugin | Funktion im Event-PVP-Plugin |
|---|---|
| **PlaceholderAPI (PAPI)** | Stellt Platzhalter für Statistiken (Kills, Deaths, Siege, Win-Streak, Wetteinsätze) für Scoreboards, Chat und Tablisten bereit. |
| **AJLeaderboards** | Ermöglicht Bestenlisten für Siege und Ranglisten-Positionen. |
| **DecentHolograms** | Interaktive 3D-Hologramm-Leaderboards an Spawns und Event-Lobbys. |
| **PvPManager** | Synchronisiert Combat-Tagging und verhindert unpassenden Schutz während aktiver Wager-Matches. |

---

## 🛠️ 4. Technische Dual-Platform-Architektur im Detail

Mit Version 1.1.0 wurde eine saubere Brücken-Architektur eingeführt, die maximale Features auf Purpur/Paper liefert und gleichzeitig 100% stabil auf Spigot läuft:

```
                  ┌──────────────────────────────┐
                  │      Event-PVP-Plugin        │
                  └──────────────┬───────────────┘
                                 │
                   [Platform.java Runtime-Check]
                                 │
            ┌────────────────────┴────────────────────┐
            ▼                                         ▼
   [Paper / Purpur 26.2]                      [Vanilla Spigot 26.2]
 ├── Adventure RGB Component                ├── Shaded Adventure 5.2.0
 ├── Bukkit.createInventory(Component)      ├── Bukkit.createInventory(String)
 ├── ItemMeta.displayName(Component)        ├── ItemMeta.setDisplayName(String)
 ├── Player.teleportAsync(Location)         ├── Player.teleport(Location)
 └── PaperRegistryHelper (TagKeys)          └── Registry.ENCHANTMENT Fallback
```

### 1. Isolierte Adventure 5.2.0 Engine
* **Keine externen Abhängigkeiten auf Spigot:** Kyori Adventure (`adventure-api`, `adventure-text-serializer-legacy`, `adventure-text-serializer-plain`) ist in das Plugin-JAR geshadet – **ohne Relocation**, die Pakete bleiben `net.kyori.*`.
* **Warum keine Relocation:** Auf Paper/Purpur übergibt das Plugin `Component`-Objekte direkt an Server-Methoden (`ItemMeta.displayName(Component)`, `Bukkit.createInventory(..., Component)`). Mit umbenannten Paketen würden diese Aufrufe mit `NoSuchMethodError` scheitern.
* **Classloading:** Der Plugin-Classloader fragt zuerst den Server. Auf Paper/Purpur gewinnt damit die Server-Kopie von Adventure, auf Spigot (ohne Adventure) die mitgelieferte. Ein Restrisiko besteht nur auf Spigot, wenn ein anderes Plugin eine abweichende, ebenfalls nicht relocatete Adventure-Version mitbringt.

### 2. Plattformunabhängige Text- & GUI-Brücke
* [`TextUtil.java`](file:///c:/Users/zfzfg/Documents/HammerMegaProjekte/selfmadePlugins/Plugins/Event-PVP-Plugins/Event-PVP-Plugin-1.1.0-BetaPurpur/src/main/java/de/zfzfg/core/util/TextUtil.java): Sendet echte RGB-Components auf Purpur/Paper und farbcodierte Strings auf Spigot.
* [`GuiUtil.java`](file:///c:/Users/zfzfg/Documents/HammerMegaProjekte/selfmadePlugins/Plugins/Event-PVP-Plugins/Event-PVP-Plugin-1.1.0-BetaPurpur/src/main/java/de/zfzfg/core/util/GuiUtil.java): Erstellt Inventare plattformgerecht mit `Component`- oder `String`-Titel.
* [`ItemUtil.java`](file:///c:/Users/zfzfg/Documents/HammerMegaProjekte/selfmadePlugins/Plugins/Event-PVP-Plugins/Event-PVP-Plugin-1.1.0-BetaPurpur/src/main/java/de/zfzfg/core/util/ItemUtil.java): Setzt Item-Namen und Lore ohne die unschönen Kursiv-Standardschrift-Artefakte von Vanilla-Minecraft.
* [`TeleportUtil.java`](file:///c:/Users/zfzfg/Documents/HammerMegaProjekte/selfmadePlugins/Plugins/Event-PVP-Plugins/Event-PVP-Plugin-1.1.0-BetaPurpur/src/main/java/de/zfzfg/core/util/TeleportUtil.java): Nutzt asynchrone Chunk-Ladevorgänge auf Paper/Purpur (`teleportAsync`) und synchrone Teleporte auf Spigot.

### 3. Registry- & Tag-Sicherheit
* [`PaperRegistryHelper.java`](file:///c:/Users/zfzfg/Documents/HammerMegaProjekte/selfmadePlugins/Plugins/Event-PVP-Plugins/Event-PVP-Plugin-1.1.0-BetaPurpur/src/main/java/de/zfzfg/core/web/PaperRegistryHelper.java): Kapselt Paper 1.21+ `EnchantmentTagKeys` (`TREASURE`, `CURSE`) in einer isolierten Helper-Klasse, sodass der Spigot-Classloader beim Laden von [`MaterialCatalog.java`](file:///c:/Users/zfzfg/Documents/HammerMegaProjekte/selfmadePlugins/Plugins/Event-PVP-Plugins/Event-PVP-Plugin-1.1.0-BetaPurpur/src/main/java/de/zfzfg/core/web/MaterialCatalog.java) keinen Fehler wirft.

---

## 🔄 5. Checkliste für Administratoren

1. **Server-Wahl:** Für beste Performance wird **Purpur 26.2** empfohlen. Das Plugin läuft ebenso auf **Purpur/Paper 26.3**, **Paper 26.2** und **Spigot 26.2**.
2. **Java-Version:** Sicherstellen, dass der Server mit `java -version` mindestens **Java 25** meldet.
3. **Konfigurationen:**
   * Bestehende YAML-Dateien (`config.yml`, `events.yml`, `worlds.yml`, `equipment.yml`, `messages_*.yml`) werden automatisch geladen und behalten alle Einstellungen.
4. **Web-Interface:**
   * Der Webserver startet auf Port `8085` (konfigurierbar in `web-config.yml`).
   * Authentifizierung für Administratoren erfolgt im Spiel über `/eventpvp webtoken`.
   * Das Panel spricht reines HTTP. Für Zugriff von außen einen Reverse-Proxy mit HTTPS davorschalten, `bind-address: "127.0.0.1"` setzen und `public-url` auf die Proxy-URL stellen. Anfragen mit fremdem `Origin` (andere Domain oder anderer Port) werden mit 403 abgewiesen.
   * Optional: `security.allowed-ips` (IP-Allowlist) und `security.bind-session-to-ip` in der `web-config.yml`.
