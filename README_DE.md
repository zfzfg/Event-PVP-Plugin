# Event-PVP-Plugin – Kurzanleitung

Diese Anleitung erklärt die Installation, Konfiguration und Nutzung des kombinierten Event- und PvP-Wager-Plugins. Ziel ist eine einfache, konsistente Struktur mit wenigen, klaren Konfigurationsdateien.

## Voraussetzungen
- Server: Paper/Spigot (API 1.19 kompatibel)
- Optional: `Vault` für Geld-Wetten (Economy)
- Optional: `Multiverse-Core` für Welt-Verwaltung (Laden/Entladen/Klonen/Regenerieren)

## Installation
- Lege das Plugin-JAR in den `plugins/` Ordner deines Servers.
- Starte den Server einmal, damit die Standard-Konfigurationsdateien erstellt werden.
- Passe die Konfigurationen an (siehe unten) und führe `/eventpvp reload` aus.

## Konfigurationsdateien
Das Plugin nutzt zentrale Dateien im Plugin-Ordner:
- `config.yml` – Allgemeine Einstellungen und Event-Definitionen
- `messages.yml` – Nachrichten (mehrsprachige Texte)
 - `worlds.yml` – Welten-Definitionen (ein Name für Events & PvP) mit Flags und PvP-Spawn
 - `equipment.yml` – Gemeinsame Ausrüstungen mit Flags für Events & PvP
- `plugin.yml` – Befehle und Berechtigungen (nur im JAR)

### worlds.yml (Beispiel)
```yml
worlds:
  arena_world:
    display-name: "&bBeispiel-Arena"
    pvpwager-world-enable: true
    build-allowed: false
    regenerate-world: false
    clone-source-world: "arena_template"
    pvpwager-spawn:
      spawn-type: FIXED_SPAWNS
      spawns:
        spectator: { x: 0, y: 80, z: 0, yaw: 0, pitch: 0 }
        player1:   { x: 10, y: 64, z: 0, yaw: 90, pitch: 0 }
        player2:   { x: -10, y: 64, z: 0, yaw: -90, pitch: 0 }

  EventWorld:
    display-name: "&aEvent-Welt"
    pvpwager-world-enable: false
    build-allowed: false
    # Optional pvpwager-spawn, falls die Event-Welt auch für PvP genutzt werden soll
```
- Welten haben einen Namen, der in beiden Modulen identisch verwendet wird.
- PvP-Schalter: `pvpwager-world-enable: true/false`.
- Bauen erlauben: `build-allowed: true/false`.
- PvP-Spawndefinition: unter `pvpwager-spawn` (gleiches Format wie bisherige `spawn-settings`).

### equipment.yml (Beispiel – zentral, aktuelles Format)
```yml
equipment-sets:
  pvp_starter:
    enabled: true
    allowed-pvpwager-worlds: PvPArena
    display-name: "&aStarter"
    armor:
      helmet: LEATHER_HELMET
      chestplate: LEATHER_CHESTPLATE
      leggings: LEATHER_LEGGINGS
      boots: LEATHER_BOOTS
    inventory:
      - slot: 0
        item: STONE_SWORD
        amount: 1
      - slot: 7
        item: GOLDEN_APPLE
        amount: 2
```
- Primärstruktur `equipment-sets` mit `enabled`-Flag; wird von Events und PvP gemeinsam genutzt.
 - Einschränkung nach Welt: `allowed-pvpwager-worlds`
   - `all` – überall in PvP
   - `none` – nur für Events (nicht in PvP)
   - Liste oder Komma-getrennt – nur in angegebenen PvP-Welten
   - Tab-Completion in PvP-Befehlen zeigt automatisch nur erlaubte Sets für die zuvor gewählte Arena.

### config.yml (Auszug)
```yml
settings:
  prefix: "&6[Event]&r"
  main-world: "world"
  save-player-location: true
  join-phase-duration: 30
  lobby-countdown: 30
  command-restriction: both
  world-loading: both  # none|arena|both (steuert die automatische Welt-Ladung für Events & PvP)
  arena-regeneration:
    backups: true       # Welt-Backup vor Regeneration
    backup-async: false # Backup asynchron (empfohlen bei großen Welten)

events:
  parkour:
    enabled: true
    command: parkour
    display-name: "&aParkour"
    description: "Springe und gewinne!"
    min-players: 2
    max-players: 20
    countdown-time: 60
    worlds:
      lobby-world: "EventLobby"
      event-world: "EventWorld"
      build-allowed: false
      regenerate-event-world: true
      clone-source-event-world: "EventWorldTemplate"
    spawn-settings:
      spawn-type: SINGLE_POINT
      single-spawn: { x: 0, y: 100, z: 0, yaw: 0, pitch: 0 }
    equipment-group: default
    give-equipment-in-lobby: false
    messages:
      start: "&e&lParkour startet!"
      winner: "&6&l{player} gewinnt!"
      eliminated: "&7{player} wurde eliminiert!"
      objective: "&7Ziel: Erreiche das Ende!"
    mechanics:
      game-mode: SOLO
      pvp-enabled: true
      hunger-enabled: true
    rewards:
      winner:
        items:
          enabled: false
          items: []
        commands:
          enabled: true
          commands:
            - "say Glückwunsch {player}!"
      team-winner:
        items:
          enabled: false
          items: []
        commands:
          enabled: false
          commands: []
      participation:
        items:
          enabled: false
          items: []
        commands:
          enabled: false
          commands: []
```

### Spawn-Methoden
- Events (in `events.<id>.spawn-settings`):
  - `SINGLE_POINT` mit `single-spawn`
  - `RANDOM_RADIUS` mit `random-radius` (center-x/center-z/radius/min-distance)
  - `RANDOM_AREA` mit `random-area` (point1/point2/min-distance)
  - `RANDOM_CUBE` mit `random-cube` (point1/point2/min-distance)
  - `MULTIPLE_SPAWNS` mit `multiple-spawns.spawns.<id>`
  - `TEAM_SPAWNS` mit `team-spawns.<team>.<id>`
  - `COMMAND` mit `spawn-command`
- PvP-Arenen (in `worlds.<welt>.pvpwager-spawn`):
  - `FIXED_SPAWNS` mit `spawns.player1/player2/spectator`
  - `RANDOM_RADIUS` mit `random-radius`
  - `RANDOM_AREA` mit `random-area`
  - `RANDOM_CUBE` mit `random-cube`
  - `MULTIPLE_SPAWNS` mit `spawns.<id>`
  - `COMMAND` mit `command.command` und optional `command.placeholders.*`

## Befehle
- Events:
  - `/event <eventname> join` – Event beitreten
  - `/event <eventname> start` – Event starten (Admin)
  - `/event <eventname> stop` – Event stoppen (Admin)
  - `/event list` – Verfügbare Events
- PvP Wager:
  - `/pvp [accept|deny|spectate|leave] [player]` – Basisfunktionen
  - `/pvpa <player> <wager> <amount> <arena> <equipment>` – Anfrage mit kompletter Konfiguration
    - `wager`: Material-Name, `MONEY` oder `SKIP` (ohne Einsatz)
    - Equipment-Vorschläge sind gefiltert nach der angegebenen Arena-Welt (`allowed-pvpwager-worlds`).
  - `/pvpanswer <wager> <amount> [arena] [equipment]` – Antwort mit Änderungen
    - Beispiele: `/pvpanswer DIAMOND_SWORD 1`, `/pvpanswer MONEY 50 desert diamond`, `/pvpanswer SKIP`
    - Equipment-Vorschläge berücksichtigen Arena-Override oder ursprüngliche Anfrage-Arena.
  - `/pvpyes` – bestätigen
  - `/pvpno` – ablehnen
  - `/pvpadmin [reload|stopall|info]` – Admin
  - `/surrender`, `/draw`, `/pvpainfo`
- Unified Reload:
  - `/eventpvp reload` – lädt alle Konfigurationen neu (config, messages, worlds, equipment) und aktualisiert Arenen/Equipment/Caches.

## Berechtigungen
Siehe `plugin.yml` (Auszug):
- `eventpvp.admin` – Reload-Befehl
- `eventplugin.admin` – Event-Admin
- `eventplugin.join` – Teilnahme an Events
- `pvpwager.admin` – PvP-Admin
- `pvpwager.use` – PvP nutzen
- `pvpwager.spectate` – Zuschauen
- `pvpwager.command` – Befehlsbasiert anfragen

## Economy (Vault)
- Ist `Vault` installiert, können Geld-Wetten genutzt werden.
- Ohne Vault sind Money-Wetten deaktiviert; Item-Wetten funktionieren weiterhin.

## Typische Workflows
- Events:
  1) Event in `config.yml` unter `events` definieren (Welt, Spawns, Equipment-Gruppe, Mechaniken).
  2) `/eventpvp reload` ausführen.
  3) `/event <eventname> start` und Spieler mit `/event <eventname> join` beitreten lassen.
- PvP Wager:
  1) Welten in `worlds.yml` unter `worlds.<name>` anlegen und `pvpwager-world-enable: true` setzen.
  2) `pvpwager-spawn` in der Welt definieren (Spawn-Mechanismus), falls benötigt.
  3) Ausrüstungen zentral in `equipment.yml` unter `equipment.<name>` mit `pvpwager-equip-enable: true` definieren.
  3) `/pvpa` oder `/pvpanswer` nutzen, um Arena/Equipment im Command festzulegen.

## Tipps & Fehlerbehebung
- Welt-Ladung:
  - `settings.world-loading` in `config.yml`: `none|arena|both` steuert die automatische Welt-Ladung für Events & PvP.
  - Mit Multiverse-Core werden Welten geladen/entladen, geklont, regeneriert. Das Plugin lädt Welten nicht-blockierend und zeigt Statusmeldungen an (z. B. „Welt wird geladen… ⏳“ und „Welt geladen! ✓“).
  - Regeneration-Backups: `settings.arena-regeneration.backups` aktiviert ein Zip-Backup vor der Regeneration. Mit `settings.arena-regeneration.backup-async` kann das Backup asynchron erfolgen (empfohlen für große Welten, reduziert Tick-Blockaden).
- Nach jeder Änderung in YAML-Dateien: `/eventpvp reload` ausführen.
- Wenn eine Welt nicht gefunden wird: Stelle sicher, dass sie existiert oder definiere `clone-source-*` zum Klonen.
- Für Events: `equipment-group` entspricht dem Namen unter `equipment` in `equipment.yml`.
- Für PvP: Equipment-Namen aus `equipment` mit `pvpwager-equip-enable: true` werden angezeigt und nutzbar.

### Event-YAML Schema (Kurzüberblick)
- `events.<id>.worlds`: `lobby-world`, `lobby-spawn` (x/y/z/yaw/pitch), `event-world`, `clone-source-event-world`, `regenerate-event-world`, `regenerate-lobby-world`
- `events.<id>.messages`: `start`, `winner`, `eliminated`, `objective` (mit Farbcodes `&` und Platzhalter `{player}`)
- `events.<id>.spawn-settings`:
  - `spawn-type`: `SINGLE_POINT|RANDOM_RADIUS|RANDOM_AREA|RANDOM_CUBE|MULTIPLE_SPAWNS|TEAM_SPAWNS|COMMAND`
  - Keys je Typ:
    - `single-spawn`: x/y/z/yaw/pitch
    - `random-radius`: `center-x`, `center-z`, `radius`, `min-distance`
    - `random-area`: `point1{x,z}`, `point2{x,z}`, `min-distance`
    - `random-cube`: `point1{x,y,z}`, `point2{x,y,z}`, `min-distance`
    - `multiple-spawns.spawns.<id>`: x/y/z/yaw/pitch
    - `team-spawns.<team>.<id>`: x/y/z/yaw/pitch
    - `spawn-command` (bei `COMMAND`)
- `events.<id>.mechanics`: `game-mode (SOLO|TEAM_2|TEAM_3)`, `pvp-enabled`, `hunger-enabled`, `win-condition`, `death-handling`
- `events.<id>.rewards`: `winner`, optional `team-winner`, und Pflicht `participation` mit jeweils `items.enabled/items[]` und `commands.enabled/commands[]`

## Performance-Logging
- Beim Aktivieren/Deaktivieren des Plugins werden Zeiten in Millisekunden geloggt (z. B. „Event-PVP-Plugin aktiviert in 123 ms“). Das hilft, Start-/Stop-Performance zu beobachten und Änderungen zu bewerten.

## Migration von Legacy-Dateien
- Legacy-Dateien (`events-config.yml`, `events-equipment.yml`, `events-messages.yml`, `arenas.yml`) wurden entfernt.
- Inhalte bitte in die neuen zentralen Dateien (`config.yml`, `messages.yml`, `equipment.yml`, `worlds.yml`) übertragen.

---
Fragen oder spezielle Anforderungen (z. B. pro Arena eine Standard-Ausrüstung setzen)? Sag Bescheid – ich erweitere die Parser gezielt.