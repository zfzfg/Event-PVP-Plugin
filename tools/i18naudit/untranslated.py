"""
Untranslated Values Auditor for Event-PVP-Plugin.

Identifies translation entries in non-English message bundles (messages_<lang>.yml)
that are byte-identical to the master messages_en.yml.

Not every identical value is a gap. A chat divider (`&6&l━━━━━━━`), a bare
placeholder (`{name}`), a command literal (`&e/pvp leave`) and a download URL
are supposed to read the same in every language -- "translating" them would
break the message. Reporting them buried the real gaps: of 66 entries listed
for German, roughly four in five were of this kind.

So the same rules D6/D7 already use to decide "this literal is not display
text" -- `ignore.literal_prefixes` and `ignore.literal_regex` in
`tools/i18n_audit_config.yml`, applied through `AuditConfig.literal_ignored()`
-- now gate this report too. Colour codes are stripped first, because they
otherwise defeat every anchored pattern: `&e/pvp leave` does not match a rule
written as `^/[A-Za-z]...`.

Generates `reports/untranslated_values.md` and provides structured data for
comprehensive quality audits.
"""

from __future__ import annotations

import collections
import re
import sys
from dataclasses import dataclass, field
from pathlib import Path
from typing import Generator

import yaml

from .config import load_config

# Same shape as detectors/hardcoded.COLOR_CODE. Duplicated rather than imported
# so this module keeps working standalone (`python tools/report_untranslated.py`)
# without pulling in the detector package.
_COLOR_CODE = re.compile(r"[&§][0-9a-fk-orA-FK-OR]")
_PLACEHOLDER = re.compile(r"\{[A-Za-z0-9_]+\}")
_HAS_LETTERS = re.compile(r"[^\W\d_]", re.UNICODE)


@dataclass
class LanguageUntranslated:
    language: str
    items: list[tuple[str, str]] = field(default_factory=list)

    @property
    def count(self) -> int:
        return len(self.items)

    @property
    def sections(self) -> collections.Counter:
        return collections.Counter(key.rsplit(".", 1)[0] for key, _ in self.items)

    def to_dict(self) -> dict:
        return {
            "language": self.language,
            "count": self.count,
            "sections": dict(self.sections),
            "items": [{"key": k, "value": v} for k, v in self.items],
        }


@dataclass
class UntranslatedResult:
    project_root: Path
    master_file: str
    languages: list[str]
    per_language: dict[str, LanguageUntranslated] = field(default_factory=dict)

    @property
    def total_untranslated(self) -> int:
        return sum(lang_data.count for lang_data in self.per_language.values())

    def to_dict(self) -> dict:
        return {
            "project_root": str(self.project_root).replace("\\", "/"),
            "master_file": self.master_file,
            "total_untranslated": self.total_untranslated,
            "languages": {
                lang: data.to_dict() for lang, data in self.per_language.items()
            },
        }


def flatten(node, prefix: str = "") -> Generator[tuple[str, str], None, None]:
    if isinstance(node, dict):
        for key, value in node.items():
            yield from flatten(value, f"{prefix}{key}.")
    elif isinstance(node, str):
        yield prefix[:-1], node


def load_yaml_bundle(file_path: Path) -> dict[str, str]:
    if not file_path.exists():
        return {}
    try:
        with file_path.open(encoding="utf-8") as handle:
            return dict(flatten(yaml.safe_load(handle) or {}))
    except Exception as e:
        print(f"Error reading YAML bundle {file_path}: {e}", file=sys.stderr)
        return {}


def value_is_language_neutral(value: str, config) -> bool:
    """Is an identical value expected to be identical, rather than untranslated?

    Dividers, placeholders, command literals and URLs carry no words to
    translate. `config.literal_ignored()` already encodes exactly that
    judgement for the Java detectors; reusing it keeps one set of rules instead
    of a second, drifting copy.
    """
    stripped = _COLOR_CODE.sub("", value).strip()
    if not stripped:
        return True

    # Nothing but placeholders, punctuation and layout once the colour codes
    # are gone: `  &8• &f{part}: {material}` or `&7- &f{item}`. There is no word
    # in there to translate, only the frame around values the code fills in.
    if not _HAS_LETTERS.search(_PLACEHOLDER.sub("", stripped)):
        return True

    if config is None:
        return False
    return config.literal_ignored(stripped)


def run_untranslated_check(
    project_root: Path | str | None = None, config=None
) -> UntranslatedResult:
    if project_root is None:
        root = Path(__file__).resolve().parent.parent.parent
    else:
        root = Path(project_root).resolve()

    # Standalone callers (`--only-untranslated`, report_untranslated.py, the
    # menu's option 6) pass no config; load the project's own so they filter
    # identically to the full suite.
    if config is None:
        try:
            config = load_config(root)
        except Exception:  # pragma: no cover -- no config: report unfiltered
            config = None

    resources_dir = root / "src" / "main" / "resources"
    master_name = "messages_en.yml"
    master_path = resources_dir / master_name

    master_values = load_yaml_bundle(master_path)

    languages = sorted(
        p.name[len("messages_"):-len(".yml")]
        for p in resources_dir.glob("messages_*.yml")
        if p.name != master_name
    )

    per_language: dict[str, LanguageUntranslated] = {}
    for lang in languages:
        bundle_path = resources_dir / f"messages_{lang}.yml"
        values = load_yaml_bundle(bundle_path)
        identical = sorted(
            (key, value)
            for key, value in values.items()
            if key in master_values
            and value == master_values[key]
            and not value_is_language_neutral(value, config)
        )
        per_language[lang] = LanguageUntranslated(language=lang, items=identical)

    return UntranslatedResult(
        project_root=root,
        master_file=master_name,
        languages=languages,
        per_language=per_language,
    )


def generate_markdown(result: UntranslatedResult) -> str:
    lines = [
        f"# Untranslated values (byte-identical to {result.master_file})",
        "",
        "| Sprache | offen |",
        "|---|---|",
    ]
    for lang in result.languages:
        lines.append(f"| {lang} | {result.per_language[lang].count} |")

    for lang in result.languages:
        lang_data = result.per_language[lang]
        lines += ["", f"## {lang} ({lang_data.count})", ""]
        for section, count in lang_data.sections.most_common():
            lines.append(f"- `{section}`: {count}")
        lines.append("")
        for key, value in lang_data.items:
            shown = value.replace("\n", " / ")
            if len(shown) > 90:
                shown = shown[:90] + "..."
            lines.append(f"- `{key}` = '{shown}'")

    return "\n".join(lines) + "\n"


def write_markdown_report(
    result: UntranslatedResult, output_path: Path | None = None
) -> Path:
    if output_path is None:
        target = result.project_root / "reports" / "untranslated_values.md"
    else:
        target = Path(output_path)
        if not target.is_absolute():
            target = result.project_root / target

    target.parent.mkdir(parents=True, exist_ok=True)
    content = generate_markdown(result)
    target.write_text(content, encoding="utf-8")
    return target


def print_untranslated_summary(result: UntranslatedResult) -> None:
    parts = [f"{lang} {result.per_language[lang].count}" for lang in result.languages]
    print(f"Untranslated values summary: {', '.join(parts)}")


def main(argv: list[str] | None = None, project_root: str | None = None) -> int:
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    if hasattr(sys.stderr, "reconfigure"):
        sys.stderr.reconfigure(encoding="utf-8", errors="replace")

    root = Path(project_root) if project_root else None
    result = run_untranslated_check(root)
    out_file = write_markdown_report(result)
    rel_out = out_file.relative_to(result.project_root) if out_file.is_relative_to(result.project_root) else out_file
    parts = [f"{lang} {result.per_language[lang].count}" for lang in result.languages]
    print(f"{rel_out}: {', '.join(parts)}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
