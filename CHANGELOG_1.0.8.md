# Event-PVP Plugin 1.0.8

## Summary

Version 1.0.8 focuses on four areas: full localization of the PvP wager GUI flow, optional leaderboard/hologram integrations (AJLeaderboards, DecentHolograms), PlaceholderAPI support so external plugins can read numeric player statistics, and optional PvPManager integration to prevent false combat logging penalties.

---

## Upgrading from 1.0.7

You **do not need to replace your entire config.yml**! The plugin will automatically add missing settings with default values.

### Important Notes
- **Config Files**: No manual config editing required! New options are automatically added to your existing config files with default values.
- **Web Interface**: After upgrading, all new features (including optional integrations) will appear in the web interface where you can easily enable/disable them.
- **Language Files**: These are NOT automatically overwritten! To get the new GUI localization keys, you need to update your language files.

### Step-by-Step Upgrade:

1. **Back up your existing files** (config.yml, worlds.yml, equipment.yml, messages_*.yml)
2. **Stop your server**
3. **Replace the plugin JAR file** with version 1.0.8
4. **Update Language Files** (IMPORTANT!):
   - Option 1: Delete your old `messages_*.yml` files and restart the server to regenerate them with all new keys. Then manually reapply any customizations.
   - Option 2: Manually merge the new `messages.gui.*` keys from the updated language files into your existing customized files.
5. **Start your server** - the plugin will handle the rest!
6. **Enable New Features** (optional):
   - Open the web interface
   - Navigate to the integrations section
   - Enable/disable the new features (PvPManager, AJLeaderboards, DecentHolograms) as desired
   - Save your changes

---

## Added

### PvPManager Integration (Combat Tagging)
- Added optional **PvPManager** support via a bridge interface
- Registered automatically on startup when PvPManager is installed and enabled in config
- Automatically removes combat tags when players:
  - Leave an event (voluntarily or eliminated)
  - Finish a PvP wager match (win/lose/draw)
  - Server shuts down during an active match
- Fallback to no-op implementation if PvPManager isn't installed or integration is disabled
- **Enable in `config.yml`:**
  ```yaml
  settings:
    integrations:
      pvpmanager:
        enabled: true
  ```

### PlaceholderAPI Integration
- Added optional **PlaceholderAPI** support via the `EventPvpExpansion` class
- Registered automatically on startup when PlaceholderAPI is installed
- All placeholders return **plain numeric strings** (e.g., "15", "0") — no color codes or formatting — so **AJLeaderboards** can parse them correctly
- **Available placeholders:**

| Placeholder | Description |
|-------------|-------------|
| `%eventpvp_event_wins%` | Total event wins for the player |
| `%eventpvp_event_participations%` | Total event participations for the player |
| `%eventpvp_pvp_wins%` | Total PvP wager wins |
| `%eventpvp_pvp_losses%` | Total PvP wager losses |
| `%eventpvp_pvp_draws%` | Total PvP wager draws |

> **Note:** Kills and deaths are not tracked by this plugin. Event stats only include wins and participations; PvP stats only include wins, losses, and draws.

### Optional AJLeaderboards & DecentHolograms Support
- Added optional integration bridges for **AJLeaderboards** and **DecentHolograms**
- Both integrations are **fully optional** — the plugin works without them
- **Enable in `config.yml` under `settings.integrations`:**
  ```yaml
  settings:
    integrations:
      ajleaderboards:
        enabled: true
      decentholograms:
        enabled: true
      refresh-interval-ticks: 20
  ```

### Extended GUI Localization (Wager System)
- Previously hardcoded GUI text in the PvP wager flow has been moved to language files under `messages.gui.*`
- This affects all wager-related screens:
  - Wager Main GUI (challenge setup, overview, arena/equipment selection)
  - Item Selection GUI (add/remove wager items, slot labels, error messages)
  - Money Selection GUI (amount picker, balance display, adjust buttons)
  - Confirmation GUI (final review before sending a challenge)
  - Response GUI (accept/decline incoming challenges, counter-wager setup)
  - Response Item/Money Selection GUIs (opponent-side wager editing)
  - Negotiation GUI (final offer review, counter-offer, accept/decline)
- **Updated language files:** messages.yml (default), messages_en.yml, messages_de.yml, messages_es.yml, messages_fr.yml, messages_pl.yml, messages_ru.yml, messages_ja.yml

### Improved Multiverse-Inventories Documentation
- Added detailed setup guide for Multiverse-Inventories world groups
- Included both guided command (`/mvinv group`) and direct command options
- Added wildcard/regex support tips for dynamic world names

---

## Changed
- Wager GUI classes (`AbstractWagerGui` and subclasses) now resolve all visible text through `messages.gui.*` keys instead of hardcoded strings
- External display refresh is scheduled automatically when AJLeaderboards and/or DecentHolograms integrations are enabled
- plugin.yml now includes `PvPManager` in `softdepend`
- pom.xml now includes PvPManager dependency (scope: provided) via CodeMC repository

---

## Goals
- Eliminate hardcoded GUI text so the entire wager UI respects the configured server language
- Provide consistent, translatable labels, lore text, error messages, and button titles across all wager dialogs
- Allow server owners to display event and PvP statistics on leaderboards and holograms without custom code
- Prevent false combat logging penalties by integrating with PvPManager
- Provide clear, step-by-step Multiverse-Inventories setup instructions

---

## How to Verify Changes In-Game

### Language Changes
1. **Prepare language files**: Language files in the plugin data folder are **not** automatically overwritten on update. To get the new `messages.gui.*` keys, either delete old language files (after backing up) or manually merge the new sections.
2. **Switch language** in config.yml (or via web interface) and run `/eventpvp reload`
3. **Test all GUI screens** using two test accounts (Player A challenges Player B)

### Integration Checks
- **PlaceholderAPI**: Run `/papi list` and verify `eventpvp` expansion is present, test with `/papi parse me %eventpvp_pvp_wins%`
- **PvPManager**: Check server logs for integration activation message, test by getting in combat during an event/match then leaving/finishing
- **AJLeaderboards/DecentHolograms**: Enable the integrations via web interface, then create a board/hologram using the placeholders and verify stats update

---

## Upgrade Notes
- **Language files:** Not auto-replaced on update. Delete or manually merge messages_*.yml in the plugin data folder to receive new GUI keys
- **PlaceholderAPI:** Install PlaceholderAPI on the server; the expansion registers automatically
- **AJLeaderboards / DecentHolograms:** Install the plugins, enable in config.yml, and use the %eventpvp_*% placeholders in board/hologram lines
- **PvPManager:** Install PvPManager, enable integration in config.yml — no extra setup needed
