#!/usr/bin/env python3
"""Findet Anbindungen ohne Ziel im Event-PVP-Plugin.

Hintergrund
-----------
Beim Umbau des Web-Panels tauchte mehrfach dieselbe Fehlerklasse auf: ein Feld, das die
Oberflaeche schreibt und der Server nie liest. ``offhand``, ``armor.<slot>-enchantments``
und ``gui-item`` fielen jeweils nur zufaellig auf, weil gerade jemand in der Naehe
gearbeitet hat. Im Betrieb aeussert sich so etwas als "die Einstellung tut nichts" - ohne
Fehlermeldung, ohne Logzeile.

Dieses Skript macht solche Stellen sichtbar. Es kennt zwei Arten von Funden:

1. **Markierungen** - Stellen, die nur ein Mensch beurteilen kann. Sie werden im Quelltext
   als Kommentar hinterlegt::

       // @loose-end(dead-config): gui-item wird von keinem Loader gelesen
       #  @loose-end(schema-drift): Panel schreibt equipment-sets, Loader liest equipment

   Das Format funktioniert unveraendert in Java und JavaScript (``//``) sowie in YAML und
   Python (``#``).

2. **Verwaiste Uebersetzungsschluessel** (``--check-i18n``) - die lassen sich mechanisch
   herleiten und brauchen keine Markierung. Das Skript beruecksichtigt dabei sowohl
   zusammengesetzte Schluessel (``i18n.t('inventory.phase.' + x)``) als auch die vom Server
   gelieferten ``messageKey``-Werte, die im JavaScript nie woertlich vorkommen.

Aufruf
------
    python tools/find_loose_ends.py                 # alle Markierungen
    python tools/find_loose_ends.py --category dead-config
    python tools/find_loose_ends.py --check-i18n    # zusaetzlich verwaiste Sprachschluessel
    python tools/find_loose_ends.py --format json
    python tools/find_loose_ends.py --strict        # Exit-Code 1, sobald es Funde gibt
"""

from __future__ import annotations

import argparse
import json
import re
import sys
from dataclasses import dataclass, asdict
from pathlib import Path

# --------------------------------------------------------------------------------------
# Kategorien
# --------------------------------------------------------------------------------------

# Bewusst eine feste Liste: ohne sie schleichen sich Schreibvarianten ein
# ("dead_config", "deadconfig"), und die Auswertung wird wertlos.
CATEGORIES = {
    "dead-config": "Konfigurationsschluessel, den kein Loader liest",
    "orphan-ui": "Oberflaechenelement ohne Eingang",
    "schema-drift": "Panel und Server sind sich ueber das Format uneinig",
    "unused-api": "Endpunkt oder Funktion ohne Aufrufer",
    "stub": "Platzhalter-Implementierung",
}

MARKER = re.compile(
    r"(?://|#)\s*@loose-end\(\s*([a-z-]+)\s*\)\s*:\s*(.*?)\s*$"
)

# Auch fehlerhaft geschriebene Markierungen finden, damit sie nicht unbemerkt wirkungslos
# im Quelltext stehen.
MARKER_LOOSE = re.compile(r"@loose-end")

SEARCH_DIRS = ("src", "tools")
SEARCH_SUFFIXES = {".java", ".js", ".yml", ".yaml", ".py", ".html", ".md", ".css"}

# Verzeichnisse, die nie durchsucht werden: erzeugte oder eingespielte Dateien.
SKIP_DIRS = {"target", "node_modules", ".git", "item-assets", "minecraft_textures_item"}

# Dieses Skript definiert das Muster und enthaelt es deshalb zwangslaeufig in Beispielen
# und in der eigenen Regex - es darf sich nicht selbst melden.
SELF = Path(__file__).resolve()


@dataclass
class Finding:
    category: str
    path: str
    line: int
    message: str


# --------------------------------------------------------------------------------------
# Markierungen
# --------------------------------------------------------------------------------------

def iter_source_files(root: Path):
    """Alle Dateien, die eine Markierung tragen koennen."""
    for directory in SEARCH_DIRS:
        base = root / directory
        if not base.is_dir():
            continue
        for path in base.rglob("*"):
            if not path.is_file() or path.suffix not in SEARCH_SUFFIXES:
                continue
            if any(part in SKIP_DIRS for part in path.parts):
                continue
            if path.resolve() == SELF:
                continue
            yield path

    # Die Dokumentation im Wurzelverzeichnis gehoert dazu - dort stehen die
    # Architekturentscheidungen, die eine Markierung erklaeren.
    for path in root.glob("*.md"):
        yield path


def scan_markers(root: Path) -> tuple[list[Finding], list[Finding]]:
    """@return (gueltige Funde, fehlerhaft geschriebene Markierungen)"""
    findings: list[Finding] = []
    malformed: list[Finding] = []

    for path in iter_source_files(root):
        try:
            lines = path.read_text(encoding="utf-8", errors="replace").splitlines()
        except OSError as exc:
            print(f"Warnung: {path} nicht lesbar ({exc})", file=sys.stderr)
            continue

        for number, line in enumerate(lines, start=1):
            match = MARKER.search(line)
            relative = path.relative_to(root).as_posix()

            if match:
                category, message = match.group(1), match.group(2)
                if category not in CATEGORIES:
                    malformed.append(Finding(
                        category, relative, number,
                        f"unbekannte Kategorie '{category}' (erlaubt: {', '.join(sorted(CATEGORIES))})"))
                    continue
                findings.append(Finding(category, relative, number, message or "(ohne Beschreibung)"))
            elif MARKER_LOOSE.search(line) and "MARKER" not in line:
                # Steht da, wirkt aber nicht - genau das soll nicht unbemerkt bleiben.
                malformed.append(Finding(
                    "?", relative, number,
                    "Markierung passt nicht auf '@loose-end(kategorie): Text'"))

    return findings, malformed


# --------------------------------------------------------------------------------------
# Verwaiste Uebersetzungsschluessel
# --------------------------------------------------------------------------------------

# Schluessel, die der Server als messageKey liefert. Sie stehen nie woertlich im Panel-Code
# und waeren sonst durchweg falsche Treffer.
SERVER_DRIVEN_PREFIXES = ("mv.error.", "inventory.error.", "items.error.")

DYNAMIC_KEY = re.compile(r"""i18n\.t\(\s*['"]([a-zA-Z0-9_.]+\.)['"]\s*\+""")


def scan_i18n(root: Path) -> list[Finding]:
    """Schluessel in en.json, die im Panel-Quelltext nirgends auftauchen."""
    web = root / "src" / "main" / "resources" / "web"
    reference = web / "lang" / "en.json"
    if not reference.is_file():
        return []

    keys = json.loads(reference.read_text(encoding="utf-8"))

    sources = []
    for name in ("app.js", "editors.js", "items.js", "index.html"):
        path = web / name
        if path.is_file():
            sources.append(path.read_text(encoding="utf-8", errors="replace"))
    haystack = "\n".join(sources)

    dynamic_prefixes = set(DYNAMIC_KEY.findall(haystack))

    findings = []
    for key in keys:
        if key in haystack:
            continue
        if key.startswith(SERVER_DRIVEN_PREFIXES):
            continue
        if any(key.startswith(prefix) for prefix in dynamic_prefixes):
            continue
        findings.append(Finding(
            "dead-config", reference.relative_to(root).as_posix(), 0,
            f"Uebersetzungsschluessel '{key}' wird nirgends verwendet"))
    return findings


# --------------------------------------------------------------------------------------
# Ausgabe
# --------------------------------------------------------------------------------------

def print_text(findings: list[Finding], malformed: list[Finding]) -> None:
    if malformed:
        print("Fehlerhafte Markierungen:")
        for item in malformed:
            print(f"  {item.path}:{item.line}   {item.message}")
        print()

    if not findings:
        print("Keine offenen Anbindungen gefunden.")
        return

    by_category: dict[str, list[Finding]] = {}
    for item in findings:
        by_category.setdefault(item.category, []).append(item)

    # Feste Reihenfolge statt Zufall aus dem Dictionary.
    for category in sorted(by_category, key=lambda c: list(CATEGORIES).index(c)
                           if c in CATEGORIES else 99):
        items = by_category[category]
        print(f"{category} ({len(items)}) - {CATEGORIES.get(category, '?')}")
        # Die laengste Position bestimmt die Spaltenbreite, damit die Texte fluchten.
        width = max(len(f"{i.path}:{i.line}") for i in items)
        for item in sorted(items, key=lambda i: (i.path, i.line)):
            position = f"{item.path}:{item.line}"
            print(f"  {position:<{width}}   {item.message}")
        print()

    total = len(findings)
    print(f"{total} offene Anbindung{'en' if total != 1 else ''} "
          f"in {len(by_category)} Kategorie{'n' if len(by_category) != 1 else ''}")


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Findet markierte Anbindungen ohne Ziel.",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="Kategorien:\n" + "\n".join(f"  {k:<14} {v}" for k, v in CATEGORIES.items()))
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parent.parent,
                        help="Projektwurzel (Standard: Elternverzeichnis dieses Skripts)")
    parser.add_argument("--category", choices=sorted(CATEGORIES),
                        help="nur diese Kategorie ausgeben")
    parser.add_argument("--check-i18n", action="store_true",
                        help="zusaetzlich verwaiste Uebersetzungsschluessel suchen")
    parser.add_argument("--format", choices=("text", "json"), default="text")
    parser.add_argument("--strict", action="store_true",
                        help="Exit-Code 1, sobald es Funde gibt (fuer einen Build-Schritt)")
    args = parser.parse_args()

    root = args.root.resolve()
    if not (root / "src").is_dir():
        print(f"Fehler: {root} sieht nicht nach der Projektwurzel aus (kein src/).", file=sys.stderr)
        return 2

    findings, malformed = scan_markers(root)
    if args.check_i18n:
        findings.extend(scan_i18n(root))
    if args.category:
        findings = [f for f in findings if f.category == args.category]

    if args.format == "json":
        print(json.dumps({
            "findings": [asdict(f) for f in findings],
            "malformed": [asdict(f) for f in malformed],
        }, indent=2, ensure_ascii=False))
    else:
        print_text(findings, malformed)

    # Eine fehlerhafte Markierung ist immer ein Fehler: sie sieht aus, als wuerde sie
    # etwas festhalten, taucht aber in keiner Auswertung auf.
    if malformed:
        return 1
    if args.strict and findings:
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
