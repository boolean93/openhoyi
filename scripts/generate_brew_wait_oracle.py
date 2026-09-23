#!/usr/bin/env python3
"""Run the extracted legacy setBrewWait encoder for the bounded product inputs."""

import hashlib
import json
from pathlib import Path
import subprocess
import sys

from generate_factory_wire_oracle import function


def main() -> None:
    if len(sys.argv) != 3:
        raise SystemExit("usage: generate_brew_wait_oracle.py app-service.js output.tsv")
    source_path, output_path = map(Path, sys.argv[1:])
    source = source_path.read_text(encoding="utf-8")
    source_hash = hashlib.sha256(source_path.read_bytes()).hexdigest()
    module = source[source.index("8625: function"):source.index("8625: function") + 100000]
    methods = {name: function(module, f"{name}: function", False) for name in ("setBrewWait", "ten2Hex")}
    harness = "(function(){return {" + ",".join(f"{name}: {body}" for name, body in methods.items()) + "};})()"
    targets = [0] + list(range(75, 106))
    node = r'''
const vm=require('node:vm');
let input='';
process.stdin.on('data',chunk=>input+=chunk).on('end',()=>{
  const {harness,targets}=JSON.parse(input);
  const legacy=vm.runInNewContext(harness,{e:()=>{}},{timeout:1000});
  const rows=[];
  for(const target of targets){
    const writes=[];
    legacy.BleWrite=(hex,size)=>writes.push([hex.toUpperCase(),size]);
    legacy.setBrewWait(target);
    if(writes.length!==1||writes[0][1]!==5||!/^[0-9A-F]{10}$/.test(writes[0][0]))
      throw new Error('bad write '+target);
    rows.push([String(target),writes[0][0]]);
  }
  process.stdout.write(JSON.stringify(rows));
});
'''
    completed = subprocess.run(["node", "-e", node], input=json.dumps({"harness": harness, "targets": targets}),
                               text=True, capture_output=True, check=True, timeout=20)
    rows = json.loads(completed.stdout)
    if len(rows) != 32:
        raise ValueError("expected 32 brew-wait frames")
    output_path.parent.mkdir(parents=True, exist_ok=True)
    output_path.write_text("# brew-wait-wire-v1\tsource-sha256=" + source_hash + "\n" +
                           "\n".join("\t".join(row) for row in rows) + "\n", encoding="utf-8")
    print(f"generated {len(rows)} legacy brew-wait frames: {output_path}")


if __name__ == "__main__":
    main()
