"""
Self-tests: the pytest regression suite as a Full Suite component.

Why this exists
----------------
Before this module, "Full Suite" meant three things: the i18n detectors
(D1-D11) against the *plugin's* source, the console/logger language check,
and the untranslated-values report. All three ask "is the plugin correct?".
None of them ask "is the audit tool itself correct?" - that question only had
an answer if someone remembered to separately run `pytest tools/tests`
(`run_scans.bat` option 8, or the README's own command block).

That gap is not hypothetical: the very detectors added alongside this module
(D10/D11, see `webi18n.py`) shipped a real bug in their first version -
`is_referenced()` forgot to check the call-site references it had just
collected, so every direct `i18n.t('literal')` use was misreported as
unused. The regression test written for exactly that case
(`test_d11_silent_on_a_direct_call_site`) would have caught it immediately -
*if* something had run it as part of the same suite that reports findings.
Trusting a Full Suite that never checks whether its own rules still work is
the same "clean = correct" assumption `AUDIT_DOKUMENTATION.md` already warns
about for bundle parity.

So `pytest tools/tests` is now a fourth Full Suite component, on equal
footing with the other three: it runs by default, appears in the terminal
dashboard, and is written into the exported Markdown/JSON reports.
"""

from __future__ import annotations

import os
import re
import subprocess
import sys
import time
from dataclasses import dataclass, field
from pathlib import Path

TOOLS_DIR = Path(__file__).resolve().parent.parent

# Recursion guard. `run_self_tests()` shells out to `pytest tools/tests` -
# the exact directory that contains `test_cli.py`, which calls `cli.main()`
# in-process (not via subprocess) with no scope-limiting flags in several of
# its tests. Since self-tests are now a default Full Suite component, that
# in-process `main()` call reaches `run_self_tests()` again, which would
# spawn *another* `pytest tools/tests` child, which runs `test_cli.py`
# again, which calls `main()` again... an unbounded fork bomb, not a
# hypothetical one: it happened during development of this module and spun
# up 500+ python processes before being caught. This is the fix, and it is
# load-bearing - do not remove without re-scoping every `main()` call in
# `test_cli.py` to pass `--no-selftest` first, and even then, a future test
# added without that flag would silently reintroduce the bomb.
_RECURSION_GUARD_ENV = "I18NAUDIT_SELFTEST_RUNNING"

# Pytest's quiet-mode summary line, e.g.:
#   "76 passed in 0.96s"
#   "3 failed, 73 passed in 1.02s"
#   "1 error in 0.05s"
#   "5 passed, 2 skipped, 1 xfailed in 0.30s"
#   "no tests ran in 0.01s"
_SUMMARY_COUNT = re.compile(r"(\d+)\s+(passed|failed|error(?:s)?|skipped|xfailed|xpassed|warnings)")
_SUMMARY_DURATION = re.compile(r"in\s+([\d.]+)s\b")


@dataclass
class SelfTestResult:
    passed: int = 0
    failed: int = 0
    errors: int = 0
    skipped: int = 0
    xfailed: int = 0
    xpassed: int = 0
    duration_seconds: float = 0.0
    returncode: int = 0
    ran: bool = True           # False if pytest itself could not be invoked
    skipped_recursion: bool = False  # True if the recursion guard refused to run
    output: str = ""           # captured stdout+stderr, for the exported reports
    setup_error: str = ""      # populated only when ran is False

    @property
    def total(self) -> int:
        return self.passed + self.failed + self.errors + self.skipped + self.xfailed + self.xpassed

    @property
    def is_clean(self) -> bool:
        # xfail/xpass and skips are not failures - a skip is a deliberate,
        # documented gap (see conftest.py fixtures), not a broken test.
        return self.ran and self.failed == 0 and self.errors == 0

    def failed_test_names(self) -> list:
        """Best-effort extraction of individual failing test node ids from
        the captured output, for the terminal dashboard and exported reports.
        Pytest's `-q` short summary prints one `FAILED path::test_name` line
        per failure when there is at least one; parsed, not guaranteed."""
        names = []
        for line in self.output.splitlines():
            line = line.strip()
            if line.startswith("FAILED ") or line.startswith("ERROR "):
                names.append(line.split(" ", 1)[1].split(" - ")[0])
        return names

    def to_dict(self) -> dict:
        return {
            "ran": self.ran,
            "skipped_recursion": self.skipped_recursion,
            "passed": self.passed,
            "failed": self.failed,
            "errors": self.errors,
            "skipped": self.skipped,
            "xfailed": self.xfailed,
            "xpassed": self.xpassed,
            "total": self.total,
            "duration_seconds": self.duration_seconds,
            "returncode": self.returncode,
            "is_clean": self.is_clean,
            "failed_tests": self.failed_test_names(),
            **({"setup_error": self.setup_error} if self.setup_error else {}),
        }


def run_self_tests(root: Path, tests_dir: Path = None) -> SelfTestResult:
    """Runs `pytest tools/tests` as a subprocess and parses its summary line.

    A subprocess, not `pytest.main()` in-process: the audit tool must survive
    (and clearly report) a pytest that is missing, misconfigured, or crashes
    outright - the same defensive posture `bundles.py`/`javaparse.py` take
    toward a malformed source file, applied to the test runner itself.

    Refuses to run at all if it detects it is already nested inside another
    self-test run (see `_RECURSION_GUARD_ENV` above) - required, not optional,
    because `test_cli.py` calls `cli.main()` in-process and this function is
    exactly what that call would otherwise recursively re-trigger.
    """
    if os.environ.get(_RECURSION_GUARD_ENV):
        return SelfTestResult(skipped_recursion=True)

    tests_dir = tests_dir or (TOOLS_DIR / "tests")
    if not tests_dir.is_dir():
        return SelfTestResult(ran=False, setup_error=f"Test directory not found: {tests_dir}")

    start = time.monotonic()
    child_env = dict(os.environ)
    child_env[_RECURSION_GUARD_ENV] = "1"
    try:
        proc = subprocess.run(
            [sys.executable, "-m", "pytest", str(tests_dir), "-q", "--tb=short", "--no-header"],
            cwd=str(root),
            capture_output=True,
            text=True,
            timeout=300,
            # Explicit, not the platform default: a subprocess that silently
            # inherits the parent's stdin can hang waiting for input that
            # will never arrive in a non-interactive run (CI, or this CLI
            # invoked from another tool).
            stdin=subprocess.DEVNULL,
            env=child_env,
        )
    except FileNotFoundError as exc:
        return SelfTestResult(ran=False, setup_error=f"Could not launch pytest: {exc}")
    except subprocess.TimeoutExpired:
        return SelfTestResult(ran=False, setup_error="pytest did not finish within 300s")
    elapsed = time.monotonic() - start

    output = (proc.stdout or "") + (proc.stderr or "")
    result = _parse_summary(output)
    result.returncode = proc.returncode
    result.output = output
    # Prefer pytest's own reported duration; fall back to our wall-clock
    # measurement if the summary line could not be parsed (e.g. a crash
    # before pytest reached its own teardown).
    if not result.duration_seconds:
        result.duration_seconds = round(elapsed, 2)

    # Exit code 5 = "no tests collected". That is a setup problem (wrong
    # path, missing __init__), not "zero findings" - never report it as clean.
    if proc.returncode == 5:
        result.ran = False
        result.setup_error = "pytest collected zero tests - check tools/tests/ and conftest.py"

    return result


def _parse_summary(output: str) -> SelfTestResult:
    result = SelfTestResult()
    # The summary line is the last one pytest prints with a count in it;
    # scanning from the end skips any "FAILED ..." lines that also contain
    # digits (a line number) and would otherwise confuse a naive first match.
    summary_line = ""
    for line in reversed(output.splitlines()):
        if _SUMMARY_COUNT.search(line) and (" in " in line or "no tests ran" in line):
            summary_line = line
            break

    for count, label in _SUMMARY_COUNT.findall(summary_line):
        n = int(count)
        if label == "passed":
            result.passed = n
        elif label == "failed":
            result.failed = n
        elif label in ("error", "errors"):
            result.errors = n
        elif label == "skipped":
            result.skipped = n
        elif label == "xfailed":
            result.xfailed = n
        elif label == "xpassed":
            result.xpassed = n

    duration = _SUMMARY_DURATION.search(summary_line)
    if duration:
        result.duration_seconds = round(float(duration.group(1)), 2)

    return result


def print_selftest_summary(result: SelfTestResult) -> None:
    """Standalone printer for `--only-selftest`, mirroring
    `print_console_summary()`/`print_untranslated_summary()`."""
    print("=" * 66)
    print("  Self-Tests -- tools/tests (pytest)")
    print("=" * 66)
    if result.skipped_recursion:
        print("  Skipped (already running inside a self-test invocation)")
        return
    if not result.ran:
        print(f"  [error] {result.setup_error}")
        return
    print(f"  Passed : {result.passed}")
    print(f"  Failed : {result.failed}")
    print(f"  Errors : {result.errors}")
    if result.skipped:
        print(f"  Skipped: {result.skipped}")
    print(f"  Total  : {result.total}  ({result.duration_seconds}s)")
    print(f"  Status : {'CLEAN' if result.is_clean else 'FAILING'}")
    if not result.is_clean:
        for name in result.failed_test_names():
            print(f"    - {name}")
