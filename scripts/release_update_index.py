#!/usr/bin/env python3
"""Build the public develop-test index from the newest complete, verified release.

Read-only: never publishes APKs, changes Git refs, or installs anything.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import tempfile
import time
import urllib.request
import urllib.error

from apk_distribution import inspect_apk
from develop_release import tag_record
from develop_release_policy import validate_manifest, validate_reservation
from develop_release_store import GitHub, SafeRedirect

REPO = "boolean93/openhoyi"
BASE = "https://github.com/" + REPO
WORKFLOW = ".github/workflows/develop-apk.yml"
INDEX_URL = "https://boolean93.github.io/openhoyi/updates/dev.json"


def authorize_event(event, name, ref):
    if event.get("repository", {}).get("full_name") != REPO or ref != "refs/heads/develop":
        raise ValueError("Only this repository's develop deployment is allowed")
    if name == "push":
        allowed = event.get("ref") == ref and event.get("deleted") is False
    elif name == "workflow_dispatch":
        allowed = True
    elif name == "workflow_run":
        run = event.get("workflow_run", {})
        # Failed/cancelled publishers may already have made some APKs public.
        allowed = (event.get("action") == "completed" and run.get("status") == "completed"
                   and run.get("repository", {}).get("full_name") == REPO
                   and run.get("head_repository", {}).get("full_name") == REPO
                   and run.get("head_branch") == "develop" and run.get("path") == WORKFLOW
                   and run.get("event") in ("push", "workflow_run", "workflow_dispatch"))
    else:
        allowed = False
    if not allowed:
        raise ValueError("Untrusted Pages wake-up event")


def newest_release(releases):
    candidates = []
    for release in releases:
        if not isinstance(release, dict):
            raise ValueError("Invalid release list")
        tag = release.get("tag_name", "")
        if isinstance(tag, str) and re.fullmatch(r"dev-[1-9][0-9]*", tag) and release.get("draft") is False:
            candidates.append((int(tag[4:]), release))
    if not candidates:
        raise ValueError("No public develop release; keep the existing site")
    code = max(code for code, _ in candidates)
    selected = [release for n, release in candidates if n == code]
    if len(selected) != 1 or selected[0].get("prerelease") is not True:
        raise ValueError("Newest dev release is ambiguous or not a prerelease")
    release = selected[0]
    if code > 2147483647 or type(release.get("id")) is not int or release["id"] <= 0:
        raise ValueError("Invalid release identity")
    if release.get("html_url") != BASE + "/releases/tag/" + release["tag_name"]:
        raise ValueError("Release must belong to the fixed repository")
    if not isinstance(release.get("published_at"), str) or not re.fullmatch(
            r"\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}Z", release["published_at"]):
        raise ValueError("Release publication time is missing")
    return release


def public_bytes(url, limit):
    # No Authorization header: the index must not depend on private assets.
    request = urllib.request.Request(url, headers={"User-Agent": "OpenHOYI-Update-Index", "Cache-Control": "no-cache"})
    with urllib.request.build_opener(SafeRedirect()).open(request, timeout=120) as response:
        if response.status != 200:
            raise ValueError("Public resource is not available")
        data = response.read(limit + 1)
    if len(data) > limit:
        raise ValueError("Public resource exceeds size limit")
    return data


def asset_bytes(assets, tag, name, limit, download):
    matches = [asset for asset in assets if asset.get("name") == name]
    url = BASE + "/releases/download/" + tag + "/" + name
    if len(matches) != 1:
        raise ValueError("Release asset is missing or ambiguous")
    asset = matches[0]
    if (asset.get("browser_download_url") != url or asset.get("state") != "uploaded"
            or type(asset.get("size")) is not int or not 0 < asset["size"] <= limit):
        raise ValueError("Invalid public release asset")
    data = download(url, limit)
    digest = hashlib.sha256(data).hexdigest()
    if len(data) != asset["size"] or asset.get("digest") not in (None, "sha256:" + digest):
        raise ValueError("Public release asset size or digest changed")
    return data, url


def build_index(api, certificate, aapt, apksigner, download=public_bytes, inspect=inspect_apk):
    release = newest_release(api.pages("releases"))
    tag = release["tag_name"]
    record = tag_record(api, tag)
    if record is None:
        raise ValueError("Release annotated tag is missing")
    reservation, source = record
    validate_reservation(tag, reservation, source, REPO)
    if reservation["signerCertificateSha256"] != certificate:
        raise ValueError("Release signer differs from approved signer")
    assets = list(api.pages(f"releases/{release['id']}/assets"))
    if any(not isinstance(asset, dict) for asset in assets):
        raise ValueError("Invalid release assets")
    raw, manifest_url = asset_bytes(assets, tag, "distribution.json", 1024 * 1024, download)
    manifest = json.loads(raw)
    version = reservation.get("versionName")
    validate_manifest(manifest, source, int(tag[4:]), version, certificate)
    if manifest.get("channel") != "develop-test":
        raise ValueError("Wrong distribution channel")
    apk, apk_url = asset_bytes(assets, tag, manifest["apkFile"], 256 * 1024 * 1024, download)
    sums, sums_url = asset_bytes(assets, tag, "SHA256SUMS.txt", 65536, download)
    expected_sums = "".join(hashlib.sha256(data).hexdigest() + "  " + name + "\n"
                            for name, data in ((manifest["apkFile"], apk), ("distribution.json", raw)))
    if sums.decode("utf-8") != expected_sums:
        raise ValueError("Checksum inventory differs from public APK and manifest")
    with tempfile.TemporaryDirectory(prefix="openhoyi-index-") as directory:
        path = Path(directory, manifest["apkFile"])
        path.write_bytes(apk)
        actual = inspect(path, aapt, apksigner, "alpha", certificate, source)
    validate_manifest(actual, source, int(tag[4:]), version, certificate)
    if actual["apkSha256"] != manifest["apkSha256"] or actual["apkSha256"] != hashlib.sha256(apk).hexdigest():
        raise ValueError("Actual APK hash differs from manifest")
    # A publication racing the download must never silently produce a lower index.
    if newest_release(api.pages("releases"))["id"] != release["id"]:
        raise ValueError("Public channel advanced during verification; rerun Pages")
    return {"schemaVersion": 1, "channel": "develop-test", "repository": REPO,
            **{key: manifest[key] for key in ("packageName", "versionCode", "versionName", "apkSha256",
                "signerCertificateSha256", "declaredSourceCommit", "sourceCommitVerified")},
            "releaseTag": tag, "publishedAt": release["published_at"], "releaseUrl": release["html_url"],
            "apkFile": manifest["apkFile"], "apkSize": len(apk),
            "downloadUrl": apk_url, "distributionUrl": manifest_url, "checksumsUrl": sums_url}


def validate_index(index):
    if not isinstance(index, dict) or index.get("repository") != REPO or index.get("channel") != "develop-test":
        raise ValueError("Invalid public index schema")
    validate_manifest({**index, "variant": "alpha", "debuggable": False},
                      index.get("declaredSourceCommit"), index.get("versionCode"),
                      index.get("versionName"), index.get("signerCertificateSha256"))
    tag = "dev-" + str(index["versionCode"])
    prefix = BASE + "/releases/download/" + tag + "/"
    if (index.get("releaseTag") != tag or index.get("releaseUrl") != BASE + "/releases/tag/" + tag
            or index.get("downloadUrl") != prefix + index["apkFile"]
            or index.get("distributionUrl") != prefix + "distribution.json"
            or index.get("checksumsUrl") != prefix + "SHA256SUMS.txt"
            or type(index.get("apkSize")) is not int or not 0 < index["apkSize"] <= 256 * 1024 * 1024
            or not isinstance(index.get("publishedAt"), str) or not re.fullmatch(
                r"\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}Z", index["publishedAt"])):
        raise ValueError("Invalid public index URLs or release identity")


def guard_public_high_water(index, download=public_bytes):
    validate_index(index)
    try:
        raw = download(INDEX_URL + "?baseline=" + str(time.time_ns()), 1024 * 1024)
    except urllib.error.HTTPError as error:
        if error.code == 404:
            return  # First index deployment only. All other failures stop deployment.
        raise
    previous = json.loads(raw)
    validate_index(previous)
    if index["versionCode"] < previous["versionCode"]:
        raise ValueError("Public index version must never decrease")
    if index["versionCode"] == previous["versionCode"] and index != previous:
        raise ValueError("Same public version changed identity or metadata")
    if index["signerCertificateSha256"] != previous["signerCertificateSha256"]:
        raise ValueError("Public signing identity changed")


def write_index(path, index):
    path = Path(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = None
    try:
        with tempfile.NamedTemporaryFile("w", encoding="utf-8", dir=path.parent, delete=False) as stream:
            temporary = Path(stream.name)
            json.dump(index, stream, ensure_ascii=False, indent=2)
            stream.write("\n")
        os.replace(temporary, path)
    finally:
        if temporary is not None:
            temporary.unlink(missing_ok=True)


def verify_public(url, expected, attempts=12, delay=10):
    # Fixed deployment destination, never a URL supplied by a release or PR.
    if url != INDEX_URL:
        raise ValueError("Unexpected public index destination")
    for attempt in range(attempts):
        try:
            data = public_bytes(url + "?verify=" + str(time.time_ns()), 1024 * 1024)
            if data == Path(expected).read_bytes():
                return
        except (OSError, ValueError):
            pass
        if attempt + 1 < attempts:
            time.sleep(delay)
    raise ValueError("Pages deployed but public index readback is not confirmed")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("mode", choices=("generate", "verify-public", "authorize"))
    parser.add_argument("--output", type=Path, default=Path("site/updates/dev.json"))
    parser.add_argument("--aapt", type=Path)
    parser.add_argument("--apksigner", type=Path)
    args = parser.parse_args()
    if args.mode == "verify-public":
        verify_public(INDEX_URL, args.output)
        return
    authorize_event(json.loads(Path(os.environ["GITHUB_EVENT_PATH"]).read_text()),
                    os.environ["GITHUB_EVENT_NAME"], os.environ["GITHUB_REF"])
    if args.mode == "authorize":
        return
    cert = os.environ.get("HOYI_RELEASE_CERT_SHA256", "").lower()
    if not re.fullmatch(r"[0-9a-f]{64}", cert) or not args.aapt or not args.apksigner:
        raise ValueError("Approved certificate and APK inspection tools are required")
    index = build_index(GitHub(REPO, os.environ["GH_TOKEN"]), cert, args.aapt, args.apksigner)
    guard_public_high_water(index)
    write_index(args.output, index)
    print("Verified public update index: " + index["releaseTag"])


if __name__ == "__main__":
    try:
        main()
    except (ValueError, KeyError, TypeError, OSError, RuntimeError, subprocess.SubprocessError) as error:
        raise SystemExit("Update index rejected: " + type(error).__name__) from None
