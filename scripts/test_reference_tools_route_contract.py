"""Exercise the actual host verifier against reference-dialog ownership and reachability failures."""
import copy
import json
import unittest
import test_extraction_tools_dialog_contract as tools_contract

class ReferenceToolsRouteContract(unittest.TestCase):
    layout = staticmethod(tools_contract.ActualToolsDialogContract.layout)
    proof = tools_contract.ActualToolsDialogContract.proof
    def setUp(self):
        tools_contract.ActualToolsDialogContract.setUp(self)
        import ast
        from test_extraction_tools_dialog_contract import SOURCE
        code=SOURCE.read_text().split("<<'VERIFY_PY'\n",1)[1].split('\nVERIFY_PY',1)[0]
        func=next(n for n in ast.parse(code).body if isinstance(n,ast.FunctionDef) and n.name=='require_reference_tools_route')
        exec(compile(ast.Module(body=[func],type_ignores=[]),str(SOURCE),'exec'),self.ns)
        entry=self.layout('product/extraction/tool/reference-choice');entry['rectPx']=[0,100,108,208]
        self.menu=dict(schema='ACTUAL_REFERENCE_TOOLS_ROUTE_V1',sourceEnabled=True,entryEnabled=True,noHiddenSourceClick=True,
                       sourceText='选择历史参考',entryText='选择历史参考',entry=entry,entryWidthPx=108,entryHeightPx=108,density=2.25,
                       windowIdentity=1,decorIdentity=2,tokenIdentity=3,ownerActivityIdentity=4,sourceIdentity=5)
        self.route=dict(menu=self.menu,menuFrameSync=self.proof(self.menu,'EXPLICIT_WINDOW'),ownerActivityIdentity=4,
                        chooserWindowIdentity=11,chooserDecorIdentity=12,chooserTokenIdentity=13,toolsDismissedByProduct=True,
                        menuProofPath='states/reference-menu/window-frame-sync.json',index=1,expectedEligibleCount=2,listCount=3,
                        itemLabels=['无参考','first','second'],offLabel='无参考',selectedRow=copy.deepcopy(entry),
                        selectedRowWidthPx=108,selectedRowHeightPx=108,density=2.25)

    def verify_route(self):
        (self.folder/'geometry.json').write_text(json.dumps({'densityDpi':360}))
        folder=self.folder/'states/reference-menu';folder.mkdir(parents=True,exist_ok=True)
        (folder/'geometry.json').write_text(json.dumps(self.menu))
        (folder/'window-frame-sync.json').write_text(json.dumps(self.route['menuFrameSync']))
        self.ns['require_reference_tools_route'](self.folder,self.route,1,2)

    def test_actual_reference_route_passes(self):self.verify_route()
    def test_wrong_owner_or_reused_window_rejected(self):
        for key,value in [('ownerActivityIdentity',99),('chooserWindowIdentity',1),('chooserTokenIdentity',0)]:
            original=self.route[key];self.route[key]=value
            with self.subTest(key=key),self.assertRaises(AssertionError):self.verify_route()
            self.route[key]=original
    def test_disabled_hidden_or_small_menu_rejected(self):
        for mutate in (lambda:self.menu.update(entryEnabled=False),lambda:self.menu['entry'].update(complete=False),lambda:self.menu.update(entryHeightPx=107)):
            original=copy.deepcopy(self.menu);mutate()
            with self.assertRaises(AssertionError):self.verify_route()
            self.menu.clear();self.menu.update(original)
    def test_wrong_selection_and_incomplete_row_rejected(self):
        for key,value in [('index',0),('listCount',2),('itemLabels',['无参考']),('selectedRowHeightPx',107)]:
            original=self.route[key];self.route[key]=value
            with self.subTest(key=key),self.assertRaises(AssertionError):self.verify_route()
            self.route[key]=original
    def test_wrong_window_kind_or_mapping_rejected(self):
        for key,value in [('captureWindowKind','ACTIVITY'),('mappingMode','ASSUMED_ORIGIN')]:
            original=self.route['menuFrameSync'][key];self.route['menuFrameSync'][key]=value
            with self.subTest(key=key),self.assertRaises(AssertionError):self.verify_route()
            self.route['menuFrameSync'][key]=original
    def test_geometry_outside_accepted_frame_rejected(self):
        frame=self.route['menuFrameSync']
        frame['attempts'][0]['geometryAfter']['target']=copy.deepcopy(self.menu)
        frame['attempts'][0]['geometryAfter']['target']['ownerActivityIdentity']=99
        with self.assertRaises(AssertionError):self.verify_route()
    def test_claimed_smaller_density_rejected(self):
        self.menu['density']=1
        with self.assertRaises(AssertionError):self.verify_route()
    def test_failed_frame_cannot_be_called_reachable(self):
        self.route['menuFrameSync']['accepted']=False
        with self.assertRaises(AssertionError):self.verify_route()
