# Umstieg von 1.0.8 auf 1.0.9

Die Konfigurationsdateien werden beim ersten Start automatisch auf den neuen Stand gebracht.
Von Hand zu tun ist nur eines: **die neuen Pflicht-Plugins installieren, bevor du das Jar
tauschst.**

---

## 1. Vor dem Jar-Tausch: Pflicht-Plugins installieren

1.0.9 setzt drei Plugins zwingend voraus:

| Plugin | Wofür | In 1.0.8 |
|---|---|---|
| **[VaultUnlocked](https://modrinth.com/project/ayRaM8J7)** (oder das klassische [Vault](https://www.spigotmc.org/resources/vault.34315/)) | Wetteinsätze, Wirtschaft | war schon Pflicht |
| **[Multiverse-Core](https://modrinth.com/project/3wmN97b8)** (v4 oder v5) | Weltenverwaltung, Klonen, Arena-Reset | war optional |
| **[InventoryBackup](https://modrinth.com/project/rpKY25cW)** | Inventarsicherung | gab es nicht |

Zu **Vault**: das Plugin hängt in der `plugin.yml` an `Vault` und holt sich die Wirtschaft
über `net.milkbowl.vault.economy.Economy`. VaultUnlocked meldet sich per `provides: [Vault]`
unter demselben Namen an und funktioniert deshalb als direkter Ersatz — installiere aber
immer nur eines von beiden.

Zu **Multiverse-Core**: das Plugin erkennt beim Start selbst, was installiert ist. Ist die
MV5-API (`org.mvplugins.multiverse.core.MultiverseCoreApi`) vorhanden, benutzt es sie direkt;
auf MV4 fällt es auf ein Kommando-Backend zurück. Beides funktioniert. Ab **MV 5.7** kommt
zusätzlich `getOfflineWorldFolder()` zum Einsatz, was das Auffinden entladener Welten
zuverlässiger macht — nötig ist es nicht, es gibt eine Fallback-Kette.

**InventoryBackup setzt Paper/Spigot 1.20+ voraus.** Das Plugin selbst läuft ab 1.19, durch
die neue Pflicht-Abhängigkeit liegt die praktische Untergrenze für 1.0.9 aber bei 1.20.

**Fehlt eines davon, lädt Spigot das Plugin gar nicht.** In der Konsole steht dann nur
`Unknown dependency InventoryBackup` — keine Meldung vom Plugin selbst, weil es nie startet.
Wenn nach dem Update scheinbar „nichts passiert", ist das fast immer die Ursache.

---

## 2. Update durchführen

1. Server stoppen.
2. Die neuen Plugins in `plugins/` legen (siehe oben).
3. Altes Jar durch das neue ersetzen.
4. Server starten.

Beim Start migriert das Plugin `config.yml`, `worlds.yml`, `equipment.yml`, `web-config.yml`
und deine Sprachdatei. **Vor jeder Änderung legt es eine Kopie
`<dateiname>.bak-<zeitstempel>` daneben.** Was verändert wurde, steht danach als Block im
Server-Log:

```
--- Konfiguration auf den aktuellen Stand gebracht ---
  config.yml: added 14 new setting(s)
  config.yml: settings.world-loading: 'both' -> settings.world-management.events: true, .arenas: true
  config.yml: settings.inventory-snapshots.enabled: true -> settings.inventory-management.provider: 'auto'
  ...
```

Deine eigenen Werte bleiben unangetastet — die Migration überschreibt nie eine Einstellung,
die du getroffen hast. Sie ergänzt nur, was fehlt, und schreibt abgelöste Schlüssel um.
Gelöschte Beispiel-Events oder -Kits kehren nicht zurück.

Ein zweiter Start verändert nichts mehr und legt keine weitere Sicherungskopie an.

---

## 3. Was sich geändert hat

### Inventarverwaltung — die größte Änderung

Das Plugin sicherte Inventare bis 1.0.8 selbst nach `inventory_backups.yml`. Ab 1.0.9
übernimmt das **InventoryBackup**.

| 1.0.8 | 1.0.9 |
|---|---|
| `settings.inventory-snapshots.enabled: true` | `settings.inventory-management.provider: "auto"` |
| `settings.inventory-snapshots.enabled: false` | `settings.inventory-management.provider: "none"` + `legacy-safety-backups: true` |
| `retain-days`, `default-group`, `groups`, `ids.*` | **ersatzlos entfallen** — die Aufbewahrung regelt jetzt InventoryBackup |
| Befehl `/inventoryrestore` | entfallen — Wiederherstellung über InventoryBackup bzw. das Web-Panel |
| Rechte `eventpvp.inventory.restore`, `eventpvp.inventory.restore.any` | entfallen — **aus deinem Rechte-Plugin entfernen** |

Neu hinzu kommen zwölf Schalter unter `settings.inventory-management.*` (automatisches
Wiederherstellen nach Match/Event/Tod/Rejoin, Verhalten bei fehlgeschlagenem Backup,
Crash-Wiederanlauf). Sie stehen nach dem Update samt Erklärung in deiner `config.yml`.

> **Hattest du eine Aufbewahrungsdauer eingestellt?** `retain-days` hat keine Entsprechung
> mehr. Stell die Aufbewahrung in InventoryBackup ein. Sie muss länger sein als die längste
> denkbare offene Sitzung — sonst verschwindet das Backup eines lange abwesenden Spielers.

> **Alte Snapshots:** `inventory_backups.yml` und `inventory_post_backups.yml` werden von
> 1.0.9 nicht mehr gelesen. Sie bleiben unangetastet liegen. Wenn du daraus noch etwas
> brauchst, sichere die Dateien weg, bevor du aufräumst.

> **Läuft Multiverse-Inventories parallel?** Dann warnt das Plugin beim Start
> (`settings.inventory-management.warn-on-multiverse-inventories`). Entweder
> Multiverse-Inventories für die Arena- und Eventwelten abschalten oder
> `provider: "none"` setzen — beide Systeme gleichzeitig führen zu Inventarverlust.

### Weltenverwaltung

`settings.world-loading` (ein Wert für zwei Module) wird zu zwei unabhängigen Schaltern:

| alt | neu |
|---|---|
| `none` | `world-management.events: false`, `arenas: false` |
| `arena` | `events: false`, `arenas: true` |
| `event` / `lobby` | `events: true`, `arenas: false` |
| `both` (und alles andere) | `events: true`, `arenas: true` |

`settings.command-restriction: "join"` bzw. `"pvp"` werden zu `"both"` — beide alten Werte
waren wirkungslos und verhielten sich ohnehin so.

### Ausrüstung

Die drei historischen Sektionen `equipment:`, `equipment-sets:` und `equipment-groups:`
werden zu einer einzigen `equipment:` zusammengeführt. Der gemeinsame Schalter `enabled`
wird dabei auf die zwei getrennten `pvpwager-equip-enable` und `event-equip-enable`
übersetzt — ein abgeschaltetes Set bleibt in beiden Systemen abgeschaltet.

Existiert dieselbe Set-ID in zwei Sektionen mit unterschiedlichem Inhalt, gewinnt der
Eintrag aus `equipment:`; der andere wird als `<id>-legacy` übernommen und die Kollision
im Log gemeldet. **Prüfe in dem Fall, welches der beiden Sets deine Kits benutzen sollen.**

### Web-Panel

`items.local-texture-path` und `items.block-texture-source` entfallen, dafür kommt
`items.resource-pack.*` (Texturen aus dem Resourcepack des Servers, standardmäßig aus). Neu ist
außerdem `web.bind-address` — leer lassen für alle Interfaces, `"127.0.0.1"` wenn ein
Reverse-Proxy (Nginx, Caddy, Cloudflare Tunnel) davorsteht.
Zudem ist die Inventar-Verwaltung nun als eigenständige 3-Tab-Kategorie (`nav.inventories`)
im Web-Panel mit interaktivem Minecraft-Canvas, XP-Leiste und 10/Min Rate Limiter integriert.

### Befehle

**Neu:**

- `/eventpvp rescue list|<player>|clean`: Verwaltet hängengebliebene Sitzungen und bringt gestrandete Spieler nach Server-Crashes an ihren Ursprungsort zurück (Recht: `eventpvp.admin`).

**Geändert:**

- `/eventpvp debug (on|on full|off|status)`: Gab es schon in 1.0.8. Neu ist, dass der Modus persistent unter `settings.debug` in der `config.yml` gespeichert wird und einen Neustart übersteht (Recht: `eventpvp.debug`).

**Unverändert** (gab es schon in 1.0.8, hier nur zur Erinnerung):

- `/eventpvp webtoken`: Erzeugt ein zeitlich begrenztes Einmal-Token für das Web-Interface (Recht: `eventpvp.admin.web` oder `eventpvp.admin`).

### Persistente Dateien

Neben der `config.yml` sichern drei synchron geschriebene Dateien den Serverbetrieb ab:
- `inventory-guard.yml` (offene Inventarsitzungen während Events/Matches)
- `player-return-locations.yml` (exakte Rückkehrorte von Spielern vor dem Teleport)
- `pending-payouts.yml` (ausstehende Wetteinsatz-Auszahlungen bei vorzeitigem Disconnect)

### Sprachdateien

Die Sprachdateien sind auf **1097 Schlüssel** gewachsen, vor allem durch lokalisierte
Konsolenausgaben. In 1.0.8 waren es je nach Sprache unterschiedlich viele (Englisch 876,
Deutsch 875, die übrigen fünf je 805) — ab 1.0.9 haben alle sieben Sprachen exakt denselben
Schlüsselsatz. Hast du deine Sprachdatei angepasst, bleiben deine Texte erhalten und die
neuen werden **in derselben Sprache** aus dem Jar ergänzt. Schlüssel, die es nicht mehr gibt,
bleiben stehen — sie stören nicht.

### Rechte

- **Neu**:
  - `pvpwager.spectate.all` (Zuschauen auch bei vollem Zuschauerlimit)
  - `pvpwager.nowager` (Matches ohne Wetteinsatz starten)
- **Unverändert** (gab es schon in 1.0.8, hier nur zur Erinnerung):
  - `eventpvp.admin.web` (Web-Token generieren und Web-Panel nutzen)
  - `eventpvp.debug` (`/eventpvp debug` verwenden)
  - `eventpvp.debug.receive` (Debug-Stream im Chat empfangen)
- **Entfallen**:
  - `eventpvp.inventory.restore` und `eventpvp.inventory.restore.any` (Wiederherstellung läuft jetzt über InventoryBackup und das Web-Panel).

---

## 4. Zurück auf 1.0.8

1. Server stoppen.
2. Altes Jar zurücklegen.
3. Zu jeder Datei die passende `*.bak-<zeitstempel>`-Kopie zurückkopieren
   (`config.yml.bak-20260811-143000` → `config.yml`).
4. Server starten.

Die Sicherungskopien liegen im Datenordner des Plugins neben den Originalen und werden nie
automatisch gelöscht.

---

## 5. Wenn etwas nicht stimmt

| Symptom | Ursache |
|---|---|
| Plugin taucht nach dem Update gar nicht auf, Log sagt `Unknown dependency` | Pflicht-Plugin fehlt, siehe Schritt 1 |
| Log sagt `... is required ... Disabling plugin` | Pflicht-Plugin ist installiert, aber nicht aktiviert |
| Log sagt `Could not save <datei> after migrating` oder `Could not back up <datei> before migrating` | Schreibrechte auf dem Datenordner prüfen; die Migration versucht es beim nächsten Start erneut, bis dahin bleibt alles beim Alten |
| Ein Kit sieht anders aus als vorher | Set-ID-Kollision beim Zusammenführen von `equipment.yml`, siehe Log-Warnung und `<id>-legacy` |
| Spieler verlieren Inventare | Multiverse-Inventories läuft parallel, siehe Abschnitt Inventarverwaltung |
