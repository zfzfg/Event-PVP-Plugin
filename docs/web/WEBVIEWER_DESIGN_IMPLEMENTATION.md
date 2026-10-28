# Webviewer – Umsetzung und Designprüfung

Stand: 28.10.2026. Umsetzung des WEBVIEWER_DESIGN_PLAN.md im vorhandenen HTML/CSS/JavaScript-Webviewer.

## Ergebnis

Die sieben Bereiche, das grüne Event-PVP-Erscheinungsbild und das Minecraft-Inventar bleiben erhalten. Dunklere neutrale Flächen, ruhigere Aktionen, lokale SVGs und native Navigationsbuttons verbessern die Lesbarkeit und Bedienbarkeit. Die Gestaltung bleibt bewusst eine kompakte Administrationsoberfläche.

- Neutrale Defaults in HTML, YAML, Reset und Farbfeldern: Hintergrund `#141414`, Oberfläche `#1c1c1c`, Karte `#242424`, Hover `#303030`. Gespeicherte Themes werden nicht migriert.
- Hilfs- und Metadaten nutzen die sekundäre Textrolle. Systemschrift, rem-Größen und tabellarische Zahlen; Minecraft-Itemzähler behalten ihre eigene Pixeltypografie.
- Deckendes Grün mit dunkler Beschriftung ersetzt den Primärbuttonverlauf. Bei individuellen Akzentfarben wird ausschließlich die Beschriftungsfarbe anhand der relativen sRGB-Luminanz abgeleitet; der gespeicherte Akzent bleibt unverändert. Akzent und Beschriftung wechseln gemeinsam, ohne kontrastarme Zwischenfarben.
- Gemeinsame lokale SVG-Registry für statische und dynamische Komponenten, inklusive Verzauberungen. Keine Font-Awesome-CDN-Abhängigkeit. Dekorative Emojis in allen sieben Übersetzungen entfernt; Nutzerdaten und Minecraft-Texturen werden nicht bereinigt.
- Native Buttons für alle sieben Navigationsziele, `aria-current`, sichtbarer Fokus, übersetzte Namen für Icon-Aktionen und Theme-Felder. Bestehende Dialog-Fokusbegrenzung, Escape und Fokusrückgabe weiterverwendet.
- Eigene auswählbare Karten, Editor-Tabs, Rüstungs- und Inventarslots sind zusätzlich per Enter/Leertaste aktivierbar. Doppelklick auf belegte Inventarslots hat eine Tastaturalternative. Hinzufügen über den Itempicker bleibt möglich, ohne Drag-and-drop.
- Status mit Symbol, Text und höflicher Live-Region; Reduced Motion und Reduced Transparency berücksichtigt. Benachrichtigungen überdecken keine Dialogaktionen.
- Headeraktionen brechen um. Die feste Sidebar berücksichtigt die tatsächliche Headerhöhe. Schalter schrumpfen in schmalen Dialogen nicht zusammen. Inventare scrollen lokal und behalten neun Spalten mit 42-px-Slots.
- Theme-Farbfeld und Hexwert werden nach Änderungen synchronisiert. Das Mapping von `text-secondary` wird beim Ändern ebenso wie beim Laden verwendet. Ungültige Hexwerte werden nicht gespeichert.

## Prüfung

Ausgangsstand: 15/15 Playwright-Tests und 3/3 Sicherheitsprüfungen bestanden. Der vorhandene, noch unversionierte Designplan wurde nicht verändert. Keine AGENTS.md in Repository oder Vorfahren gefunden.

Abschluss: `npm test --prefix tools/web-tests` mit 18/18 bestandenen Tests; `node --test tools/web-safety.test.cjs` mit 3/3 bestandenen Prüfungen. Die zusätzlichen Tests decken Navigation per Tastatur, dynamische SVGs, Namen sichtbarer Icon-Aktionen, Theme-Speichern/Neuladen/Reset, ungültige Farben, Buttonkontraste und Inventarbearbeitung per Tastatur mit Fokusrückgabe ab. Die bestehenden Prüfungen für Speicherkonflikte, Teilspeicherung, Wiederholen, Sitzungsablauf und Slot-Geometrie bestehen weiterhin.

Screenshots liegen unter `target/design-*.png`: alle sieben Bereiche bei 320, 360, 768, 1024 und 1440 px; Ausrüstungsdialog, Inventarvorschau und Restore-Bestätigung bei diesen Breiten; alle Bereiche zusätzlich mit 200 % CSS-Zoom; Login bei 320 px. Automatisiert kein seitenweiter horizontaler Überlauf in den Bereichsansichten. Screenshotübersichten und ausgewählte Originalbilder wurden visuell geprüft, darunter Login, Theme, Inventar und der schmale Bestätigungsdialog. Dabei entdeckte Fehler (sichtbarer SVG-Ausdruck, schrumpfender Schalter, überlagernde Benachrichtigung, Sidebarversatz) wurden behoben.

Kontraste nach WCAG-sRGB-Formel:

| Kombination | Verhältnis |
| --- | ---: |
| `#e0e0e0` auf `#242424` | 11,76:1 |
| `#b0b0b0` auf `#303030` | 6,09:1 |
| `#4caf50` auf `#242424` | 5,58:1 |
| `#1a1a1a` auf `#4caf50` | 6,26:1 |

Primärbuttons werden zusätzlich mit Standardgrün, dunklem und weißem benutzerdefiniertem Akzent in Normal-, Hover- und Pressed-Zuständen auf mindestens 4,5:1 geprüft. Frei gewählte Nutzerthemes können weiterhin unzureichende Text-/Flächenkontraste enthalten; ihre expliziten Werte werden entsprechend dem Auftrag nicht überschrieben.

## Apple-Design-Einordnung

Bewertung: **Gut**. Die eindeutige Eigenständigkeit entsteht aus grünem Event-PVP-Akzent und authentischen Minecraft-Inventaren. Ein weiteres dekoratives Element ist nicht erforderlich. Die vorhandene fachliche Dichte wird durch klare Flächenrollen und ruhigere Bedienelemente getragen.

Gelesene Grundlagen aus `C:/Users/zfzfg/.agents/skills/apple-design/`: accessibility, layout, typography, color, designing-for-macos, icons, buttons, motion, cross-platform und code-audit. Angewendet wurden Web-relevante Grundsätze, keine nativen Apple-Navigationskonventionen.

- `accessibility.md › Vision`: „Strive to meet color contrast minimum standards.“ Umsetzung: lesbare Metadaten, Buttonfarben, Zoom und Fokus.
- `icons.md › Best practices`: „Maintain visual consistency across all interface icons in your app.“ Umsetzung: gemeinsame 24×24-SVGs mit currentColor und dekorativer ARIA-Kennzeichnung.
- `buttons.md › Style`: „Keep the number of prominent buttons to one or two per view.“ Umsetzung: Speichern bleibt prominent, ergänzende Aktionen bleiben zurückhaltend.
- `motion.md › Best practices`: „Make motion optional.“ Umsetzung: reduzierte Bewegung schaltet Übergänge und Rotation ab; Text trägt die Statusinformation.

## Abweichungen und Prüfgrenzen

- Die Vorher-Screenshots stammen aus dem bestehenden Inventar-Geometrietest (360/768/1024/1440 px), gesichert unter `target/design-baseline/`. Eine vollständige Vorher-Serie aller Bereiche und Dialoge wurde nicht aufgenommen; die vollständige Breitenserie wurde nach der Umsetzung ergänzt.
- Die vorhandenen i-Elemente dienen als SVG-Träger weiter. Ein gemeinsamer Renderer beobachtet neue Komponenten und wechselnde Zustandsklassen. So bleiben vorhandene Selektoren und Zustandsschalter funktionsfähig, während die Darstellung lokal auf SVG umgestellt wird.
- Kein zusätzliches Light Theme, keine automatische Theme-Migration und keine Java-/API-Änderungen. Daher kein Java-Build erforderlich.
- Prüfungen erfolgen mit lokalen API-Fixtures in Chromium. Kein echter Minecraft-Restore, kein produktiver Servereingriff, keine vollständige Screenreader- oder geräteübergreifende Browserprüfung. Die Kontrasttabelle beschreibt die gemeinsamen Standardrollen und ist kein vollständiger Kontrastnachweis beliebiger Minecraft-Farbcodes oder individueller Nutzerthemes.
