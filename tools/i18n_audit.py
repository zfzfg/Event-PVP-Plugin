#!/usr/bin/env python3
"""
Comprehensive Localization & Quality Audit Suite -- Event-PVP Plugin

Unifies eleven i18n rule detectors, the console & logger language auditor, and
untranslated values reporting into a single CLI tool.

  D1  key-as-default              a helper returns the key when nothing matches
  D2  missing-key                 code asks for a key no bundle defines
  D3  yaml-boolean-key            unquoted `on:` / `off:` parsed as a boolean
  D4  placeholder-mismatch        {x} in code vs template, in both directions
  D5  untranslatable-display-name prose baked into enum constants
  D6  hardcoded-message           literal sent straight to a player
  D7  natural-language-literal    prose outside any bundle (incl. web assets)
  D8  bundle-parity               key sets, extra keys, empty/TODO values
  D9  unused-key                  bundle key nothing reads
  D10 web-missing-key             i18n.t() call the web panel bundle can't resolve
  D11 web-unused-key              web panel bundle key nothing reads

D1-D9 audit messages_*.yml + Java (getMsg() and friends). D10/D11 audit the
web panel's own, independent bundle: web/lang/*.json + i18n.t() in
app.js/editors.js/items.js/index.html.

Scans:
  - Full Suite (default)         Runs all 11 detectors, console check, and untranslated analysis
  - Console & Logger Check       Ensures all console/logger messages are standardized in English
  - Untranslated Values          Analyzes bundles for byte-identical translations vs English master

Usage:
  python tools/i18n_audit.py                           # Full Suite
  python tools/i18n_audit.py --only-console            # Only console/logger check
  python tools/i18n_audit.py --only-untranslated       # Only untranslated values report
  python tools/i18n_audit.py --only-i18n --only D1,D2  # Specific detectors
  python tools/i18n_audit.py --export-markdown --export-json
  python tools/i18n_audit.py --strict --fail-on critical

Exit codes: 0 clean, 1 findings at or above --fail-on / strict failure, 2 tool error.
"""

from __future__ import annotations

import sys
from pathlib import Path

# Ensure package import resolution
sys.path.insert(0, str(Path(__file__).resolve().parent))

from i18naudit.cli import main  # noqa: E402

if __name__ == "__main__":
    sys.exit(main())
