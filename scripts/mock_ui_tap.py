#!/usr/bin/env python3
"""Tap an unambiguous visible Mock control; never interact with Alpha or devices."""
import argparse
import hashlib
import json
import re
import subprocess
import sys
import tempfile
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


def find_target(xml, text, contains=False):
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


def save_ambiguity_diagnostics(directory, xml, error, selector, contains, attempt, completed_scrolls, foreground):
    parent = Path(directory)
    parent.mkdir(parents=True, exist_ok=True)
    folder = Path(tempfile.mkdtemp(prefix='ambiguous-', dir=parent))
    source = xml.encode('utf-8')
    (folder / 'hierarchy.xml').write_bytes(source)
    record = {'selector': selector, 'contains': contains, 'attempt': attempt,
              'completedScrollDirections': completed_scrolls,
              'foregroundCheckBeforeHierarchy': foreground,
              'hierarchySha256': hashlib.sha256(source).hexdigest(),
              'candidates': error.candidates, 'rawCenters': error.raw_centers,
              'centers': error.centers, 'outcome': 'NO_TAP_AFTER_AMBIGUITY'}
    (folder / 'candidates.json').write_text(json.dumps(record, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    return folder


def adb(*args, include_stderr=False):
    result = subprocess.run(['adb', *args], check=True, capture_output=True, text=True, timeout=30)
    return result.stdout + (result.stderr if include_stderr else '')


def ensure_mock():
    state = adb('shell', 'dumpsys', 'activity', 'activities')
    if not re.search(r'topResumedActivity=.*\b' + re.escape(PACKAGE) + r'/', state):
        raise ValueError('foreground activity is not isolated Mock')
    return state


def read_hierarchy():
    # Never reuse an old dump if the platform reports success without writing one.
    adb('shell', 'rm', '-f', '/data/local/tmp/openhoyi-mock-ui.xml')
    result = adb('shell', 'uiautomator', 'dump', '/data/local/tmp/openhoyi-mock-ui.xml', include_stderr=True)
    try:
        return adb('shell', 'cat', '/data/local/tmp/openhoyi-mock-ui.xml')
    except subprocess.CalledProcessError as error:
        raise ValueError(f'Mock hierarchy was not written; dump output: {result}; read error: {error.stderr}') from error


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('text')
    parser.add_argument('--contains', action='store_true')
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
            point = find_target(xml, args.text, args.contains)
        except AmbiguousMockControl as error:
            if args.diagnostics_dir:
                try:
                    save_ambiguity_diagnostics(args.diagnostics_dir, xml, error, args.text,
                        args.contains, attempt, completed_scrolls, foreground)
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
