#!/usr/bin/env python3
"""Run only extracted legacy encoding functions against normalized factory rows."""

import base64
import csv
import hashlib
import json
from pathlib import Path
import subprocess
import sys


def balanced(source: str, marker: str) -> str:
    start = source.index(marker)
    begin = source.index("{", start)
    depth = 0
    quote = None
    escaped = False
    for end in range(begin, len(source)):
        char = source[end]
        if quote:
            if escaped:
                escaped = False
            elif char == "\\":
                escaped = True
            elif char == quote:
                quote = None
            continue
        if char in ('"', "'", chr(96)):
            quote = char
        elif char == "{":
            depth += 1
        elif char == "}":
            depth -= 1
            if depth == 0:
                return source[start:end + 1]
    raise ValueError(f"unclosed source for {marker}")


def function(source: str, marker: str, declaration: bool) -> str:
    piece = balanced(source, marker)
    return piece if declaration else piece[piece.index("function"):]


def normalized_rows(path: Path) -> tuple[str, list[dict]]:
    lines = path.read_text(encoding="utf-8").splitlines()
    prefix, source_hash = lines[0].split("source-sha256=")
    if prefix != "# factory-v3\t" or len(source_hash) != 64:
        raise ValueError("wrong factory catalog header")
    fields = ("flow", "weight", "wdelta", "time", "temp", "press", "chart", "seg",
              "press1", "flow1", "time1", "press2", "flow2", "press3", "flow3", "press4", "flow4")
    rows = []
    for row in csv.reader(lines[1:], delimiter="\t"):
        if len(row) != 22:
            raise ValueError("wrong factory field count")
        data = {key: int(value) for key, value in zip(fields, row[5:])}
        data["press"] = bool(data["press"])
        data["chart"] = bool(data["chart"])
        data["name"] = base64.urlsafe_b64decode(row[2]).decode("utf-8")
        rows.append({"id": row[0], "curve": data})
    if len(rows) != 100:
        raise ValueError("expected 100 factory curves")
    return source_hash, rows


def main() -> None:
    if len(sys.argv) not in (4, 5):
        raise SystemExit("usage: generate_factory_wire_oracle.py app-service.js factory_curves_v3.tsv temp.tsv [slots.tsv]")
    bundle, catalog, output = map(Path, sys.argv[1:4])
    slot_output = Path(sys.argv[4]) if len(sys.argv) == 5 else None
    source = bundle.read_text(encoding="utf-8")
    expected_hash, rows = normalized_rows(catalog)
    if hashlib.sha256(bundle.read_bytes()).hexdigest() != expected_hash:
        raise ValueError("catalog and legacy source differ")
    module = source[source.index("8625: function"):source.index("8625: function") + 100000]
    helpers = "\n".join(function(module, f"function {name}(", True) for name in ("m", "g", "w", "_", "b", "x"))
    methods = {name: function(module, f"{name}: function", False)
               for name in ("startTempChart", "startChart", "ten2Hex", "xor_fun")}
    harness = "(function(){\n" + helpers + "\nreturn {" + ",".join(
        f"{name}: {body}" for name, body in methods.items()) + "};})()"
    node = r'''
const vm = require('node:vm');
let input='';
process.stdin.on('data',chunk=>input+=chunk).on('end',()=>{
  const {harness,rows}=JSON.parse(input);
  const data={scaleSta:false};
  const context={getApp:()=>({globalData:data}),e:()=>{}};
  const legacy=vm.runInNewContext(harness,context,{timeout:1000});
  const result=[],slotResult=[];
  for(const item of rows){
    const frames=[];
    for(const scale of [false,true]){
      data.scaleSta=scale;
      const writes=[];
      legacy.BleWrite=(hex,size)=>writes.push({hex,size});
      legacy.startTempChart(7,item.curve,1);
      if(writes.length!==1||writes[0].size!==20||!/^[0-9a-f]{40}$/i.test(writes[0].hex))
        throw new Error('invalid legacy write '+item.id+': '+JSON.stringify(writes));
      frames.push(writes[0].hex.toUpperCase());
    }
    result.push([item.id,...frames]);
    for(let slot=1;slot<=5;slot++){
      const slotFrames=[];
      for(const scale of [false,true]){
        data.scaleSta=scale;
        data.chartList=Array(5).fill(null);
        data.chartList[slot-1]=item.curve;
        const writes=[];
        legacy.BleWrite=(hex,size)=>writes.push({hex,size});
        legacy.startChart(slot,1);
        if(writes.length!==1||writes[0].size!==20||!/^[0-9a-f]{40}$/i.test(writes[0].hex))
          throw new Error('invalid legacy slot write '+item.id+'/'+slot);
        slotFrames.push(writes[0].hex.toUpperCase());
      }
      slotResult.push([item.id,String(slot),...slotFrames]);
    }
  }
  process.stdout.write(JSON.stringify({result,slotResult}));
});
'''
    result = subprocess.run(["node", "-e", node], input=json.dumps({"harness": harness, "rows": rows}),
                            text=True, capture_output=True, check=True, timeout=20)
    decoded = json.loads(result.stdout)
    frames = decoded["result"]
    lines = [f"# factory-wire-v1\tsource-sha256={expected_hash}"]
    lines += ["\t".join(item) for item in frames]
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(f"generated {len(frames)} two-mode factory frames: {output}")
    if slot_output is not None:
        slot_rows = decoded["slotResult"]
        if len(slot_rows) != 500:
            raise ValueError("expected 500 slot rows")
        slot_lines = [f"# factory-slot-wire-v1\tsource-sha256={expected_hash}"]
        slot_lines += ["\t".join(item) for item in slot_rows]
        slot_output.parent.mkdir(parents=True, exist_ok=True)
        slot_output.write_text("\n".join(slot_lines) + "\n", encoding="utf-8")
        print(f"generated {len(slot_rows)} two-mode slot rows: {slot_output}")


if __name__ == "__main__":
    main()
