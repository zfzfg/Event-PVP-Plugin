# Lokalisierungs-Audit: hardcodierte und falsch gebundene Spielertexte

Gesamtdokumentation des Problems, der Behebung und des heutigen Stands.
Ersetzt die früheren Arbeitsdateien `AUDIT_FIX_PROMPT.md` (Auftrag für die
Stufen D1–D7) und `AUDIT_NEXT_STEPS.md` (Auftrag für die Restarbeit).

Stand: 09.08.2026 — **alle neun Regeln melden 0, das Baseline-File ist leer.**
(08.08.2026 erstmals erreicht; am 09.08.2026 nach der Multiverse-Weltverwaltung
wiederhergestellt, siehe Abschnitt 2 „Multiverse-Weltverwaltung“.)

---

## 1. Das Problem

Ein Minecraft-Server stellt seine Sprache über `settings.language` ein. Das
Plugin hielt sich nicht daran: Texte standen fest im Java-Code, Keys zeigten auf
Sektionen, die ihr Helper nie liest, und Platzhalter fehlten in den Vorlagen.
Für den Spieler sah das so aus:

- `/eventpvp debug` zeigte als Überschrift die Zeile `status-header` — der
  Helper gab den **Key als eigenen Default** zurück, wenn der Wert fehlte.
- Die Event-Ankündigung `messages.start.join-phase-started` lautete nur
  „Join phase started! Use /event join". Der Code ersetzte dort `{event}`,
  `{description}`, `{min}`, `{max}`, `{time}` — **weder Name noch Spielerzahl
  noch Zeit** kamen an, ohne dass irgendetwas nach Fehler aussah.
- `on:` / `off:` unter `messages.debug.help` waren unquotiert. YAML 1.1 macht
  daraus `true:` / `false:` — die Hilfe fand ihre eigenen Einträge nicht.
- `TeamManager.Team.GREEN` trug `"&a&lGrün"` fest im Enum: deutscher Text
  mitten in einer englischen Sitzung.
- `InventoryRestoreCommand` war komplett unlokalisiert — zwölf deutsche Sätze,
  die auch bei `language: en` erschienen.

Der Startbefund: **552 Funde, davon 129 kritisch.** Sie lagen in
`tools/i18n_audit_baseline.json` eingefroren — also *bekannt und akzeptiert*,
**nicht** behoben.

### Der Regelkatalog

| Regel | Bedeutung | Spieler betroffen? |
|---|---|---|
| D1 | Helper endet in `getString(pfad, key)` — der Key ist sein eigener Default | ja, direkt |
| D2 | Code fragt einen Key an, den keine Lookup-Stufe seines Helpers findet | ja, direkt |
| D3 | Unquotierter `on:`/`off:`-Key, den YAML 1.1 zu einem Boolean macht | ja, direkt |
| D4 | `{x}` wird im Code ersetzt, fehlt aber im Template — Wert geht verloren | ja, stille Fehlinfo |
| D5 | Anzeigetext fest in Enum-Konstanten, ignoriert die Sprachwahl | ja |
| D6 | Literal geht direkt an `sendMessage` & Co. statt durch ein Bundle | ja |
| D7 | Anzeigetext ausserhalb erkannter Senken (inkl. Web-Assets) | teils, Konsistenz |
| D8 | Bundle-Parität: fehlende/überzählige Keys, leere oder TODO-Werte | ja, in Fremdsprachen |
| D9 | Bundle-Key, den niemand liest | nein, Konsistenz |
| D10 | Web-Panel-Pendant zu D2: `i18n.t('literal')` ohne passenden Key in `web/lang/*.json` | ja, direkt |
| D11 | Web-Panel-Pendant zu D9: `web/lang/*.json`-Key, den nichts liest | nein, Konsistenz |

---

## 2. Was geändert wurde

Gearbeitet wurde in Stufen, jede einzeln verifiziert. Die Reihenfolge war
bewusst gewählt: frühe Stufen machen Fehler sichtbar, die spätere schliessen.

### D1 — Key-als-Default (13 → 0)

In 9 Klassen gab der Helper bei fehlendem Wert den Key zurück. Ersetzt durch
einen expliziten Marker plus einmalige Konsolenwarnung je Key:

```java
String value = messages.getString("messages.debug." + key, null);
if (value != null) return value;
warnMissingKey("messages.debug." + key);
return "&c[missing: " + key + "]";
```

Der Marker `&c[missing: <key>]` ist seitdem projektweit einheitlich — bewusst
unlokalisiert, damit im Fehlerfall der Key-Pfad lesbar bleibt.
**Erwartete Nebenwirkung:** D2 stieg an, weil bis dahin unsichtbare fehlende
Keys nun auffielen.

### D2 + D3 — fehlende und unerreichbare Keys (81 → 0)

Pro Fund war zuerst zu klären, ob der Key **falsch adressiert** war (dann den
passenden Helper benutzen, nicht einen neuen Key anlegen) oder wirklich fehlte
(dann in allen 7 Sprachdateien anlegen). `'on':` / `'off':` wurden quotiert.

### D4 — Platzhalter (32 → 0)

Templates und `.replace(...)`-Aufrufe in Einklang gebracht;
`start.join-phase-started` trägt `{event}` und `{time}` wieder.

### D5 — Enum-Anzeigetexte (1 → 0)

`TeamManager.Team` liefert jetzt `getTranslationKey()`, aufgelöst an der
Ausgabestelle — nach dem Muster von `DebugLevel` / `DebugOutput`.

### D6 — hardcodierte Nachrichten (34 + 24 → 0)

Erst 34 Funde behoben, dann kamen 24 weitere dazu: `MessageUtil.error(...)`,
`sendMessages(...)` und `TextUtil.send(...)` waren **nicht als Spieler-Senken
registriert**, wodurch der Scanner sie nie gesehen hatte. 14 davon deutsch,
allein 12 in `InventoryRestoreCommand`. Nach Registrierung der Senken (mit
Regressionstest) waren alle behoben.

### Die Übersetzungen selbst (1543 Werte)

Kein Detektor konnte das sehen: Ein früherer Durchgang hatte „100 % Parität"
erreicht, indem `messages_en.yml` über alle anderen Bundles kopiert wurde. Alle
8 Dateien waren byte-identisch — ein deutscher Spieler las
`&cYou don't have permission for this command!`. **D8 meldet das nicht: der Key
existiert ja, nur der Wert war ersetzt.**

Wiederhergestellt aus einer Sicherung, danach 1543 Werte neu übersetzt
(de 125, es 284, fr 282, ja 284, pl 284, ru 284).

### D7 — Anzeigetext ausserhalb erkannter Senken (300 → 0)

Drei verschiedene Ursachen hinter einer Zahl:

- **71 waren Protokoll, keine Prosa** — `"Content-Type"`,
  `"application/json; charset=UTF-8"`, `"/api/auth/login"`,
  `"Method Not Allowed"`. Der Detektor erkennt Header-Namen, MIME-Typen,
  Routen, Status-Texte, Cookie-Attribute und CORS-Listen jetzt als das, was sie
  sind — jedes Muster verankert, damit Prosa *um* ein Token weiterhin auffällt.
- **41 waren Code-Identifier** — `DIAMOND_CHESTPLATE`, `PVP_MATCH_PRE`,
  Zeitstempelmuster, und der eigene `&c[missing: …]`-Marker.
- **82 waren echte Strings ohne Spielerkontakt** — Multiverse-Konsolenbefehle
  und deren Log-Diagnosen, Exception-Texte, `[SafeRespawn-PvP]`- und
  `[VoidProtection]`-Meldungen. Jede trägt `// i18n-ignore` **mit Begründung
  daneben**, statt eine Config-weite Stummschaltung zu setzen.

Vier waren echte Fehler und wurden lokalisiert: `MoneySelectionGui` mischte
englischen Titel `ALL IN!` mit deutscher Warnung auf demselben Button,
`NegotiationGui` zeigte `&8Kein Geld` in jeder Sprache, `ResponseGui`
beschriftete die Gegner-Kiste `&6&lGegner Items`, und
`MessageUtil.formatItemList` gab das Literal `"no items"` in Chat-Nachrichten
zurück, obwohl `MatchManager` für denselben Zweck längst
`messages.utility.no-items` las.

### Das Web-Panel (57 → 0)

Erste Einschätzung war falsch — das Panel hat sehr wohl eine eigene i18n
(`web/lang/<code>.json`, sieben Sprachen, inzwischen 135 `data-i18n`-Attribute
in `index.html`). Die Funde waren Code, der daran vorbeigeschrieben wurde:

- 6 deutsche Entwicklerkommentare (der Web-Scanner übersprang nur Zeilen, die
  *mit* `//` beginnen; er entfernt Kommentare jetzt quote-bewusst),
- 38 deutsche Inline-Defaults an bereits übersetzten Elementen — dieselbe Falle
  wie bei `messages.gui.pvpask.*`, wo der „englische Master" selbst deutsch war,
- 13, die `i18n.t()` umgingen.

**Dabei kam ein unsichtbarer Fehler heraus:** `es.json` hing **145 Keys** hinter
`en.json` zurück. `i18n.t()` gibt bei fehlendem Eintrag den Key zurück, also las
ein spanischer Admin rohe Namen wie `spawn.radius` — das sieht aus wie ein
Layout-Fehler, nicht wie eine fehlende Übersetzung. Alle 145 übersetzt; D8
vergleicht die Web-Bundles seitdem mit.

### D8 — Bundle-Parität (→ 0)

`messages.yml` wird von keinem Loader geladen (jeder baut `messages_<lang>.yml`).
Produktentscheidung: **bleibt ausgeliefert, wird nicht mehr gepflegt** — der
D8-Hinweis ist über `legacy_bundles_accepted` beantwortet statt ignoriert.
189 tote Keys in den Web-Bundles (179 in `es.json`, je 5 in `pl`/`ru`) wurden
als tot nachgewiesen und entfernt; alle sieben halten jetzt dieselben 555 Keys.

### D9 — unbenutzte Keys (113 → 0)

Zuerst wurden 4 echte Lücken verdrahtet (u. a. `messages.general.cooldown`, das
`CommandCooldownManager` als englisches Literal einkompiliert hatte, und
`pvpdeny.request-removed-offline`, dessen Code-Zweig über `getOnlinePlayers()`
nach einem ausgeloggten Spieler suchte und ihn nie finden konnte).

Die verbleibenden 107 wurden auf **fünf unabhängigen Wegen** als tot
nachgewiesen — Volltextsuche des Key-Pfads, Leaf-Literal-Suche, die *nicht
gewählten* Kandidaten aller lebenden Helper-Ketten (0 von 107), dynamisch
gebaute Keys inklusive PlaceholderAPI und Web-Panel, und die Admin-Doku — und
dann entfernt. Es waren Umbenennungsreste aus drei Generationen:
`livetrade.*` → `livetrade.broadcast-*`, `system.debug-*` →
`debug.messages.*`, und `messages.wager.*` als abgelöster Chat-Wager-Flow.

Eine echte Lücke fiel dabei auf: `SpectateSubCommand` prüfte nicht, ob der
Spieler bereits zuschaut. Dieser Text wurde nicht gelöscht, sondern in die
lebende Gruppe umgezogen (`messages.command.pvp.spectate.already-spectating`)
### Konsole & Terminal — Echte i18n-Lokalisierung (`messages.console.*`)

Nachdem alle Spieler- und Web-Meldungen lokalisiert waren, nutzte der Server im Terminal weiterhin deutsche bzw. unlokalisierte Log-Nachrichten. **Statt Log-Texte stumpf hart nach Englisch umzuschreiben, wurden alle Konsolen- und Terminal-Ausgaben zu echten i18n-Nachrichten in allen 7 Sprachen umgebaut**:

- **Auflösungs-Hierarchie (`CoreConfigManager.getConsoleMsg`)**:
  - `messages.console.<key>` in der konfigurierten Sprache
  - Fallback zu `messages.system.<key>`
  - Fallback zur englischen Master-Sprachdatei (`messages_en.yml`)
  - Fallback-Marker `&c[missing: messages.console.<key>]`
  - Automatische Platzhalter-Ersetzung (`{player}`, `{world}`, `{coords}`, `{error}`, `{group}`) und Bereinigung von Farbcodes für das Terminal.
- **100% Bundle-Parität (D8)**: Über 70 Konsolen-Keys in allen 7 Sprachdateien (`de`, `en`, `es`, `fr`, `ja`, `pl`, `ru`) mit identischen Platzhaltern angelegt.
- **Verdrahtete Kernklassen**: `EventPlugin`, `AutoEventManager`, `ConfigManager`, `EventSession`, `EventListener`, `VoidProtectionListener`, `PvPListener`, `MatchManager`, `SpawnManager`, `ArenaManager`, `SpectatorRecoveryListener`, `LiveTradeSession`, `WorldStateManager`, `MultiverseHelper`, `ConfigurationService`, `WebServer`.
- **Verifikation**: `tools/i18n_audit.py` (0 Befunde), `pytest tools/tests` (45/45 bestanden), Maven `BUILD SUCCESS`.

### Zwei Blindstellen aus dem Panel-Umbau (08.08.2026)

Beim Aufteilen des Web-Panels in Basis- und Experten-Einstellungen kamen zwei
Fehlerklassen heraus, die **kein Detektor sehen kann** — beide gehören zu
Regel 6 („eine Null bedeutet nicht automatisch sauber"):

- **Der Browser-Cache schlägt jede Bundle-Prüfung.** `StaticFileHandler` setzte
  `Cache-Control: public, max-age=3600` auf *alle* statischen Dateien, also auch
  auf `/lang/*.json`. Nach einem Plugin-Update hielt der Browser bis zu eine
  Stunde das alte Bundle fest, während `index.html` schon das neue war — neue
  Keys erschienen als roher Key-Name auf dem Bildschirm. Auf der Platte war
  alles korrekt, D8 meldete 0, und trotzdem las der Admin `expert.title`.
  Behoben: `html`/`js`/`json` gehen mit `no-cache, no-store, must-revalidate`
  raus, nur unveränderliche Assets behalten die Stundenfrist.
  **Merksatz:** Ein sauberes Bundle im Repo beweist nicht, dass der Client es
  auch bekommt. Nach jeder Bundle-Änderung einmal hart neu laden, bevor man
  einen „fehlenden Key" für echt hält.
- **D6 & D7 schlossen Logger-Senken früher aus.** Vor der Konsolen-Lokalisierung
  wurden Konsolenausgaben (`getLogger().info(...)`, `warning(...)`, `severe(...)`)
  in `i18n_audit_config.yml` als reine Debug-Logs ohne Spielerbezug eingestuft.
  Daher prüfte D6 nur `player`-Senken (`sendMessage`, `sendTitle`, etc.) und D7
  übersprang `logger` explizit.
  *Auswirkung:* Hardcodierte deutsche/englische Texte in `getLogger().info(...)`
  (wie im `UpdateChecker`) fielen weder D6 noch D7 auf.
  *Behebung:*
  1. `getConsoleMsg` ist jetzt offizieller Lokalisierungs-Helper in `localization_methods`.
  2. D6 prüft nun **sowohl `player`- als auch `logger`-Senken**. Unlokalisierte
     Texte in Logger-Aufrufen ohne `getConsoleMsg` oder `// i18n-ignore` werden
     sofort als hardcodierte Meldungen gemeldet.
  3. Regressionstest `test_d6_flags_hardcoded_logger_message` sichert dies dauerhaft ab.

### Korrekturen am Werkzeug selbst

Fünf echte Fehlalarme/Lücken wurden nicht weggeklickt, sondern behoben — jeder mit
Regressionstest:

- `getString` auf `config.yml` galt als fehlender Message-Key,
- der GUI-Helper `t(...)` und Konsolen-Helper `getConsoleMsg(...)` waren unbekannt,
- das spanische `&a&l¡TODO ELEGIDO!` wurde als TODO-Marker gelesen (*todo* =
  *alles*),
- Kommentare in Web-Assets zählten als Anzeigetext,
- Logger-Aufrufe mit hardcodiertem Text wurden von D6 übersprungen.

Testsuite: **31 → 46 Tests.**

---

### Multiverse-Weltverwaltung (09.08.2026)

Die neue Weltverwaltung im Web-Panel brachte **40 neue Funde** (0 kritisch, 39
Warnungen, 1 Info) — allesamt in neuem Code. Aufgeteilt in drei Ursachen:

**27 waren echte Befunde.** Die Fehlergründe der
Weltoperationen standen als fertige englische Sätze im Java-Code
(`MvResult.fail("Multiverse-Core is not installed")`) und wurden vom Panel in
einen bereits übersetzten Rahmen gesetzt — der Rahmen war lokalisiert, der Inhalt
nicht. Ein deutscher Admin las also „Weltoperation fehlgeschlagen: Multiverse-Core
is not installed“.

Behoben durch eine Trennung, die es vorher nicht gab: `MvResult` und die neue
`MvInputException` tragen jetzt einen **Bundle-Key** (`mv.error.*`) plus ein
optionales **`detail`** — den untranslatierbaren Rest, also den Original-Fehlertext
von Multiverse oder den abgelehnten Wert. Das Panel löst den Key auf und hängt
`detail` in Klammern an. 22 neue Keys in allen 7 `web/lang/*.json`.

Davon wurden **21 zu Bundle-Keys**; die restlichen **6 fielen ersatzlos weg**
(`"World already loaded"`, `"World already exists"`, `"World folder does not
exist"` …). Sie hingen an Erfolgsrückgaben, und der Erfolgsfall zeigt im Panel
ohnehin seinen eigenen Text — die Sätze wurden also nie irgendwo ausgegeben.

**12 waren Protokoll oder Bezeichner** — `mv create `, `mv load ` und die übrigen
Konsolenbefehle, Regex-Muster, Klassennamen für `Class.forName`, der interne
Marker `__MV_NOT_MANAGED__` und die Status-Token `RUNNING`/`SUCCESS`/`FAILED` der
JSON-API. Jedes trägt ein `// i18n-ignore` **mit Begründung daneben**.

**Ein deutscher Entwicklerkommentar** in einem neuen CSS-Block von `index.html`
wurde auf Englisch umgestellt.

#### Zwei Korrekturen am Werkzeug (Regel 4: Config *und* Regressionstest)

- **`mv.error.` in `literal_prefixes`.** Nach der Umstellung meldete D7 die
  Bundle-Keys selbst als Anzeigetext — dieselbe Situation wie bei den bereits
  eingetragenen `messages.` und `settings.`. Zwei Tests: einer, dass die Keys
  ignoriert werden, und einer, dass echte Prosa *daneben*
  (`fail("mv.error.loadFailed", "Multiverse could not load the world")`) weiterhin
  auffällt.
- **CSS-Deklarationen im Web-Scan übersprungen.** `flex-basis: 100%;` wurde als
  deutscher Text gemeldet: die Wortliste enthält `basis`, und der Vergleich lief
  ohne Rücksicht darauf, dass `basis` hier nur eine Silbe im englischen
  CSS-Bezeichner `flex-basis` ist. Dasselbe hätte `order`, `content` oder
  `aus`-haltige Custom-Properties getroffen. Das neue Muster `CSS_DECLARATION`
  verlangt eine reine Kleinbuchstaben-Eigenschaft, Doppelpunkt und
  abschliessendes Semikolon — Prosa mit Doppelpunkt bleibt damit ein Fund. Auch
  hier ein Gegentest: `content: "Welt nicht verfuegbar";` innerhalb eines
  `<style>`-Blocks wird weiterhin gemeldet.

Testsuite: **46 → 50 Tests.** Web-Bundles: **593 → 683 Keys** je Sprache
(+68 für die Weltverwaltung, +22 für `mv.error.*`).

---

### D10/D11 — die D9-Blindstelle im Web-Panel geschlossen (11.08.2026)

`i18n_audit.py` prüfte seit jeher nur `messages_*.yml` + Java. Das Web-Panel
(`web/lang/*.json` + `i18n.t()` in `app.js`/`editors.js`/`items.js`/
`index.html`) ist ein zweites, unabhängiges Übersetzungssystem — D1–D9 lasen
`web_files` nur als Wortliste, um *Java*-Keys nicht fälschlich als Web-only
zu markieren, nie um zu prüfen, ob ein *Web*-Key selbst noch gelesen wird.
Der Fund oben (`sidebar.web`, Abschnitt 3) war deshalb kein Einzelfall,
sondern ein dokumentierter, bekannter blinder Fleck ohne Detektor.

Ein Wegwerf-Skript (`tools/find_loose_ends.py --check-i18n`) fand beim ersten
Lauf sofort einen zweiten, echten Fall: `items.error.catalogFailed` ist in
allen sieben `web/lang/*.json` definiert und wird vom Server als
`messageKey` zurückgegeben, aber `loadMaterialsFromServer()` (`items.js`) las
nie mehr als `json.success` — der Key wurde nirgends aufgelöst. Behoben,
indem der Fehlerfall jetzt `i18n.t(json.messageKey || 'items.error.catalogFailed')`
ins Log schreibt (`apiErrorText()`, ein aus `inventoryErrorText()`/
`mvErrorText()` herausgezogener gemeinsamer Helfer).

Das eigentliche Ergebnis ist **D10** (`web-missing-key`, Pendant zu D2) und
**D11** (`web-unused-key`, Pendant zu D9) im echten Werkzeug, nicht nur im
Wegwerf-Skript — neues Modul `i18naudit/webi18n.py` plus
`detectors/webkeys.py`, `web:`-Sektion in `i18n_audit_config.yml` für die
serverseitig aufgelösten `messageKey`-Familien (`mv.error.`,
`inventory.error.`, `items.error.` — nie ein Literal im Quelltext, sondern
immer eine Variable zur Laufzeit) und automatische Erkennung dynamischer
Präfixe (`i18n.t('inventory.phase.' + x)`), analog zu `dynamic_key_roots`
auf der Java-Seite.

**Ein echter Fehlalarm dabei gefangen, bevor er in den Report ging:** die
erste Fassung von `is_referenced()` prüfte `attr_refs` und `literal_tokens`,
vergaß aber die direkt gesammelten `call_refs` selbst — jeder per
`i18n.t('literal')` verwendete Key erschien dadurch trotzdem als unbenutzt
(569 statt der echten 128 Funde). Der eigene Regressionstest
(`test_detectors_webkeys.py::test_d11_silent_on_a_direct_call_site`) hätte
das sofort gefangen, wäre er zuerst geschrieben worden — ist jetzt Teil der
Suite.

**D11 zeigt aktuell 128 echte, unabhängig geprüfte Funde** (`sidebar.web` ist
einer davon) — bestehende Altlast aus früheren Umbauten, kein Regressions-
Fund aus dieser Änderung. Bewusst nicht in die Baseline geschrieben: dieses
Projekt hält die Baseline leer und räumt Funde auf, statt sie zu verstecken
(vgl. D9 oben, 113 → 0). Das nächste Aufräumen dieser 128 ist eine eigene
Aufgabe.

Testsuite: **64 → 76 Tests.**

---

### Selbsttests als vierte Full-Suite-Komponente (11.08.2026)

**Auslöser:** genau der D11-Fehlalarm oben. `test_d11_silent_on_a_direct_call_site`
hätte den Bug (569 statt 128 Funde) beim ersten Lauf gefangen — *wenn* ihn
zu dem Zeitpunkt jemand ausgeführt hätte. Die „Full Suite" prüfte bis dahin
nur, ob das **Plugin** sauber ist; ob das **Werkzeug** selbst noch korrekt
funktioniert, war ein separater, leicht vergessener Schritt (`run_scans.bat`
Option 8 oder das README-Kommando von Hand). Jetzt ist `pytest tools/tests`
eine vierte, gleichberechtigte Komponente der Full Suite — neues Modul
`i18naudit/selftest.py`, Dashboard-Sektion `[n/N]` dynamisch nummeriert (nicht
mehr hart auf drei Komponenten verdrahtet), Ergebnis in beiden exportierten
Berichten (`i18n_audit_report.md`/`.json`, Feld `self_tests`). `--strict`
blockt jetzt **immer**, wenn die Selbsttests nicht sauber sind — unabhängig
von `--fail-on`, weil ein kaputter Selbsttest bedeutet, dass den Funden des
gerade gelaufenen Durchlaufs grundsätzlich nicht zu trauen ist.

**Ein selbst verursachter Vorfall dabei, live gefangen:** die erste Fassung
ließ `run_self_tests()` unbedingt einen Kindprozess `pytest tools/tests`
starten, sobald die Full Suite (Standardfall!) läuft. `test_cli.py` ruft
`cli.main()` aber **in-process** auf (kein Subprozess), mehrfach ohne
einschränkendes Scope-Flag. Der erste Lauf der neuen Selbsttest-Komponente
lief also mitten in `pytest tools/tests` selbst, rief darin erneut
`cli.main()` mit Full-Suite-Voreinstellung auf, der das *wieder* einen
Kindprozess `pytest tools/tests` startete — unbegrenzte Rekursion. Ergebnis:
über **500 hängende `python.exe`-Prozesse**, bevor der Ressourcenverbrauch
auffiel. Alle Prozesse per `taskkill /F /IM python.exe /T` beendet.

**Behoben mit einer strukturellen Sperre, nicht mit einem Testfall-Patch:**
`run_self_tests()` setzt vor dem Start des Kindprozesses die Umgebungsvariable
`I18NAUDIT_SELFTEST_RUNNING=1` und prüft sie als Erstes selbst — findet sie
sich bereits gesetzt vor, bricht sie sofort mit `skipped_recursion=True` ab,
ohne irgendetwas zu starten. Bewusst *nicht* nur `test_cli.py` um
`--no-selftest` ergänzt: das hätte den konkreten Fall behoben, aber jeder
künftige Test, der `main()` ohne dieses Flag aufruft, hätte die Bombe
unbemerkt wieder scharf gemacht. Zusätzlich `stdin=subprocess.DEVNULL` am
Kindprozess, da ein geerbtes Stdin beim ersten Debuggen ebenfalls zu
Hängern führte.

Testsuite: **76 Tests** (unverändert — der Vorfall betraf die
Orchestrierung, nicht den geprüften Code). `pytest tools/tests` läuft seitdem
mehrfach hintereinander direkt und über `i18n_audit.py --no-baseline` ohne
einen einzigen verwaisten Prozess.

---

## 3. Wie es jetzt ist

```
D1 key-as-default              0
D2 missing-key                 0
D3 yaml-boolean-key            0
D4 placeholder-mismatch        0
D5 untranslatable-display-name 0
D6 hardcoded-message           0
D7 natural-language-literal    0
D8 bundle-parity               0
D9 unused-key                  0
D10 web-missing-key            0
D11 web-unused-key           128  (offene Altlast, siehe Abschnitt „D10/D11")
```

- **Baseline-File leer** — es wird nichts mehr unterdrückt, jeder neue Fund
  fällt sofort auf. D11s 128 Funde sind absichtlich *nicht* hineingeschrieben.
- 7 Sprachdateien mit je **1080 Werten**, identischer Key-Menge (inkl. `messages.console.*`).
- 7 Web-Bundles mit je **850 Keys**, identische Key-Menge.
- `pytest tools/tests` = **76 passed**, `mvn clean package` = **BUILD SUCCESS**.
- `python tools/i18n_audit.py` prüft das seit 11.08.2026 **selbst mit** — die
  76 Tests sind die vierte Full-Suite-Komponente (`[4/4]` im Dashboard,
  Abschnitt „Selbsttests" in beiden exportierten Berichten).

**Die D9-Blindstelle ist seit 11.08.2026 geschlossen.** `sidebar.web` — der
tote Key aus der Basis/Experte-Aufteilung, den D9 grundsaetzlich nicht sehen
konnte, weil D9 nur `messages_*.yml` prueft — wird jetzt von **D10/D11**
gemeldet: einem eigenstaendigen Regelpaar fuer `web/lang/*.json` +
`i18n.t()`, das genau diese Luecke schliesst (siehe `i18naudit/webi18n.py`).
`sidebar.web` ist damit kein bewusst offener Fall mehr, sondern ein
regulaerer D11-Fund und sollte beim naechsten Aufraeumen entfernt werden.

**Bewusst offen:** `messages.yml` bleibt ausgeliefert, wird aber nicht gepflegt
und enthält weiterhin die entfernten Keys. Keine neuen Keys dort ergänzen.

**Nicht auf 0 zu bringen und auch nicht sinnvoll:**
`reports/untranslated_values.md` listet die Werte, die byte-identisch mit dem
Englischen sind — de 72, es 59, fr 59, ja 50, pl 62, ru 46. Was übrig ist, ist
zu Recht identisch: Trennlinien (`&6&l━━━`), reine Platzhalter (`{player}`),
Befehlssyntax (`/pvp leave`), Produktnamen (`PVP WAGER`) und Begriffe, die die
Zielsprache genauso schreibt.

Deutsch führt die Liste an und bleibt trotzdem unverändert: `Arena`, `Items`,
`Level`, `Status`, `Chat`, `Event`, `Match`, `Teleport`, `System`, `Listener`,
`Admin`, `Download` und `Version` **sind** die deutschen Wörter — sie zu
ersetzen wäre schlechteres Deutsch. Bei den anderen Sprachen wurden am
07.08.2026 die echten Rückstände nachgezogen (es 11, fr 12, ja 15, pl 9,
ru 15 Werte): Labels wie `Items:`/`Level:`/`Status:`/`Filter:`, die
Debug-Kategorien (`Event`, `Match`, `Teleport`, `System`, `Listener`, `Chat`)
und der Download-Hinweis. Maßstab war der Sprachgebrauch der jeweiligen Datei —
Französisch schrieb an anderer Stelle längst `Arène` und `objet`, Japanisch
`アリーナ` und `アイテム`.

**Sicherung:** `reports/backup_pre_d9_cleanup/` enthält die 7 Bundles vor der
D9-Löschung. Das Projekt steht nicht unter Versionskontrolle — die Kopie bleibt,
bis die Änderung einmal auf einem Server lief.

---

## 4. Regeln für künftige Arbeit an den Texten

1. **Vor jedem neuen Key `--list-helpers` ansehen.** Sonst landet er in einer
   Sektion, die der zuständige Helper nie liest.
2. **Niemals eine Sprachdatei über eine andere kopieren.** Parität heisst:
   gleiche *Keys*, unterschiedliche *Werte*. Fehlt ein Key, genau diesen Key
   mit echter Übersetzung ergänzen.
3. **YAML niemals per PyYAML round-trippen.** Load-und-Dump zerstört Kommentare
   und macht `'on':` wieder zu `true:`. Nur textuell editieren — und mehrzeilige
   Werte als ganzen Block, sonst bleiben Folgezeilen als Müll stehen.
4. **Detektoren nicht weichspülen.** Funde durch Änderungen an `tools/i18naudit/`
   oder `i18n_audit_config.yml` verschwinden zu lassen ist kein Fix. Bei einem
   *echten* Fehlalarm: Config anpassen **und** Regressionstest in
   `tools/tests/test_detectors_*.py` ergänzen. Nie die Baseline erweitern, um
   etwas zu verstecken.
5. **`// i18n-ignore` nur mit Begründung daneben** — und die Begründung muss
   stimmen. 33 Kommentare beriefen sich einmal auf ein „deutschsprachiges
   Web-Panel", das es nie gab.
6. **Eine Null bedeutet nicht automatisch sauber.** Siehst du offensichtlichen
   Anzeigetext, den kein Detektor meldet, ist das ein Fund *über* den Scanner —
   melden, nicht weitergehen. Genau so kamen die 24 versteckten D6-Funde und
   das spanische Web-Panel heraus.
7. **Keine Löschung von Keys oder Dateien ohne Rückfrage.**
8. **Keine Verhaltensänderungen ausserhalb der Lokalisierung.** Echte
   Logikfehler melden, nicht nebenbei umbauen.
9. **Neue Web-Texte immer in alle 7 `web/lang/*.json` eintragen — und danach
   hart neu laden.** Ein fehlender Web-Key fällt nicht als Fehler auf, sondern
   erscheint als sein eigener Name im Layout (`i18n.t()` gibt den Key zurück).
   Bevor du so einen rohen Key für einen Übersetzungsfehler hältst: erst
   `Strg`+`Shift`+`R`, dann prüfen — bis zum Cache-Fix vom 08.08.2026 lieferte
   der Server die Bundles mit einer Stunde Cache aus, und eine bereits
   gecachte Kopie läuft trotz Fix noch ab.
10. **Web-Keys beim Umbenennen von Markup mitziehen.** D9 prüft die Web-Bundles
    nicht auf unbenutzte Keys (nur `messages_en.yml`), D8 vergleicht sie nur
    untereinander. Wer einen `data-i18n`-Wert ändert, hinterlässt sonst
    unbemerkt einen toten Key — kontrolliere von Hand, ob der alte Key noch
    irgendwo in `index.html`/`app.js`/`editors.js` vorkommt.

---

## 5. Verifikation

```bash
# Standard: Vollstaendige Suite (Detektoren D1-D11 + Console Check + Untranslated Analyse)
python tools/i18n_audit.py --no-baseline

# Vollstaendiger Lauf mit Berichtsexport (Markdown & JSON nach reports/)
python tools/i18n_audit.py --no-baseline --export-markdown --export-json

# Einzelne Scans / Detektoren
python tools/i18n_audit.py --only-console
python tools/i18n_audit.py --only-untranslated
python tools/i18n_audit.py --only-i18n --only D6

# Lookup-Ketten der Message-Helper
python tools/i18n_audit.py --list-helpers

# Selbsttests der Suite (alle 76 Tests oder einzelne Module)
python -m pytest tools/tests -q
python -m pytest tools/tests/test_detectors_keys.py -q
python -m pytest tools/tests/test_detectors_yaml.py -q
python -m pytest tools/tests/test_detectors_hardcoded.py -q
python -m pytest tools/tests/test_console_check.py -q
python -m pytest tools/tests/test_untranslated.py -q
python -m pytest tools/tests/test_cli.py -q

# Bundles parsen
python -c "import yaml,glob; [yaml.safe_load(open(f,encoding='utf-8')) for f in glob.glob('src/main/resources/messages_*.yml')]; print('alle parsen')"

# Build
mvn clean package -DskipTests
```

Maven ist seit dem 07.08.2026 systemweit installiert (Chocolatey, **3.9.16**
unter `C:\ProgramData\chocolatey\lib\maven\apache-maven-3.9.16`) und steht im
Maschinen-PATH — `mvn` funktioniert also direkt. Nur in einer Shell, die vor der
Installation geöffnet wurde, fehlt der Eintrag noch; dort hilft `refreshenv`
oder ein neues Fenster.

Verifiziert mit Maven 3.9.16 auf JDK 26: `mvn clean package -DskipTests`
liefert BUILD SUCCESS.

**Das wichtigste Sicherheitsnetz ist D2.** Wird ein Key entfernt, den der Code
doch liest, meldet `missing-key` ihn sofort. D8 zeigt zusätzlich, dass alle
sieben Bundles dieselbe Key-Menge tragen — also keine Sprache halb bearbeitet
wurde.

---

## 6. Weiterführende Dokumente

| Datei | Inhalt |
|---|---|
| `tools/README.md` | Bedienung der `i18naudit`-Suite, Flag-Referenz, Aufbau der Detektoren |
| `reports/i18n_audit_report.md` / `.json` | aktueller konsolidierter Report |
| `reports/untranslated_values.md` | Werte, die mit dem englischen Master identisch sind |
| `reports/d9_unused_keys_review.md` | Entscheidungsvorlage und Nachweis zu den 107 entfernten Keys |
| `CHANGELOG_1.0.9.md` | Langfassung aller Änderungen mit Ursache und Auswirkung |
