# Event-PVP-Plugin - Quick Description

A comprehensive Minecraft plugin that combines **custom event management** and **PvP wager system** into one unified solution.

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
1. Install Multiverse-Core
2. Install Multiverse-Inventories
3. Configure world groups in Multiverse-Inventories:
   - `default` group: Main/survival worlds
   - `event` group: Event worlds
   - `pvp` group: PvP arena worlds

### Other Dependencies
- **Vault** (Required): For economy integration (money wagers)

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
   ```
   /mvinv group default
   /mvinv group event
   /mvinv group pvp
   ```

3. **Assign Worlds to Groups:**
   ```
   /mvinv world <world_name> event
   /mvinv world <arena_name> pvp
   ```

4. **Configure the Plugin:**
   - Edit `config.yml`, `worlds.yml`, `equipment.yml`
   - Or use the web interface: `/eventpvp webtoken`

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
