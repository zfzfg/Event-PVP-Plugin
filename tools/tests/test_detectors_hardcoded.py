"""
Regression tests for hardcoded text and natural language literal detectors:
- D5: untranslatable-display-name
- D6: hardcoded-message
- D7: natural-language-literal
"""

from __future__ import annotations


# --------------------------------------------------------------------------- D5

def test_d5_flags_german_enum_display_name(project, english_bundle):
    """The "Vollstaendig despite language: en" bug."""
    project.bundle("messages_en.yml", english_bundle)
    project.java("DebugLevel.java", '''
package de.zfzfg.test;
public enum DebugLevel {
    OFF(0, "Aus"),
    LEVEL_3(3, "Vollständig");

    private final int level;
    private final String displayName;

    DebugLevel(int level, String displayName) {
        this.level = level;
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
''')

    findings = project.findings("D5")

    assert {f.literal for f in findings} == {"Aus", "Vollständig"}
    assert all(f.severity == "critical" for f in findings)
    assert findings[0].extra["language"] == "de"


def test_d5_accepts_english_fallback_next_to_a_translation_key(project, english_bundle):
    """The fixed shape: a neutral English fallback plus a bundle key."""
    project.bundle("messages_en.yml", english_bundle)
    project.java("DebugLevel.java", '''
package de.zfzfg.test;
public enum DebugLevel {
    OFF(0, "Off", "level-off"),
    LEVEL_3(3, "Full", "level-full");

    private final String displayName;
    private final String translationKey;

    public String getDisplayName() {
        return displayName;
    }

    public String getTranslationKey() {
        return translationKey;
    }
}
''')

    assert project.findings("D5") == []


def test_d5_still_flags_a_german_fallback_despite_translation_key(project, english_bundle):
    """A German fallback leaks into other languages whenever the key is missing."""
    project.bundle("messages_en.yml", english_bundle)
    project.java("DebugLevel.java", '''
package de.zfzfg.test;
public enum DebugLevel {
    OFF(0, "Aus", "level-off"),
    LEVEL_3(3, "Full", "level-full");

    private final String displayName;
    private final String translationKey;

    public String getDisplayName() {
        return displayName;
    }

    public String getTranslationKey() {
        return translationKey;
    }
}
''')

    findings = project.findings("D5")

    assert [f.literal for f in findings] == ["Aus"]


# --------------------------------------------------------------------------- D6

def test_d6_flags_hardcoded_text_sent_to_a_player(project, english_bundle):
    project.bundle("messages_en.yml", english_bundle)
    project.java("EventSession.java", '''
package de.zfzfg.test;
public class EventSession {
    public void apply(Player player) {
        player.sendMessage("&7[Debug] Ausrüstung nicht vollständig angewendet!");
    }
}
''')

    findings = project.findings("D6")

    assert len(findings) == 1
    assert findings[0].severity == "critical"
    assert findings[0].extra["language"] == "de"


def test_d6_sees_the_hardcoded_half_of_a_mixed_statement(project, english_bundle):
    """The legacy scanner cleared the whole line because it contained getMsg."""
    project.bundle("messages_en.yml", english_bundle)
    project.java("Mixed.java", '''
package de.zfzfg.test;
public class Mixed {
    public void send(Player p) {
        p.sendMessage(ColorUtil.color(getMsg("prefix") + "&cFehler beim Speichern"));
    }
}
''')

    findings = project.findings("D6")

    assert [f.literal for f in findings] == ["&cFehler beim Speichern"]


def test_d6_spans_multiple_physical_lines(project, english_bundle):
    """A continuation line used to lose its sendMessage context entirely."""
    project.bundle("messages_en.yml", english_bundle)
    project.java("Wrapped.java", '''
package de.zfzfg.test;
public class Wrapped {
    public void send(Player p) {
        p.sendMessage(
            "&cDu darfst das nicht"
        );
    }
}
''')

    assert len(project.findings("D6")) == 1


def test_d6_sees_project_specific_send_wrappers(project, english_bundle):
    """`MessageUtil.error(...)` reaches the player just like `sendMessage`.

    Only `sendMessage` was listed as a sink at first, so 24 hardcoded messages
    routed through the project's own wrappers stayed invisible while D6
    reported zero findings.
    """
    project.bundle("messages_en.yml", english_bundle)
    project.java("InventoryRestoreCommand.java", '''
package de.zfzfg.test;
public class InventoryRestoreCommand {
    public void run(CommandSender sender) {
        MessageUtil.error(sender, "Nur Spieler koennen Inventare wiederherstellen.");
        MessageUtil.sendMessages(sender, buildLines());
        TextUtil.send(sender, "&cKein Inventar mit dieser ID gefunden.");
    }
}
''')

    findings = project.findings("D6")

    assert len(findings) == 2
    assert all(f.severity == "critical" for f in findings)


def test_d6_flags_hardcoded_logger_message(project, english_bundle):
    project.bundle("messages_en.yml", english_bundle)
    project.java("LoggerCall.java", '''
package de.zfzfg.test;
public class LoggerCall {
    public void log() {
        plugin.getLogger().info("Plugin ist auf dem neuesten Stand");
    }
}
''')

    findings = project.findings("D6")
    assert len(findings) == 1
    assert "sent to console/logger" in findings[0].message
    assert findings[0].severity == "critical"


def test_d6_ignores_keys_comments_loggers_and_banners(project, english_bundle):
    project.bundle("messages_en.yml", english_bundle)
    project.java("Clean.java", '''
package de.zfzfg.test;
public class Clean {
    // Sendet eine Nachricht an den Spieler wenn die Wette abgebrochen wurde
    private String getMsg(String key) {
        return plugin.getMessages().getString("messages.general." + key, "&c[missing]");
    }

    public void send(Player p) {
        p.sendMessage(getMsg("prefix"));
        p.sendMessage("&8&m                    &r");
        plugin.getLogger().info(plugin.getConsoleMsg("plugin-enabled"));
        plugin.getLogger().info("Diagnostic trace"); // i18n-ignore: technical log
        p.sendMessage("&cHardcoded"); // i18n-ignore
    }
}
''')

    assert project.findings("D6") == []


# --------------------------------------------------------------------------- D7

def test_d7_flags_prose_built_into_a_variable(project, english_bundle):
    project.bundle("messages_en.yml", english_bundle)
    project.java("Builder.java", '''
package de.zfzfg.test;
public class Builder {
    public String build() {
        String text = "&cDeine Wette wurde abgebrochen";
        return text;
    }
}
''')

    findings = project.findings("D7")

    assert [f.literal for f in findings] == ["&cDeine Wette wurde abgebrochen"]


def test_d7_ignores_formatting_scraps_and_enum_fallbacks(project, english_bundle):
    """A colour code alone does not make a literal display text."""
    project.bundle("messages_en.yml", english_bundle)
    project.java("Bits.java", '''
package de.zfzfg.test;
public class Bits {
    private final String prefix = "&8[&bDEBUG&8]";
    private final String sep = "&7, ";
    private final String close = "]&r";
    private final String reset = "&r";
}
''')
    project.java("DebugLevel.java", '''
package de.zfzfg.test;
public enum DebugLevel {
    OFF(0, "Off", "level-off");
    public String getDisplayName() { return displayName; }
    public String getTranslationKey() { return translationKey; }
}
''')

    assert project.findings("D7") == []


def test_d7_flags_a_one_word_label(project, english_bundle):
    """`"&7Output: "` is a single word but still addresses the player."""
    project.bundle("messages_en.yml", english_bundle)
    project.java("Label.java", '''
package de.zfzfg.test;
public class Label {
    private final String line = "&7  Ausgabe: &e";
}
''')

    assert [f.literal for f in project.findings("D7")] == ["&7  Ausgabe: &e"]


def test_d7_ignores_identifiers_sql_formats_and_commands(project, english_bundle):
    project.bundle("messages_en.yml", english_bundle)
    project.java("Repo.java", '''
package de.zfzfg.test;
public class Repo {
    private static final String QUERY = "SELECT id FROM wagers WHERE player = ?";
    private static final String NODE = "eventpvp.admin.reload";
    private static final String MODE = "survival";
    private static final String STAMP = "HH:mm:ss.SSS";
    private static final String DATE = "dd.MM.yyyy";
    private static final String CMD = "/eventpvp webtoken";
}
''')

    assert project.findings("D7") == []


def test_d7_ignores_web_bundle_error_keys(project, english_bundle):
    """`mv.error.*` is a key into web/lang/*.json, not display text."""
    project.bundle("messages_en.yml", english_bundle)
    project.java("MvBackend.java", '''
package de.zfzfg.test;
public class MvBackend {
    MvResult load() {
        if (!isAvailable()) {
            return MvResult.fail("mv.error.notInstalled");
        }
        return MvResult.fail("mv.error.loadFailed");
    }
    void check(String name) {
        throw new MvInputException("mv.error.invalidName", name);
    }
}
''')

    assert project.findings("D7") == []


def test_d7_ignores_inventory_bundle_error_keys(project, english_bundle):
    """`inventory.error.*` is a key into web/lang/*.json, not display text."""
    project.bundle("messages_en.yml", english_bundle)
    project.java("InventoryApi.java", '''
package de.zfzfg.test;
public class InventoryApi {
    Object list() {
        return failure(response, "inventory.error.unknownPlayer", name);
    }
    Object restore() {
        return failure(response, "inventory.error.sessionActive", id);
    }
}
''')

    assert project.findings("D7") == []


def test_d7_still_flags_prose_next_to_an_inventory_error_key(project, english_bundle):
    project.bundle("messages_en.yml", english_bundle)
    project.java("InventoryApiLoud.java", '''
package de.zfzfg.test;
public class InventoryApiLoud {
    Object restore() {
        return failure(response, "inventory.error.restoreFailed", "The backup could not be restored");
    }
}
''')

    assert len(project.findings("D7")) == 1


def test_d7_still_flags_prose_next_to_an_error_key(project, english_bundle):
    project.bundle("messages_en.yml", english_bundle)
    project.java("MvBackendLoud.java", '''
package de.zfzfg.test;
public class MvBackendLoud {
    MvResult load() {
        return MvResult.fail("mv.error.loadFailed", "Multiverse could not load the world");
    }
}
''')

    assert len(project.findings("D7")) == 1


def test_d7_ignores_css_declarations(project, english_bundle):
    project.bundle("messages_en.yml", english_bundle)
    project.web("index.html",
                '<style>\n'
                '.mv-usage {\n'
                '    flex-basis: 100%;\n'
                '    border-bottom: 1px solid var(--border);\n'
                '    order: 2;\n'
                '    --custom-aus: 3px;\n'
                '}\n'
                '</style>\n')

    assert project.findings("D7") == []


def test_d7_still_flags_german_prose_inside_a_style_block(project, english_bundle):
    project.bundle("messages_en.yml", english_bundle)
    project.web("index.html",
                '<style>\n'
                '.tip::after { content: "Welt nicht verfuegbar"; }\n'
                '</style>\n')

    assert len(project.findings("D7")) == 1


def test_d7_ignores_german_comments_in_web_assets(project, english_bundle):
    project.bundle("messages_en.yml", english_bundle)
    project.web("index.html", '<div>\n<!-- Welten werden dynamisch geladen -->\n</div>\n')
    project.web("app.js",
                'const url = "https://example.invalid/api"; // ID ist der Key\n'
                '/* Prueft freien Platz */\n')

    assert project.findings("D7") == []


def test_d7_still_flags_german_web_text_next_to_a_comment(project, english_bundle):
    project.bundle("messages_en.yml", english_bundle)
    project.web("app.js", 'showToast("Event nicht gefunden"); // spaeter ueber i18n\n')

    assert len(project.findings("D7")) == 1


def test_d7_honours_the_ignore_marker_in_web_assets(project, english_bundle):
    project.bundle("messages_en.yml", english_bundle)
    project.web("app.js",
                'const fallback = "Keine Backups gefunden";  // i18n-ignore: Notnagel\n')

    assert project.findings("D7") == []


def test_d7_ignores_enum_constants_stamps_and_missing_markers(project, english_bundle):
    project.bundle("messages_en.yml", english_bundle)
    project.java("Constants.java", '''
package de.zfzfg.test;
public class Constants {
    private static final String ITEM = "DIAMOND_CHESTPLATE";
    private static final String PHASE = "PVP_MATCH_PRE";
    private static final String DIM = "THE_END";
    private static final String FILE_STAMP = "yyyyMMdd_HHmmss";
    private static final String ISO = "yyyy-MM-dd'T'HH:mm:ss.SSSZ";
    private static final String MARKER = "&c[missing: " + "messages.gui.title" + "]";
}
''')

    assert project.findings("D7") == []


def test_d7_still_flags_a_shouted_german_sentence(project, english_bundle):
    project.bundle("messages_en.yml", english_bundle)
    project.java("Shout.java", '''
package de.zfzfg.test;
public class Shout {
    private static final String WIN = "DU HAST GEWONNEN";
}
''')

    assert len(project.findings("D7")) == 1


def test_d7_ignores_http_protocol_tokens(project, english_bundle):
    project.bundle("messages_en.yml", english_bundle)
    project.java("Http.java", '''
package de.zfzfg.test;
public class Http {
    private static final String ROUTE = "/api/auth/login";
    private static final String HEADER = "Content-Type";
    private static final String CORS = "Access-Control-Allow-Origin";
    private static final String MIME = "application/json; charset=UTF-8";
    private static final String IMAGE = "image/svg+xml";
    private static final String REASON = "Method Not Allowed";
    private static final String COOKIE = "; Path=/; HttpOnly; SameSite=Strict";
    private static final String CLEARED = "session=; Path=/; HttpOnly; Max-Age=0";
    private static final String METHODS = "GET, POST, OPTIONS";
    private static final String ALLOWED = "Content-Type, Authorization";
    private static final String CACHE = "public, max-age=3600";
}
''')

    assert project.findings("D7") == []


def test_d7_still_flags_prose_that_contains_a_protocol_token(project, english_bundle):
    project.bundle("messages_en.yml", english_bundle)
    project.java("Api.java", '''
package de.zfzfg.test;
public class Api {
    private static final String ERR = "Method Not Allowed - bitte benutze POST";
    private static final String OK = "Config gespeichert";
}
''')

    assert len(project.findings("D7")) == 2


def test_d7_still_flags_a_sentence_that_starts_with_a_slash(project, english_bundle):
    project.bundle("messages_en.yml", english_bundle)
    project.java("Hint.java", '''
package de.zfzfg.test;
public class Hint {
    private static final String HINT = "/pvp help - zeigt alle verfuegbaren Befehle an";
}
''')

    assert len(project.findings("D7")) == 1


# ----------------------------------------------------- i18n-ignore statement scope

def test_ignore_marker_covers_the_whole_concatenated_statement(project, english_bundle):
    """A marker on the first line silences the continuation lines too.

    The audit reported 7 critical D6 findings for messages that already carried
    a `// i18n-ignore`: the marker sat on the statement's first line, the
    literal it was written for on the next. There is nowhere else to put it --
    a `//` inside the argument list would comment out the rest of the call.
    """
    project.bundle("messages_en.yml", english_bundle)
    project.java("Migration.java", '''
package de.zfzfg.test;
public class Migration {
    void run(java.util.logging.Logger logger, String name) {
        logger.warning("Could not back up equipment.yml ("  // i18n-ignore: migration note
                + name + ") - keeping the file unchanged. The old sections stay in use.");
    }
}
''')

    assert project.findings("D6") == []
    assert project.findings("D7") == []


def test_ignore_marker_does_not_leak_into_the_next_statement(project, english_bundle):
    """Statement scope, not block scope: the call below must still be reported."""
    project.bundle("messages_en.yml", english_bundle)
    project.java("Mixed.java", '''
package de.zfzfg.test;
public class Mixed {
    void run(java.util.logging.Logger logger, String name) {
        logger.warning("Silenced on purpose ("  // i18n-ignore: technical log
                + name + ") - not a translation gap");
        logger.warning("Diese Meldung gehoert in die Sprachdateien");
    }
}
''')

    findings = project.findings("D6")
    assert len(findings) == 1
    assert findings[0].literal == "Diese Meldung gehoert in die Sprachdateien"


# ----------------------------------------------------- exception diagnostics (D7)

def test_d7_skips_exception_constructor_messages(project, english_bundle):
    """`new IOException("...")` is written for a stack trace, not a translator."""
    project.bundle("messages_en.yml", english_bundle)
    project.java("Limiter.java", '''
package de.zfzfg.test;
public class Limiter {
    Limiter(int maxPermits) throws java.io.IOException {
        if (maxPermits < 1) {
            throw new IllegalArgumentException("maxPermits must be at least 1");
        }
        throw new java.io.IOException("Resource pack URL returned HTTP " + maxPermits);
    }
}
''')

    assert project.findings("D7") == []


def test_d7_still_flags_player_text_next_to_an_exception(project, english_bundle):
    """The suffix rule must not spill over to the rest of the method."""
    project.bundle("messages_en.yml", english_bundle)
    project.java("Guard.java", '''
package de.zfzfg.test;
public class Guard {
    void check(org.bukkit.entity.Player player, int n) {
        if (n < 1) {
            throw new IllegalStateException("counter must be positive");
        }
        player.sendMessage("Du hast nicht genug Items dabei");
    }
}
''')

    findings = project.findings("D6")
    assert len(findings) == 1
    assert findings[0].literal == "Du hast nicht genug Items dabei"


# ----------------------------------------------------- literal_regex additions

def test_d7_skips_code_shaped_literals(project, english_bundle):
    """Date pattern with a dash, Material name suffix, resource path, dotfile."""
    project.bundle("messages_en.yml", english_bundle)
    project.java("Shapes.java", '''
package de.zfzfg.test;
public class Shapes {
    private static final String STAMP = "yyyyMMdd-HHmmss";
    private static final String EGG = "_SPAWN_EGG";
    private static final String PLATE = "_PRESSURE_PLATE";
    private static final String PREFIX = "assets/minecraft/textures/item/";
    private static final String MARKER = ".pack-sha1";
}
''')

    assert project.findings("D7") == []


def test_divider_rule_does_not_swallow_words_built_from_colour_code_letters():
    """"Cancel", "None", "Reload" and "Arena" consist only of a-f/k-o letters.

    The divider rule listed those letters as a bare character class instead of
    matching a colour code as the `&`+letter pair it is, so every such word
    counted as decoration and no detector could ever see it.
    """
    from pathlib import Path

    from i18naudit.config import load_config

    tools_dir = Path(__file__).resolve().parent.parent
    config = load_config(Path("."), tools_dir / "i18n_audit_config.yml")

    for word in ("Cancel", "None", "Reload", "Arena", "Deck"):
        assert not config.literal_ignored(word), word

    # Still decoration, and still ignored.
    for divider in ("&6&l━━━━", "   ", "-----", "&a&l===="):
        assert config.literal_ignored(divider), divider


def test_d6_flags_a_coloured_label_built_from_colour_code_letters(project, english_bundle):
    """The same bug at detector level: `&cCancel` read as pure decoration."""
    project.bundle("messages_en.yml", english_bundle)
    project.java("Labels.java", '''
package de.zfzfg.test;
public class Labels {
    void show(org.bukkit.entity.Player player) {
        player.sendMessage("&cCancel");
        player.sendMessage("&aReload");
    }
}
''')

    assert {f.literal for f in project.findings("D6")} == {"&cCancel", "&aReload"}


def test_d6_still_ignores_a_real_divider(project, english_bundle):
    project.bundle("messages_en.yml", english_bundle)
    project.java("Divider.java", '''
package de.zfzfg.test;
public class Divider {
    void show(org.bukkit.entity.Player player) {
        player.sendMessage("&6&l\u2501\u2501\u2501\u2501\u2501\u2501\u2501");
    }
}
''')

    assert project.findings("D6") == []


# ----------------------------------------------------- multi-line block comments

def test_web_scan_ignores_a_multiline_block_comment(project):
    """A German CSS comment spanning two lines was reported on its second line."""
    project.web("index.html", '''<html><head><style>
        /* Vorschau von Anzeigename und Lore so, wie Minecraft sie zeigt:
           dunkler Tooltip-Hintergrund, Monospace, Farben aus den Codes. */
        .mc-preview { background: #100010; }
</style></head><body></body></html>
''')

    assert [f for f in project.findings("D7") if f.file.endswith("index.html")] == []


def test_web_scan_still_flags_german_after_a_block_comment_closes(project):
    project.web("app.js", '''
/* Ein mehrzeiliger Kommentar,
   der hier endet. */
const label = "Welt wurde gespeichert";
''')

    findings = [f for f in project.findings("D7") if f.file.endswith("app.js")]
    assert len(findings) == 1
