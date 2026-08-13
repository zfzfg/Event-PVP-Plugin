"""
Source-literal detectors: D5 (display names), D6 (hardcoded messages),
D7 (natural-language literals).
"""

from __future__ import annotations

import re

from ..findings import Finding

COLOR_CODE = re.compile(r"[&§][0-9a-fk-orA-FK-OR]")
_ENUM_CONSTANT = re.compile(r"^[A-Z][A-Z0-9_]*$")
_ENUM_DECL = re.compile(r"\benum\s+([A-Za-z_$][A-Za-z0-9_$]*)")
_HAS_LETTERS = re.compile(r"[A-Za-zÀ-ɏ]")
_KEY_SHAPE = re.compile(r"^[a-z0-9]+([._\-][a-z0-9]+)*$")

# Words that mark a literal as German prose rather than an identifier.
GERMAN_WORDS = re.compile(
    r"\b(der|die|das|du|dich|dir|dein\w*|sie|ihr|ist|sind|wurde|wurden|kann|muss|"
    r"nicht|kein\w*|fehler|befehl|erfolg\w*|inventar|spieler\w*|abgebrochen|gespeichert|"
    r"geladen|deaktiviert|aktiviert|nutze|klicke|betreten|teleportiert|gesperrt|"
    r"ausführung|wette|ausrüstung|vollständig|erweitert|basis|aus|beides|"
    r"konsole|welt|konfig|angewendet|ungültig\w*|verfügbar|bereits|wurde)\b",
    re.IGNORECASE,
)
GERMAN_CHARS = re.compile(r"[äöüÄÖÜß]")

# Eine einzelne CSS-Deklaration: "flex-basis: 100%;", "border-bottom: 1px solid var(--x);".
# CSS-Eigenschaften sind englische Bezeichner, die zufaellig deutsche Silben enthalten
# koennen -- `flex-basis` traf `basis`, `border-order` traefe `order`. Das Muster verlangt
# eine reine Kleinbuchstaben-Eigenschaft, einen Doppelpunkt und ein abschliessendes
# Semikolon, damit Prosa mit Doppelpunkt ("Hinweis: bitte pruefen") weiterhin auffaellt.
CSS_DECLARATION = re.compile(r"^-{0,2}[a-z][a-z0-9-]*\s*:\s*[^;{}]*;$")

ENGLISH_WORDS = re.compile(
    r"\b(the|you|your|is|are|was|were|has|have|not|no|error|command|success|"
    r"inventory|player|cancelled|canceled|saved|loaded|disabled|enabled|click|"
    r"invalid|already|available|please|must|cannot|failed)\b",
    re.IGNORECASE,
)


def guess_language(text: str) -> str:
    if GERMAN_WORDS.search(text) or GERMAN_CHARS.search(text):
        return "de"
    if ENGLISH_WORDS.search(text):
        return "en"
    return "?"


_WORD = re.compile(r"[A-Za-zÀ-ɏ]{2,}")


def looks_like_prose(text: str) -> bool:
    """Text meant for a human, as opposed to an identifier or a formatting scrap.

    Colour codes alone are not evidence: `"]&r"` and `"&7, "` are separators, and
    `"&8[&bDEBUG&8]"` is a prefix constant. What marks display text is actual
    words -- two of them, a recognised language, or a single trailing-colon label
    such as `"&7Output: "`.
    """
    stripped = COLOR_CODE.sub("", text).strip()
    if not _HAS_LETTERS.search(stripped):
        return False
    if _KEY_SHAPE.match(stripped):
        return False
    if guess_language(stripped) != "?":
        return True
    words = _WORD.findall(stripped)
    if len(words) >= 2:
        return True
    # `Ausgabe:` / `Level:` -- a one-word label still addresses the player.
    return len(words) == 1 and stripped.rstrip().endswith(":")


def _decisive_call(lit, config):
    """First call in the chain that tells us what the literal is used for.

    `ignore.call_suffixes` catches constructor families by name shape rather
    than by enumerating every class: `new IOException("Resource pack URL
    returned HTTP " + code)` is a diagnostic for a developer reading a stack
    trace, not text a translator should ever see. Where such a message does
    reach the admin panel it travels as the `detail` field beside a localized
    `messageKey` (see WebApiHandler.failure), so the panel, not the exception,
    owns the wording.
    """
    for name in lit.call_chain:
        if name in config.localization_methods or name in config.bundle_accessors:
            return "localization"
        if name in config.player_text_methods:
            return "player"
        if name in config.logger_methods:
            return "logger"
        if name in config.ignore_calls:
            return "ignored"
        if config.ignore_call_suffixes and name.endswith(config.ignore_call_suffixes):
            return "ignored"
    return ""


def _skip(lit, jf, config) -> bool:
    if lit.line in jf.ignored_lines:
        return True
    text = lit.text.strip()
    if len(text) < 3:
        return True
    return config.literal_ignored(text)


def detect_display_names(ctx):
    """D5 -- human-readable text baked into enum constants.

    `LEVEL_3(3, "Vollstaendig")` is language-independent code, so `/eventpvp debug`
    injected German into an otherwise English message via
    `.replace("{level}", level.getDisplayName())`. The bundle was translated;
    the value was not.
    """
    config = ctx.config
    findings = []

    for jf in ctx.java_files:
        enum_match = _ENUM_DECL.search(jf.masked)
        if not enum_match:
            continue
        has_display_getter = any(
            re.search(rf"\b{re.escape(getter)}\s*\(\s*\)", jf.masked)
            for getter in config.enum_display_getters
        )
        if not has_display_getter:
            continue

        # An enum that also exposes a translation key resolves its label from
        # the bundles; the baked-in string is only the neutral fallback. Such a
        # fallback is fine in English, but a German one still leaks into other
        # languages whenever the key is missing, so keep reporting those.
        has_translation_getter = any(
            re.search(rf"\b{re.escape(getter)}\s*\(\s*\)", jf.masked)
            for getter in config.enum_translation_getters
        )

        for lit in jf.literals:
            if _skip(lit, jf, config):
                continue
            if not _ENUM_CONSTANT.match(lit.enclosing_call or ""):
                continue
            if not looks_like_prose(lit.text) and not GERMAN_CHARS.search(lit.text):
                continue
            lang = guess_language(lit.text)
            if has_translation_getter and lang != "de":
                continue
            findings.append(Finding(
                detector="D5",
                severity="critical",
                title="",
                message=(
                    f"Enum {enum_match.group(1)}.{lit.enclosing_call} carries the display "
                    f"text \"{lit.text}\"{f' ({lang})' if lang != '?' else ''}. It is emitted "
                    f"through a display getter and therefore ignores the selected language."
                    + (" The constant has a translation key, so this is only the fallback -- "
                       "but a German fallback still leaks into other languages."
                       if has_translation_getter else "")
                ),
                file=jf.rel_path,
                line=lit.line,
                literal=lit.text,
                snippet=_snippet(jf, lit.line),
                hint="Store a translation key on the constant and resolve it at the "
                     "output site, or expose getTranslationKey() next to the raw name.",
                extra={"language": lang, "enum": enum_match.group(1)},
            ))
    return findings


def detect_hardcoded_messages(ctx):
    """D6 -- a literal handed straight to a player-facing API or logger output.

    Statement-aware, unlike the legacy line scan: catches literals passed to
    player-facing sinks (sendMessage, sendTitle, createInventory) as well as
    logger sinks (getLogger().info/warning/severe) without going through
    getConsoleMsg or message bundle helpers.
    """
    config = ctx.config
    findings = []

    for jf in ctx.java_files:
        for lit in jf.literals:
            if _skip(lit, jf, config):
                continue
            decisive = _decisive_call(lit, config)
            if decisive not in ("player", "logger"):
                continue
            text = lit.text
            has_color = bool(COLOR_CODE.search(text))
            prose = looks_like_prose(text)
            if not has_color and not prose:
                continue
            lang = guess_language(text)
            target_desc = "players" if decisive == "player" else "console/logger"
            helper_hint = (
                "Move the text into messages_*.yml and read it through the class's message helper."
                if decisive == "player"
                else "Move the text into messages.console.* and read it through getConsoleMsg (or add `// i18n-ignore: <reason>`)."
            )
            findings.append(Finding(
                detector="D6",
                severity="critical" if (has_color or lang != "?") else "warning",
                title="",
                message=(
                    f"Literal \"{_trim(text)}\" is sent to {target_desc} by "
                    f"{lit.qualified_call}() without going through a message bundle"
                    f"{f' (looks like {lang})' if lang != '?' else ''}."
                ),
                file=jf.rel_path,
                line=lit.line,
                literal=text,
                snippet=_snippet(jf, lit.line),
                hint=helper_hint,
                extra={"language": lang, "call": lit.qualified_call, "sink": decisive},
            ))
    return findings


def detect_natural_language(ctx):
    """D7 -- prose literals outside any recognised sink, plus the web assets.

    Catches text that is built up in a variable first and only sent later, and
    covers every language via umlaut/word heuristics rather than the legacy
    German-only word list.
    """
    config = ctx.config
    findings = []

    for jf in ctx.java_files:
        for lit in jf.literals:
            if _skip(lit, jf, config):
                continue
            decisive = _decisive_call(lit, config)
            if decisive in ("localization", "player", "logger", "ignored"):
                continue
            # Enum constant arguments belong to D5, which knows about the
            # translation-key pattern; reporting them here too is duplicate noise.
            if _ENUM_CONSTANT.match(lit.enclosing_call or ""):
                continue
            text = lit.text
            if not looks_like_prose(text):
                continue
            lang = guess_language(text)
            findings.append(Finding(
                detector="D7",
                severity="warning",
                title="",
                message=(
                    f"Literal \"{_trim(text)}\" looks like display text"
                    f"{f' ({lang})' if lang != '?' else ''} but is not read from a bundle."
                ),
                file=jf.rel_path,
                line=lit.line,
                literal=text,
                snippet=_snippet(jf, lit.line),
                hint="If it reaches a player or console, move it into the bundles/getConsoleMsg; "
                     "otherwise add `// i18n-ignore`.",
                extra={"language": lang, "call": lit.qualified_call},
            ))

    findings.extend(_scan_web(ctx))
    return findings


def _strip_web_comments(line, in_html_comment, in_block_comment=False):
    """Remove comment text from one line of an HTML/JS asset.

    A German developer comment is not display text, but the scanner used to
    report `<!-- Welten werden dynamisch geladen -->` and a trailing
    `// ID ist der Key` exactly like a visible label. Quotes are tracked so a
    `//` inside "https://..." is not mistaken for a comment.

    `/* ... */` is tracked across lines, the same way `<!-- ... -->` already
    was. Handling it only within one line left every continuation line of a
    multi-line comment to be read as code -- which is how the German second
    line of a CSS comment in `index.html` became a finding while its own first
    line was correctly ignored.

    Returns the code part of the line and both comment states.
    """
    out = []
    quote = None
    i = 0
    while i < len(line):
        ch = line[i]
        if in_html_comment:
            if line.startswith("-->", i):
                in_html_comment = False
                i += 3
                continue
            i += 1
            continue
        if in_block_comment:
            if line.startswith("*/", i):
                in_block_comment = False
                i += 2
                continue
            i += 1
            continue
        if quote:
            out.append(ch)
            if ch == "\\":
                if i + 1 < len(line):
                    out.append(line[i + 1])
                i += 2
                continue
            if ch == quote:
                quote = None
            i += 1
            continue
        if ch in "\"'`":
            quote = ch
            out.append(ch)
            i += 1
            continue
        if line.startswith("<!--", i):
            in_html_comment = True
            i += 4
            continue
        if line.startswith("//", i):
            break
        if line.startswith("/*", i):
            end = line.find("*/", i + 2)
            if end == -1:
                in_block_comment = True
                break
            i = end + 2
            continue
        out.append(ch)
        i += 1
    return "".join(out).strip(), in_html_comment, in_block_comment


def _scan_web(ctx):
    findings = []
    for path in ctx.web_files:
        if path.suffix.lower() not in (".html", ".js"):
            continue
        try:
            lines = path.read_text(encoding="utf-8", errors="replace").splitlines()
        except OSError:
            continue
        try:
            rel = str(path.relative_to(ctx.config.project_root)).replace("\\", "/")
        except ValueError:
            rel = path.name
        in_html_comment = False
        in_block_comment = False
        for idx, line in enumerate(lines, 1):
            stripped = line.strip()
            # The guards below must not skip a line before the comment state is
            # updated, or a `/*` opener on such a line would leak into the rest
            # of the file. Only lines that cannot change the state are skipped.
            if not in_block_comment and (stripped.startswith("//") or stripped.startswith("*")):
                continue
            if "i18n-ignore" in stripped:
                continue
            stripped, in_html_comment, in_block_comment = _strip_web_comments(
                stripped, in_html_comment, in_block_comment
            )
            if not stripped:
                continue
            if CSS_DECLARATION.match(stripped):
                continue
            if not (GERMAN_WORDS.search(stripped) or GERMAN_CHARS.search(stripped)):
                continue
            findings.append(Finding(
                detector="D7", severity="info", title="web-hardcoded-text",
                message="German text in a web asset -- the web UI has its own language files.",
                file=rel, line=idx, snippet=_trim(stripped, 160),
                hint="Move the string into web/lang and look it up at render time.",
                extra={"language": "de"},
            ))
    return findings


def _snippet(jf, line, width=200) -> str:
    if 1 <= line <= len(jf.lines):
        return _trim(jf.lines[line - 1].strip(), width)
    return ""


def _trim(text: str, width: int = 90) -> str:
    text = text.replace("\n", "\\n")
    return text if len(text) <= width else text[: width - 3] + "..."
