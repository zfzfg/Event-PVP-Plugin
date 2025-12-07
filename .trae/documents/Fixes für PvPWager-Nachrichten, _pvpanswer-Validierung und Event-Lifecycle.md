## Ziele
- Korrekte Wager-Nachrichten (zeige wirklich erhaltene Gegenstände/Geld).
- Strengere /pvpanswer-Validierung und Anzeige, damit nur tatsächlich vorhandene Items gezeigt werden.
- Event-Lobby-Teleport vor dem Match zuverlässig.
- Volle Lebens-/Sättigungswerte nach Event-Teleport in die Event-Welt.
- Inventar automatisch wiederherstellen nach Event.

## PvPWager
- Gewinner-Nachrichten anpassen:
  - Nach `distributeWinnings(...)` die erhaltenen Items explizit mit `MessageUtil.formatItemList(...)` aus der Summe beider Wager anzeigen (statt Eingabe-Items).
  - Zeige getrennt: „Erhaltene Items“ (Gegenstände beider Spieler) und „Erhaltenes Geld“ (Summe beider Beiträge). Referenz: `src/main/java/de/zfzfg/pvpwager/managers/MatchManager.java:811–856`.
- Verlierer-/Unentschieden-Nachrichten ergänzen:
  - Bei Verlust: zeige dem Verlierer, welche eigenen Wager-Items abgegeben wurden.
  - Bei Draw: zeige Rückgabe-Items pro Spieler.
- /pvpanswer Validierung/Anzeige härten:
  - Beim Parsen die Inventarmenge pro Material robust zählen (ohne Metadaten-Bindung) und bei unzureichender Menge ablehnen.
  - In den Bestätigungsnachrichten „Your Wager“/„Their Wager“ nur die validierten und tatsächlich vorhandenen Items anzeigen. Referenz: `src/main/java/de/zfzfg/pvpwager/commands/PvPAnswerCommand.java:70–190`.

## Event
- Lobby-Teleport vor dem Match sicherstellen:
  - In `startJoinPhase()` sicherstellen, dass Lobby-Welt geladen ist (direkter Load-Fallback), damit `teleportToLobby(...)` (`src/main/java/de/zfzfg/eventplugin/session/EventSession.java:515–536`) nicht ins Leere läuft.
- Volle Lebens-/Sättigungswerte nach Teleport in Event-Welt:
  - In `teleportPlayersToSpawns(...)` und `teleportTeamsToSpawns(...)` zusätzlich `setHealth(20.0)` und `setFoodLevel(20)` setzen (falls Equipment nicht in Lobby verteilt wurde). Referenzen: `...EventSession.java:616–640, 642–730`.
- Inventar automatisch wiederherstellen nach Event:
  - In `stopEvent()` und `forceStop()` nach `teleportBack(...)` den letzten Snapshot des Spielers wiederherstellen (via `InventorySnapshotStorage`), damit Cleans die ursprünglichen Items zurückbringen. Referenzen: `...EventSession.java:1330–1407`.

## Verifikation
- Szenario-Tests:
  - PvPWager: Gewinner erhält korrekt Items/Geld; Nachrichten zeigen Opponenten-Items.
  - /pvpanswer: Eingabe für Items, die nicht im Inventar sind, wird abgelehnt; Anzeigen spiegeln tatsächliche Items.
  - Event: Join-Phase lädt Lobby; Teilnehmer werden in Lobby teleportiert; nach Teleport in Event-Welt volle Werte; nach Event Inventar wiederhergestellt.

Wenn du bestätigst, implementiere ich die Änderungen und führe Build/Tests aus.