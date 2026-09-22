#!/usr/bin/env python3
"""Extract allowlisted wire evidence. Never copies IDs, authentication, or app source.
Usage: python3 scripts/extract_fixtures.py /path/to/protocol-recordings
The golden values below are independently transcribed wire/manual examples, not
obtained from the production decoder. Notification TSV preserves listener duplicates.
"""
import hashlib
import json
import pathlib
import sys

root = pathlib.Path(__file__).resolve().parents[1]
source = pathlib.Path(sys.argv[1])
out = root / 'protocol-core/src/test/resources'
out.mkdir(parents=True, exist_ok=True)
rows, starts, provenance = [], [], []
keys = ['segNum','press1','flow1','time1','press2','flow2','press3','flow3','press4','flow4','totalFlow','totalWeight','waitTime','tempSv','pressMode','flowMode','seg1FlowMode']
for index, path in enumerate(sorted(source.glob('*.jsonl')), 1):
    session = f'session-{index:02d}'
    provenance.append({'session': session, 'sourceSha256': hashlib.sha256(path.read_bytes()).hexdigest()})
    last_state = None
    for line in path.read_text().splitlines():
        record = json.loads(line)
        data = record['data']
        if record['kind'] == 'ui.before':
            state = data.get('state', {})
            if 'segNum' in state:
                last_state = {'seq': record['seq'], 'state': {k: state[k] for k in keys if k in state}}
        wire = data.get('hex', '')
        if record['kind'] == 'ble.notification':
            payload = data.get('payload', {})
            role = payload.get('role')
            if role not in ('coffee','scale'): continue
            frame = bytes.fromhex(wire)
            assert (role == 'coffee' and len(frame) in (13,15,19,20)) or (role == 'scale' and len(frame) == 20)
            if role == 'scale':
                from functools import reduce
                from operator import xor
                assert frame[:2] == b'\x03\x0b' and reduce(xor, frame) == 0
            rows.append(f"{session}\t{record['seq']}\t{record['elapsedMs']}\t{role}\t{wire}")
        elif record['kind'] == 'ble.request' and wire.startswith('02') and len(wire)==40:
            starts.append({'session': session, 'seq': record['seq'], 'hex': wire, 'uiSnapshot': last_state})
(out / 'notifications.tsv').write_text('# session\tseq\telapsedMs\trole\thex (listener deliveries, duplicates retained)\n' + '\n'.join(rows) + '\n')
(out / 'provenance.json').write_text(json.dumps({'sources': provenance, 'notificationCount':len(rows), 'redaction':'Only role, relative time, seq, wire notifications; no identifiers/authentication', 'startVectors':starts}, indent=2)+'\n')
# Fixed expectations transcribed independently from observed bytes / protocol offsets.
(out / 'golden.tsv').write_text('''# role\thex\tmanual expectation
coffee\t830113FD5C007D0F350019006E\tfirmware=1.1.3;flags=253;brewC=92;steamC=125;cups=25
coffee\t400024BF2F1C770B00000000000000190321AF\tbrewHundredthsC=9407;steamHundredthsC=12060;pressureTenthsBar=119;extraRaw=801
coffee\t80080700000000052421325103\tslotOrPhase=7;seconds=0;waterTenthsMl=5;brewHundredthsC=9249;flowTenths=50;status=81
scale\t030B000000012D007A3A2D03424600C803010084\tweightHundredthsGram=-31290;flowHundredthsRaw=-834;ASCII-sign interpretation inferred, known-mass test pending
''')
assert len(starts)==3
print(f'{len(rows)} notifications, {len(starts)} start vectors, {len(provenance)} source hashes')
