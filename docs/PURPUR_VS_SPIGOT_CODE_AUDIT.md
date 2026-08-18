# 🔬 Vollständiges Code-Audit: Spigot-Kompatibilität für Event-PVP-Plugin

Dieses Dokument enthält eine lückenlose Erfassung **aller 53 Code-Stellen in 16 Dateien**, die auf einem reinen **Vanilla Spigot 26.2** zu Laufzeitfehlern führen, sowie die vollständige Architektur für eine **Dual-Platform Bridge (Purpur & Spigot)**.

---

## 📑 Inhaltsverzeichnis
1. [Übersicht & Fehler-Klassifizierung](#1-übersicht--fehler-klassifizierung)
2. [Vollständiges Inventar aller inkompatiblen Stellen](#2-vollständiges-inventar-aller-inkompatiblen-stellen)
3. [Architektur der Dual-Platform Bridge](#3-architektur-der-dual-platform-bridge)
4. [Detaillierte Lösungsoptionen pro Bereich](#4-detaillierte-lösungsoptionen-pro-bereich)
   - [Bereich 1: Maven `pom.xml` & Classpath-Shading](#bereich-1-maven-pomxml--classpath-shading)
   - [Bereich 2: Plattform-Erkennung (`Platform.java`)](#bereich-2-plattform-erkennung-platformjava)
   - [Bereich 3: Metadaten (`getPluginMeta()` vs. `getDescription()`)](#bereich-3-metadaten-getpluginmeta-vs-getdescription)
   - [Bereich 4: Text- & Nachrichtenversand (`TextUtil.java` & `Text.java`)](#bereich-4-text--nachrichtenversand-textutiljava--textjava)
   - [Bereich 5: GUI-Erstellung (`createInventory`)](#bereich-5-gui-erstellung-createinventory)
   - [Bereich 6: `ItemMeta` Lore & DisplayName (`ItemUtil.java`)](#bereich-6-itemmeta-lore--displayname-itemutiljava)
   - [Bereich 7: Paper Registry Tags (`MaterialCatalog.java`)](#bereich-7-paper-registry-tags-materialcatalogjava)
   - [Bereich 8: Optionaler Performance-Boost (`teleportAsync`)](#bereich-8-optionaler-performance-boost-teleportasync)
5. [Schritt-für-Schritt Umsetzungs-Checkliste](#5-schritt-für-schritt-umsetzungs-checkliste)

---

## 1. 🔍 Übersicht & Fehler-Klassifizierung

| Kategorie | Betroffene Dateien | Anzahl Stellen | JVM-Fehler auf Spigot | Schweregrad |
|---|:---:|:---:|---|:---:|
| **1. Maven Shading** | `pom.xml` | 1 | `NoClassDefFoundError: net/kyori/adventure/text/Component` | 🔴 **Fatal** (Plugin startet nicht) |
| **2. Plugin-Metadaten** | 6 Klassen | 7 | `NoSuchMethodError: Plugin.getPluginMeta()` | 🔴 **Fatal** (Start- & Befehlsabsturz) |
| **3. Adventure Chat & Titel** | 6 Klassen | 10 | `NoSuchMethodError: Player.sendMessage(Component)` | 🔴 **Kritisch** (Chat/Titel stürzt ab) |
| **4. GUI-Erstellung** | 12 Klassen | 12 | `NoSuchMethodError: Bukkit.createInventory(..., Component)` | 🔴 **Kritisch** (GUIs öffnen nicht) |
| **5. ItemMeta Component-API** | 8 Klassen | 26 | `NoSuchMethodError: ItemMeta.lore(List)` / `.displayName()` | 🔴 **Kritisch** (Items fehlerhaft) |
| **6. Paper Registry Tags** | 1 Klasse | 2 | `NoClassDefFoundError` / `NoSuchMethodError` | 🟡 **Mittel** (Web-Katalog) |
| **Gesamt** | **16 Dateien** | **53 Stellen** | – | – |

---

## 2. 📋 Vollständiges Inventar aller inkompatiblen Stellen

### 2.1 Plugin-Metadaten (`getPluginMeta()`) – 7 Stellen
Auf Spigot existiert nur `getDescription()`, kein `getPluginMeta()`.

1. [`EventPlugin.java:L343`](file:///c:/Users/zfzfg/Documents/HammerMegaProjekte/selfmadePlugins\Plugins\Event-PVP-Plugins\Event-PVP-Plugin-1.1.0-BetaPurpur/src/main/java/de/zfzfg/eventplugin/EventPlugin.java#L343): `getPluginMeta().getVersion()` beim Initialisieren des UpdateCheckers.
2. [`EventPvpCommand.java:L195`](file:///c:/Users/zfzfg/Documents/HammerMegaProjekte/selfmadePlugins\Plugins\Event-PVP-Plugins\Event-PVP-Plugin-1.1.0-BetaPurpur/src/main/java/de/zfzfg/eventplugin/commands/EventPvpCommand.java#L195): `plugin.getPluginMeta().getVersion()` für Versionsanzeige.
3. [`PvPAdminCommand.java:L85`](file:///c:/Users/zfzfg/Documents/HammerMegaProjekte/selfmadePlugins\Plugins\Event-PVP-Plugins\Event-PVP-Plugin-1.1.0-BetaPurpur/src/main/java/de/zfzfg/pvpwager/commands/PvPAdminCommand.java#L85): `plugin.getPluginMeta().getVersion()` für Admin-Status.
4. [`UpdateChecker.java:L72`](file:///c:/Users/zfzfg/Documents/HammerMegaProjekte/selfmadePlugins\Plugins\Event-PVP-Plugins\Event-PVP-Plugin-1.1.0-BetaPurpur/src/main/java/de/zfzfg/eventplugin/util/UpdateChecker.java#L72): `plugin.getPluginMeta().getName()`.
5. [`WebApiHandler.java:L1010`](file:///c:/Users/zfzfg/Documents/HammerMegaProjekte/selfmadePlugins\Plugins\Event-PVP-Plugins\Event-PVP-Plugin-1.1.0-BetaPurpur/src/main/java/de/zfzfg/core/web/WebApiHandler.java#L1010): `plugin.getPluginMeta().getName()` für Web-Dashboard.
6. [`WebApiHandler.java:L1011`](file:///c:/Users/zfzfg/Documents/HammerMegaProjekte/selfmadePlugins\Plugins\Event-PVP-Plugins\Event-PVP-Plugin-1.1.0-BetaPurpur/src/main/java/de/zfzfg/core/web/WebApiHandler.java#L1011): `plugin.getPluginMeta().getVersion()` für Web-Dashboard.
7. [`MultiverseInventoriesBridge.java:L68`](file:///c:/Users/zfzfg/Documents/HammerMegaProjekte/selfmadePlugins\Plugins\Event-PVP-Plugins\Event-PVP-Plugin-1.1.0-BetaPurpur/src/main/java/de/zfzfg/core/inventory/mvi/MultiverseInventoriesBridge.java#L68): `mvi.getPluginMeta().getVersion()` für Multiverse-Inventories-Erkennung.

---

### 2.2 Chat-, Button- & Titel-Versand (`Player.sendMessage(Component)`) – 10 Stellen
Spigot's `Player` und `CommandSender` implementieren `Audience` nicht.

1. [`TextUtil.java:L43`](file:///c:/Users/zfzfg/Documents/HammerMegaProjekte/selfmadePlugins\Plugins\Event-PVP-Plugins\Event-PVP-Plugin-1.1.0-BetaPurpur/src/main/java/de/zfzfg/core/util/TextUtil.java#L43): `sender.sendMessage(Text.of(message))`
2. [`TextUtil.java:L48`](file:///c:/Users/zfzfg/Documents/HammerMegaProjekte/selfmadePlugins\Plugins\Event-PVP-Plugins\Event-PVP-Plugin-1.1.0-BetaPurpur/src/main/java/de/zfzfg/core/util/TextUtil.java#L48): `player.sendMessage(Text.of(message))`
3. [`TextUtil.java:L54`](file:///c:/Users/zfzfg/Documents/HammerMegaProjekte/selfmadePlugins\Plugins\Event-PVP-Plugins\Event-PVP-Plugin-1.1.0-BetaPurpur/src/main/java/de/zfzfg/core/util/TextUtil.java#L54): `sender.sendMessage(message)` (Component)
4. [`EventSession.java:L1885`](file:///c:/Users/zfzfg/Documents/HammerMegaProjekte/selfmadePlugins\Plugins\Event-PVP-Plugins\Event-PVP-Plugin-1.1.0-BetaPurpur/src/main/java/de/zfzfg/eventplugin/session/EventSession.java#L1885): `player.showTitle(Title.title(...))`
5. [`WebTokenSubCommand.java:L114`](file:///c:/Users/zfzfg/Documents/HammerMegaProjekte/selfmadePlugins\Plugins\Event-PVP-Plugins\Event-PVP-Plugin-1.1.0-BetaPurpur/src/main/java/de/zfzfg/core/commands/WebTokenSubCommand.java#L114): `player.sendMessage(tokenComponent)`
6. [`WebTokenSubCommand.java:L132`](file:///c:/Users/zfzfg/Documents/HammerMegaProjekte/selfmadePlugins\Plugins\Event-PVP-Plugins\Event-PVP-Plugin-1.1.0-BetaPurpur/src/main/java/de/zfzfg/core/commands/WebTokenSubCommand.java#L132): `player.sendMessage(urlComponent)`
7. [`EventPvpCommand.java:L287`](file:///c:/Users/zfzfg/Documents/HammerMegaProjekte/selfmadePlugins\Plugins\Event-PVP-Plugins\Event-PVP-Plugin-1.1.0-BetaPurpur/src/main/java/de/zfzfg/eventplugin/commands/EventPvpCommand.java#L287): `player.sendMessage(tokenComponent)`
8. [`EventPvpCommand.java:L302`](file:///c:/Users/zfzfg/Documents/HammerMegaProjekte/selfmadePlugins\Plugins\Event-PVP-Plugins\Event-PVP-Plugin-1.1.0-BetaPurpur/src/main/java/de/zfzfg/eventplugin/commands/EventPvpCommand.java#L302): `player.sendMessage(urlComponent)`
9. [`PvPWagerGuiCommand.java:L223`](file:///c:/Users/zfzfg/Documents/HammerMegaProjekte/selfmadePlugins\Plugins\Event-PVP-Plugins\Event-PVP-Plugin-1.1.0-BetaPurpur/src/main/java/de/zfzfg/pvpwager/commands/PvPWagerGuiCommand.java#L223): `target.sendMessage(message.append(...))`
10. [`CommandRequestManager.java:L146, L161`](file:///c:/Users/zfzfg/Documents/HammerMegaProjekte/selfmadePlugins\Plugins\Event-PVP-Plugins\Event-PVP-Plugin-1.1.0-BetaPurpur/src/main/java/de/zfzfg/pvpwager/managers/CommandRequestManager.java#L146): `target.sendMessage(accept.append(...))`

> ⚠️ **Wichtiger Hinweis zu `catch (Exception e)`:**
> In `PvPWagerGuiCommand`, `CommandRequestManager` und `RequestManager` wird um `target.sendMessage(Component)` ein `try-catch (Exception e)` verwendet. Da ein fehlender Methodenaufruf in Java einen `NoSuchMethodError` (Unterklasse von `Error`, nicht `Exception`) auslöst, greift der Catch-Block auf Spigot **nicht** und der Thread stürzt ab.

---

### 2.3 GUI-Erstellung (`createInventory`) – 12 Stellen
Spigot akzeptiert bei `Bukkit.createInventory` nur `String`, keine Adventure-`Component`.

1. [`WagerMainGui.java:L38`](file:///c:/Users/zfzfg/Documents/HammerMegaProjekte/selfmadePlugins\Plugins\Event-PVP-Plugins\Event-PVP-Plugin-1.1.0-BetaPurpur/src/main/java/de/zfzfg/pvpwager/gui/WagerMainGui.java#L38)
2. [`ResponseMoneySelectionGui.java:L49`](file:///c:/Users/zfzfg/Documents/HammerMegaProjekte/selfmadePlugins\Plugins\Event-PVP-Plugins\Event-PVP-Plugin-1.1.0-BetaPurpur/src/main/java/de/zfzfg/pvpwager/gui/ResponseMoneySelectionGui.java#L49)
3. [`ResponseItemSelectionGui.java:L51`](file:///c:/Users/zfzfg/Documents/HammerMegaProjekte/selfmadePlugins\Plugins\Event-PVP-Plugins\Event-PVP-Plugin-1.1.0-BetaPurpur/src/main/java/de/zfzfg/pvpwager/gui/ResponseItemSelectionGui.java#L51)
4. [`ResponseGui.java:L47`](file:///c:/Users/zfzfg/Documents/HammerMegaProjekte/selfmadePlugins\Plugins\Event-PVP-Plugins\Event-PVP-Plugin-1.1.0-BetaPurpur/src/main/java/de/zfzfg/pvpwager/gui/ResponseGui.java#L47)
5. [`NegotiationGui.java:L60`](file:///c:/Users/zfzfg/Documents/HammerMegaProjekte/selfmadePlugins\Plugins\Event-PVP-Plugins\Event-PVP-Plugin-1.1.0-BetaPurpur/src/main/java/de/zfzfg/pvpwager/gui/NegotiationGui.java#L60)
6. [`MoneySelectionGui.java:L54`](file:///c:/Users/zfzfg/Documents/HammerMegaProjekte/selfmadePlugins\Plugins\Event-PVP-Plugins\Event-PVP-Plugin-1.1.0-BetaPurpur/src/main/java/de/zfzfg/pvpwager/gui/MoneySelectionGui.java#L54)
7. [`LiveTradeGui.java:L127`](file:///c:/Users/zfzfg/Documents/HammerMegaProjekte/selfmadePlugins\Plugins\Event-PVP-Plugins\Event-PVP-Plugin-1.1.0-BetaPurpur/src/main/java/de/zfzfg/pvpwager/gui/livetrade/LiveTradeGui.java#L127)
8. [`ItemSelectionGui.java:L50`](file:///c:/Users/zfzfg/Documents/HammerMegaProjekte/selfmadePlugins\Plugins\Event-PVP-Plugins\Event-PVP-Plugin-1.1.0-BetaPurpur/src/main/java/de/zfzfg/pvpwager/gui/ItemSelectionGui.java#L50)
9. [`EquipmentSelectionGui.java:L47`](file:///c:/Users/zfzfg/Documents/HammerMegaProjekte/selfmadePlugins\Plugins\Event-PVP-Plugins\Event-PVP-Plugin-1.1.0-BetaPurpur/src/main/java/de/zfzfg/pvpwager/gui/EquipmentSelectionGui.java#L47)
10. [`CounterOfferItemGui.java:L73`](file:///c:/Users/zfzfg/Documents/HammerMegaProjekte/selfmadePlugins\Plugins\Event-PVP-Plugins\Event-PVP-Plugin-1.1.0-BetaPurpur/src/main/java/de/zfzfg/pvpwager/gui/CounterOfferItemGui.java#L73)
11. [`ConfirmationGui.java:L41`](file:///c:/Users/zfzfg/Documents/HammerMegaProjekte/selfmadePlugins\Plugins\Event-PVP-Plugins\Event-PVP-Plugin-1.1.0-BetaPurpur/src/main/java/de/zfzfg/pvpwager/gui/ConfirmationGui.java#L41)
12. [`ArenaSelectionGui.java:L37`](file:///c:/Users/zfzfg/Documents/HammerMegaProjekte/selfmadePlugins\Plugins\Event-PVP-Plugins\Event-PVP-Plugin-1.1.0-BetaPurpur/src/main/java/de/zfzfg/pvpwager/gui/ArenaSelectionGui.java#L37)

---

### 2.4 `ItemMeta` Component-APIs (`displayName` & `lore`) – 26 Stellen
Spigot's `ItemMeta` besitzt nur `setDisplayName(String)`, `getDisplayName()`, `setLore(List<String>)`, `getLore()`.

1. [`ItemBuilder.java:L27`](file:///c:/Users/zfzfg/Documents/HammerMegaProjekte/selfmadePlugins\Plugins\Event-PVP-Plugins\Event-PVP-Plugin-1.1.0-BetaPurpur/src/main/java/de/zfzfg/pvpwager/utils/ItemBuilder.java#L27): `meta.displayName(Text.ofItem(name))`
2. [`ItemBuilder.java:L40`](file:///c:/Users/zfzfg/Documents/HammerMegaProjekte/selfmadePlugins\Plugins\Event-PVP-Plugins\Event-PVP-Plugin-1.1.0-BetaPurpur/src/main/java/de/zfzfg/pvpwager/utils/ItemBuilder.java#L40): `meta.lore(lore.stream().map(Text::ofItem).toList())`
3. [`AbstractWagerGui.java:L85, L98, L100, L114, L116`](file:///c:/Users/zfzfg/Documents/HammerMegaProjekte/selfmadePlugins\Plugins\Event-PVP-Plugins\Event-PVP-Plugin-1.1.0-BetaPurpur/src/main/java/de/zfzfg/pvpwager/gui/AbstractWagerGui.java#L85): `meta.displayName(...)` & `meta.lore(...)`
4. [`AbstractWagerGui.java:L295`](file:///c:/Users/zfzfg/Documents/HammerMegaProjekte/selfmadePlugins\Plugins\Event-PVP-Plugins\Event-PVP-Plugin-1.1.0-BetaPurpur/src/main/java/de/zfzfg/pvpwager/gui/AbstractWagerGui.java#L295): `meta.displayName()`
5. [`LiveTradeGui.java:L225, L234, L244, L253, L875, L885, L887, L898, L900`](file:///c:/Users/zfzfg/Documents/HammerMegaProjekte/selfmadePlugins\Plugins\Event-PVP-Plugins\Event-PVP-Plugin-1.1.0-BetaPurpur/src/main/java/de/zfzfg/pvpwager/gui/livetrade/LiveTradeGui.java#L225)
6. [`ResponseItemSelectionGui.java:L142, L145, L153, L156`](file:///c:/Users/zfzfg/Documents/HammerMegaProjekte/selfmadePlugins\Plugins\Event-PVP-Plugins\Event-PVP-Plugin-1.1.0-BetaPurpur/src/main/java/de/zfzfg/pvpwager/gui/ResponseItemSelectionGui.java#L142)
7. [`ItemSelectionGui.java:L144, L147, L155, L158`](file:///c:/Users/zfzfg/Documents/HammerMegaProjekte/selfmadePlugins\Plugins\Event-PVP-Plugins\Event-PVP-Plugin-1.1.0-BetaPurpur/src/main/java/de/zfzfg/pvpwager/gui/ItemSelectionGui.java#L144)
8. [`CounterOfferItemGui.java:L132, L135, L194, L197, L368`](file:///c:/Users/zfzfg/Documents/HammerMegaProjekte/selfmadePlugins\Plugins\Event-PVP-Plugins\Event-PVP-Plugin-1.1.0-BetaPurpur/src/main/java/de/zfzfg/pvpwager/gui/CounterOfferItemGui.java#L132)
9. [`ConfiguredItemFactory.java:L144, L149, L198, L203`](file:///c:/Users/zfzfg/Documents/HammerMegaProjekte/selfmadePlugins\Plugins\Event-PVP-Plugins\Event-PVP-Plugin-1.1.0-BetaPurpur/src/main/java/de/zfzfg/core/items/ConfiguredItemFactory.java#L144)
10. [`WebApiHandler.java:L546, L549`](file:///c:/Users/zfzfg/Documents/HammerMegaProjekte/selfmadePlugins\Plugins\Event-PVP-Plugins\Event-PVP-Plugin-1.1.0-BetaPurpur/src/main/java/de/zfzfg/core/web/WebApiHandler.java#L546)

---

### 2.5 Paper Registry Tag Keys – 2 Stellen
1. [`MaterialCatalog.java:L303`](file:///c:/Users/zfzfg/Documents/HammerMegaProjekte/selfmadePlugins\Plugins\Event-PVP-Plugins\Event-PVP-Plugin-1.1.0-BetaPurpur/src/main/java/de/zfzfg/core/web/MaterialCatalog.java#L303): `io.papermc.paper.registry.keys.tags.EnchantmentTagKeys.TREASURE`
2. [`MaterialCatalog.java:L319`](file:///c:/Users/zfzfg/Documents/HammerMegaProjekte/selfmadePlugins\Plugins\Event-PVP-Plugins\Event-PVP-Plugin-1.1.0-BetaPurpur/src/main/java/de/zfzfg/core/web/MaterialCatalog.java#L319): `io.papermc.paper.registry.keys.tags.EnchantmentTagKeys.CURSE`

---

## 3. 🏛️ Architektur der Dual-Platform Bridge

Damit das Plugin **ohne Performanceverlust auf Purpur** läuft und gleichzeitig **voll funktionsfähig auf Spigot** ist, wird eine schlanke Adapter-Architektur eingesetzt:

```mermaid
graph TD
    A[Plugin Core Code] --> B{Platform.isPaperOrPurpur()}
    B -->|JA - Purpur 26.2| C[Native Adventure Component Pipeline]
    B -->|JA - Purpur 26.2| D[Native Component ItemMeta & GUIs]
    B -->|JA - Purpur 26.2| E[Async Teleportation & Chunk Loading]
    B -->|NEIN - Spigot 26.2| F[Shaded Adventure Serializer]
    B -->|NEIN - Spigot 26.2| G[Legacy String ItemMeta & GUIs]
    B -->|NEIN - Spigot 26.2| H[Synchronous Teleport Fallback]
```

---

## 4. 🛠️ Detaillierte Lösungsoptionen pro Bereich

### Bereich 1: Maven `pom.xml` & Classpath-Shading

#### Lösung:
1. Adventure-Bibliotheken in `pom.xml` als `compile`-Scope einbinden.
2. Im `maven-shade-plugin` die Adventure-Klassen schattieren und relocaten:

```xml
<dependencies>
  <!-- Shaded Adventure für Spigot-Kompatibilität -->
  <dependency>
    <groupId>net.kyori</groupId>
    <artifactId>adventure-api</artifactId>
    <version>4.17.0</version>
    <scope>compile</scope>
  </dependency>
  <dependency>
    <groupId>net.kyori</groupId>
    <artifactId>adventure-text-serializer-legacy</artifactId>
    <version>4.17.0</version>
    <scope>compile</scope>
  </dependency>
  <dependency>
    <groupId>net.kyori</groupId>
    <artifactId>adventure-text-serializer-plain</artifactId>
    <version>4.17.0</version>
    <scope>compile</scope>
  </dependency>
</dependencies>

<build>
  <plugins>
    <plugin>
      <groupId>org.apache.maven.plugins</groupId>
      <artifactId>maven-shade-plugin</artifactId>
      <version>3.5.0</version>
      <configuration>
        <relocations>
          <relocation>
            <pattern>net.kyori</pattern>
            <shadedPattern>de.zfzfg.eventplugin.libs.kyori</shadedPattern>
          </relocation>
        </relocations>
      </configuration>
    </plugin>
  </plugins>
</build>
```

---

### Bereich 2: Plattform-Erkennung (`Platform.java`)

Erstellen einer zentralen Hilfsklasse `de.zfzfg.core.util.Platform`:

```java
package de.zfzfg.core.util;

import org.bukkit.entity.Player;

public final class Platform {
    private static final boolean IS_PAPER;

    static {
        boolean paper = false;
        try {
            // Prüft, ob Player die native Adventure-sendMessage-Methode besitzt
            Player.class.getMethod("sendMessage", net.kyori.adventure.text.Component.class);
            paper = true;
        } catch (Throwable ignored) {
            paper = false;
        }
        IS_PAPER = paper;
    }

    private Platform() {}

    /** @return true wenn der Server Paper, Purpur oder Pufferfish ist */
    public static boolean isPaper() {
        return IS_PAPER;
    }
}
```

---

### Bereich 3: Metadaten (`getPluginMeta()` vs. `getDescription()`)

#### Lösung:
Ersetzen aller Aufrufe von `plugin.getPluginMeta().getVersion()` bzw. `.getName()` durch:
* `plugin.getDescription().getVersion()`
* `plugin.getDescription().getName()`

`getDescription()` ist der offizielle Bukkit-Standard und funktioniert auf **Purpur 26.2, Paper und Spigot gleichermaßen zuverlässig**.

---

### Bereich 4: Text- & Nachrichtenversand (`TextUtil.java` & `Text.java`)

#### Lösung in [`TextUtil.java`](file:///c:/Users/zfzfg/Documents/HammerMegaProjekte/selfmadePlugins\Plugins\Event-PVP-Plugins\Event-PVP-Plugin-1.1.0-BetaPurpur/src/main/java/de/zfzfg/core/util/TextUtil.java):
```java
public static void send(CommandSender sender, Component message) {
    if (sender == null || message == null) return;
    if (Platform.isPaper()) {
        sender.sendMessage(message); // Nativ Purpur!
    } else {
        sender.sendMessage(Text.toLegacy(message)); // Spigot Fallback
    }
}

public static void send(CommandSender sender, String message) {
    if (sender == null || message == null) return;
    if (Platform.isPaper()) {
        sender.sendMessage(Text.of(message)); // Nativ Purpur Adventure!
    } else {
        sender.sendMessage(color(message)); // Spigot Legacy String
    }
}
```

#### Titel-Versand in [`EventSession.java`](file:///c:/Users/zfzfg/Documents/HammerMegaProjekte/selfmadePlugins\Plugins\Event-PVP-Plugins\Event-PVP-Plugin-1.1.0-BetaPurpur/src/main/java/de/zfzfg/eventplugin/session/EventSession.java):
```java
private static void showTitle(Player player, String title, String subtitle, int in, int stay, int out) {
    if (player == null) return;
    if (Platform.isPaper()) {
        player.showTitle(net.kyori.adventure.title.Title.title(
            Text.of(title),
            Text.of(subtitle),
            net.kyori.adventure.title.Title.Times.times(
                java.time.Duration.ofMillis(in * 50L),
                java.time.Duration.ofMillis(stay * 50L),
                java.time.Duration.ofMillis(out * 50L)
            )
        ));
    } else {
        player.sendTitle(TextUtil.color(title), TextUtil.color(subtitle), in, stay, out);
    }
}
```

---

### Bereich 5: GUI-Erstellung (`createInventory`)

#### Lösung in `de.zfzfg.core.util.GuiUtil`:
```java
package de.zfzfg.core.util;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public final class GuiUtil {
    private GuiUtil() {}

    public static Inventory createInventory(InventoryHolder holder, int size, Component title) {
        if (Platform.isPaper()) {
            return Bukkit.createInventory(holder, size, title); // Nativ Purpur
        } else {
            return Bukkit.createInventory(holder, size, Text.toLegacy(title)); // Spigot
        }
    }
}
```
*In allen 12 GUI-Klassen wird `Bukkit.createInventory(...)` durch `GuiUtil.createInventory(...)` ersetzt.*

---

### Bereich 6: `ItemMeta` Lore & DisplayName (`ItemUtil.java`)

#### Lösung in `de.zfzfg.core.util.ItemUtil`:
```java
package de.zfzfg.core.util;

import net.kyori.adventure.text.Component;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public final class ItemUtil {
    private ItemUtil() {}

    public static void setDisplayName(ItemMeta meta, Component name) {
        if (meta == null) return;
        if (Platform.isPaper()) {
            meta.displayName(name);
        } else {
            meta.setDisplayName(Text.toLegacy(name));
        }
    }

    public static void setLore(ItemMeta meta, List<Component> lore) {
        if (meta == null || lore == null) return;
        if (Platform.isPaper()) {
            meta.lore(lore);
        } else {
            meta.setLore(lore.stream().map(Text::toLegacy).collect(Collectors.toList()));
        }
    }

    public static List<Component> getLore(ItemMeta meta) {
        if (meta == null || !meta.hasLore()) return new ArrayList<>();
        if (Platform.isPaper()) {
            List<Component> l = meta.lore();
            return l != null ? new ArrayList<>(l) : new ArrayList<>();
        } else {
            List<String> legacy = meta.getLore();
            if (legacy == null) return new ArrayList<>();
            return legacy.stream().map(Text::of).collect(Collectors.toCollection(ArrayList::new));
        }
    }
}
```

---

### Bereich 7: Paper Registry Tags (`MaterialCatalog.java`)

#### Lösung:
Auslagern der Paper-Tag-Abfrage in eine isolierte Klasse, die nur geladen wird, wenn `Platform.isPaper()` wahr ist:

```java
public class MaterialCatalog {
    private static boolean isTreasure(Enchantment enchantment) {
        if (Platform.isPaper()) {
            try {
                return PaperRegistryHelper.isTreasure(enchantment);
            } catch (Throwable ignored) {}
        }
        try {
            return enchantment.isTreasure();
        } catch (Throwable ignored) {
            return false;
        }
    }
}

// Separate Klasse - wird auf Spigot niemals vom ClassLoader berührt!
final class PaperRegistryHelper {
    static boolean isTreasure(Enchantment enchantment) {
        var tagValues = org.bukkit.Registry.ENCHANTMENT.getTagValues(io.papermc.paper.registry.keys.tags.EnchantmentTagKeys.TREASURE);
        return tagValues != null && tagValues.contains(enchantment);
    }
    static boolean isCursed(Enchantment enchantment) {
        var tagValues = org.bukkit.Registry.ENCHANTMENT.getTagValues(io.papermc.paper.registry.keys.tags.EnchantmentTagKeys.CURSE);
        return tagValues != null && tagValues.contains(enchantment);
    }
}
```

---

### Bereich 8: Optionaler Performance-Boost (`teleportAsync`)

Anstelle von rein synchronem `player.teleport(location)` kann eine zukunftssichere Teleport-Methode genutzt werden:

```java
public static CompletableFuture<Boolean> teleport(Player player, Location location) {
    if (player == null || location == null) {
        return CompletableFuture.completedFuture(false);
    }
    if (Platform.isPaper()) {
        return player.teleportAsync(location); // Nativ Purpur Async Chunk Loading
    } else {
        return CompletableFuture.completedFuture(player.teleport(location)); // Spigot Synchron
    }
}
```

---

## 5. 📋 Schritt-für-Schritt Umsetzungs-Checkliste

- [ ] **Schritt 1:** `pom.xml` anpassen: Adventure-Artefakte als `compile` deklarieren und Relocation einrichten.
- [ ] **Schritt 2:** `Platform.java`, `GuiUtil.java` und `ItemUtil.java` im Package `de.zfzfg.core.util` anlegen.
- [ ] **Schritt 3:** `getPluginMeta()` in allen 6 Klassen durch `getDescription()` ersetzen.
- [ ] **Schritt 4:** `TextUtil.java` auf Dual-Dispatch umstellen.
- [ ] **Schritt 5:** `createInventory` in den 12 GUI-Klassen auf `GuiUtil.createInventory` umstellen.
- [ ] **Schritt 6:** `ItemBuilder.java` und GUI-Item-Erstellungen auf `ItemUtil` umstellen.
- [ ] **Schritt 7:** `MaterialCatalog.java` Registry-Tags in `PaperRegistryHelper` auslagern.
- [ ] **Schritt 8:** Testlauf auf Purpur 26.2 (`C:\Users\zfzfg\Documents\servers\purpur-26-2`) und Spigot 26.2 (`C:\Users\zfzfg\Documents\servers\spigot-26.2`).
