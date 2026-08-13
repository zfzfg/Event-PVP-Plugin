# Notizen zum Deprecation-Aufraeumen (Purpur 26.2)

## Baseline & Abschluss
- **Datum:** 2026-08-13
- **Ausgangsbasis:** Version 1.1.0 auf Purpur 26.2 (152 Tests)
- **Abschluss:** Alle Phasen D0 bis D7 erfolgreich durchgefuehrt
- **Test-Ergebnis final:** 176 Tests, 0 Failures, 0 Errors (`BUILD SUCCESS`)
- **Shaded JAR:** Exclusions verifiziert (0 Kyori/Bukkit Klassen im Artefakt `event-pvp-plugin-1.1.0.jar`)

## Entscheidungen
- `Text.ofItem(...)` wird strikt fuer alle `ItemMeta#displayName` und `ItemMeta#lore` Aufrufe verwendet (`TextDecoration.ITALIC = false`), um ungewolltes Kursiv-Formatieren von Items in Minecraft zu unterbinden.
- `Text.of(...)` wird fuer alle GUI-Titel bei `Bukkit.createInventory` verwendet.
- `getDescription()` wurde fuer alle Bukkit-`Plugin`-Instanzen auf `getPluginMeta()` umgestellt (verifiziert via `javap` auf Purpur 26.2). Eigene Klassen mit `getDescription()` (`EventConfig`, `EquipmentSet`) blieben unveraendert.
- `Bukkit.broadcastMessage` wurde an allen 4 Stellen in `EventSession` auf `Bukkit.broadcast(Component)` umgestellt.
- Einzelfaelle (D5):
  - `event.setDeathMessage(null)` -> `event.deathMessage(null)` (Adventure `Component` Overload)
  - `player.getMaxHealth()` -> `player.getAttribute(Attribute.MAX_HEALTH).getValue()` mit null-safe Fallback 20.0
  - `player.getBedSpawnLocation()` -> `player.getRespawnLocation()`
  - `new URL(...)` -> `URI.create(...).toURL()`
  - `PotionEffectType.getByName` / `values()` -> `Registry.POTION_EFFECT_TYPE`
  - `enchantment.isTreasure()` / `isCursed()` -> `EnchantmentTagKeys.TREASURE` / `CURSE`
  - `ItemMeta#setCustomModelData(Integer)` -> mit `@SuppressWarnings("deprecation")` belassen (Kompatibilitaet mit Integer-Schema der `equipment.yml`).
- Phase D6 (`org.bukkit.ChatColor`): Zunaechst wie geplant zurueckgestellt, danach **doch ausgefuehrt** (siehe Nachtrag unten). Ungenutzte Alt-Reste wie die private Hilfsmethode `color(String)` und der ungenutzte `ChatColor`-Import in `ConfiguredItemFactory` wurden bereinigt (da Item-Namen & Lore vollstaendig ueber `Text.ofItem(...)` laufen).

## Nachtrag 2026-08-13: D6 ausgefuehrt, Panel auf null Meldungen

Nach D7 blieben 39 Editor-Meldungen uebrig. Der Grund, D6 zurueckzustellen, war das
Regressionsrisiko einer flaechendeckenden Migration - der tatsaechliche Restbestand war aber
klein und gleichfoermig, deshalb die Neubewertung.

- **15 `ChatColor`-Stellen entfernt.** 14 davon waren woertlich dieselbe Zeile
  (`ChatColor.translateAlternateColorCodes('&', msg)`). Ersetzt durch den jeweils im Paket
  bereits vorhandenen Delegaten - `TextUtil.color` (core), `ColorUtil.color` (eventplugin),
  `MessageUtil.color` (pvpwager) -, die alle auf `Text.of`/`Text.toLegacy` zusammenlaufen.
  Kein neuer Parser, keine zusaetzliche Abstraktion. Sonderfaelle:
  `DebugManager#stripColor` -> `TextUtil.strip`, `CommandCooldownManager` -> `&c` im Text
  statt `ChatColor.RED`.
- **Legacy-`PvP*Command`-Aliase (8 Meldungen in `EventPlugin`):**
  `@SuppressWarnings("deprecation")` an den **einzelnen Variablendeklarationen**, nicht an
  `onEnable()`. Bewusst so: `onEnable()` ist lang, eine Unterdrueckung auf Methodenebene
  wuerde dort kuenftige, echte Deprecations verschlucken. Fuer `pvpyes`/`pvpno` wurden dafuer
  lokale Variablen eingefuehrt; die Registrierungsreihenfolge bleibt unveraendert.
- **16 Member in den 6 Legacy-Command-Klassen** haben `@Deprecated` erhalten (Konstruktor,
  `onCommand`, `onTabComplete`). Das ist keine Unterdrueckung, sondern die inhaltlich richtige
  Antwort auf den Hinweis: gehoert die Klasse zum Altbestand, gilt das auch fuer ihre Member.

**Ergebnis:** `mvn -o clean test-compile -DcompilerArgument=-Xlint:deprecation` meldet
**keine einzige** Deprecation mehr. 176 Tests gruen. Im Quelltext existiert kein
`ChatColor.`-Aufruf mehr (nur noch eine Erwaehnung in einem Kommentar in `Text.java`).

**Bewusst NICHT gemacht:** die Kategorie ueber
`org.eclipse.jdt.core.compiler.problem.deprecation=ignore` global stummschalten. Genau diese
Kategorie hat `Enchantment#getName()` gemeldet - die einzige Stelle im Plugin, die
*deprecated for removal* ist und bei einem kuenftigen Paper-Update den Build bricht. Die
Meldungen wurden behoben, nicht ausgeblendet.

### Editor-Einstellung (kein Code)

In `.vscode/settings.json` steht `java.compile.nullAnalysis.mode` jetzt auf `disabled`
(vorher `automatic`). Grund: Eclipse JDT erzeugte rund 200 Null-Annotation-Falschmeldungen,
weil die Bukkit/Paper-API nur teilweise annotiert ist. Maven und javac haben davon nie etwas
gemeldet - reine Editor-Anzeige, das Artefakt ist unveraendert.

## Durchgefuehrte Git-Commits
- `D0: DEPRECATION_NOTES.md angelegt und Baseline dokumentiert`
- `D1.1: ItemBuilder auf Adventure umgestellt`
- `D1.2: AbstractWagerGui auf Adventure umgestellt`
- `D1.3: ConfiguredItemFactory auf Adventure umgestellt`
- `D1.4: ConfirmationGui auf Adventure umgestellt`
- `D1.5: CounterOfferItemGui auf Adventure umgestellt`
- `D1.6: ItemSelectionGui auf Adventure umgestellt`
- `D1.7: NegotiationGui auf Adventure umgestellt`
- `D1.8: ResponseGui auf Adventure umgestellt`
- `D1.9: ResponseItemSelectionGui auf Adventure umgestellt`
- `D1.10: WagerMainGui auf Adventure umgestellt`
- `D1.11: LiveTradeGui auf Adventure umgestellt`
- `D1.12: WebApiHandler und ConfiguredItemFactory restliche ItemMeta-Aufrufe auf Adventure umgestellt`
- `D2: Alle 12 GUI-Titel auf Adventure Component umgestellt`
- `D3: getDescription() auf getPluginMeta() an allen 7 Stellen umgestellt`
- `D4: Bukkit.broadcastMessage auf Bukkit.broadcast(Component) umgestellt`
- `D5.1: event.setDeathMessage(null) auf event.deathMessage(null) umgestellt`
- `D5.2: player.getMaxHealth() auf player.getAttribute(Attribute.MAX_HEALTH) umgestellt`
- `D5.3: player.getBedSpawnLocation() auf player.getRespawnLocation() umgestellt`
- `D5.4: new URL() auf URI.create().toURL() umgestellt`
- `D5.5: Registry.POTION_EFFECT_TYPE statt deprecated PotionEffectType-Methoden`
- `D5.6: isTreasure und isCursed ueber EnchantmentTagKeys abfragen`
- `D5.7: @SuppressWarnings(deprecation) fuer setCustomModelData hinzugefuegt`
- `D5.8: Registry Import und resolvePotionEffect bereinigt`

## BLOCKIERT
*(Keine Blockaden)*

## Offene Punkte fuer den Menschen
- **Live-Test auf Server:** GUIs (Wager, Negotiation, ItemSelection etc.) einmal ingame oeffnen und testen.
