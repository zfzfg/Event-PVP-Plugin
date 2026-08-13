# Problems Export

Generated: 2026-08-13T10:27:10.657Z
Total problems: 366 across 85 file(s)

## src/main/java/de/zfzfg/core/commands/SmartTabCompleter.java

- **Line 6:8** Warning [Java, 268435844]: The import org.bukkit.command.CommandSender is never used
- **Line 21:28** Warning [Java, 976]: Potential null pointer access: this expression has type 'capture#1-of ? extends org.bukkit.entity.Player', a free type variable that may represent a '@Nullable' type
- **Line 23:26** Information [Java, 536871895]: Unsafe null type conversion (type annotations): The value of type 'Collector<@NonNull String,capture#of ?,List<@NonNull String>>' is made accessible using the less-annotated type 'Collector<? super @NonNull String,Object,List<String>>'

## src/main/java/de/zfzfg/core/commands/WebTokenSubCommand.java

- **Line 113:45** Warning [Java, 536871898]: Null type safety (type annotations): The expression of type 'Component' needs unchecked conversion to conform to '@NonNull Component'

## src/main/java/de/zfzfg/core/config/CoreConfigManager.java

- **Line 162:54** Warning [Java, 536871898]: Null type safety (type annotations): The expression of type 'String' needs unchecked conversion to conform to '@NonNull String'
- **Line 337:16** Warning [Java, 16777221]: The type ChatColor is deprecated

## src/main/java/de/zfzfg/core/inventory/adapter/InventoryRestoreApiAdapter.java

- **Line 58:24** Warning [Java, 67109822]: Null type safety: parameter 'this' provided via method descriptor Function<InventoryBackupAPI,Integer>.apply(InventoryBackupAPI) needs unchecked conversion to conform to '@NonNull InventoryBackupAPI'
- **Line 160:28** Warning [Java, 67109822]: Null type safety: parameter 'this' provided via method descriptor Function<Optional<PendingRestore>,Boolean>.apply(Optional<PendingRestore>) needs unchecked conversion to conform to '@NonNull Optional<PendingRestore>'
- **Line 198:16** Information [Java, 536871895]: Unsafe null type conversion (type annotations): The value of type 'CompletableFuture<Optional<@NonNull CapturedInventory>>' is made accessible using the less-annotated type 'CompletableFuture<Optional<CapturedInventory>>'

## src/main/java/de/zfzfg/core/inventory/mvi/MultiverseInventoriesBridge.java

- **Line 68:24** Warning [Java, 536871364]: Potential null pointer access: The variable mvi may be null at this location
- **Line 68:28** Warning [Java, 67108967]: The method getDescription() from the type Plugin is deprecated

## src/main/java/de/zfzfg/core/items/ConfiguredItemFactory.java

- **Line 143:18** Warning [Java, 67108967]: The method setDisplayName(String) from the type ItemMeta is deprecated
- **Line 152:18** Warning [Java, 67108967]: The method setLore(List<String>) from the type ItemMeta is deprecated
- **Line 200:18** Warning [Java, 67108967]: The method setDisplayName(String) from the type ItemMeta is deprecated
- **Line 209:18** Warning [Java, 67108967]: The method setLore(List<String>) from the type ItemMeta is deprecated
- **Line 227:22** Warning [Java, 67110270]: The method setCustomModelData(Integer) from the type ItemMeta is deprecated since version 1.21.5
- **Line 418:55** Warning [Java, 67110270]: The method getByKey(NamespacedKey) from the type PotionEffectType is deprecated since version 1.20.3
- **Line 427:37** Warning [Java, 67110270]: The method getByName(String) from the type PotionEffectType is deprecated since version 1.20.3
- **Line 434:16** Warning [Java, 16777221]: The type ChatColor is deprecated

## src/main/java/de/zfzfg/core/location/SafeLocationResolver.java

- **Line 69:31** Warning [Java, 67110270]: The method getBedSpawnLocation() from the type OfflinePlayer is deprecated since version 1.20.4

## src/main/java/de/zfzfg/core/location/StrandedPlayerListener.java

- **Line 74:13** Warning [Java, 536871364]: Potential null pointer access: The variable location may be null at this location

## src/main/java/de/zfzfg/core/monitoring/debug/DebugManager.java

- **Line 139:17** Warning [Java, 536871364]: Potential null pointer access: The variable player may be null at this location
- **Line 146:16** Warning [Java, 16777221]: The type ChatColor is deprecated
- **Line 150:16** Warning [Java, 16777221]: The type ChatColor is deprecated

## src/main/java/de/zfzfg/core/reward/PendingPayoutStore.java

- **Line 275:75** Warning [Java, 976]: Potential null pointer access: this expression has type 'capture#8-of ?', a free type variable that may represent a '@Nullable' type
- **Line 278:33** Warning [Java, 976]: Potential null pointer access: this expression has type 'capture#16-of ?', a free type variable that may represent a '@Nullable' type

## src/main/java/de/zfzfg/core/util/CommandCooldownManager.java

- **Line 26:43** Warning [Java, 16777221]: The type ChatColor is deprecated

## src/main/java/de/zfzfg/core/util/Text.java

- **Line 59:57** Warning [Java, 536871898]: Null type safety (type annotations): The expression of type 'String' needs unchecked conversion to conform to '@NonNull String'
- **Line 79:67** Warning [Java, 536871898]: Null type safety (type annotations): The expression of type 'Component' needs unchecked conversion to conform to '@NonNull Component'
- **Line 93:50** Warning [Java, 536871898]: Null type safety (type annotations): The expression of type 'Component' needs unchecked conversion to conform to '@NonNull Component'
- **Line 100:63** Warning [Java, 536871898]: Null type safety (type annotations): The expression of type 'String' needs unchecked conversion to conform to '@NonNull String'
- **Line 102:50** Warning [Java, 536871898]: Null type safety (type annotations): The expression of type 'Component' needs unchecked conversion to conform to '@NonNull Component'

## src/main/java/de/zfzfg/core/web/MaterialCatalog.java

- **Line 293:51** Warning [Java, 67110270]: The method isTreasure() from the type Enchantment is deprecated since version 1.21
- **Line 326:23** Warning [Java, 536871547]: Unnecessary @SuppressWarnings("deprecation")
- **Line 331:32** Warning [Java, 67110275]: The method getName() from the type Enchantment has been deprecated since version 1.13 and marked for removal
- **Line 363:95** Warning [Java, 67110270]: The method values() from the type PotionEffectType is deprecated since version 1.20.3
- **Line 365:38** Warning [Java, 67110270]: The method getName() from the type PotionEffectType is deprecated since version 1.20.3

## src/main/java/de/zfzfg/core/web/ResourcePackTextureService.java

- **Line 179:64** Warning [Java, 67110271]: The constructor URL(String) is deprecated since version 20
- **Line 236:21** Warning [Java, 536871364]: Potential null pointer access: The variable entry may be null at this location

## src/main/java/de/zfzfg/core/web/WebApiHandler.java

- **Line 17:24** Warning [Java, 570425421]: The value of the field WebApiHandler.gson is not used
- **Line 549:49** Warning [Java, 67108967]: The method getDisplayName() from the type ItemMeta is deprecated
- **Line 552:42** Warning [Java, 67108967]: The method getLore() from the type ItemMeta is deprecated
- **Line 815:50** Warning [Java, 976]: Potential null pointer access: this expression has type 'capture#8-of ?', a free type variable that may represent a '@Nullable' type
- **Line 836:50** Warning [Java, 976]: Potential null pointer access: this expression has type 'capture#22-of ?', a free type variable that may represent a '@Nullable' type
- **Line 1008:41** Warning [Java, 67108967]: The method getDescription() from the type JavaPlugin is deprecated
- **Line 1009:44** Warning [Java, 67108967]: The method getDescription() from the type JavaPlugin is deprecated

## src/main/java/de/zfzfg/core/web/WebConfigManager.java

- **Line 375:23** Warning [Java, 536871547]: Unnecessary @SuppressWarnings("unchecked")
- **Line 408:23** Warning [Java, 536871547]: Unnecessary @SuppressWarnings("unchecked")

## src/main/java/de/zfzfg/core/web/WebServer.java

- **Line 555:18** Warning [Java, 603979894]: The method handleApiRequest(HttpExchange, WebServer.ResponseProvider) from the type WebServer is never used locally
- **Line 582:18** Warning [Java, 603979894]: The method handleApiPostRequest(HttpExchange, WebServer.PostRequestHandler) from the type WebServer is never used locally

## src/main/java/de/zfzfg/core/world/mv/LegacyCommandWorldBackend.java

- **Line 75:52** Warning [Java, 67109822]: Null type safety: parameter 'this' provided via method descriptor Function<MvWorldInfo,String>.apply(MvWorldInfo) needs unchecked conversion to conform to '@NonNull MvWorldInfo'

## src/main/java/de/zfzfg/core/world/mv/Mv5WorldBackend.java

- **Line 118:52** Warning [Java, 67109822]: Null type safety: parameter 'this' provided via method descriptor Function<MvWorldInfo,String>.apply(MvWorldInfo) needs unchecked conversion to conform to '@NonNull MvWorldInfo'

## src/main/java/de/zfzfg/core/world/mv/MvWorldService.java

- **Line 9:8** Warning [Java, 268435844]: The import java.util.Collections is never used
- **Line 106:17** Warning [Java, 536871364]: Potential null pointer access: The variable mv5 may be null at this location
- **Line 655:69** Warning [Java, 536871364]: Potential null pointer access: The variable entry may be null at this location

## src/main/java/de/zfzfg/eventplugin/commands/EventCommand.java

- **Line 15:8** Warning [Java, 268435844]: The import java.util.Arrays is never used

## src/main/java/de/zfzfg/eventplugin/commands/EventPvpCommand.java

- **Line 195:40** Warning [Java, 67108967]: The method getDescription() from the type JavaPlugin is deprecated
- **Line 286:45** Warning [Java, 536871898]: Null type safety (type annotations): The expression of type 'Component' needs unchecked conversion to conform to '@NonNull Component'
- **Line 604:33** Warning [Java, 536871364]: Potential null pointer access: The variable online may be null at this location

## src/main/java/de/zfzfg/eventplugin/commands/EventStatsCommand.java

- **Line 52:38** Warning [Java, 67109822]: Null type safety: parameter 'this' provided via method descriptor Function<EventStats,Integer>.apply(EventStats) needs unchecked conversion to conform to '@NonNull EventStats'
- **Line 53:39** Warning [Java, 67109822]: Null type safety: parameter 'this' provided via method descriptor Function<EventStats,Integer>.apply(EventStats) needs unchecked conversion to conform to '@NonNull EventStats'
- **Line 102:123** Warning [Java, 536871364]: Potential null pointer access: The variable op may be null at this location
- **Line 127:121** Warning [Java, 536871364]: Potential null pointer access: The variable op may be null at this location
- **Line 147:64** Warning [Java, 67109822]: Null type safety: parameter 'this' provided via method descriptor Function<capture#1-of ? extends Player,String>.apply(capture#1-of ? extends Player) needs unchecked conversion to conform to '@NonNull Player'

## src/main/java/de/zfzfg/eventplugin/EventPlugin.java

- **Line 234:20** Warning [Java, 16777221]: The type ChatColor is deprecated
- **Line 248:9** Warning [Java, 16777221]: The type PvPACommand is deprecated
- **Line 248:39** Warning [Java, 16777221]: The type PvPACommand is deprecated
- **Line 252:39** Warning [Java, 16777221]: The type PvPYesCommand is deprecated
- **Line 253:38** Warning [Java, 16777221]: The type PvPNoCommand is deprecated
- **Line 269:9** Warning [Java, 16777221]: The type PvPAcceptCommand is deprecated
- **Line 269:46** Warning [Java, 16777221]: The type PvPAcceptCommand is deprecated
- **Line 272:9** Warning [Java, 16777221]: The type PvPDenyCommand is deprecated
- **Line 272:42** Warning [Java, 16777221]: The type PvPDenyCommand is deprecated
- **Line 331:49** Warning [Java, 67108967]: The method getDescription() from the type JavaPlugin is deprecated
- **Line 757:50** Information [Java, 536871895]: Unsafe null type conversion (type annotations): The value of type 'RegisteredServiceProvider<@NonNull Economy>' is made accessible using the less-annotated type 'RegisteredServiceProvider<Economy>'
- **Line 767:59** Warning [Java, 536871898]: Null type safety (type annotations): The expression of type 'String' needs unchecked conversion to conform to '@NonNull String'
- **Line 776:59** Warning [Java, 536871898]: Null type safety (type annotations): The expression of type 'String' needs unchecked conversion to conform to '@NonNull String'

## src/main/java/de/zfzfg/eventplugin/integration/papi/EventPvpExpansion.java

- **Line 66:22** Warning [Java, 67109822]: Null type safety: parameter 'this' provided via method descriptor Function<EventStats,Integer>.apply(EventStats) needs unchecked conversion to conform to '@NonNull EventStats'
- **Line 72:22** Warning [Java, 67109822]: Null type safety: parameter 'this' provided via method descriptor Function<EventStats,Integer>.apply(EventStats) needs unchecked conversion to conform to '@NonNull EventStats'
- **Line 78:22** Warning [Java, 67109822]: Null type safety: parameter 'this' provided via method descriptor Function<PlayerStats,Integer>.apply(PlayerStats) needs unchecked conversion to conform to '@NonNull PlayerStats'
- **Line 84:22** Warning [Java, 67109822]: Null type safety: parameter 'this' provided via method descriptor Function<PlayerStats,Integer>.apply(PlayerStats) needs unchecked conversion to conform to '@NonNull PlayerStats'
- **Line 90:22** Warning [Java, 67109822]: Null type safety: parameter 'this' provided via method descriptor Function<PlayerStats,Integer>.apply(PlayerStats) needs unchecked conversion to conform to '@NonNull PlayerStats'

## src/main/java/de/zfzfg/eventplugin/listeners/EventListener.java

- **Line 183:21** Warning [Java, 603979894]: The method isEventWorldUnloaded(EventSession) from the type EventListener is never used locally

## src/main/java/de/zfzfg/eventplugin/listeners/UpdateNotifyListener.java

- **Line 51:32** Warning [Java, 16777221]: The type ChatColor is deprecated
- **Line 52:32** Warning [Java, 16777221]: The type ChatColor is deprecated

## src/main/java/de/zfzfg/eventplugin/listeners/VoidProtectionListener.java

- **Line 201:75** Warning [Java, 67110270]: The method getMaxHealth() from the type Damageable is deprecated since version 1.11
- **Line 204:32** Warning [Java, 16777221]: The type ChatColor is deprecated
- **Line 223:21** Warning [Java, 603979894]: The method isLocationSafe(Location) from the type VoidProtectionListener is never used locally

## src/main/java/de/zfzfg/eventplugin/listeners/WorldChangeListener.java

- **Line 5:8** Warning [Java, 268435844]: The import org.bukkit.Bukkit is never used

## src/main/java/de/zfzfg/eventplugin/manager/ConfigManager.java

- **Line 21:20** Warning [Java, 570425421]: The value of the field ConfigManager.equipmentFilePath is not used

## src/main/java/de/zfzfg/eventplugin/managers/EventStatsManager.java

- **Line 33:49** Warning [Java, 67109822]: Null type safety: parameter 'this' provided via method descriptor ToIntFunction<EventStats>.applyAsInt(EventStats) needs unchecked conversion to conform to '@NonNull EventStats'

## src/main/java/de/zfzfg/eventplugin/security/PlayerModeListener.java

- **Line 30:16** Warning [Java, 16777221]: The type ChatColor is deprecated
- **Line 36:16** Warning [Java, 536870973]: The value of the local variable cmd is not used

## src/main/java/de/zfzfg/eventplugin/security/WorldProtectionListener.java

- **Line 15:8** Warning [Java, 268435844]: The import java.util.HashSet is never used
- **Line 16:8** Warning [Java, 268435844]: The import java.util.Set is never used

## src/main/java/de/zfzfg/eventplugin/session/EventSession.java

- **Line 220:28** Warning [Java, 67108967]: The method broadcastMessage(String) from the type Bukkit is deprecated
- **Line 234:13** Warning [Java, 536871364]: Potential null pointer access: The variable onlinePlayer may be null at this location
- **Line 251:16** Warning [Java, 67108967]: The method broadcastMessage(String) from the type Bukkit is deprecated
- **Line 258:20** Warning [Java, 67108967]: The method broadcastMessage(String) from the type Bukkit is deprecated
- **Line 286:24** Warning [Java, 67108967]: The method broadcastMessage(String) from the type Bukkit is deprecated
- **Line 813:17** Warning [Java, 33555193]: The enum constant TEAM_SPAWNS needs a corresponding case label in this enum switch on EventConfig.SpawnType
- **Line 1833:29** Information [Java, 16778197]: Unsafe interpretation of method return type as '@NonNull' based on substitution 'T=@NonNull Firework'. Declaring type 'RegionAccessor' doesn't seem to be designed with null type annotations in mind
- **Line 1873:17** Warning [Java, 536871898]: Null type safety (type annotations): The expression of type 'Component' needs unchecked conversion to conform to '@NonNull Component'
- **Line 1874:17** Warning [Java, 536871898]: Null type safety (type annotations): The expression of type 'Component' needs unchecked conversion to conform to '@NonNull Component'
- **Line 1876:25** Warning [Java, 536871898]: Null type safety (type annotations): The expression of type 'Duration' needs unchecked conversion to conform to '@NonNull Duration'
- **Line 1877:25** Warning [Java, 536871898]: Null type safety (type annotations): The expression of type 'Duration' needs unchecked conversion to conform to '@NonNull Duration'
- **Line 1878:25** Warning [Java, 536871898]: Null type safety (type annotations): The expression of type 'Duration' needs unchecked conversion to conform to '@NonNull Duration'
- **Line 1885:34** Warning [Java, 536871865]: Null type mismatch (type annotations): required '@NonNull Location' but this expression has type '@Nullable Location'
- **Line 1885:56** Warning [Java, 536871898]: Null type safety (type annotations): The expression of type 'Sound' needs unchecked conversion to conform to '@NonNull Sound'

## src/main/java/de/zfzfg/eventplugin/util/UpdateChecker.java

- **Line 72:32** Warning [Java, 67108967]: The method getDescription() from the type JavaPlugin is deprecated
- **Line 93:42** Information [Java, 16778197]: Unsafe interpretation of method return type as '@NonNull' based on substitution 'T=@NonNull JsonArray'. Declaring type 'Gson' doesn't seem to be designed with null type annotations in mind

## src/main/java/de/zfzfg/pvpwager/commands/PvPAcceptCommand.java

- **Line 29:12** Information [Java, 16778649]: The enclosing type PvPAcceptCommand is deprecated, perhaps this member should be marked as deprecated, too?
- **Line 52:20** Information [Java, 16778649]: The enclosing type PvPAcceptCommand is deprecated, perhaps this member should be marked as deprecated, too?
- **Line 114:25** Information [Java, 16778649]: The enclosing type PvPAcceptCommand is deprecated, perhaps this member should be marked as deprecated, too?
- **Line 127:21** Warning [Java, 536871364]: Potential null pointer access: The variable onlinePlayer may be null at this location

## src/main/java/de/zfzfg/pvpwager/commands/PvPACommand.java

- **Line 36:12** Information [Java, 16778649]: The enclosing type PvPACommand is deprecated, perhaps this member should be marked as deprecated, too?
- **Line 42:20** Information [Java, 16778649]: The enclosing type PvPACommand is deprecated, perhaps this member should be marked as deprecated, too?
- **Line 206:25** Information [Java, 16778649]: The enclosing type PvPACommand is deprecated, perhaps this member should be marked as deprecated, too?
- **Line 218:21** Warning [Java, 536871364]: Potential null pointer access: The variable p may be null at this location

## src/main/java/de/zfzfg/pvpwager/commands/PvPAdminCommand.java

- **Line 85:31** Warning [Java, 67108967]: The method getDescription() from the type JavaPlugin is deprecated

## src/main/java/de/zfzfg/pvpwager/commands/PvPAskCommand.java

- **Line 35:12** Information [Java, 16778649]: The enclosing type PvPAskCommand is deprecated, perhaps this member should be marked as deprecated, too?
- **Line 111:20** Information [Java, 16778649]: The enclosing type PvPAskCommand is deprecated, perhaps this member should be marked as deprecated, too?
- **Line 207:25** Information [Java, 16778649]: The enclosing type PvPAskCommand is deprecated, perhaps this member should be marked as deprecated, too?
- **Line 215:31** Warning [Java, 976]: Potential null pointer access: this expression has type 'capture#1-of ? extends org.bukkit.entity.Player', a free type variable that may represent a '@Nullable' type
- **Line 218:22** Warning [Java, 67109822]: Null type safety: parameter 'this' provided via method descriptor Function<capture#1-of ? extends Player,String>.apply(capture#1-of ? extends Player) needs unchecked conversion to conform to '@NonNull Player'
- **Line 220:26** Information [Java, 536871895]: Unsafe null type conversion (type annotations): The value of type 'Collector<@NonNull String,capture#of ?,List<@NonNull String>>' is made accessible using the less-annotated type 'Collector<? super @NonNull String,Object,List<String>>'

## src/main/java/de/zfzfg/pvpwager/commands/PvPDenyCommand.java

- **Line 28:12** Information [Java, 16778649]: The enclosing type PvPDenyCommand is deprecated, perhaps this member should be marked as deprecated, too?
- **Line 51:20** Information [Java, 16778649]: The enclosing type PvPDenyCommand is deprecated, perhaps this member should be marked as deprecated, too?
- **Line 71:68** Warning [Java, 536871364]: Potential null pointer access: The variable senderPlayer may be null at this location
- **Line 118:25** Information [Java, 16778649]: The enclosing type PvPDenyCommand is deprecated, perhaps this member should be marked as deprecated, too?
- **Line 131:21** Warning [Java, 536871364]: Potential null pointer access: The variable onlinePlayer may be null at this location

## src/main/java/de/zfzfg/pvpwager/commands/PvPNoCommand.java

- **Line 5:8** Warning [Java, 268435844]: The import de.zfzfg.pvpwager.models.Match is never used
- **Line 21:12** Information [Java, 16778649]: The enclosing type PvPNoCommand is deprecated, perhaps this member should be marked as deprecated, too?
- **Line 26:20** Information [Java, 16778649]: The enclosing type PvPNoCommand is deprecated, perhaps this member should be marked as deprecated, too?

## src/main/java/de/zfzfg/pvpwager/commands/PvPRespondCommand.java

- **Line 41:16** Warning [Java, 16777221]: The type ChatColor is deprecated

## src/main/java/de/zfzfg/pvpwager/commands/PvPStatsCommand.java

- **Line 102:121** Warning [Java, 536871364]: Potential null pointer access: The variable op may be null at this location
- **Line 128:119** Warning [Java, 536871364]: Potential null pointer access: The variable opAdd may be null at this location
- **Line 152:77** Warning [Java, 67109822]: Null type safety: parameter 'this' provided via method descriptor Function<capture#1-of ? extends Player,String>.apply(capture#1-of ? extends Player) needs unchecked conversion to conform to '@NonNull Player'

## src/main/java/de/zfzfg/pvpwager/commands/PvPWagerGuiCommand.java

- **Line 49:16** Warning [Java, 16777221]: The type ChatColor is deprecated
- **Line 224:47** Warning [Java, 536871898]: Null type safety (type annotations): The expression of type 'Component' needs unchecked conversion to conform to '@NonNull Component'
- **Line 224:68** Warning [Java, 536871898]: Null type safety (type annotations): The expression of type 'Component' needs unchecked conversion to conform to '@NonNull Component'
- **Line 224:87** Warning [Java, 536871898]: Null type safety (type annotations): The expression of type 'Component' needs unchecked conversion to conform to '@NonNull Component'
- **Line 431:31** Warning [Java, 976]: Potential null pointer access: this expression has type 'capture#2-of ? extends org.bukkit.entity.Player', a free type variable that may represent a '@Nullable' type
- **Line 434:30** Warning [Java, 976]: Potential null pointer access: this expression has type 'capture#2-of ? extends org.bukkit.entity.Player', a free type variable that may represent a '@Nullable' type
- **Line 435:22** Warning [Java, 67109822]: Null type safety: parameter 'this' provided via method descriptor Function<capture#2-of ? extends Player,String>.apply(capture#2-of ? extends Player) needs unchecked conversion to conform to '@NonNull Player'
- **Line 436:26** Information [Java, 536871895]: Unsafe null type conversion (type annotations): The value of type 'Collector<@NonNull String,capture#of ?,List<@NonNull String>>' is made accessible using the less-annotated type 'Collector<? super @NonNull String,Object,List<String>>'

## src/main/java/de/zfzfg/pvpwager/commands/PvPYesCommand.java

- **Line 4:8** Warning [Java, 268435844]: The import de.zfzfg.pvpwager.managers.ArenaManager is never used
- **Line 6:8** Warning [Java, 268435844]: The import de.zfzfg.pvpwager.models.Match is never used
- **Line 23:12** Information [Java, 16778649]: The enclosing type PvPYesCommand is deprecated, perhaps this member should be marked as deprecated, too?
- **Line 28:20** Information [Java, 16778649]: The enclosing type PvPYesCommand is deprecated, perhaps this member should be marked as deprecated, too?

## src/main/java/de/zfzfg/pvpwager/commands/unified/PvPUnifiedCommand.java

- **Line 38:16** Warning [Java, 16777221]: The type ChatColor is deprecated

## src/main/java/de/zfzfg/pvpwager/commands/unified/subcommands/ChallengeSubCommand.java

- **Line 199:21** Warning [Java, 536871364]: Potential null pointer access: The variable p may be null at this location

## src/main/java/de/zfzfg/pvpwager/gui/AbstractWagerGui.java

- **Line 84:18** Warning [Java, 67108967]: The method setDisplayName(String) from the type ItemMeta is deprecated
- **Line 97:18** Warning [Java, 67108967]: The method setDisplayName(String) from the type ItemMeta is deprecated
- **Line 99:22** Warning [Java, 67108967]: The method setLore(List<String>) from the type ItemMeta is deprecated
- **Line 113:18** Warning [Java, 67108967]: The method setDisplayName(String) from the type ItemMeta is deprecated
- **Line 115:22** Warning [Java, 67108967]: The method setLore(List<String>) from the type ItemMeta is deprecated
- **Line 136:22** Warning [Java, 16777221]: The type ChatColor is deprecated
- **Line 226:26** Warning [Java, 536871865]: Null type mismatch (type annotations): required '@NonNull Location' but this expression has type '@Nullable Location'
- **Line 226:48** Warning [Java, 536871898]: Null type safety (type annotations): The expression of type 'Sound' needs unchecked conversion to conform to '@NonNull Sound'
- **Line 233:26** Warning [Java, 536871865]: Null type mismatch (type annotations): required '@NonNull Location' but this expression has type '@Nullable Location'
- **Line 233:48** Warning [Java, 536871898]: Null type safety (type annotations): The expression of type 'Sound' needs unchecked conversion to conform to '@NonNull Sound'
- **Line 240:26** Warning [Java, 536871865]: Null type mismatch (type annotations): required '@NonNull Location' but this expression has type '@Nullable Location'
- **Line 240:48** Warning [Java, 536871898]: Null type safety (type annotations): The expression of type 'Sound' needs unchecked conversion to conform to '@NonNull Sound'
- **Line 253:38** Warning [Java, 536871898]: Null type safety (type annotations): The expression of type 'Inventory' needs unchecked conversion to conform to '@NonNull Inventory'
- **Line 294:48** Warning [Java, 67108967]: The method getDisplayName() from the type ItemMeta is deprecated

## src/main/java/de/zfzfg/pvpwager/gui/ArenaSelectionGui.java

- **Line 37:28** Warning [Java, 67108967]: The method createInventory(InventoryHolder, int, String) from the type Bukkit is deprecated
- **Line 202:13** Warning [Java, 536870973]: The value of the local variable index is not used

## src/main/java/de/zfzfg/pvpwager/gui/ConfirmationGui.java

- **Line 28:30** Warning [Java, 570425421]: The value of the field ConfirmationGui.ITEMS_DISPLAY_START is not used
- **Line 42:28** Warning [Java, 67108967]: The method createInventory(InventoryHolder, int, String) from the type Bukkit is deprecated
- **Line 84:18** Warning [Java, 67108967]: The method setDisplayName(String) from the type ItemMeta is deprecated
- **Line 85:18** Warning [Java, 67108967]: The method setLore(List<String>) from the type ItemMeta is deprecated

## src/main/java/de/zfzfg/pvpwager/gui/CounterOfferItemGui.java

- **Line 17:8** Warning [Java, 268435844]: The import java.util.Map is never used
- **Line 74:28** Warning [Java, 67108967]: The method createInventory(InventoryHolder, int, String) from the type Bukkit is deprecated
- **Line 133:79** Warning [Java, 67108967]: The method getLore() from the type ItemMeta is deprecated
- **Line 136:26** Warning [Java, 67108967]: The method setLore(List<String>) from the type ItemMeta is deprecated
- **Line 195:83** Warning [Java, 67108967]: The method getLore() from the type ItemMeta is deprecated
- **Line 198:30** Warning [Java, 67108967]: The method setLore(List<String>) from the type ItemMeta is deprecated
- **Line 369:46** Warning [Java, 67108967]: The method getDisplayName() from the type ItemMeta is deprecated
- **Line 369:76** Warning [Java, 67108967]: The method getDisplayName() from the type ItemMeta is deprecated

## src/main/java/de/zfzfg/pvpwager/gui/EquipmentSelectionGui.java

- **Line 47:28** Warning [Java, 67108967]: The method createInventory(InventoryHolder, int, String) from the type Bukkit is deprecated

## src/main/java/de/zfzfg/pvpwager/gui/GuiManager.java

- **Line 111:16** Warning [Java, 536870973]: The value of the local variable title is not used
- **Line 111:44** Warning [Java, 67108967]: The method getDefaultTitle() from the type InventoryType is deprecated

## src/main/java/de/zfzfg/pvpwager/gui/ItemSelectionGui.java

- **Line 50:28** Warning [Java, 67108967]: The method createInventory(InventoryHolder, int, String) from the type Bukkit is deprecated
- **Line 144:71** Warning [Java, 67108967]: The method getLore() from the type ItemMeta is deprecated
- **Line 147:18** Warning [Java, 67108967]: The method setLore(List<String>) from the type ItemMeta is deprecated
- **Line 155:71** Warning [Java, 67108967]: The method getLore() from the type ItemMeta is deprecated
- **Line 158:18** Warning [Java, 67108967]: The method setLore(List<String>) from the type ItemMeta is deprecated

## src/main/java/de/zfzfg/pvpwager/gui/livetrade/LiveTradeGui.java

- **Line 95:30** Warning [Java, 570425421]: The value of the field LiveTradeGui.CENTER_COLUMN is not used
- **Line 118:40** Warning [Java, 570425421]: The value of the field LiveTradeGui.TOP_FILLER is not used
- **Line 119:40** Warning [Java, 570425421]: The value of the field LiveTradeGui.BOTTOM_FILLER is not used
- **Line 133:28** Warning [Java, 67108967]: The method createInventory(InventoryHolder, int, String) from the type Bukkit is deprecated
- **Line 141:38** Warning [Java, 536871898]: Null type safety (type annotations): The expression of type 'Inventory' needs unchecked conversion to conform to '@NonNull Inventory'
- **Line 231:19** Warning [Java, 67108967]: The method setDisplayName(String) from the type ItemMeta is deprecated
- **Line 240:19** Warning [Java, 67108967]: The method setLore(List<String>) from the type ItemMeta is deprecated
- **Line 250:19** Warning [Java, 67108967]: The method setDisplayName(String) from the type ItemMeta is deprecated
- **Line 259:19** Warning [Java, 67108967]: The method setLore(List<String>) from the type ItemMeta is deprecated
- **Line 559:34** Warning [Java, 536871865]: Null type mismatch (type annotations): required '@NonNull Location' but this expression has type '@Nullable Location'
- **Line 559:56** Warning [Java, 536871898]: Null type safety (type annotations): The expression of type 'Sound' needs unchecked conversion to conform to '@NonNull Sound'
- **Line 563:34** Warning [Java, 536871865]: Null type mismatch (type annotations): required '@NonNull Location' but this expression has type '@Nullable Location'
- **Line 563:56** Warning [Java, 536871898]: Null type safety (type annotations): The expression of type 'Sound' needs unchecked conversion to conform to '@NonNull Sound'
- **Line 573:30** Warning [Java, 536871865]: Null type mismatch (type annotations): required '@NonNull Location' but this expression has type '@Nullable Location'
- **Line 573:52** Warning [Java, 536871898]: Null type safety (type annotations): The expression of type 'Sound' needs unchecked conversion to conform to '@NonNull Sound'
- **Line 614:34** Warning [Java, 536871865]: Null type mismatch (type annotations): required '@NonNull Location' but this expression has type '@Nullable Location'
- **Line 614:56** Warning [Java, 536871898]: Null type safety (type annotations): The expression of type 'Sound' needs unchecked conversion to conform to '@NonNull Sound'
- **Line 624:30** Warning [Java, 536871865]: Null type mismatch (type annotations): required '@NonNull Location' but this expression has type '@Nullable Location'
- **Line 624:52** Warning [Java, 536871898]: Null type safety (type annotations): The expression of type 'Sound' needs unchecked conversion to conform to '@NonNull Sound'
- **Line 644:34** Warning [Java, 536871865]: Null type mismatch (type annotations): required '@NonNull Location' but this expression has type '@Nullable Location'
- **Line 644:56** Warning [Java, 536871898]: Null type safety (type annotations): The expression of type 'Sound' needs unchecked conversion to conform to '@NonNull Sound'
- **Line 665:34** Warning [Java, 536871865]: Null type mismatch (type annotations): required '@NonNull Location' but this expression has type '@Nullable Location'
- **Line 665:56** Warning [Java, 536871898]: Null type safety (type annotations): The expression of type 'Sound' needs unchecked conversion to conform to '@NonNull Sound'
- **Line 671:30** Warning [Java, 536871865]: Null type mismatch (type annotations): required '@NonNull Location' but this expression has type '@Nullable Location'
- **Line 671:52** Warning [Java, 536871898]: Null type safety (type annotations): The expression of type 'Sound' needs unchecked conversion to conform to '@NonNull Sound'
- **Line 682:18** Warning [Java, 603979894]: The method handlePlaceItem(int, ItemStack) from the type LiveTradeGui is never used locally
- **Line 687:30** Warning [Java, 536871865]: Null type mismatch (type annotations): required '@NonNull Location' but this expression has type '@Nullable Location'
- **Line 687:52** Warning [Java, 536871898]: Null type safety (type annotations): The expression of type 'Sound' needs unchecked conversion to conform to '@NonNull Sound'
- **Line 692:26** Warning [Java, 536871865]: Null type mismatch (type annotations): required '@NonNull Location' but this expression has type '@Nullable Location'
- **Line 692:48** Warning [Java, 536871898]: Null type safety (type annotations): The expression of type 'Sound' needs unchecked conversion to conform to '@NonNull Sound'
- **Line 708:34** Warning [Java, 536871865]: Null type mismatch (type annotations): required '@NonNull Location' but this expression has type '@Nullable Location'
- **Line 708:56** Warning [Java, 536871898]: Null type safety (type annotations): The expression of type 'Sound' needs unchecked conversion to conform to '@NonNull Sound'
- **Line 739:26** Warning [Java, 536871865]: Null type mismatch (type annotations): required '@NonNull Location' but this expression has type '@Nullable Location'
- **Line 739:48** Warning [Java, 536871898]: Null type safety (type annotations): The expression of type 'Sound' needs unchecked conversion to conform to '@NonNull Sound'
- **Line 775:26** Warning [Java, 536871865]: Null type mismatch (type annotations): required '@NonNull Location' but this expression has type '@Nullable Location'
- **Line 775:48** Warning [Java, 536871898]: Null type safety (type annotations): The expression of type 'Sound' needs unchecked conversion to conform to '@NonNull Sound'
- **Line 816:30** Warning [Java, 536871865]: Null type mismatch (type annotations): required '@NonNull Location' but this expression has type '@Nullable Location'
- **Line 816:52** Warning [Java, 536871898]: Null type safety (type annotations): The expression of type 'Sound' needs unchecked conversion to conform to '@NonNull Sound'
- **Line 836:26** Warning [Java, 536871865]: Null type mismatch (type annotations): required '@NonNull Location' but this expression has type '@Nullable Location'
- **Line 836:48** Warning [Java, 536871898]: Null type safety (type annotations): The expression of type 'Sound' needs unchecked conversion to conform to '@NonNull Sound'
- **Line 895:18** Warning [Java, 67108967]: The method setDisplayName(String) from the type ItemMeta is deprecated
- **Line 905:18** Warning [Java, 67108967]: The method setDisplayName(String) from the type ItemMeta is deprecated
- **Line 911:22** Warning [Java, 67108967]: The method setLore(List<String>) from the type ItemMeta is deprecated
- **Line 922:18** Warning [Java, 67108967]: The method setDisplayName(String) from the type ItemMeta is deprecated
- **Line 927:26** Warning [Java, 67108967]: The method setLore(List<String>) from the type ItemMeta is deprecated

## src/main/java/de/zfzfg/pvpwager/gui/livetrade/LiveTradeListener.java

- **Line 19:31** Warning [Java, 570425421]: The value of the field LiveTradeListener.plugin is not used
- **Line 43:19** Warning [Java, 536870973]: The value of the local variable clickedInv is not used

## src/main/java/de/zfzfg/pvpwager/gui/livetrade/LiveTradePlayer.java

- **Line 259:30** Warning [Java, 536871865]: Null type mismatch (type annotations): required '@NonNull Location' but this expression has type '@Nullable Location'
- **Line 259:52** Warning [Java, 536871898]: Null type safety (type annotations): The expression of type 'Sound' needs unchecked conversion to conform to '@NonNull Sound'
- **Line 264:26** Warning [Java, 536871865]: Null type mismatch (type annotations): required '@NonNull Location' but this expression has type '@Nullable Location'
- **Line 264:48** Warning [Java, 536871898]: Null type safety (type annotations): The expression of type 'Sound' needs unchecked conversion to conform to '@NonNull Sound'
- **Line 273:26** Warning [Java, 536871865]: Null type mismatch (type annotations): required '@NonNull Location' but this expression has type '@Nullable Location'
- **Line 273:48** Warning [Java, 536871898]: Null type safety (type annotations): The expression of type 'Sound' needs unchecked conversion to conform to '@NonNull Sound'

## src/main/java/de/zfzfg/pvpwager/gui/livetrade/LiveTradeSession.java

- **Line 95:40** Warning [Java, 536871831]: Potential null pointer access: The method getLocation() may return null
- **Line 96:40** Warning [Java, 536871831]: Potential null pointer access: The method getLocation() may return null
- **Line 209:43** Warning [Java, 536871865]: Null type mismatch (type annotations): required '@NonNull Location' but this expression has type '@Nullable Location'
- **Line 209:78** Warning [Java, 536871898]: Null type safety (type annotations): The expression of type 'Sound' needs unchecked conversion to conform to '@NonNull Sound'
- **Line 210:43** Warning [Java, 536871865]: Null type mismatch (type annotations): required '@NonNull Location' but this expression has type '@Nullable Location'
- **Line 210:78** Warning [Java, 536871898]: Null type safety (type annotations): The expression of type 'Sound' needs unchecked conversion to conform to '@NonNull Sound'
- **Line 221:39** Warning [Java, 536871865]: Null type mismatch (type annotations): required '@NonNull Location' but this expression has type '@Nullable Location'
- **Line 221:74** Warning [Java, 536871898]: Null type safety (type annotations): The expression of type 'Sound' needs unchecked conversion to conform to '@NonNull Sound'
- **Line 222:39** Warning [Java, 536871865]: Null type mismatch (type annotations): required '@NonNull Location' but this expression has type '@Nullable Location'
- **Line 222:74** Warning [Java, 536871898]: Null type safety (type annotations): The expression of type 'Sound' needs unchecked conversion to conform to '@NonNull Sound'

## src/main/java/de/zfzfg/pvpwager/gui/MoneySelectionGui.java

- **Line 54:28** Warning [Java, 67108967]: The method createInventory(InventoryHolder, int, String) from the type Bukkit is deprecated

## src/main/java/de/zfzfg/pvpwager/gui/NegotiationGui.java

- **Line 13:8** Warning [Java, 268435844]: The import java.util.ArrayList is never used
- **Line 61:28** Warning [Java, 67108967]: The method createInventory(InventoryHolder, int, String) from the type Bukkit is deprecated
- **Line 146:18** Warning [Java, 67108967]: The method setDisplayName(String) from the type ItemMeta is deprecated
- **Line 147:18** Warning [Java, 67108967]: The method setLore(List<String>) from the type ItemMeta is deprecated
- **Line 190:18** Warning [Java, 67108967]: The method setDisplayName(String) from the type ItemMeta is deprecated
- **Line 191:18** Warning [Java, 67108967]: The method setLore(List<String>) from the type ItemMeta is deprecated
- **Line 347:26** Warning [Java, 536871865]: Null type mismatch (type annotations): required '@NonNull Location' but this expression has type '@Nullable Location'
- **Line 347:48** Warning [Java, 536871898]: Null type safety (type annotations): The expression of type 'Sound' needs unchecked conversion to conform to '@NonNull Sound'

## src/main/java/de/zfzfg/pvpwager/gui/ResponseGui.java

- **Line 9:8** Warning [Java, 268435844]: The import org.bukkit.event.inventory.ClickType is never used
- **Line 48:28** Warning [Java, 67108967]: The method createInventory(InventoryHolder, int, String) from the type Bukkit is deprecated
- **Line 88:18** Warning [Java, 67108967]: The method setDisplayName(String) from the type ItemMeta is deprecated
- **Line 89:18** Warning [Java, 67108967]: The method setLore(List<String>) from the type ItemMeta is deprecated

## src/main/java/de/zfzfg/pvpwager/gui/ResponseItemSelectionGui.java

- **Line 51:28** Warning [Java, 67108967]: The method createInventory(InventoryHolder, int, String) from the type Bukkit is deprecated
- **Line 142:71** Warning [Java, 67108967]: The method getLore() from the type ItemMeta is deprecated
- **Line 145:18** Warning [Java, 67108967]: The method setLore(List<String>) from the type ItemMeta is deprecated
- **Line 153:71** Warning [Java, 67108967]: The method getLore() from the type ItemMeta is deprecated
- **Line 156:18** Warning [Java, 67108967]: The method setLore(List<String>) from the type ItemMeta is deprecated

## src/main/java/de/zfzfg/pvpwager/gui/ResponseMoneySelectionGui.java

- **Line 49:28** Warning [Java, 67108967]: The method createInventory(InventoryHolder, int, String) from the type Bukkit is deprecated

## src/main/java/de/zfzfg/pvpwager/gui/WagerMainGui.java

- **Line 38:28** Warning [Java, 67108967]: The method createInventory(InventoryHolder, int, String) from the type Bukkit is deprecated
- **Line 71:18** Warning [Java, 67108967]: The method setDisplayName(String) from the type ItemMeta is deprecated
- **Line 72:18** Warning [Java, 67108967]: The method setLore(List<String>) from the type ItemMeta is deprecated
- **Line 319:18** Warning [Java, 603979894]: The method cancelAndClose() from the type WagerMainGui is never used locally

## src/main/java/de/zfzfg/pvpwager/gui/WagerSession.java

- **Line 43:20** Warning [Java, 570425421]: The value of the field WagerSession.originalMoney is not used

## src/main/java/de/zfzfg/pvpwager/listeners/PvPListener.java

- **Line 161:19** Warning [Java, 67108967]: The method setDeathMessage(String) from the type PlayerDeathEvent is deprecated
- **Line 340:35** Warning [Java, 536871364]: Potential null pointer access: The variable current may be null at this location
- **Line 351:20** Warning [Java, 536870973]: The value of the local variable expectedCoords is not used
- **Line 383:35** Warning [Java, 536871364]: Potential null pointer access: The variable current may be null at this location
- **Line 402:21** Warning [Java, 603979894]: The method isArenaWorldUnloaded(Match) from the type PvPListener is never used locally

## src/main/java/de/zfzfg/pvpwager/listeners/WorldChangeListener.java

- **Line 110:30** Warning [Java, 536871865]: Null type mismatch (type annotations): required '@NonNull Location' but this expression has type '@Nullable Location'
- **Line 110:52** Warning [Java, 536871898]: Null type safety (type annotations): The expression of type 'Sound' needs unchecked conversion to conform to '@NonNull Sound'

## src/main/java/de/zfzfg/pvpwager/managers/CommandRequestManager.java

- **Line 51:16** Warning [Java, 16777221]: The type ChatColor is deprecated
- **Line 147:76** Warning [Java, 536871898]: Null type safety (type annotations): The expression of type 'Component' needs unchecked conversion to conform to '@NonNull Component'
- **Line 162:76** Warning [Java, 536871898]: Null type safety (type annotations): The expression of type 'Component' needs unchecked conversion to conform to '@NonNull Component'

## src/main/java/de/zfzfg/pvpwager/managers/EquipmentManager.java

- **Line 215:53** Warning [Java, 67109822]: Null type safety: parameter 'this' provided via method descriptor ToIntFunction<EquipmentSet>.applyAsInt(EquipmentSet) needs unchecked conversion to conform to '@NonNull EquipmentSet'
- **Line 216:32** Warning [Java, 67109822]: Null type safety: parameter 'this' provided via method descriptor Function<EquipmentSet,String>.apply(EquipmentSet) needs unchecked conversion to conform to '@NonNull EquipmentSet'

## src/main/java/de/zfzfg/pvpwager/managers/MatchManager.java

- **Line 81:16** Warning [Java, 16777221]: The type ChatColor is deprecated
- **Line 334:31** Warning [Java, 536871865]: Null type mismatch (type annotations): required '@NonNull Location' but this expression has type '@Nullable Location'
- **Line 334:54** Warning [Java, 536871898]: Null type safety (type annotations): The expression of type 'Sound' needs unchecked conversion to conform to '@NonNull Sound'
- **Line 335:31** Warning [Java, 536871865]: Null type mismatch (type annotations): required '@NonNull Location' but this expression has type '@Nullable Location'
- **Line 335:54** Warning [Java, 536871898]: Null type safety (type annotations): The expression of type 'Sound' needs unchecked conversion to conform to '@NonNull Sound'
- **Line 777:35** Warning [Java, 536871865]: Null type mismatch (type annotations): required '@NonNull Location' but this expression has type '@Nullable Location'
- **Line 777:58** Warning [Java, 536871898]: Null type safety (type annotations): The expression of type 'Sound' needs unchecked conversion to conform to '@NonNull Sound'
- **Line 778:35** Warning [Java, 536871865]: Null type mismatch (type annotations): required '@NonNull Location' but this expression has type '@Nullable Location'
- **Line 778:58** Warning [Java, 536871898]: Null type safety (type annotations): The expression of type 'Sound' needs unchecked conversion to conform to '@NonNull Sound'
- **Line 829:35** Warning [Java, 536871865]: Null type mismatch (type annotations): required '@NonNull Location' but this expression has type '@Nullable Location'
- **Line 829:58** Warning [Java, 536871898]: Null type safety (type annotations): The expression of type 'Sound' needs unchecked conversion to conform to '@NonNull Sound'
- **Line 830:35** Warning [Java, 536871865]: Null type mismatch (type annotations): required '@NonNull Location' but this expression has type '@Nullable Location'
- **Line 830:58** Warning [Java, 536871898]: Null type safety (type annotations): The expression of type 'Sound' needs unchecked conversion to conform to '@NonNull Sound'
- **Line 854:51** Warning [Java, 536871898]: Null type safety (type annotations): The expression of type 'Component' needs unchecked conversion to conform to '@NonNull Component'
- **Line 854:72** Warning [Java, 536871898]: Null type safety (type annotations): The expression of type 'Component' needs unchecked conversion to conform to '@NonNull Component'
- **Line 858:21** Warning [Java, 536871364]: Potential null pointer access: The variable online may be null at this location
- **Line 866:21** Warning [Java, 536871364]: Potential null pointer access: The variable online may be null at this location
- **Line 891:27** Warning [Java, 536871865]: Null type mismatch (type annotations): required '@NonNull Location' but this expression has type '@Nullable Location'
- **Line 891:50** Warning [Java, 536871898]: Null type safety (type annotations): The expression of type 'Sound' needs unchecked conversion to conform to '@NonNull Sound'
- **Line 892:27** Warning [Java, 536871865]: Null type mismatch (type annotations): required '@NonNull Location' but this expression has type '@Nullable Location'
- **Line 892:50** Warning [Java, 536871898]: Null type safety (type annotations): The expression of type 'Sound' needs unchecked conversion to conform to '@NonNull Sound'
- **Line 1323:30** Warning [Java, 536871364]: Potential null pointer access: The variable current may be null at this location
- **Line 1636:33** Warning [Java, 536871831]: Potential null pointer access: The method getLocation() may return null
- **Line 1646:33** Warning [Java, 536871831]: Potential null pointer access: The method getLocation() may return null

## src/main/java/de/zfzfg/pvpwager/managers/RequestManager.java

- **Line 77:47** Warning [Java, 536871898]: Null type safety (type annotations): The expression of type 'Component' needs unchecked conversion to conform to '@NonNull Component'
- **Line 77:68** Warning [Java, 536871898]: Null type safety (type annotations): The expression of type 'Component' needs unchecked conversion to conform to '@NonNull Component'
- **Line 77:87** Warning [Java, 536871898]: Null type safety (type annotations): The expression of type 'Component' needs unchecked conversion to conform to '@NonNull Component'

## src/main/java/de/zfzfg/pvpwager/managers/SpawnManager.java

- **Line 83:22** Warning [Java, 67109822]: Null type safety: parameter 'this' provided via method descriptor Function<Location,Location>.apply(Location) needs unchecked conversion to conform to '@NonNull Location'
- **Line 86:22** Warning [Java, 67109822]: Null type safety: parameter 'this' provided via method descriptor Function<Location,Location>.apply(Location) needs unchecked conversion to conform to '@NonNull Location'

## src/main/java/de/zfzfg/pvpwager/managers/StatsManager.java

- **Line 44:49** Warning [Java, 67109822]: Null type safety: parameter 'this' provided via method descriptor ToIntFunction<PlayerStats>.applyAsInt(PlayerStats) needs unchecked conversion to conform to '@NonNull PlayerStats'

## src/main/java/de/zfzfg/pvpwager/models/CommandRequest.java

- **Line 53:39** Warning [Java, 536871831]: Potential null pointer access: The method getLocation() may return null
- **Line 54:39** Warning [Java, 536871831]: Potential null pointer access: The method getLocation() may return null

## src/main/java/de/zfzfg/pvpwager/utils/ItemBuilder.java

- **Line 26:18** Warning [Java, 67108967]: The method setDisplayName(String) from the type ItemMeta is deprecated
- **Line 40:18** Warning [Java, 67108967]: The method setLore(List<String>) from the type ItemMeta is deprecated

## src/main/java/de/zfzfg/pvpwager/utils/LocationUtil.java

- **Line 6:8** Warning [Java, 268435844]: The import org.bukkit.configuration.ConfigurationSection is never used

## src/test/java/de/zfzfg/core/util/CommandCooldownManagerTest.java

- **Line 17:25** Information [Java, 16778197]: Unsafe interpretation of method return type as '@NonNull' based on substitution 'T=@NonNull Player'. Declaring type 'Mockito' doesn't seem to be designed with null type annotations in mind
- **Line 19:47** Warning [Java, 536871898]: Null type safety (type annotations): The expression of type 'UUID' needs unchecked conversion to conform to '@NonNull UUID'
- **Line 31:30** Information [Java, 16778197]: Unsafe interpretation of method return type as '@NonNull' based on substitution 'T=@NonNull Player'. Declaring type 'Mockito' doesn't seem to be designed with null type annotations in mind
- **Line 32:52** Warning [Java, 536871898]: Null type safety (type annotations): The expression of type 'UUID' needs unchecked conversion to conform to '@NonNull UUID'
- **Line 39:25** Information [Java, 16778197]: Unsafe interpretation of method return type as '@NonNull' based on substitution 'T=@NonNull Player'. Declaring type 'Mockito' doesn't seem to be designed with null type annotations in mind
- **Line 41:47** Warning [Java, 536871898]: Null type safety (type annotations): The expression of type 'UUID' needs unchecked conversion to conform to '@NonNull UUID'

## src/test/java/de/zfzfg/core/util/ConcurrencyTest.java

- **Line 72:25** Information [Java, 16778197]: Unsafe interpretation of method return type as '@NonNull' based on substitution 'T=@NonNull Player'. Declaring type 'Mockito' doesn't seem to be designed with null type annotations in mind
- **Line 73:47** Warning [Java, 536871898]: Null type safety (type annotations): The expression of type 'UUID' needs unchecked conversion to conform to '@NonNull UUID'

## src/test/java/de/zfzfg/core/util/TextButtonTest.java

- **Line 4:8** Warning [Java, 268435844]: The import net.kyori.adventure.text.format.NamedTextColor is never used
- **Line 13:67** Warning [Java, 536871898]: Null type safety (type annotations): The expression of type 'Component' needs unchecked conversion to conform to '@NonNull Component'
- **Line 41:65** Warning [Java, 536871898]: Null type safety (type annotations): The expression of type 'Component' needs unchecked conversion to conform to '@NonNull Component'

## src/test/java/de/zfzfg/core/util/TextTest.java

- **Line 14:67** Warning [Java, 536871898]: Null type safety (type annotations): The expression of type 'Component' needs unchecked conversion to conform to '@NonNull Component'

## src/test/java/de/zfzfg/core/world/mv/WorldRestoreTest.java

- **Line 79:29** Information [Java, 16778197]: Unsafe interpretation of method return type as '@NonNull' based on substitution 'T=@NonNull IOException'. Declaring type 'Assertions' doesn't seem to be designed with null type annotations in mind
- **Line 96:30** Information [Java, 16778197]: Unsafe interpretation of method return type as '@NonNull' based on substitution 'T=@NonNull EventPlugin'. Declaring type 'Mockito' doesn't seem to be designed with null type annotations in mind
- **Line 97:35** Information [Java, 16778197]: Unsafe interpretation of method return type as '@NonNull' based on substitution 'T=@NonNull TaskManager'. Declaring type 'Mockito' doesn't seem to be designed with null type annotations in mind
- **Line 99:57** Warning [Java, 536871898]: Null type safety (type annotations): The expression of type 'File' needs unchecked conversion to conform to '@NonNull File'
- **Line 100:53** Warning [Java, 536871898]: Null type safety (type annotations): The expression of type 'Logger' needs unchecked conversion to conform to '@NonNull Logger'

## src/test/java/de/zfzfg/core/world/WorldBackupTest.java

- **Line 33:30** Information [Java, 16778197]: Unsafe interpretation of method return type as '@NonNull' based on substitution 'T=@NonNull EventPlugin'. Declaring type 'Mockito' doesn't seem to be designed with null type annotations in mind
- **Line 34:57** Warning [Java, 536871898]: Null type safety (type annotations): The expression of type 'File' needs unchecked conversion to conform to '@NonNull File'
- **Line 35:53** Warning [Java, 536871898]: Null type safety (type annotations): The expression of type 'Logger' needs unchecked conversion to conform to '@NonNull Logger'
- **Line 58:44** Information [Java, 536871895]: Unsafe null type conversion (type annotations): The value of type 'MockedStatic<@NonNull Bukkit>' is made accessible using the less-annotated type 'MockedStatic<Bukkit>'
- **Line 72:29** Warning [Java, 536871364]: Potential null pointer access: The variable e may be null at this location
- **Line 88:44** Information [Java, 536871895]: Unsafe null type conversion (type annotations): The value of type 'MockedStatic<@NonNull Bukkit>' is made accessible using the less-annotated type 'MockedStatic<Bukkit>'
- **Line 91:33** Information [Java, 16778197]: Unsafe interpretation of method return type as '@NonNull' based on substitution 'T=@NonNull IOException'. Declaring type 'Assertions' doesn't seem to be designed with null type annotations in mind
- **Line 108:44** Information [Java, 536871895]: Unsafe null type conversion (type annotations): The value of type 'MockedStatic<@NonNull Bukkit>' is made accessible using the less-annotated type 'MockedStatic<Bukkit>'
- **Line 134:44** Information [Java, 536871895]: Unsafe null type conversion (type annotations): The value of type 'MockedStatic<@NonNull Bukkit>' is made accessible using the less-annotated type 'MockedStatic<Bukkit>'
- **Line 142:51** Warning [Java, 976]: Potential null pointer access: this expression has type 'capture#2-of ? extends java.util.zip.ZipEntry', a free type variable that may represent a '@Nullable' type
- **Line 153:44** Information [Java, 536871895]: Unsafe null type conversion (type annotations): The value of type 'MockedStatic<@NonNull Bukkit>' is made accessible using the less-annotated type 'MockedStatic<Bukkit>'

## src/test/java/de/zfzfg/pvpwager/managers/MatchManagerTest.java

- **Line 3:8** Warning [Java, 268435844]: The import de.zfzfg.eventplugin.EventPlugin is never used
- **Line 5:8** Warning [Java, 268435844]: The import org.mockito.Mockito is never used

## src/test/java/de/zfzfg/pvpwager/managers/PlaceholderReplacementTest.java

- **Line 17:30** Information [Java, 16778197]: Unsafe interpretation of method return type as '@NonNull' based on substitution 'T=@NonNull EventPlugin'. Declaring type 'Mockito' doesn't seem to be designed with null type annotations in mind
- **Line 42:30** Information [Java, 16778197]: Unsafe interpretation of method return type as '@NonNull' based on substitution 'T=@NonNull EventPlugin'. Declaring type 'Mockito' doesn't seem to be designed with null type annotations in mind

## src/test/java/de/zfzfg/pvpwager/models/MatchModelTest.java

- **Line 16:21** Information [Java, 16778197]: Unsafe interpretation of method return type as '@NonNull' based on substitution 'T=@NonNull Player'. Declaring type 'Mockito' doesn't seem to be designed with null type annotations in mind
- **Line 17:21** Information [Java, 16778197]: Unsafe interpretation of method return type as '@NonNull' based on substitution 'T=@NonNull Player'. Declaring type 'Mockito' doesn't seem to be designed with null type annotations in mind
- **Line 18:51** Warning [Java, 536871898]: Null type safety (type annotations): The expression of type 'UUID' needs unchecked conversion to conform to '@NonNull UUID'
- **Line 19:51** Warning [Java, 536871898]: Null type safety (type annotations): The expression of type 'UUID' needs unchecked conversion to conform to '@NonNull UUID'
- **Line 36:21** Information [Java, 16778197]: Unsafe interpretation of method return type as '@NonNull' based on substitution 'T=@NonNull Player'. Declaring type 'Mockito' doesn't seem to be designed with null type annotations in mind
- **Line 37:21** Information [Java, 16778197]: Unsafe interpretation of method return type as '@NonNull' based on substitution 'T=@NonNull Player'. Declaring type 'Mockito' doesn't seem to be designed with null type annotations in mind
- **Line 38:51** Warning [Java, 536871898]: Null type safety (type annotations): The expression of type 'UUID' needs unchecked conversion to conform to '@NonNull UUID'
- **Line 39:51** Warning [Java, 536871898]: Null type safety (type annotations): The expression of type 'UUID' needs unchecked conversion to conform to '@NonNull UUID'
- **Line 52:21** Information [Java, 16778197]: Unsafe interpretation of method return type as '@NonNull' based on substitution 'T=@NonNull Player'. Declaring type 'Mockito' doesn't seem to be designed with null type annotations in mind
- **Line 53:21** Information [Java, 16778197]: Unsafe interpretation of method return type as '@NonNull' based on substitution 'T=@NonNull Player'. Declaring type 'Mockito' doesn't seem to be designed with null type annotations in mind
- **Line 56:51** Warning [Java, 536871898]: Null type safety (type annotations): The expression of type 'UUID' needs unchecked conversion to conform to '@NonNull UUID'
- **Line 57:51** Warning [Java, 536871898]: Null type safety (type annotations): The expression of type 'UUID' needs unchecked conversion to conform to '@NonNull UUID'
