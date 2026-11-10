"""Offline state-transition regressions; no network, Android build, or signing keys."""
from contextlib import contextmanager
from copy import deepcopy
import json
import os
from pathlib import Path
import re
import tempfile
import unittest
from unittest import mock

import develop_release as runner
from develop_release_policy import allocate_version
from develop_release_store import ApiError, GitHub, Ledger, STATE_REF, sha256


SOURCE = "a" * 40
CERT = "b" * 64
CODE = 42
VERSION = "1.0.0-dev.42"


def reservation():
    return dict(schemaVersion=1, channel="develop-test", repository=runner.REPO,
                sourceCommit=SOURCE, versionCode=CODE, versionName=VERSION,
                signerCertificateSha256=CERT, approvalReference="approved test channel",
                workflowRun=123, attempt=1, status="reserved")


def state():
    return dict(schemaVersion=1, allocations={str(CODE): reservation()},
                lease={"runId": 123, "attempt": 1})


class MemoryLedger(Ledger):
    """Keep real acquire/update/owns semantics, replacing only persistence."""
    def __init__(self, api, initial=None):
        super().__init__(api, 123, 1)
        self.state = deepcopy(initial if initial is not None else state())
        self.writes = []

    def read(self):
        return "parent", deepcopy(self.state)

    def write(self, parent, value):
        self.state = deepcopy(value)
        self.writes.append(deepcopy(value))


class GitObjects:
    """Tiny Git object graph: sibling commits cannot fast-forward one another."""
    def __init__(self):
        self.head = None
        self.objects = {}
        self.calls = []

    def optional(self, path):
        assert path == "git/ref/" + STATE_REF
        return {"object": {"sha": self.head}} if self.head else None

    def request(self, path, method="GET", data=None):
        self.calls.append((path, method, deepcopy(data)))
        if method == "GET":
            return deepcopy(self.objects[path.rsplit("/", 1)[-1]])
        if path in ("git/trees", "git/commits"):
            key = str(len(self.objects) + 1)
            if path == "git/trees":
                # Ledger.read expects tree entries to refer to separately readable blobs.
                blob = key + "-blob"
                import base64
                self.objects[blob] = {"content": base64.b64encode(data["tree"][0]["content"].encode()).decode()}
                value = {"tree": [{"path": "state.json", "type": "blob", "sha": blob}]}
            else:
                value = {**deepcopy(data), "tree": {"sha": data["tree"]}}
            self.objects[key] = value
            return {"sha": key}
        if path == "git/refs" and method == "POST":
            if self.head is not None:
                raise ApiError(422)
        elif path == "git/refs/" + STATE_REF and method == "PATCH":
            if data["force"] or self.objects[data["sha"]]["parents"] != [self.head]:
                raise ApiError(422)
        else:
            raise AssertionError((path, method))
        self.head = data["sha"]
        return {"object": {"sha": self.head}}


class ReleaseAPI:
    """Only the publication endpoints used by publish_one; writes stay in memory."""
    def __init__(self):
        self.calls = []
        self.tag = None
        self.release = None
        self.assets = []
        self.contents = {}
        self.corrupt_upload = False
        self.lose_patch = False
        self.before_public = lambda: None

    def optional(self, path):
        if path.startswith("git/ref/tags/"):
            return {"object": {"type": "tag", "sha": "tag-object"}} if self.tag else None
        if path == "releases/tags/dev-42":
            return deepcopy(self.release)
        raise AssertionError(path)

    def pages(self, path):
        assert path == "releases/7/assets", path
        return iter(deepcopy(self.assets))

    def asset(self, asset):
        return self.contents[asset["name"]]

    def request(self, path, method="GET", data=None, upload_name=None):
        self.calls.append((path, method, deepcopy(data)))
        if path == "git/tags" and method == "POST":
            self.tag = deepcopy(data)
            return {"sha": "tag-object"}
        if path == "git/refs" and method == "POST":
            return {}
        if path == "git/tags/tag-object":
            return {"message": self.tag["message"], "object": {"type": "commit", "sha": self.tag["object"]}}
        if path == "releases" and method == "POST":
            self.release = {**deepcopy(data), "id": 7}
            return deepcopy(self.release)
        if path == "releases/7/assets" and method == "POST":
            self.contents[upload_name] = b"corrupted" if self.corrupt_upload else data
            self.assets.append({"name": upload_name, "id": len(self.assets) + 1})
            return deepcopy(self.assets[-1])
        if path == "releases/7" and method == "PATCH":
            self.before_public()
            self.release.update(data)
            if self.lose_patch:
                raise RuntimeError("response lost after server committed visibility")
        elif path != "releases/7" or method != "GET":
            raise AssertionError((path, method))
        return deepcopy(self.release)

    def public_record(self):
        return {**json.loads(self.contents["distribution.json"]),
                "_release": deepcopy(self.release), "_assets": deepcopy(self.assets),
                "_manifestHash": sha256(self.contents["distribution.json"])}


class DevelopReleaseRunnerTests(unittest.TestCase):
    @contextmanager
    def publication(self):
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            apk = directory / "OpenHOYI-test.apk"
            apk.write_bytes(b"fake APK, never executed")
            manifest = dict(schemaVersion=1, variant="alpha", packageName="io.openhoyi.mobile",
                            versionCode=CODE, versionName=VERSION, debuggable=False,
                            signerCertificateSha256=CERT, apkSha256=sha256(apk.read_bytes()),
                            declaredSourceCommit=SOURCE, sourceCommitVerified=False, apkFile=apk.name)
            manifest_path = directory / "distribution.json"
            manifest_path.write_text(json.dumps(manifest))
            (directory / "SHA256SUMS.txt").write_text("".join(
                sha256(p.read_bytes()) + "  " + p.name + "\n" for p in (apk, manifest_path)))
            api = ReleaseAPI()
            ledger = MemoryLedger(api)
            item = dict(source=SOURCE, code=CODE, versionName=VERSION, certificate=CERT,
                        previous={"apkSha256": "c" * 64, "versionCode": 41, "sourceCommit": "d" * 40})
            with mock.patch.dict(os.environ, {"DEVELOP_APK_ENABLED": "true", "RUNNER_TEMP": temporary}, clear=True), \
                    mock.patch.object(runner, "inspect_apk", return_value=manifest) as inspect, \
                    mock.patch.object(runner, "tools", return_value=("fake-aapt", "fake-apksigner")), \
                    mock.patch.object(runner, "successful_ci", return_value={"verified": True}), \
                    mock.patch.object(runner, "inventory", return_value=[]) as inventory, \
                    mock.patch.object(runner, "release_body", return_value="## 本次更新\n\n- 测试更新。") :
                yield api, ledger, item, directory, inspect, inventory

    def test_publish_flat_named_and_partial_single_artifact_downloads(self):
        for layout in ("flat", "named", "partial-flat"):
            with self.subTest(layout=layout), self.publication() as (api, ledger, item, directory, inspect, _):
                item.update(workflowRun=123, attempt=1)
                plans = [item]
                root = directory
                if layout == "named":
                    root = directory / "packages"
                    target = root / f"develop-apk-1-{CODE}"
                    target.mkdir(parents=True)
                    for name in ("OpenHOYI-test.apk", "distribution.json", "SHA256SUMS.txt"):
                        (target / name).write_bytes((directory / name).read_bytes())
                if layout == "partial-flat":
                    missing = {**item, "code": CODE - 1, "source": "c" * 40, "versionName": "1.0.0-dev.41"}
                    plans.insert(0, missing)
                    held = {**reservation(), "versionCode": CODE - 1, "sourceCommit": missing["source"],
                            "versionName": missing["versionName"]}
                    ledger.state["allocations"][str(CODE - 1)] = held
                plan_file = directory / "plans.json"
                plan_file.write_text(json.dumps(plans))
                with mock.patch.dict(os.environ, PLANS_FILE=str(plan_file), ARTIFACTS_ROOT=str(root)), \
                        mock.patch.object(runner, "configuration"), \
                        mock.patch.object(runner, "services", return_value=(api, (123, 1))), \
                        mock.patch.object(runner, "check_protection"), \
                        mock.patch.object(runner, "Ledger", return_value=ledger):
                    runner.publish()
                self.assertFalse(api.release["draft"])
                self.assertTrue(api.release["prerelease"])
                self.assertEqual(len(api.assets), 3)
                self.assertEqual(ledger.state["allocations"][str(CODE)]["status"], "published")
                inspect.assert_called_once()  # Directory compatibility does not bypass real APK inspection.
                if layout == "partial-flat":
                    self.assertEqual(ledger.state["allocations"][str(CODE - 1)], held)

    def test_flat_artifact_identity_and_ambiguous_layout_are_rejected(self):
        for failure in ("version", "source", "mixed"):
            with self.subTest(failure=failure), self.publication() as (api, ledger, item, directory, inspect, _):
                item.update(workflowRun=123, attempt=1)
                path = directory / "distribution.json"
                manifest = json.loads(path.read_text())
                if failure == "mixed":
                    named = directory / f"develop-apk-1-{CODE}"
                    named.mkdir()
                    (named / path.name).write_bytes(path.read_bytes())
                else:
                    manifest["versionCode" if failure == "version" else "declaredSourceCommit"] = (
                        CODE + 1 if failure == "version" else "c" * 40)
                    path.write_text(json.dumps(manifest))
                with self.assertRaises((runner.PublisherError, ValueError)):
                    runner.artifact_directories(directory, [item])
                inspect.assert_not_called()
                self.assertEqual(api.calls, [])
                self.assertEqual(ledger.writes, [])

    def test_no_artifacts_fails_instead_of_green_no_publication(self):
        with self.publication() as (api, ledger, item, directory, inspect, _):
            item.update(workflowRun=123, attempt=1)
            plan_file = directory / "plans.json"
            plan_file.write_text(json.dumps([item]))
            empty = directory / "empty-packages"
            empty.mkdir()
            with mock.patch.dict(os.environ, PLANS_FILE=str(plan_file), ARTIFACTS_ROOT=str(empty)), \
                    mock.patch.object(runner, "configuration"), \
                    mock.patch.object(runner, "services", return_value=(api, (123, 1))), \
                    mock.patch.object(runner, "check_protection"), \
                    mock.patch.object(runner, "Ledger", return_value=ledger):
                with self.assertRaisesRegex(runner.PublisherError, "No built artifacts"):
                    runner.publish()
            self.assertEqual(ledger.state, state())
            self.assertEqual(ledger.writes, [])
            self.assertEqual(api.calls, [])
            inspect.assert_not_called()

    def test_pages_complete_and_second_page_failure_is_not_done(self):
        for key in (None, "workflow_runs"):
            with self.subTest(key=key):
                api = GitHub(runner.REPO, "fake-token")
                wrap = lambda values: {key: values} if key else values
                with mock.patch.object(api, "request", side_effect=[wrap(list(range(100))), wrap([100])]) as request:
                    self.assertEqual(list(api.pages("releases?draft=false", key)), list(range(101)))
                    self.assertEqual(request.call_args_list[-1], mock.call("releases?draft=false&per_page=100&page=2"))
                with mock.patch.object(api, "request", side_effect=[wrap(list(range(100))), ApiError(503)]):
                    with self.assertRaises(ApiError):
                        list(api.pages("releases", key))
        # A partially read release inventory must not become a successful publication scan.
        api = GitHub(runner.REPO, "fake-token")
        with mock.patch.object(api, "request", side_effect=[
                [{"id": n} for n in range(100)], ApiError(503)]), \
                mock.patch.object(api, "asset") as asset:
            with self.assertRaises(ApiError):
                runner.inventory(api)
            asset.assert_not_called()

    def test_ledger_sibling_cas_has_one_winner_and_burns_reserved_number(self):
        api = GitObjects()
        first, second = Ledger(api, 123, 1), Ledger(api, 124, 1)
        first.write(None, dict(schemaVersion=1, allocations={}, lease=None))
        parent_a, a = first.read()
        parent_b, b = second.read()
        self.assertEqual(parent_a, parent_b)
        for value in (a, b):
            value["allocations"][str(CODE)] = reservation()
        first.write(parent_a, a)
        winner = api.head
        with self.assertRaises(ApiError):
            second.write(parent_b, b)
        self.assertEqual(api.head, winner)
        patches = [data for _, method, data in api.calls if method == "PATCH"]
        self.assertEqual(len(patches), 2)
        self.assertTrue(all(data["force"] is False for data in patches))
        _, durable = second.read()
        durable["allocations"][str(CODE)]["status"] = "failed"
        second.write(winner, durable)
        _, reloaded = first.read()
        self.assertEqual(allocate_version(0, list(reloaded["allocations"].values()), []), CODE + 1)

    def test_lease_running_not_stolen_but_completed_reclaimed(self):
        for status in ("in_progress", "completed"):
            with self.subTest(status=status):
                api = mock.Mock()
                api.request.return_value = {"status": status}
                initial = state()
                initial["lease"] = {"runId": 122, "attempt": 1}
                ledger = MemoryLedger(api, initial)
                if status == "in_progress":
                    with self.assertRaisesRegex(ValueError, "do not steal"):
                        ledger.acquire()
                    self.assertEqual(ledger.writes, [])
                    self.assertEqual(ledger.state, initial)
                else:
                    self.assertTrue(ledger.owns(ledger.acquire()))
                    self.assertEqual(len(ledger.writes), 1)
                api.request.assert_called_once_with("actions/runs/122")
                # Paused entry points must also be able to clear a cancelled,
                # confirmed-completed owner's stale lease without stealing one.
                stale = MemoryLedger(api, initial)
                stale.release()
                self.assertEqual(stale.state["lease"], None if status == "completed" else initial["lease"])
                self.assertEqual(len(stale.writes), 1 if status == "completed" else 0)

    def test_old_skipped_job_does_not_poison_later_healthy_source(self):
        old, new = "a" * 40, "c" * 40
        workflows = list(runner.REQUIRED_WORKFLOWS)
        api = mock.Mock()
        def pages(path, key):
            if key == "workflow_runs":
                filename = path.split("/")[2]
                source = path.split("head_sha=", 1)[1]
                run_id = workflows.index(filename) + 1 + (10 if source == new else 0)
                return [{"id": run_id, "run_number": run_id, "run_attempt": 1,
                         "event": "push", "head_branch": "develop", "head_sha": source,
                         "head_repository": {"full_name": runner.REPO},
                         "path": ".github/workflows/" + filename, "status": "completed",
                         "conclusion": "success", "html_url": "https://github.com/ci/" + str(run_id)}]
            run_id = int(path.split("/")[2])
            filename = workflows[(run_id - 1) % 10]
            return [{"name": runner.REQUIRED_WORKFLOWS[filename], "status": "completed",
                     "conclusion": "skipped" if run_id == 1 else "success"}]
        api.pages.side_effect = pages
        ready = [source for source in (old, new) if runner.successful_ci(api, source)]
        self.assertEqual(ready, [new])
        # Network/API failures remain errors, never interpreted as a complete scan.
        api.pages.side_effect = ApiError(503)
        with self.assertRaises(ApiError):
            runner.successful_ci(api, old)

    def test_pending_sources_backfills_missing_event_on_first_parent_history(self):
        baseline, a, b, c = (letter * 40 for letter in "0abc")
        snapshot = state()
        snapshot["allocations"][str(CODE)]["sourceCommit"] = a
        snapshot["allocations"][str(CODE)]["status"] = "published"
        policy = {"startAfterCommit": baseline}
        # Only C woke the runner; scanning A,B,C must still include B. A is published.
        for c_has_policy in (True, False):
            with self.subTest(c_has_policy=c_has_policy), \
                    mock.patch.object(runner, "git", side_effect=["", "\n".join((a, b, c))]) as git, \
                    mock.patch.object(runner.subprocess, "run", side_effect=lambda args, **kw:
                                      mock.Mock(returncode=0 if args[-1].startswith(b + ":") or c_has_policy else 1)) as cat_file:
                self.assertEqual(runner.pending_sources(policy, snapshot), [b, c] if c_has_policy else [b])
                self.assertEqual(git.call_args_list[-1], mock.call(
                    "rev-list", "--first-parent", "--reverse", baseline + "..origin/develop"))
                self.assertEqual([call.args[0][-1] for call in cat_file.call_args_list],
                                 [b + ":" + runner.POLICY_PATH, c + ":" + runner.POLICY_PATH])

    def test_bad_artifact_or_corrupt_upload_stays_private_and_never_overwrites(self):
        for failure in ("local-checksum", "uploaded-hash", "existing-hash"):
            with self.subTest(failure=failure), self.publication() as (api, ledger, item, directory, _, _):
                if failure == "local-checksum":
                    (directory / "SHA256SUMS.txt").write_text("incorrect")
                elif failure == "uploaded-hash":
                    api.corrupt_upload = True
                else:
                    api.release = {"id": 7, "tag_name": "dev-42", "draft": True, "prerelease": True}
                    api.assets = [{"id": 1, "name": "OpenHOYI-test.apk"}]
                    api.contents["OpenHOYI-test.apk"] = b"different existing bytes"
                expected = {"local-checksum": "checksum list", "uploaded-hash": "read-back mismatch",
                            "existing-hash": "never overwrite"}[failure]
                with self.assertRaisesRegex(ValueError, expected):
                    runner.publish_one(api, ledger, item, directory)
                self.assertEqual(ledger.state["allocations"][str(CODE)]["status"], "reserved")
                self.assertFalse(any(method in ("PATCH", "DELETE") for _, method, _ in api.calls))
                if api.release:
                    self.assertTrue(api.release["draft"])
                if failure == "existing-hash":
                    self.assertEqual(api.contents["OpenHOYI-test.apk"], b"different existing bytes")
                    self.assertFalse(any(path.endswith("/assets") and method == "POST"
                                         for path, method, _ in api.calls))

    def test_lost_public_patch_reconciles_prepared_hashes_without_rebuild_or_republish(self):
        with self.publication() as (api, ledger, item, directory, inspect, inventory):
            def assert_prepared():
                saved = ledger.state["allocations"][str(CODE)]
                self.assertEqual(saved["status"], "prepared")
                self.assertEqual(saved["manifestSha256"], sha256((directory / "distribution.json").read_bytes()))
                self.assertEqual(set(saved["assetHashes"]), {p.name for p in directory.iterdir()})
            api.before_public = assert_prepared
            api.lose_patch = True
            with self.assertRaisesRegex(RuntimeError, "response lost"):
                runner.publish_one(api, ledger, item, directory)
            self.assertFalse(api.release["draft"])
            record = api.public_record()
            self.assertEqual(runner.check_published(api, ledger.state, [record]), [(str(CODE), 7)])
            inventory.return_value = [record]
            event = directory / "event.json"
            event.write_text(json.dumps({"repository": {"full_name": runner.REPO}, "ref": "refs/heads/develop"}))
            env = dict(GITHUB_EVENT_PATH=str(event), GITHUB_EVENT_NAME="push", HOYI_RELEASE_CERT_SHA256=CERT,
                       HOYI_DISTRIBUTION_VERSION_FLOOR=str(CODE))
            policy = dict(startAfterCommit="0" * 40, minimumKnownVersionCode=41)
            with mock.patch.dict(os.environ, env), mock.patch.object(runner, "configuration", return_value=policy), \
                    mock.patch.object(runner, "services", return_value=(api, (123, 1))), \
                    mock.patch.object(runner, "check_protection"), mock.patch.object(runner, "Ledger", return_value=ledger), \
                    mock.patch.object(runner, "git", side_effect=["", SOURCE]), \
                    mock.patch.object(runner.subprocess, "run") as process, \
                    mock.patch.object(runner, "build") as build:
                runner.plan()
                process.assert_not_called()  # Already published: not even a source policy lookup.
                build.assert_not_called()
            self.assertEqual(ledger.state["allocations"][str(CODE)]["status"], "published")
            inspect.assert_called_once()
            writes = [(path, method, data) for path, method, data in api.calls if method != "GET"]
            self.assertEqual(sum(path == "releases" for path, _, _ in writes), 1)
            self.assertEqual(sum(method == "PATCH" for _, method, _ in writes), 1)
            self.assertTrue(all(data.get("make_latest") == "false" for path, _, data in writes
                                if path in ("releases", "releases/7")))
            changed = deepcopy(record)
            changed["_manifestHash"] = "f" * 64
            with self.assertRaisesRegex(ValueError, "changed|unverified"):
                runner.check_published(api, ledger.state, [changed])

    def test_higher_public_rc_prevents_public_patch(self):
        with self.publication() as (api, ledger, item, directory, _, inventory):
            inventory.return_value = [{"versionCode": CODE + 1, "channel": "rc",
                                       "_release": {"draft": False, "tag_name": "v1.0.0-rc.1"}}]
            with self.assertRaisesRegex(ValueError, "Another channel overtook"):
                runner.publish_one(api, ledger, item, directory)
            self.assertTrue(api.release["draft"])
            self.assertEqual(len(api.assets), 3)  # Full draft exists; visibility is still forbidden.
            self.assertFalse(any(method == "PATCH" for _, method, _ in api.calls))
            self.assertEqual(ledger.state["allocations"][str(CODE)]["status"], "reserved")

    def test_missing_signing_secrets_never_invokes_gradle(self):
        with self.publication() as (_, _, item, directory, inspect, _):
            inspect.return_value = dict(apkSha256=item["previous"]["apkSha256"], versionCode=41)
            # clear=True ensures this regression never reads host signing secrets.
            with mock.patch.dict(os.environ, {"SOURCE_ROOT": str(directory), "RUNNER_TEMP": str(directory)}, clear=True), \
                    mock.patch.object(runner, "configuration"), mock.patch.object(runner, "item_from_env", return_value=item), \
                    mock.patch.object(runner.subprocess, "check_output", return_value=SOURCE), \
                    mock.patch.object(runner.subprocess, "run") as process, \
                    mock.patch.object(runner.tempfile, "TemporaryDirectory") as signing_directory:
                with self.assertRaisesRegex(ValueError, "signing secrets are incomplete"):
                    runner.build()
                process.assert_not_called()
                signing_directory.assert_not_called()
            inspect.assert_called_once()

    def test_old_attempt_plan_cannot_retry_signing_or_download(self):
        item = {"workflowRun": 123, "attempt": 1}
        env = {"PLAN_JSON": json.dumps(item), "GITHUB_RUN_ID": "123", "GITHUB_RUN_ATTEMPT": "1"}
        with mock.patch.dict(os.environ, env, clear=True):
            self.assertEqual(runner.item_from_env(), item)
        for run_id, attempt in ((123, 2), (124, 1)):
            with self.subTest(run_id=run_id, attempt=attempt), \
                    mock.patch.dict(os.environ, {**env, "GITHUB_RUN_ID": str(run_id),
                                                 "GITHUB_RUN_ATTEMPT": str(attempt)}, clear=True), \
                    mock.patch.object(runner, "configuration"), \
                    mock.patch.object(runner, "services") as services, \
                    mock.patch.object(runner.subprocess, "run") as process:
                for operation in (runner.download_previous, runner.build):
                    with self.assertRaisesRegex(ValueError, "Stale attempt plan"):
                        operation()
                services.assert_not_called()
                process.assert_not_called()

    def test_workflow_security_boundaries_without_yaml_dependency(self):
        text = (runner.ROOT / ".github/workflows/develop-apk.yml").read_text()
        jobs = dict(re.findall(r"^  ([a-z_]+):\n(.*?)(?=^  [a-z_]+:\n|\Z)",
                               text.split("\njobs:\n", 1)[1], re.M | re.S))
        steps = lambda job: re.split(r"^      - ", job, flags=re.M)[1:]
        all_steps = [(name, step) for name, job in jobs.items() for step in steps(job)]
        secret_steps = [(name, step) for name, step in all_steps if re.search(r"\bsecrets\.", step)]
        self.assertEqual(len(secret_steps), 1)
        name, signing = secret_steps[0]
        self.assertEqual(name, "build")
        self.assertIn("develop_release.py build", signing)
        self.assertEqual(set(re.findall(r"secrets\.([A-Z0-9_]+)", signing)), {
            "HOYI_RELEASE_KEYSTORE_BASE64", "HOYI_RELEASE_STORE_PASSWORD",
            "HOYI_RELEASE_KEY_ALIAS", "HOYI_RELEASE_KEY_PASSWORD"})
        self.assertEqual(text.count("secrets."), signing.count("secrets."))  # Not job/global env.
        self.assertNotIn("HOYI_RELEASE_KEY", jobs["publish"])
        self.assertNotIn("secrets.", jobs["publish"])
        uses = re.findall(r"\buses:\s*(\S+)", text)
        self.assertGreater(len(uses), 0)
        for action in uses:
            self.assertRegex(action, r"^[\w/-]+@[0-9a-f]{40}$")
        source = [step for name, step in all_steps if name == "build" and re.search(r"^          path: source$", step, re.M)]
        self.assertEqual(len(source), 1)
        self.assertIn("ref: ${{ matrix.source }}", source[0])
        downloads = [step for _, step in all_steps if "uses: actions/download-artifact@" in step]
        self.assertEqual(len(downloads), 2)
        for step in downloads:
            self.assertIsNone(re.search(r"^\s*(run-id|repository|github-token):", step, re.M))
        self.assertIn("contents: read", jobs["build"].split("    steps:", 1)[0])
        self.assertNotIn("contents: write", jobs["build"])
        self.assertEqual(text.count("name: develop-plans-${{ github.run_attempt }}"), 2)
        self.assertIn("name: develop-apk-${{ github.run_attempt }}-${{ matrix.code }}", text)
        self.assertIn("pattern: develop-apk-${{ github.run_attempt }}-*", text)


if __name__ == "__main__":
    unittest.main()
