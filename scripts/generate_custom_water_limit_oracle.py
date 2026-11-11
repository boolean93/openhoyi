#!/usr/bin/env python3
"""Offline water-limit evidence from the original 1.2.4 APK. Never sends BLE.

Usage: generate_custom_water_limit_oracle.py 1.2.4.apk export.json proof.tsv
The older 72-recipe oracle and its modified-source hash remain unchanged.
"""
import hashlib
import json
from pathlib import Path
import subprocess
import sys
import zipfile

from generate_legacy_curve_oracle import NODE, encoder_harness, load_export

APK_SHA256 = "401afbd9d36494f5acc41b0c15f7dae5a59684573ecdc5d905c8fd8ed40ace21"
BUNDLE_SHA256 = "70ccac9c2bb3705b446cbc9a338f68706506901be05d18d6c84cdae0b7a1cbe1"
ENCODER_SHA256 = "2fa002d7307e0b6192afdaf6c0d1b50f2d9d1c9f9f477eeb87c8aa0e879d515f"


def generate(apk: Path, export: Path, output: Path) -> int:
    if hashlib.sha256(apk.read_bytes()).hexdigest() != APK_SHA256:
        raise ValueError("original APK differs from the reviewed 1.2.4 artifact")
    with zipfile.ZipFile(apk) as archive:
        bundle = archive.read("assets/apps/__UNI__7D80DAB/www/app-service.js")
    if hashlib.sha256(bundle).hexdigest() != BUNDLE_SHA256:
        raise ValueError("original APK bundle differs from the reviewed source")
    # Only locating delimiters outside the extracted functions are spaced. The
    # original function bodies stay intact and the complete harness is hash-bound.
    source = bundle.decode("utf-8").replace("8625:function", "8625: function", 1)
    for name in ("startTempChart", "startChart", "ten2Hex", "xor_fun"):
        source = source.replace(f"{name}:function", f"{name}: function")
    harness = encoder_harness(source)
    if hashlib.sha256(harness.encode("utf-8")).hexdigest() != ENCODER_SHA256:
        raise ValueError("original APK encoder differs from the reviewed extraction")
    export_hash, rows = load_export(export)
    result = subprocess.run(["node", "-e", NODE], input=json.dumps({"harness": harness, "rows": rows}),
                            text=True, capture_output=True, check=True, timeout=60)
    entries = json.loads(result.stdout)
    indices = [index for index, row in enumerate(rows) if row is not None]
    if len(entries) != len(indices) * 6:
        raise ValueError("incomplete original APK evidence")
    lines = [f"# legacy-curve-wire-v1\tsource-sha256={BUNDLE_SHA256}\tencoder-sha256={ENCODER_SHA256}"
             f"\tapk-sha256={APK_SHA256}\texport-sha256={export_hash}"]
    for position, entry in enumerate(entries):
        index, slot, no_scale, scale = entry
        if index != indices[position // 6] or slot != (7, 1, 2, 3, 4, 5)[position % 6]:
            raise ValueError("unordered original APK evidence")
        if any(not isinstance(frame, str) or len(frame) != 40 or
               not frame.startswith("02") or any(char not in "0123456789ABCDEF" for char in frame)
               for frame in (no_scale, scale)):
            raise ValueError("invalid original APK frame")
        lines.append(f"{index}\t{slot}\t{no_scale}\t{scale}")
    output.write_text("\n".join(lines) + "\n", encoding="utf-8")
    return len(indices)


if __name__ == "__main__":
    if len(sys.argv) != 4:
        raise SystemExit(__doc__)
    count = generate(*map(Path, sys.argv[1:]))
    print(f"generated {count} original APK curves × 6 slots × 2 scale modes; offline only")
