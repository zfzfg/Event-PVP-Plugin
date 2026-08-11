"""
Central CLI and flag dispatcher for the Event-PVP localization and audit suite.

Defaults to running the comprehensive Full Suite (i18n Detectors D1-D11 +
Console & Logger Language Check + Untranslated Values Analysis + the
`tools/tests` pytest self-tests), while providing granular flags for
individual scans, severity thresholds, baseline tracking, and export formats.
"""

from __future__ import annotations

import argparse
import sys
from pathlib import Path

from . import detectors, report
from .config import find_project_root, load_config
from .console import print_console_summary, run_console_check
from .context import build_context
from .findings import SEVERITY_ORDER
from .selftest import print_selftest_summary, run_self_tests
from .untranslated import print_untranslated_summary, run_untranslated_check, write_markdown_report

DEFAULT_REPORT_DIR = "reports"


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description="Comprehensive Localization & Quality Audit Suite -- Event-PVP Plugin",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
Examples:
  python tools/i18n_audit.py                           # Full Suite (all scans + self-tests, dashboard)
  python tools/i18n_audit.py --export-markdown --export-json  # Full Suite with reports in reports/
  python tools/i18n_audit.py --only-console            # Run only Console & Logger check
  python tools/i18n_audit.py --only-untranslated       # Run only Untranslated values analysis
  python tools/i18n_audit.py --only-selftest           # Run only the tools/tests pytest suite
  python tools/i18n_audit.py --only-i18n --only D1,D2  # Run only i18n detectors D1 and D2
  python tools/i18n_audit.py --severity critical       # Filter findings to critical only
  python tools/i18n_audit.py --list-helpers            # Display helper lookup chains
  python tools/i18n_audit.py --strict --fail-on critical # CI Quality gate
""",
    )

    # Scope selection
    scope = parser.add_argument_group("Scope Selection")
    scope.add_argument("--all", action="store_true",
                       help="Run all scans (i18n Detectors + Console Check + Untranslated Analysis "
                            "+ Self-Tests) [Default]")
    scope.add_argument("--only-i18n", action="store_true",
                       help="Run only the i18n rule detectors (D1-D11)")
    scope.add_argument("--only-console", action="store_true",
                       help="Run only the Console & Logger language check")
    scope.add_argument("--only-untranslated", action="store_true",
                       help="Run only the Untranslated values analysis")
    scope.add_argument("--only-selftest", action="store_true",
                       help="Run only the tools/tests pytest self-tests")
    scope.add_argument("--no-console", action="store_true",
                       help="Skip Console & Logger check during full suite run")
    scope.add_argument("--no-untranslated", action="store_true",
                       help="Skip Untranslated values check during full suite run")
    scope.add_argument("--no-i18n", action="store_true",
                       help="Skip i18n detectors during full suite run")
    scope.add_argument("--no-selftest", action="store_true",
                       help="Skip the tools/tests pytest self-tests during full suite run")

    # Project paths
    paths = parser.add_argument_group("Project Configuration")
    paths.add_argument("--project-root", default=None,
                       help="Project root (default: nearest parent containing pom.xml)")
    paths.add_argument("--config", default=None, help="Path to i18n_audit_config.yml")

    # i18n Detector options
    i18n_opts = parser.add_argument_group("i18n Detector Options")
    i18n_opts.add_argument("--only", default="",
                           help="Comma-separated detector ids (e.g. D1,D2,D6)")
    i18n_opts.add_argument("--severity", default="info", choices=("critical", "warning", "info"),
                           help="Only report findings at this level or above")
    i18n_opts.add_argument("--list-helpers", action="store_true",
                           help="Print the discovered message helpers and their lookup chains")

    # Baseline & CI Quality Gate
    ci_opts = parser.add_argument_group("Baseline & CI Quality Gate")
    ci_opts.add_argument("--baseline", default=None,
                         help="Baseline file of accepted findings (default: from config)")
    ci_opts.add_argument("--write-baseline", action="store_true",
                         help="Record current findings as the accepted baseline")
    ci_opts.add_argument("--no-baseline", action="store_true",
                         help="Ignore baseline and report everything")
    ci_opts.add_argument("--strict", action="store_true",
                         help="Exit with code 1 if blocking findings remain")
    ci_opts.add_argument("--fail-on", default="critical", choices=("critical", "warning", "info"),
                         help="Severity threshold for --strict failure")

    # Reporting & Export
    export_opts = parser.add_argument_group("Export & Reports")
    export_opts.add_argument("--export-markdown", action="store_true",
                             help="Export reports to Markdown in --report-dir")
    export_opts.add_argument("--export-json", action="store_true",
                             help="Export findings and statistics to JSON in --report-dir")
    export_opts.add_argument("--report-dir", default=DEFAULT_REPORT_DIR,
                             help="Target directory for exported reports (default: reports)")

    return parser


def main(argv: list[str] | None = None) -> int:
    if hasattr(sys.stdout, "reconfigure"):
        try:
            sys.stdout.reconfigure(encoding="utf-8", errors="replace")
        except Exception:
            pass
    if hasattr(sys.stderr, "reconfigure"):
        try:
            sys.stderr.reconfigure(encoding="utf-8", errors="replace")
        except Exception:
            pass

    parser = build_parser()
    args = parser.parse_args(argv)

    root = Path(args.project_root).resolve() if args.project_root else find_project_root(Path(__file__).parent)
    config_path = Path(args.config).resolve() if args.config else None

    # Handle single-target shortcut scopes
    if args.only_console:
        res = run_console_check(root)
        print_console_summary(res)
        return 0 if res.is_clean else 1

    if args.only_untranslated:
        res = run_untranslated_check(root)
        out_file = write_markdown_report(res, Path(args.report_dir) / "untranslated_values.md")
        rel_out = out_file.relative_to(root) if out_file.is_relative_to(root) else out_file
        parts = [f"{lang} {res.per_language[lang].count}" for lang in res.languages]
        print(f"{rel_out}: {', '.join(parts)}")
        return 0

    if args.only_selftest:
        res = run_self_tests(root)
        print_selftest_summary(res)
        return 0 if res.is_clean else 1

    # Determine active scan components
    run_i18n = not args.no_i18n
    run_console = not (args.no_console or args.only_i18n)
    run_untrans = not (args.no_untranslated or args.only_i18n)
    run_selftest = not (args.no_selftest or args.only_i18n)

    if args.only_i18n:
        run_i18n = True
        run_console = False
        run_untrans = False
        run_selftest = False

    try:
        config = load_config(root, config_path)
        ctx = build_context(config)
    except Exception as exc:
        print(f"[error] {type(exc).__name__}: {exc}", file=sys.stderr)
        return 2

    if not ctx.java_files and not ctx.bundles:
        print(f"[error] Nothing to scan under {root}. Is this the project root?", file=sys.stderr)
        return 2

    if args.list_helpers:
        _print_helpers(ctx)
        return 0

    # Validate detector filter
    only_detectors = {s.strip().upper() for s in args.only.split(",") if s.strip()} or None
    if only_detectors:
        unknown = only_detectors - set(detectors.ALL_IDS)
        if unknown:
            print(f"[error] Unknown detector id(s): {', '.join(sorted(unknown))}", file=sys.stderr)
            return 2

    # Execute i18n detectors
    findings = []
    baseline_suppressed = 0
    if run_i18n:
        findings = detectors.run(ctx, only=only_detectors)
        threshold = SEVERITY_ORDER[args.severity]
        findings = [f for f in findings if SEVERITY_ORDER[f.severity] <= threshold]

        baseline_path = _baseline_path(args, config, root)
        if args.write_baseline:
            report.write_baseline(baseline_path, findings)
        elif not args.no_baseline:
            known = report.load_baseline(baseline_path)
            if known:
                before = len(findings)
                findings = [f for f in findings if f.fingerprint not in known]
                baseline_suppressed = before - len(findings)

    # Execute Console Check if enabled
    console_result = run_console_check(root) if run_console else None

    # Execute Untranslated Check if enabled
    untranslated_result = run_untranslated_check(root, config) if run_untrans else None

    # Execute the pytest self-tests if enabled -- verifies the audit tool
    # itself, not the plugin. See selftest.py for why this belongs in the
    # Full Suite rather than being a separately-remembered manual step.
    selftest_result = run_self_tests(root) if run_selftest else None

    summary = report.summarize(findings)

    # Render Terminal Output
    if run_console or run_untrans or run_selftest:
        report.print_suite_dashboard(
            ctx,
            findings,
            console_result=console_result,
            untranslated_result=untranslated_result,
            selftest_result=selftest_result,
            baseline_suppressed=baseline_suppressed,
        )
    else:
        report.print_console(ctx, findings, baseline_suppressed=baseline_suppressed)

    # Export Reports
    report_dir = Path(args.report_dir)
    if not report_dir.is_absolute():
        report_dir = root / report_dir

    if args.export_markdown:
        report.export_markdown(
            report_dir / "i18n_audit_report.md",
            ctx,
            findings,
            summary,
            console_result=console_result,
            untranslated_result=untranslated_result,
            selftest_result=selftest_result,
        )
        if untranslated_result is not None:
            write_markdown_report(untranslated_result, report_dir / "untranslated_values.md")

    if args.export_json:
        report.export_json(
            report_dir / "i18n_audit_report.json",
            ctx,
            findings,
            summary,
            console_result=console_result,
            untranslated_result=untranslated_result,
            selftest_result=selftest_result,
        )

    # Check CI Strict Quality Gate
    if args.strict:
        # A broken self-test suite blocks strict mode regardless of --fail-on:
        # it means the detectors that just ran cannot be trusted, which is a
        # different (and worse) kind of problem than a graded finding.
        if selftest_result is not None and not selftest_result.is_clean:
            reason = selftest_result.setup_error or (
                f"{selftest_result.failed} failed, {selftest_result.errors} error(s) "
                f"in tools/tests ({selftest_result.passed}/{selftest_result.total} passed)")
            print(f"\n[strict] Self-tests are not clean: {reason}")
            return 1
        limit = SEVERITY_ORDER[args.fail_on]
        blocking = [f for f in findings if SEVERITY_ORDER[f.severity] <= limit]
        if blocking:
            print(f"\n[strict] {len(blocking)} finding(s) at severity '{args.fail_on}' or above.")
            return 1
        if console_result is not None and not console_result.is_clean and args.fail_on in ("warning", "info"):
            print(f"\n[strict] {console_result.total_findings} German string literals in console calls.")
            return 1

    return 0


def _baseline_path(args, config, root: Path) -> Path:
    raw = args.baseline or config.baseline_path or "tools/i18n_audit_baseline.json"
    path = Path(raw)
    return path if path.is_absolute() else root / path


def _print_helpers(ctx) -> None:
    print(f"Discovered {len(ctx.helpers)} message helper(s):\n")
    for name, helper in sorted(ctx.helpers.items()):
        print(f"{name}  ({helper.file}:{helper.line}, key param '{helper.key_param}')")
        for step in helper.steps:
            guard = f" [only if key startsWith {list(step.guards)}]" if step.guards else ""
            strip = f" [strip '{step.strip_prefix}']" if step.strip_prefix else ""
            default = "  <-- DEFAULTS TO THE KEY ITSELF" if step.default_is_key else ""
            print(f"    {step.line:>5}: {step.prefix}<key>{strip}{guard}{default}")
        print()
