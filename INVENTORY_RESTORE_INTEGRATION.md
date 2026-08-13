# Vollstaendige Integrations- & Architektur-Analyse: InventoryRestore in Event-PVP-Plugin

**Dokument-Version:** 2.1 (verifiziert gegen `InventoryRestore-0.0.7` und `Event-PVP-Plugin 1.0.9`)

> **Umsetzungsstand (09.08.2026): Phasen 0–4 und 6 sind implementiert.**
>
> | Phase | Stand | Wo |
> |---|---|---|
> | 0 Bugfix Backup-Zeitpunkt | **fertig** | `MatchManager.beginInventorySessions`, vor beiden `teleportPlayers`-Aufrufen |
> | 1 Service + Adapter + Factory | **fertig** | `de.zfzfg.core.inventory`, drei Adapter, Startup-Diagnose |
> | 2 Guard-Journal + Wiederanlauf | **fertig** | `guard/InventoryGuard`, `inventory-guard.yml`, `recoverOpenSessions()` im `onEnable` |
> | 3 Auto-Restore PvP | **fertig** | Match-Ende, Respawn, Quit; Ausschuettung im Erfolgs-Callback |
> | 4 Auto-Restore Events | **fertig** | `teleportBack` als zentraler Ausgang, Belohnungen ueber `scheduleRewards` |
> | 5 Befehle & GUI-Preview | **offen** | `/inventoryrestore` laeuft weiter auf dem Alt-System |
> | 6 Web-API + Panel | **fertig** | 7 Endpunkte, Karte „Inventar-Verwaltung", Modus-Umschaltung, Backup-Browser mit Gitter-Vorschau, Restore/Loeschen, Rate-Limit |
> | 7 Migration der Alt-Snapshots | **offen** | `InventorySnapshotStorage` schreibt weiter, Importer fehlt |
>
> Der Legacy-Betrieb ist als `provider: none` umgesetzt und im Panel als solcher
> gekennzeichnet: Multiverse-Inventories tauscht, das Plugin sammelt nur Snapshots und
> stellt nichts automatisch wieder her.
>
> Nicht umgesetzt und bewusst offen: `/pvp invdebug`, `/inventoryrestore undo`, die
> GUI-Vorschau ueber die API sowie der einmalige Importer der alten
> `inventory_backups.yml`. Die Testmatrix aus Abschnitt 11 ist **nicht** durchlaufen -
> verifiziert sind bisher nur Kompilierung, Build und das i18n-Audit.

Diese Dokumentation beschreibt lueckenlos, wie die API von **InventoryRestore
(`InventoryBackup-API 0.1.0`, API-Level 1)** in das **Event-PVP-Plugin** integriert werden kann,
um die fehleranfaellige Abhaengigkeit von **Multiverse-Inventories** abzuloesen.

Alle Aussagen zu Signaturen, Threading-Verhalten und Zeilennummern wurden gegen den
Quelltext geprueft. Stellen, die in Version 1.0 dieses Dokuments falsch oder
unvollstaendig waren, sind in [Abschnitt 0](#0-aenderungen-gegenueber-version-10) aufgefuehrt.

---

## Inhaltsverzeichnis
0. [Aenderungen gegenueber Version 1.0](#0-aenderungen-gegenueber-version-10)
1. [Executive Summary & Problemstellung](#1-executive-summary--problemstellung)
2. [Analyse von InventoryRestore (API & Grenzen)](#2-analyse-von-inventoryrestore-api--grenzen)
3. [Ist-Zustand im Event-PVP-Plugin & verifizierte Schwachstellen](#3-ist-zustand-im-event-pvp-plugin--verifizierte-schwachstellen)
4. [Leitplanken: Invarianten, die jede Integration einhalten muss](#4-leitplanken-invarianten-die-jede-integration-einhalten-muss)
5. [Vollstaendige Matrix aller Integrationsmoeglichkeiten](#5-vollstaendige-matrix-aller-integrationsmoeglichkeiten)
   - [5.1 PvP-Wager-System](#51-pvp-wager-system)
   - [5.2 Event-System](#52-event-system)
   - [5.3 Spectator- & Recovery-System](#53-spectator--recovery-system)
   - [5.4 Ingame-Befehle & GUI-Preview](#54-ingame-befehle--gui-preview)
   - [5.5 Web-Interface & REST-API](#55-web-interface--rest-api)
   - [5.6 Event-Interception & Filter](#56-event-interception--filter)
6. [Architektur- & Refactoring-Plan](#6-architektur--refactoring-plan)
7. [Crash- & Shutdown-Recovery (Guard-Journal)](#7-crash--shutdown-recovery-guard-journal)
8. [Migration & Koexistenz](#8-migration--koexistenz)
9. [Build, Dependency & Deployment](#9-build-dependency--deployment)
10. [Konfiguration & Dokumentation](#10-konfiguration--dokumentation)
11. [Test- & Abnahmematrix](#11-test--abnahmematrix)
12. [Rollout in Phasen](#12-rollout-in-phasen)
13. [Risikoregister](#13-risikoregister)
14. [Zusammenfassung der Vorteile](#14-zusammenfassung-der-vorteile)

---

## 0. Aenderungen gegenueber Version 1.0

### Sachliche Korrekturen

| # | Aussage in v1.0 | Tatsaechlicher Befund |
|---|---|---|
| K1 | „Erst nach erfolgreichem Snapshot-Handle wird das Inventar gecleart." | Falsch und schaedlich. `createBackup(Player, …)` ruft **synchron** `BackupSnapshot.of(player)` auf ([InventoryBackupService.java:77](file:///C:/Users/zfzfg/Documents/HammerMegaProjekte/selfmadePlugins/Plugins/InventoryRestore/InventoryRestore-0.0.7/plugin/src/main/java/com/zfzfg/inventorybackup/api/impl/InventoryBackupService.java#L77)); die Items sind beim Rueckkehren des Methodenaufrufs bereits erfasst. Nur der *Handle* kommt asynchron. Auf den Handle zu warten, bevor gecleart wird, erzeugt ein Zeitfenster von mehreren Ticks, in dem der Spieler in der Arena mit Survival-Inventar steht (Dupe-Fenster). **Richtig: sofort clearen, Handle im Callback nachtragen.** |
| K2 | Backup-Punkt im Match ist `MatchManager:275`. | Es gibt **zwei** Start-Pfade: [MatchManager.java:275](file:///c:/Users/zfzfg/Documents/HammerMegaProjekte/selfmadePlugins/Plugins/Event-PVP-Plugins/Event-PVP-Plugin-1.0.8%20-%20Copy/src/main/java/de/zfzfg/pvpwager/managers/MatchManager.java#L275) (`continueMatchStart`) und [MatchManager.java:443](file:///c:/Users/zfzfg/Documents/HammerMegaProjekte/selfmadePlugins/Plugins/Event-PVP-Plugins/Event-PVP-Plugin-1.0.8%20-%20Copy/src/main/java/de/zfzfg/pvpwager/managers/MatchManager.java#L443) (`continueMatchSetup`, Emergency-Pfad). Wer nur einen umbaut, verliert im anderen Pfad alles. |
| K3 | Multiverse-Inventories sei eine „zwingende Abhaengigkeit". | In [plugin.yml](file:///c:/Users/zfzfg/Documents/HammerMegaProjekte/selfmadePlugins/Plugins/Event-PVP-Plugins/Event-PVP-Plugin-1.0.8%20-%20Copy/src/main/resources/plugin.yml) steht Multiverse-Inventories **nicht** in `depend`/`softdepend` — nur `Multiverse-Core`. Es ist eine reine Dokumentations-Empfehlung (`DESCRIPTION.md:64`) plus eine Laufzeit-Warnmeldung (`multiverse-inventories-recommended`). Das aendert nichts am Nutzen der Migration, aber die Formulierung „Abhaengigkeit entfernen" ist zu stark. |
| K4 | `giveMissingItems` sei „ideal fuer Wager-Rueckgaben". | Nein. Die Methode gibt nur Stacks, die der Spieler *nicht* traegt. Bei Wager-Items (oft identische Materialien wie 64x Diamant) fuehrt das zu **stiller Unterausschuettung**. `giveMissingItems` ist ein Reparaturwerkzeug fuer „Restore ist teilweise durchgelaufen", nicht der Ausschuettungsweg. Fuer Wager bleibt `InventoryUtil.giveItems` richtig. |
| K5 | „100 % atomar & Thread-safe durch CompletableFuture". | `CompletableFuture` macht nichts atomar. Die API garantiert: I/O laeuft async, Completion auf dem Main-Thread, kein halb gelesener Snapshot (`loadBackup` liefert leer statt Bruchstueck). Atomar wird die *Fachlogik* erst durch das Guard-Journal aus [Abschnitt 7](#7-crash--shutdown-recovery-guard-journal). |
| K6 | `restore(...)` „feuert `InventoryRestoreEvent`" — ohne Ergebnisbehandlung. | `restore` liefert `CompletableFuture<RestoreResult>` mit fuenf Zustaenden (`APPLIED`, `QUEUED_FOR_JOIN`, `NOT_FOUND`, `CANCELLED`, `FAILED`). v1.0 nutzt ueberall `thenAccept` ohne Auswertung — genau dort entstehen Item-Verluste. Siehe [Regel I4](#4-leitplanken-invarianten-die-jede-integration-einhalten-muss). |

### Neue Inhalte

- **B1 – Kritischer Ist-Bug:** Der Match-Snapshot wird **nach** dem Arena-Teleport gezogen ([Abschnitt 3.1](#31-kritisch-match-snapshot-wird-zu-spaet-gezogen)).
- Invarianten-Kapitel als verbindliche Regeln fuer alle Integrationspunkte (Abschnitt 4).
- Crash-/Shutdown-Recovery mit persistentem Guard-Journal (Abschnitt 7) — v1.0 haelt Handles nur im `Match`-Objekt, also im RAM; ein Absturz bedeutete weiterhin Totalverlust.
- Was die API **nicht** sichert (Enderchest, Potion-Effekte, Health/Food, Gamemode) — [Abschnitt 2.4](#24-was-die-api-nicht-abdeckt).
- Reihenfolge-Regeln Teleport ↔ Restore ↔ Ausschuettung, inkl. Sequenzdiagrammen.
- Migration der Alt-Snapshots statt Wegwerfen (Abschnitt 8).
- Build-/Dependency-Realitaet: die API liegt in keinem oeffentlichen Repo (Abschnitt 9).
- Test- und Abnahmematrix, Rollout-Phasen, Risikoregister (Abschnitte 11–13).

---

## 1. Executive Summary & Problemstellung

### Das Problem mit Multiverse-Inventories
Das Plugin verlaesst sich darauf, dass Multiverse-Inventories Inventare beim Weltwechsel
zwischen Hauptwelt (Survival) und Arena-/Lobby-Welten tauscht:

- **Fehlkonfiguration = Datenverlust:** Admins muessen Weltgruppen manuell anlegen
  (`/mvinv group`). Fehlt die Gruppe, loeschen die `getInventory().clear()`-Aufrufe in
  `MatchManager` und `EventSession` echte Survival-Items unwiderruflich.
- **Unsichtbare Kopplung:** Die Korrektheit des Plugins haengt an der Konfiguration eines
  Fremdplugins, das das Plugin selbst weder pruefen noch reparieren kann.
- **Race Conditions:** Bei schnellen Teleports (Arena-Setup, Emergency-Teleport, Respawn)
  konkurriert der Mv-Inv-Weltwechsel-Hook mit den eigenen Inventar-Operationen.
- **Crash/Ragequit:** Faellt der Server waehrend eines Matches aus, bleibt das Inventar im
  Kit-Zustand oder leer — es gibt keinen Wiederanlaufpfad.

### Die Loesung durch InventoryRestore
`InventoryBackup` liefert eine asynchrone Java-API mit UUID-Ordnerstruktur, typisierten
Metadaten, GUI-Preview und persistenter Offline-Join-Queue (`pending-restores.yml`).
Das Event-PVP-Plugin bekommt damit die **explizite Kontrolle** darueber, wann welches
Inventar gesichert und wiederhergestellt wird, statt sie an einen Weltwechsel-Hook zu
delegieren.

### Was diese Migration nicht automatisch loest
InventoryRestore ist ein Backup-Werkzeug, keine Zustandsmaschine. Die eigentliche
Zuverlaessigkeit entsteht erst durch das, was das Event-PVP-Plugin drumherum baut:
ein persistentes Journal offener Sitzungen, Exactly-Once-Ausschuettung und ein
definierter Wiederanlauf nach Crash. Diese Teile sind der Kern von
[Abschnitt 6](#6-architektur--refactoring-plan) und [7](#7-crash--shutdown-recovery-guard-journal).

---

## 2. Analyse von InventoryRestore (API & Grenzen)

Quelle: `C:\Users\zfzfg\Documents\HammerMegaProjekte\selfmadePlugins\Plugins\InventoryRestore\InventoryRestore-0.0.7`
(`api/` = API-Modul, `API.md` = Entwicklerdoku).

### 2.1 Koordinaten

- **Artifact:** `com.zfzfg:InventoryBackup-API:0.1.0` (Scope `provided`, **nicht shaden**)
- **Plugin-Name auf dem Server:** `InventoryBackup` → `softdepend: [InventoryBackup]`
- **Einstieg:** `InventoryBackupProvider.get()` / `.getOptional()` / `.isAvailable()`
- **API-Level:** `InventoryBackupAPI.API_VERSION == 1`, zur Laufzeit gegen
  `api.getApiVersion()` pruefen
- **Laufzeit:** Java 17, Spigot/Paper 1.20+ — das Event-PVP-Plugin kompiliert aktuell gegen
  `spigot-api 1.19.4` mit `api-version: 1.19`. Kein Konflikt (die API nutzt keine 1.20-Typen),
  aber die Doku muss klarstellen: **InventoryRestore selbst verlangt 1.20+**.

### 2.2 API-Methoden (vollstaendig, verifiziert)

| Methode | Rueckgabe | Threading / Verhalten |
|---|---|---|
| `createBackup(Player, BackupRequest)` | `CF<Optional<BackupHandle>>` | **Nur Main-Thread** (Future scheitert sonst mit `IllegalStateException`). Snapshot wird **synchron** erfasst, Schreiben async. Leer = von `BackupCreateEvent` gecancelt oder Schreibfehler. |
| `createBackup(UUID, String name, BackupSnapshot, BackupRequest)` | `CF<Optional<BackupHandle>>` | Von **jedem Thread** sicher. Weg fuer Offline-Spieler und selbst gebaute Snapshots. |
| `listBackups(UUID)` / `listBackups(UUID, type)` | `CF<List<BackupHandle>>` | Neueste zuerst. `type == null` = alle. |
| `getLatestBackup(UUID, type)` | `CF<Optional<BackupHandle>>` | Bequemer Zugriff auf `list…get(0)`. |
| `getBackup(UUID, String backupId)` | `CF<Optional<BackupHandle>>` | **Der Schluessel fuer Persistenz:** `handle.id()` ist stabil und speicherbar; nach Restart wird der Handle darueber rekonstruiert. In v1.0 nicht erwaehnt. |
| `loadBackup(BackupHandle)` | `CF<Optional<BackupSnapshot>>` | Leer bei fehlender Datei *oder* Dekodierfehler — nie ein halber Snapshot. |
| `restore(UUID target, BackupHandle, RestoreOptions)` | `CF<RestoreResult>` | Online → `APPLIED`; offline → `QUEUED_FOR_JOIN` (persistent). Feuert cancelbares `InventoryRestoreEvent`. Ziel muss nicht Eigentuemer sein. |
| `giveMissingItems(UUID, BackupHandle)` | `CF<Integer>` | Nur online; `-1` bei Fehler/offline. Siehe Korrektur K4. |
| `queueRestoreOnJoin(UUID, BackupHandle, RestoreOptions)` | `CF<Boolean>` | Erzwingt Join-Pfad auch fuer Online-Spieler. **Ersetzt** einen bereits gequeueten Restore. |
| `getPendingRestore(UUID)` | `CF<Optional<PendingRestore>>` | Pruefen, bevor ueberschrieben wird (siehe Regel I6). |
| `cancelPendingRestore(UUID)` | `CF<Boolean>` | Queue-Eintrag verwerfen. |
| `deleteBackup(BackupHandle)` / `deleteBackups(UUID, type)` | `CF<Boolean>` / `CF<Integer>` | Feuert `BackupDeletedEvent`. |
| `openPreview(Player, BackupHandle)` | `boolean` | **Nur Main-Thread**, `false` = abgelehnt. `true` heisst „angenommen", nicht „offen". |
| `resolvePlayerId(name)` / `resolvePlayerName(uuid)` | `CF<Optional<…>>` | Lokaler Index, kein Mojang-Call. Kennt nur Spieler, die dieser Server gesehen hat. |

**Threading-Merksatz:** Jedes Future wird **auf dem Main-Thread** komplettiert; in
`thenAccept` darf direkt Bukkit aufgerufen werden. `.join()`/`.get()` auf dem Main-Thread
ist ein garantierter Deadlock.

### 2.3 Request- und Options-Struktur

```java
BackupRequest request = BackupRequest.builder()
    .type("pvp-pre-match")              // normalisiert auf [a-z0-9-], max 32 Zeichen
    .sourcePlugin(plugin)               // erscheint in /inv <player> list
    .metadata("match_id", matchId.toString())
    .metadata("opponent", opponentName)
    .metadata("arena", arenaName)
    .metadata("origin_world", world.getName())
    .build();

RestoreOptions options = RestoreOptions.builder()
    .contents(true).armor(true).offhand(true)
    .level(true).exp(true)
    .clearBefore(true)                  // true = Rollback, false = dazulegen
    .dropOverflow(true)                 // was nicht passt, faellt vor die Fuesse
    .build();
// Kurzformen: RestoreOptions.all(), RestoreOptions.itemsOnly()
```

**Typ-Katalog fuer dieses Plugin** (alle ≤ 32 Zeichen, bereits normalisiert):

| Typ | Wann erzeugt | Lebensdauer |
|---|---|---|
| `pvp-pre-match` | vor dem Arena-Teleport, pro Spieler | bis Match sauber beendet |
| `pvp-post-match` | optional nach Restore, forensisch | Pruning nach `retain-days` |
| `event-pre-join` | vor dem Lobby-Teleport | bis Event-Ende / Ausscheiden |
| `event-post` | optional nach Restore | Pruning |
| `guard-recovery` | beim Wiederanlauf nach Crash gefundene Reste | manuell |

### 2.4 Was die API **nicht** abdeckt

`BackupSnapshot` besteht aus `contents`, `armor`, `offhand`, `level`, `exp` — mehr nicht.
Damit wird **nicht** gesichert oder wiederhergestellt:

| Nicht abgedeckt | Konsequenz fuer dieses Plugin |
|---|---|
| **Enderchest** | Wenn ein Kit oder Event die Enderchest anfasst, muss das Plugin sie selbst sichern (eigener Snapshot ueber die `UUID`-Overload, Typ `pvp-pre-match-ec`). Sonst: aktuell kein Problem, da keine Enderchest-Manipulation im Code — **muss bei kuenftigen Kits geprueft werden**. |
| **Potion-Effekte** | Kit-Effekte bleiben nach dem Match aktiv. Explizit `player.getActivePotionEffects()` leeren beim Rueckteleport. |
| **Health / Food / Saturation** | Wird beim Match-Start bereits gesetzt; beim Rueckweg ebenfalls definiert setzen. |
| **Gamemode** | Wird vom Plugin ohnehin gesetzt (`SURVIVAL` / `ADVENTURE` / `SPECTATOR`) — beibehalten. |
| **Position** | Bleibt Aufgabe von `originalLocations` in `Match` / `EventSession`. |
| **Attribute, Advancements, Vault-Guthaben** | Ausserhalb des Scopes; Geld laeuft weiterhin ueber Vault. |

### 2.5 Event-Lifecycle

| Event | Cancelbar | Zeitpunkt |
|---|---|---|
| `BackupCreateEvent` | ja | vor dem Schreiben; `setRequest(...)` erlaubt Retyping/Metadaten |
| `BackupCreatedEvent` | nein | nach dem Schreiben |
| `InventoryRestoreEvent` | ja | vor dem Anwenden; `setSnapshot(...)` erlaubt Filtern |
| `InventoryRestoredEvent` | nein | nach dem Anwenden |
| `BackupDeletedEvent` | nein | nach Loeschung; `Reason` = `API`/`COMMAND`/`EXPIRED` |

Die Event-Klassen liegen **im Plugin-Jar**, nicht nur im API-Modul — auf sie kann auch
ohne Compile-Dependency reagiert werden. Fuer gequeuete Offline-Restores feuert
`InventoryRestoreEvent` **beim Join**, nicht beim Einreihen.

---

## 3. Ist-Zustand im Event-PVP-Plugin & verifizierte Schwachstellen

### 3.1 KRITISCH: Match-Snapshot wird zu spaet gezogen

Das ist ein **bestehender Bug**, unabhaengig von dieser Migration, und er entwertet
jeden naiven Umbau auf InventoryRestore.

In [MatchManager.java:270-285](file:///c:/Users/zfzfg/Documents/HammerMegaProjekte/selfmadePlugins/Plugins/Event-PVP-Plugins/Event-PVP-Plugin-1.0.8%20-%20Copy/src/main/java/de/zfzfg/pvpwager/managers/MatchManager.java#L270-L285) laeuft die Reihenfolge so:

```
spawnManager.teleportPlayers(...)        // Spieler ist jetzt in der ARENA-Welt
  └─ runTaskLater(...)
       ├─ saveSnapshotWithIdsAsync(...)  // Snapshot der ARENA-Welt-Inventare (!)
       └─ getInventory().clear()
```

Mit aktivem Multiverse-Inventories hat der Weltwechsel das Survival-Inventar **bereits
ausgetauscht**. Der „Pre-Match-Snapshot" enthaelt also das (in der Regel leere)
Arena-Welt-Inventar. Ein Restore aus diesem Snapshot wuerde das Survival-Inventar
**loeschen** statt es zu retten.

`EventSession` macht es richtig — dort steht sogar der Kommentar dazu
([EventSession.java:530-539](file:///c:/Users/zfzfg/Documents/HammerMegaProjekte/selfmadePlugins/Plugins/Event-PVP-Plugins/Event-PVP-Plugin-1.0.8%20-%20Copy/src/main/java/de/zfzfg/eventplugin/session/EventSession.java#L530-L539)):
*„Jetzt VOR dem Teleport ausfuehren, damit die Welt im Snapshot die urspruengliche Welt ist."*

**Fix (Voraussetzung fuer alles Weitere):** Das Backup gehoert dorthin, wo bereits die
Original-Position erfasst wird — [MatchManager.java:119-120](file:///c:/Users/zfzfg/Documents/HammerMegaProjekte/selfmadePlugins/Plugins/Event-PVP-Plugins/Event-PVP-Plugin-1.0.8%20-%20Copy/src/main/java/de/zfzfg/pvpwager/managers/MatchManager.java#L119-L120)
in `startMatchSetup`, bzw. spaetestens unmittelbar vor `teleportPlayers`. Beides —
Position und Inventar — beschreibt denselben „Zustand vor dem Match" und muss zum selben
Zeitpunkt eingefroren werden.

### 3.2 Zwei Start-Pfade

| Pfad | Zeile | Kontext |
|---|---|---|
| `continueMatchStart` | [MatchManager.java:275](file:///c:/Users/zfzfg/Documents/HammerMegaProjekte/selfmadePlugins/Plugins/Event-PVP-Plugins/Event-PVP-Plugin-1.0.8%20-%20Copy/src/main/java/de/zfzfg/pvpwager/managers/MatchManager.java#L275) | Normalfall nach Weltladung |
| `continueMatchSetup` | [MatchManager.java:443](file:///c:/Users/zfzfg/Documents/HammerMegaProjekte/selfmadePlugins/Plugins/Event-PVP-Plugins/Event-PVP-Plugin-1.0.8%20-%20Copy/src/main/java/de/zfzfg/pvpwager/managers/MatchManager.java#L443) | Emergency-Teleport-Pfad |

Beide clearen Inventar + Ruestung und legen das Kit an. Nach dem Umbau darf es **genau
einen** gemeinsamen Einstiegspunkt geben (`prepareForMatch(match, player)`), damit ein
dritter Pfad nicht wieder still am Backup vorbeilaeuft.

### 3.3 Der alte Backup-Mechanismus

[InventorySnapshotStorage.java](file:///c:/Users/zfzfg/Documents/HammerMegaProjekte/selfmadePlugins/Plugins/Event-PVP-Plugins/Event-PVP-Plugin-1.0.8%20-%20Copy/src/main/java/de/zfzfg/eventplugin/storage/InventorySnapshotStorage.java) (717 Zeilen, statische Utility-Klasse):

- **Dateien:** `inventory_backups.yml` (Pre), `inventory_post_backups.yml` (Post)
- **IDs:** 4-stellig numerisch, praefixiert (`MATCH0001`, `EVENT0001`)
- **Befehl:** `/inventoryrestore <id>` ([InventoryRestoreCommand.java](file:///c:/Users/zfzfg/Documents/HammerMegaProjekte/selfmadePlugins/Plugins/Event-PVP-Plugins/Event-PVP-Plugin-1.0.8%20-%20Copy/src/main/java/de/zfzfg/eventplugin/commands/InventoryRestoreCommand.java), 199 Zeilen)
- **Pruning:** [EventPlugin.java:369](file:///c:/Users/zfzfg/Documents/HammerMegaProjekte/selfmadePlugins/Plugins/Event-PVP-Plugins/Event-PVP-Plugin-1.0.8%20-%20Copy/src/main/java/de/zfzfg/eventplugin/EventPlugin.java#L369)

Schwachstellen:

1. **Monolithische YAML-Dateien** — alle Spieler in einer Datei; jeder Schreib- und
   Pruning-Vorgang serialisiert alles unter `synchronized (lock)`.
2. **Reines Notfall-Archiv ohne Automatik** — nichts wird jemals automatisch
   zurueckgespielt; der Snapshot existiert nur fuer den manuellen Admin-Befehl.
3. **Keine Offline-Queue** — Disconnect im Match = kein Rueckweg.
4. **Keine Vorschau** — Admins restaurieren blind.
5. **Kein Crash-Wiederanlauf** — offene Match-/Event-Zustaende sind nach einem Absturz
   nicht mehr aufloesbar, weil die Zuordnung nur im RAM lag.
6. **Snapshot-Zeitpunkt falsch im PvP-Pfad** — siehe 3.1.

### 3.4 Item-Verlust bei `keepInventory: false`

In [PvPListener.java:180-186](file:///c:/Users/zfzfg/Documents/HammerMegaProjekte/selfmadePlugins/Plugins/Event-PVP-Plugins/Event-PVP-Plugin-1.0.8%20-%20Copy/src/main/java/de/zfzfg/pvpwager/listeners/PvPListener.java#L180-L186)
werden Drops geleert, `setKeepInventory(false)` gesetzt und Inventar + Ruestung gecleart —
mit dem Kommentar, Per-World-Inventory kuemmere sich um den Rest. Ohne Mv-Inv respawnt der
Spieler leer. Der Respawn-Handler
([PvPListener.java:199-240](file:///c:/Users/zfzfg/Documents/HammerMegaProjekte/selfmadePlugins/Plugins/Event-PVP-Plugins/Event-PVP-Plugin-1.0.8%20-%20Copy/src/main/java/de/zfzfg/pvpwager/listeners/PvPListener.java#L199-L240))
regelt heute ausschliesslich die Position, nie das Inventar. Genau dort muss der Restore andocken.

### 3.5 Shutdown-Pfad ohne Async-Moeglichkeit

`endMatchOnShutdown` ([MatchManager.java:1165](file:///c:/Users/zfzfg/Documents/HammerMegaProjekte/selfmadePlugins/Plugins/Event-PVP-Plugins/Event-PVP-Plugin-1.0.8%20-%20Copy/src/main/java/de/zfzfg/pvpwager/managers/MatchManager.java#L1165))
laeuft bewusst **ohne verzoegerte Tasks**, weil im `onDisable` der Scheduler bereits
heruntergefahren wird. Damit gilt: **Waehrend des Shutdowns kann kein
InventoryRestore-Future mehr komplettieren** (die Completion braucht den Main-Thread-Tick).
Jede Loesung, die sich beim Herunterfahren auf `restore(...).thenAccept(...)` verlaesst,
verliert Items. Antwort darauf: Abschnitt 7.

---

## 4. Leitplanken: Invarianten, die jede Integration einhalten muss

Diese acht Regeln sind der eigentliche Kern des Umbaus. Jeder Integrationspunkt in
Abschnitt 5 verweist auf sie.

| ID | Invariante | Begruendung |
|---|---|---|
| **I1** | **Backup vor dem Weltwechsel.** Der Snapshot wird im selben Tick erfasst, in dem auch `originalLocations` gesetzt wird — immer noch in der Ursprungswelt. | Siehe 3.1: nach dem Teleport ist das Inventar bereits ein anderes. |
| **I2** | **Nie clearen ohne erfassten Snapshot.** `BackupSnapshot.of(player)` wird synchron gehalten, *bevor* `clear()` laeuft. Der Handle darf nachlaufen; der Snapshot nicht. | Verhindert das Dupe-Fenster aus Korrektur K1 und den Totalverlust bei Schreibfehler. |
| **I3** | **Restore erst nach abgeschlossenem Weltwechsel**, plus 1 Tick. In der Koexistenzphase mit Mv-Inv wuerde ein Restore vor dem Teleport durch den Weltwechsel-Hook ueberschrieben. | Reihenfolge: `teleport → PlayerChangedWorldEvent → 1 Tick → restore`. |
| **I4** | **`RestoreResult` immer auswerten.** `APPLIED` und `QUEUED_FOR_JOIN` = Erfolg; `NOT_FOUND`, `CANCELLED`, `FAILED` = Alarm: Journal-Eintrag bleibt offen, Admin-Warnung im Log, Spieler bekommt Hinweis. | Ohne das laufen Fehler still ins Leere. |
| **I5** | **Ausschuettung nach Restore, nie davor.** Wager-Gewinne und Event-Rewards erst im Erfolgs-Callback vergeben — ein `clearBefore(true)`-Restore wuerde vorher gegebene Items loeschen. | Sonst: Gewinner verliert seinen Gewinn im selben Tick. |
| **I6** | **Exactly-Once.** Jede Restore-/Ausschuettungs-Aktion laeuft ueber das Guard-Journal und ist idempotent; Vorbild ist das bestehende Flag `wagerItemsReturned` in [Match.java:29](file:///c:/Users/zfzfg/Documents/HammerMegaProjekte/selfmadePlugins/Plugins/Event-PVP-Plugins/Event-PVP-Plugin-1.0.8%20-%20Copy/src/main/java/de/zfzfg/pvpwager/models/Match.java#L29). | Doppeltod, Quit-waehrend-Restore und Shutdown-Rennen erzeugen sonst Dupes. |
| **I7** | **Ein Spieler = eine offene Sitzung.** Vor jedem neuen `pre`-Backup pruefen, ob im Journal bereits ein offener Eintrag existiert (z. B. Event beitreten waehrend eines Matches). Dann: neues Backup verweigern, nicht ueberschreiben. | Ein zweites Backup ueber dem Kit-Zustand macht das erste unbrauchbar. Gleiches gilt fuer `queueRestoreOnJoin`, das bestehende Queue-Eintraege **ersetzt** → vorher `getPendingRestore` pruefen. |
| **I8** | **API nicht in einem Feld cachen.** Pro Aufruf ueber `InventoryBackupProvider.getOptional()` aufloesen. | `API.md` §2: ein `/reload` ersetzt die Registrierung; ein gecachter Verweis zeigt danach auf eine tote Instanz. |

---

## 5. Vollstaendige Matrix aller Integrationsmoeglichkeiten

```
+---------------------------------------------------------------------------------------+
|                                EVENT-PVP-PLUGIN                                        |
|                                                                                        |
|   +--------------------------+       +--------------------------+                      |
|   |    PvP Wager Modul       |       |      Event Modul         |                      |
|   | (MatchManager, Listener) |       | (EventSession, Listener) |                      |
|   +------------+-------------+       +-----------+--------------+                      |
|                |                                 |                                     |
|                +----------------+----------------+                                     |
|                                 v                                                      |
|               +-----------------------------------+     +---------------------------+  |
|               |      InventoryBackupService       |<--->|      Guard-Journal        |  |
|               |  (Interface, Provider-agnostisch) |     |  inventory-guard.yml      |  |
|               +-----------------+-----------------+     |  uuid -> {backupId,       |  |
|                                 |                       |   context, phase, ts}     |  |
|          +----------------------+---------------------+ +---------------------------+  |
|          v                      v                      v                               |
|  InventoryRestoreAdapter  InternalFallbackAdapter  NoOpAdapter                          |
|          |                                                                              |
|          v                                                                              |
|  +-----------------------------------+                                                  |
|  |    InventoryBackup-API (0.1.0)    |                                                  |
|  +-----------------------------------+                                                  |
|    |               |               |                                                    |
|    v               v               v                                                    |
|  Async Storage  Offline Queue   GUI Preview                                             |
|  (UUID-Ordner)  (onJoin Hook)   (/inv preview)                                          |
+---------------------------------------------------------------------------------------+
```

---

### 5.1 PvP-Wager-System

#### A. Match-Vorbereitung (`startMatchSetup` + beide Start-Pfade)

- **Dateien:** `MatchManager.java` — [Z. 111-121](file:///c:/Users/zfzfg/Documents/HammerMegaProjekte/selfmadePlugins/Plugins/Event-PVP-Plugins/Event-PVP-Plugin-1.0.8%20-%20Copy/src/main/java/de/zfzfg/pvpwager/managers/MatchManager.java#L111-L121), [Z. 270-290](file:///c:/Users/zfzfg/Documents/HammerMegaProjekte/selfmadePlugins/Plugins/Event-PVP-Plugins/Event-PVP-Plugin-1.0.8%20-%20Copy/src/main/java/de/zfzfg/pvpwager/managers/MatchManager.java#L270-L290), [Z. 437-455](file:///c:/Users/zfzfg/Documents/HammerMegaProjekte/selfmadePlugins/Plugins/Event-PVP-Plugins/Event-PVP-Plugin-1.0.8%20-%20Copy/src/main/java/de/zfzfg/pvpwager/managers/MatchManager.java#L437-L455)
- **Ist:** Snapshot nach dem Teleport (Bug 3.1), danach hart clearen, Kit anlegen.
- **Soll** (Regeln I1, I2, I7):

```java
// in startMatchSetup, direkt neben originalLocations.put(...)
for (Player p : List.of(player1, player2)) {
    if (guard.hasOpenSession(p.getUniqueId())) {          // I7
        abortMatch(match, p, "inventory.session-already-open");
        return;
    }
    // I2: Snapshot synchron erfassen, als RAM-Fallback halten
    BackupSnapshot snapshot = BackupSnapshot.of(p);
    match.setFallbackSnapshot(p.getUniqueId(), snapshot);

    backups.createBackup(p, BackupRequest.builder()
            .type("pvp-pre-match")
            .sourcePlugin(plugin)
            .metadata("match_id", match.getMatchId().toString())
            .metadata("match_short_id", match.getEventMatchIdShort())
            .metadata("opponent", match.getOpponent(p).getName())
            .metadata("arena", match.getArena().getName())
            .metadata("origin_world", p.getWorld().getName())
            .build())
        .thenAccept(opt -> {
            if (opt.isPresent()) {
                match.setBackupId(p.getUniqueId(), opt.get().id());
                guard.open(p.getUniqueId(), GuardContext.PVP_MATCH,
                           match.getMatchId(), opt.get().id());   // persistiert!
            } else {
                // I4: Schreiben gescheitert oder gecancelt
                plugin.getLogger().severe("Pre-Match-Backup fehlgeschlagen: " + p.getName());
                guard.openWithoutHandle(p.getUniqueId(), GuardContext.PVP_MATCH,
                                        match.getMatchId());
                // Policy aus config: abort-match-on-backup-failure
            }
        });
}
// Clear + Kit laufen unveraendert weiter -- KEIN Warten auf den Handle (K1)
```

Sequenz:

```
Tick 0 | startMatchSetup: originalLocations + BackupSnapshot.of() [synchron]
       |                  createBackup(...) abgesetzt
Tick 0 | teleportPlayers -> Arena
Tick n | clear() + Kit anlegen                (Snapshot ist laengst erfasst)
Tick m | Future komplettiert -> backupId in Match + Guard-Journal geschrieben
```

#### B. Match-Ende, Sieg (`endMatch` → `distributeWinnings`)

- **Datei:** [MatchManager.java:757](file:///c:/Users/zfzfg/Documents/HammerMegaProjekte/selfmadePlugins/Plugins/Event-PVP-Plugins/Event-PVP-Plugin-1.0.8%20-%20Copy/src/main/java/de/zfzfg/pvpwager/managers/MatchManager.java#L757), [:877](file:///c:/Users/zfzfg/Documents/HammerMegaProjekte/selfmadePlugins/Plugins/Event-PVP-Plugins/Event-PVP-Plugin-1.0.8%20-%20Copy/src/main/java/de/zfzfg/pvpwager/managers/MatchManager.java#L877)
- **Soll** (I3, I4, I5, I6):

```
1. Teleport zurueck zur originalLocation
2. Auf PlayerChangedWorldEvent warten (oder 1 Tick nach bestaetigtem Teleport)   [I3]
3. restore(uuid, handle, RestoreOptions.all())  -- clearBefore(true)
4. Ergebnis auswerten:                                                          [I4]
     APPLIED         -> weiter zu 5
     QUEUED_FOR_JOIN -> Journal bleibt offen, Wager-Items zusaetzlich queuen
     NOT_FOUND/FAILED/CANCELLED -> RAM-Fallback-Snapshot anwenden,
                                   sonst Journal offen lassen + Admin-Alarm
5. Erst jetzt: InventoryUtil.giveItems(winner, wonItems) + Vault-Auszahlung     [I5]
6. guard.close(uuid) -- idempotent, verhindert Doppelausschuettung              [I6]
7. Backup je nach cleanup-backups-after-match loeschen oder als
   pvp-post-match behalten
```

Der Fall `QUEUED_FOR_JOIN` verdient besondere Sorgfalt: der Gewinner ist offline gegangen,
bevor der Restore lief. Dann duerfen die Wager-Items **nicht** verfallen — sie muessen in
ein zusammengesetztes Backup wandern (Snapshot + Gewinn-Items ueber die
`createBackup(UUID, …)`-Overload) und dieses wird gequeued. Das ist der einzige Weg, mit
dem die API Items an einen Offline-Spieler uebergibt.

#### C. Match-Ende, Verlierer (`teleportPlayerBack`)

- **Datei:** [MatchManager.java:990](file:///c:/Users/zfzfg/Documents/HammerMegaProjekte/selfmadePlugins/Plugins/Event-PVP-Plugins/Event-PVP-Plugin-1.0.8%20-%20Copy/src/main/java/de/zfzfg/pvpwager/managers/MatchManager.java#L990)
- Identischer Ablauf wie B, ohne Schritt 5. Das Pre-Match-Backup bildet bereits den Stand
  **nach** Abzug des Wetteinsatzes ab — der Verlierer bekommt also korrekt nichts zurueck.
  Voraussetzung: der Wager-Abzug passiert **vor** dem Backup. Das ist heute der Fall
  (`handleWagerConfirmation` laeuft vor dem Arena-Teleport), muss aber als Kommentar im
  Code festgehalten werden, sonst kippt die Semantik beim naechsten Refactoring.

#### D. Unentschieden & Abbruch (`distributeItemsBack`, `cancelMatch`)

- **Datei:** [MatchManager.java:952](file:///c:/Users/zfzfg/Documents/HammerMegaProjekte/selfmadePlugins/Plugins/Event-PVP-Plugins/Event-PVP-Plugin-1.0.8%20-%20Copy/src/main/java/de/zfzfg/pvpwager/managers/MatchManager.java#L952)
- Beide Spieler: Teleport → Restore → **dann** Rueckgabe der eigenen Einsaetze.
  Das bestehende Flag `wagerItemsReturned` bleibt und wird zusaetzlich im Journal
  gespiegelt, damit es einen Restart ueberlebt.

#### E. Tod im Match (`onPlayerDeath` / `onPlayerRespawn`)

- **Datei:** [PvPListener.java:117-240](file:///c:/Users/zfzfg/Documents/HammerMegaProjekte/selfmadePlugins/Plugins/Event-PVP-Plugins/Event-PVP-Plugin-1.0.8%20-%20Copy/src/main/java/de/zfzfg/pvpwager/listeners/PvPListener.java#L117-L240)
- **Soll:**
  1. Im Death-Handler bleibt alles wie es ist (Drops leeren, `keepInventory(false)`, clearen)
     — der Kit-Zustand soll gerade **nicht** ins Grab.
  2. Im Respawn-Handler nach `event.setRespawnLocation(...)` **einen Tick spaeter**
     (der Respawn ist erst nach dem Event abgeschlossen) den Restore anstossen, sofern
     das Journal einen offenen `PVP_MATCH`-Eintrag fuehrt.
  3. Race gegen `endMatch`: Tod loest `endMatch` aus, das ebenfalls restaurieren will.
     Beide Pfade gehen ueber `guard.close(...)` → **genau einer** gewinnt (I6). Ohne diese
     Absicherung restauriert man zweimal und dupliziert bei `clearBefore(false)`.
  4. Deckt zusaetzlich Void-, Lava- und Doppeltod ab.

#### F. Disconnect / Ragequit (`onPlayerQuit`)

- **Datei:** [PvPListener.java:492-548](file:///c:/Users/zfzfg/Documents/HammerMegaProjekte/selfmadePlugins/Plugins/Event-PVP-Plugins/Event-PVP-Plugin-1.0.8%20-%20Copy/src/main/java/de/zfzfg/pvpwager/listeners/PvPListener.java#L492-L548)
- **Ist:** Bei `SETUP`/`STARTING` wird das Match abgebrochen, bei `FIGHTING` gewinnt der Gegner.
  Fuer den Aussteiger passiert mit dem Inventar nichts.
- **Soll:**
  1. `getPendingRestore(uuid)` pruefen (I7), dann
     `queueRestoreOnJoin(uuid, handle, RestoreOptions.all())`.
  2. Journal-Eintrag auf Phase `QUEUED` setzen — so weiss der Wiederanlauf nach einem
     Crash, dass hier nichts mehr zu tun ist.
  3. **Wichtig:** `PlayerQuitEvent` wird auch beim Server-Shutdown fuer jeden Spieler
     gefeuert. Dort ist die async I/O des Queue-Schreibens nicht mehr garantiert — deshalb
     schreibt das Guard-Journal seinen Zustand **synchron** und der Wiederanlauf raeumt
     nach (Abschnitt 7).

---

### 5.2 Event-System

#### A. Event-Join & Lobby (`teleportToLobby`)

- **Datei:** [EventSession.java:524-560](file:///c:/Users/zfzfg/Documents/HammerMegaProjekte/selfmadePlugins/Plugins/Event-PVP-Plugins/Event-PVP-Plugin-1.0.8%20-%20Copy/src/main/java/de/zfzfg/eventplugin/session/EventSession.java#L524-L560)
- **Ist:** korrekt vor dem Teleport gesichert (`saveSnapshotAsync`), aber in **zwei**
  Codezweigen dupliziert (direkter Pfad + Multiverse-Nachladepfad ab Z. 545). Beide
  umbauen oder vorher zu einer Methode zusammenziehen.
- **Soll:**

```java
BackupRequest req = BackupRequest.builder()
    .type("event-pre-join")
    .sourcePlugin(plugin)
    .metadata("event_id", config.getId())
    .metadata("event_name", config.getName())
    .metadata("origin_world", player.getWorld().getName())
    .build();

BackupSnapshot fallback = BackupSnapshot.of(player);      // I2
backups.createBackup(player, req).thenAccept(opt -> opt.ifPresentOrElse(
        h -> guard.open(player.getUniqueId(), GuardContext.EVENT, config.getId(), h.id()),
        () -> session.keepFallback(player.getUniqueId(), fallback)));
// Teleport + Gamemode + clear laufen unmittelbar weiter
```

Weitere `clear()`-Stellen in derselben Datei, die zum selben Lebenszyklus gehoeren und
mitgeprueft werden muessen: [Z. 913](file:///c:/Users/zfzfg/Documents/HammerMegaProjekte/selfmadePlugins/Plugins/Event-PVP-Plugins/Event-PVP-Plugin-1.0.8%20-%20Copy/src/main/java/de/zfzfg/eventplugin/session/EventSession.java#L913), [Z. 953](file:///c:/Users/zfzfg/Documents/HammerMegaProjekte/selfmadePlugins/Plugins/Event-PVP-Plugins/Event-PVP-Plugin-1.0.8%20-%20Copy/src/main/java/de/zfzfg/eventplugin/session/EventSession.java#L953), [Z. 1009](file:///c:/Users/zfzfg/Documents/HammerMegaProjekte/selfmadePlugins/Plugins/Event-PVP-Plugins/Event-PVP-Plugin-1.0.8%20-%20Copy/src/main/java/de/zfzfg/eventplugin/session/EventSession.java#L1009), [Z. 1173](file:///c:/Users/zfzfg/Documents/HammerMegaProjekte/selfmadePlugins/Plugins/Event-PVP-Plugins/Event-PVP-Plugin-1.0.8%20-%20Copy/src/main/java/de/zfzfg/eventplugin/session/EventSession.java#L1173).
Jede davon braucht die Antwort auf die Frage: *„Existiert zu diesem Zeitpunkt ein gueltiges,
offenes Backup?"* Wenn nein — nicht clearen.

#### B. Event-Ende (`stopEvent`, `forceStop`)

- **Datei:** [EventSession.java:1433](file:///c:/Users/zfzfg/Documents/HammerMegaProjekte/selfmadePlugins/Plugins/Event-PVP-Plugins/Event-PVP-Plugin-1.0.8%20-%20Copy/src/main/java/de/zfzfg/eventplugin/session/EventSession.java#L1433), [:1485](file:///c:/Users/zfzfg/Documents/HammerMegaProjekte/selfmadePlugins/Plugins/Event-PVP-Plugins/Event-PVP-Plugin-1.0.8%20-%20Copy/src/main/java/de/zfzfg/eventplugin/session/EventSession.java#L1485)
- **Soll:** Fuer jeden Teilnehmer `teleportBack(player)` → Weltwechsel abwarten (I3) →
  Restore → Ergebnis auswerten (I4) → `guard.close(...)` (I6).
- **Belohnungen** (`giveRewards`, [Z. 1402](file:///c:/Users/zfzfg/Documents/HammerMegaProjekte/selfmadePlugins/Plugins/Event-PVP-Plugins/Event-PVP-Plugin-1.0.8%20-%20Copy/src/main/java/de/zfzfg/eventplugin/session/EventSession.java#L1402)) **ausschliesslich**
  im Erfolgs-Callback (I5). Achtung: `giveRewards` fuehrt auch Konsolen-Befehle aus —
  diese sind nicht idempotent und muessen ebenfalls ueber das Journal gegen doppelte
  Ausfuehrung geschuetzt werden, sonst zahlt ein Retry doppelt aus.
- **Massen-Restore:** Bei 50 Teilnehmern werden 50 Futures gleichzeitig ausgeloest.
  Empfehlung: gestaffelt in Bloecken von 5–10 pro Tick abarbeiten, damit der I/O-Pool und
  der Main-Thread-Completion-Hop nicht in einem Tick zusammenfallen.

#### C. Tod & Ausscheiden im Event

- **Datei:** [EventListener.java:35-112](file:///c:/Users/zfzfg/Documents/HammerMegaProjekte/selfmadePlugins/Plugins/Event-PVP-Plugins/Event-PVP-Plugin-1.0.8%20-%20Copy/src/main/java/de/zfzfg/eventplugin/listeners/EventListener.java#L35-L112)
- Elimination → Rueckteleport → Restore beim Respawn, analog 5.1 E.
- Bei Modi mit Wiedereinstieg (`allow-rejoin`) darf beim Ausscheiden **nicht**
  restauriert werden, solange der Spieler noch teilnehmen kann — sonst steht er mit
  Survival-Inventar in der Arena. Der Journal-Zustand entscheidet, nicht das Death-Event.

#### D. Disconnect / Rejoin waehrend eines Events

- Ohne `allow-rejoin`: `getPendingRestore` pruefen → `queueRestoreOnJoin`.
- Mit `allow-rejoin`: Journal bleibt offen, **kein** Queue-Eintrag. Beim Join prueft
  das Plugin selbst, ob das Event noch laeuft: laeuft es → zurueck in die Lobby;
  ist es beendet → jetzt restaurieren.

---

### 5.3 Spectator- & Recovery-System

- **Datei:** [SpectatorRecoveryListener.java:27-66](file:///c:/Users/zfzfg/Documents/HammerMegaProjekte/selfmadePlugins/Plugins/Event-PVP-Plugins/Event-PVP-Plugin-1.0.8%20-%20Copy/src/main/java/de/zfzfg/pvpwager/listeners/SpectatorRecoveryListener.java#L27-L66)
- **Ist:** Holt verwaiste Spectatoren nach Disconnect in Survival zurueck und teleportiert
  sie zum Spawn.
- **Soll:** Der Listener wird zum **zweiten Sicherheitsnetz neben dem Guard-Journal**:
  beim Join pruefen, ob ein offener Journal-Eintrag existiert, dessen Match/Event nicht
  mehr laeuft → Restore nachholen. Spectatoren selbst bekommen **kein** Backup, solange
  ihr Inventar nicht angefasst wird (heute wird es das nicht) — das spart Schreiblast und
  Verwirrung im Archiv.

---

### 5.4 Ingame-Befehle & GUI-Preview

#### A. Umbau von `/inventoryrestore`

- **Datei:** [InventoryRestoreCommand.java](file:///c:/Users/zfzfg/Documents/HammerMegaProjekte/selfmadePlugins/Plugins/Event-PVP-Plugins/Event-PVP-Plugin-1.0.8%20-%20Copy/src/main/java/de/zfzfg/eventplugin/commands/InventoryRestoreCommand.java)
- Neuer Umfang:
  1. `/inventoryrestore <player> [backupId]` — ohne ID: Liste der juengsten Backups
     mit klickbaren Chat-Komponenten.
  2. `/inventoryrestore preview <player> <backupId>` → `api.openPreview(sender, handle)`
     (Main-Thread, `false` sauber melden).
  3. `/inventoryrestore undo <player>` — nutzt das automatisch angelegte
     `pvp-post-match`-Backup, um einen versehentlichen Restore rueckgaengig zu machen.
  4. **Legacy-Bruecke:** Alte numerische IDs (`MATCH0001`) muessen weiter aufloesbar
     bleiben, solange `inventory_backups.yml` existiert (siehe Abschnitt 8).
  5. Tab-Completion ueber `resolvePlayerId` / `listBackups` — beide async, also Ergebnisse
     in einem kurzlebigen Cache halten; Tab-Completion darf nicht blockieren.
- **Namenskonflikt beachten:** Der Befehl `/inventoryrestore` des Event-PVP-Plugins und
  der Befehl `/inv` des InventoryBackup-Plugins existieren parallel. In der Doku klar
  trennen, welcher Befehl welche Datenbasis anspricht, sonst suchen Admins Backups im
  falschen Werkzeug.

#### B. Neue Komfort-Subcommands

- `/pvp restore <player>` — juengste `pvp-pre-match`-Backups
- `/event restore <player>` — juengste `event-pre-join`-Backups
- `/pvp invdebug <player>` — **neu:** zeigt den Journal-Zustand (offene Sitzung, Phase,
  backupId, Alter, Pending-Restore ja/nein). Das ist das Werkzeug, mit dem im Support-Fall
  in 10 Sekunden klar ist, ob ein Item-Verlust ein Bug oder eine offene Queue ist.

---

### 5.5 Web-Interface & REST-API

Bestehende Struktur: [WebServer.java](file:///c:/Users/zfzfg/Documents/HammerMegaProjekte/selfmadePlugins/Plugins/Event-PVP-Plugins/Event-PVP-Plugin-1.0.8%20-%20Copy/src/main/java/de/zfzfg/core/web/WebServer.java) registriert Kontexte,
[WebApiHandler.java](file:///c:/Users/zfzfg/Documents/HammerMegaProjekte/selfmadePlugins/Plugins/Event-PVP-Plugins/Event-PVP-Plugin-1.0.8%20-%20Copy/src/main/java/de/zfzfg/core/web/WebApiHandler.java) beantwortet sie.

**Threading — der entscheidende Punkt, den v1.0 uebersieht:** HTTP-Handler laufen auf
`HttpServer`-Threads, **nicht** auf dem Main-Thread. Damit gilt:

- `createBackup(Player, …)` und `openPreview(...)` sind von dort **verboten** →
  vorher via `Bukkit.getScheduler().runTask(...)` auf den Main-Thread springen.
- `.join()` auf einem API-Future ist vom HTTP-Thread technisch erlaubt (kein Deadlock,
  weil die Completion auf dem Main-Thread laeuft), aber es blockiert den HTTP-Thread und
  haengt beim Server-Shutdown. **Besser:** dem bereits existierenden Job-Muster folgen,
  das fuer `/api/mvworlds/job?id=…` gebaut wurde
  ([WebServer.java:104](file:///c:/Users/zfzfg/Documents/HammerMegaProjekte/selfmadePlugins/Plugins/Event-PVP-Plugins/Event-PVP-Plugin-1.0.8%20-%20Copy/src/main/java/de/zfzfg/core/web/WebServer.java#L104)): Anfrage nimmt an,
  liefert eine Job-ID, das Frontend pollt.

#### Neue Endpunkte

| Methode | Pfad | Verhalten |
|---|---|---|
| `GET` | `/api/inventories/list?player=<name\|uuid>[&type=]` | Handles mit Zeitstempel, Typ, Metadaten (match_id, arena, origin_world) |
| `GET` | `/api/inventories/get?player=<uuid>&id=<backupId>` | Item-Details als JSON: Material, DisplayName, Lore, Enchantments, Amount, Slot |
| `POST` | `/api/inventories/restore` | `{"player":"<uuid>","backupId":"<id>","clearBefore":true}` → Job-ID; funktioniert auch offline (Queue) |
| `POST` | `/api/inventories/delete` | Einzelnes Backup loeschen |
| `GET` | `/api/inventories/guard` | **neu:** offene Journal-Eintraege — das Ops-Dashboard fuer haengende Sitzungen |

#### Sicherheit & Nachvollziehbarkeit (in v1.0 nicht adressiert)

- Alle Endpunkte ueber `handleProtectedApiRequest` / `handleProtectedApiPostRequest`,
  niemals oeffentlich — ein Restore-Endpunkt ist effektiv ein Item-Spawner.
- **Audit-Log:** jeder Web-Restore mit Web-Benutzer, Ziel-UUID, backupId und Ergebnis ins
  Server-Log **und** als `metadata("restored_by", user)` ins Post-Backup.
- Rate-Limit auf `/restore`, damit ein kompromittiertes Web-Login keine Item-Fabrik wird.
- `/api/inventories/get` liefert Item-Metadaten, keine rohen NBT-Blobs.

#### Visualisierung

**Umgesetzt in 1.0.9.** Das Gitter steht in `renderInventoryBackupPreview()`
([src/main/resources/web/app.js](src/main/resources/web/app.js)): 27 Felder Hauptinventar,
darunter die Hotbar, daneben die vier Ruestungsslots und die Nebenhand.

Die Icons kommen nicht mehr aus `minecraft_textures_item/`, sondern aus dem mitgelieferten
Ordner `web/item-assets/` (1658 PNG, 64x64, Dateiname = Material-Enum), aufgeloest ueber
`itemIconHtml()` in [src/main/resources/web/items.js](src/main/resources/web/items.js).
Dieser Bestand ist ein echtes Superset des alten Ordners und deckt auch Bloecke ab, die
dort grundsaetzlich fehlten. Die geforderte Platzhalter-Kette ist enthalten:
Resourcepack-Textur → mitgeliefertes Icon → optionaler Remote-Abruf → Inline-SVG mit dem
Anfangsbuchstaben. Ein unbekanntes Item aus einer neueren Version bricht die Ansicht
dadurch nicht.

---

### 5.6 Event-Interception & Filter

1. **`BackupCreateEvent`** — Arena-Tode aus dem Death-Archiv heraushalten.
   **Empfehlung gegenueber v1.0:** *nicht* canceln, sondern **umtypen**:
   ```java
   event.setRequest(event.getRequest().withType("pvp-arena-death"));
   ```
   Cancellen wirft die Information weg; Umtypen haelt sie forensisch verfuegbar und haelt
   das `death`-Archiv trotzdem sauber. `withType` leitet vom bestehenden Request ab und
   verwirft damit nicht, was ein anderer Listener bereits gesetzt hat.
2. **`InventoryRestoreEvent`** — verhindern, dass ein Admin oder ein Fremdplugin mitten
   im laufenden Kampf ein Survival-Inventar einspielt. Cancel + Log + Hinweis an den
   Ausloeser. Achtung: Die eigenen Restores laufen durch dasselbe Event → sie muessen
   anhand des Journal-Zustands (Phase `RESTORING`) erkennbar sein, sonst blockiert sich
   das Plugin selbst.
3. **`BackupDeletedEvent`** — bei `Reason.EXPIRED` den Journal-Eintrag als verwaist
   markieren, statt still auf einen toten Handle zu zeigen. Genau hier hilft ein
   Pruning-Schutz: `retain-days` in InventoryBackup **muss** groesser sein als die
   laengste denkbare offene Sitzung; sonst loescht das Pruning das Backup eines Spielers,
   der seit drei Wochen nicht eingeloggt ist. In der Doku als Anforderung festhalten.

---

## 6. Architektur- & Refactoring-Plan

### 6.1 Paketstruktur

```
de.zfzfg.core.inventory/
├── InventoryBackupService.java        Interface (backup, restore, list, preview, queue)
├── BackupRef.java                     Provider-neutraler Handle (ownerId + backupId + type)
├── adapter/
│   ├── InventoryRestoreApiAdapter.java  ueber InventoryBackupProvider (Vorzugspfad)
│   ├── InternalFallbackAdapter.java     schlanker Eigenspeicher (UUID-Ordner, kein YAML-Monolith)
│   └── NoOpAdapter.java                 deaktiviert; jeder Aufruf loggt einmal und liefert leer
├── guard/
│   ├── InventoryGuard.java              Journal-API: open/close/phase/openSessions
│   ├── GuardEntry.java                  uuid, context, refId, backupId, phase, timestamp
│   └── GuardContext.java                PVP_MATCH | EVENT | WEB | MANUAL
└── InventoryBackupServiceFactory.java   Provider-Auswahl nach config + Startup-Diagnose
```

### 6.2 Service-Interface (Vorschlag)

Das Interface bleibt bewusst **Future-basiert** — ein synchrones Interface ueber eine
async API zu legen, endet zwangslaeufig in `.join()` auf dem Main-Thread.

```java
public interface InventoryBackupService {

    boolean isAvailable();
    String providerName();

    /** Erfasst synchron und schreibt async. Main-Thread. */
    CompletableFuture<Optional<BackupRef>> backup(Player player, BackupContext ctx);

    /** Fuer Offline-Spieler und selbst gebaute Snapshots. Jeder Thread. */
    CompletableFuture<Optional<BackupRef>> backup(UUID owner, String name,
                                                  CapturedInventory snapshot, BackupContext ctx);

    CompletableFuture<RestoreOutcome> restore(UUID target, BackupRef ref, RestoreMode mode);
    CompletableFuture<Boolean>        queueOnJoin(UUID target, BackupRef ref, RestoreMode mode);
    CompletableFuture<Boolean>        hasPendingRestore(UUID target);

    CompletableFuture<List<BackupRef>> list(UUID owner, String type);
    CompletableFuture<Optional<BackupRef>> resolve(UUID owner, String backupId);
    CompletableFuture<Boolean>         delete(BackupRef ref);

    boolean preview(Player viewer, BackupRef ref);   // Main-Thread, false = abgelehnt
}
```

`RestoreOutcome` bildet `RestoreResult` ab, ergaenzt um `UNAVAILABLE` (kein Provider) und
`FALLBACK_APPLIED` (aus dem RAM-Snapshot wiederhergestellt).

### 6.3 Zustandsmaschine je Spieler

```
   IDLE
     |  backup() erfolgreich
     v
   BACKED_UP  ---- Kit angelegt, Match/Event laeuft ---->  ACTIVE
     |                                                       |
     |  Backup fehlgeschlagen                                |  Ende / Tod / Quit
     v                                                       v
   DEGRADED (RAM-Fallback, Alarm)                        RESTORING
                                                             |
                                        +--------------------+-----------------+
                                        v                    v                 v
                                    APPLIED              QUEUED            FAILED
                                        |                    |                 |
                                        v                    v                 v
                                   Ausschuettung        Journal offen    Journal offen
                                        |                    |            + Admin-Alarm
                                        v                    v
                                      CLOSED          CLOSED beim Join
```

Die Phase gehoert ins Journal, nicht nur ins `Match`-Objekt — sonst ueberlebt sie keinen
Restart.

### 6.4 Vorteile dieses Aufbaus

- **Soft-Dependence:** Ist InventoryRestore da, wird die API genutzt; fehlt es, greift der
  interne Fallback und der Server laeuft weiter.
- **Keine Hard-Dependency:** `softdepend`, kein `depend` — das Plugin startet auch ohne.
- **Testbar:** Gegen `InventoryBackupService` laesst sich ein Fake einsetzen; die
  Match-Logik wird ohne laufenden Server pruefbar.
- **Ein Ort fuer Regeln:** Die Invarianten aus Abschnitt 4 leben im Service und im Guard,
  nicht verteilt ueber 6 Listener.

---

## 7. Crash- & Shutdown-Recovery (Guard-Journal)

**Das war die groesste Luecke in v1.0.** Dort lag der `BackupHandle` ausschliesslich im
`Match`-Objekt, also im RAM. Ein Absturz waehrend eines Matches bedeutete: Backup liegt auf
der Platte, aber niemand weiss mehr, zu wem es gehoerte oder dass es offen war — genau das
Problem, das die Migration loesen sollte.

### 7.1 Datei `plugins/Event-PVP-Plugin/inventory-guard.yml`

```yaml
version: 1
sessions:
  "11111111-2222-3333-4444-555555555555":
    context: PVP_MATCH                 # PVP_MATCH | EVENT | WEB | MANUAL
    ref-id: "a1b2c3d4-..."             # matchId oder eventId
    backup-id: "2026-08-09_12-00-00_pvp-pre-match.yml"
    phase: ACTIVE                      # BACKED_UP | ACTIVE | RESTORING | QUEUED
    origin-world: "world"
    opened-at: 1786000000000
    payout-done: false                 # Exactly-Once fuer Gewinne/Rewards (I6)
```

Eigenschaften:
- Wird **synchron** geschrieben, wenn ein Eintrag geoeffnet, die Phase gewechselt oder
  geschlossen wird. Die Datei ist klein (nur offene Sitzungen, typisch < 20 Zeilen) —
  synchrones Schreiben ist hier vertretbar und der Preis fuer Crash-Sicherheit.
- Ein geschlossener Eintrag wird sofort entfernt. Die Datei ist damit im Normalbetrieb leer.

### 7.2 Wiederanlauf beim `onEnable`

```
1. inventory-guard.yml lesen
2. Fuer jeden offenen Eintrag:
     a) Backup ueber getBackup(uuid, backupId) aufloesen
     b) Backup nicht auffindbar -> SEVERE-Log + Eintrag als ORPHANED markieren,
        NICHT loeschen (Admin muss ihn sehen)
     c) Spieler online   -> restore(...) sofort
        Spieler offline  -> getPendingRestore pruefen, sonst queueRestoreOnJoin(...)
     d) phase == QUEUED  -> nichts tun, Queue erledigt es
3. Ergebnis als Startmeldung: "Inventory-Guard: 3 offene Sitzungen wiederhergestellt,
   0 verwaist."
```

### 7.3 Sauberer Shutdown (`onDisable`)

Da im `onDisable` keine Futures mehr komplettieren (vgl. 3.5), gilt dort:

1. **Nicht** `restore(...)` aufrufen und auf das Ergebnis hoffen.
2. Journal-Eintraege stehen bereits auf der Platte → **nichts tun ist der sichere Weg**.
   Der Wiederanlauf aus 7.2 erledigt beim naechsten Start alles.
3. `endMatchOnShutdown` gibt weiterhin **synchron** Wager-Items zurueck (bestehendes
   Verhalten, [MatchManager.java:1205-1215](file:///c:/Users/zfzfg/Documents/HammerMegaProjekte/selfmadePlugins/Plugins/Event-PVP-Plugins/Event-PVP-Plugin-1.0.8%20-%20Copy/src/main/java/de/zfzfg/pvpwager/managers/MatchManager.java#L1205-L1215)) und markiert
   `payout-done: true` im Journal, damit der Wiederanlauf nicht doppelt auszahlt.

Damit gilt die Zusicherung: **Ein Absturz zu jedem beliebigen Zeitpunkt kostet
schlimmstenfalls einen Login — nie ein Inventar.**

### 7.4 Positions-Wiederherstellung (nachgezogen)

Die Zusicherung oben galt lange nur fuer das **Inventar**. Die Rueckkehr-Position lag
ausschliesslich im Arbeitsspeicher — `EventManager.globalSavedLocations` und
`Match.originalLocations`. Ein Absturz gab dem Spieler seine Items zurueck und liess ihn
in der Eventwelt stehen, ohne Weg nach Hause.

Das Gegenstueck zum Guard-Journal ist `player-return-locations.yml`, verwaltet von
`ReturnLocationStore`. Bewusst eine **eigene Datei**: im Legacy-Betrieb
(`provider: none`) wird absichtlich kein Guard-Eintrag angelegt (siehe I7), teleportiert
wird dort aber trotzdem. Die Position muss in jedem Modus ueberleben.

Es gelten dieselben Ueberlegungen wie beim Journal:

| Invariante | Bedeutung fuer die Position |
|---|---|
| **P1** | Geschrieben wird **synchron**, bevor der Teleport stattfindet. |
| **P2** | Ein bestehender Eintrag wird **nie ueberschrieben** — die zweite Position entstuende bereits in der Event- oder Arenawelt (Gegenstueck zu I7). |
| **P3** | Verbraucht wird erst **nach** gelungenem Rueckweg; scheitert der Teleport, bleibt der Eintrag fuer den naechsten Versuch stehen. |
| **P4** | Beim Herunterfahren wird nur gespeichert, **nie aufgeraeumt** — wer dann noch einen Eintrag hat, braucht ihn beim naechsten Start. |

Drei Wege fuehren zurueck:

1. **Wiederanlauf beim Start** — der Guard holt das Inventar, der Store haelt die Position bereit.
2. **`StrandedPlayerListener`** — 20 Ticks nach dem Join (also nach dem Inventar-Netz bei
   10 Ticks): steht der Spieler in einer Plugin-Welt ohne laufende Sitzung, geht es zurueck.
3. **`/eventpvp rescue`** — von Hand, wenn ein Admin eingreifen will.

Wohin genau, beantwortet an **einer** Stelle der `SafeLocationResolver`: hinterlegte
Position → Match-Ursprung → Bett → Hauptwelt-Spawn → erste geladene Welt. Vorher lagen
vier eigene Ketten mit unterschiedlichen Prioritaeten im Code.

Damit gilt die Zusicherung vollstaendig: **Ein Absturz kostet schlimmstenfalls einen
Login — nie ein Inventar und nie den Weg zurueck.**

---

## 8. Migration & Koexistenz

### 8.1 Alt-Snapshots nicht wegwerfen

v1.0 schlug vor, `InventorySnapshotStorage` und `inventory_backups.yml` schlicht zu
entfernen. Auf einem Produktivserver liegen dort aber die einzigen Rettungsanker fuer
zurueckliegende Vorfaelle. Besserer Weg:

1. **Einmaliger Importer** (`--migrate-inventories` oder beim ersten Start mit
   `provider: inventoryrestore`): jeden Alt-Eintrag als `BackupSnapshot` bauen und ueber
   die **thread-sichere** `createBackup(UUID, name, snapshot, request)`-Overload mit
   `type = "legacy-import"` und `metadata("legacy_id", "MATCH0001")` uebergeben.
2. Alte Dateien nach `inventory_backups.yml.migrated` umbenennen, **nicht** loeschen.
3. `/inventoryrestore MATCH0001` loest die Legacy-ID danach ueber
   `metadata("legacy_id")` auf — Admin-Muskelgedaechtnis bleibt intakt.
4. `InventorySnapshotStorage` erst in einer spaeteren Version entfernen, wenn der Import
   auf allen Servern gelaufen ist. Bis dahin: `@Deprecated` und keine neuen Schreibpfade.

### 8.2 Koexistenz mit Multiverse-Inventories

Beide Systeme gleichzeitig aktiv zu betreiben ist **die gefaehrlichste Konfiguration** —
Mv-Inv tauscht beim Weltwechsel, das Plugin restauriert danach, und je nach Timing gewinnt
mal der eine, mal der andere. Deshalb:

- Beim Start pruefen: `Bukkit.getPluginManager().getPlugin("Multiverse-Inventories") != null`
  **und** `provider != none` → **deutliche Warnung** mit der Empfehlung, entweder die
  Weltgruppen aufzuloesen oder das Plugin auf `provider: none` zu stellen.
- Uebergangsbetrieb ist moeglich, solange Regel **I3** eingehalten wird (Restore erst nach
  abgeschlossenem Weltwechsel) — aber als temporaerer Zustand dokumentieren, nicht als Ziel.
- Die bestehende Meldung `multiverse-inventories-recommended` umformulieren, sobald der
  neue Weg Standard ist.

---

## 9. Build, Dependency & Deployment

**Praktische Huerde, die v1.0 nicht erwaehnt:** `com.zfzfg:InventoryBackup-API:0.1.0` liegt
in **keinem oeffentlichen Repository**. Das Artefakt entsteht aus dem `api/`-Modul von
InventoryRestore und muss vor jedem Build vorhanden sein.

### Option A — lokale Installation (empfohlen fuer den eigenen Build)

```bash
cd .../InventoryRestore/InventoryRestore-0.0.7
mvn clean install          # legt InventoryBackup-API:0.1.0 im lokalen Repo ab
```

```xml
<dependency>
    <groupId>com.zfzfg</groupId>
    <artifactId>InventoryBackup-API</artifactId>
    <version>0.1.0</version>
    <scope>provided</scope>
</dependency>
```

`provided` ist zwingend richtig: die API-Klassen liegen im `InventoryBackup.jar` auf dem
Server. **Niemals shaden** — zwei Kopien derselben Klasse passen nicht zueinander.

*Nachteil:* Wer das Event-PVP-Plugin ohne die InventoryRestore-Quellen auscheckt, kann es
nicht bauen. Fuer ein oeffentlich vertriebenes Plugin ist das ein Problem.

### Option B — Adapter ueber Reflection, kein Compile-Abhaengigkeit

`InventoryRestoreApiAdapter` spricht die API rein reflektiv an (`Class.forName`,
`MethodHandle`). Der Build bleibt unabhaengig, der Preis ist fehlende Compile-Sicherheit.

### Empfehlung

Option A fuer den eigenen Betrieb, **plus** ein JitPack- oder GitHub-Packages-Deployment
des `api/`-Moduls, sobald das Plugin oeffentlich verteilt wird. Option B nur, wenn eine
Veroeffentlichung des API-Artefakts nicht moeglich ist.

### plugin.yml

```yaml
softdepend: [Multiverse-Core, AJLeaderboards, DecentHolograms, PlaceholderAPI, PvPManager, InventoryBackup]
```

`InventoryBackup` **muss** in `softdepend`, sonst ist die Ladereihenfolge undefiniert und
`InventoryBackupProvider.get()` kann im `onEnable` noch leer sein.

### Versions-Gate im `onEnable`

```java
InventoryBackupProvider.getOptional().ifPresentOrElse(api -> {
    if (api.getApiVersion() < InventoryBackupAPI.API_VERSION) {
        getLogger().warning("InventoryBackup ist aelter als erwartet (API v"
                + api.getApiVersion() + " < " + InventoryBackupAPI.API_VERSION
                + ") - Integration laeuft im eingeschraenkten Modus.");
    }
    getLogger().info("InventoryBackup gefunden, API v" + api.getApiVersion());
}, () -> getLogger().warning("InventoryBackup nicht gefunden - interner Fallback aktiv."));
```

Zur Erinnerung (I8): das Ergebnis **nicht** in einem Feld halten.

---

## 10. Konfiguration & Dokumentation

```yaml
settings:
  # INVENTAR-VERWALTUNG (Ersatz fuer Multiverse-Inventories)
  inventory-management:
    # "auto" (InventoryRestore, sonst intern), "inventoryrestore", "internal", "none"
    provider: "auto"

    # Automatische Wiederherstellung
    auto-restore-on-match-end: true
    auto-restore-on-event-end: true
    auto-restore-on-respawn: true
    auto-restore-on-rejoin: true

    # Was passiert, wenn das Pre-Backup nicht geschrieben werden konnte?
    #   abort  = Match/Event-Beitritt abbrechen (sicherste Variante)
    #   warn   = fortfahren, RAM-Fallback nutzen, Admin warnen
    on-backup-failure: "abort"

    # Nach wie vielen Sekunden gilt ein Restore als haengend (Log + Alarm)?
    restore-timeout-seconds: 30

    # Wie viele Restores pro Tick beim Event-Ende (Massen-Restore drosseln)
    restore-batch-size: 8

    # Temporaere Backups nach erfolgreichem Ende loeschen oder archivieren
    cleanup-backups-after-match: false
    keep-post-restore-backup: true      # ermoeglicht /inventoryrestore undo

    # Aufbewahrungsdauer in Tagen (nur fuer den internen Provider).
    # ACHTUNG: Muss laenger sein als die laengste denkbare offene Sitzung.
    retain-days: 30

    # Crash-Recovery
    guard:
      enabled: true
      restore-orphans-on-start: true
      warn-on-orphan: true

    # Koexistenz-Warnung, wenn Multiverse-Inventories parallel laeuft
    warn-on-multiverse-inventories: true

    debug: false
```

**Validierung beim Laden:** unbekannter `provider`-Wert → Warnung + `auto`;
`retain-days < 7` bei aktivem Guard → Warnung.

### Dokumentations-Updates

- [DESCRIPTION.md:64-105](DESCRIPTION.md) — Block „⚠️ Multiverse-Inventories (STRONGLY
  RECOMMENDED)" ersetzen durch die neue Empfehlung. **Nicht ersatzlos streichen**, solange
  `provider: none` unterstuetzt wird — fuer diesen Fall bleibt Mv-Inv der richtige Weg.
- [CONFIG_EXAMPLES.md](CONFIG_EXAMPLES.md) / `CONFIG_EXAMPLES_EN.md` (Z. 43, 49, 829-831)
  entsprechend anpassen.
- Neue Meldung statt `multiverse-inventories-recommended`:
  *„Kein Inventar-Provider aktiv. Installiere InventoryBackup oder aktiviere den internen
  Provider — sonst koennen Inventare bei Matches und Events verloren gehen."*
- [WEB_API_DOCUMENTATION.md](WEB_API_DOCUMENTATION.md) um die fuenf neuen Endpunkte ergaenzen.
- `CHANGELOG_1.0.9.md`: Breaking-Change-Hinweis zum Backup-Zeitpunkt (3.1) — Server, die
  bisher auf die alten Match-Snapshots gebaut haben, bekommen ab jetzt korrekte, aber
  **andere** Inhalte.
- Voraussetzung dokumentieren: **InventoryBackup verlangt Paper/Spigot 1.20+**, waehrend
  das Event-PVP-Plugin selbst ab 1.19 laeuft.

---

## 11. Test- & Abnahmematrix

Kein Punkt gilt als erledigt, bevor der zugehoerige Fall gruen ist.

| # | Szenario | Erwartung |
|---|---|---|
| T1 | Match normal beenden, Gewinner | Survival-Inventar exakt wie vorher **plus** Wager-Gewinn |
| T2 | Match normal beenden, Verlierer | Survival-Inventar wie vorher, **ohne** den Einsatz |
| T3 | Unentschieden | Beide exakt wie vorher inkl. eigenem Einsatz |
| T4 | Tod im Match | Nach Respawn Survival-Inventar zurueck, kein Kit-Item uebrig |
| T5 | Doppeltod im selben Tick | Genau ein Restore pro Spieler, keine Duplikate (I6) |
| T6 | Quit waehrend `FIGHTING` | Beim naechsten Join automatisch vollstaendig zurueck |
| T7 | Quit + Server-Restart vor dem Rejoin | Wie T6 — Queue ueberlebt den Restart |
| T8 | **Server-Kill (`kill -9`) waehrend `FIGHTING`** | Nach Neustart Wiederanlauf: alle offenen Sitzungen wiederhergestellt |
| T9 | Backup-Schreiben scheitert (Platte voll) | `on-backup-failure: abort` → Match startet nicht, Inventar unangetastet |
| T10 | InventoryBackup nicht installiert | Plugin startet, interner Fallback, klare Startmeldung |
| T11 | InventoryBackup mit `/reload` neu geladen | Keine `IllegalStateException` (I8) |
| T12 | Voll gepacktes Inventar beim Restore | `dropOverflow` greift, nichts verschwindet |
| T13 | Admin restauriert waehrend laufendem Match | Blockiert mit verstaendlicher Meldung (5.6.2) |
| T14 | Event mit 50 Teilnehmern beenden | Keine TPS-Einbrueche, alle 50 korrekt (Batch-Groesse) |
| T15 | Event-Beitritt waehrend laufendem Match | Abgelehnt (I7), kein zweites Backup |
| T16 | Web-Restore fuer Offline-Spieler | `QUEUED_FOR_JOIN`, Frontend zeigt den Zustand |
| T17 | Restore auf einem Server **mit** aktivem Mv-Inv | Inventar bleibt korrekt (I3) |
| T18 | Legacy-Befehl `/inventoryrestore MATCH0001` nach Migration | Loest weiterhin auf |
| T19 | Backup laeuft waehrend offener Sitzung ab (`EXPIRED`) | Journal markiert verwaist, Admin-Warnung, kein stiller Verlust |
| T20 | Kit mit Potion-Effekten | Effekte nach dem Match entfernt (2.4) |

**Ehrliche Anforderung an die Testumgebung:** T8 und T17 lassen sich nicht mit Unit-Tests
abdecken. Dafuer braucht es einen Testserver mit zwei Accounts. Alles, was das
`InventoryBackupService`-Interface kapselt, ist dagegen mit einem Fake unit-testbar —
das ist das Hauptargument fuer das Interface aus 6.2.

---

## 12. Rollout in Phasen

| Phase | Inhalt | Abnahme |
|---|---|---|
| **0** | Bugfix 3.1 isoliert: Match-Snapshot vor den Teleport ziehen, beide Start-Pfade zusammenfuehren. **Noch ohne InventoryRestore.** | Alte Snapshots enthalten endlich das Survival-Inventar |
| **1** | `InventoryBackupService` + Adapter + Factory + Startup-Diagnose. Noch kein Verhaltenswechsel: es wird nur zusaetzlich gesichert. | T10, T11 |
| **2** | Guard-Journal + Wiederanlauf. Immer noch kein Auto-Restore. | T8 (Sitzungen werden erkannt und geloggt) |
| **3** | Auto-Restore PvP: Match-Ende, Respawn, Quit. Hinter `auto-restore-on-*` schaltbar. | T1–T7, T12, T13 |
| **4** | Auto-Restore Events inkl. Batching und Reward-Reihenfolge. | T14, T15, T20 |
| **5** | Befehle, GUI-Preview, `/pvp invdebug`. | T18 |
| **6** | Web-API + Frontend-Gitter + Audit. | T16 |
| **7** | Migration der Alt-Snapshots, Doku-Umstellung, `InventorySnapshotStorage` deprecaten. | T18, T19 |

Jede Phase ist einzeln deploybar und ueber Config abschaltbar. Der Rueckweg ist immer
`provider: none` plus die alten `auto-restore-*: false` — damit verhaelt sich das Plugin
wie 1.0.8.

---

## 13. Risikoregister

| Risiko | Auswirkung | Gegenmassnahme |
|---|---|---|
| Restore ueberschreibt ein *neueres* Survival-Inventar (Spieler hat zwischenzeitlich gefarmt) | Item-Verlust, schwer zu erklaeren | Restore nur bei offener Journal-Sitzung; `pvp-post-match`-Backup vor jedem Restore → `undo` moeglich |
| Doppelter Restore (Tod + endMatch) | Item-Duplikation | `guard.close(...)` als einziger Ausloeser, idempotent (I6) |
| Ausschuettung vor Restore | Gewinn wird vom `clearBefore` geloescht | Regel I5, in beiden Modulen im Code kommentiert |
| Backup-Handle geht bei Crash verloren | Inventar nicht mehr zuordenbar | Guard-Journal (Abschnitt 7) |
| Pruning loescht Backup einer offenen Sitzung | Totalverlust | `retain-days` > laengste Sitzung; `BackupDeletedEvent`-Hook markiert verwaist |
| Mv-Inv laeuft parallel | Nichtdeterministisches Ergebnis | Startup-Warnung, Doku, Regel I3 |
| API-Artefakt nicht baubar | Build bricht bei Dritten | Abschnitt 9, Option A + Repo-Deployment |
| InventoryBackup verlangt 1.20+, Plugin zielt auf 1.19 | Auf 1.19-Servern kein Provider | Interner Fallback bleibt erhalten, Startmeldung nennt den Grund |
| Web-Restore als Item-Fabrik missbraucht | Wirtschaftsschaden | Auth, Rate-Limit, Audit-Log (5.5) |
| Massen-Restore am Event-Ende | TPS-Einbruch | `restore-batch-size` |

---

## 14. Zusammenfassung der Vorteile

| Bereich | Bisher (Mv-Inv + alter Snapshot) | Neu (InventoryRestore + Guard) |
|---|---|---|
| **Abhaengigkeiten** | Korrektheit haengt an fremder Weltgruppen-Konfiguration | Plugin kontrolliert Sicherung und Restore selbst |
| **Snapshot-Zeitpunkt** | Im PvP-Pfad **nach** dem Weltwechsel — Snapshot inhaltlich falsch (3.1) | Immer vor dem Weltwechsel, zusammen mit der Original-Position |
| **Automatik** | Kein automatischer Restore, nur Admin-Befehl | Restore bei Match-Ende, Event-Ende, Respawn, Rejoin |
| **Offline-Sicherheit** | Disconnect im Kampf = Inventar weg | Persistente Offline-Join-Queue |
| **Crash-Sicherheit** | Zuordnung nur im RAM → nach Absturz verloren | Persistentes Guard-Journal + Wiederanlauf beim Start |
| **Dateisystem** | Monolithische YAMLs, Full-Reparse pro Schreibvorgang | UUID-Unterordner, async I/O |
| **Admin-Werkzeuge** | Textbefehl mit numerischer ID | GUI-Preview, `undo`, `invdebug`, Web-Panel mit Audit |
| **Fehlersichtbarkeit** | Fehler laufen still ins Leere | `RestoreResult` ausgewertet, Alarm + offener Journal-Eintrag |
| **Testbarkeit** | Statische Utility-Klasse, nicht mockbar | Interface + Fake, Unit-Tests moeglich |
