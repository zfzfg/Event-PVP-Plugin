"""
Audit configuration.

All heuristics that used to be hardcoded at the top of the two legacy scripts
live in `tools/i18n_audit_config.yml` now, so tuning the scanner is a reviewable
config change instead of a code edit.
"""

from __future__ import annotations

import re
from dataclasses import dataclass, field
from pathlib import Path

try:
    import yaml
except ImportError:  # pragma: no cover
    yaml = None

DEFAULT_CONFIG_NAME = "i18n_audit_config.yml"

SEVERITIES = ("critical", "warning", "info")

_DEFAULTS = {
    "roots": {
        "java": "src/main/java",
        "resources": "src/main/resources",
        "web": "src/main/resources/web",
    },
    "master_bundle": "messages_en.yml",
    "bundle_glob": "messages*.yml",
    "legacy_bundles": ["messages.yml"],
    "legacy_bundles_accepted": [],
    "absolute_key_prefixes": ["messages.", "settings."],
    "player_text_methods": [
        "sendMessage", "sendMessages", "send", "error", "success", "warn",
        "broadcast", "broadcastMessage", "sendTitle", "sendActionBar",
        "setDisplayName", "setLore", "setHeader", "setFooter", "setCustomName",
        "TextComponent", "ComponentBuilder", "createInventory", "kickPlayer",
    ],
    "localization_methods": [
        "getMsg", "getMessage", "getHelpMsg", "getDebugMsg",
        "getGeneralMsg", "getWebtokenMsg", "getLangMsg", "t", "tl",
    ],
    "bundle_accessors": ["getString", "getStringList"],
    # `getString` reads whatever configuration it is called on. Only a receiver
    # matching one of these patterns is a message bundle; everything else is
    # config.yml, equipment.yml, worlds.yml and must not be judged against the
    # language files.
    "message_receiver_patterns": ["message", "msg", "lang"],
    "logger_methods": [
        "getLogger", "log", "info", "warning", "severe", "fine", "finer", "config",
        "printStackTrace", "debug", "println", "print",
    ],
    "enum_display_getters": ["getDisplayName", "getDisplay", "getLabel", "getTitle"],
    "enum_translation_getters": ["getTranslationKey", "getMessageKey"],
    "class_prefix_map": {},
    # Bundle subtrees addressed through a runtime-built key. Their entries are
    # real and used, but no static reference points at them, so D9 must not
    # call them orphans.
    "dynamic_key_roots": [],
    # The web panel (web/lang/*.json + i18n.t() in app.js/editors.js/items.js/
    # index.html) is a second, independent translation system that D2/D9 never
    # looked at - see webi18n.py for why. D10/D11 are its mirror.
    "web": {
        "lang_dir": "lang",
        "master_bundle": "en.json",
        "source_globs": ["*.js", "*.html"],
        # A key that reaches i18n.t() only through the server's `messageKey`
        # field (WebApiHandler.failure(...) and friends) - no static scan can
        # ever find that call site, because the argument is a variable, not a
        # literal. Mirrors ignore.literal_prefixes below, which silences the
        # same three families on the Java side for the opposite reason (the
        # literal there is a key, not display text).
        "server_driven_prefixes": ["mv.error.", "inventory.error.", "items.error."],
        "ignore_keys": [],
    },
    "ignore": {
        "literal_prefixes": [],
        "literal_regex": [],
        "paths": [],
        "keys": [],
        "calls": [],
        "call_suffixes": [],
    },
    "detectors": {},
    "baseline": "",
}


@dataclass
class AuditConfig:
    project_root: Path
    raw: dict = field(default_factory=dict)

    # resolved fields
    roots: dict = field(default_factory=dict)
    master_bundle: str = "messages_en.yml"
    bundle_glob: str = "messages*.yml"
    legacy_bundles: list = field(default_factory=list)
    legacy_bundles_accepted: list = field(default_factory=list)
    absolute_key_prefixes: list = field(default_factory=list)
    player_text_methods: set = field(default_factory=set)
    localization_methods: set = field(default_factory=set)
    bundle_accessors: set = field(default_factory=set)
    message_receiver_patterns: list = field(default_factory=list)
    logger_methods: set = field(default_factory=set)
    enum_display_getters: set = field(default_factory=set)
    enum_translation_getters: set = field(default_factory=set)
    class_prefix_map: dict = field(default_factory=dict)
    dynamic_key_roots: list = field(default_factory=list)
    web_lang_dir: str = "lang"
    web_master_bundle: str = "en.json"
    web_source_globs: list = field(default_factory=list)
    web_server_driven_prefixes: list = field(default_factory=list)
    web_ignore_keys: list = field(default_factory=list)
    ignore_literal_prefixes: list = field(default_factory=list)
    ignore_literal_regex: list = field(default_factory=list)
    ignore_paths: list = field(default_factory=list)
    ignore_keys: list = field(default_factory=list)
    ignore_calls: set = field(default_factory=set)
    ignore_call_suffixes: tuple = ()
    detector_severity: dict = field(default_factory=dict)
    baseline_path: str = ""

    @property
    def java_dir(self) -> Path:
        return self.project_root / self.roots["java"]

    @property
    def resources_dir(self) -> Path:
        return self.project_root / self.roots["resources"]

    @property
    def web_dir(self) -> Path:
        return self.project_root / self.roots["web"]

    @property
    def web_lang_path(self) -> Path:
        return self.web_dir / self.web_lang_dir

    def web_key_ignored(self, key: str) -> bool:
        return any(frag in key for frag in self.web_ignore_keys)

    def path_ignored(self, rel_path: str) -> bool:
        return any(_glob_match(rel_path, pat) for pat in self.ignore_paths)

    def literal_ignored(self, text: str) -> bool:
        if any(text.startswith(p) for p in self.ignore_literal_prefixes):
            return True
        return any(rx.search(text) for rx in self.ignore_literal_regex)

    def key_ignored(self, key: str) -> bool:
        return any(frag in key for frag in self.ignore_keys)

    def is_message_lookup(self, literal) -> bool:
        """Does this literal name a message key, or a plain config path?

        `getString("settings.language")` on config.yml and
        `section.getString("helmet")` on equipment.yml look identical to a
        message lookup unless the receiver is taken into account.
        """
        call = literal.enclosing_call
        if call in self.localization_methods:
            return True
        if call not in self.bundle_accessors:
            return False
        receiver = (literal.receiver or "").lower()
        return any(pattern in receiver for pattern in self.message_receiver_patterns)

    def severity_for(self, detector: str, default: str) -> str:
        value = self.detector_severity.get(detector, default)
        return value if value in SEVERITIES or value == "off" else default


def _glob_match(path: str, pattern: str) -> bool:
    from fnmatch import fnmatch
    if fnmatch(path, pattern):
        return True
    # `**/x.java` should also match a bare `x.java`
    if pattern.startswith("**/") and fnmatch(path, pattern[3:]):
        return True
    return fnmatch(path.split("/")[-1], pattern)


def _merge(base: dict, override: dict) -> dict:
    out = dict(base)
    for k, v in (override or {}).items():
        if isinstance(v, dict) and isinstance(out.get(k), dict):
            out[k] = _merge(out[k], v)
        else:
            out[k] = v
    return out


def load_config(project_root: Path, config_path: Path = None) -> AuditConfig:
    data = dict(_DEFAULTS)
    if config_path is None:
        config_path = Path(__file__).resolve().parent.parent / DEFAULT_CONFIG_NAME
    if config_path.exists() and yaml is not None:
        loaded = yaml.safe_load(config_path.read_text(encoding="utf-8")) or {}
        data = _merge(_DEFAULTS, loaded)

    ignore = data.get("ignore") or {}
    web = data.get("web") or {}
    cfg = AuditConfig(
        project_root=project_root,
        raw=data,
        roots=data["roots"],
        master_bundle=data["master_bundle"],
        bundle_glob=data["bundle_glob"],
        legacy_bundles=list(data.get("legacy_bundles") or []),
        legacy_bundles_accepted=list(data.get("legacy_bundles_accepted") or []),
        absolute_key_prefixes=list(data["absolute_key_prefixes"]),
        player_text_methods=set(data["player_text_methods"]),
        localization_methods=set(data["localization_methods"]),
        bundle_accessors=set(data["bundle_accessors"]),
        message_receiver_patterns=[p.lower() for p in data["message_receiver_patterns"]],
        logger_methods=set(data["logger_methods"]),
        enum_display_getters=set(data["enum_display_getters"]),
        enum_translation_getters=set(data["enum_translation_getters"]),
        class_prefix_map=dict(data.get("class_prefix_map") or {}),
        dynamic_key_roots=list(data.get("dynamic_key_roots") or []),
        web_lang_dir=web.get("lang_dir", "lang"),
        web_master_bundle=web.get("master_bundle", "en.json"),
        web_source_globs=list(web.get("source_globs") or ["*.js", "*.html"]),
        web_server_driven_prefixes=list(web.get("server_driven_prefixes") or []),
        web_ignore_keys=list(web.get("ignore_keys") or []),
        ignore_literal_prefixes=list(ignore.get("literal_prefixes") or []),
        ignore_literal_regex=[re.compile(r) for r in (ignore.get("literal_regex") or [])],
        ignore_paths=list(ignore.get("paths") or []),
        ignore_keys=list(ignore.get("keys") or []),
        ignore_calls=set(ignore.get("calls") or []),
        ignore_call_suffixes=tuple(ignore.get("call_suffixes") or []),
        detector_severity=dict(data.get("detectors") or {}),
        baseline_path=data.get("baseline") or "",
    )
    return cfg


def find_project_root(start: Path) -> Path:
    """Walk up from `start` until a pom.xml is found.

    The legacy scripts defaulted `--project-root` to the *current working
    directory*, so running `tools\\run_scans.bat` from inside `tools/` scanned
    `tools/`, found no `src/main/java`, and cheerfully reported zero issues.
    """
    start = start.resolve()
    for candidate in [start, *start.parents]:
        if (candidate / "pom.xml").exists():
            return candidate
        if (candidate / "src" / "main" / "java").is_dir():
            return candidate
    return start
