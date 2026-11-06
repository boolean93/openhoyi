"""Pure, fail-closed checks for the independent develop-test release channel.

Inputs from HTTP are data only. This module performs no I/O, executes no code,
and does not prove APK provenance, installation, data retention or hardware acceptance.
"""
import re


REQUIRED_WORKFLOWS = {
    "branch-policy.yml": "branch-policy",
    "verify.yml": "verify",
    "mock-lifecycle.yml": "lifecycle",
    "mock-upgrade.yml": "upgrade",
}
PACKAGE_NAME = "io.openhoyi.mobile"
MAX_VERSION_CODE = 2147483647


def _integer(value, minimum=1, maximum=None):
    return (type(value) is int and value >= minimum
            and (maximum is None or value <= maximum))


def _hex(value, length):
    return isinstance(value, str) and re.fullmatch(r"[0-9a-f]{%d}" % length, value) is not None


def _version_code(value):
    if not _integer(value, maximum=MAX_VERSION_CODE):
        raise ValueError("versionCode must be a positive Android integer")
    return value


def select_successful_runs(run_lists: dict, sha: str, repo: str) -> dict | None:
    """Select the newest matching push run, never fall back from a newer failure.

    A rerun shares a run_number/id and advances run_attempt. Missing run_number
    falls back to id; absent run_attempt means the original attempt (1).
    The caller must separately fetch and validate the selected runs' jobs.
    """
    if not isinstance(run_lists, dict) or not _hex(sha, 40) or not isinstance(repo, str) or not repo:
        return None
    selected = {}
    for filename in REQUIRED_WORKFLOWS:
        runs = run_lists.get(filename)
        if not isinstance(runs, list):
            return None
        candidates = []
        for run in runs:
            if not isinstance(run, dict):
                return None
            repository = run.get("head_repository")
            if not (run.get("event") == "push" and run.get("head_branch") == "develop"
                    and run.get("head_sha") == sha and isinstance(repository, dict)
                    and repository.get("full_name") == repo
                    and run.get("path") == ".github/workflows/" + filename):
                continue
            run_id = run.get("id")
            number = run.get("run_number", run_id)
            attempt = run.get("run_attempt", 1)
            if not all(_integer(value) for value in (run_id, number, attempt)):
                return None
            candidates.append(((number, attempt, run_id), run))
        if not candidates:
            return None
        latest_key = max(key for key, _ in candidates)
        # Ambiguous duplicate API records must not let a success mask a failure.
        latest = [run for key, run in candidates if key == latest_key]
        if any(run.get("status") != "completed" or run.get("conclusion") != "success"
               for run in latest):
            return None
        selected[filename] = latest[0]
    return selected


def validate_jobs(jobs: list[dict], required_name: str) -> None:
    """Require exactly one completed successful job with the exact required name."""
    if (not isinstance(jobs, list) or not isinstance(required_name, str) or not required_name
            or any(not isinstance(job, dict) for job in jobs)):
        raise ValueError("Invalid jobs data")
    matching = [job for job in jobs if job.get("name") == required_name]
    if (len(matching) != 1 or matching[0].get("status") != "completed"
            or matching[0].get("conclusion") != "success"):
        raise ValueError("Required job must occur once and complete successfully")


def allocate_version(floor: int, reservations: list[dict], manifests: list[dict]) -> int:
    """Allocate above all reserved and published main-package codes, across channels."""
    if not _integer(floor, minimum=0):
        raise ValueError("Version floor must be a nonnegative integer")
    if not isinstance(reservations, list) or not isinstance(manifests, list):
        raise ValueError("Version history must be lists")
    high_water = floor
    for record in reservations:
        if not isinstance(record, dict):
            raise ValueError("Invalid reservation")
        # Failed or abandoned reservations still consume their code permanently.
        high_water = max(high_water, _version_code(record.get("versionCode")))
    for manifest in manifests:
        if not isinstance(manifest, dict):
            raise ValueError("Invalid historical manifest")
        if manifest.get("packageName") == PACKAGE_NAME:
            # Public debug, RC and stable packages count, without new-build restrictions.
            high_water = max(high_water, _version_code(manifest.get("versionCode")))
    return _version_code(high_water + 1)


def validate_reservation(tag: str, record: dict, actual_sha: str, repo: str) -> dict:
    """Check an immutable dev-N reservation against the actually dereferenced tag SHA."""
    if not isinstance(tag, str) or re.fullmatch(r"dev-[1-9][0-9]*", tag) is None:
        raise ValueError("Reservation tag must be dev-N without leading zeroes")
    if not isinstance(record, dict):
        raise ValueError("Invalid reservation record")
    code = _version_code(record.get("versionCode"))
    if (type(record.get("schemaVersion")) is not int or record["schemaVersion"] != 1
            or record.get("channel") != "develop-test"
            or not isinstance(repo, str) or not repo or record.get("repository") != repo
            or not _hex(record.get("sourceCommit"), 40)
            or actual_sha != record.get("sourceCommit")
            or str(code) != tag[4:]
            or not _hex(record.get("signerCertificateSha256"), 64)
            or not isinstance(record.get("approvalReference"), str)
            or not record["approvalReference"].strip()
            or not _integer(record.get("workflowRun"))):
        raise ValueError("Reservation identity or approval metadata does not match")
    return record


def validate_manifest(manifest: dict, sha: str, code: int, version_name: str, cert: str) -> None:
    """Check newly built release metadata without claiming binary/source attestation."""
    _version_code(code)
    if (not isinstance(manifest, dict) or not _hex(sha, 40)
            or not isinstance(version_name, str) or not version_name
            or not isinstance(cert, str) or not _hex(cert.lower(), 64)):
        raise ValueError("Invalid expected APK identity or manifest")
    if (type(manifest.get("schemaVersion")) is not int or manifest["schemaVersion"] != 1
            or manifest.get("variant") != "alpha"
            or manifest.get("packageName") != PACKAGE_NAME
            or type(manifest.get("versionCode")) is not int or manifest["versionCode"] != code
            or not isinstance(manifest.get("versionName"), str)
            or manifest["versionName"] != version_name
            or manifest.get("debuggable") is not False
            or not _hex(manifest.get("signerCertificateSha256"), 64)
            or manifest["signerCertificateSha256"] != cert.lower()
            or not _hex(manifest.get("apkSha256"), 64)
            or manifest.get("declaredSourceCommit") != sha
            or manifest.get("sourceCommitVerified") is not False):
        raise ValueError("APK manifest does not match the expected release identity")
    filename = manifest.get("apkFile")
    # Portable basename only: no paths, leading dot/option, spaces or shell metacharacters.
    if not isinstance(filename, str) or re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9._-]*\.apk", filename) is None:
        raise ValueError("APK filename must be a single safe .apk basename")
    if "upgradeComparison" in manifest:
        comparison = manifest["upgradeComparison"]
        if (not isinstance(comparison, dict)
                or comparison.get("identityAndVersionCompatible") is not True
                or comparison.get("installationVerified") is not False
                or comparison.get("dataPreservationVerified") is not False):
            raise ValueError("Upgrade comparison must not claim device verification")
        if _version_code(comparison.get("previousVersionCode")) >= code:
            raise ValueError("Upgrade versionCode must increase")
