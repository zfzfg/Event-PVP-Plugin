# Stabilitäts- und Sicherheitsänderungen für 1.1.2

Stand: 28. Oktober 2026. Implementierung auf Basis von `59b6aeb`; kein Deployment auf einen laufenden Server.

## Verhalten und Datenhaltung

- GUI-Einsätze und Inventarangebote haben getrennte Match-Einstiege. Der Einsatzdienst prüft die gesamte Menge gegen einen Inventarsnapshot; bereits gehaltene GUI-Items werden nicht erneut entnommen. Fehlende Kits, ungültige Geldbeträge und nicht verfügbare Economy verhindern den Start.
- Vault-Abbuchungen besitzen vor dem Aufruf einen persistenten Prüfbeleg. Nur bestätigte Abbuchungen fließen in den Pot. Item-Entnahmen besitzen ebenfalls Prüfbelege für das Zeitfenster bis zur Übernahme durch das Matchjournal.
- `pending-payouts.yml` verwendet Formatversion 2 mit Vorgangs-ID, Empfänger, Ursprungsreferenzen und getrennten Zuständen für Items, Geld und jeden Belohnungsbefehl. Abgeschlossene Belege bleiben zur Deduplizierung erhalten. Gewinneransprüche werden vor dem verzögerten Inventar-Restore gesichert.
- Bestehende Payout-Dateien werden beim Laden migriert; das Original bleibt als `pending-payouts.yml.v1-backup` erhalten. Unlesbare Einträge werden unter `quarantine` bewahrt. Bei beschädigten Daten werden weitere Auszahlungen gesperrt, bis ein Administrator die Datei anhand der Sicherung und externer Belege korrigiert hat.
- Bekannte Geldablehnungen bleiben erneut zustellbar. Unterbrochene oder nicht eindeutig beantwortete externe Aufrufe werden `REVIEW_REQUIRED`; es erfolgt keine automatische Wiederholung. Einheiten, die bereits bestätigt ausgegeben wurden, werden nicht erneut ausgegeben.
- Items, die nicht ins Inventar passen, bleiben vorgemerkt; sie werden nicht mehr als frei aufhebbare Drops zurückgegeben. Auszahlungen erfolgen erst, wenn keine Wiederherstellung das Inventar überschreiben kann. Event-Belohnungsbefehle werden ebenfalls im Beleg erfasst und beim sicheren Zustellversuch ausgeführt.

### Adminprüfung

Berechtigung: `eventpvp.admin`.

```text
/eventpvp payouts list
/eventpvp payouts inspect <id>
/eventpvp payouts resolve <id> delivered
/eventpvp payouts resolve <id> retry
```

`inspect` zeigt Empfänger, Betrag, Itemanzahl, Komponentenstatus und Ursprungsreferenzen. `delivered` bestätigt alle unklaren Komponenten des Vorgangs als erledigt. `retry` gibt diese Komponenten für den nächsten sicheren Zustellversuch frei. Bereits bestätigte Komponenten bleiben unverändert. Vor `retry` anhand des Economy-Providers beziehungsweise Spielerinventars prüfen, dass die betreffende Wirkung nicht bereits eingetreten ist. Entscheidungen werden mit Administrator und Vorgangs-ID geloggt. Ein durch einen bestehenden Auszahlungsbeleg konsumierter Einsatz kann nicht zusätzlich als Debit-Rückzahlung freigegeben werden.

Nach Behebung einer vorübergehenden Economy-Störung oder Schaffung von Inventarplatz erfolgt ein weiterer Zustellversuch beim nächsten Join beziehungsweise Restore-Abschluss. Beschädigte YAML-Daten sind kein normaler `resolve`-Fall: zuerst die erhaltenen Originaldaten prüfen.

Ein sicher genau einmal ausgeführter externer Vault-/Befehlsaufruf über einen Prozessabsturz hinweg wird nicht zugesichert. Der Prüfstatus hält dieses unvermeidbare Unsicherheitsfenster sichtbar. Vollständiger Datenträgerausfall und verlorene externe Providerdaten lassen sich damit nicht kompensieren.

## Welten und Dateisystem

- Weltjobs reservieren kanonische Zielpfade einschließlich überlappender Eltern-/Kindpfade. Konkurrierende Aufträge scheitern mit einem Konflikt statt dasselbe Ziel gleichzeitig zu bearbeiten.
- Backups werden in ein exklusives temporäres Verzeichnis entpackt. Erst nach erneuter Zielprüfung wird dieses ohne Überschreiben veröffentlicht. Fehlerbereinigung betrifft nur das vom Job angelegte Verzeichnis.
- Eigene Dateibaumoperationen folgen keinen Verknüpfungen. Nicht sicher bewertbare Links/Junctions werden abgelehnt. Evakuierungs- und Unloadfehler brechen den Löschablauf ab.
- Der Welt-I/O-Executor hat zwei Worker und maximal 16 wartende Aufgaben. Bukkit-/Multiverse-API-Aufrufe bleiben auf dem Main-Thread; die synchronen Kompatibilitätsmethoden bleiben synchron. Dateiarbeit innerhalb eines externen Multiverse-Backends liegt weiterhin in dessen Verantwortung.
- Laufende Jobs werden nicht durch die TTL-Bereinigung entfernt. Beim Shutdown werden neue Aufträge gesperrt und laufende I/O-Aufgaben unterbrochen. `interrupted-world-jobs.yml` dokumentiert unvollständige Jobs einschließlich bekannter Stagingpfade. Diese Datei ist ein Prüfbericht, keine automatische Wiederholungsanweisung.

Neue Konfigurationswerte unter `settings.world-io`:

```yaml
max-restore-bytes: 21474836480
max-restore-entries: 1000000
```

Die Bytegrenze betrifft tatsächlich entpackte Daten. Fehlende Schlüssel verwenden die genannten Defaults. Externe Plugins, die ohne diesen Dienst Weltdateien verändern, nehmen nicht an dessen Reservierungen teil.

## Webpanel, Konfiguration und Betrieb

- Dynamische Handler werden als Callback-Funktionen gebunden; Konfigurationstexte werden für ihre HTML-Kontexte escaped. Namen, Beschreibungen, Gruppen, Itemdaten und Fehlermeldungen werden nicht als ausführbarer Handlercode eingebaut.
- Tokenverbrauch ist atomar. Sessionvalidierung, Verlängerung und Cleanup sind koordiniert. HTTPS-Public-URLs aktivieren `Secure` für das Sessioncookie; Logout verlangt POST.
- Vier HTTP-Worker, eine Warteschlange mit maximal 64 Aufgaben und zwei gleichzeitig zugelassene teure API-Aufrufe begrenzen Last. Überlast bei teuren Aufrufen ergibt HTTP 503; volle Dispatcher-Warteschlangen können Verbindungen zurückweisen. Request-Deadlines schließen Auth-Anfragen nach 10 Sekunden und andere Anfragen nach 35 Sekunden.
- Die externe Bind-Adresse und der bisherige Standard bleiben erhalten.
- Konfigurationsänderungen laufen auf dem Main-Thread über einen gemeinsamen Dateischreiber. Sprach-, Debug- und Provideränderungen lesen vorher den aktuellen Dateistand. Dateiersetzungsfehler führen nicht zum ungeschützten Überschreiben des Originals.
- Config-GET liefert `revision`; das Panel sendet sie beim Speichern zurück. Veraltete Revisionen ergeben HTTP 409. Bestehende API-Clients ohne Revision bleiben kompatibel, besitzen aber keinen Schutz vor veralteten Gesamtdatei-Schreibzugriffen. Neue Clients sollten immer eine Revision senden.
- Antworten unterscheiden `saved`, `applied`, `deferred` und `failed`. `saved: true` mit `state: failed` bedeutet: Datei gespeichert, anschließender Reload fehlgeschlagen. Netzwerktimeouts erfordern erneutes Lesen vor einem weiteren Schreibversuch.
- Aufgeschobene Gameplay-Reloads warten auch auf LiveTrades; manuelle Admin-Reloads bleiben unmittelbar und melden Teilfehler.
- Texturimporte begrenzen einzelne PNGs auf 16 MiB und 4096 × 4096 Pixel, die übernommenen entpackten Texturen insgesamt auf 256 MiB und Archive auf 100.000 Einträge. Die konfigurierte Downloadgrenze bleibt zusätzlich wirksam. Ein vorhandener SHA-1 wird überprüft. Fehlerhafte Importe ersetzen den bisherigen Texturbestand nicht.
- Shutdown-Schritte sind voneinander isoliert. Statistikdateien werden aus unveränderlichen Textsnapshots geordnet geschrieben. `/api/status` enthält Auszahlungslage, Weltjob-Auslastung und Schreibdauer-/Fehlerzähler unter `payouts`, `worldJobs` und `persistence`.

## Prüfung und Freigabe

```powershell
./tools/verify.ps1 -JavaHome 'C:/Program Files/Eclipse Adoptium/jdk-26.0.2.101-hotspot'
```

Der Wrapper verlangt JDK 25 oder neuer und Node.js, prüft beide Webskripte sowie die Web-Sicherheitstests und führt Maven `verify` aus. Die lokale, nicht öffentlich veröffentlichte InventoryBackup-API muss wie bisher im Maven-Repository vorhanden sein. Es wurden keine Abhängigkeiten aktualisiert.

Neue Regressionstests behandeln unter anderem abgelehnte und unklare Geldtransaktionen, Teilauszahlungen, Quittungen nach Neustart, Itemmengen, fehlende Kits, volles Inventar, konkurrierende Restore-Jobs, Archivgrenzen, fehlerhafte Bildimporte, Schreibrevisionen, Main-Thread-Dispatch, Tokenwettläufe und isolierten Shutdown. Die alten Diagnoseassertionen unter `tools/cocoindex-plan-audit` prüfen absichtlich früheres Fehlverhalten und sind kein Freigabetest für diese Implementierung.

**Offene Freigabeprüfung:** Ein separater echter Purpur-Testserver mit passender Vault-Economy, InventoryBackup und Multiverse war lokal nicht verfügbar. Vor produktiver Freigabe dort GUI-Matchstart, beide Auszahlungsarten, Disconnect/Rejoin, Inventar-Restore, Neustart während einer Auszahlung, Web-Speichern sowie Welt-Restore/-Löschen prüfen. Dateisystemverknüpfungen zusätzlich unter Windows mit Junctions und unter Linux mit Symlinks testen. Die Unit-/MockBukkit-Tests ersetzen diese Integrationsprüfung nicht.

Für einen Rollback alle Plugin-Datendateien vor dem ersten Start sichern. Die alte Pluginversion versteht das neue Payout-Format nicht; nicht einfach das alte JAR über bereits migrierte und weiterverarbeitete Ledger starten. Zwischenzeitliche Auszahlungen anhand der Belege abgleichen.
