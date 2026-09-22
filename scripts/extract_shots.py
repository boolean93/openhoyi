#!/usr/bin/env python3
"""Sanitized replay input: only known notification and start/stop/tare commands."""
from pathlib import Path
import json,sys
source=Path(sys.argv[1]);out=Path(__file__).resolve().parents[1]/'device-session/src/test/resources/shots.tsv'
lines=['# shot\tdeltaMs\tkind\trole\thex']
shot=0
for path in sorted(source.glob('*.jsonl')):
    rows=[json.loads(x) for x in path.read_text().splitlines()]
    for start in rows:
        if start['kind']!='ble.request' or start['data'].get('hex','')[:2]!='02' or start['data'].get('length')!=20:continue
        shot+=1;t=start['time']
        for r in rows:
            if not -2000<=r['time']-t<=60000:continue
            d=r['data'];wire=d.get('hex','')
            if r['kind']=='ble.notification':
                role=d.get('payload',{}).get('role')
                if role in ('coffee','scale'):lines.append(f'{shot}\t{r["time"]-t}\trx\t{role}\t{wire}')
            elif r['kind']=='ble.request' and (wire.startswith('02') or wire=='030A01000008'):
                kind='manual-stop' if wire=='0200070000' and r.get('actionId') else 'tx'
                lines.append(f'{shot}\t{r["time"]-t}\t{kind}\t{d["role"]}\t{wire}')
assert shot==3
out.write_text('\n'.join(lines)+'\n')
print(f'{shot} shot traces, {len(lines)-1} observations')
