#!/usr/bin/env python3
"""Develop-only APK publishing. No device operations or stable-release bypass."""
import argparse
import base64
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import tempfile

from apk_distribution import inspect_apk, compare_upgrade
from develop_release_policy import (REQUIRED_WORKFLOWS, allocate_version, select_successful_runs,
                                    validate_jobs, validate_manifest, validate_reservation)
from develop_release_store import ApiError, GitHub, Ledger, check_protection, output, sha256

ROOT = Path(__file__).resolve().parent.parent
POLICY_PATH = ".github/develop-apk.json"
REPO = "boolean93/openhoyi"


class PublisherError(ValueError):
    """A fixed, non-secret operator diagnostic safe to include in job output."""


def git(*args):
    return subprocess.check_output(["git", *args], cwd=ROOT, text=True, stderr=subprocess.PIPE).strip()


def configuration():
    policy = json.loads((ROOT / POLICY_PATH).read_text())
    if policy["repository"] != REPO or os.environ.get("GITHUB_REPOSITORY") != REPO:
        raise PublisherError("Publisher is restricted to its approved repository")
    if not re.fullmatch(r"[0-9a-f]{40}", policy["startAfterCommit"]):
        raise PublisherError("Invalid queue activation baseline")
    if not re.fullmatch(r"[0-9]+\.[0-9]+\.[0-9]+", policy["baseVersion"]):
        raise PublisherError("Invalid base version")
    return policy


def services():
    return GitHub(REPO, os.environ["GH_TOKEN"]), (int(os.environ["GITHUB_RUN_ID"]), int(os.environ["GITHUB_RUN_ATTEMPT"]))


def authorized_event(event, name):
    if event.get("repository", {}).get("full_name") != REPO:
        return False
    if name == "push":
        return event.get("ref") == "refs/heads/develop" and not event.get("deleted")
    if name == "workflow_dispatch":
        return os.environ.get("GITHUB_REF") == "refs/heads/develop"
    if name == "workflow_run":
        run = event.get("workflow_run", {})
        return (run.get("event") == "push" and run.get("head_branch") == "develop"
                and run.get("head_repository", {}).get("full_name") == REPO)
    return False


def successful_ci(api, source):
    runs = {file: list(api.pages(f"actions/workflows/{file}/runs?branch=develop&event=push&head_sha={source}", "workflow_runs"))
            for file in REQUIRED_WORKFLOWS}
    selected = select_successful_runs(runs, source, REPO)
    if selected is None:
        return None
    evidence = {}
    for file, run in selected.items():
        jobs = list(api.pages(f"actions/runs/{run['id']}/attempts/{run['run_attempt']}/jobs", "jobs"))
        if any(not isinstance(job, dict) for job in jobs):
            raise PublisherError("Malformed job API response")
        try:
            validate_jobs(jobs, REQUIRED_WORKFLOWS[file])
        except ValueError:
            # A skipped/missing/duplicate required job makes only this source
            # ineligible; do not poison all later healthy merged commits.
            return None
        evidence[file] = {key: run[key] for key in ("id", "run_attempt", "head_sha", "html_url")}
    return evidence


def inventory(api):
    """Include draft/RC/stable manifests in the global high-water mark."""
    releases = list(api.pages("releases"))
    records = []
    for release in releases:
        assets = list(api.pages(f"releases/{release['id']}/assets"))
        package_records = []
        for asset in assets:
            if asset["name"] == "distribution.json" or asset["name"].endswith("-manifest.json"):
                raw = api.asset(asset)
                record = json.loads(raw)
                if record.get("packageName") == "io.openhoyi.mobile":
                    record = {**record, "_release": release, "_assets": assets, "_manifestHash": sha256(raw)}
                    package_records.append(record)
        if len(package_records) > 1:
            raise PublisherError("Ambiguous main-package manifests in release")
        records.extend(package_records)
    return records


def previous_public(records, certificate):
    public = [r for r in records if not r["_release"]["draft"]]
    if not public:
        raise PublisherError("Existing main-package distribution baseline is required")
    previous = max(public, key=lambda r: r["versionCode"])
    if previous["signerCertificateSha256"] != certificate:
        raise PublisherError("Configured signer differs from the existing public main package")
    matches = [asset for asset in previous["_assets"] if asset["name"] == previous["apkFile"]]
    if len(matches) != 1:
        raise PublisherError("Previous APK asset is missing or ambiguous")
    asset = {key: matches[0][key] for key in ("id", "name", "size", "digest") if key in matches[0]}
    return {"asset": asset, "apkSha256": previous["apkSha256"],
            "versionCode": previous["versionCode"], "sourceCommit": previous["declaredSourceCommit"]}


def pending_sources(policy, state):
    git("merge-base", "--is-ancestor", policy["startAfterCommit"], "origin/develop")
    history = git("rev-list", "--first-parent", "--reverse", policy["startAfterCommit"] + "..origin/develop").splitlines()
    published = {r["sourceCommit"] for r in state["allocations"].values() if r.get("status") == "published"}
    sources = []
    for source in history:
        if source in published:
            continue
        # Do not retroactively publish commits from before the approved publisher existed.
        exists = subprocess.run(["git", "cat-file", "-e", f"{source}:{POLICY_PATH}"], cwd=ROOT,
                                stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL).returncode == 0
        if exists:
            sources.append(source)
    return sources


def check_published(api, state, records):
    by_tag = {r["_release"]["tag_name"]: r for r in records if not r["_release"]["draft"]}
    recovered = []
    for reservation in state["allocations"].values():
        record = by_tag.get("dev-" + str(reservation["versionCode"]))
        if reservation.get("status") != "published" and record is None:
            continue
        # Prepared hashes are persisted BEFORE the visibility transition. A lost
        # PATCH response can therefore be reconciled without another build/tag.
        if (record is None or record["_manifestHash"] != reservation.get("manifestSha256")
                or record["declaredSourceCommit"] != reservation["sourceCommit"]):
            raise PublisherError("Published release was changed or its outcome is unverified")
        actual_tag = tag_record(api, record["_release"]["tag_name"])
        if actual_tag is None:
            raise PublisherError("Published release tag is missing")
        validate_reservation(record["_release"]["tag_name"], actual_tag[0], actual_tag[1], REPO)
        if actual_tag[1] != reservation["sourceCommit"]:
            raise PublisherError("Published tag target differs from the reserved source")
        validate_manifest(record, reservation["sourceCommit"], reservation["versionCode"],
                          reservation["versionName"], reservation["signerCertificateSha256"])
        if record["_release"].get("prerelease") is not True:
            raise PublisherError("Develop release must remain a prerelease")
        if reservation.get("status") != "published":
            recovered.append((str(reservation["versionCode"]), record["_release"]["id"]))
        for name, digest in reservation["assetHashes"].items():
            assets = [a for a in record["_assets"] if a["name"] == name]
            if len(assets) != 1:
                raise PublisherError("Published attachment is missing or ambiguous")
            remote = assets[0].get("digest")
            if remote:
                if remote != "sha256:" + digest:
                    raise PublisherError("Published attachment was changed")
            elif sha256(api.asset(assets[0])) != digest:
                raise PublisherError("Published attachment was changed")
    return recovered


def plan():
    policy = configuration()
    event = json.loads(Path(os.environ["GITHUB_EVENT_PATH"]).read_text())
    if not authorized_event(event, os.environ["GITHUB_EVENT_NAME"]):
        raise PublisherError("Untrusted publisher wake-up event")
    output("has_work", False)
    if os.environ.get("DEVELOP_APK_ENABLED") != "true":
        print("Develop APK publishing is paused/not configured; no key access or publication.")
        return
    cert = os.environ.get("HOYI_RELEASE_CERT_SHA256", "").lower()
    floor_text = os.environ.get("HOYI_DISTRIBUTION_VERSION_FLOOR", "")
    if not re.fullmatch(r"[0-9a-f]{64}", cert) or not re.fullmatch(r"0|[1-9][0-9]*", floor_text):
        raise PublisherError("Explicit signing certificate and confirmed distribution version floor are required")
    api, identity = services()
    check_protection(api)
    ledger = Ledger(api, *identity)
    snapshot = ledger.acquire()
    records = inventory(api)
    recovered = check_published(api, snapshot, records)
    for number, release_id in recovered:
        def reconcile(s, n=number, release=release_id):
            s["allocations"][n].update(status="published", releaseId=release)
        snapshot = ledger.update(reconcile)
    previous = previous_public(records, cert)
    if (int(floor_text) < policy["minimumKnownVersionCode"]
            or (not snapshot["allocations"] and int(floor_text) < previous["versionCode"])):
        raise PublisherError("Confirmed initial installed/distributed floor is below the known public baseline")
    ready = []
    for source in pending_sources(policy, snapshot):
        evidence = successful_ci(api, source)
        if evidence:
            ready.append((source, evidence))
    if not ready:
        print("No unpublished merged commits with four successful push checks.")
        return
    if len(ready) > 256:
        raise PublisherError("Backlog exceeds one Actions matrix; explicit recovery required, no commits discarded")
    state = snapshot
    plans = []
    for source, evidence in ready:
        code = allocate_version(int(floor_text), list(state["allocations"].values()), records)
        item = {"source": source, "code": code, "versionName": policy["baseVersion"] + f"-dev.{code}",
                "certificate": cert, "previous": previous, "checks": evidence,
                "approvalReference": policy["approvalReference"], "workflowRun": identity[0], "attempt": identity[1]}
        reservation = {"schemaVersion": 1, "channel": "develop-test", "repository": REPO,
                       "sourceCommit": source, "versionCode": code, "versionName": item["versionName"],
                       "signerCertificateSha256": cert,
                       "approvalReference": item["approvalReference"], "workflowRun": identity[0],
                       "attempt": identity[1], "status": "reserved"}
        state = ledger.update(lambda s, r=reservation: s["allocations"].update({str(r["versionCode"]): r}))
        plans.append(item)
    Path(os.environ["RUNNER_TEMP"], "develop-plans.json").write_text(json.dumps(plans))
    output("matrix", {"include": plans})
    output("has_work", True)
    print(f"Reserved {len(plans)} immutable source/version assignments; failures burn their numbers.")


def check_attempt(item, identity):
    if item.get("workflowRun") != identity[0] or item.get("attempt") != identity[1]:
        raise PublisherError("Stale attempt plan: use Re-run all jobs or a new develop dispatch, never retry a failed signing job alone")


def item_from_env():
    item = json.loads(os.environ["PLAN_JSON"])
    check_attempt(item, (int(os.environ["GITHUB_RUN_ID"]), int(os.environ["GITHUB_RUN_ATTEMPT"])))
    return item


def artifact_directory(item):
    return Path(os.environ["RUNNER_TEMP"], "develop-dist", str(item["code"]))


def tools():
    base = Path(os.environ["ANDROID_HOME"], "build-tools", "35.0.0")
    return base / "aapt", base / "apksigner"


def download_previous():
    configuration()
    item = item_from_env()
    api, _ = services()
    directory = artifact_directory(item)
    directory.mkdir(parents=True, exist_ok=False)
    data = api.asset(item["previous"]["asset"])
    if sha256(data) != item["previous"]["apkSha256"]:
        raise PublisherError("Previous public APK hash mismatch")
    (directory / "previous.apk").write_bytes(data)


def build():
    configuration()
    item = item_from_env()
    source_root = Path(os.environ["SOURCE_ROOT"]).resolve()
    source = subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=source_root, text=True).strip()
    if source != item["source"]:
        raise PublisherError("Build checkout does not match the reserved source")
    directory = artifact_directory(item)
    version = item["versionName"]
    name = f"OpenHOYI-{version}.apk"
    aapt, apksigner = tools()
    previous_path = directory / "previous.apk"
    previous = inspect_apk(previous_path, aapt, apksigner, "alpha", item["certificate"], item["previous"]["sourceCommit"])
    if previous["apkSha256"] != item["previous"]["apkSha256"] or previous["versionCode"] != item["previous"]["versionCode"]:
        raise PublisherError("Actual previous APK differs from its published baseline")
    required = ("HOYI_RELEASE_KEYSTORE_BASE64", "HOYI_RELEASE_STORE_PASSWORD", "HOYI_RELEASE_KEY_ALIAS", "HOYI_RELEASE_KEY_PASSWORD")
    if any(not os.environ.get(key) for key in required):
        raise PublisherError("Persistent release signing secrets are incomplete; no fallback key")
    # Key exists outside both source checkouts and artifact directories, mode 0600.
    with tempfile.TemporaryDirectory(prefix="hoyi-signing-", dir=os.environ["RUNNER_TEMP"]) as secret_dir:
        key = Path(secret_dir, "release.keystore")
        key.write_bytes(base64.b64decode(os.environ["HOYI_RELEASE_KEYSTORE_BASE64"], validate=True))
        key.chmod(0o600)
        environment = dict(os.environ)
        for name_to_remove in ("GH_TOKEN", "GITHUB_TOKEN", "HOYI_RELEASE_KEYSTORE_BASE64"):
            environment.pop(name_to_remove, None)
        environment.update(HOYI_RELEASE_STORE_FILE=str(key), HOYI_RELEASE_CERT_SHA256=item["certificate"])
        subprocess.run(["./gradlew", "--no-daemon", "--console=plain", ":mobile:assembleRelease",
                        f"-PhoyiVersionCode={item['code']}", f"-PhoyiPreviousVersionCode={previous['versionCode']}",
                        f"-PhoyiVersionName={version}"], cwd=source_root, env=environment, check=True)
    actual = source_root / "mobile/build/outputs/apk/release/mobile-release.apk"
    manifest = inspect_apk(actual, aapt, apksigner, "alpha", item["certificate"], source)
    manifest["upgradeComparison"] = compare_upgrade(previous, manifest)
    validate_manifest(manifest, source, item["code"], version, item["certificate"])
    target = directory / name
    shutil.copyfile(actual, target)
    manifest.update(apkFile=name, channel="develop-test", checks=item["checks"], workflowRun=item["workflowRun"],
                    hardwareAcceptanceVerified=False, installationVerified=False, dataPreservationVerified=False,
                    approvalReference=item["approvalReference"])
    (directory / "distribution.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n")
    sums = "".join(sha256((directory / p).read_bytes()) + "  " + p + "\n" for p in (name, "distribution.json"))
    (directory / "SHA256SUMS.txt").write_text(sums)
    print(f"Verified non-debuggable main APK for source {source}, versionCode {item['code']}.")


def tag_record(api, tag):
    ref = api.optional("git/ref/tags/" + tag)
    if ref is None:
        return None
    if ref["object"]["type"] != "tag":
        raise PublisherError("Develop publication requires an annotated tag")
    obj = api.request("git/tags/" + ref["object"]["sha"])
    if obj["object"]["type"] != "commit":
        raise PublisherError("Nested or non-commit tag rejected")
    return json.loads(obj["message"]), obj["object"]["sha"]


def publish_one(api, ledger, item, directory):
    code, source = item["code"], item["source"]
    tag = f"dev-{code}"
    _, state = ledger.read()
    if not ledger.owns(state):
        raise PublisherError("Publication lease lost")
    reservation = state["allocations"][str(code)]
    validate_reservation(tag, reservation, source, REPO)
    if reservation["signerCertificateSha256"] != item["certificate"]:
        raise PublisherError("Reserved signer differs from build plan")
    manifest_path = directory / "distribution.json"
    manifest = json.loads(manifest_path.read_text())
    validate_manifest(manifest, source, code, item["versionName"], item["certificate"])
    apk = directory / manifest["apkFile"]
    actual = inspect_apk(apk, *tools(), "alpha", item["certificate"], source)
    validate_manifest(actual, source, code, item["versionName"], item["certificate"])
    if actual["apkSha256"] != manifest["apkSha256"]:
        raise PublisherError("Build artifact changed before publication")
    files = [apk, manifest_path, directory / "SHA256SUMS.txt"]
    hashes = {p.name: sha256(p.read_bytes()) for p in files}
    expected_sums = "".join(hashes[p.name] + "  " + p.name + "\n" for p in files[:2])
    if files[2].read_text() != expected_sums:
        raise PublisherError("Artifact checksum list is inconsistent")
    if successful_ci(api, source) is None:
        raise PublisherError("Required source checks no longer successful")
    immutable = {key: value for key, value in reservation.items()
                 if key not in ("status", "releaseId", "manifestSha256", "assetHashes")}
    existing_tag = tag_record(api, tag)
    if existing_tag:
        validate_reservation(tag, existing_tag[0], existing_tag[1], REPO)
        if existing_tag[0] != immutable or existing_tag[1] != source:
            raise PublisherError("Existing tag does not match the immutable reservation")
    else:
        # Version reservation is already durable; tags appear only after actual APK verification.
        obj = api.request("git/tags", "POST", {"tag": tag, "message": json.dumps(immutable, sort_keys=True),
                          "object": source, "type": "commit"})
        api.request("git/refs", "POST", {"ref": "refs/tags/" + tag, "sha": obj["sha"]})
    actual_tag = tag_record(api, tag)
    if actual_tag is None or actual_tag[1] != source:
        raise PublisherError("Actual tag target does not match the build")
    release = api.optional("releases/tags/" + tag)
    if release is None:
        release = api.request("releases", "POST", {
            "tag_name": tag, "target_commitish": source, "name": "OpenHOYI Develop 测试版 · " + str(code),
            "body": f"自动测试包，非正式稳定版。\n\n源码：`{source}`\n\n"
                    "新增设备协议仍待实机验收；不代表已验证硬件控制、覆盖安装或数据保留。\n"
                    "请核对签名及版本后自行选择安装；不要卸载清除旧数据。",
            "draft": True, "prerelease": True, "make_latest": "false"})
    if release.get("prerelease") is not True or release["tag_name"] != tag:
        raise PublisherError("Wrong release channel")
    assets = list(api.pages(f"releases/{release['id']}/assets"))
    for path in files:
        same = [asset for asset in assets if asset["name"] == path.name]
        if len(same) > 1:
            raise PublisherError("Ambiguous release attachment")
        if same:
            if sha256(api.asset(same[0])) != hashes[path.name]:
                raise PublisherError("Existing attachment differs; never overwrite, use a new reservation")
        elif release["draft"]:
            api.request(f"releases/{release['id']}/assets", "POST", path.read_bytes(), upload_name=path.name)
        else:
            raise PublisherError("Public release is incomplete; never modify its assets")
    uploaded = list(api.pages(f"releases/{release['id']}/assets"))
    if {a["name"] for a in uploaded} != set(hashes) or len(uploaded) != len(hashes):
        raise PublisherError("Release attachment set is not exact")
    for asset in uploaded:
        if sha256(api.asset(asset)) != hashes[asset["name"]]:
            raise PublisherError("Uploaded attachment read-back mismatch")
    if release["draft"]:
        if os.environ.get("DEVELOP_APK_ENABLED") != "true":
            raise PublisherError("Publisher paused before public release")
        public = [r for r in inventory(api) if not r["_release"]["draft"]]
        if any(r["versionCode"] >= code for r in public):
            raise PublisherError("Another channel overtook this version; allocate/build a newer package")
        if successful_ci(api, source) is None:
            raise PublisherError("Required source checks changed before public release")
        def prepared(s):
            s["allocations"][str(code)].update(status="prepared", manifestSha256=hashes["distribution.json"], assetHashes=hashes)
        ledger.update(prepared)
        # Single visibility transition, only after read-back AND durable hashes.
        # If the response is lost, the next controller reconciles this exact set.
        api.request(f"releases/{release['id']}", "PATCH", {"draft": False, "prerelease": True, "make_latest": "false"})
    confirmed = api.request(f"releases/{release['id']}")
    if confirmed["draft"] or confirmed["prerelease"] is not True:
        raise PublisherError("Publication outcome not confirmed")
    def done(s):
        s["allocations"][str(code)].update(status="published", releaseId=release["id"],
                                          manifestSha256=hashes["distribution.json"], assetHashes=hashes)
    ledger.update(done)
    print(f"Published {tag} for {source}; no installation performed.")


def artifact_directories(root, plans):
    """download-artifact v7 flattens a lone match, but names multiple matches."""
    named = {item["code"]: root / f"develop-apk-{item['attempt']}-{item['code']}" for item in plans}
    flat = root / "distribution.json"
    if flat.is_file():
        if any((directory / "distribution.json").is_file() for directory in named.values()):
            raise PublisherError("Ambiguous flat and named artifact layouts")
        manifest = json.loads(flat.read_text())
        if not isinstance(manifest, dict):
            raise PublisherError("Flattened artifact manifest is not an object")
        matches = [item for item in plans if item["code"] == manifest.get("versionCode")]
        if len(matches) != 1:
            raise PublisherError("Flattened artifact does not match exactly one current plan")
        item = matches[0]
        validate_manifest(manifest, item["source"], item["code"], item["versionName"], item["certificate"])
        return {item["code"]: root}
    return {code: directory for code, directory in named.items() if (directory / "distribution.json").is_file()}


def publish():
    configuration()
    api, identity = services()
    check_protection(api)
    ledger = Ledger(api, *identity)
    plans = json.loads(Path(os.environ["PLANS_FILE"]).read_text())
    # Validate every attempt before any publication, even when a build is missing.
    for item in plans:
        check_attempt(item, identity)
    # Only this very run AND attempt, never old/failed-attempt or PR output.
    directories = artifact_directories(Path(os.environ["ARTIFACTS_ROOT"]), plans)
    if not directories:
        raise PublisherError("No built artifacts available; reservations retained, nothing published")
    for item in sorted(plans, key=lambda item: item["code"]):
        directory = directories.get(item["code"])
        if directory is None:
            print(f"Build {item['code']} unavailable; reservation retained, not published.")
            continue
        publish_one(api, ledger, item, directory)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("mode", choices=("plan", "download-previous", "build", "publish", "release-lease"))
    args = parser.parse_args()
    try:
        if args.mode == "release-lease":
            configuration()
            api, identity = services()
            Ledger(api, *identity).release()
        else:
            {"plan": plan, "download-previous": download_previous, "build": build, "publish": publish}[args.mode]()
    except (ValueError, RuntimeError, KeyError, OSError, subprocess.SubprocessError) as error:
        # Only controlled constant diagnostics/API status are safe for output;
        # never echo arbitrary SDK, HTTP-body or signing-secret-derived errors.
        detail = str(error) if isinstance(error, (PublisherError, ApiError)) else type(error).__name__
        parser.exit(1, f"Develop publication stopped: {detail}; no fallback.\n")


if __name__ == "__main__":
    main()
