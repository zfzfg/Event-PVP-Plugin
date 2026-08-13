# Event-PVP-Plugin – Dokumentation (v1.0.9)

Ein umfassendes Minecraft-Plugin, das Event-Management und ein PvP-Wager-Wettsystem mit einer modernen, browserbasierten Web-Oberfläche vereint.

---

## 🌐 Web-Interface

Das gesamte Plugin kann über eine intuitive Benutzeroberfläche im Browser konfiguriert werden – ohne manuelles Bearbeiten von YAML-Dateien!

### Highlights
- **🎨 Visuelle Konfiguration**: Bearbeitung aller Einstellungen, Events, Arenen und Ausrüstungen im Browser.
- **🔒 Sichere Authentifizierung**: Einmalige, zeitlich begrenzte Token via `/eventpvp webtoken`.
- **🌍 Mehrsprachig**: Vollständige Unterstützung für 7 Sprachen (DE, EN, FR, ES, RU, PL, JA) mit 100% Key-Parität.
- **📦 Inventar-Verwaltung**: Integrierter Backup-Explorer mit Canvas-Vorschau, 2-Stufen-Wiederherstellung und Session-Journal.
- **🗺️ Multiverse-Verwaltung**: Welten direkt aus dem Web-Panel erstellen, laden, entladen und Presets zuweisen.
- **🖼️ Item-Texturen**: Minecraft-Texturen und dynamischer Material-Katalog für fehlerfreie Ausrüstungs-Sets.

### Schnellstart Web-Interface
1. Web-Server in `web-config.yml` aktivieren:
   ```yaml
   web:
     enabled: true
     port: 8085
   ```
2. Im Spiel den Token generieren: `/eventpvp webtoken`
3. Im Browser `http://localhost:8085` (oder Server-IP:Port) aufrufen und Token eingeben.

---

## 📋 Voraussetzungen

- **Server**: Spigot / Paper / Purpur (kompatibel mit Minecraft 1.19.4+)
- **Java**: Version 17 oder neuer
- **Pflicht-Abhängigkeiten**:
  - `Multiverse-Core` (Welt-Verwaltung, dynamisches Laden/Entladen, Klonen und Zurücksetzen)
  - `InventoryBackup` / `InventoryRestore` (Sichere Inventar-Snapshots und Crash-Sicherheitsnetz)
  - `Vault` (Wirtschafts- und Wetteinsatz-System)
- **Optionale Abhängigkeiten**:
  - `PlaceholderAPI` (PAPI-Platzhalter für Event- und PvP-Statistiken)
  - `PvPManager` (Kampf-Integration)

---

## 📁 Konfigurationsdateien

Das Plugin verwendet zentrale, übersichtliche Dateien im Plugin-Ordner `plugins/Event-PVP-Plugin/`:

- `config.yml` – Allgemeine Einstellungen, Event-Definitionen, Sicherheitsnetze & Debug-Modus
- `worlds.yml` – Zentrale Welten-Definitionen für Events und PvP-Arenen
- `equipment.yml` – Zentrale Ausrüstungs-Sets (Kits) mit Items, Rüstung und Verzauberungen
- `web-config.yml` – Einstellungen für den integrierten Web-Server und das Web-Panel
- `messages_<lang>.yml` – Sprachdateien (`de`, `en`, `es`, `fr`, `ja`, `pl`, `ru`) mit automatischem Fallback

---

## 🎮 Befehle & Berechtigungen

### Allgemeine & Admin-Befehle
| Befehl | Berechtigung | Beschreibung |
|---|---|---|
| `/eventpvp reload` | `eventpvp.admin` | Lädt alle Konfigurationen, Sprachdateien, Welten und Kits neu |
| `/eventpvp webtoken` | `eventpvp.admin.web` | Generiert ein Einmal-Token für den Web-Login |
| `/eventpvp rescue <list\|player\|clean>` | `eventpvp.admin` | Rettet gestrandete Spieler und bereinigt verwaiste Sessions |
| `/eventpvp debug <on\|on full\|off\|status>` | `eventpvp.debug` | Steuert das präzise Debug-Logging |
| `/eventpvp stats [player]` | `eventpvp.stats` | Zeigt Event- und PvP-Statistiken |

### Event-Befehle
| Befehl | Berechtigung | Beschreibung |
|---|---|---|
| `/event <name> join` | `eventplugin.join` | Einem aktiven Event beitreten |
| `/event <name> leave` | `eventplugin.join` | Das aktuelle Event verlassen |
| `/event <name> start` | `eventplugin.admin` | Startet ein konfiguriertes Event manuell |
| `/event <name> stop` | `eventplugin.admin` | Beendet ein laufendes Event |
| `/event list` | `eventplugin.join` | Listet alle verfügbaren Events auf |

### PvP-Wager-Befehle
| Befehl | Berechtigung | Beschreibung |
|---|---|---|
| `/pvp` | `pvpwager.use` | Öffnet das interaktive Wager-GUI |
| `/pvpa <player> <wager> <amount> <arena> <kit>` | `pvpwager.command` | Fordert einen Spieler per Direktbefehl heraus |
| `/pvp accept [player]` | `pvpwager.use` | Akzeptiert eine offene PvP-Herausforderung |
| `/pvp deny [player]` | `pvpwager.use` | Lehnt eine Herausforderung ab |
| `/pvp spectate <player>` | `pvpwager.spectate` | Schaut einem laufenden Duell zu |
| `/pvp leave` | `pvpwager.spectate` | Verlässt den Zuschauermodus |
| `/surrender` | `pvpwager.use` | Gibt ein laufendes Duell auf |
| `/draw` | `pvpwager.use` | Schlägt ein Unentschieden vor |

---

## 🛡️ Sicherheitsfunktionen & Crash-Schutz

- **Synchrones Positions-Journal (`player-return-locations.yml`)**: Speichert Ursprungspositionen vor Teleports; gestrandete Spieler werden beim Rejoin automatisch sicher zurückteleportiert.
- **Inventarsicherung via InventoryBackup**: Vollautomatische Backups vor Duellen/Events und verlässliche Wiederherstellung.
- **Offline-Gewinnauszahlung (`pending-payouts.yml`)**: Gewinne und Belohnungen von getrennten Spielern werden synchron gespeichert und beim nächsten Login ausgezahlt.
- **Sichere Entladungs-Abbrüche**: Welten werden nur entladen, wenn alle Spieler nachweislich erfolgreich herausteleportiert wurden.

---

## 📄 Lizenz

Dieses Projekt steht unter der [MIT-Lizenz](LICENSE).
