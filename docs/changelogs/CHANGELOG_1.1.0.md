# Changelog 1.1.0 (Release)

## [1.1.0] - 2026-08-18

This is the official release of **Event-PVP-Plugin 1.1.0**, featuring full **dual-platform compatibility for Purpur 26.2 and Vanilla Spigot 26.2**, a complete overhaul of the GUI/trading system, modern **Kyori Adventure 5.2.0** text formatting with true RGB color support, total deprecation cleanup under **Java 21 LTS**, unified sub-command handling, and 100% clean localization across all 7 supported languages.

---

### 🌟 Release Highlights

- **Full Dual-Platform Compatibility (Purpur 26.2 & Spigot 26.2):** Native support for Purpur and Paper features (asynchronous teleportation, Adventure components, Paper Registry TagKeys) with seamless, zero-crash fallback to Spigot standard APIs.
- **Modern LiveTrade GUI System:** The legacy 16-class GUI system has been completely retired and archived into `old-files/`, replaced by a unified, real-time `LiveTrade` architecture and `LiveTradeBridge`.
- **Adventure Text & True RGB:** Complete migration of all chat messages, titles, hover tooltips, and clickable actions to Kyori Adventure 5.2.0 (shaded & relocated to prevent server classloader collisions).
- **Interactive Chat & SubCommand Unification:** Clickable chat action buttons for challenges, invites, and tokens now delegate directly to unified subcommands (`AcceptSubCommand`, `DenySubCommand`, `RespondSubCommand`).
- **100% Clean Localization Audit (i18n D1–D11):** All 11 detectors report 0 Critical and 0 Warnings. 284 legacy orphaned GUI keys safely pruned across all 7 language bundles (`de`, `en`, `es`, `fr`, `ja`, `pl`, `ru`) while preserving 100% key parity (815 master keys per language).
- **Total Deprecation & Warning Cleanup:** 100% clean compilation against Java 21 LTS, resolving all compiler deprecation and platform-specific warnings.
- **Automated Test Suite & JaCoCo Coverage:** 360 comprehensive unit, integration, and MockBukkit tests across 50 test classes (100% green) verifying dual-platform adapters, in-memory Bukkit listeners (friendly fire, spectator recovery, world protection, request cleanup, delayed payouts, stranded player rescue), trade bridges, item migrations, inventory guards, safe location resolvers, event models, team balancing, request handling, and REST/auth APIs, with integrated JaCoCo code coverage.

---

### 🚀 Detailed Changes & Features

#### 1. Dual-Platform Compatibility Engine
- **Runtime Platform Detection (`Platform.java`):** Automatically detects whether the host server is running Purpur, Paper, or Vanilla Spigot and activates platform-optimized code paths dynamically.
- **Shaded Adventure Library:** `net.kyori:adventure-api` (5.2.0), `adventure-text-serializer-legacy`, and `adventure-text-serializer-plain` are shaded and relocated to `de.zfzfg.eventplugin.libs.kyori`, guaranteeing Adventure support on vanilla Spigot without external dependencies or classloader collisions.
- **Universal Text Dispatch (`TextUtil.java`):** Sends native Adventure `Component` objects on Paper/Purpur while automatically serializing to formatted legacy strings on Spigot.
- **Cross-Platform GUI Factory (`GuiUtil.java`):** Creates custom inventories using modern Component titles on Purpur and formatted string titles on Spigot.
- **Cross-Platform Item Metadata (`ItemUtil.java`):** Manages ItemMeta display names and lores using clean Component pipelines on Purpur (eliminating default italic formatting artifacts) and compatible String lists on Spigot.
- **Asynchronous Teleportation Adapter (`TeleportUtil.java`):** Dispatches asynchronous chunk loading and teleportation via `Player#teleportAsync` on Paper/Purpur and reliable synchronous teleports on Spigot.
- **Isolated Paper Registry Helper (`PaperRegistryHelper.java`):** Isolates Paper 1.21+ `EnchantmentTagKeys` (`TREASURE`, `CURSE`) so that the classloader on Spigot servers never encounters missing class definitions when querying `MaterialCatalog`.

#### 2. GUI System Modernization & Legacy Archive
- **Retired Legacy GUI System:** Archived 16 old GUI classes and legacy response handlers into `old-files/` and renamed them to `.java.old` to prevent classpath pollution in IDEs and build tools.
- **New `LiveTradeBridge`:** Bridges command-based wager invitations directly into the modern `LiveTradeSession` and `LiveTradeGui`, pre-filling items and money wagers automatically.
- **Modernized `/pvprespond`:** Overhauled command handler to utilize `LiveTradeBridge`, giving players a fluid, visual counter-offer and trade interface.
- **Streamlined Plugin Lifecycle:** Cleaned `EventPlugin` and `MatchManager` to remove deprecated `GuiManager` and `GuiListener` references.

#### 3. Interactive Chat, Action Buttons & Title Formatting
- **Interactive Action Buttons:** Clickable chat buttons for wager requests (`[► ACCEPT ◄]`, `[✖ DENY]`, `[📋 OPEN GUI]`), event broadcast joins, spectator invites, and web authentication.
- **Unified Subcommands:** Consolidated all challenge responses into dedicated, testable subcommands (`AcceptSubCommand`, `DenySubCommand`, `RespondSubCommand`), eliminating duplicate logic across legacy `/pvpyes`, `/pvpno`, and `/pvpdeny` commands.
- **Centralized Title Dispatching (`TextUtil#sendTitle`):** Full RGB title and subtitle delivery with configurable fade-in, stay, and fade-out timings.
- **Web Token URL Integration:** In-game `/eventpvp webtoken` command features one-click clipboard copying and direct browser link integration.

#### 4. Localization & Quality Audit Suite
- **Full D1–D11 Detection Suite:** Comprehensive audit tool (`tools/i18n_audit.py`) validating key lookups, boolean YAML parsers, placeholder mismatches, hardcoded messages, and web-panel translations.
- **Orphaned Key Pruning (D9):** Removed 284 dead message keys resulting from the retirement of legacy GUIs across all 7 language files (`messages_de.yml`, `messages_en.yml`, `messages_es.yml`, `messages_fr.yml`, `messages_ja.yml`, `messages_pl.yml`, `messages_ru.yml`).
- **Complete Parity (D8):** 100% key parity across all 7 languages (815 keys per bundle).
- **Console & Logger Localization:** All terminal outputs standardized in English and routed through `CoreConfigManager.getConsoleMsg` with placeholder support.
- **Safety Backups:** Pre-cleanup configuration archives preserved under `reports/backup_pre_cleanup_1.1.0/`.

#### 5. Deprecation & Modern Bukkit API Migration
- **BungeeCord Chat Removal:** Removed `net.md-5:bungeecord-chat` dependency and all `player.spigot().sendMessage(...)` invocations.
- **`ChatColor` Replacement:** Replaced all `org.bukkit.ChatColor` usages with `Text` delegates.
- **Modern Attribute System:** Migrated `player.getMaxHealth()` to `player.getAttribute(Attribute.MAX_HEALTH).getValue()`.
- **Modern Respawn API:** Migrated `player.getBedSpawnLocation()` to `player.getRespawnLocation()`.
- **URL Constructor Modernization:** Migrated `new URL(...)` to `URI.create(...).toURL()`.
- **Potion Effect Registries:** Migrated deprecated `PotionEffectType` methods to `org.bukkit.Registry.POTION_EFFECT_TYPE`.
- **Enchantment Tag Keys:** Migrated `isTreasure()` and `isCursed()` to Paper `EnchantmentTagKeys`.
- **Native TPS Metric:** Removed reflection in `WebApiHandler` and replaced with native `Bukkit.getServer().getTPS()`.
- **PlayerMoveEvent Optimization:** Throttled move listeners in `WorldChangeListener` and `VoidProtectionListener` using `ignoreCancelled = true` and `hasChangedBlock()`.
- **Thread-Safe Cooldowns:** `CommandCooldownManager` synchronized with thread-safe access patterns.
- **Event Constructor Modernization (Purpur 26.2 / Paper 1.21):** Migrated `EntityDamageEvent`, `EntityDamageByEntityEvent`, and `PlayerQuitEvent` constructors to current, non-deprecated API signatures with modifier maps and `QuitReason`.

#### 6. Safety, World Management & Inventory Protection
- **Multiverse Integration:** Compatible with Multiverse-Core world cloning, arena resetting, and automatic world loading/unloading policies.
- **Synchronous Safety Stores:** Protected against server crashes and disconnections via `inventory-guard.yml`, `player-return-locations.yml`, and `pending-payouts.yml`.
- **Stranded Player Recovery:** Automatic detection and safe relocation of players remaining in unloaded or orphaned event/arena worlds upon server join.

#### 7. Architecture Documentation
- **Server Compatibility Matrix:** Comprehensive reference guide in `docs/SERVER_COMPATIBILITY.md`.
- **Conceptual Analysis & Roadmap:** System architecture evaluation and feature roadmap in `docs/PROJEKT_KONZEPT_ANALYSE.md`.

#### 8. Automated Test Suite, MockBukkit & Code Coverage Expansion
- **Comprehensive Test Suite (360 Tests across 50 Test Classes):** Expanded test coverage across all plugin modules (Core, Location, Inventory Guard, Multiverse-Inventories Bridge, Events, PvP Wager, LiveTrade, and Web API).
- **In-Memory Server Testing via MockBukkit 26.2:** Integrated `MockBukkit-v26.2` (4.116.1) and `paper-api` for realistic, in-memory server simulation of listeners and commands (`MockBukkitTestBase`, `VoidProtectionListenerMockTest`, `PvPListenerMockTest`, `PvPUnifiedCommandMockTest`, `TeamPvPListenerMockTest`, `SpectatorRecoveryListenerMockTest`, `WorldProtectionListenerMockTest`, `RequestCleanupListenerMockTest`, `PendingPayoutListenerMockTest`, `StrandedPlayerListenerMockTest`).
- **JaCoCo Code Coverage (`jacoco-maven-plugin:0.8.12`):** Automated coverage measurement and HTML report generation (`target/site/jacoco/index.html`).
- **Modern Mocking & Assertions:** Integrated `mockito-junit-jupiter:5.11.0` and `assertj-core:3.25.3` with Java 21 dynamic agent loading support.
- **Quality Improvements:** Enhanced `InputValidator` with `NaN` and `Infinity` bounds checks and hardened `SafeLocationResolver` with null-safe return store lookups.

---

### 📦 Technical Specifications

| Property | Value |
| :--- | :--- |
| **Plugin Version** | `1.1.0` |
| **Java Version** | `Java 21 LTS` (Source / Target) |
| **Target API** | `purpur-api:26.2.build.2618-stable` |
| **Supported Server Engines** | Purpur 26.2+, Paper 1.21.x / 1.20.5+, Pufferfish 1.21.x, Spigot 26.2 / 1.21.x |
| **Unsupported Engines** | Folia (requires regionised scheduler rebuild), 1.19.4 & older |
| **Kyori Adventure** | `5.2.0` (Shaded & Relocated to `de.zfzfg.eventplugin.libs.kyori`) |
| **Test Suite** | 360 Passed Unit, Integration & MockBukkit Tests (100% Green, 50 Classes) |
| **Code Coverage** | JaCoCo 0.8.12 (`target/site/jacoco/index.html`) |
| **Localization** | 7 Languages (`de`, `en`, `es`, `fr`, `ja`, `pl`, `ru`) – 815 Master Keys |

---

### 🔧 Upgrade & Migration Notes

1. **Drop-in Replacement:** `event-pvp-plugin-1.1.0.jar` is a direct replacement for previous 1.0.x and 1.1.0-beta builds.
2. **Java 21 Required:** Ensure your server runtime is Java 21 or higher.
3. **Configuration Compatibility:** All existing configuration files (`config.yml`, `messages_*.yml`, `worlds.yml`, `equipment.yml`, `web-config.yml`) remain fully compatible and will be migrated automatically if new keys are present.
4. **Dependencies:** `Multiverse-Core` and `Vault` are recommended for full feature support (arena resetting and economy wagers).
