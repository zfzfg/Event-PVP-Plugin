# Configuration Examples (YAML)

This file shows complete, commented examples for all central YAML files:
- `config.yml` – global settings and event definitions
- `worlds.yml` – world/arena definitions (for Events & PvP)
- `equipment.yml` – equipment sets (for Events & PvP)
- `messages.yml` – messages, placeholders, and categories

Note: After making changes, run `/eventpvp reload` to apply them.

## config.yml – Global Settings & Events

```yml
settings:
  # Language: "en" (English), "de" (German), "fr" (French), "es" (Spanish), "ru" (Russian), "pl" (Polish), "ja" (Japanese), "zh" (Chinese)
  language: "en"

  # Prefix for messages
  prefix: "&6[Event-PvP]&r"

  # Name of the main world (teleport destination for world operations)
  main-world: "world"

  # Saves original player location before events/matches
  save-player-location: true

  # Duration (seconds) of the event join phase (JOIN_PHASE)
  join-phase-duration: 30

  # Countdown (seconds) in the lobby before start
  lobby-countdown: 30

  # Command restriction during events/matches: none|event|pvp|both
  command-restriction: both

  # Automatic world loading: none|lobby|event|arena|both
  # - Events typically use: none|lobby|event|both
  # - PvP arenas use: none|arena|both
  world-loading: both

  # Inventory snapshots (Backup & Restore)
  # WARNING: This system is experimental and may be buggy!
  # Strongly recommended to use Multiverse-Inventories instead
  inventory-snapshots:
    enabled: true
    default-group: "default"
    groups:
      - "default"
    # Note: World assignment is handled by Multiverse-Inventories
    # This plugin only saves by group logic; no per-world mappings needed
    retain-days: 30
    ids:
      inventory-id-digits: 4    # unique inventory ID per world, e.g., 0001
      eventmatch-id-digits: 5   # Event/Match ID for linking before/after

  # Regeneration/Backup options (Events & Arenas)
  arena-regeneration:
    backups: true        # Create ZIP backup before regeneration
    backup-async: true   # Backup asynchronously (recommended for large worlds)

  # Spectator system & match settings (PvP)
  spectators:
    enabled: true
    max-spectators: 10
    announce-join: true
    announce-leave: true

  match:
    countdown-time: 10        # Seconds before combat begins
    max-duration: 600         # Max match duration in seconds
    draw-vote-time: 30        # Time for draw vote
    allow-no-wager: true      # Allow matches without wagers

  # Security checks (PvP)
  checks:
    inventory-space: true          # Check for free space for wager items
    minimum-bet-items: 1           # Minimum number of items
    minimum-bet-money: 10          # Minimum money wager
    max-bet-money: 100000          # Maximum money wager (0 = unlimited)

  # Auto-Event System (Events)
  auto-events:
    enabled: false
    interval-min: 1800         # 30 minutes
    interval-max: 3600         # 60 minutes
    random-selection: true
    check-online-players: true
    # Event selection: List of event IDs to use
    # Empty = use all enabled events
    # When random-selection: true  → random selection from list
    # When random-selection: false → sequential rotation in specified order
    selected-events:
      # - "pvparena"
      # - "ctf"
      # - "ffa"

# Note: Messages are in resources/messages.yml and copied to the plugin directory

# Permissions for special features
permissions:
  spectate-all: "pvpwager.spectate.all"
  bypass-bet-limit: "pvpwager.bypass.betlimit"
  no-wager: "pvpwager.nowager"

# ============================================
# Events – Definitions
# ============================================

events:
  parkour:
    enabled: true
    command: parkour
    display-name: "&aParkour"
    description: "Jump and win!"
    min-players: 2
    max-players: 20
    countdown-time: 60
    worlds:
      lobby-world: "EventLobby"
      lobby-spawn:
        x: 0.5
        y: 65
        z: 0.5
        yaw: 0
        pitch: 0
      event-world: "EventWorld"
      build-allowed: false
      regenerate-event-world: true
      regenerate-lobby-world: false
      clone-source-event-world: "EventWorldTemplate"
    spawn-settings:
      # Spawn types: SINGLE_POINT | RANDOM_RADIUS | RANDOM_AREA | RANDOM_CUBE | MULTIPLE_SPAWNS | TEAM_SPAWNS | COMMAND
      spawn-type: SINGLE_POINT
      single-spawn: { x: 0, y: 100, z: 0, yaw: 0, pitch: 0 }
    equipment-group: default
    give-equipment-in-lobby: false
    lobby-team-colored-armor: false
    messages:
      start: "&e&lParkour starts!"
      winner: "&6&l{player} won the parkour!"
      eliminated: "&7{player} failed!"
      objective: "&7Goal: Reach the end!"
    mechanics:
      game-mode: SOLO         # SOLO | TEAM_2 | TEAM_3
      pvp-enabled: true
      hunger-enabled: true
      friendly-fire: false
      win-condition:
        type: LAST_ALIVE
      death-handling:
        eliminate-on-death: true
        spectator-mode: true
        allow-rejoin: false
    rewards:
      winner:
        items:
          enabled: false
          items: []
        commands:
          enabled: true
          commands:
            - "say Congratulations {player}!"
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
    description: "2 teams fight in the arena."
    min-players: 4
    max-players: 20
    countdown-time: 30
    worlds:
      lobby-world: "TeamLobby"
      lobby-spawn:
        x: 0.5
        y: 65
        z: 0.5
        yaw: 0
        pitch: 0
      event-world: "TeamArena"
      build-allowed: false
      regenerate-event-world: false
      regenerate-lobby-world: false
    spawn-settings:
      spawn-type: TEAM_SPAWNS
      team-spawns:
        RED:
          spawn1: { x: 10, y: 64, z: 0, yaw: 90, pitch: 0 }
          spawn2: { x: 12, y: 64, z: 2, yaw: 90, pitch: 0 }
        BLUE:
          spawn1: { x: -10, y: 64, z: 0, yaw: -90, pitch: 0 }
          spawn2: { x: -12, y: 64, z: -2, yaw: -90, pitch: 0 }
    equipment-group: diamond
    give-equipment-in-lobby: true
    lobby-team-colored-armor: true
    messages:
      start: "&b&lTeam Fight begins!"
      winner: "&6&lTeam {player} wins!"
      eliminated: "&7{player} was eliminated!"
      objective: "&7Goal: Defeat the enemy team!"
    mechanics:
      game-mode: TEAM_2
      pvp-enabled: true
      hunger-enabled: false
      friendly-fire: false
      win-condition:
        type: LAST_TEAM_ALIVE
      death-handling:
        eliminate-on-death: true
        spectator-mode: true
        allow-rejoin: false
    rewards:
      winner:
        items:
          enabled: false
          items: []
        commands:
          enabled: false
          commands: []
      team-winner:
        items:
          enabled: false
          items: []
        commands:
          enabled: true
          commands:
            - "say Team {player} won!"
      participation:
        items:
          enabled: false
          items: []
        commands:
          enabled: false
          commands: []

  pvparena:
    enabled: true
    command: "pvparena"
    display-name: "&c&lPvP Arena"
    description: "&7Fight to the last man!"
    min-players: 2
    max-players: 16
    countdown-time: 45
    worlds:
      lobby-world: "EventLobby"
      lobby-spawn:
        x: 0.5
        y: 65
        z: 0.5
        yaw: 0
        pitch: 0
      event-world: "PvPArena"
      clone-source-event-world: "PvPArena_original"
      build-allowed: false
      regenerate-event-world: true
      regenerate-lobby-world: false
    spawn-settings:
      spawn-type: "SINGLE_POINT"
      single-spawn:
        x: 0.5
        y: 65
        z: 0.5
        yaw: 0
        pitch: 0
    equipment-group: "pvp_starter"
    give-equipment-in-lobby: true
    mechanics:
      game-mode: "SOLO"
      pvp-enabled: true
      hunger-enabled: true
      win-condition:
        type: "LAST_ALIVE"
      death-handling:
        eliminate-on-death: true
        spectator-mode: true
        allow-rejoin: false
    rewards:
      winner:
        items:
          enabled: false
          items: []
        commands:
          enabled: true
          commands:
            - "say Congratulations {player}!"
      participation:
        items:
          enabled: false
        commands:
          enabled: false
    messages:
      start: "&c&lPVP ARENA STARTS!"
      winner: "&6&l{player} IS THE CHAMPION!"
      eliminated: "&7{player} was eliminated!"
      objective: "&7Goal: &cBe the last survivor!"
```

## worlds.yml – Worlds & PvP Arenas

```yml
worlds:
  # Example Arena with PvP enabled
  desert_arena:
    display-name: "&eDesert Arena"
    pvpwager-world-enable: true         # World available for PvP?
    build-allowed: false                # Allow building?
    regenerate-world: false             # Regenerate before use?
    clone-source-world: "desert_template" # Optional clone source world name

    # PvP spawn configuration (for arenas)
    pvpwager-spawn:
      # Spawn types:
      # - FIXED_SPAWNS: fixed positions per role (player1, player2, spectator)
      # - RANDOM_RADIUS: random within radius around center
      # - RANDOM_AREA: random within rectangle (2D X/Z)
      # - RANDOM_CUBE: random within 3D box
      # - MULTIPLE_SPAWNS: random from predefined spawn points
      # - COMMAND: execute command for spawning
      spawn-type: FIXED_SPAWNS
      spawns:
        spectator: { x: 0, y: 80, z: 0, yaw: 0, pitch: 0 }
        player1:   { x: 15, y: 64, z: 0, yaw: 90, pitch: 0 }
        player2:   { x: -15, y: 64, z: 0, yaw: -90, pitch: 0 }

  # Alternative spawn types for PvP arenas:

  forest_arena:
    display-name: "&2Forest Arena"
    pvpwager-world-enable: true
    build-allowed: false
    regenerate-world: true
    clone-source-world: "forest_template"
    
    # RANDOM_RADIUS example
    pvpwager-spawn:
      spawn-type: RANDOM_RADIUS
      random-radius:
        center-x: 0
        center-z: 0
        radius: 20
        min-distance: 10

  snow_arena:
    display-name: "&fSnow Arena"
    pvpwager-world-enable: true
    build-allowed: false
    
    # RANDOM_AREA example
    pvpwager-spawn:
      spawn-type: RANDOM_AREA
      random-area:
        point1: { x: -30, z: -30 }
        point2: { x: 30, z: 30 }
        min-distance: 15

  void_arena:
    display-name: "&5Void Arena"
    pvpwager-world-enable: true
    build-allowed: false
    
    # RANDOM_CUBE example (3D spawning)
    pvpwager-spawn:
      spawn-type: RANDOM_CUBE
      random-cube:
        point1: { x: -20, y: 64, z: -20 }
        point2: { x: 20, y: 80, z: 20 }
        min-distance: 10

  castle_arena:
    display-name: "&6Castle Arena"
    pvpwager-world-enable: true
    build-allowed: false
    
    # MULTIPLE_SPAWNS example
    pvpwager-spawn:
      spawn-type: MULTIPLE_SPAWNS
      spawns:
        spawn1: { x: 25, y: 70, z: 0, yaw: 90, pitch: 0 }
        spawn2: { x: -25, y: 70, z: 0, yaw: -90, pitch: 0 }
        spawn3: { x: 0, y: 65, z: 25, yaw: 180, pitch: 0 }
        spawn4: { x: 0, y: 65, z: -25, yaw: 0, pitch: 0 }

  # Event worlds (PvP disabled)
  EventLobby:
    display-name: "&aEvent Lobby"
    pvpwager-world-enable: false
    build-allowed: false

  EventWorld:
    display-name: "&aEvent World"
    pvpwager-world-enable: false
    build-allowed: false
    regenerate-world: true
    clone-source-world: "EventWorldTemplate"

  PvPArena:
    display-name: "&cPvP Arena (Event)"
    pvpwager-world-enable: true
    build-allowed: false
    regenerate-world: true
    clone-source-world: "PvPArena_original"
    # This world is used for events, but can also be used for PvP wagers
    pvpwager-spawn:
      spawn-type: FIXED_SPAWNS
      spawns:
        spectator: { x: 0, y: 80, z: 0, yaw: 0, pitch: 0 }
        player1:   { x: 10, y: 65, z: 0, yaw: 90, pitch: 0 }
        player2:   { x: -10, y: 65, z: 0, yaw: -90, pitch: 0 }
```

## equipment.yml – Equipment Sets

```yml
equipment-sets:
  # Starter PvP set - enabled for all PvP worlds
  pvp_starter:
    enabled: true
    allowed-pvpwager-worlds: all    # all | none | comma-separated list
    display-name: "&aStarter PvP"
    description: "&7Basic starting equipment"
    
    # GUI display item (for selection menus)
    gui-item:
      material: STONE_SWORD
      slot: 10
      name: "&a&lSTARTER"
      lore:
        - "&7Basic equipment for quick matches"
    
    armor:
      helmet: LEATHER_HELMET
      chestplate: LEATHER_CHESTPLATE
      leggings: LEATHER_LEGGINGS
      boots: LEATHER_BOOTS
    
    inventory:
      - slot: 0
        item: STONE_SWORD
        amount: 1
      - slot: 1
        item: BOW
        amount: 1
        enchantments:
          - "POWER:2"
          - "KNOCKBACK:1"
      - slot: 8
        item: ARROW
        amount: 32
      - slot: 2
        item: GOLDEN_APPLE
        amount: 2
      - slot: 3
        item: COOKED_BEEF
        amount: 16

  # Diamond set - not available for PvP (events only)
  diamond:
    enabled: true
    allowed-pvpwager-worlds: none    # Only for events, not PvP
    display-name: "&bDiamond"
    description: "&7Full diamond equipment"
    
    gui-item:
      material: DIAMOND_SWORD
      slot: 11
      name: "&b&lDIAMOND"
      lore:
        - "&7Full diamond armor and tools"
    
    armor:
      helmet: DIAMOND_HELMET
      helmet-enchantments:
        - "PROTECTION:4"
        - "UNBREAKING:3"
      chestplate: DIAMOND_CHESTPLATE
      chestplate-enchantments:
        - "PROTECTION:4"
        - "UNBREAKING:3"
      leggings: DIAMOND_LEGGINGS
      leggings-enchantments:
        - "PROTECTION:4"
        - "UNBREAKING:3"
      boots: DIAMOND_BOOTS
      boots-enchantments:
        - "PROTECTION:4"
        - "UNBREAKING:3"
        - "FEATHER_FALLING:4"
    
    inventory:
      - slot: 0
        item: DIAMOND_SWORD
        amount: 1
        enchantments:
          - "SHARPNESS:5"
          - "UNBREAKING:3"
          - "FIRE_ASPECT:2"
        name: "&b&lDiamond Blade"
      - slot: 1
        item: BOW
        amount: 1
        enchantments:
          - "POWER:5"
          - "UNBREAKING:3"
          - "INFINITY:1"
      - slot: 8
        item: ARROW
        amount: 1
      - slot: 2
        item: GOLDEN_APPLE
        amount: 5
      - slot: 3
        item: ENDER_PEARL
        amount: 3

  # Arena-specific set - only available in desert_arena
  desert_pvp:
    enabled: true
    allowed-pvpwager-worlds: "desert_arena"    # Comma-separated or list format
    display-name: "&eDesert Warrior"
    description: "&7Equipment for desert combat"
    
    gui-item:
      material: IRON_SWORD
      slot: 12
      name: "&e&lDESERT"
      lore:
        - "&7Specialized for desert arena"
    
    armor:
      helmet: IRON_HELMET
      chestplate: IRON_CHESTPLATE
      leggings: IRON_LEGGINGS
      boots: IRON_BOOTS
    
    inventory:
      - slot: 0
        item: IRON_SWORD
        amount: 1
        enchantments:
          - "SHARPNESS:3"
      - slot: 1
        item: BOW
        amount: 1
        enchantments:
          - "POWER:3"
      - slot: 8
        item: ARROW
        amount: 64
      - slot: 2
        item: GOLDEN_APPLE
        amount: 3

  # Multiple arenas - list format
  forest_pvp:
    enabled: true
    allowed-pvpwager-worlds: ["forest_arena", "snow_arena"]  # Available in multiple arenas
    display-name: "&2Forest Hunter"
    description: "&7Stealth equipment"
    
    gui-item:
      material: BOW
      slot: 13
      name: "&2&lFOREST"
      lore:
        - "&7Bow-focused combat"
    
    armor:
      helmet: CHAINMAIL_HELMET
      chestplate: CHAINMAIL_CHESTPLATE
      leggings: CHAINMAIL_LEGGINGS
      boots: CHAINMAIL_BOOTS
    
    inventory:
      - slot: 0
        item: STONE_SWORD
        amount: 1
      - slot: 1
        item: BOW
        amount: 1
        enchantments:
          - "POWER:4"
          - "PUNCH:2"
          - "FLAME:1"
      - slot: 8
        item: ARROW
        amount: 128
      - slot: 2
        item: GOLDEN_APPLE
        amount: 4
      - slot: 3
        item: COOKED_SALMON
        amount: 32

  # Event-only equipment set
  event_standard:
    enabled: true
    allowed-pvpwager-worlds: none    # Not available for PvP wagers
    display-name: "&6Event Standard"
    description: "&7Standard event equipment"
    
    armor:
      helmet: IRON_HELMET
      chestplate: IRON_CHESTPLATE
      leggings: IRON_LEGGINGS
      boots: IRON_BOOTS
    
    inventory:
      - slot: 0
        item: IRON_SWORD
        amount: 1
      - slot: 1
        item: BOW
        amount: 1
      - slot: 8
        item: ARROW
        amount: 64
      - slot: 2
        item: GOLDEN_APPLE
        amount: 3
```

### allowed-pvpwager-worlds Explanation
- Controls which PvP worlds can use this equipment and show it in tab completion
- Values:
  - `all` – allowed in all PvP worlds
  - `none` – not allowed in any PvP world (events only)
  - Comma-separated list or array – only in explicitly named worlds
    - Examples: 
      - `allowed-pvpwager-worlds: "PvPArena, DesertArena"`
      - `allowed-pvpwager-worlds: ["PvPArena", "DesertArena"]`

Tab completion in PvP commands automatically filters based on the previously specified arena, showing only allowed sets.

## messages.yml – Messages & Placeholders

```yml
general:
  prefix: "&6[Event-PvP]&r"
  player-only: "&cOnly players can use this command!"
  no-permission: "&cNo permission."
  unknown-command: "&cUnknown command."
  event-not-found: "&cEvent &e{event}&c not found."
  arena-not-found: "&cArena &e{arena}&c not found."
  reload-success: "&aConfiguration reloaded successfully!"
  reload-failed: "&cFailed to reload configuration: {error}"

join:
  already-joined: "&cYou are already in an event."
  success: "&aYou joined the event!"
  event-full: "&cEvent is full."
  event-running: "&cEvent is already running."
  countdown-active: "&eCountdown active – joining not possible."
  min-players-not-met: "&cNot enough players. Minimum: &e{min}"

leave:
  not-in-event: "&cYou are not in an event."
  success: "&aYou left the event."

countdown:
  preparing: "&7Preparing..."
  starting: "&eStarting..."
  seconds: "&eStarting in &6{seconds}&e seconds..."
  go: "&aGO!"

start:
  preparing-worlds: "&7Preparing worlds..."
  loading-world: "&7Loading world &e{world}&7..."
  force: "&cAdmin force-start!"
  success: "&aEvent started!"
  failed: "&cFailed to start event: {reason}"

stop:
  success: "&aEvent stopped."
  cleanup: "&7Cleaning up..."

pvp:
  request-sent: "&aRequest sent to &e{target}&a."
  request-received: "&e{sender}&7 challenged you!"
  request-accepted: "&aYou accepted &e{sender}&a's challenge!"
  request-denied: "&cYou denied &e{sender}&c's request."
  request-expired-sender: "&cYour request to &e{target}&c expired."
  request-expired-target: "&cRequest from &e{sender}&c expired."
  wager-invalid: "&cInvalid wager: {reason}"
  money-insufficient: "&cNot enough money. Balance: &6${balance}"
  items-insufficient: "&cYou don't have enough: &e{item}"
  inventory-full: "&cNot enough inventory space!"
  match-starting: "&7Match starts in &e{seconds}s"
  match-begun: "&aFIGHT!"
  match-ended: "&7Match ended. Winner: &e{winner}"
  match-draw: "&7Match ended in a draw."
  no-active-match: "&cYou are not in a match."
  surrender-success: "&cYou surrendered."
  draw-voted: "&eYou voted for a draw."
  spectating: "&7Spectating: &e{player1} &7vs &e{player2}"
  spectator-join: "&e{player}&7 is now spectating."
  spectator-leave: "&e{player}&7 stopped spectating."

worlds:
  loading: "&7Loading world... &e⏳"
  loaded: "&aWorld loaded! ✓"
  load-failed: "&cWorld could not be loaded! ✗"
  regenerating: "&7Regenerating world..."
  regeneration-complete: "&aRegeneration complete!"
  backup-start: "&7Creating backup..."
  backup-done: "&aBackup completed."
  backup-failed: "&cBackup failed: {error}"
  cloning: "&7Cloning world from &e{source}&7..."
  clone-complete: "&aWorld cloned successfully!"
  clone-failed: "&cFailed to clone world: {error}"

inventory:
  snapshot-saved: "&7Inventory saved (ID: &e{id}&7)"
  snapshot-restored: "&aInventory restored!"
  snapshot-failed: "&cFailed to restore inventory."
  snapshot-not-found: "&cInventory snapshot not found."

errors:
  multiverse-required: "&cMultiverse-Core is required for this operation!"
  multiverse-inventories-recommended: "&eWarning: Multiverse-Inventories not found. Inventory restoration may be unreliable."
  world-not-found: "&cWorld &e{world}&c not found!"
  player-offline: "&cPlayer &e{player}&c is offline."
  already-in-match: "&cYou are already in a match!"
  target-in-match: "&e{target}&c is already in a match!"
  command-blocked: "&cThis command is blocked during events/matches!"
```

### Placeholders
- `{event}`: Event ID
- `{arena}`: Arena ID (world name)
- `{target}` / `{sender}`: Player names
- `{seconds}`: Remaining seconds
- `{winner}`: Player name
- `{balance}`: Money amount
- `{item}`: Item name
- `{world}`: World name
- `{source}`: Source world name
- `{id}`: Inventory snapshot ID
- `{player}` / `{player1}` / `{player2}`: Player names
- `{min}`: Minimum players
- `{error}`: Error message
- `{reason}`: Reason/explanation

Color codes use `&` (Bukkit/Spigot convention)

## Spawn Type COMMAND (Events)

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
      winner:
        commands:
          enabled: true
          commands:
            - "say {player} won!"
      participation:
        items:
          enabled: false
        commands:
          enabled: false
```

## Spawn Type COMMAND (PvP Arena)

```yml
worlds:
  command_arena:
    display-name: "&dCommand Arena"
    pvpwager-world-enable: true
    build-allowed: false
    pvpwager-spawn:
      spawn-type: COMMAND
      command:
        command: "tp {player} {x} {y} {z}"
        placeholders:
          player: "{player}"
          x: "0"
          y: "64"
          z: "0"
```

---

## Notes & Best Practices

### World Management
- World loading is non-blocking (asynchronous). Players see status messages.
- Backups can be executed asynchronously to save server ticks (`settings.arena-regeneration.backup-async: true`)
- For large worlds, `clone-source-world` & regeneration are recommended for consistent states
- After YAML changes, run `/eventpvp reload`

### Inventory Management
- **⚠️ Important**: The plugin's built-in inventory system is experimental and may be buggy
- **Strongly recommended**: Install **Multiverse-Inventories** for reliable inventory restoration
- Configure world groups in Multiverse-Inventories for automatic inventory switching
- The plugin's inventory snapshots work best as a fallback alongside Multiverse-Inventories

### Equipment Configuration
- Use `allowed-pvpwager-worlds` to control equipment availability
- `all`: Available in all PvP arenas
- `none`: Only for events, not for PvP wagers
- Comma-separated list: Specific arenas only
- Tab completion automatically filters based on selected arena

### Performance Optimization
- Enable async backups for large worlds
- Use world cloning instead of full regeneration when possible
- Set appropriate `world-loading` options (e.g., `arena` to only load when needed)
- Configure `retain-days` for inventory snapshots to prevent database bloat

### Multi-language Support
- Set `settings.language` to your preferred language (en, de, fr, es, ru, pl, ja, zh)
- Each language has its own `messages_<lang>.yml` file
- Messages automatically load based on the configured language
