#!/usr/bin/env python3
"""Tap an unambiguous visible Mock control; never interact with Alpha or devices."""
import argparse
import hashlib
import json
import re
import subprocess
import sys
import tempfile
import time
import uuid
import os
import xml.etree.ElementTree as ET
from pathlib import Path

PACKAGE = 'io.openhoyi.mobile.mock'


class AmbiguousMockControl(ValueError):
    def __init__(self, text, candidates, raw_centers, centers):
        super().__init__(f'ambiguous Mock control: {text}')
        self.candidates = candidates
        self.raw_centers = raw_centers
        self.centers = centers


def node_record(node, paths):
    return {'path': paths[node], 'attributes': dict(node.attrib)}


def find_target(xml, text, contains=False, node_class=None):
    root = ET.fromstring(xml)
    parents = {child: parent for parent in root.iter() for child in parent}
    paths = {root: '0'}
    for parent in root.iter():
        for index, child in enumerate(parent):
            paths[child] = f'{paths[parent]}/{index}'
    targets = set()
    raw_centers = []
    candidates = []
    for node in root.iter('node'):
        labels = (node.get('text', ''), node.get('content-desc', ''))
        sources = [source for source, value in zip(('text', 'content-desc'), labels)
                   if (text in value if contains else text == value)]
        if not sources:
            continue
        record = {'matchedNode': node_record(node, paths), 'matchedSources': sources,
                  'ancestorChain': [], 'selectionReason': None, 'selectedNode': None,
                  'selectedBounds': None, 'center': None, 'invalidReason': None}
        candidates.append(record)
        if node_class is not None and node.get('class') != node_class:
            record['invalidReason'] = 'class-filtered'
            continue
        candidate = node
        child = node
        while candidate is not None:
            record['ancestorChain'].append(node_record(candidate, paths))
            if candidate.get('class') == 'android.widget.ListView' and candidate.get('enabled') == 'true':
                candidate = child
                record['selectionReason'] = 'list-row'
                record['selectedNode'] = node_record(candidate, paths)
                record['selectedBounds'] = candidate.get('bounds', '')
                bounds = re.fullmatch(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]', candidate.get('bounds', ''))
                if bounds:
                    x1, y1, x2, y2 = map(int, bounds.groups())
                    if x2 > x1 and y2 > y1:
                        point = ((x1 + x2) // 2, (y1 + y2) // 2)
                        targets.add(point)
                        raw_centers.append(list(point))
                        record['center'] = list(point)
                    else: record['invalidReason'] = 'non-positive bounds'
                else: record['invalidReason'] = 'invalid bounds'
                break
            if candidate.get('clickable') == 'true' and candidate.get('enabled') == 'true':
                record['selectionReason'] = 'clickable-ancestor'
                record['selectedNode'] = node_record(candidate, paths)
                record['selectedBounds'] = candidate.get('bounds', '')
                bounds = re.fullmatch(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]', candidate.get('bounds', ''))
                if bounds:
                    x1, y1, x2, y2 = map(int, bounds.groups())
                    if x2 > x1 and y2 > y1:
                        point = ((x1 + x2) // 2, (y1 + y2) // 2)
                        targets.add(point)
                        raw_centers.append(list(point))
                        record['center'] = list(point)
                    else: record['invalidReason'] = 'non-positive bounds'
                else: record['invalidReason'] = 'invalid bounds'
                break
            child, candidate = candidate, parents.get(candidate)
        if record['selectionReason'] is None: record['invalidReason'] = 'no enabled clickable ancestor or ListView'
    if len(targets) > 1:
        raise AmbiguousMockControl(text, candidates, raw_centers, [list(point) for point in sorted(targets)])
    return next(iter(targets), None)


def save_ambiguity_diagnostics(directory, xml, error, selector, contains, attempt, completed_scrolls, foreground,
                               node_class=None):
    parent = Path(directory)
    parent.mkdir(parents=True, exist_ok=True)
    folder = Path(tempfile.mkdtemp(prefix='ambiguous-', dir=parent))
    source = xml.encode('utf-8')
    (folder / 'hierarchy.xml').write_bytes(source)
    record = {'selector': selector, 'contains': contains, 'nodeClass': node_class, 'attempt': attempt,
              'completedScrollDirections': completed_scrolls,
              'foregroundCheckBeforeHierarchy': foreground,
              'hierarchySha256': hashlib.sha256(source).hexdigest(),
              'candidates': error.candidates, 'rawCenters': error.raw_centers,
              'centers': error.centers, 'outcome': 'NO_TAP_AFTER_AMBIGUITY'}
    (folder / 'candidates.json').write_text(json.dumps(record, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    return folder


def adb(*args, include_stderr=False, timeout=30):
    result = subprocess.run(['adb', *args], check=True, capture_output=True, text=True, timeout=timeout)
    return result.stdout + (result.stderr if include_stderr else '')


def ensure_mock(timeout=30):
    state = adb('shell', 'dumpsys', 'activity', 'activities', timeout=timeout)
    if not re.search(r'topResumedActivity=.*\b' + re.escape(PACKAGE) + r'/', state):
        raise ValueError('foreground activity is not isolated Mock')
    return state


TEST_PACKAGE = 'io.openhoyi.mobile.mock.test'
TREE_RUNNER = TEST_PACKAGE + '/io.openhoyi.mobile.MockHierarchyInstrumentation'


def validate_runner_manifest(xml):
    root = ET.fromstring(xml)
    android = '{http://schemas.android.com/apk/res/android}'
    expected = {'io.openhoyi.mobile.BrewAudioInstrumentation': PACKAGE,
                'io.openhoyi.mobile.MockHierarchyInstrumentation': TEST_PACKAGE}
    entries = root.findall('instrumentation')
    actual = {n.get(android + 'name'): n.get(android + 'targetPackage') for n in entries}
    app = root.find('application')
    if (root.get('package') != TEST_PACKAGE or len(entries) != 2 or actual != expected
            or app is None or app.get(android + 'debuggable') != 'true'
            or app.findall('activity') or app.findall('activity-alias')):
        raise ValueError('Packaged test runners/targets/application contract differs')
    return actual


def validate_installed_runners(text):
    entries = re.findall(r'^instrumentation:' + re.escape(TEST_PACKAGE) + r'/([^ ]+) \(target=([^)]*)\)$', text, re.MULTILINE)
    expected = {'io.openhoyi.mobile.BrewAudioInstrumentation': PACKAGE,
                'io.openhoyi.mobile.MockHierarchyInstrumentation': TEST_PACKAGE}
    if len(entries) != 2 or dict(entries) != expected:
        raise ValueError('Installed runner targets do not match isolated Mock/self contract')
    return dict(entries)


def validate_hierarchy(xml, nonce):
    root = ET.fromstring(xml)
    if (root.tag != 'hierarchy' or root.get('schema') != 'mock-active-tree-v1'
            or root.get('nonce') != nonce or root.get('package') != PACKAGE):
        raise ValueError('Missing/stale capture identity')
    nodes = list(root.iter('node'))
    completed = root.findall('capture')
    if len(completed) != 1 or len(nodes) < 2:
        raise ValueError('Empty/incomplete real hierarchy')
    done = completed[0]
    if (done.get('complete') != 'true' or int(done.get('nodes', '-1')) != len(nodes)
            or root.get('window-id') != done.get('window-id') or int(root.get('window-id', '-1')) < 0
            or root.get('bounds') != done.get('bounds')
            or not 0 < int(root.get('started-elapsed-nanos', '0')) <= int(done.get('finished-elapsed-nanos', '0'))):
        raise ValueError('Capture window/completion changed or unknown')
    bounds = re.fullmatch(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]', root.get('bounds', ''))
    if not bounds or not (int(bounds[3]) > int(bounds[1]) and int(bounds[4]) > int(bounds[2])):
        raise ValueError('Empty real window bounds')
    if any(node.get('package') != PACKAGE or not node.get('class') for node in nodes):
        raise ValueError('Foreign/unknown package or class in tree')
    return root


def read_hierarchy():
    # One 30-second total request, real accessibility tree, no global-idle requirement.
    nonce = str(uuid.uuid4())
    relative = 'files/mock-hierarchy/' + nonce + '.xml'
    deadline = time.monotonic() + 30
    record = {'nonce': nonce, 'runner': TREE_RUNNER, 'scope': 'REAL_ACTIVE_TREE_INTERVAL_NOT_ATOMIC_SCREENSHOT'}
    xml = None
    failure = None
    def remaining():
        left = deadline - time.monotonic()
        if left <= 0: raise TimeoutError('Active hierarchy exceeded original 30-second budget')
        return left
    def pid():
        value = adb('shell', 'pidof', PACKAGE, timeout=remaining()).strip()
        if not re.fullmatch(r'[1-9]\d*', value): raise ValueError('Unknown/ambiguous Mock PID')
        return value
    try:
        ensure_mock(timeout=remaining())
        record['pidBefore'] = pid()
        adb('shell', 'run-as', TEST_PACKAGE, 'rm', '-f', relative, timeout=remaining())
        output = adb('shell', 'am', 'instrument', '-w', '-e', 'nonce', nonce, TREE_RUNNER,
                     include_stderr=True, timeout=remaining())
        record['instrumentation'] = output
        if 'MOCK_ACTIVE_TREE_CAPTURED nonce=' + nonce + ' ' not in output:
            raise ValueError('Real active-tree collector failed: ' + output)
        try:
            xml = adb('shell', 'run-as', TEST_PACKAGE, 'cat', relative, timeout=remaining())
        except subprocess.CalledProcessError as error:
            raise ValueError(f'Mock hierarchy was not written for fresh nonce {nonce}: {error.stderr}') from error
        root = validate_hierarchy(xml, nonce)
        record['windowId'] = root.get('window-id')
        record['pidAfter'] = pid()
        ensure_mock(timeout=remaining())
        if record['pidBefore'] != record['pidAfter']:
            raise ValueError('Mock process changed during self-target capture')
        record['status'] = 'COMPLETE_REAL_TREE_SAME_MOCK_PID'
        return xml
    except BaseException as error:
        failure = error
        record.update(status='FAIL', failure=repr(error))
        raise
    finally:
        try:
            adb('shell', 'run-as', TEST_PACKAGE, 'rm', '-f', relative, timeout=remaining())
            record['ownedFileRemoved'] = True
        except Exception as cleanup:
            record['cleanupFailure'] = repr(cleanup)
            if failure is None:
                failure = cleanup
                record['status'] = 'FAIL_OWNED_CLEANUP'
                raise
        finally:
            directory = os.environ.get('MOCK_HIERARCHY_EVIDENCE_DIR')
            if directory:
                try:
                    destination = Path(directory); destination.mkdir(parents=True, exist_ok=True)
                    if xml is not None: (destination / (nonce + '.xml')).write_text(xml, encoding='utf-8')
                    (destination / (nonce + '.json')).write_text(json.dumps(record, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
                except OSError as diagnostic:
                    if failure is None: raise
                    print(f'Hierarchy evidence write failed after original failure: {diagnostic}', file=sys.stderr)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('text')
    parser.add_argument('--contains', action='store_true')
    parser.add_argument('--node-class')
    parser.add_argument('--scroll', action='store_true')
    parser.add_argument('--diagnostics-dir')
    args = parser.parse_args()
    # Reordered activities retain their scroll position. Search upwards first,
    # then downwards, so header controls remain reachable after a tool visit.
    directions = (["up"] * 6 + ["down"] * 12) if args.scroll else []
    completed_scrolls = []
    for attempt in range(len(directions) + 1):
        foreground = ensure_mock()
        xml = read_hierarchy()
        try:
            point = find_target(xml, args.text, args.contains, args.node_class)
        except AmbiguousMockControl as error:
            if args.diagnostics_dir:
                try:
                    save_ambiguity_diagnostics(args.diagnostics_dir, xml, error, args.text,
                        args.contains, attempt, completed_scrolls, foreground, args.node_class)
                except OSError as write_error:
                    print(f'Mock selector diagnostic write failed: {write_error}', file=sys.stderr)
            raise
        if point is not None:
            ensure_mock()
            adb('shell', 'input', 'tap', *map(str, point))
            return
        if attempt < len(directions):
            size = re.findall(r'(\d+)x(\d+)', adb('shell', 'wm', 'size'))[-1]
            width, height = map(int, size)
            top, bottom = str(height // 4), str(height * 3 // 4)
            start, end = (top, bottom) if directions[attempt] == 'up' else (bottom, top)
            adb('shell', 'input', 'swipe', str(width // 2), start, str(width // 2), end, '400')
            completed_scrolls.append(directions[attempt])
    raise ValueError(f'Mock control not found: {args.text}')


if __name__ == '__main__':
    main()
