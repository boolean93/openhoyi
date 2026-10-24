"""Offline contract for one-geometry TextView identity reuse; no build artifact dependency."""
import argparse
import copy
import hashlib
import json
from pathlib import Path
import unittest
import zipfile


SOURCE = Path(__file__).resolve().parents[1] / 'mobile/src/androidTest/kotlin/io/openhoyi/mobile/SettingsVisualMigrationChecks.kt'


class Text:
    def __init__(self, tag, value, color='#ff123456'):
        self.tag, self.value, self.color = tag, value, color
        self.reads = 0

    def read(self):
        self.reads += 1
        return {'actualTypography': {'text': self.value, 'colors': {'ink': self.color}},
                'semanticId': self.tag, 'text': self.value, 'fullViewVisible': True,
                'visibility': {'complete': True}, 'layoutPresent': bool(self.value),
                'lineCount': self.value.count('\n') + 1 if self.value else 0,
                'ellipsisCount': 0 if self.value else None,
                'layoutHeightPx': 48 if self.value else None,
                'contentHeightPx': 52, 'contentWidthPx': 180,
                'horizontalLayoutLines': [{'line': 0, 'leftPx': 0, 'rightPx': 90}] if self.value else None}


def observe(ancestors, reuse):
    cache = {} if reuse else None  # Text instances use reference identity, never tag/value.
    result = []
    for tag, descendants in ancestors:
        lines = []
        for text in descendants:
            if cache is None:
                lines.append(text.read())
            else:
                if text not in cache:
                    cache[text] = text.read()
                lines.append(cache[text])
        result.append({'semanticId': tag, 'textLineBoxes': lines, 'roles': [tag]})
    return result


def payload(value):
    return json.dumps(value, ensure_ascii=False, separators=(',', ':'), indent=2).encode()


def replay(path):
    with zipfile.ZipFile(path) as archive:
        names = [name for name in archive.namelist()
                 if name.endswith('/tablet-user-landscape-light-font-1.0/machine/window-frame-sync.json')
                 and not name.startswith('raw-device/')]
        assert len(names) == 1
        target = json.loads(archive.read(names[0]))['attempts'][0]['geometryBefore']['target']
    original = payload(target)
    candidate = copy.deepcopy(target)
    cache = {}
    for node in candidate['nodes']:
        for position, line in enumerate(node['textLineBoxes']):
            # Only exact duplicate serialized values are reused in this host replay.
            # This is not an identity proof; Android synthetic check covers real View keys.
            key = payload(line)
            node['textLineBoxes'][position] = cache.setdefault(key, line)
    expanded = payload(candidate)
    count = sum(len(node['textLineBoxes']) for node in target['nodes'])
    assert len(target['nodes']) == 117 and count == 501 and expanded == original
    return {'nodes': 117, 'textLineBoxes': count, 'originalBytes': len(original),
            'reexpandedBytes': len(expanded), 'originalSha256': hashlib.sha256(original).hexdigest(),
            'reexpandedSha256': hashlib.sha256(expanded).hexdigest(), 'serialization': 'Python ordered JSON replay, not Android JSONObject bytes'}


class ReuseContract(unittest.TestCase):
    def test_source_has_strict_one_call_scope(self):
        source = SOURCE.read_text()
        for token in ('IdentityHashMap<TextView,JSONObject>()', 'private fun textLineBox(text:TextView)',
                      'main {syntheticObservationEquivalence()}'):
            self.assertTrue(token in source, f'missing source contract: {token}')

    def test_identity_and_new_observation(self):
        first = Text('same', '第一行\n第二行')
        same_tag = Text('same', '第一行\n第二行', '#ff654321')
        tree = [('ancestor', [first, same_tag]), ('nested', [first]), ('leaf', [same_tag, first])]
        old = observe(tree, False)
        first.reads = same_tag.reads = 0
        new = observe(tree, True)
        self.assertEqual(payload(old), payload(new))
        self.assertEqual((first.reads, same_tag.reads), (1, 1))
        self.assertEqual(sum(len(n['textLineBoxes']) for n in new), 5)
        self.assertIs(new[0]['textLineBoxes'][0], new[1]['textLineBoxes'][0])
        self.assertIsNot(new[0]['textLineBoxes'][0], new[0]['textLineBoxes'][1])
        first.value = '变更'; first.color = '#ffabcdef'
        again = observe(tree, True)
        self.assertEqual(first.reads, 2)
        self.assertNotEqual(payload(new), payload(again))
        self.assertEqual(again[0]['textLineBoxes'][0]['text'], '变更')

    def test_mutations_rejected(self):
        first = Text('same', '甲')
        second = Text('same', '甲')
        tree = [('parent', [first, second]), ('child', [first])]
        expected = payload(observe(tree, False))
        valid = observe(tree, True)
        for mutate in (
            lambda value: value[0]['textLineBoxes'][0].pop('ellipsisCount'),
            lambda value: value[0]['textLineBoxes'][0].update(ellipsisCount=None),
            lambda value: value[0]['textLineBoxes'].pop(),
            lambda value: value[0].update(roles=['wrong']),
            lambda value: value[0]['textLineBoxes'][0].update(text='changed'),
        ):
            altered = copy.deepcopy(valid)
            mutate(altered)
            self.assertNotEqual(payload(altered), expected)
        reordered = copy.deepcopy(valid)
        line = reordered[0]['textLineBoxes'][0]
        reordered[0]['textLineBoxes'][0] = dict(reversed(list(line.items())))
        self.assertNotEqual(payload(reordered), expected)
        empty = observe([('empty-parent', [Text('empty', '')])], True)
        missing_null = copy.deepcopy(empty)
        missing_null[0]['textLineBoxes'][0].pop('ellipsisCount')
        self.assertNotEqual(payload(missing_null), payload(empty))


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--replay-zip', type=Path)
    args, unittest_args = parser.parse_known_args()
    if args.replay_zip:
        print(json.dumps(replay(args.replay_zip), ensure_ascii=False, indent=2))
    else:
        unittest.main(argv=[__file__, *unittest_args])
