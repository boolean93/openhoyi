#!/usr/bin/env python3
"""Read-only evidence that hierarchy collection preserves the running disposable CI cup."""
import base64
import hashlib
import json
import re
import subprocess
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

PACKAGE = 'io.openhoyi.mobile.mock'


def rows(xml):
    value = ET.fromstring(xml).find("string[@name='entries_v1']")
    if value is None or not value.text:
        raise ValueError('Missing durable shot history')
    result = []
    for line in value.text.splitlines():
        # Android's SharedPreferences serializer can indent after the terminal newline.
        # This is XML formatting, not a history row; nonblank unknown rows still fail.
        if not line.strip():
            continue
        fields = line.split('\t')
        if len(fields) not in (8, 9, 10):
            raise ValueError('Unexpected shot history schema')
        decode = lambda s: base64.urlsafe_b64decode(s + '=' * (-len(s) % 4)).decode()
        result.append({'id': decode(fields[0]), 'curve': decode(fields[1]),
                       'startedAtMs': int(fields[2]), 'status': fields[5],
                       'observedRunning': len(fields) == 10 and fields[9] == 'true'})
    return result


def adb(*args):
    return subprocess.run(['adb', *args], check=True, text=True, capture_output=True, timeout=15).stdout


def snapshot(folder, phase):
    pid = adb('shell', 'pidof', PACKAGE).strip()
    if not re.fullmatch(r'[1-9][0-9]*', pid):
        raise ValueError('Expected one live Mock process')
    services = adb('shell', 'dumpsys', 'activity', 'services', PACKAGE)
    (folder / (phase + '-services.txt')).write_text(services)
    owners = set(re.findall(r'ServiceRecord\{([0-9a-f]+) u\d+ io\.openhoyi\.mobile\.mock/(?:io\.openhoyi\.mobile)?\.MobileService\}', services))
    if len(owners) != 1:
        raise ValueError('Expected one actual Mock MobileService owner')
    xml = adb('shell', 'run-as', PACKAGE, 'cat', 'shared_prefs/shot_history.xml')
    (folder / (phase + '-history.xml')).write_text(xml)
    running = [r for r in rows(xml) if r['status'] == 'RUNNING']
    if len(running) != 1 or running[0]['curve'] != 'factory-v3-002' or not running[0]['observedRunning']:
        raise ValueError('Expected the real factory-v3-002 running cup')
    epoch_seconds = int(adb('shell', 'date', '+%s').strip())
    return {'pid': pid, 'serviceToken': next(iter(owners)), 'shot': running[0],
            'elapsedLowerMs': epoch_seconds * 1000 - running[0]['startedAtMs'],
            'elapsedUpperMs': (epoch_seconds + 1) * 1000 - running[0]['startedAtMs']}


def same_owner(before, after):
    for key in ('pid', 'serviceToken', 'shot'):
        if before[key] != after[key]:
            raise ValueError('Capture changed running owner/cup: ' + key)


def sample_span(tsv, lower, upper):
    lines = tsv.splitlines()
    if not lines or lines[0] not in ('# openhoyi-shot-points-v3', '# openhoyi-shot-points-v4'):
        raise ValueError('Unexpected actual samples schema')
    count = 8 if lines[0].endswith('v3') else 9
    fields = [line.split('\t') for line in lines[1:]]
    if len(fields) < 2 or any(len(row) != count for row in fields):
        raise ValueError('Missing complete sample rows')
    times = [int(row[0]) for row in fields]
    if times[0] < 0 or any(a >= b for a, b in zip(times, times[1:])):
        raise ValueError('Samples reset or repeat elapsed time')
    if lower < 0 or times[0] > lower or times[-1] < upper:
        raise ValueError('Actual samples do not span hierarchy capture')
    return {'count': len(times), 'firstElapsedMs': times[0], 'lastElapsedMs': times[-1],
            'captureLowerMs': lower, 'captureUpperMs': upper,
            'sha256': hashlib.sha256(tsv.encode()).hexdigest()}


def main():
    phase, name, output = sys.argv[1:]
    folder = Path(output) / ('continuity-' + name)
    folder.mkdir(parents=True, exist_ok=True)
    if phase in ('before', 'after'):
        value = snapshot(folder, phase)
        if phase == 'after':
            same_owner(json.loads((folder / 'before.json').read_text()), value)
        (folder / (phase + '.json')).write_text(json.dumps(value, indent=2) + '\n')
    elif phase == 'ended':
        before = json.loads((folder / 'before.json').read_text())
        after = json.loads((folder / 'after.json').read_text())
        same_owner(before, after)
        shot_id = before['shot']['id']
        if not re.fullmatch(r'[A-Za-z0-9-]{1,64}', shot_id):
            raise ValueError('Unsafe persisted shot identity')
        xml = adb('shell', 'run-as', PACKAGE, 'cat', 'shared_prefs/shot_history.xml')
        (folder / 'ended-history.xml').write_text(xml)
        ended = [r for r in rows(xml) if r['id'] == shot_id]
        if len(ended) != 1 or ended[0] != dict(before['shot'], status='ENDED'):
            raise ValueError('Original captured cup did not end continuously')
        tsv = adb('shell', 'run-as', PACKAGE, 'cat', 'files/shot_samples/samples-' + shot_id + '.tsv')
        (folder / 'ended-samples.tsv').write_text(tsv)
        evidence = sample_span(tsv, before['elapsedLowerMs'], after['elapsedUpperMs'])
        evidence.update({'samePid': True, 'sameServiceOwner': True, 'sameCup': True,
                         'shotId': shot_id, 'actualSampleSpan': True,
                         'note': 'Strict monotonic stored samples span capture; no exact Handler cadence claim.'})
        (folder / 'accepted.json').write_text(json.dumps(evidence, indent=2) + '\n')
    else:
        raise ValueError('Unknown continuity phase')


if __name__ == '__main__':
    main()
