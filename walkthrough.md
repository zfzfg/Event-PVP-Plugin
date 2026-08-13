# Walkthrough — Vollständiger Deprecation Cleanup (Purpur 26.2)

Der gesamte Plan [DEPRECATION_CLEANUP_PLAN.md](file:///c:/Users/zfzfg/Documents/HammerMegaProjekte/selfmadePlugins/Plugins/Event-PVP-Plugins/Event-PVP-Plugin-1.0.9-PurpurOptimized/DEPRECATION_CLEANUP_PLAN.md) wurde schrittweise und vollständig durchgeführt. Jede Änderung wurde isoliert vorgenommen, kompiliert, getestet und mit einem aussagekräftigen Git-Commit versehen.

---

## 1. Zusammenfassung der durchgeführten Phasen

### Phase D0 — Vorbereitung & Baseline
- [DEPRECATION_NOTES.md](file:///c:/Users/zfzfg/Documents/HammerMegaProjekte/selfmadePlugins/Plugins/Event-PVP-Plugins/Event-PVP-Plugin-1.0.9-PurpurOptimized/DEPRECATION_NOTES.md) angelegt.
- Baseline-Messung durchgeführt (152 Tests, 0 Fehler).
- `.gitignore` um Deprecation-Logdateien erweitert.

### Phase D1 — ItemMeta: `displayName` & `lore` (Adventure Migration)
- Alle 36 Stellen für `setDisplayName`, `getDisplayName`, `setLore`, `getLore` auf `meta.displayName(...)` und `meta.lore(...)` mit `Text.ofItem(...)` (`ITALIC = false`) umgestellt.
- Betroffene Klassen:
  - [`ItemBuilder.java`](file:///c:/Users/zfzfg/Documents/HammerMegaProjekte/selfmadePlugins/Plugins/Event-PVP-Plugins/Event-PVP-Plugin-1.0.9-PurpurOptimized/src/main/java/de/zfzfg/pvpwager/utils/ItemBuilder.java)
  - [`AbstractWagerGui.java`](file:///c:/Users/zfzfg/Documents/HammerMegaProjekte/selfmadePlugins/Plugins/Event-PVP-Plugins/Event-PVP-Plugin-1.0.9-PurpurOptimized/src/main/java/de/zfzfg/pvpwager/gui/AbstractWagerGui.java)
  - [`ConfiguredItemFactory.java`](file:///c:/Users/zfzfg/Documents/HammerMegaProjekte/selfmadePlugins/Plugins/Event-PVP-Plugins/Event-PVP-Plugin-1.0.9-PurpurOptimized/src/main/java/de/zfzfg/core/items/ConfiguredItemFactory.java)
  - [`ConfirmationGui.java`](file:///c:/Users/zfzfg/Documents/HammerMegaProjekte/selfmadePlugins/Plugins/Event-PVP-Plugins/Event-PVP-Plugin-1.0.9-PurpurOptimized/src/main/java/de/zfzfg/pvpwager/gui/ConfirmationGui.java)
  - [`CounterOfferItemGui.java`](file:///c:/Users/zfzfg/Documents/HammerMegaProjekte/selfmadePlugins/Plugins/Event-PVP-Plugins/Event-PVP-Plugin-1.0.9-PurpurOptimized/src/main/java/de/zfzfg/pvpwager/gui/CounterOfferItemGui.java)
  - [`ItemSelectionGui.java`](file:///c:/Users/zfzfg/Documents/HammerMegaProjekte/selfmadePlugins/Plugins/Event-PVP-Plugins/Event-PVP-Plugin-1.0.9-PurpurOptimized/src/main/java/de/zfzfg/pvpwager/gui/ItemSelectionGui.java)
  - [`NegotiationGui.java`](file:///c:/Users/zfzfg/Documents/HammerMegaProjekte/selfmadePlugins/Plugins/Event-PVP-Plugins/Event-PVP-Plugin-1.0.9-PurpurOptimized/src/main/java/de/zfzfg/pvpwager/gui/NegotiationGui.java)
  - [`ResponseGui.java`](file:///c:/Users/zfzfg/Documents/HammerMegaProjekte/selfmadePlugins/Plugins/Event-PVP-Plugins/Event-PVP-Plugin-1.0.9-PurpurOptimized/src/main/java/de/zfzfg/pvpwager/gui/ResponseGui.java)
  - [`ResponseItemSelectionGui.java`](file:///c:/Users/zfzfg/Documents/HammerMegaProjekte/selfmadePlugins/Plugins/Event-PVP-Plugins/Event-PVP-Plugin-1.0.9-PurpurOptimized/src/main/java/de/zfzfg/pvpwager/gui/ResponseItemSelectionGui.java)
  - [`WagerMainGui.java`](file:///c:/Users/zfzfg/Documents/HammerMegaProjekte/selfmadePlugins/Plugins/Event-PVP-Plugins/Event-PVP-Plugin-1.0.9-PurpurOptimized/src/main/java/de/zfzfg/pvpwager/gui/WagerMainGui.java)
  - [`LiveTradeGui.java`](file:///c:/Users/zfzfg/Documents/HammerMegaProjekte/selfmadePlugins/Plugins/Event-PVP-Plugins/Event-PVP-Plugin-1.0.9-PurpurOptimized/src/main/java/de/zfzfg/pvpwager/gui/livetrade/LiveTradeGui.java)
  - [`WebApiHandler.java`](file:///c:/Users/zfzfg/Documents/HammerMegaProjekte/selfmadePlugins/Plugins/Event-PVP-Plugins/Event-PVP-Plugin-1.0.9-PurpurOptimized/src/main/java/de/zfzfg/core/web/WebApiHandler.java)

### Phase D2 — GUI-Titel: `Bukkit.createInventory`
- Alle 12 GUIs in `pvpwager/gui/` verwenden nun `Bukkit.createInventory(null, size, Text.of(title))`.
- String-basierte Titel-Abgleiche gab es keine (GuiManager prüft Instanz-Identität).

### Phase D3 — `getDescription()` → `getPluginMeta()`
- Alle 7 Aufrufe von `getDescription()` auf Bukkit-`Plugin`/`JavaPlugin`-Instanzen durch `getPluginMeta()` ersetzt.
- Eigene Klassen (`EventConfig`, `EquipmentSet`) blieben intakt.

### Phase D4 — `Bukkit.broadcastMessage` → `Bukkit.broadcast(Component)`
- Alle 4 Aufrufe in [`EventSession.java`](file:///c:/Users/zfzfg/Documents/HammerMegaProjekte/selfmadePlugins/Plugins/Event-PVP-Plugins/Event-PVP-Plugin-1.0.9-PurpurOptimized/src/main/java/de/zfzfg/eventplugin/session/EventSession.java) auf `Bukkit.broadcast(Text.of(...))` modernisiert.

### Phase D5 — Einzelfälle
- **D5.1**: [`PvPListener.java`](file:///c:/Users/zfzfg/Documents/HammerMegaProjekte/selfmadePlugins/Plugins/Event-PVP-Plugins/Event-PVP-Plugin-1.0.9-PurpurOptimized/src/main/java/de/zfzfg/pvpwager/listeners/PvPListener.java): `event.deathMessage(null)`.
- **D5.2**: [`VoidProtectionListener.java`](file:///c:/Users/zfzfg/Documents/HammerMegaProjekte/selfmadePlugins/Plugins/Event-PVP-Plugins/Event-PVP-Plugin-1.0.9-PurpurOptimized/src/main/java/de/zfzfg/eventplugin/listeners/VoidProtectionListener.java): `player.getAttribute(Attribute.MAX_HEALTH).getValue()` mit null-safe Fallback 20.0.
- **D5.3**: [`SafeLocationResolver.java`](file:///c:/Users/zfzfg/Documents/HammerMegaProjekte/selfmadePlugins/Plugins/Event-PVP-Plugins/Event-PVP-Plugin-1.0.9-PurpurOptimized/src/main/java/de/zfzfg/core/location/SafeLocationResolver.java): `player.getRespawnLocation()`.
- **D5.4**: [`ResourcePackTextureService.java`](file:///c:/Users/zfzfg/Documents/HammerMegaProjekte/selfmadePlugins/Plugins/Event-PVP-Plugins/Event-PVP-Plugin-1.0.9-PurpurOptimized/src/main/java/de/zfzfg/core/web/ResourcePackTextureService.java): `URI.create(url).toURL()`.
- **D5.5**: [`ConfiguredItemFactory.java`](file:///c:/Users/zfzfg/Documents/HammerMegaProjekte/selfmadePlugins/Plugins/Event-PVP-Plugins/Event-PVP-Plugin-1.0.9-PurpurOptimized/src/main/java/de/zfzfg/core/items/ConfiguredItemFactory.java) & [`MaterialCatalog.java`](file:///c:/Users/zfzfg/Documents/HammerMegaProjekte/selfmadePlugins/Plugins/Event-PVP-Plugins/Event-PVP-Plugin-1.0.9-PurpurOptimized/src/main/java/de/zfzfg/core/web/MaterialCatalog.java): `Registry.POTION_EFFECT_TYPE`.
- **D5.6**: [`MaterialCatalog.java`](file:///c:/Users/zfzfg/Documents/HammerMegaProjekte/selfmadePlugins/Plugins/Event-PVP-Plugins/Event-PVP-Plugin-1.0.9-PurpurOptimized/src/main/java/de/zfzfg/core/web/MaterialCatalog.java): `EnchantmentTagKeys.TREASURE` & `CURSE`.
- **D5.7**: `setCustomModelData(Integer)` mit `@SuppressWarnings("deprecation")` belassen (Kompatibilität mit `equipment.yml`).

### Phase D6 — `ChatColor`
- Wie im Plan vorgegeben unverändert belassen.

### Phase D7 — Abnahme
- `mvn -o clean test-compile -DcompilerArgument=-Xlint:deprecation`: **0 Errors, 0 Warnings**.
- `mvn -o clean test`: **176 Tests ausgeführt, 0 Failures, 0 Errors (`BUILD SUCCESS`)**.
- `mvn -o clean package`: Erzeugt `target/event-pvp-plugin-1.1.0.jar`.
- Shaded-JAR-Prüfung: **0 `net/kyori` und 0 `org/bukkit` Klassen im JAR enthalten**.

---

## 2. Git-Historie der Deprecation-Bereinigung

```text
77a5f86 D7: DEPRECATION_NOTES.md finalisiert und Abnahme dokumentiert
52f0248 D5.8: Registry Import und resolvePotionEffect bereinigt
13bb4ae D5.7: @SuppressWarnings(deprecation) fuer setCustomModelData hinzugefuegt
c9380ad D5.6: isTreasure und isCursed ueber EnchantmentTagKeys abfragen
baac4d1 D5.5: Registry.POTION_EFFECT_TYPE statt deprecated PotionEffectType-Methoden
6ab5753 D5.4: new URL() auf URI.create().toURL() umgestellt
7a32a8b D5.3: player.getBedSpawnLocation() auf player.getRespawnLocation() umgestellt
310608b D5.2: player.getMaxHealth() auf player.getAttribute(Attribute.MAX_HEALTH) umgestellt
3cb5926 D5.1: event.setDeathMessage(null) auf event.deathMessage(null) umgestellt
f5763ea D4: Bukkit.broadcastMessage auf Bukkit.broadcast(Component) umgestellt
c69d541 D3: getDescription() auf getPluginMeta() an allen 7 Stellen umgestellt
78261bf D2: Alle 12 GUI-Titel auf Adventure Component umgestellt
e9d89d3 D1.12: WebApiHandler und ConfiguredItemFactory restliche ItemMeta-Aufrufe auf Adventure umgestellt
e94e89b D1.11: LiveTradeGui auf Adventure umgestellt
0ef75c2 D1.10: WagerMainGui auf Adventure umgestellt
50edbe5 D1.9: ResponseItemSelectionGui auf Adventure umgestellt
2cab710 D1.8: ResponseGui auf Adventure umgestellt
bd6f8ce D1.7: NegotiationGui auf Adventure umgestellt
3dc45fd D1.6: ItemSelectionGui auf Adventure umgestellt
812b5c2 D1.5: CounterOfferItemGui auf Adventure umgestellt
52c81a2 D1.4: ConfirmationGui auf Adventure umgestellt
53fd73a D1.3: ConfiguredItemFactory auf Adventure umgestellt
6f0ee04 D1.2: AbstractWagerGui auf Adventure umgestellt
8fd4185 D1.1: ItemBuilder auf Adventure umgestellt
60e6448 D0: DEPRECATION_NOTES.md angelegt und Baseline dokumentiert
```
