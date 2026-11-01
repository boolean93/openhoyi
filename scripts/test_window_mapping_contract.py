"""Offline runner-contract tests using retained a47 raw facts; not Android acceptance."""
import copy
import glob
import hashlib
import json
import unittest
from pathlib import Path

from window_mapping_contract import validate, DEFAULT_ACTIVITY, TRANSLATED_WINDOW, SHORT_ACTIVITY


def raw_case(kind):
    paths = glob.glob(f'build/task-first-evidence/ci-a47c870-{kind}/raw/**/diagnostic-window-origin.json', recursive=True)
    assert len(paths) == 1, paths
    path = Path(paths[0]); raw = json.loads(path.read_text())
    assert raw['status'] == 'DIAGNOSTIC_NOT_ACCEPTANCE' and not raw['accepted']
    artifacts = {a['role']: a for a in raw['artifacts']}
    for artifact in artifacts.values():
        image = path.parent / artifact['file']
        assert hashlib.sha256(image.read_bytes()).hexdigest() == artifact['sha256']
    geometry = raw['geometry']; mode = TRANSLATED_WINDOW if kind == 'scale' else SHORT_ACTIVITY
    source = geometry['originalWindowSourceRectPx']
    screen = geometry['originalDisplayAppRectPx']
    decor = geometry['decorSizePx']
    nodes = []
    for identity in dict.fromkeys((geometry['rootIdentity'], geometry['decorIdentity'])):
        nodes.append(dict(identity=identity, attached=True, hardwareAccelerated=True, laidOut=True,
            hasWindowFocus=True, layoutRequested=False, tokenIdentity=geometry['rootTokenIdentity'],
            displayId=0, matrixIdentity=True, transform=[1, 1, 0, 0, 0, 0, 0, 0]))
    g = dict(windowIdentity=geometry['windowIdentity'], rootIdentity=geometry['rootIdentity'],
        decorIdentity=geometry['decorIdentity'], rootViewIdentity=geometry['decorIdentity'],
        rootTokenIdentity=geometry['rootTokenIdentity'], decorTokenIdentity=geometry['decorTokenIdentity'],
        displayId=geometry['displayId'], hasWindowFocus=True, rootLayoutRequested=False,
        decorLayoutRequested=False, windowDecorSizePx=decor,
        decorLocationInWindowPx=geometry['decorLocationInWindowPx'],
        decorLocationOnScreenPx=geometry['decorLocationOnScreenPx'],
        windowMetricsBoundsPx=geometry['hostCurrentWindowMetricsBoundsPx'],
        displaySizePx=artifacts['display']['sizePx'], targetAncestorChain=nodes,
        metricsRole='diagnostic_host_container_not_assumed_dialog_bounds')
    proof = dict(mappingMode=mode, captureWindowKind='EXPLICIT_WINDOW' if kind == 'scale' else 'ACTIVITY',
        windowIdentity=g['windowIdentity'], rootIdentity=g['rootIdentity'], decorIdentity=g['decorIdentity'],
        windowTokenIdentity=g['rootTokenIdentity'], displayId=0)
    attempt = dict(windowSourceRectPx=source, displayAppRectPx=screen,
        **{'surface-beforeSourceRectAfterSdk': raw['pixelCopySourceRectAfterSdkPx'],
           'surface-afterSourceRectAfterSdk': raw['pixelCopySourceRectAfterSdkPx'],
           'layer-before': {'sizePx': artifacts['layer']['sizePx']},
           'layer-after': {'sizePx': artifacts['layer']['sizePx']},
           'geometryBefore': copy.deepcopy(g), 'geometryAfter': copy.deepcopy(g)},
        windowLayerMapping=('SDK34_measured_source_translation_symmetric_layer_extension' if kind == 'scale'
            else 'measured_short_decor_within_host_metrics_and_display'))
    if kind == 'scale':
        attempt.update(measuredSourceTranslationPx=raw['pixelCopySourceOffsetPx'],
            windowLayerRoiPx=raw['pixelCopySourceRectAfterSdkPx'],
            measuredWindowLayerSizePx=artifacts['layer']['sizePx'],
            surfaceInsetsMeasurement='UNKNOWN_NOT_READ; actual source offset only')
    return proof, attempt, mode


class MappingContractTest(unittest.TestCase):
    def test_raw_scale_and_legacy_dimensions(self):
        for kind in ('scale', 'legacy'):
            proof, attempt, mode = raw_case(kind)
            validate(proof, attempt, mode)

    def reject(self, kind, mutation, mode=None):
        proof, attempt, actual_mode = raw_case(kind)
        mutation(proof, attempt)
        with self.assertRaises((AssertionError, KeyError)):
            validate(proof, attempt, mode or actual_mode)

    def test_non_translation(self):
        self.reject('scale', lambda p, a: a['surface-beforeSourceRectAfterSdk'].__setitem__(2, 1479))

    def test_negative_offset(self):
        self.reject('scale', lambda p, a: a.__setitem__('surface-beforeSourceRectAfterSdk', [-1, 72, 1407, 1120]))

    def test_s1_s2_offset_mismatch(self):
        self.reject('scale', lambda p, a: a.__setitem__('surface-afterSourceRectAfterSdk', [73, 72, 1481, 1120]))

    def test_w1_w2_size_mismatch(self):
        self.reject('scale', lambda p, a: a['layer-after'].__setitem__('sizePx', [1553, 1192]))

    def test_asymmetric_extension(self):
        self.reject('scale', lambda p, a: a['layer-before'].__setitem__('sizePx', [1553, 1192]))

    def test_roi_out_of_bounds(self):
        self.reject('scale', lambda p, a: a.__setitem__('windowLayerRoiPx', [72, 72, 1553, 1120]))

    def test_screen_window_origin_mismatch(self):
        self.reject('legacy', lambda p, a: a['geometryBefore'].__setitem__('decorLocationOnScreenPx', [0, 484]))

    def test_default_activity_never_falls_back_to_short(self):
        proof, attempt, _ = raw_case('legacy')
        proof['mappingMode'] = DEFAULT_ACTIVITY
        attempt['windowLayerMapping'] = 'SDK34_measured_zero_surfaceInsets_unscaled_Window_local'
        with self.assertRaises(AssertionError):
            validate(proof, attempt, DEFAULT_ACTIVITY)


if __name__ == '__main__':
    unittest.main()
