# Event-PVP Web Interface – Anleitung

## Übersicht

Das Event-PVP Web Interface bietet eine intuitive, grafische Konfigurationsoberfläche für alle relevanten YAML-Dateien des Plugins.

### Verfügbare Konfigurationen
- **config.yml** – Allgemeine Einstellungen, Events, Match-Einstellungen, externe Integrationen
- **worlds.yml** – Welten und PvP-Arenen mit Spawn-Konfigurationen
- **equipment.yml** – Ausrüstungs-Sets mit Armor und Inventory
- **web-config.yml** – Web-Server Einstellungen und Theme-Farben

---

## Zugriff auf das Interface

### Token-basierte Authentifizierung
Das Interface nutzt ein sicheres Token-System für den Zugriff:
1. Führe im Spiel `/eventpvp webtoken` aus
2. Du erhältst einen einmaligen Token und die URL zum Interface
3. Öffne die URL im Browser
4. Gib den Token ein (oder klicke den Link in der Chat-Nachricht)
5. Du bist eingeloggt!

### URL
Standardmäßig: `http://localhost:8085`
Der Port kann in `web-config.yml` angepasst werden.

### Konfiguration der Authentifizierung
In `web-config.yml` unter `security`:
- `auth-enabled`: Aktiviert/deaktiviert die Authentifizierung (Standard: true)
- `token-validity-minutes`: Wie lange ein Token gültig ist (Standard: 10)
- `session-validity-minutes`: Wie lange eine Session gültig bleibt (Standard: 60)
- `required-permission`: Erforderliche Permission (Standard: eventpvp.admin.web)

---

## Bedienung

### Navigation (Sidebar)
Die Sidebar ist in zwei Gruppen unterteilt:

**Konfiguration**

| Icon | Bereich | Funktion |
|------|---------|----------|
| ⚙️ | Allgemeine Einstellungen | Alltägliche Plugin-Konfiguration, Sprache, Wett-Limits, Integrationen |
| 📅 | Events | Event-Definitionen verwalten |
| 🌍 | Welten & Arenen | PvP-Welten konfigurieren, Server-Welten verwalten |
| 📦 | Inventare & Backups | Spieler-Backups ansehen, Canvas-Viewer, Guard-Sitzungen, Provider-Steuerung |
| 🛡️ | Equipment-Sets | Rüstungs- und Inventar-Konfigurationen mit Live-Materialien |

**Erweitert**

| Icon | Bereich | Funktion |
|------|---------|----------|
| 🔧 | Experten-Einstellungen | Selten benötigte, riskante oder infrastrukturelle Optionen |
| 🎨 | Theme & Farben | Web-Interface Anpassung |

> **YAML Preview** ist kein Sidebar-Eintrag, sondern ein Button in der oberen
> Leiste (📄) — dort liegen auch Server-Reload, Undo/Redo, Import, Export und
> Speichern.

### Basis oder Experte — nach welchem Kriterium? (Neu in 1.0.9)

Aufgeteilt wird nach **Risiko, nicht nach Häufigkeit**:

- **Allgemeine Einstellungen** enthält alles, was man braucht, um Events und
  Welten *korrekt einzurichten* — auch wenn man es nur einmal anfasst.
- **Experten-Einstellungen** enthält alles, dessen Änderung zerstörerisch,
  experimentell oder infrastrukturell ist und wo der Standardwert fast immer
  richtig ist. Faustregel: Wenn ein Fehlklick hier Daten zerstört, den Zugang
  abschneidet oder überraschendes Verhalten auslöst, gehört es in den
  Experten-Bereich.

Besonders riskante Schalter tragen zusätzlich eine orange ⚠-Warnung direkt am
Feld.

### Statusanzahl (Badges)
Neben Events, Welten und Equipment-Sets werden die Anzahl der konfigurierten Einträge angezeigt.

---

## Funktionen

### 1. Allgemeine Einstellungen
Die alltägliche Konfiguration, aufgeteilt in sechs Karten:

- **Basis-Einstellungen** – Prefix (mit Farbcodes: `&6[Event]&r`), Hauptwelt,
  Sprache, Beitrittsphase, Lobby-Countdown
- **Zuschauer-System** – Erlaubt Spielern, Matches zuzuschauen
- **Match-Einstellungen** – PvP-Match Countdown, Maximaldauer, Kampf ohne Einsatz
- **Auto-Event-System** – Startet Events automatisch in Intervallen
- **Wetten & Limits** – Minimaler und maximaler Geldeinsatz
- **Externe Integrationen** – AJLeaderboards, DecentHolograms, PvPManager

**Sprache:** 7 Sprachen stehen zur Wahl — Deutsch, Englisch, Spanisch,
Französisch, Japanisch, Polnisch, Russisch. Die Auswahl wird in `config.yml`
unter `settings.language` gespeichert und gilt serverweit.

#### Integrationen (Neu in 1.0.8)
- **AJLeaderboards** – Zeige Event- und PvP-Statistiken in Leaderboards
- **DecentHolograms** – Zeige Statistiken in Hologrammen
- **PvPManager** – Entferne Combat-Tags automatisch bei Event- oder Match-Ende

> Der **Update-Check** wird **nicht** im Web-Interface konfiguriert. Er liegt in
> `config.yml` unter `settings.update-check` und muss dort direkt bearbeitet
> werden. Den aktuellen Stand fragst du im Spiel mit `/eventpvp version` ab —
> siehe `UPDATE_CHECK_CONCEPT.md`.

### 1b. Experten-Einstellungen (Neu in 1.0.9)

Karten hinter einem Warnbanner für fortgeschrittene Server-Konfiguration:

- **Inventar-Verwaltung** – Modus-Auswahl (`auto` = InventoryBackup empfohlen, `inventoryrestore` = explizit, `none` = Legacy Multiverse-Inventories), Schalter für Sicherheitskopien im Legacy-Modus, automatische Wiederherstellung (nach Match-Ende, Event-Ende, Respawn, Rejoin), Fehlerverhalten bei fehlgeschlagener Sicherung (`abort`/`warn`), Archivierung vs. Bereinigung sowie Überwachung und Aktualisierung offener Guard-Sitzungen.
- **Spielerdaten-Sicherung** – **Spielerposition speichern** (`save-player-location`). Trägt eine ⚠-Warnung: abgeschaltet werden Positionen vor Events/Matches nicht gesichert. Das Abschalten ist ausdrücklich experimentell.
- **Weltverwaltung** – Multiverse-Weltenverwaltung für Events (`world-management.events`) und Arenen (`world-management.arenas`) sowie Befehlssperre bei Events (`command-restriction: both|event|lobby|none`).
- **Arena-Regeneration** – Backups vor Regeneration erstellen, asynchrones Backup (`arena-regeneration.backups`, `arena-regeneration.backup-async`).
- **Sicherheitschecks** – Inventarplatz-Prüfung vor Wetteinsätzen (`checks.inventory-space`).
- **Performance** – Aktualisierungsintervall der Integrationen in Ticks (`integrations.refresh-interval-ticks`).
- **Webserver-Einstellungen** – Port und Browser-Titel. Ein falscher Port sperrt dich aus dem Panel aus; Portänderungen greifen erst nach einem Server-Neustart.

### 2. Events
Hier können neue Event-Typen erstellt oder bearbeitet werden.

**Event-Editor Tabs (8):**
- **Basis** – Name, Befehl, Min/Max Spieler, Status
- **Welten** – Lobby- und Event-Welt
- **Spawns** – Spawn-Mechanismen (Single, Random, Team, etc.)
- **Equipment** – Equipment-Gruppe und Lobby-Einstellungen
- **Mechaniken** – Spielmodus (SOLO / TEAM_2 / TEAM_3), Siegbedingung samt
  ihrer Optionen, PvP, Hunger, Friendly Fire
- **Nachrichten** – Event-Messages (Start, Winner, Eliminated, Objective)
- **Belohnungen** – Winner-, Team-Winner- und Teilnahme-Belohnungen
- **🔧 Experte** – Event-Welt regenerieren ⚠ und Klon-Quelle. Die Regeneration
  überschreibt die Welt mit der Klon-Quelle; bestehende Bauten gehen verloren.

**Messages-Tab:**
Editiere Event-spezifische Nachrichten mit Minecraft-Farbcodes (`&a`, `&e`, `&l`, etc.)

**Button-Funktionen:**
- ➕ **Neues Event** – Erstellt einen neuen Event
- ✏️ **Bearbeiten** – Öffnet den Event-Editor
- 🗑️ **Löschen** – Entfernt einen Event

### 3. Welten & Arenen
Verwaltet alle PvP-Welten und Event-Arenen.

**Welt-Editor Tabs (5):**
- **Basis** – Welt-ID, Anzeigename, PvP-Welt aktivieren, Bauen erlaubt
- **Spawns** – Spawn-Typ und Positionen
- **Einstellungen** – erlaubte Equipment-Gruppen
- **🌍 Multiverse** *(neu in 1.0.9)* – Weltstatus, Welt auf dem Server erstellen, laden/entladen, löschen
- **🔧 Experte** – Welt regenerieren ⚠ und Klon-Quelle

#### Welt-ID auswählen (neu in 1.0.9)
Beim Anlegen einer neuen Welt ist die Welt-ID kein Freitextfeld mehr, sondern ein Dropdown der
Welten, die auf dem Server **tatsächlich existieren**. Jeder Eintrag zeigt Environment, Ladezustand
und wer die Welt bereits benutzt:

```
arena_1     — NORMAL · Geladen  · bereits von einem Preset belegt   (gesperrt)
nether_pit  — NETHER · Entladen
EventLobby  — NORMAL · Geladen  · Genutzt von: pvparena (lobby-world)
──────────────
✎ Eigene Welt-ID eingeben…
```

Welten, die schon Schlüssel eines Presets sind, werden angezeigt, aber gesperrt: der Preset-Schlüssel
*ist* der Weltname, ein zweites Preset würde das erste überschreiben.

Über **„Eigene Welt-ID eingeben…"** legst du wie bisher ein Preset ohne zugehörige Welt an — die
Karte kennzeichnet es dann als ⚪ *Platzhalter*.

#### Weltstatus in der Übersicht
Jede Weltkarte zeigt den Zustand der zugehörigen Server-Welt:

| Badge | Bedeutung | Aktion |
|---|---|---|
| 🟢 Geladen | Welt existiert und ist aktiv | *Entladen* |
| 🟡 Entladen | Welt existiert, ist aber nicht geladen | *Laden* |
| ⚪ Platzhalter | Nur Preset, keine Welt auf dem Server | *Welt erstellen* |

Darüber listet das aufklappbare Panel **Server-Welten** *alle* Multiverse-Welten auf — auch die ohne
Preset — samt Belegung und Laden/Entladen-Knöpfen.

#### Welt über das Panel erstellen (neu in 1.0.9)
Im Tab **Multiverse** legst du die Welt direkt auf dem Server an. Alle Einstellungen sind optional:

| Einstellung | Werte | Hinweis |
|---|---|---|
| Environment | `NORMAL`, `NETHER`, `THE_END` | |
| Welt-Typ | `NORMAL`, `FLAT`, `LARGE_BIOMES`, `AMPLIFIED` | |
| Seed | Text oder Zahl | leer = zufällig |
| Generator | `Plugin` oder `Plugin:id` | z.B. ein Void-Generator |
| Generator-Einstellungen | JSON | nur mit Multiverse-Core 5 |
| Biom | Biom-Name | Single-Biome-Welt, nur mit Multiverse-Core 5 |
| Strukturen generieren | an/aus | Dörfer, Tempel, Festungen |
| Spawn anpassen | an/aus | Multiverse sucht einen sicheren Spawn |

Das Erstellen läuft im Hintergrund weiter und das Panel zeigt den Fortschritt — auch große Welten
laufen so nicht in einen Timeout.

Ohne Multiverse-Core zeigt der Tab einen Hinweis und die Weltauswahl funktioniert weiterhin; nur die
Knöpfe zum Erstellen, Laden und Entladen entfallen.

#### Welt oder Preset löschen (neu in 1.0.9)
Ein Preset zu löschen ist eine YAML-Änderung, eine Welt zu löschen nicht — deshalb sind beide im
Dialog getrennt:

- Die Checkbox **„Auch die Welt auf dem Server löschen"** ist **standardmäßig aus** und mit einer
  roten Warnung versehen.
- Wird sie angehakt, erscheint **„Vorher ein Backup erstellen"** (**standardmäßig an**, zippt nach
  `plugins/<plugin>/backups/`) und ein Bestätigungsfeld: der Löschen-Knopf bleibt gesperrt, bis die
  Welt-ID abgetippt wurde.
- Im Welt-Editor gibt es zusätzlich **„Nur Welt löschen"** für den umgekehrten Fall — Welt weg,
  Preset bleibt als Platzhalter stehen.

Die Hauptwelt des Servers lässt sich auf diesem Weg weder entladen noch löschen.

Lässt sich das Backup nicht schreiben, **wird die Welt nicht gelöscht** — das Panel nennt den Grund,
statt dich ohne Welt *und* ohne Sicherung zurückzulassen.

#### Backups wiederherstellen (neu in 1.0.9)
Unter dem Server-Welten-Panel liegt das aufklappbare Panel **„Backup-Welten"**. Es listet alle
Zips aus `plugins/<plugin>/backups/` mit Weltname, Datum und Größe.

- **Wiederherstellen**: Ein Dialog fragt nach dem Weltnamen — vorbelegt mit dem Original,
  änderbar (z.B. `arena_1_restored`). Eine existierende Welt wird **nie überschrieben**; in dem
  Fall wählst du einfach einen anderen Namen. Die Wiederherstellung läuft im Hintergrund und
  die Welt wird danach über Multiverse importiert und geladen.
- **Backup löschen**: Entfernt nach einer Bestätigung nur die Zip-Datei — niemals eine Welt.

Löschst du eine Welt mit aktivierter Backup-Option, taucht das frische Backup sofort in diesem
Panel auf — von dort ist sie jederzeit wiederherstellbar.

#### Wenn der Weltstatus nicht gelesen werden kann
Kann das Panel den Weltbestand gerade nicht abfragen (z.B. weil der Server nach einem Unload noch
speichert), zeigt es den zuletzt bekannten Stand mit dem Hinweis **„Weltstatus konnte nicht
aktualisiert werden"** und einem *Erneut versuchen*-Knopf. Erstellen und Löschen sind dann gesperrt.
Der Status-Badge steht in diesem Fall auf *Status unbekannt* — er behauptet bewusst nicht
„Platzhalter", denn ob die Welt existiert, ist in dem Moment schlicht nicht bekannt.

**Spawn-Typen für PvP:**
- `FIXED_SPAWNS` – Feste Positionen für Player1, Player2, Spectator
- `RANDOM_RADIUS` – Zufällig innerhalb Radius
- `RANDOM_AREA` – Zufällig in rechteckiger Fläche
- `RANDOM_CUBE` – Zufällig in 3D-Box
- `COMMAND` – Spawn via Befehl

### 4. Inventare & Backups (Neu in 1.0.9)
Bietet eine vollständige Verwaltung aller Spieler-Inventare und Backups mit einer modernen 3-Tab-Architektur:

#### Tab 1: Backup Explorer (Split-View & Minecraft Canvas)
- **Spielersuche**: Dropdown-Vorschläge mit Online-Spielern und kürzlich gesuchten Spielern (inkl. 1-Klick-Pills).
- **Filterbare Backup-Liste**: Filterung nach Match-Typen (`pvp_match`, `event`, `manual`) und Anzeige der exakten Snapshot-Zeiten.
- **Interaktiver Canvas-Viewer**:
  - Originalgetreue Minecraft-Darstellung mit Rüstungs-Slots, Offhand, Hauptinventar und Hotbar.
  - XP-Leiste mit Füllstand und Level.
  - Echte 64x64 PNG-Texturen und animierte Tooltips mit Minecraft-Farbcodes und Verzauberungen.
- **Wiederherstellung (2-Stufen-Modal)**:
  - Sicherheitsabfrage mit Namenseingabe.
  - Schalter **„Zuvor Inventar leeren“**: Ausgeschaltet führt dies zu einem nicht-destruktiven *Givemissing*-Restore (fehlende Items werden dem aktuellen Inventar hinzugefügt).
  - Rate Limiting: Maximal 10 Restores/Minute zum Schutz vor Duplikations-Exploits.
- **Ausrüstungsset-Export**: Wandelt ein ausgewähltes Backup mit einem Klick in ein neues Equipment-Set in `equipment.yml` um.
- **Raw-JSON**: Kopiert die Rohdaten des Snapshots in die Zwischenablage.

#### Tab 2: Live-Sitzungen & Guard
- Überwacht aktive `InventoryGuard`-Sitzungen in Echtzeit mit Kontext (`PVP_MATCH`, `EVENT`) und Phase (`ACTIVE`, `RETURNING`, `ORPHANED`).
- Zeigt gesicherte Rückkehr-Koordinaten aus `player-return-locations.yml` an.

#### Tab 3: Einstellungen & Richtlinien
- Schnellumschaltung des Inventory-Providers (`auto` / `none`).
- Status- und Warnmeldungen bei Koexistenz mit Multiverse-Inventories.

### 5. Equipment-Sets
Erstellt Rüstungs- und Inventar-Konfigurationen.

**Eigenschaften:**
- Anzeigename und Set-ID
- Getrennte Aktivierungsschalter (`pvpwager-equip-enable` / `event-equip-enable`)
- Allowed PvP Worlds (in welchen Welten nutzbar: `all`, `none` oder Liste)
- Armor Slots (Helm, Chestplate, Leggings, Boots)
- Inventory Items mit Slot, Menge, Lore, Unbreakable und Custom-Model-Data

**Dynamischer Item-Katalog (`/api/materials`):**
Klicke auf einen Item-Slot und wähle aus dem dynamischen Katalog. Das Panel bezieht alle verfügbaren Items, Stapelgrößen und Verzauberungen direkt vom laufenden Server.

### 6. Theme & Farben
Passt das Aussehen des Web-Interface an.

**Farboptionen:**
- **Primärfarbe** – Akzentfarbe (Standard: `#4caf50` Grün)
- **Sekundärfarbe** – Zusätzliche Akzentfarbe
- **Hintergrund** – Dunkler Hintergrund (Standard: `#1a1a1a`)
- **Oberfläche** – Panel-Hintergrund
- **Karten** – Card-Elemente
- **Textfarbe** – Haupt-Textfarbe
- Und weitere Farben (Error, Warning, Success, Info)

**Eingabemethoden:**
- Klicke auf die Farbbox, um einen Farbwähler zu öffnen
- Oder gib direkt einen HEX-Wert ein (z.B. `#4caf50`)

Über **↶ Standard wiederherstellen** setzt du alle Farben auf die Vorgabewerte
zurück. Port und Browser-Titel liegen seit 1.0.9 nicht mehr hier, sondern unter
**Experten-Einstellungen**.

---

## Änderungen & Speichern

### Live-Synchronisation & Status-Badge (Neu in 1.0.9)
Das Interface verfolgt den genauen Synchronisationsstand mit dem Minecraft-Server in Echtzeit über ein farbiges Status-Badge in der oberen Leiste:
- 🟢 **Mit Server synchronisiert** (`sync.synced`) – Der Stand im Web-Editor entspricht exakt der gespeicherten Server-Konfiguration.
- 🟡 **{X} ungespeicherte Änderung(en)** (`sync.unsaved`) – Lokale Änderungen im Editor vorhanden, die noch nicht auf dem Server gespeichert wurden.
- 🔵 **Wird gespeichert...** (`sync.saving`) – Aktive Übertragung und Speicherung auf dem Server.
- 🔴 **Nicht synchronisiert / Offline** (`sync.error`) – Verbindung zum Server unterbrochen.

### Speichern
Klicke **💾 Speichern (X)** im Header, um alle Änderungen auf einmal auf den Server zu schreiben:
- config.yml
- worlds.yml
- equipment.yml
- web-config.yml

### Undo / Redo
- **↶ Undo** – Letzte Änderung im Web-Editor rückgängig machen
- **↷ Redo** – Wiederherstellen

### Werkzeuge-Menü (Tools Dropdown)
Alle Hilfsfunktionen sind übersichtlich unter **🛠 Werkzeuge ▾** zusammengefasst:
- **📄 YAML-Vorschau** – Zeigt die generierten YAML-Dateien an (`config.yml`, `worlds.yml`, `equipment.yml`) inklusive Kopierfunktion.
- **📥 Konfiguration importieren** – Lädt eine exportierte JSON-Konfiguration in den Editor.
- **📤 Konfiguration exportieren** – Sichert den aktuellen Editor-Stand als JSON-Datei.
- **🔄 Vom Server neu laden** – Lädt den aktuellen Stand direkt vom Server (verwirft ungespeicherte Web-Änderungen).

### Server Reload
Klicke auf **🔄 Server Reload**:
- Führt `/eventpvp reload` auf dem Minecraft-Server aus
- Liest alle Konfigurationsdateien auf dem Server neu ein

### Event-Erstellung: Lobby-Phase & Welten-Validierung (Neu in 1.0.9)
- **Welt-Auswahl**: Felder starten standardmäßig leer (`-- Welt auswählen... --`).
- **Validierungs-Warnung**: Wird keine Event-Welt gewählt, weist ein deutliches Warnbanner darauf hin.
- **Schaltbare Lobby-Phase (`use-lobby`)**:
  - *Aktiviert*: Vor Event-Start sammeln sich Spieler in der konfigurierten Lobby-Welt. Fehlt die Lobby-Welt, warnt der Editor.
  - *Deaktiviert*: Spieler treten dem Event sofort bei und werden direkt auf die Spawns der Event-Welt teleportiert. Keine Lobby-Welt erforderlich.
- **Regenerations-Sperre**:
  - `regenerate-event-world` steht beim Erstellen eines neuen Events standardmäßig auf `false`.
  - Hat die gewählte Welt in den Welteneinstellungen bereits `regenerate-world: true` aktiv, wird der Schalter im Event-Editor automatisch gesperrt und mit einem Hinweis versehen, um doppelte und redundante Welt-Resets zu verhindern.

---

## Best Practices

### 1. Immer Speichern!
Nach Änderungen **immer** auf **Speichern** klicken, sonst gehen die Änderungen verloren.

### 2. Backup vor großen Änderungen
Nutze **Export**, um die aktuelle Konfiguration zu sichern.

### 3. Koordinaten-Editor
Bei Koordinaten-Eingaben:
- **X, Y, Z** – Position im Spiel
- **Yaw, Pitch** – Blickrichtung (Yaw: 0°=Osten, 90°=Süden, etc.)

### 4. Item-Namen
Bei der Item-Auswahl:
- Alle Namen in UPPERCASE (z.B. `DIAMOND_SWORD`)
- Namen können manuell editiert werden, wenn nicht in der Liste

### 5. Welten-Verwaltung
- **clone-source-event-world** / **clone-source-world** – Template-Welt, aus der die Event- bzw. Arena-Welt geklont wird (empfohlen für handgebaute Maps & Arenen).
- **regenerate-event-world** – Setzt die Event-Welt nach/vor dem Event via Multiverse (`/mv regen`) auf den Seed zurück (Default: `false` – Achtung: überschreibt handgebaute Bauten!).
- **regenerate-world** – Setzt die PvP-Wager Arena-Welt nach 1v1-Matches via Multiverse (`/mv regen`) zurück (Default: `false`).
- **Welt löschen** – Das Löschen einer Welt ist unwiderruflich. Lass die Backup-Option an, wenn du
  dir nicht ganz sicher bist; das Zip liegt danach unter `plugins/<plugin>/backups/`.
- **Platzhalter sind erlaubt** – Ein Preset ohne Welt (⚪) ist kein Fehler. Es ist dann aber auch
  keine Welt da, in die ein Event teleportieren könnte: erstelle sie vor dem ersten Start über den
  Multiverse-Tab oder trage eine Klon-Quelle ein.

---

## Fehlerbehebung

### Änderungen werden nicht gespeichert
1. Überprüfe, dass der **Speichern**-Button gedrückt wurde
2. Schaue in die Browser-Konsole (F12) für Fehler
3. Überprüfe, dass der Plugin-Ordner schreibbar ist

### Web-Interface nicht erreichbar
1. Überprüfe Port in `web-config.yml` (Standard: 8085)
2. Firewall-Einstellungen prüfen
3. Server-Logs prüfen auf Fehler

### Items werden nicht angezeigt
1. Minecraft-Version und Asset-Version stimmen nicht überein
2. Offline-Modus: Items haben nur Text-Fallback
3. Überprüfe Minecraft-Item-Namen

### Token funktioniert nicht
1. Stelle sicher, dass der Token nicht abgelaufen ist (Standard: 10 Minuten)
2. Token kann nur einmal verwendet werden
3. Generiere einen neuen Token mit `/eventpvp webtoken`

### Beschriftungen zeigen Punkt-Namen statt Text (z. B. `expert.title`)
Dem geladenen Sprachpaket fehlt dieser Eintrag — `i18n.t()` gibt bei einem
unbekannten Key dessen Namen zurück.
1. **Zuerst `Strg`+`Shift`+`R`.** In den allermeisten Fällen hält der Browser nur
   noch ein altes Sprachpaket aus der Zeit vor dem Update (siehe *Caching*).
2. Erscheint es danach immer noch: Läuft der Server wirklich mit dem neuen Jar?
3. Nur falls du eigene `web/lang/*.json` pflegst — dort fehlt der Key. Alle
   sieben Dateien müssen dieselbe Key-Menge tragen.

### Eine Einstellung ist verschwunden
Seit 1.0.9 ist das Panel in Basis und Experte geteilt. Prüfe die
**Experten-Einstellungen** in der Sidebar-Gruppe *Erweitert* — dorthin sind unter
anderem *Spielerposition speichern*, *Inventar-Snapshots*, *Weltverwaltung*,
*Arena-Regeneration* und die *Webserver-Einstellungen* umgezogen. In den Editoren
liegen Welt-Regeneration und Klon-Quelle im Tab **🔧 Experte**.

---

## Konfiguration des Web-Servers

### web-config.yml
```yaml
web:
  enabled: true
  port: 8085
  # Netzwerk-Bindeadresse: "" = alle Interfaces (0.0.0.0), "127.0.0.1" = nur lokal (Reverse Proxy)
  bind-address: ""
  public-url: "http://localhost:{port}"
  title: "Event-PVP Konfigurator"
  
  theme:
    primary-color: "#4caf50"
    secondary-color: "#66bb6a"
    background-color: "#1a1a1a"
    surface-color: "#2d2d2d"
    card-color: "#3a3a3a"
    text-color: "#e0e0e0"
    text-secondary: "#b0b0b0"
    error-color: "#f44336"
    warning-color: "#ff9800"
    success-color: "#4caf50"
    info-color: "#2196f3"

security:
  auth-enabled: true
  token-validity-minutes: 10
  session-validity-minutes: 60
  required-permission: "eventpvp.admin.web"
  allowed-ips: []

items:
  enable-textures: true
  # Nur Ausweg für Items ohne mitgeliefertes Icon; die rund 1650 Icons liegen im Plugin.
  texture-source: "https://raw.githubusercontent.com/InventivetalentDev/minecraft-assets/1.21/assets/minecraft/textures/item/"
  resource-pack:
    enabled: false
    max-size-mb: 50

interface:
  auto-refresh-interval: 0
  auto-save: false
  confirm-save: true
  max-undo-steps: 20
  compact-view: false
  syntax-highlighting: true
```

---

## Technische Details

### API-Endpunkte
Das Interface nutzt folgende REST-API:

```
GET  /api/config/get        – Lädt config.yml
POST /api/config/save       – Speichert config.yml

GET  /api/worlds/get        – Lädt worlds.yml
POST /api/worlds/save       – Speichert worlds.yml

GET  /api/equipment/get     – Lädt equipment.yml
POST /api/equipment/save    – Speichert equipment.yml

GET  /api/webconfig/get     – Lädt web-config.yml
POST /api/webconfig/save    – Speichert web-config.yml

GET  /api/materials         – Item-Katalog des laufenden Servers (Materials, Verzauberungen,
                              Stapelgrößen, Item-Flags, Trankarten) – speist die Item-Auswahl
GET  /api/textures/overrides – Items mit einer Textur aus dem Server-Resourcepack

GET  /api/mvworlds/list     – Welten auf dem Server samt Belegung in den Konfigurationen
POST /api/mvworlds/create   – Legt eine Welt an (Job-ID, Fortschritt über /api/mvworlds/job)
POST /api/mvworlds/action   – Welt laden/entladen/löschen
GET  /api/mvworlds/job      – Status eines laufenden Welt-Jobs
GET  /api/mvworlds/backups  – Vorhandene Welt-Backups
POST /api/mvworlds/backup-action – Backup wiederherstellen oder löschen

GET  /api/inventories/status   – Betriebsart der Inventarverwaltung
POST /api/inventories/provider – Betriebsart umschalten
GET  /api/inventories/list     – Backups eines Spielers (?player=<name|uuid>)
GET  /api/inventories/get      – Inhalt eines Backups (?player=…&id=…)
POST /api/inventories/restore  – Backup zurückspielen (ratenbegrenzt, siehe unten)
POST /api/inventories/delete   – Einzelnes Backup löschen
GET  /api/inventories/guard    – Offene Sitzungen und gespeicherte Rückkehrpositionen

POST /api/reload            – Führt Plugin-Reload aus
GET  /api/status            – Status-Information (Plugin-/Server-Version, Spieler, TPS)

GET  /api/language/get      – Liest die eingestellte Sprache
POST /api/language/save     – Speichert die Sprache in config.yml

POST /api/auth/login        – Validiert einen Token und erstellt eine Session
POST /api/auth/logout       – Beendet die aktuelle Session
GET  /api/auth/check        – Prüft, ob die Session gültig ist
GET  /api/auth/validate     – Alias für /api/auth/check
```

Alle Endpunkte ausser `/api/auth/*` und `/api/language/get` erfordern eine
gültige Session.

`POST /api/inventories/restore` ist zusätzlich auf 10 Wiederherstellungen pro Minute
begrenzt (panelweit, nicht je Spieler). Der Endpunkt legt Items im Spiel an; ohne Bremse
wäre ein übernommenes Web-Login eine Item-Fabrik. Wird die Grenze erreicht, antwortet er
mit `inventory.error.rateLimited`. Jede Wiederherstellung und jede Löschung landet
ausserdem als Warnung im Serverlog.

### Statische Dateien
Das Interface wird direkt aus dem Jar serviert (`web/` im Ressourcen-Ordner):
```
web/
├── index.html                (Markup + komplettes CSS inline)
├── items.js                  (Item-Katalog und Icon-Auflösung; lädt vor app.js)
├── app.js                    (i18n, Auth, Laden/Speichern, Navigation, Theme,
│                              Inventar-Backup-Browser)
├── editors.js                (Event-, Welt-, Equipment-Editor, Item-Picker)
├── logo.png
├── lang/
│   ├── languages.json        (Liste der verfügbaren Sprachen)
│   └── de|en|es|fr|ja|pl|ru.json   (je 835 Keys, identische Key-Menge)
└── item-assets/              (1658 Item-Icons, 64x64 PNG, Dateiname = Material-Enum)
    └── _index.json           (Manifest; Notfall-Item-Liste, falls /api/materials ausfällt)
```

Die Icons machen das Panel unabhängig von einer Internetverbindung — vorher lud es sie
von einem GitHub-CDN, und jedes Block-Item bekam grundsätzlich nur einen Platzhalter.
Erzeugt werden sie aus dem Quellordner `item-assets/` im Projektwurzelverzeichnis mit
`tools/scale-item-assets.ps1`; nach einem Asset-Update dieses Skript erneut laufen lassen.

Texturen aus dem Resourcepack des Servers liegen nicht im Jar, sondern unter
`plugins/<Plugin>/texture-overrides/` und werden vom Static-Handler unter
`/item-assets/override/<MATERIAL>.png` ausgeliefert (siehe `items.resource-pack` in der
web-config.yml).

### Caching (wichtig nach Updates)
Seit 1.0.9 werden `index.html`, die JavaScript-Dateien und die Sprachdateien mit
`Cache-Control: no-cache, no-store, must-revalidate` ausgeliefert; nur
unveränderliche Assets wie Bilder und Schriften behalten eine Stunde Cache.

Frühere Versionen cachten **alles** eine Stunde lang — auch die Sprachdateien.
Nach einem Plugin-Update konnte das Panel deshalb rohe Key-Namen wie
`expert.title` anzeigen, weil der Browser noch das alte Sprachpaket hielt. Der
Fix kann eine bereits gecachte Kopie nicht nachträglich verwerfen: **nach dem
Update auf 1.0.9 einmal `Strg`+`Shift`+`R` drücken.** Danach tritt das Problem
nicht mehr auf.

---

## Häufig gestellte Fragen

**F: Wie erhalte ich Zugang zum Web-Interface?**
A: Führe `/eventpvp webtoken` im Spiel aus.

**F: Kann ich Passwort-Schutz aktivieren?**
A: Ja, aber jetzt wird Token-basierte Authentifizierung empfohlen.

**F: Welche Farben sind empfohlen?**
A: Grün (`#4caf50`), Blau (`#2196f3`), Orange (`#ff9800`) - dunkle Themen.

**F: Kann ich meine Konfiguration extern bearbeiten?**
A: Ja, aber dann **Server Reload** im Interface klicken, um Änderungen zu laden.

**F: Können mehrere Spieler gleichzeitig konfigurieren?**
A: Ja, aber es wird die letzte Speicherung verwendet (keine Konflikt-Erkennung).

---

## Changelog

### v1.0.9
- ✨ **Basis- und Experten-Einstellungen** – aufgeteilt nach Risiko, nicht nach
  Häufigkeit. Neuer Sidebar-Bereich *Experte*; „Allgemeine Einstellungen" von
  9 auf 6 Karten geschrumpft.
- ✨ **⚠-Warnungen an riskanten Schaltern** – Snapshots aus, Positionsspeicherung
  aus, Welt-Regeneration
- ✨ **Event-Editor 8 Tabs, Welt-Editor 4 Tabs** – Siegbedingung, Spielmodus und
  Nachrichten bleiben ausdrücklich normale Tabs; nur die weltzerstörenden Felder
  liegen unter *Experte*
- 🐛 **Veraltete Sprachpakete nach Updates** – Panel-Dateien werden nicht mehr
  eine Stunde lang gecacht (führte zu rohen Key-Namen im Interface)
- 🐛 **Port, Titel und Farbwähler zeigten immer die Vorgabewerte** statt der in
  `web-config.yml` gespeicherten
- 🐛 **Doppelte Element-ID `main-content`** entfernt
- 📝 15 neue Interface-Texte in allen 7 Sprachen (je 570 Keys)

### v1.0.8
- ✨ **Integrationen** – Unterstützung für AJLeaderboards, DecentHolograms und PvPManager
- ✨ **Token-Authentifizierung** – Sicheres Login-System statt einfaches Passwort
- ✨ **Sprachauswahl** – 7 Sprachen im Interface verfügbar
- ✨ **YAML Preview** – Zeigt generierte YAML-Dateien an
- ✨ **Verbesserte Theme-Einstellungen** – Mehr Farboptionen
- 📝 **Dokumentation aktualisiert**

### v0.9.86-Beta
- ✨ Intuitives Web-Interface mit dunklem Theme
- ✨ Alle YMLs außer plugin.yml und messages.yml editierbar
- ✨ Undo/Redo Funktionalität
- ✨ Item-Picker mit Minecraft-Texturen
- ✨ Theme-Anpassung
- ✨ Responsive Design (Desktop & Mobile)
