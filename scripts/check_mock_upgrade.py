#!/usr/bin/env python3
"""Bounded data-preservation update test; only qemu emulator and the Mock package."""
import argparse
import json
from pathlib import Path
import re
import subprocess

from apk_distribution import compare_upgrade, inspect_apk


PACKAGE = "io.openhoyi.mobile.mock"
RUNNER = "io.openhoyi.mobile.mock.test/io.openhoyi.mobile.BrewAudioInstrumentation"


def require_emulator(serial, kernel_qemu):
    if not re.fullmatch(r"emulator-[0-9]+", serial) or kernel_qemu.strip() != "1":
        raise ValueError("Mock upgrade checks require a qemu emulator")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--serial", required=True)
    parser.add_argument("--sdk", type=Path, required=True)
    parser.add_argument("--old-apk", type=Path, required=True)
    parser.add_argument("--new-apk", type=Path, required=True)
    parser.add_argument("--test-apk", type=Path, required=True)
    parser.add_argument("--expected-cert-sha256", required=True)
    parser.add_argument("--source-commit", required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    if not re.fullmatch(r"emulator-[0-9]+", args.serial):
        parser.error("Only an explicit emulator serial is allowed")
    args.output.mkdir(parents=True, exist_ok=True)
    tools = args.sdk / "build-tools" / "35.0.0"
    adb = args.sdk / "platform-tools" / "adb"
    def command(arguments):
        return subprocess.run([str(adb), "-s", args.serial, *arguments], check=True,
                              capture_output=True, text=True, timeout=180).stdout
    # The only ADB command before this check is a read-only property lookup.
    require_emulator(args.serial, command(["shell", "getprop", "ro.kernel.qemu"]))
    old = inspect_apk(args.old_apk, tools / "aapt", tools / "apksigner", "mock", args.expected_cert_sha256, args.source_commit)
    new = inspect_apk(args.new_apk, tools / "aapt", tools / "apksigner", "mock", args.expected_cert_sha256, args.source_commit)
    comparison = compare_upgrade(old, new)
    if old["versionCode"] != 1 or new["versionCode"] != 2:
        raise ValueError("Mock fixture requires exact version 1 to 2")
    test_certificates = subprocess.run([str(tools / "apksigner"), "verify", "--print-certs", str(args.test_apk)],
                                       check=True, capture_output=True, text=True, timeout=60).stdout
    certificates = re.findall(r"^Signer #(\d+) certificate SHA-256 digest: ([0-9a-fA-F]{64})$", test_certificates, re.MULTILINE)
    test_badging = subprocess.run([str(tools / "aapt"), "dump", "badging", str(args.test_apk)],
                                 check=True, capture_output=True, text=True, timeout=60).stdout
    if len(certificates) != 1 or certificates[0][0] != "1" or certificates[0][1].lower() != args.expected_cert_sha256.lower() or not test_badging.startswith("package: name='io.openhoyi.mobile.mock.test' "):
        raise ValueError("Test APK package or signing identity is not allowed")
    def phase(name):
        output = command(["shell", "am", "instrument", "-w", "-e", "upgradeChecks", name, RUNNER])
        (args.output / f"{name}.txt").write_text(output)
        if f"MOCK_UPGRADE_{name.upper()}_PASSED" not in output or "CHECKS_FAILED" in output or "FAILURE" in output:
            raise RuntimeError(f"Mock upgrade phase failed: {name}")
    try:
        # Never downgrade, clear app data, uninstall, or access another package.
        installed = command(["shell", "pm", "list", "packages", PACKAGE])
        if f"package:{PACKAGE}" in installed.splitlines():
            raise ValueError("Upgrade fixture requires a fresh emulator Mock install")
        (args.output / "install-old.txt").write_text(command(["install", str(args.old_apk)]))
        (args.output / "install-test.txt").write_text(command(["install", str(args.test_apk)]))
        phase("seed")
        command(["shell", "am", "force-stop", PACKAGE])
        (args.output / "install-new.txt").write_text(command(["install", "-r", str(args.new_apk)]))
        phase("verify")
        (args.output / "upgrade.log").write_text(command(["logcat", "-d", "-s", "OpenHoyiUpgrade:I", "AndroidRuntime:E"]))
        (args.output / "result.json").write_text(json.dumps({"oldApk": old, "newApk": new, "identityComparison": comparison,
            "mockInstallationVerified": True, "mockDataPreservationVerified": True,
            "scope": "Same current source/schema Mock 1->2 on qemu; not Alpha, real hardware or historical schema migration"}, indent=2) + "\n")
        print("MOCK_UPGRADE_CHECKS_PASSED versions=1,2 preserved=true pendingBlocked=true noPhysicalDevice=true")
    except Exception:
        try:
            (args.output / "upgrade-failure.log").write_text(command(["logcat", "-d", "-s", "OpenHoyiUpgrade:I", "AndroidRuntime:E"]))
        except Exception:
            pass
        raise


if __name__ == "__main__":
    main()
