"""
Web-panel translation bundles (`web/lang/*.json`) and their consumers.

Why this exists
----------------
The rest of this package audits the *Java* side: `messages_*.yml`, read
through `getMsg()`/`getMessage()` and friends. The web panel is a second,
independent translation system - `web/lang/en.json` plus `i18n.t(key)` in
`app.js` / `editors.js` / `items.js` / `index.html` - and nothing here ever
looked at it. D9 (`detect_unused_keys`) treats `web_files` only as a token
pool to avoid false-flagging a *Java* key that a JS file happens to consume;
it never asks whether a *web* bundle key is itself still read.

That gap was not theoretical. Building a one-off checker for it
(`tools/find_loose_ends.py --check-i18n`) immediately found a live instance:
`items.error.catalogFailed` is defined in every `web/lang/*.json` and
returned by the server as `messageKey`, but no JS file ever calls
`i18n.t('items.error.catalogFailed')` - the failure path only logs to the
console. The key was dead from the day it was added, and nothing detected it
because nothing was looking in that bundle at all.

Design, mirrored from the Java-side detectors on purpose
----------------------------------------------------------
* **D9's "literal anywhere" fallback** (a bundle key counts as used if its
  exact text appears as *any* quoted string literal in the source, not only
  as the direct argument of a lookup call) is reused here for
  `detect_web_unused_keys`. It is what makes `keyByMode = {auto:
  'inventory.descAuto', ...}` - a key reached through a local variable,
  never as `i18n.t('literal')` - count as used without a bespoke case for
  every such indirection.
* **`_is_dynamic()`'s plus-sign lookaround** (context.py) is reused here as
  `_DYNAMIC_CONCAT` / `_DYNAMIC_TEMPLATE`: `i18n.t('inventory.phase.' +
  session.phase)` marks the whole `inventory.phase.` subtree as covered,
  the same way `dynamic_key_roots` does for the Java side - except the
  prefix is *discovered* by scanning, not hand-maintained in the config.
* This module is deliberately separate from D8's `_web_lang_parity()`
  (`detectors/yamlcheck.py`), which already loads `web/lang/*.json` on its
  own to compare *key sets* across languages. That check answers "does
  `es.json` match `en.json`?"; this one answers "does anything still read
  this key at all?" - different questions, and D8's loader has no reason to
  track line numbers or scan JS call sites. Two small loaders beat one that
  serves two unrelated concerns.
* **Server-driven keys** (`mv.error.*`, `inventory.error.*`,
  `items.error.*`) reach `i18n.t()` only through a variable filled in from
  the server's `messageKey` field at runtime
  (`inventoryErrorText()`/`mvErrorText()` in app.js), so no static scan can
  ever find the call site. These three families are already documented as
  the same pattern on the Java side (`ignore.literal_prefixes` in
  `i18n_audit_config.yml`, next to `WebApiHandler.failure(...)`); the `web:`
  section of that file is their web-side mirror.
"""

from __future__ import annotations

import json
import re
from dataclasses import dataclass, field
from pathlib import Path

_LINE_KEY = re.compile(r'^\s*"((?:[^"\\]|\\.)+)"\s*:')
_STRING_LITERAL = re.compile(r"""(['"`])((?:(?!\1)[^\\]|\\.)*)\1""")
_KEY_SHAPE = re.compile(r"^[A-Za-z][A-Za-z0-9]*(?:\.[A-Za-z0-9]+)+$")

# `i18n.t('key'` immediately followed by `)` or `,` is a direct, resolvable
# call. Anything else (`+`, a backtick head) is handled by the dynamic
# patterns below and must not also be treated as a literal reference -
# `'inventory.phase.'` is a prefix, not a key in its own right.
_CALL_LITERAL = re.compile(r"""i18n\.t\(\s*(['"])((?:(?!\1)[^\\]|\\.)+)\1\s*(?=[),])""")
_DYNAMIC_CONCAT = re.compile(r"""i18n\.t\(\s*(['"])([A-Za-z0-9_]+(?:\.[A-Za-z0-9_]+)*\.)\1\s*\+""")
_DYNAMIC_TEMPLATE = re.compile(r"""i18n\.t\(\s*`([A-Za-z0-9_]+(?:\.[A-Za-z0-9_]+)*\.)\$\{""")
_DATA_ATTR = re.compile(r'data-i18n(?:-placeholder)?="([A-Za-z0-9_.]+)"')


@dataclass
class WebBundle:
    """One `web/lang/<code>.json` file: flat `"a.b.c": "text"` entries."""

    path: Path
    name: str
    keys: set = field(default_factory=set)
    values: dict = field(default_factory=dict)
    key_lines: dict = field(default_factory=dict)
    parse_error: str = ""


def load_web_bundle(path: Path) -> WebBundle:
    bundle = WebBundle(path=path, name=path.name)
    text = path.read_text(encoding="utf-8", errors="replace")

    for lineno, line in enumerate(text.splitlines(), 1):
        m = _LINE_KEY.match(line)
        if m:
            bundle.key_lines[_json_unescape(m.group(1))] = lineno

    try:
        data = json.loads(text)
    except json.JSONDecodeError as exc:
        bundle.parse_error = f"JSON parse error: {exc}"
        return bundle

    if not isinstance(data, dict):
        bundle.parse_error = "Top level of the bundle is not an object"
        return bundle

    for key, value in data.items():
        bundle.keys.add(key)
        bundle.values[key] = value
    return bundle


def _json_unescape(text: str) -> str:
    try:
        return json.loads(f'"{text}"')
    except json.JSONDecodeError:
        return text


@dataclass
class WebKeyReference:
    """A key passed to `i18n.t('literal', ...)` directly - the call site D10 checks."""

    key: str
    file: str
    line: int


@dataclass
class WebScanResult:
    call_refs: list = field(default_factory=list)     # WebKeyReference, one per call site
    call_keys: set = field(default_factory=set)        # {ref.key for ref in call_refs}
    attr_refs: set = field(default_factory=set)        # data-i18n(-placeholder) values
    dynamic_prefixes: set = field(default_factory=set)  # "inventory.phase." etc.
    literal_tokens: set = field(default_factory=set)   # every quoted string in the sources

    def is_referenced(self, key: str, server_driven_prefixes) -> bool:
        if key in self.call_keys or key in self.attr_refs or key in self.literal_tokens:
            return True
        if any(key.startswith(p) for p in self.dynamic_prefixes):
            return True
        return any(key.startswith(p) for p in server_driven_prefixes)


def scan_web_sources(paths, project_root: Path) -> WebScanResult:
    """Scans `app.js` / `editors.js` / `items.js` / `index.html` for key usage.

    One pass collects everything every detector below needs, the same
    division of labour as `context.py`'s "expensive work happens once" rule.
    """
    result = WebScanResult()

    for path in paths:
        try:
            text = path.read_text(encoding="utf-8", errors="replace")
        except OSError:
            continue
        rel = str(path.relative_to(project_root)).replace("\\", "/")

        for lineno, line in enumerate(text.splitlines(), 1):
            for m in _CALL_LITERAL.finditer(line):
                result.call_refs.append(WebKeyReference(key=m.group(2), file=rel, line=lineno))
                result.call_keys.add(m.group(2))
            for m in _DATA_ATTR.finditer(line):
                result.attr_refs.add(m.group(1))

        for m in _DYNAMIC_CONCAT.finditer(text):
            result.dynamic_prefixes.add(m.group(2))
        for m in _DYNAMIC_TEMPLATE.finditer(text):
            result.dynamic_prefixes.add(m.group(1))

        # D9's proven fallback, ported: a key-shaped quoted string anywhere in
        # the file counts as a reference, even reached only through a local
        # variable (`keyByMode = { auto: 'inventory.descAuto', ... }`). Kept
        # to full dotted keys, not bare leaves - D9's docstring explains why
        # leaf-only matching swallows nearly every real orphan.
        for m in _STRING_LITERAL.finditer(text):
            token = m.group(2)
            if _KEY_SHAPE.match(token):
                result.literal_tokens.add(token)

    return result
