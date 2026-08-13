"""
Bundle detectors: D3 (boolean keys), D4 (placeholder parity), D8 (bundle parity).
"""

from __future__ import annotations

import re

from ..bundles import YAML11_BOOLS, YAML11_NULLS, raw_key_is_unquoted
from ..findings import Finding

_PLACEHOLDER = re.compile(r"\{([A-Za-z0-9_]+)\}")
_REPLACE_CALL = re.compile(r'\.replace\(\s*"\{([A-Za-z0-9_]+)\}"')

# A TODO *marker* stands alone or introduces a lowercase instruction:
# "TODO", "TODO: translate", "[TODO]", "TODO translate this".
# It is never followed by another capitalised word -- but Spanish prose is:
# "todo" means "all", and messages.selection.both-selected legitimately reads
# "&a&l¡TODO ELEGIDO!". Matching the bare substring flagged that as untranslated.
_TODO_MARKER = re.compile(r"\bTODO\b(?!\s+[A-ZÁÉÍÓÚÑÜ])")


def detect_boolean_keys(ctx):
    """D3 -- an unquoted `on:` / `off:` key that YAML turns into a boolean.

    The author writes `on:` and the runtime looks up `debug.help.on`, but
    YAML 1.1 (PyYAML and SnakeYAML alike) resolves the *key* to the boolean
    `true`, so the entry is stored under `true` and the lookup always misses.
    The file reads correctly, which is why this survived every previous scan.
    """
    findings = []
    resources = ctx.config.roots["resources"]
    for bundle in ctx.bundles:
        text = getattr(bundle, "text", "")
        for dotted, lineno in sorted(bundle.key_lines.items(), key=lambda kv: kv[1]):
            leaf = dotted.split(".")[-1]
            lowered = leaf.lower()
            if lowered not in YAML11_BOOLS and lowered not in YAML11_NULLS:
                continue
            if not raw_key_is_unquoted(text, lineno):
                continue
            parsed_as = _parsed_name(lowered)
            aliases = _aliases_for(parsed_as, leaf)
            alias_note = (
                f" If the key was meant to be {aliases}, a YAML round-trip has already "
                f"rewritten it -- the code still looks up the original name and misses."
                if aliases else ""
            )
            findings.append(Finding(
                detector="D3",
                severity="critical",
                title="",
                message=(
                    f"Key '{leaf}' is an unquoted YAML 1.1 boolean, so it is stored as "
                    f"{parsed_as}, not as the text '{leaf}'.{alias_note}"
                ),
                file=f"{resources}/{bundle.name}",
                line=lineno,
                key=dotted,
                hint=f"Quote the key (\"'{leaf}':\") so YAML keeps it as a string, and make "
                     f"sure the name matches what the code requests.",
            ))
    return findings


def _aliases_for(parsed_as: str, leaf: str) -> str:
    """Other spellings YAML maps onto the same boolean, excluding the one written."""
    group = {"true": ["on", "yes", "y"], "false": ["off", "no", "n"]}.get(parsed_as, [])
    others = [f"'{g}'" for g in group if g != leaf.lower()]
    return " or ".join(others)


def _parsed_name(lowered: str) -> str:
    if lowered in YAML11_NULLS:
        return "null"
    return "true" if lowered in ("y", "yes", "on", "true") else "false"


def detect_placeholder_mismatch(ctx):
    """D4 -- `{x}` in a template vs `.replace("{x}", ...)` in the code.

    Checked in both directions: a placeholder the code never fills is shown raw
    to the player, and a replacement the template lacks silently drops data --
    which is why the debug status line never printed its level number.
    """
    if not ctx.master:
        return []

    findings = []
    resources = ctx.config.roots["resources"]

    # code side: key -> placeholders the code substitutes
    code_placeholders = {}
    for ref in ctx.references:
        if not ref.resolved:
            continue
        jf = _file_for(ctx, ref.file)
        if jf is None:
            continue
        stmt = _statement_at(jf, ref.line)
        if stmt is None:
            continue
        names = set(_REPLACE_CALL.findall(stmt.raw))
        if names:
            code_placeholders.setdefault(ref.resolved, set()).update(names)

    for key, used in sorted(code_placeholders.items()):
        value = ctx.master.values.get(key)
        if not isinstance(value, str):
            continue
        declared = set(_PLACEHOLDER.findall(value))
        missing = used - declared
        if missing:
            findings.append(Finding(
                detector="D4",
                severity="warning",
                title="",
                message=(
                    f"Code substitutes {_fmt(missing)} for '{key}', but the "
                    f"{ctx.config.master_bundle} template does not contain "
                    f"{'it' if len(missing) == 1 else 'them'} -- the value is dropped."
                ),
                file=f"{resources}/{ctx.config.master_bundle}",
                line=ctx.master.key_lines.get(key, 0),
                key=key,
                literal=value,
                hint=f"Add {_fmt(missing)} to the template in every bundle.",
            ))

    # translation side: a placeholder present in the master must exist everywhere
    for bundle in ctx.translation_bundles:
        for key, value in sorted(bundle.values.items()):
            if not isinstance(value, str):
                continue
            master_value = ctx.master.values.get(key)
            if not isinstance(master_value, str):
                continue
            declared = set(_PLACEHOLDER.findall(value))
            expected = set(_PLACEHOLDER.findall(master_value))
            missing = expected - declared
            if missing:
                findings.append(Finding(
                    detector="D4",
                    severity="warning",
                    title="",
                    message=(
                        f"'{key}' in {bundle.name} is missing placeholder(s) "
                        f"{_fmt(missing)} that the English template declares."
                    ),
                    file=f"{resources}/{bundle.name}",
                    line=bundle.key_lines.get(key, 0),
                    key=key,
                    literal=value,
                    hint="Keep placeholders identical across all languages.",
                ))
    return findings


def _fmt(names) -> str:
    return ", ".join(f"{{{n}}}" for n in sorted(names))


def _file_for(ctx, rel_path):
    for jf in ctx.java_files:
        if jf.rel_path == rel_path:
            return jf
    return None


def _statement_at(jf, line):
    for stmt in jf.statements:
        if stmt.line <= line <= stmt.end_line:
            return stmt
    return None


def _web_lang_parity(ctx):
    """The browser panel has its own bundles -- compare them like the YAML ones.

    `web/lang/*.json` is a second, independent set of translations, and nothing
    used to look at it: `es.json` had drifted 145 keys behind `en.json`, so a
    Spanish admin read raw key names like `spawn.radius` on screen. `i18n.t()`
    returns the key when it is missing, which looks like a layout glitch rather
    than a missing translation, so it never got reported.
    """
    import json

    findings = []
    lang_dir = ctx.config.web_dir / "lang"
    if not lang_dir.is_dir():
        return findings

    def load(path):
        try:
            with path.open(encoding="utf-8") as handle:
                data = json.load(handle)
        except (OSError, ValueError) as error:
            return None, str(error)
        return (data, None) if isinstance(data, dict) else (None, "not a JSON object")

    rel_dir = f"{ctx.config.roots['web']}/lang"
    master_path = lang_dir / "en.json"
    if not master_path.is_file():
        return findings
    master, error = load(master_path)
    if master is None:
        findings.append(Finding(
            detector="D8", severity="critical", title="web-bundle-unreadable",
            message=f"en.json: {error}",
            file=f"{rel_dir}/en.json", line=0,
            hint="Fix the JSON; the web UI falls back to raw keys.",
        ))
        return findings

    for path in sorted(lang_dir.glob("*.json")):
        if path.name in ("en.json", "languages.json"):
            continue
        data, error = load(path)
        if data is None:
            findings.append(Finding(
                detector="D8", severity="critical", title="web-bundle-unreadable",
                message=f"{path.name}: {error}",
                file=f"{rel_dir}/{path.name}", line=0,
                hint="Fix the JSON; the web UI falls back to raw keys.",
            ))
            continue
        missing = sorted(set(master) - set(data))
        extra = sorted(set(data) - set(master))
        if missing:
            findings.append(Finding(
                detector="D8", severity="warning", title="web-bundle-missing-keys",
                message=f"web/lang/{path.name} is missing {len(missing)} key(s) that "
                        f"en.json defines.",
                file=f"{rel_dir}/{path.name}", line=0,
                hint="i18n.t() returns the raw key for a missing entry, so the panel "
                     "shows the key name to the user.",
                extra={"keys": missing[:50], "total": len(missing)},
            ))
        if extra:
            findings.append(Finding(
                detector="D8", severity="warning", title="web-bundle-extra-keys",
                message=f"web/lang/{path.name} defines {len(extra)} key(s) that do not "
                        f"exist in en.json -- likely renamed or stale entries.",
                file=f"{rel_dir}/{path.name}", line=0,
                hint="Rename to match en.json, or delete.",
                extra={"keys": extra[:50], "total": len(extra)},
            ))
    return findings


def detect_bundle_parity(ctx):
    """D8 -- key sets, empty values and stale bundles.

    The legacy check only reported `master - translation`. Extra keys in a
    translation (typos, renames that were applied on one side only) were
    invisible, and so were empty or TODO values.
    """
    findings = []
    resources = ctx.config.roots["resources"]

    for bundle in ctx.bundles:
        if bundle.parse_error:
            findings.append(Finding(
                detector="D8", severity="critical", title="bundle-unreadable",
                message=f"{bundle.name}: {bundle.parse_error}",
                file=f"{resources}/{bundle.name}", line=0,
                hint="Fix the YAML syntax; the plugin cannot load this file.",
            ))

    if not ctx.master:
        return findings

    master_keys = ctx.master.keys
    for bundle in ctx.translation_bundles:
        if bundle.parse_error:
            continue
        missing = sorted(master_keys - bundle.keys)
        extra = sorted(bundle.keys - master_keys)
        if missing:
            findings.append(Finding(
                detector="D8", severity="warning", title="bundle-missing-keys",
                message=f"{bundle.name} is missing {len(missing)} key(s) that "
                        f"{ctx.config.master_bundle} defines.",
                file=f"{resources}/{bundle.name}", line=0,
                hint="Untranslated keys fall back to English at best, and to a "
                     "missing-message marker where no default is registered.",
                extra={"keys": missing[:50], "total": len(missing)},
            ))
        if extra:
            findings.append(Finding(
                detector="D8", severity="warning", title="bundle-extra-keys",
                message=f"{bundle.name} defines {len(extra)} key(s) that do not exist "
                        f"in {ctx.config.master_bundle} -- likely typos or stale entries.",
                file=f"{resources}/{bundle.name}", line=0,
                hint="Rename to match the master bundle, or delete.",
                extra={"keys": extra[:50], "total": len(extra)},
            ))

        for key, value in sorted(bundle.values.items()):
            if isinstance(value, str) and (not value.strip() or _TODO_MARKER.search(value)):
                findings.append(Finding(
                    detector="D8", severity="warning", title="bundle-placeholder-value",
                    message=f"'{key}' in {bundle.name} is empty or still marked TODO.",
                    file=f"{resources}/{bundle.name}",
                    line=bundle.key_lines.get(key, 0), key=key,
                    hint="Provide a real translation.",
                ))

    findings.extend(_web_lang_parity(ctx))

    # Bundles that ship but are never loaded.
    # `legacy_bundles_accepted` holds the ones the project decided to keep shipping
    # anyway. That is a product decision, not a detector tweak: the bundle stays
    # listed in `legacy_bundles`, so it is still excluded from parity comparisons --
    # only the "please delete me" reminder is silenced.
    accepted = set(ctx.config.legacy_bundles_accepted)
    for legacy in ctx.config.legacy_bundles:
        if legacy in accepted:
            continue
        bundle = ctx.bundle(legacy)
        if bundle is not None:
            findings.append(Finding(
                detector="D8", severity="warning", title="bundle-never-loaded",
                message=f"{legacy} is shipped but never loaded -- every loader builds "
                        f"the filename as messages_<lang>.yml.",
                file=f"{resources}/{legacy}", line=0,
                hint="Delete it, or fold its content into the per-language bundles.",
            ))
    return findings
