# Zweite Prüfung des Verbesserungsplans

Stand: 10. Oktober 2026. Die Diagnose wurde gegen unveränderte Produktionsklassen ausgeführt. Es wurde kein laufender Minecraft-Server verändert.

## Ergebnis

13 von 13 gezielten Diagnosefällen haben das gefundene Fehlerverhalten reproduziert:

| Fall | Beobachtung | Konsequenz |
| --- | --- | --- |
| 01 | GUI setzt 8 von 16 Diamanten ein; Matchstart entfernt auch die restlichen 8. | Gehaltene GUI-Items nicht erneut entnehmen. |
| 02 | Angebot kopiert 8 Diamanten in die GUI; Abbruch erhöht den Bestand auf 16. | Vorbefüllung muss echte Entnahme sein. |
| 03 | Fehlende Arena: Trade beendet, kein Match und keine Rückgabe. | Explizites Übergabeergebnis. |
| 04 | Zweite Geldabbuchung abgelehnt; Match und Journal entstehen trotzdem. | Vault-Antworten prüfen und Teilabbuchungen zurückrollen. |
| 05 | Deposit FAILURE; direkte Auszahlung meldet Erfolg ohne offenen Anspruch. | Geldanspruch erhalten. |
| 06 | Erste Zustellung wirft Exception; auch unangetasteter Folgeposten ist gelöscht. | Posten einzeln bearbeiten. |
| 07 | Payout-Schreiben scheitert; Einsatzjournal trotzdem geleert. | Ursprung erst nach bestätigter Persistenz aufgeben. |
| 08 | Volles Inventar: Einsatzliste leer, keine Items/Drops. | Restmengen behandeln. |
| 09 | Beide Threads lesen Token vor Entfernung; zwei Sessions entstehen. | Atomare Entnahme. |
| 10 | worlds.yml-Schreiben scheitert; API meldet success=true. | Schreibfehler propagieren. |
| 11 | HTTP-Thread ändert Config vor Sync-Dispatch; Gameplay-Reload aufgeschoben. | Auch vorherige Zugriffe koordinieren. |
| 12 | 8 Diamanten vorhanden, 16 angeboten: Match startet, Journal erstattet 16. | Mengen aggregieren, Entnahme prüfen. |
| 13 | Economy fehlt; vorgemerkter Geldposten als ausgegeben gelöscht. | Anspruch bei Economy-Ausfall behalten. |

Die vorhandene Maven-Suite war auf demselben Produktionscode grün: 412 Tests in 63 Testklassen, keine Fehler oder übersprungenen Tests.

## Wiederholung

Zuerst das Projekt mit dem erforderlichen JDK bauen und `mvn test` ausführen. Der Runner liest Test-Classpath und JDK-Pfad aus dem Surefire-Report; bei Bedarf einen JDK-Pfad mit `-JavaHome` angeben.

Aus dem Repository-Verzeichnis in PowerShell:

```powershell
& ./tools/cocoindex-plan-audit/run.ps1
```

Nur `PlanAuditProbe.java` wird separat kompiliert. Klassen, temporäre Daten und Ergebnisse liegen unter `target/cocoindex-plan-audit`; Produktionscode und reguläre Testquellen bleiben unverändert. Letzte Ausgabe: `DIAGNOSTIC_CASES_CONFIRMED=13`. Die Schreibfehler-Logs in Fällen 07 und 10 sind absichtliche Nachweise.

## Grenzen

- Diagnoseassertionen prüfen das **aktuelle falsche Verhalten**. Ein erfolgreicher Lauf heißt „Fehler reproduziert“, nicht „Plugin repariert“. Nach einer Reparatur gewünschtes Verhalten mit regulären Regressionstests prüfen.
- MockBukkit verwendet reale In-Memory-Inventare. Mockito simuliert Abhängigkeiten und Ablehnungen. Fall 02 führt Bridge und Abort aus; lediglich das Öffnen der GUI wird unterdrückt.
- Fall 03 ruft den echten Countdown-Abschluss per Reflection auf, danach laufen die regulären Scheduler-Tasks.
- Fall 09 setzt eine ConcurrentHashMap mit Barriere nach `get` ein. Das erzwingt den möglichen Ablauf „beide lesen vor Entfernen“, misst aber keine natürliche Häufigkeit des Wettlaufs.
- Fall 11 protokolliert Zugriffe und simuliert den späteren Sync-Scheduler-Callback. Er belegt die Reihenfolge im API-Code, keinen realen Purpur-Tickwettlauf und keine sofortige Änderung aller separaten Gameplay-Konfigurationen.
- Kein Realserver-Absturztest und keine volle Vault-/InventoryBackup-/Multiverse-Integration. Diese Prüfungen sind vor Freigabe der späteren Implementierung noch erforderlich.
