#!/usr/bin/env python3
"""Generate offline curve frames from reviewed original HOYI 1.2.4/1.2.6 APKs.

Usage: generate_custom_flow_curve_oracle.py original.apk curves.json wire.tsv
Extracted functions execute in a sandbox whose BleWrite only captures bytes.
No BLE, Android device, or network is used. The resulting wire evidence does
not grant execution permission; firmware-safety policy remains independent.
"""

import hashlib
import json
from pathlib import Path
import subprocess
import sys
import zipfile

from generate_factory_wire_oracle import function
from generate_legacy_curve_oracle import FRAME, NODE, load_export
from generate_custom_water_limit_oracle import encoder_harness


ARTIFACTS = {
    "401afbd9d36494f5acc41b0c15f7dae5a59684573ecdc5d905c8fd8ed40ace21": {
        "version": "1.2.4",
        "bundle": "70ccac9c2bb3705b446cbc9a338f68706506901be05d18d6c84cdae0b7a1cbe1",
        "encoder": "2fa002d7307e0b6192afdaf6c0d1b50f2d9d1c9f9f477eeb87c8aa0e879d515f",
    },
    "26133c2ac8ecf3d5ee7682bafa6555b1c78836749c937477b05214a88d8ea238": {
        "version": "1.2.6",
        "bundle": "4ed90ea99240b7f37373cdb5c9df0bf315103ca7aea6aaab6f37b2c58dd564d7",
        "encoder": "5d120498919b7a467a70be6d522e9bb556ec3fc32f686854de8e20b3aa9ae301",
    },
}


def extract_harness(source: str, version: str) -> str:
    if version == "1.2.4":
        source = source.replace("8625:function", "8625: function", 1)
        for name in ("startTempChart", "startChart", "ten2Hex", "xor_fun"):
            source = source.replace(f"{name}:function", f"{name}: function")
        return encoder_harness(source)
    module = source[source.index("addd:function"):source.index("addd:function") + 100000]
    helpers = "\n".join(function(module, f"function {name}(", True)
                        for name in ("g", "y", "v", "w", "b", "_", "x"))
    methods = {name: function(module, f"{name}:function", False)
               for name in ("startTempChart", "startChart", "ten2Hex", "xor_fun")}
    return "(function(){\n" + helpers + "\nreturn {" + ",".join(
        f"{name}: {body}" for name, body in methods.items()) + "};})()"


def generate(apk: Path, export: Path, output: Path) -> int:
    apk_hash = hashlib.sha256(apk.read_bytes()).hexdigest()
    artifact = ARTIFACTS.get(apk_hash)
    if artifact is None:
        raise ValueError("APK is not a reviewed original artifact")
    with zipfile.ZipFile(apk) as archive:
        bundle = archive.read("assets/apps/__UNI__7D80DAB/www/app-service.js")
    if hashlib.sha256(bundle).hexdigest() != artifact["bundle"]:
        raise ValueError("original APK bundle differs from the reviewed source")
    harness = extract_harness(bundle.decode("utf-8"), artifact["version"])
    if hashlib.sha256(harness.encode("utf-8")).hexdigest() != artifact["encoder"]:
        raise ValueError("original APK encoder differs from reviewed extraction")
    export_hash, rows = load_export(export)
    result = subprocess.run(["node", "-e", NODE], input=json.dumps({"harness": harness, "rows": rows}),
                            text=True, capture_output=True, check=True, timeout=60)
    entries = json.loads(result.stdout)
    indices = [index for index, row in enumerate(rows) if row is not None]
    if len(entries) != len(indices) * 6:
        raise ValueError("incomplete original APK evidence")
    lines = [f"# legacy-curve-wire-v1\tsource-sha256={artifact['bundle']}"
             f"\tencoder-sha256={artifact['encoder']}\tapk-sha256={apk_hash}"
             f"\tapk-version={artifact['version']}\texport-sha256={export_hash}"]
    for position, entry in enumerate(entries):
        index, slot, no_scale, scale = entry
        if index != indices[position // 6] or slot != (7, 1, 2, 3, 4, 5)[position % 6]:
            raise ValueError("unordered original APK evidence")
        if any(not isinstance(frame, str) or not FRAME.fullmatch(frame)
               for frame in (no_scale, scale)):
            raise ValueError("invalid original APK frame")
        lines.append(f"{index}\t{slot}\t{no_scale}\t{scale}")
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text("\n".join(lines) + "\n", encoding="utf-8")
    return len(indices)


if __name__ == "__main__":
    if len(sys.argv) != 4:
        raise SystemExit(__doc__)
    count = generate(*map(Path, sys.argv[1:]))
    print(f"generated {count} original APK curves × 6 slots × 2 scale modes; offline only")
