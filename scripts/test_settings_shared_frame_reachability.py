"""Replay the real verifier with two targets sharing one immutable accepted frame."""
import ast
import copy
import json
from pathlib import Path
import tempfile
import unittest

SOURCE = Path(__file__).with_name('check_settings_visual_migration.sh')


class SharedFrameReachability(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.folder = Path(self.temp.name)
        source = SOURCE.read_text().split("<<'VERIFY_PY'\n", 1)[1].split('\nVERIFY_PY', 1)[0]
        tree = ast.parse(source)
        functions = ast.Module(body=[node for node in tree.body if isinstance(node, ast.FunctionDef)
                                    and node.name in ('require_reach', 'require_whole')], type_ignores=[])
        self.ids = ['product/settings/test/a', 'product/settings/test/b']
        self.namespace = {'json': json, 'core_app': set(self.ids), 'core_machine': set(), 'core_scale': set(),
                          'require_state': lambda path, kind: self.assertTrue((path / 'window-frame-sync.json').is_file())}
        exec(compile(functions, str(SOURCE), 'exec'), self.namespace)
        self.geometry = {'nodes': [self.target(index) for index in (1, 2)]}
        self.report = {'page': 'app', 'domain': 'main', 'completed': True, 'exactScrollRestored': True,
                       'beforeScroll': [], 'restoredScroll': [], 'coreTargets': self.ids,
                       'allActualBodyTargets': [{'id': key, 'viewIdentity': index}
                                                for index, key in enumerate(self.ids, 1)],
                       'targets': [{'id': key, 'viewIdentity': index,
                                    'acceptedActualTarget': copy.deepcopy(self.geometry['nodes'][index - 1]),
                                    'proof': 'target-0/window-frame-sync.json', 'window': 'target-0/window.png',
                                    'geometry': 'target-0/geometry.json'} for index, key in enumerate(self.ids, 1)],
                       'restoreProof': 'restored/window-frame-sync.json'}
        for name in ('target-0', 'restored'):
            directory = self.folder / name
            directory.mkdir()
            (directory / 'window-frame-sync.json').write_text('{}')

    @staticmethod
    def target(identity):
        return {'viewIdentity': identity, 'complete': True, 'textLineBoxes': [
            {'fullViewVisible': True, 'layoutPresent': True, 'lineCount': 1,
             'ellipsisCount': 0, 'layoutHeightPx': 20, 'contentHeightPx': 20,
             'contentWidthPx': 100, 'horizontalLayoutLines': [{'leftPx': 0, 'rightPx': 80}],
             'actualTypography': {'textSizePx': 16, 'weight': 400, 'typeface': 'fixture',
                                  'textScaleX': 1, 'textSkewX': 0, 'fakeBold': False,
                                  'letterSpacingEm': 0, 'baselineScreenPx': 15, 'colors': {},
                                  'actualLayout': {}, 'fontMetricsPx': [-15, -12, 3, 4, 0]}}]}

    def verify(self):
        (self.folder / 'reachability.json').write_text(json.dumps(self.report))
        (self.folder / 'target-0/geometry.json').write_text(json.dumps(self.geometry))
        self.namespace['require_reach'](self.folder, 'app', 'main')

    def test_every_target_can_reference_the_same_real_frame(self):
        self.verify()

    def test_missing_target_still_fails(self):
        self.report['targets'].pop()
        with self.assertRaises(AssertionError):
            self.verify()

    def test_wrong_frame_identity_still_fails(self):
        self.geometry['nodes'][1]['viewIdentity'] = 99
        with self.assertRaises(AssertionError):
            self.verify()

    def test_frame_clipping_still_fails_even_with_matching_report(self):
        self.geometry['nodes'][1]['complete'] = False
        self.report['targets'][1]['acceptedActualTarget']['complete'] = False
        with self.assertRaises(AssertionError):
            self.verify()

    def test_text_overflow_still_fails_even_with_matching_report(self):
        for target in (self.geometry['nodes'][1], self.report['targets'][1]['acceptedActualTarget']):
            target['textLineBoxes'][0]['contentHeightPx'] = 19
        with self.assertRaises(AssertionError):
            self.verify()


if __name__ == '__main__':
    unittest.main()
