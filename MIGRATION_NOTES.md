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
- `button(...)` fügt standardmäßig einen `Component.space()` zwischen mehreren Buttons ein für bessere Optik.
- Kein Folia-Umbau: Purpur 26.2 unterstützt den Bukkit-Scheduler vollständig.
- MockBukkit: Erst prüfen, ob offline/online kompatibel für 26.2 vorhanden; falls nicht, auf Unit-Tests mit isolierter Logik setzen.

## BLOCKIERT
*(Keine aktuellen Blockaden)*

## Offene Punkte für den Menschen
*(Werden während der Migration erfasst)*
