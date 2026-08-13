# Localization & Quality Audit Suite (`i18naudit`)

A unified, modular tool suite for verifying localization integrity, console/logger language standards, and translation parity across the entire Event-PVP plugin.

## Core Features

- **Full Suite by Default**: Running `python tools/i18n_audit.py` performs a complete scan across:
  1. **i18n Rule Detectors (D1–D11)** (key resolution, YAML syntax, placeholders, hardcoded player text, unused keys — D1-D9 for `messages_*.yml`/Java, D10-D11 for the web panel's own `web/lang/*.json`/`i18n.t()`)
  2. **Console & Logger Language Audit** (verifies that all console, logger, and terminal outputs are standardized in English)
  3. **Untranslated Values Analysis** (identifies entries identical to `messages_en.yml`)
  4. **Self-Tests** (`tools/tests`, run as a fourth component so "Full Suite" also verifies the *audit tool itself*, not only the plugin — see `i18naudit/selftest.py`)
- **Flag-Based Granular Execution**: Any check, detector, or report can be executed individually using command-line flags.
- **Modular Architecture**: Clean multi-file Python package structure (`i18naudit/`).
- **Granular Test Suite**: Every detector group and component has its own dedicated pytest module under `tools/tests/`.
- **Interactive Batch Menu**: `tools\run_scans.bat` provides a Windows menu for all tools, scans, exports, and test runs.

---

## Quick Start & CLI Flags

```bash
# Standard: Run the complete Full Suite (all 4 scan areas with terminal dashboard)
python tools/i18n_audit.py

# Export full reports (Markdown & JSON into reports/, incl. self-test results)
python tools/i18n_audit.py --export-markdown --export-json

# Run only individual scan components
python tools/i18n_audit.py --only-console           # Only Console & Logger language check
python tools/i18n_audit.py --only-untranslated      # Only Untranslated values analysis
python tools/i18n_audit.py --only-selftest          # Only the tools/tests pytest suite
python tools/i18n_audit.py --only-i18n              # Only i18n rule detectors (D1-D11)

# Filter specific i18n detectors or severity levels
python tools/i18n_audit.py --only-i18n --only D1,D2,D6
python tools/i18n_audit.py --severity critical       # Only player-visible breakage

# Helper inspection & CI quality gate
python tools/i18n_audit.py --list-helpers           # Show discovered message helpers & chains
python tools/i18n_audit.py --strict --fail-on critical # Exit 1 on critical findings
python tools/i18n_audit.py --write-baseline         # Freeze accepted debt to baseline JSON
```

---

## Rule Reference (i18n Detectors D1–D11)

D1-D9 audit `messages_*.yml` + Java (`getMsg()` and friends). D10-D11 audit the web
panel's own, independent bundle — `web/lang/*.json` + `i18n.t()` in
`app.js`/`editors.js`/`items.js`/`index.html` — which D1-D9 never look at. See
`i18naudit/webi18n.py` for why that needed its own scan rather than reusing the
Java-side machinery.

| Rule | Name | What it catches | Severity Default |
|---|---|---|---|
| **D1** | `key-as-default` | A helper ending in `getString(path + key, key)` — a missing key is rendered to the player as its own raw path. | `critical` |
| **D2** | `missing-key` | A message key requested in Java code that no lookup step of its helper resolves. | `critical` |
| **D3** | `yaml-boolean-key` | An unquoted `on:` / `off:` / `yes:` / `no:` key. YAML 1.1 stores it as a boolean, breaking lookups. | `critical` |
| **D4** | `placeholder-mismatch` | `{x}` substituted in code but absent from template, or placeholders missing from translations. | `warning` |
| **D5** | `untranslatable-display-name` | Display prose baked into enum constants rather than localized dynamically. | `critical` |
| **D6** | `hardcoded-message` | Literal handed straight to `sendMessage`, `sendTitle`, `createInventory`, `getLogger()` etc. | `critical` |
| **D7** | `natural-language-literal` | Prose outside recognized sinks, plus unlocalized text in web assets. | `warning` |
| **D8** | `bundle-parity` | Missing keys, extra keys, empty or `TODO` values across language files (incl. `web/lang/*.json` vs `en.json`). | `critical` / `warning` |
| **D9** | `unused-key` | A `messages_*.yml` key that nothing reads in Java or web assets. | `info` |
| **D10** | `web-missing-key` | `i18n.t('literal.key')` in the web panel where no `web/lang/*.json` defines that key — `i18n.t()` falls back to showing the raw key text. | `critical` |
| **D11** | `web-unused-key` | A `web/lang/*.json` key that nothing in the web panel reads. | `warning` |

---

## Project Structure

```
tools/
├── i18naudit/                      # Unified Python Package
│   ├── __init__.py                 # Package version & exports
│   ├── cli.py                      # Central CLI & flag dispatcher
│   ├── console.py                  # Console & Logger language auditor
│   ├── untranslated.py             # Untranslated values analyzer
│   ├── selftest.py                 # Runs tools/tests as a Full Suite component
│   ├── config.py                   # Configuration loader (i18n_audit_config.yml)
│   ├── context.py                  # AST/Lexing context builder
│   ├── bundles.py                  # YAML bundle parser & flattener
│   ├── findings.py                 # Finding data model & severity ranking
│   ├── javaparse.py                # Java tokenizer & call-site analyzer
│   ├── resolvers.py                # Helper chain & prefix resolver
│   ├── report.py                   # Terminal dashboard & Markdown/JSON exporters
│   ├── webi18n.py                  # Web panel bundle loader & i18n.t() call-site scanner
│   └── detectors/                  # Modular rule detector implementations
│       ├── keys.py                 # D1, D2, D9
│       ├── yamlcheck.py            # D3, D4, D8
│       ├── hardcoded.py            # D5, D6, D7
│       └── webkeys.py              # D10, D11 (web panel mirror of D2/D9)
│
├── i18n_audit.py                   # Central CLI entry point
├── check_console_messages.py       # Wrapper -> i18n_audit.py --only-console
├── report_untranslated.py          # Wrapper -> i18n_audit.py --only-untranslated
├── scan_hardcoded_messages.py      # Legacy wrapper -> i18n_audit.py --only D5,D6,D7,D8
├── verify_key_usage.py             # Legacy wrapper -> i18n_audit.py --only D1,D2,D4,D9
├── i18n_audit_config.yml           # Versioned audit configuration
├── i18n_audit_baseline.json        # Accepted baseline findings
├── run_scans.bat                   # Interactive Windows batch menu
│
└── tests/                          # Modular Pytest Suite
    ├── conftest.py                 # Shared fixtures (synthetic projects, bundle mocks)
    ├── test_detectors_keys.py      # D1, D2, D9 tests
    ├── test_detectors_yaml.py      # D3, D4, D8 tests
    ├── test_detectors_hardcoded.py # D5, D6, D7 tests
    ├── test_detectors_webkeys.py   # D10, D11 tests
    ├── test_console_check.py       # Console & Logger checker tests
    ├── test_untranslated.py        # Untranslated values tests
    └── test_cli.py                 # CLI flags, scopes, and dispatcher tests
```

---

## Running Automated Tests

The test suite is modularized so tests can be run in bulk or individually per component:

```bash
# Run all tests (76 tests across all modules)
python -m pytest tools/tests -v

# Run individual test files
python -m pytest tools/tests/test_detectors_keys.py -v       # D1, D2, D9
python -m pytest tools/tests/test_detectors_yaml.py -v       # D3, D4, D8
python -m pytest tools/tests/test_detectors_hardcoded.py -v  # D5, D6, D7
python -m pytest tools/tests/test_detectors_webkeys.py -v    # D10, D11
python -m pytest tools/tests/test_console_check.py -v       # Console checker
python -m pytest tools/tests/test_untranslated.py -v        # Untranslated analyzer
python -m pytest tools/tests/test_cli.py -v                 # CLI & dispatching

# Run a specific test function by name
python -m pytest tools/tests/test_detectors_keys.py -k test_d1_flags_helper_that_returns_the_key -v
```

---

## Suppressing Findings

In Java source code:
```java
p.sendMessage("&cinternal diagnostic");  // i18n-ignore
// i18n-ignore-next
p.sendMessage("&cinternal diagnostic");
```

Project-wide heuristics and ignore rules live in `tools/i18n_audit_config.yml` under `ignore:` (`literal_prefixes`, `literal_regex`, `paths`, `keys`, `calls`).
