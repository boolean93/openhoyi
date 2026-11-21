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


def validate_capture(image, geometry, width, height, dpi, theme, background="wallpaper", empty_recent=False, no_lever=False):
    require(png_size(image) == (width, height), "Wrong screenshot PNG geometry")
    require(geometry["density"] == dpi / 160 and geometry["dark"] == (theme == "dark") and
            geometry["portrait"] == (height > width), "Android view proof did not match expected configuration")
    require(geometry["wallpaper"] == (background == "wallpaper"), "Wrong captured backdrop")
    cards = geometry["items"]
    if no_lever:
        require(geometry["noLever"] and geometry["emptyRecent"] == empty_recent and len(cards) == 5 and
                cards[2]["name"] == "HOYI Limit Extraction" and
                cards[4]["label"] == ("上一杯" if empty_recent else "复用上一杯"),
                "Expected A–D and the correct last-cup state")
        require(cards[4]["rect"][0] == cards[2]["rect"][0] and cards[4]["rect"][2] == cards[3]["rect"][2],
                "Expected Last to span both phone columns")
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


def capture_boiler_warmup(adb, executable, output, receipt):
    # Use Android's real display encoder on the already identity-checked disposable AVD.
    target = "/data/local/tmp/openhoyi-boiler-warmup.mp4"
    previous = adb("shell", "settings", "get", "global", "animator_duration_scale")
    require(previous == "null" or re.fullmatch(r"[0-9]+(?:[.][0-9]+)?", previous), "Unexpected fresh AVD animator scale")
    worker = None
    try:
        adb("shell", "settings", "put", "global", "animator_duration_scale", "1")
        worker = subprocess.Popen([str(executable), "-s", "emulator-5554", "shell", "screenrecord",
                                   "--time-limit", "24", "--bit-rate", "5000000", target],
                                  stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        time.sleep(.4)
        report = adb("shell", "am", "instrument", "-w", "-r", "-e", "disposableCiEmulator", "true",
                     "-e", "homeBoilerWarmupChecks", "true", RUNNER, timeout=70)
        (output / "boiler-warmup-instrumentation.txt").write_text(report + "\n")
        # Close MediaMuxer on SIGINT so the output contains the movie index.
        if worker.poll() is None:
            try:
                adb("shell", "pkill", "-2", "-x", "screenrecord")
            except subprocess.CalledProcessError:
                pass  # The 24s recorder may already have exited.
        worker.wait(timeout=26)
        movie = output / "boiler-warmup.mp4"
        adb("pull", target, str(movie))
        require(movie.stat().st_size > 10000 and movie.read_bytes()[4:8] == b"ftyp", "No playable container from AVD")
        require(re.search(r"^INSTRUMENTATION_CODE: -1$", report, re.M) and "HOME_BOILER_WARMUP_PASSED" in report,
                "Actual Home warmup failed; see recorded MP4 and instrumentation log")
        proof = json.loads(adb("exec-out", "run-as", APP, "cat", "files/boiler-warmup.json"))
        require(proof["path"] == "real_home_guarded_mock_idle_telemetry" and proof["coolingSnaps"] and proof["retargetAndReset"] and
                proof["sameViewResumeSnaps"] and
                len(proof["samples"]) == 6 and proof["samples"][-1]["actual"] == proof["targets"],
                "Missing actual Home warmup evidence")
        (output / "boiler-warmup.json").write_text(json.dumps(proof, indent=2) + "\n")
        receipt["warmupRecording"] = {"sha256": digest(movie), "realHome": True, "pairedAnimation": True}
    finally:
        if worker and worker.poll() is None:
            worker.terminate()
            worker.wait(timeout=5)
        adb("shell", "rm", "-f", target)
        if previous == "null":
            adb("shell", "settings", "delete", "global", "animator_duration_scale")
        else:
            adb("shell", "settings", "put", "global", "animator_duration_scale", previous)


def capture_settings_categories(adb, output, receipt, profile, width, height):
    require(profile in {"phone", "phone-short", "tablet"}, "Expected the fresh phone/tablet Settings profile")
    report = adb("shell", "am", "instrument", "-w", "-r", "-e", "disposableCiEmulator", "true",
                 "-e", "settingsCategoryProfile", profile, RUNNER, timeout=220)
    (output / "settings-instrumentation.txt").write_text(report + "\n")
    # Pull actual captured image(s) even on a later UI assertion failure.
    keys = ("device", "brewing", "appearance") if profile == "phone-short" else (
        "device", "machine", "brewing", "appearance", "ai", "maintenance")
    if profile == "phone":
        keys += ("appearance-light",)
    if profile == "tablet":
        keys += tuple(f"dark-{key}" for key in keys)
    standard_keys = keys
    if profile == "tablet":
        keys += ("extra-standby", "extra-weekly", "extra-weekly-editor", "extra-scale-capabilities",
                 "extra-appearance-lower", "extra-cups", "extra-diagnostics", "extra-updates",
                 "extra-ai-chat", "extra-ai-model", "extra-ai-reasoning", "extra-ai-usage", "extra-ai-key")
    for key in keys:
        try:
            frame = adb("exec-out", "run-as", APP, "cat", f"files/settings-categories/{key}.png", binary=True)
        except subprocess.CalledProcessError:
            continue
        # Some adb exec-out implementations return cat's error as bytes with exit 0.
        if not frame.startswith(b"\x89PNG\r\n\x1a\n"):
            continue
        require(png_size(frame) == (width, height), f"Wrong Settings screenshot geometry: {key}")
        image = output / f"settings-{profile}-{key}.png"
        image.write_bytes(frame)
        receipt["screenshots"][image.name] = digest(image)
    require(re.search(r"^INSTRUMENTATION_CODE: -1$", report, re.M) and "SETTINGS_CATEGORIES_PASSED" in report,
            "Settings UI checks failed; see settings-instrumentation.txt")
    proof = json.loads(adb("exec-out", "run-as", APP, "cat", "files/settings-categories/receipt.json"))
    expected_names = {f"settings-{profile}-{key}.png" for key in standard_keys}
    if profile == "tablet":
        details = proof["supplemental"]
        required_detail = {f"{key}.png" for key in keys[len(standard_keys):] if key != "extra-weekly-editor"}
        require(required_detail.issubset(details), "Some original Settings configuration areas were not captured")
        expected_names.update(f"settings-{profile}-{name}" for name in details)
    require(proof["wide"] == (profile == "tablet") and proof["dark"] == (profile == "phone") and
            proof["sixCategories"] and proof["exactRoutes"] == 11 and proof["returnedToCaller"] and
            proof["recreatedCategory"] and proof["continuousDrag"] and proof["scaleToolReturned"] and proof["shortEnglishLarge"] == (profile == "phone-short") and
            proof["appearancePreviewNoWrite"] == (profile != "phone-short") and proof["wideDark"] == (profile == "tablet") and
            set(receipt["screenshots"]) == expected_names and
            len({receipt["screenshots"][name] for name in expected_names if "-extra-" not in name}) == len(standard_keys),
            "Incomplete Settings category evidence")
    (output / "settings-geometry.json").write_text(json.dumps(proof, indent=2) + "\n")


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
        if os.environ.get("SETTINGS_CATEGORIES") == "true":
            name = "settings-categories"
            capture_settings_categories(adb, output, receipt, profile, width, height)
            receipt["passed"] = True
            return
        if os.environ.get("BOILER_WARMUP") == "true":
            require(profile == "phone", "Boiler recording requires the dedicated phone viewport")
            name = "boiler-warmup"
            capture_boiler_warmup(adb, executable, output, receipt)
            receipt["passed"] = True
            return
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
        fixtures = [(background, theme, "") for background, theme in cases]
        if profile == "phone":
            fixtures.extend([("wallpaper", "dark", "empty-recent"), ("wallpaper", "dark", "five-recent")])
        def capture_name(background, theme, variant):
            return f"{profile}-{theme}" + ("-solid" if background == "solid" else "") + (f"-{variant}" if variant else "")
        for background, theme, variant in fixtures:
            empty_recent, no_lever = variant == "empty-recent", bool(variant)
            name = capture_name(background, theme, variant)
            # All data in this exact ephemeral install is synthetic and fixture-owned.
            require(adb("shell", "pm", "clear", APP) == "Success", "Cannot reset isolated Mock fixture")
            try:
                report = adb("shell", "am", "instrument", "-w", "-r", "-e", "disposableCiEmulator", "true",
                             "-e", "quickCurveVisualTheme", theme, "-e", "quickCurveVisualBackground",
                             background, "-e", "quickCurveShortPortrait", str(profile == "phone-short").lower(),
                             "-e", "quickCurveEmptyRecent", str(empty_recent).lower(),
                             "-e", "quickCurveNoLever", str(no_lever).lower(), RUNNER, timeout=100)
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
            validate_capture(image, geometry, width, height, dpi, theme, background, empty_recent, no_lever)
            receipt["screenshots"][target.name] = digest(target)
        expected_frames = {capture_name(*fixture) + ".png" for fixture in fixtures}
        require(set(receipt["screenshots"]) == expected_frames and
                len(set(receipt["screenshots"].values())) == len(expected_frames),
                "Missing or duplicate capture outputs")
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
        require(not cleanup_errors, "Display restoration failed; see quick-curve receipt")
    require(receipt["passed"], "Capture did not pass; see quick-curve receipt")


if __name__ == "__main__":
    main()
