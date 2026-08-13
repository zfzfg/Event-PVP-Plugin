# Changelog 1.1.0 (Purpur 26.2 Migration)

## [1.1.0] - 2026-08-13

### Plattform & Build
- **Purpur 26.2:** Umstellung von Spigot-API 1.19.4 auf `purpur-api:26.2.build.2618-stable` (Java 21).
- **plugin.yml:** `api-version` auf `'26.2'` aktualisiert.
- **Maven Shade Plugin:** Ausschluss von `net.kyori:*`, `org.purpurmc.purpur:*`, `io.papermc.paper:*`, `org.spigotmc:*`, `com.google.guava:*`, `com.google.code.gson:*` konfiguriert (schlankes JAR, keine ClassLoader-Konflikte mit dem Server).
- **Surefire:** `-XX:+EnableDynamicAgentLoading` für Mockito unter Java 21 konfiguriert.

### Adventure Migration (Ersatz für BungeeCord-Chat)
- **Vollständige Entfernung von `net.md-5:bungeecord-chat`:** 0 verbleibende Referenzen im gesamten Quelltext.
- **`Text.java` Bridge:** Zentrale, thread-sichere Übersetzung von Legacy-Nachrichten (`&`-Codes und `&#RRGGBB`-Hex) in Adventure 5.2.0 `Component` mit 4096-Einträge Cache.
- **Interaktive Chat-Nachrichten:** Umstellung aller Klick- und Hover-Aktionen auf `Text.button` (`ClickEvent.runCommand`), `Text.link` (`ClickEvent.openUrl`) und `ClickEvent.copyToClipboard`:
  - `RequestManager.java`: PvP-Wager Herausforderungen mit [Annehmen] und [Ablehnen] Buttons
  - `MatchManager.java`: Globaler Zuschauereinladungs-Broadcast mit [Zuschauen] Button
  - `CommandRequestManager.java`: Skip- und Gegenangebot-Nachrichten
  - `PvPWagerGuiCommand.java`: Chat-Herausforderungen
  - `EventSession.java`: Event-Broadcast-Join-Button und Zuschauer-Leave-Button
  - `EventPvpCommand.java` & `WebTokenSubCommand.java`: Klickbarer Web-Token (Copy to Clipboard) und Webpanel-URL Link
- **`TextUtil.java`:** Intern vollständig auf `Text.of()` / `Text.toLegacy()` umgestellt bei Erhalt der bestehenden String-Signatur für Kompatibilität mit Bukkit-Alt-APIs.

### Modernisierung von Alt-APIs
- **Title-API:** `EventSession.java` von `player.sendTitle(...)` auf Adventure `player.showTitle(...)` mit `net.kyori.adventure.title.Title` und `Title.Times` (`Duration`) migriert.
- **Enchantment-Auflösung:** `ConfiguredItemFactory.java` modernisiert: bevorzugt `org.bukkit.Registry.ENCHANTMENT` mit `NamespacedKey` vor sicherem Fallback auf alte Bukkit-Namen.
- **Trank-API:** `ConfiguredItemFactory.java` von `PotionData`-Reflection auf direktes `PotionMeta.setBasePotionType(PotionType)` migriert.
- **TPS-Ermittlung:** `WebApiHandler.java` von Reflection auf natives `Bukkit.getServer().getTPS()` umgestellt.

### Performance & Optimierung
- **PlayerMoveEvent:** Handler in `pvpwager/WorldChangeListener`, `eventplugin/WorldChangeListener` und `VoidProtectionListener` mit `ignoreCancelled = true` und `event.hasChangedBlock()` optimiert.
- **CommandCooldownManager:** Thread-sichere Synchronisation (`synchronized checkAndApply`) ergänzt.

### Test-Suite & Qualitätssicherung
- **Testabdeckung:** Von 93 Baseline-Tests auf **152 automatisierte Unit-Tests** ausgebaut (+59 neue Tests):
  - `TextTest.java` (14 Tests): Parsing, Hex-Farben, Caching, Button- & Link-Erzeugung, Section-Codes
  - `TextUtilTest.java` (7 Tests): Delegation, Formatierung, Strip, Idempotenz
  - `TextButtonTest.java` (8 Tests): Edge-Cases, null-Safety, überlange Strings, Cache-Limits
  - `EnchantmentResolveTest.java` (7 Tests): Namespaces, Normalisierung, Groß-/Kleinschreibung
  - `ConfiguredItemAmountTest.java` (3 Tests): Range-Clamping und Parsing
  - `MessageUtilTest.java` (4 Tests): Zeitformatierung, Farbdelegation, Listen
  - `ColorUtilTest.java` (2 Tests): Delegation und Farbcode-Entfernung
  - `CommandCooldownManagerTest.java` (2 Tests): Cooldown-Verhalten und Spieler-Bereinigung
  - `MvWorldInfoTest.java` (2 Tests): JSON-Serialisierung
  - `MigrationRegressionTest.java` (5 Tests): Sicherstellung von 0 `md_5`, 0 `spigot().sendMessage`, korrekter API-Version und Dependencies
  - `ResourceConfigTest.java` (2 Tests): Validierung aller mitgelieferten YAML-Dateien (`messages_de.yml`, `messages_en.yml`, `config.yml`, etc.)
  - `ConcurrencyTest.java` (3 Tests): Parallele Cache-Zugriffe, Rate-Limiter und Cooldown-Manager
  - `TextureOverridePathTest.java` & `MvWorldInputValidationTest.java`: Gehärtete Sicherheits- und Path-Traversal-Tests
