"""
Reporting: console, Markdown, JSON, and the regression baseline.

Supports single detector reports as well as the comprehensive Full Suite dashboard
covering i18n detectors, console/logger language checks, and untranslated values.
"""

from __future__ import annotations

import json
from collections import Counter, defaultdict
from datetime import datetime, timezone
from pathlib import Path
from typing import TYPE_CHECKING

from .detectors import REGISTRY
from .findings import SEVERITY_ORDER

if TYPE_CHECKING:
    from .console import ConsoleCheckResult
    from .selftest import SelfTestResult
    from .untranslated import UntranslatedResult

_BAR = "=" * 66
_THIN_BAR = "-" * 66


def summarize(findings):
    counts = Counter(f.severity for f in findings)
    return {
        "critical": counts.get("critical", 0),
        "warning": counts.get("warning", 0),
        "info": counts.get("info", 0),
        "total": len(findings),
    }


def print_suite_dashboard(
    ctx,
    findings,
    console_result: ConsoleCheckResult | None = None,
    untranslated_result: UntranslatedResult | None = None,
    selftest_result: SelfTestResult | None = None,
    baseline_suppressed: int = 0,
    max_per_detector: int = 15,
) -> None:
    """Print the unified terminal dashboard covering all active scan components."""
    print(_BAR)
    print("  Event-PVP Plugin -- Comprehensive Localization & Quality Suite")
    print(_BAR)
    print(f"  Project root : {ctx.config.project_root}")
    print(f"  Java files   : {len(ctx.java_files)}")
    print(f"  Bundles      : {len(ctx.bundles)} "
          f"(master: {ctx.config.master_bundle}, {len(ctx.master_keys)} keys)")
    print(f"  Helpers found: {len(ctx.helpers)}")
    print(f"  Key refs     : {len(ctx.references)} resolved-or-missing, "
          f"{len(ctx.dynamic_keys)} dynamic")
    print(_BAR)

    for err in ctx.errors:
        print(f"  [setup] {err}")
    if ctx.errors:
        print(_BAR)

    # The i18n detectors section always runs when this dashboard is used; the
    # other three are optional (--no-console / --no-untranslated / --no-selftest),
    # so the section count -- and therefore the "[n/N]" labels below -- is
    # computed rather than hardcoded. A hardcoded "[1/3]" would mislabel a run
    # with, say, only console + selftest active as "[1/3]" and "[3/3]" with a
    # silently skipped "[2/3]" in between.
    total_sections = 1 + sum(x is not None for x in (console_result, untranslated_result, selftest_result))
    section = 0

    def _next_section() -> int:
        nonlocal section
        section += 1
        return section

    # 1. i18n Detectors Section
    summary = summarize(findings)
    by_detector = defaultdict(list)
    for f in findings:
        by_detector[f.detector].append(f)

    print(f"\n  [{_next_section()}/{total_sections}] i18n Audit Findings (by rule)")
    print(_THIN_BAR)
    for det_id, (title, _fn, _sev) in REGISTRY.items():
        items = by_detector.get(det_id, [])
        counts = Counter(i.severity for i in items)
        badge = " ".join(f"{n}x{s}" for s, n in sorted(counts.items(),
                                                       key=lambda kv: SEVERITY_ORDER[kv[0]]))
        print(f"   {det_id} {title:<28} {len(items):>4}  {badge}")
    print(_THIN_BAR)
    print(f"   CRITICAL {summary['critical']}   WARNING {summary['warning']}   "
          f"INFO {summary['info']}   TOTAL {summary['total']}")
    if baseline_suppressed:
        print(f"   ({baseline_suppressed} known finding(s) suppressed by the baseline)")

    # 2. Console & Logger Section
    if console_result is not None:
        print(f"\n  [{_next_section()}/{total_sections}] Console & Logger Language Audit")
        print(_THIN_BAR)
        if console_result.is_clean:
            print("   Findings: 0 German string literals in logger / console calls")
            print("   Status  : CLEAN (All outputs standardized in English)")
        else:
            print(f"   Findings: {console_result.total_findings} German string literal(s) in code")
            print(f"   Files   : {len(console_result.by_file())} file(s) affected")

    # 3. Untranslated Section
    if untranslated_result is not None:
        print(f"\n  [{_next_section()}/{total_sections}] Untranslated Values Analysis (identical to master)")
        print(_THIN_BAR)
        parts = [f"{lang} ({untranslated_result.per_language[lang].count})" for lang in untranslated_result.languages]
        print(f"   Languages: {', '.join(parts)}")
        print(f"   Total    : {untranslated_result.total_untranslated} identical values documented")

    # 4. Self-Tests Section -- verifies the audit tool itself, not the plugin.
    # See selftest.py for why a Full Suite that never runs its own regression
    # tests cannot be trusted just because every other section reads "0".
    if selftest_result is not None:
        print(f"\n  [{_next_section()}/{total_sections}] Self-Tests (tools/tests, pytest)")
        print(_THIN_BAR)
        if selftest_result.skipped_recursion:
            print("   Status  : SKIPPED (nested inside another self-test run)")
        elif not selftest_result.ran:
            print(f"   Status  : ERROR ({selftest_result.setup_error})")
        else:
            print(f"   Passed  : {selftest_result.passed}   Failed: {selftest_result.failed}   "
                  f"Errors: {selftest_result.errors}"
                  + (f"   Skipped: {selftest_result.skipped}" if selftest_result.skipped else ""))
            print(f"   Status  : {'CLEAN' if selftest_result.is_clean else 'FAILING'} "
                  f"({selftest_result.duration_seconds}s)")
            if not selftest_result.is_clean:
                for name in selftest_result.failed_test_names():
                    print(f"     - {name}")

    # Overall Summary
    print("\n" + _BAR)
    selftest_broken = selftest_result is not None and not selftest_result.is_clean
    has_critical = summary["critical"] > 0 or selftest_broken
    has_warning = summary["warning"] > 0 or (console_result is not None and not console_result.is_clean)
    if selftest_broken:
        reason = selftest_result.setup_error or f"{selftest_result.failed + selftest_result.errors} self-test(s) failing"
        print(f"  OVERALL STATUS: FAILED (self-tests: {reason})")
    elif not has_critical and not has_warning:
        print("  OVERALL STATUS: SUCCESS (All audits clean)")
    elif has_critical:
        print(f"  OVERALL STATUS: FAILED ({summary['critical']} critical issue(s) detected)")
    else:
        print("  OVERALL STATUS: WARNINGS (Non-critical warnings or console text detected)")
    print(_BAR + "\n")

    # Print detailed finding items
    if findings:
        for det_id in REGISTRY:
            items = sorted(by_detector.get(det_id, []), key=lambda f: f.sort_key())
            if not items:
                continue
            title = REGISTRY[det_id][0]
            print(f"--- {det_id} {title} ({len(items)}) " + "-" * max(0, 40 - len(title)))
            for item in items[:max_per_detector]:
                print(f"  [{item.severity.upper()}] {item.location}")
                print(f"    {item.message}")
                if item.snippet:
                    print(f"    > {item.snippet}")
                if item.hint:
                    print(f"    -> {item.hint}")
                print()
            if len(items) > max_per_detector:
                print(f"  ... and {len(items) - max_per_detector} more "
                      f"(see the exported report)\n")

    if console_result is not None and not console_result.is_clean:
        print("--- Console & Logger German Literals ------------------------")
        for path, file_findings in console_result.by_file().items():
            print(f"  {path} ({len(file_findings)} findings):")
            for finding in file_findings:
                print(f"    L{finding.line:4d}: \"{finding.literal}\"")
                print(f"           {finding.snippet}")
            print()


def print_console(ctx, findings, baseline_suppressed=0, max_per_detector=15):
    """Print standard i18n detector console output."""
    print(_BAR)
    print("  i18n Audit -- Event-PVP Plugin")
    print(_BAR)
    print(f"  Project root : {ctx.config.project_root}")
    print(f"  Java files   : {len(ctx.java_files)}")
    print(f"  Bundles      : {len(ctx.bundles)} "
          f"(master: {ctx.config.master_bundle}, {len(ctx.master_keys)} keys)")
    print(f"  Helpers found: {len(ctx.helpers)}")
    print(f"  Key refs     : {len(ctx.references)} resolved-or-missing, "
          f"{len(ctx.dynamic_keys)} dynamic")
    print(_BAR)

    for err in ctx.errors:
        print(f"  [setup] {err}")
    if ctx.errors:
        print(_BAR)

    summary = summarize(findings)
    by_detector = defaultdict(list)
    for f in findings:
        by_detector[f.detector].append(f)

    print("  Findings by rule")
    for det_id, (title, _fn, _sev) in REGISTRY.items():
        items = by_detector.get(det_id, [])
        counts = Counter(i.severity for i in items)
        badge = " ".join(f"{n}x{s}" for s, n in sorted(counts.items(),
                                                       key=lambda kv: SEVERITY_ORDER[kv[0]]))
        print(f"   {det_id} {title:<28} {len(items):>4}  {badge}")
    print(_BAR)
    print(f"  CRITICAL {summary['critical']}   WARNING {summary['warning']}   "
          f"INFO {summary['info']}   TOTAL {summary['total']}")
    if baseline_suppressed:
        print(f"  ({baseline_suppressed} known finding(s) suppressed by the baseline)")
    print(_BAR)
    print()

    if not findings:
        print("  No findings. Every key referenced in code resolves, and no "
              "user-facing text is hardcoded.\n")
        return

    for det_id in REGISTRY:
        items = sorted(by_detector.get(det_id, []), key=lambda f: f.sort_key())
        if not items:
            continue
        title = REGISTRY[det_id][0]
        print(f"--- {det_id} {title} ({len(items)}) " + "-" * max(0, 40 - len(title)))
        for item in items[:max_per_detector]:
            print(f"  [{item.severity.upper()}] {item.location}")
            print(f"    {item.message}")
            if item.snippet:
                print(f"    > {item.snippet}")
            if item.hint:
                print(f"    -> {item.hint}")
            print()
        if len(items) > max_per_detector:
            print(f"  ... and {len(items) - max_per_detector} more "
                  f"(see the exported report)\n")


def export_json(path: Path, ctx, findings, summary, console_result=None, untranslated_result=None,
                selftest_result=None):
    payload = {
        "generated": datetime.now(timezone.utc).isoformat(timespec="seconds"),
        "project_root": str(ctx.config.project_root).replace("\\", "/"),
        "summary": summary,
        "stats": {
            "java_files": len(ctx.java_files),
            "bundles": [b.name for b in ctx.bundles],
            "master_keys": len(ctx.master_keys),
            "helpers": sorted(ctx.helpers),
            "references": len(ctx.references),
            "dynamic_keys": [{"key": k, "file": f, "line": l} for k, f, l in ctx.dynamic_keys],
        },
        "findings": [f.to_dict() for f in sorted(findings, key=lambda f: f.sort_key())],
    }
    if console_result is not None:
        payload["console_check"] = {
            "scanned_files": console_result.total_files_scanned,
            "findings_count": console_result.total_findings,
            "findings": [f.to_dict() for f in console_result.findings],
        }
    if untranslated_result is not None:
        payload["untranslated_check"] = untranslated_result.to_dict()
    if selftest_result is not None:
        payload["self_tests"] = selftest_result.to_dict()

    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(payload, indent=2, ensure_ascii=False), encoding="utf-8")
    print(f"  JSON report  -> {path}")


def export_markdown(path: Path, ctx, findings, summary, console_result=None, untranslated_result=None,
                    selftest_result=None):
    by_detector = defaultdict(list)
    for f in findings:
        by_detector[f.detector].append(f)

    out = []
    out.append("# i18n & Quality Audit Report\n")
    out.append(f"**Generated**: {datetime.now(timezone.utc).isoformat(timespec='seconds')}  ")
    out.append(f"**Project root**: `{ctx.config.project_root}`  ")
    out.append(f"**Master bundle**: `{ctx.config.master_bundle}` "
               f"({len(ctx.master_keys)} keys)  ")
    out.append(f"**Critical**: {summary['critical']} | **Warning**: {summary['warning']} "
               f"| **Info**: {summary['info']}\n")

    out.append("## Overview (i18n Detectors)\n")
    out.append("| Rule | Name | Findings |")
    out.append("|---|---|---|")
    for det_id, (title, _fn, _sev) in REGISTRY.items():
        out.append(f"| {det_id} | {title} | {len(by_detector.get(det_id, []))} |")
    out.append("")

    if console_result is not None:
        out.append("## Console & Logger Language Status\n")
        out.append(f"- **Scanned Java Files**: {console_result.total_files_scanned}")
        out.append(f"- **German Literals**: {console_result.total_findings}")
        if console_result.is_clean:
            out.append("- **Status**: All console and logger messages standardized in English.\n")
        else:
            out.append("\n| File | Line | Literal | Snippet |")
            out.append("|---|---|---|---|")
            for f in console_result.findings:
                out.append(f"| `{f.file}` | {f.line} | `{f.literal}` | `{_md(f.snippet)}` |")
            out.append("")

    if untranslated_result is not None:
        out.append("## Untranslated Values (Identical to English Master)\n")
        out.append("| Language | Identical Count |")
        out.append("|---|---|")
        for lang in untranslated_result.languages:
            out.append(f"| {lang} | {untranslated_result.per_language[lang].count} |")
        out.append("")

    if selftest_result is not None:
        out.append("## Self-Tests (`tools/tests`, pytest)\n")
        if not selftest_result.ran:
            out.append(f"- **Status**: ERROR -- {selftest_result.setup_error}\n")
        else:
            out.append(f"- **Passed**: {selftest_result.passed} | **Failed**: {selftest_result.failed} "
                       f"| **Errors**: {selftest_result.errors}"
                       + (f" | **Skipped**: {selftest_result.skipped}" if selftest_result.skipped else ""))
            out.append(f"- **Duration**: {selftest_result.duration_seconds}s")
            status = "CLEAN" if selftest_result.is_clean else "FAILING"
            out.append(f"- **Status**: {status}\n")
            if not selftest_result.is_clean:
                failed_names = selftest_result.failed_test_names()
                if failed_names:
                    out.append("Failing tests:\n")
                    for name in failed_names:
                        out.append(f"- `{name}`")
                    out.append("")

    if ctx.errors:
        out.append("## Setup problems\n")
        for err in ctx.errors:
            out.append(f"- {err}")
        out.append("")

    for det_id, (title, _fn, _sev) in REGISTRY.items():
        items = sorted(by_detector.get(det_id, []), key=lambda f: f.sort_key())
        out.append(f"## {det_id} -- {title}\n")
        if not items:
            out.append("_No findings._\n")
            continue
        out.append("| Severity | Location | Detail |")
        out.append("|---|---|---|")
        for item in items:
            detail = item.message
            if item.hint:
                detail += f"<br>_{item.hint}_"
            out.append(f"| {item.severity} | `{item.location}` | {_md(detail)} |")
        out.append("")

    if ctx.dynamic_keys:
        out.append("## Dynamic keys (not statically resolvable)\n")
        out.append("These call sites build the key at runtime, so neither the "
                   "missing-key nor the unused-key rule can judge them.\n")
        for key, file, line in sorted(ctx.dynamic_keys):
            out.append(f"- `{key}` at `{file}:{line}`")
        out.append("")

    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text("\n".join(out), encoding="utf-8")
    print(f"  Markdown     -> {path}")


def _md(text: str) -> str:
    return text.replace("|", "\\|")


def load_baseline(path: Path):
    if not path or not path.exists():
        return set()
    try:
        data = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError):
        return set()
    return set(data.get("fingerprints", []))


def write_baseline(path: Path, findings):
    payload = {
        "generated": datetime.now(timezone.utc).isoformat(timespec="seconds"),
        "note": "Known findings accepted at the time of writing. Delete an entry "
                "once it is fixed so it can never silently come back.",
        "fingerprints": sorted({f.fingerprint for f in findings}),
        "entries": sorted(
            ({"fingerprint": f.fingerprint, "detector": f.detector,
              "file": f.file, "key": f.key, "message": f.message} for f in findings),
            key=lambda e: (e["detector"], e["file"], e["key"]),
        ),
    }
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(payload, indent=2, ensure_ascii=False), encoding="utf-8")
    print(f"  Baseline     -> {path} ({len(payload['fingerprints'])} entries)")
