"""Exercise the actual host measured-dialog protocol with explicit synthetic inputs."""
import copy
import hashlib
import json
from pathlib import Path
import tempfile
import unittest

from test_settings_language_host_contract import verifier_functions


ALLOWED = [('app', 'language-write-rejected'),
           ('machine', 'legal-input-mock-preview'),
           ('machine', 'schedule-draft-native-ime'),
           ('machine', 'schedule-cancel-no-write')]
FILES = {'layer-before': 'window-layer-before.png', 'display': 'window.png',
         'display-after': 'window-display-after.png', 'layer-after': 'window-layer-after.png'}


def fixture(width=1260, height=430, shift=(72, 72)):
    dx, dy = shift
    ancestor = {'identity': 2, 'attached': True, 'hardwareAccelerated': True,
                'laidOut': True, 'hasWindowFocus': True, 'layoutRequested': False,
                'tokenIdentity': 3, 'displayId': 0, 'matrixIdentity': True,
                'transform': [1, 1, 0, 0, 0, 0, 0, 0]}
    geometry = {'hasWindowFocus': True, 'rootLayoutRequested': False, 'decorLayoutRequested': False,
                'windowDecorSizePx': [width, height], 'decorLocationOnScreenPx': [650, 500],
                'decorLocationInWindowPx': [0, 0], 'mappingMode': 'MEASURED_WINDOW_BUFFER_TRANSLATION',
                'windowIdentity': 1, 'decorIdentity': 2, 'rootIdentity': 2,
                'rootViewIdentity': 2, 'rootTokenIdentity': 3, 'decorTokenIdentity': 3,
                'displayId': 0, 'metricsRole': 'diagnostic_host_container_not_assumed_dialog_bounds',
                'targetAncestorChain': [ancestor], 'actualWindowInsets': {'imeVisible': False},
                'target': {'textSnapshotContract': 'SETTINGS_TEXT_AT_MAIN_THREAD_OBSERVATION_V1',
                           'nodes': [{'textLineBoxes': [{'text': 'synthetic',
                                                        'actualTypography': {'text': 'synthetic'}}]}]}}
    attempt = {'accepted': True, 'withinMatchingBudget': True, 'geometryUnchanged': True,
               'windowReadyAtBothEnds': True, 'windowDomainColorSpacesEqual': True,
               'displayDomainColorSpacesEqual': True, 'windowLayerMatch': True, 'displayStable': True,
               'budget': [{'stage': 'comparison_complete', 'elapsedMs': 120, 'remainingMs': 4880}],
               'windowSourceRectPx': [0, 0, width, height],
               'displayAppRectPx': [650, 500, 650+width, 500+height],
               'measuredSourceTranslationPx': [dx, dy],
               'surface-beforeSourceRectAfterSdk': [dx, dy, width+dx, height+dy],
               'surface-afterSourceRectAfterSdk': [dx, dy, width+dx, height+dy],
               'windowLayerRoiPx': [dx, dy, width+dx, height+dy],
               'measuredWindowLayerSizePx': [width+2*dx, height+2*dy],
               'geometryBefore': geometry, 'geometryAfter': copy.deepcopy(geometry)}
    for key in ('S1_W1', 'S1_W2', 'S1_S2', 'D1_D2_app'):
        attempt[key] = {'count': 0}
    for role in FILES:
        attempt[role] = {'sizePx': ([width+2*dx, height+2*dy] if role.startswith('layer-')
                                    else [2560, 1600])}
    proof = {'protocolVersion': 2, 'accepted': True, 'timeoutMs': 5000, 'acceptedAttempt': 1,
             'captureWindowKind': 'EXPLICIT_WINDOW', 'mappingMode': 'MEASURED_WINDOW_BUFFER_TRANSLATION',
             'pixelComparison': 'each_equivalent_domain_all_app_pixels_no_tolerance_no_mask',
             'crossDomainComparison': 'DIAGNOSTIC_ONLY_PIPELINES_NOT_EQUIVALENT',
             'displayId': 0, 'windowIdentity': 1, 'decorIdentity': 2, 'rootIdentity': 2,
             'windowTokenIdentity': 3, 'attempts': [attempt], 'captureArtifacts': []}
    return proof


class MeasuredDialogContract(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.functions = verifier_functions()

    def save(self, proof, page='machine', state='legal-input-mock-preview', parent='special-states'):
        folder = self.root / page / parent / state
        folder.mkdir(parents=True, exist_ok=True)
        if not proof['captureArtifacts']:
            for role, filename in FILES.items():
                content = ('synthetic raw bitmap role ' + role).encode()
                (folder / filename).write_bytes(content)
                proof['captureArtifacts'].append({'role': role, 'file': filename, 'attempt': 1,
                    'fileSha256': hashlib.sha256(content).hexdigest(),
                    'bitmap': copy.deepcopy(proof['attempts'][0][role])})
        path = folder / 'window-frame-sync.json'
        path.write_text(json.dumps(proof))
        return path

    def verify(self, proof, **path):
        return self.functions['require_protocol'](self.save(proof, **path), 'EXPLICIT_WINDOW')

    def test_measured_translation_accepted_only_for_frozen_explicit_dialog_paths(self):
        for page, state in ALLOWED:
            for height in (430, 459):
                with self.subTest(page=page, state=state, height=height):
                    self.verify(fixture(height=height), page=page, state=state)

    def test_zero_translation_is_still_measured_without_assuming_insets(self):
        self.verify(fixture(shift=(0, 0)))

    def test_rejects_wrong_path_owner_kind_or_mapping_mode(self):
        for page, state, parent in [('app', 'legal-input-mock-preview', 'special-states'),
                                    ('machine', 'language-write-rejected', 'special-states'),
                                    ('machine', 'theme-write-rejected', 'special-states'),
                                    ('machine', 'legal-input-mock-preview', 'other'),
                                    ('other', 'legal-input-mock-preview', 'special-states')]:
            with self.subTest(page=page, state=state, parent=parent):
                with self.assertRaises(AssertionError):
                    self.verify(fixture(), page=page, state=state, parent=parent)
        for key, value in [('captureWindowKind', 'ACTIVITY'),
                           ('mappingMode', 'EXPLICIT_WINDOW_MEASURED_DECOR')]:
            proof = fixture();proof[key] = value
            with self.subTest(field=key), self.assertRaises(AssertionError):
                self.verify(proof)

    def test_coordinate_identity_budget_and_pixel_mutations_are_rejected(self):
        changes = {
            'negative_translation': lambda p, a: a.update(measuredSourceTranslationPx=[-1, 72]),
            's1_changed_size': lambda p, a: a['surface-beforeSourceRectAfterSdk'].__setitem__(2, 1333),
            's2_different_translation': lambda p, a: a['surface-afterSourceRectAfterSdk'].__setitem__(0, 73),
            'wrong_roi': lambda p, a: a['windowLayerRoiPx'].__setitem__(0, 71),
            'asymmetric_layer': lambda p, a: a['layer-after']['sizePx'].__setitem__(0, 1405),
            'false_measured_layer': lambda p, a: a['measuredWindowLayerSizePx'].__setitem__(1, 575),
            'partial_source': lambda p, a: a['windowSourceRectPx'].__setitem__(0, 1),
            'shift_added_to_screen': lambda p, a: a.update(displayAppRectPx=[722, 572, 1982, 1002]),
            'changed_geometry': lambda p, a: a['geometryAfter']['actualWindowInsets'].update(imeVisible=True),
            'changed_target': lambda p, a: a['geometryAfter']['target']['nodes'][0]['textLineBoxes'][0].update(text='changed'),
            'changed_window': lambda p, a: a['geometryAfter'].update(windowIdentity=999),
            'changed_token': lambda p, a: a['geometryAfter'].update(rootTokenIdentity=999),
            'changed_ancestor': lambda p, a: a['geometryAfter']['targetAncestorChain'][0].update(identity=999),
            'wrong_owner_both': lambda p, a: [a[key].update(windowIdentity=999) for key in ('geometryBefore', 'geometryAfter')],
            'wrong_token_both': lambda p, a: [a[key].update(rootTokenIdentity=999) for key in ('geometryBefore', 'geometryAfter')],
            'wrong_transform_both': lambda p, a: [a[key]['targetAncestorChain'][0].update(transform=[1, 1, 0, 0, 0, 1, 0, 0]) for key in ('geometryBefore', 'geometryAfter')],
            'lost_focus_both': lambda p, a: [a[key].update(hasWindowFocus=False) for key in ('geometryBefore', 'geometryAfter')],
            'layout_pending_both': lambda p, a: [a[key].update(rootLayoutRequested=True) for key in ('geometryBefore', 'geometryAfter')],
            'timeout_increased': lambda p, a: p.update(timeoutMs=5001),
            'comparison_late': lambda p, a: a['budget'][0].update(elapsedMs=5001),
            'budget_exhausted': lambda p, a: a['budget'][0].update(remainingMs=-1),
            'window_color_mismatch': lambda p, a: a.update(windowDomainColorSpacesEqual=False),
            'display_color_mismatch': lambda p, a: a.update(displayDomainColorSpacesEqual=False),
        }
        for key in ('S1_W1', 'S1_W2', 'S1_S2', 'D1_D2_app'):
            changes['pixel_difference_' + key] = lambda p, a, key=key: a[key].update(count=1)
        for name, change in changes.items():
            proof = fixture();change(proof, proof['attempts'][0])
            with self.subTest(mutation=name), self.assertRaises(AssertionError):
                self.verify(proof)

    def test_four_original_roles_attempt_filenames_and_hashes_remain_required(self):
        changes = {
            'missing': lambda p: p['captureArtifacts'].pop(),
            'duplicate': lambda p: p['captureArtifacts'][0].update(role='display'),
            'wrong_attempt': lambda p: p['captureArtifacts'][0].update(attempt=2),
            'wrong_filename': lambda p: p['captureArtifacts'][0].update(file='other.png'),
            'wrong_sha': lambda p: p['captureArtifacts'][0].update(fileSha256='0'*64),
            'wrong_bitmap': lambda p: p['captureArtifacts'][0]['bitmap'].update(sizePx=[1, 1]),
        }
        for name, change in changes.items():
            proof = fixture();path = self.save(proof);change(proof)
            path.write_text(json.dumps(proof))
            with self.subTest(mutation=name), self.assertRaises(AssertionError):
                self.functions['require_protocol'](path, 'EXPLICIT_WINDOW')


if __name__ == '__main__':
    unittest.main()
