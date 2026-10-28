# Event-PVP-Plugin

Run custom events, host PvP duels with item or money wagers, and manage your setup from a browser. **Event-PVP-Plugin** brings events, arenas, equipment kits and inventory recovery together in one plugin.

Build a solo survival event, organize team battles, or let players challenge each other to a wager match—with configurable rules, rewards and loadouts.

![Event-PVP web configurator overview](https://cdn.modrinth.com/data/cached_images/dca84c8f21502a2f9d2525a1555f72510bfa1cf9.png)

## Custom events

- **Solo and team modes:** Free-for-all, two-team and three-team events.
- **Flexible win conditions:** Last player standing, item collection, kill targets and timed survival.
- **Your arena, your rules:** Configure PvP, hunger, elimination, spectators and an optional pre-event lobby.
- **Seven spawn types:** Single point, random radius, random area, random cube, multiple points, team spawns and command-based spawning.
- **Automatic scheduling:** Rotate events randomly or in sequence at configurable intervals.
- **Rewards and statistics:** Give winners and participants items or command-based rewards. Display statistics with PlaceholderAPI, AJLeaderboards and DecentHolograms.

## PvP wagers

Let players challenge each other through an in-game GUI, choose an arena and agree on their stakes.

- Wager **items or money** through a Vault-compatible economy.
- Use configurable equipment kits with armor, weapons, enchantments, offhand items and inventories.
- Support counter-offers, draw votes, surrender and spectators.
- Configure arena protection and resets for repeatable matches.
- Keep persistent payout records and queue eligible offline rewards. Uncertain payout outcomes remain available for administrator review.

## Configure it in your browser

The web configurator gives administrators a visual way to manage settings, events, worlds, equipment and inventory backups. YAML files remain available for manual configuration.

- **Visual editors:** Configure events and kits, preview items with Minecraft textures, and inspect generated YAML.
- **Inventory backup explorer:** Search players, inspect saved inventories with armor, offhand and XP, and restore a selected backup after confirming the target player.
- **Recovery overview:** Inspect inventory-guard sessions and inventory-management settings.
- **World management:** Create, import, load and unload worlds through Multiverse. Back up and restore worlds, or delete them with explicit confirmation.
- **Clear save feedback:** See unsaved changes, retry failed loads and handle save conflicts without silently discarding edits.
- **Token-based access:** Generate a one-time login token in-game with `/eventpvp webtoken`.
- **Custom appearance:** Adjust theme colors. The darker interface supports narrow screens, keyboard navigation and reduced motion, with local SVG icons and bundled vanilla item textures.
- **Resource-pack textures:** Optionally import item textures from your server resource pack.

The panel uses port **8085** by default. Its port, bind address and public URL are configurable in `web-config.yml`. For remote access, put it behind an **HTTPS reverse proxy**.

![Event-PVP interface preview](https://cdn.modrinth.com/data/cached_images/cccdf739c9185ab11c8e5d8133bc1504d3125fa1.png)

## Inventory recovery

With **InventoryBackup**, managed events and matches save player inventories before entry and restore them when the session ends. Persistent session records and return locations support recovery after disconnects and restarts.

The web panel exposes backup previews and restore failures so administrators can inspect recovery problems. Inventory recovery depends on a compatible, enabled InventoryBackup provider; it does not replace regular server backups.

**Avoid competing inventory managers on event and arena worlds.** If you intentionally use another plugin to manage inventory swaps, review the inventory-provider settings first.

## Seven languages

English, German, Spanish, French, Japanese, Polish and Russian are included for the in-game interface and web configurator. Missing custom translation entries fall back to the bundled English defaults.

## Requirements

For the current **1.1.x release**:

| Requirement | Version / details |
| --- | --- |
| Server | Purpur, Paper or Spigot **26.2 / 26.3** |
| Java | **25 or newer** |
| [Multiverse-Core](https://modrinth.com/project/3wmN97b8) | Version **4 or 5** |
| [VaultUnlocked](https://modrinth.com/project/ayRaM8J7) or [Vault](https://www.spigotmc.org/resources/vault.34315/) | Install **one** of these; money wagers also require a compatible economy plugin |
| [InventoryBackup](https://modrinth.com/project/rpKY25cW) | **0.2.0 / API revision 2 or newer** |

**Minecraft 1.21.x and older, Java 17/21 and Folia are not supported by this release.**

Optional integrations: **PlaceholderAPI**, **AJLeaderboards**, **DecentHolograms** and **PvPManager**.

## Quick start

1. Install the required dependencies and `event-pvp-plugin-1.1.3.jar` in your server's `plugins/` folder.
2. Start the server and check the console for dependency or configuration errors.
3. Run `/eventpvp webtoken` in-game as an administrator.
4. Open the web address shown in the message, enter your token and configure your events, arenas and kits.
5. Start an event with `/event start <name>` or send a duel request with `/pvpask <player>`.

## Essential commands

### Players

```text
/event list                  List available events
/event join <name>           Join an event
/event leave                 Leave the current event
/pvpask <player>              Open a PvP challenge
/pvpaccept                   Accept a challenge
/pvpdeny                     Decline a challenge
/surrender                   Surrender the current match
/draw                        Vote for a draw
/pvp spectate <player>       Spectate a duel
```

### Administrators

```text
/eventpvp webtoken            Generate a web login token
/eventpvp reload              Reload configuration and language files
/event start <name>           Start an event
/event stop <name>            Stop an event
/event forcestart <name>      Skip the remaining join countdown
/eventpvp rescue list         List players awaiting return or recovery
/eventpvp payouts list        Inspect pending payouts
```

## Updating an existing server

Stop the server and **back up plugin data before replacing the JAR**. Check the current server, Java and dependency requirements above, especially when upgrading from an older Minecraft version.

Configuration migrations preserve existing settings and create backups when changes are needed. Updates from before **1.1.2** also introduce a new payout ledger: rolling back requires the pre-update data backup and a review of any payouts made since the update.

See the [upgrade guide for 1.0.8 → 1.0.9](https://github.com/zfzfg/Event-PVP-Plugin/blob/main/docs/migration/UPGRADE.md) for the older dependency transition and the [1.1.2 migration and recovery notes](https://github.com/zfzfg/Event-PVP-Plugin/blob/main/docs/HARDENING_1.1.2.md) for payout recovery and rollback details.

[Documentation](https://github.com/zfzfg/Event-PVP-Plugin#readme) · [Release notes](https://github.com/zfzfg/Event-PVP-Plugin/releases) · [Report an issue](https://github.com/zfzfg/Event-PVP-Plugin/issues)
