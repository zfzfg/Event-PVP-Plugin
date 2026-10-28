# Geprüfter Verbesserungsplan nach CocoIndex-Codeanalyse

Stand: 10. Oktober 2026. Nach zweiter Codeprüfung und ausführbaren Fehlerreproduktionen überarbeitet. Dieser Plan beschreibt geplante Änderungen; der Produktionscode ist unverändert.

## Ausgangslage und Umfang

Die betroffenen Implementierungen wurden über den CocoIndex-MCP semantisch gesucht und anschließend im lokalen Quellcode samt Aufrufpfaden geprüft. Der bestehende Maven-Testlauf mit dem installierten JDK 26 war erfolgreich: **412 Tests in 63 Testklassen, 0 Fehler, 0 Fehlschläge, 0 übersprungene Tests**.

Zusätzlich wurden **13 Diagnosefälle** ausgeführt, die das aktuelle fehlerhafte Verhalten reproduzieren. Das sind keine bestandenen Regressionstests für eine bereits erfolgte Reparatur. Nachweise und Wiederholungsanleitung: [Auditbericht](../tools/cocoindex-plan-audit/README.md), [Diagnoseprogramm](../tools/cocoindex-plan-audit/PlanAuditProbe.java), [Runner](../tools/cocoindex-plan-audit/run.ps1).

Die Reproduktionen verwenden MockBukkit-Inventare und gezielt simulierte Fehler. Der Token-Test erzwingt eine mögliche Thread-Reihenfolge; der Web-Thread-Test protokolliert den Zugriff vor der Scheduler-Übergabe und simuliert den Scheduler. Kein echter Purpur-Absturz oder realer Economy-Provider wurde getestet.

## 1. Item-Besitz zwischen Anfrage, Trade und Match eindeutig machen — zuerst

**Befund:** `LiveTradeGui.java:551–552` bzw. `663–664` entnimmt eingesetzte Items bereits vom Cursor bzw. aus dem Spielerinventar. `LiveTradeSession.java:254–273` übergibt diese Einsatzlisten an `startMatchFromCommand`. Diese Methode entfernt die Items in `MatchManager.java:1708–1713` nochmals aus dem Inventar. Gleichartige verbleibende Items können dadurch zusätzlich verloren gehen.

**Wichtige Ergänzung:** `LiveTradeBridge.startSessionFromRequest` kopiert angebotene Items in dieselbe Session, ohne sie zu entnehmen. Beim Abbruch werden diese Kopien zusätzlich zurückgegeben. Diagnose **01** reproduziert den doppelten Abzug; **02** die Duplikation von 8 auf 16 Diamanten. Diagnose **03** zeigt außerdem: Eine fehlende Arena führt zu einem stillen `return` im Matchstart, nachdem die Trade-Session bereits beendet/entfernt wurde; ihre Items werden weder übergeben noch zurückgegeben.

**Geplante Änderung:**

- **Eine Trade-Session darf ausschließlich tatsächlich entnommene Items halten.** Beim Übernehmen einer Befehlsanfrage vollständige Item-Mengen prüfen und entnehmen, erst danach die entnommenen Kopien aufnehmen. Bei unvollständiger Aufnahme oder Initialisierung genau die entnommenen Mengen zurückgeben.
- Gleiche Items über alle Angebotszeilen summieren und ihre Metadaten berücksichtigen. Zweimal 8 angebotene Diamanten benötigen insgesamt 16.
- Zwei explizite Einstiegspunkte verwenden: Befehlseinsatz aus dem Inventar vorbereiten und bereits gehaltenen Trade-Einsatz übernehmen. Arena-/Kit-/Teleportvorbereitung gemeinsam lassen. Ein pauschales Flag „kommt aus der GUI“ wäre vor der Bridge-Korrektur falsch, weil GUI-Listen bisher entnommene Items und unentnommene Kopien mischen können.
- Den Matchstart mit einem strukturierten Ergebnis versehen, das den Besitzer der Einsätze festlegt: beispielsweise `ACCEPTED`, `REJECTED_CALLER_OWNS_STAKE`, `RETURNED_OR_DURABLY_QUEUED`. Keine Rückgabe im Caller, wenn der MatchManager bereits übernommen oder erstattet hat.
- Session erst nach bestätigter Übergabe aus dem Manager entfernen; während der Übergabe weitere Bestätigungen sperren. Online-Status, Match-/Sessionkonflikte, Arena und **beide** Equipment-Sets vor dem Start prüfen.
- Inventarbackups weiterhin **nach** der tatsächlichen Einsatzentnahme und **vor** dem Arena-Teleport erfassen. Sonst kann der Restore einen verlorenen Einsatz zurückgeben und Gewinne duplizieren.

**Regressionstests / Abnahmekriterium:** Frische GUI, Befehlsanfrage mit Vorbefüllung, gemischte Vorbefüllung plus manuell hinzugefügte Items, wiederholter Start, fehlende Arena/Kit und Disconnect prüfen. Bei zwei identischen Stacks und einem eingelegten Stack bleibt der zweite erhalten. Ein Abbruch gibt genau den tatsächlich gehaltenen Einsatz zurück. Die Summe aus Inventar, Einsatz, Rückgabe und bestätigter Vormerkung bleibt erhalten.

## 2. Vault-Abbuchungen prüfen und fehlgeschlagene Starts zurückrollen — hohe Priorität

**Befund:** `MatchManager.java:184–187` und `1718–1721` ignorieren die Rückgabe von `withdrawPlayer`. Die Rückzahlungen in `1006` und `1050` prüfen das Ergebnis ebenfalls nicht. Eine vorangegangene Guthabenprüfung macht zwei getrennte Abbuchungen nicht zu einer atomaren Transaktion.

Diagnose **04** reproduziert eine erfolgreiche erste und abgelehnte zweite Abbuchung: Trotzdem entstehen Match und Journal für beide Beträge. Diagnose **12** reproduziert einen Item-Pot über 16 Diamanten, obwohl nur 8 entnommen werden konnten.

**Präzisierung:** `handleWagerConfirmation` hat im aktuellen Repository keinen internen Aufrufer. Vorrang hat der tatsächlich verwendete `startMatchFromCommand`-Pfad. Den alten öffentlichen Pfad kompatibel an die neue Vorbereitung anschließen, sofern er beibehalten wird; keine Behauptung, dass beide aktuell vom GUI genutzt werden.

**Geplante Änderung:**

- `EconomyResponse.transactionSuccess()` bei jeder Ab- und Rückbuchung auswerten.
- Tatsächlich abgebuchte Beträge getrennt von angebotenen Beträgen erfassen. Nur bestätigte Abbuchungen dürfen Teil des Geldpots werden.
- Arena, Kits, Teilnehmer und Einsatzdaten vor jeder Entnahme prüfen. Bei einem Fehler die weitere Vorbereitung stoppen und nur bereits erfolgreich entnommene Einsätze gezielt zurückgeben.
- Scheitert eine Rückzahlung, diese dauerhaft vormerken, statt den Betrag als zurückgezahlt zu behandeln.
- Geldwerte an der Übergabegrenze auf endlich, nicht negativ und vorhandene Limits einschließlich vorhandener Bypass-Berechtigungen prüfen. Die Logik aus `InputValidator.validateMoney` wiederverwenden; keinen widersprüchlichen zweiten Validator einführen.
- Inventar-Entnahmen anhand eines Snapshots planen, auf dem Main-Thread durchführen und tatsächlich entnommene Mengen bzw. `removeItem`-Restmengen erfassen. Diese Prüfung darf bereits übernommene GUI-Einsätze nicht betreffen.
- Einen unvollständig finanzierten Start nicht über den normalen Matchende-Pfad erstatten: Dieser setzt bereits gültige Einsätze voraus und könnte nicht abgebuchte Beträge auszahlen.

**Regressionstests / Abnahmekriterium:** Erste Abbuchung erfolgreich, zweite abgelehnt; geändertes Guthaben zwischen Angebot und Start; Rückzahlung abgelehnt; ungültige Geldwerte; fehlende Item-Mengen. Ein Match darf mit einem unvollständig finanzierten Pot nicht starten.

## 3. Auszahlungen und Journal-Übergaben gegen Fehler absichern — hohe Priorität

**Befund:** `PendingPayoutStore.java:241` ignoriert das Ergebnis von `depositPlayer`. `deliverOrQueue` meldet trotzdem eine erfolgreiche Ausgabe. `deliverAll` entfernt sämtliche Posten und speichert diese Entfernung vor der tatsächlichen Übergabe (`188–197`); bei einer nachfolgenden Exception sind auch noch nicht ausgegebene Posten nicht mehr vorgemerkt. Außerdem gibt `save()` keinen Fehler an den Aufrufer zurück. `ActiveWagerJournal.refundOpen` entfernt den Einsatz deshalb auch dann, wenn das Speichern der Rückerstattung fehlgeschlagen ist (`195–200`).

Reproduziert durch **05** (Deposit FAILURE), **06** (Exception löscht auch unangetastete Folgeposten), **07** (gescheiterte Speicherung löscht ursprünglichen Anspruch) und **13** (fehlende Economy löscht Geldposten als angeblich ausgegeben).

**Geplante Änderung:**

- Die boolesche Auszahlungsschnittstelle durch mindestens `DELIVERED`, `QUEUED_DURABLE`, `FAILED` ersetzen: Bisher unterscheidet `false` keine dauerhafte Vormerkung von einem ungesicherten Anspruch.
- Neue Auszahlungsdatensätze zuerst dauerhaft speichern, bevor ein Einsatzjournal oder Match seine Verantwortung abgibt. Speicherzustände erst nach erfolgreichem Schreiben als bestätigt veröffentlichen. Ein rein im Speicher vorhandener `hasReason`-Treffer ist kein Persistenznachweis.
- Pending-Payouts über eine temporäre Datei und Dateiersetzung speichern; Fehler nicht als Erfolg quittieren.
- Einen aktiven Einsatz erst vergessen, wenn seine Auszahlung erfolgreich erfolgt oder seine Rückerstattung nachweislich dauerhaft gespeichert ist.
- Posten einzeln bearbeiten und Item-/Geldanteile getrennt verfolgen. Bei einer abgelehnten Geldzahlung dürfen bereits übergebene Items beim nächsten Versuch nicht nochmals ausgegeben werden.
- Stabile IDs aus Vorgang/Match, Empfänger und Auszahlungstyp verwenden. Ein Siegerposten referenziert beide konsumierten Einsatzquellen; Recovery darf daraus nicht zusätzlich Rückerstattungen an beide Teilnehmer erzeugen.
- Auszahlungsbelege auch für abgeschlossene Posten erhalten, solange zugehörige Ursprungsjournale wieder auftauchen können. Nur offene Posten zu deduplizieren reicht nicht, wenn die Auszahlung erfolgt, aber die Journalentfernung scheitert.
- Die vorgezogenen InventoryGuard-Payout-Claims in `returnOwnStake` und `distributeWinnings` mit der dauerhaft bestätigten Übergabe abstimmen. Ein fehlgeschlagenes Queue darf nicht zugleich einen irreversiblen `payoutDone`-Status und ein vergessenes Einsatzjournal hinterlassen. Restore-Schutz und nicht idempotente Event-Belohnungsbefehle weiter berücksichtigen.
- Geldansprüche bei fehlender Economy bereits beim Aufrufer erhalten: `hasEconomy() ? wager : 0` darf sie nicht verschwinden lassen.
- Alte YAML-Einträge beim Laden in das neue Format übernehmen.

**Grenze des Verfahrens:** Eine Plugin-Datei, ein Spielerinventar und eine Vault-Buchung bilden keine gemeinsame Transaktion. Sowohl Ab- als auch Gutschriften können bei einem Absturz nach externer Änderung, aber vor ihrer Journalbestätigung mehrdeutig bleiben. Eine Exception beweist ebenfalls nicht immer, dass keinerlei Teiländerung erfolgt ist. Solche Vorgänge als **ungeklärt** erhalten, nicht blind wiederholen und über den Rescue-/Diagnoseweg zugänglich machen. Dieser zusätzliche Zustand wird dort bislang nicht unterstützt. Ein bloßes Umstellen auf „erst auszahlen, dann löschen“ schafft Duplikationsrisiken; „genau einmal trotz beliebigem Serverabsturz“ ist mit den vorhandenen APIs keine belegbare Garantie.

**Regressionstests / Abnahmekriterium:** Deposit meldet FAILURE; Economy ist vorübergehend nicht verfügbar; Übergabe wirft eine Exception; Schreiben der Payout-Datei scheitert; Neustart nach erfolgreicher Journal-Übergabe; gemischte Item-/Geldauszahlung scheitert teilweise. Noch offene Ansprüche bleiben nachvollziehbar erhalten und bestätigte Teile werden nicht erneut ausgegeben.

## 4. Item-Rückgaben bei vollem Inventar erhalten — hohe Priorität

**Befund:** `LiveTradePlayer.returnItems` und `LiveTradeGui.handleOwnItemSlotClick` ignorieren die Restmengen aus `Inventory.addItem`. Die Einsatzliste wird danach geleert bzw. der einzelne Eintrag ist bereits entfernt. Bei vollem Inventar können diese Items verschwinden.

Diagnose **08** bestätigt: Volles Inventar, keine Drops, Einsatzliste leer, 8 Diamanten verloren.

**Geplante Änderung:**

- Rückgaben über eine gemeinsame Routine führen, die Restmengen behandelt.
- Für Online-Spieler geklonte Stacks über den bestehenden `InventoryUtil.giveItems`-Weg mit Drops zurückgeben, sofern Inventar und Welt sicher verfügbar sind.
- Bei Offline-Spielern oder ausstehendem Restore dauerhaft vormerken und danach zustellen. `giveItems` steigt bei Offline-Spielern still aus und darf dort nicht als erfolgreiche Rückgabe gelten. Bei Schreibfehlern bleibt die Verantwortung beim bisherigen Besitzer.

**Regressionstests / Abnahmekriterium:** Volles und teilweise volles Inventar, GUI-Abbruch, Einzelitem-Rücknahme und Disconnect. Jede Rückgabemenge landet vollständig im Inventar, als Drop oder in einer bestätigten Vormerkung.

## 5. Login-Token atomar verbrauchen — mittlere Priorität

**Befund:** `WebAuthManager.java:74` liest den Token mit `get`, während `88` ihn erst später entfernt. Zwei HTTP-Threads können denselben gültigen Token lesen und jeweils eine Session erstellen. `WebServer` verarbeitet Anfragen mit vier Threads. Der vorhandene Test prüft nur zwei aufeinanderfolgende Einlösungen.

Diagnose **09** erzwingt den möglichen Ablauf „beide lesen vor Entfernen“ und erzeugt tatsächlich zwei Sessions. Der Test misst keine natürliche Häufigkeit des Wettlaufs.

**Geplante Änderung:**

- Direkt zu Beginn `activeTokens.remove(token)` statt `get` verwenden und den entnommenen Wert auf Ablauf prüfen. Nur der Thread mit diesem Wert erstellt eine Session; die spätere separate Entnahme entfällt. Kein globales Lock über alle HTTP-Anfragen nötig.
- Zusatzprüfung: Session-Ablaufzeitpunkt, Aktivitätsverlängerung und Cleanup atomar bzw. synchronisiert abstimmen. Nur `volatile` löst einen Cleanup-/Verlängerungswettlauf nicht vollständig. Dieser Zusatz ist durch Codeanalyse begründet, bislang nicht als gesonderter Diagnosefall reproduziert.

**Regressionstests / Abnahmekriterium:** Mehrere gleichzeitige Einlösungen eines Tokens erzeugen exakt eine Session. Abgelaufene Tokens, Cleanup und Aktivitätsverlängerung bleiben korrekt.

## 6. Web-Konfiguration kontrolliert speichern und anwenden — mittlere Priorität

**Befund:** `WebApiHandler.saveConfig` ruft `saveConfigFromMap` direkt vom HTTP-Thread auf (`59`). Diese Methode verändert die aktive Bukkit-Konfiguration und ruft `saveConfig`/`reloadConfig` auf (`WebConfigManager.java:207–212`). Erst das anschließende `applyGameplayReload` wechselt auf den Main-Thread. Bei `worlds.yml` und `equipment.yml` werden Schreibfehler nur protokolliert, während der API-Handler weiterhin Erfolg melden kann.

Diagnose **10** bestätigt einen erfolgreichen API-Status trotz gescheitertem Schreiben. **11** protokolliert die HTTP-Config-Änderung vor der Scheduler-Übergabe.

**Korrektur des ersten Plans:** Core-, PvP- und Event-Konfigurationen halten eigene geladene Daten. Der Fehler belegt nicht, dass durch diesen einzelnen Save sofort alle laufenden Matches neue Gameplay-Werte verwenden. Den vorhandenen verzögerten Gameplay-Reload erhalten; kein vollständiger Neuaufbau des Konfigurationssystems ist dafür erforderlich.

**Geplante Änderung:**

- Eingehende Daten vor dem Ersetzen in einer separaten YAML-Struktur auf Struktur und erforderliche Werte prüfen. Bestehende Migrationen/Range-Validierung berücksichtigen und valide benutzerdefinierte IDs erhalten.
- Schreibzugriffe je Datei ordnen. Fehler aller betroffenen Schreiber bis zur API weiterreichen. Speichern und Reload getrennt bestätigen; fehlgeschlagener Reload darf nicht als erfolgreich angewendet erscheinen.
- Bukkit-Konfigurationszugriffe und Anwendung auf dem Main-Thread ausführen. GET-Antworten aus sicheren Snapshots erzeugen; reine Dateiarbeit kann getrennt erfolgen, muss aber bestätigt und geordnet sein.
- Alle Schreiber derselben `config.yml` berücksichtigen: allgemeiner Save, Sprache, Debug und Providerwechsel. Ein gemeinsamer Schreibkoordinator mit aktuellem Dateistand bzw. Revision verhindert, dass ein Manager später seine alte Gesamt-Konfiguration über neue Änderungen schreibt.
- `hasLiveGameplay` berücksichtigt bisher nur Matches/Events. Auch aktive LiveTrade-Sessions und begonnene Übergaben bei verzögerten Web-Reloads berücksichtigen, da sie bereits Arena-/Kit-Auswahlen halten.
- Bestehende Providerwechsel-Sperre bei offenen Inventarsitzungen erhalten. Die bewusst sofortige Wirkung manueller Admin-Reload-Befehle prüfen und nicht beiläufig ändern.

**Regressionstests / Abnahmekriterium:** HTTP-Save/GET, konkurrierende Saves, echte Schreibfehler je Dateityp, Save gefolgt von Sprach-/Debug-/Provideränderung, aktive Trade-/Match-/Event-Sitzung, gültige/ungültige Daten und Reload-Fehler. Nur tatsächlich gespeicherte Änderungen bestätigen; keinen falschen Anwendungsstatus ausgeben.

## Reihenfolge und Abschlussprüfung

1. Diagnosefälle in Regressionstests mit dem gewünschten Verhalten übertragen. Die jetzigen Diagnoseassertionen beschreiben absichtlich Fehler und sind nicht unverändert als zukünftige Tests geeignet.
2. Einsatzübergabe und Rückgaben zusammen bearbeiten (Pakete 1 und 4). Die Vorbefüllungs-Duplikation hat dieselbe hohe Priorität wie der doppelte Abzug.
3. Pakete 2 und 3 als zusammengehörige Zahlungs-/Persistenzkorrektur umsetzen: sichere Ergebnis-/Übergabeschnittstellen zuerst, anschließend deren Aufrufer. Rollback nicht auf dem bisherigen ungesicherten Auszahlungspfad aufbauen.
4. Token-Korrektur separat, Web-Konfiguration anschließend bearbeiten.

Jedes Paket bekommt die genannten Regressionstests. Anschließend den vollständigen Maven-Testlauf erneut ausführen. Für die Freigabe zusätzlich den GUI-Matchstart, Rückgaben bei vollem Inventar, Disconnect/Rejoin, Restart-Recovery und Web-Speichern auf einem Testserver mit InventoryBackup, Vault-Economy und Multiverse prüfen. MockBukkit allein belegt diese Integrationen nicht vollständig.

Nicht Bestandteil dieses Plans: pauschaler Performance-Umbau, neue Datenbank, allgemeines Refactoring großer Manager oder ungeprüfte Dependency-Upgrades. Dafür liegen aus diesem Audit keine ausreichenden Nachweise vor.
