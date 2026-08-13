"""Finding model shared by every detector."""

from __future__ import annotations

import hashlib
from dataclasses import dataclass, field

SEVERITY_ORDER = {"critical": 0, "warning": 1, "info": 2}


@dataclass
class Finding:
    detector: str          # "D1" ... "D9"
    severity: str          # critical | warning | info
    title: str             # short rule name
    message: str           # what is wrong, in one sentence
    file: str = ""
    line: int = 0
    key: str = ""
    literal: str = ""
    snippet: str = ""
    hint: str = ""         # what to do about it
    extra: dict = field(default_factory=dict)

    @property
    def fingerprint(self) -> str:
        """Position-independent identity, so a baseline survives line shifts."""
        basis = "|".join([self.detector, self.file, self.key, self.literal[:120], self.title])
        return hashlib.sha1(basis.encode("utf-8")).hexdigest()[:16]

    @property
    def location(self) -> str:
        return f"{self.file}:{self.line}" if self.file else "-"

    def sort_key(self):
        return (SEVERITY_ORDER.get(self.severity, 9), self.detector, self.file, self.line)

    def to_dict(self) -> dict:
        return {
            "detector": self.detector,
            "severity": self.severity,
            "title": self.title,
            "message": self.message,
            "file": self.file,
            "line": self.line,
            "key": self.key,
            "literal": self.literal,
            "snippet": self.snippet,
            "hint": self.hint,
            "fingerprint": self.fingerprint,
            **({"extra": self.extra} if self.extra else {}),
        }
