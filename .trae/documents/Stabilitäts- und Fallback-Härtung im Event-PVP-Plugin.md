## Ziel
Verbessere Stabilität und Fallbacks systemweit: Teleports, Match-Lifecycle, Welt-Management, Equipment-Verteilung, Command-Restriktionen, Storage/Async I/O und Konfigurations-Nullsicherheit.

## Teleport & Weltzugriff
- Teleport-Schleifen vermeiden:
  - Reentrancy-Guard für commandbasierte Teleports (`SpawnManager.teleportViaCommand`), max. Versuche mit klarem Log.
  - Notfall-Re-Teleport (`MatchManager.attemptEmergencyTeleport`) mit harter Abbruchbedingung nach zweitem Versuch und Ursachen-Logging.
- Nullsichere Ziele:
  - Welt-Nullchecks in allen `PlayerTeleportEvent`-Pfaden (bereits teilweise umgesetzt), konsequent in allen Teleporthilfen.
  - Einheitlicher Fallback-Spawn: `main-world` oder erste Welt mit klarer Meldung.
- Arena-Welt laden:
  - Vor Teleport immer prüfen, ob Welt tatsächlich geladen ist (bereits vorhanden), bei Fehlschlag: Abbruch und Spieler informieren.
  - Lock-Granularität in `WorldStateManager.ensureEventWorldReady`: lange Operationen (Backup, Regeneration, Commands) außerhalb des Mutex.

## Match-Lifecycle & Ausrüstung
- Start-Härtung:
  - Vor Equip: Validierung, dass beide Spieler in der Zielwelt sind; Equip-Verteilung nur danach.
  - Einheitliche Nutzung von `safeTeleport(...)`; direkte Teleports (`teleportRandomCube`) auf safe-Variante umbauen.
- Verify-Timer:
  - `applyEquipmentWithVerify(...)` Timer optional zentral registrieren und bei Logout/Match-Ende abbrechen.
- End-Härtung:
  - Rück-Teleport robust: Fallback auf Hauptspawn bei fehlender `originalLocation` plus Logging.

## Command-Restriktionen
- Whitelist konfigurierbar machen (statt harter Strings) und in Config dokumentieren.
- Einheitliche Bypass-Permission (`eventpvp.opbypass`) überall respektieren.
- Arena-Grenzenprüfung: Robustheit beibehalten (throttling), Grenzwerte konfigurierbar machen.

## Storage & Async I/O
- Datei-Schreibkoordination:
  - Pro Datei einfacher Mutex oder Single-Thread-Executor für `EventStatsStorage`, `PvpStatsStorage`, `InventorySnapshotStorage`.
  - Atomisches Schreiben: Temp-Datei und `Files.move(..., ATOMIC_MOVE)`.
- Hauptthread-Sicherheit beim Restore:
  - `InventorySnapshotStorage.restoreByInventoryId` sicher im Main-Thread ausführen.
- Vor-Serialisierung:
  - `ItemStack`-Listen vor Async in Maps serialisieren; Async-Block arbeitet nur mit POJOs.

## Konfigurations-Nullsicherheit
- EventConfig:
  - Nullchecks für `worlds`, `spawn-settings`, `mechanics`, `rewards`.
  - Tolerante Konstruktoren (`WinCondition`, `DeathHandling`, `RewardConfig`) bei `section == null` mit Defaults statt NPE.
  - Sicherer Enum-Parser für `spawn-type` mit Default und Logging.
  - `parseLocation(...)` null-tolerant; vermeiden, `Location` mit `world:null` zu verwenden.
- Event-ConfigManager:
  - `validateEnum` für `world-loading` an tatsächliche YAML-Optionen anpassen.
  - Nach `parseEvents()` Pflichtsektionen validieren; bei Fehlen Event deaktivieren und loggen.
- Konsistenz für Messages-Pfade in PvP-Config optional angleichen.

## Crash-Prävention (konkret)
- `LocationUtil.serializeLocation(...)` mit `location.getWorld() == null`-Guard.
- Reentrancy-Guard und max. Versuche für commandbasierte Teleports.
- Harte Abbruchbedingung und Logging bei wiederholtem Emergency-Teleport.

## Tests & Verifikation
- Unit-Tests:
  - Null-Config-Fälle für `EventConfig`-Parser und Safe-Enum-Parser.
  - `LocationUtil`-Serialisierung ohne Welt.
  - Mutex-Koordination: gleichzeitige Async-Saves auf dieselbe Datei ohne Datenverlust.
- Manuelle Szenarien:
  - Spieler ohne Rechte betritt Event-/Lobby-Welt bei inaktivem Event → Spawn-Fallback und Nachricht.
  - Aktives Match: Teleport/Equip-Verteilung robust auch bei Verzögerungen durch Weltladen.
  - Parallele Stats-Saves → keine Datenverluste.

Wenn du bestätigst, setze ich die Härtungen und Fallbacks im Code um und führe Build/Tests aus.