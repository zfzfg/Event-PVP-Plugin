# Konfigurations-Beispiele (YAML)

Diese Datei zeigt vollständige, kommentierte Beispiele für alle zentralen YAMLs:
- `config.yml` – globale Einstellungen und Event-Definitionen
- `worlds.yml` – Welten-/Arena-Definitionen (für Events & PvP)
- `equipment.yml` – Ausrüstungssets (für Events & PvP)
- `messages_<lang>.yml` – Nachrichten, Platzhalter und Kategorien (eine Datei je Sprache)

Hinweis: Nach Änderungen bitte `/eventpvp reload` ausführen.

## config.yml – Globale Einstellungen & Events

```yml
settings:
  # Sprache: "en" (Englisch), "de" (Deutsch), "fr" (Französisch), "es" (Spanisch), "ru" (Russisch), "pl" (Polnisch), "ja" (Japanisch)
  language: "de"

  # Präfix für Nachrichten
  prefix: "&6[Event-PvP]&r"

  # Name der Hauptwelt (Teleport-Ziel bei Weltoperationen)
  main-world: "world"

  # Debug-Modus: "off" (aus), "on" (normal), "full" (ausführlich)
  debug: "off"

  # Speichert ursprüngliche Spielerposition vor Events/Matches
  save-player-location: true

  # Dauer (Sekunden) der Event-Beitrittsphase (JOIN_PHASE)
  join-phase-duration: 30

  # Countdown (Sekunden) in der Lobby vor dem Start
  lobby-countdown: 30

  # Inventar-Verwaltung (über InventoryBackup-Backend)
  inventory-management:
    # auto             = Automatische Sicherung & Wiederherstellung (empfohlen)
    # inventoryrestore = Expliziter Modusbezeichner (identisch mit auto)
    # none             = LEGACY: Multiverse-Inventories verwaltet Inventarwechsel
    provider: "auto"
    legacy-safety-backups: true
    auto-restore-on-match-end: true
    auto-restore-on-event-end: true
    auto-restore-on-respawn: true
    auto-restore-on-rejoin: true
    on-backup-failure: "abort"
    cleanup-backups-after-match: false
    guard:
      enabled: true
      restore-orphans-on-start: true
    warn-on-multiverse-inventories: true

  # Multiverse-Weltverwaltung
  world-management:
    events: true    # Lobby-/Eventwelten bei Bedarf laden und danach entladen
    arenas: true    # Arenawelten nach Match-Ende entladen

  # Befehlssperre bei Events: both | event | lobby | none
  # (PvP-Matches sperren Befehle unabhängig davon immer)
  command-restriction: "both"

  # Regenerations-/Backup-Optionen (Events & Arenen)
  arena-regeneration:
    backups: true        # Zip-Backup vor Regeneration
    backup-async: true   # Backup asynchron (empfohlen bei großen Welten)

  # Zuschauersystem & Match-Einstellungen (PvP)
  spectators:
    enabled: true
    max-spectators: 10
    announce-join: true
    announce-leave: true

  match:
    countdown-time: 10        # Sekunden vor Kampfbeginn
    max-duration: 600         # Max. Matchdauer in Sekunden
    draw-vote-time: 30        # Zeit für Unentschieden-Abstimmung
    allow-no-wager: true      # Matches ohne Wetteinsatz erlauben

  # Sicherheitsprüfungen (PvP)
  checks:
    inventory-space: true     # Prüft freien Platz für Wette-Items
    minimum-bet-items: 1      # Minimale Item-Anzahl
    minimum-bet-money: 10     # Minimale Geldwette
    max-bet-money: 100000     # Maximale Geldwette (0 = unbegrenzt)

  # Auto-Event-System (Events)
  auto-events:
    enabled: false
    interval-min: 1800        # 30 Minuten
    interval-max: 3600        # 60 Minuten
    random-selection: true
    check-online-players: true
    selected-events:
      # - "pvparena"
      # - "ctf"
      # - "ffa"

  # Update-Prüfung (Modrinth API)
  update-check:
    enabled: true
    check-on-startup: true
    notify-admins-on-join: true
    modrinth-project-id: "pqJQdZ6R"
    startup-delay-ticks: 20
    stable-only: true
    contact: "https://modrinth.com/plugin/pqJQdZ6R"

  # Externe Integrationen
  integrations:
    ajleaderboards:
      enabled: false
    decentholograms:
      enabled: false
    pvpmanager:
      enabled: true
    refresh-interval-ticks: 20

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
      # Spawn-Varianten: SINGLE_POINT | RANDOM_RADIUS | RANDOM_AREA | RANDOM_CUBE | MULTIPLE_SPAWNS | TEAM_SPAWNS | COMMAND
      spawn-type: SINGLE_POINT
      single-spawn: { x: 0, y: 100, z: 0, yaw: 0, pitch: 0 }
    equipment-group: default
    give-equipment-in-lobby: false
    lobby-team-colored-armor: false
    messages:
      start: "&e&lParkour startet!"
      winner: "&6&l{player} hat das Parkour gewonnen!"
      eliminated: "&7{player} ist gescheitert!"
      objective: "&7Ziel: Erreiche das Ende!"
    mechanics:
      game-mode: SOLO         # SOLO | TEAM_2 | TEAM_3
      pvp-enabled: true
      hunger-enabled: true
      friendly-fire: false
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

  teamfight:
    enabled: true
    command: teamfight
    display-name: "&bTeam Fight"
    description: "2 Teams kämpfen in der Arena."
    min-players: 4
    max-players: 20
    countdown-time: 30
    worlds:
      lobby-world: "TeamLobby"
      event-world: "TeamArena"
      build-allowed: false
      regenerate-event-world: false
    spawn-settings:
      spawn-type: TEAM_SPAWNS
      team-spawns:
        team1:
          a: { x: 10, y: 64, z: 0, yaw: 90, pitch: 0 }
          b: { x: 12, y: 64, z: 2, yaw: 90, pitch: 0 }
        team2:
          a: { x: -10, y: 64, z: 0, yaw: -90, pitch: 0 }
          b: { x: -12, y: 64, z: -2, yaw: -90, pitch: 0 }
    equipment-group: diamond
    give-equipment-in-lobby: true
    lobby-team-colored-armor: true
    messages:
      start: "&b&lTeam Fight beginnt!"
      winner: "&6&lTeam {player} gewinnt!"
      eliminated: "&7{player} wurde eliminiert!"
      objective: "&7Ziel: Besiege das gegnerische Team!"
    mechanics:
      game-mode: TEAM_2
      pvp-enabled: true
      hunger-enabled: false
      friendly-fire: false
    rewards:
      winner:
        items:
          enabled: false
          items: []
        commands:
          enabled: true
          commands:
            - "say Team {player} hat gewonnen!"
      participation:
        items:
          enabled: false
          items: []
        commands:
          enabled: false
          commands: []
```

## worlds.yml – Welten & PvP-Arenen

```yml
worlds:
  desert_arena:
    display-name: "&eDesert Arena"
    pvpwager-world-enable: true         # Welt für PvP verfügbar?
    build-allowed: false                # Bauen erlauben?
    regenerate-world: false             # vor Nutzung regenerieren
    clone-source-world: "desert_template" # optionaler Klon-Quellweltname

    # PvP-Spawn-Konfiguration (für Arenen)
    pvpwager-spawn:
      # Spawn-Typen:
      # - FIXED_SPAWNS: feste Positionen je Rolle
      # - RANDOM_RADIUS: zufällig innerhalb Radius um Mittelpunkt
      # - RANDOM_AREA: zufällig innerhalb Rechteck (min/max)
      # - RANDOM_CUBE: zufällig innerhalb 3D-Box
      # - SINGLE_POINT: beide Spieler am selben Punkt (nicht empfohlen für PvP)
      spawn-type: FIXED_SPAWNS
      spawns:
        spectator: { x: 0, y: 80, z: 0, yaw: 0, pitch: 0 }
        player1:   { x: 15, y: 64, z: 0, yaw: 90, pitch: 0 }
        player2:   { x: -15, y: 64, z: 0, yaw: -90, pitch: 0 }

      # Alternativ: RANDOM_RADIUS
      # center: { x: 0, y: 64, z: 0 }
      # radius: 10

      # Alternativ: RANDOM_AREA
      # min: { x: -20, y: 64, z: -20 }
      # max: { x: 20, y: 64, z: 20 }

      # Alternativ: RANDOM_CUBE
      # min: { x: -20, y: 64, z: -20 }
      # max: { x: 20, y: 70, z: 20 }

  EventWorld:
    display-name: "&aEvent-Welt"
    pvpwager-world-enable: false
    build-allowed: false
    regenerate-world: true
    clone-source-world: "EventWorldTemplate"
```

## equipment.yml – Ausrüstungssets

```yml
equipment-sets:
  pvp_starter:
    enabled: true
    allowed-pvpwager-worlds: PvPArena
    display-name: "&aStarter PvP"
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

  diamond:
    enabled: true
    allowed-pvpwager-worlds: none
    display-name: "&bDiamant"
    armor:
      helmet: DIAMOND_HELMET
      chestplate: DIAMOND_CHESTPLATE
      leggings: DIAMOND_LEGGINGS
      boots: DIAMOND_BOOTS
    inventory:
      - slot: 0
        item: DIAMOND_SWORD
        amount: 1
        enchantments:
          - "SHARPNESS:5"
          - "UNBREAKING:3"
      - slot: 1
        item: ENDER_PEARL
        amount: 2
```

### allowed-pvpwager-worlds
- Steuert, in welchen PvP-Welten ein Equipment genutzt und vorgeschlagen wird.
- Werte:
  - `all` – in allen PvP-Welten erlaubt
  - `none` – in keiner PvP-Welt erlaubt (nur für Events)
  - Liste oder Komma-getrennt – nur in explizit genannten Welten
    - Beispiele: `allowed-pvpwager-worlds: [PvPArena, DesertArena]` oder `allowed-pvpwager-worlds: "PvPArena, DesertArena"`

Tab-Completion in PvP-Kommandos filtert automatisch anhand der zuvor angegebenen Arena, sodass nur erlaubte Sets angeboten werden.

## messages_&lt;lang&gt;.yml – Nachrichten & Platzhalter

> Bearbeite immer die Sprachdatei, die zu `settings.language` passt, also z. B.
> `messages_de.yml`. Die mitgelieferte `messages.yml` wird von keinem Loader
> gelesen — Änderungen darin bleiben wirkungslos.

```yml
general:
  prefix: "&6[Event-PvP]&r"
  player-only: "&cNur Spieler können diesen Befehl nutzen!"
  no-permission: "&cKeine Berechtigung."
  unknown-command: "&cUnbekannter Befehl."
  event-not-found: "&cEvent &e{event}&c nicht gefunden."
  arena-not-found: "&cArena &e{arena}&c nicht gefunden."

join:
  already-joined: "&cDu bist bereits in einem Event."
  success: "&aDu bist beigetreten!"
  event-full: "&cEvent ist voll."
  event-running: "&cEvent läuft bereits."
  countdown-active: "&eCountdown läuft – kein Beitritt möglich."

leave:
  not-in-event: "&cDu bist in keinem Event."
  success: "&aDu hast das Event verlassen."

countdown:
  preparing: "&7Vorbereiten..."
  starting: "&eStartet..."
  go: "&aLos!"

start:
  preparing-worlds: "&7Welten werden vorbereitet..."
  force: "&cAdmin-Start erzwungen!"

pvp:
  request-sent: "&aAnfrage an &e{target}&a gesendet."
  request-received: "&e{sender}&7 hat dich herausgefordert!"
  request-expired-sender: "&cDeine Anfrage an &e{target}&c ist abgelaufen."
  request-expired-target: "&cAnfrage von &e{sender}&c ist abgelaufen."
  wager-invalid: "&cUngültige Wette: {reason}"
  money-insufficient: "&cNicht genug Geld. Kontostand: &6${balance}"
  items-insufficient: "&cDu hast nicht genug: &e{item}"
  match-starting: "&7Match startet in &e{seconds}s"
  match-begun: "&aKampf!"
  match-ended: "&7Match beendet. Sieger: &e{winner}"

worlds:
  loading: "&7Welt wird geladen... &e⏳"
  loaded: "&aWelt geladen! ✓"
  load-failed: "&cWelt konnte nicht geladen werden! ✗"
  regenerating: "&7Welt wird regeneriert..."
  backup-start: "&7Backup wird erstellt..."
  backup-done: "&aBackup abgeschlossen."

## Spawn-Typ `COMMAND` (Events)
```yml
events:
  commandspawn:
    enabled: true
    command: commandspawn
    display-name: "&eCommand Spawn"
    min-players: 2
    max-players: 20
    countdown-time: 30
    worlds:
      lobby-world: "EventLobby"
      event-world: "CmdArena"
    spawn-settings:
      spawn-type: COMMAND
      spawn-command: "tp {player} 0 64 0"
    equipment-group: default
    mechanics:
      game-mode: SOLO
      pvp-enabled: true
      hunger-enabled: true
    rewards:
      participation:
        items:
          enabled: false
          items: []
        commands:
          enabled: false
          commands: []
```
```

### Platzhalter
- `{event}`: Event-ID
- `{arena}`: Arena-ID (Weltenname)
- `{target}` / `{sender}`: Spielernamen
- `{seconds}`: verbleibende Sekunden
- `{winner}`: Spielername
- `{balance}`: Geldbetrag
- `{item}`: Itemname
- Farbcodes nutzen `&` (Bukkit/Spigot-Konvention)

---

## Weltenverwaltung & Befehlssperre

```yaml
settings:
  world-management:
    events: true    # Lobby-/Eventwelten laden und nach dem Event entladen
    arenas: true    # Arenawelten nach dem Match entladen
  command-restriction: "both"   # both | event | lobby | none
```

**`world-management.events`** entscheidet nur, *ob* das Plugin Welten anfassen darf.
*Welche* Welt ein Event braucht, steht im Event selbst: `use-lobby: false` bedeutet, dass
weder eine Lobbywelt geladen noch eine entladen wird. Auf `false` setzen, wenn deine Welten
dauerhaft geladen sind oder extern verwaltet werden. Die Hauptwelt (`settings.main-world`)
wird nie entladen, auch wenn sie als Lobby eingetragen ist.

**`world-management.arenas`** steuert nur das *Entladen* nach dem Match. Geladen werden
Arenawelten immer bei Bedarf — sonst könnte kein Match starten. Auf `false` setzen, um
Arenen dauerhaft im Speicher zu halten (schnellere Match-Starts).

**`command-restriction`** sperrt Befehle für Event-Teilnehmer (außer `/event leave`).
OPs und Träger von `eventpvp.opbypass` sind ausgenommen. PvP-Matches sperren Befehle
unabhängig von dieser Einstellung immer.

| Wert | Gesperrt wird in |
|---|---|
| `both` | Eventwelt und Lobbywelt |
| `event` | nur der Eventwelt |
| `lobby` | nur der Lobbywelt |
| `none` | nirgends |

> Der frühere Schlüssel `settings.world-loading` wird beim ersten Start automatisch auf
> `world-management` umgeschrieben.

---

## Sicherheitsnetz: hängende Spieler und Inventare

Zwei Dateien im Plugin-Ordner halten fest, was gerade offen ist. Im Normalbetrieb sind beide
leer; bleibt etwas darin stehen, ist ein Wiederherstellungspfad gescheitert.

| Datei | Inhalt |
|---|---|
| `inventory-guard.yml` | Offene Inventar-Sitzungen (wem gehört welches Backup) |
| `player-return-locations.yml` | Wohin ein Spieler nach Event oder Match zurückgehört |

Beide werden **synchron** geschrieben und überleben einen Serverabsturz. Beim nächsten Start
bekommt der Spieler sein Inventar zurück; steht er in einer Event- oder Arenawelt, ohne dass
dort noch etwas läuft, wird er beim Einloggen automatisch an seine Ursprungsposition
zurückgeholt. Die Hauptwelt (`settings.main-world`) wird dabei nie entladen oder angetastet.

Bleibt trotzdem etwas hängen, meldet die Konsole das nach 24 Stunden. Nachsehen und
eingreifen (Permission `eventpvp.admin`):

| Befehl | Wirkung |
|---|---|
| `/eventpvp rescue list` | Offene Sitzungen und hinterlegte Positionen mit Alter |
| `/eventpvp rescue <spieler>` | Inventar wiederherstellen und zurückholen |
| `/eventpvp rescue clean` | Verwaiste Einträge ohne Backup verwerfen |

`clean` entfernt bewusst nur Einträge **ohne** Backup — bei denen gibt es nichts mehr zu
retten. Einträge mit Backup bleiben stehen, auch alte; dort wäre das Verwerfen der
eigentliche Datenverlust.

---

## Debug-Modus

Der Debug-Modus kennt genau drei Zustände. Ausgabe geht immer in die Server-Konsole;
OPs und Spieler mit `eventpvp.debug.receive` sehen sie zusätzlich im Chat.

```yaml
settings:
  # "off"  = aus (Standard)
  # "on"   = normal       - Match-Ablauf, Equipment, Config-Laden
  # "full" = ausführlich  - zusätzlich Teleports, Spawn-Handling, Stack-Traces
  debug: "off"
```

Umschalten im Spiel (Permission `eventpvp.debug`):

| Befehl | Wirkung |
|---|---|
| `/eventpvp debug` | Status anzeigen |
| `/eventpvp debug on` | Debug an (normal) |
| `/eventpvp debug on full` | Debug an (ausführlich) |
| `/eventpvp debug off` | Debug aus |

Der Command schreibt `settings.debug` in die `config.yml` zurück — die Einstellung
überlebt also einen Serverneustart.

---

## Hinweise & Best Practices
- **Multiverse-Core** ist zwingend erforderlich für Weltenverwaltung, Klonen und Regeneration. Ohne Multiverse-Core startet das Plugin nicht.
- Welt-Ladung erfolgt nicht-blockierend (asynchron). Spieler sehen Statusmeldungen.
- Backups können asynchron ausgeführt werden, um Server-Ticks zu schonen (`settings.arena-regeneration.backup-async: true`).
- Für große Welten sind `clone-source-world` & Regeneration sinnvoll, um konsistente Zustände zu gewährleisten.
- Nach YAML-Änderungen: `/eventpvp reload` durchführen.