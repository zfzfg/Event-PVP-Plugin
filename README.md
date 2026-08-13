# Event-PVP-Plugin

A comprehensive Minecraft plugin combining custom event management and PvP wager system with a modern web interface. Build engaging player experiences with customizable events, arenas, equipment sets, and betting mechanics.

## Web Interface

Configure your entire plugin through an intuitive browser-based interface! No need to manually edit YAML files.

### 🌐 Key Features
- **🎨 Visual Configuration**: Edit all plugin settings through a modern, user-friendly web interface
- **🔒 Secure Access**: Token-based authentication with configurable expiration times
- **🌍 Multi-language**: Full support for 7 languages (EN, DE, FR, ES, RU, PL, JA)
- **📝 Live YAML Editor**: Edit config, worlds, equipment, and events with syntax highlighting
- **🖼️ Item Textures**: Visual item preview with Minecraft textures for equipment creation
- **💾 Real-time Validation**: Instant syntax checking before saving changes
- **🎯 Theme Customization**: Customize colors and appearance to match your server
- **🌍 World Management**: Pick world IDs from a dropdown of the worlds that actually exist on the server, see at a glance whether each one is loaded, unloaded or just a placeholder, load/unload them, and create new worlds through Multiverse without leaving the browser

### Quick Start
1. Enable the web interface in `web-config.yml`:
   ```yml
   web:
     enabled: true
     port: 8085
   ```
2. Generate a secure access token: `/eventpvp webtoken`
3. Open your browser to `http://localhost:8085` (or your server IP)
4. Enter the token and start configuring!

The web interface automatically loads all current configurations and allows you to edit them visually with instant feedback. Changes are validated before being saved to ensure your configuration remains error-free.

## Features

### 🎮 Event System
- **Multiple Event Types**: Create custom events with unique mechanics and win conditions
- **Flexible Spawn Systems**: 7 different spawn types including single point, random radius, team spawns, and more
- **Team & Solo Modes**: Support for solo, 2-team, and 3-team game modes
- **Auto-Events**: Automated event scheduling with random or sequential selection
- **Custom Rewards**: Configure item and command rewards for winners and participants
- **Statistics Tracking**: Track player wins, participations, and leaderboards

### ⚔️ PvP Wager System
- **Wagering**: Bet items or money (Vault integration) on PvP matches
- **Interactive GUI**: Modern inventory-based wager setup interface
- **Arena Selection**: Multiple arenas with custom spawn configurations
- **Equipment Sets**: Pre-configured loadouts with customizable enchantments
- **Spectator Mode**: Allow players to watch ongoing matches
- **Match Management**: Draw votes, surrender options, and timeout handling

### 🌍 World Management
- **Dynamic Loading**: Automatic world loading/unloading with Multiverse-Core integration
- **World Cloning**: Clone template worlds for events and arenas
- **Regeneration**: Automatic world reset after events/matches with backup support
- **Build Protection**: Per-world build permission control
- **Web Panel Control**: Create, load, unload and delete worlds from the web interface — see
  [Managing worlds from the web interface](#managing-worlds-from-the-web-interface)

#### About Multiverse-Core (Required)
**Multiverse-Core** is a required dependency for world operations:
- **World Loading/Unloading**: Automatically loads event and arena worlds when needed and unloads them after use to save resources
- **World Cloning**: Creates copies of template worlds (e.g., `PvPArena_original` → `PvPArena`) for each event/match
- **World Regeneration**: Resets worlds to their original state after events/matches, with optional backup creation
- **Environment Detection**: Automatically detects world type (NORMAL, NETHER, THE_END) for proper loading
- **World Creation & Deletion**: Creates and removes worlds on request from the web interface

Both Multiverse-Core 4 and 5 are supported. On version 5 the plugin talks to the typed API; on
version 4 it uses console commands. Without Multiverse-Core installed and enabled, the plugin refuses to start.

> **Note for Multiverse-Core 5 users:** deleting a world used to fail silently. MV5 protects
> `mv delete` with a one-time confirmation code that no external plugin can read, so the queued
> deletion simply expired. Since 1.0.9 the plugin uses the API, which needs no confirmation.

#### Managing worlds from the web interface

In **Worlds & Arenas** the world ID is a dropdown of the worlds that really exist on the server.
Each entry shows its environment, whether it is loaded, and which preset or event already uses it;
worlds that already back another preset are disabled, because the preset key *is* the world name.
Choosing *"Enter a custom world ID…"* lets you create a preset with no world behind it — the card
then marks it ⚪ *Placeholder*.

The editor's **Multiverse** tab creates the world on the server. Every setting is optional:

| Setting | Values | Notes |
|---|---|---|
| Environment | `NORMAL`, `NETHER`, `THE_END` | |
| World type | `NORMAL`, `FLAT`, `LARGE_BIOMES`, `AMPLIFIED` | |
| Seed | any text or number | empty = random |
| Generator | `Plugin` or `Plugin:id` | e.g. a void generator |
| Generator settings | JSON | Multiverse-Core 5 only |
| Biome | biome name | single-biome world, Multiverse-Core 5 only |
| Generate structures | on/off | villages, temples, strongholds |
| Adjust spawn | on/off | Multiverse looks for a safe spawn |

World creation runs in the background and the panel shows its progress, so large worlds cannot run
into a request timeout.

Every world card shows 🟢 *Loaded*, 🟡 *Unloaded* or ⚪ *Placeholder* together with a load/unload
button, and the collapsible **Server worlds** panel lists all Multiverse worlds — including those
without a preset.

**Deleting.** Removing a preset does not touch the world unless you say so. The delete dialog has a
separate *"also delete the world on the server"* checkbox that is **off by default**; switching it on
reveals a *"create a backup first"* option (**on by default**, zips into `plugins/<plugin>/backups/`)
and requires you to type the world ID before the delete button unlocks. The editor also offers
*"Delete world only"*, which removes the world but keeps the preset as a placeholder. The server's
main world can never be deleted or unloaded this way.

If the backup cannot be written, **the world is not deleted** — the panel reports why instead of
leaving you with neither the world nor a backup.

**Restoring backups.** The collapsible **Backup worlds** panel (below *Server worlds*) lists every
backup zip with world name, date and size. *Restore* brings a backup back as a world — the name is
prefilled with the original and can be changed; an existing world is never overwritten. Restoring
runs in the background and the world is imported and loaded through Multiverse when it finishes.
Backups can also be deleted from the panel (only the zip file — never a world).

Worlds stored as dimensions inside the main world (`world/dimensions/minecraft/<name>`, the layout
modern servers use) are fully supported for status display, backup and deletion.

#### About Inventory Management (via InventoryBackup)
**Inventory management** is handled automatically by the plugin using the required backend plugin **InventoryBackup** (from the InventoryRestore project):
- **Pre-teleport Backups**: Backups are taken before a player is teleported to an arena or lobby world.
- **Automatic Restoration**: Inventories are automatically restored after matches, events, deaths, and reconnects.
- **Persistent Offline Queue & Crash Recovery**: If a player disconnects mid-match or the server restarts unexpectedly, open sessions in `inventory-guard.yml` and return locations in `player-return-locations.yml` guarantee items and positions are safely restored on the next login.
- **No Multiverse-Inventories Needed**: In default mode (`settings.inventory-management.provider: "auto"`), Multiverse-Inventories is not required.
- **Legacy Mode**: If you intentionally want Multiverse-Inventories to handle inventory swaps, set `settings.inventory-management.provider: "none"`.

### 🎨 Web Interface
- **Real-time Configuration**: Edit config, worlds, equipment, and events through web browser
- **Token Authentication**: Secure access with time-limited tokens
- **Live Preview**: See changes instantly with syntax validation
- **Multi-language**: Support for 7 languages (EN, DE, FR, ES, RU, PL, JA)
- **Item Textures**: Visual item selection with Minecraft textures

### 🔧 Advanced Features
- **Inventory Management**: Automated backup, restore, and crash recovery via InventoryBackup
- **Multi-language Support**: Built-in translations for 7 languages
- **Performance Optimized**: Async operations for backups and world operations
- **Command Restriction**: Configurable command blocking during events
- **Tab Completion**: Smart tab completion for all commands
- **Update Checking**: Automatic update notifications via Modrinth API
  - Checks for newer versions on server startup
  - Notifies admins with `eventpvp.admin.updatenotify` permission on join
  - Manual check with `/eventpvp version` command
  - Configurable via `settings.update-check` in config.yml

## Requirements

- **Server**: Paper/Spigot 1.19+ compatible
- **Required Dependencies**: 
  - **Multiverse-Core** (v4 or v5): Essential for world management (loading, unloading, cloning, regeneration)
  - **Vault**: Economy integration for money wagers
  - **InventoryBackup** (from InventoryRestore project): Storage backend for player inventory backups and auto-restore
- **Optional Integrations**: 
  - **PlaceholderAPI**: Numeric statistics placeholders
  - **AJLeaderboards / DecentHolograms**: Statistics displays on leaderboards and holograms
  - **PvPManager**: Automatic combat tag removal

## Installation

1. **Install Required Dependencies:**
   - Download and install **Multiverse-Core**
   - Download and install **Vault**
   - Download and install **InventoryBackup** (InventoryRestore project)

2. **Install the Plugin:**
   - Download the plugin JAR file
   - Place it in your server's `plugins/` folder

3. **Start the Server:**
   - Start the server to generate configuration files
   - The plugin will verify dependencies and initialize language and configuration files

4. **Configure the Plugin:**
   - Edit configuration files (see Configuration section)
   - Or use the web interface: `/eventpvp webtoken`

5. **Reload and Test:**
   ```
   /eventpvp reload
   /event list
   ```

## Updating

When updating to a new version of the plugin:

- **Language Files**: Language files (`messages_*.yml`) are NOT automatically replaced when updating. If you have customized language files, you need to delete them from the plugin data folder and restart the server to let the plugin regenerate them with the new message keys, then manually reapply your customizations.

- **Configuration Files**: Other configuration files (`config.yml`, `worlds.yml`, `equipment.yml`, `web-config.yml`) are preserved during updates. The plugin will not overwrite existing files. If new configuration options are added in a new version, they will use default values until you manually add them to your config files.

## Configuration Files

The plugin uses centralized configuration files in the plugin folder:

| File | Purpose |
|------|---------|
| `config.yml` | General settings, event definitions, auto-events |
| `worlds.yml` | World definitions with spawn points and flags |
| `equipment.yml` | Shared equipment sets for events and PvP |
| `messages_<lang>.yml` | Message configuration, one file per language (`de`, `en`, `es`, `fr`, `ja`, `pl`, `ru`) |
| `web-config.yml` | Web interface settings and theming |

### config.yml Structure

```yml
settings:
  language: "en"                    # en, de, fr, es, ru, pl, ja
  prefix: "&6[Event]&r"
  main-world: "world"
  debug: "off"                      # off, on, full
  save-player-location: true
  join-phase-duration: 30
  lobby-countdown: 10

  # Inventory management (InventoryBackup backend)
  inventory-management:
    provider: "auto"                # auto (recommended), inventoryrestore, none (legacy)
    legacy-safety-backups: true
    auto-restore-on-match-end: true
    auto-restore-on-event-end: true
    auto-restore-on-respawn: true
    auto-restore-on-rejoin: true
    on-backup-failure: "abort"      # abort (safest), warn
    cleanup-backups-after-match: false
    guard:
      enabled: true
      restore-orphans-on-start: true
    warn-on-multiverse-inventories: true

  # World management via Multiverse
  world-management:
    events: true                    # Load/unload event worlds
    arenas: true                    # Unload arena worlds after match

  # Command restrictions during events (both, event, lobby, none)
  command-restriction: "both"
  
  # World regeneration settings
  arena-regeneration:
    backups: true                   # Create backups before regeneration
    backup-async: true              # Async backups (recommended)
  
  # Auto-event scheduler
  auto-events:
    enabled: false
    interval-min: 1800              # 30 minutes
    interval-max: 3600              # 60 minutes
    random-selection: true
    check-online-players: true
    selected-events:
      # - "pvparena"
      # - "ctf"
  
  # PvP settings
  match:
    countdown-time: 10
    max-duration: 600               # 10 minutes
    draw-vote-time: 30
    allow-no-wager: true
  
  spectators:
    enabled: true
    max-spectators: 10
    announce-join: true
    announce-leave: true

  # External integrations
  integrations:
    ajleaderboards:
      enabled: false
    decentholograms:
      enabled: false
    pvpmanager:
      enabled: true
    refresh-interval-ticks: 20

events:
  pvparena:
    enabled: true
    command: "pvparena"
    display-name: "&c&lPvP Arena"
    description: "&7Fight to the last man standing!"
    
    min-players: 2
    max-players: 16
    countdown-time: 45
    
    worlds:
      lobby-world: "EventLobby"
      lobby-spawn: { x: 0.5, y: 65, z: 0.5, yaw: 0, pitch: 0 }
      event-world: "PvPArena"
      clone-source-event-world: "PvPArena_original"
      build-allowed: false
      regenerate-event-world: true
    
    spawn-settings:
      spawn-type: "SINGLE_POINT"    # See Spawn Types section
      single-spawn: { x: 0.5, y: 65, z: 0.5, yaw: 0, pitch: 0 }
    
    equipment-group: "pvp_starter"
    give-equipment-in-lobby: true
    
    mechanics:
      game-mode: "SOLO"              # SOLO, TEAM_2, TEAM_3
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

### worlds.yml Structure

```yml
worlds:
  PvPArena:
    display-name: "&cPvP Arena"
    pvpwager-world-enable: true     # Enable for PvP wagers
    build-allowed: false
    regenerate-world: true
    clone-source-world: "PvPArena_original"
    
    pvpwager-spawn:
      spawn-type: FIXED_SPAWNS
      spawns:
        spectator: { x: 0, y: 80, z: 0, yaw: 0, pitch: 0 }
        player1:   { x: 10, y: 64, z: 0, yaw: 90, pitch: 0 }
        player2:   { x: -10, y: 64, z: 0, yaw: -90, pitch: 0 }
  
  EventLobby:
    display-name: "&aEvent Lobby"
    pvpwager-world-enable: false
    build-allowed: false
```

### equipment.yml Structure

```yml
equipment-sets:
  pvp_starter:
    enabled: true
    allowed-pvpwager-worlds: "all"  # all, none, or world list
    display-name: "&aStarter PvP"
    description: "&7Basic starting equipment"
    
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
  
  diamond_pvp:
    enabled: true
    allowed-pvpwager-worlds: "all"
    display-name: "&b&lDiamond PvP Set"
    
    armor:
      helmet: DIAMOND_HELMET
      helmet-enchantments:
        - "PROTECTION:4"
        - "UNBREAKING:3"
        - "RESPIRATION:3"
      chestplate: DIAMOND_CHESTPLATE
      chestplate-enchantments:
        - "PROTECTION:4"
        - "UNBREAKING:3"
      # ... more armor pieces
    
    inventory:
      - slot: 0
        item: DIAMOND_SWORD
        amount: 1
        enchantments:
          - "SHARPNESS:5"
          - "UNBREAKING:3"
        name: "&b&lDiamond Slayer"
```

### Spawn Types

#### Event Spawn Types
Configure in `events.<id>.spawn-settings`:

- **SINGLE_POINT**: All players spawn at one location
  ```yml
  spawn-type: SINGLE_POINT
  single-spawn: { x: 0, y: 65, z: 0, yaw: 0, pitch: 0 }
  ```

- **RANDOM_RADIUS**: Random spawn within a circular radius
  ```yml
  spawn-type: RANDOM_RADIUS
  random-radius:
    center-x: 0
    center-z: 0
    radius: 50
    min-distance: 10
  ```

- **RANDOM_AREA**: Random spawn in 2D area (X/Z)
  ```yml
  spawn-type: RANDOM_AREA
  random-area:
    point1: { x: -50, z: -50 }
    point2: { x: 50, z: 50 }
    min-distance: 10
  ```

- **RANDOM_CUBE**: Random spawn in 3D volume
  ```yml
  spawn-type: RANDOM_CUBE
  random-cube:
    point1: { x: -50, y: 60, z: -50 }
    point2: { x: 50, y: 100, z: 50 }
    min-distance: 10
  ```

- **MULTIPLE_SPAWNS**: Predefined spawn points
  ```yml
  spawn-type: MULTIPLE_SPAWNS
  multiple-spawns:
    spawns:
      spawn1: { x: 10, y: 64, z: 0, yaw: 0, pitch: 0 }
      spawn2: { x: -10, y: 64, z: 0, yaw: 180, pitch: 0 }
  ```

- **TEAM_SPAWNS**: Team-specific spawn points
  ```yml
  spawn-type: TEAM_SPAWNS
  team-spawns:
    RED:
      spawn1: { x: 50, y: 64, z: 0, yaw: -90, pitch: 0 }
    BLUE:
      spawn1: { x: -50, y: 64, z: 0, yaw: 90, pitch: 0 }
  ```

- **COMMAND**: Execute command for spawning
  ```yml
  spawn-type: COMMAND
  spawn-command: "tp {player} 0 65 0"
  ```

#### PvP Arena Spawn Types
Configure in `worlds.<world>.pvpwager-spawn`:

- **FIXED_SPAWNS**: Dedicated player/spectator spawns
  ```yml
  spawn-type: FIXED_SPAWNS
  spawns:
    spectator: { x: 0, y: 80, z: 0, yaw: 0, pitch: 0 }
    player1:   { x: 10, y: 64, z: 0, yaw: 90, pitch: 0 }
    player2:   { x: -10, y: 64, z: 0, yaw: -90, pitch: 0 }
  ```

- **RANDOM_RADIUS**, **RANDOM_AREA**, **RANDOM_CUBE**, **MULTIPLE_SPAWNS**: Same as event spawn types
- **COMMAND**: Command-based spawning with placeholders

## Commands

### Event Commands

| Command | Description | Permission |
|---------|-------------|------------|
| `/event <name> join` | Join an event | `eventplugin.join` |
| `/event <name> leave` | Leave an event | `eventplugin.join` |
| `/event <name> start` | Start an event (admin) | `eventplugin.admin` |
| `/event <name> stop` | Stop an event (admin) | `eventplugin.admin` |
| `/event <name> forcestart` | Force start without min players | `eventplugin.admin` |
| `/event list` | List available events | `eventplugin.join` |
| `/eventstats me` | View your statistics | - |
| `/eventstats top [N]` | View leaderboard | - |

### PvP Wager Commands

| Command | Description | Permission |
|---------|-------------|------------|
| `/pvpask <player>` | Send PvP request (opens GUI) | `pvpwager.use` |
| `/pvpaccept [player]` | Accept request and open GUI | `pvpwager.use` |
| `/pvpdeny [player]` | Deny a request | `pvpwager.use` |
| `/pvpa <player> <wager> <amount> <arena> <equipment>` | Full request command | `pvpwager.use` |
| `/pvpanswer <wager> <amount> [arena] [equipment]` | Counter-offer | `pvpwager.use` |
| `/pvpyes` | Confirm counter-offer | `pvpwager.use` |
| `/pvpno` | Decline counter-offer | `pvpwager.use` |
| `/surrender` | Surrender current match | `pvpwager.use` |
| `/draw` | Vote for a draw | `pvpwager.use` |
| `/pvp spectate <player>` | Spectate a match | `pvpwager.spectate` |
| `/pvp leave` | Leave match/spectator mode | `pvpwager.use` |
| `/pvpainfo` | Show PvP command help | - |
| `/pvpstats me` | View your PvP stats | - |
| `/pvpstats top [N]` | View PvP leaderboard | - |
| `/pvpadmin reload` | Reload PvP config | `pvpwager.admin` |
| `/pvpadmin stopall` | Stop all matches | `pvpwager.admin` |
| `/pvpadmin info` | Show system info | `pvpwager.admin` |

### Unified Commands

| Command | Description | Permission |
|---------|-------------|------------|
| `/eventpvp reload` | Reload all configurations | `eventpvp.admin` |
| `/eventpvp version` | Check plugin version and update status | `eventpvp.admin` |
| `/eventpvp webtoken` | Generate web interface token | `eventpvp.admin.web` |
| `/eventpvp debug` | Toggle debug logging (`on`, `on full`, `off`, `status`) | `eventpvp.debug` |
| `/eventpvp rescue` | Manage stuck sessions (`list`, `<player>`, `clean`) | `eventpvp.admin` |
| `/inv <player>` | Inspect and restore inventory backups via InventoryBackup | `inventoryrestore.admin` |

### Command Examples

**PvP Wager with Items:**
```
/pvpa Steve DIAMOND_SWORD 1 PvPArena diamond_pvp
```

**PvP Wager with Money (requires Vault):**
```
/pvpa Alex MONEY 100 desert standard
```

**No-Wager Match:**
```
/pvpa Mike SKIP 0 forest pvp_starter
```

**Counter-Offer:**
```
/pvpanswer MONEY 150 PvPArena diamond_pvp
/pvpyes
```

## Permissions

### Core Permissions
- `eventpvp.admin` - Access to unified admin commands (reload, version, rescue)
- `eventpvp.admin.web` - Access to web interface
- `eventpvp.admin.updatenotify` - Receive update notifications on join
- `eventpvp.debug` - Toggle debug mode (`/eventpvp debug`)
- `eventpvp.debug.receive` - Receive debug log output in chat
- `eventpvp.opbypass` - Bypass event command restrictions

### Event Permissions
- `eventplugin.admin` - Event administration
- `eventplugin.join` - Join events
- `eventplugin.stats.reset` - Reset player statistics

### PvP Permissions
- `pvpwager.admin` - PvP administration
- `pvpwager.use` - Use PvP wager system
- `pvpwager.spectate` - Spectate matches
- `pvpwager.spectate.all` - Spectate any match
- `pvpwager.command` - Use command-based requests
- `pvpwager.bypass.betlimit` - Bypass betting limits
- `pvpwager.nowager` - Create no-wager matches

## Web Interface

### Access the Web Interface

1. Configure in `web-config.yml`:
   ```yml
   server:
     port: 8085
     bind-address: ""              # "" for all interfaces, or "127.0.0.1" behind a reverse proxy
     public-url: "http://localhost:8085"

   auth:
     enabled: true
     token-expiration: 300         # 5 minutes token validity
     session-expiration: 86400     # 24 hours session validity

   items:
     enable-textures: true
     resource-pack:
       enabled: false              # Extract textures from server.properties resource-pack
       max-size-mb: 50
   ```

2. Generate an access token in-game or via console:
   ```
   /eventpvp webtoken
   ```
   *(Requires permission `eventpvp.admin.web` or `eventpvp.admin`)*

3. Open the URL and enter the token

4. Configure your plugin through the web interface!

### Features
- **Live Config Editor**: Edit `config.yml`, `worlds.yml`, `equipment.yml`, and `web-config.yml` with syntax highlighting
- **Dedicated 3-Tab Inventory Manager**: Search player backups, inspect authentic Minecraft Canvas inventory layout with XP bar, execute 2-step restores with rate limiting (10/min), export to equipment sets, and monitor active guard sessions
- **Multiverse World Management**: Create, import, load, unload, delete, and backup/restore worlds directly in the browser
- **Dynamic Material Catalog**: Loads item IDs, max stack sizes, and valid enchantments directly from the running server version (`/api/materials`)
- **Server Resource Pack Textures**: Automatically pulls custom item textures from your server resource pack
- **Visual Equipment Builder**: Create equipment sets with live item previews and server material validation
- **Event Creator**: Design events with visual spawn configuration and optional lobby phase
- **Multi-language**: Seamlessly switch between 7 supported languages (EN, DE, FR, ES, RU, PL, JA)
- **Live Sync Badge**: Color-coded top-bar status (`🟢 Synced`, `🟡 Unsaved`, `🔵 Saving`, `🔴 Out of Sync`)
- **Theme Customization**: Adjust colors and appearance
- **Token Security**: Time-limited access tokens with rate-limited restore operations

## Workflows

### Creating a New Event

1. **Define the event** in `config.yml` under `events`:
   ```yml
   events:
     my_event:
       enabled: true
       command: "myevent"
       display-name: "&aMy Event"
       min-players: 2
       max-players: 10
       worlds:
         lobby-world: "EventLobby"
         event-world: "MyEventWorld"
       spawn-settings:
         spawn-type: "SINGLE_POINT"
         single-spawn: { x: 0, y: 65, z: 0 }
       equipment-group: "default"
       mechanics:
         game-mode: "SOLO"
         pvp-enabled: true
       rewards:
         winner:
           commands:
             enabled: true
             commands:
               - "say {player} won!"
         participation:
           items:
             enabled: false
   ```

2. **Reload the configuration**:
   ```
   /eventpvp reload
   ```

3. **Start the event**:
   ```
   /event myevent start
   ```

4. **Players join**:
   ```
   /event myevent join
   ```

### Setting up PvP Arena

1. **Create the world** (copy template if needed)

2. **Define in `worlds.yml`**:
   ```yml
   worlds:
     MyArena:
       display-name: "&cMy Arena"
       pvpwager-world-enable: true
       build-allowed: false
       regenerate-world: true
       clone-source-world: "MyArena_template"
       pvpwager-spawn:
         spawn-type: FIXED_SPAWNS
         spawns:
           spectator: { x: 0, y: 100, z: 0 }
           player1:   { x: 20, y: 64, z: 0, yaw: 90 }
           player2:   { x: -20, y: 64, z: 0, yaw: -90 }
   ```

3. **Create equipment set** in `equipment.yml`:
   ```yml
   equipment-sets:
     my_pvp_set:
       enabled: true
       allowed-pvpwager-worlds: "MyArena"
       # ... define armor and inventory
   ```

4. **Reload configuration**:
   ```
   /eventpvp reload
   ```

5. **Challenge someone**:
   ```
   /pvpa Steve DIAMOND_SWORD 1 MyArena my_pvp_set
   ```

### Setting up Auto-Events

1. **Configure in `config.yml`**:
   ```yml
   settings:
     auto-events:
       enabled: true
       interval-min: 1800        # 30 min
       interval-max: 3600        # 60 min
       random-selection: true
       check-online-players: true
       selected-events:
         - "pvparena"
         - "ctf"
         - "ffa"
   ```

2. **Reload**:
   ```
   /eventpvp reload
   ```

Auto-events will now start automatically based on the configured interval!

## Troubleshooting

### World Not Loading
- Ensure world folder exists in server directory
- Check `settings.world-loading` is set to `both` or appropriate value
- Install Multiverse-Core for advanced world management
- Use `clone-source-world` to automatically clone template worlds

### Equipment Not Showing in Tab Completion
- Check `enabled: true` in equipment set
- Verify `allowed-pvpwager-worlds` matches your arena
- Use `all` to allow in all arenas
- Reload config with `/eventpvp reload`

### Match Not Starting
- Check minimum player count
- Verify both players have inventory space
- Ensure arena world is loaded
- Check console for error messages
- Verify spawn points are configured

### Web Interface Not Accessible
- Check `web.enabled: true` in `web-config.yml`
- Verify port is not in use: `netstat -an | grep 8085`
- Check firewall allows the port
- Generate a new token: `/eventpvp webtoken`
- Check `security.auth-enabled` setting

### Inventory Not Restored
- Verify the backend plugin **InventoryBackup** is active on the server
- Check `/eventpvp rescue list` to inspect stuck sessions or pending return locations
- Use `/eventpvp rescue <player>` to manually restore an inventory and return location
- Inspect stored player backups via `/inv <player>` or in the web panel under *Expert Settings* -> *Inventory Management*

### Performance Issues
- Enable async backups: `arena-regeneration.backup-async: true`
- Reduce auto-event frequency
- Limit max players in events
- Use `world-management.arenas: true` to unload arena worlds after matches

## Advanced Features

### Inventory Management System
Inventories are managed reliably through the `InventoryBackup` API with persistent crash protection:

```yml
settings:
  inventory-management:
    provider: "auto"              # auto (recommended), inventoryrestore, none (legacy)
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
```

- **Crash recovery & Offline Safety:** Open sessions are tracked in `inventory-guard.yml`, original return locations in `player-return-locations.yml`, and offline wager rewards in `pending-payouts.yml`.
- **Diagnostic command:** `/eventpvp rescue list|<player>|clean` for emergency inspection and recovery.
- **Legacy mode:** Setting `provider: "none"` delegates inventory switching to Multiverse-Inventories while still taking safety backups.

### World Regeneration with Backups

```yml
settings:
  arena-regeneration:
    backups: true           # Create ZIP backup before regeneration
    backup-async: true      # Non-blocking backups (recommended)
```

Backups are stored in `plugins/Event-PVP-Plugin/backups/`

### Multi-language & Console i18n Support

Set language in `config.yml`:
```yml
settings:
  language: "en"  # en, de, fr, es, ru, pl, ja
```

Supported languages with 100% key parity across in-game messages, web interface, and server console output:
- **English (en)** – `messages_en.yml` (Master)
- **German (de)** – `messages_de.yml`
- **French (fr)** – `messages_fr.yml`
- **Spanish (es)** – `messages_es.yml`
- **Russian (ru)** – `messages_ru.yml`
- **Polish (pl)** – `messages_pl.yml`
- **Japanese (ja)** – `messages_ja.yml`

All server console loggers and diagnostic traces (`messages.console.*`) dynamically adapt to the selected language, complete with color formatting and placeholder resolution.

### Statistics System

Track player performance:
- Event wins and participations
- PvP match wins/losses
- Leaderboards with `/eventstats top` and `/pvpstats top`
- Admin commands to reset or modify stats

### Command Restrictions

Control command access during events:
```yml
settings:
  command-restriction: "both"  # both (event+lobby), event, lobby, none
```

Blocks commands for event participants (except `/event leave`). OPs and players with `eventpvp.opbypass` are exempt. PvP matches block commands unconditionally.

## Performance Tips

1. **Enable async backups** for large worlds
2. **Use world cloning** instead of full regeneration when possible
3. **Limit spectators** per match to reduce entity processing
4. **Use fixed spawns** instead of random when possible (faster)
5. **Configure auto-events** with reasonable intervals
6. **Enable world-management.arenas: true** to unload worlds when not in use

## Support & Development

### Plugin Version
Check version: `/pvpadmin info`, `/eventpvp version`, or see `plugin.yml`

### Reporting Issues
When reporting issues, provide:
- Server version (Paper/Spigot)
- Plugin version
- Error logs from console
- Configuration files
- Steps to reproduce

### Performance Monitoring
Plugin logs startup/shutdown times:
```
[Event-PVP-Plugin] Plugin enabled in 123 ms
```

Use this to track performance changes after configuration updates.

## License & Credits

**Author**: zfzfg  
**Version**: See `plugin.yml`  
**API**: 1.19+  
**Dependencies**: Multiverse-Core (required), Vault (required), InventoryBackup (required)

---

For questions, feature requests, or custom arena configurations, contact the plugin author.
