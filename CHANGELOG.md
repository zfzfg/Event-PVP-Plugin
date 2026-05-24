# Changelog

## [1.0.7] - 2026-05-24

### Changed
- **Code Refactoring**: Removed the wrapper class `de.zfzfg.pvpwager.utils.MultiverseHelper.java`
- The Multiverse functionality now uses the central `de.zfzfg.core.world.MultiverseHelper` class directly
- This change simplifies the codebase by removing an unnecessary abstraction layer

### Added
- **Version Check Command**: Added `/epvp version` and `/eventpvp version` commands
  - Requires `eventpvp.admin` permission (same as reload/debug)
  - Displays current plugin version
  - Triggers update check and shows update status
  - Shows download link if update is available
  - Added to help menu and tab completion
- **Command Cooldown System**: Integrated `CommandCooldownManager` into the main plugin
  - Applied to `/pvp challenge` command with 3-second default cooldown
  - Includes memory leak prevention with automatic player cleanup on quit
  - Customizable cooldown messages via message provider
- **Web Server Rate Limiting**: Added rate limiting to the embedded HTTP server
  - Prevents API abuse with per-IP rate counters (100 requests per window)
  - **Note**: Rate limit counters are not automatically cleared - may require future cleanup mechanism
  - Improves security and stability of the web interface
- **Text Utility Performance**: Added color caching to `TextUtil`
  - Caches colored text to improve performance
  - Reduces redundant color code processing
  - **Note**: Cache has no size limit - may require monitoring for memory usage with many unique messages
- **Improved Task Management**: Enhanced countdown task handling in `MatchManager`
  - Changed from single task to task list for better management
  - Allows multiple concurrent countdown operations
- **Memory Management**: Added player cleanup method to prevent memory leaks
  - `CommandCooldownManager.removePlayer()` called on player quit
  - Ensures proper cleanup of player-specific data
- **Update Reminder System**: Added automatic update checking via Modrinth API
  - Checks for newer plugin versions on server startup with configurable delay
  - Notifies admins with `eventpvp.admin.updatenotify` permission on join
  - Console logging when updates are available
  - Caching mechanism to prevent repeated API calls
  - Configurable via `settings.update-check` in config.yml
  - Supports all languages: German, English, Spanish, French, Japanese, Polish, Russian
  - Includes proper User-Agent header for Modrinth API compliance
  - SemVer-based version comparison
  - Uses Modrinth project ID `pqJQdZ6R`

### Fixed
- **Missing Help Message Keys**: Fixed YAML key collision causing help messages to be missing
  - Root cause: Multiple duplicate `help:` sections in YAML files caused key collision (YAML only keeps last instance)
  - Renamed sections to `event-help:`, `pvp-help:`, and `command-help:` in all language files
  - Updated `EventCommand.java` to use `event-help.` prefix for all help message lookups
  - Fixed `/event help` command showing raw message keys instead of actual text
  - Applied to all languages: German, English, Spanish, French, Japanese, Polish, and Russian
- **Incorrect Command Documentation**: Updated PvP help to show correct command syntax
  - Changed `/pvp <spieler>` to `/pvpask <spieler>` in all language files
  - Updated `help.challenge` entries to reference the correct command with Wager-GUI hint
  - Applied to all languages with appropriate translations
- **Win/Loss Screen Placeholders**: Fixed YAML key collision causing win/loss messages to show as placeholders
  - Root cause: Multiple duplicate `match:` sections in YAML files caused key collision
  - Renamed sections to `match-display:`, `match-system:`, and `match-manager:` in all language files
  - Updated `MatchManager.java` `getMsg()` method to route keys to correct YAML section based on key name
  - Fixed `you-won-header`, `you-won`, `you-lost-header`, and `you-lost` keys not being found
  - Applied to all language files
- **Draw Vote Expiration Bug**: Fixed "expired" message showing after draw is accepted
  - Added `match.isDrawVoteActive()` check in `DrawSubCommand.java` timeout task
  - Prevents showing expired message when draw vote has already been accepted and deactivated
  - Resolves race condition where timeout task fires after draw acceptance
- **Update Checker Version Parsing**: Fixed version comparison failing with non-numeric suffixes
  - Updated `UpdateChecker.java` to extract numeric part before parsing (e.g., "1.0.6-Multilingual" -> "1.0.6")
  - Prevents `NumberFormatException` when comparing versions with suffixes like "-Multilingual"
- **Error Handling Improvements**: Added logging to all previously empty catch blocks
  - `MatchManager`: Added warning logs for task cancellation failures, snapshot save errors, and statistics recording failures
  - `EventSession`: Added warning logs for task cleanup failures, inventory snapshot errors, and health/food level setting errors
  - `EquipmentManager`: Added warning logs for allowed worlds parsing errors
  - `PvpStatsStorage`: Added warning logs for UUID parsing errors
  - `SpawnManager`: Added warning logs for teleport fallback failures
  - `RequestManager` & `CommandRequestManager`: Added warning logs for task cancellation errors
  - `InventorySnapshotStorage`: Added comment for regex error handling (expected fallback behavior)
- **Enum Switch Completeness**: Added missing `COMMAND` case to spawn type switch in `EventSession`
  - Resolves compiler warning about unhandled enum constant
  - Delegates to `executeSpawnCommand()` method for command-based spawning
- **Material Caching**: Added material name cache to `EquipmentManager`
  - Caches `Material.valueOf()` lookups to avoid repeated parsing
  - Cache is cleared on equipment reload to ensure consistency
- **Enchantment Caching**: Added enchantment cache to `EquipmentManager`
  - Caches `Registry.ENCHANTMENT.get()` lookups for enchantment names
  - Cache is cleared on equipment reload to ensure consistency
- **HashMap Cloning Reduction**: Replaced defensive copies with unmodifiable views
  - `MatchManager.getMatches()` now returns `Collections.unmodifiableMap()`
  - `StatsManager.toMap()` now returns `Collections.unmodifiableMap()`
  - Reduces memory allocation for admin/debug commands

### Deprecated
- **PvPAcceptCommand**: Marked as deprecated
  - Users should use `/pvp accept` instead
  - Old command kept for backward compatibility

### Technical Details
- The old wrapper class at `src/main/java/de/zfzfg/pvpwager/utils/MultiverseHelper.java` has been removed
- Code that previously used the wrapper now imports and uses `de.zfzfg.core.world.MultiverseHelper` directly
- This refactoring improves code maintainability and reduces complexity
- Various code cleanups and optimizations across multiple files
- Improved command registration with helper method in `EventPlugin`

### Compatibility
- No breaking changes for end users
- All existing functionality remains intact
- Plugin configuration and commands work exactly as before
- Deprecated commands continue to function but users should migrate to new syntax

### Upgrade Notes
- **Language Files**: Language files are NOT automatically replaced when updating the plugin. If you have customized language files (`messages_*.yml`), you need to delete them from the plugin data folder and restart the server to let the plugin regenerate them with the new update notification messages, then manually reapply your customizations.

## [1.0.6-Multilingual] - Previous Version
- Multilingual support for German, English, Spanish, French, Japanese, Polish, and Russian
- Event and PvP wager system integration
- Web interface for configuration
- Multiverse-Core integration for world management
