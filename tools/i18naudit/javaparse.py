"""
Java source pre-processing.

The legacy scanners matched regexes against raw physical lines, which produced
both false positives (German text inside comments) and false negatives (a
message split across several lines loses its `sendMessage` context).

This module does a single character-level pass per file that:
  1. records every string literal with its exact position,
  2. masks comments and string bodies so later regexes cannot see into them,
  3. groups physical lines into logical statements,
  4. determines the enclosing call and argument index of every literal.

It is deliberately not a full Java parser -- it is a lexer that knows about
comments, strings, text blocks, chars and brackets. That is enough to answer
"which method is this literal an argument of?" reliably.
"""

from __future__ import annotations

import re
from dataclasses import dataclass, field
from pathlib import Path
from typing import Iterator

# Marker comments authors can put in the source to silence a finding.
IGNORE_LINE = re.compile(r"//\s*i18n-ignore\b")
IGNORE_NEXT = re.compile(r"//\s*i18n-ignore-next\b")

_IDENT_TAIL = re.compile(r"([A-Za-z_$][A-Za-z0-9_$]*)\s*$")

# `if (...)` is a bracket group, not a method call.
_CONTROL_KEYWORDS = {"if", "while", "for", "switch", "catch", "synchronized", "return", "new"}


@dataclass
class Literal:
    """A string literal found in Java source."""

    text: str                 # decoded content, without the surrounding quotes
    raw: str                  # source text as written, without the quotes
    line: int                 # 1-based line of the opening quote
    col: int                  # 0-based column of the opening quote
    start: int                # offset of the opening quote in the file
    end: int                  # offset just past the closing quote
    enclosing_call: str = ""  # method name this literal is an argument of
    receiver: str = ""        # `foo` in `foo.bar("x")`, best effort
    arg_index: int = -1       # 0-based argument position, -1 if not in a call
    call_chain: tuple = ()     # every enclosing call from innermost outwards
    statement_index: int = -1  # index into JavaFile.statements
    text_block: bool = False

    @property
    def qualified_call(self) -> str:
        return f"{self.receiver}.{self.enclosing_call}" if self.receiver else self.enclosing_call


@dataclass
class Statement:
    """A logical statement: source joined until `;` at bracket depth 0."""

    index: int
    line: int          # 1-based line the statement starts on
    end_line: int
    code: str          # masked source (comments/strings blanked), whitespace collapsed
    raw: str           # original source text of the statement


@dataclass
class JavaFile:
    path: Path
    rel_path: str
    source: str
    masked: str                          # comments + literal bodies replaced by spaces
    no_comments: str = ""                # comments blanked, literals intact
    lines: list = field(default_factory=list)
    literals: list = field(default_factory=list)
    statements: list = field(default_factory=list)
    ignored_lines: set = field(default_factory=set)

    def line_of(self, offset: int) -> int:
        # binary search over line start offsets
        lo, hi = 0, len(self._line_starts) - 1
        while lo < hi:
            mid = (lo + hi + 1) // 2
            if self._line_starts[mid] <= offset:
                lo = mid
            else:
                hi = mid - 1
        return lo + 1

    def literals_in_call(self, *names: str) -> Iterator[Literal]:
        wanted = set(names)
        for lit in self.literals:
            if lit.enclosing_call in wanted:
                yield lit

    def statement_of(self, lit: Literal):
        if 0 <= lit.statement_index < len(self.statements):
            return self.statements[lit.statement_index]
        return None


def _decode(raw: str) -> str:
    """Resolve the escape sequences that matter for message text."""
    out = []
    i = 0
    while i < len(raw):
        c = raw[i]
        if c == "\\" and i + 1 < len(raw):
            nxt = raw[i + 1]
            mapping = {"n": "\n", "t": "\t", "r": "\r", "\\": "\\", '"': '"', "'": "'", "0": "\0"}
            if nxt in mapping:
                out.append(mapping[nxt])
                i += 2
                continue
            if nxt == "u":
                hexpart = raw[i + 2:i + 6]
                if len(hexpart) == 4:
                    try:
                        out.append(chr(int(hexpart, 16)))
                        i += 6
                        continue
                    except ValueError:
                        pass
            out.append(nxt)
            i += 2
            continue
        out.append(c)
        i += 1
    return "".join(out)


def parse_java(path: Path, root: Path) -> JavaFile:
    source = path.read_text(encoding="utf-8", errors="replace")
    return parse_java_source(source, path, root)


def parse_java_source(source: str, path: Path, root: Path) -> JavaFile:
    try:
        rel = str(path.relative_to(root)).replace("\\", "/")
    except ValueError:
        rel = str(path).replace("\\", "/")

    masked = list(source)
    nocom = list(source)
    literals: list = []
    n = len(source)

    line_starts = [0]
    for i, ch in enumerate(source):
        if ch == "\n":
            line_starts.append(i + 1)

    def line_at(off: int) -> int:
        lo, hi = 0, len(line_starts) - 1
        while lo < hi:
            mid = (lo + hi + 1) // 2
            if line_starts[mid] <= off:
                lo = mid
            else:
                hi = mid - 1
        return lo + 1

    i = 0
    while i < n:
        c = source[i]

        # line comment
        if c == "/" and i + 1 < n and source[i + 1] == "/":
            j = source.find("\n", i)
            j = n if j == -1 else j
            for k in range(i, j):
                masked[k] = " "
                nocom[k] = " "
            i = j
            continue

        # block comment
        if c == "/" and i + 1 < n and source[i + 1] == "*":
            j = source.find("*/", i + 2)
            j = n if j == -1 else j + 2
            for k in range(i, j):
                if source[k] != "\n":
                    masked[k] = " "
                    nocom[k] = " "
            i = j
            continue

        # text block
        if c == '"' and source.startswith('"""', i):
            j = source.find('"""', i + 3)
            j = n if j == -1 else j
            raw = source[i + 3:j]
            literals.append(Literal(
                text=_decode(raw), raw=raw, line=line_at(i), col=i - line_starts[line_at(i) - 1],
                start=i, end=min(j + 3, n), text_block=True,
            ))
            for k in range(i, min(j + 3, n)):
                if source[k] != "\n":
                    masked[k] = " "
            i = min(j + 3, n)
            continue

        # string literal
        if c == '"':
            j = i + 1
            while j < n:
                if source[j] == "\\":
                    j += 2
                    continue
                if source[j] == '"' or source[j] == "\n":
                    break
                j += 1
            raw = source[i + 1:j]
            ln = line_at(i)
            literals.append(Literal(
                text=_decode(raw), raw=raw, line=ln, col=i - line_starts[ln - 1],
                start=i, end=min(j + 1, n),
            ))
            for k in range(i + 1, min(j, n)):
                masked[k] = " "
            i = min(j + 1, n)
            continue

        # char literal
        if c == "'":
            j = i + 1
            while j < n:
                if source[j] == "\\":
                    j += 2
                    continue
                if source[j] == "'" or source[j] == "\n":
                    break
                j += 1
            for k in range(i + 1, min(j, n)):
                masked[k] = " "
            i = min(j + 1, n)
            continue

        i += 1

    jf = JavaFile(
        path=path,
        rel_path=rel,
        source=source,
        masked="".join(masked),
        no_comments="".join(nocom),
        lines=source.splitlines(),
        literals=literals,
    )
    jf._line_starts = line_starts  # type: ignore[attr-defined]

    _attach_calls(jf)
    _build_statements(jf)
    _collect_ignores(jf)
    return jf


class _Frame:
    __slots__ = ("name", "receiver", "arg_index", "is_call")

    def __init__(self, name: str, receiver: str, is_call: bool):
        self.name = name
        self.receiver = receiver
        self.arg_index = 0
        self.is_call = is_call


def _attach_calls(jf: JavaFile) -> None:
    """Single left-to-right pass over the masked source, tracking the bracket stack.

    When a literal's opening quote is reached, the innermost call frame on the
    stack is its enclosing call and that frame's comma count is its argument index.
    """
    masked = jf.masked
    by_start = {lit.start: lit for lit in jf.literals}
    stack: list = []

    i = 0
    n = len(masked)
    while i < n:
        lit = by_start.get(i)
        if lit is not None:
            call_frames = [f for f in stack if f.is_call]
            if call_frames:
                inner = call_frames[-1]
                lit.enclosing_call = inner.name
                lit.receiver = inner.receiver
                lit.arg_index = inner.arg_index
                lit.call_chain = tuple(f.name for f in reversed(call_frames) if f.name)
            i = lit.end
            continue

        ch = masked[i]
        if ch == "(":
            name, receiver = _identifier_before(masked, i)
            stack.append(_Frame(name, receiver, bool(name)))
        elif ch in "[{":
            stack.append(_Frame("", "", False))
        elif ch in ")]}":
            if stack:
                stack.pop()
        elif ch == ",":
            if stack and stack[-1].is_call:
                stack[-1].arg_index += 1
        i += 1


def _identifier_before(masked: str, paren_pos: int):
    """Return (method_name, receiver) for the `(` at paren_pos."""
    head = masked[:paren_pos]
    m = _IDENT_TAIL.search(head)
    if not m:
        return "", ""
    name = m.group(1)
    if name in _CONTROL_KEYWORDS:
        return "", ""
    before = head[:m.start(1)].rstrip()
    receiver = ""
    if before.endswith("."):
        head2 = before[:-1].rstrip()
        # `getMessages().getString(...)` -- step over the receiver's own call so
        # the receiver reads as `getMessages`, not as an empty string. Knowing
        # which configuration a `getString` reads is what separates a message
        # lookup from a plain config.yml read.
        if head2.endswith(")"):
            depth = 0
            for i in range(len(head2) - 1, -1, -1):
                if head2[i] == ")":
                    depth += 1
                elif head2[i] == "(":
                    depth -= 1
                    if depth == 0:
                        head2 = head2[:i]
                        break
        m2 = _IDENT_TAIL.search(head2)
        if m2:
            receiver = m2.group(1)
    # `new TextComponent("...")` keeps `TextComponent` as the call name.
    return name, receiver


def _build_statements(jf: JavaFile) -> None:
    """Split the masked source into logical statements at `;`/`{`/`}` on depth 0."""
    masked = jf.masked
    source = jf.source
    depth = 0
    start = 0
    statements: list = []
    bounds: list = []

    def flush(end: int):
        nonlocal start
        raw = source[start:end]
        if raw.strip():
            code = " ".join(masked[start:end].split())
            ln = jf.line_of(start + (len(raw) - len(raw.lstrip())))
            statements.append(Statement(
                index=len(statements), line=ln, end_line=jf.line_of(max(start, end - 1)),
                code=code, raw=raw,
            ))
            bounds.append((start, end, statements[-1].index))
        start = end

    for i, ch in enumerate(masked):
        if ch in "([":
            depth += 1
        elif ch in ")]":
            depth = max(0, depth - 1)
        elif depth == 0 and ch in ";{}":
            flush(i + 1)
    flush(len(masked))

    jf.statements = statements

    for lit in jf.literals:
        for lo, hi, idx in bounds:
            if lo <= lit.start < hi:
                lit.statement_index = idx
                break


def _collect_ignores(jf: JavaFile) -> None:
    """Mark the lines a `// i18n-ignore` silences.

    The marker covers the whole *logical statement*, not just the physical line
    it sits on. A message built by concatenation puts its later fragments on
    continuation lines:

        logger.warning("Could not back up equipment.yml ("  // i18n-ignore: ...
                + e.getMessage() + ") - keeping the file unchanged.");

    Line-only matching silenced the first fragment and reported the second,
    which is the same sink, the same author decision and the same reason. There
    is no place to put a second marker either -- a `//` comment inside the
    argument list would comment out the rest of the call.

    Widening to the statement is safe because `_build_statements` splits on
    `;{}` at bracket depth 0: a statement never spans a block body, so a marker
    can never silence anything but the call it was written for.
    """
    marked = set()
    for idx, line in enumerate(jf.lines, 1):
        if IGNORE_NEXT.search(line):
            marked.add(idx + 1)
        elif IGNORE_LINE.search(line):
            marked.add(idx)

    jf.ignored_lines.update(marked)
    for stmt in jf.statements:
        if any(stmt.line <= idx <= stmt.end_line for idx in marked):
            jf.ignored_lines.update(range(stmt.line, stmt.end_line + 1))


def iter_java_files(java_root: Path, project_root: Path) -> Iterator[JavaFile]:
    for path in sorted(java_root.rglob("*.java")):
        yield parse_java(path, project_root)
