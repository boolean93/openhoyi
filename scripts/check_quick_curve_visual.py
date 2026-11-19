#!/usr/bin/env python3
"""Capture the real, seeded Home on a fresh CI AVD only; no physical devices."""
import json
import os
from pathlib import Path
import re
import struct
import subprocess

from shared_mock_artifact import APP, TEST, DESTINATIONS, digest, inspect_apk, require, run


PROFILES = {"phone": (720, 1560, 320), "tablet": (2560, 1600, 360)}
RUNNER = TEST + "/io.openhoyi.mobile.BrewAudioInstrumentation"
MARKER = "QUICK_CURVE_VISUAL_PASSED androidHome=true tap48=true noBle=true noShot=true"


def validate_capture(image, geometry, width, height, dpi, theme):
    require(len(image) > 10000 and image[:8] == b"\x89PNG\r\n\x1a\n" and
            struct.unpack(">II", image[16:24]) == (width, height), "Wrong screenshot PNG geometry")
    require(geometry["density"] == dpi / 160 and geometry["dark"] == (theme == "dark") and
            geometry["portrait"] == (height > width), "Android view proof did not match expected configuration")
    cards = geometry["items"]
    require(len(cards) == 6 and cards[3]["name"] == cards[5]["name"] == "HOYI Limit Extraction",
            "Expected six visible controls including both long titles")


def main():
    require(os.environ.get("GITHUB_ACTIONS") == "true" and os.environ.get("GITHUB_EVENT_NAME") == "workflow_dispatch"
            and os.environ.get("QUICK_CURVE_VISUAL") == "true", "Manual CI runner required")
    run_id, attempt = os.environ["GITHUB_RUN_ID"], os.environ["GITHUB_RUN_ATTEMPT"]
    avd = os.environ["QUICK_CURVE_AVD"]
    require(run_id.isdecimal() and attempt.isdecimal() and avd == f"openhoyi-quick-curve-{run_id}-{attempt}",
            "Dedicated AVD identity required")
    sha = run("git", "rev-parse", "HEAD")
    require(re.fullmatch(r"[0-9a-f]{40}", sha) and sha == os.environ["GITHUB_SHA"],
            "Checkout differs from dispatched commit")
    run("git", "diff", "--quiet", "HEAD", "--")
    executable = Path(os.environ["ANDROID_HOME"], "platform-tools", "adb")

    def adb(*args, binary=False, timeout=60):
        result = subprocess.run([str(executable), "-s", "emulator-5554", *args], check=True,
                                capture_output=True, timeout=timeout).stdout
        return result if binary else result.decode("utf-8", errors="replace").strip()

    require(adb("get-state") == "device" and adb("shell", "getprop", "ro.kernel.qemu") == "1" and
            adb("shell", "getprop", "ro.hardware") in ("goldfish", "ranchu") and
            adb("emu", "avd", "name").splitlines()[:1] == [avd], "Fresh named emulator required")
    packages = set(adb("shell", "pm", "list", "packages").splitlines())
    require({f"package:{APP}", f"package:{TEST}"}.isdisjoint(packages), "Mock fixture is already installed")

    apks = {kind: dict(inspect_apk(source, kind), sha256=digest(source)) for kind, source in DESTINATIONS.items()}
    require(apks["app.apk"]["certificateSha256"] == apks["test.apk"]["certificateSha256"],
            "Mock APK pair signer mismatch")
    output = Path("build/mock-ui-screenshots")
    output.mkdir(parents=True, exist_ok=False)
    receipt = dict(sourceCommit=sha, runId=run_id, runAttempt=attempt, avd=avd, apks=apks,
                   passed=False, mockOnly=True, noBle=True, noPhysicalDevice=True, screenshots={})
    settings_keys = ("accelerometer_rotation", "user_rotation", "font_scale")
    previous_settings = {key: adb("shell", "settings", "get", "system", key) for key in settings_keys}
    require(all(value == "null" or re.fullmatch(r"[0-9]+(?:[.][0-9]+)?", value)
                for value in previous_settings.values()), "Unexpected fresh emulator display settings")
    try:
        for kind, source in DESTINATIONS.items():
            require(digest(source) == apks[kind]["sha256"], "APK changed before install")
            require(adb("install", source, timeout=120).splitlines()[-1] == "Success", "Mock fixture install failed")
        adb("shell", "settings", "put", "system", "accelerometer_rotation", "0")
        adb("shell", "settings", "put", "system", "user_rotation", "0")
        adb("shell", "settings", "put", "system", "font_scale", "1.0")
        for profile, (width, height, dpi) in PROFILES.items():
            adb("shell", "wm", "size", f"{width}x{height}")
            adb("shell", "wm", "density", str(dpi))
            for theme in ("light", "dark"):
                name = f"{profile}-{theme}"
                # All data in this exact ephemeral install is synthetic and fixture-owned.
                require(adb("shell", "pm", "clear", APP) == "Success", "Cannot reset isolated Mock fixture")
                report = adb("shell", "am", "instrument", "-w", "-r", "-e", "disposableCiEmulator", "true",
                             "-e", "quickCurveVisualTheme", theme, RUNNER, timeout=100)
                (output / f"{name}-instrumentation.txt").write_text(report + "\n")
                if not re.search(r"^INSTRUMENTATION_CODE: -1$", report, re.M) or MARKER not in report:
                    (output / f"{name}-failed.png").write_bytes(adb("exec-out", "screencap", "-p", binary=True))
                    raise ValueError(f"{name} instrumentation failed; see preserved log and failure frame")
                image = adb("exec-out", "run-as", APP, "cat", "files/quick-curve-visual/home.png", binary=True)
                geometry = json.loads(adb("exec-out", "run-as", APP, "cat", "files/quick-curve-visual/geometry.json"))
                target = output / f"{name}.png"
                target.write_bytes(image)
                (output / f"{name}.json").write_text(json.dumps(geometry, ensure_ascii=False, indent=2) + "\n")
                validate_capture(image, geometry, width, height, dpi, theme)
                receipt["screenshots"][target.name] = digest(target)
        require(len(set(receipt["screenshots"].values())) == 4, "Duplicate capture outputs")
        receipt["passed"] = True
    finally:
        cleanup_errors = []
        def restore(*command):
            try:
                adb("shell", *command)
            except (subprocess.SubprocessError, OSError) as exc:
                cleanup_errors.append(str(exc))
        restore("wm", "size", "reset")
        restore("wm", "density", "reset")
        for key, previous in previous_settings.items():
            if previous == "null":
                restore("settings", "delete", "system", key)
            else:
                restore("settings", "put", "system", key, previous)
        receipt["cleanupErrors"] = cleanup_errors
        receipt["passed"] = receipt["passed"] and not cleanup_errors
        (output / "quick-curve-receipt.json").write_text(json.dumps(receipt, indent=2) + "\n")
    require(receipt["passed"], "Display restoration failed; see quick-curve receipt")


if __name__ == "__main__":
    main()
