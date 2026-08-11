#!/usr/bin/env python3
"""
DEPRECATED -- superseded by tools/i18n_audit.py.

This auditor guessed key bindings: for every raw key it built ~16 candidate
paths and accepted the first that happened to exist. Consequences:

  * a key bound to the wrong section still "resolved", hiding the real bug;
  * it could not model a helper whose last lookup defaults to the key itself,
    so `getDebugMsg("status-header")` looked fine while the player saw the
    literal text `status-header`;
  * the unused-key check searched the concatenated source for the *leaf* name,
    and leaves like `name` or `title` match nearly any file, so almost every
    orphan key was swallowed.

The replacement reads the actual helper implementations out of the source and
derives their real lookup chains. This wrapper forwards to it so existing
scripts keep working. Detectors D1, D2, D4 and D9 cover what this file used to do.
"""

from __future__ import annotations

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

from i18n_audit import main  # noqa: E402


def _translate(argv):
    out = ["--only-i18n", "--only", "D1,D2,D4,D9"]
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
    print("Forwarding to tools/i18n_audit.py --only D1,D2,D4,D9\n")
    sys.exit(main(_translate(sys.argv[1:])))
