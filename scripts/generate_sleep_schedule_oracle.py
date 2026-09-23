#!/usr/bin/env python3
"""Execute the legacy two-fragment weekly sleep encoder for fixed test schedules."""

import hashlib
import json
from pathlib import Path
import subprocess
import sys

from generate_factory_wire_oracle import function


def main() -> None:
    if len(sys.argv) != 3:
        raise SystemExit("usage: generate_sleep_schedule_oracle.py app-service.js output.tsv")
    bundle, output = map(Path, sys.argv[1:])
    source = bundle.read_text(encoding="utf-8")
    module = source[source.index("8625: function"):source.index("8625: function") + 100000]
    methods = {name: function(module, f"{name}: function", False)
               for name in ("setSleepArrTime", "ten2Hex")}
    harness = "(function(){return {" + ",".join(f"{name}: {body}" for name, body in methods.items()) + "};})()"
    cases = [
        ("all_enabled", [[1, 22, 15, 7, 30] for _ in range(7)]),
        ("all_disabled", [[0, 0, 0, 0, 0] for _ in range(7)]),
        ("distinct_days", [[i % 2, i + 1, i + 10, 12 + i, 40 + i] for i in range(7)]),
    ]
    node = r'''
const vm=require('node:vm');
let input='';
process.stdin.on('data',chunk=>input+=chunk).on('end',()=>{
  const {harness,cases}=JSON.parse(input);
  const rows=[];
  for(const [name,days] of cases){
    const writes=[];
    const globalData={sleepArr:days.map(day=>({enable:day[0],time:day.slice(1)}))};
    const legacy=vm.runInNewContext(harness,{e:()=>{},getApp:()=>({globalData})},{timeout:1000});
    legacy.BleWrite=(hex,size)=>writes.push([hex.toUpperCase(),size]);
    legacy.setSleepArrTime(1);
    legacy.setSleepArrTime(0);
    if(writes.length!==2||writes[0][1]!==19||writes[1][1]!==15||
       !/^[0-9A-F]{38}$/.test(writes[0][0])||!/^[0-9A-F]{30}$/.test(writes[1][0]))
      throw new Error('bad legacy sleep writes '+name);
    rows.push([name,JSON.stringify(days),writes[0][0],writes[1][0]]);
  }
  process.stdout.write(JSON.stringify(rows));
});
'''
    result = subprocess.run(["node", "-e", node], input=json.dumps({"harness": harness, "cases": cases}),
                            text=True, capture_output=True, check=True, timeout=20)
    rows = json.loads(result.stdout)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text("# sleep-schedule-wire-v1\tsource-sha256=" + hashlib.sha256(bundle.read_bytes()).hexdigest() + "\n" +
                      "\n".join("\t".join(row) for row in rows) + "\n", encoding="utf-8")
    print(f"generated {len(rows)} legacy schedules: {output}")


if __name__ == "__main__":
    main()
