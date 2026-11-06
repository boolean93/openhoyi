#!/usr/bin/env python3
"""Classify pull request changes for the fast, non-AVD verify gate.

The classifier is deliberately fail-closed: only a strict site/docs allowlist
may use the fast checks. Documentation fast checks only cover Markdown files
under ``docs/`` (never ``docs/releases/``) plus the root README.md/AGENTS.md;
docs scripts, JSON and other assets stay on the full path. Paths are classified
exactly as ``git -z`` reports them: no stripping or separator rewriting, and
non-canonical/control-character paths are treated as unknown. Everything else,
including an empty/unknown diff, shallow history, missing commits or malformed
git output, falls back to the complete non-AVD verification path.
"""
from __future__ import annotations

import argparse
import json
import os
import re
import subprocess
import sys
from dataclasses import dataclass
from pathlib import Path

DOCS_PREFIXES = ("docs/",)
DOCS_MARKDOWN_SUFFIX = ".md"
# Release records carry approval fields; even Markdown changes there stay full.
DOCS_EXCLUDED_PREFIXES = ("docs/releases/",)
DOCS_FILES = frozenset({"README.md", "AGENTS.md"})
SITE_PREFIXES = ("site/",)
SITE_FILES = frozenset({"scripts/test_product_pages.py"})

_CONTROL_CHARS = re.compile(r"[\x00-\x1f\x7f]")


class ScopeError(Exception):
    """The diff cannot be classified safely."""


@dataclass(frozen=True)
class Scope:
    mode: str
    site: bool = False
    docs: bool = False
    reason: str = ""


def is_canonical_path(path: str) -> bool:
    """Return True only for a plain repository-relative path.

    ``git diff -z`` already reports real paths, so nothing is rewritten here.
    Leading/trailing whitespace, backslashes, control characters, absolute
    paths, trailing slashes and ``.``/``..``/empty segments are not canonical
    and must stay outside the fast allowlist.
    """
    value = str(path)
    if not value or value != value.strip():
        return False
    if "\\" in value or _CONTROL_CHARS.search(value):
        return False
    if any("\ud800" <= char <= "\udfff" for char in value):
        return False  # undecodable byte from git -z, not a valid UTF-8 path
    if value.startswith("/") or value.endswith("/"):
        return False
    return all(segment not in ("", ".", "..") for segment in value.split("/"))


def normalize_path(path: str) -> str:
    """Return the path unchanged when canonical, else an empty string.

    No stripping, separator rewriting or ``./`` trimming is performed.
    Callers must treat an empty result as unknown, never as allowlisted.
    """
    value = str(path)
    return value if is_canonical_path(value) else ""


def is_docs(path: str) -> bool:
    value = normalize_path(path)
    if not value:
        return False
    if value in DOCS_FILES:
        return True
    if not value.startswith(DOCS_PREFIXES) or not value.endswith(DOCS_MARKDOWN_SUFFIX):
        return False
    return not any(value.startswith(prefix) for prefix in DOCS_EXCLUDED_PREFIXES)


def is_site(path: str) -> bool:
    value = normalize_path(path)
    if not value:
        return False
    return value in SITE_FILES or any(value.startswith(prefix) for prefix in SITE_PREFIXES)


def classify_paths(paths: list[str]) -> Scope:
    """Classify a full changed-path list without consulting the network."""
    cleaned: list[str] = []
    for path in paths:
        value = str(path)
        if value not in cleaned:
            cleaned.append(value)
    if not cleaned:
        return Scope("full", reason="no changed paths")

    docs = [path for path in cleaned if is_docs(path)]
    site = [path for path in cleaned if is_site(path)]
    unknown = [path for path in cleaned if not is_docs(path) and not is_site(path)]
    if unknown:
        sample = ", ".join(unknown[:5])
        extra = "" if len(unknown) <= 5 else ", ..."
        return Scope("full", reason="paths outside fast allowlist: " + sample + extra)
    if not docs and not site:
        return Scope("full", reason="no allowlisted docs/site paths")
    return Scope("fast", site=bool(site), docs=bool(docs),
                 reason="strict docs/site allowlist")


def _git(args: list[str], root: Path, *, text: bool = True) -> subprocess.CompletedProcess:
    return subprocess.run(["git", *args], cwd=str(root), capture_output=True, text=text)


def is_shallow(root: Path) -> bool:
    result = _git(["rev-parse", "--is-shallow-repository"], root)
    if result.returncode != 0:
        raise ScopeError("cannot inspect repository depth")
    return result.stdout.strip() == "true"


def resolve_commit(revision: str, root: Path) -> str | None:
    if not revision:
        return None
    result = _git(["rev-parse", "--verify", revision + "^{commit}"], root)
    if result.returncode != 0:
        return None
    value = result.stdout.strip()
    return value if re.fullmatch(r"[0-9a-f]{40}", value) else None


def _parse_name_status(data: bytes) -> list[tuple[str, list[str]]]:
    fields = data.split(b"\0")
    entries: list[tuple[str, list[str]]] = []
    index = 0
    while index < len(fields):
        status = fields[index]
        if not status:
            index += 1
            continue
        code = status.decode("ascii", "replace")
        if code[0] in "RC":
            if index + 2 >= len(fields):
                raise ScopeError("malformed rename/copy diff entry")
            old = fields[index + 1].decode("utf-8", "surrogateescape")
            new = fields[index + 2].decode("utf-8", "surrogateescape")
            entries.append((code, [old, new]))
            index += 3
        else:
            if index + 1 >= len(fields):
                raise ScopeError("malformed diff entry")
            path = fields[index + 1].decode("utf-8", "surrogateescape")
            entries.append((code, [path]))
            index += 2
    return entries


def changed_entries(base: str, head: str, root: Path) -> list[tuple[str, list[str]]]:
    """Return name-status entries for the complete PR base...head diff."""
    result = _git(["diff", "--name-status", "-z", "--find-renames", "--no-ext-diff",
                   base + "..." + head], root, text=False)
    if result.returncode != 0:
        detail = result.stderr.decode("utf-8", "replace").strip()
        raise ScopeError("git diff failed: " + (detail or "unknown error"))
    return _parse_name_status(result.stdout)


def changed_paths(base: str, head: str, root: Path) -> list[str]:
    """Return every real path from the diff, including non-canonical ones."""
    paths: list[str] = []
    for _status, names in changed_entries(base, head, root):
        for name in names:
            if name not in paths:
                paths.append(name)
    return paths


def classify_pr(base: str, head: str, root: Path | str = ".") -> Scope:
    """Classify a PR range, falling back to full verification on any doubt."""
    root = Path(root)
    try:
        if is_shallow(root):
            return Scope("full", reason="shallow clone; full history required")
        base_sha = resolve_commit(base, root)
        head_sha = resolve_commit(head, root)
        if not base_sha or not head_sha:
            return Scope("full", reason="base or head commit is unavailable")
        if base_sha == head_sha:
            return Scope("full", reason="empty diff")
        return classify_paths(changed_paths(base_sha, head_sha, root))
    except ScopeError as error:
        return Scope("full", reason="classification failed: " + str(error))


def check_changed_docs(base: str, head: str, root: Path | str = ".") -> int:
    """Run the cheap documentation sanity check on changed Markdown files."""
    root = Path(root)
    entries = changed_entries(base, head, root)
    checked = 0
    for status, names in entries:
        if status.startswith("D"):
            continue
        # For renames/copies the destination is the last name.
        name = names[-1]
        if not name.lower().endswith(".md"):
            continue
        path = root / name
        if not path.is_file():
            raise ScopeError("changed Markdown file is missing: " + name)
        raw = path.read_bytes()
        try:
            text = raw.decode("utf-8")
        except UnicodeDecodeError as error:
            raise ScopeError("Markdown is not UTF-8: " + name) from error
        if "\0" in text:
            raise ScopeError("Markdown contains a NUL byte: " + name)
        for line in text.splitlines():
            if re.match(r"^(<<<<<<<|>>>>>>>)(\s|$)", line):
                raise ScopeError("Markdown contains a conflict marker: " + name)
        checked += 1
    print("DOCS_FAST_CHECKS_PASSED files=" + str(checked))
    return checked


def _event_pull_request_shas(event_path: str) -> tuple[str, str]:
    if not event_path:
        return "", ""
    try:
        event = json.loads(Path(event_path).read_text(encoding="utf-8"))
        pull = event["pull_request"]
        return str(pull["base"]["sha"]), str(pull["head"]["sha"])
    except (OSError, KeyError, TypeError, ValueError):
        return "", ""


def _output_safe(value: str, limit: int = 500) -> str:
    """Keep GITHUB_OUTPUT single-line and UTF-8 encodable.

    Replace CR/LF and every other control character (plus undecodable-path
    surrogates) before writing or printing the classification reason.
    """
    cleaned = "".join(char if char.isprintable() else " " for char in str(value))
    return cleaned[:limit]


def _write_scope(scope: Scope) -> None:
    values = {
        "mode": scope.mode,
        "site": "true" if scope.site else "false",
        "docs": "true" if scope.docs else "false",
        "reason": _output_safe(scope.reason),
    }
    output = os.environ.get("GITHUB_OUTPUT")
    if output:
        with open(output, "a", encoding="utf-8") as handle:
            for key, value in values.items():
                handle.write(key + "=" + value + "\n")
    print("PR_VERIFY_SCOPE " + " ".join(key + "=" + value for key, value in values.items()))


def _classify_command(args: argparse.Namespace) -> int:
    event = args.event if args.event is not None else os.environ.get("GITHUB_EVENT_NAME", "")
    base = args.base or os.environ.get("BASE_SHA", "")
    head = args.head or os.environ.get("HEAD_SHA", "")
    if not event:
        # Local/offline invocation: an explicit base/head pair is a PR range.
        event = "pull_request" if base and head else ""
    if event and event != "pull_request":
        _write_scope(Scope("full", reason="non-PR event: " + event))
        return 0
    if not base or not head:
        base, head = _event_pull_request_shas(args.event_path or os.environ.get("GITHUB_EVENT_PATH", ""))
    if not base or not head:
        _write_scope(Scope("full", reason="pull request base/head is unavailable"))
        return 0
    try:
        scope = classify_pr(base, head, args.root)
    except Exception as error:  # Fail closed, never fail-open.
        print("PR scope classification error: " + str(error), file=sys.stderr)
        scope = Scope("full", reason="classification error")
    _write_scope(scope)
    return 0


def _check_docs_command(args: argparse.Namespace) -> int:
    base = args.base or os.environ.get("BASE_SHA", "")
    head = args.head or os.environ.get("HEAD_SHA", "")
    if not base or not head:
        base, head = _event_pull_request_shas(args.event_path or os.environ.get("GITHUB_EVENT_PATH", ""))
    if not base or not head:
        print("docs fast check needs base and head commits", file=sys.stderr)
        return 1
    try:
        check_changed_docs(base, head, args.root)
    except (ScopeError, OSError) as error:
        print("docs fast check failed: " + str(error), file=sys.stderr)
        return 1
    return 0


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("command", choices=["classify", "check-docs"])
    parser.add_argument("--base", default="", help="pull request base commit")
    parser.add_argument("--head", default="", help="pull request head commit")
    parser.add_argument("--root", default=".", help="repository checkout")
    parser.add_argument("--event", default=None, help="GitHub event name")
    parser.add_argument("--event-path", default="", help="GitHub event JSON path")
    args = parser.parse_args(argv)
    if args.command == "classify":
        return _classify_command(args)
    return _check_docs_command(args)


if __name__ == "__main__":
    sys.exit(main())
