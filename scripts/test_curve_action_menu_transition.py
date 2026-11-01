"""The real slot-window classifier must recognize the current owned AlertDialog menu."""
import ast
import copy
from pathlib import Path
import unittest


class ActionMenuTransition(unittest.TestCase):
    def setUp(self):
        source=Path(__file__).with_name('check_curve_visual_migration.sh').read_text().split("<<'VERIFY_PY'\n",1)[1].split('\nVERIFY_PY',1)[0]
        tree=ast.parse(source)
        functions=[n for n in tree.body if isinstance(n,ast.FunctionDef) and n.name=='classify_slot_transition']
        self.ns={'POPUP_DECOR':'com.android.internal.policy.DecorView','DIALOG_DECOR':'com.android.internal.policy.DecorView'}
        exec(compile(ast.Module(body=functions,type_ignores=[]),'actual slot classifier','exec'),self.ns)
        self.original=dict(self.root(1,11),listIdentity=5,listTokenIdentity=11,choices=['Assign'],
                           menuKind='ALERT_DIALOG',windowIdentity=101,ownerActivityIdentity=7)
        root=self.root(2,12)
        self.observed=dict(originalPopup=copy.deepcopy(self.original),foreignRoots=[root],
                           decision='READY',reason='original_popup_absent',
                           dialog=dict(root,windowIdentity=102,ownerActivityIdentity=7,listIdentity=6,
                                       listAttached=True,listLaidOut=True,listRootIdentity=2,
                                       listTokenIdentity=12,listDisplayId=0))

    @staticmethod
    def root(identity,token):
        return dict(class_='unused',identity=identity,tokenIdentity=token,attached=True,laidOut=True,
                    layoutRequested=False,focused=True,displayId=0,**{'class':'com.android.internal.policy.DecorView'})

    def verify(self):
        return self.ns['classify_slot_transition'](self.observed,self.original,7)

    def test_owned_native_dialog_menu_transition_passes(self):
        self.assertEqual('READY',self.verify())

    def test_wrong_original_owner_fails(self):
        self.original['ownerActivityIdentity']=99
        self.observed['originalPopup']['ownerActivityIdentity']=99
        with self.assertRaises(AssertionError):self.verify()

    def test_retired_popup_window_shape_fails(self):
        self.original['class']=self.observed['originalPopup']['class']='android.widget.PopupWindow$PopupDecorView'
        with self.assertRaises(AssertionError):self.verify()

    def test_foreign_chooser_owner_fails(self):
        self.observed['dialog']['ownerActivityIdentity']=99
        with self.assertRaises(AssertionError):self.verify()

    def test_retained_original_token_mutation_fails(self):
        self.observed['foreignRoots'].insert(0,dict(self.root(1,88),focused=False))
        with self.assertRaises(AssertionError):self.verify()


if __name__=='__main__':unittest.main()
