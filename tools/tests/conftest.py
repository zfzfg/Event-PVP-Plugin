"""
Test fixtures: a synthetic mini-project per test.

Each test builds a throwaway project tree (src/main/java + src/main/resources)
so a detector can be exercised against source it fully controls. Fixtures use
the same shapes as the real plugin, including the exact bugs that shipped.
"""

from __future__ import annotations

import sys
from pathlib import Path

import pytest

TOOLS_DIR = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(TOOLS_DIR))

from i18naudit import detectors  # noqa: E402
from i18naudit.config import load_config  # noqa: E402
from i18naudit.context import build_context  # noqa: E402


class Project:
    """A synthetic project the detectors can be pointed at."""

    def __init__(self, root: Path):
        self.root = root
        self.java_dir = root / "src" / "main" / "java"
        self.res_dir = root / "src" / "main" / "resources"
        self.java_dir.mkdir(parents=True, exist_ok=True)
        self.res_dir.mkdir(parents=True, exist_ok=True)
        (root / "pom.xml").write_text("<project/>", encoding="utf-8")

    def java(self, name: str, source: str) -> "Project":
        (self.java_dir / name).write_text(source, encoding="utf-8")
        return self

    def bundle(self, name: str, body: str) -> "Project":
        (self.res_dir / name).write_text(body, encoding="utf-8")
        return self

    def web(self, name: str, body: str) -> "Project":
        web_dir = self.res_dir / "web"
        web_dir.mkdir(parents=True, exist_ok=True)
        (web_dir / name).write_text(body, encoding="utf-8")
        return self

    def weblang(self, name: str, body: str) -> "Project":
        lang_dir = self.res_dir / "web" / "lang"
        lang_dir.mkdir(parents=True, exist_ok=True)
        (lang_dir / name).write_text(body, encoding="utf-8")
        return self

    def run(self, only=None, accepted_legacy=None):
        config = load_config(self.root, TOOLS_DIR / "i18n_audit_config.yml")
        if accepted_legacy is not None:
            # Let a test see the project *before* the product decision was taken.
            config.legacy_bundles_accepted = list(accepted_legacy)
        ctx = build_context(config)
        selected = {only} if isinstance(only, str) else (set(only) if only else None)
        return ctx, detectors.run(ctx, only=selected)

    def findings(self, only=None, accepted_legacy=None):
        return self.run(only, accepted_legacy)[1]


@pytest.fixture
def project(tmp_path):
    return Project(tmp_path)


@pytest.fixture
def english_bundle():
    """A minimal but realistic messages_en.yml."""
    return (
        "messages:\n"
        "  debug:\n"
        "    help:\n"
        "      header: '&bDebug Commands'\n"
        "    messages:\n"
        "      status-label: '&7Status: {status}'\n"
        "      level-label: '&7Level: &e{level}'\n"
        "  general:\n"
        "    prefix: '&8[&bPvP&8]&r '\n"
    )
