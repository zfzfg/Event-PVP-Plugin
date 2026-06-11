# Event-PVP-Plugin - Quick Description

A comprehensive Minecraft plugin that combines **custom event management** and **PvP wager system** into one unified solution.

## Upgrading from 1.0.7 to 1.0.8

You **do not need to replace your entire config.yml**! The plugin will automatically add missing settings with default values. However, to use new features, you should:

1. **Back up your existing files** (config.yml, worlds.yml, equipment.yml, messages_*.yml)
2. **Stop your server**
3. **Replace the plugin JAR file** with version 1.0.8
4. **Optionally update your config.yml** to enable new features:
   - Add PvPManager integration (see "Optional Integrations" below)
   - Ensure your integrations section has all settings
5. **Start your server** - the plugin will handle the rest!

### If you want to use the new web interface features:
No changes needed! The web interface will automatically support all new config options.

**🌍 Multilingual Support: Available in 8 Languages** (English, German, French, Spanish, Russian, Polish, Japanese, Chinese)

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
- Edit all YAML configurations visually
- Create events and equipment sets with live preview
- Token-based secure access
- **Multi-language support: 8 languages** (EN, DE, FR, ES, RU, PL, JA, ZH)

## Important Dependencies

### ⚠️ Multiverse-Core (REQUIRED)
**This plugin fundamentally requires Multiverse-Core** for proper operation:
- **World Loading**: Loads event/arena worlds when matches start
- **World Cloning**: Creates temporary world copies from templates
- **World Regeneration**: Resets worlds after events/matches
- **World Unloading**: Unloads worlds to save server resources

**Without Multiverse-Core**, the plugin will have limited functionality and may not work correctly.

### ⚠️ Multiverse-Inventories (STRONGLY RECOMMENDED)
**Multiverse-Inventories is essential for proper inventory restoration**. Here's why:

**The Problem:**
- When players join an event or PvP match, they are teleported to a different world
- Their inventory is cleared and replaced with event/match equipment
- When returning to the main world, they need their original inventory back

**The Solution:**
Multiverse-Inventories handles this automatically:
- **Separate Inventories**: Each world group (main, event, pvp) has its own inventory
- **Automatic Switching**: When players change worlds, their inventory is automatically restored
- **No Data Loss**: Player inventories are preserved by Multiverse-Inventories

**Without Multiverse-Inventories:**
- The plugin has a fallback inventory backup system, but it's still **experimental and buggy**
- Players may lose items or experience inventory issues
- Inventory restoration is not guaranteed to work correctly

**Recommended Setup:**

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

### Optional Integrations

The plugin optionally supports several external plugins to enhance functionality. None of these are required — the plugin works fully without them.

#### Leaderboards & Holograms
Supports **AJLeaderboards**, **DecentHolograms**, and **PlaceholderAPI** for displaying event and PvP statistics.

**Enable in `config.yml`:**
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

**Enable in `config.yml`:**
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
/event <name> start         - Start an event
/event <name> stop          - Stop an event
/event <name> forcestart    - Force start without minimum players
/pvpadmin reload            - Reload PvP configuration
/pvpadmin stopall           - Stop all active matches
/inventoryrestore <player> <ID>  - Restore player inventory (fallback)
```

## Quick Start

1. **Install Dependencies:**
   - Vault
   - Multiverse-Core
   - Multiverse-Inventories

2. **Configure World Groups** (in Multiverse-Inventories):
   Use `/mvinv group` for guided setup, or direct commands:
   ```
   /mvinv create-group default,world,world_nether,world_the_end all
   /mvinv create-group event,<your_event_world> all
   /mvinv create-group pvp,<your_pvp_arena_world> all
   ```

3. **Verify World Groups:**
   ```
   /mvinv list
   /mvinv info event
   ```

4. **Configure the Plugin:**
   - Edit `config.yml`, `worlds.yml`, `equipment.yml`
   - Or use the web interface: `/eventpvp webtoken`
   - Enable optional integrations in `config.yml` (AJLeaderboards, DecentHolograms, PvPManager)

5. **Reload and Test:**
   ```
   /eventpvp reload
   /event pvparena start
   ```

## Server Requirements

- **Platform**: Paper/Spigot 1.19+
- **RAM**: 4GB+ recommended (depending on world size)
- **Multiverse-Core**: Latest version
- **Multiverse-Inventories**: Latest version (strongly recommended)
- **Vault**: Latest version

## Need Help?

- Check the full `README.md` for detailed documentation
- Review configuration examples in `CONFIG_EXAMPLES.md`
- Test spawn configurations using `SpawnExamples.md`

---

**Remember**: Multiverse-Core and Multiverse-Inventories are not just optional - they are **essential** for this plugin to work properly!
