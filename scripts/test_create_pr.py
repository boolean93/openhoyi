"""Exercise the actual CLI against a disposable Git origin, with gh recorded locally."""
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest


SCRIPTS = Path(__file__).resolve().parent


class CreatePRTests(unittest.TestCase):
    def setUp(self):
        temp = tempfile.TemporaryDirectory()
        self.addCleanup(temp.cleanup)
        root = Path(temp.name)
        self.repo = root / "checkout"
        self.repo.mkdir()
        self.calls = root / "calls"
        self.body = root / "body"
        self.body.write_text(
            "## 规划与范围\ndocs/plans/example.md\n## 验证证据\n本地预检\n"
            "## 协议与设备风险\n无\n## 发布与回退\n不发版\n"
        )
        self.env = dict(os.environ, HOME=str(root), GIT_CONFIG_NOSYSTEM="1",
                        PATH=str(root) + os.pathsep + os.environ["PATH"], CALLS=str(self.calls))
        self.git("init", "-q", "-b", "develop")
        self.git("config", "user.email", "test@example.org")
        self.git("config", "user.name", "Test")
        remote = root / "remote.git"
        self.git("init", "--bare", "-q", str(remote))
        url = "https://github.com/example/openhoyi.git"
        self.git("config", "url." + remote.as_uri() + ".insteadOf", url)
        self.git("remote", "add", "origin", url)
        scripts = self.repo / "scripts"
        scripts.mkdir()
        for name in ("create-pr", "git_policy.py", "pr_verify_scope.py"):
            shutil.copy2(SCRIPTS / name, scripts / name)
        self.executable(scripts / "verify-pr-checks",
                        'echo "$1" >> "$CALLS"\n[ "$1" != "${FAIL_MODE:-}" ]')
        self.executable(root / "gh", 'echo "gh $*" >> "$CALLS"\ncat >> "$CALLS"')
        (self.repo / ".gitignore").write_text("__pycache__/\n")
        self.git("add", ".")
        self.git("commit", "-qm", "base")
        self.git("push", "-q", "origin", "develop")
        self.git("switch", "-qc", "docs/example")
        plan = self.repo / "docs/plans/example.md"
        plan.parent.mkdir(parents=True)
        plan.write_text("# Example\n")
        self.git("add", ".")
        self.git("commit", "-qm", "docs")
        self.git("push", "-q", "origin", "HEAD")

    @staticmethod
    def executable(path, body):
        path.write_text("#!/bin/sh\n" + body + "\n")
        path.chmod(0o755)

    def git(self, *args):
        return subprocess.check_output(["git", *args], cwd=self.repo, env=self.env, text=True)

    def create(self, **env):
        return subprocess.run(["scripts/create-pr", "--title", "example", "--body-file",
                               str(self.body), "--draft"], cwd=self.repo,
                              env={**self.env, **env}, capture_output=True, text=True)

    def test_failed_check_blocks_creation(self):
        result = self.create(FAIL_MODE="fast-docs")
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("fast-docs", self.calls.read_text())
        self.assertNotIn("gh ", self.calls.read_text())

    def test_pushed_commit_creates_draft_with_evidence(self):
        result = self.create()
        self.assertEqual(result.returncode, 0, result.stderr)
        calls = self.calls.read_text()
        self.assertIn("fast-docs", calls.split("gh ")[0])
        self.assertIn("fast-policy", calls.split("gh ")[0])
        self.assertIn("--draft", calls)
        self.assertIn("docs/example", calls)
        self.assertIn(self.git("rev-parse", "HEAD").strip(), calls.split("gh ")[1])
        self.assertIn(self.body.read_text().strip(), calls)

    def test_unpushed_commit_cannot_open_pr_for_older_remote_head(self):
        self.git("commit", "-q", "--allow-empty", "-m", "unpushed")
        self.assertNotEqual(self.create().returncode, 0)
        self.assertFalse(self.calls.exists() and "gh " in self.calls.read_text())
