"""
Regression tests for YAML syntax and bundle consistency detectors:
- D3: yaml-boolean-key
- D4: placeholder-mismatch
- D8: bundle-parity
"""

from __future__ import annotations

# The helper the plugin really uses
DEBUG_HELPER = '''
package de.zfzfg.test;

public class EventPvpCommand {
    private String getDebugMsg(String key) {
        if (key.startsWith("help-") || key.startsWith("level-")) {
            String subKey = key.replace("help-", "");
            String val = plugin.getMessages().getString("messages.debug.help." + subKey, null);
            if (val != null) return val;
        }
        String msgVal = plugin.getMessages().getString("messages.debug.messages." + key, null);
        if (msgVal != null) return msgVal;
        return plugin.getMessages().getString("messages.debug." + key, key);
    }
%s
}
'''


# --------------------------------------------------------------------------- D3

def test_d3_flags_unquoted_boolean_key(project):
    project.bundle("messages_en.yml",
                   "messages:\n"
                   "  debug:\n"
                   "    help:\n"
                   "      on: '&e/debug on'\n"
                   "      off: '&e/debug off'\n")

    findings = project.findings("D3")

    assert len(findings) == 2
    assert all(f.severity == "critical" for f in findings)
    assert "stored as true" in findings[0].message


def test_d3_accepts_quoted_boolean_key(project):
    project.bundle("messages_en.yml",
                   "messages:\n"
                   "  debug:\n"
                   "    help:\n"
                   "      'on': '&e/debug on'\n"
                   "      'off': '&e/debug off'\n")

    assert project.findings("D3") == []


# --------------------------------------------------------------------------- D4

def test_d4_flags_placeholder_the_template_lacks(project):
    """`level-label` has no {number}, but the code substitutes one."""
    project.bundle("messages_en.yml",
                   "messages:\n"
                   "  debug:\n"
                   "    messages:\n"
                   "      level-label: '&7Level: &e{level}'\n")
    project.java("EventPvpCommand.java", DEBUG_HELPER % '''
    public void show(CommandSender sender) {
        sender.sendMessage(getDebugMsg("level-label")
            .replace("{level}", level.getDisplayName())
            .replace("{number}", String.valueOf(level.getLevel())));
    }
''')

    findings = project.findings("D4")

    assert len(findings) == 1
    assert "{number}" in findings[0].message


def test_d4_flags_translation_missing_a_placeholder(project):
    project.bundle("messages_en.yml",
                   "messages:\n  greet: 'Hello {player}'\n")
    project.bundle("messages_de.yml",
                   "messages:\n  greet: 'Hallo'\n")

    findings = [f for f in project.findings("D4") if f.file.endswith("messages_de.yml")]

    assert len(findings) == 1
    assert "{player}" in findings[0].message


def test_d4_silent_when_placeholders_agree(project):
    project.bundle("messages_en.yml", "messages:\n  greet: 'Hello {player}'\n")
    project.bundle("messages_de.yml", "messages:\n  greet: 'Hallo {player}'\n")

    assert project.findings("D4") == []


# --------------------------------------------------------------------------- D8

def test_d8_reports_missing_and_extra_keys(project):
    project.bundle("messages_en.yml", "messages:\n  a: 'A'\n  b: 'B'\n")
    project.bundle("messages_de.yml", "messages:\n  a: 'A'\n  c: 'C'\n")

    titles = {f.title for f in project.findings("D8")}

    assert "bundle-missing-keys" in titles
    assert "bundle-extra-keys" in titles


def test_d8_reports_empty_and_todo_values(project):
    project.bundle("messages_en.yml", "messages:\n  a: 'A'\n  b: 'B'\n")
    project.bundle("messages_de.yml", "messages:\n  a: ''\n  b: 'TODO translate'\n")

    findings = [f for f in project.findings("D8") if f.title == "bundle-placeholder-value"]

    assert len(findings) == 2


def test_d8_does_not_read_spanish_prose_as_a_todo_marker(project):
    """'todo' is Spanish for 'all' -- "¡TODO ELEGIDO!" is a finished translation."""
    project.bundle("messages_en.yml", "messages:\n  a: 'ALL CHOICES MADE!'\n")
    project.bundle("messages_es.yml", "messages:\n  a: '&a&l¡TODO ELEGIDO!'\n")

    findings = [f for f in project.findings("D8") if f.title == "bundle-placeholder-value"]

    assert findings == []


def test_d8_reports_a_legacy_bundle_that_was_not_accepted(project):
    """Without an explicit product decision the never-loaded bundle must still show up."""
    project.bundle("messages_en.yml", "messages:\n  a: 'A'\n")
    project.bundle("messages.yml", "messages:\n  a: 'A'\n")

    findings = [f for f in project.findings("D8", accepted_legacy=[])
                if f.title == "bundle-never-loaded"]

    assert len(findings) == 1


def test_d8_accepts_the_legacy_bundle_the_project_decided_to_keep(project):
    """messages.yml is kept on purpose (see legacy_bundles_accepted), so no reminder."""
    project.bundle("messages_en.yml", "messages:\n  a: 'A'\n")
    project.bundle("messages.yml", "messages:\n  a: 'A'\n")

    titles = {f.title for f in project.findings("D8")}

    assert "bundle-never-loaded" not in titles


def test_d8_reports_a_web_language_file_that_drifted(project):
    """web/lang/*.json is a second bundle set -- es.json had fallen 145 keys behind."""
    project.bundle("messages_en.yml", "messages:\n  a: 'A'\n")
    project.weblang("en.json", '{"spawn.radius": "Radius", "button.save": "Save"}')
    project.weblang("es.json", '{"spawn.radius": "Radio", "button.guardar": "Guardar"}')

    findings = {f.title: f for f in project.findings("D8")}

    assert findings["web-bundle-missing-keys"].extra["keys"] == ["button.save"]
    assert findings["web-bundle-extra-keys"].extra["keys"] == ["button.guardar"]


def test_d8_silent_on_matching_web_language_files(project):
    project.bundle("messages_en.yml", "messages:\n  a: 'A'\n")
    project.weblang("en.json", '{"button.save": "Save"}')
    project.weblang("de.json", '{"button.save": "Speichern"}')
    project.weblang("languages.json", '{"default": "en", "available": []}')

    assert project.findings("D8") == []


def test_d8_reports_unreadable_web_language_file(project):
    project.bundle("messages_en.yml", "messages:\n  a: 'A'\n")
    project.weblang("en.json", '{"button.save": "Save"}')
    project.weblang("fr.json", '{"button.save": "Enregistrer",}')

    findings = [f for f in project.findings("D8") if f.title == "web-bundle-unreadable"]

    assert len(findings) == 1
    assert findings[0].severity == "critical"


def test_d8_silent_on_identical_bundles(project):
    project.bundle("messages_en.yml", "messages:\n  a: 'A'\n")
    project.bundle("messages_de.yml", "messages:\n  a: 'A auf Deutsch'\n")

    assert project.findings("D8") == []
