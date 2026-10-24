"""Pure verification of accepted protocol-2 origin facts; never creates acceptance."""

DEFAULT_ACTIVITY = 'ACTIVITY_STRICT_METRICS_DECOR'
DEFAULT_WINDOW = 'EXPLICIT_WINDOW_MEASURED_DECOR'
TRANSLATED_WINDOW = 'MEASURED_WINDOW_BUFFER_TRANSLATION'
SHORT_ACTIVITY = 'MEASURED_SHORT_ACTIVITY_WINDOW'


def validate(proof, attempt, expected_mode):
    assert proof['mappingMode'] == expected_mode
    source = attempt['windowSourceRectPx']
    screen = attempt['displayAppRectPx']
    s1 = attempt['surface-beforeSourceRectAfterSdk']
    s2 = attempt['surface-afterSourceRectAfterSdk']
    width, height = source[2] - source[0], source[3] - source[1]
    assert width > 0 and height > 0
    assert [screen[2]-screen[0], screen[3]-screen[1]] == [width, height]
    assert attempt['windowLayerMapping'] == {
        DEFAULT_ACTIVITY: 'SDK34_measured_zero_surfaceInsets_unscaled_Window_local',
        DEFAULT_WINDOW: 'SDK34_measured_zero_surfaceInsets_unscaled_Window_local',
        TRANSLATED_WINDOW: 'SDK34_measured_source_translation_symmetric_layer_extension',
        SHORT_ACTIVITY: 'measured_short_decor_within_host_metrics_and_display',
    }[expected_mode], attempt
    if expected_mode == TRANSLATED_WINDOW:
        dx, dy = s1[0]-source[0], s1[1]-source[1]
        assert dx >= 0 and dy >= 0
        assert s1 == s2 == [source[0]+dx, source[1]+dy, source[2]+dx, source[3]+dy]
        assert attempt['measuredSourceTranslationPx'] == [dx, dy]
        assert attempt['windowLayerRoiPx'] == s1
        assert attempt['measuredWindowLayerSizePx'] == attempt['layer-before']['sizePx'] == attempt['layer-after']['sizePx']
        assert attempt['surfaceInsetsMeasurement'].startswith('UNKNOWN_NOT_READ')
    else:
        assert s1 == source == s2
    for geometry in (attempt['geometryBefore'], attempt['geometryAfter']):
        assert geometry['hasWindowFocus'] and not geometry['rootLayoutRequested'] and not geometry['decorLayoutRequested']
        decor = geometry['windowDecorSizePx']
        dw, ds = geometry['decorLocationInWindowPx'], geometry['decorLocationOnScreenPx']
        assert dw == [0, 0]
        assert screen[0]-source[0] == ds[0] and screen[1]-source[1] == ds[1]
        assert 0 <= source[0] < source[2] <= decor[0] and 0 <= source[1] < source[3] <= decor[1]
        layer = attempt['layer-before']['sizePx']
        if expected_mode == TRANSLATED_WINDOW:
            dx, dy = attempt['measuredSourceTranslationPx']
            assert layer == attempt['layer-after']['sizePx'] == [decor[0]+2*dx, decor[1]+2*dy]
            assert 0 <= s1[0] < s1[2] <= layer[0] and 0 <= s1[1] < s1[3] <= layer[1]
            assert proof['captureWindowKind'] == 'EXPLICIT_WINDOW'
        else:
            assert layer == decor == attempt['layer-after']['sizePx']
        if expected_mode in (DEFAULT_WINDOW, TRANSLATED_WINDOW, SHORT_ACTIVITY):
            assert geometry['windowIdentity'] == proof['windowIdentity']
            assert geometry['decorIdentity'] == proof['decorIdentity']
            assert geometry['rootIdentity'] == proof['rootIdentity']
            assert geometry['rootViewIdentity'] == proof['decorIdentity']
            assert geometry['rootTokenIdentity'] == geometry['decorTokenIdentity'] == proof['windowTokenIdentity']
            assert geometry['displayId'] == proof['displayId'] == 0
            chain = geometry['targetAncestorChain']
            assert chain and chain[0]['identity'] == proof['rootIdentity'] and chain[-1]['identity'] == proof['decorIdentity']
            for node in chain:
                assert node['attached'] and node['hardwareAccelerated'] and node['laidOut'] and node['hasWindowFocus'] and not node['layoutRequested']
                assert node['tokenIdentity'] == proof['windowTokenIdentity'] and node['displayId'] == 0
                assert node['matrixIdentity'] and node['transform'] == [1, 1, 0, 0, 0, 0, 0, 0]
        if expected_mode in (DEFAULT_ACTIVITY, SHORT_ACTIVITY):
            assert proof['captureWindowKind'] == 'ACTIVITY'
            bounds = geometry['windowMetricsBoundsPx']
            decor_rect = [ds[0], ds[1], ds[0]+decor[0], ds[1]+decor[1]]
            if expected_mode == DEFAULT_ACTIVITY:
                assert bounds == decor_rect
            else:
                assert bounds[0] <= decor_rect[0] < decor_rect[2] <= bounds[2]
                assert bounds[1] <= decor_rect[1] < decor_rect[3] <= bounds[3]
                display_size = geometry['displaySizePx']
                assert 0 <= decor_rect[0] < decor_rect[2] <= display_size[0]
                assert 0 <= decor_rect[1] < decor_rect[3] <= display_size[1]
        else:
            assert geometry['metricsRole'] == 'diagnostic_host_container_not_assumed_dialog_bounds'
