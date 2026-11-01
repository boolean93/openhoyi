"""Self-contained host contract tests; synthetic fixtures are not captured CI evidence."""
import copy
import json
from pathlib import Path
import tempfile
import unittest

from test_settings_language_host_contract import verifier_functions


ERROR_TAG = 'product/settings/app/language/error'


def node(identity, kind, tag=None, value='fixture'):
    text = {'text': value, 'semanticId': tag, 'fullViewVisible': True,
            'layoutPresent': True, 'lineCount': 1, 'ellipsisCount': 0,
            'layoutHeightPx': 20, 'contentHeightPx': 20, 'contentWidthPx': 100,
            'horizontalLayoutLines': [{'leftPx': 0, 'rightPx': 80}],
            'actualTypography': {'text': value, 'class': kind, 'tag': tag,
                'textSizePx': 16, 'weight': 400, 'typeface': 'synthetic',
                'textScaleX': 1, 'textSkewX': 0, 'fakeBold': False,
                'letterSpacingEm': 0, 'baselineScreenPx': 15, 'colors': {},
                'actualLayout': {}, 'fontMetricsPx': [-15, -12, 3, 4, 0]}}
    return {'viewIdentity': identity, 'semanticId': tag, 'complete': True,
            'rectPx': [0, 0, 100, 48], 'visibleRectPx': [0, 0, 100, 48],
            'textLineBoxes': [text]}


def fixture(locale='zh-CN', value='语言保存失败，请重试。'):
    button = node(1, 'android.widget.Button')
    control = dict(copy.deepcopy(button), roles=['negative'], isShown=True,
                   heightPx=48, isButton=True, isEditText=False)
    safe = {'dialogIdentity': 10, 'ownerActivityIdentity': 11,
            'windowIdentity': 12, 'decorIdentity': 13,
            'callbackIsDialog': True, 'dialogWindowIsWindow': True, 'ownerMatches': True,
            'decorAttached': True, 'decorLaidOut': True, 'decorHasWindowFocus': True,
            'uniqueFocusedWindow': True, 'safeIntersectsVisibleFrame': True,
            'decorLayoutRequested': False, 'density': 1,
            'safeRectScreenPx': [0, 0, 100, 100], 'visibleFrameScreenPx': [0, 0, 100, 100],
            'focusedControlIdentity': None, 'controls': [control],
            'buttonRoles': [{'role': 'positive', 'present': False, 'isShown': False, 'identity': None},
                            {'role': 'negative', 'present': True, 'isShown': True, 'identity': 1}]}
    target = {'imeVisible': False, 'decorScreenPx': [0, 0, 100, 100], 'locale': locale,
              'languageErrorExpectation': {'resourceName': 'language_save_failed',
                                           'resourceLocale': locale, 'text': value},
              'safeControls': safe, 'nodes': [button,
                node(2, 'android.widget.CheckedTextView'),
                node(3, 'com.android.internal.widget.DialogTitle'),
                node(4, 'android.widget.TextView', ERROR_TAG, value)]}
    proof = {'windowIdentity': 12, 'decorIdentity': 13, 'acceptedAttempt': 1,
             'attempts': [{'geometryBefore': {'target': target},
                           'geometryAfter': {'target': copy.deepcopy(target)}}]}
    return target, safe, proof


class LanguageErrorContract(unittest.TestCase):
    def setUp(self):
        self.functions = verifier_functions()
        self.target, self.safe, self.proof = fixture()

    def validate(self):
        self.functions['validate_language_dialog_target'](self.target, self.safe, self.proof)

    def test_precise_localized_resource_expectation(self):
        for locale, value in [('zh-CN', '语言保存失败，请重试。'),
                              ('en', 'Could not save language. Try again.')]:
            with self.subTest(locale=locale):
                self.target, self.safe, self.proof = fixture(locale, value)
                self.validate()

    def test_missing_duplicate_wrong_and_truncated_error_are_rejected(self):
        changes = {
            'missing': lambda t: t['nodes'].pop(),
            'duplicate': lambda t: t['nodes'].append(copy.deepcopy(t['nodes'][-1])),
            'wrong_tag': lambda t: t['nodes'][-1]['textLineBoxes'][0]['actualTypography'].update(tag='wrong'),
            'wrong_semantic_tag': lambda t: t['nodes'][-1]['textLineBoxes'][0].update(semanticId='wrong'),
            'wrong_class': lambda t: t['nodes'][-1]['textLineBoxes'][0]['actualTypography'].update(
                **{'class': 'android.widget.Button'}),
            'wrong_text': lambda t: t['nodes'][-1]['textLineBoxes'][0].update(text='wrong'),
            'wrong_typography_text': lambda t: t['nodes'][-1]['textLineBoxes'][0]['actualTypography'].update(text='wrong'),
            'missing_expectation': lambda t: t.pop('languageErrorExpectation'),
            'wrong_resource': lambda t: t['languageErrorExpectation'].update(resourceName='wrong'),
            'wrong_locale': lambda t: t['languageErrorExpectation'].update(resourceLocale='en'),
            'hidden': lambda t: t['nodes'][-1].update(complete=False),
            'partial_rect': lambda t: t['nodes'][-1].update(visibleRectPx=[0, 0, 99, 48]),
            'outside_safe': lambda t: t['nodes'][-1].update(rectPx=[0, 0, 101, 48], visibleRectPx=[0, 0, 101, 48]),
            'zero_area': lambda t: t['nodes'][-1].update(rectPx=[0, 0, 0, 48], visibleRectPx=[0, 0, 0, 48]),
            'duplicate_identity': lambda t: t['nodes'][0].update(viewIdentity=t['nodes'][-1]['viewIdentity']),
            'truncated': lambda t: t['nodes'][-1]['textLineBoxes'][0].update(ellipsisCount=1),
            'partial_text': lambda t: t['nodes'][-1]['textLineBoxes'][0].update(fullViewVisible=False),
            'vertical_overflow': lambda t: t['nodes'][-1]['textLineBoxes'][0].update(contentHeightPx=19),
            'horizontal_overflow': lambda t: t['nodes'][-1]['textLineBoxes'][0].update(contentWidthPx=79),
            'extra_text': lambda t: t['nodes'][-1]['textLineBoxes'].append(copy.deepcopy(t['nodes'][-1]['textLineBoxes'][0])),
            'unexpected_textview': lambda t: t['nodes'].append(node(5, 'android.widget.TextView', 'other')),
            'unexpected_class': lambda t: t['nodes'].append(node(5, 'android.widget.EditText', 'other')),
        }
        for name, change in changes.items():
            with self.subTest(mutation=name):
                self.target, self.safe, self.proof = fixture()
                change(self.target)
                with self.assertRaises((AssertionError, KeyError)):
                    self.validate()

    def test_resource_expectation_is_observed_from_real_owner_on_main_thread(self):
        source = (Path(__file__).resolve().parents[1] /
                  'mobile/src/androidTest/kotlin/io/openhoyi/mobile/SettingsVisualMigrationChecks.kt').read_text()
        self.assertIn('.put("text",owner.getString(R.string.language_save_failed))', source)
        self.assertIn('.put("resourceLocale",owner.resources.configuration.locales.toLanguageTags())', source)

    def test_accepted_before_after_and_saved_geometry_remain_bound(self):
        with tempfile.TemporaryDirectory() as directory:
            folder = Path(directory) / 'app/special-states/language-write-rejected'
            folder.mkdir(parents=True)
            (folder / 'window.png').write_bytes(b'synthetic placeholder; protocol separately verified')
            self.functions['require_protocol'] = lambda *args: self.proof
            for mutation in ('none', 'after_target', 'saved_target'):
                with self.subTest(mutation=mutation):
                    self.target, self.safe, self.proof = fixture()
                    saved = copy.deepcopy(self.target)
                    if mutation == 'after_target':
                        self.proof['attempts'][0]['geometryAfter']['target']['locale'] = 'en'
                    if mutation == 'saved_target':
                        saved['locale'] = 'en'
                    (folder / 'geometry.json').write_text(json.dumps(saved))
                    (folder / 'safe-controls.json').write_text(json.dumps(self.safe))
                    (folder / 'dialog-typography.json').write_text(json.dumps({
                        'source': 'accepted_explicit_dialog_window_target_geometry_not_owner_Activity',
                        'nodes': saved['nodes']}))
                    if mutation == 'none':
                        self.functions['require_state'](folder, 'EXPLICIT_WINDOW')
                    else:
                        with self.assertRaises(AssertionError):
                            self.functions['require_state'](folder, 'EXPLICIT_WINDOW')


if __name__ == '__main__':
    unittest.main()
