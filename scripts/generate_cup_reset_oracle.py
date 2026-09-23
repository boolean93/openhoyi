#!/usr/bin/env python3
"""Run the legacy reset-cups encoder in isolation and record its single frame."""

import hashlib
from pathlib import Path
import subprocess
import sys

from generate_factory_wire_oracle import function


def main() -> None:
    if len(sys.argv) != 3:
        raise SystemExit("usage: generate_cup_reset_oracle.py app-service.js output.tsv")
    bundle, output = map(Path, sys.argv[1:])
    source = bundle.read_text(encoding="utf-8")
    module = source[source.index("8625: function"):source.index("8625: function") + 100000]
    reset = function(module, "rstCups: function", False)
    to_hex = function(module, "ten2Hex: function", False)
    node = """
const vm=require('node:vm');
const writes=[];
const legacy=vm.runInNewContext(`({rstCups:%s,ten2Hex:%s})`,{e:()=>{}},{timeout:1000});
legacy.BleWrite=(hex,size)=>writes.push([hex.toUpperCase(),size]);
legacy.rstCups();
if(writes.length!==1||writes[0][1]!==5||!/^[0-9A-F]{10}$/.test(writes[0][0]))
  throw new Error('bad legacy cup reset write');
process.stdout.write(writes[0][0]);
""" % (reset, to_hex)
    result = subprocess.run(["node", "-e", node], text=True, capture_output=True, check=True, timeout=20)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text("# cup-reset-wire-v1\tsource-sha256=" + hashlib.sha256(bundle.read_bytes()).hexdigest() +
                      "\n" + result.stdout + "\n", encoding="utf-8")
    print(f"generated legacy cup-reset frame: {output}")


if __name__ == "__main__":
    main()
