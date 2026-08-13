"""
Language bundle loading.

Two views of every `messages_*.yml` are needed:

  * the parsed view (PyYAML) -- what the plugin actually sees at runtime,
  * the raw textual view -- what the author wrote, with line numbers.

The difference between the two is itself a bug class: an unquoted `on:` key is
written as `on` but parsed as the boolean `True`, so the runtime lookup of
`debug.help.on` fails while the file "looks" correct. Detector D3 lives off
exactly this discrepancy.
"""

from __future__ import annotations

import re
from dataclasses import dataclass, field
from pathlib import Path

try:
    import yaml
except ImportError:  # pragma: no cover - handled by the CLI
    yaml = None

# YAML 1.1 boolean-ish plain scalars. PyYAML resolves these to True/False when
# used unquoted as a mapping key; SnakeYAML (Bukkit) behaves the same way.
YAML11_BOOLS = {
    "y", "n", "yes", "no", "on", "off", "true", "false",
}
YAML11_NULLS = {"null", "~"}

_KEY_LINE = re.compile(r"^(?P<indent>\s*)(?P<key>[^\s#][^:]*?)\s*:(?:\s|$)")


@dataclass
class Bundle:
    path: Path
    name: str
    data: dict = field(default_factory=dict)
    keys: set = field(default_factory=set)          # flattened dotted keys, parsed view
    values: dict = field(default_factory=dict)      # dotted key -> scalar value
    key_lines: dict = field(default_factory=dict)   # dotted key (raw view) -> 1-based line
    raw_keys: set = field(default_factory=set)      # flattened dotted keys, textual view
    parse_error: str = ""

    @property
    def is_master_candidate(self) -> bool:
        return self.name.endswith("_en.yml")


def flatten(node, prefix: str = "", out_keys=None, out_values=None):
    """Flatten a nested mapping into dotted keys.

    Lifted from the legacy scanners (`extract_keys`) and extended to also keep
    the leaf values, which the placeholder and empty-value detectors need.
    """
    if out_keys is None:
        out_keys = set()
    if out_values is None:
        out_values = {}
    if isinstance(node, dict):
        for k, v in node.items():
            key = _key_str(k)
            new_prefix = f"{prefix}.{key}" if prefix else key
            flatten(v, new_prefix, out_keys, out_values)
    else:
        if prefix:
            out_keys.add(prefix)
            out_values[prefix] = node
    return out_keys, out_values


def _key_str(k) -> str:
    """Render a parsed YAML key the way a dotted lookup path would see it."""
    if k is True:
        return "true"
    if k is False:
        return "false"
    if k is None:
        return "null"
    return str(k)


def parse_raw_keys(text: str):
    """Indentation-based scan of the file text: dotted key -> line number.

    This sees what the *author* wrote, so `on:` stays `on` here even though
    PyYAML turns it into `true`.
    """
    stack: list = []  # (indent, key)
    result: dict = {}
    for lineno, line in enumerate(text.splitlines(), 1):
        if not line.strip() or line.lstrip().startswith("#"):
            continue
        if line.lstrip().startswith("-"):
            continue
        m = _KEY_LINE.match(line)
        if not m:
            continue
        indent = len(m.group("indent").expandtabs(2))
        key = m.group("key").strip()
        # Strip quotes the author used, they are not part of the key path.
        if len(key) >= 2 and key[0] == key[-1] and key[0] in "\"'":
            key = key[1:-1]
        while stack and stack[-1][0] >= indent:
            stack.pop()
        stack.append((indent, key))
        dotted = ".".join(k for _, k in stack)
        result[dotted] = lineno
    return result


def raw_key_is_unquoted(text: str, lineno: int) -> bool:
    lines = text.splitlines()
    if not (1 <= lineno <= len(lines)):
        return False
    stripped = lines[lineno - 1].lstrip()
    return not (stripped.startswith('"') or stripped.startswith("'"))


def load_bundle(path: Path) -> Bundle:
    text = path.read_text(encoding="utf-8", errors="replace")
    bundle = Bundle(path=path, name=path.name)
    bundle.key_lines = parse_raw_keys(text)
    bundle.raw_keys = set(bundle.key_lines)
    bundle.text = text  # type: ignore[attr-defined]

    if yaml is None:
        bundle.parse_error = "PyYAML is not installed"
        return bundle

    try:
        data = yaml.safe_load(text) or {}
    except Exception as exc:
        bundle.parse_error = f"YAML parse error: {exc}"
        return bundle

    if not isinstance(data, dict):
        bundle.parse_error = "Top level of the bundle is not a mapping"
        return bundle

    bundle.data = data
    bundle.keys, bundle.values = flatten(data)
    return bundle


def load_bundles(resources_dir: Path, pattern: str = "messages*.yml"):
    return [load_bundle(p) for p in sorted(resources_dir.glob(pattern))]
