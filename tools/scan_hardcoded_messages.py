#!/usr/bin/env python3
"""
DEPRECATED -- superseded by tools/i18n_audit.py.

This scanner matched regexes against raw physical lines. That made it blind to
the bugs it was supposed to catch:

  * a line containing any `getMsg` was cleared wholesale, so the hardcoded half
    of `sendMessage(getMsg("x") + "&cFehler")` was never reported;
  * `"INFO" in line` dropped any message whose text happened to contain "INFO";
  * German prose inside comments was reported as a hardcoded message;
  * enum constructor arguments such as `LEVEL_3(3, "Vollstaendig")` were not a
    `sendMessage` line and therefore invisible -- which is why the debug status
    printed German while the language was set to English.

This wrapper forwards to the replacement so existing scripts keep working.
Detectors D5, D6 and D7 cover what this file used to do.
"""

from __future__ import annotations

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

from i18n_audit import main  # noqa: E402

def _translate(argv):
    out = ["--only-i18n", "--only", "D5,D6,D7,D8"]
    i = 0
    while i < len(argv):
        arg = argv[i]
        if arg == "--project-root" and i + 1 < len(argv):
            out += ["--project-root", argv[i + 1]]
            i += 2
            continue
        if arg in ("--export-markdown", "--export-json", "--strict"):
            out.append(arg)
        i += 1
    return out


if __name__ == "__main__":
    print(__doc__.strip().splitlines()[0])
    print("Forwarding to tools/i18n_audit.py --only D5,D6,D7,D8\n")
    sys.exit(main(_translate(sys.argv[1:])))
