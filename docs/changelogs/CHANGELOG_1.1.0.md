# Changelog 1.1.0-beta

## [1.1.0-beta] - 2026-08-13

This beta release brings the plugin onto the modern Purpur 26.2 / Java 21 toolchain and includes a broad cleanup of compatibility issues, chat rendering, localization, and stability fixes. It is primarily intended for testing on Purpur-based servers before a wider release.

> Beta builds are hidden from normal update notifications by default. The update checker respects the Modrinth `version_type` and the `stable-only: true` setting, so standard users do not see beta or alpha alerts unless they intentionally opt in.

### Highlights
- Updated for Purpur 26.2 and Java 21
- Replaced legacy BungeeCord chat handling with Adventure-based components
- Improved localization and console output in all supported languages
- Better web-panel and config reliability
- Reduced compatibility issues with newer Bukkit/Purpur APIs
- Increased stability around world handling, in-game actions, and startup behavior

### Platform & compatibility
- Updated the plugin to target Purpur 26.2 (`purpur-api:26.2.build.2618-stable`) and Java 21.
- Updated `plugin.yml` with the correct `api-version: '26.2'`.
- Cleaned the package setup to avoid classloader conflicts with server-provided libraries.
- Added the required Java 21 compatibility flags for Surefire/Mockito during tests.
- Removed legacy compatibility paths and replaced old API usage with modern equivalents where possible.

### Adventure migration and chat improvements
- Removed the remaining `net.md-5:bungeecord-chat` usage from the source tree.
- Reworked message translation through a central `Text` bridge that handles legacy color codes, hex colors, and Adventure `Component` output safely.
- Updated interactive chat messages to use modern clickable and hover actions instead of older wrappers.
- Added clickable buttons for common actions such as:
  - accept/decline wager requests
  - join event broadcasts
  - spectator invitations
  - skip and counter-offer interactions
  - web token copy actions and direct web-panel links
- Kept compatibility with older Bukkit-style APIs by preserving the existing string signatures while internally converting to Adventure where needed.

### Modern API fixes
- Updated title handling to use Adventure `showTitle(...)` instead of the legacy title API.
- Modernized enchantment resolution to prefer `Registry.ENCHANTMENT` with namespaced keys before falling back to legacy names.
- Migrated potion handling away from deprecated `PotionData` reflection to the direct `PotionMeta.setBasePotionType(...)` API.
- Switched TPS retrieval from reflection-based logic to native `Bukkit.getServer().getTPS()` calls.
- Improved movement-event handling by avoiding unnecessary processing when the block has not actually changed and by respecting cancelled events correctly.

### Stability and gameplay improvements
- Improved cooldown handling with safer synchronization in the command cooldown system.
- Reduced unnecessary event processing and world checks to lower overhead and avoid noisy side effects.
- Improved world-transition and safety checks for players moving between lobby, event, and arena areas.
- Better handling for player return/safe respawn scenarios after disconnects, crashes, or world changes.
- Fixed several situations where state could be left behind or where the wrong world/location was used during handler flow.

### Localization and console output
- Added and cleaned up localization keys for console/admin-facing output, including warnings and unsupported spawn-type messages.
- Console output now follows the plugin's i18n framework in all supported languages instead of relying on hardcoded strings.
- Added explicit `// i18n-ignore` markers where technical strings must remain unchanged for technical reasons.
- Added a local i18n audit toolchain to detect missing keys, placeholder mismatches, YAML issues, and hardcoded strings more reliably.
- Reports now use the local system timezone instead of UTC for clearer release and audit output.

### Web panel, config, and documentation
- Reorganized docs into a cleaner structure under `docs/`.
- Added and improved server compatibility guidance for Purpur, Paper, Folia, Spigot, Java 21, and version expectations.
- Added a more structured release and migration documentation set for easier upgrades.
- Improved the project setup and maintenance scripts, including Git hygiene and repository newline handling.
- Added release guidance for beta/alpha distribution without triggering normal stable update notifications.

### Quality and verification
- Expanded automated test coverage substantially compared to the previous baseline.
- Added validation for message parsing, color conversion, tooltip/chat actions, enchantment resolution, config loading, and world/path validation.
- Added a Python-based audit suite covering parity, missing keys, hardcoded messages, placeholder consistency, YAML syntax, and more.
- Verified the plugin loads cleanly on Purpur 26.2, with the expected startup behavior and no major runtime exceptions during the validation pass.

### Upgrade notes
- This is a beta release and should be tested on a staging server before using it on a production world.
- Make sure to back up your plugin config and world-related data before switching to Purpur 26.2 / Java 21.
- Update-check behavior is now more conservative for beta/alpha versions by default; stable users will not get noisy prerelease alerts unless they enable them.
- If you use custom language files, check for newly added console or message keys after updating.

