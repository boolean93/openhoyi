#!/usr/bin/env python3
"""Execute only legacy setting encoders to produce bounded Kotlin parity fixtures."""

import hashlib
import json
from pathlib import Path
import subprocess
import sys

from generate_factory_wire_oracle import function


def main() -> None:
    if len(sys.argv) != 3:
        raise SystemExit("usage: generate_machine_setting_oracle.py app-service.js output.tsv")
    bundle, output = map(Path, sys.argv[1:])
    source = bundle.read_text(encoding="utf-8")
    source_hash = hashlib.sha256(bundle.read_bytes()).hexdigest()
    module = source[source.index("8625: function"):source.index("8625: function") + 100000]
    methods = {name: function(module, f"{name}: function", False) for name in
               ("setTemp", "setSteamTemp", "setTempPowerEn", "setSteamPowerEn", "setLedEn", "setPress", "setStandby", "ten2Hex")}
    harness = "(function(){return {" + ",".join(f"{name}: {body}" for name, body in methods.items()) + "};})()"
    cases = (
        [["brew", value, "setTemp"] for value in range(75, 106)] +
        [["steam", value, "setSteamTemp"] for value in range(110, 146)] +
        [[kind, value, method] for kind, method in
         (("brew_heat", "setTempPowerEn"), ("steam_heat", "setSteamPowerEn"), ("light", "setLedEn"))
         for value in (0, 1)] +
        [["lever", value, "setPress"] for value in (0, 10, 11)] +
        [["standby", code * 256 + temperature, "setStandby"] for code in range(5) for temperature in (70, 92)]
    )
    node = r'''
const vm=require('node:vm');
let input='';
process.stdin.on('data',chunk=>input+=chunk).on('end',()=>{
  const {harness,cases}=JSON.parse(input);
  const legacy=vm.runInNewContext(harness,{e:()=>{}},{timeout:1000});
  const result=[];
  for(const [kind,value,method] of cases){
    const writes=[];
    legacy.BleWrite=(hex,size)=>writes.push([hex.toUpperCase(),size]);
    if(kind==='lever') legacy.setPress(value>=10,value%10===1);
    else if(kind==='standby') legacy.setStandby(Math.floor(value/256),value%256);
    else legacy[method](value);
    if(writes.length!==1||writes[0][1]!==5||!/^[0-9A-F]{10}$/.test(writes[0][0]))
      throw new Error('bad write '+kind+'/'+value);
    result.push([kind,String(value),writes[0][0]]);
  }
  process.stdout.write(JSON.stringify(result));
});
'''
    result = subprocess.run(["node", "-e", node], input=json.dumps({"harness": harness, "cases": cases}),
                            text=True, capture_output=True, check=True, timeout=20)
    rows = json.loads(result.stdout)
    if len(rows) != 86:
        raise ValueError("expected 86 setting frames")
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text("# machine-setting-wire-v1\tsource-sha256=" + source_hash + "\n" +
                      "\n".join("\t".join(row) for row in rows) + "\n", encoding="utf-8")
    print(f"generated {len(rows)} legacy setting frames: {output}")


if __name__ == "__main__":
    main()
