"""Small representative regressions for the pure develop release policy."""
from copy import deepcopy
import unittest

from develop_release_policy import (
    REQUIRED_WORKFLOWS, allocate_version, select_successful_runs,
    validate_jobs, validate_manifest, validate_reservation,
)


SHA = "a" * 40
REPO = "openhoyi/openhoyi"
CERT = "b" * 64


def run(filename, **changes):
    return {
        "id": 100, "run_number": 10, "run_attempt": 1,
        "event": "push", "head_branch": "develop", "head_sha": SHA,
        "head_repository": {"full_name": REPO},
        "path": ".github/workflows/" + filename,
        "status": "completed", "conclusion": "success", **changes,
    }


def reservation(**changes):
    return {
        "schemaVersion": 1, "channel": "develop-test", "repository": REPO,
        "sourceCommit": SHA, "versionCode": 42,
        "signerCertificateSha256": CERT, "approvalReference": "user approval",
        "workflowRun": 123, **changes,
    }


def manifest(**changes):
    return {
        "schemaVersion": 1, "variant": "alpha", "packageName": "io.openhoyi.mobile",
        "versionCode": 42, "versionName": "1.0.0-dev.42", "debuggable": False,
        "signerCertificateSha256": CERT, "apkSha256": "c" * 64,
        "declaredSourceCommit": SHA, "sourceCommitVerified": False,
        "apkFile": "openhoyi-alpha-42.apk", **changes,
    }


class DevelopReleasePolicyTests(unittest.TestCase):
    def test_required_workflows_and_latest_push_runs(self):
        self.assertEqual(REQUIRED_WORKFLOWS, {
            "branch-policy.yml": "branch-policy", "verify.yml": "verify",
            "mock-lifecycle.yml": "lifecycle", "mock-upgrade.yml": "upgrade",
        })
        lists = {filename: [
            run(filename),
            run(filename, id=101, run_attempt=2),
            # Newer unrelated HTTP data is not an eligible run or an instruction.
            run(filename, id=999, run_number=99, event="pull_request"),
            run(filename, id=998, run_number=98, head_repository={"full_name": "fork/repo"}),
            run(filename, id=997, run_number=97, path=".github/workflows/other.yml"),
        ] for filename in REQUIRED_WORKFLOWS}
        selected = select_successful_runs(lists, SHA, REPO)
        self.assertEqual(set(selected), set(REQUIRED_WORKFLOWS))
        self.assertTrue(all(item["run_attempt"] == 2 for item in selected.values()))
        for state in ("failure", "in_progress", "missing"):
            with self.subTest(state=state):
                changed = deepcopy(lists)
                if state == "missing":
                    del changed["verify.yml"]
                else:
                    changed["verify.yml"].append(run(
                        "verify.yml", id=102, run_attempt=3,
                        status="in_progress" if state == "in_progress" else "completed",
                        conclusion=None if state == "in_progress" else "failure"))
                self.assertIsNone(select_successful_runs(changed, SHA, REPO))

    def test_exact_required_job_and_no_skipped_success(self):
        successful = {"name": "verify", "status": "completed", "conclusion": "success"}
        self.assertIsNone(validate_jobs([successful, {"name": "other"}], "verify"))
        for jobs in (
            [{**successful, "conclusion": "skipped"}],
            [successful, successful.copy()],
            [{**successful, "name": "verify (matrix)"}],
        ):
            with self.subTest(jobs=jobs), self.assertRaises(ValueError):
                validate_jobs(jobs, "verify")

    def test_cross_channel_version_high_water_and_limits(self):
        self.assertEqual(allocate_version(10, [
            {"versionCode": 50, "status": "failed"},
        ], [
            {"packageName": "io.openhoyi.mobile", "versionCode": 40, "channel": "rc"},
            {"packageName": "io.openhoyi.mobile", "versionCode": 51, "debuggable": True},
            {"packageName": "io.openhoyi.mobile", "versionCode": 49, "channel": "stable"},
            {"packageName": "io.openhoyi.mobile.mock", "versionCode": "ignored"},
        ]), 52)
        for floor, reservations, manifests in (
            (True, [], []),
            (0, [], [{"packageName": "io.openhoyi.mobile", "versionCode": True}]),
            (0, [{"versionCode": 2147483647, "status": "failed"}], []),
        ):
            with self.subTest(floor=floor, reservations=reservations), self.assertRaises(ValueError):
                allocate_version(floor, reservations, manifests)

    def test_reservation_tag_sha_and_integer_identity(self):
        record = reservation(extra="permitted")
        self.assertIs(validate_reservation("dev-42", record, SHA, REPO), record)
        for tag, record, actual in (
            ("dev-042", reservation(), SHA),
            ("dev-42", reservation(), "d" * 40),
            ("dev-1", reservation(versionCode=True), SHA),
        ):
            with self.subTest(tag=tag, actual=actual), self.assertRaises(ValueError):
                validate_reservation(tag, record, actual, REPO)

    def test_new_manifest_identity_and_honest_upgrade_evidence(self):
        comparison = {
            "identityAndVersionCompatible": True, "installationVerified": False,
            "dataPreservationVerified": False, "previousVersionCode": 41,
        }
        self.assertIsNone(validate_manifest(manifest(upgradeComparison=comparison),
                                           SHA, 42, "1.0.0-dev.42", CERT.upper()))
        for changes in (
            {"apkFile": "../openhoyi.apk"},
            {"versionCode": True},
            {"upgradeComparison": {**comparison, "installationVerified": True}},
        ):
            with self.subTest(changes=changes), self.assertRaises(ValueError):
                validate_manifest(manifest(**changes), SHA, 42, "1.0.0-dev.42", CERT)


if __name__ == "__main__":
    unittest.main()
