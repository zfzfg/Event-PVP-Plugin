## Überblick
- Implementiere einen World-Access-Listener: erzwingt Zugriffsregeln für `Eventworld`/`Eventlobby` abhängig von Event-Status, Permission und aktivem PvP-Match.
- Überarbeite PvPWager: vereinheitliche den Flow über `/pvp` und entkoppel die Annahme von Wetten; `/pvp accept` ist nur erlaubt, wenn der Herausforderer `SKIP` gewählt hat. Andernfalls muss der Gegner zuerst sein Item über einen eigenen Befehl setzen.

## Event-World Access System
- Aktivierung nur bei geladenen Welten:
  - Prüfe `Bukkit.getWorld(lobby)`/`Bukkit.getWorld(event)` vor jeder Verarbeitung und kehre frühzeitig zurück, wenn `null`.
  - Quellen: `EventSession.prepareWorlds()` und `WorldStateManager.ensureEventWorldReady(...)` bestätigen Ladepfad.
- Welt-Erkennung:
  - Nutze aktuelle Session-/Konfigwerte: `EventConfig.getLobbyWorld()`/`getEventWorld()` (src/main/java/de/zfzfg/eventplugin/model/EventConfig.java) und `EventManager.getActiveSessions()`.
- Regeln und Bypass:
  - Wenn kein aktives Event in der jeweiligen Welt läuft: erlaube Aufenthalt nur mit `eventpvp.world.access` oder `eventpvp.opbypass`.
  - Wenn Spieler in einem aktiven PvP-Match ist: erlauben, unabhängig von Permission. Prüfen über `MatchManager.getMatchByPlayer(...)` und `MatchState.FIGHTING` (z. B. src/main/java/de/zfzfg/pvpwager/managers/MatchManager.java:68–79, 254–260).
  - Zuschauer zählen nicht als „aktives Match“ (nur Teilnehmer). Optionaler Bypass via Permission.
- Durchsetzungs-Events:
  - `PlayerMoveEvent` und `PlayerTeleportEvent`: erkennen Betreten/Verweilen in Event-/Lobby-Welten und teleportieren bei Verstößen.
  - Fallback-Teleport: `settings.main-world` Spawn oder `Bukkit.getWorlds().get(0).getSpawnLocation()`; Hinweis-Nachrichten an den Spieler.
- Implementierung:
  - Entweder neue Klasse `de.zfzfg.eventplugin.listeners.WorldAccessListener` oder Erweiterung von `de.zfzfg.eventplugin.listeners.WorldChangeListener` (src/main/java/de/zfzfg/eventplugin/listeners/WorldChangeListener.java), damit Presence-Checks zusätzlich zu Command-Restriktionen ausgeführt werden.

## PvPWager – komplette Vereinfachung
- Zielbild der Befehle:
  - `/pvp challenge <player> [wager] [amount] [arena] [equipment]`: erstellt Anfrage; ohne weitere Argumente defaultet `wager=SKIP`.
  - `/pvp accept <player>`: funktioniert nur, wenn der Herausforderer `SKIP` gewählt hat; dann Start des Setups ohne Wette.
  - `/pvp deny <player>`: lehnt Anfrage ab.
  - Gegnerischer „eigener“ Befehl für Einsatz: weiterverwendung von `/pvpanswer` zum Setzen des Gegeneinsatzes, wenn nicht `SKIP`.
- Konkrete Codeanpassungen:
  - Unified Challenge erweitern:
    - `ChallengeSubCommand.execute(...)` (src/main/java/de/zfzfg/pvpwager/commands/unified/subcommands/ChallengeSubCommand.java:33–75) erweitert um optionale Args; nutzt `CommandRequestManager.addRequest(...)` statt `RequestManager.sendRequest(...)`, speichert Wette/ Arena/ Equipment analog zu `PvPACommand`.
  - Annahme nur bei SKIP:
    - `AcceptSubCommand.execute(...)` (src/main/java/de/zfzfg/pvpwager/commands/unified/subcommands/AcceptSubCommand.java:23–46) ändert den Flow: hole `CommandRequest` für Sender/Ziel; prüfe, ob `senderWagerItems` leer und `senderMoney==0` → SKIP. Nur dann Match-Setup starten (z. B. über `MatchManager.handleWagerConfirmation(...)` mit `Match#setNoWagerMode(true)`). Sonst: weise den Gegner an, `/pvpanswer` zu nutzen.
  - Klickbare Chat-GUI nur bei SKIP:
    - `CommandRequestManager.sendRequestNotification(...)` (src/main/java/de/zfzfg/pvpwager/managers/CommandRequestManager.java:76–102) zeigt ACCEPT/ABLEHNEN Buttons nur, wenn `request` `SKIP` (Items leer und Geld 0) ist; ansonsten nur Hinweis „Nutze `/pvpanswer`“.
  - Entferne paralleles, vereinfachtes Request-System:
    - Deaktivierung/Entkopplung von `RequestManager`-basierter Annahme (`sendRequest(...)`, `acceptRequest(...)`) aus Unified-Kommandos, damit es keine doppelte Logik gibt. Unified `/pvp` nutzt ausschließlich `CommandRequestManager`.
  - Start eines Matches aus Befehlen:
    - Nach erfolgreicher Annahme bei SKIP: setze `Match#setNoWagerMode(true)` und starte Arena-/Equipment-Auswahl wie heute (`MatchManager.handleArenaSelection(...)`).
    - Bei nicht-SKIP: erst nachdem der Gegner über `/pvpanswer` seinen Einsatz gesetzt hat, kann der Herausforderer bestätigen (optional über ein vereinfachtes `/pvp accept` oder kurzer Bestätigungsfluss in `MatchManager.handleWagerConfirmation(...)`).
- Kommandoreduktion:
  - Beibehalten: `/pvp` (challenge/accept/deny), `/pvpanswer` (Gegeneinsatz setzen), `/surrender`, `/draw`.
  - Ausblenden/Deaktivieren: `/pvpyes` und `/pvpno` (Legacy), `/pvpa` (Legacy-Erstellung). `plugin.yml` und `EventPlugin.onEnable()` entsprechend bereinigen.

## Sicherheit & UX
- Permissions:
  - Weltzugriff: `eventpvp.world.access` und `eventpvp.opbypass` für Staff.
  - Kommandos: vereinheitlichte Permission `pvpwager.command` bleibt bestehen.
- Fehlermeldungen & Hinweise:
  - Konsistente deutschsprachige Nachrichten über `MessageUtil` und `messages.yml`.
  - Klare Kommunikation, wann `/pvp accept` gesperrt ist und welcher Schritt als Nächstes notwendig ist (`/pvpanswer`).

## Tests & Verifikation
- Manuelle Testszenarien:
  - Spieler ohne Permission betritt `Eventworld`/`Eventlobby` bei inaktivem Event → wird zum Hauptspawn teleportiert.
  - Teilnehmer in aktivem Match dürfen sich in Event-/Lobby-Welten bewegen; Zuschauer ohne Permission werden teleportiert.
  - `/pvp challenge <player>` ohne Argumente → Anfrage mit `SKIP`; Zielspieler sieht klickbare Accept/Decline Buttons; `/pvp accept` startet Setup.
  - `/pvp challenge <player> MONEY 50 ...` → Zielspieler sieht keinen Accept-Button; muss `/pvpanswer` ausführen; erst danach ist Bestätigung möglich.
- Code-Pfade validieren:
  - `MatchManager.getMatchByPlayer(...)` und `MatchState.FIGHTING` für Weltzugriff.
  - `CommandRequestManager` für Anfragen, Annahme-Gates und Benachrichtigung.

Wenn das so passt, setze ich die Änderungen im Code um und führe die Tests aus.