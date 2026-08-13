"""
Detector registry.

Each detector is a callable `(ctx) -> list[Finding]` registered under a stable
id (D1..D11) so it can be filtered with `--only` and re-graded via the config.
"""

from __future__ import annotations

from . import hardcoded, keys, webkeys, yamlcheck

# id -> (title, function, default severity)
REGISTRY = {
    "D1": ("key-as-default", keys.detect_key_as_default, "critical"),
    "D2": ("missing-key", keys.detect_missing_keys, "critical"),
    "D3": ("yaml-boolean-key", yamlcheck.detect_boolean_keys, "critical"),
    "D4": ("placeholder-mismatch", yamlcheck.detect_placeholder_mismatch, "warning"),
    "D5": ("untranslatable-display-name", hardcoded.detect_display_names, "critical"),
    "D6": ("hardcoded-message", hardcoded.detect_hardcoded_messages, "critical"),
    "D7": ("natural-language-literal", hardcoded.detect_natural_language, "warning"),
    "D8": ("bundle-parity", yamlcheck.detect_bundle_parity, "warning"),
    "D9": ("unused-key", keys.detect_unused_keys, "warning"),
    # The web panel (web/lang/*.json) is a second, independent translation
    # system that D1-D9 never look at - see detectors/webkeys.py.
    "D10": ("web-missing-key", webkeys.detect_web_missing_keys, "critical"),
    "D11": ("web-unused-key", webkeys.detect_web_unused_keys, "warning"),
}

ALL_IDS = tuple(REGISTRY)


def run(ctx, only=None):
    """Run the selected detectors and return all findings."""
    selected = [d for d in ALL_IDS if not only or d in only]
    results = []
    for det_id in selected:
        title, func, default_severity = REGISTRY[det_id]
        severity = ctx.config.severity_for(det_id, default_severity)
        if severity == "off":
            continue
        for finding in func(ctx):
            finding.detector = det_id
            finding.title = finding.title or title
            # Config may downgrade a whole rule; a detector may still mark an
            # individual finding as less severe than the rule default.
            if severity != default_severity:
                finding.severity = severity
            results.append(finding)
    return results
