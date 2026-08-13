# Event-PVP Plugin 1.0.9

## Summary

Version 1.0.9 focuses on **complete localization and zero hardcoded messages**, **true i18n localization for all console and terminal logger messages**, an **automatic embedded language fallback system**, **100% key parity across all supported language files**, **web interface live synchronization & button styling fixes**, **switchable lobby phases**, **Multiverse world management from the web panel**, and critical **inventory & location safety enhancements**.

---

## Upgrading from 1.0.8

### ⛔ Before you start the server

1. **Install required dependencies (`Multiverse-Core`, `Vault`, `InventoryBackup`).** All three are hard `depend:` entries in `plugin.yml`. Without them **Bukkit will not enable Event-PVP-Plugin at all** — you get `Unknown/missing dependency` in the console. `Multiverse-Core` is mandatory for world management, cloning, and arena resets, and the plugin will refuse to start without it.
2. **Back up your `plugins/<plugin>/` folder.** `config.yml` is rewritten in place on first start (see below), and no command in the plugin can open `inventory_backups.yml` / `inventory_post_backups.yml` any more — the files stay on disk and are still readable by hand, but nothing will restore from them.
3. **Take a world backup** if you run events. Event and lobby worlds are now genuinely unloaded when an event ends; in 1.0.8 that never happened (see *Behaviour changes*).

### 🔁 Your `config.yml` is migrated automatically

Unlike previous releases, this one **rewrites `config.yml` in place** on first start, once:

| Old (1.0.8) | New (1.0.9) | Handling |
|---|---|---|
| `settings.world-loading: both` | `world-management: {events: true, arenas: true}` | automatic, old key removed |
| `settings.world-loading: event` / `lobby` | `{events: true, arenas: false}` | automatic |
| `settings.world-loading: arena` | `{events: false, arenas: true}` | automatic |
| `settings.world-loading: none` | `{events: false, arenas: false}` | automatic |
| `settings.world-loading: clone` / `load` / anything else | `{events: true, arenas: true}` | automatic (those values never did anything) |
| `settings.command-restriction: join` / `pvp` | `both` | automatic (both were inoperative) |
| `settings.inventory-snapshots.*` | — | **left in the file and ignored.** Delete it by hand when you are sure you no longer need it as a reference |

The migration runs once, logs one line per rewritten key, and is idempotent — a second start changes nothing. **Your comments survive** the rewrite (verified on Spigot 1.19.4); quoted scalars lose their quotes (`language: "en"` → `language: en`, YAML-equivalent), and the new `world-management` block is appended to the end of `settings:` without the explanatory comments that the shipped default carries. Compare against the bundled `config.yml` if you want those.

New keys that an existing `config.yml` will **not** have — all fall back to their defaults, nothing breaks if you never add them: `settings.debug` (`"off"`), `settings.inventory-management.*`, `settings.update-check.stable-only` (`true`), `settings.update-check.contact`.

### ⚠️ Behaviour changes to expect on a live server

- **Event and lobby worlds are now actually unloaded after an event.** `unloadWorlds()` existed in 1.0.8 but had no caller — the comment *"Entlade Welten nach Event"* sat above a block that only handled the clone-reset path, and lobby worlds were never unloaded under any configuration. It is wired up now. The server's main world is never unloaded, even if a preset names it as its lobby. Set `world-management.events: false` if your worlds must stay loaded.
- **Unloading is aborted when a player cannot be moved out.** Previously every teleport failure was swallowed and the unload proceeded regardless, which could leave someone inside an unloaded world. A world left loaded is now the accepted outcome, and each failure is logged.
- **`command-restriction` finally does something.** In 1.0.8 the value was validated against `{join, lobby, both}` while the UI offered `{both, event, pvp, none}` — every option except *Both* was silently reset. If your config said `event` or `none`, it behaved like `both`; from now on it does what it says. Check the setting if you ever changed it and wondered why nothing happened.
- **Players are teleported out of event/arena worlds on login** when no session is running there — the fix for people stranded by a crash mid-event. A player rejoining an event that is *still running* is not touched.
- **`/eventpvp debug` lost most of its subcommands** (see *Debug mode*). Scripts or macros using `/eventpvp debug level 2`, `output`, `test`, `subscribe`, `categories` or the bare numeric form need updating.

### 🧰 Commands and permissions

- **Removed:** `/inventoryrestore`, permissions `eventpvp.inventory.restore` and `eventpvp.inventory.restore.any`. Inventory backups now live in InventoryBackup — reach them via `/inv <player>` or the web panel. Drop the two permissions from your permission plugin.
- **New Commands:**
  - `/eventpvp rescue list|<player>|clean`: Rescue stranded players and clean orphan sessions (permission `eventpvp.admin`). This replaces `/pvp invdebug`, which several translated messages referenced in 1.0.8 although it was never registered.
  - `/eventpvp webtoken`: Generate a one-time authentication token for the web interface (requires `eventpvp.admin.web` or `eventpvp.admin`).
- **New Permissions:**
  - `pvpwager.spectate.all` (default `op`): Allows spectating PvP matches that have reached the spectator limit.
  - `pvpwager.nowager` (default `op`): Allows starting friendly PvP matches with no monetary or item wager.
  - `eventpvp.admin.web` (default `op`): Grants access to generate web tokens via `/eventpvp webtoken` and log into the web interface.
  - `eventpvp.debug` (default `op`): Allows toggling `/eventpvp debug (on | on full | off | status)`.
  - `eventpvp.debug.receive` (default `op`): Receives debug stream output in chat in addition to the console.
- **Changed:** `/eventpvp debug` now accepts only `on`, `on full`, `off`, `status` and `help`.

### 📁 Files in the plugin folder

| File | Change |
|---|---|
| `inventory-guard.yml` | **new** — open inventory sessions, so a crash costs at most a login |
| `player-return-locations.yml` | **new** — where each player belongs after an event or match |
| `pending-payouts.yml` | **new** — queued payouts for winners/participants who disconnect before rewards are delivered |
| `inventory_backups.yml`, `inventory_post_backups.yml` | **orphaned** — no longer read and not deleted. Nothing in the plugin can open them any more; archive them if they still matter |
| `worlds.yml`, `equipment.yml`, `web-config.yml` | updated with new settings (e.g. `bind-address`, `items.resource-pack` in `web-config.yml`) |

All three state files (`inventory-guard.yml`, `player-return-locations.yml`, `pending-payouts.yml`) are written synchronously and are empty in normal operation. If entries linger for more than 24 h the console says so — inspect with `/eventpvp rescue list`.

### 🐛 Debug mode was rebuilt

1.0.8 offered 4 levels × 19 categories × 3 output modes across eleven subcommands — and **no production code ever called the debug manager**, while the real debug logging bypassed it and ran unconditionally. The level also reset to *off* on every restart, since nothing persisted it.

Now: `settings.debug: "off" | "on" | "full"`, written back to `config.yml` so it survives a restart, five subcommands, and roughly 16 formerly always-on log statements moved behind the switch. **With the default config your console is quieter than it was in 1.0.8.** `DebugCategory` and `DebugOutput` are deleted, `DebugLevel` is down to three values, and `messages.debug.*` went from 24 keys to 19 per language (12 added, 17 removed).

### 🌐 Customized language files

If you maintain your own bundles, the deltas against 1.0.8 are:

- **`messages_<lang>.yml`: 876 → 1097 keys** (+339 new, −118 removed). Largest new groups: `messages.console.*` (+128, console output is translated now), `messages.pvp-wager-gui.*` (+36), `messages.system.*` (+25), `messages.gui.*` (+25), `messages.pvp-listener.*` (+20), `messages.match-manager.*` (+18), `messages.rescue.*` (+14, the new command). `messages.debug.*` was reduced by 17 and gained 12. Anything you miss now renders as `&c[missing: <key>]` with a one-time console warning instead of failing silently.
- **`web/lang/*.json`: 548 → 764 keys.** New: `inventory.*` (61), `editor.*` (45), `mv.*` (42), `toast.*` (18), `confirm.*` (17), `web.publicUrl` (2), `web.portChangeWarning` (1). Removed: `settings.worldLoading*` (6), `settings.snapshot*`/`defaultGroup`/`retainDays` (5), `settings.commandRestrictionPvp`, `web.title`.
- The shipped `messages.yml` is deliberately excluded from all of this — it is loaded by nothing (every loader builds `messages_<lang>.yml`) and receives no new keys.

### 🔨 Building from source

`pom.xml` gained the `onarandombox` repository and two `provided` dependencies: `multiverse-core:5.7.3` and `InventoryBackup-API:0.1.0`. The latter is in no public repository — install it once before the first build:

```
cd .../InventoryRestore/InventoryRestore-0.0.7 && mvn -pl api -am clean install
```

`build.bat` in the project root does both plugins in one go and drops the jars into `dist\`; `build.bat quick` skips rebuilding InventoryBackup.

### Important Notes

*Background to the points above — everything here still applies.*

- **Inventories are now managed by the plugin itself — Multiverse-Inventories is no longer needed.** Inventories are backed up before the arena/lobby teleport and restored automatically after matches, events, deaths and disconnects. Disconnect restores are queued persistently; open sessions survive a crash through `inventory-guard.yml` and are worked off on the next start. New config section `settings.inventory-management` (default `provider: auto`); the migration does **not** insert this block into an existing `config.yml`, so the defaults apply until you add it yourself.
- **Fixed: the PvP pre-match snapshot was taken at the wrong moment.** It ran *after* the arena teleport, so with Multiverse-Inventories active it captured the already-swapped arena inventory — restoring from it would have wiped the real one instead of saving it. It now runs before the teleport, in both the normal and the emergency start path. Event snapshots were always correct. If you relied on the old match snapshots, note that their content changes with this release.
- **Legacy mode**: set `settings.inventory-management.provider` to `none` (or pick *Legacy: Multiverse-Inventories* in the web panel) to keep the previous behaviour. Multiverse-Inventories then handles the swap and the plugin restores nothing on its own — but it still writes a safety backup through InventoryBackup before every arena and lobby teleport (`legacy-safety-backups`, on by default, tagged `mode=legacy-safety`). Those copies are never applied automatically; an admin restores from them by hand via the web panel or `/inv <player>`. Running Multiverse-Inventories *and* the plugin's full management at once is the one configuration to avoid — both swap inventories and the outcome depends on timing. The plugin warns about it on startup and in the panel.
- **Removed**: the plugin's own `/inventoryrestore` command, the `eventpvp.inventory.restore*` permissions, the old `InventorySnapshotStorage` (`inventory_backups.yml` / `inventory_post_backups.yml`) and the `settings.inventory-snapshots` config section. Those files are no longer read and are not deleted — back them up if you still need anything from them, because there is no command left to read them. Inventory backups now live in InventoryBackup and are reachable through `/inv` or the web panel.
- **New helper**: `build.bat` in the project root builds InventoryBackup and Event-PVP-Plugin in one go and drops both jars into `dist\`. `build.bat quick` skips rebuilding InventoryBackup.
- **Rewards and winnings are now handed over after the restore**, not before. Previously an event reward given at the moment of victory would have been deleted by the restore a few seconds later. Players see a short "reward is waiting" notice in between.
- **Required dependency `InventoryBackup` (InventoryRestore)**: the storage backend for all inventory work (UUID folders, preview GUI, persistent join queue). It is listed under `depend:`, so the server refuses to enable this plugin without it — see the checklist at the top. Building from source needs the API artifact once: `cd .../InventoryRestore/InventoryRestore-0.0.7 && mvn -pl api -am clean install`.
- **New web panel section "Inventory management"** under *General Settings*: mode selector, the automatic-restore toggles, the backup-failure policy, and a live list of open sessions left behind by a crash.
- **New message keys**: `messages.console.inventory-*`, `messages.console.guard-*`, `messages.inventory.*`, `messages.match-manager.inventory-*` and `messages.rewards.pending`. All 7 bundles are synchronized. Web bundles gained 59 `inventory.*` keys — press `Ctrl`+`Shift`+`R` once if you see raw key names.
- **Console & Terminal Localization**: Server console output, logger messages, and diagnostic traces now respect `settings.language` and display fully translated text in all 7 supported languages (`en`, `de`, `es`, `fr`, `ja`, `pl`, `ru`) through `messages.console.*`.
- **Language Files**: Language files have been expanded with new keys for interactive buttons, negotiation responses, death causes, debug commands, event world restrictions, and over 70 console keys.
- **Automatic Fallbacks**: Missing keys in custom or partial translation files now automatically display English text via embedded resource defaults instead of returning raw key paths or breaking formatting.
- **Switchable Lobby Phase**: Events now feature a `use-lobby` toggle. If disabled, players skip the lobby phase and teleport directly into the event world without requiring a lobby world.
- **Regeneration Safeguards**: `regenerate-event-world` now defaults to `false` and is automatically locked if the chosen world already has global regeneration enabled in `worlds.yml`, preventing redundant regenerations.
- **Web Top-Bar & Live Sync**: The web editor header has been reorganized into clean functional groups with a live server-synchronization badge (`🟢 Synced`, `🟡 Unsaved Changes`, `🔵 Saving`, `🔴 Out of Sync`) and a consolidated Tools menu.
- **Customized Language Files**: If you maintain your own `messages_*.yml`, add the new `messages.console.*`, `messages.debug.enums.*` and `messages.equipment.*` sections and the new `messages.debug.messages.*` keys, and make sure the debug help entries are written as quoted `'on':` / `'off':`. Anything you miss now shows up as a `&c[missing: <key>]` marker plus a console warning instead of failing silently.
- **`messages.yml`**: still shipped but loaded by nothing — every loader builds the filename as `messages_<lang>.yml`. The audit reports it (D8); deleting it is safe.
- **Web Panel**: the configurator is now split into base and expert settings, so several options have moved — most notably "Save player position", which is in the expert area from now on. The restructure itself changed nothing in `config.yml`; only where a field is displayed.
- **Two new `config.yml` keys**: `settings.update-check.stable-only` (default `true`, ignores beta/alpha releases) and `settings.update-check.contact` (goes into the User-Agent of the Modrinth request, which is why it should be an address someone actually reads). An existing `config.yml` is **not** rewritten on update, so both fall back to their defaults until you add them — nothing breaks either way.
- **One new message key**: `messages.system.check-failed`, shown when `/eventpvp version` cannot reach the update API. If you maintain your own `messages_*.yml`, add it; otherwise the command prints the `&c[missing: …]` marker in that one case.
- **Customized Web Language Files**: if you maintain your own `web/lang/*.json`, add the new keys (`expert.*`, `sync.*`, `header.tools`, `editor.useLobbyPhase*`, `card.custom`, etc.). All 7 default files are synchronized at 691 keys (the Multiverse world management added `mv.*`, `card.mv*`, `editor.mv*`, `confirm.deleteWorld*` and `toast.mv*`).
- **Multiverse world management in the panel**: the *Worlds & Arenas* section can now create, load, unload and delete worlds directly. The world ID field became a dropdown of the worlds that actually exist on the server; a custom ID is still possible for presets that intentionally have no world. Deleting a preset optionally deletes the world as well — **off by default**, guarded by a typed confirmation and an on-by-default backup.
- **Multiverse-Core 5 is now a build dependency** (`provided`, so nothing is bundled). Servers on Multiverse-Core 4 — or without Multiverse at all — are unaffected: the MV5 types live in a single class that is only loaded when the API is actually present, and a console-command backend takes over otherwise. The world list even works without Multiverse installed.
- **World deletion works again on Multiverse-Core 5.** The old path sent `mv delete` and then a bare `mv confirm`; with MV5's default `command.use-confirm-otp: true` that confirmation requires a one-time code no external plugin can know, so the deletion never happened. This affected event world cleanup as well, not just the new panel feature.
- **Web Server Network Interface Binding (`bind-address`)**: Added `server.bind-address` to `web-config.yml` (default `""` for all interfaces, or `"127.0.0.1"` to restrict the panel to localhost behind a reverse proxy like Nginx or Caddy).
- **Server Resource Pack Item Textures**: `web-config.yml` now supports `items.resource-pack.enabled` and `max-size-mb: 50`. `ResourcePackTextureService` automatically downloads and unpacks textures from the server's `server.properties` resource-pack at startup, providing true in-game item textures in the web editor. Overrides are exposed via `GET /api/textures/overrides`.
- **Dynamic Server Material Catalog**: The web interface now loads materials and valid enchantments directly from the running server via `GET /api/materials` (`MaterialCatalog`), ensuring the kit editor only presents items and enchantment levels compatible with your server version.
- **Dedicated 3-Tab Inventory Management Web UI**: The inventory manager has been extracted from expert settings into a dedicated primary sidebar section (`#section-inventories`) featuring:
  - **Tab 1 (Backup Explorer)**: Split-view list with player search, custom Minecraft Canvas inventory viewer with XP bar, 2-step restore modal, kit export, and raw JSON inspection.
  - **Tab 2 (Live Sessions & Guard)**: Real-time active guard sessions, pending player return locations, and crash recovery status.
  - **Tab 3 (Engine Settings & Policies)**: Provider toggle (`auto`/`none`) and Multiverse-Inventories conflict alerts.
- **Web Restore Rate Limiter**: Added `SlidingWindowLimiter` (10 restores/minute globally) on `POST /api/inventories/restore` to protect against token hijacking and economy item duplication exploits. Exceeding the rate limit returns HTTP 429.
- **One hard reload on the way in**: earlier versions told browsers to cache the panel for an hour, language bundles included. That is fixed, but the fix cannot expire a copy your browser already holds — press `Ctrl`+`Shift`+`R` once after updating, otherwise the new labels may briefly appear as raw key names.

---

## Fixed & Refactored

### 🛡️ Localization Audit Fixes (Sequential 7-Stage Execution)
- **Stage 1 — D1: Key-as-default Fallbacks (COMPLETED)**:
  - Fixed 13 key-as-default fallbacks across 9 Java files (`WebTokenSubCommand`, `EventPvpCommand`, `PvPAskCommand`, `PvPInfoCommand`, `PvPWagerGuiCommand`, `LiveTradeGui`, `LiveTradePlayer`, `LiveTradeSession`, `PvPListener`).
  - Raw key fallbacks now log single warnings per key path and return explicit `&c[missing: <key>]` markers.
- **Stage 2 — D2 + D3: Missing & Unreachable Keys (COMPLETED)**:
  - Eliminated all 79 D2 missing key findings and 2 D3 YAML boolean key findings (D1, D2, D3 all 0 findings).
  - Quoted `'on':` and `'off':` under `messages.debug.help` in `messages.yml`.
  - Refactored message lookup chains and explicit `getString` candidate paths across all helper methods in `ConfigManager`, `AbstractWagerGui`, `PvPAcceptCommand`, `PvPDenyCommand`, `PvPRespondCommand`, `PvPAskCommand`, `CommandRequestManager`, `MatchManager`, and `WorldChangeListener`.
  - Added missing localization keys (`event-not-found`, `target-in-match`, `no-arenas`, `no-equipment`, `equipment-item-title`, `end.draw`, `usage`, `boundaries-warning`, `arena-display`, `equipment-display`) across all 8 language configuration files (`messages_de.yml`, `messages_en.yml`, `messages_es.yml`, `messages_fr.yml`, `messages_ja.yml`, `messages_pl.yml`, `messages_ru.yml`, `messages.yml`).
- **Stage 3 — D4: Placeholder Mismatches (COMPLETED)**:
  - Eliminated all 32 D4 placeholder mismatch findings (D1, D2, D3, D4 all 0 findings).
  - Synchronized template placeholders (`{player}`, `{label}`, `{time}`, `{event}`, `{amount}`, `{items}`) across all 8 language bundle files.
  - Refactored Java call sites in `EventPvpCommand`, `EventSession`, `MatchManager`, `PvPWagerGuiCommand`, and `PvPListener` to use explicit placeholder replacement overloads.
- **Stage 4 — D5: Untranslatable Enum Display Names (COMPLETED)**:
  - Fixed `TeamManager.Team` enum constants to expose `getTranslationKey()`.
- **Stage 5 — D6: Hardcoded Messages (COMPLETED)**:
  - Resolved all 34 D6 hardcoded message findings across `EventCommand`, `EventPvpCommand`, `EventSession`, `TeamPvPListener`, `VoidProtectionListener`, `MatchManager`, `PvPListener`, `WebTokenSubCommand`, and GUI components.
  - Added new configuration keys across all 8 language bundle files.
- **Stage 6 & 7 — D8, D9 & Baseline Verification (PARTIAL)**:
  - Key parity verified across all 8 bundle files; D9 and D7 were deliberately deferred.
  - Maven build (`BUILD SUCCESS`) & Pytest suite passing.

### 🔎 Independent verification of the staged run

D1 through D5 were confirmed genuinely fixed: the detector sources were not
modified, the baseline holds nothing from D1–D6, and spot checks confirmed real
fixes (`start.join-phase-started` really does carry `{event}` and `{time}` again).
Two findings did not hold up:

- **All translations had been overwritten with English.** Parity was reached by
  copying `messages_en.yml` over every other bundle — all 8 files were
  byte-identical (68071 bytes). German went from 83 English values out of 961
  (8.6%) to 1033 out of 1033 (100%); a German player was reading
  `&cYou don't have permission for this command!`. Rule D8 cannot detect this:
  the key exists, so the file looks complete.
  **Recovered** from a pre-run backup — 819 German, ~700 per other language —
  while keeping the structural fixes (quoted `'on'`/`'off'`, corrected
  placeholders). Values whose placeholder set had legitimately changed were left
  alone rather than undoing the D4 work. Remaining gaps are listed in
  `reports/untranslated_values.md` (de 214, es 336, fr 337, ja 332, pl 337, ru 328).
- **D6 reporting zero was an artefact of the scanner.** `MessageUtil.error(...)`,
  `sendMessages(...)` and `TextUtil.send(...)` were not registered as
  player-facing sinks, hiding **24 hardcoded messages, 14 of them German** —
  `InventoryRestoreCommand` (12) had never been touched at all. The sinks are
  registered now, with a regression test; D6 honestly reports 24.

### 🖥️ True i18n Localization for Console & Terminal Messages (COMPLETED)

All server terminal and console logger output has been refactored from hardcoded strings to proper `messages.console.*` i18n keys across all 7 supported languages (`en`, `de`, `es`, `fr`, `ja`, `pl`, `ru`):

- **Centralized Resolution**: Implemented `CoreConfigManager.getConsoleMsg(key, replacements...)` which resolves through `messages.console.<key>`, falls back to `messages.system.<key>`, then to the master English bundle, and finally to `&c[missing: messages.console.<key>]`. Placeholders (`{player}`, `{world}`, `{coords}`, `{error}`, `{group}`) are replaced automatically and Minecraft color codes stripped or rendered appropriately.
- **Full Bundle Parity**: Added over 70 console keys across all 7 YAML bundles with 100% key and placeholder parity:
  - Startup & Shutdown: `plugin-enabled`, `plugin-disabled`, `vault-hooked`, `vault-not-found`, `papi-registered`, `pvpmanager-enabled`, `web-auth-enabled`, `web-disabled`, `web-started`, `web-stopped`.
  - World & Multiverse: `world-loading`, `world-loaded`, `world-unloading`, `world-load-failed`, `backup-created`, `backup-skipped`, `backup-failed`, `zip-error`.
  - Teleportation & Respawn Safety: `safe-teleport-player`, `safe-teleport-fallback`, `safe-teleport-no-location`, `safe-teleport-critical`, `safe-respawn-invalid-loc`, `safe-respawn-unloaded-world`, `safe-respawn-wrong-world`, `safe-respawn-distance-warn`, `safe-respawn-correct`, `safe-respawn-correction-failed`, `safe-respawn-correction-success`, `void-protection-rescued`, `void-protection-critical`, `teleport-error`.
  - Events & Match Execution: `event-loaded`, `event-auto-started`, `event-auto-stopped`, `event-auto-next`, `event-auto-no-events`, `event-auto-not-enough-players`, `event-auto-starting`, `pvp-death-saved`, `pvp-death-no-location`, `pvp-respawn-unloaded`, `pvp-respawn-no-location`, `spectator-recovered`, `arena-world-loaded`, `arena-clone-reset`, `match-dead-player-gamemode`, `match-origin-saved`, `match-origin-warning`, `livetrade-start-error`, `stats-queue-error`, `update-invalid-response`.
  - Equipment Management: `equipment-loaded`, `equipment-all-loaded`, `equipment-empty-error`, `equipment-not-found`, `equipment-lobby-not-found`, `equipment-lobby-retry`, `equipment-lobby-still-incomplete`, `equipment-lobby-applied`, `equipment-event-not-found`, `equipment-event-retry`, `equipment-event-still-incomplete`, `equipment-event-applied`.
- **Wired Java Classes**: `EventPlugin`, `AutoEventManager`, `ConfigManager`, `EventSession`, `EventListener`, `VoidProtectionListener`, `MultiverseHelper`, `WorldStateManager`, `PvPListener`, `SpectatorRecoveryListener`, `ArenaManager`, `SpawnManager`, `MatchManager`, `LiveTradeSession`, `ConfigurationService`, and `WebServer`.
- **Audit Results**: 0 findings across all rules (D1–D9), 45/45 pytest tests passed, and clean `mvn clean package` build.

### 🌐 Stage B — the translations themselves (COMPLETED)

Cause: the earlier parity run had copied `messages_en.yml` over every bundle and
the restore from backup was partial. Effect: 1980 values across the six
translations were still byte-identical with the English master — a Spanish or
Japanese player read English for a third of the plugin.

Retranslated **1543 values** (de 125, es 284, fr 282, ja 284, pl 284, ru 284);
each bundle was edited textually, never round-tripped through PyYAML, and
re-parsed after every file. What is left is intentional: 437 values that are
identical because they carry no words — dividers (`&6&l━━━`), pure placeholders
(`{name}`, `&f{player}`), command syntax (`/pvp leave`), and terms that do not
change (`Arena`, `Chat`, `Items`, `Download`).

| Sprache | vorher | nachher |
|---|---|---|
| de | 200 | 75 |
| es | 358 | 74 |
| fr | 359 | 77 |
| ja | 354 | 70 |
| pl | 359 | 75 |
| ru | 350 | 66 |

### 🕳️ Two blind spots no detector reported

- **`messages.gui.pvpask.*` was German in all eight bundles — including
  `messages_en.yml`.** Cause: the section was authored in German and copied
  outward, so the "English master" itself carried `&cSpieler {player} ist nicht
  online!`. Effect: every non-German player saw German on the `/pvpask` screens,
  and no rule fired because the key existed everywhere and D5/D7 only look at
  Java sources. The 15 keys in the master are English now; German was already
  correct. **Correction:** the first pass rewrote only the master, which made the
  five other bundles stop counting as "identical with English" — so `es`, `fr`,
  `ja`, `pl` and `ru` silently kept the German text. Caught in review; all five
  are translated now (15 keys each).
- **`CounterOfferItemGui` was completely unlocalized.** Cause: its texts go
  through `createButton(...)`/item lore, which is not a registered player-facing
  sink, so D6 never saw them; D7 only warns. Effect: the counter-offer screen was
  German for everyone. 21 new keys under `messages.pvp-wager-gui.counter-offer-*`
  in all 7 bundles, `ResponseGui`'s arena/equipment/opponent-money labels moved to
  `messages.gui.response.*` (3 keys).

### 🇩🇪 German fallbacks that would have shown up in English sessions

`EventSession` carried German hardcoded fallbacks (`"&aDein Team hat gewonnen!"`,
`"&eEs ist ein Unentschieden!"`, `"&6&lTEAM {team} HAT GEWONNEN!"`,
`"&6&lGEWONNEN!"`) for keys that exist in every bundle. Effect: whenever a key
went missing the fallback answered in German regardless of `language:`. They now
mirror the English master. `ConfigurationService.getMessage` returned
`&cNachricht nicht gefunden: <path>`; it now emits the same
`&c[missing: <key>]` marker the rest of the code uses.

### 🔧 D8 false alarm fixed (detector, not the translation)

`bundle-placeholder-value` flagged any value containing the substring `TODO`.
Spanish `messages.selection.both-selected` = `&a&l¡TODO ELEGIDO!` is a finished
translation — *todo* means *all*. The check now recognises a TODO **marker**
(alone, before punctuation, or introducing a lowercase instruction) instead of
the bare substring, with a regression test
(`test_d8_does_not_read_spanish_prose_as_a_todo_marker`). Test suite: 32 → 33.

### 🔌 Stage C — D9: the unused keys (product decision taken)

Decision of 06.08.2026: **keep every key, wire the ones that were only missing a
call, delete nothing.** Four of the sixteen `WIRE?` candidates turned out to be
real gaps; the other twelve are duplicates of groups the code already uses in
full, so rewiring them would have been renaming for its own sake.

- **`messages.general.cooldown`** — cause: `CommandCooldownManager` shipped
  `ChatColor.RED + "Please wait " + seconds + " more seconds!"` as a compiled-in
  default, and only `ChallengeSubCommand` ever replaced it. Effect: any consumer
  built before that sub-command answered in English on a German server. The
  localized provider is now installed centrally in `EventPlugin` right after the
  manager is created (`messages.system.cooldown-wait`, falling back to
  `messages.general.cooldown`); the compiled-in default is a `[missing: …]`
  marker instead of an English sentence.
- **`messages.commands.pvprespond.request-expired` / `.requester-offline`** —
  cause: `PvPRespondCommand` asked for `expired` and `player-offline`, names that
  do not exist in its own section, so the fallback chain silently answered from
  `messages.request.*`. Effect: two keys written for this command were dead while
  the command showed the generic text. The call sites now use the real names.
- **`messages.commands.pvpdeny.request-removed-offline`** — cause: when the
  requesting player had logged off, `PvPDenyCommand` searched
  `getOnlinePlayers()` for exactly that offline player, which can never match.
  Effect: `/pvpdeny <name>` could not clear a request from someone who had left;
  it stayed pending until it expired. The branch now resolves the offline UUID,
  removes the request and reports this key.
- **`messages.draw.*` (6) and `messages.spectator.*` (6)** — kept. `DrawSubCommand`
  uses the complete `messages.command.draw.*` group, `SpectateSubCommand` uses
  `messages.command.pvp.spectate.*`.

D9: 113 → **107**, all recorded in `tools/i18n_audit_baseline.json` as accepted
stock so anything new stands out. `reports/d9_unused_keys_review.md` carries the
decision and the corrections to its own proposal.

### 📄 Stage C — D8: `messages.yml` stays

Decision of 06.08.2026: the file keeps shipping but is no longer maintained — no
new keys go in. The detector gained a `legacy_bundles_accepted` list so the
"delete it" reminder is answered instead of ignored; the file stays in
`legacy_bundles`, so it is still excluded from parity comparisons. Two
regression tests pin both directions: a legacy bundle that was *not* accepted is
still reported.

### 🔤 Stage D — D7: 266 → 0 warnings

Three different causes hid behind one number.

- **71 findings were protocol, not prose.** `WebServer` and `WebApiHandler` were
  reported for `"Content-Type"`, `"application/json; charset=UTF-8"`,
  `"/api/auth/login"`, `"Method Not Allowed"`, `"; Path=/; HttpOnly"` and the
  like. Renaming those breaks HTTP. The detector now recognises header names,
  MIME types, route paths, status reason phrases, cookie attributes and CORS
  value lists — every pattern anchored, so `"Method Not Allowed - bitte benutze
  POST"` is still a finding. Regression tests both ways.
- **41 findings were code identifiers.** `DIAMOND_CHESTPLATE`, `PVP_MATCH_PRE`,
  `THE_END`, the timestamp patterns `yyyyMMdd_HHmmss` and
  `yyyy-MM-dd'T'HH:mm:ss.SSSZ`, and the project's own `&c[missing: <key>]`
  marker — which must stay identical in every language, otherwise a broken key
  can no longer be read off the screen. Also covered by config plus tests; a
  shouted German sentence is still reported, upper case alone is no free pass.
- **82 findings were real strings that never reach a player** — Multiverse
  console commands and their log diagnostics, exception texts for the stack
  trace, `[SafeRespawn-PvP]` and `[VoidProtection]` log reasons, the German
  JSON/HTML of the web admin panel. Each one carries `// i18n-ignore` **with the
  reason next to it**, as the audit brief demands, rather than a config-wide
  silencer.

Four findings were genuine bugs and were localized instead:

- **`MoneySelectionGui`** showed `&6&lALL IN!` and the German `&c⚠ Setzt alles
  ein!` side by side on the same button — English title, German warning, in every
  language. Now `messages.gui.money-selection.all-in-title` / `.all-in-warning`.
- **`NegotiationGui`** displayed `&8Kein Geld` for both players' empty money slot
  → `messages.gui.negotiation.no-money`.
- **`ResponseGui`** labelled the opponent's chest `&6&lGegner Items` while every
  other label in that screen was translated →
  `messages.gui.response.opponent-items-title`.
- **`MessageUtil.formatItemList`** returned the literal `"no items"`, which is
  substituted as `{items}` into win/loss chat messages. `MatchManager` already
  read `messages.utility.no-items` for the same purpose; the shared utility now
  does too.

All four keys were added to all seven bundles with real translations.

### 🌐 Stage D — the web panel: D7's last 57 findings

Correction to the paragraph this replaces: the panel **does** have its own i18n —
`app.js` fetches `web/lang/<code>.json`, seven languages exist, and `index.html`
already carried 134 `data-i18n` attributes. The 57 findings were not "no
mechanism", they were code written past the mechanism.

- **6 were German developer comments** (`<!-- Welten werden dynamisch geladen -->`,
  a trailing `// ID ist der Key`). Cause: the web scanner only skipped lines that
  *start* with `//` or `*`. It now strips HTML and JS comments properly, quote-aware
  so `https://` is not mistaken for a comment, and honours the `i18n-ignore` marker
  that until now only worked in Java. Three regression tests.
- **38 were German inline defaults on elements that are already translated.**
  Cause: the markup was authored in German and the language files were added
  afterwards, so the untranslated fallback in the file was German — the same trap
  as `messages.gui.pvpask.*`, where the "English master" itself was German. Effect:
  German text flashes before `applyTranslations()` runs, and any element whose key
  is ever lost falls back to German for everyone. All 110 inline defaults plus two
  placeholder attributes now carry the English master text from `en.json`; not a
  single key or attribute was removed, so the rendered UI is unchanged.
- **13 were real strings that bypassed `i18n.t()`.** Four toasts
  (`Event nicht gefunden`, `Wähle zuerst einen Rüstungsslot aus`,
  `Wähle zuerst einen Slot aus`, the wrong-slot message), the item-picker tooltip
  and the armour-slot labels now resolve through the language files — six new keys
  in all seven bundles. `showToast(i18n.t('error.noBackup') || '…')` was dead code
  twice over: the key did not exist, and `i18n.t()` returns the key itself rather
  than a falsy value, so the German fallback could never run; the key exists now.
  The event/world default values and the placeholder examples (`&e&lEvent startet!`,
  `'display-name': 'Neue Welt'`) are config content, not UI, and were moved to the
  English master wording.

### 🇪🇸 A broken Spanish web panel nobody could see

Cause: `web/lang/es.json` had drifted **145 keys** behind `en.json` — a whole
generation of features (spawn configuration, item picker, rewards, win
conditions) was never added, while 179 keys under older names stayed behind.
Effect: a Spanish admin read raw key names such as `spawn.radius` and
`rewards.addItem` on screen, because `i18n.t()` returns the key when the entry is
missing. That looks like a layout glitch, not a missing translation, which is why
it was never reported.

All 145 keys were translated into Spanish. Every key the code uses now exists in
all seven web bundles.

**D8 now also checks the web bundles** (`web-bundle-missing-keys`,
`web-bundle-extra-keys`, `web-bundle-unreadable`) — nothing had ever compared
them. Three regression tests. Test suite: 39 → 45.

That check immediately surfaced the other half of the drift: 179 keys in
`es.json` and five each in `pl.json` / `ru.json` that `en.json` does not define.
They were first kept, by analogy with the D9 decision; the review pass below
found that wrong — unlike the D9 keys, nothing had ever decided to keep these —
verified all 189 dead and removed them. **Final state: all seven web bundles hold
the same 555 keys, D8 reports 0.**

### 🔁 Review pass over the finished plan

Re-ran every stage against the rules the plan sets out. Three things did not hold.

- **The baseline was hiding unfixed findings.** Alongside the 107 accepted D9
  keys it also carried the three D8 web-bundle findings. Those are drift, not a
  decision: `es.json` defined 179 keys and `pl.json`/`ru.json` five each that
  `en.json` does not have. Checked properly — the web code asks for 433 keys,
  `en.json` covers all of them, and not one of the 189 extras is requested via
  `i18n.t()` or `data-i18n` — so they were deleted rather than suppressed. All
  seven web bundles now hold the same 555 keys and the baseline contains D9 only.
- **German server text reached the translated panel.** `/api/auth/login` answers
  with `error: "Token fehlt"` / `"Ungültiger oder abgelaufener Token"`, and
  `app.js` rendered `data.error` verbatim — so the German sentence showed up on
  the login screen in all seven languages. The panel now uses its own
  `auth.invalidToken`, and a new `auth.rateLimited` (all seven bundles) covers
  HTTP 429, which previously misreported throttling as a bad token. `/api/reload`
  — the only response whose `message` is rendered — returns a neutral `OK` and
  the bare exception text; the sentence around it comes from the panel.
- **33 `// i18n-ignore` comments justified themselves with a false premise**
  ("deutschsprachiges Web-Adminpanel"), while the panel ships seven language
  files. Each one now states the reason that actually holds — that the field is
  never rendered, or that it is HTTP/HTML wire level.

Additionally, `messages.gui.pvpask.*` still held German in five bundles (see the
correction above), which the audit cannot see by construction.

### 🧹 D9 closed: 107 dead keys removed, one wired

The 107 keys were kept earlier because deleting without proof would have been
reckless. They were then verified dead on five independent routes — full-path
search across 129 source files, leaf-literal search (helpers prepend the prefix
at runtime), the *unchosen* candidates of every live helper chain, dynamic key
construction plus PlaceholderAPI and the web panel, and the admin documentation.
The decisive one: **not one of the 107 is even reachable as a fallback** — zero
appear as a non-selected candidate in any live chain.

Cause: they are **rename leftovers from three generations**, not missing
features. `messages.livetrade.both-ready` / `.cancelled` / `.change-detected` /
`.error-match-start` live on as `messages.livetrade.broadcast-*`;
`messages.wager.inventory-full-*` as
`messages.match-manager.not-enough-inventory` (`MatchManager:194-203`);
`messages.system.debug-*` as `messages.debug.messages.*`; `messages.wager.*` as a
whole is the retired chat wager flow the LiveTrade GUI replaced. `messages.prefix`
was decorative — the prefix comes from `config.yml settings.prefix`.

Effect was not a malfunction but a maintenance trap: 107 dead entries that look
like valid examples to anyone editing the bundles — which is exactly how the
Spanish web bundle drifted 145 keys.

**One real gap surfaced.** `SpectateSubCommand` never checked whether the player
is already spectating, so a second `/pvp spectate` silently teleported again.
That text was not deleted but moved into the live group as
`messages.command.pvp.spectate.already-spectating` (all 7 bundles) and wired up.

107 removed, 1 added, all seven bundles edited textually and re-parsed. `D2`
stayed at 0 throughout, which is the proof that nothing still in use was hit, and
`D8` at 0 proves no bundle was half-cleaned. The pre-cleanup bundles are kept in
`reports/backup_pre_d9_cleanup/` because the project is not under version control.

### 🔤 Leftover English labels in five translations

`reports/untranslated_values.md` lists every value byte-identical with the
English master. Most are identical for good reason — dividers, bare
placeholders, command syntax, product names. A review by language found 62 that
were not, and they sat in plain sight: a French player read `Items:` and
`Arena:` while the same file wrote `objet` and `Arène` elsewhere; a Japanese
player read `Status:` next to `ステータス`.

Translated: es 11, fr 12, ja 15, pl 9, ru 15 — the `Items:` / `Level:` /
`Status:` / `Filter:` labels, the debug category names (`Event`, `Match`,
`Teleport`, `System`, `Listener`, `Chat`) and the download hint. The yardstick
was each file's own usage, not a dictionary.

**German was deliberately left untouched**, even though it now tops the list
with 72: `Arena`, `Items`, `Level`, `Status`, `Chat`, `Event`, `Match`,
`Teleport`, `System`, `Listener`, `Admin`, `Download` and `Version` **are** the
German words. Translating them would be worse German, not better — the count is
not a quality metric.

Remaining: de 72, es 59, fr 59, ja 50, pl 62, ru 46.

### 🕸️ Three web-panel bugs found while restructuring it

- **The panel served stale language bundles after an update.** `StaticFileHandler`
  put `Cache-Control: public, max-age=3600` on *every* static resource, language
  bundles included. After an update the browser kept the old `/lang/<code>.json`
  for up to an hour while already rendering the new `index.html`, so every newly
  added key showed up as its raw key name on screen — `i18n.t()` returns the key
  when the entry is missing, and a cached bundle is indistinguishable from a
  bundle that never had the key. Panel code and bundles (`html`/`js`/`json`) now
  go out as `no-cache, no-store, must-revalidate`; only immutable assets (images,
  fonts) keep the one-hour cache. Note this cannot fix an *already* cached copy —
  one hard reload is still needed on the way to the fixed build.
  No audit rule can catch this class of bug: the bundles on disk were perfect.
- **`id="main-content"` existed twice** — on the auth wrapper `<div>` and on
  `<main class="content">`. `document.getElementById` returns only the first
  match, so the second element was unreachable by id and, worse, silently picked
  up the wrapper's `display:flex; min-height:100vh` from the `#main-content`
  rule. The id is gone from `<main>`; `showSection()` reaches it via `.content`.
  Pre-existing, verified against the 1.0.8 tree.
- **Nothing ever wrote `web-config.yml` into its own form.** Port, browser title
  and the six theme colour pickers always showed the values hardcoded in the
  HTML, no matter what the file said — `loadThemeFromConfig()` only pushed the
  colours into CSS variables, never into the inputs. New
  `populateWebConfigForm()` runs at the end of `populateSettingsForm()` and again
  from `resetTheme()`, so a reset moves the pickers instead of leaving them stale.

### 🔄 Update-Check und Rate-Limit — fünf Fehler beim Dokumentieren gefunden

Beim Schreiben von `UPDATE_CHECK_CONCEPT.md` (Konzept-Vorlage für andere
Plugins) mussten alle Behauptungen gegen den Code belegt werden. Dabei fiel auf:

- **`/eventpvp version` stürzte ab, wenn `check-on-startup: false` gesetzt war.**
  `EventPlugin` legte den `UpdateChecker` nur an, wenn `enabled` **und**
  `check-on-startup` beide wahr waren; `handleVersion` rief ihn ungeprüft auf.
  Wer den Startup-Check abschaltete — völlig legitim, etwa wegen Firewall —
  bekam eine NullPointerException. Der Checker wird jetzt immer angelegt, nur
  der Abruf beim Start hängt an der Konfiguration. Zusätzlich respektiert der
  Befehl `enabled` und macht dann gar keinen HTTP-Request mehr.
- **Der Befehl konnte nie ein frisches Ergebnis zeigen.** Er plante seine
  Ausgabe 20 Ticks voraus, während `checkForUpdates()` seinen HTTP-Aufruf
  ebenfalls 20 Ticks voraus plante. Beide feuerten im selben Tick — der Request
  hatte nicht einmal begonnen. Angezeigt wurde immer das gecachte Ergebnis vom
  Serverstart. `UpdateChecker` nimmt jetzt einen Callback entgegen, der nach
  Abschluss im Main-Thread läuft, im `finally` — damit auch ein
  fehlgeschlagener Abruf zurückmeldet. Dafür neu: `messages.system.check-failed`
  in allen 7 Bundles, statt fälschlich „ist aktuell" zu behaupten.
- **`update-check.startup-delay-ticks` war wirkungslos.** Der Wert wurde
  gelesen und hatte einen Getter, aber `UpdateChecker` plante mit fest
  verdrahteten 20 Ticks; `getStartupDelayTicks()` wurde nirgends aufgerufen.
  Ein Schalter, der nichts tut, ist schlimmer als kein Schalter — er wirkt
  jetzt.
- **Vier Meldungen zeigten wörtlich `{version}`.** `update-available`,
  `download`, `up-to-date` und `checking` trugen einen Platzhalter, den kein
  Aufrufer ersetzte: Admins lasen „Update available! {version}" auf dem
  Bildschirm. In allen 7 Bundles entfernt; `current` und `latest` behalten ihn,
  dort wird er gefüllt. Die beiden Aufrufstellen nutzen jetzt die vorhandene
  Überladung `getMessage(pfad, "version", wert)` statt `.replace(...)` — das
  war ohnehin nötig, weil D4 sonst die „multi-statement AST placeholder scope
  pollution" meldet, gegen die diese Überladung in Stufe 3 eingeführt wurde.
- **Das Rate-Limit der Web-API sperrte Admins dauerhaft aus.** `checkRateLimit`
  zählte Requests je IP, setzte den Zähler aber **nie** zurück — trotz des
  Kommentars „100 requests per window" gab es kein Zeitfenster. Nach 100
  Requests antwortete der Server bis zum Neustart mit HTTP 429, und allein das
  Panel fragt jede Minute `/api/status` ab. Jetzt ein echtes 60-Sekunden-Fenster
  mit `Retry-After`-Header; verwaiste Einträge werden aufgeräumt, damit die Map
  nicht unbegrenzt wächst.

### 🔍 Vier weitere Fehler beim zweiten Hinsehen

Diese Sorte ist die unangenehmste: Das Plugin lief, meldete keinen Fehler — und
arbeitete trotzdem falsch.

- **Der Gson-Zweig war faktisch tot, und das Log blieb stumm.** Ob Gson benutzbar
  ist, wurde mit `getServicesManager().getRegistration(Gson.class)` geprüft.
  Gson ist aber eine Bibliothek auf dem Klassenpfad und kein Bukkit-Service —
  die Abfrage liefert *immer* `null`. Also lief immer der Handparser, der per
  `indexOf("\"version_number\"")` den ersten Treffer aus dem rohen JSON schnitt.
  Der gab zusätzlich `null` zurück, wodurch der nachfolgende
  `if (versions != null)`-Block übersprungen wurde: **die „UPDATE VERFÜGBAR"-
  Meldung im Serverlog ist in keiner Version je erschienen.** Gson wird jetzt
  direkt benutzt, der Handparser ist entfernt.
- **Die erste statt der höchsten Version.** `versions.get(0)` verlässt sich auf
  eine Sortierung, die die Modrinth-API nicht zusichert. Jetzt wird das Maximum
  über alle Einträge gebildet.
- **Vorabversionen wurden nicht gefiltert.** Eine veröffentlichte Beta hätte
  allen stabilen Servern ein „Update" gemeldet. Neu: `stable-only` (Standard
  `true`) prüft `version_type == "release"`.
- **`1.0.0-RC1` galt als neuer als `1.0.0`.** Der Vergleich entfernte alle
  Nicht-Ziffern, *bevor* der Suffix abgeschnitten wurde — aus `1.0.0-RC1` wurde
  dadurch `1.0.01`. Jetzt wird zuerst am `-`/`+` getrennt, und bei gleichem
  Zahlenteil gilt eine Vorabversion als älter als ihr eigenes Release.
  Nachgemessen an 16 Fällen inklusive `null`, unparsbarem Text und
  Zahlenüberlauf.

Dazu eine Kleinigkeit mit Aussenwirkung: Der User-Agent wies sich als
`EventPVPPlugin/<version> (kontakt@email.com)` aus — eine erfundene Adresse,
obwohl Modrinth eine erreichbare verlangt. Sie kommt jetzt aus
`settings.update-check.contact`, der Plugin-Name aus der `plugin.yml`.

Lehre, die in `UPDATE_CHECK_CONCEPT.md` §10 festgehalten ist: Ein Platzhalter im
Template ist eine Zusage, eine geratene Wartezeit ist keine Synchronisation —
und eine Verfügbarkeitsprüfung, die immer dasselbe Ergebnis liefert, ist keine
Prüfung. Der billigste Gegentest wäre gewesen, nach dem Serverstart einmal ins
Log zu sehen: Dort stand nie eine Zeile zum Update-Check.

### ⏳ Deliberately still open

- **`messages.yml`** — kept but unmaintained, answered via `legacy_bundles_accepted`.
  It still holds the removed keys; that is deliberate, the file is not maintained.
- **`sidebar.web`** — the only web key left with no reference after the
  restructure. Kept, not deleted, per the "no deleting without asking" rule.

**Every rule now reports 0 and the baseline file is empty** — nothing is being
suppressed any more.

---

## Added & Improved

### 🌍 Multiverse world management from the web panel

Until now the panel only ever edited YAML. The **world ID** was a free-text
field, so an admin had to know by heart which worlds exist on the server,
whether they are loaded, and whether another preset already claims them. A typo
produced a preset pointing at nothing — and that only surfaced when the event
started.

**What the field became.** In the *new world* dialog the world ID is a dropdown
of the worlds that really exist, each line carrying everything needed to decide:

```
arena_1     — NORMAL · Loaded   · already used by a preset   (disabled)
nether_pit  — NETHER · Unloaded
EventLobby  — NORMAL · Loaded   · Used by: pvparena (lobby-world)
──────────────
✎ Enter a custom world ID…
```

Worlds that already are a preset key are offered but disabled: the preset key
*is* the world name, so a second preset on the same world would silently
overwrite the first. The *custom ID* option is deliberately kept — a preset
without any world on the server is a legitimate placeholder, and the panel now
labels it as one (⚪ *Placeholder*) instead of letting it look configured.

**Creating a world.** A new *Multiverse* tab in the world editor creates the
world without leaving the browser. Everything is optional: environment, world
type, seed, generator, generator settings, biome, structure generation and spawn
adjustment. Biome and generator settings exist only in the Multiverse-Core 5 API
and are greyed out with a note on older setups instead of silently doing nothing.

Because chunk generation can take a minute, creation and deletion answer
immediately with a job ID that the panel polls (`GET /api/mvworlds/job`) — no
request left hanging in an HTTP timeout. Every world operation is dispatched onto
the server main thread; the HTTP handlers run on the `HttpServer` executor and
must not touch Bukkit directly.

**Load, unload, overview.** Each world card shows 🟢 *Loaded*, 🟡 *Unloaded* or
⚪ *Placeholder* with the fitting action. A collapsible *Server worlds* panel
lists **all** Multiverse worlds — also those without any preset — including who
uses them and load/unload buttons.

**Deleting.** Deleting a preset is a YAML edit; deleting a world is not. The two
are therefore separated in the dialog: the *"also delete the world on the
server"* checkbox is **off by default**, carries a red warning, reveals an
on-by-default *"create a backup first"* option, and unlocks the delete button
only once the world ID has been typed out. The world editor also offers *"delete
world only"* for the reverse case — drop the world, keep the preset as a
placeholder. Deletion runs unload → backup → delete, so the backup is never a
snapshot of a world still being written to.

Guard rails, because this deletes directories: world names are restricted to
`[A-Za-z0-9_-]` (no `.`, `/` or `\`), reserved server directories such as
`plugins` and `logs` are refused, and the server's main world can neither be
unloaded nor deleted. `seed`, `generator` and `biome` additionally may not
contain spaces — on the command backend they end up in a console command line,
where a space would be a flag injection. `MvWorldInputValidationTest` covers
these cases.

### 🐞 Two bugs found in testing the world management

Both had the same shape — **a failure that stayed silent** — and both were introduced
with the feature itself.

**The backup that was never written, while the world was deleted anyway.**
`MultiverseHelper.backupWorld()` caught every error and returned quietly: a missing
world folder, a zip failure, and — inside `zipFolder` — even an `IOException` per
individual file, which produced an archive silently missing exactly the files that
could not be read. The delete path logged the failure and then deleted the world
regardless. Ticking *"create a backup first"* could therefore lose a world with
nothing to restore from.

Fixed by splitting the two use cases: `createBackup()` throws and returns the written
file, `backupWorld()` stays lenient for the event-regeneration path where a backup is
a bonus rather than a promise. A failed backup now aborts the deletion and names the
reason in the panel; a partial or empty archive is removed instead of being left
behind looking like a valid backup. `session.lock` is skipped — on Windows it is the
likeliest read error, and it does not belong in a backup anyway.

**Unloading a world made it look like it had never existed.**
`listWorldsSync()` returned `Collections.emptyList()` on any failure or timeout, and
the endpoint still answered `success: true`. The panel has no way to tell "this server
has no worlds" from "the lookup failed", so *every* configured world fell back to
⚪ *Placeholder* and offered *"Create world"*. Unloading is precisely when this
triggers: the main thread is busy saving the world and unloading its chunks.

Failure is now reported as failure — the endpoint answers `success: false` with
`mv.error.listFailed`, the panel keeps the last known state, marks it stale, disables
creation and deletion on top of data it cannot trust, and offers a retry. The status
badge gained an *unknown* state so it stops asserting something about a world it
currently cannot see.

`WorldBackupTest` covers the backup contract (archive contents, missing folder, empty
archive, the lenient legacy path).

### 🗺️ Worlds that live inside the main world — the second round of fixes

Field testing on a Purpur 26.2 server with Multiverse-Core 5.7.3 surfaced two failures
with one shared root cause. The log gave it away:

```
Saving chunks for level 'ServerLevel[world]'/minecraft:newworld
java.io.IOException: World folder not found: C:\...\purpur-26-2\.\newworld
```

On modern servers a world created through Multiverse is often a **dimension inside the
main world** — its folder is `world/dimensions/minecraft/newworld/`, not
`container/newworld`. Every folder lookup in the plugin assumed the container layout, and
the folder scan additionally required a `level.dat`, which dimension folders do not have.
Consequences: the pre-delete backup failed with "World folder not found" (and correctly
aborted the deletion — so the user could not delete with backup at all), and `existsOnDisk`
was always false for such worlds.

The second failure was independent but hit the same feature: after unloading, the world
disappeared from the panel's world list entirely and its card fell back to ⚪ *Placeholder*
with a *"Create world"* button — for a world that existed and could be `/mv load`ed.
The list only read `WorldManager.getWorlds()`; since the `WorldStore` split in
Multiverse 5.7 that view does not reliably contain unloaded worlds.

**Fixes:**

- World folders are now resolved through a chain: Bukkit's `getWorldFolder()` while the
  world is loaded (authoritative, knows dimensions) → Multiverse 5.7's
  `getOfflineWorldFolder()` (guarded — on older 5.x the call falls through instead of
  throwing `NoSuchMethodError`) → the classic container path → a
  `container/<world>/dimensions/<namespace>/<name>` scan. The delete flow resolves the
  folder **before** unloading, while Bukkit still knows it, and hands it to the backup.
- A world folder now counts as existing with `level.dat` **or** a `region/` directory.
- The MV5 world list is the union of `getLoadedWorlds()`, `getUnloadedWorlds()` and
  `getWorlds()`.
- Deleting a resolved folder gained stricter guards, because it may now live *inside* the
  main world: the canonical path must be strictly below the container and must not be the
  root folder of any loaded world.
- The build dependency moved to Multiverse-Core **5.7.3**; nothing is bundled and older
  5.x servers keep working through the fallback chain (verified by loading the plugin
  classes against the 5.6.1 jar).
- Failed world jobs are now logged to the console as well — a failed *create* used to be
  visible only in the panel.

### 💾 Backup worlds panel — restore worlds from the web interface

Below *Server worlds* sits a new collapsible **Backup worlds** panel listing every zip in
`plugins/<plugin>/backups/` with world name, date and size (parsed from the
`<world>_<yyyyMMdd_HHmmss>.zip` file name).

- **Restore**: a dialog asks for the world name, prefilled with the original and editable.
  An existing world is never overwritten — the target is rejected both in the dialog and
  again on the server. Restoring runs as a background job: extract to
  `container/<target>` with **zip-slip protection** (an archive entry trying to escape via
  `../` aborts the whole restore and cleans up), skip `session.lock`, then import and load
  the world through Multiverse. A backup of a dimension world (no `level.dat`) is
  extracted anyway; if the import then fails, the job says why.
- **Delete**: removes the zip after a confirmation. Only the file — never a world.
- New endpoints `GET /api/mvworlds/backups` and `POST /api/mvworlds/backup-action`;
  `WorldRestoreTest` covers zip-slip, path tricks in file names, metadata parsing and
  deletion.

### 🔌 Multiverse-Core 5 API — and why MV4 servers still work

World operations now use the typed `WorldManager` API where it exists. This also
fixes a bug that had nothing to do with the panel:

> `MultiverseHelper.deleteWorld()` sent `mv delete <world>` and, 40 ticks later,
> a bare `mv confirm`. Multiverse-Core 5 ships with
> `command.use-confirm-otp: true`, which turns the confirmation into
> `/mv confirm <3-digit code>` — a code no outside plugin can know. The
> confirmation therefore expired unanswered and **the world was never deleted**.
> Event world cleanup was affected as well, not just the new feature. The API
> knows no confirmation step at all.

The dependency is `provided`, so nothing is shaded into the JAR. The
compatibility risk is real but contained: **every** MV5 type lives in
`Mv5WorldBackend`, which `MvWorldService` instantiates *reflectively* and only
after `Class.forName("…MultiverseCoreApi")` succeeded. Naming the class directly
would let bytecode verification pull the MV5 types in before the `try` block ever
runs. `MvWorldService` itself contains no MV5 type reference — the only mention
is the `Class.forName` string constant.

On MV4, or with no Multiverse at all, `LegacyCommandWorldBackend` takes over with
the familiar `mv …` console commands. Its world list needs no Multiverse
whatsoever: Bukkit reports the loaded worlds, a folder scan finds the unloaded
ones. Without Multiverse the panel still lets you pick worlds — it just hides the
create/load/unload buttons.

### 🧭 Web panel split into base and expert settings

The configurator showed every admin everything at once: "General Settings"
stacked 9 cards with 32 fields, and the event editor opened with 7 tabs. It is
now split in two — but the split is by **risk, not by frequency**.

> **Base** — everything needed to set an event or world up *correctly*, even if
> it is only ever touched once. Rarity is not a reason to hide a setting.
>
> **Expert** — everything whose change is destructive, experimental or
> infrastructural, where the default is almost always right. Rule of thumb: if a
> wrong click here destroys data, cuts off access or causes surprising
> behaviour, it belongs in expert.

- **New sidebar group** with an **Expert Settings** section (`#section-expert`),
  carrying a warning banner. "General Settings" is down to 6 cards.
- **Moved into expert**: `save-player-location` — switching it off is
  experimental and players can lose their position. It shares a new
  "Player data protection" card with the inventory snapshots, since both protect
  player state and both lose data when disabled. Also expert: world loading and
  command restriction, arena regeneration backups, the inventory-space check,
  the refresh interval (own "Performance" card) and the web server port/title.
- **Moved back out of expert**: the wager limits (min/max) and the three
  integration toggles — normal setup, not risk.
- **Event editor 7 → 8 tabs, world editor 3 → 4 tabs.** Win condition, game mode
  (team size) and the event messages are ordinary base tabs. A first attempt had
  put them behind the expert tab; that was wrong and is corrected here — those
  are precisely the settings one needs to configure an event at all, and on
  non-English servers the messages *must* be edited. The expert tabs now hold
  only world regeneration and clone source, the sole fields that overwrite a
  world.
- **Inline warnings** on the three genuinely risky switches (snapshots off,
  save-position off, world regeneration), on top of the page banner — the risk is
  shown where the click happens, not only at the top of the page.
- **i18n**: 15 new keys translated into all 7 web bundles (555 → 570 per file,
  key sets identical). `editor.tabSettings` — dead after the first restructure —
  is wired up again as the world editor's settings tab. No existing key renamed
  or deleted, no `updateConfig()` path and no editor binding changed: the
  restructure is markup movement only, so `config.yml`, `worlds.yml` and the REST
  API are untouched.
- **`.tabs` had neither `flex-wrap` nor `overflow-x`** — with 8 tabs the bar
  would have overflowed the 900px modal. It wraps now.

### 📚 Dokumentation überarbeitet — vier Dateien mit echten Falschangaben

Beim Schreiben der Update-Check-Vorlage mussten alle Aussagen gegen den Code
belegt werden. Dabei stellte sich heraus, dass mehrere Dokumente nicht nur
unvollständig, sondern **falsch** waren — jemand, der danach gebaut hätte, wäre
aufgelaufen.

- **`WEB_API_DOCUMENTATION.md` neu geschrieben.** Die alte Fassung beschrieb
  *„HTTP Basic Auth mit Passwort"* — real ist es seit 1.0.8 ein Einmal-Token
  plus `HttpOnly`-Session-Cookie. Alle sechs Auth- und Sprach-Endpunkte fehlten,
  und der dokumentierte `POST /api/auth/token` existiert überhaupt nicht (real:
  `/api/auth/login`). Ebenfalls falsch: *„Derzeit gibt es kein Rate Limiting"* —
  es existiert seit 1.0.8. Am folgenschwersten war die fehlende
  **`data`-Verpackung**: Wer nach der alten Doku ein Speicher-Request baute,
  schrieb nichts, weil der Server `requestBody.get("data")` erwartet. Neu dazu:
  Fehlercode-Tabelle, Caching-Verhalten, cURL-Beispiele mit Cookie-Handling und
  `credentials: 'include'` in den JS-Beispielen.
- **`WEB_INTERFACE_README.md` aktualisiert.** Neben der neuen Basis/Experte-
  Struktur wurden vier Altfehler korrigiert: „8 Sprachen" (es sind 7), die
  YAML-Vorschau war als Sidebar-Eintrag geführt (sie ist ein Button in der
  Kopfleiste), der Auth-Endpunkt hiess dort ebenfalls `/api/auth/token`, und der
  Update-Check war als Web-Interface-Einstellung gelistet, obwohl das Panel ihn
  gar nicht anbietet.
- **`tools/AUDIT_DOKUMENTATION.md` korrigiert und erweitert.** Zahlen
  nachgezogen (Web-Bundles 555 → 570, `data-i18n`-Attribute 134 → 135) und zwei
  **Blindstellen** dokumentiert, die kein Detektor sieht: der Browser-Cache
  schlägt jede Bundle-Prüfung (auf der Platte war alles korrekt, D8 meldete 0,
  und der Admin las trotzdem rohe Keys), und **D9 prüft die Web-Bundles gar
  nicht** — die Regel iteriert nur über `messages_en.yml` und liest
  `web/lang/*.json` ausschliesslich als Fundquelle für benutzte Keys, ein toter
  Web-Key wird also nie gemeldet. Dazu zwei neue Arbeitsregeln (9 und 10).
- **`UPDATE_CHECK_CONCEPT.md` neu.** Erklärt den Update-Mechanismus dieses
  Plugins als Konzept, mit Ursache und Wirkung aller neun gefundenen Fehler.
- **`UPDATE_CHECK_TEMPLATE.md` neu**, bewusst *ausserhalb* dieses Projekts unter
  `selfmadePlugins/Plugins/` abgelegt: eine projektunabhängige, kopierfertige
  `UpdateChecker`-Klasse für andere Plugins, mit durchgerechnetem Einbau-Beispiel.
  Der Code darin ist gegen `spigot-api 1.20.1` kompiliert und die
  Versionsvergleichs-Tabelle an 16 Fällen nachgemessen — dabei fiel ein
  Zahlenüberlauf auf, der ein Update gemeldet hätte, das es nicht gibt.

### 🌍 Complete Localization (Zero Hardcoded Messages)
- **Interactive Chat Buttons & Hover Text**:
  - Replaced hardcoded text in `/pvp` challenge request buttons (`[ACCEPT]`, `[DECLINE]`, `[OPEN GUI]` and hover descriptions) with configurable message keys (`messages.command-request.btn-*`).
- **Wager Negotiation & Counter-Offers**:
  - Fully localized all negotiation outcomes in `NegotiationGui` and `CounterOfferItemGui` (offer accepted/declined, opponent offline, player in match, insufficient funds, counter-offer sent/received).
- **Match Management & Announcements**:
  - Fully localized match timer announcements ("TIME'S UP!", countdown warnings), spectate hover text, win/loss item & money distribution summaries, wager return notifications, and server shutdown broadcasts in `MatchManager`.
- **Environmental & PvP Death Causes**:
  - Moved all 13 death cause descriptions in `PvPListener` (*fall*, *fire*, *lava*, *void*, *drowning*, *suffocation*, *starvation*, *lightning*, *explosion*, *magic*, *wither*, *contact*, *unknown*) to language configuration keys under `messages.pvp-listener.cause.*`.
  - Localized player kill broadcasts, double-death draw announcements, and spectator exit messages.
- **Event World Access & Commands**:
  - Localized command blocking and world access restriction messages in `WorldChangeListener` and `EventSession`.
- **Admin & Utility Commands**:
  - Localized `/pvp` GUI usage hint in `PvPUnifiedCommand`.
  - Fixed key path mismatches in `EventPvpCommand` (`messages.command-help.eventpvp.*` and `messages.debug.messages.*`), resolving raw key displays (e.g. `header`, `reload`, `version`, `debug`, `status-label`, `level-label`, `active-categories`, `use-debug-help`).
  - Fully localized the `/eventpvp debug` messages, categories overview, status header, level lists and help output. (The unused standalone `DebugCommand` class this originally also touched has since been removed — see *Removed dead code* below.)
  - Fixed missing key definitions under `messages.pvpask.*` in `PvPAskCommand` (`not-online`, `already-in-trade`, `target-in-trade`, `pending-request`, `target-pending`, `trade-error`, `self-request`).

### 🛠️ Automated Localization Audit Suite & Tooling
- Introduced permanent production-grade Python audit tools:
  - `tools/scan_hardcoded_messages.py`: Deep scanner for hardcoded strings, unlocalized color codes, and natural language heuristics in Java and Web code.
  - `tools/verify_key_usage.py`: Two-way binding auditor verifying Code ➔ YAML key existence and YAML ➔ Code orphan key detection.
- Created `run_scans.bat`: Interactive Windows Command Prompt menu allowing single-click execution of localization audits with automatic report generation and user confirmation prompts.

### 🔬 Audit Suite Rewritten (`tools/i18n_audit.py`)

The two scanners above reported "clean" while real, player-visible localization bugs were shipping. Both had structural blind spots:

| Scanner | Blind spot |
|---|---|
| `scan_hardcoded_messages.py` | Line-based. Any line containing a `getMsg` was cleared wholesale, so the hardcoded half of `sendMessage(getMsg("x") + "&cFehler")` was invisible. `"INFO" in line` discarded whole messages. German prose inside comments was reported as a finding. Enum constructor arguments such as `LEVEL_3(3, "Vollständig")` were not a `sendMessage` line and were never examined. |
| `verify_key_usage.py` | Guessed among ~16 candidate prefixes and accepted the first hit, so a key bound to the wrong section still "resolved". It could not model a helper whose final lookup defaults to the key itself. Its orphan check substring-searched the concatenated codebase for *leaf* names — and leaves like `name` or `title` match nearly every Java file. |
| both | `--project-root` defaulted to the working directory, so running them from inside `tools\` scanned `tools\` and reported zero issues. |

Replaced by a single tool with a shared analysis core (`tools/i18naudit/`):

- **`javaparse.py`** lexes each source file once: string literals with exact positions, comments masked, physical lines grouped into logical statements, and the enclosing call plus argument index determined for every literal. Detectors ask *"is this literal argument 0 of a lookup, or an argument of `sendMessage`?"* instead of matching raw text.
- **`resolvers.py`** reads the message helpers out of the source and derives their real lookup chain, including guarded branches and stripped prefixes. `--list-helpers` prints what it found:
  ```
  EventPvpCommand.getDebugMsg  (key param 'key')
       40: messages.debug.help.<key> [strip 'help-'] [only if key startsWith ['help-', 'level-']]
       44: messages.debug.messages.<key>
       48: messages.system.<key>
       52: messages.debug.<key>  <-- DEFAULTS TO THE KEY ITSELF
  ```
  A key counts as resolved only if one of *its own helper's* steps matches.
- **Nine rules**: D1 key-as-default, D2 missing-key, D3 yaml-boolean-key, D4 placeholder-mismatch, D5 untranslatable-display-name, D6 hardcoded-message, D7 natural-language-literal, D8 bundle-parity, D9 unused-key.
- **`tools/i18n_audit_config.yml`**: all heuristics, prefix maps, ignore lists, per-rule severity (`critical`/`warning`/`info`/`off`) and dynamically addressed key roots are versioned config now. Inline `// i18n-ignore` and `// i18n-ignore-next` markers work in Java source.
- **Baseline & CI**: `--write-baseline` freezes known debt into `tools/i18n_audit_baseline.json`; later runs report only new findings, so `--strict --fail-on critical` can gate CI. Exit codes: `0` clean, `1` findings at or above the threshold, `2` tool error.
- **25 regression tests** (`python -m pytest tools/tests`) — one positive and one negative case per rule, each modelled on a bug that actually shipped, so a regression in the analysis core fails a test instead of producing a quiet "clean" report.
- **False positives removed after the first full run:**
  - `getString` reads whatever configuration it is invoked on. `config.getString("settings.language")` and `section.getString("helmet")` were being judged against the language files and reported as missing message keys. The receiver is now taken into account (`message_receiver_patterns`), which removed 24 bogus D2 findings.
  - A colour code alone no longer counts as display text, so separators (`"&7, "`), closing tags (`"]&r"`) and prefix constants (`"&8[&bDEBUG&8]"`) are no longer reported by D7; a single word ending in a colon (`"&7Ausgabe: "`) still is.
  - Enum constant arguments are reported by D5 only; D7 no longer duplicates them.
  - The wager GUI helper `t(key)` was unknown to the resolver because it delegates to `getMessage("messages.gui." + key)` instead of calling `getString` itself. Helper discovery now follows such delegations, which removed **158 false "unused key" reports** on keys the GUI reads on every click — and surfaced 14 genuinely missing keys that the same blind spot had hidden.
  - Added ignore patterns for `SimpleDateFormat` strings (`"HH:mm:ss.SSS"`) and bare command literals (`"/eventpvp webtoken"`), while a sentence that merely starts with a slash is still reported.
- `scan_hardcoded_messages.py` and `verify_key_usage.py` remain as thin forwarding wrappers; their docstrings record why they were replaced.
- Reports moved from the project root to `reports/`; the stale root and `tools/` report artifacts were deleted.
- `run_scans.bat` reworked: project root derived from the script location, options for full/critical/helper-chain/test/baseline runs, and a keypress-driven prompt after each run so results stay on screen until you return to the menu or exit.

### 🛡️ Embedded Language Fallback System
- Updated `CoreConfigManager` to set the embedded `messages_en.yml` resource as default stream for Bukkit's `YamlConfiguration`.
- Custom or incomplete translation files (e.g. `messages_es.yml` or customized `messages_de.yml`) now fall back to English for any missing keys without requiring manual file regeneration.
- Synchronized all 7 active language bundles (`messages_de.yml`, `messages_en.yml`, `messages_es.yml`, `messages_fr.yml`, `messages_ja.yml`, `messages_pl.yml`, `messages_ru.yml`) and the master copy `messages.yml` to 100% key parity.
- `messages.yml` updated as an exact master copy of `messages_en.yml`.

## Fixed & Robustness Enhancements

### 🐛 Rule D1 Key-As-Default Fallback Fixes (Stage 1)
- Replaced unsafe key-as-default fallbacks across 13 message helper methods in `WebTokenSubCommand`, `EventPvpCommand`, `PvPAskCommand`, `PvPInfoCommand`, `PvPWagerGuiCommand`, `LiveTradeGui`, `LiveTradePlayer`, `LiveTradeSession`, and `PvPListener` with explicit null-lookup checks, missing key warnings logged once per path to console, and visible fallback markers (`&c[missing: <key>]`).

### 🐛 Debug Output Bugs Found By The New Audit

- **`/eventpvp debug` printed the raw key `status-header` as its heading.**
  `EventPvpCommand.getDebugMsg(...)` ended in `getString(path, key)` — the key served as its own default, so an unmapped key looked exactly like a real message. It now returns a visible `&c[missing: <key>]` marker and logs each miss once to the console (rule D1).
  Added `status-header`, `categories-header`, `output-label`, `output-set`, `category-separator`, `category-entry-level` and `category-entry-name` to all 7 language files. `use-level-change`, `use-output-change` and `level-values` were redirected to the already existing `level-usage`, `output-usage` and `valid-levels` keys instead of duplicating them.

- **`/eventpvp debug help` never showed the `on` / `off` entries.**
  Those keys under `messages.debug.help` were unquoted and had already been rewritten to `true:` / `false:` by a YAML round-trip. YAML 1.1 resolves such plain scalars to booleans, so the runtime lookup of `debug.help.on` always missed while the file still looked correct. All bundles now quote them (rule D3).

- **German words appeared even with `settings.language: en`** (e.g. `Vollständig`, `Konsole`, `Spieler`).
  `DebugLevel`, `DebugOutput` and `DebugCategory` stored German display names that were substituted into otherwise translated templates via `.replace("{level}", level.getDisplayName())`. The bundles were translated; the injected value was not.
  Each constant now carries a language-neutral English fallback **and** a `getTranslationKey()`. Chat output resolves the key through the new `messages.debug.enums.*` section (26 entries per language across all 7 bundles); the plain name is kept for console logs and comparisons (rule D5).
  *Note:* Debug mode was subsequently streamlined into the persistent 3-state system (`settings.debug: "off" | "on" | "full"` toggled via `/eventpvp debug [on|on full|off]`).

- **Hardcoded German messages sent to players.**
  The equipment debug messages in `EventSession` (8 call sites) moved to the new `messages.equipment.*` keys; the `Debug-Kategorien` heading and the category list rows in `EventPvpCommand` now come from the bundles (rule D6).

- **Silently dropped placeholders.**
  `level-label`, `current-level` and `level-set` lacked the `{number}` placeholder the code substitutes, so the numeric debug level was never displayed. `level-usage` and `output-usage` gained `{label}` so the usage hint shows the actual command (rule D4).

- **Removed dead code.** `de.zfzfg.core.commands.DebugCommand` was never instantiated and no `debug` command existed in `plugin.yml`, yet it held a second, divergent debug-status implementation with German hardcoded fallbacks — a standing source of exactly this confusion. `/eventpvp debug` is and remains the live path.

Verified end to end: `mvn clean package` builds, and replaying the lookup chain against the bundles yields `Debug Status` / `Level: Full` for `en` and `Debug-Status` / `Vollständig` for `de`.

### 📦 Inventory & Item Loss Prevention
- Added defensive `player == null || !player.isOnline()` checks across all `InventoryUtil` methods (`giveItems`, `canFitItems`, `hasSpaceForItems`, `clearInventory`).
- Added automatic natural ground-dropping logic in `giveItems(...)` for any leftover items if a player's inventory becomes full when receiving wager rewards or returned items.

### 📍 Location & World Safety
- Added `loc.getWorld() != null` guards in `LocationUtil.getCenterLocation(...)` to prevent potential `NullPointerException` errors during multi-world comparisons.
- Safe handling of delayed teleports and item distribution when players disconnect mid-match or during countdowns.

### 🌐 Web Interface: Precision Dirty-Tracking, Public-URL & Web Server Safety

- **Baseline Snapshot & Deep-Equality Tracking (`CONFIG_BASELINE` & `isDeepEqual`)**:
  - Replaced naive action-history counting with an exact deep snapshot engine (`CONFIG_BASELINE`).
  - `getRealUnsavedChanges()` compares client configuration memory directly against the baseline across all 4 YAML files (`config.yml`, `worlds.yml`, `equipment.yml`, `web-config.yml`).
  - **No more false-positive dirty states**: Reverting an edited field back to its original value (`A -> B -> A`), focusing/blurring inputs without modifications, or opening and saving unchanged Event, World, or Equipment modal editors no longer triggers the "Unsaved Changes" indicator or Save button.
  - **Clean modal handling**: Event, World, and Equipment editors snapshot their pre-edit state (`currentEditing*Original`) and perform deep-equality validation on save, closing cleanly with an informative toast (`info.noChanges`) if no values were altered.
- **Decoupled Immediate Server Operations**:
  - Actions that execute immediately on the server — such as interface language switching (`POST /api/language/save`), inventory provider switching (`POST /api/inventories/provider`), Multiverse world actions, backup creation/restoration, and server reloading — now synchronize `CONFIG_BASELINE` immediately, preventing spurious unwritten YAML draft states.
- **Selective YAML Persistence (`saveAllConfigs`)**:
  - The "Save All Configs" button evaluates actual category diffs and dispatches POST requests (`/api/config/save`, `/api/worlds/save`, `/api/equipment/save`, `/api/webconfig/save`) **only** for categories that genuinely changed.
  - On successful save, `CONFIG_BASELINE` and `localStorage` emergency backups are updated, the change history is reset, and the sync badge is immediately restored to green.
- **Web Server Settings: Public URL, Port Safety & Title Removal**:
  - **Configurable Public URL (`web.publicUrl`)**: Added an explicit Public URL field in Web Server Settings. Admins input a clean base URL (e.g. `http://localhost` or `http://myserver.net`), while the configuration engine automatically and invisibly stores the `:{port}` placeholder suffix (e.g. `http://myserver.net:{port}`) in `web-config.yml`. This guarantees that `/eventpvp webtoken` in-game resolves the public link dynamically with the active server port.
  - **Port Change Safety Warning (`web.portChangeWarning`)**: Attempting to save a modified web server port now triggers an explicit confirmation warning dialog informing the admin that the web interface will no longer be accessible at the current URL after server reload/restart.
  - **Obsolete Title Input Removed**: Removed the static title input field from the settings panel, as browser window titles and page headings are fully localized via the i18n system.
  - **Full 7-Language Parity**: Added `web.publicUrl`, `web.publicUrlHint`, and `web.portChangeWarning` across all 7 bundles (`de.json`, `en.json`, `fr.json`, `es.json`, `pl.json`, `ru.json`, `ja.json`) and removed obsolete `web.title` keys, fully verified with 100% clean passes in the automated D10/D11 detector test suite.

