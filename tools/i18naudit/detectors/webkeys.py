"""
Web-panel key detectors: D10 (missing key) and D11 (unused key).

Mirror D2 and D9, but for `web/lang/*.json` + `i18n.t()` in the web panel's
JavaScript/HTML instead of `messages_*.yml` + `getMsg()` in Java. See
`webi18n.py` for why this bundle needed its own scan rather than reusing the
Java-side machinery, and for the concrete dead key (`items.error.catalogFailed`)
that motivated it.
"""

from __future__ import annotations

from ..findings import Finding


def detect_web_missing_keys(ctx):
    """D10 -- `i18n.t('literal.key')` where no bundle defines that key.

    `i18n.t()` falls back to the key itself when the lookup misses
    (`this.strings[key] || key`, app.js) - the same "key as default" failure
    mode D1 catches on the Java side, except here there is no separate
    detector for the fallback: the missing key alone is the whole bug, so
    this is critical like D2, not merely a warning like D9's web mirror D11.
    """
    if ctx.web_bundle is None or ctx.web_scan is None:
        return []

    by_key = {}
    for ref in ctx.web_scan.call_refs:
        if ref.key in ctx.web_bundle.keys:
            continue
        by_key.setdefault(ref.key, []).append(ref)

    findings = []
    for key, refs in sorted(by_key.items()):
        first = refs[0]
        locations = ", ".join(sorted({f"{r.file}:{r.line}" for r in refs})[:8])
        findings.append(Finding(
            detector="D10",
            severity="critical",
            title="",
            message=(
                f"i18n.t('{key}') is called but no key '{key}' exists in "
                f"{ctx.config.web_master_bundle} - the panel will show the raw key "
                f"text instead of a translation."
            ),
            file=first.file,
            line=first.line,
            key=key,
            hint=f"Add '{key}' to every file under web/lang/, or fix the call site.",
            extra={"locations": sorted({f"{r.file}:{r.line}" for r in refs})},
        ))
        findings[-1].snippet = locations
    return findings


def detect_web_unused_keys(ctx):
    """D11 -- a web bundle key nothing references.

    A key counts as used if its exact text appears anywhere as a quoted
    string literal in the web sources (not only as a direct `i18n.t()`
    argument - see webi18n.py's docstring for the `keyByMode` example this
    is built to catch), if it falls under a dynamically detected prefix
    (`i18n.t('inventory.phase.' + x)`), or if it starts with one of the
    configured `web.server_driven_prefixes` (a server `messageKey`, never a
    source-code literal at all).
    """
    if ctx.web_bundle is None or ctx.web_scan is None:
        return []

    findings = []
    for key in sorted(ctx.web_bundle.keys):
        if ctx.config.web_key_ignored(key):
            continue
        if ctx.web_scan.is_referenced(key, ctx.config.web_server_driven_prefixes):
            continue
        findings.append(Finding(
            detector="D11",
            severity="warning",
            title="",
            message=f"Key '{key}' is defined in {ctx.config.web_master_bundle} but never read.",
            file=f"{ctx.config.roots['web']}/{ctx.config.web_lang_dir}/{ctx.config.web_master_bundle}",
            line=ctx.web_bundle.key_lines.get(key, 0),
            key=key,
            hint="Remove it from every web/lang/*.json, or wire up the call site that "
                 "was meant to use it.",
        ))
    return findings
