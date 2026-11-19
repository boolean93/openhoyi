#!/usr/bin/env python3
"""Manual CI-only native Live Extraction screenshots on an isolated Mock AVD per profile."""
import json
import math
import os
from pathlib import Path
import re
import struct
import subprocess
import time

from mock_ui_tap import validate_installed_runners
from shared_mock_artifact import APP, TEST, DESTINATIONS, digest, inspect_apk, require, run

PROFILES = {"phone": (720, 1280, 360, "1.3"), "tablet": (2560, 1600, 320, "1.0")}
APP_FONTS = {"phone": ("large", 1.1), "tablet": ("medium", 1.0)}
RUNNER = TEST + "/io.openhoyi.mobile.BrewAudioInstrumentation"
MARKER = ("LIVE_SHORTCUT_WINDOW_PASSED exactSlot=true staleSelectionDenied=true disabledRows=true "
          "preferenceOff=true explicitPreheatNoReplay=true stopPreserved=true noBle=true")
SCREENSHOTS = ("idle.png", "expanded-long-name.png", "running-stop-visible.png")


def png_size(data):
    require(len(data) > 10000 and data[:8] == b"\x89PNG\r\n\x1a\n" and data[12:16] == b"IHDR",
            "Missing or invalid screenshot")
    return struct.unpack(">II", data[16:24])


def display_value(report, prop):
    pattern = r"[0-9]+x[0-9]+" if prop == "size" else r"[0-9]+"
    match = re.findall(rf"^(?:Physical|Override) {prop}: ({pattern})$", report, re.M)
    require(1 <= len(match) <= 2, f"Missing or ambiguous emulator {prop} override")
    return match[-1]


def validate_viewport(viewport, profile):
    width, height, density, _ = PROFILES[profile]
    app_font, app_scale = APP_FONTS[profile]
    app_width, app_height = viewport["widthDp"], viewport["heightDp"]
    # Android Configuration may deduct system insets, especially from height. Prove
    # the narrow phone width exactly; allow bars only in the secondary dimensions.
    logical_width, logical_height = width * 160 / density, height * 160 / density
    require(viewport["densityDpi"] == density and viewport["appFont"] == app_font
            and math.isclose(viewport["fontScale"], app_scale, abs_tol=0.001)
            and viewport["portrait"] == (profile == "phone"), "Instrumentation density / font / orientation mismatch")
    min_width = 320 if profile == "phone" else 1200
    require(min_width <= app_width <= math.ceil(logical_width) and
            logical_height - 128 <= app_height <= math.ceil(logical_height), "Wrong app viewport")


def main():
    env = os.environ
    require(env.get("GITHUB_ACTIONS") == "true" and env.get("GITHUB_EVENT_NAME") == "workflow_dispatch"
            and env.get("RUNNER_ENVIRONMENT") == "github-hosted"
            and env.get("LIVE_SHORTCUT_VISUAL") == "true", "Explicit manual GitHub-hosted QA only")
    run_id, attempt, profile = env["GITHUB_RUN_ID"], env["GITHUB_RUN_ATTEMPT"], env["LIVE_SHORTCUT_PROFILE"]
    require(run_id.isdecimal() and attempt.isdecimal() and profile in PROFILES, "Invalid CI identity/profile")
    width, height, density, font = PROFILES[profile]
    avd = env["LIVE_SHORTCUT_AVD"]
    require(avd == f"openhoyi-live-slots-{run_id}-{attempt}-{profile}", "Dedicated AVD required")
    sha = run("git", "rev-parse", "HEAD")
    require(re.fullmatch(r"[0-9a-f]{40}", sha) and sha == env["GITHUB_SHA"], "Checkout differs from dispatch")
    run("git", "diff", "--quiet", "HEAD", "--")
    adb_path = str(Path(env["ANDROID_HOME"], "platform-tools", "adb"))
    devices = run(adb_path, "devices").splitlines()[1:]
    require([line.split() for line in devices if line.strip()] == [["emulator-5554", "device"]],
            "Exactly one isolated synthetic emulator must be attached")

    def adb(*args, binary=False, timeout=45):
        response = subprocess.run([adb_path, "-s", "emulator-5554", *args], check=True,
                                  capture_output=True, timeout=timeout).stdout
        return response if binary else response.decode("utf-8", errors="replace").strip()

    require(adb("shell", "getprop", "ro.kernel.qemu") == "1"
            and adb("shell", "getprop", "ro.hardware") in ("goldfish", "ranchu")
            and adb("emu", "avd", "name").splitlines()[:1] == [avd], "Wrong synthetic AVD")
    packages = set(adb("shell", "pm", "list", "packages").splitlines())
    require({f"package:{APP}", f"package:{TEST}", "package:io.openhoyi.mobile"}.isdisjoint(packages),
            "Fresh Mock-only fixture required")
    apks = {kind: dict(inspect_apk(path, kind), sha256=digest(path)) for kind, path in DESTINATIONS.items()}
    require(apks["app.apk"]["certificateSha256"] == apks["test.apk"]["certificateSha256"], "Mock signer mismatch")
    output = Path("build/mock-ui-screenshots")
    output.mkdir(parents=True, exist_ok=False)
    receipt = dict(sourceCommit=sha, runId=run_id, runAttempt=attempt, avd=avd, profile=profile, apks=apks,
                   marker=MARKER, mockOnly=True, noPhysicalDevice=True, passed=False, screenshots={})
    keys = ("accelerometer_rotation", "user_rotation", "font_scale")
    settings = {key: adb("shell", "settings", "get", "system", key) for key in keys}
    require(all(value == "null" or re.fullmatch(r"[0-9]+(?:[.][0-9]+)?", value) for value in settings.values()),
            "Unexpected display settings on fresh AVD")
    try:
        for key, value in zip(keys, ("0", "0", font)):
            adb("shell", "settings", "put", "system", key, value)
        adb("shell", "wm", "size", f"{width}x{height}")
        adb("shell", "wm", "density", str(density))
        deadline = time.monotonic() + 35
        while time.monotonic() < deadline:
            display = dict(size=display_value(adb("shell", "wm", "size"), "size"),
                           density=display_value(adb("shell", "wm", "density"), "density"),
                           font=adb("shell", "settings", "get", "system", "font_scale"))
            if (display["size"], display["density"], float(display["font"])) == (f"{width}x{height}", str(density), float(font)):
                if png_size(adb("exec-out", "screencap", "-p", binary=True)) == (width, height):
                    receipt["initialDisplay"] = display
                    break
            time.sleep(1)
        require("initialDisplay" in receipt, "AVD failed to settle at expected display")
        for kind, path in DESTINATIONS.items():
            require(digest(path) == apks[kind]["sha256"], "APK changed before install")
            require(adb("install", path, timeout=120).splitlines()[-1] == "Success", f"Cannot install isolated {kind}")
        validate_installed_runners(adb("shell", "pm", "list", "instrumentation"))
        raw = subprocess.run([adb_path, "-s", "emulator-5554", "shell", "am", "instrument", "-w", "-r",
                              "-e", "disposableCiEmulator", "true", "-e", "liveShortcutChecks", "true",
                              "-e", "liveShortcutAppFont", APP_FONTS[profile][0], RUNNER],
                             capture_output=True, text=True, timeout=150)
        (output / "instrumentation.txt").write_text(raw.stdout[-32000:] + raw.stderr[-6000:])
        require(raw.returncode == 0 and re.search(r"^INSTRUMENTATION_CODE: -1$", raw.stdout, re.M)
                and re.search(r"(?:^|^INSTRUMENTATION_RESULT: stream=)" + re.escape(MARKER) + "$", raw.stdout, re.M),
                "Exact native Live Shortcut result marker or success code missing")

        def fixture(name):
            return adb("exec-out", "run-as", APP, "cat", "files/live-shortcut-checks/" + name, binary=True)

        raw_viewport = fixture("viewport.json")
        (output / "viewport.json").write_bytes(raw_viewport)
        viewport = json.loads(raw_viewport)
        receipt["appViewport"] = viewport
        receipt["finalSystemFont"] = adb("shell", "settings", "get", "system", "font_scale")
        for name in SCREENSHOTS:
            data = fixture(name)
            (output / name).write_bytes(data)
            receipt["screenshots"][name] = digest(output / name)
        require(math.isclose(float(receipt["finalSystemFont"]), float(font), abs_tol=0.001),
                "System font changed while checking app font")
        validate_viewport(viewport, profile)
        for name in SCREENSHOTS:
            require(png_size((output / name).read_bytes()) == (width, height), f"Unexpected PNG dimensions: {name}")
        require(len(set(receipt["screenshots"].values())) == len(SCREENSHOTS), "Duplicate screenshots")
        receipt["passed"] = True
    except (ValueError, KeyError, subprocess.SubprocessError, OSError) as error:
        receipt["errorType"] = type(error).__name__
        if isinstance(error, subprocess.TimeoutExpired):
            raw = error.stdout or b""
            (output / "instrumentation-timeout.txt").write_text(
                (raw.decode(errors="replace") if isinstance(raw, bytes) else raw)[-32000:])
        try:
            (output / "crash.txt").write_text(adb("logcat", "-b", "crash", "-d", "-t", "100", timeout=10)[-20000:])
        except (subprocess.SubprocessError, OSError):
            pass
        raise
    finally:
        cleanup = []
        commands = [("wm", "size", "reset"), ("wm", "density", "reset")]
        commands.extend(("settings", "delete", "system", key) if value == "null"
                        else ("settings", "put", "system", key, value) for key, value in settings.items())
        for command in commands:
            try:
                adb("shell", *command, timeout=8)
            except (subprocess.SubprocessError, OSError) as error:
                cleanup.append(type(error).__name__)
        receipt["restoreErrors"] = cleanup
        receipt["passed"] = receipt["passed"] and not cleanup
        (output / "live-shortcuts-receipt.json").write_text(json.dumps(receipt, indent=2) + "\n")
    require(receipt["passed"], "Display cleanup failed; see receipt")


if __name__ == "__main__":
    main()
