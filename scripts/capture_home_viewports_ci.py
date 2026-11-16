#!/usr/bin/env python3
"""Capture real Home windows on a fresh Linux GitHub Actions AVD only.

No user device, user data, BLE action or slot click. Geometry is evidence for
human review, not an assertion that every viewport fits without scrolling.
"""
import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys
import time
import struct
import re
import xml.etree.ElementTree as ET

PACKAGE = "io.openhoyi.mobile.mock"
SERIAL = "emulator-5554"
CASES = (
    ("landscape-16x10-medium", 1600, 1000, "medium"),
    ("landscape-16x10-small", 1600, 1000, "small"),
    ("landscape-16x10-large", 1600, 1000, "large"),
    ("portrait-large", 1000, 1600, "large"),
    ("square-medium", 1100, 1100, "medium"),
    ("flat-landscape-large", 1600, 400, "large"),
    ("narrow-landscape-large", 900, 600, "large"),
)


def adb(*args, data=None, binary=False):
    return subprocess.run(["adb", "-s", SERIAL, *args], input=data,
                          stdout=subprocess.PIPE, stderr=subprocess.PIPE,
                          text=not binary, timeout=60, check=True).stdout


def require_environment():
    if sys.platform != "linux" or os.environ.get("GITHUB_ACTIONS") != "true":
        raise RuntimeError("Linux GitHub Actions only; never run on Mac or a user tablet")
    if os.environ.get("GITHUB_REPOSITORY") != "boolean93/openhoyi":
        raise RuntimeError("Unexpected repository")
    if os.environ.get("GITHUB_EVENT_NAME") != "workflow_dispatch":
        raise RuntimeError("Manual workflow only")
    if not os.environ.get("GITHUB_RUN_ID", "").isdigit():
        raise RuntimeError("Missing run identity")
    if adb("shell", "getprop", "ro.kernel.qemu").strip() != "1":
        raise RuntimeError("Dedicated disposable emulator required")
    if adb("shell", "pm", "list", "packages", PACKAGE).strip():
        raise RuntimeError("Fresh emulator required; refusing to modify an existing installation")


def main():
    require_environment()  # All guards precede installation or display changes.
    out = Path("build/home-viewports")
    out.mkdir(parents=True, exist_ok=True)
    apk = Path("mobile/build/outputs/apk/mock/mobile-mock.apk")
    identity = {"sourceCommit": os.environ["GITHUB_SHA"],
                "apkSha256": hashlib.sha256(apk.read_bytes()).hexdigest(),
                "runId": os.environ["GITHUB_RUN_ID"], "densityDpi": 200,
                "kind": "actual Android Home windows; synthetic fresh Mock data"}
    (out / "identity.json").write_text(json.dumps(identity, indent=2) + "\n")
    result = adb("install", str(apk))
    if "Success" not in result:
        raise RuntimeError(result)
    for name, width, height, font in CASES:
        folder = out / name
        folder.mkdir(exist_ok=True)
        adb("shell", "am", "force-stop", PACKAGE)
        adb("shell", "wm", "size", f"{width}x{height}")
        adb("shell", "wm", "density", "200")
        adb("shell", "settings", "put", "system", "accelerometer_rotation", "0")
        adb("shell", "settings", "put", "system", "user_rotation", "0")
        # This file belongs only to the new disposable Mock install above. Use the
        # production app's absolute font choice; never change system font_scale.
        xml = f'<map><string name="size">{font}</string></map>\n'
        adb("shell", "run-as", PACKAGE, "sh", "-c",
            "'mkdir -p shared_prefs && cat > shared_prefs/app_typography.xml'", data=xml)
        saved_font = adb("shell", "run-as", PACKAGE, "cat", "shared_prefs/app_typography.xml")
        if ET.fromstring(saved_font).find("string[@name='size']").text != font:
            raise RuntimeError("App font preference write did not persist")
        launch = adb("shell", "am", "start", "-W", "-n", f"{PACKAGE}/io.openhoyi.mobile.MainActivity",
            "--es", "io.openhoyi.mobile.initial_destination", "BREW")
        (folder / "launch.txt").write_text(launch)
        if "Status: ok" not in launch or "Error:" in launch or "MainActivity" not in launch:
            raise RuntimeError("Home launch was not confirmed")
        time.sleep(3)
        hierarchy = adb("exec-out", "uiautomator", "dump", "/dev/tty")
        (folder / "window.xml").write_text(hierarchy)
        xml_start = hierarchy.find("<?xml")
        xml_end = hierarchy.find("</hierarchy>") + len("</hierarchy>")
        nodes = list(ET.fromstring(hierarchy[xml_start:xml_end]).iter("node"))
        home_nodes = [n for n in nodes if n.get("package") == PACKAGE]
        if not any(n.get("text") in ("冲泡锅炉", "蒸汽锅炉", "Brew boiler", "Steam boiler") for n in home_nodes):
            raise RuntimeError("Screenshot is not a rendered Home boiler window")
        resumed = adb("shell", "dumpsys", "activity", "activities")
        if not any("ResumedActivity" in line and PACKAGE in line and "MainActivity" in line
                   for line in resumed.splitlines()):
            raise RuntimeError("Mock MainActivity is not foreground")
        actual_size = adb("shell", "wm", "size")
        actual_density = adb("shell", "wm", "density")
        if f"Override size: {width}x{height}" not in actual_size or "Override density: 200" not in actual_density:
            raise RuntimeError("Requested CI window override not confirmed")
        png = adb("exec-out", "screencap", "-p", binary=True)
        (folder / "home.png").write_bytes(png)
        if png[:8] != b"\x89PNG\r\n\x1a\n" or struct.unpack(">II", png[16:24]) != (width, height):
            raise RuntimeError("Actual screenshot dimensions differ from requested window")
        (folder / "activity.txt").write_text(adb("shell", "dumpsys", "activity", "top"))
        (folder / "display.txt").write_text(actual_size + actual_density)
        (folder / "case.json").write_text(json.dumps({"requestedPixels": [width, height],
            "actualScreenshotPixels": [width, height], "persistedAppFont": font,
            "requestedAppFontScale": {"small": .9, "medium": 1., "large": 1.1}[font],
            "fontEvidence": "production preference confirmed before cold launch; runtime font scale not instrumented",
            "scrollPerformed": False, "slotActionsPerformed": False}, indent=2) + "\n")
        if name == "flat-landscape-large":
            # Real swipe inside the blank center of this known flat Home heading;
            # never tap a slot or a machine control. The fresh CI guard still owns
            # the target. This proves actual overflow reachability, not one-screen fit.
            if not any(n.get("class") == "android.widget.ScrollView" and
                       n.get("scrollable") == "true" and n.get("bounds") == "[130,35][1600,340]"
                       for n in home_nodes):
                raise RuntimeError("Flat Home scroll geometry changed; refuse blind input")
            adb("shell", "input", "swipe", "800", "260", "800", "100", "300")
            time.sleep(1)
            after = adb("exec-out", "uiautomator", "dump", "/dev/tty")
            (folder / "scrolled-window.xml").write_text(after)
            (folder / "home-scrolled.png").write_bytes(adb("exec-out", "screencap", "-p", binary=True))
            tree = ET.fromstring(after[after.find("<?xml"):after.find("</hierarchy>") + 12])
            expected = {"Espresso深烘", "SOE浅烘", "HOYI Turbo Shot", "HOYI Limit Extraction", "Hyper Ex 超萃手冲"}
            cards = [n for n in tree.iter("node") if n.get("package") == PACKAGE and
                     n.get("clickable") == "true" and n.get("content-desc") in expected]
            if {n.get("content-desc") for n in cards} != expected:
                raise RuntimeError("Not all five slots are reachable after scroll")
            bounds = [list(map(int, re.findall(r"\d+", n.get("bounds")))) for n in cards]
            if any(x2 - x1 < 60 or y2 - y1 < 60 or y1 < 35 or y2 > 340
                   for x1, y1, x2, y2 in bounds):
                raise RuntimeError("Scrolled slot lacks a complete 48dp touch region")
            (folder / "scroll-result.json").write_text(json.dumps({"slotBounds": bounds,
                "allFiveReachable": True, "minimumVisibleTouchDp": 48,
                "oneScreenFit": False, "slotActionsPerformed": False}, indent=2) + "\n")
    print(f"HOME_VIEWPORT_CAPTURES_COMPLETE cases={len(CASES)} humanReviewRequired=true")


if __name__ == "__main__":
    main()
