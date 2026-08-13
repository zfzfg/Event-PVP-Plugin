"""
Regression tests for the web panel's own key detectors:
- D10: web-missing-key   (i18n.t() call the bundle can't resolve)
- D11: web-unused-key    (bundle key nothing reads)

The web panel (`web/lang/*.json` + `i18n.t()` in app.js/editors.js/items.js/
index.html) is a second, independent translation system that D1-D9 never
looked at. `items.error.catalogFailed` shipped exactly this bug - defined in
every `web/lang/*.json`, returned by the server as `messageKey`, never once
read by `i18n.t()` - and nothing caught it before D10/D11 existed.
"""

from __future__ import annotations


def _keys(findings):
    return {f.key for f in findings}


# -------------------------------------------------------------------- D10

def test_d10_flags_a_call_to_an_undefined_key(project):
    project.bundle("messages_en.yml", "messages:\n  a: 'A'\n")
    project.weblang("en.json", '{"button.save": "Save"}')
    project.web("app.js", "showToast(i18n.t('button.saev'), 'error');")

    findings = project.findings("D10")

    assert _keys(findings) == {"button.saev"}
    assert findings[0].severity == "critical"
    assert "raw key text" in findings[0].message


def test_d10_silent_on_a_key_that_exists(project):
    project.bundle("messages_en.yml", "messages:\n  a: 'A'\n")
    project.weblang("en.json", '{"button.save": "Save"}')
    project.web("app.js", "showToast(i18n.t('button.save'), 'success');")

    assert project.findings("D10") == []


def test_d10_ignores_dynamic_concatenation(project):
    """`i18n.t('inventory.phase.' + phase)` names a prefix, not a key -- D2's
    Java-side counterpart treats the same shape as dynamic, not missing."""
    project.bundle("messages_en.yml", "messages:\n  a: 'A'\n")
    project.weblang("en.json", '{"inventory.phase.open": "Open"}')
    project.web("app.js", "i18n.t('inventory.phase.' + session.phase)")

    assert project.findings("D10") == []


def test_d10_reports_every_call_site_for_a_shared_bad_key(project):
    project.bundle("messages_en.yml", "messages:\n  a: 'A'\n")
    project.weblang("en.json", '{"button.save": "Save"}')
    project.web("app.js", "i18n.t('typo.key')")
    project.web("editors.js", "i18n.t('typo.key')")

    findings = project.findings("D10")

    assert len(findings) == 1
    assert len(findings[0].extra["locations"]) == 2


# -------------------------------------------------------------------- D11

def test_d11_flags_a_key_outside_every_exemption(project):
    """The shape of bug that motivated D10/D11 in the first place: a real key
    with zero consumers, once `items.error.catalogFailed` was fixed to be
    genuinely read (see app.js). This uses an unrelated key so the test does
    not depend on that fix, or on the default server_driven_prefixes list."""
    project.bundle("messages_en.yml", "messages:\n  a: 'A'\n")
    project.weblang("en.json", '{"equipment.edit": "Edit"}')
    project.web("app.js", "console.log('unrelated');")

    findings = project.findings("D11")

    assert _keys(findings) == {"equipment.edit"}
    assert findings[0].severity == "warning"


def test_d11_silent_on_a_direct_call_site(project):
    project.bundle("messages_en.yml", "messages:\n  a: 'A'\n")
    project.weblang("en.json", '{"button.save": "Save"}')
    project.web("app.js", "i18n.t('button.save')")

    assert project.findings("D11") == []


def test_d11_silent_on_a_key_reached_through_a_local_variable(project):
    """`keyByMode = { auto: 'inventory.descAuto', ... }; i18n.t(key)` -- the
    key is a literal in the source, just never a direct i18n.t() argument."""
    project.bundle("messages_en.yml", "messages:\n  a: 'A'\n")
    project.weblang("en.json", '{"inventory.descAuto": "Automatic"}')
    project.web("app.js", "const keyByMode = { auto: 'inventory.descAuto' }; i18n.t(key);")

    assert project.findings("D11") == []


def test_d11_silent_on_a_data_i18n_attribute(project):
    project.bundle("messages_en.yml", "messages:\n  a: 'A'\n")
    project.weblang("en.json", '{"editor.helmet": "Helmet"}')
    project.web("index.html", '<span data-i18n="editor.helmet">Helmet</span>')

    assert project.findings("D11") == []


def test_d11_silent_on_a_data_i18n_placeholder_attribute(project):
    project.bundle("messages_en.yml", "messages:\n  a: 'A'\n")
    project.weblang("en.json", '{"editor.searchPlaceholder": "Search..."}')
    project.web("index.html", '<input data-i18n-placeholder="editor.searchPlaceholder">')

    assert project.findings("D11") == []


def test_d11_silent_on_a_dynamically_covered_prefix(project):
    project.bundle("messages_en.yml", "messages:\n  a: 'A'\n")
    project.weblang("en.json", '{"picker.category.weapons": "Weapons"}')
    project.web("editors.js", "i18n.t('picker.category.' + category)")

    assert project.findings("D11") == []


def test_d11_silent_on_a_server_driven_prefix(project):
    """No i18n.t() call site can ever exist for these -- the argument is a
    variable filled in from the server's `messageKey` field at runtime."""
    project.bundle("messages_en.yml", "messages:\n  a: 'A'\n")
    project.weblang("en.json", '{"items.error.catalogFailed": "Could not load"}')
    project.web("app.js", "const text = i18n.t(source.messageKey || 'items.error.catalogFailed');")

    assert project.findings("D11") == []


def test_d11_honours_web_ignore_keys(project):
    from i18naudit.detectors.webkeys import detect_web_unused_keys

    project.bundle("messages_en.yml", "messages:\n  a: 'A'\n")
    project.weblang("en.json", '{"debug.scratch.temp": "Temp"}')
    project.web("app.js", "console.log('unrelated');")

    ctx, _ = project.run("D11")
    ctx.config.web_ignore_keys = ["debug.scratch"]

    assert detect_web_unused_keys(ctx) == []
