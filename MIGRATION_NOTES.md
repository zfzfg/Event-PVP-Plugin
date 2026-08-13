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
- [x] **Phase 5 (Umfangreiche Test-Suite):** ABGESCHLOSSEN (176 Tests)
- [~] **Phase 6 (Verifikation auf dem echten Server):** TEILWEISE - siehe unten
- [x] **Phase 7 (Abschluss & Versionierung):** ABGESCHLOSSEN

> Korrektur: Phase 6 war im Plan die Live-Verifikation auf dem Server, nicht
> "Packaging & Artefakt-Validierung". Die urspruengliche Eintragung hat eine andere,
> leichtere Phase unter derselben Nummer abgehakt. Tatsaechlicher Stand siehe P6-Abschnitt.

## Phase 6 - Live-Verifikation (Stand 2026-08-13, 11:44)

### Durchgefuehrt
- **P6.1 Deployment:** Altes `event-pvp-plugin-1.0.9.jar` und der Datenordner nach
  `*.bak-20260813-1143` gesichert, altes JAR entfernt, `event-pvp-plugin-1.1.0.jar` deployt.
- **P6.1 Shade-Pruefung:** JAR enthaelt 0 Kyori-, 0 Bukkit-, 0 md_5-Klassen.
- **P6.2 Serverstart:** Purpur 26.2 gestartet, `Done (8.926s)`.
  - `Loading/Enabling Event-PVP-Plugin v1.1.0` - fehlerfrei
  - Alle 7 Events und alle 7 Equipment-Sets geladen
  - `Inventory provider: InventoryBackup (API v1)` erkannt
  - Multiverse-Backend MV5 aktiv
  - Web-Server auf Port 8085 gestartet
  - **0** `NoClassDefFoundError`, **0** `NoSuchMethodError`, **0** `Unsupported api-version`
  - **0** Stacktraces mit `de.zfzfg`
  - Einzige Plugin-Warnung: `World 'PvPArena' not found for arena spawn!` (3x) -
    **identisch zur Baseline vom 12.08. unter 1.0.9**, also vorbestehend und keine Regression.
- **P6.2 Web-Server:** Port 8085 antwortet mit HTTP 200; `/api/status` ohne Token
  korrekt mit HTTP 401 `{"success":false,"error":"Nicht authentifiziert"}`.
- **P4.7 Startzeit:** 1.0.9 = 181 ms, 1.1.0 = 152 ms (Einzelmessung, nicht belastbar).

### NICHT durchgefuehrt - braucht einen Spieler im Spiel
Der Serverstart beweist, dass das Plugin laedt. Er beweist **nicht**, dass die in Phase 2
umgebauten Chat-Pfade funktionieren - die werden erst bei Spieleraktionen ausgefuehrt.
Offen bleibt die 15-Punkte-Checkliste P6.3, insbesondere:

| # | Punkt | Warum kritisch |
|---|---|---|
| 1-6 | Klick- und Hover-Buttons (`/pvpask`, `/pvpa`, Event-Join) | Adventure-Immutability: fehlende Zuweisung kompiliert, sieht korrekt aus, tut beim Klick nichts |
| 7-8 | Countdown- und Start-Titel | P3.2, Ticks-zu-Duration-Umrechnung |
| 9 | Web-Token-Nachricht (Copy-to-Clipboard + URL-Link) | P2.8c |
| 10-11 | Equipment: Verzauberungen vorhanden, Item-Namen nicht kursiv | P3.3 Registry-Aufloesung, P3.1 Kursiv-Default |
| 14 | Web-UI TPS-Anzeige | P3.6, `Bukkit.getServer().getTPS()` - Endpunkt braucht Token, ohne Spieler nicht erreichbar |
| 15 | Match komplett durchspielen | Payout + Inventar-Wiederherstellung |

Ebenfalls offen: **P6.4** (spark-Profiling) und **P6.5** (Void-Schutz und Arena-Grenzen
nach der `hasChangedBlock()`-Optimierung aus P4.2 - dort war bereits ein Nachfixen
noetig, Commit `3a3e2b5`).

### Rueckfallebene
`plugins/event-pvp-plugin-1.0.9.jar.bak-20260813-1143` und
`plugins/Event-PVP-Plugin.bak-20260813-1143`. Zum Zurueckrollen: Server stoppen,
1.1.0-JAR entfernen, Backup-JAR auf `.jar` zurueckbenennen.

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

> Korrektur der frueheren Eintragung "Keine": alle *automatisierten* Pruefungen sind gruen,
> das ist nicht dasselbe wie "nichts offen".

1. **15-Punkte-Checkliste P6.3 im Spiel** - der Serverstart deckt sie nicht ab, siehe
   Phase-6-Abschnitt. Der Server laeuft aktuell mit 1.1.0 und ist bereit dafuer.
2. **P6.4 spark-Profiling** und **P6.5 Void-/Arena-Grenzen** stehen aus.
3. **Section-Codes in den Sprachdateien:** Die Chat-Button-Texte
   (`accept-button`, `deny-button`, `spectate-button`, zugehoerige Hover- und
   Header-Schluessel) sind in allen sieben Sprachdateien mit `§` statt `&` geschrieben.
   Funktional unkritisch - Adventure parst beide Zeichen, verifiziert mit einer
   Probe (`§a§l[ANNEHMEN]` -> Farbe gruen, Codes sauber entfernt). Rein redaktionell
   waere eine Vereinheitlichung auf `&` sauberer.
4. **Multiverse-Inventories** ist auf dem Server installiert, steht aber weder in
   `depend` noch in `softdepend` der plugin.yml. Nicht Teil dieser Migration, nur als
   Beobachtung notiert.
