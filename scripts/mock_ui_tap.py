#!/usr/bin/env python3
"""Tap an unambiguous visible Mock control; never interact with Alpha or devices."""
import argparse
import re
import subprocess
import xml.etree.ElementTree as ET

PACKAGE = 'io.openhoyi.mobile.mock'


def find_target(xml, text, contains=False):
    root = ET.fromstring(xml)
    parents = {child: parent for parent in root.iter() for child in parent}
    targets = set()
    for node in root.iter('node'):
        labels = (node.get('text', ''), node.get('content-desc', ''))
        if not any((text in value if contains else text == value) for value in labels):
            continue
        candidate = node
        child = node
        while candidate is not None:
            if candidate.get('class') == 'android.widget.ListView' and candidate.get('enabled') == 'true':
                candidate = child
                bounds = re.fullmatch(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]', candidate.get('bounds', ''))
                if bounds:
                    x1, y1, x2, y2 = map(int, bounds.groups())
                    if x2 > x1 and y2 > y1:
                        targets.add(((x1 + x2) // 2, (y1 + y2) // 2))
                break
            if candidate.get('clickable') == 'true' and candidate.get('enabled') == 'true':
                bounds = re.fullmatch(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]', candidate.get('bounds', ''))
                if bounds:
                    x1, y1, x2, y2 = map(int, bounds.groups())
                    if x2 > x1 and y2 > y1:
                        targets.add(((x1 + x2) // 2, (y1 + y2) // 2))
                break
            child, candidate = candidate, parents.get(candidate)
    if len(targets) > 1:
        raise ValueError(f'ambiguous Mock control: {text}')
    return next(iter(targets), None)


def adb(*args):
    return subprocess.run(['adb', *args], check=True, capture_output=True, text=True, timeout=30).stdout


def ensure_mock():
    state = adb('shell', 'dumpsys', 'activity', 'activities')
    if not re.search(r'topResumedActivity=.*\b' + re.escape(PACKAGE) + r'/', state):
        raise ValueError('foreground activity is not isolated Mock')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('text')
    parser.add_argument('--contains', action='store_true')
    parser.add_argument('--scroll', action='store_true')
    args = parser.parse_args()
    # Reordered activities retain their scroll position. Search upwards first,
    # then downwards, so header controls remain reachable after a tool visit.
    directions = (["up"] * 6 + ["down"] * 12) if args.scroll else []
    for attempt in range(len(directions) + 1):
        ensure_mock()
        adb('shell', 'uiautomator', 'dump', '/data/local/tmp/openhoyi-mock-ui.xml')
        xml = adb('shell', 'cat', '/data/local/tmp/openhoyi-mock-ui.xml')
        point = find_target(xml, args.text, args.contains)
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
    raise ValueError(f'Mock control not found: {args.text}')


if __name__ == '__main__':
    main()
