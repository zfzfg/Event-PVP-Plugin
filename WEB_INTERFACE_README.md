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
Die linke Sidebar bietet folgende Kategorien:

| Icon | Bereich | Funktion |
|------|---------|----------|
| ⚙️ | Allgemeine Einstellungen | Globale Plugin-Konfiguration, Sprache, Integrationen |
| 📅 | Events | Event-Definitionen verwalten |
| 🌍 | Welten & Arenen | PvP-Welten konfigurieren |
| 🛡️ | Equipment-Sets | Rüstungs- und Inventar-Konfigurationen |
| 🎨 | Theme & Farben | Web-Interface Anpassung |
| 📄 | YAML Preview | Zeigt die generierten YAML-Dateien |

### Statusanzahl (Badges)
Neben Events, Welten und Equipment-Sets werden die Anzahl der konfigurierten Einträge angezeigt.

---

## Funktionen

### 1. Allgemeine Einstellungen
Hier findest du alle globalen Einstellungen:
- **Sprache** – Wähle zwischen Deutsch, Englisch, Französisch, Spanisch, Russisch, Polnisch, Japanisch
- **Prefix** – Nachrichten-Präfix (mit Farbcodes: `&6[Event]&r`)
- **Hauptwelt** – Standard-Welt für Teleporte
- **Beitrittsphase** – Countdown zum Beitreten (in Sekunden)
- **Lobby-Countdown** – Countdown vor Event-Start
- **Inventar-Snapshots** – Speichert Inventare vor Events
- **Weltenverwaltung** – Automatische Welt-Ladung aktivieren
- **Zuschauer-System** – Erlaubt Spielern, Matches zuzuschauen
- **Match-Einstellungen** – PvP-Match Countdown und Dauer
- **Arena-Regeneration** – Backups und Wiederherstellung
- **Update-Check** – Automatische Prüfung auf neue Versionen
- **Externe Integrationen** – Konfiguriere AJLeaderboards, DecentHolograms und PvPManager

#### Integrationen (Neu in 1.0.8)
- **AJLeaderboards** – Zeige Event- und PvP-Statistiken in Leaderboards
- **DecentHolograms** – Zeige Statistiken in Hologrammen
- **PvPManager** – Entferne Combat-Tags automatisch bei Event- oder Match-Ende

### 2. Events
Hier können neue Event-Typen erstellt oder bearbeitet werden.

**Event-Editor Tabs:**
- **Basis** – Name, Befehl, Min/Max Spieler, Status
- **Welten** – Lobby und Event-Welt Konfiguration
- **Spawns** – Spawn-Mechanismen (Single, Random, Team, etc.)
- **Equipment** – Equipment-Gruppe und Lobby-Einstellungen
- **Mechaniken** – Game Mode, PvP, Hunger, Friendly Fire
- **Nachrichten** – Event-Messages (Start, Winner, Eliminated, Objective)
- **Belohnungen** – Winner, Team-Winner und Teilnahme-Belohnungen

**Messages-Tab:**
Editiere Event-spezifische Nachrichten mit Minecraft-Farbcodes (`&a`, `&e`, `&l`, etc.)

**Button-Funktionen:**
- ➕ **Neues Event** – Erstellt einen neuen Event
- ✏️ **Bearbeiten** – Öffnet den Event-Editor
- 🗑️ **Löschen** – Entfernt einen Event

### 3. Welten & Arenen
Verwaltet alle PvP-Welten und Event-Arenen.

**Welten-Eigenschaften:**
- Anzeigename und Welt-ID
- PvP-Status (PvP-Welt oder Event-Welt)
- Build-Erlaubnis
- Regeneration aktivieren
- PvP-Spawn-Konfiguration (Typ und Positionen)

**Spawn-Typen für PvP:**
- `FIXED_SPAWNS` – Feste Positionen für Player1, Player2, Spectator
- `RANDOM_RADIUS` – Zufällig innerhalb Radius
- `RANDOM_AREA` – Zufällig in rechteckiger Fläche
- `RANDOM_CUBE` – Zufällig in 3D-Box
- `COMMAND` – Spawn via Befehl

### 4. Equipment-Sets
Erstellt Rüstungs- und Inventar-Konfigurationen.

**Eigenschaften:**
- Anzeigename und Set-ID
- Aktivität (aktiv/inaktiv)
- Allowed PvP Worlds (in welchen Welten nutzbar)
- Armor Slots (Helm, Chestplate, Leggings, Boots)
- Inventory Items mit Slot und Menge

**Item-Auswahl:**
Klicke auf einen Item-Slot und wähle ein Minecraft-Item aus der Picker-Liste aus. Alle Items haben Minecraft-Texturen-Vorschau.

### 5. Theme & Farben
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

---

## Änderungen & Speichern

### Tracking von Änderungen
Das Interface verfolgt alle Änderungen automatisch. Wenn ungespeicherte Änderungen vorhanden sind:
- Erscheint eine "Ungespeicherte Änderungen" Leiste unten
- Die Speichern-Buttons sind aktiv

### Speichern
Klicke **💾 Speichern** im Header, um alle Änderungen zu speichern:
- config.yml
- worlds.yml
- equipment.yml
- web-config.yml

### Undo / Redo
- **↶ Undo** – Letzte Änderung rückgängig machen
- **↷ Redo** – Undo rückgängig machen

### Änderungen verwerfen
Klicke auf die **×** Taste in der "Ungespeicherte Änderungen"-Leiste, um alle Änderungen zu verwerfen.

---

## Erweiterte Funktionen

### 1. Export/Import
- **💾 Speichern** – Lädt alle Konfigurationen herunter (JSON-Format)
- Kann später wieder hochgeladen werden

### 2. YAML Vorschau
Klicke auf **📄 YAML Preview** in der Sidebar:
- Zeigt die generierten YAML-Dateien an
- Tabs für config.yml, worlds.yml, equipment.yml
- **📋 Kopieren** – YAML in die Zwischenablage kopieren

### 3. Server Reload
Klicke auf **🔄 Server Reload**:
- Führt `/eventpvp reload` aus
- Lädt alle Konfigurationen neu

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
- **clone-source-world** – Welt wird geklont statt direkt genutzt (besser für Regeneration)
- **regenerate-event-world** – Setzt die Welt zurück nach jedem Event/Match

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

---

## Konfiguration des Web-Servers

### web-config.yml
```yaml
web:
  enabled: true
  port: 8085
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
  local-texture-path: "minecraft_textures_item/"
  texture-source: "https://raw.githubusercontent.com/InventivetalentDev/minecraft-assets/1.21/assets/minecraft/textures/item/"
  block-texture-source: "https://raw.githubusercontent.com/InventivetalentDev/minecraft-assets/1.21/assets/minecraft/textures/block/"

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

POST /api/reload            – Führt Plugin-Reload aus
GET  /api/status            – Status-Information

POST /api/auth/token        – Validiert einen Token und erstellt eine Session
POST /api/auth/logout       – Beendet die aktuelle Session
```

### Statische Dateien
Das Interface wird aus dem `web/`-Ordner serviert:
```
plugins/Event-PVP/web/
├── index.html
├── app.js
├── editors.js
└── minecraft_textures_item/  (optional, lokale Texturen)
```

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

### v1.0.8
- ✨ **Integrationen** – Unterstützung für AJLeaderboards, DecentHolograms und PvPManager
- ✨ **Token-Authentifizierung** – Sicheres Login-System statt einfaches Passwort
- ✨ **Sprachauswahl** – 8 Sprachen im Interface verfügbar
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
