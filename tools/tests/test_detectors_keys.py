"""
Regression tests for key resolution and life cycle detectors:
- D1: key-as-default
- D2: missing-key
- D9: unused-key
"""

from __future__ import annotations

# The helper the plugin really uses: several prefixed lookups, a guarded branch
# with a stripped prefix, and a final lookup that defaults to the key itself.
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


def _keys(findings):
    return {f.key for f in findings}


# --------------------------------------------------------------------------- D1

def test_d1_flags_helper_that_returns_the_key(project, english_bundle):
    project.bundle("messages_en.yml", english_bundle)
    project.java("EventPvpCommand.java", DEBUG_HELPER % "")

    findings = project.findings("D1")

    assert len(findings) == 1
    assert "falls back to the key itself" in findings[0].message
    assert findings[0].severity == "critical"


def test_d1_ignores_helper_with_a_real_default(project, english_bundle):
    project.bundle("messages_en.yml", english_bundle)
    project.java("Safe.java", '''
package de.zfzfg.test;
public class Safe {
    private String getMsg(String key) {
        return plugin.getMessages().getString("messages.general." + key, "&c[missing]");
    }
}
''')

    assert project.findings("D1") == []


# --------------------------------------------------------------------------- D2

def test_d2_reports_key_that_no_lookup_step_resolves(project, english_bundle):
    """`status-header` exists in no bundle, so /eventpvp debug printed it raw."""
    project.bundle("messages_en.yml", english_bundle)
    project.java("EventPvpCommand.java", DEBUG_HELPER % '''
    public void showStatus(CommandSender sender) {
        sender.sendMessage(getDebugMsg("status-header"));
        sender.sendMessage(getDebugMsg("status-label"));
    }
''')

    findings = project.findings("D2")

    assert _keys(findings) == {"status-header"}
    tried = findings[0].extra["candidates"]
    assert "messages.debug.messages.status-header" in tried
    assert "messages.debug.status-header" in tried


def test_d2_honours_guarded_prefix_stripping(project, english_bundle):
    """`help-header` must resolve via the guarded `messages.debug.help.` step."""
    project.bundle("messages_en.yml", english_bundle)
    project.java("EventPvpCommand.java", DEBUG_HELPER % '''
    public void showHelp(CommandSender sender) {
        sender.sendMessage(getDebugMsg("help-header"));
    }
''')

    assert project.findings("D2") == []


def test_d2_ignores_plain_config_reads(project, english_bundle):
    """`getString` reads whatever config it is called on.

    `config.getString("settings.language")` targets config.yml and
    `section.getString("helmet")` targets equipment.yml -- neither belongs in
    the language files, so neither may be reported as a missing message key.
    """
    project.bundle("messages_en.yml", english_bundle)
    project.java("CoreConfigManager.java", '''
package de.zfzfg.test;
public class CoreConfigManager {
    public void load(ConfigurationSection section) {
        String language = config.getString("settings.language", "en");
        String prefix = config.getString("settings.prefix");
        ItemStack helmet = parseItem(section.getString("helmet"));
        String real = messages.getString("messages.debug.messages.status-label");
    }
}
''')

    assert project.findings("D2") == []


def test_d2_still_sees_lookups_on_a_message_config(project, english_bundle):
    project.bundle("messages_en.yml", english_bundle)
    project.java("Reader.java", '''
package de.zfzfg.test;
public class Reader {
    public void load() {
        String gone = plugin.getMessages().getString("messages.debug.messages.nope");
    }
}
''')

    assert _keys(project.findings("D2")) == {"messages.debug.messages.nope"}


def test_d2_does_not_guess_across_unrelated_sections(project):
    """The legacy auditor tried ~16 prefixes and accepted any hit.

    `prefix` exists under `messages.general`, but this helper only ever reads
    `messages.debug.*`, so the call is broken and must be reported.
    """
    project.bundle("messages_en.yml",
                   "messages:\n"
                   "  general:\n"
                   "    prefix: '&8[&bPvP&8]&r '\n"
                   "  debug:\n"
                   "    messages:\n"
                   "      status-label: '&7Status'\n")
    project.java("EventPvpCommand.java", DEBUG_HELPER % '''
    public void show(CommandSender sender) {
        sender.sendMessage(getDebugMsg("prefix"));
    }
''')

    assert _keys(project.findings("D2")) == {"prefix"}


# --------------------------------------------------------------------------- D9

def test_d9_reports_orphan_key(project):
    project.bundle("messages_en.yml",
                   "messages:\n"
                   "  debug:\n"
                   "    messages:\n"
                   "      status-label: '&7Status'\n"
                   "      forgotten-key: '&7Nobody reads this'\n")
    project.java("EventPvpCommand.java", DEBUG_HELPER % '''
    public void show(CommandSender sender) {
        sender.sendMessage(getDebugMsg("status-label"));
    }
''')

    assert _keys(project.findings("D9")) == {"messages.debug.messages.forgotten-key"}


def test_d9_does_not_swallow_orphans_with_common_leaf_names(project):
    """The legacy check substring-searched the whole codebase for the leaf name.

    `name` appears in every Java file, so orphans like this were never reported.
    """
    project.bundle("messages_en.yml",
                   "messages:\n"
                   "  gui:\n"
                   "    name: '&7Unused label'\n")
    project.java("Something.java", '''
package de.zfzfg.test;
public class Something {
    public String getName() {
        return this.name;
    }
}
''')

    assert _keys(project.findings("D9")) == {"messages.gui.name"}


def test_d9_follows_a_helper_that_delegates_to_another_helper(project):
    """The wager GUI's `t(key)` calls `getMessage("messages.gui." + key)`.

    A helper does not have to touch getString itself. Missing this cost 158
    false "unused" reports on keys the GUI reads on every click.
    """
    project.bundle("messages_en.yml",
                   "messages:\n"
                   "  gui:\n"
                   "    back-title: '&7Back'\n"
                   "    orphan: '&7Nobody reads this'\n")
    project.java("AbstractWagerGui.java", '''
package de.zfzfg.test;
public class AbstractWagerGui {
    protected String t(String key, String... replacements) {
        return plugin.getPvpConfigManager().getMessage("messages.gui." + key, replacements);
    }

    protected ItemStack backButton() {
        return createButton(Material.ARROW, t("back-title"));
    }
}
''')

    assert _keys(project.findings("D9")) == {"messages.gui.orphan"}
    assert project.findings("D2") == []


def test_d9_treats_web_only_keys_as_used(project):
    project.bundle("messages_en.yml", "messages:\n  web:\n    title: 'Dashboard'\n")
    project.web("app.js", "const label = t('title');")

    assert project.findings("D9") == []
