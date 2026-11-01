"""Exercise the real host gate with accepted-target files and deliberate corruptions."""
import ast
import copy
import json
from pathlib import Path
import tempfile
import unittest

SOURCE=Path(__file__).with_name('check_extraction_history_visual_migration.sh')

class StandaloneHeaderContract(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        code=SOURCE.read_text().split("<<'VERIFY_PY'\n",1)[1].split('\nVERIFY_PY',1)[0]
        functions=[n for n in ast.parse(code).body if isinstance(n,ast.FunctionDef) and n.name=='require_standalone_header']
        namespace=dict(json=json,Path=Path,require_proof=lambda path:json.loads(path.read_text()))
        exec(compile(ast.Module(body=functions,type_ignores=[]),str(SOURCE),'exec'),namespace)
        cls.validate=staticmethod(namespace['require_standalone_header'])
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory();self.addCleanup(self.temp.cleanup);self.root=Path(self.temp.name)
        tags=[f'product/extraction/metric/{m}/{p}' for m in ('PRESSURE','WATER','WEIGHT','TEMPERATURE') for p in ('name','number','unit')]
        tags += ['chart-range:'+m for m in ('PRESSURE','WATER','WEIGHT','TEMPERATURE')]+[None]
        texts=[dict(identity=i+10,semanticId=tag,text='原值',lineCount=1,lineCoordinateSource='TextView.getLineBounds + native_Layout_horizontal_advance + actual_ancestor_clip',visibility=dict(rectPx=[0,0,100,20],visibleRectPx=[0,0,100,20],screenMappingVerified=True),rows=[dict(line=0,start=0,end=2,ellipsisCount=0,rectPx=[0,0,20,20],complete=True)]) for i,tag in enumerate(tags)]
        controls=[dict(identity=i+100,semanticId='chart-metric:'+m,widthDp=96,heightDp=96,visibility=dict(rectPx=[0,0,216,216],visibleRectPx=[0,0,216,216],complete=True,screenMappingVerified=True),layout=dict(complete=True,rectPx=[0,0,216,216])) for i,m in enumerate(('PRESSURE','WATER','WEIGHT','TEMPERATURE'))]
        snapshot=dict(headerIdentity=1,headerVisibility=dict(complete=True,screenMappingVerified=True,visibleRectPx=[0,0,400,300]),viewport=[0,0,400,300],scrollPx=[0,0],texts=texts,controls=controls)
        self.report=dict(schema='STANDALONE_NATIVE_HEADER_REACH_V1',completed=True,initial=copy.deepcopy(snapshot),restored=copy.deepcopy(snapshot),beforeState={'points':[1]},afterState={'points':[1]},restoredProof='states/detail-header-restored/window-frame-sync.json',pages=[dict(snapshot=copy.deepcopy(snapshot),proof='states/detail-header-0/window-frame-sync.json')])
    def save(self):
        for relative,snapshot in [('states/detail-header-initial/window-frame-sync.json',self.report['initial']),(self.report['restoredProof'],self.report['restored']),*[(p['proof'],p['snapshot']) for p in self.report['pages']]]:
            path=self.root/relative;path.parent.mkdir(parents=True,exist_ok=True)
            nodes=[dict(rectPx=t['visibility']['rectPx'],textLineBoxes=[dict(text=t['text'],lineCount=t['lineCount'],ellipsisCount=0)],**({'semanticId':t['semanticId']} if t['semanticId'] is not None else {})) for t in snapshot['texts']]
            nodes += [dict(semanticId=c['semanticId'],rectPx=c['layout']['rectPx'],textLineBoxes=[]) for c in snapshot['controls']]
            target=dict(densityDpi=360,nodes=nodes,standaloneHeader=snapshot,standaloneHistoryPlot=dict(plotPresent=True,actualPlotHeightDp=96.1,visiblePlotHeightDp=96.1))
            path.write_text(json.dumps(dict(captureWindowKind='ACTIVITY',acceptedAttempt=1,attempts=[dict(geometryBefore=dict(target=target),geometryAfter=dict(target=target))])))
            path.with_name('geometry.json').write_text(json.dumps(target))
        (self.root/'standalone-header-reachability.json').write_text(json.dumps(self.report))
    def run_gate(self):self.save();self.validate(self.root)
    def test_complete_original_content_and_controls_pass(self):self.run_gate()
    def test_native_untagged_explanation_uses_missing_key_in_raw_node(self):
        self.save()
        target=json.loads((self.root/'states/detail-header-0/geometry.json').read_text())
        explanation=target['nodes'][16]
        self.assertNotIn('semanticId',explanation)
        self.assertIsNone(target['standaloneHeader']['texts'][16]['semanticId'])
        self.validate(self.root)
    def test_missing_line_coverage_fails(self):
        self.report['pages'][0]['snapshot']['texts'][0]['rows'][0].update(rectPx=[0,310,20,330],complete=False)
        with self.assertRaises(AssertionError):self.run_gate()
    def test_false_complete_claim_fails(self):
        self.report['pages'][0]['snapshot']['texts'][0]['rows'][0]['rectPx']=[0,310,20,330]
        with self.assertRaises(AssertionError):self.run_gate()
    def test_changed_original_text_fails(self):
        self.report['pages'][0]['snapshot']['texts'][0]['text']='替换'
        with self.assertRaises(AssertionError):self.run_gate()
    def test_small_touch_target_fails(self):
        self.report['pages'][0]['snapshot']['controls'][0]['heightDp']=47
        with self.assertRaises(AssertionError):self.run_gate()
    def test_lost_range_fails(self):
        for snapshot in [self.report['initial'],self.report['restored'],self.report['pages'][0]['snapshot']]:snapshot['texts']=[t for t in snapshot['texts'] if t['semanticId']!='chart-range:WATER']
        with self.assertRaises(AssertionError):self.run_gate()
    def test_scroll_not_restored_fails(self):
        self.report['restored']['scrollPx']=[0,100]
        with self.assertRaises(AssertionError):self.run_gate()
    def test_business_state_changed_fails(self):
        self.report['afterState']['points']=[2]
        with self.assertRaises(AssertionError):self.run_gate()
    def test_saved_geometry_not_the_accepted_snapshot_fails(self):
        self.save();(self.root/'states/detail-header-0/geometry.json').write_text('{}')
        with self.assertRaises(AssertionError):self.validate(self.root)
    def test_false_dp_size_fails_against_accepted_rect(self):
        for snapshot in [self.report['initial'],self.report['restored'],self.report['pages'][0]['snapshot']]:snapshot['controls'][0]['widthDp']=100
        with self.assertRaises(AssertionError):self.run_gate()
    def test_control_outside_header_viewport_fails(self):
        c=self.report['pages'][0]['snapshot']['controls'][0]
        c['layout']['rectPx']=[0,200,216,416];c['visibility']['rectPx']=[0,200,216,416]
        with self.assertRaises(AssertionError):self.run_gate()
    def test_wrong_ancestor_clip_fails(self):
        self.report['pages'][0]['snapshot']['texts'][0]['visibility']['visibleRectPx']=[0,10,100,20]
        with self.assertRaises(AssertionError):self.run_gate()
    def test_missing_accepted_proof_fails(self):
        self.save();(self.root/'states/detail-header-0/window-frame-sync.json').unlink()
        with self.assertRaises(FileNotFoundError):self.validate(self.root)

if __name__=='__main__':unittest.main()
