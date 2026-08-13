# 🌐 Minecraft Server Kompatibilitäts- & Versionsbericht

Dieses Dokument enthält einen detaillierten technischen Leitfaden und eine Kompatibilitätsmatrix für das **Event-PVP-Plugin (Version 1.1.0)**. Es beschreibt unterstützte Minecraft-Server-Versionen, Software-Forks, Java-Laufzeitanforderungen sowie Abhängigkeiten.

---

## 📊 1. Kompatibilitätsmatrix

| Server-Software | Minecraft Version | Kompatibilität | Status & Empfehlung |
|---|---|---|---|
| **Purpur** | **26.2 / 1.21.x** (Build 2618+) | 🟢 **100% Nativ** | ⭐ **Offizielle Referenzplattform** (vollständig live-getestet & optimiert) |
| **Paper** | **1.21.x** | 🟢 **100% Nativ** | ✅ **Vollständig unterstützt** (alle Adventure- & Paper-APIs vorhanden) |
| **Paper** | **1.20.5 – 1.20.6** | 🟢 **Kompatibel** | ✅ **Unterstützt** (Java 21 und Adventure 5.x vorhanden) |
| **Folia** | **1.21.x** | 🟡 **Kompatibel\*** | ⚠️ Grundfunktionen laufen; weltübergreifende Async-Operationen beachten |
| **Pufferfish** | **1.21.x** | 🟢 **Kompatibel** | ✅ Vollständige Paper-API-Kompatibilität |
| **Spigot (Vanilla Spigot)** | **1.21.x** | 🔴 **Nicht unterstützt** | ❌ Fehlt native Adventure Component & `getPluginMeta()` API |
| **Spigot / Paper** | **1.19.4 & älter** | 🔴 **Inkompatibel** | ❌ Java 21 / Adventure 5.2.0 / `api-version: '26.2'` nicht vorhanden |

---

## ☕ 2. Java-Laufzeitumgebung (Java Runtime)

* **Mindestanforderung:** **Java 21 (LTS)** oder neuer (z. B. Eclipse Temurin 21, OpenJDK 21, GraalVM 21).
* **Bytecode-Zielversion:** Java 21 (Class File Version `65.0`).
* **Hintergrund:**
  * Minecraft-Server ab Version 1.20.5 setzen Java 21 zwingend voraus.
  * Das Plugin nutzt moderne Java-21-Sprachfeatures, Pattern Matching und thread-sichere Concurrency-Strukturen (`ConcurrentHashMap`, synchronisierte Cooldown-Manager).
* **JVM-Empfehlungen:**
  * Für Server-Performance werden die standardmäßigen **Aikar's Flags** mit G1GC empfohlen.

---

## 🧩 3. Erforderliche & Optionale Plugin-Abhängigkeiten

### 🔒 Pflicht-Abhängigkeiten (Hard Dependencies)
Ohne diese Plugins startet das Event-PVP-Plugin nicht oder schaltet sich sicherheitsbedingt ab:

1. **Multiverse-Core**
   * **Multiverse-Core 5 (Empfohlen):** Vollständige Anbindung an die typsichere Java-API. Ermöglicht asynchrones Erstellen von Welten ohne Request-Timeouts, direkte Weltlöschung ohne lästige Bestätigungscodes und präzise Biome-/Generator-Steuerung.
   * **Multiverse-Core 4:** Ebenfalls abwärtskompatibel über Konsolen-Dispatching unterstützt.
2. **Vault**
   * Notwendig für das Wirtschaftssystem (Wetteinsätze mit Spielgeld bei PvP-Kämpfen).
   * Erfordert ein kompatibles Economy-Plugin auf dem Server (z. B. *EssentialsX*, *Treasury*, etc.).
3. **InventoryBackup**
   * Sichert Spieler-Inventare vor Event-Beitritten und PvP-Matches und stellt sie nach Rundenende oder bei Disconnects verlustfrei wieder her.

---

### ✨ Optionale Erweiterungen (Soft Dependencies)
Diese Plugins erweitern den Funktionsumfang, sind aber für den Betrieb nicht zwingend erforderlich:

| Plugin | Funktion im Event-PVP-Plugin |
|---|---|
| **PlaceholderAPI (PAPI)** | Stellt Platzhalter für Spielerstatistiken (Siege, K/D, Event-Gewinne, aktuelle Einsätze) für Scoreboards, Chat und Tablisten bereit. |
| **AJLeaderboards** | Automatisierte Ranglisten für Top-Sieger und Vielspieler. |
| **DecentHolograms** | Interaktive 3D-Hologramm-Ranglisten in Event-Lobbys und PvP-Spawnzonen. |
| **PvPManager** | Synchronisiert Combat-Tagging, verhindert unerwünschten PvP-Schutz während laufender Wager-Matches. |

---

## 🛠️ 4. Technische Schnittstellen & Architektur im Detail

### 1. Kyori Adventure 5.2.0 (Component Chat & GUI)
* **Kein BungeeCord-Chat mehr:** Das veraltete `net.md-5:bungeecord-chat` wurde vollständig entfernt.
* **Adventure Components:** Sämtliche Spielerausgaben (Chat, Actionbars, Titles, Inventar-Titel und Item-Lores) nutzen die Paper/Purpur-native Adventure-Component-Pipeline.
* **Klickbare Aktionen:** Interaktive Chat-Nachrichten nutzen `ClickEvent` (`runCommand`, `openUrl`, `copyToClipboard`) und `HoverEvent.showText`.

### 2. Paper `getPluginMeta()` API
* Das veraltete Bukkit `getDescription()` wurde project-weit durch das neue Paper `getPluginMeta()` ersetzt, wodurch Versions-, Autoren- und Namensabfragen zukunftssicher sind.

### 3. Registry & Tag-basierte APIs
* **Verzauberungen:** Auflösung über `org.bukkit.Registry.ENCHANTMENT` mit `NamespacedKey` (z. B. `minecraft:sharpness`).
* **Trank-Effekte:** Nutzung von `Registry.POTION_EFFECT_TYPE` und nativem `PotionMeta.setBasePotionType(...)` anstelle veralteter Reflection.
* **Attribute:** Lebenspunkte werden modern über `player.getAttribute(Attribute.MAX_HEALTH)` verwaltet.

---

## 🔄 5. Upgrade-Leitfaden für Administratoren (von Version 1.0.9)

1. **Server-Software prüfen:** Sicherstellen, dass der Server auf **Purpur 26.2** oder **Paper 1.21.x** mit **Java 21** läuft.
2. **Konfigurationen:**
   * Alle bestehenden YAML-Dateien (`config.yml`, `events.yml`, `worlds.yml`, `equipment.yml`, `messages_*.yml`) sind **100% abwärtskompatibel** und werden automatisch übernommen.
3. **Multiverse:**
   * Falls Multiverse-Core 5 genutzt wird, profitiert das Plugin automatisch von der neuen API-Integration (keine manuelle Konfigurationsänderung nötig).
4. **Web-Interface:**
   * Das integrierte Webinterface läuft wie gewohnt auf dem konfigurierten Port (Standard: `8085`) und ist sofort einsatzbereit.
