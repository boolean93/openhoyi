#!/usr/bin/env python3
"""Capture the real, seeded Home on a fresh CI AVD only; no physical devices."""
import json
import os
from pathlib import Path
import re
import struct
import subprocess
import time

from shared_mock_artifact import APP, TEST, DESTINATIONS, digest, inspect_apk, require, run


PROFILES = {"phone": (720, 1560, 320), "phone-short": (720, 1120, 320), "tablet": (2560, 1600, 360)}
RUNNER = TEST + "/io.openhoyi.mobile.BrewAudioInstrumentation"
MARKER = "QUICK_CURVE_VISUAL_PASSED androidHome=true tap48=true noBle=true noShot=true"


def png_size(image):
    if len(image) <= 10000 or image[:8] != b"\x89PNG\r\n\x1a\n":
        return None
    return struct.unpack(">II", image[16:24])


def display_value(report, kind):
    # Android prints Physical and, when set, Override; the last line is authoritative.
    value = r"[0-9]+x[0-9]+" if kind == "size" else r"[0-9]+"
    if kind not in ("size", "density"):
        raise ValueError("Only validated display properties are accepted")
    rows = [match.group(1) for line in report.splitlines()
            if (match := re.fullmatch(rf"(?:Physical|Override) {kind}: ({value})", line.strip()))]
    require(bool(rows), f"Cannot read emulator display {kind}")
    return rows[-1]


def validate_capture(image, geometry, width, height, dpi, theme, background="wallpaper", empty_recent=False):
    require(png_size(image) == (width, height), "Wrong screenshot PNG geometry")
    require(geometry["density"] == dpi / 160 and geometry["dark"] == (theme == "dark") and
            geometry["portrait"] == (height > width), "Android view proof did not match expected configuration")
    require(geometry["wallpaper"] == (background == "wallpaper"), "Wrong captured backdrop")
    cards = geometry["items"]
    if empty_recent:
        require(geometry["emptyRecent"] and len(cards) == 5 and cards[2]["name"] == "HOYI Limit Extraction" and
                cards[4]["label"] == "上一杯", "Expected A–D and the existing empty last-cup placeholder")
    else:
        require(len(cards) == 6 and cards[3]["name"] == cards[5]["name"] == "HOYI Limit Extraction",
                "Expected six visible controls including both long titles")


def save_failure(adb, output, name):
    """Keep only bounded crash/runtime evidence from our dedicated synthetic AVD."""
    for suffix, args in (
        ("android-crash", ("logcat", "-b", "crash", "-d", "-t", "180", "-v", "threadtime")),
        ("android-runtime", ("logcat", "-b", "main", "-b", "system", "-d", "-t", "400", "-v", "threadtime",
                             "AndroidRuntime:E", "libc:F", "DEBUG:F", "tombstoned:E",
                             "ActivityManager:W", "ActivityTaskManager:W", "*:S")),
    ):
        path = output / f"{name}-{suffix}.txt"
        try:
            path.write_text(adb(*args, timeout=12)[-32000:] + "\n")
        except (subprocess.SubprocessError, OSError) as diagnostic:
            path.write_text(f"Unavailable: {type(diagnostic).__name__}\n")
    try:
        try:
            image = adb("exec-out", "run-as", APP, "cat", "files/quick-curve-visual/failure-home.png",
                        binary=True, timeout=12)
        except (subprocess.SubprocessError, OSError):
            image = b""
        if png_size(image) is None:
            image = adb("exec-out", "screencap", "-p", binary=True, timeout=12)
        (output / f"{name}-failed.png").write_bytes(image)
    except (subprocess.SubprocessError, OSError):
        pass  # Diagnostics must never conceal the original test/transport failure.


def confirm_display(adb, width, height, dpi):
    deadline = time.monotonic() + 30
    expected = (f"{width}x{height}", str(dpi))
    while time.monotonic() < deadline:
        size = display_value(adb("shell", "wm", "size", timeout=5), "size")
        density = display_value(adb("shell", "wm", "density", timeout=5), "density")
        if (size, density) == expected:
            frame = adb("exec-out", "screencap", "-p", binary=True, timeout=10)
            if png_size(frame) == (width, height):
                return {"width": width, "height": height, "dpi": dpi}
        time.sleep(1)
    raise ValueError("AVD did not settle at the required size, dpi and orientation before instrumentation")


def main():
    require(os.environ.get("GITHUB_ACTIONS") == "true" and os.environ.get("GITHUB_EVENT_NAME") == "workflow_dispatch"
            and os.environ.get("QUICK_CURVE_VISUAL") == "true", "Manual CI runner required")
    run_id, attempt = os.environ["GITHUB_RUN_ID"], os.environ["GITHUB_RUN_ATTEMPT"]
    profile = os.environ["QUICK_CURVE_PROFILE"]
    require(profile in PROFILES, "One profile per dedicated AVD is required")
    width, height, dpi = PROFILES[profile]
    avd = os.environ["QUICK_CURVE_AVD"]
    require(run_id.isdecimal() and attempt.isdecimal() and avd == f"openhoyi-quick-curve-{run_id}-{attempt}-{profile}",
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
    receipt = dict(sourceCommit=sha, runId=run_id, runAttempt=attempt, avd=avd, profile=profile, apks=apks,
                   passed=False, mockOnly=True, noBle=True, noPhysicalDevice=True, screenshots={})
    settings_keys = ("accelerometer_rotation", "user_rotation", "font_scale")
    previous_settings = {key: adb("shell", "settings", "get", "system", key) for key in settings_keys}
    require(all(value == "null" or re.fullmatch(r"[0-9]+(?:[.][0-9]+)?", value)
                for value in previous_settings.values()), "Unexpected fresh emulator display settings")
    name = f"{profile}-startup"
    try:
        # All display mutations happen before either APK is installed. No phone display,
        # settings, app process or app data ever exists on the dedicated tablet runner.
        adb("shell", "settings", "put", "system", "accelerometer_rotation", "0")
        adb("shell", "settings", "put", "system", "user_rotation", "0")
        adb("shell", "settings", "put", "system", "font_scale", "1.0")
        adb("shell", "wm", "size", f"{width}x{height}")
        adb("shell", "wm", "density", str(dpi))
        receipt["initialDisplay"] = confirm_display(adb, width, height, dpi)
        for kind, source in DESTINATIONS.items():
            require(digest(source) == apks[kind]["sha256"], "APK changed before install")
            require(adb("install", source, timeout=120).splitlines()[-1] == "Success", "Mock fixture install failed")
        if os.environ.get("QUICK_CURVE_BOILER_VIEW") == "true" and profile == "phone":
            name = f"{profile}-boiler-view"
            report = adb("shell", "am", "instrument", "-w", "-r", "-e", "disposableCiEmulator", "true",
                         "-e", "homeBoilerVisualChecks", "true", RUNNER, timeout=180)
            (output / f"{name}-instrumentation.txt").write_text(report + "\n")
            require(re.search(r"^INSTRUMENTATION_CODE: -1$", report, re.M) and "HOME_BOILER_LAYER_PROOF_PASSED" in report,
                    "Detached boiler layer checks failed")
            receipt["detachedBoilerViewAndFakeGattPassed"] = True
        cases = [("wallpaper", "dark")] if profile == "phone-short" else [
            (background, theme) for background in ("wallpaper", "solid") for theme in ("light", "dark")]
        fixtures = [(background, theme, False) for background, theme in cases]
        if profile == "phone":
            fixtures.append(("wallpaper", "dark", True))
        for background, theme, empty_recent in fixtures:
            name = f"{profile}-{theme}" + ("-solid" if background == "solid" else "") + ("-empty-recent" if empty_recent else "")
            # All data in this exact ephemeral install is synthetic and fixture-owned.
            require(adb("shell", "pm", "clear", APP) == "Success", "Cannot reset isolated Mock fixture")
            try:
                report = adb("shell", "am", "instrument", "-w", "-r", "-e", "disposableCiEmulator", "true",
                             "-e", "quickCurveVisualTheme", theme, "-e", "quickCurveVisualBackground",
                             background, "-e", "quickCurveShortPortrait", str(profile == "phone-short").lower(),
                             "-e", "quickCurveEmptyRecent", str(empty_recent).lower(), RUNNER, timeout=100)
            except (subprocess.SubprocessError, OSError) as failure:
                # am instrument can disappear with a nonzero exit or a killed process.
                output_text = getattr(failure, "output", None)
                if isinstance(output_text, bytes):
                    output_text = output_text.decode("utf-8", errors="replace")
                (output / f"{name}-instrumentation.txt").write_text(
                    (output_text or "")[-32000:] + f"\nTransport error: {type(failure).__name__}\n")
                raise
            (output / f"{name}-instrumentation.txt").write_text(report + "\n")
            require(re.search(r"^INSTRUMENTATION_CODE: -1$", report, re.M) and MARKER in report,
                    f"{name} instrumentation failed; see preserved log, diagnostics and failure frame")
            image = adb("exec-out", "run-as", APP, "cat", "files/quick-curve-visual/home.png", binary=True)
            geometry = json.loads(adb("exec-out", "run-as", APP, "cat", "files/quick-curve-visual/geometry.json"))
            target = output / f"{name}.png"
            target.write_bytes(image)
            (output / f"{name}.json").write_text(json.dumps(geometry, ensure_ascii=False, indent=2) + "\n")
            validate_capture(image, geometry, width, height, dpi, theme, background, empty_recent)
            receipt["screenshots"][target.name] = digest(target)
        require(len(set(receipt["screenshots"].values())) == 4, "Duplicate capture outputs")
        receipt["passed"] = True
    except (ValueError, KeyError, subprocess.SubprocessError, OSError):
        save_failure(adb, output, name)
        raise
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
