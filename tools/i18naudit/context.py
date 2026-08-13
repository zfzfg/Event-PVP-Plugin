"""
Shared analysis context.

Everything expensive (parsing Java, loading bundles, discovering helpers,
resolving call sites) happens once here; the detectors are pure consumers.
"""

from __future__ import annotations

import re
from dataclasses import dataclass, field
from pathlib import Path

from . import bundles as bundles_mod
from . import javaparse
from . import webi18n
from .resolvers import KeyResolver, discover_helpers

_CLASS_DECL = re.compile(r"\b(?:class|enum|interface)\s+(?P<name>[A-Za-z_$][A-Za-z0-9_$]*)")
_IDENT = re.compile(r"^[A-Za-z_$][A-Za-z0-9_$.\-]*$")


@dataclass
class AuditContext:
    config: object
    java_files: list = field(default_factory=list)
    bundles: list = field(default_factory=list)
    master: object = None
    helpers: dict = field(default_factory=dict)
    resolver: object = None
    references: list = field(default_factory=list)   # KeyReference, resolved or not
    dynamic_keys: list = field(default_factory=list)
    web_files: list = field(default_factory=list)
    web_bundle: object = None          # web/lang/en.json, parsed - see webi18n.py
    web_bundles: list = field(default_factory=list)   # every web/lang/*.json, for parity
    web_scan: object = None            # WebScanResult: call sites, dynamic prefixes, ...
    class_names: dict = field(default_factory=dict)  # rel_path -> class name
    errors: list = field(default_factory=list)

    @property
    def master_keys(self) -> set:
        return self.master.keys if self.master else set()

    def bundle(self, name: str):
        for b in self.bundles:
            if b.name == name:
                return b
        return None

    @property
    def translation_bundles(self):
        """Every bundle that is actually loaded at runtime, minus the master."""
        legacy = set(self.config.legacy_bundles)
        return [b for b in self.bundles
                if b is not self.master and b.name not in legacy]


def build_context(config) -> AuditContext:
    ctx = AuditContext(config=config)

    # --- Java ---------------------------------------------------------------
    if config.java_dir.is_dir():
        for jf in javaparse.iter_java_files(config.java_dir, config.project_root):
            if config.path_ignored(jf.rel_path):
                continue
            ctx.java_files.append(jf)
            m = _CLASS_DECL.search(jf.masked)
            ctx.class_names[jf.rel_path] = m.group("name") if m else jf.path.stem
    else:
        ctx.errors.append(f"Java source directory not found: {config.java_dir}")

    # --- Bundles ------------------------------------------------------------
    if config.resources_dir.is_dir():
        ctx.bundles = bundles_mod.load_bundles(config.resources_dir, config.bundle_glob)
        ctx.master = ctx.bundle(config.master_bundle)
        if ctx.master is None:
            ctx.errors.append(f"Master bundle {config.master_bundle} not found in {config.resources_dir}")
    else:
        ctx.errors.append(f"Resources directory not found: {config.resources_dir}")

    # --- Web assets ---------------------------------------------------------
    if config.web_dir.is_dir():
        for path in sorted(config.web_dir.rglob("*")):
            if path.suffix.lower() in (".html", ".js", ".json"):
                ctx.web_files.append(path)

    # --- Web translation bundles (D10/D11) -----------------------------------
    if config.web_lang_path.is_dir():
        ctx.web_bundles = [
            webi18n.load_web_bundle(p) for p in sorted(config.web_lang_path.glob("*.json"))
        ]
        for b in ctx.web_bundles:
            if b.name == config.web_master_bundle:
                ctx.web_bundle = b
                break
        if ctx.web_bundle is None and ctx.web_bundles:
            ctx.errors.append(
                f"Web master bundle {config.web_master_bundle} not found in {config.web_lang_path}")

    if ctx.web_bundle is not None:
        source_paths = []
        for pattern in config.web_source_globs:
            source_paths.extend(sorted(config.web_dir.glob(pattern)))
        ctx.web_scan = webi18n.scan_web_sources(source_paths, config.project_root)

    # --- Helpers & call sites ----------------------------------------------
    ctx.helpers = discover_helpers(ctx.java_files, config)
    ctx.resolver = KeyResolver(ctx.helpers, ctx.master_keys, config)
    _collect_references(ctx)
    return ctx


def _collect_references(ctx: AuditContext) -> None:
    """Every literal used as the first argument of a message lookup."""
    config = ctx.config
    lookup_methods = set(config.localization_methods) | set(config.bundle_accessors)
    lookup_methods |= {h.name for h in ctx.helpers.values()}

    for jf in ctx.java_files:
        class_name = ctx.class_names.get(jf.rel_path, jf.path.stem)
        for lit in jf.literals:
            if lit.arg_index != 0 or lit.enclosing_call not in lookup_methods:
                continue
            if lit.line in jf.ignored_lines:
                continue
            # A bare getString may be reading config.yml rather than a bundle.
            if lit.enclosing_call in config.bundle_accessors \
                    and not config.is_message_lookup(lit):
                continue
            key = lit.text.strip()
            if not key or len(key) < 2 or key.endswith("."):
                continue
            if not _IDENT.match(key):
                # Not a key shape (a sentence, a colour code, a SQL fragment).
                continue
            if config.key_ignored(key):
                continue
            if _is_dynamic(jf, lit):
                ctx.dynamic_keys.append((key, jf.rel_path, lit.line))
                continue
            ref = ctx.resolver.resolve(key, lit.enclosing_call, class_name, jf.rel_path, lit.line)
            ctx.references.append(ref)


def _is_dynamic(jf, lit) -> bool:
    """`getMsg(prefix + key)` -- the literal is only part of the real key."""
    after = jf.source[lit.end:lit.end + 40].lstrip()
    if after.startswith("+"):
        return True
    before = jf.no_comments[max(0, lit.start - 40):lit.start].rstrip()
    return before.endswith("+")
