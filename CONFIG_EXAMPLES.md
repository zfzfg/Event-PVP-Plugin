# Konfigurations-Beispiele (YAML)

Diese Datei zeigt vollständige, kommentierte Beispiele für alle zentralen YAMLs:
- `config.yml` – globale Einstellungen und Event-Definitionen
- `worlds.yml` – Welten-/Arena-Definitionen (für Events & PvP)
- `equipment.yml` – Ausrüstungssets (für Events & PvP)
- `messages.yml` – Nachrichten, Platzhalter und Kategorien

Hinweis: Nach Änderungen bitte `/eventpvp reload` ausführen.

## config.yml – Globale Einstellungen & Events

```yml
settings:
  # Präfix für Nachrichten
  prefix: "&6[Event-PvP]&r"

  # Name der Hauptwelt (Teleport-Ziel bei Weltoperationen)
  main-world: "world"

  # Speichert ursprüngliche Spielerposition vor Events/Matches
  save-player-location: true

  # Dauer (Sekunden) der Event-Beitrittsphase (JOIN_PHASE)
  join-phase-duration: 30

  # Countdown (Sekunden) in der Lobby vor dem Start
  lobby-countdown: 30

  # Beschränkt Befehle im Event/Match: none|event|pvp|both
  command-restriction: both

  # Automatische Welt-Ladung: none|lobby|event|arena|both
  # - Events nutzen meist: none|lobby|event|both
  # - PvP-Arenen nutzen: none|arena|both
  world-loading: both

  # Regenerations-/Backup-Optionen (Events & Arenen)
  arena-regeneration:
    backups: true       # Zip-Backup vor Regeneration
    backup-async: false # Backup asynchron (empfohlen bei großen Welten)

  # Zuschauersystem & Match-Einstellungen (PvP)
  spectator:
    enable: true
    allow-damage: false

  match:
    countdown: 10        # Sekunden vor Kampfbeginn
    duration: 300        # Max. Matchdauer in Sekunden
    draw-vote: true      # Unentschieden-Abstimmung erlauben
    no-wager-mode: false # Wetten vollständig deaktivieren
    inventory-group: default # (optional) PvP-Inventargruppe

  security:
    check-inventory-space: true   # Prüft freien Platz für Wette-Items
    min-bet-money: 0.0            # minimale Geldwette
    max-bet-money: 100000.0       # maximale Geldwette

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

## messages.yml – Nachrichten & Platzhalter

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

## Hinweise & Best Practices
- Welt-Ladung erfolgt nicht-blockierend (asynchron). Spieler sehen Statusmeldungen.
- Backups können asynchron ausgeführt werden, um Server-Ticks zu schonen (`settings.arena-regeneration.backup-async`).
- Für große Welten sind `clone-source-world` & Regeneration sinnvoll, um konsistente Zustände zu gewährleisten.
- Nach YAML-Änderungen: `/eventpvp reload` durchführen.