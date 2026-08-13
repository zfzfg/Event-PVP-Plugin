# Event-PVP-Plugin — Quick Overview

A lightweight, powerful all-in-one Minecraft plugin that unifies **custom automated event management** and a **PvP wager betting system** with an interactive **browser-based web configurator**.

---

## What Can You Do?

### Custom Event System
- **3 Game Modes**: Solo (Free-For-All), 2-Team, and 3-Team.
- **4 Win Conditions**: Last Standing, Pickup Item, Kill Count, and Time Survival — combinable with every game mode.
- **Switchable Lobby Phase**: Run events with an optional pre-event lobby world or teleport directly into the arena.
- **7 Spawn Types**: Single point, random radius, random area, random cube, multiple spawns, team spawns, and command-based spawns.
- **Automated Scheduling**: Automatically launch events on intervals with random or sequential rotation.
- **Custom Rewards & Leaderboards**: Distribute item and command payouts to winners and participants; track stats with PlaceholderAPI, AJLeaderboards, and DecentHolograms.

### PvP Wager System
- **Betting Mechanics**: Challenge players to duels with money wagers (Vault / VaultUnlocked) or item wagers.
- **Pre-Configured Equipment Kits**: Design custom armor, weapons, off-hand items, and inventories.
- **Arena Management**: Multiple custom arena presets with automatic resets and world protection.
- **Interactive Match Flow**: GUI challenge creation, counter-offers, draw voting, surrendering, and spectator mode.

### Modern Web Configurator
- **Visual YAML Editor**: Edit `config.yml`, `worlds.yml`, `equipment.yml`, and `web-config.yml` in real time with deep-snapshot precision dirty-tracking.
- **Dedicated 3-Tab Inventory Manager**: *Explorer* (search and inspect backups on an authentic Minecraft canvas with XP bar, restore after a confirmation prompt), *Sessions* (live crash-guard sessions), and *Settings*.
- **Multiverse World Management**: Create, import, load, unload, delete (with safety checks), and backup/restore worlds directly in the browser.
- **Server Item Textures**: Optionally extracts and displays custom item textures from your server resource pack (off by default); ~1650 vanilla item icons ship with the plugin, no internet required.
- **Secure Token Login**: Access the panel via one-time tokens (`/eventpvp webtoken`) with per-IP rate limiting; restores are additionally capped at 10 per minute.
- **Web Server Settings**: Configurable port with pre-save safety warning dialog, bind address for reverse-proxy setups, and a public URL with automatic `{port}` substitution.

### Crash Protection & Inventory Safety
- **Powered by `InventoryBackup`**: Full automatic backup before every teleport and restore on match/event end, respawn, or rejoin.
- **Anti-Strand Recovery**: Players stranded in an arena or event world after a server crash/disconnect are returned safely on reconnect via `player-return-locations.yml`.
- **Persistent Payout Queue**: Queues wager winnings and event rewards for players who disconnect before payout.

### 100% Multilingual (7 Languages)
- Fully translated in **English**, **German**, **Spanish**, **French**, **Japanese**, **Polish**, and **Russian** with 100% key parity (1097 keys).
- Full console and logger localization via `messages.console.*`.
- Automatic fallback to embedded English defaults for missing custom keys.

---

## Quick Start & Dependencies

### 1. Requirements
- **Server**: Paper / Spigot **1.19+**, Java 17+ — in practice **1.20+**, because the mandatory InventoryBackup requires it.
- **Mandatory Plugins**:
  * [Multiverse-Core](https://modrinth.com/project/3wmN97b8) (v4 or v5)
  * [VaultUnlocked](https://modrinth.com/project/ayRaM8J7) — recommended; the classic [Vault](https://www.spigotmc.org/resources/vault.34315/) from SpigotMC also works
  * [InventoryBackup](https://modrinth.com/project/rpKY25cW)
- **Optional Integrations**: PlaceholderAPI, AJLeaderboards, DecentHolograms, PvPManager

### 2. Setup
1. Put `Event-PVP-Plugin.jar`, `Multiverse-Core`, `VaultUnlocked` (or `Vault`), and `InventoryBackup` into your `plugins/` folder.
2. Start the server (configurations migrate automatically).
3. Run `/eventpvp webtoken` in-game to log into the web interface and start customizing!

### 3. Upgrading from 1.0.8

1. **Stop the server.**
2. **Install the new mandatory plugins first**: `Multiverse-Core` and `InventoryBackup` were optional or non-existent in 1.0.8. If one is missing, Spigot will not load the plugin at all (`Unknown dependency ...` in the log).
3. **Replace the jar**, then start the server. `config.yml`, `worlds.yml`, `equipment.yml`, `web-config.yml`, and your language file migrate automatically; a `<file>.bak-<timestamp>` copy is written before every change and your own values are never overwritten.
4. **Clean up your permission plugin**: `eventpvp.inventory.restore` and `eventpvp.inventory.restore.any` no longer exist.
5. **Check for Multiverse-Inventories**: running it in parallel on event/arena worlds causes inventory loss — either exclude those worlds or set `settings.inventory-management.provider: "none"`.
6. **Review the migration log block** printed on first start, and see [UPGRADE.md](UPGRADE.md) for the full details (including how to roll back to 1.0.8).

---

## Essential Commands

### For Players
```text
/event join <name>            - Join an event
/event leave                  - Leave the current event
/event list                   - View available events
/pvpask <player>              - Send a PvP challenge via GUI
/pvpaccept | /pvpdeny         - Accept or decline challenges
/surrender | /draw            - Surrender or vote for a draw in a match
/pvp spectate <player>        - Spectate an ongoing PvP duel
```

### For Admins
```text
/eventpvp webtoken            - Generate a secure web interface token
/eventpvp rescue <list|player|clean> - Manage and rescue stuck players
/eventpvp reload              - Reload all configurations and language bundles
/eventpvp debug <on|on full|off|status> - Toggle persistent debug logging
/event start <name>           - Start an event
/event stop <name>            - Stop an event
/event forcestart <name>      - Skip the remaining join countdown
/inv <player>                 - Inspect and restore player backups (provided by InventoryBackup)
```

> The reversed order `/event <name> join|leave|start|stop|forcestart` works as well.
