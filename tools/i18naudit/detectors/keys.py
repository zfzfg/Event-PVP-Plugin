"""
Key-binding detectors: D1 (key as default), D2 (missing key), D9 (unused key).
"""

from __future__ import annotations

import re

from ..findings import Finding

_WEB_KEY = re.compile(r"""["'`]([A-Za-z0-9_.\-]{2,})["'`]""")


def detect_key_as_default(ctx):
    """D1 -- a helper that returns the key itself when no bundle entry matches.

    `getString(path + key, key)` makes a missing translation look like a real
    message: `/eventpvp debug` printed the literal text `status-header` as its
    header for exactly this reason. A missing key must be *visible*, not silent.
    """
    findings = []
    for helper in sorted(ctx.helpers.values(), key=lambda h: (h.file, h.line)):
        for step in helper.key_as_default_steps:
            findings.append(Finding(
                detector="D1",
                severity="critical",
                title="",
                message=(
                    f"{helper.qualified}() falls back to the key itself "
                    f"(`{step.prefix}\" + {helper.key_param}, {helper.key_param}`), so an "
                    f"unmapped key is rendered to the player as plain text."
                ),
                file=helper.file,
                line=step.line,
                key=step.prefix,
                hint=(
                    f'Return a visible marker instead, e.g. "&c[missing:" + '
                    f'{helper.key_param} + "]", and log the miss once.'
                ),
            ))
    return findings


def detect_missing_keys(ctx):
    """D2 -- a key referenced in code that no bundle path resolves to.

    Unlike the legacy auditor this reports one finding per key with all call
    sites attached, instead of one entry per occurrence.
    """
    if not ctx.master:
        return []

    by_key = {}
    for ref in ctx.references:
        if ref.resolved:
            continue
        entry = by_key.setdefault(ref.key, {"refs": [], "candidates": []})
        entry["refs"].append(ref)
        # A key can be requested through several helpers; report the union of
        # every path that was tried, not just the first call site's chain.
        for cand in ref.candidates:
            if cand not in entry["candidates"]:
                entry["candidates"].append(cand)

    findings = []
    for key, entry in sorted(by_key.items()):
        refs = entry["refs"]
        first = refs[0]
        locations = ", ".join(sorted({f"{r.file}:{r.line}" for r in refs})[:8])
        helpers = sorted({r.helper for r in refs})
        tried = ", ".join(entry["candidates"][:6]) or "(no candidate path)"
        if len(entry["candidates"]) > 6:
            tried += f", ... (+{len(entry['candidates']) - 6})"
        findings.append(Finding(
            detector="D2",
            severity="critical",
            title="",
            message=(
                f"Key '{key}' is requested via {', '.join(helpers)} but exists in no "
                f"bundle path. Tried: {tried}."
            ),
            file=first.file,
            line=first.line,
            key=key,
            hint=f"Add the key to {ctx.config.master_bundle} (and every translation), "
                 f"or point the call at an existing key.",
            extra={"locations": sorted({f"{r.file}:{r.line}" for r in refs}),
                   "candidates": list(entry["candidates"])},
        ))
        findings[-1].snippet = locations
    return findings


def detect_unused_keys(ctx):
    """D9 -- a bundle key nothing references.

    The legacy check asked whether the *leaf* name appeared anywhere in the
    concatenated source. Leaves like `name`, `title` or `error` match almost any
    Java file, so nearly every orphan was swallowed. This version matches on
    resolved key paths plus exact literal occurrences, and also reads the web
    assets so web-only keys are not reported as dead.
    """
    if not ctx.master:
        return []

    used = {ref.resolved for ref in ctx.references if ref.resolved}

    # Exact literals anywhere in Java (covers constants and dynamic assembly).
    java_literals = set()
    for jf in ctx.java_files:
        for lit in jf.literals:
            text = lit.text.strip()
            if text:
                java_literals.add(text)

    # Web assets consume keys too (web/lang/*.json, app.js).
    web_tokens = set()
    for path in ctx.web_files:
        try:
            text = path.read_text(encoding="utf-8", errors="replace")
        except OSError:
            continue
        web_tokens.update(_WEB_KEY.findall(text))

    # Dynamic call sites: `getMsg("prefix." + type)` can reach a whole subtree,
    # so treat every key under a referenced prefix as potentially used.
    dynamic_prefixes = [k for k, _f, _l in ctx.dynamic_keys if k]

    dynamic_roots = tuple(ctx.config.dynamic_key_roots)

    findings = []
    for key in sorted(ctx.master.keys):
        if key in used or ctx.config.key_ignored(key):
            continue
        if dynamic_roots and key.startswith(dynamic_roots):
            continue
        leaf = key.split(".")[-1]
        if key in java_literals or leaf in java_literals:
            continue
        if leaf in web_tokens or key in web_tokens:
            continue
        if any(key.startswith(p) or p.startswith(key) for p in dynamic_prefixes):
            continue
        findings.append(Finding(
            detector="D9",
            severity="warning",
            title="",
            message=f"Key '{key}' is defined in {ctx.config.master_bundle} but never read.",
            file=f"{ctx.config.roots['resources']}/{ctx.config.master_bundle}",
            line=ctx.master.key_lines.get(key, 0),
            key=key,
            hint="Remove it, or wire up the code path that was meant to use it.",
        ))
    return findings
