"""Offline public-release fixtures; no credentials, API writes, SDK or devices."""
from copy import deepcopy
import hashlib
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import urllib.error

import release_update_index as updater

SHA, CERT = "a" * 40, "b" * 64
APK = b"verified-apk-fixture"


def fixture(code=42):
    tag = f"dev-{code}"
    manifest = dict(schemaVersion=1, variant="alpha", packageName="io.openhoyi.mobile",
                    versionCode=code, versionName=f"1.0.0-dev.{code}", debuggable=False,
                    signerCertificateSha256=CERT, apkSha256=hashlib.sha256(APK).hexdigest(),
                    apkFile=f"OpenHOYI-{code}.apk", declaredSourceCommit=SHA,
                    sourceCommitVerified=False, channel="develop-test")
    raw = json.dumps(manifest).encode()
    files = {"distribution.json": raw, manifest["apkFile"]: APK,
             "SHA256SUMS.txt": "".join(hashlib.sha256(data).hexdigest() + "  " + name + "\n"
                                for name, data in ((manifest["apkFile"], APK), ("distribution.json", raw))).encode()}
    release = dict(id=code, tag_name=tag, draft=False, prerelease=True,
                   html_url=updater.BASE + "/releases/tag/" + tag, published_at="2026-11-12T19:00:00Z")
    assets = [dict(id=i + 1, name=name, size=len(data), state="uploaded",
                   browser_download_url=updater.BASE + "/releases/download/" + tag + "/" + name,
                   digest="sha256:" + hashlib.sha256(data).hexdigest())
              for i, (name, data) in enumerate(files.items())]
    reservation = dict(schemaVersion=1, channel="develop-test", repository=updater.REPO,
                       sourceCommit=SHA, versionCode=code, versionName=manifest["versionName"],
                       signerCertificateSha256=CERT, approvalReference="approved dev channel", workflowRun=123)
    return manifest, files, release, assets, reservation


class API:
    def __init__(self):
        self.manifest, self.files, self.release, self.assets, self.reservation = fixture()
        self.releases = [self.release]
        self.calls = []

    def pages(self, path):
        self.calls.append(path)
        return self.releases if path == "releases" else self.assets

    def optional(self, path):
        assert path == "git/ref/tags/dev-42"
        return {"object": {"type": "tag", "sha": "c" * 40}}

    def request(self, path):
        assert path == "git/tags/" + "c" * 40
        return {"message": json.dumps(self.reservation), "object": {"type": "commit", "sha": SHA}}

    def download(self, url, limit):
        assert url.startswith(updater.BASE + "/releases/download/dev-42/")
        return self.files[url.rsplit("/", 1)[1]]

    def inspect(self, path, aapt, apksigner, variant, cert, source):
        assert Path(path).read_bytes() == APK
        assert (variant, cert, source) == ("alpha", CERT, SHA)
        return deepcopy(self.manifest)

    def build(self):
        return updater.build_index(self, CERT, "aapt", "apksigner", self.download, self.inspect)


class UpdateIndexTests(unittest.TestCase):
    def test_complete_public_apk_and_atomic_index(self):
        api = API()
        index = api.build()
        updater.validate_index(index)
        self.assertEqual(index["releaseTag"], "dev-42")
        self.assertIs(index["sourceCommitVerified"], False)
        self.assertEqual(index["apkSize"], len(APK))
        self.assertEqual(api.calls.count("releases"), 2)
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory, "updates/dev.json")
            updater.write_index(path, index)
            self.assertEqual(json.loads(path.read_bytes()), index)
            with patch.object(updater.os, "replace", side_effect=OSError):
                with self.assertRaises(OSError):
                    updater.write_index(path, {"bad": True})
            self.assertEqual(json.loads(path.read_bytes()), index)
            self.assertEqual(list(path.parent.iterdir()), [path])

    def test_optional_prompt_verified_but_app_index_unchanged(self):
        baseline = API().build()
        for corrupt in (False, True):
            api = API()
            name = "OpenHOYI-curve-import-prompt.zh-CN.txt"
            prompt = "中文提示词".encode()
            api.manifest["curveImportPrompt"] = dict(file=name, sha256=hashlib.sha256(prompt).hexdigest(),
                                                    schema=3, revision=1)
            api.files[name] = b"tampered" if corrupt else prompt
            api.files["distribution.json"] = json.dumps(api.manifest).encode()
            api.files["SHA256SUMS.txt"] = "".join(hashlib.sha256(api.files[n]).hexdigest() + "  " + n + "\n"
                for n in (api.manifest["apkFile"], "distribution.json")).encode()
            api.assets = [dict(id=i+1, name=n, size=len(data), state="uploaded",
                browser_download_url=updater.BASE + "/releases/download/dev-42/" + n)
                for i, (n, data) in enumerate(api.files.items())]
            if corrupt:
                with self.assertRaisesRegex(ValueError, "prompt hash"):
                    api.build()
            else:
                prompt_index = {}
                self.assertEqual(updater.build_index(api, CERT, "aapt", "apksigner", api.download,
                    api.inspect, prompt_index=prompt_index), baseline)
                self.assertTrue(prompt_index["available"])
                self.assertEqual(prompt_index["sha256"], hashlib.sha256(prompt).hexdigest())
                self.assertEqual(prompt_index["declaredSourceCommit"], SHA)
                self.assertIn("/dev-42/", prompt_index["downloadUrl"])
        api = API()
        prompt_index = {}
        updater.build_index(api, CERT, "aapt", "apksigner", api.download, api.inspect, prompt_index)
        self.assertFalse(prompt_index["available"])
        self.assertNotIn("downloadUrl", prompt_index)

    def test_numeric_highest_not_latest_date_and_no_fallback(self):
        a, b = fixture(9)[2], fixture(100)[2]
        b["published_at"] = "2020-01-01T00:00:00Z"
        self.assertEqual(updater.newest_release([b, a])["tag_name"], "dev-100")
        b["prerelease"] = False
        with self.assertRaises(ValueError):
            updater.newest_release([a, b])
        b["draft"] = True
        self.assertEqual(updater.newest_release([b, a]), a)
        with self.assertRaises(ValueError):
            updater.newest_release([])

    def test_missing_duplicate_or_foreign_asset_rejected(self):
        for mutate in (lambda api: api.assets.pop(),
                       lambda api: api.assets.append(api.assets[0]),
                       lambda api: api.assets[0].update(browser_download_url="https://evil.test/a"),
                       lambda api: api.assets[0].update(size=1)):
            api = API()
            mutate(api)
            with self.assertRaises(ValueError):
                api.build()

    def test_manifest_actual_apk_and_checksum_mismatches_rejected(self):
        for change in ("versionCode", "packageName", "signerCertificateSha256", "debuggable",
                       "apkSha256", "sourceCommitVerified", "channel"):
            api = API()
            api.manifest[change] = "wrong"
            # Alter manifest and keep transport digest valid; schema must still reject it.
            raw = json.dumps(api.manifest).encode()
            api.files["distribution.json"] = raw
            api.assets[0].update(size=len(raw), digest="sha256:" + hashlib.sha256(raw).hexdigest())
            with self.subTest(field=change), self.assertRaises(ValueError):
                api.build()
        for key, value in (("apkSha256", "d" * 64), ("signerCertificateSha256", "d" * 64),
                           ("versionCode", 43), ("packageName", "io.openhoyi.mobile.mock")):
            api = API()
            api.inspect = lambda *args, k=key, v=value: {**api.manifest, k: v}
            # Public manifest, transport digests and SHA256SUMS remain valid.
            with self.subTest(actual_apk_field=key), self.assertRaises(ValueError):
                api.build()
        api = API()
        api.files["SHA256SUMS.txt"] = b"bad-checksum"
        api.assets[-1].update(size=12, digest=None)
        with self.assertRaises(ValueError):
            api.build()

    def test_channel_advancing_during_download_stops(self):
        api = API()
        original = api.inspect
        def inspect(*args):
            api.releases.append(fixture(43)[2])
            return original(*args)
        api.inspect = inspect
        with self.assertRaises(ValueError):
            api.build()

    def test_public_high_water_and_invalid_existing_index(self):
        index = API().build()
        read = lambda old: lambda *args: json.dumps(old).encode()
        updater.guard_public_high_water(index, read(index))
        previous = {**index, "apkSha256": "e" * 64}
        with self.assertRaises(ValueError):
            updater.guard_public_high_water(index, read(previous))
        lower = {**index, "versionCode": 41, "versionName": "1.0.0-dev.41", "releaseTag": "dev-41",
                 "apkFile": "OpenHOYI-41.apk", "releaseUrl": updater.BASE + "/releases/tag/dev-41",
                 "downloadUrl": updater.BASE + "/releases/download/dev-41/OpenHOYI-41.apk",
                 "distributionUrl": updater.BASE + "/releases/download/dev-41/distribution.json",
                 "checksumsUrl": updater.BASE + "/releases/download/dev-41/SHA256SUMS.txt"}
        updater.validate_index(lower)
        with self.assertRaisesRegex(ValueError, "never decrease"):
            updater.guard_public_high_water(lower, read(index))
        with self.assertRaises(ValueError):
            updater.guard_public_high_water(index, read({"schemaVersion": 999}))
        def missing(*args):
            raise urllib.error.HTTPError(updater.INDEX_URL, 404, "missing", {}, None)
        updater.guard_public_high_water(index, missing)
        def forbidden(*args):
            raise urllib.error.HTTPError(updater.INDEX_URL, 403, "forbidden", {}, None)
        with self.assertRaises(urllib.error.HTTPError):
            updater.guard_public_high_water(index, forbidden)

    def test_wake_up_trust_and_failed_or_manual_publisher(self):
        event = dict(repository={"full_name": updater.REPO}, action="completed",
                     workflow_run=dict(repository={"full_name": updater.REPO}, head_repository={"full_name": updater.REPO},
                                       head_branch="develop", path=updater.WORKFLOW, event="workflow_dispatch",
                                       status="completed", conclusion="failure"))
        updater.authorize_event(event, "workflow_run", "refs/heads/develop")
        for key, value in (("head_repository", {"full_name": "attacker/fork"}), ("repository", {"full_name": "attacker/fork"}),
                           ("path", ".github/workflows/evil.yml"), ("head_branch", "feature/evil"), ("event", "pull_request")):
            bad = deepcopy(event)
            bad["workflow_run"][key] = value
            with self.subTest(field=key), self.assertRaises(ValueError):
                updater.authorize_event(bad, "workflow_run", "refs/heads/develop")
        with self.assertRaises(ValueError):
            updater.authorize_event(event, "pull_request", "refs/heads/develop")

    def test_public_readback_retries_then_requires_exact_bytes(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory, "dev.json")
            path.write_bytes(b"expected")
            with patch.object(updater, "public_bytes", side_effect=[b"stale", b"expected"]):
                updater.verify_public(updater.INDEX_URL, path, attempts=2, delay=0)
            with patch.object(updater, "public_bytes", return_value=b"stale"):
                with self.assertRaises(ValueError):
                    updater.verify_public(updater.INDEX_URL, path, attempts=1)

    def test_workflow_unprivileged_pr_and_current_develop_only(self):
        workflow = (Path(__file__).resolve().parents[1] / ".github/workflows/product-pages.yml").read_text()
        self.assertIn("workflows: [Develop APK prerelease]", workflow)
        self.assertIn("ref: refs/heads/develop", workflow)
        self.assertNotIn("conclusion == 'success'", workflow)
        self.assertNotIn("actions/download-artifact", workflow)
        self.assertIn("path: site", workflow)
        self.assertIn("python3 scripts/release_update_index.py verify-public", workflow)
        validate = workflow.split("  validate:", 1)[1].split("  deploy:", 1)[0]
        self.assertNotIn("pages: write", validate)
        self.assertNotIn("id-token: write", validate)


if __name__ == "__main__":
    unittest.main()
