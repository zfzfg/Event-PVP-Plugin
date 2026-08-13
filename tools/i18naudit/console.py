"""
Console & Terminal Message Checker for Event-PVP-Plugin.

Scans Java source files for hardcoded German or non-English messages sent to:
- Logger calls: getLogger().info, warning, severe, log
- Console sender messages: Bukkit.getConsoleSender().sendMessage
- System.out / System.err

Ensures all console, logger, and terminal outputs are standardized in English.
"""

from __future__ import annotations

import os
import re
import sys
from dataclasses import dataclass, field
from pathlib import Path

GERMAN_PATTERNS = [
    r'\bgeladen\b', r'\bwurde\b', r'\bFehler\b', r'\bfuer\b', r'\bfür\b',
    r'\berfolgreich\b', r'\bgespeichert\b', r'\berstellt\b', r'\bbeendet\b',
    r'\baktiviert\b', r'\bdeaktiviert\b', r'\bungueltig\b', r'\bungültig\b',
    r'\babgelaufen\b', r'\bentfernt\b', r'\bSpeichere\b', r'\bgeändert\b',
    r'\bgeaendert\b', r'\bKonnte\b', r'\bAusrüstung\b', r'\bAusrüstungen\b',
    r'\bSpieler\b', r'\bHauptwelt\b', r'\bversuche\b', r'\bBereinigung\b',
    r'\bbereinigen\b', r'\bKeine\b', r'\bbereits\b', r'\bangefordert\b',
    r'\bgebunden\b', r'\beingebunden\b', r'\bgefunden\b', r'\bNutze\b',
    r'\bWelten\b', r'\bStatistiken\b', r'\bRettung\b', r'\bSichere\b',
    r'\bKRITISCH\b', r'\bStandarddatei\b', r'\bZurück\b', r'\bzurück\b',
    r'\büberprüfe\b', r'\bungültiger\b', r'\bErsetze\b', r'\bPaket-Standarddatei\b',
    r'\bNachrichten\b', r'\bSprache\b', r'\bKonfigurationen\b', r'\bKopiere\b'
]

GERMAN_RE = re.compile('|'.join(GERMAN_PATTERNS), re.IGNORECASE)
STRING_LITERAL_RE = re.compile(r'"([^"\\]*(?:\\.[^"\\]*)*)"')


@dataclass
class ConsoleFinding:
    file: Path
    line: int
    literal: str
    snippet: str

    def to_dict(self) -> dict:
        return {
            "file": str(self.file).replace("\\", "/"),
            "line": self.line,
            "literal": self.literal,
            "snippet": self.snippet,
        }


@dataclass
class ConsoleCheckResult:
    project_root: Path
    total_files_scanned: int
    findings: list[ConsoleFinding] = field(default_factory=list)

    @property
    def total_findings(self) -> int:
        return len(self.findings)

    @property
    def is_clean(self) -> bool:
        return len(self.findings) == 0

    def by_file(self) -> dict[Path, list[ConsoleFinding]]:
        grouped: dict[Path, list[ConsoleFinding]] = {}
        for f in self.findings:
            grouped.setdefault(f.file, []).append(f)
        return grouped


def scan_java_file(file_path: Path, project_root: Path | None = None) -> list[ConsoleFinding]:
    findings: list[ConsoleFinding] = []
    try:
        content = file_path.read_text(encoding="utf-8")
    except Exception as e:
        print(f"Could not read {file_path}: {e}", file=sys.stderr)
        return findings

    rel_path = file_path.relative_to(project_root) if project_root else file_path
    lines = content.splitlines()
    for line_idx, line in enumerate(lines, 1):
        sline = line.strip()
        if sline.startswith("//") or sline.startswith("*") or sline.startswith("/*"):
            continue

        for match in STRING_LITERAL_RE.finditer(line):
            lit = match.group(1)
            if GERMAN_RE.search(lit):
                if lit.startswith("messages.") or lit.startswith("settings."):
                    continue
                findings.append(ConsoleFinding(
                    file=rel_path,
                    line=line_idx,
                    literal=lit,
                    snippet=sline
                ))
    return findings


def run_console_check(project_root: Path | str | None = None) -> ConsoleCheckResult:
    if project_root is None:
        root = Path(__file__).resolve().parent.parent.parent
    else:
        root = Path(project_root).resolve()

    java_dir = root / "src" / "main" / "java"
    if not java_dir.exists():
        return ConsoleCheckResult(project_root=root, total_files_scanned=0, findings=[])

    all_java_files = sorted(java_dir.rglob("*.java"))
    all_findings: list[ConsoleFinding] = []

    for java_file in all_java_files:
        file_findings = scan_java_file(java_file, project_root=root)
        all_findings.extend(file_findings)

    return ConsoleCheckResult(
        project_root=root,
        total_files_scanned=len(all_java_files),
        findings=all_findings,
    )


def print_console_summary(result: ConsoleCheckResult) -> None:
    bar = "=" * 66
    print(bar)
    print("  Console & Logger Language Audit -- Event-PVP Plugin")
    print(bar)
    print(f"  Project root : {result.project_root}")
    print(f"  Java files   : {result.total_files_scanned}")
    print(f"  Findings     : {result.total_findings} German string literals in code")
    print(bar)

    if result.is_clean:
        print("\n  SUCCESS: All console and logger messages are standardized in English.\n")
        return

    for path, file_findings in result.by_file().items():
        print(f"\n{path} ({len(file_findings)} findings):")
        for finding in file_findings:
            print(f"  L{finding.line:4d}: \"{finding.literal}\"")
            print(f"         {finding.snippet}")

    print(f"\n  WARNING: {result.total_findings} German string literals detected in Java source code.\n")


def main(argv: list[str] | None = None, project_root: str | None = None) -> int:
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    if hasattr(sys.stderr, "reconfigure"):
        sys.stderr.reconfigure(encoding="utf-8", errors="replace")

    root = Path(project_root) if project_root else None
    result = run_console_check(root)
    print_console_summary(result)
    return 0 if result.is_clean else 1


if __name__ == "__main__":
    sys.exit(main())
