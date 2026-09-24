#!/usr/bin/env python3
"""Generate offline old-App wire evidence for one exported hy_chartLib snapshot.

This tool never writes to BLE and its TSV does not authorize runtime control.
Usage: python3 scripts/generate_legacy_curve_oracle.py app-service.js export.json proof.tsv
"""

import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import tempfile

from generate_factory_wire_oracle import function


MAX_BYTES = 16 * 1024 * 1024
EXPECTED_ENCODER_SHA256 = "635ddbe7bbc63d8d74b5053cd5c2ca1d5e8a0dfab0f24447171e5043fd01855f"
FRAME = re.compile(r"02[0-9A-F]{38}")


def load_export(path: Path) -> tuple[str, list[dict]]:
    raw = path.read_bytes()
    if len(raw) > MAX_BYTES:
        raise ValueError("curve export exceeds 16 MiB")
    data = json.loads(raw.decode("utf-8"))
    if not isinstance(data, dict) or data.get("format") != "openhoyi-legacy-curves-v1":
        raise ValueError("unsupported curve export format")
    rows = data.get("items")
    if not isinstance(rows, list) or not 1 <= len(rows) <= 2000:
        raise ValueError("invalid curve list length")
    if any(row is not None and not isinstance(row, dict) for row in rows):
        raise ValueError("curve list contains a non-object row")
    return hashlib.sha256(raw).hexdigest(), rows


def encoder_harness(source: str) -> str:
    module = source[source.index("8625: function"):source.index("8625: function") + 100000]
    helpers = "\n".join(function(module, f"function {name}(", True)
                        for name in ("m", "g", "w", "_", "b", "x"))
    methods = {name: function(module, f"{name}: function", False)
               for name in ("startTempChart", "startChart", "ten2Hex", "xor_fun")}
    return "(function(){\n" + helpers + "\nreturn {" + ",".join(
        f"{name}: {body}" for name, body in methods.items()) + "};})()"


NODE = r'''
const vm=require('node:vm');
let input='';
process.stdin.on('data',part=>input+=part).on('end',()=>{
  const {harness,rows}=JSON.parse(input);
  const data={scaleSta:false};
  const legacy=vm.runInNewContext(harness,{getApp:()=>({globalData:data}),e:()=>{}},{timeout:1000});
  const result=[];
  function frame(row,slot,scale){
    data.scaleSta=scale;
    data.chartList=Array(5).fill(null);
    if(slot!==7)data.chartList[slot-1]=row;
    const writes=[];
    legacy.BleWrite=(hex,size)=>writes.push({hex,size});
    if(slot===7)legacy.startTempChart(7,row,1);
    else legacy.startChart(slot,1);
    if(writes.length!==1||writes[0].size!==20||!/^[0-9a-f]{40}$/i.test(writes[0].hex))
      throw new Error('invalid old-App frame at slot '+slot);
    return writes[0].hex.toUpperCase();
  }
  rows.forEach((row,index)=>{
    if(row===null)return;
    for(const slot of [7,1,2,3,4,5])
      result.push([index,slot,frame(row,slot,false),frame(row,slot,true)]);
  });
  process.stdout.write(JSON.stringify(result));
});
'''


def generate(bundle: Path, export: Path, output: Path) -> int:
    export_hash, rows = load_export(export)
    source_bytes = bundle.read_bytes()
    source_hash = hashlib.sha256(source_bytes).hexdigest()
    harness = encoder_harness(source_bytes.decode("utf-8"))
    encoder_hash = hashlib.sha256(harness.encode("utf-8")).hexdigest()
    if encoder_hash != EXPECTED_ENCODER_SHA256:
        raise ValueError("old-App encoder differs from the verified source")
    result = subprocess.run(["node", "-e", NODE], input=json.dumps({"harness": harness, "rows": rows}),
                            text=True, capture_output=True, check=True, timeout=60)
    entries = json.loads(result.stdout)
    active_indices = [index for index, row in enumerate(rows) if row is not None]
    if len(entries) != len(active_indices) * 6:
        raise ValueError("incomplete old-App curve evidence")
    lines = [f"# legacy-curve-wire-v1\tsource-sha256={source_hash}\tencoder-sha256={encoder_hash}\texport-sha256={export_hash}"]
    for position, entry in enumerate(entries):
        index, slot, no_scale, scale = entry
        curve_position, expected_slot = divmod(position, 6)
        expected_index = active_indices[curve_position]
        expected_slot = (7, 1, 2, 3, 4, 5)[expected_slot]
        if index != expected_index or slot != expected_slot or not all(
                isinstance(value, str) and FRAME.fullmatch(value) for value in (no_scale, scale)):
            raise ValueError(f"invalid old-App curve evidence at {position}")
        lines.append(f"{index}\t{slot}\t{no_scale}\t{scale}")
    output.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.NamedTemporaryFile("w", encoding="utf-8", dir=output.parent,
                                     prefix="curve-oracle-", delete=False) as temporary:
        temporary.write("\n".join(lines) + "\n")
        temporary_path = Path(temporary.name)
    try:
        os.replace(temporary_path, output)
    finally:
        temporary_path.unlink(missing_ok=True)
    return len(active_indices)


if __name__ == "__main__":
    if len(sys.argv) != 4:
        raise SystemExit("usage: generate_legacy_curve_oracle.py app-service.js export.json proof.tsv")
    count = generate(*(Path(value) for value in sys.argv[1:]))
    print(f"generated {count} nonempty curves × 6 slots × 2 scale modes")
