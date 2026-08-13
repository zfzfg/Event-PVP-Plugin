"""Zaehlt Dateien und Zeilen in src.

Binaerdateien (z.B. die ~1660 PNGs unter web/item-assets) werden standardmaessig
ignoriert - sonst wird die Kennzahl von Assets dominiert statt von Code.
"""

from __future__ import annotations

import argparse
import sys
from pathlib import Path

# Aus dem tatsaechlichen Inhalt von src abgeleitet, plus die naheliegenden Nachbarn.
TEXT_EXTENSIONS = {
    ".java",
    ".yml",
    ".yaml",
    ".json",
    ".js",
    ".html",
    ".css",
    ".md",
    ".properties",
    ".xml",
    ".txt",
    ".code-workspace",
}

SNIFF_BYTES = 8192


def is_binary(path: Path) -> bool:
    """Zweite Instanz neben der Allowlist: NUL-Byte in den ersten 8 KB."""
    try:
        with path.open("rb") as handle:
            return b"\0" in handle.read(SNIFF_BYTES)
    except OSError:
        return True


def count_lines(path: Path) -> int:
    """Encoding-unabhaengig: Bytes lesen, ueber \\n zaehlen.

    Eine letzte Zeile ohne abschliessenden Umbruch wird mitgezaehlt, eine leere
    Datei ergibt 0.
    """
    data = path.read_bytes()
    if not data:
        return 0
    lines = data.count(b"\n")
    if not data.endswith(b"\n"):
        lines += 1
    return lines


def parse_args(argv: list[str] | None = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Zaehlt Dateien und Zeilen in einem Verzeichnisbaum (Standard: src).",
    )
    parser.add_argument(
        "--path",
        default=None,
        help="Zielverzeichnis (Standard: src neben dem tools-Ordner).",
    )
    parser.add_argument(
        "--include-binary",
        action="store_true",
        help="Binaerdateien in der Dateizahl mitzaehlen (Zeilen bleiben 0).",
    )
    parser.add_argument(
        "--ext",
        action="append",
        default=None,
        metavar=".java",
        help="Nur diese Endung auswerten. Mehrfach nutzbar.",
    )
    return parser.parse_args(argv)


def normalize_extensions(raw: list[str] | None) -> set[str] | None:
    if not raw:
        return None
    return {ext.lower() if ext.startswith(".") else "." + ext.lower() for ext in raw}


def format_table(stats: dict[str, dict[str, int]]) -> list[str]:
    # 16 Zeichen, damit auch ".code-workspace" die Spalte nicht sprengt.
    header = f"{'Endung':<16}  {'Dateien':>8}  {'Zeilen':>11}"
    rule = f"{'-' * 16}  {'-' * 8}  {'-' * 11}"
    rows = [header, rule]

    # Absteigend nach Zeilen, bei Gleichstand nach Dateizahl - haelt die Ausgabe stabil.
    ordered = sorted(
        stats.items(),
        key=lambda item: (item[1]["lines"], item[1]["files"]),
        reverse=True,
    )
    for ext, values in ordered:
        rows.append(
            f"{ext or '(ohne)':<16}  {values['files']:>8,}  {values['lines']:>11,}".replace(
                ",", "."
            )
        )

    total_files = sum(v["files"] for v in stats.values())
    total_lines = sum(v["lines"] for v in stats.values())
    rows.append(rule)
    rows.append(
        f"{'GESAMT':<16}  {total_files:>8,}  {total_lines:>11,}".replace(",", ".")
    )
    return rows


def main(argv: list[str] | None = None) -> int:
    args = parse_args(argv)

    if args.path:
        target = Path(args.path).expanduser()
    else:
        target = Path(__file__).resolve().parent.parent / "src"

    if not target.is_dir():
        print(f"FEHLER: Verzeichnis nicht gefunden: {target}", file=sys.stderr)
        return 1

    wanted = normalize_extensions(args.ext)

    stats: dict[str, dict[str, int]] = {}
    skipped: dict[str, int] = {}

    for path in sorted(target.rglob("*")):
        if not path.is_file():
            continue

        ext = path.suffix.lower()
        if wanted is not None and ext not in wanted:
            continue

        countable = ext in TEXT_EXTENSIONS and not is_binary(path)
        if not countable:
            skipped[ext] = skipped.get(ext, 0) + 1
            if not args.include_binary:
                continue

        entry = stats.setdefault(ext, {"files": 0, "lines": 0})
        entry["files"] += 1
        if countable:
            entry["lines"] += count_lines(path)

    print("=== src: Dateien und Zeilen ===")
    print(f"Pfad: {target}")
    print()

    if not stats:
        print("Keine passenden Dateien gefunden.")
    else:
        for line in format_table(stats):
            print(line)

    if skipped and not args.include_binary:
        total_skipped = sum(skipped.values())
        detail = ", ".join(
            ext or "(ohne)"
            for ext, _ in sorted(skipped.items(), key=lambda i: i[1], reverse=True)
        )
        print()
        print(f"Ignoriert: {total_skipped} Binaerdateien ({detail})")

    return 0


if __name__ == "__main__":
    sys.exit(main())
