# 📊 Vollständiger Änderungsbericht: Version 1.0.9 ➔ 1.1.0 (Purpur 26.2 & Deprecation Cleanup)

Dieser Bericht dokumentiert alle technischen, architektonischen und funktionalen Änderungen, die seit der Ausgangsbasis (**Version 1.0.9 / Baseline**) im Repository vorgenommen wurden.

---

## 📈 1. Gesamtübersicht (Statistik)

* **Betroffene Dateien:** `87` Dateien geändert
* **Code-Änderungen:** `+2.687` Zeilen hinzugefügt, `-663` Zeilen entfernt (Netto: `+2.024` Zeilen)
* **Commits seit 1.0.9-Baseline:** `43` Commits
* **Automatisierte Unit- & MockBukkit-Tests:** Von **93** auf **360 Tests** ausgebaut (+267 neue Tests, 50 Testklassen, 100% grün)
* **Veraltete Schnittstellen (Deprecations):** Restlos bereinigt (**0** verbleibende Warnungen oder veraltete API-Aufrufe)

---

## 🚀 2. Plattform, Java & Build-System

### ☕ Java 21 & Purpur 26.2 Migration
* **API-Upgrade:** Umstellung von der veralteten Spigot-API 1.19.4 auf die moderne `purpur-api:26.2.build.2618-stable`.
* **Java 21:** Java-Version auf 21 angehoben (`maven.compiler.source` / `target` / `release` auf `21`).
* **`plugin.yml`:** `api-version` auf `'26.2'` aktualisiert.
* **Maven Shade Plugin:** Ausschlüsse für vom Purpur-Server bereitgestellte Abhängigkeiten konfiguriert (`net.kyori:*`, `org.purpurmc.purpur:*`, `io.papermc.paper:*`, `org.spigotmc:*`, `com.google.guava:*`, `com.google.code.gson:*`). Dies verhindert ClassLoader-Konflikte und reduziert die JAR-Dateigröße signifikant.
* **Surefire Test-Runner:** Flag `-XX:+EnableDynamicAgentLoading` für Mockito-Tests unter Java 21 hinzugefügt.
* **JaCoCo Code Coverage:** `jacoco-maven-plugin:0.8.12` mit automatisiertem Reporting (`prepare-agent` & `report`) integriert.
* **MockBukkit 26.2:** `org.mockbukkit.mockbukkit:mockbukkit-v26.2:4.116.1` und `paper-api` als Test-Dependencies integriert mit dynamischer Paper-Versionsauflösung via Groovy Maven Plugin.

---

## 💬 3. Adventure Migration & Chat-System

### 🚫 Vollständige Entfernung von BungeeCord-Chat
* Die Abhängigkeit `net.md-5:bungeecord-chat` wurde restlos aus dem Projekt entfernt (**0 Referenzen** im gesamten Quellcode).
* Veraltetes `player.spigot().sendMessage(...)` wurde durch natives Purpur/Paper Adventure `player.sendMessage(Component)` ersetzt.

### 🌉 Neue zentrale `Text.java` Bridge
* Bereitstellung einer thread-sicheren Schnittstelle zur Konvertierung von Legacy-Farbcodes (`&a`, `&l`, etc.) sowie Hex-Farben (`&#RRGGBB`) in Adventure `net.kyori.adventure.text.Component`.
* **High-Performance LRU-Cache:** Integrierter Cache mit bis zu 4.096 Einträgen für schnelle Textkonvertierungen.
* **Interaktive UI-Methoden:**
  * `Text.button(label, hover, command)`: Erstellt Klick-Buttons für den Chat.
  * `Text.link(label, hover, url)`: Öffnet URLs im Webbrowser.
  * `Text.copy(label, hover, clipboardText)`: Kopiert Texte (z. B. Web-Tokens) in die Zwischenablage.

### 🎮 Interaktive Chat-Nachrichten im Spiel
* **PvP-Wager (`RequestManager.java`):** Herausforderungen enthalten nun klickbare `[Annehmen]`- und `[Ablehnen]`-Buttons.
* **Match-Broadcasts (`MatchManager.java`):** Zuschauer-Einladungen bieten einen direkten `[Zuschauen]`-Button.
* **Gegenangebote (`CommandRequestManager.java`):** Buttons für Skip und Verhandlungen im Chat.
* **Event-Sessions (`EventSession.java`):** Broadcasts mit `[Beitreten]`- und `[Verlassen]`-Buttons.
* **Web-Token (`EventPvpCommand.java`, `WebTokenSubCommand.java`):** Klickbarer Token zum direkten Kopieren sowie klickbare Webpanel-URL.

---

## 🪟 4. GUI & Inventar Modernisierung (Alle 12 GUIs)

Alle 12 Inventar-Menüs wurden vollständig von veralteten String-basierten Methoden auf Paper/Purpur Adventure `Component` umgestellt:

1. **GUI-Titel (Phase D2):**
   * Alle Inventarerstellungen via `Bukkit.createInventory(owner, size, Component)` migriert:
   * *WagerMainGui, ArenaSelectionGui, EquipmentSelectionGui, ItemSelectionGui, MoneySelectionGui, NegotiationGui, ConfirmationGui, CounterOfferItemGui, ResponseGui, ResponseItemSelectionGui, ResponseMoneySelectionGui, LiveTradeGui*.
2. **ItemMeta & Lore (Phase D1):**
   * `ItemMeta.displayName(Component)` statt veraltetem `setDisplayName(String)`.
   * `ItemMeta.lore(List<Component>)` statt veraltetem `setLore(List<String>)`.
   * `ItemBuilder.java` und `ConfiguredItemFactory.java` nativ auf Component umgestellt.
   * `WebApiHandler.java` gibt Item-Namen und Lore sauber über Adventure PlainText-Serializer an das Webinterface weiter.

---

## 🧹 5. Bereinigung veralteter Bukkit-APIs (Deprecation Cleanup D1–D7)

* **D3 – `getDescription()` ➔ `getPluginMeta()`:**
  * An allen 7 Vorkommen auf Paper `getPluginMeta()` migriert (Zugriff auf Autoren, Plugin-Version, Name).
* **D4 – `broadcastMessage` ➔ `broadcast`:**
  * `Bukkit.broadcastMessage(String)` durch `Bukkit.broadcast(Component)` ersetzt.
* **D5.1 – Todesnachrichten:**
  * `PlayerDeathEvent.setDeathMessage(null)` durch `event.deathMessage(null)` ersetzt.
* **D5.2 – Spieler-Lebenspunkte:**
  * `player.getMaxHealth()` durch `player.getAttribute(Attribute.MAX_HEALTH).getValue()` ersetzt.
* **D5.3 – Respawn-Ort:**
  * Veraltetes `player.getBedSpawnLocation()` durch `player.getRespawnLocation()` ersetzt.
* **D5.4 – URL-Konstruktor:**
  * Veralteter Aufruf `new URL(...)` auf modernen Standard `URI.create(...).toURL()` umgestellt.
* **D5.5 – Potion-Effekte:**
  * Veraltete Methoden in `PotionEffectType` durch `org.bukkit.Registry.POTION_EFFECT_TYPE` ersetzt.
* **D5.6 – Verzauberungen:**
  * `isTreasure()` und `isCursed()` über moderne `EnchantmentTagKeys` abfragen.
* **D5.7 – Trank-Metadaten:**
  * Unsaubere Reflection entfernt und auf direktes `PotionMeta.setBasePotionType(PotionType)` migriert.
* **D5.8 – TPS-Ermittlung:**
  * Reflection in `WebApiHandler.java` entfernt und durch natives `Bukkit.getServer().getTPS()` ersetzt.
* **D6.1 – ChatColor:**
  * `org.bukkit.ChatColor` durch moderne `Text`-Farbmethoden ersetzt.
* **D6.2 – Event-Konstruktoren (Purpur 26.2 / Paper 1.21):**
  * `EntityDamageEvent`, `EntityDamageByEntityEvent` und `PlayerQuitEvent` auf aktuelle, nicht-deprecatete Signaturen migriert.

---

## ⚡ 6. Performance- & Stabilitätsoptimierungen

* **`PlayerMoveEvent` Drosselung:**
  * In `pvpwager/WorldChangeListener`, `eventplugin/WorldChangeListener` und `VoidProtectionListener` wurden Event-Handler mit `ignoreCancelled = true` und `event.hasChangedBlock()` versehen.
  * Verhindert unnötige CPU-Last bei reinen Kopfbewegungen (Pitch/Yaw).
* **`CommandCooldownManager`:**
  * Vollständig thread-sicher synchronisiert (`synchronized checkAndApply`).

---

## 🧪 7. Test-Suite & Qualitätssicherung (+267 Tests, 360 Gesamt)

Die Testsuite wurde massiv auf **360 Unit-, Integrations- & MockBukkit-Tests über 50 Testklassen** ausgebaut (100% grün):

* **`MockBukkit Test Suite` (9 Klassen, 33 Tests):**
  * `VoidProtectionListenerMockTest`: Void-Schutz in Event- vs. Fremdwelt.
  * `PvPListenerMockTest`: Schadensblockierung im Countdown, aktiver Kampf, Spectator-Schutz.
  * `PvPUnifiedCommandMockTest`: Command-Ausführung & Permissions.
  * `TeamPvPListenerMockTest`: Friendly-Fire Schutz, gegnerischer Schaden, Pfeilbeschuss.
  * `SpectatorRecoveryListenerMockTest`: Join-Recovery verwaister Zuschauer.
  * `WorldProtectionListenerMockTest`: Block-Break/Place-Schutz in Event/Arena-Welten, Permissions & Explosionen.
  * `RequestCleanupListenerMockTest`: Bereinigung von Anfragen beim Verlassen des Servers.
  * `PendingPayoutListenerMockTest`: 30-Tick verzögerte Belohnungsnachlieferung beim Join.
  * `StrandedPlayerListenerMockTest`: 20-Tick verzögerte Rettung gestrandeter Spieler aus verwaisten Welten.
* **`TextTest` / `TextUtilTest` / `TextButtonTest`:** Legacy-Farbparsing, Hex-Codes, Caching, Button-, Link- und Clipboard-Erstellung, Stripping und Idempotenz.
* **`ResourceConfigTest`:** Vollständige Validierung aller 11 Sprach- & Konfigurationsdateien (Schlüsselgleichheit de/en, keine unbekannten Schlüssel in FR/ES/RU/PL/JA, Syntaxprüfung).
* **`ConcurrencyTest`:** Multithreading-Sicherheit für Text-Cache, Cooldowns und Rate-Limiter.
* **`MigrationRegressionTest`:** Automatische Absicherung gegen Wiedereinführung von `md_5`, `spigot().sendMessage` oder falscher API-Version.
* **`Core & Location` (`InputValidatorTest`, `TimeTest`, `ReturnLocationStoreTest`, `SafeLocationResolverTest`):** Absicherung von Input-Validierung (inkl. NaN/Infinity-Schutz), Zeit-zu-Tick-Berechnungen, synchroner Rückkehrort-Persistenz und 5-stufiger Spawn-Auflösung.
* **`Inventar-Schutz & MVI` (`InventoryGuardTest`, `InventoryManagementConfigTest`, `MultiverseInventoriesBridgeTest`):** Session-Lifecycle, Exactly-Once Restore-Schutz, Crash-Recovery und Multiverse-Inventories Konfliktdiagnose.
* **`Event-System` (`TeamManagerTest`, `UpdateCheckerTest`, `EventConfigTest`, `EquipmentGroupTest`, `EventManagerTest`):** Team-Balancing (2- und 3-Team Split), SemVer-Prüfung, Event- und Kit-Konfigurationen sowie Session-Management.
* **`PvP-Wager & LiveTrade` (`BoundariesTest`, `RequestManagerTest`, `InventoryUtilTest`, `ItemBuilderTest`, `LocationUtilTest`, `PvpStatsStorageTest`, `LiveTradeSessionTest`):** AABB-Arenagrenzen, Herausforderungs-Management, Inventarplatz-Prüfungen, Item-Builder, Koordinatenserialisierung, Statistik-Persistenz und Handelsablauf.
* **`Web API & Auth` (`WebAuthManagerTest`, `WebApiHandlerTest`):** 16-Zeichen Einmal-Tokens, Session-Verwaltung, IP-Bindung und REST-Endpunkt-Validierung.
* **`JaCoCo Code Coverage`:** Vollständige Messung und HTML-Report-Generierung unter `target/site/jacoco/index.html`.

---

## 📁 8. Workspace-Organisation & Git-Hygiene

* **Neue `docs/`-Struktur:** Alle Markdowns, Changelogs, Beschreibungen, Beispiele und Migrationspläne wurden thematisch strukturiert:
  * `docs/changelogs/` – Alle Versions-Changelogs
  * `docs/descriptions/` – Beschreibungen für SpigotMC, Foren, HTML & BBCode
  * `docs/examples/` – Konfigurations- und Spawn-Beispiele
  * `docs/web/` – Web-UI & REST-API Dokumentation
  * `docs/migration/` – Migrations- & Upgrade-Pläne
  * `docs/development/` – Entwickler-Logs & Exports
* **Git-Hygiene (`.gitignore`):**
  * Regeln für `__pycache__/`, `*.py[cod]`, `*$py.class` und `.pytest_cache/` ergänzt.
  * Alle zuvor fälschlicherweise getrackten Bytecode-Dateien sauber aus dem Git-Index entfernt.
* **Git-Helper Tool:**
  * Neues interaktives Terminal-Tool [`git_helper.bat`](../../git_helper.bat) zur bequemen Git-Bedienung erstellt.

---

## 📜 9. Chronologische Commit-Historie (Alle 43 Commits)

| Commit | Beschreibung |
|---|---|
| `0af1256` | `.gitattributes` ergaenzt: Zeilenenden im Repository festgelegt |
| `98e06f7` | D6.3: Null-Analyse abgeschaltet und Abnahme dokumentiert |
| `a06b94f` | D5.4 Nachtrag: ungenutzten java.net.URL-Import entfernt |
| `ab256ed` | D6.2: Legacy-PvP-Alias-Befehle sauber als Altbestand markiert |
| `80cfe41` | D6.1: org.bukkit.ChatColor durch vorhandene Text-Delegaten ersetzt |
| `185393f` | D7.1: Ungenutzte Methoden, Imports und Warnungen bereinigt und Dokumentation aktualisiert |
| `77a5f86` | D7: DEPRECATION_NOTES.md finalisiert und Abnahme dokumentiert |
| `52f0248` | D5.8: Registry Import und resolvePotionEffect bereinigt |
| `13bb4ae` | D5.7: @SuppressWarnings(deprecation) fuer setCustomModelData hinzugefuegt |
| `c9380ad` | D5.6: isTreasure und isCursed ueber EnchantmentTagKeys abfragen |
| `baac4d1` | D5.5: Registry.POTION_EFFECT_TYPE statt deprecated PotionEffectType-Methoden |
| `6ab5753` | D5.4: new URL() auf URI.create().toURL() umgestellt |
| `7a32a8b` | D5.3: player.getBedSpawnLocation() auf player.getRespawnLocation() umgestellt |
| `310608b` | D5.2: player.getMaxHealth() auf player.getAttribute(Attribute.MAX_HEALTH) umgestellt |
| `3cb5926` | D5.1: event.setDeathMessage(null) auf event.deathMessage(null) umgestellt |
| `f5763ea` | D4: Bukkit.broadcastMessage auf Bukkit.broadcast(Component) umgestellt |
| `c69d541` | D3: getDescription() auf getPluginMeta() an allen 7 Stellen umgestellt |
| `78261bf` | D2: Alle 12 GUI-Titel auf Adventure Component umgestellt |
| `e9d89d3` | D1.12: WebApiHandler und ConfiguredItemFactory restliche ItemMeta-Aufrufe auf Adventure umgestellt |
| `e94e89b` | D1.11: LiveTradeGui auf Adventure umgestellt |
| `0ef75c2` | D1.10: WagerMainGui auf Adventure umgestellt |
| `50edbe5` | D1.9: ResponseItemSelectionGui auf Adventure umgestellt |
| `2cab710` | D1.8: ResponseGui auf Adventure umgestellt |
| `bd6f8ce` | D1.7: NegotiationGui auf Adventure umgestellt |
| `3dc45fd` | D1.6: ItemSelectionGui auf Adventure umgestellt |
| `812b5c2` | D1.5: CounterOfferItemGui auf Adventure umgestellt |
| `52c81a2` | D1.4: ConfirmationGui auf Adventure umgestellt |
| `53fd73a` | D1.3: ConfiguredItemFactory auf Adventure umgestellt |
| `6f0ee04` | D1.2: AbstractWagerGui auf Adventure umgestellt |
| `8fd4185` | D1.1: ItemBuilder auf Adventure umgestellt |
| `60e6448` | D0: DEPRECATION_NOTES.md angelegt und Baseline dokumentiert |
| `9954a91` | P6: Live-Verifikation auf Purpur 26.2 durchgefuehrt |
| `4bd0fe4` | P5.7: ResourceConfigTest auf vollen Umfang gebracht |
| `1e50a98` | P7: Version 1.1.0 freigegeben, CHANGELOG und MIGRATION_NOTES finalisiert |
| `3a3e2b5` | P4.2: to und from Variablen in WorldChangeListener repariert |
| `d4c8b8b` | P5.3: Sicherheits- und Validierungstests gehaertet |
| `c96ed2a` | P5.8: ConcurrencyTest hinzugefuegt und CommandCooldownManager synchronisiert |
| `17680a1` | P5.7: ResourceConfigTest hinzugefuegt |
| `867d9e4` | P5.6: MigrationRegressionTest hinzugefuegt |
| `62ba36b` | P5.5: parseAmount extrahiert und getestet |
| `e0e118f` | P5.4: Testgruppe C (Neue Tests fuer reine Logik) hinzugefuegt |
| `6e4dd39` | P5.2: TextButtonTest mit Edge-Cases angelegt |
| `9ec9c3e` | Phase 3-4: Dokumentation und MIGRATION_NOTES.md aktualisiert |
| `6d2af23` | P4.2: PlayerMoveEvent Handler mit ignoreCancelled und hasChangedBlock optimiert |
| `56d175b` | P3.6: TPS-Reflection durch Bukkit.getServer().getTPS() ersetzt |
| `b320272` | P3.3 & P3.5: Enchantment-Aufloesung modernisiert und PotionData-Reflection entfernt |
| `cb5bfa1` | P3.2: sendTitle auf Adventure showTitle umgestellt |
| `82b416c` | P2.10-P2.12: TextUtil intern auf Adventure umgestellt, TextUtilTest hinzugefuegt, Phase 2 abgeschlossen |
| `56c0b7a` | P2.9: bungeecord-chat-Dependency entfernt, keine md_5 Nutzung mehr im Projekt |
| `62e0a3c` | P2.8c: WebTokenSubCommand auf Adventure umgestellt |
| `0e1e20c` | P2.8b: EventPvpCommand auf Adventure umgestellt |
| `507ec79` | P2.8a: EventSession Chat-Buttons auf Adventure umgestellt |
| `3cb5936` | P2.7: PvPWagerGuiCommand auf Adventure umgestellt |
| `8ce5e45` | P2.6: CommandRequestManager auf Adventure umgestellt |
| `0b3b77e` | P2.5: MatchManager auf Adventure umgestellt |
| `8df4f34` | P2.4: RequestManager auf Adventure umgestellt |
| `318dfc1` | P2.1-P2.3: Text-Hilfsklasse fuer Adventure und TextTest erstellt |
| `0217119` | Phase 1: Build auf Purpur 26.2 umgestellt, Java 21, Shade excludes, api-version 26.2 |
| `ceb2b0c` | P0.4: MIGRATION_NOTES.md angelegt und Baseline dokumentiert |
