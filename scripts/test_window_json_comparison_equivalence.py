#!/usr/bin/env python3
"""Compile unchanged Android 34 org.json sources and compare the production Java helper."""

import hashlib
import json
from pathlib import Path
import subprocess
import tempfile
import zipfile

ROOT = Path(__file__).resolve().parents[1]
SDK_JSON = Path.home() / "Library/Android/sdk/sources/android-34/org/json"
OUT = ROOT / "build/task-first-evidence/window-json-equivalence"
SETTINGS_ZIP = ROOT / "build/task-first-evidence/ci-7082e04-settings/artifact.zip"


def sha(data):
    return hashlib.sha256(data).hexdigest()


def stub(base, package, name, source):
    folder = base / package.replace(".", "/")
    folder.mkdir(parents=True, exist_ok=True)
    path = folder / f"{name}.java"
    path.write_text(f"package {package};\n{source}\n")
    return path


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    scale_candidates = list((ROOT / "build/task-first-evidence/ci-7082e04-scale-push/raw/raw-device")
                            .rglob("scale/window-frame-sync.json"))
    assert len(scale_candidates) == 1, scale_candidates
    scale_proof = scale_candidates[0]
    sources = sorted(SDK_JSON.glob("*.java"))
    assert len(sources) == 6, sources
    entries = []
    with tempfile.TemporaryDirectory(prefix="window-json-android34-") as folder:
        tmp = Path(folder)
        stubs = [
            stub(tmp, "android.annotation", "SystemApi", "public @interface SystemApi { Client client(); enum Client { MODULE_LIBRARIES } }"),
            stub(tmp, "android.compat.annotation", "UnsupportedAppUsage", "public @interface UnsupportedAppUsage {}"),
            stub(tmp, "libcore.util", "NonNull", "@java.lang.annotation.Target({java.lang.annotation.ElementType.TYPE_USE, java.lang.annotation.ElementType.METHOD, java.lang.annotation.ElementType.PARAMETER, java.lang.annotation.ElementType.FIELD}) public @interface NonNull {}"),
            stub(tmp, "libcore.util", "Nullable", "@java.lang.annotation.Target({java.lang.annotation.ElementType.TYPE_USE, java.lang.annotation.ElementType.METHOD, java.lang.annotation.ElementType.PARAMETER, java.lang.annotation.ElementType.FIELD}) public @interface Nullable {}"),
        ]
        classes = tmp / "classes"
        classes.mkdir()
        subprocess.run(["javac", "-d", str(classes), *map(str, sources + stubs),
                        str(ROOT / "mobile/src/androidTest/java/io/openhoyi/mobile/WindowGeometryJsonEquality.java"),
                        str(ROOT / "scripts/WindowGeometryJsonEqualityOracle.java")], check=True)
        paths = []
        with zipfile.ZipFile(SETTINGS_ZIP) as archive:
            selected = [name for name in archive.namelist() if name.startswith("raw-device/")
                        and name.endswith("/machine/window-frame-sync.json")
                        and "landscape-light-font-1.3" in name]
            assert len(selected) == 1, selected
            data = archive.read(selected[0])
            entry = tmp / "settings.json"
            entry.write_bytes(data)
            paths.append(entry)
            entries.append({"source": str(SETTINGS_ZIP), "sourceSha256": sha(SETTINGS_ZIP.read_bytes()),
                            "entry": selected[0], "entrySha256": sha(data)})
        scale = scale_proof.read_bytes()
        paths.append(scale_proof)
        entries.append({"source": str(scale_proof), "sourceSha256": sha(scale)})
        result = subprocess.run(["java", "-cp", str(classes), "io.openhoyi.mobile.WindowGeometryJsonEqualityOracle",
                                 *map(str, paths)], text=True, capture_output=True)
        if result.returncode:
            raise RuntimeError(result.stderr or result.stdout)
        report = {"oracle": "unmodified Android SDK34 org.json Java sources; annotation-only stubs",
                  "sourceSha256": {source.name: sha(source.read_bytes()) for source in sources},
                  "inputs": entries, "result": result.stdout.strip().splitlines(),
                  "hostTimingIsNotAndroid": True}
        (OUT / "report.json").write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n")
        print(result.stdout, end="")
        print(f"report={OUT / 'report.json'}")


if __name__ == "__main__":
    main()
