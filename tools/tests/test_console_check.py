"""
Tests for the Console & Logger Language Checker (i18naudit.console).
"""

from __future__ import annotations

from pathlib import Path

from i18naudit.console import main, run_console_check, scan_java_file


def test_console_check_flags_german_logger_message(tmp_path):
    java_file = tmp_path / "src" / "main" / "java" / "de" / "test" / "Broken.java"
    java_file.parent.mkdir(parents=True, exist_ok=True)
    java_file.write_text('''
package de.test;
public class Broken {
    public void test() {
        getLogger().info("Ausrüstung wurde erfolgreich gespeichert");
    }
}
''', encoding="utf-8")

    findings = scan_java_file(java_file, project_root=tmp_path)
    assert len(findings) == 1
    assert "Ausrüstung" in findings[0].literal or "gespeichert" in findings[0].literal
    assert findings[0].line == 5

    res = run_console_check(tmp_path)
    assert not res.is_clean
    assert res.total_findings == 1
    assert res.total_files_scanned == 1


def test_console_check_ignores_english_messages(tmp_path):
    java_file = tmp_path / "src" / "main" / "java" / "de" / "test" / "Clean.java"
    java_file.parent.mkdir(parents=True, exist_ok=True)
    java_file.write_text('''
package de.test;
public class Clean {
    public void test() {
        getLogger().info("Configuration loaded successfully");
        Bukkit.getConsoleSender().sendMessage("Event plugin enabled");
    }
}
''', encoding="utf-8")

    res = run_console_check(tmp_path)
    assert res.is_clean
    assert res.total_findings == 0


def test_console_check_ignores_german_comments(tmp_path):
    java_file = tmp_path / "src" / "main" / "java" / "de" / "test" / "Commented.java"
    java_file.parent.mkdir(parents=True, exist_ok=True)
    java_file.write_text('''
package de.test;
// Hier wurde ein Fehler behoben
/* Speichere alle Daten */
/**
 * Ueberpruefe die Ausruestung
 */
public class Commented {
    public void test() {
        getLogger().info("Everything is fine");
    }
}
''', encoding="utf-8")

    res = run_console_check(tmp_path)
    assert res.is_clean
    assert res.total_findings == 0


def test_console_check_ignores_bundle_keys(tmp_path):
    java_file = tmp_path / "src" / "main" / "java" / "de" / "test" / "Keys.java"
    java_file.parent.mkdir(parents=True, exist_ok=True)
    java_file.write_text('''
package de.test;
public class Keys {
    public void test() {
        String k = "messages.general.fehler";
        String s = "settings.language.sprache";
    }
}
''', encoding="utf-8")

    res = run_console_check(tmp_path)
    assert res.is_clean
    assert res.total_findings == 0


def test_console_check_main_exit_code(tmp_path):
    java_file = tmp_path / "src" / "main" / "java" / "de" / "test" / "App.java"
    java_file.parent.mkdir(parents=True, exist_ok=True)
    java_file.write_text('public class App { String msg = "Konnte Datei nicht laden"; }', encoding="utf-8")

    code = main(project_root=str(tmp_path))
    assert code == 1

    java_file.write_text('public class App { String msg = "Could not load file"; }', encoding="utf-8")
    code = main(project_root=str(tmp_path))
    assert code == 0
