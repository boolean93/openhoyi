#!/usr/bin/env python3
"""Byte oracle and fault tests against unmodified SDK34 org.json sources."""

import hashlib
import json
from pathlib import Path
import subprocess
import tempfile
import zipfile

from test_window_json_comparison_equivalence import ROOT, SDK_JSON, stub

OUT = ROOT / "build/task-first-evidence/window-proof-streaming"
RAW = ROOT / "build/task-first-evidence/ci-643d828-scale-pr/raw/raw-device"
M5_ZIP = ROOT / "build/task-first-evidence/ci-8b52379/m5-artifact.zip"
M5_ZIP_SHA = "1d9e89e5a9e2e39200d332ef63bc6846f436d4314c91f7dae14b97636df23758"
M5_MEMBER = ("raw-device/44d34c52a3f3b18537f59cb92607bcc7c6252d09/"
             "tablet-user-landscape-light-font-1.3/history/special-states/manifest.json")
M5_MEMBER_SHA = "c7efdaf9d496ddf3f800a32abe643ce27759505ebd0ca2174fdc4eb889873755"
M5_MEMBER_BYTES = 17_877_067


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    m5_source = (ROOT / "mobile/src/androidTest/kotlin/io/openhoyi/mobile/"
                 "ExtractionHistoryVisualMigrationChecks.kt").read_text()
    assert m5_source.count("fun persist() { WindowProofStreamingWriter.writeStrict(summary,") == 2, (
        "both M5 cumulative summaries must use strict streaming")
    profiles = list(RAW.glob("*/tablet-user-landscape-light-font-1.3/connection"))
    assert len(profiles) == 1, profiles
    connection = profiles[0]
    inputs = [connection / "window-frame-sync.json",
              connection / "special-states/long-name/window-frame-sync.json",
              connection / "failure-context/geometry.json"]
    settings = list((ROOT / "build/task-first-evidence/ci-bcc64c1-settings/raw/raw-device")
                    .glob("*/tablet-user-landscape-light-font-1.3/app/manifest.json"))
    m5 = list((ROOT / "build/task-first-evidence/ci-bcc64c1-m5/raw/raw-device")
              .glob("*/tablet-user-landscape-light-font-1.3/history/special-states/manifest.json"))
    assert len(settings) == len(m5) == 1, (settings, m5)
    inputs.extend([settings[0], m5[0]])
    assert all(path.is_file() for path in inputs)
    sources = sorted(SDK_JSON.glob("*.java"))
    assert len(sources) == 6, sources
    with tempfile.TemporaryDirectory(prefix="window-proof-sdk34-") as directory:
        tmp = Path(directory)
        assert sha(M5_ZIP) == M5_ZIP_SHA
        with zipfile.ZipFile(M5_ZIP) as archive:
            assert archive.getinfo(M5_MEMBER).file_size == M5_MEMBER_BYTES
            m5_summary = tmp / "m5-special-summary.json"
            with archive.open(M5_MEMBER) as source, m5_summary.open("wb") as target:
                while chunk := source.read(65536):
                    target.write(chunk)
        assert m5_summary.stat().st_size == M5_MEMBER_BYTES
        assert sha(m5_summary) == M5_MEMBER_SHA
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
        strict_detail_cases = ["strict-stringbuilder-pointer", "strict-custom-charsequence",
                               "strict-first-bad-order", "strict-custom-number-path",
                               "strict-container-subclass-path", "strict-nonfinite-path",
                               "strict-unknown-no-tostring"]
        for label in strict_detail_cases:
            assert f"case={label} strictRejected=true path=" in normal.stdout, label
        real_summary = subprocess.run(["java", "-Xmx1g", "-cp", str(classes),
                                       "io.openhoyi.mobile.WindowProofStreamingWriterOracle",
                                       "--real-summary", str(m5_summary), M5_MEMBER_SHA],
                                      text=True, capture_output=True)
        if real_summary.returncode:
            raise RuntimeError("real summary byte oracle: " + (real_summary.stderr or real_summary.stdout))
        summary_line = real_summary.stdout.strip().splitlines()[-1]
        summary_parts = dict(part.split("=", 1) for part in summary_line.split() if "=" in part)
        assert summary_parts["realSummary"] == "true" and summary_parts["zipBytesEqual"] == "true", summary_line
        limited_real = subprocess.run(["java", "-Xmx192m", "-cp", str(classes),
                                       "io.openhoyi.mobile.WindowProofStreamingWriterOracle",
                                       "--limited-summary", str(m5_summary),
                                       summary_parts["bytes"], summary_parts["sha256"]],
                                      text=True, capture_output=True)
        if limited_real.returncode:
            raise RuntimeError("limited real summary: " + (limited_real.stderr or limited_real.stdout))
        flows = subprocess.run(["java", "-Xmx192m", "-cp", str(classes),
                                "io.openhoyi.mobile.WindowProofStreamingWriterOracle", "--special-state-flows"],
                               text=True, capture_output=True)
        if flows.returncode:
            raise RuntimeError("special-state control flow: " + (flows.stderr or flows.stdout))
        limited = subprocess.run(["java", "-Xmx192m", "-cp", str(classes),
                                  "io.openhoyi.mobile.WindowProofStreamingWriterOracle", "--limited", str(inputs[2])],
                                 text=True, capture_output=True)
        if limited.returncode:
            raise RuntimeError("limited heap: " + (limited.stderr or limited.stdout))
    report = {"oracle": "unmodified Android SDK34 org.json Java sources; annotation-only stubs",
              "sourceSha256": {source.name: sha(source) for source in sources},
              "inputs": [{"path": str(path), "sha256": sha(path), "bytes": path.stat().st_size} for path in inputs],
              "m5RealSummary": {"zip": str(M5_ZIP), "zipSha256": M5_ZIP_SHA,
                                "member": M5_MEMBER, "bytes": M5_MEMBER_BYTES, "sha256": M5_MEMBER_SHA,
                                "normalResult": real_summary.stdout.strip(),
                                "limitedResult": limited_real.stdout.strip()},
              "specialStateControlFlowModel": {"result": flows.stdout.strip().splitlines(),
                                               "isOfflineModelNotAndroidExecution": True},
              "normalHostHeap": "-Xmx1g", "limitedHostHeap": "-Xmx192m",
              "normalResults": normal.stdout.strip().splitlines(),
              "strictValuePathProbe": {"cases": strict_detail_cases,
                                       "sdkCharSequenceSamplesAreNotAndroidSpannableStringEvidence": True,
                                       "successfulEncodingWhitelistUnchanged": True},
              "limitedResult": limited.stdout.strip(),
              "syntheticPressureIsNotLostFortyGestureProof": True,
              "hostTestIsNotAndroidAcceptance": True}
    (OUT / "report.json").write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n")
    print(normal.stdout, end="")
    print(limited.stdout, end="")
    print(real_summary.stdout, end="")
    print(limited_real.stdout, end="")
    print(flows.stdout, end="")
    print(f"report={OUT / 'report.json'}")


if __name__ == "__main__":
    main()
