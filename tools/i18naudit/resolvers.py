"""
Key resolution.

The legacy auditor guessed: for every raw key it built ~16 candidate paths
(`messages.`, `messages.request.`, `messages.wager.`, ...) and accepted the
first one that happened to exist. That hides real bugs -- a key bound to the
wrong section still "resolves" -- and it cannot model a helper that returns the
key itself when nothing matches.

This module instead reads the helper methods out of the source. For

    private String getDebugMsg(String key) {
        if (key.startsWith("help-") || key.startsWith("level-")) {
            String subKey = key.replace("help-", "");
            String val = ...getString("messages.debug.help." + subKey, null);
            if (val != null) return val;
        }
        String msgVal = ...getString("messages.debug.messages." + key, null);
        ...
        return ...getString("messages.debug." + key, key);
    }

it derives the ordered lookup chain and notices that the final step defaults to
the key parameter itself -- the reason `/eventpvp debug` prints `status-header`.
"""

from __future__ import annotations

import re
from dataclasses import dataclass, field

# Methods that read a value out of a loaded YAML configuration.
BUNDLE_ACCESSORS = {"getString", "getStringList", "get", "contains", "isSet"}

_METHOD_DECL = re.compile(
    r"(?:public|private|protected)\s+(?:static\s+)?(?:final\s+)?"
    r"(?:String|List<String>)\s+(?P<name>[A-Za-z_$][A-Za-z0-9_$]*)\s*\("
    r"(?P<params>[^)]*)\)\s*\{"
)
_CLASS_DECL = re.compile(r"\b(?:class|enum|interface)\s+(?P<name>[A-Za-z_$][A-Za-z0-9_$]*)")
_STARTS_WITH = re.compile(r'\.startsWith\(\s*"([^"]*)"')
_REPLACE_ASSIGN = re.compile(
    r'String\s+(?P<var>[A-Za-z_$][A-Za-z0-9_$]*)\s*=\s*(?P<src>[A-Za-z_$][A-Za-z0-9_$]*)'
    r'\s*\.replace\(\s*"(?P<old>[^"]*)"\s*,\s*"(?P<new>[^"]*)"\s*\)'
)


@dataclass
class LookupStep:
    """One `getString(prefix + key)` inside a helper."""

    prefix: str
    strip_prefix: str = ""       # applied to the key before the lookup
    guards: tuple = ()            # step only applies if key startswith one of these
    default_is_key: bool = False  # `getString(path, key)` -- D1
    line: int = 0

    def applies_to(self, key: str) -> bool:
        return not self.guards or any(key.startswith(g) for g in self.guards)

    def path_for(self, key: str) -> str:
        if self.strip_prefix and key.startswith(self.strip_prefix):
            key = key[len(self.strip_prefix):]
        elif self.strip_prefix:
            key = key.replace(self.strip_prefix, "")
        return f"{self.prefix}{key}"


@dataclass
class Helper:
    """A message-lookup helper method discovered in the source."""

    name: str
    class_name: str
    file: str
    line: int
    key_param: str
    steps: list = field(default_factory=list)

    @property
    def qualified(self) -> str:
        return f"{self.class_name}.{self.name}"

    @property
    def key_as_default_steps(self):
        return [s for s in self.steps if s.default_is_key]

    def candidate_paths(self, key: str):
        seen = []
        for step in self.steps:
            if not step.applies_to(key):
                continue
            path = step.path_for(key)
            if path not in seen:
                seen.append(path)
        return seen


@dataclass
class KeyReference:
    """A literal used as a message key at a call site."""

    key: str
    helper: str            # qualified helper name, or "<direct>"
    file: str
    line: int
    candidates: tuple = ()
    resolved: str = ""     # the bundle path it actually binds to
    ambiguous: bool = False


def _find_body(source: str, open_brace: int):
    """Return (start, end) offsets of a block given the offset of its `{`."""
    depth = 0
    for i in range(open_brace, len(source)):
        c = source[i]
        if c == "{":
            depth += 1
        elif c == "}":
            depth -= 1
            if depth == 0:
                return open_brace + 1, i
    return open_brace + 1, len(source)


def _guard_blocks(masked_body: str):
    """Ranges of `if (... startsWith("X") ...) { ... }` blocks, with their prefixes."""
    blocks = []
    for m in re.finditer(r"\bif\s*\(", masked_body):
        open_paren = m.end() - 1
        depth = 0
        close = open_paren
        for i in range(open_paren, len(masked_body)):
            if masked_body[i] == "(":
                depth += 1
            elif masked_body[i] == ")":
                depth -= 1
                if depth == 0:
                    close = i
                    break
        condition = masked_body[open_paren:close + 1]
        prefixes = tuple(_STARTS_WITH.findall(condition))
        if not prefixes:
            continue
        rest = masked_body[close + 1:]
        brace = rest.find("{")
        if brace == -1 or rest[:brace].strip():
            continue
        start, end = _find_body(masked_body, close + 1 + brace)
        blocks.append((start, end, prefixes))
    return blocks


def discover_helpers(java_files, config) -> dict:
    """Scan every parsed Java file for message-lookup helper methods."""
    helpers: dict = {}
    # A helper does not have to touch getString itself. The wager GUI's
    # `t(key)` delegates to `getMessage("messages.gui." + key)`, so a lookup
    # step can also be a call to another localization method.
    accessors = set(config.bundle_accessors) | BUNDLE_ACCESSORS | set(config.localization_methods)

    for jf in java_files:
        cls = _CLASS_DECL.search(jf.masked)
        class_name = cls.group("name") if cls else jf.path.stem

        for m in _METHOD_DECL.finditer(jf.masked):
            params = m.group("params")
            key_param = _first_string_param(params)
            if not key_param:
                continue
            body_start, body_end = _find_body(jf.masked, m.end() - 1)
            steps = _extract_steps(jf, body_start, body_end, key_param, accessors, config)
            if not steps:
                continue
            helper = Helper(
                name=m.group("name"),
                class_name=class_name,
                file=jf.rel_path,
                line=jf.line_of(m.start()),
                key_param=key_param,
                steps=steps,
            )
            helpers[helper.qualified] = helper
    return helpers


def _first_string_param(params: str) -> str:
    for part in params.split(","):
        part = part.strip()
        if part.startswith("String ") or part.startswith("final String "):
            name = part.split()[-1]
            if not name.endswith("..."):
                return name
    return ""


def _extract_steps(jf, body_start, body_end, key_param, accessors, config):
    """Find `accessor(prefix + key)` calls inside a helper body."""
    steps = []
    # Comments are blanked but literals are intact, so `startsWith("help-")`
    # and `replace("help-", "")` stay readable while `//` noise cannot match.
    body = jf.no_comments[body_start:body_end]
    guards = _guard_blocks(body)

    # `String subKey = key.replace("help-", "");` -> subKey carries a strip.
    strips = {}
    for m in _REPLACE_ASSIGN.finditer(body):
        if m.group("new") == "" and (m.group("src") == key_param or m.group("src") in strips):
            strips[m.group("var")] = m.group("old")

    key_vars = {key_param} | set(strips)

    for lit in jf.literals:
        if not (body_start <= lit.start < body_end):
            continue
        if lit.enclosing_call not in accessors or lit.arg_index != 0:
            continue
        # A method assembling paths for config.yml is not a message helper.
        if not config.is_message_lookup(lit):
            continue
        stmt = jf.statement_of(lit)
        if stmt is None:
            continue

        # The literal must be concatenated with a key variable: getString("x." + key)
        after = jf.source[lit.end:lit.end + 80]
        cm = re.match(r"\s*\+\s*([A-Za-z_$][A-Za-z0-9_$]*)", after)
        if not cm or cm.group(1) not in key_vars:
            continue
        var = cm.group(1)

        rel = lit.start - body_start
        step_guards: tuple = ()
        for gstart, gend, prefixes in guards:
            if gstart <= rel < gend:
                step_guards = prefixes
                break

        steps.append(LookupStep(
            prefix=lit.text,
            strip_prefix=strips.get(var, ""),
            guards=step_guards,
            default_is_key=_default_is_key(jf, lit, var),
            line=lit.line,
        ))

    steps.sort(key=lambda s: s.line)
    return steps


def _default_is_key(jf, lit, key_var) -> bool:
    """True for `getString(path + key, key)` -- the default is the key itself."""
    tail = jf.source[lit.end:]
    depth = 1
    i = 0
    # walk to the matching ')' of the accessor call, collecting the argument list
    while i < len(tail) and depth > 0:
        c = tail[i]
        if c == "(":
            depth += 1
        elif c == ")":
            depth -= 1
        i += 1
    args = tail[:max(0, i - 1)]
    parts = _split_top_level(args)
    if len(parts) < 2:
        return False
    default = parts[-1].strip()
    return default == key_var


def _split_top_level(text: str):
    parts = []
    depth = 0
    current = []
    in_str = False
    escape = False
    for c in text:
        if in_str:
            current.append(c)
            if escape:
                escape = False
            elif c == "\\":
                escape = True
            elif c == '"':
                in_str = False
            continue
        if c == '"':
            in_str = True
            current.append(c)
        elif c in "([{":
            depth += 1
            current.append(c)
        elif c in ")]}":
            depth -= 1
            current.append(c)
        elif c == "," and depth == 0:
            parts.append("".join(current))
            current = []
        else:
            current.append(c)
    parts.append("".join(current))
    return parts


class KeyResolver:
    """Resolves call-site literals to bundle paths using discovered helpers."""

    def __init__(self, helpers: dict, master_keys: set, config):
        self.helpers = helpers
        self.master_keys = master_keys
        self.config = config
        self.by_name: dict = {}
        for helper in helpers.values():
            self.by_name.setdefault(helper.name, []).append(helper)

    def helpers_for(self, method: str, class_name: str):
        """Same-class helper wins; otherwise every helper with that method name."""
        local = self.helpers.get(f"{class_name}.{method}")
        if local:
            return [local]
        return list(self.by_name.get(method, []))

    def resolve(self, key: str, method: str, class_name: str, file: str, line: int) -> KeyReference:
        # A key already written as a full path is used verbatim.
        if any(key.startswith(p) for p in self.config.absolute_key_prefixes):
            resolved = key if key in self.master_keys else ""
            return KeyReference(key=key, helper="<direct>", file=file, line=line,
                                candidates=(key,), resolved=resolved)

        candidates: list = []
        helpers = self.helpers_for(method, class_name)
        for helper in helpers:
            for path in helper.candidate_paths(key):
                if path not in candidates:
                    candidates.append(path)

        helper_name = helpers[0].qualified if len(helpers) == 1 else (
            f"{len(helpers)} candidates" if helpers else "<unknown>"
        )

        if not helpers:
            # No helper found: fall back to the legacy class prefix map.
            prefix = self.config.class_prefix_map.get(file.split("/")[-1], "")
            if prefix:
                candidates = [f"{prefix}{key}"]
            candidates.append(key)

        resolved = ""
        for cand in candidates:
            if cand in self.master_keys:
                resolved = cand
                break

        return KeyReference(
            key=key, helper=helper_name, file=file, line=line,
            candidates=tuple(candidates), resolved=resolved,
            ambiguous=len(helpers) > 1,
        )
