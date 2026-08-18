# Changelog 1.1.0 (Release)

## [1.1.0] - 2026-08-18

This is the official stable release of **Event-PVP-Plugin 1.1.0**, featuring full **dual-platform compatibility for Purpur 26.2 and Vanilla Spigot 26.2**, a complete overhaul of the GUI/trading system, modern **Kyori Adventure 5.2.0** text formatting with true RGB color support, and extensive architectural refactorings for maximum performance, stability, and maintainability under **Java 21**.

---

### 🌟 Release Highlights

- **Full Dual-Platform Compatibility (Purpur 26.2 & Spigot 26.2):** Native support for Purpur and Paper features (asynchronous teleportation, Adventure components, Paper Registry TagKeys) with seamless, zero-crash fallback to Spigot standard APIs.
- **Modern LiveTrade GUI System:** The legacy 16-class GUI system has been completely retired and replaced by a unified, real-time `LiveTrade` architecture and `LiveTradeBridge`.
- **Adventure Text & True RGB:** Complete migration of all chat messages, titles, hover tooltips, and clickable actions to Kyori Adventure 5.2.0 (shaded & relocated to prevent server classloader collisions).
- **Zero-Warning Codebase:** 100% clean compilation against Java 21, resolving all compiler deprecation and platform-specific warnings.
- **Automated Test Suite:** 182 comprehensive unit and integration tests verifying dual-platform adapters, trade bridges, item migrations, and world backup recovery.

---

### 🚀 Major Improvements & Features

#### 1. Dual-Platform Compatibility Engine
- **Runtime Platform Detection (`Platform.java`):** Automatically detects whether the host server is running Purpur, Paper, or Vanilla Spigot and activates platform-optimized code paths dynamically.
- **Shaded Adventure Library:** `net.kyori:adventure-api` (5.2.0), `adventure-text-serializer-legacy`, and `adventure-text-serializer-plain` are now shaded and relocated to `de.zfzfg.eventplugin.libs.kyori`, guaranteeing Adventure support on vanilla Spigot without external dependencies.
- **Universal Text Dispatch (`TextUtil.java`):** Sends native Adventure `Component` objects on Paper/Purpur while automatically serializing to formatted legacy strings on Spigot.
- **Cross-Platform GUI Factory (`GuiUtil.java`):** Creates custom inventories using modern Component titles on Purpur and formatted string titles on Spigot.
- **Cross-Platform Item Metadata (`ItemUtil.java`):** Manages ItemMeta display names and lores using clean Component pipelines on Purpur (eliminating default italic formatting artifacts) and compatible String lists on Spigot.
- **Asynchronous Teleportation Adapter (`TeleportUtil.java`):** Dispatches asynchronous chunk loading and teleportation via `Player#teleportAsync` on Paper/Purpur and reliable synchronous teleports on Spigot.
- **Isolated Paper Registry Helper (`PaperRegistryHelper.java`):** Isolates Paper 1.21+ `EnchantmentTagKeys` (`TREASURE`, `CURSE`) so that the classloader on Spigot servers never encounters missing class definitions when querying `MaterialCatalog`.

#### 2. GUI System Modernization & Legacy Archive
- **Retired Legacy GUI System:** Archived 16 old GUI classes and legacy response handlers into `old-files/` to eliminate technical debt, code duplication, and stale listeners.
- **New `LiveTradeBridge`:** Bridges command-based wager invitations directly into the modern `LiveTradeSession` and `LiveTradeGui`, pre-filling items and money wagers automatically.
- **Modernized `/pvprespond`:** Overhauled command handler to utilize `LiveTradeBridge`, giving players a fluid, visual counter-offer and trade interface.
- **Streamlined Plugin Lifecycle:** Cleaned `EventPlugin` and `MatchManager` to remove deprecated `GuiManager` and `GuiListener` references.

#### 3. Interactive Chat & Title Formatting
- **Interactive Action Buttons:** Clickable chat buttons for wager requests (Accept / Decline / Counter), event broadcast joins, spectator invites, and web authentication.
- **Centralized Title Dispatching (`TextUtil#sendTitle`):** Full RGB title and subtitle delivery with configurable fade-in, stay, and fade-out timings.
- **Web Token URL Integration:** In-game `/eventpvp webtoken` command features one-click clipboard copying and direct browser link integration.

#### 4. Safety, World Management & Inventory Protection
- **Multiverse Integration:** Compatible with Multiverse-Core world cloning, arena resetting, and automatic world loading/unloading policies.
- **Synchronous Safety Stores:** Protected against server crashes and disconnections via `inventory-guard.yml`, `player-return-locations.yml`, and `pending-payouts.yml`.
- **Stranded Player Recovery:** Automatic detection and safe relocation of players remaining in unloaded or orphaned event/arena worlds upon server join.

---

### 📦 Technical Specifications

| Property | Value |
| :--- | :--- |
| **Plugin Version** | `1.1.0` |
| **Java Version** | `Java 21 LTS` (Source / Target) |
| **Target API** | `purpur-api:26.2.build.2618-stable` |
| **Supported Servers** | Purpur 26.2+, Paper 26.2+, Spigot 26.2+ |
| **Kyori Adventure** | `5.2.0` (Shaded & Relocated) |
| **Test Suite** | 182 Passed Unit & Integration Tests |

---

### 🔧 Upgrade & Migration Notes

1. **Drop-in Replacement:** `event-pvp-plugin-1.1.0.jar` is a direct replacement for previous 1.0.x and 1.1.0-beta builds.
2. **Java 21 Required:** Ensure your server runtime is Java 21 or higher.
3. **Configuration Compatibility:** All existing configuration files (`config.yml`, `messages_*.yml`, `worlds.yml`, `equipment.yml`, `web-config.yml`) remain fully compatible and will be migrated automatically if new keys are present.
4. **Dependencies:** `Multiverse-Core` and `Vault` are recommended for full feature support (arena resetting and economy wagers).
