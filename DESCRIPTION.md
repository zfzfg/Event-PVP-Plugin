# Event-PVP-Plugin - Quick Description

A comprehensive Minecraft plugin that combines **custom event management** and **PvP wager system** into one unified solution.

## Upgrading to 1.0.9

You **do not need to replace your entire config.yml**! The plugin automatically adds missing settings with default values. Here is the step-by-step guide:

### Important Notes in 1.0.9
- **True i18n Localization for Console & Terminal Output**: All logger, startup/shutdown, and server console messages now respect `settings.language` in all 7 languages via `messages.console.*`.
- **Automatic Fallbacks**: Missing keys in custom or partial translation files now automatically display English text via embedded resource defaults instead of failing or printing raw keys.
- **Web Interface Live Server Sync Badge**: Real-time status indicators in the top bar show whether your configuration changes are synchronized with the server (`🟢 Synced`, `🟡 Unsaved Changes`, `🔵 Saving`, `🔴 Out of Sync`).
- **Switchable Lobby Phase (`use-lobby`)**: Events can now run with or without a lobby phase. When disabled, players join the event world immediately without needing a separate lobby world.
- **World Regeneration Safeguards**: Redundant double-regenerations are automatically prevented, and `regenerate-event-world` is locked when the world is already set to regenerate globally in `worlds.yml`.
- **Zero Hardcoded Messages**: Complete localization audit pass (0 findings across all 9 rules D1–D9).

### Step-by-Step Upgrade
1. **Back up your existing files** (`config.yml`, `worlds.yml`, `equipment.yml`, `messages_*.yml`)
2. **Stop your server**
3. **Replace the plugin JAR file** with version 1.0.9
4. **Update Language Files**:
   - Option 1 (Recommended): Delete older `messages_*.yml` files to let the plugin generate the new bundles with all 1097 keys and console localization.
   - Option 2: Keep your customizations and benefit from the automatic embedded fallback system for new keys.
5. **Start your server** - the plugin will handle the rest!

**🌍 Multilingual Support: Available in 7 Core Languages** (English, German, Spanish, French, Japanese, Polish, Russian) with 100% key parity and full web interface translation.

## What Does This Plugin Do?

### Event System
Host custom server events with:
- Multiple event types (PvP Arena, Capture the Flag, Free-for-All, etc.)
- Automated event scheduling
- Configurable spawn systems (single point, random, team-based)
- Custom rewards for winners and participants
- Statistics tracking and leaderboards

### PvP Wager System
Let players bet on PvP matches:
- Challenge other players with item or money wagers
- Multiple arena configurations
- Pre-configured equipment sets
- Spectator mode for watching matches
- Match management (draw votes, surrender, timeouts)

### Web Interface
Configure everything through a modern browser interface:
- Edit all YAML configurations visually (`config.yml`, `worlds.yml`, `equipment.yml`, `web-config.yml`) with deep-snapshot precision dirty-tracking
- Create events and equipment sets with live preview and server material validation
- **Dedicated 3-Tab Inventory Manager**: Search backups, inspect with authentic Minecraft Canvas & XP bar, 2-step restores, live session/guard monitoring
- **Multiverse World Management**: Create, import, load, unload, delete, and backup/restore worlds directly in the browser
- **Server Resource Pack Textures**: Automatically extracts and displays custom item textures from your server resource pack
- **Web Server Settings**: Configurable port with pre-save safety warning dialog and public URL with automatic dynamic port binding for `/eventpvp webtoken`
- Token-based secure access (`/eventpvp webtoken`) with rate-limiting protection
- **Multi-language support: 7 languages** (EN, DE, FR, ES, RU, PL, JA) with real-time synchronization badges (`🟢 Synced`, `🟡 Unsaved`, `🔵 Saving`, `🔴 Out of Sync`)

## Important Dependencies

### ⚠️ InventoryBackup / InventoryRestore (REQUIRED)
**This plugin requires the plugin `InventoryBackup`** (from the InventoryRestore project) and
will not start without it — it is listed under `depend` in `plugin.yml`, exactly like Vault.

It is the storage behind all inventory safety: the backup taken before a player is teleported
into an arena or lobby, the automatic restore afterwards, and the persistent queue that returns
an inventory to a player who disconnected mid-match. Requires Paper/Spigot **1.20+**.

If the plugin is present but its API is not registered (for example because it disabled itself
on startup), Event-PVP-Plugin **refuses to start matches and events** rather than clearing a
player's inventory with nothing to restore it from.

### ⚠️ Multiverse-Core (REQUIRED)
**This plugin requires Multiverse-Core** for proper operation and will not start without it — it is listed under `depend` in `plugin.yml`:
- **World Loading**: Loads event/arena worlds when matches start
- **World Cloning**: Creates temporary world copies from templates
- **World Regeneration**: Resets worlds after events/matches
- **World Unloading**: Unloads worlds to save server resources

Both Multiverse-Core 4 and 5 are supported. Without Multiverse-Core, the plugin will refuse to start.

### ✅ Inventory Management (no Multiverse-Inventories required)

**The Problem:**
- When players join an event or PvP match, they are teleported to a different world
- Their inventory is cleared and replaced with event/match equipment
- When returning to the main world, they need their original inventory back

**The Solution (default since 1.0.9):**
The plugin manages this itself. Before a player is teleported into an arena or lobby — while
they are still standing in their original world — their inventory is backed up, and it is
restored automatically afterwards:

- **After a match or event ends**, including draws and cancellations
- **After a death** in an arena, on respawn
- **After a disconnect**: the restore is queued and applied on the player's next login. The
  queue is persistent, so it survives a server restart.
- **After a crash**: open sessions are written to `inventory-guard.yml` and worked off on the
  next server start. A crash costs at most one login, never an inventory.

Winnings and rewards are handed over *after* the restore, never before — otherwise the restore
would wipe them in the same tick.

**Inventory Storage Backend:**
The plugin uses `InventoryBackup` as its required storage backend: one file per backup under
the player's UUID, an interactive preview GUI, a persistent join queue, and crash journal recovery.

**Modes** (`settings.inventory-management.provider`, also switchable in the web panel):

| Mode | Behaviour |
|---|---|
| `auto` *(default)* | Backup and automatic restore through InventoryBackup |
| `inventoryrestore` | Same as `auto`, written out explicitly |
| `none` | **Legacy**: Multiverse-Inventories handles the swap; the plugin only writes safety backups (see below) |

### ⚠️ Legacy mode: Multiverse-Inventories (`provider: none`)

Only needed if you deliberately want Multiverse-Inventories to keep handling the inventory
swap. What the plugin still does in this mode:

- **It writes safety backups** through InventoryBackup, taken before every arena and lobby
  teleport — while the player is still in their original world, so the copy holds the real
  survival inventory. Tagged `mode=legacy-safety`.
- **It never restores anything by itself.** No auto-restore, no join queue, no crash recovery.
  The copies are purely a net: if Multiverse-Inventories loses an inventory, an admin pulls it
  back by hand from the web panel or with `/inv <player>`.

Turn the copies off with `settings.inventory-management.legacy-safety-backups: false` — then
legacy mode has no net of its own at all.

**Do not run both at once.** Multiverse-Inventories swapping on world change while the plugin
restores afterwards gives results that depend on timing. Either dissolve the world groups, or
set `provider: none`. The plugin warns on startup and in the web panel if it detects both.

**Without Multiverse-Inventories in legacy mode:**
- Players lose their survival inventory when they enter an arena or event world
- Nothing is restored automatically

**Legacy Setup:**

#### Step 1: Install Plugins
1. Install Multiverse-Core
2. Install Multiverse-Inventories

#### Step 2: Configure World Groups (Command Line)
You can use the guided command `/mvinv group` or direct commands to set up your groups.

Option A: Guided Setup
1. Run `/mvinv group`
2. Select "create"
3. Name your group (e.g., "event")
4. Add your event world names (e.g., "event_world", "pvp_arena")
5. Type "@" to continue
6. Select shares (type "all" to share everything within the group)
7. Type "@" to finish

Option B: Direct Commands
For advanced users, you can use direct commands:
- Create group: `/mvinv create-group default,world,world_nether,world_the_end all`
- Add worlds to group: `/mvinv add-worlds event,event_world`
- Add more shares: `/mvinv add-share event all`

**Recommended World Groups:**
- `default` group: Main/survival worlds (e.g., `world`, `world_nether`, `world_the_end`)
- `event` group: Event worlds (e.g., your event world names)
- `pvp` group: PvP arena worlds (e.g., your arena world names)

#### Step 3: Verify Setup
Run `/mvinv list` to see your groups and `/mvinv info <groupname>` to confirm worlds are assigned correctly.

**Wildcard Support (Optional):**
If you use dynamic world names (e.g., event worlds that are cloned/regenerated), you can use wildcards:
- Example: `event_*` matches any world starting with "event_"
- Example: `r=arena_[0-9]+` (regex) matches numbered arena worlds

### Other Dependencies
- **Vault** (Required): For economy integration (money wagers)
- **InventoryBackup** (Required): Inventory backup and restore, see above

### Optional Integrations

The plugin optionally supports several external plugins to enhance functionality. None of these are required — the plugin works fully without them.

#### Leaderboards & Holograms
Supports **AJLeaderboards**, **DecentHolograms**, and **PlaceholderAPI** for displaying event and PvP statistics.

**Enable via web interface** (or manually in `config.yml`):
```yaml
settings:
  integrations:
    ajleaderboards:
      enabled: true
    decentholograms:
      enabled: true
```

**PlaceholderAPI placeholders** (use these in AJLeaderboards boards or DecentHologram lines):

| Placeholder | Stat |
|-------------|------|
| `%eventpvp_event_wins%` | Event wins |
| `%eventpvp_event_participations%` | Event participations |
| `%eventpvp_pvp_wins%` | PvP wager wins |
| `%eventpvp_pvp_losses%` | PvP wager losses |
| `%eventpvp_pvp_draws%` | PvP wager draws |

All placeholders return plain numbers (e.g. `15`, `0`) for compatibility with AJLeaderboards. Requires **PlaceholderAPI** to be installed on the server.

#### PvPManager (Combat Tagging)
Supports **PvPManager** to automatically remove combat tags when players leave an event or match, preventing false combat logging penalties.

**Enable via web interface** (or manually in `config.yml`):
```yaml
settings:
  integrations:
    pvpmanager:
      enabled: true
```

Requires **PvPManager** to be installed on the server. On plugin startup, you'll see a message confirming integration is active. Without PvPManager or with this setting disabled, the plugin falls back to a no-op implementation (no errors).

## Essential Commands

### For Players

**Events:**
```
/event <name> join          - Join an event
/event <name> leave         - Leave an event
/event list                 - Show available events
/eventstats me              - View your event statistics
```

**PvP Wagers:**
```
/pvpask <player>                                      - Challenge a player (GUI)
/pvpa <player> <wager> <amount> <arena> <equipment>  - Challenge with full setup
/pvpaccept [player]                                   - Accept a challenge
/pvpdeny [player]                                     - Deny a challenge
/surrender                                            - Surrender current match
/draw                                                 - Vote for a draw
/pvp spectate <player>                                - Spectate a match
/pvpstats me                                          - View your PvP statistics
```

**Examples:**
```
/pvpa Steve DIAMOND_SWORD 1 PvPArena diamond_pvp    - Challenge Steve with item wager
/pvpa Alex MONEY 100 desert standard                - Challenge Alex with $100 wager
/pvpa Mike SKIP 0 forest pvp_starter                - Challenge Mike without wager
```

### For Admins

```
/eventpvp reload            - Reload all configurations
/eventpvp webtoken          - Generate web interface access token
/eventpvp version           - Check plugin version and update status
/eventpvp debug             - Toggle debug logging (on|on full|off|status)
/eventpvp rescue            - Manage stuck sessions and stranded player return locations
/event <name> start         - Start an event
/event <name> stop          - Stop an event
/event <name> forcestart    - Force start without minimum players
/pvpadmin reload            - Reload PvP configuration
/pvpadmin stopall           - Stop all active matches
/inv <player>               - Inspect and restore player backups via InventoryBackup
```

### Key Permissions

| Permission | Description | Default |
|---|---|---|
| `eventpvp.admin` | Full administrative control | OP |
| `eventpvp.admin.web` | Generate web tokens & access Web Panel | OP |
| `eventpvp.debug` | Toggle debug mode (`/eventpvp debug`) | OP |
| `eventpvp.debug.receive` | Receive real-time debug stream in chat | OP |
| `pvpwager.spectate.all` | Spectate matches even if limit is reached | OP |
| `pvpwager.nowager` | Start matches without wagers | OP |
| `eventpvp.opbypass` | Bypass in-event command restrictions | OP |

## Quick Start

1. **Install Dependencies:**
   - Multiverse-Core (Required)
   - Vault (Required)
   - InventoryBackup from InventoryRestore (Required)

2. **Inventories need no further setup.** The plugin backs them up and restores them automatically via `InventoryBackup`.
   Only if you deliberately want the legacy behaviour, set
   `settings.inventory-management.provider: none` and configure world groups in
   Multiverse-Inventories — do not run both at the same time:
   ```
   /mvinv create-group default,world,world_nether,world_the_end all
   /mvinv create-group event,<your_event_world> all
   /mvinv create-group pvp,<your_pvp_arena_world> all
   ```

3. **Verify World Groups (Legacy only):**
   ```
   /mvinv list
   /mvinv info event
   ```

4. **Configure the Plugin:**
   - Edit `config.yml`, `worlds.yml`, `equipment.yml`
   - Or use the web interface: `/eventpvp webtoken`
   - Enable optional integrations via web interface (AJLeaderboards, DecentHolograms, PvPManager)

5. **Reload and Test:**
   ```
   /eventpvp reload
   /event pvparena start
   ```

## Server Requirements

- **Platform**: Paper/Spigot 1.19+ (InventoryBackup plugin requires 1.20+)
- **RAM**: 4GB+ recommended (depending on world size)
- **Required Plugins**: Multiverse-Core (v4 or v5), Vault, InventoryBackup (from InventoryRestore)
- **Optional / Soft Integrations**: PlaceholderAPI, AJLeaderboards, DecentHolograms, PvPManager

## Need Help?

- Check the full `README.md` for detailed documentation
- Review configuration examples in `CONFIG_EXAMPLES.md` (or `CONFIG_EXAMPLES_EN.md`)
- Test spawn configurations using `SpawnExamples.md`

---

**Remember**: Multiverse-Core, Vault, and InventoryBackup are required dependencies. The plugin will not start without them!
