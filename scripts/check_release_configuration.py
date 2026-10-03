#!/usr/bin/env python3
"""Exercise real Gradle release guards using only a temporary test signing key."""
import argparse
import hashlib
import os
from pathlib import Path
import shutil
import subprocess
import tempfile

from apk_distribution import inspect_apk


ROOT = Path(__file__).resolve().parents[1]
PREFIX = "HOYI_RELEASE_"
VERSION = ["-PhoyiVersionCode=2", "-PhoyiVersionName=0.2.0", "-PhoyiPreviousVersionCode=1"]


def check_case(label, environment, options=VERSION, expected=None, task=":mobile:verifyReleaseDistribution"):
    command = [str(ROOT / "gradlew"), task, "--offline", "--console=plain", *options]
    result = subprocess.run(command, cwd=ROOT, env=environment, capture_output=True, text=True, timeout=180)
    output = result.stdout + result.stderr
    if expected is None:
        passed = result.returncode == 0 and "Release distribution configuration verified" in output
    else:
        passed = result.returncode != 0 and expected in output
    if not passed:
        # Never forward Gradle output from a process with secret signing variables.
        raise RuntimeError(f"Release configuration check failed: {label}; exit={result.returncode}")
    print(f"PASS {label}", flush=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--assemble-fixture", action="store_true", help="Build and verify one disposable release APK")
    parser.add_argument("--sdk", type=Path, help="Android SDK path required for disposable APK verification")
    parser.add_argument("--failure-log", type=Path, help="Optional redacted disposable build diagnostics")
    parser.add_argument("--google-mirror", choices=["aliyun"], help="Use the project's existing optional cached mirror")
    args = parser.parse_args()
    if args.assemble_fixture and args.sdk is None:
        parser.error("--assemble-fixture requires --sdk")
    clean = {key: value for key, value in os.environ.items() if not key.startswith(PREFIX)}
    check_case("missing signing settings", clean, expected="Release signing configuration is incomplete")
    check_case("release build is guarded", clean, expected="Release signing configuration is incomplete", task=":mobile:assembleRelease")
    check_case("missing explicit version", clean, options=[], expected="Release builds require explicit version properties")
    check_case("non-increasing version", clean, options=VERSION[:-1] + ["-PhoyiPreviousVersionCode=2"], expected="Release version must be greater than the declared previous version")
    check_case("invalid version code", clean, options=["-PhoyiVersionCode=0"], expected="hoyiVersionCode must be a positive Android version code")
    check_case("invalid version name", clean, options=["-PhoyiVersionName=invalid name"], expected="hoyiVersionName must be a version identifier")
    with tempfile.TemporaryDirectory(prefix="hoyi-release-check-") as directory:
        key = Path(directory) / "fixture.jks"
        environment = {**clean, "HOYI_FIXTURE_STORE_PASSWORD": "temporary-fixture-password"}
        subprocess.run(["keytool", "-genkeypair", "-storetype", "JKS", "-keystore", str(key),
                        "-storepass:env", "HOYI_FIXTURE_STORE_PASSWORD", "-keypass:env", "HOYI_FIXTURE_STORE_PASSWORD",
                        "-alias", "release-fixture", "-dname", "CN=OpenHOYI Temporary Test", "-keyalg", "RSA", "-keysize", "2048", "-validity", "1"],
                       env=environment, check=True, capture_output=True, timeout=60)
        exported = subprocess.run(["keytool", "-exportcert", "-keystore", str(key), "-storepass:env", "HOYI_FIXTURE_STORE_PASSWORD", "-alias", "release-fixture"],
                                  env=environment, check=True, capture_output=True, timeout=60).stdout
        signed = {**clean, "HOYI_RELEASE_STORE_FILE": str(key), "HOYI_RELEASE_STORE_PASSWORD": environment["HOYI_FIXTURE_STORE_PASSWORD"],
                  "HOYI_RELEASE_KEY_ALIAS": "release-fixture", "HOYI_RELEASE_KEY_PASSWORD": environment["HOYI_FIXTURE_STORE_PASSWORD"],
                  "HOYI_RELEASE_CERT_SHA256": hashlib.sha256(exported).hexdigest()}
        check_case("valid external signing configuration", signed)
        check_case("wrong certificate", {**signed, "HOYI_RELEASE_CERT_SHA256": "0" * 64}, expected="Release signing certificate does not match the expected identity")
        check_case("wrong key password", {**signed, "HOYI_RELEASE_KEY_PASSWORD": "wrong-test-password"}, expected="Release signing keystore cannot be opened")
        (ROOT / "build").mkdir(exist_ok=True)
        with tempfile.TemporaryDirectory(prefix="release-key-fixture-", dir=ROOT / "build") as inside:
            internal = Path(inside) / "fixture.jks"
            shutil.copyfile(key, internal)
            check_case("repository signing key rejected", {**signed, "HOYI_RELEASE_STORE_FILE": str(internal)}, expected="Release signing key must be outside the repository")
        if args.assemble_fixture:
            # Keep every project's release outputs away from users' build artifacts.
            outputs = Path(directory) / "outputs"
            escaped = str(outputs).replace("\\", "\\\\").replace("'", "\\'")
            init = Path(directory) / "isolated.gradle"
            init.write_text(f"allprojects {{ layout.buildDirectory.set(new File('{escaped}', name)) }}\n")
            tools = args.sdk / "build-tools" / "35.0.0"
            command = [str(ROOT / "gradlew"), ":mobile:assembleRelease", "--offline", "--console=plain", "--init-script", str(init),
                       f"-Pandroid.aapt2FromMavenOverride={tools / 'aapt2'}", *VERSION]
            if args.google_mirror:
                command.append(f"-PgoogleMirror={args.google_mirror}")
            built = subprocess.run(command, cwd=ROOT, env=signed, capture_output=True, text=True, timeout=360)
            if built.returncode != 0:
                if args.failure_log:
                    diagnostics = built.stdout + built.stderr
                    for value in sorted(set(signed[key] for key in signed if key.startswith(PREFIX)), key=len, reverse=True):
                        diagnostics = diagnostics.replace(value, "<redacted-fixture-signing-value>")
                    args.failure_log.write_text(diagnostics)
                raise RuntimeError(f"Disposable release APK build failed; exit={built.returncode}")
            apk = outputs / "mobile" / "outputs" / "apk" / "release" / "mobile-release.apk"
            commit = subprocess.run(["git", "rev-parse", "HEAD"], cwd=ROOT, capture_output=True, text=True, check=True).stdout.strip()
            metadata = inspect_apk(apk, tools / "aapt", tools / "apksigner", "alpha", signed["HOYI_RELEASE_CERT_SHA256"], commit)
            if metadata["debuggable"] or metadata["versionCode"] != 2 or metadata["versionName"] != "0.2.0":
                raise RuntimeError("Disposable release APK identity is incorrect")
            print("RELEASE_APK_FIXTURE_CHECKS_PASSED signed=true version=2 isolatedOutputs=true noInstall=true", flush=True)
    for task in [":mobile:assembleRelease", ":mobile:bundleRelease"]:
        graph = subprocess.run([str(ROOT / "gradlew"), task, "--offline", "--console=plain", "--dry-run", *VERSION],
                               cwd=ROOT, env=clean, capture_output=True, text=True, timeout=180)
        guard = ":mobile:verifyReleaseDistribution SKIPPED"
        prebuild = ":mobile:preReleaseBuild SKIPPED"
        if graph.returncode != 0 or guard not in graph.stdout or prebuild not in graph.stdout or graph.stdout.index(guard) > graph.stdout.index(prebuild):
            raise RuntimeError("Release task graph omitted signing preflight")
    print("RELEASE_CONFIGURATION_CHECKS_PASSED cases=12 temporaryKey=true noInstall=true", flush=True)


if __name__ == "__main__":
    main()
