#!/usr/bin/env python3
"""Byte oracle and fault tests against unmodified SDK34 org.json sources."""

import hashlib
import json
from pathlib import Path
import subprocess
import tempfile

from test_window_json_comparison_equivalence import ROOT, SDK_JSON, stub

OUT = ROOT / "build/task-first-evidence/window-proof-streaming"
RAW = ROOT / "build/task-first-evidence/ci-643d828-scale-pr/raw/raw-device"


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    profiles = list(RAW.glob("*/tablet-user-landscape-light-font-1.3/connection"))
    assert len(profiles) == 1, profiles
    connection = profiles[0]
    inputs = [connection / "window-frame-sync.json",
              connection / "special-states/long-name/window-frame-sync.json",
              connection / "failure-context/geometry.json"]
    assert all(path.is_file() for path in inputs)
    sources = sorted(SDK_JSON.glob("*.java"))
    assert len(sources) == 6, sources
    with tempfile.TemporaryDirectory(prefix="window-proof-sdk34-") as directory:
        tmp = Path(directory)
        stubs = [
            stub(tmp, "android.annotation", "SystemApi", "public @interface SystemApi { Client client(); enum Client { MODULE_LIBRARIES } }"),
            stub(tmp, "android.compat.annotation", "UnsupportedAppUsage", "public @interface UnsupportedAppUsage {}"),
            stub(tmp, "libcore.util", "NonNull", "@java.lang.annotation.Target({java.lang.annotation.ElementType.TYPE_USE, java.lang.annotation.ElementType.METHOD, java.lang.annotation.ElementType.PARAMETER, java.lang.annotation.ElementType.FIELD}) public @interface NonNull {}"),
            stub(tmp, "libcore.util", "Nullable", "@java.lang.annotation.Target({java.lang.annotation.ElementType.TYPE_USE, java.lang.annotation.ElementType.METHOD, java.lang.annotation.ElementType.PARAMETER, java.lang.annotation.ElementType.FIELD}) public @interface Nullable {}"),
        ]
        classes = tmp / "classes"
        classes.mkdir()
        subprocess.run(["javac", "-d", str(classes), *map(str, sources + stubs),
                        str(ROOT / "mobile/src/androidTest/java/io/openhoyi/mobile/WindowProofStreamingWriter.java"),
                        str(ROOT / "scripts/WindowProofStreamingWriterOracle.java")], check=True)
        normal = subprocess.run(["java", "-Xmx1g", "-cp", str(classes),
                                 "io.openhoyi.mobile.WindowProofStreamingWriterOracle", *map(str, inputs)],
                                text=True, capture_output=True)
        if normal.returncode:
            raise RuntimeError(normal.stderr or normal.stdout)
        limited = subprocess.run(["java", "-Xmx192m", "-cp", str(classes),
                                  "io.openhoyi.mobile.WindowProofStreamingWriterOracle", "--limited", str(inputs[2])],
                                 text=True, capture_output=True)
        if limited.returncode:
            raise RuntimeError("limited heap: " + (limited.stderr or limited.stdout))
    report = {"oracle": "unmodified Android SDK34 org.json Java sources; annotation-only stubs",
              "sourceSha256": {source.name: sha(source) for source in sources},
              "inputs": [{"path": str(path), "sha256": sha(path), "bytes": path.stat().st_size} for path in inputs],
              "normalHostHeap": "-Xmx1g", "limitedHostHeap": "-Xmx192m",
              "normalResults": normal.stdout.strip().splitlines(),
              "limitedResult": limited.stdout.strip(),
              "syntheticPressureIsNotLostFortyGestureProof": True,
              "hostTestIsNotAndroidAcceptance": True}
    (OUT / "report.json").write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n")
    print(normal.stdout, end="")
    print(limited.stdout, end="")
    print(f"report={OUT / 'report.json'}")


if __name__ == "__main__":
    main()
