"""
Tests for the Untranslated Values Auditor (i18naudit.untranslated).
"""

from __future__ import annotations

from pathlib import Path

from i18naudit.untranslated import (
    generate_markdown,
    main,
    run_untranslated_check,
    write_markdown_report,
)


def test_untranslated_detects_identical_values(tmp_path):
    res_dir = tmp_path / "src" / "main" / "resources"
    res_dir.mkdir(parents=True, exist_ok=True)

    master = res_dir / "messages_en.yml"
    master.write_text('''
messages:
  gui:
    title: "Arena Selection"
    cancel: "Cancel"
  general:
    prefix: "[PvP]"
''', encoding="utf-8")

    german = res_dir / "messages_de.yml"
    german.write_text('''
messages:
  gui:
    title: "Arena Auswahl"
    cancel: "Cancel"
  general:
    prefix: "[PvP]"
''', encoding="utf-8")

    result = run_untranslated_check(tmp_path)
    assert "de" in result.languages
    de_data = result.per_language["de"]
    assert de_data.count == 2
    keys = [k for k, _ in de_data.items]
    assert "messages.gui.cancel" in keys
    assert "messages.general.prefix" in keys
    assert "messages.gui.title" not in keys

    md = generate_markdown(result)
    assert "# Untranslated values" in md
    assert "| de | 2 |" in md
    assert "messages.gui.cancel" in md

    report_path = write_markdown_report(result)
    assert report_path.exists()
    assert report_path.name == "untranslated_values.md"


def test_untranslated_main_execution(tmp_path):
    res_dir = tmp_path / "src" / "main" / "resources"
    res_dir.mkdir(parents=True, exist_ok=True)

    (res_dir / "messages_en.yml").write_text("messages:\n  test: 'Hello'\n", encoding="utf-8")
    (res_dir / "messages_es.yml").write_text("messages:\n  test: 'Hola'\n", encoding="utf-8")

    code = main(project_root=str(tmp_path))
    assert code == 0
    out_file = tmp_path / "reports" / "untranslated_values.md"
    assert out_file.exists()


def test_untranslated_skips_language_neutral_values(tmp_path):
    """Dividers, placeholders, commands and URLs are meant to stay identical.

    Reporting them buried the real gaps: of 66 entries listed for German, four
    in five were of this kind. The rules come from `ignore.literal_regex` in
    tools/i18n_audit_config.yml -- the same ones D6/D7 use -- applied after the
    colour codes are stripped, because `&e/pvp leave` otherwise never matches a
    pattern anchored at `/`.
    """
    res_dir = tmp_path / "src" / "main" / "resources"
    res_dir.mkdir(parents=True, exist_ok=True)

    body = '''
messages:
  divider: '&6&l\u2501\u2501\u2501\u2501\u2501\u2501\u2501'
  placeholder: '{name}'
  item-line: '&7- &f{item}'
  armor-line: '  &8\u2022 &f{part}: {material}'
  command: '&e/pvp leave'
  real-gap: 'Waiting for an opponent'
'''
    (res_dir / "messages_en.yml").write_text(body, encoding="utf-8")
    (res_dir / "messages_de.yml").write_text(body, encoding="utf-8")

    result = run_untranslated_check(tmp_path)
    keys = [k for k, _ in result.per_language["de"].items]

    assert keys == ["messages.real-gap"]


def test_untranslated_value_is_language_neutral_helper():
    from pathlib import Path

    from i18naudit.config import load_config
    from i18naudit.untranslated import value_is_language_neutral

    tools_dir = Path(__file__).resolve().parent.parent
    config = load_config(Path("."), tools_dir / "i18n_audit_config.yml")

    for neutral in ('&6&l\u2501\u2501\u2501', '{count}', '&7- &f{item}', '&e/pvp leave', '   '):
        assert value_is_language_neutral(neutral, config), neutral

    for gap in ('&7Arena: &e{arena}', 'Normal', '&eAdmin', 'Waiting for an opponent'):
        assert not value_is_language_neutral(gap, config), gap
