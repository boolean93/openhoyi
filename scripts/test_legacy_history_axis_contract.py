import copy
import json
import math
import struct
import unittest


def validate(report, initial, reached, restored, density):
    assert report['completed'] is True and density > 0
    assert report['initial'] == initial and report['reached'] == reached and report['restored'] == restored
    canvas = initial['canvasOperations']
    derived = [{'index':index,'role':operation['role'],'text':operation['text'],
                'glyphBoundsPx':operation['glyphBoundsPx']}
               for index,operation in enumerate(canvas)
               if operation.get('operation')=='text' and operation.get('role')=='time-tick']
    ticks = initial['timeTicks']
    assert derived and len(ticks)==len(derived)
    assert all(all(tick[key]==operation[key] for key in ('index','role','text','glyphBoundsPx'))
               for tick,operation in zip(ticks,derived))
    assert ticks and [tick['index'] for tick in ticks] == sorted(set(tick['index'] for tick in ticks))
    assert all(tick['role'] == 'time-tick' and tick['text'] for tick in ticks)
    rect = [math.floor(min(t['glyphBoundsPx'][0] for t in ticks)),
            math.floor(min(t['glyphBoundsPx'][1] for t in ticks)),
            math.ceil(max(t['glyphBoundsPx'][2] for t in ticks)),
            math.ceil(max(t['glyphBoundsPx'][3] for t in ticks))]
    assert report['requestRectChartPx'] == rect and rect[2] > rect[0] and rect[3] > rect[1]
    fixed = ('activityIdentity','windowIdentity','rootIdentity','decorIdentity','windowTokenIdentity',
             'chartIdentity','viewportIdentity','selectedId','listState','points','nativeInvariant','canvasOperations',
             'detailActionHeightPx','actualPlotHeightDp')
    for state in (initial,reached,restored):
        assert all(state[key] == initial[key] for key in fixed)
        assert state['actualPlotHeightDp'] >= 160 and state['visiblePlotHeightDp'] >= 160 and state['chartMappingVerified']
        assert state['detailActionHeightPx'] >= math.ceil(48*density)
        assert state['detailAction']['semanticId'] == 'legacy/history/selected/open' and state['detailAction']['complete']
        assert len(state['timeTicks']) == len(ticks)
        assert [(s['identity'],s['tag']) for s in state['scrolls']] == [(s['identity'],s['tag']) for s in initial['scrolls']]
        origin = state['chartOriginScreenPx']; clip = state['chartVisibleRectPx']
        assert len(origin) == 2 and len(clip) == 4 and clip[2] > clip[0] and clip[3] > clip[1]
        for before,after in zip(ticks,state['timeTicks']):
            assert all(before[key] == after[key] for key in ('index','role','text','glyphBoundsPx'))
            box = after['glyphBoundsPx'];screen = after['screenBoundsPx']
            assert len(box) == len(screen) == 4 and box[2] > box[0] and box[3] > box[1]
            float_box=[struct.unpack('>f',struct.pack('>f',value))[0] for value in box]
            assert screen == [origin[0]+float_box[0],origin[1]+float_box[1],
                              origin[0]+float_box[2],origin[1]+float_box[3]]
            assert after['wholeGlyph'] is True
            assert clip[0] <= screen[0] < screen[2] <= clip[2]
            assert clip[1] <= screen[1] < screen[3] <= clip[3]
        if state is initial:
            assert state['chartRectPx'] == clip
    assert restored['scrolls'] == initial['scrolls'] and restored['timeTicks'] == initial['timeTicks']


def validate_badge_typography(initial, reached, restored, scale, theme):
    expected_size = 31.5 if scale == 1.0 else 42.3
    expected_ink = {'light': '#FF795119', 'dark': '#FFEACB87'}[theme]
    fixed = ('objectIdentity','semanticId','text','textSourceClass','viewClass','density','fontScale',
             'textSizePx','paintTextSizePx','typefacePresent','typefaceIdentity','typefaceUnknownReason',
             'weight','isItalic','fakeBold','letterSpacingEm','textScaleX','paintTextSkewX',
             'includeFontPadding','lineHeightPx','lineSpacingExtraPx','lineSpacingMultiplier',
             'baselinePx','paddingPx','textColors','currentTextColorArgb','drawableState',
             'background','foreground','glyphFontIdentity')
    for state in (initial,reached,restored):
        typography=state['headingReadonlyTypography'];badge=state['headingReadonly']
        assert typography['source']=='same_original_readonly_TextView_public_getters'
        assert typography['objectIdentity']==state['headingReadonlyIdentity']
        assert typography['semanticId']==badge['semanticId']=='legacy/readonly'
        assert typography['text']==badge['textLineBoxes'][0]['text'] and typography['textSourceClass']
        assert typography['attached'] is True and typography['layoutRequested'] is False
        assert typography['glyphFontIdentity']=='UNKNOWN_PUBLIC_TEXTVIEW_API_DOES_NOT_EXPOSE_FALLBACK_GLYPH_FONT'
        assert typography['typefacePresent'] is True and isinstance(typography['typefaceIdentity'],int)
        assert typography['typefaceUnknownReason'] is None and typography['weight']==400 and typography['isItalic'] is False
        assert typography['fakeBold'] is False and typography['letterSpacingEm']==0
        for key in ('textSizePx','paintTextSizePx'):
            assert isinstance(typography[key],(int,float)) and math.isfinite(typography[key])
            assert struct.pack('>f',typography[key])==struct.pack('>f',expected_size)
        assert typography['fontScale']==scale and typography['density']>0
        for key in ('textScaleX','paintTextSkewX','lineHeightPx','lineSpacingExtraPx',
                    'lineSpacingMultiplier','baselinePx'):
            assert isinstance(typography[key],(int,float)) and math.isfinite(typography[key])
        assert typography['lineHeightPx']>0 and typography['textScaleX']>0 and typography['lineSpacingMultiplier']>0
        assert len(typography['paddingPx'])==4 and all(isinstance(x,int) and x>=0 for x in typography['paddingPx'])
        assert typography['currentTextColorArgb']==expected_ink
        assert typography['textColors']['resolvedTextColorArgb']==expected_ink
        assert typography['drawableState']['enabled'] is True
        assert isinstance(typography['background'],dict) and isinstance(typography['foreground'],dict)
        layout=typography['actualLayout'];line=badge['textLineBoxes'][0]
        assert layout['source']=='existing_TextView_layout_and_getLineBounds_APIs'
        assert layout['status']=='actual_layout_api' and layout['displayedLayoutConfirmed'] is True
        assert layout['viewAttachedToWindow'] is True and layout['viewLayoutRequested'] is False
        assert layout['lineCount']==line['lineCount']==len(layout['lines'])>0
        assert layout['layoutHeightPx']==line['layoutHeightPx']
        assert layout['viewSizePx']==[badge['rectPx'][2]-badge['rectPx'][0],badge['rectPx'][3]-badge['rectPx'][1]]
        assert layout['layoutTextLengthUtf16']==len(typography['text'].encode('utf-16-le'))//2
        assert sum(item['ellipsisCountUtf16'] for item in layout['lines'])==line['ellipsisCount']==0
        assert typography['actualLayout']['viewOriginOnScreenPx']==badge['rectPx'][:2]
        assert all(typography[key]==initial['headingReadonlyTypography'][key] for key in fixed)
    return [state['headingReadonlyTypography']['background'] for state in (initial,reached,restored)]


def sample():
    base = dict(activityIdentity=1, windowIdentity=2, rootIdentity=3, decorIdentity=4,
                windowTokenIdentity=5, chartIdentity=6, viewportIdentity=7,
                selectedId='m7-main-legacy-shot', listState={'first': 0, 'offset': 0, 'selected': 'm7-main-legacy-shot'},
                points=['p0'], nativeInvariant='models', detailActionHeightPx=108,
                canvasOperations=[{'operation':'text','role':'time-tick','text':'0s','glyphBoundsPx':[5,60,15,70]},
                                  {'operation':'text','role':'time-tick','text':'30s','glyphBoundsPx':[75,60,90,70]}],
                detailAction={'complete': True, 'semanticId': 'legacy/history/selected/open', 'textLineBoxes': []},
                actualPlotHeightDp=160, visiblePlotHeightDp=160, chartMappingVerified=True,
                chartRectPx=[0,0,100,100],
                chartOriginScreenPx=[0, 0], chartVisibleRectPx=[0, 0, 100, 100],
                scrolls=[{'identity': 8, 'tag': 'legacy/chart/viewport', 'x': 0, 'y': 0}],
                timeTicks=[{'index': 0, 'role': 'time-tick', 'text': '0s', 'glyphBoundsPx': [5, 60, 15, 70], 'screenBoundsPx': [5, 60, 15, 70], 'wholeGlyph': True},
                           {'index': 1, 'role': 'time-tick', 'text': '30s', 'glyphBoundsPx': [75, 60, 90, 70], 'screenBoundsPx': [75, 60, 90, 70], 'wholeGlyph': True}])
    after = copy.deepcopy(base)
    after['scrolls'][0]['y'] = 20
    after['chartOriginScreenPx'] = [0, -20]
    after['timeTicks'][0]['screenBoundsPx'] = [5, 40, 15, 50]
    after['timeTicks'][1]['screenBoundsPx'] = [75, 40, 90, 50]
    return {'completed': True, 'requestRectChartPx': [5, 60, 90, 70],
            'initial': base, 'reached': after, 'restored': copy.deepcopy(base)}, base, after, copy.deepcopy(base)


def serialized_float_sample():
    report,_,_,_=sample()
    bounds=[63,535.928,126,560.928]
    for state in (report['initial'],report['reached'],report['restored']):
        state['canvasOperations'][0]['glyphBoundsPx']=bounds[:]
        state['chartOriginScreenPx']=[1090,692 if state is not report['reached'] else 672]
        state['chartVisibleRectPx']=[1000,700,1400,1400]
        state['chartRectPx']=[1000,700,1400,1400]
        tick=state['timeTicks'][0]
        tick['glyphBoundsPx']=bounds[:]
        origin=state['chartOriginScreenPx']
        for current in state['timeTicks']:
            current['screenBoundsPx']=[origin[index%2]+struct.unpack('>f',struct.pack('>f',value))[0]
                                       for index,value in enumerate(current['glyphBoundsPx'])]
    report['requestRectChartPx']=[63,60,126,561]
    report=json.loads(json.dumps(report))
    return report,report['initial'],report['reached'],report['restored']


def badge_sample():
    layout={'source':'existing_TextView_layout_and_getLineBounds_APIs','status':'actual_layout_api',
            'displayedLayoutConfirmed':True,'viewAttachedToWindow':True,'viewLayoutRequested':False,
            'lineCount':1,'lines':[{'ellipsisCountUtf16':0}], 'layoutHeightPx':30,
            'viewSizePx':[100,40], 'viewOriginOnScreenPx':[10,20], 'layoutTextLengthUtf16':2}
    typography={'source':'same_original_readonly_TextView_public_getters','objectIdentity':7,
                'semanticId':'legacy/readonly','text':'只读','textSourceClass':'java.lang.String',
                'viewClass':'android.widget.TextView','attached':True,'layoutRequested':False,
                'density':2.25,'fontScale':1.0,'textSizePx':31.5,'paintTextSizePx':31.5,
                'typefacePresent':True,'typefaceIdentity':9,'typefaceUnknownReason':None,
                'weight':400,'isItalic':False,'fakeBold':False,'letterSpacingEm':0,
                'textScaleX':1,'paintTextSkewX':0,'includeFontPadding':True,'lineHeightPx':33,
                'lineSpacingExtraPx':0,'lineSpacingMultiplier':1,'baselinePx':29,'paddingPx':[1,2,1,2],
                'textColors':{'resolvedTextColorArgb':'#FF795119'},'currentTextColorArgb':'#FF795119',
                'drawableState':{'enabled':True},'background':{'status':'unknown','reason':'clone_unavailable'},
                'foreground':{'status':'none'},
                'glyphFontIdentity':'UNKNOWN_PUBLIC_TEXTVIEW_API_DOES_NOT_EXPOSE_FALLBACK_GLYPH_FONT',
                'actualLayout':layout}
    state={'headingReadonlyIdentity':7,'headingReadonly':{'semanticId':'legacy/readonly',
           'rectPx':[10,20,110,60],'textLineBoxes':[{'text':'只读','lineCount':1,'layoutHeightPx':30,'ellipsisCount':0}]},
           'headingReadonlyTypography':typography}
    return [copy.deepcopy(state) for _ in range(3)]


class AxisContractTest(unittest.TestCase):
    def test_valid(self):
        validate(*sample(), 2.0)

    def test_serialized_float_source(self):
        validate(*serialized_float_sample(),2.0)

    def test_serialized_float_screen_nextafter_still_rejected(self):
        report,initial,reached,restored=serialized_float_sample()
        reached['timeTicks'][0]['screenBoundsPx'][1]=math.nextafter(
            reached['timeTicks'][0]['screenBoundsPx'][1],math.inf)
        with self.assertRaises(AssertionError):
            validate(report,initial,reached,restored,2.0)

    def test_mutations(self):
        for name, change in [
            ('missing-tick', lambda a: a['reached']['timeTicks'].clear()),
            ('all-states-drop-one', lambda a: [state['timeTicks'].pop() for state in
                (a['initial'],a['reached'],a['restored'])]),
            ('all-states-wrong-index', lambda a: [state['timeTicks'][0].update(index=4) for state in
                (a['initial'],a['reached'],a['restored'])]),
            ('wrong-index', lambda a: a['reached']['timeTicks'][0].update(index=5)),
            ('outside-clip', lambda a: a['reached']['timeTicks'][0].update(screenBoundsPx=[5, 40, 15, 101])),
            ('initial-clipped-reached-whole', lambda a: [state.update(chartVisibleRectPx=[0,0,100,65],visiblePlotHeightDp=120)
                for state in (a['initial'],a['restored'])]),
            ('wrong-scroll', lambda a: a['restored']['scrolls'][0].update(y=1)),
            ('wrong-selection', lambda a: a['reached'].update(selectedId='other')),
            ('new-window', lambda a: a['reached'].update(windowIdentity=9)),
            ('plot-96', lambda a: a['reached'].update(actualPlotHeightDp=96)),
            ('incomplete', lambda a: a.update(completed=False))]:
            report, initial, reached, restored = sample()
            change(report)
            with self.subTest(name=name), self.assertRaises(AssertionError):
                validate(report, report['initial'], report['reached'], report['restored'], 2.0)

    def test_badge_same_frame_metadata(self):
        validate_badge_typography(*badge_sample(),1.0,'light')

    def test_badge_mutations_fail(self):
        for name,change in [
            ('wrong-owner',lambda states:states[1]['headingReadonlyTypography'].update(objectIdentity=8)),
            ('wrong-text',lambda states:states[1]['headingReadonlyTypography'].update(text='其他')),
            ('missing-layout',lambda states:states[1]['headingReadonlyTypography'].pop('actualLayout')),
            ('wrong-size',lambda states:states[1]['headingReadonlyTypography'].update(textSizePx=30)),
            ('wrong-weight',lambda states:states[1]['headingReadonlyTypography'].update(weight=500)),
            ('wrong-spacing',lambda states:states[1]['headingReadonlyTypography'].update(letterSpacingEm=.1)),
            ('nan',lambda states:states[1]['headingReadonlyTypography'].update(lineHeightPx=float('nan'))),
            ('resource-as-actual',lambda states:states[1]['headingReadonlyTypography'].update(currentTextColorArgb='#FFFF0000')),
            ('wrong-theme',lambda states:states[1]['headingReadonlyTypography']['textColors'].update(resolvedTextColorArgb='#FFEACB87')),
            ('fake-glyph-family',lambda states:states[1]['headingReadonlyTypography'].update(glyphFontIdentity='Noto'))]:
            states=badge_sample();change(states)
            with self.subTest(name=name),self.assertRaises((AssertionError,KeyError)):
                validate_badge_typography(*states,1.0,'light')


if __name__ == '__main__':
    unittest.main()
