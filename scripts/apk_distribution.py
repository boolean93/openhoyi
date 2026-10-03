#!/usr/bin/env python3
"""Verify one APK's identity before exporting its public distribution manifest."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import tempfile


PACKAGES = {"alpha": "io.openhoyi.mobile", "mock": "io.openhoyi.mobile.mock"}


def inspect_metadata(badging, signatures, variant, expected_certificate):
    if variant not in PACKAGES:
        raise ValueError("Unknown variant")
    if not re.fullmatch(r"[0-9a-fA-F]{64}", expected_certificate):
        raise ValueError("Expected certificate must be a SHA256 hex digest")
    packages = [line for line in badging.splitlines() if line.startswith("package: ")]
    if len(packages) != 1:
        raise ValueError("Expected exactly one APK package")
    match = re.match(r"package: name='([^']+)' versionCode='([0-9]+)' versionName='([^']+)'(?: |$)", packages[0])
    if not match:
        raise ValueError("Invalid APK version metadata")
    name, code, version = match.groups()
    if name != PACKAGES[variant] or not 1 <= int(code) <= 2147483647:
        raise ValueError("APK package or versionCode is not allowed")
    certificates = re.findall(r"^Signer #(\d+) certificate SHA-256 digest: ([0-9a-fA-F]{64})$", signatures, re.MULTILINE)
    if len(certificates) != 1 or certificates[0][0] != "1":
        raise ValueError("Expected exactly one APK signing certificate")
    certificate = certificates[0][1].lower()
    if certificate != expected_certificate.lower():
        raise ValueError("APK signing certificate does not match expected identity")
    return {"variant": variant, "packageName": name, "versionCode": int(code),
            "versionName": version, "debuggable": "application-debuggable" in badging.splitlines(),
            "signerCertificateSha256": certificate}


def inspect_apk(apk, aapt, apksigner, variant, expected_certificate, source_commit):
    apk = Path(apk)
    if not re.fullmatch(r"[0-9a-fA-F]{40}", source_commit):
        raise ValueError("Declared source commit must be a full Git SHA1")
    if not apk.is_file() or apk.suffix.lower() != ".apk":
        raise ValueError("Expected an existing .apk file")
    before = hashlib.sha256(apk.read_bytes()).hexdigest()
    # apksigner's nonzero result rejects unsigned, damaged, or unverifiable APKs.
    signatures = subprocess.run([str(apksigner), "verify", "--print-certs", str(apk)],
                                check=True, capture_output=True, text=True, timeout=60).stdout
    badging = subprocess.run([str(aapt), "dump", "badging", str(apk)],
                            check=True, capture_output=True, text=True, timeout=60).stdout
    result = inspect_metadata(badging, signatures, variant, expected_certificate)
    if hashlib.sha256(apk.read_bytes()).hexdigest() != before:
        raise ValueError("APK changed while being inspected")
    return {"schemaVersion": 1, **result, "apkFile": apk.name, "apkSha256": before,
            # APKs currently do not embed the build commit; do not claim it was verified.
            "declaredSourceCommit": source_commit.lower(), "sourceCommitVerified": False}


def compare_upgrade(previous, current):
    if previous["packageName"] != current["packageName"]:
        raise ValueError("Upgrade package identity does not match")
    if previous["signerCertificateSha256"] != current["signerCertificateSha256"]:
        raise ValueError("Upgrade signing identity does not match")
    if current["versionCode"] <= previous["versionCode"]:
        raise ValueError("Upgrade versionCode must increase")
    return {"previousApkSha256": previous["apkSha256"], "previousVersionCode": previous["versionCode"],
            "identityAndVersionCompatible": True, "installationVerified": False, "dataPreservationVerified": False}


def write_manifest(output, result, apk, overwrite=False):
    output = Path(output)
    if output.suffix.lower() != ".json" or output.resolve() == Path(apk).resolve():
        raise ValueError("Manifest must be a separate .json file")
    # Recheck the declared hash before output; never write an unvalidated manifest.
    if hashlib.sha256(Path(apk).read_bytes()).hexdigest() != result["apkSha256"]:
        raise ValueError("APK changed before manifest export")
    temporary = None
    try:
        with tempfile.NamedTemporaryFile(mode="w", encoding="utf-8", dir=output.parent,
                                         prefix=".apk-manifest-", delete=False) as stream:
            temporary = Path(stream.name)
            json.dump(result, stream, ensure_ascii=False, indent=2)
            stream.write("\n")
        if overwrite:
            os.replace(temporary, output)
        else:
            # Same-directory hard link publishes atomically without replacing a file
            # that appeared between inspection and export.
            os.link(temporary, output)
    finally:
        if temporary is not None:
            temporary.unlink(missing_ok=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apk", type=Path, required=True)
    parser.add_argument("--previous-apk", type=Path, help="Optional actual previous APK for identity/version comparison")
    parser.add_argument("--variant", choices=PACKAGES, required=True)
    parser.add_argument("--expected-cert-sha256", required=True)
    parser.add_argument("--source-commit", required=True)
    parser.add_argument("--aapt", type=Path, required=True)
    parser.add_argument("--apksigner", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--overwrite", action="store_true", help="Explicitly replace an existing manifest")
    args = parser.parse_args()
    try:
        result = inspect_apk(args.apk, args.aapt, args.apksigner, args.variant,
                             args.expected_cert_sha256, args.source_commit)
        if args.previous_apk:
            previous = inspect_apk(args.previous_apk, args.aapt, args.apksigner, args.variant,
                                   args.expected_cert_sha256, args.source_commit)
            result["upgradeComparison"] = compare_upgrade(previous, result)
        write_manifest(args.output, result, args.apk, overwrite=args.overwrite)
    except (ValueError, OSError, subprocess.CalledProcessError, subprocess.TimeoutExpired) as error:
        # Do not forward arbitrary tool output or signing-tool environment values.
        parser.exit(1, f"APK manifest rejected: {type(error).__name__}\n")
    print(json.dumps(result, ensure_ascii=False))


if __name__ == "__main__":
    main()
