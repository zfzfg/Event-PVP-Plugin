# Event-PVP Web-Interface – REST API Dokumentation

Stand: 1.0.9

## Server Information

**Base URL:** `http://localhost:8085` (Port konfigurierbar in `web-config.yml`)

**Netzwerk-Bindung:** In `web-config.yml` unter `server.bind-address`. Leer (`""`) bindet an alle Interfaces (`0.0.0.0`), `"127.0.0.1"` beschränkt den Server auf localhost (empfohlen hinter einem Reverse Proxy wie Nginx, Caddy oder Cloudflare Tunnel).

**Content-Type:** `application/json`

**Implementierung:** `com.sun.net.httpserver.HttpServer` in
`de.zfzfg.core.web.WebServer`, Handler in `de.zfzfg.core.web.WebApiHandler`.

**CORS:** Die Antwort spiegelt den `Origin`-Header des Requests wider
(`Access-Control-Allow-Origin: <Origin>`, `Vary: Origin`) und erlaubt
`Access-Control-Allow-Credentials: true`. Fehlt der Header, wird `null`
gesendet. Erlaubte Methoden: `GET, POST, OPTIONS`. Ein `OPTIONS`-Preflight wird
mit **204** ohne Body beantwortet.

**Antwortformat:** Jede API-Antwort ist ein JSON-Objekt mit `success`. Lese-
Endpunkte legen die Nutzdaten unter **`data`** ab, Schreib-Endpunkte antworten
mit `message`.

---

## Authentifizierung

> Die frühere Beschreibung „HTTP Basic Auth mit Passwort" ist überholt. Das
> Interface nutzt seit 1.0.8 **Einmal-Token plus Session-Cookie**.

Ablauf:

1. Im Spiel oder über die Serverkonsole `/eventpvp webtoken` ausführen (erfordert `eventpvp.admin.web` oder `eventpvp.admin`) → Einmal-Token (Standard: 5 Minuten
   gültig, nur einmal verwendbar).
2. `POST /api/auth/login` mit dem Token. Der Server setzt daraufhin ein Cookie
   `session=<id>; Path=/; HttpOnly; SameSite=Strict`.
3. Alle weiteren Requests senden dieses Cookie mit (`credentials: 'include'`).

Konfiguriert wird das in `web-config.yml` unter `security`:
`auth-enabled`, `token-validity-minutes`, `session-validity-minutes`,
`required-permission: "eventpvp.admin.web"`, `allowed-ips`.

Ist `auth-enabled: false`, sind alle Endpunkte ungeschützt und
`/api/auth/check` meldet `authRequired: false`.

**Ungeschützt sind ausserdem:** alle `/api/auth/*`-Endpunkte und
`GET /api/language/get`. Alles andere verlangt eine gültige Session und
antwortet sonst mit **401**.

---

## API-Übersicht

### Authentifizierung
- `POST /api/auth/login` – Token einlösen, Session anlegen
- `POST /api/auth/logout` – Session beenden
- `GET  /api/auth/check` – Session prüfen
- `GET  /api/auth/validate` – Alias für `/api/auth/check`

### Status
- `GET /api/status` – Plugin-/Server-Status

### Konfiguration (config.yml)
- `GET  /api/config/get` – Laden der gesamten Konfiguration
- `POST /api/config/save` – Speichern der Konfiguration

### Welten & Arenen (worlds.yml)
- `GET  /api/worlds/get` – Laden aller Welten
- `POST /api/worlds/save` – Speichern der Welten-Konfiguration

### Multiverse-Welten (Server-Weltbestand)
- `GET  /api/mvworlds/list` – Welten, die auf dem Server existieren, inkl. Lade-Status und Belegung
- `POST /api/mvworlds/create` – Welt über Multiverse anlegen (liefert eine Job-ID)
- `POST /api/mvworlds/action` – `load`, `unload` oder `delete` (liefert eine Job-ID)
- `GET  /api/mvworlds/job?id=…` – Status eines laufenden Auftrags
- `GET  /api/mvworlds/backups` – Welt-Backups in `plugins/<plugin>/backups/`
- `POST /api/mvworlds/backup-action` – Backup wiederherstellen (Job) oder löschen

### Inventar-Verwaltung (InventoryBackup)
- `GET  /api/inventories/status` – Status des Inventar-Providers und der Schnittstelle
- `POST /api/inventories/provider` – Umschalten des Providers (`auto`, `inventoryrestore`, `none`)
- `GET  /api/inventories/list?player=…[&type=…]` – Backups eines Spielers abfragen
- `GET  /api/inventories/get?player=…&id=…` – Einzelnes Inventar-Backup im Detail abrufen
- `POST /api/inventories/restore` – Inventar-Backup an einen Spieler zurückspielen
- `POST /api/inventories/delete` – Inventar-Backup löschen
- `GET  /api/inventories/guard` – Offene Sitzungen aus `inventory-guard.yml` abfragen

### Equipment (equipment.yml)
- `GET  /api/equipment/get` – Laden aller Equipment-Sets
- `POST /api/equipment/save` – Speichern der Equipment-Konfiguration

### Web-Interface (web-config.yml)
- `GET  /api/webconfig/get` – Laden der Web-Interface Konfiguration
- `POST /api/webconfig/save` – Speichern der Web-Interface Konfiguration

### Sprache
- `GET  /api/language/get` – Liest `settings.language` (ungeschützt)
- `POST /api/language/save` – Schreibt `settings.language`

### Admin
- `POST /api/reload` – Lädt alle Konfigurationen neu

---

## Detaillierte Endpunkt-Dokumentation

### POST /api/auth/login

**Request Body:**
```json
{ "token": "AB12-CD34-EF56" }
```

**Response 200:**
```json
{
  "success": true,
  "playerName": "Notch",
  "message": "Erfolgreich eingeloggt"
}
```
Zusätzlich: `Set-Cookie: session=<id>; Path=/; HttpOnly; SameSite=Strict`

**Fehler:** `400` wenn kein Token gesendet wurde, `401` bei ungültigem oder
abgelaufenem Token.

> Die `error`-Texte dieser Antworten sind deutsch und **nicht** zur Anzeige
> gedacht — das Panel formuliert seine eigenen Meldungen aus `web/lang/*.json`.

---

### POST /api/auth/logout

Kein Body nötig. Invalidiert die Session und löscht das Cookie
(`Max-Age=0`).

```json
{ "success": true, "message": "Erfolgreich ausgeloggt" }
```

---

### GET /api/auth/check  ·  GET /api/auth/validate

```json
{ "authenticated": true, "authRequired": true, "playerName": "Notch" }
```

Ohne gültige Session (HTTP **200**, nicht 401):
```json
{ "authenticated": false, "authRequired": true }
```

Bei `auth-enabled: false`:
```json
{ "authenticated": true, "authRequired": false, "playerName": "Admin" }
```

---

### GET /api/status

Auch hier liegen die Nutzdaten unter `data`:

```json
{
  "success": true,
  "data": {
    "pluginName": "Event-PVP-Plugin",
    "pluginVersion": "1.0.9",
    "serverVersion": "git-Paper-123 (MC: 1.21.3)",
    "onlinePlayers": 7,
    "maxPlayers": 40,
    "tps": 19.98,
    "uptime": "2h 14m",
    "language": "de"
  }
}
```

---

### GET /api/config/get  ·  /api/worlds/get  ·  /api/equipment/get  ·  /api/webconfig/get

Alle vier Lese-Endpunkte antworten nach demselben Muster — die YAML-Datei als
verschachteltes Objekt unter `data`:

```json
{
  "success": true,
  "data": {
    "settings": {
      "prefix": "&6[Event]&r",
      "main-world": "world",
      "save-player-location": true,
      "join-phase-duration": 30,
      "lobby-countdown": 10,
      "update-check": { "enabled": true, "modrinth-project-id": "pqJQdZ6R" }
    },
    "events": {
      "parkour": { "enabled": true, "command": "parkour", "display-name": "&aParkour" }
    }
  }
}
```

`/api/worlds/get` liefert unter `data` den `worlds`-Baum, `/api/equipment/get`
den `equipment-sets`-Baum, `/api/webconfig/get` die Abschnitte `web`,
`security`, `items` und `interface`.

---

### POST /api/config/save  ·  /worlds/save  ·  /equipment/save  ·  /webconfig/save

**Wichtig:** Die Nutzdaten müssen in ein `data`-Objekt verpackt werden —
genau so, wie der Lese-Endpunkt sie geliefert hat. Fehlt `data`, antwortet der
Server mit `success: false` und speichert nichts.

**Request Body:**
```json
{ "data": { "settings": { "prefix": "&6[Event]&r" }, "events": {} } }
```

**Response:**
```json
{ "success": true, "message": "Config gespeichert" }
```

---

### GET /api/language/get  ·  POST /api/language/save

```json
{ "success": true, "language": "de" }
```

**Save-Body:** `{ "language": "de" }` — gültig sind die Codes aus
`web/lang/languages.json` (`de`, `en`, `es`, `fr`, `ja`, `pl`, `ru`).
Geschrieben wird nach `config.yml` → `settings.language`.

---

### POST /api/reload

Führt denselben Reload aus wie `/eventpvp reload`.

**Request Body:** leer oder `{}`

```json
{ "success": true, "message": "OK" }
```

Dies ist die einzige Antwort, deren `message` das Panel tatsächlich anzeigt —
sie ist deshalb bewusst neutral gehalten.

---

### GET /api/mvworlds/list

Liefert den tatsächlichen Weltbestand des Servers — unabhängig davon, ob dazu ein
Preset in `worlds.yml` existiert. Speist das World-ID-Dropdown und die
Server-Welten-Übersicht.

```json
{
  "success": true,
  "data": {
    "available": true,
    "backend": "MV5",
    "supportsAdvancedOptions": true,
    "worlds": [
      {
        "name": "arena_1",
        "environment": "NORMAL",
        "worldType": "NORMAL",
        "loaded": true,
        "knownToMultiverse": true,
        "existsOnDisk": true,
        "usedBy": [{ "type": "world", "id": "arena_1", "field": "world-id" }]
      }
    ]
  }
}
```

- `backend`: `MV5` (Multiverse-Core-5-API), `LEGACY` (MV4 bzw. `mv`-Konsolenbefehle)
  oder `NONE` (kein Multiverse installiert).
- `supportsAdvancedOptions`: nur bei `MV5` sind `biome` und `generatorSettings` nutzbar.
- `usedBy`: wo die Welt referenziert wird — aus `worlds.yml` (Preset-Key,
  `clone-source-world`) und `config.yml` (`lobby-world`, `event-world`,
  `clone-source-event-world`).
- Ein Preset ohne passenden Eintrag in `worlds` ist ein **Platzhalter**: konfiguriert,
  aber ohne Welt auf dem Server.

---

### POST /api/mvworlds/create

Legt eine Welt über Multiverse an. Ausser `world` ist alles optional.

```json
{
  "world": "arena_2",
  "environment": "NORMAL",
  "worldType": "FLAT",
  "seed": "12345",
  "generator": "VoidGen",
  "generatorSettings": "{}",
  "biome": "plains",
  "generateStructures": true,
  "adjustSpawn": true
}
```

```json
{ "success": true, "jobId": "a1b2c3..." }
```

Die Antwort kommt sofort — die Chunk-Generierung läuft weiter und wird über
`/api/mvworlds/job` verfolgt. `environment` ist `NORMAL`, `NETHER` oder `THE_END`,
`worldType` eines von `NORMAL`, `FLAT`, `LARGE_BIOMES`, `AMPLIFIED`.

Ungültige Werte werden abgelehnt (`success: false`). `world` muss auf
`[A-Za-z0-9_-]{1,64}` passen; `seed`, `generator` und `biome` dürfen keine
Leerzeichen enthalten, weil sie im Legacy-Backend in eine Kommandozeile eingesetzt
werden.

---

### POST /api/mvworlds/action

```json
{ "action": "delete", "world": "arena_2", "backup": true }
```

```json
{ "success": true, "jobId": "a1b2c3..." }
```

- `action`: `load`, `unload` oder `delete`.
- `backup`: nur bei `delete` relevant, Standard `true`. Die Welt wird dann vor dem
  Löschen nach `plugins/<plugin>/backups/` gezippt.

**`delete` ist unwiderruflich** — der komplette Weltordner verschwindet. Ablauf:
Welt entladen → Backup (async) → löschen. Die Haupt-/Default-Welt des Servers und
reservierte Verzeichnisnamen (`plugins`, `logs`, …) werden abgelehnt.

---

### GET /api/mvworlds/job?id=…

```json
{
  "success": true,
  "data": {
    "id": "a1b2c3...",
    "action": "create",
    "worldName": "arena_2",
    "status": "RUNNING",
    "message": ""
  }
}
```

`status` ist `RUNNING`, `SUCCESS` oder `FAILED`; bei `FAILED` nennt `messageKey` den
übersetzbaren Grund (`mv.error.*` in den Web-Sprachdateien) und `detail` den technischen
Zusatz. Jobs werden nach 5 Minuten verworfen, danach antwortet der Endpunkt mit
`success: false`.

---

### GET /api/mvworlds/backups

```json
{
  "success": true,
  "data": {
    "backups": [
      {
        "file": "arena_1_20260809_141200.zip",
        "worldName": "arena_1",
        "timestamp": "20260809_141200",
        "sizeBytes": 4400000
      }
    ]
  }
}
```

Listet die Zips aus `plugins/<plugin>/backups/`. Der Weltname wird aus dem Dateinamen
(`<welt>_<yyyyMMdd_HHmmss>.zip`) geparst; bei fremden Zips im Ordner ist `worldName` `null`.

---

### POST /api/mvworlds/backup-action

```json
{ "action": "restore", "file": "arena_1_20260809_141200.zip", "target": "arena_1_restored" }
```

- `action: "restore"` — stellt das Backup als Welt `target` wieder her. Antwort ist eine
  Job-ID (`/api/mvworlds/job`). Ablauf: Ziel prüfen → entpacken nach `container/<target>`
  (mit Zip-Slip-Schutz; `session.lock` wird übersprungen) → über Multiverse importieren und
  laden. **Ein existierendes Ziel wird abgelehnt** (`mv.error.restoreTargetExists`) —
  Wiederherstellen überschreibt nie.
- `action: "delete"` — löscht die Zip-Datei sofort (keine Job-ID). Die Welt selbst ist
  nicht betroffen.

`file` muss ein reiner Dateiname aus dem Backup-Ordner sein — Pfadangaben (`../`, `/`, `\`)
werden mit `mv.error.backupFileInvalid` abgelehnt.

---

### GET /api/inventories/status

Liefert den aktuellen Status der Inventarverwaltung, den konfigurierten und aktiven Provider sowie die Zahl offener Guard-Sitzungen.

**Response 200:**
```json
{
  "success": true,
  "data": {
    "provider": "auto",
    "activeProvider": "InventoryBackup",
    "inventoryRestoreAvailable": true,
    "multiverseInventoriesLoaded": false,
    "openSessions": 0
  }
}
```

---

### POST /api/inventories/provider

Schaltet den Provider für die Inventarverwaltung in `config.yml` um.

**Request Body:**
```json
{ "provider": "auto" }
```
Gültige Werte: `"auto"`, `"inventoryrestore"`, `"none"`.

**Response 200:**
```json
{
  "success": true,
  "data": { "provider": "auto" }
}
```

---

### GET /api/inventories/list

Listet alle gespeicherten Backups eines Spielers auf.

**Query Parameter:**
- `player` (erforderlich): Spielername oder UUID. Offline-Spieler werden aufgelöst.
- `type` (optional): Filter nach Backup-Typ. Leer oder weggelassen = alle.

**Response 200:**
```json
{
  "success": true,
  "data": {
    "player": "069a79f4-44e9-4726-a5be-f25130f25137",
    "backups": [
      {
        "id": "20260810_120000",
        "type": "match",
        "createdAt": 1786363200000,
        "metadata": { "context": "PVP_MATCH" }
      }
    ]
  }
}
```

`createdAt` sind Millisekunden seit 1970. `metadata` ist frei belegbar und stammt aus der
Inventar-Backup-API; das Panel zeigt es nicht an, protokolliert es aber mit.

---

### GET /api/inventories/get

Lädt den Inhalt eines einzelnen Backups für die Gitter-Vorschau im Panel.

**Query Parameter:**
- `player` (erforderlich): Spielername oder UUID
- `id` (erforderlich): Backup-ID aus `/api/inventories/list`

**Response 200:**
```json
{
  "success": true,
  "data": {
    "id": "20260810_120000",
    "type": "match",
    "createdAt": 1786363200000,
    "metadata": {},
    "level": 30,
    "exp": 0.5,
    "contents": [
      {
        "slot": 0,
        "material": "DIAMOND_SWORD",
        "amount": 1,
        "displayName": "§bExcalibur",
        "lore": ["§7Ein Schwert"],
        "enchantments": { "sharpness": 5 }
      }
    ],
    "armor": [
      { "slot": 3, "material": "DIAMOND_HELMET", "amount": 1 }
    ],
    "offhand": { "slot": 0, "material": "SHIELD", "amount": 1 }
  }
}
```

Hinweise zum Schema:
- `contents` benutzt die Bukkit-Slotnummern: 0–8 ist die Hotbar, 9–35 das Hauptinventar.
- `armor` benutzt die Bukkit-Rüstungsindizes: 0 = Stiefel, 1 = Hose, 2 = Brustpanzer, 3 = Helm.
- `offhand` ist `null`, wenn die Nebenhand leer war.
- Leere Felder erscheinen **nicht** in den Listen — das Panel füllt sie beim Zeichnen auf.
- `displayName`, `lore` und `enchantments` fehlen, wenn das Item sie nicht trägt.
- Es werden bewusst nur Metadaten geliefert, **keine rohen NBT-Blobs**.

---

### POST /api/inventories/restore

Spielt ein Backup an den Spieler zurück.

**Request Body:**
```json
{
  "player": "069a79f4-44e9-4726-a5be-f25130f25137",
  "backupId": "20260810_120000",
  "clearBefore": true
}
```

`clearBefore` ist standardmäßig `true`. Auf `false` kommen die Items zu dem hinzu, was der
Spieler bereits trägt — was nicht passt, geht verloren.

**Response 200:**
```json
{
  "success": true,
  "data": {
    "outcome": "RESTORED",
    "queued": false
  }
}
```

Ist der Spieler offline, antwortet der Endpunkt mit `outcome: "QUEUED_FOR_JOIN"` und
`queued: true`; die Wiederherstellung läuft dann beim nächsten Beitritt.

**Ablehnungsgründe:**
- `inventory.error.sessionActive` — der Spieler ist gerade in einem Match oder Event. Sein Kit
  würde überschrieben, deshalb wird der Vorgang abgelehnt.
- `inventory.error.rateLimited` — mehr als 10 Wiederherstellungen in der laufenden Minute
  (panelweit, nicht je Spieler). Der Endpunkt legt Items im Spiel an; ohne diese Bremse wäre
  ein übernommenes Web-Login eine Item-Fabrik.

Jede Wiederherstellung wird mit Ziel-UUID, Backup-ID und Ergebnis als Warnung ins Serverlog
geschrieben.

---

### POST /api/inventories/delete

Löscht ein einzelnes Backup unwiderruflich.

**Request Body:**
```json
{
  "player": "069a79f4-44e9-4726-a5be-f25130f25137",
  "backupId": "20260810_120000"
}
```

**Response 200:**
```json
{ "success": true }
```

---

### GET /api/inventories/guard

Liest die offenen, nicht abgeschlossenen Sitzungen des Guard-Journals aus — etwa nach einem
Serverabsturz, bevor die automatische Wiederherstellung greift. Dazu kommen die gespeicherten
Rückkehrpositionen: ein Eintrag ohne offene Sitzung bedeutet, dass ein Spieler zurückgeblieben
ist, und das ist eine eigenständige Störung.

**Response 200:**
```json
{
  "success": true,
  "data": {
    "sessions": [
      {
        "player": "069a79f4-44e9-4726-a5be-f25130f25137",
        "playerName": "Notch",
        "online": false,
        "backupId": "20260810_115500",
        "phase": "orphaned",
        "context": "PVP_MATCH"
      }
    ],
    "returnLocations": [
      {
        "player": "069a79f4-44e9-4726-a5be-f25130f25137",
        "playerName": "Notch",
        "online": false,
        "world": "world",
        "x": 128.5,
        "y": 64.0,
        "z": -310.5,
        "reason": "pvp_match",
        "savedAt": 1786363200000,
        "worldLoaded": true
      }
    ]
  }
}
```

`context` ist einer von `PVP_MATCH`, `EVENT`, `WEB`, `MANUAL`. `worldLoaded: false` heißt,
dass die Zielwelt gerade nicht geladen ist — eine Rückkehr dorthin liefe ins Leere.

---

### GET /api/materials

Liefert den Item-Katalog des **laufenden** Servers. Das Panel speist daraus die Item-Auswahl,
die Verzauberungslisten und die Stapelgrößen — so wird dort nie ein Item angeboten, das der
Server gar nicht kennt.

Der Katalog ändert sich zur Laufzeit nicht und wird deshalb einmalig berechnet und gehalten.

**Response 200:**
```json
{
  "success": true,
  "data": {
    "materials": [
      {
        "name": "DIAMOND_SWORD",
        "category": "weapons",
        "maxStack": 1,
        "maxDurability": 1561,
        "enchantments": ["SHARPNESS", "LOOTING", "UNBREAKING"]
      },
      {
        "name": "DIAMOND_HELMET",
        "category": "armor",
        "maxStack": 1,
        "maxDurability": 363,
        "armorSlot": "helmet",
        "enchantments": ["PROTECTION", "UNBREAKING"]
      }
    ],
    "categories": ["weapons", "armor", "tools", "food", "potions",
                   "projectiles", "blocks", "redstone", "spawnEggs", "misc"],
    "enchantments": [
      { "key": "SHARPNESS", "maxLevel": 5, "startLevel": 1, "treasure": false, "curse": false }
    ],
    "itemFlags": ["HIDE_ENCHANTS", "HIDE_ATTRIBUTES"],
    "potionTypes": ["STRENGTH", "SWIFTNESS"],
    "potionEffects": ["SPEED", "STRENGTH"]
  }
}
```

Die optionalen Felder `block`, `edible`, `maxDurability`, `armorSlot` und `potion` erscheinen
nur, wenn sie zutreffen. Nicht enthalten sind Legacy-Materials, `AIR` und alles, was sich nicht
als Item ins Inventar legen lässt.

---

### GET /api/textures/overrides

Nennt die Materials, für die eine Textur aus dem Resourcepack des Servers vorliegt. Ist die
Funktion in der `web-config.yml` abgeschaltet (Standard), kommt eine leere Liste.

**Response 200:**
```json
{
  "success": true,
  "data": { "materials": ["DIAMOND_SWORD", "IRON_HELMET"] }
}
```

Die Bilder selbst liegen unter `/item-assets/override/<MATERIAL>.png`.

---

## Fehlerbehandlung

Fehler der Handler folgen dem Antwortschema:

```json
{ "success": false, "error": "Nicht authentifiziert" }
```

`sendError(...)` (z. B. bei 405/500) verwendet zusätzlich:

```json
{ "error": "Method Not Allowed", "code": 405 }
```

| Code | Bedeutung |
|---|---|
| **400** | Kein Token im Login-Body, ungültiges JSON |
| **401** | Keine oder abgelaufene Session; ungültiger Token |
| **403** | IP nicht in `security.allowed-ips` |
| **404** | Unbekannter Pfad (auch bei Directory-Traversal-Versuchen) |
| **405** | Falsche HTTP-Methode für den Endpunkt |
| **429** | Rate-Limit überschritten |
| **500** | Fehler beim Speichern oder beim Reload |

---

## Rate Limiting

> Frühere Fassungen dieser Doku behaupteten „derzeit kein Rate Limiting" — das
> war falsch, es existiert seit 1.0.8.

**100 Requests pro IP und 60-Sekunden-Fenster.** Wird das überschritten,
antwortet der Server mit **429** und setzt `Retry-After: 60`.

```json
{ "success": false, "error": "Rate limit exceeded" }
```

Das Limit greift nur auf den geschützten Endpunkten, nicht auf statischen
Dateien.

> **Behoben in 1.0.9:** Der Zähler wurde nie zurückgesetzt — trotz des
> Kommentars „per window" gab es kein Zeitfenster. Nach 100 Requests war eine IP
> **dauerhaft** gesperrt, bis der Server neu startete. Da das Panel allein alle
> 60 Sekunden den Status abfragt, traf das jeden längeren Konfigurationslauf.

---

## Caching der statischen Dateien

Seit 1.0.9 unterscheidet `StaticFileHandler`:

| Typ | Header |
|---|---|
| `.html`, `.js`, `.json` (inkl. `web/lang/*.json`) | `no-cache, no-store, must-revalidate` |
| Bilder, Schriften (`.png`, `.svg`, `.woff2`, …) | `public, max-age=3600` |

Vorher galt eine Stunde für **alles**, auch für die Sprachdateien — nach einem
Update zeigte das Panel deshalb rohe Key-Namen, bis der Cache ablief.

---

## cURL Beispiele

Login und Session in einer Cookie-Datei halten:

```bash
# 1. Token einloesen (Token kommt aus /eventpvp webtoken)
curl -c cookies.txt -X POST http://localhost:8085/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"token":"AB12-CD34-EF56"}'

# 2. Konfiguration laden
curl -b cookies.txt http://localhost:8085/api/config/get

# 3. Konfiguration speichern -- Nutzdaten unter "data"
curl -b cookies.txt -X POST http://localhost:8085/api/config/save \
  -H "Content-Type: application/json" \
  -d '{"data":{"settings":{"prefix":"&6[Event]&r"},"events":{}}}'

# 4. Reload
curl -b cookies.txt -X POST http://localhost:8085/api/reload
```

---

## JavaScript Beispiele

`credentials: 'include'` ist Pflicht, sonst wird das Session-Cookie nicht
mitgesendet.

```javascript
// Login
await fetch('/api/auth/login', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  credentials: 'include',
  body: JSON.stringify({ token })
});

// Laden
async function loadConfig() {
  const res  = await fetch('/api/config/get', { credentials: 'include' });
  const json = await res.json();
  return json.data;                     // Nutzdaten liegen unter "data"
}

// Speichern
async function saveConfig(config) {
  const res = await fetch('/api/config/save', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    credentials: 'include',
    body: JSON.stringify({ data: config })    // Verpackung nicht vergessen
  });
  const result = await res.json();
  if (!result.success) throw new Error(result.message || result.error);
}
```

---

## WebSocket (Zukünftig)

Geplant für Live-Updates der Konfiguration ohne Polling. Aktuell fragt das Panel
den Status alle 60 Sekunden per `GET /api/status` ab.

---

## Support

Bei API-Problemen:
1. Server-Log prüfen — die Handler loggen jeden Speichervorgang mit `[Web-API]`
2. Browser-Konsole prüfen (F12 → Netzwerk); auf **401** (Session abgelaufen)
   und **429** (Rate-Limit) achten
3. Statusendpunkt testen: `GET /api/status`
4. Zeigt das Panel rohe Key-Namen statt Text: einmal `Strg`+`Shift`+`R`
