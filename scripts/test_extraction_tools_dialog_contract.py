"""Exercise real tool verifier: shared native dialog proofs retain all old safety gates."""
import ast
import copy
import json
from pathlib import Path
import tempfile
import unittest

SOURCE = Path(__file__).with_name('check_extraction_history_visual_migration.sh')


class ActualToolsDialogContract(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.folder = Path(self.temp.name)
        source = SOURCE.read_text().split("<<'VERIFY_PY'\n", 1)[1].split('\nVERIFY_PY', 1)[0]
        functions = [n for n in ast.parse(source).body if isinstance(n, ast.FunctionDef)
                     and n.name in ('require_proof', 'require_text_layout', 'require_tools_report', 'require_dialog_state', 'require_overview_proof')]
        self.ns = {'json': json, 'Path': Path}
        exec(compile(ast.Module(body=functions, type_ignores=[]), str(SOURCE), 'exec'), self.ns)
        keys = ['choose-curve', 'connect-devices', 'choose-beans', 'reference-choice', 'overview', 'prepare', 'cancel-prepare']
        self.expected = ['reference-choice', 'overview']
        source_tools = [{'semanticKey': key, 'identity': index + 1, 'semanticVisible': key in self.expected,
                         'enabled': key not in ('prepare', 'cancel-prepare'), 'text': key}
                        for index, key in enumerate(keys)]
        fixed = self.layout('product/extraction/stop')
        fixed.update(enabled=True, startVisibility=8, stopVisibility=0, startEnabled=False, stopEnabled=True)
        state = dict(sourceTools=source_tools, fixedAction=fixed, shotState='RUNNING', traceIdentity='trace',
                     chartTraceIdentity='trace', clock=self.layout('clock'), clockLocation='header_outside_support_scroll',
                     headerIdentity=99, headerScrollPx=[0, 0], plotRectPx=[0, 0, 100, 100])
        menu = [dict(semanticKey=x['semanticKey'], sourceIdentity=x['identity'], menuIdentity=x['identity'] + 10,
                     shown=x['semanticVisible'], enabled=x['enabled'], text=x['text']) for x in source_tools]
        self.state = state
        self.baseline = {'densityDpi': 360, 'nodes': [fixed]}
        self.dialog = {'ownerState': state, 'nodes': [self.layout('product/extraction/tool/' + key) for key in self.expected]}
        self.overview_state = dict(text='device ready', chartExplanation='device ready', lineCount=1, viewport=[0, 0, 100, 100], scrollY=0,
                                   rows=[dict(line=0, complete=True, rectPx=[0, 0, 80, 20],
                                              start=0, end=12, ellipsisCount=0)])
        self.overview_geometry = {'ownerState': state, 'nodes': [], 'overview': self.overview_state}
        self.report = dict(schema='ACTUAL_CHART_TOOLS_DIALOG_V1', completed=True, case='RUNNING',
                           noMachineToolClicks=True, sourceToolCount=7, targetKeys=self.expected,
                           initialState=state, restoredState=state, exactScrollRestored=True,
                           initialScrollPx=[0, 0], restoredScrollPx=[0, 0], menuStates=menu,
                           reached=[dict(semanticKey=key, identity=next(x['menuIdentity'] for x in menu if x['semanticKey'] == key),
                                         text=key, enabled=True, widthPx=108, heightPx=108, density=2.25,
                                         layout=self.dialog['nodes'][index], proof='states/extraction-tools/window-frame-sync.json')
                                    for index, key in enumerate(self.expected)],
                           sourceSummaries=[dict(semanticKey='device-summary', sourceIdentity=50, text='device ready')],
                           overview=dict(text='device ready', chartExplanation='device ready', lineCount=1, coveredLines=[0], scrollRestored=True,
                                         sourceSummariesPresent=True, pages=[dict(self.overview_state,
                                             proof='states/extraction-overview/window-frame-sync.json')]),
                           restoredProof='states/extraction-tools-restored/window-frame-sync.json')

    @staticmethod
    def layout(tag):
        return dict(semanticId=tag, rectPx=[0, 100, 100, 200], complete=True, textLineBoxes=[
            dict(fullViewVisible=True, layoutPresent=True, lineCount=1, ellipsisCount=0, layoutHeightPx=20,
                 contentHeightPx=20, contentWidthPx=100, horizontalLayoutLines=[dict(leftPx=0, rightPx=80)])])

    def proof(self, geometry, kind):
        attempt = dict(accepted=True, withinMatchingBudget=True, geometryUnchanged=True, windowReadyAtBothEnds=True,
                       windowDomainColorSpacesEqual=True, displayDomainColorSpacesEqual=True,
                       budget=[dict(stage='comparison_complete', elapsedMs=100, remainingMs=4900)],
                       geometryBefore={'target': geometry}, geometryAfter={'target': geometry})
        attempt.update({key: {'count': 0} for key in ('S1_W1', 'S1_W2', 'S1_S2', 'D1_D2_app')})
        return dict(protocolVersion=2, accepted=True, timeoutMs=5000, windowLayerMatch=True, displayStable=True,
                    attempts=[attempt], acceptedAttempt=1, captureWindowKind=kind,
                    mappingMode='MEASURED_WINDOW_BUFFER_TRANSLATION')

    def verify(self):
        self.folder.mkdir(exist_ok=True)
        (self.folder / 'geometry.json').write_text(json.dumps(self.baseline))
        for name, geometry, kind in [('extraction-tools', self.dialog, 'EXPLICIT_WINDOW'),
                                     ('extraction-overview', self.overview_geometry, 'EXPLICIT_WINDOW'),
                                     ('extraction-tools-restored', self.baseline, 'ACTIVITY')]:
            directory = self.folder / 'states' / name
            directory.mkdir(parents=True, exist_ok=True)
            proof = self.proof(geometry, kind)
            (directory / 'geometry.json').write_text(json.dumps(geometry))
            (directory / 'window-frame-sync.json').write_text(json.dumps(proof))
            if kind == 'ACTIVITY':
                self.report['restoredFrameSync'] = proof
        (self.folder / 'extraction-reachability.json').write_text(json.dumps(self.report))
        self.ns['require_tools_report'](self.folder, 'extraction-reachability.json', 'RUNNING', self.expected)

    def test_full_shared_dialog_proof_passes(self):
        self.verify()

    def test_missing_tool_fails(self):
        self.report['reached'].pop()
        with self.assertRaises(AssertionError): self.verify()

    def test_claimed_smaller_density_cannot_pass_48dp(self):
        self.report['reached'][0]['density'] = 1
        with self.assertRaises(AssertionError): self.verify()

    def test_nonzero_initial_scroll_fails(self):
        self.report['initialScrollPx'] = self.report['restoredScrollPx'] = [0, 1]
        with self.assertRaises(AssertionError): self.verify()

    def test_disabled_stop_fails(self):
        self.state['fixedAction']['stopEnabled'] = False
        with self.assertRaises(AssertionError): self.verify()

    def test_missing_overview_line_fails(self):
        self.report['overview']['coveredLines'] = []
        with self.assertRaises(AssertionError): self.verify()

    def test_chart_explanation_cannot_be_omitted(self):
        self.report['overview']['chartExplanation'] = 'missing explanation'
        with self.assertRaises(AssertionError): self.verify()

    def test_overview_cannot_reuse_another_owner_state(self):
        self.overview_geometry['ownerState'] = copy.deepcopy(self.state)
        self.overview_geometry['ownerState']['traceIdentity'] = 'another-cup'
        with self.assertRaises(AssertionError): self.verify()

    def test_partial_overview_line_fails(self):
        self.overview_state['rows'][0]['complete'] = False
        with self.assertRaises(AssertionError): self.verify()


if __name__ == '__main__': unittest.main()
