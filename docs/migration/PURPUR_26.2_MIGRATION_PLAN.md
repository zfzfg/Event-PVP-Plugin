# Migrationsplan: Event-PVP-Plugin 1.0.9 → Purpur 26.2 + Adventure

**Zielserver:** `C:\Users\zfzfg\Documents\servers\purpur-26-2`
**Projekt:** `C:\Users\zfzfg\Documents\HammerMegaProjekte\selfmadePlugins\Plugins\Event-PVP-Plugins\Event-PVP-Plugin-1.0.9-PurpurOptimized`
**Erstellt:** 2026-08-13

---

## 0. Wie dieser Plan zu lesen ist

Dieser Plan ist für eine **ausführende KI** geschrieben, die den Code nicht kennt.
Er ist in **Phasen** (P0–P7) und darin in **atomare Aufgaben** (z. B. `P1.3`) gegliedert.

**Verbindliche Arbeitsregeln — nicht abweichen:**

1. **Reihenfolge einhalten.** Phasen strikt nacheinander. Innerhalb einer Phase Aufgaben in Nummernreihenfolge.
2. **Eine Aufgabe = eine Änderung = eine Verifikation.** Nach *jeder* Aufgabe die dort angegebene Verifikation ausführen. Erst bei Erfolg zur nächsten Aufgabe.
3. **Niemals raten.** Wenn eine Signatur, ein Klassenname oder ein Enum-Wert unklar ist: mit `javap` (Kommando siehe Anhang A) gegen die echte JAR prüfen. Nicht aus dem Gedächtnis schreiben.
4. **Kein Scope-Creep.** Nur was hier steht. Keine Umbenennungen, keine Formatierungs-Sweeps, keine „Verbesserungen" nebenbei.
5. **Kein Löschen von Funktionalität.** Wenn eine Migration eine Funktion kaputt machen würde, in `MIGRATION_NOTES.md` dokumentieren und **stoppen**, statt die Funktion zu entfernen.
6. **Bei Blockade:** In `MIGRATION_NOTES.md` unter `## BLOCKIERT` festhalten (Aufgabe, Datei, Fehlermeldung, was versucht wurde), alle *unabhängigen* restlichen Aufgaben trotzdem fertigstellen, am Ende berichten.
7. **Deutsch** in Kommentaren/Doku (das Projekt ist deutschsprachig kommentiert). Code-Bezeichner bleiben englisch.
8. **Umlaute im Quelltext vermeiden** — die vorhandenen Kommentare schreiben bewusst `ue/ae/oe`. Das beibehalten.

**Abbruchkriterien (STOPP, nicht weiterarbeiten):**
- `mvn -o test-compile` schlägt nach einer Aufgabe fehl und lässt sich nicht in max. 3 Versuchen beheben.
- Ein bereits grüner Test wird rot und die Ursache ist nicht innerhalb der Aufgabe erklärbar.

---

## 1. Verifizierte Fakten (bereits geprüft — nicht erneut recherchieren)

Diese Angaben wurden gegen die echten Dateien auf diesem Rechner geprüft. Sie sind Grundlage des Plans.

| Fakt | Wert | Quelle |
|---|---|---|
| Server-Brand | Purpur | `versions/26.2/purpur-26.2.jar` → MANIFEST |
| Server-Version | `26.2-2618-5a85de0 (MC: 26.2)` | `.paper/version_history.json` |
| Build-Nummer | 2618, `26.2.build.2618-stable` | MANIFEST `Specification-Version` |
| Passendes API-Artefakt | `org.purpurmc.purpur:purpur-api:26.2.build.2618-stable` | liegt bereits in `~/.m2` |
| Purpur-Repo | `https://repo.purpurmc.org/snapshots` | Referenz-Plugin JsonBuild |
| Adventure-Version im Server | **5.2.0** (nicht 4.x!) | `libraries/net/kyori/*/5.2.0/` |
| `api-version` die auf diesem Server akzeptiert wird | `"26.2"` | JsonBuild-0.0.2 läuft damit |
| Lokal installiertes JDK | Java 26.0.1 | `java -version` |
| Referenz-Plugin (läuft dort) kompiliert mit | Java 21 (source/target) | JsonBuild `pom.xml` im JAR |
| `net/md_5/**` in `purpur-api` enthalten? | **NEIN** (0 Klassen) | `unzip -l` auf die API-JAR |
| `net/md_5/**` im Server-JAR enthalten? | **NEIN** (0 Klassen) | `unzip -l` auf `purpur-26.2.jar` |
| BungeeCord-Chat zur Laufzeit | nur noch als *deprecated* Library: `libraries/net/md-5/bungeecord-chat/1.21-R0.2-**deprecated**+build.21/` | Dateisystem |
| Aktueller Build-Zustand | `mvn -o test-compile` läuft **grün** durch, alle Deps im lokalen `.m2` | ausgeführt |
| Aktueller Laufzeit-Zustand | Plugin **lädt und enabled** heute auf 26.2 | `logs/2026-08-12-9.log.gz` |

### 1.1 Warum die BungeeCord-Chat-Migration nötig ist (präzise Einordnung)

Der Nutzer hat recht, die Begründung ist aber wichtig für korrekte Entscheidungen:

- Das Plugin **stürzt heute noch nicht ab**. Der Server liefert `bungeecord-chat` weiterhin als
  Runtime-Library aus — allerdings in einer Version, die im Dateinamen wörtlich `deprecated` trägt.
- **Aber:** `purpur-api` selbst enthält die Klassen nicht mehr. Der Compile funktioniert nur, weil
  in der `pom.xml` eine separate `net.md-5:bungeecord-chat`-Dependency steht.
- Die API-Methoden (`CommandSender#sendMessage(BaseComponent)`, `Player#spigot()`) existieren zwar
  noch als Signaturen, sind aber alle deprecated-for-removal. Sobald Paper/Purpur die Library
  entfernt, wirft **jeder** der 11 `spigot().sendMessage(...)`-Aufrufe zur Laufzeit
  `NoClassDefFoundError` — und zwar erst *dann*, wenn ein Spieler die Aktion auslöst
  (Klick-Nachrichten bei PvP-Anfragen, Event-Join-Buttons, Web-Token-Links). Das ist der
  gefährlichste Fehlertyp: unauffällig beim Start, kaputt im Betrieb.

**Konsequenz für den Plan:** Migration nach Adventure ist **Pflicht**, nicht optional, und die
`bungeecord-chat`-Dependency wird am Ende **ersatzlos entfernt** (P2.9). Erst wenn die Dependency
weg ist und alles kompiliert, ist bewiesen, dass keine Restnutzung übrig ist.

### 1.2 Betroffener Code — vollständige Inventur

**A) `net.md_5.bungee`-Nutzung — 7 Dateien, 11 Sende-Stellen:**

| # | Datei | Art | Sende-Zeilen (Stand heute) |
|---|---|---|---|
| 1 | `src/main/java/de/zfzfg/core/commands/WebTokenSubCommand.java` | Imports (inkl. `net.md_5.bungee.api.ChatColor`!) | 116, 133 |
| 2 | `src/main/java/de/zfzfg/eventplugin/commands/EventPvpCommand.java` | Imports | 288, 302 |
| 3 | `src/main/java/de/zfzfg/eventplugin/session/EventSession.java` | voll qualifiziert (kein Import) | 248, 1317 |
| 4 | `src/main/java/de/zfzfg/pvpwager/commands/PvPWagerGuiCommand.java` | Imports | 241 |
| 5 | `src/main/java/de/zfzfg/pvpwager/managers/CommandRequestManager.java` | voll qualifiziert | 162, 197 |
| 6 | `src/main/java/de/zfzfg/pvpwager/managers/MatchManager.java` | Imports | 862 |
| 7 | `src/main/java/de/zfzfg/pvpwager/managers/RequestManager.java` | Imports | 106 |

> Zeilennummern sind **Orientierung, kein Anker**. Immer per Textsuche nach `spigot()` bzw.
> `md_5` in der jeweiligen Datei arbeiten, nie blind nach Zeilennummer editieren.

**B) Der zentrale Text-Chokepoint (der Glücksfall dieses Projekts):**

```
de/zfzfg/core/util/TextUtil.java     ← EINZIGE Stelle mit ChatColor.translateAlternateColorCodes
        ↑                    ↑
        │                    │
ColorUtil.java        MessageUtil.java        (dünne Delegates)
   (eventplugin)         (pvpwager)
        ↑                    ↑
        └──── ~700 sendMessage-Aufrufe im ganzen Plugin ────┘
```

Das bedeutet: **Die Adventure-Umstellung des Fließtexts passiert in genau einer Datei**
(`TextUtil.java`). Die ~700 Aufrufstellen bleiben unverändert. Das ist der Kern der Strategie in
P2 und der Grund, warum diese Migration überhaupt beherrschbar ist.

**C) `org.bukkit.ChatColor` — 18 Dateien.** Nicht entfernt in 26.2, aber deprecated.
Wird in diesem Plan **bewusst NICHT flächendeckend angefasst** (siehe P3.1 für die Begründung
und die eine Ausnahme).

**D) Weitere Alt-API-Stellen (Details in Phase 3):**
- `EventSession.java:1878,1884` — `player.sendTitle(String,String,int,int,int)` (deprecated)
- `ConfiguredItemFactory.java:404` — `Enchantment.getByName(...)` (deprecated)
- `ConfiguredItemFactory.java:341-357` — Reflection auf `org.bukkit.potion.PotionData`
- `MaterialCatalog.java:321` — `Enchantment.values()` (deprecated seit 1.20.5)
- `WebApiHandler.java:1090-1111` — Reflection auf `recentTps` für TPS

**E) Tests heute:** 12 Testklassen. Keine Server-Simulation (kein MockBukkit), reines
JUnit 5 + Mockito + Reflection auf private Felder.

---

## Phase 0 — Sicherung & Baseline

> **Ziel:** Ein reproduzierbarer Ausgangszustand und eine Rückfallebene. Ohne P0 keine weitere Phase.

### P0.1 — Sicherungskopie anlegen

Das Projekt ist **kein Git-Repo**. Es gibt also kein `git checkout` als Rettung.

```bash
cp -r "/c/Users/zfzfg/Documents/HammerMegaProjekte/selfmadePlugins/Plugins/Event-PVP-Plugins/Event-PVP-Plugin-1.0.9-PurpurOptimized" \
      "/c/Users/zfzfg/Documents/HammerMegaProjekte/selfmadePlugins/Plugins/Event-PVP-Plugins/Event-PVP-Plugin-1.0.9-BACKUP-vor-purpur26"
```

**Verifikation:** Der Backup-Ordner existiert und enthält `pom.xml` und `src/`.
**STOPP-Bedingung:** Backup fehlgeschlagen → nicht weitermachen.

### P0.2 — Git initialisieren (dringend empfohlen)

```bash
cd "/c/Users/zfzfg/Documents/HammerMegaProjekte/selfmadePlugins/Plugins/Event-PVP-Plugins/Event-PVP-Plugin-1.0.9-PurpurOptimized"
printf 'target/\n.pytest_cache/\nreports/\n' > .gitignore
git init && git add -A && git commit -m "Baseline vor Purpur-26.2-Migration"
```

Ab hier: **nach jeder abgeschlossenen Aufgabe committen**, Commit-Message = Aufgaben-ID
(z. B. `P2.3: EventSession auf Adventure umgestellt`). Das ist die Rückfallebene für alles Weitere.

**Verifikation:** `git log --oneline` zeigt einen Commit.

### P0.3 — Baseline-Build & Baseline-Tests festhalten

```bash
mvn -o clean test 2>&1 | tee "$SCRATCH/baseline-tests.txt" | tail -30
```

Ergebnis (Zahl der Tests, run/failures/errors/skipped) in `MIGRATION_NOTES.md` unter
`## Baseline` notieren.

**Verifikation:** `BUILD SUCCESS`.
**Falls rot:** Die Migration startet auf keiner roten Baseline. Fehlschlagende Tests zuerst
verstehen und in `MIGRATION_NOTES.md` dokumentieren; erst dann fortfahren.

### P0.4 — Notizdatei anlegen

Datei `MIGRATION_NOTES.md` im Projektwurzelverzeichnis mit den Abschnitten:
```markdown
# Migrationsnotizen Purpur 26.2
## Baseline
## Entscheidungen
## BLOCKIERT
## Offene Punkte für den Menschen
```

---

## Phase 1 — Build auf Purpur 26.2 umstellen

> **Ziel:** Gegen die echte Ziel-API kompilieren. Danach zeigt der Compiler selbst, was kaputt ist.
> Das ist der Grund, warum diese Phase **vor** der Adventure-Migration kommt: der Compiler wird
> zum Werkzeug, statt dass geraten wird.

### P1.1 — `pom.xml`: Java-Level auf 21

In `pom.xml`, Block `<properties>`:

```xml
<maven.compiler.source>21</maven.compiler.source>
<maven.compiler.target>21</maven.compiler.target>
```

Begründung: Das Referenz-Plugin auf demselben Server (JsonBuild) nutzt 21, `purpur-api` 26.2 ist
gegen ≥21 gebaut. Nicht auf 25/26 gehen — 21 ist die konservative, belegte Wahl. Falls der
Compiler später `class file has wrong version`-Fehler meldet, in `MIGRATION_NOTES.md` notieren
und auf 25 erhöhen; nicht vorher.

**Verifikation:** —(kommt mit P1.4)

### P1.2 — `pom.xml`: Purpur-Repository ergänzen

Im `<repositories>`-Block **ganz oben** einfügen:

```xml
<repository>
  <id>purpur-repo</id>
  <url>https://repo.purpurmc.org/snapshots</url>
</repository>
```

Das `spigot-repo` bleibt vorerst drin (schadet nicht, wird in P1.6 aufgeräumt).

### P1.3 — `pom.xml`: Spigot-API durch Purpur-API ersetzen

**Ersetze** den kompletten Block

```xml
<dependency>
  <groupId>org.spigotmc</groupId>
  <artifactId>spigot-api</artifactId>
  <version>${spigot.api.version}</version>
  <scope>provided</scope>
</dependency>
```

**durch**

```xml
<!--
  Purpur-API 26.2 (Superset von Paper- und Bukkit-API) - passend zum Zielserver
  C:\Users\zfzfg\Documents\servers\purpur-26-2 (Build 2618).
  Bringt Adventure 5.2.0 transitiv mit; deshalb ist hier KEINE eigene
  net.kyori-Dependency noetig und auch nicht erwuenscht (siehe P1.5).
-->
<dependency>
  <groupId>org.purpurmc.purpur</groupId>
  <artifactId>purpur-api</artifactId>
  <version>${purpur.api.version}</version>
  <scope>provided</scope>
</dependency>
```

Und in `<properties>` die alte Property ersetzen:

```xml
<!-- ersetzt <spigot.api.version> -->
<purpur.api.version>26.2.build.2618-stable</purpur.api.version>
```

> **Wichtig:** Genau diese Version verwenden. Sie liegt bereits im lokalen `~/.m2` und der Build
> funktioniert dadurch **offline**. Eine andere Version würde einen Netzwerkzugriff erzwingen.

### P1.4 — Kompilieren und den Schadensbericht erstellen

```bash
mvn -o clean test-compile 2>&1 | tee "$SCRATCH/p1-compile.txt" | tail -60
```

**Erwartung:** Der Build schlägt jetzt möglicherweise fehl — das ist gewollt und informativ.

Aus der Ausgabe eine Liste aller Fehler (`[ERROR]`) und Warnungen (`[WARNING]`) extrahieren und
in `MIGRATION_NOTES.md` unter `## P1.4 Schadensbericht` aufnehmen, gruppiert nach Ursache:

- `cannot find symbol` bei `net.md_5.*` → wird in Phase 2 gelöst
- `deprecated` → wird in Phase 3 bewertet
- alles andere → einzeln bewerten

**Wenn der Build hier bereits grün ist:** ebenfalls gut. Dann trotzdem mit
`-Xlint:all,-serial` die Deprecation-Liste erzeugen:

```bash
mvn -o clean test-compile -DcompilerArgument=-Xlint:deprecation 2>&1 | grep -i deprecat | sort -u
```

### P1.5 — Sicherstellen, dass Adventure NICHT geshaded wird

**Kritischer Punkt.** Der `maven-shade-plugin` steht in dieser `pom.xml` mit
`<minimizeJar>false</minimizeJar>` und ohne `<artifactSet>`-Einschränkung. Weil alle Dependencies
`provided` sind, wird heute nichts eingepackt. Das **muss so bleiben**.

Wenn Adventure jemals mit ins JAR geriete, gäbe es zwei Kopien von `net.kyori.adventure.text.Component`
im Speicher (eine vom Server, eine vom Plugin) und jeder `sendMessage(Component)`-Aufruf würde mit
`ClassCastException` oder `NoSuchMethodError` fehlschlagen. Das ist der klassische, schwer zu
findende Fehler dieser Migration.

**Aufgabe:** Zur Absicherung im Shade-Plugin explizit machen:

```xml
<configuration>
  <artifactSet>
    <excludes>
      <exclude>net.kyori:*</exclude>
      <exclude>org.purpurmc.purpur:*</exclude>
      <exclude>io.papermc.paper:*</exclude>
      <exclude>org.spigotmc:*</exclude>
      <exclude>com.google.guava:*</exclude>
      <exclude>com.google.code.gson:*</exclude>
    </excludes>
  </artifactSet>
  <relocations>
    ... (bestehender Block unveraendert lassen) ...
  </relocations>
  <minimizeJar>false</minimizeJar>
</configuration>
```

**Verifikation (nach dem ersten erfolgreichen `package`, spätestens Ende Phase 2):**

```bash
unzip -l target/event-pvp-plugin-1.0.9.jar | grep -c "net/kyori"
# MUSS 0 ergeben
unzip -l target/event-pvp-plugin-1.0.9.jar | grep -c "org/bukkit"
# MUSS 0 ergeben
```

### P1.6 — `plugin.yml`: `api-version` anheben

In `src/main/resources/plugin.yml`:

```yaml
api-version: '26.2'
```

(bisher `1.19`). Belegt durch JsonBuild, das mit `api-version: "26.2"` auf genau diesem Server läuft.

**Wirkung:** Der Server schaltet den *Legacy-Material-Konvertierungspfad* ab. Das ist eine echte
Performance- und Korrektheitsverbesserung, aber auch ein Risiko: falls irgendwo Legacy-Material-
Namen benutzt werden, fallen sie **jetzt** auf. Genau deshalb steht in Phase 6 ein Live-Test.

**Verifikation:** `mvn -o clean package` und dann
```bash
unzip -p target/event-pvp-plugin-1.0.9.jar plugin.yml | head -5
```
zeigt `api-version: '26.2'` und die korrekt ersetzte `version: 1.0.9`.

### P1.7 — `pom.xml`: Surefire-Argline prüfen

Aktuell: `<argLine>-Dnet.bytebuddy.experimental=true</argLine>`. Unter Java 21+ und Mockito 5.11
kann zusätzlich eine Warnung zu dynamischem Agent-Attach auftreten. Falls Tests in P0.3/P5 mit
`Java agent has been loaded dynamically` scheitern, ergänzen:

```xml
<argLine>-Dnet.bytebuddy.experimental=true -XX:+EnableDynamicAgentLoading</argLine>
```

Nur ändern, **wenn** ein Test deswegen fehlschlägt. Nicht prophylaktisch.

---

## Phase 2 — Adventure-Migration (Kernstück)

> **Ziel:** Kein `net.md_5.bungee` mehr im Quelltext, Dependency entfernt, Verhalten identisch.
> **Strategie:** Erst eine neue Utility-Schicht bauen (P2.1), dann die 7 Dateien darauf umstellen
> (P2.2–P2.8), dann die Dependency entfernen als Beweis (P2.9).

### P2.0 — Zwei Regeln, die für die ganze Phase gelten

**Regel A — Adventure ist Version 5.2.0, nicht 4.x.**
Die meisten Tutorials und Trainingsdaten beschreiben Adventure 4.x. In 5.x ist
`ClickEvent.Action` **kein Enum mehr**, sondern eine versiegelte Schnittstelle mit generischem
Payload. Der 4.x-Stil

```java
// FALSCH unter Adventure 5.x — kompiliert nicht
Component.text("x").clickEvent(ClickEvent.clickEvent(ClickEvent.Action.RUN_COMMAND, "/cmd"))
```

funktioniert nicht. **Immer die statischen Fabrikmethoden benutzen** — die sind stabil und
wurden gegen die echte JAR verifiziert:

```java
ClickEvent.runCommand(String)      // → ClickEvent<Payload.Text>
ClickEvent.suggestCommand(String)
ClickEvent.openUrl(String)
ClickEvent.copyToClipboard(String)
HoverEvent.showText(Component)     // → HoverEvent<Component>
```

**Regel B — kein Text-Verlust bei Farbcodes.**
Alle Nachrichten des Plugins kommen als Legacy-Strings mit `&`-Codes aus den `messages_*.yml`.
Die dürfen **nicht** durch MiniMessage ersetzt werden — das wäre eine Umstellung aller sieben
Sprachdateien und ist ausdrücklich **nicht Teil dieses Plans**. Stattdessen wird der
`LegacyComponentSerializer` als Brücke benutzt.

### P2.1 — Neue Klasse `de.zfzfg.core.util.Text` anlegen

**Neue Datei:** `src/main/java/de/zfzfg/core/util/Text.java`

Diese Klasse ist ab jetzt die **einzige** Stelle im Plugin, die Legacy-Strings in Adventure-
Components übersetzt. Sie ersetzt `TextUtil` nicht, sondern ergänzt sie (`TextUtil` wird in P2.10
intern darauf umgebaut).

```java
package de.zfzfg.core.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bruecke zwischen den Legacy-Nachrichten aus den messages_*.yml (&-Farbcodes)
 * und der Adventure-API von Purpur 26.2.
 *
 * <p>Bewusst die einzige Stelle im Plugin, die Legacy-Text parst. Wer Text an einen
 * Spieler schicken will, geht ueber {@link #of(String)} oder ueber TextUtil/MessageUtil,
 * die hier hindurch delegieren. Direkte Aufrufe von LegacyComponentSerializer an anderer
 * Stelle sind ein Fehler - dann liegt Parsing-Logik doppelt vor.
 *
 * <p>Der Serializer ist mit {@code hexColors()} konfiguriert, damit die in einigen
 * Sprachdateien vorhandenen &#RRGGBB-Codes weiterhin funktionieren.
 */
public final class Text {

    private Text() {}

    /** Parser fuer &-Codes inkl. &#RRGGBB-Hex. Thread-safe und wiederverwendbar. */
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.builder()
            .character('&')
            .hexColors()
            .build();

    /**
     * Cache fuer bereits geparste Nachrichten. Ersetzt den frueheren String-Cache in
     * TextUtil: dieselbe Motivation (Nachrichten wiederholen sich stark), nur eine Ebene
     * weiter oben, sodass auch das Parsing gespart wird und nicht nur das Ersetzen.
     * Components sind immutable, das Teilen ist daher gefahrlos.
     */
    private static final Map<String, Component> CACHE = new ConcurrentHashMap<>();

    /** Obergrenze, damit dynamisch erzeugte Strings den Cache nicht unbegrenzt fuellen. */
    private static final int CACHE_LIMIT = 4096;

    /** Legacy-String (&-Codes) zu Component. {@code null} wird zu {@link Component#empty()}. */
    public static Component of(String legacy) {
        if (legacy == null || legacy.isEmpty()) return Component.empty();
        Component cached = CACHE.get(legacy);
        if (cached != null) return cached;
        Component parsed = LEGACY.deserialize(legacy);
        if (CACHE.size() < CACHE_LIMIT) CACHE.put(legacy, parsed);
        return parsed;
    }

    /**
     * Wie {@link #of(String)}, aber ohne den Kursiv-Standard, den Minecraft auf
     * Item-Namen und Lore legt. Fuer ItemMeta IMMER diese Variante nehmen.
     */
    public static Component ofItem(String legacy) {
        return of(legacy).decoration(TextDecoration.ITALIC, false);
    }

    /** Component zurueck in einen Legacy-String - nur fuer Alt-APIs, die noch String wollen. */
    public static String toLegacy(Component component) {
        return component == null ? "" : LEGACY.serialize(component);
    }

    /** Farbcodes entfernen (Ersatz fuer ChatColor.stripColor). */
    public static String plain(String legacy) {
        return PlainTextComponentSerializer.plainText().serialize(of(legacy));
    }

    /** Nur fuer Tests: Cache leeren, damit Testreihenfolge egal ist. */
    static void clearCache() { CACHE.clear(); }
}
```

**Verifikation:**
```bash
mvn -o test-compile
```
muss grün sein. Falls `PlainTextComponentSerializer` nicht gefunden wird, prüfen ob
`adventure-text-serializer-plain` transitiv da ist:
```bash
mvn -o dependency:tree | grep -i kyori
```
(Es *ist* da — Server-Library-Ordner belegt 5.2.0. Falls doch nicht: `plain()` mit
`Text.of(x)` + manuellem Strip ersetzen und in `MIGRATION_NOTES.md` vermerken.)

### P2.2 — Test für `Text` schreiben (Test-First ab hier)

**Neue Datei:** `src/test/java/de/zfzfg/core/util/TextTest.java`

Dieser Test läuft **ohne Server** — Adventure ist eine reine Bibliothek. Das ist der Grund,
warum diese Klasse so gut testbar ist, und der Hebel für Phase 5.

```java
package de.zfzfg.core.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TextTest {

    private String plain(Component c) {
        return PlainTextComponentSerializer.plainText().serialize(c);
    }

    @Test
    void nullUndLeerErgebenLeereComponent() {
        assertEquals(Component.empty(), Text.of(null));
        assertEquals(Component.empty(), Text.of(""));
    }

    @Test
    void ampersandCodeWirdZuFarbe() {
        Component c = Text.of("&cFehler");
        assertEquals("Fehler", plain(c));
        assertEquals(NamedTextColor.RED, c.color());
    }

    @Test
    void hexCodeWirdGeparst() {
        Component c = Text.of("&#ff8800Warnung");
        assertEquals("Warnung", plain(c));
        assertNotNull(c.color());
    }

    @Test
    void formatierungBleibtErhalten() {
        Component c = Text.of("&l&aFett");
        assertEquals(TextDecoration.State.TRUE, c.decoration(TextDecoration.BOLD));
    }

    @Test
    void textOhneCodesBleibtUnveraendert() {
        assertEquals("Hallo Welt", plain(Text.of("Hallo Welt")));
    }

    @Test
    void itemVarianteSchaltetKursivAb() {
        assertEquals(TextDecoration.State.FALSE,
                Text.ofItem("&bSchwert").decoration(TextDecoration.ITALIC));
    }

    @Test
    void cacheLiefertIdentischeInstanz() {
        Text.clearCache();
        assertSame(Text.of("&aWiederholt"), Text.of("&aWiederholt"));
    }

    @Test
    void roundtripUeberLegacyIstStabil() {
        String original = "&cRot &7grau";
        assertEquals(original, Text.toLegacy(Text.of(original)));
    }

    @Test
    void plainEntferntAlleCodes() {
        assertEquals("Rot grau", Text.plain("&cRot &7grau"));
    }

    @Test
    void mehrzeiligerTextUeberlebt() {
        assertTrue(plain(Text.of("&aZeile1\nZeile2")).contains("\n"));
    }
}
```

**Verifikation:** `mvn -o test -Dtest=TextTest` → alle 10 grün.
**Falls `roundtripUeberLegacyIstStabil` rot ist:** Das ist akzeptabel (Legacy-Roundtrips sind
nicht bitgenau garantiert). Dann die Assertion auf `Text.plain(...)`-Vergleich abschwächen und in
`MIGRATION_NOTES.md` notieren — **nicht** die `Text`-Klasse anpassen.

### P2.3 — Zusätzlich: Helfer für Klick-Nachrichten

Weil alle 7 Dateien dasselbe Muster bauen (Text + RUN_COMMAND + SHOW_TEXT-Hover), gehört das
einmal zentral hin. **Ergänze in `Text.java`:**

```java
    /**
     * Baut einen anklickbaren Chat-Button: Beschriftung, auszufuehrender Befehl,
     * Hover-Text. Alle drei Parameter sind Legacy-Strings aus den messages_*.yml.
     *
     * <p>Der Befehl wird mit fuehrendem "/" normalisiert, weil die Aufrufer in
     * diesem Projekt es mal so und mal so uebergeben haben.
     */
    public static Component button(String label, String command, String hover) {
        String cmd = command == null ? "" : (command.startsWith("/") ? command : "/" + command);
        Component c = of(label).clickEvent(net.kyori.adventure.text.event.ClickEvent.runCommand(cmd));
        if (hover != null && !hover.isEmpty()) {
            c = c.hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(of(hover)));
        }
        return c;
    }

    /** Wie {@link #button}, aber oeffnet eine URL statt einen Befehl auszufuehren. */
    public static Component link(String label, String url, String hover) {
        Component c = of(label).clickEvent(net.kyori.adventure.text.event.ClickEvent.openUrl(url));
        if (hover != null && !hover.isEmpty()) {
            c = c.hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(of(hover)));
        }
        return c;
    }
```

**Ergänze in `TextTest.java`:**

```java
    @Test
    void buttonSetztRunCommandMitSlash() {
        Component c = Text.button("&aJa", "pvpaccept Bob", "&7Annehmen");
        var click = c.clickEvent();
        assertNotNull(click);
        assertNotNull(c.hoverEvent());
        assertTrue(Text.toLegacy(c).contains("Ja"));
    }

    @Test
    void buttonDoppeltKeinenSlash() {
        assertNotNull(Text.button("&aJa", "/pvpaccept Bob", null).clickEvent());
    }

    @Test
    void buttonOhneHoverHatKeinenHover() {
        assertNull(Text.button("&aJa", "pvpaccept", null).hoverEvent());
    }

    @Test
    void linkSetztOpenUrl() {
        assertNotNull(Text.link("&bWeb", "http://localhost:8080", "&7Oeffnen").clickEvent());
    }
```

**Verifikation:** `mvn -o test -Dtest=TextTest` → jetzt 14 grün.

> **Hinweis für die ausführende KI:** Falls `c.clickEvent()` einen Compile-Fehler wegen der
> Generics von `ClickEvent<?>` wirft, den Rückgabetyp als `var` oder `ClickEvent<?>` deklarieren,
> **nicht** als `ClickEvent`. Adventure 5.x hat den Typ generisch gemacht.

### P2.4 bis P2.8 — Die 7 Dateien umstellen

Für **jede** der 7 Dateien aus Tabelle 1.2-A gilt dasselbe Rezept. Eine Datei pro Aufgabe,
eine Verifikation pro Datei, ein Commit pro Datei.

| Aufgabe | Datei |
|---|---|
| P2.4 | `pvpwager/managers/RequestManager.java` *(kleinste, 1 Sendestelle — hier anfangen)* |
| P2.5 | `pvpwager/managers/MatchManager.java` |
| P2.6 | `pvpwager/managers/CommandRequestManager.java` |
| P2.7 | `pvpwager/commands/PvPWagerGuiCommand.java` |
| P2.8a | `eventplugin/session/EventSession.java` |
| P2.8b | `eventplugin/commands/EventPvpCommand.java` |
| P2.8c | `core/commands/WebTokenSubCommand.java` |

**Rezept pro Datei:**

1. **Alle** `import net.md_5.bungee.*;`-Zeilen löschen.
   Achtung bei `WebTokenSubCommand.java`: dort ist auch
   `import net.md_5.bungee.api.ChatColor;` (nicht `org.bukkit.ChatColor`!). Ersatz ist
   **nicht** `org.bukkit.ChatColor`, sondern die Codes durch `Text.of(...)` zu leiten — siehe
   Schritt 3.
2. Import ergänzen: `import de.zfzfg.core.util.Text;` und, falls Component direkt gebraucht wird,
   `import net.kyori.adventure.text.Component;`
3. Jede Konstruktion umschreiben. Muster-für-Muster-Tabelle:

| Alt (BungeeCord) | Neu (Adventure 5.x) |
|---|---|
| `new TextComponent(s)` | `Text.of(s)` |
| `c.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, cmd))` | `c = c.clickEvent(ClickEvent.runCommand(cmd))` |
| `c.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder(t).create()))` | `c = c.hoverEvent(HoverEvent.showText(Text.of(t)))` |
| `p.spigot().sendMessage(c)` | `p.sendMessage(c)` |
| `p.spigot().sendMessage(a, b)` | `p.sendMessage(a.append(b))` |
| `p.spigot().sendMessage(a, space, b)` | `p.sendMessage(a.append(Component.space()).append(b))` |
| komplettes Button-Konstrukt (Text+Click+Hover) | `Text.button(label, cmd, hover)` — **bevorzugt** |
| `net.md_5.bungee.api.ChatColor.RED + s` | `Text.of("&c" + s)` |

> **Die wichtigste Falle:** Adventure-Components sind **immutable**. `c.clickEvent(...)` verändert
> `c` **nicht**, sondern gibt eine neue Component zurück. Wer `c.clickEvent(...)` schreibt ohne
> das Ergebnis zuzuweisen, erhält eine Nachricht ohne Klick-Funktion — sie kompiliert, sie sieht
> im Chat richtig aus, und sie tut beim Klick nichts. Das ist der wahrscheinlichste stille Fehler
> dieser Phase. **Nach jeder Datei prüfen:** Steht links von jedem `.clickEvent(`/`.hoverEvent(`
> eine Zuweisung oder ist es Teil einer durchgehenden Kette?

**Konkretes Vorher/Nachher (aus `CommandRequestManager.java`, Zeilen ~142–162):**

```java
// VORHER
net.md_5.bungee.api.chat.TextComponent accept =
    new net.md_5.bungee.api.chat.TextComponent(MessageUtil.color(getMsg("btn-accept")));
accept.setClickEvent(new net.md_5.bungee.api.chat.ClickEvent(
    net.md_5.bungee.api.chat.ClickEvent.Action.RUN_COMMAND, "/pvpaccept " + name));
accept.setHoverEvent(new net.md_5.bungee.api.chat.HoverEvent(
    net.md_5.bungee.api.chat.HoverEvent.Action.SHOW_TEXT,
    new net.md_5.bungee.api.chat.ComponentBuilder(MessageUtil.color(getMsg("btn-accept-hover"))).create()));
// ... deny analog ...
target.spigot().sendMessage(accept, deny);

// NACHHER
Component accept = Text.button(getMsg("btn-accept"), "/pvpaccept " + name, getMsg("btn-accept-hover"));
Component deny   = Text.button(getMsg("btn-deny"),   "/pvpdeny "   + name, getMsg("btn-deny-hover"));
target.sendMessage(accept.append(Component.space()).append(deny));
```

> Beachte: `MessageUtil.color(...)` fällt weg — `Text.button` parst die `&`-Codes bereits selbst.
> Doppeltes Parsen wäre nicht falsch (idempotent), aber unnötig und irreführend.
> **Beachte auch:** Im Original steht bei `sendMessage(accept, deny)` **kein** Leerzeichen
> zwischen den Buttons, weil BungeeCord die Komponenten unverändert aneinanderhängt. Ob hier
> `Component.space()` eingefügt werden soll, ist eine Verhaltensänderung — sie ist optisch
> besser, aber sie *ist* eine Änderung. Entscheidung: **Space einfügen**, in
> `MIGRATION_NOTES.md` unter `## Entscheidungen` vermerken, in Phase 6 optisch prüfen.
> (Bei `sendMessage(guiBtn, space, denyBtn)` in Zeile ~197 war der Space bereits explizit — dort
> also 1:1 übernehmen, nicht verdoppeln.)

4. **Verifikation pro Datei:**
```bash
mvn -o test-compile && grep -c "md_5" src/main/java/<PFAD_DER_DATEI>
```
Der `grep` **muss 0** ergeben, der Compile grün sein.

5. Commit mit der Aufgaben-ID.

### P2.9 — `bungeecord-chat`-Dependency entfernen (der Beweis)

Aus `pom.xml` **ersatzlos löschen**:

```xml
<dependency>
  <groupId>net.md-5</groupId>
  <artifactId>bungeecord-chat</artifactId>
  <version>1.16-R0.4</version>
  <scope>provided</scope>
</dependency>
```

Dann:

```bash
mvn -o clean test-compile
grep -rn "md_5" src/main/java src/test/java   # MUSS leer sein
```

**Das ist die eigentliche Abnahme von Phase 2.** Solange die Dependency da ist, könnte
irgendwo noch eine Restnutzung schlummern. Ohne sie beweist ein grüner Compile, dass keine mehr
existiert.

**Wenn hier Fehler auftauchen:** Es wurde eine Stelle übersehen. Fehlerliste durchgehen, die
betroffenen Dateien nach dem Rezept aus P2.4 nachziehen, dann erneut.

### P2.10 — `TextUtil` intern auf Adventure umstellen

Erst **jetzt**, nachdem die Komponenten-Fälle sauber sind, wird der Fließtext umgestellt.
Der Effekt: ~700 Aufrufstellen wechseln auf einen Schlag zu echten Components, ohne dass eine
einzige davon angefasst wird.

**Ersetze den Inhalt von `src/main/java/de/zfzfg/core/util/TextUtil.java`:**

```java
package de.zfzfg.core.util;

import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Zentraler Ausgang fuer Spielernachrichten.
 *
 * <p>Historisch hat diese Klasse Legacy-Strings mit ChatColor uebersetzt und als String
 * verschickt. Seit der Umstellung auf Purpur 26.2 laeuft das Verschicken ueber Adventure
 * ({@link Text}); die String-Signaturen bleiben aber erhalten, damit die rund 700
 * Aufrufstellen im Plugin unveraendert bleiben konnten.
 *
 * <p>{@link #color(String)} gibt weiterhin einen Legacy-String zurueck - er wird an vielen
 * Stellen weiterverarbeitet (Item-Namen, Konfigvergleiche, Logausgaben) und darf deshalb
 * keine Component werden. Neuer Code sollte stattdessen direkt {@link Text#of(String)}
 * benutzen.
 */
public class TextUtil {

    /**
     * Uebersetzt &-Codes in Section-Codes. Bewusst weiterhin ueber den Adventure-Serializer,
     * damit es exakt EINE Parser-Implementierung im Plugin gibt und &#RRGGBB genauso
     * behandelt wird wie beim Verschicken.
     */
    public static String color(String text) {
        if (text == null) return "";
        return Text.toLegacy(Text.of(text));
    }

    public static String strip(String text) {
        return Text.plain(text);
    }

    /** Component-Variante fuer neuen Code. */
    public static Component component(String text) {
        return Text.of(text);
    }

    public static void send(CommandSender sender, String message) {
        if (sender == null) return;
        sender.sendMessage(Text.of(message));
    }

    public static void send(Player player, String message) {
        if (player == null) return;
        player.sendMessage(Text.of(message));
    }

    /** Direktversand einer fertigen Component. */
    public static void send(CommandSender sender, Component message) {
        if (sender == null || message == null) return;
        sender.sendMessage(message);
    }
}
```

> **Warum `color()` weiterhin einen String liefert:** Ein Blick in die Aufrufer zeigt, dass das
> Ergebnis nicht nur verschickt, sondern auch in `ItemMeta#setDisplayName`, in Vergleiche und in
> Logs gesteckt wird. Eine Signaturänderung auf `Component` würde hier Dutzende Aufrufstellen
> brechen. Der Plan behält den String bewusst bei — das ist eine bewusste Entscheidung, kein
> Versäumnis. In `MIGRATION_NOTES.md` unter `## Entscheidungen` vermerken.

**Wichtige Änderung mit dokumentieren:** Der alte `colorCache` (String→String) entfällt; der Cache
liegt jetzt in `Text.CACHE` (String→Component) und deckt mehr ab.

**Verifikation:**
```bash
mvn -o test-compile
mvn -o test   # Baseline-Tests aus P0.3 muessen weiterhin gruen sein
```

### P2.11 — Test: `TextUtil` verhält sich wie vorher

**Neue Datei:** `src/test/java/de/zfzfg/core/util/TextUtilTest.java`

```java
package de.zfzfg.core.util;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TextUtilTest {

    @Test
    void colorErzeugtSectionCodes() {
        assertEquals("\u00a7cFehler", TextUtil.color("&cFehler"));
    }

    @Test
    void colorAufNullIstLeerString() {
        assertEquals("", TextUtil.color(null));
    }

    @Test
    void colorIstIdempotent() {
        String einmal = TextUtil.color("&aTest");
        assertEquals(einmal, TextUtil.color(einmal));
    }

    @Test
    void stripEntferntCodes() {
        assertEquals("Fehler", TextUtil.strip("&cFehler"));
    }

    @Test
    void stripAufNullIstLeerString() {
        assertEquals("", TextUtil.strip(null));
    }

    @Test
    void componentLiefertGeparsteComponent() {
        assertNotNull(TextUtil.component("&cX").color());
    }

    @Test
    void sendAufNullEmpfaengerWirftNicht() {
        assertDoesNotThrow(() -> TextUtil.send((org.bukkit.entity.Player) null, "x"));
    }
}
```

**Verifikation:** `mvn -o test -Dtest=TextUtilTest` grün.
**Falls `colorIstIdempotent` rot ist:** Das ist ein echter Fund. Der Legacy-Serializer könnte
`§`-Codes anders behandeln als `ChatColor.translateAlternateColorCodes` es tat. Dann in
`Text.of()` zusätzlich `§` als Eingabezeichen akzeptieren
(`LegacyComponentSerializer.builder().character('&').hexColors().build()` → zusätzlich einen
zweiten Serializer mit `character('\u00a7')` und Vorabnormalisierung). In `MIGRATION_NOTES.md`
dokumentieren.

### P2.12 — Abschluss-Scan Phase 2

```bash
grep -rn "md_5\|spigot()\|BaseComponent" src/main/java src/test/java pom.xml
```
**Muss vollständig leer sein.** Wenn nicht: die Fundstellen nach P2.4-Rezept nachziehen.

---

## Phase 3 — Restliche Alt-API modernisieren

> **Ziel:** Deprecation-Warnungen, die *echte* Ausfallrisiken sind, beseitigen. Nicht mehr.

### P3.1 — Entscheidung zu `org.bukkit.ChatColor` (18 Dateien)

**Keine flächendeckende Migration.** Begründung:

- `org.bukkit.ChatColor` existiert in `purpur-api` 26.2 (verifiziert) und funktioniert.
- 18 Dateien anzufassen wäre ein großer Diff mit hohem Regressionsrisiko und **null**
  Laufzeitgewinn.
- Der gefährliche Teil (BungeeCord-Chat) ist in Phase 2 bereits erledigt.

**Ausnahme (Pflicht):** Wo `ChatColor` genutzt wird, um Text für `ItemMeta` zu bauen, entsteht ein
sichtbares Problem — Adventure-Item-Namen sind kursiv, Legacy-Namen waren es nicht. Prüfen:

```bash
grep -rn "setDisplayName\|setLore" src/main/java | head -40
```

Solange nur die **String**-Varianten (`setDisplayName(String)`, `setLore(List<String>)`)
verwendet werden, ändert sich nichts — der Server konvertiert intern und behält das
Nicht-Kursiv-Verhalten. **Aufgabe:** Verifizieren, dass keine Stelle bereits auf
`displayName(Component)` / `lore(List<Component>)` umgestellt wurde. Falls doch: dort
`Text.ofItem(...)` statt `Text.of(...)` benutzen.

In `MIGRATION_NOTES.md` unter `## Entscheidungen` festhalten: *„ChatColor bleibt bewusst stehen."*

### P3.2 — `sendTitle` auf Adventure umstellen

**Datei:** `src/main/java/de/zfzfg/eventplugin/session/EventSession.java`, Zeilen ~1874–1885.

```java
// VORHER
player.sendTitle(ColorUtil.color(title), ColorUtil.color(subtitle), 10, 40, 10);

// NACHHER
player.showTitle(net.kyori.adventure.title.Title.title(
        Text.of(title),
        Text.of(subtitle),
        net.kyori.adventure.title.Title.Times.times(
                java.time.Duration.ofMillis(10 * 50L),
                java.time.Duration.ofMillis(40 * 50L),
                java.time.Duration.ofMillis(10 * 50L))));
```

> **Wichtig:** Ticks → Duration. 1 Tick = 50 ms. Beide Aufrufstellen (Zeile ~1878 mit 40 Ticks
> Anzeigedauer und ~1884 mit 60) haben **unterschiedliche** Werte — nicht vereinheitlichen,
> jeweils die Originalwerte übernehmen.

Sauberer wäre eine kleine private Hilfsmethode in `EventSession`:

```java
    /** Titel an einen Spieler, Zeiten in Ticks wie in der alten sendTitle-API. */
    private static void showTitle(Player player, String title, String subtitle,
                                  int fadeIn, int stay, int fadeOut) {
        player.showTitle(Title.title(Text.of(title), Text.of(subtitle),
                Title.Times.times(ticks(fadeIn), ticks(stay), ticks(fadeOut))));
    }

    private static Duration ticks(int t) { return Duration.ofMillis(t * 50L); }
```

**Verifikation:** `mvn -o test-compile` grün; `grep -n "sendTitle(" src/main/java/de/zfzfg/eventplugin/session/EventSession.java`
zeigt nur noch die *eigene* private Methode, keine Bukkit-API mehr.

### P3.3 — `Enchantment.getByName` ersetzen

**Datei:** `src/main/java/de/zfzfg/core/items/ConfiguredItemFactory.java`, Zeile ~404.

`Enchantment.getByName(String)` arbeitet mit alten Bukkit-Namen (`DAMAGE_ALL`), die es in
modernen Versionen nicht mehr gibt. Auf 26.2 liefert das für viele Konfigwerte `null` → das
Item wird ohne Verzauberung erstellt. **Das ist ein stiller Funktionsverlust, kein Absturz** —
und damit ein realer Bug für den Nutzer (Equipment-Sets `diamond_pvp`, `netherite`, `uhc` laden
laut Log heute schon).

**Neue Logik (defensiv, beide Schreibweisen):**

```java
    /**
     * Loest einen Verzauberungsnamen aus der equipment.yml auf. Akzeptiert sowohl moderne
     * Registry-Keys ("sharpness", "minecraft:sharpness") als auch die alten Bukkit-Konstanten
     * ("DAMAGE_ALL"), damit bestehende Konfigurationen weiterlaufen.
     */
    private static Enchantment resolveEnchantment(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String key = raw.trim().toLowerCase(java.util.Locale.ROOT);
        if (!key.contains(":")) key = "minecraft:" + key;
        NamespacedKey nsk = NamespacedKey.fromString(key);
        if (nsk != null) {
            Enchantment byKey = Registry.ENCHANTMENT.get(nsk);
            if (byKey != null) return byKey;
        }
        // Fallback fuer alte Konfigurationen mit Bukkit-Konstantennamen.
        @SuppressWarnings("deprecation")
        Enchantment legacy = Enchantment.getByName(raw.trim().toUpperCase(java.util.Locale.ROOT));
        return legacy;
    }
```

> **Vor dem Schreiben verifizieren** (nicht raten), ob `Registry.ENCHANTMENT` in dieser API-Version
> so heißt:
> ```bash
> javap -cp "<PURPUR_API_JAR>" org.bukkit.Registry | grep -i enchant
> ```
> (Kommando komplett in Anhang A.) Falls der Name abweicht, den echten Namen verwenden.

**Zugehöriger Test** — `src/test/java/de/zfzfg/core/items/EnchantmentResolveTest.java`.
Achtung: `Registry` braucht einen laufenden Server, ist im Unit-Test also nicht verfügbar.
Deshalb **nur die Normalisierungslogik** testen. Dazu die reine String-Normalisierung in eine
eigene, package-private, statische Methode `normalizeEnchantKey(String)` herausziehen und
diese testen:

```java
class EnchantmentResolveTest {
    @Test void ergaenztNamespace()      { assertEquals("minecraft:sharpness", ConfiguredItemFactory.normalizeEnchantKey("sharpness")); }
    @Test void behaeltNamespace()       { assertEquals("minecraft:sharpness", ConfiguredItemFactory.normalizeEnchantKey("minecraft:sharpness")); }
    @Test void kleinschreibung()        { assertEquals("minecraft:sharpness", ConfiguredItemFactory.normalizeEnchantKey("SHARPNESS")); }
    @Test void trimmtLeerzeichen()      { assertEquals("minecraft:sharpness", ConfiguredItemFactory.normalizeEnchantKey("  sharpness ")); }
    @Test void fremderNamespaceBleibt() { assertEquals("custom:foo", ConfiguredItemFactory.normalizeEnchantKey("custom:foo")); }
    @Test void nullBleibtNull()         { assertNull(ConfiguredItemFactory.normalizeEnchantKey(null)); }
    @Test void leerBleibtNull()         { assertNull(ConfiguredItemFactory.normalizeEnchantKey("   ")); }
}
```

> **Merke dir dieses Muster** — es ist der Schlüssel zu Phase 5: *reine Logik von Bukkit-Aufrufen
> trennen, dann die reine Logik testen.*

### P3.4 — `Enchantment.values()` in `MaterialCatalog` prüfen

**Datei:** `src/main/java/de/zfzfg/core/web/MaterialCatalog.java`, Zeile ~321.

Der Kommentar dort sagt bereits, dass die Registry bevorzugt wird und `values()` nur Fallback ist.
**Aufgabe:** Prüfen, ob der Registry-Pfad auf 26.2 tatsächlich greift (also ob der Fallback tote
Code ist). Wenn `Enchantment.values()` auf 26.2 nicht mehr existiert, schlägt bereits P1.4 fehl
und die Zeile muss weg. Wenn sie existiert: unverändert lassen, nur `@SuppressWarnings("deprecation")`
an der umgebenden Methode ergänzen, damit die Warnung nicht das Build-Log flutet.

### P3.5 — `PotionData`-Reflection prüfen

**Datei:** `src/main/java/de/zfzfg/core/items/ConfiguredItemFactory.java`, Zeilen ~290–357.

Der Code versucht per Reflection erst `PotionMeta.setBasePotionType` (modern) und fällt sonst auf
`org.bukkit.potion.PotionData` (entfernt seit 1.20.5) zurück. Auf 26.2 greift der moderne Pfad.

**Aufgabe:** Den `PotionData`-Fallback-Block **entfernen** und stattdessen den modernen Pfad
**direkt** (ohne Reflection) aufrufen, da die Zielversion feststeht:

```java
meta.setBasePotionType(PotionType.valueOf(name.toUpperCase(Locale.ROOT)));
```

mit `try/catch (IllegalArgumentException)` und einer Log-Warnung bei unbekanntem Typ.

> **Vorher verifizieren:** `javap -cp "<API_JAR>" org.bukkit.inventory.meta.PotionMeta | grep -i basePotion`
> **Wenn die Methode nicht existiert:** Aufgabe überspringen, Reflection stehen lassen, in
> `MIGRATION_NOTES.md` unter `## BLOCKIERT` vermerken. Reflection zu entfernen, die noch gebraucht
> wird, wäre schlimmer als sie zu behalten.

### P3.6 — TPS-Reflection durch API ersetzen

**Datei:** `src/main/java/de/zfzfg/core/web/WebApiHandler.java`, Zeilen ~1090–1111.

Paper/Purpur bieten `Bukkit.getServer().getTPS()` als offizielle API. Die Reflection auf
`MinecraftServer.recentTps` ist unnötig und bricht bei jedem Remapping.

```java
// VORHER: ~20 Zeilen gecachte Reflection
// NACHHER
private static double getTps() {
    double[] tps = org.bukkit.Bukkit.getServer().getTPS();
    return (tps != null && tps.length > 0) ? Math.min(20.0, tps[0]) : 20.0;
}
```

Die Felder `cachedGetServerMethod`, `cachedRecentTpsField`, `tpsReflectionFailed` und deren
Initialisierung entfernen.

**Verifikation:**
```bash
javap -cp "<API_JAR>" org.bukkit.Server | grep -i getTPS   # muss getTPS() finden
mvn -o test-compile
grep -c "recentTps" src/main/java/de/zfzfg/core/web/WebApiHandler.java   # muss 0 sein
```

### P3.7 — Deprecation-Restliste erzeugen und bewerten

```bash
mvn -o clean test-compile -DcompilerArgument=-Xlint:deprecation 2>&1 \
  | grep -i "deprecat" | sort -u > "$SCRATCH/deprecations-nach-p3.txt"
```

Die Liste in `MIGRATION_NOTES.md` unter `## Verbleibende Deprecations` ablegen, je Eintrag mit
einer Einordnung: `entfernt-in-zukunft-riskant` / `harmlos-bleibt`. **Nichts weiter ändern** —
diese Liste ist für den Menschen, nicht zum Abarbeiten.

---

## Phase 4 — Purpur-/Paper-spezifische Optimierung

> **Ziel:** Messbare Verbesserungen mit geringem Risiko. **Keine spekulativen Umbauten.**
> Wenn eine Aufgabe hier Zweifel auslöst: überspringen und notieren. Ein laufendes Plugin ist
> mehr wert als ein theoretisch schnelleres.

### P4.1 — Regel: kein Folia-Umbau

Purpur 26.2 ist **kein** Folia. Der Bukkit-Scheduler (`BukkitRunnable`, `runTaskTimer`,
`runTaskAsynchronously` — 67 Fundstellen) funktioniert unverändert und korrekt.
**Nicht** auf `RegionScheduler`/`GlobalRegionScheduler` umstellen. Das wäre ein
Großumbau ohne Gegenwert auf diesem Server.

In `MIGRATION_NOTES.md` unter `## Entscheidungen` festhalten.

### P4.2 — Event-Handler auf `ignoreCancelled` prüfen

`PlayerMoveEvent` wird an 6 Stellen benutzt. Das ist das mit Abstand häufigste Event im Server
(mehrfach pro Spieler pro Tick).

**Aufgabe:** Für **jeden** `PlayerMoveEvent`-Handler prüfen und ggf. korrigieren:

1. Ist `ignoreCancelled = true` gesetzt? Wenn der Handler bei abgebrochener Bewegung nichts tun
   soll: setzen.
2. **Frühzeitiger Ausstieg auf Blockebene** — der wichtigste Einzelgewinn:
   ```java
   @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
   public void onMove(PlayerMoveEvent event) {
       // Nur reagieren, wenn der Spieler den Block gewechselt hat. Reine Blickrichtungs-
       // und Sub-Block-Bewegungen feuern dieses Event mehrfach pro Tick und interessieren
       // hier nicht.
       if (!event.hasChangedBlock()) return;
       ...
   }
   ```
   `hasChangedBlock()` ist Paper-API und in `purpur-api` verfügbar — vor Nutzung mit `javap`
   bestätigen (Anhang A).
3. Falls der Handler nur für Spieler in einem laufenden Event/Match relevant ist: **zuerst**
   die Set-Mitgliedschaft prüfen, **dann** alles andere.

**Verifikation:** `mvn -o test-compile` grün. Fachliche Prüfung erfolgt in Phase 6 (P6.5).

### P4.3 — `Bukkit.getOnlinePlayers()` in Schleifen prüfen

15 Fundstellen. **Aufgabe:** Jede Stelle ansehen. Wo der Aufruf *innerhalb* einer Schleife oder
eines Ticks steht, einmal vor der Schleife in eine lokale Variable ziehen. Wo bereits außerhalb:
nicht anfassen.

> Kein Micro-Optimierungs-Sweep. Nur die Stellen, die tatsächlich in einer Schleife stehen.

### P4.4 — `Location`-Objekte in heißen Pfaden

183 `new Location(...)`. **Nicht alle anfassen.** Nur dort prüfen, wo pro Tick allokiert wird
(Countdown-Tasks, Move-Listener, Arena-Grenzprüfungen). Typische Verbesserung:

```java
// statt: loc.distance(other) < r          → Wurzel pro Aufruf
if (loc.distanceSquared(other) < r * r) { ... }
```

und Vergleiche über `loc.getBlockX()` statt neuer `Location`-Objekte.

**Wichtig:** `distance` → `distanceSquared` erfordert das Quadrieren des Radius. Wer das vergisst,
ändert die Spiellogik. Nach jeder solchen Änderung: die Radius-Konstante prüfen.

### P4.5 — Web-Server-Thread-Pool prüfen

`WebServer.java` nutzt `com.sun.net.httpserver.HttpServer`. **Aufgabe:** Prüfen, ob ein
`Executor` gesetzt ist:

```bash
grep -n "setExecutor" src/main/java/de/zfzfg/core/web/WebServer.java
```

Wenn **nicht** gesetzt: Der HttpServer bearbeitet dann alle Anfragen in **einem** Thread, was den
Server bei mehreren gleichzeitigen Web-UI-Zugriffen ausbremst. Setzen:

```java
// Begrenzter Pool: das Web-UI ist ein Admin-Werkzeug, kein Massen-Endpunkt. Ein fester
// kleiner Pool verhindert, dass Anfragen den Server-Thread-Haushalt belasten.
server.setExecutor(java.util.concurrent.Executors.newFixedThreadPool(4, r -> {
    Thread t = new Thread(r, "EventPvP-Web");
    t.setDaemon(true);
    return t;
}));
```

Und im `onDisable` sauber herunterfahren (`executor.shutdownNow()` + `server.stop(0)`).

> **Achtung, wichtig:** Handler laufen dann auf Fremd-Threads. Jeder Bukkit-API-Zugriff aus einem
> Handler heraus **muss** über `Bukkit.getScheduler().runTask(plugin, ...)` zurück auf den Main-Thread.
> **Aufgabe:** `WebApiHandler.java` durchgehen und jeden Bukkit-API-Aufruf identifizieren. Wenn
> welche gefunden werden, die nicht bereits gescheduled sind: **diese Aufgabe abbrechen**, in
> `MIGRATION_NOTES.md` unter `## BLOCKIERT` mit der Liste vermerken. Ein Thread-Safety-Fehler ist
> deutlich schlimmer als ein langsamer Web-Server.

### P4.6 — `HashMap` vs. `ConcurrentHashMap`

211 `HashMap`, 91 `ConcurrentHashMap`. **Keine pauschale Umstellung.**
`HashMap` ist auf dem Main-Thread korrekt und schneller. Nur wo eine `HashMap` nachweislich aus
mehreren Threads berührt wird (Web-Handler, Async-Tasks), umstellen. Diese Aufgabe nur ausführen,
wenn in P4.5 konkrete Fundstellen auftauchen.

### P4.7 — Startzeit messen (Vorher/Nachher)

Aus dem Serverlog die `Enabling Event-PVP-Plugin`- und die nachfolgende Zeile mit Zeitstempel
extrahieren, vor und nach der Migration. In `MIGRATION_NOTES.md` gegenüberstellen. Das ist die
einzige belastbare Aussage über den Optimierungseffekt, die dieser Plan zulässt — alles andere
wäre Behauptung.

---

## Phase 5 — Tests (so viele wie sinnvoll möglich)

> **Ziel:** Von 12 auf deutlich mehr Testklassen. **Aber:** Ein Test, der nur bestätigt, dass
> Mockito Mocks erzeugt, ist wertlos und kostet Wartung. Dieser Plan priorisiert nach
> Fehlerwahrscheinlichkeit.

### P5.0 — Die Testphilosophie dieses Projekts

Die bestehenden 12 Tests zeigen das Muster: **kein Server, reines JUnit + Mockito**, teils
Reflection auf private Felder. Das funktioniert, weil die getestete Logik von Bukkit entkoppelt ist.

**Die Regel, die für alle neuen Tests gilt:**

> Wenn eine Methode nicht ohne laufenden Server testbar ist, ist meistens die *Methode* das
> Problem, nicht der Test. Dann die reine Logik in eine eigene, statische, package-private
> Methode ohne Bukkit-Bezug herausziehen und **die** testen.

Das ist keine Formalie — es ist der einzige Weg, wie diese Phase überhaupt liefern kann.

### P5.1 — MockBukkit: Entscheidung

MockBukkit würde echte Server-Simulation erlauben. **Aber:**
- Es ist **nicht** im lokalen `~/.m2` → braucht Netzwerk.
- Es müsste zur MC-Version 26.2 passen. Ob es eine solche Version gibt, ist **nicht verifiziert**.

**Aufgabe:** *Einen* Versuch unternehmen:
```bash
mvn dependency:get -Dartifact=org.mockbukkit.mockbukkit:mockbukkit-v1.21:4.+ 2>&1 | tail -5
```
- **Erfolg und es gibt eine 26.2-passende Variante** → als `test`-Dependency aufnehmen, P5.9 wird
  möglich.
- **Kein Erfolg / keine passende Version** → **kein Problem.** In `MIGRATION_NOTES.md` unter
  `## Entscheidungen` notieren, P5.9 überspringen, mit P5.2–P5.8 den Großteil des Werts trotzdem
  liefern. Nicht kämpfen, nicht auf eine unpassende Version zwingen.

### P5.2 — Testgruppe A: Text & Adventure (höchste Priorität)

Bereits in P2.2 und P2.11 angelegt. **Ergänzen** um `src/test/java/de/zfzfg/core/util/TextButtonTest.java`:

| Test | Prüft |
|---|---|
| `buttonMitLeeremLabel` | leeres Label → keine NPE, leere Component |
| `buttonMitNullCommand` | `null`-Befehl → `/` oder kein Click-Event, kein Absturz |
| `buttonHoverEnthaeltFarbe` | Farbcode im Hover überlebt |
| `verschachtelteAppendKette` | `a.append(space).append(b)` ergibt korrekten Plaintext |
| `sehrLangerTextWirdNichtAbgeschnitten` | 5000 Zeichen bleiben vollständig |
| `unbekannterFarbcode` | `&z` bleibt als Literal erhalten, kein Absturz |
| `nurFarbcodeOhneText` | `"&c"` → leerer Text, kein Absturz |
| `cacheUeberschreitetLimitNicht` | 5000 verschiedene Strings → `CACHE.size() <= 4096` |

Diese Gruppe ist die wichtigste, weil sie genau das absichert, was in Phase 2 geändert wurde.

### P5.3 — Testgruppe B: Bestehende Tests härten

Für die 12 vorhandenen Testklassen jeweils prüfen und **je 2–4 Fälle ergänzen**:

| Testklasse | Zu ergänzende Fälle |
|---|---|
| `ConfigMigrationServiceTest` | leere Config, Config mit unbekannten Keys, doppelte Migration (Idempotenz), Config mit `null`-Werten |
| `EquipmentSchemaMigrationTest` | fehlendes `enchantments`-Feld, unbekannter Enchantment-Name, negative Level, Level über Maximum |
| `PendingPayoutSerializationTest` | Betrag `0`, negativer Betrag, sehr großer Betrag (`Long.MAX_VALUE`), leere Item-Liste, `null`-Spieler |
| `ItemAssetManifestTest` | leeres Manifest, Eintrag mit Sonderzeichen, doppelter Key |
| `SlidingWindowLimiterTest` | exakt an der Grenze, Grenze +1, Fenster läuft ab, mehrere Schlüssel unabhängig, Nebenläufigkeit (2 Threads) |
| `TextureOverridePathTest` | Pfad mit `..` (Traversal!), absoluter Pfad, Backslash unter Windows, leerer Pfad |
| `MvWorldInputValidationTest` | Weltname mit Leerzeichen, mit `/`, mit `;` (Command-Injection!), Leerstring, sehr langer Name |
| `WorldRestoreTest` / `WorldBackupTest` | Zielordner existiert bereits, Quelle fehlt, keine Schreibrechte |
| `MatchManagerTest` | Match mit sich selbst, Spieler offline mitten im Match, doppeltes Beenden |
| `PlaceholderReplacementTest` | Platzhalter fehlt in Nachricht, Wert ist `null`, Platzhalter kommt doppelt vor, ungerade Zahl von Varargs |
| `MatchModelTest` | Gleichheit/HashCode, Zustandsübergänge, ungültiger Übergang |

> Die mit `!` markierten Fälle (`TextureOverridePathTest` Traversal, `MvWorldInputValidationTest`
> Injection) sind **Sicherheitstests** — die zuerst schreiben. Ein Web-UI, das Dateipfade
> entgegennimmt, und ein Weltname, der in einen Konsolenbefehl fließt, sind die beiden
> risikoreichsten Eingänge in diesem Plugin.

### P5.4 — Testgruppe C: Neue Tests für reine Logik (ohne Refactoring)

Diese Klassen enthalten testbare Logik und haben **keinen** Test. Je Klasse eine neue Testklasse:

| Zielklasse | Was testen |
|---|---|
| `pvpwager/utils/MessageUtil` | `formatTime(0/59/60/61/3599/3600)`, `formatItemList(null/leer/mit null-Eintrag/mehrere)` |
| `eventplugin/util/ColorUtil` | Delegation an `TextUtil` korrekt, `null`-Eingaben |
| `core/util/CommandCooldownManager` | Cooldown aktiv/abgelaufen/nie gesetzt, verschiedene Spieler unabhängig, Cooldown 0 |
| `core/config/CoreConfigManager` | fehlender Key → Default, verschachtelte Keys, Platzhalter-Varianten (existiert teils schon) |
| `eventplugin/model/EventConfig` | Laden aus YamlConfiguration, fehlende Pflichtfelder, ungültige Zahlen |
| `core/world/mv/MvWorldInfo` | die handgeschriebene JSON-Serialisierung: Sonderzeichen, `null`-Felder, Anführungszeichen im Weltnamen |

`YamlConfiguration` funktioniert **ohne** laufenden Server (reine Bukkit-Utility-Klasse) — die
bestehenden Tests nutzen das bereits. Das ist der Hebel für alles Konfigurationsbezogene.

### P5.5 — Testgruppe D: Logik herausziehen, dann testen

Hier wird zuerst **refaktoriert** (reine Logik in statische Methoden), dann getestet.
Pro Eintrag: erst Extraktion, `mvn -o test-compile` grün, dann Test, dann Commit.

| Ziel | Zu extrahierende Methode | Testfälle |
|---|---|---|
| `ConfiguredItemFactory` | `normalizeEnchantKey(String)` (bereits in P3.3) | 7 Fälle, siehe P3.3 |
| `ConfiguredItemFactory` | `parseAmount(String, int default)` | `null`, leer, `"0"`, `"64"`, `"999"`, `"-1"`, `"abc"` |
| `MatchManager` | Gewinner-/Payout-Berechnung | Gleichstand, Aufgabe, Sieg, beide offline |
| `ArenaManager` | Arena-Auswahl / Belegungsprüfung | keine frei, alle frei, genau eine frei |
| `EventSession` | Countdown-Formatierung, Teilnehmer-Mindestzahl-Prüfung | 0/1/min-1/min/min+1 Teilnehmer |
| `WebApiHandler` | Request-Pfad-Parsing, Token-Validierung | gültig, abgelaufen, falsch, leer, `null` |

> **Warnung:** `EventSession` (92 KB) und `MatchManager` (87 KB) sind die größten Klassen im
> Projekt. **Nicht umstrukturieren.** Nur einzelne, klar abgegrenzte Berechnungen in statische
> Methoden herausziehen und die alte Stelle darauf umleiten. Bei jedem Zweifel: überspringen und
> in `MIGRATION_NOTES.md` notieren.

### P5.6 — Testgruppe E: Regressionstests der Migration

**Neue Datei:** `src/test/java/de/zfzfg/MigrationRegressionTest.java`

Diese Tests sichern die Migration selbst ab und schlagen an, falls jemand später zurückfällt:

```java
class MigrationRegressionTest {

    /** Kein Quelltext darf wieder BungeeCord-Chat benutzen. */
    @Test
    void keinBungeeCordChatImQuelltext() throws Exception {
        List<Path> treffer = Files.walk(Path.of("src/main/java"))
                .filter(p -> p.toString().endsWith(".java"))
                .filter(p -> {
                    try { return Files.readString(p).contains("md_5"); }
                    catch (IOException e) { return false; }
                }).toList();
        assertTrue(treffer.isEmpty(), "BungeeCord-Chat wieder eingefuehrt in: " + treffer);
    }

    /** Kein spigot()-Aufruf mehr. */
    @Test
    void keinSpigotAufruf() throws Exception { /* analog, Suchstring "spigot()" */ }

    /** plugin.yml zielt auf die richtige API-Version. */
    @Test
    void pluginYmlApiVersion() throws Exception {
        assertTrue(Files.readString(Path.of("src/main/resources/plugin.yml")).contains("26.2"));
    }

    /** pom.xml enthaelt keine bungeecord-chat-Dependency mehr. */
    @Test
    void pomOhneBungeeCord() throws Exception {
        assertFalse(Files.readString(Path.of("pom.xml")).contains("bungeecord-chat"));
    }

    /** pom.xml zielt auf purpur-api. */
    @Test
    void pomMitPurpurApi() throws Exception {
        assertTrue(Files.readString(Path.of("pom.xml")).contains("purpur-api"));
    }
}
```

> Diese fünf Tests sind billig, schnell und fangen genau den Rückfall ab, der bei einer solchen
> Migration am wahrscheinlichsten ist.

### P5.7 — Testgruppe F: Konfigurationsdateien validieren

**Neue Datei:** `src/test/java/de/zfzfg/config/ResourceConfigTest.java`

Lädt **jede** mitgelieferte YAML aus `src/main/resources/` mit `YamlConfiguration.loadConfiguration`
und prüft:

1. Jede Datei ist syntaktisch gültiges YAML (kein leeres Ergebnis bei nicht-leerer Datei).
2. **Alle 7 `messages_*.yml` haben denselben Schlüsselsatz.** Das fängt fehlende Übersetzungen ab
   — bei 7 Sprachen ist das eine reale, dauerhafte Fehlerquelle.
3. Kein Nachrichtenwert enthält `§` (muss `&` sein, sonst greift der Serializer nicht).
4. `config.yml`, `equipment.yml`, `worlds.yml`, `web-config.yml` laden fehlerfrei.
5. Jeder in `plugin.yml` deklarierte Command hat eine `description`.

Als parametrisierter JUnit-5-Test (`@ParameterizedTest` + `@ValueSource`) über die Dateiliste.

> **Erwartung:** Test 2 wird beim ersten Lauf vermutlich **rot**. Das ist ein echter Fund, kein
> Testfehler. Dann: die fehlenden Schlüssel auflisten, in `MIGRATION_NOTES.md` unter
> `## Offene Punkte für den Menschen` dokumentieren und den Test zunächst auf die *deutsche und
> englische* Datei beschränken (die beiden gepflegten). Sprachdateien **nicht** eigenmächtig
> übersetzen.

### P5.8 — Testgruppe G: Nebenläufigkeit

Für die Klassen mit `ConcurrentHashMap`/`synchronized` (91 bzw. 37 Fundstellen) je einen Test, der
mit `ExecutorService` und `CountDownLatch` gleichzeitig zugreift und auf Konsistenz prüft.
Priorität: `SlidingWindowLimiter`, `CommandCooldownManager`, `Text` (Cache).

Beispielmuster:
```java
@Test
void cacheIstThreadSicher() throws Exception {
    ExecutorService pool = Executors.newFixedThreadPool(8);
    CountDownLatch start = new CountDownLatch(1);
    List<Future<Component>> futures = new ArrayList<>();
    for (int i = 0; i < 200; i++) {
        futures.add(pool.submit(() -> { start.await(); return Text.of("&aParallel"); }));
    }
    start.countDown();
    Component erste = futures.get(0).get();
    for (Future<Component> f : futures) assertEquals(erste, f.get());
    pool.shutdown();
}
```

### P5.9 — Optional: MockBukkit-Integrationstests

**Nur ausführen, wenn P5.1 erfolgreich war.** Sonst überspringen.

Dann: Plugin-Enable-Test (lädt das Plugin in einer Server-Simulation), Command-Ausführungstest für
die Haupt-Commands, Listener-Test mit simulierten Events.

### P5.10 — Testabdeckung messen und berichten

JaCoCo in die `pom.xml` aufnehmen:

```xml
<plugin>
  <groupId>org.jacoco</groupId>
  <artifactId>jacoco-maven-plugin</artifactId>
  <version>0.8.12</version>
  <executions>
    <execution><goals><goal>prepare-agent</goal></goals></execution>
    <execution><id>report</id><phase>test</phase><goals><goal>report</goal></goals></execution>
  </executions>
</plugin>
```

```bash
mvn -o clean test   # benoetigt evtl. Netzwerk fuer JaCoCo beim ersten Mal
```

Bericht: `target/site/jacoco/index.html`. Die Gesamtabdeckung und die Abdeckung der in Phase 2/3
geänderten Klassen in `MIGRATION_NOTES.md` notieren.

> **Falls JaCoCo nicht offline verfügbar ist:** überspringen, notieren. Die Tests sind der Wert,
> nicht die Prozentzahl.

**Ziel für diese Phase:** Alle neuen und alten Tests grün, Anzahl Testklassen deutlich über 12.
Eine konkrete Prozentzahl wird hier **bewusst nicht** vorgegeben — eine erzwungene Coverage-Zahl
produziert wertlose Tests.

---

## Phase 6 — Verifikation auf dem echten Server

> **Ziel:** Beweisen, dass es tatsächlich läuft. Ohne diese Phase ist die Migration nicht fertig —
> Kompilieren und Unit-Tests sagen **nichts** darüber aus, ob eine Klick-Nachricht im Chat
> ankommt.

### P6.1 — Server sichern und Plugin deployen

```bash
# Server MUSS gestoppt sein.
cd "/c/Users/zfzfg/Documents/servers/purpur-26-2/plugins"
cp event-pvp-plugin-1.0.9.jar "event-pvp-plugin-1.0.9.jar.bak-$(date +%Y%m%d)"
cp -r "Event-PVP-Plugin" "Event-PVP-Plugin.bak-$(date +%Y%m%d)"
```

Dann das neue JAR bauen und kopieren:
```bash
cd "<PROJEKT>" && mvn -o clean package
cp target/event-pvp-plugin-1.0.9.jar "/c/Users/zfzfg/Documents/servers/purpur-26-2/plugins/"
```

**Vor dem Kopieren zwingend die Shade-Prüfung aus P1.5 ausführen** (`net/kyori` und `org/bukkit`
müssen 0 ergeben).

**STOPP:** Der Server darf nur gestartet werden, wenn Backup **und** Shade-Prüfung erledigt sind.

### P6.2 — Serverstart und Log-Prüfung

Server starten (`start.bat`) und das frische Log prüfen:

```bash
cd "/c/Users/zfzfg/Documents/servers/purpur-26-2/logs"
grep -iE "Event-PVP|ERROR|WARN|Exception|NoClassDefFound|NoSuchMethod" latest.log | head -60
```

**Muss zu sehen sein** (wie im Baseline-Log vom 12.08.):
```
[Event-PVP-Plugin] Enabling Event-PVP-Plugin v1.0.9
[Event-PVP-Plugin] Loaded messages for language 'en'
[Event-PVP-Plugin] Loaded event: pvparena ... (7 Events)
[Event-PVP-Plugin] Loaded equipment set: ... (7 Sets)
[Event-PVP-Plugin] Inventory provider: InventoryBackup (API v1).
```

**Darf NICHT zu sehen sein:** `NoClassDefFoundError`, `NoSuchMethodError`, `Unsupported api-version`,
irgendein Stacktrace mit `de.zfzfg`.

**Bei `NoClassDefFoundError: net/md_5/...`** → eine Sendestelle wurde übersehen. Zurück zu P2.12.

### P6.3 — Funktionale Checkliste (manuell im Spiel)

Diese Punkte **müssen** einzeln geprüft werden. Sie decken exakt die in Phase 2 geänderten Pfade ab —
Unit-Tests können das nicht.

| # | Aktion | Erwartetes Ergebnis |
|---|---|---|
| 1 | `/pvpask <Spieler>` | Ziel erhält Chat-Nachricht **mit** Buttons |
| 2 | Maus über den Annehmen-Button | Hover-Text erscheint, korrekt gefärbt |
| 3 | Annehmen-Button anklicken | Befehl wird ausgeführt, GUI öffnet |
| 4 | Ablehnen-Button anklicken | Anfrage wird abgelehnt |
| 5 | `/pvpa <Spieler> ...` (Voll-Config) | Klickbare Antwort kommt an |
| 6 | Event starten, Join-Broadcast | Join-Button erscheint und funktioniert |
| 7 | Event-Countdown ansehen | Titel 3/2/1 erscheinen, richtige Anzeigedauer |
| 8 | Event-Start-Titel | Titel + Untertitel korrekt, richtig gefärbt |
| 9 | Web-Token-Befehl | Token- und URL-Nachricht klickbar, URL öffnet |
| 10 | Equipment-Set anlegen lassen | **Verzauberungen sind vorhanden** (P3.3!) |
| 11 | Item-Namen im Equipment | **nicht kursiv**, Farben korrekt |
| 12 | Beliebige Fehlermeldung provozieren | rot, lesbar, keine `§`-Artefakte |
| 13 | Mehrzeilige Nachricht (z. B. Hilfe) | Zeilenumbrüche korrekt |
| 14 | Web-UI aufrufen | lädt, TPS-Anzeige zeigt plausiblen Wert (P3.6!) |
| 15 | Match komplett durchspielen | Payout korrekt, Inventar wiederhergestellt |

Punkt 10, 11 und 14 sind die drei, die aus Phase 3 stammen und dort am ehesten schiefgehen.

Ergebnis je Punkt (OK / FEHLER + Beschreibung) in `MIGRATION_NOTES.md` unter
`## P6.3 Funktionstest` protokollieren.

### P6.4 — `spark`-Profiling (Vorher/Nachher)

Das `spark`-Plugin ist auf dem Server bereits vorhanden.

```
/spark profiler start --timeout 300
   ... 5 Minuten normale Nutzung, idealerweise mit laufendem Event ...
/spark profiler stop
```

Den Report-Link speichern, den Anteil von `de.zfzfg.*` an der Server-Tick-Zeit notieren.
Wenn möglich, mit einem Lauf **vor** der Migration vergleichen. Falls kein Vorher-Wert
existiert: den Nachher-Wert als neue Baseline dokumentieren.

### P6.5 — Bewegungs-Listener verifizieren (aus P4.2)

Falls in P4.2 `hasChangedBlock()` ergänzt wurde: prüfen, dass die betroffenen Funktionen
weiterhin auslösen — insbesondere Arena-Grenzen und Void-Schutz
(`VoidProtectionListener.java`). **Konkret:** In die Arena-Grenze hineinlaufen und den Void
testen. Ein zu aggressiver Early-Return würde genau diese Schutzfunktionen still abschalten.

---

## Phase 7 — Abschluss

### P7.1 — Vollständiger sauberer Durchlauf

```bash
mvn -o clean test package
```
Muss von Null grün durchlaufen.

### P7.2 — Version und Changelog

- `pom.xml`: `<version>1.1.0</version>` (Breaking-Change-Charakter: neue Mindest-Serverversion).
- Neue Datei `CHANGELOG_1.1.0.md` mit den Abschnitten:
  - **Breaking:** Benötigt jetzt Purpur/Paper 26.2+ (vorher 1.19+)
  - **Breaking:** BungeeCord-Chat-API vollständig entfernt, Chat läuft über Adventure
  - **Behoben:** Verzauberungen aus `equipment.yml` mit modernen Namen (P3.3)
  - **Behoben:** TPS-Anzeige im Web-UI ohne Reflection (P3.6)
  - **Intern:** Java 21, `api-version 26.2`, Legacy-Material-Pfad abgeschaltet
  - **Tests:** von 12 auf N Testklassen

### P7.3 — Abschlussbericht an den Menschen

In `MIGRATION_NOTES.md` und in der Antwort:

1. Was geändert wurde (Phasen, Dateien, Zeilen)
2. Was **nicht** geändert wurde und warum (ChatColor, Folia, ggf. MockBukkit)
3. Testzahlen vorher/nachher
4. Ergebnis der Checkliste P6.3, mit jedem FEHLER einzeln
5. Alle `## BLOCKIERT`-Einträge
6. Alle `## Offene Punkte für den Menschen` (z. B. fehlende Übersetzungsschlüssel aus P5.7)

**Nicht beschönigen.** Wenn etwas nicht geprüft wurde, muss dastehen, dass es nicht geprüft wurde.

---

## Anhang A — Nachschlage-Kommandos

**API-JAR-Pfad (immer diesen verwenden):**
```bash
API_JAR="C:/Users/zfzfg/.m2/repository/org/purpurmc/purpur/purpur-api/26.2.build.2618-stable/purpur-api-26.2.build.2618-stable.jar"
JAVAP="/c/Program Files/Java/jdk-26.0.1/bin/javap.exe"
```
> `javap` liegt **nicht** im PATH dieser Git-Bash — immer den vollen Pfad benutzen.

**Signatur einer Klasse prüfen:**
```bash
"$JAVAP" -cp "$API_JAR" org.bukkit.entity.Player | grep -i "<SUCHWORT>"
```

**Adventure-Klasse prüfen:**
```bash
ADV="C:/Users/zfzfg/.m2/repository/net/kyori/adventure-api/5.2.0/adventure-api-5.2.0.jar"
"$JAVAP" -cp "$ADV" net.kyori.adventure.text.event.ClickEvent
```

**Prüfen, ob eine Klasse überhaupt existiert:**
```bash
unzip -l "$API_JAR" | grep "<Pfad/Zur/Klasse>"
```

**Offline bauen (immer `-o` benutzen, alle Deps sind lokal):**
```bash
mvn -o clean test-compile
mvn -o test
mvn -o clean package
```

---

## Anhang B — Fehlerkatalog

| Symptom | Ursache | Lösung |
|---|---|---|
| `cannot find symbol: ClickEvent.Action.RUN_COMMAND` | Adventure-4.x-Syntax auf 5.x | `ClickEvent.runCommand(cmd)` (P2.0 Regel A) |
| Nachricht kommt an, Klick tut nichts | `.clickEvent(...)`-Ergebnis nicht zugewiesen (immutable!) | Zuweisung ergänzen (P2.4) |
| `NoClassDefFoundError: net/md_5/...` zur Laufzeit | Sendestelle übersehen | P2.12-Scan wiederholen |
| `ClassCastException` bei `Component` | Adventure wurde ins JAR geshaded | P1.5-Prüfung, `artifactSet`-Excludes |
| Item-Namen plötzlich kursiv | `Text.of` statt `Text.ofItem` bei ItemMeta | `Text.ofItem` (P3.1) |
| `Unsupported api-version` beim Start | falscher Wert in `plugin.yml` | `'26.2'` (P1.6) |
| Verzauberungen fehlen im Equipment | `Enchantment.getByName` mit Alt-Namen | P3.3 |
| Mockito: `Java agent loaded dynamically` | Java 21+ | `-XX:+EnableDynamicAgentLoading` (P1.7) |
| Farbcodes doppelt geparst / `§`-Artefakte | `MessageUtil.color()` **und** `Text.of()` auf demselben String | Nur `Text.of()` — es parst selbst |
| `distance`-Vergleich verhält sich falsch | `distanceSquared` ohne Radius zu quadrieren | `r * r` (P4.4) |
| Web-Handler wirft „not on main thread" | P4.5 ohne Scheduler-Rückführung | P4.5 zurückrollen, blockieren |

---

## Anhang C — Abnahmekriterien

Die Migration gilt als abgeschlossen, wenn **alle** Punkte erfüllt sind:

- [ ] `grep -rn "md_5\|spigot()" src/ pom.xml` ist leer
- [ ] `pom.xml` nutzt `purpur-api:26.2.build.2618-stable`, Java 21, keine `bungeecord-chat`
- [ ] `plugin.yml` hat `api-version: '26.2'`
- [ ] `unzip -l target/*.jar | grep -c "net/kyori"` ergibt `0`
- [ ] `mvn -o clean test package` läuft von Null grün durch
- [ ] Deutlich mehr als 12 Testklassen, alle grün
- [ ] Server startet ohne Stacktrace, Log zeigt alle 7 Events und 7 Equipment-Sets
- [ ] Checkliste P6.3: alle 15 Punkte protokolliert, keine offenen FEHLER
- [ ] `MIGRATION_NOTES.md` vollständig ausgefüllt
- [ ] `CHANGELOG_1.1.0.md` existiert
