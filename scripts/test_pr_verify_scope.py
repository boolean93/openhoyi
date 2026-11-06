"""Representative regressions for the fail-closed fast PR scope classifier."""
import importlib.util
import pathlib
import subprocess
import sys
import tempfile
import unittest

SPEC = importlib.util.spec_from_file_location(
    "pr_verify_scope", pathlib.Path(__file__).with_name("pr_verify_scope.py"))
scope = importlib.util.module_from_spec(SPEC)
sys.modules["pr_verify_scope"] = scope
SPEC.loader.exec_module(scope)


def git(root, *args):
    return subprocess.run(["git", *args], cwd=root, text=True,
                          capture_output=True, check=True).stdout.strip()


def init_repo(root):
    git(root, "init", "-q")
    git(root, "config", "user.name", "Fixture")
    git(root, "config", "user.email", "fixture@example.invalid")
    git(root, "config", "commit.gpgSign", "false")


def commit(root, message="fixture"):
    git(root, "add", "-A")
    git(root, "commit", "-qm", message)
    return git(root, "rev-parse", "HEAD")


class ClassifyPathsTests(unittest.TestCase):
    def test_strict_docs_and_site_allowlists(self):
        cases = [
            (["docs/a.md"], "fast", False, True),
            (["README.md", "AGENTS.md"], "fast", False, True),
            (["site/index.html"], "fast", True, False),
            (["scripts/test_product_pages.py"], "fast", True, False),
            (["docs/a.md", "site/index.html"], "fast", True, True),
        ]
        for paths, mode, site, docs in cases:
            with self.subTest(paths=paths):
                result = scope.classify_paths(paths)
                self.assertEqual((result.mode, result.site, result.docs), (mode, site, docs))

    def test_unknown_or_empty_paths_fall_back_to_full(self):
        for paths in ([], ["app/Main.kt"], [".github/workflows/verify.yml"],
                      ["scripts/other.py"], ["README.md", "app/Main.kt"],
                      ["docs/a.md", "gradle/libs.versions.toml"]):
            with self.subTest(paths=paths):
                result = scope.classify_paths(paths)
                self.assertEqual(result.mode, "full")
                self.assertTrue(result.reason)


class ClassifyDiffTests(unittest.TestCase):
    def test_docs_rename_and_delete_are_fast(self):
        with tempfile.TemporaryDirectory() as temp:
            root = pathlib.Path(temp)
            init_repo(root)
            (root / "docs").mkdir()
            (root / "docs/a.md").write_text("a\n", encoding="utf-8")
            base = commit(root)
            git(root, "mv", "docs/a.md", "docs/b.md")
            head = commit(root)
            result = scope.classify_pr(base, head, root)
            self.assertEqual((result.mode, result.docs), ("fast", True))
            git(root, "rm", "-q", "docs/b.md")
            deleted = commit(root)
            result = scope.classify_pr(head, deleted, root)
            self.assertEqual((result.mode, result.docs), ("fast", True))

    def test_rename_out_of_fast_allowlist_is_full(self):
        with tempfile.TemporaryDirectory() as temp:
            root = pathlib.Path(temp)
            init_repo(root)
            (root / "docs").mkdir()
            (root / "app").mkdir()
            (root / "docs/a.md").write_text("a\n", encoding="utf-8")
            (root / "app/Main.kt").write_text("// main\n", encoding="utf-8")
            base = commit(root)
            git(root, "mv", "docs/a.md", "app/notes.md")
            head = commit(root)
            result = scope.classify_pr(base, head, root)
            self.assertEqual(result.mode, "full")
            self.assertIn("app/notes.md", result.reason)

    def test_missing_commit_falls_back_to_full(self):
        with tempfile.TemporaryDirectory() as temp:
            root = pathlib.Path(temp)
            init_repo(root)
            (root / "docs").mkdir()
            (root / "docs/a.md").write_text("a\n", encoding="utf-8")
            commit(root)
            result = scope.classify_pr("0" * 40, git(root, "rev-parse", "HEAD"), root)
            self.assertEqual(result.mode, "full")
            self.assertIn("unavailable", result.reason)

    def test_shallow_clone_falls_back_to_full(self):
        with tempfile.TemporaryDirectory() as temp:
            source = pathlib.Path(temp) / "source"
            source.mkdir()
            init_repo(source)
            (source / "docs").mkdir()
            (source / "docs/a.md").write_text("a\n", encoding="utf-8")
            base = commit(source)
            (source / "docs/a.md").write_text("b\n", encoding="utf-8")
            head = commit(source)
            clone = pathlib.Path(temp) / "clone"
            subprocess.run(["git", "clone", "-q", "--depth", "1",
                            source.as_uri(), str(clone)], check=True)
            result = scope.classify_pr(base, head, clone)
            self.assertEqual(result.mode, "full")
            self.assertIn("shallow", result.reason)


class DocsCheckTests(unittest.TestCase):
    def test_changed_markdown_is_utf8_and_has_no_conflict_markers(self):
        with tempfile.TemporaryDirectory() as temp:
            root = pathlib.Path(temp)
            init_repo(root)
            (root / "docs").mkdir()
            (root / "docs/a.md").write_text("ok\n", encoding="utf-8")
            base = commit(root)
            (root / "docs/a.md").write_text("ok\nmore\n", encoding="utf-8")
            (root / "docs/b.md").write_text("new\n", encoding="utf-8")
            head = commit(root)
            self.assertEqual(scope.check_changed_docs(base, head, root), 2)

            for content, expected in (
                ("<<<<<<< HEAD\nbad\n", "conflict marker"),
                ("\ufeffok\n", None),
            ):
                (root / "docs/b.md").write_text(content, encoding="utf-8")
                bad = commit(root)
                if expected is None:
                    scope.check_changed_docs(base, bad, root)
                else:
                    with self.subTest(content=content), self.assertRaises(scope.ScopeError) as error:
                        scope.check_changed_docs(base, bad, root)
                    self.assertIn(expected, str(error.exception))

    def test_invalid_utf8_is_rejected(self):
        with tempfile.TemporaryDirectory() as temp:
            root = pathlib.Path(temp)
            init_repo(root)
            (root / "docs").mkdir()
            (root / "docs/a.md").write_text("ok\n", encoding="utf-8")
            base = commit(root)
            (root / "docs/a.md").write_bytes(b"bad \xff\n")
            head = commit(root)
            with self.assertRaises(scope.ScopeError) as error:
                scope.check_changed_docs(base, head, root)
            self.assertIn("UTF-8", str(error.exception))


if __name__ == "__main__":
    unittest.main()
