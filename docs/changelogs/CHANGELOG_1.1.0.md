# Changelog 1.1.0-beta (Purpur 26.2 Migration)

## [1.1.0-beta] - 2026-08-13

### Plattform & Build
- **Purpur 26.2:** Umstellung von Spigot-API 1.19.4 auf `purpur-api:26.2.build.2618-stable` (Java 21).
- **plugin.yml:** `api-version` auf `'26.2'` aktualisiert.
- **Maven Shade Plugin:** Ausschluss von `net.kyori:*`, `org.purpurmc.purpur:*`, `io.papermc.paper:*`, `org.spigotmc:*`, `com.google.guava:*`, `com.google.code.gson:*` konfiguriert (schlankes JAR, keine ClassLoader-Konflikte mit dem Server).
- **Surefire:** `-XX:+EnableDynamicAgentLoading` für Mockito unter Java 21 konfiguriert.
- **Beta-Versionierung:** Version auf `1.1.0-beta` gesetzt. Der bestehende UpdateChecker filtert Vorabversionen automatisch über den Modrinth `version_type` und die Config-Option `stable-only: true` (Standard) — normale Nutzer sehen keine Update-Benachrichtigung für Beta/Alpha-Releases.

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

### i18n & Lokalisierung
- **Konsolenausgaben lokalisiert:** Alle Admin-sichtbaren Konsolennachrichten (z. B. Spawn-Typ-Warnungen) werden nun über das i18n-System (`plugin.getConsoleMsg(...)`) in allen 7 Sprachen ausgegeben.
- **Neuer Lokalisierungsschlüssel:** `spawn-type-unsupported` in `messages.console` aller 7 Sprachdateien (DE, EN, FR, ES, PL, RU, JA) eingetragen.
- **`// i18n-ignore`-Annotationen:** Technische Code-Fragmente (Minecraft-Namespace-Prefixe, Missing-Key-Sentinel) als bewusst nicht übersetzbar markiert.
- **i18n-Audit-System:** Python-basiertes Audit-Tool (`tools/i18naudit/`) mit 11 automatischen Detektoren (Key-Parity, Hardcoded-Strings, Placeholder-Mismatches, YAML-Syntax u. v. m.) und 88 Self-Tests.
- **Report-Zeitzone:** Audit-Reports verwenden jetzt die lokale System-Zeitzone (`astimezone()`) statt UTC.

### Workspace-Organisation & Dokumentation
- **`docs/`-Verzeichnisstruktur:** Alle 28 losen Markdown- und Textdateien thematisch in Unterordner gegliedert:
  - `docs/changelogs/` – Versions-Changelogs und Diff-Berichte
  - `docs/descriptions/` – Plattform-Beschreibungen (Markdown, BBCode, HTML)
  - `docs/examples/` – Konfigurations- und Spawn-Beispiele (DE/EN)
  - `docs/web/` – Web-UI & REST-API Dokumentation
  - `docs/migration/` – Migrations- & Upgrade-Pläne
  - `docs/development/` – Entwickler-Logs, Exports und Guides
- **`docs/README.md`:** Zentraler Dokumentationsindex mit Links zu allen Unterordnern und Dokumenten.
- **`docs/SERVER_COMPATIBILITY.md`:** Detaillierte Server-Kompatibilitätsmatrix (Purpur, Paper, Folia, Spigot, Versionsanforderungen, Java 21).
- **`docs/changelogs/VERSION_DIFF_1.0.9_TO_1.1.0.md`:** Vollständiger technischer Änderungsbericht mit allen 43+ Commits, Statistiken und Architektur-Übersicht.
- **`docs/development/ALPHA_RELEASE_GUIDE.md`:** Anleitung zum Veröffentlichen von Alpha/Beta-Versionen ohne Update-Benachrichtigung (Modrinth `version_type` + `stable-only`-Config).
- **Git-Helper Tool:** Interaktives Terminal-Menü (`git_helper.bat`) für Status, Diff, Commit, Branch, Stash und Pycache-Cleanup.
- **Git-Hygiene:** `.gitignore` erweitert für `__pycache__/`, `*.py[cod]`, `.pytest_cache/`; alle getrackten Bytecode-Dateien aus dem Index entfernt.
- **`.gitattributes`:** Zeilenenden im Repository festgelegt (`* text=auto`, `*.bat text eol=crlf`).

### Test-Suite & Qualitätssicherung
- **Testabdeckung:** Von 93 Baseline-Tests auf **176 automatisierte Unit-Tests** ausgebaut (+83 neue Tests):
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
  - `ResourceConfigTest.java` (26 Tests): Ladbarkeit aller 11 YAML-Ressourcen, Schluesselgleichheit de/en, keine unbekannten Schluessel in den fuenf Uebersetzungen, restloses Parsen aller Nachrichtenwerte, plugin.yml-Commands mit description
  - `ConcurrencyTest.java` (3 Tests): Parallele Cache-Zugriffe, Rate-Limiter und Cooldown-Manager
  - `TextureOverridePathTest.java` & `MvWorldInputValidationTest.java`: Gehärtete Sicherheits- und Path-Traversal-Tests
- **i18n-Audit Self-Tests:** 88 automatisierte Python-Tests (`tools/tests/`) für alle 11 Detektoren und den Console-Check — alle grün.

### Live-Verifikation
- Auf Purpur 26.2 (Build 2618) deployt und gestartet: Plugin lädt und aktiviert sich
  fehlerfrei in 152 ms (vorher 181 ms), alle 7 Events und 7 Equipment-Sets geladen,
  Web-Server auf Port 8085 aktiv, Multiverse-Backend MV5 erkannt.
- Keine `NoClassDefFoundError`, `NoSuchMethodError` oder Stacktraces.
- Einzige Plugin-Warnung (`World 'PvPArena' not found`) ist identisch zur 1.0.9-Baseline.
- **Offen:** Die In-Game-Checkliste (Klick-Buttons, Titel, Equipment-Verzauberungen,
  Web-UI-TPS, kompletter Match-Durchlauf) erfordert einen Spieler und steht noch aus.
  Details in `MIGRATION_NOTES.md`.

