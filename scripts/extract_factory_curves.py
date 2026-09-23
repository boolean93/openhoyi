#!/usr/bin/env python3
"""Extract the legacy factory-v3 data literal without executing the app bundle.

Usage: python3 scripts/extract_factory_curves.py path/to/app-service.js mobile/src/main/assets/factory_curves_v3.tsv
Node's VM evaluates only the bracketed array literal, not the surrounding bundle.
"""

import argparse
import base64
import hashlib
import json
import math
from pathlib import Path
import subprocess

FIELDS = ("flow", "weight", "wdelta", "time", "temp", "press", "chart", "seg",
          "press1", "flow1", "time1", "press2", "flow2", "press3", "flow3", "press4", "flow4")
NODE = """const vm=require('node:vm');let text='';process.stdin.on('data',x=>text+=x).on('end',()=>{const data=vm.runInNewContext('('+text+')',Object.create(null),{timeout:1000});process.stdout.write(JSON.stringify(data))})"""


def data_literal(bundle: str) -> str:
    module = bundle.index('"0ffc": function')
    start = bundle.index("var i = [", module) + len("var i = ")
    depth = 0
    quote = None
    escaped = False
    for end in range(start, len(bundle)):
        char = bundle[end]
        if quote:
            if escaped:
                escaped = False
            elif char == "\\":
                escaped = True
            elif char == quote:
                quote = None
            continue
        if char in ('"', "'"):
            quote = char
        elif char == "[":
            depth += 1
        elif char == "]":
            depth -= 1
            if depth == 0:
                return bundle[start:end + 1]
    raise ValueError("factory curve array not closed")


def normalize(row: dict) -> dict:
    """Port of module 0ffc normalizeFactoryChartFlow; all integer inputs are checked later."""
    out = row.copy()
    weight_grams = 0.1 * out["weight"]
    flow = out["flow"]
    if 32 <= weight_grams <= 40:
        if flow < 65 or flow > 78:
            flow = 70
    elif 20 <= weight_grams < 32:
        flow = max(flow, max(45, math.ceil(1.9 * weight_grams)))
    elif weight_grams > 40:
        flow = max(flow, math.ceil(1.8 * weight_grams))
    segments = max(0, min(4, out["seg"]))
    if segments:
        names = [f"flow{i}" for i in range(1, segments + 1)]
        delta = flow - sum(out[name] for name in names)
        if abs(delta) >= 1:
            last = names[-1]
            if 32 <= weight_grams <= 40:
                out[last] = max(5, flow - sum(out[name] for name in names[:-1]))
            else:
                out[last] = max(5, out[last] + delta)
                flow = sum(out[name] for name in names)
    out["flow"] = flow
    return out


def encoded(value: str) -> str:
    return base64.urlsafe_b64encode(value.encode("utf-8")).decode("ascii")


def main() -> None:
    args = argparse.ArgumentParser()
    args.add_argument("bundle", type=Path)
    args.add_argument("output", type=Path)
    parsed = args.parse_args()
    source = parsed.bundle.read_text(encoding="utf-8")
    literal = data_literal(source)
    result = subprocess.run(["node", "-e", NODE], input=literal, text=True,
                            capture_output=True, check=True, timeout=5)
    rows = json.loads(result.stdout)
    if len(rows) != 100 or {category: sum(row["category"] == category for row in rows)
                            for category in ("dark", "medium", "light", "super")} != dict.fromkeys(
                            ("dark", "medium", "light", "super"), 25):
        raise ValueError("factory-v3 expected 100 curves in four groups of 25")
    expected = set(FIELDS) | {"category", "name", "tips", "icon", "factory"}
    lines = ["# factory-v3\tsource-sha256=" + hashlib.sha256(parsed.bundle.read_bytes()).hexdigest()]
    for index, raw in enumerate(rows, 1):
        if set(raw) != expected or raw["factory"] is not True:
            raise ValueError(f"unexpected fields or factory flag at index {index}")
        row = normalize(raw)
        if any(type(row[field]) is not (bool if field in ("press", "chart") else int) for field in FIELDS):
            raise ValueError(f"unexpected field type at index {index}")
        fields = [f"factory-v3-{index:03d}", row["category"], encoded(row["name"]),
                  encoded(row["tips"]), encoded(row["icon"])]
        fields += [str(int(row[field])) for field in FIELDS]
        lines.append("\t".join(fields))
    parsed.output.parent.mkdir(parents=True, exist_ok=True)
    parsed.output.write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(f"extracted {len(rows)} factory-v3 curves to {parsed.output}")


if __name__ == "__main__":
    main()
