#!/usr/bin/env python3
"""
Console & Terminal Message Checker for Event-PVP-Plugin.

Part of the unified i18naudit suite. Forwards to i18naudit.console.main().
Can also be invoked via `python tools/i18n_audit.py --only-console`.
"""

from __future__ import annotations

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

from i18naudit.console import main  # noqa: E402

if __name__ == "__main__":
    sys.exit(main())
