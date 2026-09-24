#!/usr/bin/env python3
"""Build deterministic offline custom-curve cases and their old-App wire proof.

Usage: python3 scripts/generate_legacy_curve_edge_fixture.py app-service.js
No BLE device is used. The output is a test fixture, never a control allowlist.
"""

from copy import deepcopy
import json
from pathlib import Path
import random
import sys

from generate_factory_wire_oracle import normalized_rows
from generate_legacy_curve_oracle import generate


ROOT = Path(__file__).resolve().parents[1]
FACTORY = ROOT / "mobile/src/main/assets/factory_curves_v3.tsv"
EXPORT = ROOT / "mobile/src/test/resources/legacy_curve_edges.json"
PROOF = ROOT / "mobile/src/test/resources/legacy_curve_edges.tsv"


def rows() -> list[dict | None]:
    _, factory = normalized_rows(FACTORY)
    base = factory[0]["curve"]
    rng = random.Random(20260924)
    options = {
        "flow": (1, 2, 5, 10, 25, 70, 150, 255, 360, 500, 1000),
        "weight": (0, 1, 2, 3, 4, 5, 9, 10, 27, 180, 360, 600, 1200, 60000),
        "wdelta": (-1000, -10, 0, 10, 1000),
        "time": (0, 1, 15, 30, 127),
        "temp": (75, 92, 93, 99, 105),
        "seg": (1, 2, 3, 4),
        "time1": (0, 1, 15, 30, 255),
    }
    result: list[dict | None] = [None]
    for index in range(128):
        row = deepcopy(base)
        row["name"] = f"Synthetic edge {index:03d}"
        row["factory"] = False
        for key, values in options.items():
            row[key] = values[index % len(values)] if index < len(values) else rng.choice(values)
        row["press"] = bool(index & 1)
        row["chart"] = bool(index & 2)
        row["seg1FlowMode"] = bool(index & 4)
        for part in range(1, 5):
            row[f"press{part}"] = rng.choice((0, 1, 20, 90, 120, 255))
            row[f"flow{part}"] = rng.choice((0, 1, 3, 20, 60, 100, 500))
        result.append(row)
    result.append(None)
    return result


def main(bundle: Path) -> None:
    EXPORT.parent.mkdir(parents=True, exist_ok=True)
    EXPORT.write_text(json.dumps({"format": "openhoyi-legacy-curves-v1", "items": rows()},
                                 ensure_ascii=False, separators=(",", ":")), encoding="utf-8")
    count = generate(bundle, EXPORT, PROOF)
    if count != 128:
        raise ValueError("edge corpus incomplete")
    print(f"generated {count} synthetic rows and {count * 12} old-App frames")


if __name__ == "__main__":
    if len(sys.argv) != 2:
        raise SystemExit("usage: generate_legacy_curve_edge_fixture.py app-service.js")
    main(Path(sys.argv[1]))
