#!/usr/bin/env python3
"""Offline mirror PoC: validate local public-release bytes and print a plan.

No network, credentials, uploads, release creation, or remote manifest changes.
This does NOT replace APK signature inspection in the trusted publish workflow.
"""
import argparse
import hashlib
import json
from pathlib import Path
import re

from release_update_index import validate_index

DESTINATION = "https://gitee.com/boolean93/openhoyi"
MAX_ATTACHMENT = 100_000_000  # conservative interpretation of Gitee's 100M cap


def local_file(directory, name, limit):
    if not re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9._-]*", name) or name in (".", ".."):
        raise ValueError("Unsafe asset name")
    path = directory / name
    if path.is_symlink() or not path.is_file() or path.stat().st_size > limit:
        raise ValueError("Asset is missing, a symlink, or exceeds its limit")
    data = path.read_bytes()
    if not data or len(data) > limit:
        raise ValueError("Invalid asset size")
    return data


def plan(index, directory, previous=None):
    validate_index(index)
    if index["apkSize"] > MAX_ATTACHMENT:
        raise ValueError("APK exceeds Gitee attachment cap")
    if previous is not None:
        validate_index(previous)
        if index["signerCertificateSha256"] != previous["signerCertificateSha256"]:
            raise ValueError("Signing identity changed")
        if index["versionCode"] < previous["versionCode"]:
            raise ValueError("Mirror version must not decrease")
        if index["versionCode"] == previous["versionCode"] and index != previous:
            raise ValueError("Existing version identity changed")
    directory = Path(directory).resolve(strict=True)
    names = [index["apkFile"], "distribution.json", "SHA256SUMS.txt"]
    if len(set(names)) != 3 or not names[0].endswith(".apk"):
        raise ValueError("Invalid APK filename")
    files = {name: local_file(directory, name, MAX_ATTACHMENT if name.endswith(".apk") else 1_048_576)
             for name in names}
    digest = hashlib.sha256(files[names[0]]).hexdigest()
    if digest != index["apkSha256"] or len(files[names[0]]) != index["apkSize"]:
        raise ValueError("APK differs from verified index")
    manifest = json.loads(files["distribution.json"])
    for key in ("packageName", "versionCode", "versionName", "apkFile", "apkSha256",
                "signerCertificateSha256", "declaredSourceCommit", "sourceCommitVerified", "channel"):
        if manifest.get(key) != index[key]:
            raise ValueError("Distribution metadata differs from index: " + key)
    if manifest.get("variant") != "alpha" or manifest.get("debuggable") is not False:
        raise ValueError("Not a public release APK manifest")
    expected = "".join(hashlib.sha256(files[n]).hexdigest() + "  " + n + "\n" for n in names[:2])
    if files["SHA256SUMS.txt"] != expected.encode():
        raise ValueError("Checksum inventory differs from local bytes")
    tag = index["releaseTag"]
    return {
        "mode": "offline-plan-only", "repository": "boolean93/openhoyi", "releaseTag": tag,
        "requiresActualApkSignatureVerification": True,
        "assets": [{"name": n, "size": len(files[n]), "sha256": hashlib.sha256(files[n]).hexdigest(),
                    "targetUrl": f"{DESTINATION}/releases/download/{tag}/{n}"} for n in names],
        "orderedSteps": ["verify-public-github-release-and-apk-signature",
                         "ensure-mirror-release-without-overwriting-existing-assets",
                         "upload-missing-identical-assets", "anonymous-download-and-verify-all-assets",
                         "compare-current-manifest-version-and-signing-identity",
                         "commit-updates/dev.json-last-with-compare-and-swap"],
        "candidateManifest": {**index, "mirrorDownloadUrls": [f"{DESTINATION}/releases/download/{tag}/{names[0]}"]},
        "manifestCompatibility": "original GitHub URLs preserved; app support for mirrorDownloadUrls is not implemented",
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--index", type=Path, required=True)
    parser.add_argument("--assets", type=Path, required=True)
    parser.add_argument("--previous-index", type=Path)
    args = parser.parse_args()
    previous = json.loads(args.previous_index.read_text()) if args.previous_index else None
    print(json.dumps(plan(json.loads(args.index.read_text()), args.assets, previous), ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
