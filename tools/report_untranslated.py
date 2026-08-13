#!/usr/bin/env python3
"""
Untranslated Values Reporter for Event-PVP-Plugin.

Part of the unified i18naudit suite. Forwards to i18naudit.untranslated.main().
Can also be invoked via `python tools/i18n_audit.py --only-untranslated`.
"""

from __future__ import annotations

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

from i18naudit.untranslated import main  # noqa: E402

if __name__ == "__main__":
    sys.exit(main())
