# Aufräumplan: Deprecated Bukkit-API in Event-PVP-Plugin

**Zielserver:** `C:\Users\zfzfg\Documents\servers\purpur-26-2` (Purpur 26.2, Build 2618)
**Projekt:** `C:\Users\zfzfg\Documents\HammerMegaProjekte\selfmadePlugins\Plugins\Event-PVP-Plugins\Event-PVP-Plugin-1.0.9-PurpurOptimized`
**Erstellt:** 2026-08-13
**Vorgänger:** `PURPUR_26.2_MIGRATION_PLAN.md` (abgeschlossen — dieser Plan setzt darauf auf)

---

## 0. Wie dieser Plan zu lesen ist

Dieser Plan ist für eine **ausführende KI** geschrieben, die den Code nicht kennt.
Er ist in **Phasen** (D0–D7) und darin in **atomare Aufgaben** (z. B. `D1.3`) gegliedert.

**Verbindliche Arbeitsregeln — nicht abweichen:**

1. **Reihenfolge einhalten.** Phasen strikt nacheinander. Innerhalb einer Phase Aufgaben in Nummernreihenfolge.
2. **Eine Aufgabe = eine Änderung = eine Verifikation.** Nach *jeder* Aufgabe die dort angegebene Verifikation ausführen. Erst bei Erfolg zur nächsten Aufgabe.
3. **Niemals raten.** Wenn eine Signatur unklar ist: mit `javap` gegen die echte JAR prüfen (Anhang A). Nicht aus dem Gedächtnis schreiben. Alle in diesem Plan genannten Signaturen wurden bereits so verifiziert und stehen in Abschnitt 1 — die musst du nicht erneut prüfen.
4. **Kein Scope-Creep.** Nur was hier steht. Keine Umbenennungen, keine Formatierungs-Sweeps, keine „Verbesserungen" nebenbei.
5. **Kein Verhaltenswechsel.** Diese Migration ist rein technisch. Wenn eine Ersetzung das sichtbare Verhalten ändern würde (Farbe, Kursivschrift, Reihenfolge, Nachrichtentext), ist das ein **Fund** — in `DEPRECATION_NOTES.md` dokumentieren, nicht stillschweigend übernehmen.
6. **Bei Blockade:** In `DEPRECATION_NOTES.md` unter `## BLOCKIERT` festhalten (Aufgabe, Datei, Fehlermeldung, was versucht wurde), alle *unabhängigen* restlichen Aufgaben trotzdem fertigstellen, am Ende berichten.
7. **Deutsch** in Kommentaren/Doku. Code-Bezeichner bleiben englisch.
8. **Umlaute im Quelltext vermeiden** — die vorhandenen Kommentare schreiben bewusst `ue/ae/oe`. Das beibehalten. (In `.md`-Dateien sind Umlaute in Ordnung.)
9. **Nach jeder Aufgabe committen**, Commit-Message = Aufgaben-ID (z. B. `D1.3: ItemBuilder auf Adventure umgestellt`).

**Abbruchkriterien (STOPP, nicht weiterarbeiten):**
- `mvn -o test` schlägt nach einer Aufgabe fehl und lässt sich nicht in max. 3 Versuchen beheben.
- Ein bereits grüner Test wird rot und die Ursache ist nicht innerhalb der Aufgabe erklärbar.
- Eine Aufgabe würde mehr als eine Datei zugleich brechen.

---

## 1. Verifizierte Fakten (bereits geprüft — nicht erneut recherchieren)

Alle Signaturen wurden am 2026-08-13 mit `javap` gegen
`purpur-api-26.2.build.2618-stable.jar` geprüft.

| Alt (deprecated) | Neu (verifiziert vorhanden) |
|---|---|
| `ItemMeta#setDisplayName(String)` | `ItemMeta#displayName(Component)` |
| `ItemMeta#getDisplayName()` | `ItemMeta#displayName()` → `Component` |
| `ItemMeta#setLore(List<String>)` | `ItemMeta#lore(List<? extends Component>)` |
| `ItemMeta#getLore()` | `ItemMeta#lore()` → `List<Component>` |
| `Bukkit.createInventory(holder, int, String)` | `Bukkit.createInventory(holder, int, Component)` |
| `Bukkit.broadcastMessage(String)` | `Bukkit.broadcast(Component)` → gibt `int` zurück |
| `JavaPlugin#getDescription()` | `JavaPlugin#getPluginMeta()` → `io.papermc.paper.plugin.configuration.PluginMeta` |
| `PlayerDeathEvent#setDeathMessage(String)` | `PlayerDeathEvent#deathMessage(Component)` |
| `OfflinePlayer#getBedSpawnLocation()` | `OfflinePlayer#getRespawnLocation()` |
| `Damageable#getMaxHealth()` | `Attribute.MAX_HEALTH` über `getAttribute(...)` |
| `PotionEffectType.values()` / `getByName` / `getByKey` | `Registry.POTION_EFFECT_TYPE` |
| `Enchantment#isTreasure()` | `EnchantmentTagKeys.TREASURE` (Paper-Tag-Registry) |
| `new URL(String)` | `URI.create(String).toURL()` |
| `ItemMeta#setCustomModelData(Integer)` | `setCustomModelDataComponent(CustomModelDataComponent)` |

**Weitere geprüfte Fakten:**

| Fakt | Wert |
|---|---|
| Aktueller Build | `mvn -o clean test` grün, **176 Tests**, 0 Failures |
| Adventure-Version | 5.2.0 (transitiv über `purpur-api`) |
| Zentrale Text-Brücke | `de.zfzfg.core.util.Text` — hat bereits `of`, **`ofItem`**, `toLegacy`, `plain`, `button`, `link` |
| `net.md_5.bungee` im Quelltext | 0 Vorkommen (in der Vorgänger-Migration entfernt) |
| `bungeecord-chat`-Dependency | entfernt |
| Offene Meldungen nach dem Vor-Aufräumen | ~350, davon ~175 Null-Annotation-Rauschen (siehe Abschnitt 4 — **nicht anfassen**) |

### 1.1 Der wichtigste Fallstrick dieser Migration

**Adventure-Item-Namen und -Lore sind standardmäßig kursiv, Legacy-Strings waren es nicht.**

Wer `meta.setDisplayName(MessageUtil.color("&bSchwert"))` durch
`meta.displayName(Text.of("&bSchwert"))` ersetzt, bekommt ein **kursives** Item — im ganzen
Plugin, in jedem GUI. Das kompiliert, es fällt in keinem Test auf, und es sieht im Spiel falsch aus.

**Deshalb gilt ausnahmslos:**

```java
// FALSCH fuer ItemMeta
meta.displayName(Text.of(name));

// RICHTIG fuer ItemMeta - IMMER
meta.displayName(Text.ofItem(name));
meta.lore(lines.stream().map(Text::ofItem).toList());
```

`Text.ofItem` existiert bereits und setzt `TextDecoration.ITALIC` auf `false`.
**Für GUI-Titel gilt das nicht** — dort ist `Text.of(...)` richtig, Titel sind nicht kursiv-behaftet.

### 1.2 Wo die Hebel liegen (Reihenfolge ist deshalb nicht beliebig)

```
ItemBuilder.setName/setLore        ← pvpwager: viele Item-Erzeugungen
AbstractWagerGui (Hilfsmethoden)   ← Basisklasse fast aller Wager-GUIs
ConfiguredItemFactory              ← core: Equipment aus equipment.yml
```

Diese drei zuerst (D1.1–D1.3). Danach ist der größte Teil der 36 `setDisplayName`/`setLore`-
Meldungen bereits weg und die restlichen Dateien sind Einzelfälle.

---

## Phase D0 — Vorbereitung

### D0.1 — Notizdatei anlegen

Neue Datei `DEPRECATION_NOTES.md` im Projektwurzelverzeichnis:

```markdown
# Notizen zum Deprecation-Aufräumen
## Baseline
## Entscheidungen
## Verhaltensänderungen (sichtbar für Spieler)
## BLOCKIERT
## Offene Punkte für den Menschen
```

### D0.2 — Baseline festhalten

```bash
mvn -o clean test 2>&1 | grep -E "Tests run:|BUILD" | tail -5
```

Ergebnis in `DEPRECATION_NOTES.md` unter `## Baseline` eintragen.

**Erwartung:** `Tests run: 176, Failures: 0, Errors: 0` und `BUILD SUCCESS`.
**STOPP-Bedingung:** Baseline rot → nicht weitermachen, Ursache melden.

### D0.3 — Aktuelle Deprecation-Liste erzeugen

```bash
mvn -o clean test-compile -DcompilerArgument=-Xlint:deprecation 2>&1 \
  | grep -i deprecat | sort -u > deprecation-baseline.txt
wc -l deprecation-baseline.txt
```

Diese Datei ist die Messlatte: am Ende jeder Phase erneut erzeugen und die Zeilenzahl vergleichen.
`deprecation-baseline.txt` **nicht** committen (in `.gitignore` aufnehmen).

---

## Phase D1 — ItemMeta: displayName und lore (größter Block, 36 Meldungen)

> **Ziel:** Kein `setDisplayName`/`setLore`/`getDisplayName`/`getLore` mehr im Quelltext.
> **Strategie:** Zuerst die drei Chokepoints, dann die Einzelfälle.

### D1.1 — `ItemBuilder` umstellen

**Datei:** `src/main/java/de/zfzfg/pvpwager/utils/ItemBuilder.java`

```java
// VORHER
public ItemBuilder setName(String name) {
    ItemMeta meta = item.getItemMeta();
    if (meta != null) {
        meta.setDisplayName(MessageUtil.color(name));
        item.setItemMeta(meta);
    }
    return this;
}

// NACHHER
public ItemBuilder setName(String name) {
    ItemMeta meta = item.getItemMeta();
    if (meta != null) {
        meta.displayName(Text.ofItem(name));
        item.setItemMeta(meta);
    }
    return this;
}
```

```java
// VORHER
public ItemBuilder setLore(List<String> lore) {
    ItemMeta meta = item.getItemMeta();
    if (meta != null) {
        List<String> coloredLore = lore.stream().map(MessageUtil::color).toList();
        meta.setLore(coloredLore);
        item.setItemMeta(meta);
    }
    return this;
}

// NACHHER
public ItemBuilder setLore(List<String> lore) {
    ItemMeta meta = item.getItemMeta();
    if (meta != null) {
        meta.lore(lore.stream().map(Text::ofItem).toList());
        item.setItemMeta(meta);
    }
    return this;
}
```

Import ergänzen: `import de.zfzfg.core.util.Text;`.
`MessageUtil.color(...)` fällt hier weg — `Text.ofItem` parst die `&`-Codes selbst. Falls
`MessageUtil` danach in dieser Datei ungenutzt ist, den Import entfernen.

**Verifikation:**
```bash
mvn -o test-compile && grep -c "setDisplayName\|setLore" src/main/java/de/zfzfg/pvpwager/utils/ItemBuilder.java
```
Compile grün, `grep` muss **0** ergeben.

### D1.2 — `AbstractWagerGui` umstellen

**Datei:** `src/main/java/de/zfzfg/pvpwager/gui/AbstractWagerGui.java`
(betroffen: 3× `setDisplayName`, 2× `setLore`, 1× `getDisplayName`)

Gleiches Rezept wie D1.1. **Zusätzlich** die Lesestelle:

```java
// VORHER
String name = meta.getDisplayName();

// NACHHER - Component zu Klartext, wenn der Wert nur verglichen/geloggt wird
String name = meta.hasDisplayName() ? Text.toLegacy(meta.displayName()) : "";
```

> **Wichtig:** Prüfe, **wofür** der gelesene Name verwendet wird.
> - Wird er nur angezeigt/geloggt → `Text.toLegacy(...)` wie oben.
> - Wird er mit einem anderen String **verglichen** (`equals`, `contains`, `startsWith`) → das ist
>   eine Falle. `Text.toLegacy` liefert `§`-Codes; der Vergleichswert könnte `&`-Codes oder gar
>   keine Codes haben. In dem Fall `Text.plain(Text.toLegacy(...))` benutzen und den
>   Vergleichswert ebenfalls durch `Text.plain(...)` schicken. **In `DEPRECATION_NOTES.md`
>   unter `## Entscheidungen` vermerken.**

**Verifikation:** wie D1.1, für diese Datei.

### D1.3 — `ConfiguredItemFactory` umstellen

**Datei:** `src/main/java/de/zfzfg/core/items/ConfiguredItemFactory.java`
(betroffen: 2× `setDisplayName`, 2× `setLore` — jeweils in zwei getrennten Methoden)

Gleiches Rezept. **Achtung:** Diese Klasse baut das Equipment aus `equipment.yml`. Die
Kursiv-Regel aus 1.1 ist hier besonders sichtbar, weil Spieler diese Items im Inventar tragen.
**Immer `Text.ofItem`.**

**Verifikation:** wie D1.1, plus:
```bash
mvn -o test -Dtest=ConfiguredItemAmountTest,EnchantmentResolveTest
```
Beide müssen grün bleiben.

### D1.4 bis D1.11 — Die restlichen Dateien

Eine Datei pro Aufgabe, gleiches Rezept, Commit pro Datei.

| Aufgabe | Datei |
|---|---|
| D1.4 | `pvpwager/gui/ConfirmationGui.java` |
| D1.5 | `pvpwager/gui/CounterOfferItemGui.java` *(hat auch 2× `getLore` und 2× `getDisplayName`)* |
| D1.6 | `pvpwager/gui/ItemSelectionGui.java` *(2× `getLore` + 2× `setLore`)* |
| D1.7 | `pvpwager/gui/NegotiationGui.java` |
| D1.8 | `pvpwager/gui/ResponseGui.java` |
| D1.9 | `pvpwager/gui/ResponseItemSelectionGui.java` *(2× `getLore` + 2× `setLore`)* |
| D1.10 | `pvpwager/gui/WagerMainGui.java` |
| D1.11 | `pvpwager/gui/livetrade/LiveTradeGui.java` *(4× `setDisplayName`, 3× `setLore`)* |
| D1.12 | `core/web/WebApiHandler.java` *(`getDisplayName` + `getLore` — nur lesend, für das Web-Panel)* |

**Muster für die Lese-Fälle in D1.5/D1.6/D1.9/D1.12** (Lore ergänzen statt ersetzen):

```java
// VORHER
List<String> lore = meta.getLore();
if (lore == null) lore = new ArrayList<>();
lore.add(MessageUtil.color("&7Zusatz"));
meta.setLore(lore);

// NACHHER
List<Component> lore = meta.hasLore() ? new ArrayList<>(meta.lore()) : new ArrayList<>();
lore.add(Text.ofItem("&7Zusatz"));
meta.lore(lore);
```

> **Falle:** `meta.lore()` kann eine unveränderliche Liste zurückgeben. Immer in ein
> `new ArrayList<>(...)` kopieren, bevor `add` aufgerufen wird — sonst
> `UnsupportedOperationException` zur Laufzeit, die kein Test findet.

**Besonderheit D1.12 (`WebApiHandler`):** Hier werden Name und Lore für die **JSON-Antwort** des
Web-Panels gelesen. Das Panel erwartet Strings, keine Components. Richtig ist hier
`Text.plain(Text.toLegacy(...))` — also Klartext ohne Farbcodes, denn HTML kann mit `§` nichts
anfangen. **Prüfe im Frontend** (`src/main/resources/web/`), ob die Farbcodes dort ausgewertet
werden; falls ja, stattdessen `Text.toLegacy(...)` und in `DEPRECATION_NOTES.md` vermerken.

### D1.13 — Abschluss-Scan Phase D1

```bash
grep -rn "setDisplayName\|getDisplayName()\|setLore(\|getLore()" src/main/java
```
**Muss leer sein.** Dann:
```bash
mvn -o clean test    # 176 Tests gruen
```

---

## Phase D2 — GUI-Titel: `Bukkit.createInventory(…, String)` (12 Meldungen)

> **Ziel:** Alle 12 Aufrufe auf die Component-Überladung.

### D2.1 — Alle 12 Stellen umstellen

**Betroffene Dateien** (je eine Aufgabe, oder gebündelt als eine Aufgabe mit 12 Edits — hier
ausnahmsweise erlaubt, weil das Muster identisch und trivial ist):

```
pvpwager/gui/ArenaSelectionGui.java:37
pvpwager/gui/ConfirmationGui.java:41
pvpwager/gui/CounterOfferItemGui.java:73
pvpwager/gui/EquipmentSelectionGui.java:47
pvpwager/gui/ItemSelectionGui.java:50
pvpwager/gui/MoneySelectionGui.java:54
pvpwager/gui/NegotiationGui.java:60
pvpwager/gui/ResponseGui.java:47
pvpwager/gui/ResponseItemSelectionGui.java:51
pvpwager/gui/ResponseMoneySelectionGui.java:49
pvpwager/gui/WagerMainGui.java:38
pvpwager/gui/livetrade/LiveTradeGui.java:131
```

```java
// VORHER
inventory = Bukkit.createInventory(null, SIZE, MessageUtil.color(title));

// NACHHER
inventory = Bukkit.createInventory(null, SIZE, Text.of(title));
```

> Hier **`Text.of`**, nicht `Text.ofItem` — Titel sind keine Item-Namen (siehe 1.1).

### D2.2 — Titelvergleiche prüfen (kritisch)

Ein GUI-Titel wird an manchen Stellen benutzt, um ein Inventar wiederzuerkennen. Wenn der Titel
jetzt eine Component ist, brechen String-Vergleiche **stumm** — das GUI reagiert nicht mehr auf
Klicks.

```bash
grep -rn "getTitle()\|getView().getTitle\|title.equals\|title.contains" src/main/java
```

**Für jeden Treffer:**
- Vergleich über `event.getView().getTopInventory().equals(guiInv)` (Identität) ist **richtig** und bleibt.
- Vergleich über den Titel-String ist **fragil** — auf Identitätsvergleich umbauen und in
  `DEPRECATION_NOTES.md` vermerken.

> `GuiManager#isWagerGui` vergleicht bereits über Identität, nicht über den Titel — das ist das
> Vorbild für alle anderen Stellen.

**Verifikation:**
```bash
mvn -o test-compile && grep -rn "Bukkit.createInventory(null, [0-9A-Z_]*, MessageUtil" src/main/java
```
Compile grün, `grep` leer.

**Live-Test (Pflicht, sobald ein Server läuft):** Jedes der 12 GUIs einmal öffnen und einen Klick
darin ausführen. Titel-Bugs zeigen sich **nur** so.

---

## Phase D3 — `getDescription()` → `getPluginMeta()` (7 Meldungen)

> **Achtung:** `getDescription()` gibt es auch auf **eigenen** Klassen des Projekts
> (`EventConfig#getDescription`, `EquipmentSet#getDescription`). Diese **nicht** anfassen —
> sie haben mit Bukkit nichts zu tun.

### D3.1 — Die 7 echten Stellen umstellen

| Datei | Zeile (Orientierung) | Aufruf |
|---|---|---|
| `core/inventory/mvi/MultiverseInventoriesBridge.java` | 68 | `mvi.getDescription().getVersion()` |
| `core/web/WebApiHandler.java` | 1005 | `plugin.getDescription().getName()` |
| `core/web/WebApiHandler.java` | 1006 | `plugin.getDescription().getVersion()` |
| `eventplugin/commands/EventPvpCommand.java` | 195 | `plugin.getDescription().getVersion()` |
| `eventplugin/EventPlugin.java` | 331 | `getDescription().getVersion()` |
| `eventplugin/util/UpdateChecker.java` | 72 | `plugin.getDescription().getName()` |
| `pvpwager/commands/PvPAdminCommand.java` | 85 | `plugin.getDescription().getVersion()` |

```java
// VORHER
String version = plugin.getDescription().getVersion();

// NACHHER
String version = plugin.getPluginMeta().getVersion();
```

**Sonderfall `MultiverseInventoriesBridge.java:68`:** Dort ist der Typ `org.bukkit.plugin.Plugin`,
nicht `JavaPlugin`. `getPluginMeta()` ist auf `JavaPlugin` verifiziert — ob es auch auf dem
`Plugin`-Interface liegt, **musst du prüfen**:

```bash
"$JAVAP" -cp "$API_JAR" org.bukkit.plugin.Plugin | grep -i PluginMeta
```

- Findet sich die Methode → normal ersetzen.
- Findet sie sich **nicht** → die Stelle unverändert lassen und stattdessen gezielt
  `@SuppressWarnings("deprecation")` an die **umgebende Methode** setzen, mit einem
  Kommentar, der die Begründung nennt. In `DEPRECATION_NOTES.md` vermerken.
  Dieselbe Zeile hat zusätzlich eine „mvi may be null"-Warnung — die ist ein Eclipse-Fehlalarm
  (siehe Abschnitt 4), **nicht** anfassen.

**Verifikation:**
```bash
mvn -o test-compile
grep -rn "getDescription()" src/main/java | grep -v "EventConfig\|EquipmentSet\|eventConfig\|equipment\.\|config\."
```
Bis auf ggf. den dokumentierten Sonderfall leer.

---

## Phase D4 — `Bukkit.broadcastMessage` (4 Meldungen)

**Datei:** `src/main/java/de/zfzfg/eventplugin/session/EventSession.java`, Zeilen ~220, 251, 258, 286.

```java
// VORHER
Bukkit.broadcastMessage(ColorUtil.color(plugin.getConfigManager().getPrefix() + " " + msg));

// NACHHER
Bukkit.broadcast(Text.of(plugin.getConfigManager().getPrefix() + " " + msg));
```

> `ColorUtil.color(...)` fällt weg — `Text.of` parst die `&`-Codes selbst.
> Der Rückgabewert von `broadcast` (Anzahl Empfänger, `int`) wird nicht gebraucht und
> darf ignoriert werden.

**Verifikation:**
```bash
mvn -o test-compile && grep -c "broadcastMessage" src/main/java/de/zfzfg/eventplugin/session/EventSession.java
```
`grep` muss **0** ergeben.

---

## Phase D5 — Einzelfälle

> Jede Aufgabe betrifft genau eine Stelle. Reihenfolge egal, aber einzeln committen.

### D5.1 — `PlayerDeathEvent#setDeathMessage`

**Datei:** `pvpwager/listeners/PvPListener.java:161`

```java
// VORHER
event.setDeathMessage(null);

// NACHHER
event.deathMessage(null);
```

> Semantik identisch: `null` unterdrückt die Todesnachricht. **Nicht** durch
> `setShowDeathMessages(false)` ersetzen — das ist etwas anderes (globale Server-Einstellung
> für dieses Event) und wäre eine Verhaltensänderung.

### D5.2 — `Damageable#getMaxHealth`

**Datei:** `eventplugin/listeners/VoidProtectionListener.java:201`

```java
// VORHER
player.setHealth(Math.min(player.getHealth() + 10, player.getMaxHealth()));

// NACHHER
var maxHealthAttr = player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
double maxHealth = maxHealthAttr != null ? maxHealthAttr.getValue() : 20.0;
player.setHealth(Math.min(player.getHealth() + 10, maxHealth));
```

> `getAttribute` kann `null` liefern (nicht bei Spielern, aber die Signatur erlaubt es) —
> der Fallback auf 20.0 ist Absicht.
> **Verifiziert:** `Attribute.MAX_HEALTH` existiert als statisches Feld (nicht `GENERIC_MAX_HEALTH`
> — das ist der alte Name aus Versionen vor 1.21.3).

### D5.3 — `OfflinePlayer#getBedSpawnLocation`

**Datei:** `core/location/SafeLocationResolver.java:69`

```java
// VORHER
Location bed = player.getBedSpawnLocation();

// NACHHER
Location bed = player.getRespawnLocation();
```

> Reine Umbenennung, gleiche Semantik (`getRespawnLocation()` ist die `default`-Methode, die
> genau das tut, was `getBedSpawnLocation()` tat).

### D5.4 — `new URL(String)`

**Datei:** `core/web/ResourcePackTextureService.java:179`

```java
// VORHER
HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();

// NACHHER
HttpURLConnection connection =
        (HttpURLConnection) java.net.URI.create(url).toURL().openConnection();
```

> **Falle:** `URI.create` wirft `IllegalArgumentException` (unchecked) statt
> `MalformedURLException` (checked). Prüfe den umgebenden `try/catch`: fängt er nur
> `MalformedURLException` oder `IOException`, rutscht eine kaputte URL jetzt durch.
> In dem Fall den `catch`-Block um `IllegalArgumentException` erweitern.
> Diese Methode lädt ein Resourcepack von einer **vom Nutzer konfigurierten** URL — hier
> darf nichts unbehandelt hochblubbern.

### D5.5 — `PotionEffectType.values()` / `getByName` / `getByKey`

**Dateien:** `core/web/MaterialCatalog.java:366`, `core/items/ConfiguredItemFactory.java:418,427`

```java
// VORHER
for (PotionEffectType type : PotionEffectType.values()) { … }

// NACHHER
for (PotionEffectType type : org.bukkit.Registry.POTION_EFFECT_TYPE) { … }
```

```java
// VORHER
PotionEffectType byKey = PotionEffectType.getByKey(nsk);

// NACHHER
PotionEffectType byKey = org.bukkit.Registry.POTION_EFFECT_TYPE.get(nsk);
```

**Für `getByName(key)` (Zeile 427):** Das ist der **Fallback für alte Konfigurationen**. Vorbild
ist die bereits existierende Methode `resolveEnchantment` in derselben Datei (aus dem
Vorgänger-Plan, Aufgabe P3.3) — bau die Auflösung genauso:
Registry-Lookup zuerst, alter Name nur als letzter Ausweg mit gezieltem
`@SuppressWarnings("deprecation")` **an einer eigenen kleinen privaten Methode**.

> **Warum nicht komplett entfernen:** Bestehende `equipment.yml`-Dateien der Nutzer enthalten
> alte Namen. Ein Entfernen wäre stiller Funktionsverlust — genau das, was Regel 5 verbietet.

**Verifikation:** `mvn -o test -Dtest=EnchantmentResolveTest,ConfiguredItemAmountTest` grün.
Zusätzlich **einen Test ergänzen**, der einen alten und einen neuen Trank-Namen auflöst
(analog zu `EnchantmentResolveTest`).

### D5.6 — `Enchantment#isTreasure()`

**Datei:** `core/web/MaterialCatalog.java:293`

```java
// VORHER
entry.put("treasure", enchantment.isTreasure());

// NACHHER
entry.put("treasure", isTreasure(enchantment));
```

Mit neuer privater Hilfsmethode:

```java
    /**
     * Ob eine Verzauberung eine "Schatz"-Verzauberung ist (nur aus Truhen/Angeln, nicht
     * am Zaubertisch). isTreasure() ist deprecated; die Information steht seit 1.21 im
     * Registry-Tag minecraft:treasure.
     */
    private static boolean isTreasure(Enchantment enchantment) {
        try {
            return org.bukkit.Registry.ENCHANTMENT
                    .getTag(io.papermc.paper.registry.keys.tags.EnchantmentTagKeys.TREASURE)
                    .contains(enchantment);
        } catch (Throwable ignored) {
            // Tag-Registry nicht verfuegbar -> konservativ "kein Schatz".
            return false;
        }
    }
```

> **Vor dem Schreiben verifizieren** (die genaue Methode zum Auflösen eines Tags ist die einzige
> Unsicherheit dieses Plans):
> ```bash
> "$JAVAP" -cp "$API_JAR" org.bukkit.Registry | grep -i "getTag\|hasTag"
> "$JAVAP" -cp "$API_JAR" io.papermc.paper.registry.tag.Tag
> ```
> **Verifiziert vorhanden ist:** `io.papermc.paper.registry.keys.tags.EnchantmentTagKeys.TREASURE`
> vom Typ `TagKey<Enchantment>`.
> Falls sich der Zugriffsweg nicht in 3 Versuchen findet: Aufgabe überspringen,
> `@SuppressWarnings("deprecation")` an die Methode, in `DEPRECATION_NOTES.md` unter
> `## BLOCKIERT` notieren. Diese eine Warnung ist es nicht wert, den Plan zu blockieren.

### D5.7 — `ItemMeta#setCustomModelData(Integer)`

**Datei:** `core/items/ConfiguredItemFactory.java:227`

**Empfehlung: diese Aufgabe überspringen.**
`setCustomModelDataComponent(CustomModelDataComponent)` hat eine andere Datenstruktur (Floats,
Flags, Farben, Strings statt einer einzelnen Zahl) und ein anderes Serialisierungsverhalten in
der `equipment.yml`. Eine Umstellung ist ein Konfigurations-Bruch, keine reine Deprecation.

**Stattdessen:** `@SuppressWarnings("deprecation")` an die umgebende Methode, mit Kommentar:

```java
    // setCustomModelData(Integer) ist deprecated, die Nachfolge-API
    // (CustomModelDataComponent) hat aber ein anderes Datenmodell. Ein Wechsel wuerde das
    // Format von 'custom-model-data' in der equipment.yml aendern und bestehende
    // Konfigurationen brechen. Bewusste Entscheidung: bleibt bis zu einem geplanten
    // Konfig-Schema-Wechsel.
```

In `DEPRECATION_NOTES.md` unter `## Offene Punkte für den Menschen` eintragen.

### D5.8 — `Enchantment#getName()` (deprecated **for removal**)

**Datei:** `core/web/MaterialCatalog.java`, Methode `keyOf`

**Status: bereits erledigt** — die Methode trägt seit dem 2026-08-13 ein
`@SuppressWarnings("removal")` (nicht `"deprecation"` — für *deprecated-for-removal* ist eine
eigene Warn-Kategorie zuständig).

**Aufgabe hier: nur prüfen und nichts ändern.** Der Aufruf steht in einem `catch (Throwable)`-
Fallback hinter `enchantment.getKey().getKey()`; er wird im Normalbetrieb nie erreicht.

> **Wichtig für die Zukunft:** Das ist die einzige API-Nutzung im Plugin, die *für Entfernung*
> markiert ist. Wenn Paper/Purpur sie streicht, bricht der **Build** (nicht die Laufzeit).
> Dann ersatzlos entfernen und im `catch` stattdessen `enchantment.toString()` zurückgeben.

---

## Phase D6 — `org.bukkit.ChatColor` (16 Meldungen, 18 Dateien)

> **Entscheidung des Vorgänger-Plans (P3.1): bleibt bewusst stehen.**
> Diese Phase ist **optional** und nur auszuführen, wenn der Mensch sie ausdrücklich anfordert.

**Begründung, warum sie normalerweise ausfällt:**
- `org.bukkit.ChatColor` existiert in 26.2 und ist **nicht** for-removal markiert.
- 18 Dateien anzufassen ist ein großer Diff mit hohem Regressionsrisiko und null Laufzeitgewinn.
- Die meisten Nutzungen sind `ChatColor.translateAlternateColorCodes('&', msg)` in kleinen
  `getMsg`-Hilfsmethoden — die exakt das tun, was `Text.of` tut, nur als String.

**Falls doch beauftragt — Rezept pro Datei:**

```java
// VORHER
private String getMsg(String key) {
    String msg = plugin.getCoreConfigManager().getMessages().getString("messages.system." + key, "");
    return ChatColor.translateAlternateColorCodes('&', msg);
}

// NACHHER
private String getMsg(String key) {
    String msg = plugin.getCoreConfigManager().getMessages().getString("messages.system." + key, "");
    return Text.toLegacy(Text.of(msg));
}
```

Für `ChatColor.stripColor(x)` → `Text.plain(x)`.
Für Konstanten wie `ChatColor.RED + text` → `Text.of("&c" + text)` **nur**, wenn das Ergebnis
direkt verschickt wird; wird es weiterverarbeitet, `Text.toLegacy(Text.of("&c" + text))`.

**Eine Datei pro Aufgabe, eine Verifikation pro Datei, ein Commit pro Datei.**

---

## Phase D7 — Abnahme

### D7.1 — Deprecation-Liste vergleichen

```bash
mvn -o clean test-compile -DcompilerArgument=-Xlint:deprecation 2>&1 \
  | grep -i deprecat | sort -u > deprecation-final.txt
diff deprecation-baseline.txt deprecation-final.txt
wc -l deprecation-baseline.txt deprecation-final.txt
```

**Erwartung nach D1–D5** (ohne die optionale Phase D6): von ~100 Deprecation-Meldungen bleiben
noch die aus D5.7 (`setCustomModelData`), D5.8 (`getName`, unterdrückt) und ggf. D3.1-Sonderfall
übrig — plus die 16 `ChatColor`-Meldungen, die bewusst stehen bleiben.

### D7.2 — Vollständiger Test- und Build-Lauf

```bash
mvn -o clean test        # MUSS: Tests run: 176+, Failures: 0, Errors: 0
mvn -o clean package     # MUSS: BUILD SUCCESS
unzip -l target/event-pvp-plugin-*.jar | grep -c "net/kyori"   # MUSS 0 sein
unzip -l target/event-pvp-plugin-*.jar | grep -c "org/bukkit"  # MUSS 0 sein
```

> Die beiden `unzip`-Prüfungen sind aus dem Vorgänger-Plan (P1.5) übernommen: gerät Adventure
> ins JAR, gibt es zwei Kopien von `Component` im Speicher und **jeder** `sendMessage(Component)`
> schlägt zur Laufzeit fehl. Diese Phase fasst sehr viel Adventure-Code an — deshalb hier erneut prüfen.

### D7.3 — Live-Verifikation auf dem Server (Pflicht)

Die gefährlichen Fehler dieser Migration sind **unsichtbar für den Compiler und die Tests**.
Diese Liste vollständig abarbeiten und das Ergebnis in `DEPRECATION_NOTES.md` protokollieren:

| # | Test | Worauf achten |
|---|---|---|
| 1 | Ein Wager-GUI öffnen (`/pvpwager`) | Titel korrekt, Item-Namen **nicht kursiv** |
| 2 | In jedem GUI einen Button klicken | GUI reagiert (sonst: Titelvergleich gebrochen, D2.2) |
| 3 | Abbrechen-Button im Haupt-GUI | Items kommen zurück |
| 4 | Ein Equipment-Set anlegen lassen (Event starten) | Namen/Lore der Items korrekt und nicht kursiv |
| 5 | Ein Event starten mit `spawn-type: TEAM_SPAWNS` | Alle Spieler werden teleportiert |
| 6 | Event-Broadcast auslösen | Prefix + Farben korrekt (D4) |
| 7 | Ein PvP-Match zu Ende spielen | Keine Todesnachricht im Chat (D5.1) |
| 8 | `/pvpadmin` (Versionsanzeige) | Version wird angezeigt (D3) |
| 9 | Web-Panel öffnen, Item-Liste ansehen | Namen/Lore lesbar, keine `§`-Zeichen im HTML (D1.12) |
| 10 | Resourcepack-Texturen importieren | Kein Fehler (D5.4) |
| 11 | Server-Log nach dem Start | Keine `NoSuchMethodError`, keine `UnsupportedOperationException` |

### D7.4 — Bericht

In `DEPRECATION_NOTES.md` abschließend festhalten:
- Zahl der Meldungen vorher/nachher
- alle Einträge unter `## Verhaltensänderungen` und `## Offene Punkte für den Menschen`
- welche Aufgaben übersprungen wurden und warum

---

## 2. Was in diesem Plan **nicht** vorkommt (und warum)

| Thema | Warum nicht |
|---|---|
| Adventure-Migration von `net.md_5.bungee` | Erledigt in `PURPUR_26.2_MIGRATION_PLAN.md`, Phase 2 |
| `sendTitle` → `showTitle` | Erledigt, Vorgänger-Plan P3.2 |
| `Enchantment.getByName` | Erledigt, Vorgänger-Plan P3.3 |
| Die ~700 `sendMessage`-Aufrufstellen | Gehen über `TextUtil`/`MessageUtil` und sind bereits Adventure-basiert. **Nicht anfassen.** |
| Die 7 deprecated `PvP*Command`-Klassen | Eigene Legacy-Aliase des Projekts, in `EventPlugin` bewusst weiter registriert. Die 15 zugehörigen Meldungen sind gewollt. |

---

## 3. Bereits erledigt (2026-08-13, nicht erneut machen)

Diese Punkte wurden vor Erstellung dieses Plans bereits umgesetzt und sind grün:

- **Bug:** `EventSession#teleportPlayersToSpawns` — fehlender `case TEAM_SPAWNS` + `default`-Zweig.
- **Bug:** `WagerMainGui` — Abbrechen-Button war gezeichnet, aber nicht im Klick-Switch verdrahtet.
- Toter Code entfernt: `PlayerModeListener#onCommandPreprocess`, `WebServer#handleApiRequest`,
  `WebServer#handleApiPostRequest`, `PvPListener#isArenaWorldUnloaded`,
  `EventListener#isEventWorldUnloaded`, `VoidProtectionListener#isLocationSafe`,
  `LiveTradeGui#handlePlaceItem`.
- Ungenutzte Felder entfernt: `WebApiHandler.gson`, `LiveTradeListener.plugin`,
  `ConfigManager.equipmentFilePath`, `WagerSession.originalMoney`,
  `ConfirmationGui.ITEMS_DISPLAY_START`, `LiveTradeGui.CENTER_COLUMN/TOP_FILLER/BOTTOM_FILLER`.
- 16 ungenutzte Imports und 4 ungenutzte lokale Variablen entfernt.
- 2 überflüssige `@SuppressWarnings("unchecked")` in `WebConfigManager` entfernt.
- `MaterialCatalog#keyOf`: `@SuppressWarnings("deprecation")` → `"removal"`.

---

## 4. Was **niemals** angefasst wird: die Null-Annotation-Meldungen

**Rund 175 der Meldungen in VS Code sind Fehlalarme.** Sie sehen so aus:

```
Null type safety (type annotations): The expression of type 'Component' needs unchecked
  conversion to conform to '@NonNull Component'
Null type mismatch (type annotations): required '@NonNull Location' but this expression
  has type '@Nullable Location'
Potential null pointer access: The variable player may be null at this location
Unsafe interpretation of method return type as '@NonNull' based on substitution …
```

**Ursache:** Eclipse' strikte Null-Analyse trifft auf die Bukkit-API, die keine
Null-Annotationen trägt. Stichproben haben bestätigt, dass die gemeldeten Stellen **korrekt**
sind — z. B. kann `player.getLocation()` bei einem Online-Spieler nicht `null` sein, und
Elemente aus `Bukkit.getOnlinePlayers()` sind nie `null`. Eine Stelle
(`StrandedPlayerListener:74`) prüft sogar explizit auf `null` und wird trotzdem gemeldet.

**Regeln dazu:**

1. **Keine `null`-Prüfungen einbauen, um diese Warnungen zu beruhigen.** Das erzeugt toten Code
   und verschleiert echte Prüfungen.
2. **Keine `@SuppressWarnings("null")` verteilen.** Das würde auch echte Funde unterdrücken.
3. Wenn diese Meldungen im Editor stören, ist der richtige Ort die **Eclipse-Projekteinstellung**
   (`Preferences → Java → Compiler → Errors/Warnings → Null analysis` auf *Ignore*) — eine
   Werkzeugeinstellung, keine Code-Änderung. Das ist ein Punkt für den Menschen, nicht für die KI.

**Wenn dir während der Arbeit eine Null-Meldung begegnet, die durch deine eigene Änderung neu
entstanden ist** — das ist etwas anderes. Die gehört behoben. Der Unterschied: sie steht nicht in
`deprecation-baseline.txt` und betrifft Code, den du gerade geschrieben hast.

---

## Anhang A — Nachschlage-Kommandos

**Pfade (immer diese verwenden):**
```bash
API_JAR="C:/Users/zfzfg/.m2/repository/org/purpurmc/purpur/purpur-api/26.2.build.2618-stable/purpur-api-26.2.build.2618-stable.jar"
ADV="C:/Users/zfzfg/.m2/repository/net/kyori/adventure-api/5.2.0/adventure-api-5.2.0.jar"
JAVAP="/c/Program Files/Java/jdk-26.0.1/bin/javap.exe"
```
> `javap` liegt **nicht** im PATH dieser Git-Bash — immer den vollen Pfad benutzen.

**Signatur prüfen:**
```bash
"$JAVAP" -cp "$API_JAR" org.bukkit.inventory.meta.ItemMeta | grep -i "displayName"
```

**Prüfen, ob eine Klasse existiert:**
```bash
unzip -l "$API_JAR" | grep "io/papermc/paper/registry/keys/tags"
```

**Offline bauen (immer `-o`, alle Deps liegen lokal):**
```bash
mvn -o clean test-compile
mvn -o test
mvn -o clean package
```

**Alle Vorkommen eines Musters finden:**
```bash
grep -rn "setDisplayName" src/main/java
```

---

## Anhang B — Fehlerkatalog

| Symptom | Wahrscheinliche Ursache | Fix |
|---|---|---|
| Item-Namen im Spiel sind **kursiv** | `Text.of` statt `Text.ofItem` bei ItemMeta | Abschnitt 1.1 |
| GUI reagiert nicht mehr auf Klicks | Titelvergleich gebrochen | D2.2 |
| `UnsupportedOperationException` beim Lore-Ergänzen | `meta.lore()` liefert unveränderliche Liste | `new ArrayList<>(...)` kopieren, D1.4-Muster |
| `§`-Zeichen erscheinen im Web-Panel | `Text.toLegacy` statt `Text.plain` in `WebApiHandler` | D1.12 |
| `cannot find symbol: getPluginMeta` | Typ ist `Plugin`, nicht `JavaPlugin` | D3.1 Sonderfall |
| `IllegalArgumentException` bei kaputter Pack-URL | `URI.create` wirft anders als `new URL` | D5.4 |
| `NoSuchMethodError` zur Laufzeit, Build war grün | Adventure ins JAR geshaded | D7.2, `unzip`-Prüfung |
| `Attribute.GENERIC_MAX_HEALTH` nicht gefunden | Alter Konstantenname vor 1.21.3 | `Attribute.MAX_HEALTH`, D5.2 |
