#!/usr/bin/env python3
"""Evaluate the supplied old App's local display methods; never invokes BLE.
Usage: generate_brew_feedback_oracle.py app-service.js output.json
"""
import hashlib
import json
from pathlib import Path
import subprocess
import sys
from generate_factory_wire_oracle import function

EXPECTED_SOURCE = "1451bddc95735fdc86b071aef98d9d5b88add12b0b3612c66f666faca7572ddc"
NODE = r'''
const vm=require('node:vm');
let input='';
process.stdin.on('data',p=>input+=p).on('end',()=>{
 const {levelHarness,phaseHarness,levels,phases}=JSON.parse(input);
 const context={P:{default:{playBrewEncourageAudio:()=>''}}};
 const level=vm.runInNewContext(levelHarness,context,{timeout:1000});
 const phase=vm.runInNewContext(phaseHarness,{}, {timeout:1000});
 const resultLevels=levels.map(([seconds,wait,early,final])=>{
  const page={isBrewTipsEnabledGlobal:()=>true,shellBrewEncLevelPlay:false,
   shellBrewEncLevel1Txt:Array(5).fill(''),shellBrewEncLevel2Txt:Array(5).fill(''),
   shellBrewEncLevel3Txt:Array(5).fill('')};
  level.call(page,seconds,final/10,early/10,wait);
  return [seconds,wait,early,final,page.shellBrewEncLevel??null];
 });
 const resultPhases=phases.map(([slot,flags,pressure,flow,secInc,flowInc])=>{
  const rx=Array(12).fill('0'); rx[2]=String(slot);rx[5]=String(pressure);
  rx[10]=String(flow);rx[11]=flags.toString(16);
  return [slot,flags,pressure,flow,secInc,flowInc,
   phase(rx,{secIncreasing:secInc,flowIncreasing:flowInc})];
 });
 process.stdout.write(JSON.stringify({levels:resultLevels,phases:resultPhases}));
});
'''

def generate(source_path: Path, output: Path) -> None:
    raw = source_path.read_bytes()
    digest = hashlib.sha256(raw).hexdigest()
    if digest != EXPECTED_SOURCE:
        raise ValueError("old App source differs from reviewed bundle")
    source = raw.decode("utf-8")
    level = function(source, "tryShellBrewEncourageShow: function", False)
    module = source[source.index('"1fc4": function'):source.index('2137: function')]
    helpers = "\n".join(function(module, f"function {name}(", True) for name in ("i", "a", "s", "r"))
    phase = function(module, "isExtractingPhase: function", False)
    level_harness = f"({level})"
    phase_harness = f"(function(){{\n{helpers}\nreturn {phase};}})()"
    levels = set()
    for seconds in (14, 15, 16, 25, 30, 60, 65535):
        for wait in (0, 5, 15, 127):
            denominator = max(1, seconds - wait - 10)
            for early in (0, 1, 11, 66, 100, 65000):
                deltas = {0, 1, 14, 15, 16, 21, 22, 23, 65535-early}
                deltas.update(denominator*rate+offset for rate in (15, 22) for offset in (-1, 0, 1))
                for delta in sorted(deltas):
                    final = early + delta
                    if 0 <= delta and final <= 65535:
                        levels.add((seconds, wait, early, final))
    phases = [(slot, flags, pressure, flow, sec_inc, flow_inc)
              for slot in (1, 6, 7, 8) for flags in (0, 32, 64, 96)
              for pressure in (0, 1, 10, 11) for flow in (0, 1, 8, 9)
              for sec_inc in (False, True) for flow_inc in (False, True)]
    result = subprocess.run(["node", "-e", NODE], input=json.dumps({
        "levelHarness": level_harness, "phaseHarness": phase_harness,
        "levels": sorted(levels), "phases": phases,
    }), text=True, capture_output=True, check=True, timeout=30)
    cases = json.loads(result.stdout)
    if len(cases["levels"]) != len(levels) or len(cases["phases"]) != len(phases):
        raise ValueError("incomplete old App evidence")
    for row in cases["levels"]:
        if row[-1] not in (None, 0, 1, 2):
            raise ValueError("invalid display level")
    if any(type(row[-1]) is not bool for row in cases["phases"]):
        raise ValueError("invalid phase result")
    evidence = {"format": "legacy-brew-feedback-v1", "sourceSha256": digest,
                "levelMethodSha256": hashlib.sha256(level.encode()).hexdigest(),
                "phaseHarnessSha256": hashlib.sha256(phase_harness.encode()).hexdigest(), **cases}
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(evidence, separators=(",", ":")) + "\n", encoding="utf-8")
    print(json.dumps({"levels": len(levels), "phases": len(phases), "bytes": output.stat().st_size}))

if __name__ == "__main__":
    if len(sys.argv) != 3:
        raise SystemExit("usage: generate_brew_feedback_oracle.py app-service.js output.json")
    generate(Path(sys.argv[1]), Path(sys.argv[2]))
