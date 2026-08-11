# Event-PVP-Plugin 1.0.9 — Compact Changelog

A concise overview of all new features, improvements, fixes, and breaking changes in version **1.0.9**.

---

## Key Highlights & New Features

### 1. Integrated Inventory Management (`InventoryBackup`)
- **Built-in Inventory Safety**: Replaced legacy snapshot files with `InventoryBackup` ([Modrinth](https://modrinth.com/project/rpKY25cW)) as a mandatory dependency (`depend:` in `plugin.yml`).
- **Automatic Restore Net**: Inventories are backed up before arena/lobby teleports and restored on match/event end, respawn, or rejoin.
- **Crash Protection**: Active sessions are written to `inventory-guard.yml` and restored automatically on server restart.
- **Legacy Mode**: Set `settings.inventory-management.provider: "none"` if you prefer Multiverse-Inventories.

### 2. Player Location Safety & Anti-Strand System
- **Return Location Journal (`player-return-locations.yml`)**: Player return coordinates are saved synchronously before any teleport.
- **Automatic Self-Rescue**: Players stranded in an event or arena world after a server crash/disconnect are returned to their origin upon reconnecting.
- **Unified `SafeLocationResolver`**: Prevents players from spawning in the void or becoming trapped in unloaded worlds.
- **New Command `/eventpvp rescue`**: Admins can inspect (`list`), rescue stranded players (`<player>`), or clean stale sessions (`clean`).
- **Persistent Payout Queue (`pending-payouts.yml`)**: Queues wager winnings and event rewards for players who disconnect before payout.

### 3. Complete Web Interface Overhaul
- **Dedicated 3-Tab Inventory Manager** (Explorer / Sessions / Settings): Search backups, inspect inventories via authentic Minecraft Canvas with XP bar, restore after a confirmation prompt, and monitor live sessions. Restores are capped at 10 per minute and refused while the player has an open guard session.
- **Multiverse World Management**: Create, import, load, unload, delete (with safety checks), and backup/restore worlds directly in the web panel. Works with both Multiverse-Core 4 (command backend) and 5 (API backend).
- **Server Resource Pack Textures**: Optionally extracts and displays custom item textures from the server's resource pack (`items.resource-pack.enabled`, off by default, size-capped). Only direct replacements under `assets/minecraft/textures/item/` are used — CustomModelData is deliberately not evaluated.
- **Dynamic Material Catalog**: Loads valid materials and enchantment limits directly from the server (`GET /api/materials`).
- **Deep-Snapshot Precision Dirty Tracking**: Baseline comparison (`CONFIG_BASELINE` & `isDeepEqual`) eliminates false-positive "Unsaved Changes" badges when reverting edits or saving unmodified modals.
- **Web Server Settings**:
  - Configurable **Public URL** with automatic `{port}` substitution for `/eventpvp webtoken`.
  - **Port Change Safety Warning**: Confirmation dialog before saving port changes to prevent accidental disconnection.
  - New **`web.bind-address`**: empty for all interfaces, `"127.0.0.1"` when a reverse proxy (Nginx, Caddy, Cloudflare Tunnel) sits in front.
  - Removed `items.local-texture-path` and `items.block-texture-source` in favor of `items.resource-pack.*`.

### 4. 100% Localization & Zero Hardcoded Messages
- **7 Supported Languages**: English, German, Spanish, French, Japanese, Polish, Russian with 100% key parity (1097 keys per `messages_*.yml`, 764 keys per `web/lang/*.json`).
- **Console & Logger Localization**: All logger, startup, shutdown, and error messages are fully translated via `messages.console.*`.
- **Embedded Fallback System**: Incomplete translation files fall back to embedded English defaults instead of failing or printing raw keys.
- **Automated Audit Suite (`tools/i18n_audit.py`)**: 0 findings across all 11 detection rules (D1–D11).

### 5. Streamlined Configuration & Debug Mode
- **Persistent Debug Switch**: `/eventpvp debug (on | on full | off | status)` saved to `settings.debug` in `config.yml`.
- **Decoupled World Management**: Replaced ambiguous `world-loading` with two independent booleans: `settings.world-management.events` and `settings.world-management.arenas`.
- **Switchable Lobby Phase (`use-lobby`)**: Events can run with or without a separate lobby phase.
- **Automatic Config Migration**: Existing configs are automatically updated on first startup without losing custom values or comments.

---

## Breaking Changes & Upgrades

| Area | Change |
|---|---|
| **Required Dependencies** | [Multiverse-Core](https://modrinth.com/project/3wmN97b8) (v4/v5), [VaultUnlocked](https://modrinth.com/project/ayRaM8J7) (or classic [Vault](https://www.spigotmc.org/resources/vault.34315/)), and [InventoryBackup](https://modrinth.com/project/rpKY25cW) are now mandatory in `plugin.yml`. |
| **Removed Command** | `/inventoryrestore` is removed. Use `/inv <player>` or the Web Panel instead. |
| **Removed Permissions** | `eventpvp.inventory.restore` and `eventpvp.inventory.restore.any` are removed. |
| **World Loading Config** | `settings.world-loading` is migrated to `settings.world-management.events` and `.arenas`. |
| **Inventory Config** | `settings.inventory-snapshots.*` is migrated to `settings.inventory-management.*`. `retain-days`, `default-group`, `groups`, and `ids.*` are dropped — retention is now InventoryBackup's job. |
| **Legacy Snapshot Files** | `inventory_backups.yml` and `inventory_post_backups.yml` are no longer read. They stay on disk untouched. |
| **Equipment Merge** | `equipment:`, `equipment-sets:`, and `equipment-groups:` are merged into a single `equipment:`. On an ID collision the `equipment:` entry wins; the other is kept as `<id>-legacy` and logged. |
| **Web Config** | `items.local-texture-path` and `items.block-texture-source` are replaced by `items.resource-pack.*`. |
| **Command Restrictions** | `settings.command-restriction` values `join` and `pvp` become `both`. |

---

## Command & Permission Quick Reference

### New & Updated Commands
| Command | Permission | Status | Description |
|---|---|---|---|
| `/eventpvp rescue <list\|player\|clean>` | `eventpvp.admin` | new | Inspect, rescue, and clean stranded player sessions |
| `/eventpvp debug <on\|on full\|off\|status>` | `eventpvp.debug` | updated | Toggle and inspect debug logging — the level is now persisted in `settings.debug` |
| `/eventpvp webtoken` | `eventpvp.admin.web` (or `eventpvp.admin`) | unchanged | Generate a secure one-time login token for the Web Panel |
| `/eventpvp reload` | `eventpvp.admin` | unchanged | Reload all configurations and language bundles |
| `/eventpvp version` | `eventpvp.admin` | unchanged | Check plugin version and update status |
| `/inv <player>` | `inventoryrestore.admin` | external | Inspect and restore inventory backups — provided by the InventoryBackup plugin, not by Event-PVP-Plugin |

### New Permissions
- `pvpwager.spectate.all`: Spectate matches that already reached the spectator limit (default: `op`).
- `pvpwager.nowager`: Start a match without any wager (default: `op`).

`eventpvp.admin.web`, `eventpvp.debug`, and `eventpvp.debug.receive` already existed in 1.0.8 and are unchanged.

### Removed Permissions
- `eventpvp.inventory.restore` and `eventpvp.inventory.restore.any` — remove them from your permission plugin.
