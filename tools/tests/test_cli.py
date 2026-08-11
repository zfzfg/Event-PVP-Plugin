"""
Tests for the central CLI flag dispatcher (i18naudit.cli).
"""

from __future__ import annotations

from pathlib import Path

from i18naudit.cli import build_parser, main


def test_cli_parser_defaults():
    parser = build_parser()
    args = parser.parse_args([])
    assert args.all is False
    assert args.only_console is False
    assert args.only_untranslated is False
    assert args.only_i18n is False
    assert args.severity == "info"
    assert args.report_dir == "reports"


def test_cli_only_console_flag(tmp_path):
    java_file = tmp_path / "src" / "main" / "java" / "de" / "test" / "Clean.java"
    java_file.parent.mkdir(parents=True, exist_ok=True)
    java_file.write_text('public class Clean { String msg = "Clean English"; }', encoding="utf-8")
    (tmp_path / "pom.xml").write_text("<project/>", encoding="utf-8")

    code = main(["--project-root", str(tmp_path), "--only-console"])
    assert code == 0


def test_cli_only_untranslated_flag(tmp_path):
    res_dir = tmp_path / "src" / "main" / "resources"
    res_dir.mkdir(parents=True, exist_ok=True)
    (res_dir / "messages_en.yml").write_text("messages:\n  k: 'V'\n", encoding="utf-8")
    (res_dir / "messages_de.yml").write_text("messages:\n  k: 'V'\n", encoding="utf-8")
    (tmp_path / "pom.xml").write_text("<project/>", encoding="utf-8")

    code = main(["--project-root", str(tmp_path), "--only-untranslated"])
    assert code == 0
    assert (tmp_path / "reports" / "untranslated_values.md").exists()


def test_cli_unknown_detector_returns_error_code(tmp_path):
    (tmp_path / "pom.xml").write_text("<project/>", encoding="utf-8")
    res_dir = tmp_path / "src" / "main" / "resources"
    res_dir.mkdir(parents=True, exist_ok=True)
    (res_dir / "messages_en.yml").write_text("messages:\n  k: 'V'\n", encoding="utf-8")

    code = main(["--project-root", str(tmp_path), "--only", "UNKNOWN_99"])
    assert code == 2


def test_cli_export_flags(tmp_path):
    (tmp_path / "pom.xml").write_text("<project/>", encoding="utf-8")
    res_dir = tmp_path / "src" / "main" / "resources"
    res_dir.mkdir(parents=True, exist_ok=True)
    (res_dir / "messages_en.yml").write_text("messages:\n  k: 'V'\n", encoding="utf-8")
    (res_dir / "messages_de.yml").write_text("messages:\n  k: 'V'\n", encoding="utf-8")

    java_dir = tmp_path / "src" / "main" / "java"
    java_dir.mkdir(parents=True, exist_ok=True)
    (java_dir / "Test.java").write_text("public class Test {}", encoding="utf-8")

    code = main([
        "--project-root", str(tmp_path),
        "--export-markdown",
        "--export-json",
        "--report-dir", str(tmp_path / "custom_reports"),
    ])
    assert code == 0
    assert (tmp_path / "custom_reports" / "i18n_audit_report.md").exists()
    assert (tmp_path / "custom_reports" / "i18n_audit_report.json").exists()
    assert (tmp_path / "custom_reports" / "untranslated_values.md").exists()
