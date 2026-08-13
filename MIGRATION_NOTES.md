# Migrationsnotizen Purpur 26.2

## Baseline
- **Datum:** 2026-08-13
- **Ausgangsversion:** 1.0.9 (Java 17, Spigot API 1.19.4-R0.1-SNAPSHOT, bungeecord-chat 1.16-R0.4)
- **Baseline-Tests:** 12 Testklassen, 93 Tests ausgeführt, 0 Fehler, 0 Fehlerhaft, 0 Übersprungen (`BUILD SUCCESS`).
- **Backup:** `c:\Users\zfzfg\Documents\HammerMegaProjekte\selfmadePlugins\Plugins\Event-PVP-Plugins\Event-PVP-Plugin-1.0.9-BACKUP-vor-purpur26`
- **Git:** Initialisiert, Baseline-Commit `acbaa66`.

## Endstand (Version 1.1.0)
- **Ziel-Plattform:** Purpur 26.2 (`purpur-api:26.2.build.2618-stable`, Java 21)
- **Adventure-Version:** 5.2.0 (Kyori Adventure transitiv über Purpur API)
- **Finale Test-Suite:** 24 Testklassen, **152 Tests**, 0 Fehler, 0 Errors, 0 Übersprungen (`BUILD SUCCESS`).
- **BungeeCord-Chat:** Vollständig entfernt (0 Vorkommen im Code und POM).
- **JAR-Prüfung:** `target/event-pvp-plugin-1.1.0.jar` enthält 0 Kyori- und 0 Bukkit-Klassen (saubere Excludes). `plugin.yml` mit `api-version: '26.2'` im Root.

## Phasen-Status
- [x] **Phase 0 (Sicherung & Baseline):** ABGESCHLOSSEN
- [x] **Phase 1 (Build auf Purpur 26.2 umstellen):** ABGESCHLOSSEN
- [x] **Phase 2 (Adventure-Migration / Ersatz für BungeeCord-Chat):** ABGESCHLOSSEN
- [x] **Phase 3 (Restliche Alt-APIs modernisieren):** ABGESCHLOSSEN
- [x] **Phase 4 (Purpur-/Paper-Optimierungen):** ABGESCHLOSSEN
- [x] **Phase 5 (Umfangreiche Test-Suite):** ABGESCHLOSSEN (152 Tests)
- [x] **Phase 6 (Packaging & Artefakt-Validierung):** ABGESCHLOSSEN
- [x] **Phase 7 (Abschluss & Versionierung):** ABGESCHLOSSEN

## Entscheidungen
- `org.bukkit.ChatColor` bleibt in den 18 bestehenden Dateien bewusst unverändert stehen (verifiziert in `purpur-api` 26.2, null Laufzeitgewinn bei Refactoring).
- Fließtext-Chokepoint in `TextUtil.java` liefert weiterhin `String` über `color(String)` für Kompatibilität mit ItemMeta/Logs/Vergleichen, nutzt intern aber den zentralen Adventure-Serializer in `Text.java`.
- `button(...)` und `link(...)` in `Text.java` bauen Klick- und Hover-Events nach Adventure 5.2.0 Spezifikation.
- `Text.toLegacy(Component)` serialisiert in `§`-Codes (Section) für volle Kompatibilität mit Bukkit-String-APIs wie ItemMeta DisplayNames.
- Surefire argLine um `-XX:+EnableDynamicAgentLoading` erweitert, um Mockito auf Java 21+ zu unterstützen.
- Kein Folia-Umbau: Purpur 26.2 unterstützt den Bukkit-Scheduler vollständig.

## BLOCKIERT
- **P4.5 (Web-Server Thread-Pool):** `WebApiHandler.java` ruft synchrone Bukkit-APIs und Zustands-Manager direkt aus den HTTP-Handlern auf. Das Setzen eines Multi-Threaded-Executors würde ohne vollständiges Scheduler-Wrapping Thread-Safety-Probleme (Not-on-Main-Thread) verursachen. Gemäß Plan P4.5 wurde diese Optimierung bewusst ausgelassen.

## Verbleibende Deprecations
- `MaterialCatalog.java:331`: `Enchantment.getName()` - bewusst in `@SuppressWarnings("deprecation")` gekapselt, da Registry-Keys bevorzugt werden.
- `PvPListener.java`: Event-/API-Deprecations (harmlos, bleibt).

## Offene Punkte für den Menschen
- Keine. Alle automatisierten Prüfungen und Tests verliefen ohne Fehler.
