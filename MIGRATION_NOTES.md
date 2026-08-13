# Migrationsnotizen Purpur 26.2

## Baseline
- **Datum:** 2026-08-13
- **Ausgangsversion:** 1.0.9 (Java 17, Spigot API 1.19.4-R0.1-SNAPSHOT, bungeecord-chat 1.16-R0.4)
- **Baseline-Tests:** 12 Testklassen, 93 Tests ausgeführt, 0 Fehler, 0 Fehlerhaft, 0 Übersprungen (`BUILD SUCCESS`).
- **Backup:** `c:\Users\zfzfg\Documents\HammerMegaProjekte\selfmadePlugins\Plugins\Event-PVP-Plugins\Event-PVP-Plugin-1.0.9-BACKUP-vor-purpur26`
- **Git:** Initialisiert, Baseline-Commit `acbaa66`.

## Entscheidungen
- `org.bukkit.ChatColor` bleibt in den 18 bestehenden Dateien bewusst unverändert stehen (verifiziert in `purpur-api` 26.2, null Laufzeitgewinn bei Refactoring).
- Fließtext-Chokepoint in `TextUtil.java` liefert weiterhin `String` über `color(String)` für Kompatibilität mit ItemMeta/Logs/Vergleichen, nutzt intern aber den zentralen Adventure-Serializer in `Text.java`.
- `button(...)` und `link(...)` in `Text.java` bauen Klick- und Hover-Events nach Adventure 5.2.0 Spezifikation.
- `Text.toLegacy(Component)` serialisiert in `§`-Codes (Section) für volle Kompatibilität mit Bukkit-String-APIs wie ItemMeta DisplayNames.
- Surefire argLine um `-XX:+EnableDynamicAgentLoading` erweitert, um Mockito auf Java 21+ zu unterstützen.
- Kein Folia-Umbau: Purpur 26.2 unterstützt den Bukkit-Scheduler vollständig.
- MockBukkit: Unit-Tests fokussieren auf isolierte Logik ohne Server-Abhängigkeit.

## BLOCKIERT
- **P4.5 (Web-Server Thread-Pool):** `WebApiHandler.java` ruft synchrone Bukkit-APIs und Zustands-Manager direkt aus den HTTP-Handlern auf. Das Setzen eines Multi-Threaded-Executors würde ohne vollständiges Scheduler-Wrapping Thread-Safety-Probleme (Not-on-Main-Thread) verursachen. Gemäß Plan P4.5 wird diese Optimierung bewusst ausgelassen.

## Verbleibende Deprecations
- `MaterialCatalog.java:331`: `Enchantment.getName()` - bewusst in `@SuppressWarnings("deprecation")` gekapselt, da Registry-Keys bevorzugt werden.
- `PvPListener.java`: Event-/API-Deprecations (harmlos, bleibt).

## Offene Punkte für den Menschen
*(Werden in Phase 5 bei den Konfigurationsvalidierungen ergänzt)*
