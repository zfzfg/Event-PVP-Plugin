# Changelog

## [1.1.0] - 2026-08-18

- **Full Dual-Platform Compatibility (Purpur 26.2 & Spigot 26.2)**: Added dynamic runtime platform detection (`Platform.java`), Kyori Adventure 5.2.0 shading with safe namespace relocation, cross-platform Component/String bridges (`TextUtil`, `GuiUtil`, `ItemUtil`, `TeleportUtil`), and isolated Paper Registry helpers (`PaperRegistryHelper`).
- **Complete GUI Modernization**: Retired and archived 16 legacy GUI classes to `old-files/`, created `LiveTradeBridge` to connect wager command requests directly into the interactive `LiveTradeSession`, and modernized `PvPRespondCommand`.
- **Adventure Text & True RGB**: Complete migration of in-game messages, hover tooltips, clickable actions, and countdown/victory titles to Adventure 5.2.0.
- **Java 21 LTS & Clean Build**: 100% clean compilation, zero compiler warnings, and 182 automated unit and integration tests passing.

## [1.0.9] - 2026-08-08

- **Inventory Management powered by InventoryBackup (Required Dependency)**: Replaced legacy snapshot storage with `InventoryBackup` (`InventoryBackup.jar` from the InventoryRestore project). Both `InventoryBackup` and `Multiverse-Core` are now hard dependencies (`depend:` in `plugin.yml`). Automatic backups are taken before match/event teleports and restored on match/event end, respawn, or rejoin.

- **The safety net now covers position, not just inventory.** The plugin went to considerable lengths to protect a player's items across a crash — a synchronously written `inventory-guard.yml`, exactly-once restore doors, a join safety net — while the *return position* lived only in RAM (`EventManager.globalSavedLocations`, `Match.originalLocations`). A crash therefore gave the player their items back and left them standing in the event world with no way home.
  - **New `player-return-locations.yml`** (`ReturnLocationStore`), written synchronously like the guard journal, with the same invariants: never overwrite an existing entry (a second position would already be inside the event world), consume only *after* a successful return, and never clean up on shutdown. Deliberately a separate file rather than a field on the guard entry — in legacy mode (`provider: none`) no guard entry is created, but players are still teleported.
  - **New: stranded players free themselves.** `StrandedPlayerListener` runs 20 ticks after join (after the inventory net at 10): standing in an event/lobby/arena world with no running session now means an automatic return to the stored position. Previously nothing handled this case — `SpectatorRecoveryListener` only covered `GameMode.SPECTATOR`, so a survival player who crashed out mid-event simply stayed there.
  - **Fixed: four separate "find a safe location" chains** with diverging priorities in `VoidProtectionListener`, `MultiverseHelper`, `EventListener` and `SpectatorRecoveryListener` — the last of which jumped straight to the main world spawn without even trying the saved position. All four now use one `SafeLocationResolver` (stored position → match origin → bed → main spawn → first loaded world), which also carries the teleport-verification pattern that previously existed only in the respawn path.
  - **Fixed: unloading a world could strand a player.** `MultiverseHelper` swallowed every teleport failure with `catch (Exception ignored)` and unloaded regardless. Failures are now logged per player, and **the unload is aborted** if anyone could not be moved out — a world loaded too long is harmless, a player inside an unloaded one is not.
  - **Fixed: stuck state was invisible.** `InventoryGuard.countStale()` had no callers at all. Open sessions and return locations older than 24 h are now reported on startup and every 6 h thereafter.
  - **New command `/eventpvp rescue`** (`list` / `<player>` / `clean`, permission `eventpvp.admin`). This replaces `/pvp invdebug`, which **was named in 7 translated messages and a Javadoc but never existed** — admins were being sent to a command that was not registered. `clean` only discards entries without a backup; entries with one are kept, since discarding those would be the actual data loss.
  - **New `pending-payouts.yml`** (`PendingPayoutStore`, `PendingPayoutListener`): offline payouts for wager winnings and event rewards are persisted synchronously when a player disconnects before rewards can be delivered, and disbursed immediately upon reconnect.
  - The event module now queues the inventory restore on quit as well (`queueForJoin`), matching what the PvP module already did — both paths now have the same two layers.
  - The web panel's guard endpoint additionally reports stored return locations.

- **Web Server, UI & Texture Integration**:
  - **Web Server `bind-address` & Public URL**: Added `server.bind-address` to `web-config.yml` (default `""`, or `"127.0.0.1"` for reverse proxies like Nginx/Caddy). Added configurable Public URL in Web Server Settings with transparent `:{port}` suffix formatting in `web-config.yml` for dynamic in-game `/eventpvp webtoken` URL resolution.
  - **Web Server Port Change Warning**: Added pre-save safety confirmation dialog preventing accidental disconnection when modifying the web server port. Removed obsolete static title field in favor of dynamic i18n localization across all 7 supported languages.
  - **Precision Dirty-State & Change Tracking**: Replaced naive action counters with `CONFIG_BASELINE` deep snapshots and recursive equality comparisons. Eliminated false-positive dirty indicators on no-op edits, reverted values, and unmodified modal saves. Decoupled immediate server operations (language switch, inventory provider toggle, Multiverse actions, backups, reloads) and implemented selective category saves for `config.yml`, `worlds.yml`, `equipment.yml`, and `web-config.yml`.
  - **Server Resource Pack Item Textures**: Added `items.resource-pack.enabled` and `max-size-mb: 50` to `web-config.yml`. `ResourcePackTextureService` automatically downloads and unpacks textures from the server's `server.properties` resource-pack at startup, providing true in-game item textures in the web editor. Overrides are exposed via `GET /api/textures/overrides`.
  - **Dynamic Material Catalog (`GET /api/materials`)**: Dynamic item and enchantment catalog powered by `MaterialCatalog`, ensuring the web kit editor only allows valid items, stack sizes, and enchantments for the running server version.
  - **Dedicated 3-Tab Inventory Management Web UI**: Extracted from expert settings into a dedicated primary sidebar section (`#section-inventories`) with Backup Explorer (search, Minecraft Canvas inventory renderer with XP bar, 2-stage restore modal, kit export, raw JSON), Live Sessions & Guard Journal, and Engine Settings & Policies.
  - **Web Restore Rate Limiter**: Added `SlidingWindowLimiter` (10 restores/minute globally) on `POST /api/inventories/restore` to protect against token hijacking and economy duplication exploits (returns HTTP 429 when exceeded).

- **New Commands & Permissions**:
  - **Commands**: `/eventpvp rescue list|<player>|clean`, `/eventpvp webtoken` (one-time web login token), `/eventpvp debug (on|on full|off|status)`.
  - **Permissions**: `pvpwager.spectate.all` (spectate full matches), `pvpwager.nowager` (start friendly matches without wagers), `eventpvp.admin.web` (generate web tokens and access web interface), `eventpvp.debug`, `eventpvp.debug.receive`.
  - **Removed**: `/inventoryrestore` command and permissions `eventpvp.inventory.restore`, `eventpvp.inventory.restore.any`.

- **Update Checker**: Added `settings.update-check.stable-only` (default `true`) and `settings.update-check.contact` in `config.yml`.

- **World management & command blocking: the settings now actually do something.** Both dropdowns in the web panel's *World management* card were decorative. `ConfigManager` validated them against whitelists that matched neither the UI nor the consuming code — `{"join", "lobby", "both"}` for `command-restriction` (UI offered `both, event, pvp, none`) and `{"both", "clone", "load"}` for `world-loading` (UI offered `both, event, lobby, arena, none`). `validateEnum` reset every unlisted value to `both`, so **every option except "Both" was silently discarded**. The permitted values `join`, `clone` and `load` were understood by no consumer at all.
  - **BREAKING — `settings.world-loading` is replaced by two independent switches**: `settings.world-management.events` (load lobby/event worlds and unload them afterwards) and `settings.world-management.arenas` (unload arena worlds after a match). Existing configs are **migrated automatically on first start** and the old key is removed; `command-restriction: join` / `pvp` become `both`, which is how they already behaved.
  - **The old key mixed two unrelated questions.** `lobby`/`event` decided *which* world an event loads — that belongs to the event and is already answered by its `use-lobby` flag. `both`/`none` answered the global question of whether the plugin may manage worlds at all. Only the latter survives; the "Lobby only" option was broken by construction anyway, since it loaded the lobby but never the event world.
  - **One key, two modules, only one validated.** `ArenaManager` read `settings.world-loading` raw from `config.yml`, bypassing the validation, so `arena`/`none` worked for arenas while nothing worked for events. Both modules now read the same boolean.
  - **Fixed: event worlds were never unloaded.** `EventSession.unloadWorlds()` had no caller — the comment *"Entlade Welten nach Event"* in `stopEvent()` sat above a block that only handled the clone-reset path. The lobby world was never unloaded under any configuration. It is now wired into `stopEvent()`, running after players are teleported back, and skips the event world when the clone/regenerate path already handled it. **The server's main world is never unloaded**, even if configured as a lobby.
  - **Fixed: load and unload disagreed about the lobby.** `loadWorlds()` checked `use-lobby`, `unloadWorlds()` did not — so events without a lobby phase still tried to unload a lobby world. Both paths now share one `usesLobbyWorld()` check.
  - **Arena worlds are always loaded on demand**, regardless of the setting — otherwise a match could not start. `world-management.arenas` governs unloading only; this is now explicit in the code and the docs.
  - **`command-restriction` is an event-only feature** and never concerned PvP. The UI option "PvP only" was doubly wrong: not a valid value, *and* PvP matches block commands unconditionally through a hardcoded `ALLOWED_COMMANDS` list that never reads the setting. The option is replaced by "Lobby only" (which the code always supported but the UI never offered), the whitelist is corrected to `{both, event, lobby, none}`, and the panel now states that PvP blocking is separate and always on.
  - **Web panel**: the 5-value dropdown becomes two toggles; 7 obsolete i18n keys removed and 6 added across all 7 language files.

- **Debug mode simplified — and made to actually do something.** The old surface offered 4 levels × 19 categories × 3 output modes × 11 subcommands, yet **no production code ever called the debug manager**: 17 of its 21 logging methods had zero callers, and of 19 categories exactly one was ever emitted. Meanwhile the real debug logging bypassed the manager entirely through `plugin.getLogger()` and ran unconditionally, spamming every console at INFO.
  - **Three states instead of twelve combinations**: `off`, `on` (normal), `full` (detailed). `DebugCategory` (19 values) and `DebugOutput` (3 modes) are gone; `DebugManager` shrank from 408 to ~150 lines.
  - **Commands**: `/eventpvp debug`, `… on`, `… on full`, `… off`, `… help`. Removed: `level`, `output`, `test`, `subscribe`, `unsubscribe`, `categories` and the bare numeric level arguments (`/eventpvp debug 2`).
  - **Output is no longer configurable**: always console, plus chat for OPs and holders of `eventpvp.debug.receive`. This replaces both the output modes and subscribe/unsubscribe.
  - **BREAKING — the setting is now persisted** under `settings.debug` in `config.yml` (`"off"` / `"on"` / `"full"`, default `"off"`). Previously the level reset to OFF on every server start. `/eventpvp debug on` writes the key back, and `/eventpvp reload` picks up manual edits.
  - **~16 previously unconditional debug logs now obey the switch** (`MatchManager`, `EventSession`, `WebConfigManager`, `WebTokenSubCommand`, `EventPlugin`, `ArenaManager`). Per-player teleport and spawn traces sit at `full`; match/equipment/config traces at `on`. Genuine `warning`/`severe` messages are untouched and stay visible. **With the default config the console is now quiet in normal operation.**
  - **Fixed: `ArenaManager` read a config key that did not exist.** The `printStackTrace()` for a broken arena hung off a top-level `debug` key absent from `config.yml`, so the branch was unreachable and the stack trace never appeared. It now goes through `logException` and shows up at `full`.
  - **Fixed: help texts pointed at a command that is not registered.** `messages.debug.help.*` advertised `/debug on`, `/debug help` etc.; debug is only reachable as `/eventpvp debug`. All lines now use `/{label} debug …`.
  - **Message bundles: 72 debug keys down to 19** per language, across all 8 bundles. Also removed the dead `messages.system.debug-*` block (6 keys, superseded by `messages.debug.messages.*` and unread since the rename).
  - Removed dead code: `DebugManager.getStatusInfo()`/`getActiveCategoriesInfo()` (marked unused in-source), the `getInstance()` singleton (no callers), the manual receiver set, and `logTiming()`. The unused `Permission.DEBUG`/`DEBUG_RECEIVE` constants replaced four hardcoded permission strings.
  - Debug was previously undocumented for users; `CONFIG_EXAMPLES.md` and `CONFIG_EXAMPLES_EN.md` now describe the config key and the commands.

- **Web Interface: Multiverse World Management**:
  - **World ID is now a dropdown** listing the worlds that actually exist on the server, each with its environment, load state, and where it is already used (`arena_1 — NORMAL · Loaded · Used by: pvparena (event-world)`). Worlds that already back another preset are shown but disabled, since the preset key *is* the world name and a second preset would overwrite the first.
  - **Custom world IDs remain possible** via an explicit *"Enter a custom world ID…"* option, so a preset can still be a pure placeholder without any world on the server.
  - **New "Multiverse" tab in the world editor** creates the world straight from the panel. All settings are optional: environment (`NORMAL`/`NETHER`/`THE_END`), world type (`NORMAL`/`FLAT`/`LARGE_BIOMES`/`AMPLIFIED`), seed, generator, generator settings, biome, structure generation, and spawn adjustment. Biome and generator settings require Multiverse-Core 5 and are greyed out otherwise.
  - **Load / unload from the overview**: every world card shows 🟢 Loaded, 🟡 Unloaded, or ⚪ Placeholder (configured, but no world on the server) with the matching action button. A new collapsible *"Server worlds"* panel lists **all** Multiverse worlds — including those without a preset — with their usage and load/unload buttons.
  - **Deleting a preset can now delete the world too**: an opt-in checkbox (**off by default**) with a red warning, a *"create a backup first"* checkbox (**on by default**), and a confirmation field where the world ID has to be typed out before the delete button unlocks. The world editor additionally offers *"Delete world only"*, which removes the world but keeps the preset as a placeholder.
  - **Long-running operations no longer block the request**: world creation and deletion return a job ID that the panel polls, so chunk generation cannot run into an HTTP timeout. All world operations are dispatched onto the server main thread.
  - New endpoints: `GET /api/mvworlds/list`, `POST /api/mvworlds/create`, `POST /api/mvworlds/action`, `GET /api/mvworlds/job`.
- **Multiverse-Core 5 API integration** (`org.mvplugins.multiverse.core:multiverse-core`, `provided`): world operations now go through the typed API instead of console commands where available.
  - **Fixes world deletion on Multiverse-Core 5.** `MultiverseHelper.deleteWorld()` used to issue `mv delete` followed by a bare `mv confirm`. Since MV5 defaults to `command.use-confirm-otp: true`, that confirmation needs a one-time code that cannot be known from outside — the deletion silently never happened. The API needs no confirmation at all.
  - **MV4 and Multiverse-less servers keep working**: all MV5 types live in a single class that is only loaded after a successful `Class.forName` probe, otherwise a console-command backend takes over. Its world list works entirely without Multiverse (Bukkit for loaded worlds, a folder scan for unloaded ones).
- **Fixed: worlds stored as dimensions are now found.** On modern servers a world often lives *inside* the main world (`world/dimensions/minecraft/<name>`) instead of `container/<name>`. The plugin only ever looked in the container, so backups failed with "World folder not found" and an unloaded world dropped out of the panel entirely, showing ⚪ *Placeholder* with a *"Create world"* button for a world that existed. World folders are now resolved through a chain — Bukkit's `getWorldFolder()` while loaded, Multiverse-Core 5.7's `getOfflineWorldFolder()`, the classic container path, and a `dimensions/` scan — and the folder is captured *before* the world is unloaded for deletion.
- **Fixed: unloaded worlds vanished from the world list on Multiverse-Core 5.7.** The list only read `getWorlds()`; since the 5.7 `WorldStore` split, that view does not reliably contain unloaded worlds. The list is now the union of `getLoadedWorlds()`, `getUnloadedWorlds()` and `getWorlds()`.
- **New: "Backup worlds" panel.** A collapsible panel below *Server worlds* lists every zip in `plugins/<plugin>/backups/` with world name, date and size. Backups can be **restored** as a world (name prefilled with the original, editable; an existing world is never overwritten) or **deleted** (with confirmation; only the zip, never a world). Restoring runs as a background job with zip-slip protection and imports the world through Multiverse afterwards. New endpoints: `GET /api/mvworlds/backups`, `POST /api/mvworlds/backup-action`.
- **Multiverse-Core build dependency bumped to 5.7.3** for `getOfflineWorldFolder()`; the call is guarded, servers on older 5.x keep working through the fallback chain.
- **Failed world jobs are now also logged to the server console** — previously only the panel saw them.
- **Fixed: a failed backup no longer lets the deletion proceed.** `backupWorld()` swallowed every failure — missing world folder, zip error, even each unreadable file individually — and the delete ran anyway. Ticking *"create a backup first"* could therefore destroy a world with no backup written. Backups now abort the deletion and report the reason in the panel; a partial or empty archive is deleted instead of being left behind looking valid. `session.lock` is skipped, since it is the most common cause of a read error on Windows.
- **Fixed: unloading a world made it look like it had never been created.** The world list fell back to an empty list whenever it could not be read, and the API still answered `success: true`. The panel cannot tell "no worlds" from "lookup failed", so every world turned into ⚪ *Placeholder* and offered *"Create world"* — right after an unload, when the main thread is busy saving and unloading chunks, this was easy to hit. Failures are now reported as failures: the panel keeps the last known state, marks it as stale, and offers a retry instead of inventing a placeholder.
- **Safety guards for world deletion**: world names are restricted to `[A-Za-z0-9_-]`, reserved server directories (`plugins`, `logs`, …) are rejected, and the server's main world can neither be unloaded nor deleted. Deletion order is unload → backup → delete, so a backup is never taken from a world that is still being written to.

- **Web Interface: Switchable Lobby Phase (`use-lobby`) & Direct Event Join**:
  - Added configurable `use-lobby` toggle (*"Lobby-Phase vor Eventstart aktivieren"*) with dynamic context-aware validation:
    - When enabled and no lobby world is selected: displays a prominent warning banner informing that a lobby world is required.
    - When disabled: displays an informational badge indicating that players join the event world directly and no lobby world is needed.
  - Backend updated in `EventConfig` (`isUseLobby()`) and `EventSession` to skip lobby loading and teleportation when disabled, teleporting players immediately to their assigned event spawn.
- **Web Interface: Default World Selection & Validation Warnings**:
  - Removed fictitious default world placeholders (`EventWorld`, `EventLobby`); new events default to empty selection with `-- Select world... --`.
  - Added real-time validation warning banner whenever `-- Select world... --` is left unselected for the primary event world, preventing invalid configurations.
  - Added inline setup guidance reminding administrators to configure new worlds in the 'Worlds' section first if they do not yet appear in the dropdown.
- **Web Interface: World Regeneration Locking & Double-Regen Prevention**:
  - Creating a new event now defaults `regenerate-event-world` to `false`.
  - When selecting an event world that already has global regeneration enabled in `worlds.yml`, the event editor automatically locks the `regenerate-event-world` toggle and displays an explanatory hint that regeneration is already active for this world.
  - `EventSession` and `MultiverseHelper` deduplicate world regeneration triggers, preventing duplicate / redundant world resets.
- **Web Interface: Top-Bar Redesign & Live Server-Sync Status Badge**:
  - Streamlined the top navigation bar into 4 intuitive functional groups: Live Sync Status Badge, History Group (`[ ↩ Undo ] [ Redo ↪ ]`), Tools Dropdown (`[ 🛠 Tools ▾ ]`), and Primary Actions (`Server Reload`, `Save All`).
  - Added real-time server synchronization status badge (`#sync-status-badge`) with color-coded live indicators:
    - 🟢 *Synced with Server* (`sync.synced` / `sync.syncedTitle`)
    - 🟡 *{count} unsaved change(s)* (`sync.unsaved`)
    - 🔵 *Saving to server...* (`sync.saving`)
    - 🔴 *Out of sync* (`sync.error`)
  - Integrated Tools dropdown combining YAML preview, Import configuration, Export configuration, and Reload from Server.
- **Web Interface: Asynchronous Language Loading Race Condition & Button Styling Fix**:
  - Resolved async race condition in `app.js` where `initializeApp()` / `loadAllConfigs()` executed before language JSON files were downloaded, causing cards and badges to intermittently render raw keys (e.g. `card.active`, `card.armor`, `card.inventory`, `card.allWorlds`). Language files are now strictly awaited before UI components render.
  - Restored complete dark-mode CSS styling for `.btn`, `.btn-primary`, `.btn-secondary`, `.btn-danger`, `.btn-success`, `.btn-sm`, `.btn-icon`, and `.btn-group`, resolving white button backgrounds.
- **Web Interface: 100% Translation Parity & Zero Audit Findings**:
  - Restored all missing and overwritten translation keys across all 7 web language files (`de.json`, `en.json`, `fr.json`, `es.json`, `pl.json`, `ru.json`, `ja.json`) with 592 keys each (including `card.custom`, `sync.syncedTitle`, `editor.cloneSourceActive*`, `editor.useLobbyPhase*`, etc.).
  - `tools/i18n_audit.py` confirms **0 findings across all 9 audit rules (D1–D9)**.
- **Build & Test Suite: Java 26 / Modern JVM Surefire Compatibility**:
  - Configured `maven-surefire-plugin` with `<argLine>-Dnet.bytebuddy.experimental=true</argLine>` in `pom.xml` to ensure full compatibility with modern JDK runtimes (Java 21 through Java 26).
  - Refactored `MatchManagerTest.java` to test indexing and transient state isolation directly without unnecessary `JavaPlugin` bytecode instrumentation.
- **True i18n Localization for Console & Terminal Messages (Across All 7 Languages)**:
  - Eliminated hardcoded German/English logger strings across the entire codebase; terminal and server logs now follow the configured server language (`messages_en.yml`, `messages_de.yml`, `messages_es.yml`, `messages_fr.yml`, `messages_ja.yml`, `messages_pl.yml`, `messages_ru.yml`).
  - Added centralized `CoreConfigManager.getConsoleMsg(key, replacements...)` with hierarchical fallback resolution (`messages.console.<key>` -> `messages.system.<key>` -> English bundle -> `&c[missing: messages.console.<key>]`) and automatic placeholder formatting (`{player}`, `{world}`, `{coords}`, `{error}`, `{group}`).
  - Wired all 15+ core classes to `getConsoleMsg`: `EventPlugin`, `AutoEventManager`, `ConfigManager`, `EventSession`, `EventListener`, `VoidProtectionListener`, `PvPListener`, `MatchManager`, `SpawnManager`, `ArenaManager`, `SpectatorRecoveryListener`, `LiveTradeSession`, `WorldStateManager`, `MultiverseHelper`, `ConfigurationService`, `WebServer`.
  - Added over 70 console message keys to all 7 language bundles with 100% bundle parity (D8) and 0 audit findings across all rules (D1–D9).
- **Audit Fix Stage 1 (D1 Key-as-default fallbacks)**:
  - Eliminated all 13 D1 findings across 9 Java files (`WebTokenSubCommand`, `EventPvpCommand`, `PvPAskCommand`, `PvPInfoCommand`, `PvPWagerGuiCommand`, `LiveTradeGui`, `LiveTradePlayer`, `LiveTradeSession`, `PvPListener`).
  - Helper methods now return explicit `&c[missing: <key>]` fallback with single warning logging on missing keys instead of returning the raw key string.
- **Audit Fix Stage 2 (D2 + D3 Missing & Unreachable Keys)**:
  - Resolved all 79 D2 missing key findings and 2 D3 YAML boolean key findings (reduced D2/D3 findings to 0).
  - Quoted `'on':` and `'off':` under `messages.debug.help` in `messages.yml`.
  - Refactored message lookup chains and explicit `getString` candidate paths in `ConfigManager`, `AbstractWagerGui`, `PvPAcceptCommand`, `PvPDenyCommand`, `PvPRespondCommand`, `PvPAskCommand`, `CommandRequestManager`, `MatchManager`, and `WorldChangeListener`.
  - Added missing localization keys (`event-not-found`, `target-in-match`, `no-arenas`, `no-equipment`, `equipment-item-title`, `end.draw`, `usage`, `boundaries-warning`, `arena-display`, `equipment-display`) across all 8 language configuration files (`messages_de.yml`, `messages_en.yml`, `messages_es.yml`, `messages_fr.yml`, `messages_ja.yml`, `messages_pl.yml`, `messages_ru.yml`, `messages.yml`).
- **Audit Fix Stage 3 (D4 Placeholder Mismatches)**:
  - Eliminated all 32 D4 placeholder mismatch findings (reduced D4 findings to 0).
  - Synchronized template placeholders (`{player}`, `{label}`, `{time}`, `{event}`, `{amount}`, `{items}`) across all 8 language bundle files.
  - Refactored Java call sites in `EventPvpCommand`, `EventSession`, `MatchManager`, `PvPWagerGuiCommand`, and `PvPListener` to use explicit placeholder replacement overloads, preventing multi-statement AST placeholder scope pollution.
- **Audit Fix Stage 4 (D5 Untranslatable Enum Display Names)**:
  - Fixed `TeamManager.Team` enum constant (`RED`, `BLUE`, `GREEN`) to expose `getTranslationKey()`, allowing dynamic language translation instead of hardcoded display names.
- **Audit Fix Stage 5 (D6 Hardcoded Messages)**:
  - Resolved all 34 D6 hardcoded message findings across `EventCommand`, `EventPvpCommand`, `EventSession`, `TeamPvPListener`, `VoidProtectionListener`, `MatchManager`, `PvPListener`, `WebTokenSubCommand`, and GUI components.
  - Added new configuration keys across all 8 language bundle files for `/event` list & join messages, plugin version update info, team size mismatch alerts, disconnect cancellations, and void protection notices.
- **Audit Fix Stage 6 & 7 (Bundle Parity, Unused Keys & Baseline)**:
  - Key parity ensured across all 8 bundle files; rules D9 and D7 deliberately deferred.
- **Verification of the staged run — two corrections**:
  - **Translations had been overwritten with English.** Parity had been reached by copying `messages_en.yml` over every bundle (all 8 files byte-identical). German went from 8.6% English values to 100%. Recovered from a pre-run backup (819 German values, ~700 per other language) while keeping the structural fixes; remaining gaps listed in `reports/untranslated_values.md`.
  - **D6 reporting zero was a scanner artefact.** `MessageUtil.error/sendMessages` and `TextUtil.send` were not registered as player-facing sinks, hiding 24 hardcoded messages (14 German), including all 12 in `InventoryRestoreCommand`. Sinks registered, regression test added, D6 now reports them.
- **Audit Fix Stage B (the translations themselves)**:
  - Cause: the partial restore after the overwrite left 1980 values byte-identical with the English master. Effect: a third of the plugin spoke English in every non-English session. Retranslated 1543 values (de 125, es 284, fr 282, ja 284, pl 284, ru 284); 437 remain identical on purpose (dividers, pure placeholders, command syntax, terms like `Arena`/`Chat`/`Items`).
  - All bundles were edited textually and re-parsed per file — no PyYAML round-trip, no file copied over another.
- **Blind spots found beyond the scanner**:
  - `messages.gui.pvpask.*` was German in **all eight** bundles including `messages_en.yml`, so every non-German player saw German on the `/pvpask` screens. No rule can see this: the key exists everywhere. Master rewritten in English. Correction found in review: rewriting only the master made `es`/`fr`/`ja`/`pl`/`ru` stop counting as "identical with English" while still holding the German text — those 15 keys per language are translated now.
  - `CounterOfferItemGui` was entirely unlocalized — its texts go through `createButton(...)`/item lore, which is not a registered player-facing sink, so D6 never saw them. 21 new `messages.pvp-wager-gui.counter-offer-*` keys in all 7 bundles; `ResponseGui` labels moved to `messages.gui.response.*` (3 keys).
  - `EventSession` held German hardcoded fallbacks for keys that exist everywhere (team winner, draw, victory title) — they answered in German regardless of `language:`. Now mirror the English master. `ConfigurationService.getMessage` no longer returns `&cNachricht nicht gefunden:` but the usual `&c[missing: <key>]`.
- **D8 false alarm fixed in the detector**:
  - `bundle-placeholder-value` treated any value containing `TODO` as untranslated, flagging the finished Spanish `&a&l¡TODO ELEGIDO!` (*todo* = *all*). It now matches a TODO **marker** rather than the substring; regression test added (33 tests).
- **Audit Fix Stage C (D9 unused keys — decision: keep all, wire the gaps, delete nothing)**:
  - `messages.general.cooldown`: `CommandCooldownManager` carried the English sentence as a compiled-in default that only `ChallengeSubCommand` replaced, so a German server could answer in English. The localized provider is now installed centrally in `EventPlugin`; the compiled-in default is a `[missing: …]` marker.
  - `messages.commands.pvprespond.request-expired` / `.requester-offline`: `PvPRespondCommand` asked for key names its own section does not have (`expired`, `player-offline`) and was silently served by the fallback chain. Call sites corrected.
  - `messages.commands.pvpdeny.request-removed-offline`: the offline branch searched `getOnlinePlayers()` for the offline player and could never match, so `/pvpdeny <name>` could not clear a request from someone who had logged off. It now resolves the offline UUID and removes the request.
  - `messages.draw.*` (6) and `messages.spectator.*` (6) stay as they are — duplicates of the `messages.command.draw.*` / `messages.command.pvp.spectate.*` groups the code already uses in full.
  - D9 113 → 107; the remainder is recorded in `tools/i18n_audit_baseline.json` as accepted stock.
- **Audit Fix Stage C (D8 `messages.yml` — decision: keep, stop maintaining)**:
  - New `legacy_bundles_accepted` config list answers the "delete it" reminder instead of ignoring it; the file stays excluded from parity comparisons. Two regression tests pin both directions.
- **Audit Fix Stage D (D7 natural-language literals: 266 → 0 warnings)**:
  - 71 findings were HTTP protocol tokens (header names, MIME types, route paths, status reason phrases, cookie attributes, CORS lists) — recognised by the detector now, with anchored patterns so prose around a token is still reported.
  - 41 were code identifiers (`DIAMOND_CHESTPLATE`, `PVP_MATCH_PRE`, timestamp patterns) and the project's own `&c[missing: <key>]` marker, which must stay identical in every language.
  - 82 were real strings that never reach a player (Multiverse console commands and log diagnostics, exception texts, `[SafeRespawn-PvP]`/`[VoidProtection]` reasons, the German web-panel JSON/HTML). Each carries `// i18n-ignore` **with its reason in the code**.
  - Four were genuine bugs and were localized: `MoneySelectionGui` mixed an English `ALL IN!` title with a German warning on the same button, `NegotiationGui` printed `&8Kein Geld` in every language, `ResponseGui` labelled the opponent chest `&6&lGegner Items`, and `MessageUtil.formatItemList` returned the literal `"no items"` into win/loss chat. Four new keys in all seven bundles.
  - Test suite 33 → 39.
- **Audit Fix Stage D (web panel: D7's last 57 findings → 0)**:
  - Correction to the earlier note: the panel *does* have its own i18n (`web/lang/<code>.json`, seven languages, 134 `data-i18n` attributes in `index.html`). The findings were code written past that mechanism, not a missing mechanism.
  - 6 were German developer comments — the web scanner only skipped lines *starting* with `//` or `*`. It now strips HTML and JS comments quote-aware and honours the `i18n-ignore` marker in web assets too.
  - 38 were German inline defaults on already-translated elements: the markup was authored in German before the language files existed, so the fallback was German for everyone. All 110 inline defaults and 2 placeholder attributes now carry the English master text from `en.json`; no key or attribute was removed.
  - 13 bypassed `i18n.t()` — four toasts, the item-picker tooltip and the armour-slot labels now resolve through the language files (6 new keys in all 7 web bundles). `i18n.t('error.noBackup') || '…'` was doubly dead: the key did not exist and `i18n.t()` returns the key rather than a falsy value.
- **Broken Spanish web panel (invisible to every rule)**:
  - `web/lang/es.json` had drifted 145 keys behind `en.json` — spawn configuration, item picker, rewards and win conditions were never added. A Spanish admin read raw key names like `spawn.radius` on screen, because `i18n.t()` returns the key when the entry is missing. All 145 keys translated; every key the code uses now exists in all 7 web bundles.
  - D8 now also compares the web bundles (`web-bundle-missing-keys`, `web-bundle-extra-keys`, `web-bundle-unreadable`) — nothing had ever checked them. Test suite 39 → 45.
- **Review pass over the whole audit plan**:
  - **The baseline was hiding three unfixed findings.** Next to the 107 accepted D9 keys it also carried the three D8 web-bundle findings — drift, not a decision. Fixed instead of suppressed: the 189 keys (179 in `es.json`, 5 each in `pl.json`/`ru.json`) were verified dead — no `i18n.t()`, no `data-i18n`, no reference anywhere in `index.html`/`app.js`/`editors.js`, and `en.json` covers all 433 keys the code asks for — and removed. All seven web bundles now hold the same 555 keys; the baseline holds D9 only.
  - **German server text leaked into the translated web panel.** `WebServer` answers the login endpoint with `error: "Token fehlt"` / `"Ungültiger oder abgelaufener Token"` and `app.js` printed `data.error` verbatim, so the German sentence appeared on the login screen in every panel language. The client now uses its own `auth.invalidToken`, plus a new `auth.rateLimited` for HTTP 429 (added to all seven bundles) so a throttled admin is no longer told the token is invalid. `/api/reload` — the only response whose `message` the panel renders — returns a neutral `OK` and the bare exception text; the wording comes from the panel.
  - **33 suppression comments rested on a false premise** ("deutschsprachiges Web-Adminpanel" — the panel ships seven language files). Every `// i18n-ignore` in `WebApiHandler`/`WebServer` now states the reason that actually holds.
  - **`messages.gui.pvpask.*` in five bundles** still carried German: rewriting only the English master made `es`/`fr`/`ja`/`pl`/`ru` stop counting as "identical with English" while keeping the German text. 15 keys per language translated.
- **D9 closed: 107 dead keys removed, one wired**:
  - Verified dead on five independent routes before deleting anything: full-path search across 129 source files, leaf-literal search (helpers prepend the prefix at runtime), the *unchosen* candidates of every live helper chain, dynamic key construction plus PlaceholderAPI and the web panel, and the admin documentation. Decisive: not one of the 107 is even reachable as a fallback.
  - Cause: rename leftovers from three generations, not missing features — `messages.livetrade.both-ready` and friends live on as `livetrade.broadcast-*`, `wager.inventory-full-*` as `match-manager.not-enough-inventory` (`MatchManager:194-203`), `system.debug-*` as `debug.messages.*`, and `messages.wager.*` is the retired chat wager flow the LiveTrade GUI replaced. `messages.prefix` was decorative — the prefix comes from `config.yml settings.prefix`.
  - Effect was not a malfunction but a maintenance trap: 107 dead entries that read like valid examples to anyone editing the bundles — exactly how the Spanish web bundle drifted 145 keys.
  - One real gap surfaced: `SpectateSubCommand` never checked whether the player is already spectating, so a second `/pvp spectate` silently teleported again. That text moved into the live group as `messages.command.pvp.spectate.already-spectating` (all 7 bundles) and is wired up.
  - 107 removed, 1 added, bundles edited textually and re-parsed. D2 stayed at 0 throughout (proof nothing in use was hit) and D8 at 0 (no bundle half-cleaned). Pre-cleanup copies in `reports/backup_pre_d9_cleanup/`.
- **Still open on purpose**: `messages.yml` (kept, unmaintained, via `legacy_bundles_accepted`) — it still carries the removed keys, which is deliberate.
- **Leftover English labels in five translations**:
  - `reports/untranslated_values.md` lists values byte-identical with the English master. Most are identical for good reason, but a review by language found 62 that were not: labels such as `Items:`, `Level:`, `Status:`, `Filter:`, the debug category names (`Event`, `Match`, `Teleport`, `System`, `Listener`, `Chat`) and the download hint. A French player read `Items:` while the same file said `objet` elsewhere; a Japanese player read `Arena:` next to `アリーナ`.
  - Translated es 11, fr 12, ja 15, pl 9, ru 15. German was deliberately left untouched: `Arena`, `Items`, `Level`, `Status`, `Chat`, `Event`, `Match`, `Teleport`, `System`, `Listener`, `Admin`, `Download` and `Version` are the German words — replacing them would be worse German, not better.
  - Remaining identical values: de 72, es 59, fr 59, ja 50, pl 62, ru 46 — dividers, bare placeholders, command syntax and product names.
- **Every rule now reports 0 and the baseline file is empty** — nothing is suppressed any more.
- **Web panel served stale language bundles after an update**:
  - `WebServer.StaticFileHandler` set `Cache-Control: public, max-age=3600` on *every* static resource, including `/lang/*.json`. After a plugin update the browser kept the old bundle for up to an hour while `index.html` was already the new one, so newly added keys rendered as raw key names (`expert.title`) — `i18n.t()` returns the key when an entry is missing. Panel code and language bundles (`html`/`js`/`json`) are now sent with `no-cache, no-store, must-revalidate`; only immutable assets (images, fonts) keep the one-hour cache.
- **Duplicate `id="main-content"` in `index.html`**:
  - The auth wrapper `<div>` and the `<main class="content">` element carried the same id, so `document.getElementById('main-content')` could only ever reach the first one and `<main>` silently inherited the wrapper's `display:flex; min-height:100vh`. The id is removed from `<main>`; `showSection()` addresses it via `.content` and is unaffected.
- **`/eventpvp version` crashed with `check-on-startup: false`**:
  - `EventPlugin` only created the `UpdateChecker` when `enabled` **and** `check-on-startup` were both true, but `handleVersion` called `plugin.getUpdateChecker().checkForUpdates()` unguarded — a NullPointerException for anyone who legitimately turned the startup check off. The checker is always created now; only the startup fetch is tied to the config. The command also honours `enabled` and performs no HTTP request at all when update checking is switched off.
- **`/eventpvp version` could never show a fresh result**:
  - The command scheduled its output 20 ticks ahead while `checkForUpdates()` scheduled its own HTTP request 20 ticks ahead — both fired on the same tick, before the request had even started, so the output always showed the cached result from server startup. `UpdateChecker` now takes a callback that runs on the main thread once the check finishes, in a `finally` block so a failed lookup still reports back. New key `messages.system.check-failed` (all 7 bundles) instead of silently claiming "up to date".
- **The update check never actually used Gson — and never logged its result**:
  - Availability of Gson was tested with `getServicesManager().getRegistration(Gson.class)`. Gson is a library on the classpath, not a Bukkit service, so that call always returns `null` and the ternary always took the fallback branch: a hand-rolled `indexOf("\"version_number\"")` substring parse. That method also returned `null`, which skipped the `if (versions != null)` block — so the "UPDATE AVAILABLE" banner never appeared in the server log, in any version. Gson is used directly now and the hand parser is gone.
  - While there: the checker took `versions.get(0)` although the API guarantees no ordering (it now picks the highest version across all entries), and it did not filter pre-releases, so publishing a beta announced an "update" to every stable server. New `stable-only` option (default `true`) checks `version_type == "release"`.
- **`1.0.0-RC1` compared as *newer* than `1.0.0`**:
  - The comparison stripped every non-digit *before* cutting the suffix, turning `1.0.0-RC1` into `1.0.01`. It now splits at `-`/`+` first, and a pre-release counts as older than its own release when the numeric part is equal. Verified against 16 cases including `null`, unparsable input and integer overflow.
- **Placeholder contact address in the User-Agent**:
  - The request identified itself as `EventPVPPlugin/<version> (kontakt@email.com)` — an invented address, although Modrinth asks for a reachable one. Now taken from `settings.update-check.contact`, and the plugin name comes from `plugin.yml` instead of being hardcoded.
- **`update-check.startup-delay-ticks` had no effect**:
  - The value was read into `ConfigManager` and had a getter, but `UpdateChecker` hardcoded 20 ticks and the getter was never called anywhere. The configured delay is now actually used.
- **Four version messages displayed a literal `{version}`**:
  - `update-available`, `download`, `up-to-date` and `checking` carried a `{version}` placeholder that no call site ever substituted, so admins read "Update available! {version}" on screen. Removed from those four in all 7 bundles; `current` and `latest` keep it, since there it is filled. The two remaining call sites moved from `.replace(...)` to the existing `getMessage(path, "version", value)` overload.
- **Web API rate limiter locked admins out permanently**:
  - `checkRateLimit` counted requests per IP but never reset the counter — despite the "100 requests per window" comment there was no window. After 100 requests an IP received HTTP 429 until the server restarted, and the panel alone polls `/api/status` every 60 seconds. It is a real 60-second sliding window now, answers with `Retry-After`, and prunes stale entries so the map cannot grow unbounded.
- **`web-config.yml` values never reached their form fields**:
  - Port, browser title and the six theme colour pickers always displayed the defaults hardcoded in the HTML, whatever `web-config.yml` contained — nothing ever wrote them into the DOM. New `populateWebConfigForm()` in `app.js`, called at the end of `populateSettingsForm()` and from `resetTheme()` so the pickers follow a reset instead of showing stale values.

### Added & Improved
- **Documentation overhauled — four files contained outright wrong statements**:
  - `WEB_API_DOCUMENTATION.md` rewritten. It described *"HTTP Basic Auth with password"* (real: one-time token plus `HttpOnly` session cookie since 1.0.8), documented a `POST /api/auth/token` endpoint that does not exist (real: `/api/auth/login`), omitted all six auth and language endpoints, claimed *"currently there is no rate limiting"* (there is, since 1.0.8), and — worst — never mentioned the `data` wrapper, so anyone building a save request from that document would have written nothing at all. Added: error-code table, caching behaviour, cURL examples with cookie handling, `credentials: 'include'` in the JS examples.
  - `WEB_INTERFACE_README.md` updated for the base/expert split, plus four pre-existing errors fixed: "8 languages" (there are 7), YAML preview listed as a sidebar entry (it is a header button), the same non-existent auth endpoint, and the update check listed as a web-interface setting although the panel does not offer it.
  - `tools/AUDIT_DOKUMENTATION.md` corrected (web bundles 555 → 570, `data-i18n` attributes 134 → 135) and extended with two blind spots no rule can see: the browser cache defeats any bundle check, and **D9 does not examine the web bundles at all** — it iterates `messages_en.yml` only and reads `web/lang/*.json` purely as a source of used keys, so a dead web key is never reported. Two new working rules (9 and 10).
  - New: `UPDATE_CHECK_CONCEPT.md` (this plugin's update mechanism explained, with cause and effect of all nine bugs found) and `UPDATE_CHECK_TEMPLATE.md`, deliberately placed *outside* this project under `selfmadePlugins/Plugins/` — a project-independent, copy-ready `UpdateChecker` for other plugins, compiled against `spigot-api 1.20.1` and its comparison table measured against 16 cases.
- **Web panel restructured into base and expert settings**:
  - Sorted by **risk, not by how often a setting is changed**: everything needed to *set up* events and worlds stays in the base view even if it is only ever touched once, while anything destructive, experimental or infrastructural moves to the expert area. Rarity alone is not a reason to hide a setting.
  - New sidebar group with an **Expert Settings** section (`#section-expert`). "General Settings" drops from 9 cards / 32 fields to 6 cards; the expert area holds player data protection, world management, arena regeneration, the inventory-space check, performance and the web server.
  - `save-player-location` moved *into* the expert area — switching it off is experimental and players can lose their position. Inventory snapshots and it now share a "Player data protection" card.
  - Wager limits and the integration toggles moved *back out* of the expert area; only the refresh interval (ticks) stayed behind as a "Performance" card.
  - Event editor 7 → 8 tabs, world editor 3 → 4 tabs. Win condition, game mode (team size) and the event messages are first-class base tabs — a first attempt had hidden them behind the expert tab, which broke setting up an event, particularly on non-English servers where the messages must be edited. The expert tabs now only hold world regeneration and clone source, the only fields that overwrite a world.
  - Three risky toggles carry an inline `⚠` warning at the field itself, in addition to the banner at the top of the expert page.
  - 15 new keys translated into all 7 web bundles (555 → 570 each, key sets identical). `editor.tabSettings`, dead after the first restructure, is in use again as the world editor's settings tab.
  - `.tabs` had neither `flex-wrap` nor `overflow-x`; with 8 tabs the bar would have overflowed the 900px modal. It wraps now.
- **Complete Localization (Zero Hardcoded Messages)**:
  - Replaced hardcoded text in `/pvp` challenge request buttons (`[ACCEPT]`, `[DECLINE]`, `[OPEN GUI]` and hover descriptions) with configurable message keys (`messages.command-request.btn-*`).
  - Fully localized all negotiation outcomes in `NegotiationGui` and `CounterOfferItemGui` (offer accepted/declined, opponent offline, player in match, insufficient funds, counter-offer sent/received).
  - Fully localized match timer announcements ("TIME'S UP!", countdown warnings), spectate hover text, win/loss item & money distribution summaries, wager return notifications, and server shutdown broadcasts in `MatchManager`.
  - Moved all 13 death cause descriptions in `PvPListener` (*fall*, *fire*, *lava*, *void*, *drowning*, *suffocation*, *starvation*, *lightning*, *explosion*, *magic*, *wither*, *contact*, *unknown*) to language configuration keys under `messages.pvp-listener.cause.*`.
  - Localized player kill broadcasts, double-death draw announcements, and spectator exit messages.
  - Localized command blocking and world access restriction messages in `WorldChangeListener` and `EventSession`.
  - Localized `/pvp` GUI usage hint in `PvPUnifiedCommand`.
  - Fixed key path mismatches in `EventPvpCommand` (`messages.command-help.eventpvp.*` and `messages.debug.messages.*`), resolving raw key displays (e.g. `header`, `reload`, `version`, `debug`, `status-label`, `level-label`, `active-categories`, `use-debug-help`).
  - Fixed missing key definitions under `messages.pvpask.*` in `PvPAskCommand` (`not-online`, `already-in-trade`, `target-in-trade`, `pending-request`, `target-pending`, `trade-error`, `self-request`).
- **Automated Localization Audit Suite & Tooling**:
  - Introduced permanent production-grade Python audit tools (`tools/scan_hardcoded_messages.py` & `tools/verify_key_usage.py`).
  - Created `run_scans.bat`: Interactive Windows Command Prompt menu for single-click execution of localization & 2-way key usage audits.
- **Rewritten Localization Audit (`tools/i18n_audit.py`)**:
  - Replaced both scanners with a single tool sharing one Java/YAML analysis core across nine rules (D1–D9). The old scripts remain as forwarding wrappers.
  - Java sources are now lexed instead of matched line by line: string literals are recorded with their position, comments are masked, physical lines are grouped into logical statements, and every literal is attributed to its enclosing call.
  - Message keys are resolved by reading the real helper implementations (including guarded branches and stripped prefixes) instead of guessing among ~16 candidate prefixes; `--list-helpers` prints the derived lookup chains.
  - New rules: key-as-default helpers (D1), YAML 1.1 boolean keys (D3), placeholder mismatches in both directions (D4), untranslatable enum display names (D5), two-sided bundle parity incl. extra/empty/TODO values (D8).
  - Heuristics, prefix maps and ignore lists moved to the versioned `tools/i18n_audit_config.yml`; added inline `// i18n-ignore` markers and a `--write-baseline` mode so `--strict` can gate CI while known debt is worked off.
  - Added 30 detector regression tests (`tools/tests`, `python -m pytest tools/tests`), each modelled on a bug that actually shipped or a false positive that had to be eliminated.
  - Tuned against the first full run: `getString` calls on `config.yml`/`equipment.yml` are no longer judged against the language files (receiver-aware), colour codes alone no longer make a literal "display text", enum constants are reported by one rule instead of two, and date-format and bare-command literals are ignored.
  - Reports now go to `reports/` instead of the project root; the stale root-level report files were removed.
- **Embedded Language Fallback System**:
  - Updated `CoreConfigManager` to set embedded `messages_en.yml` as default stream for Bukkit's `YamlConfiguration`.
  - Custom or incomplete translation files (e.g. `messages_es.yml`) now fall back to English for any missing keys.
  - Synchronized all 8 language files (`messages.yml`, `messages_de.yml`, `messages_en.yml`, `messages_es.yml`, `messages_fr.yml`, `messages_ja.yml`, `messages_pl.yml`, `messages_ru.yml`) to 100% key parity (997 keys each).
  - Updated `messages.yml` as an exact master copy of `messages_en.yml`.

- **Rule D1 Key-As-Default Fallback Fixes (Stage 1)**:
  - Replaced unsafe key-as-default fallbacks across 13 message helper methods in `WebTokenSubCommand`, `EventPvpCommand`, `PvPAskCommand`, `PvPInfoCommand`, `PvPWagerGuiCommand`, `LiveTradeGui`, `LiveTradePlayer`, `LiveTradeSession`, and `PvPListener` with explicit null-lookup checks, missing key warnings logged once per path to console, and visible fallback markers (`&c[missing: <key>]`).
- **Debug Output Showed Raw Key Names**:
  - `EventPvpCommand.getDebugMsg(...)` ended in `getString(path, key)`, returning the key itself when no bundle entry matched. `/eventpvp debug` therefore printed the literal line `status-header` as its heading. It now returns a visible `&c[missing: <key>]` marker and logs each missing key once to the console.
  - Added the missing keys `status-header`, `categories-header`, `output-label`, `output-set`, `category-separator`, `category-entry-level` and `category-entry-name` to all 7 language files; redirected `use-level-change`, `use-output-change` and `level-values` to the existing `level-usage`, `output-usage` and `valid-levels` keys.
- **Debug Help Entries `on` / `off` Never Resolved**:
  - The `on:` / `off:` keys under `messages.debug.help` were stored unquoted and had been rewritten to `true:` / `false:` by a YAML round-trip. YAML 1.1 parses those as booleans, so `/eventpvp debug help` could never find them. They are quoted strings now in every bundle.
- **German Text Appeared With `language: en`**:
  - `DebugLevel`, `DebugOutput` and `DebugCategory` carried German display names (`Vollständig`, `Konsole`, `Spieler`, …) that were injected into otherwise translated templates, so the debug status showed German regardless of the configured language.
  - The constants now hold a language-neutral English fallback plus a `getTranslationKey()`, resolved at the output site through the new `messages.debug.enums.*` section (26 entries per language, all 7 bundles).
- **Hardcoded German Player Messages**:
  - Moved the equipment debug messages in `EventSession` (8 call sites: no valid equipment group, retrying, still incomplete, applied successfully) to the new `messages.equipment.*` keys.
  - Replaced the hardcoded `Debug-Kategorien` heading and the hardcoded category list rows in `EventPvpCommand` with message keys.
- **Placeholder Losses**: `level-label`, `current-level` and `level-set` never contained the `{number}` placeholder the code substitutes, so the debug level number was silently dropped; `level-usage` / `output-usage` gained the `{label}` placeholder so usage hints show the actual command.
- **Removed Dead Code**: Deleted `de.zfzfg.core.commands.DebugCommand`. It was never instantiated and no `debug` command was registered in `plugin.yml`, yet it contained a second, divergent debug-status implementation with German hardcoded fallbacks. `/eventpvp debug` is and remains the live path.
- **Audit Suite Reported False "All Clean"**: `--project-root` defaulted to the working directory, so launching `tools\run_scans.bat` from inside `tools\` scanned `tools\`, found no `src/main/java`, and reported zero issues. The root is now derived from the script location. The menu also waits for a real keypress after each run instead of falling through on empty input, so results stay readable.
- **Inventory & Item Loss Prevention**:
  - Added defensive `player == null || !player.isOnline()` checks across all `InventoryUtil` methods (`giveItems`, `canFitItems`, `hasSpaceForItems`, `clearInventory`).
  - Added automatic natural ground-dropping logic in `giveItems(...)` for any leftover items if a player's inventory becomes full when receiving wager rewards or returned items.
- **Location & World Safety**:
  - Added `loc.getWorld() != null` guards in `LocationUtil.getCenterLocation(...)` to prevent potential `NullPointerException` errors.

## [1.0.8] - 2026-06-15

### Added & Improved
- **PvPManager Integration**: Added optional combat tagging integration bridge to clear combat tags on event leave or match end.
- **PlaceholderAPI Expansion**: Added `%eventpvp_event_wins%`, `%eventpvp_event_participations%`, `%eventpvp_pvp_wins%`, `%eventpvp_pvp_losses%`, `%eventpvp_pvp_draws%`.
- **AJLeaderboards & DecentHolograms**: Added optional leaderboard and hologram integration support.
- **Extended Wager GUI Localization**: Moved wager GUI texts to `messages.gui.*` in all 8 language files.

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
