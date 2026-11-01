import subprocess
import unittest
import uuid
import xml.etree.ElementTree as ET
from unittest.mock import patch
import mock_ui_tap as tap

NONCE='12345678-1234-1234-1234-123456789012'

def tree():
    return (f'<hierarchy schema="mock-active-tree-v1" nonce="{NONCE}" package="{tap.PACKAGE}" window-id="7" bounds="[0,0][100,100]" started-elapsed-nanos="10">'
            f'<node package="{tap.PACKAGE}" class="android.widget.FrameLayout" bounds="[0,0][100,100]">'
            f'<node package="{tap.PACKAGE}" class="android.widget.Button" text="A &amp; B" enabled="true" clickable="true" bounds="[0,0][50,50]"/></node>'
            '<capture complete="true" nodes="2" window-id="7" bounds="[0,0][100,100]" finished-elapsed-nanos="20"/></hierarchy>')

class ActiveTreeTest(unittest.TestCase):
    def test_real_xml_preserves_labels_and_original_selector(self):
        tap.validate_hierarchy(tree(),NONCE)
        self.assertEqual((25,25),tap.find_target(tree(),'A & B'))
    def test_unknown_partial_foreign_or_stale_tree_fails_closed(self):
        bad=[tree().replace(NONCE,'old'),tree().replace('complete="true"','complete="false"'),
             tree().replace('nodes="2"','nodes="1"'),tree().replace('package="'+tap.PACKAGE+'"','package="other"',1),
             tree().replace('window-id="7" bounds="[0,0][100,100]" finished','window-id="8" bounds="[0,0][100,100]" finished'),
             tree().replace('bounds="[0,0][100,100]" finished','bounds="[0,0][90,100]" finished'),
             tree().replace('finished-elapsed-nanos="20"','finished-elapsed-nanos="9"'),'<hierarchy/>','<broken']
        for xml in bad:
            with self.subTest(xml=xml), self.assertRaises((ValueError,ET.ParseError)):tap.validate_hierarchy(xml,NONCE)
    def call(self,responses):
        with patch.object(tap,'ensure_mock',return_value='mock'),patch.object(tap.uuid,'uuid4',return_value=uuid.UUID(NONCE)),patch.object(tap,'adb',side_effect=responses) as adb:
            result=tap.read_hierarchy()
            return result,adb
    def test_self_target_and_same_pid_with_unique_file(self):
        xml,adb=self.call(['42','','MOCK_ACTIVE_TREE_CAPTURED nonce='+NONCE+' nodes=2 window=7',tree(),'42',''])
        self.assertEqual(tree(),xml)
        self.assertIn(tap.TREE_RUNNER,adb.call_args_list[2].args)
        command=adb.call_args_list[2].args
        budget=int(command[command.index('budgetMs')+1])
        self.assertTrue(0<budget<=30000)
        self.assertTrue(all('uiautomator' not in call.args for call in adb.call_args_list))
        self.assertEqual(('shell','run-as',tap.TEST_PACKAGE,'rm','-f','files/mock-hierarchy/'+NONCE+'.xml'),adb.call_args_list[1].args)
        self.assertTrue(all(0<call.kwargs['timeout']<=30 for call in adb.call_args_list))
    def test_process_change_never_returns_xml(self):
        with self.assertRaisesRegex(ValueError,'process changed'):
            self.call(['42','','MOCK_ACTIVE_TREE_CAPTURED nonce='+NONCE+' nodes=2',tree(),'43',''])
    def test_zero_adb_exit_without_capture_marker_never_reads_stale_file(self):
        with self.assertRaisesRegex(ValueError,'collector failed'):
            self.call(['42','','INSTRUMENTATION_CODE: 0',''])
    def test_missing_unique_file_is_not_success(self):
        error=subprocess.CalledProcessError(1,['adb'],stderr='No such file')
        with self.assertRaisesRegex(ValueError,'hierarchy was not written'):
            self.call(['42','','MOCK_ACTIVE_TREE_CAPTURED nonce='+NONCE+' nodes=2',error,''])
    def test_two_runner_targets_are_both_preserved(self):
        installed=(f'instrumentation:{tap.TEST_PACKAGE}/io.openhoyi.mobile.BrewAudioInstrumentation (target={tap.PACKAGE})\n'
                   f'instrumentation:{tap.TEST_PACKAGE}/io.openhoyi.mobile.MockHierarchyInstrumentation (target={tap.TEST_PACKAGE})\n')
        self.assertEqual(2,len(tap.validate_installed_runners(installed)))
        with self.assertRaises(ValueError):tap.validate_installed_runners(installed.replace('(target='+tap.TEST_PACKAGE+')','(target='+tap.PACKAGE+')'))
    def test_packaged_manifest_rejects_agp_overwriting_self_runner(self):
        xml=f'<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="{tap.TEST_PACKAGE}"><instrumentation android:name="io.openhoyi.mobile.BrewAudioInstrumentation" android:targetPackage="{tap.PACKAGE}"/><instrumentation android:name="io.openhoyi.mobile.MockHierarchyInstrumentation" android:targetPackage="{tap.TEST_PACKAGE}"/><application android:debuggable="true"/></manifest>'
        self.assertEqual(2,len(tap.validate_runner_manifest(xml)))
        with self.assertRaises(ValueError):tap.validate_runner_manifest(xml.replace('android:targetPackage="'+tap.TEST_PACKAGE+'"','android:targetPackage="'+tap.PACKAGE+'"'))
