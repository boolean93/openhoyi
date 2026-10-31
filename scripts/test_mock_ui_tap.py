import unittest
from unittest.mock import patch
import subprocess
import hashlib
import io
import json
import sys
import tempfile
from contextlib import redirect_stderr
from pathlib import Path
from mock_ui_tap import AmbiguousMockControl, find_target, read_hierarchy, main


REAL_HISTORY_XML = Path(__file__).resolve().parent / 'fixtures/mock_ui_tap/history-detail-ambiguous.xml'
REAL_HISTORY_XML_SHA = '2bf2c04e93e3d7038e0c69393b64093660d20f4465c0944d46052ef6bac88336'


class MockUiTapTest(unittest.TestCase):
    def test_real_history_xml_requires_exact_button_class(self):
        source = REAL_HISTORY_XML.read_bytes()
        self.assertEqual(REAL_HISTORY_XML_SHA, hashlib.sha256(source).hexdigest())
        xml = source.decode('utf-8')
        with self.assertRaises(AmbiguousMockControl):
            find_target(xml, '查看详情')
        self.assertEqual((2398, 1366), find_target(xml, '查看详情', node_class='android.widget.Button'))
        self.assertEqual((2470, 317), find_target(xml, '查看详情', node_class='android.widget.ImageButton'))
        self.assertIsNone(find_target(xml, '查看详情', node_class='Button'))

    def test_class_filter_preserves_ancestor_and_center_rules(self):
        xml = ('<hierarchy><node class="android.widget.ImageButton" content-desc="查看详情" '
            'enabled="true" clickable="true" bounds="[0,0][20,20]"/>'
            '<node class="android.widget.Button" text="查看详情" enabled="true" clickable="true" '
            'bounds="[20,0][40,20]"/>'
            '<node class="android.widget.Button" text="查看详情" enabled="true" clickable="true" '
            'bounds="[40,0][60,20]"/></hierarchy>')
        with self.assertRaises(AmbiguousMockControl) as caught:
            find_target(xml, '查看详情', node_class='android.widget.Button')
        self.assertEqual([[30, 10], [50, 10]], caught.exception.centers)
        filtered = caught.exception.candidates[0]
        self.assertEqual(['content-desc'], filtered['matchedSources'])
        self.assertEqual('class-filtered', filtered['invalidReason'])
        self.assertEqual([], filtered['ancestorChain'])
        self.assertIsNone(filtered['selectedNode'])
        self.assertIsNone(filtered['center'])
        same_center = xml.replace('[40,0][60,20]', '[20,0][40,20]')
        self.assertEqual((30, 10), find_target(same_center, '查看详情', node_class='android.widget.Button'))
        list_xml = ('<hierarchy><node class="android.widget.ListView" enabled="true" bounds="[0,0][100,500]">'
            '<node bounds="[0,20][100,80]"><node class="android.widget.TextView" text="查看详情"/>'
            '</node></node></hierarchy>')
        self.assertEqual((50, 50), find_target(list_xml, '查看详情', node_class='android.widget.TextView'))

    def test_exact_class_does_not_tap_disabled_or_invalid_button(self):
        for enabled, bounds in [('false', '[0,0][50,50]'), ('true', '[0,0][0,0]')]:
            xml = (f'<hierarchy><node class="android.widget.Button" text="查看详情" clickable="true" '
                   f'enabled="{enabled}" bounds="{bounds}" /></hierarchy>')
            self.assertIsNone(find_target(xml, '查看详情', node_class='android.widget.Button'))

    def test_main_passes_exact_class_and_keeps_two_foreground_checks_one_tap(self):
        with patch('mock_ui_tap.ensure_mock', return_value='topResumedActivity=mock') as foreground, \
                patch('mock_ui_tap.read_hierarchy', return_value='<hierarchy/>') as hierarchy, \
                patch('mock_ui_tap.find_target', return_value=(17, 23)) as finder, \
                patch('mock_ui_tap.adb', return_value='') as adb_call, \
                patch.object(sys, 'argv', ['mock_ui_tap.py', '查看详情', '--node-class', 'android.widget.Button']):
            main()
        finder.assert_called_once_with('<hierarchy/>', '查看详情', False, 'android.widget.Button')
        self.assertEqual(2, foreground.call_count)
        hierarchy.assert_called_once_with()
        adb_call.assert_called_once_with('shell', 'input', 'tap', '17', '23')

    def test_class_ambiguity_diagnostic_uses_one_xml_without_extra_adb(self):
        xml = ('<hierarchy><node class="android.widget.ImageButton" content-desc="查看详情" '
            'clickable="true" enabled="true" bounds="[0,0][20,20]"/>'
            '<node class="android.widget.Button" text="查看详情" clickable="true" enabled="true" '
            'bounds="[20,0][40,20]"/><node class="android.widget.Button" text="查看详情" '
            'clickable="true" enabled="true" bounds="[40,0][60,20]"/></hierarchy>')
        with tempfile.TemporaryDirectory() as directory, \
                patch('mock_ui_tap.ensure_mock', return_value='topResumedActivity=mock') as foreground, \
                patch('mock_ui_tap.read_hierarchy', return_value=xml) as hierarchy, \
                patch('mock_ui_tap.adb') as adb_call, \
                patch.object(sys, 'argv', ['mock_ui_tap.py', '查看详情', '--node-class',
                                          'android.widget.Button', '--scroll', '--diagnostics-dir', directory]):
            with self.assertRaises(AmbiguousMockControl):
                main()
            folder, = Path(directory).iterdir()
            self.assertEqual(xml, (folder / 'hierarchy.xml').read_text())
            report = json.loads((folder / 'candidates.json').read_text())
            self.assertEqual('android.widget.Button', report['nodeClass'])
            self.assertEqual(hashlib.sha256(xml.encode()).hexdigest(), report['hierarchySha256'])
            self.assertEqual('class-filtered', report['candidates'][0]['invalidReason'])
            self.assertEqual([[30, 10], [50, 10]], report['centers'])
            self.assertEqual(1, foreground.call_count)
            hierarchy.assert_called_once_with()
            adb_call.assert_not_called()

    def test_class_diagnostic_write_failure_keeps_original_ambiguity(self):
        xml = ('<hierarchy><node class="android.widget.Button" text="X" clickable="true" '
            'enabled="true" bounds="[0,0][50,50]"/><node class="android.widget.Button" '
            'text="X" clickable="true" enabled="true" bounds="[0,60][50,100]"/></hierarchy>')
        with tempfile.TemporaryDirectory() as directory:
            blocked = Path(directory) / 'occupied'; blocked.write_text('file')
            with patch('mock_ui_tap.ensure_mock', return_value='topResumedActivity=mock') as foreground, \
                    patch('mock_ui_tap.read_hierarchy', return_value=xml) as hierarchy, \
                    patch('mock_ui_tap.adb') as adb_call, \
                    patch.object(sys, 'argv', ['mock_ui_tap.py', 'X', '--node-class', 'android.widget.Button',
                                              '--diagnostics-dir', str(blocked)]):
                stderr = io.StringIO()
                with redirect_stderr(stderr), self.assertRaises(AmbiguousMockControl) as caught:
                    main()
                self.assertEqual([[25, 25], [25, 80]], caught.exception.centers)
                self.assertIn('diagnostic write failed', stderr.getvalue())
                self.assertEqual(1, foreground.call_count)
                hierarchy.assert_called_once_with()
                adb_call.assert_not_called()

    def test_zero_exit_without_dump_is_not_success_or_stale_data(self):
        error = subprocess.CalledProcessError(1, ['adb'], stderr='No such file')
        with patch('mock_ui_tap.adb', side_effect=['', 'ERROR: idle timeout', error]) as calls:
            with self.assertRaisesRegex(ValueError, 'idle timeout'):
                read_hierarchy()
            self.assertEqual(('shell', 'rm', '-f', '/data/local/tmp/openhoyi-mock-ui.xml'), calls.call_args_list[0].args)
    def test_list_label_taps_its_row_not_the_list_center(self):
        xml = '<hierarchy><node class="android.widget.ListView" clickable="true" enabled="true" bounds="[0,0][100,500]"><node bounds="[0,20][100,80]"><node text="Shot" /></node></node></hierarchy>'
        self.assertEqual((50, 50), find_target(xml, 'Shot'))
    def test_nonclickable_label_uses_enabled_parent(self):
        xml = '<hierarchy><node clickable="true" enabled="true" bounds="[10,20][110,80]"><node text="应用设置" clickable="false" /></node></hierarchy>'
        self.assertEqual((60, 50), find_target(xml, '应用设置'))
        self.assertIsNone(find_target(xml, '设置'))
        self.assertEqual((60, 50), find_target(xml, '设置', True))

    def test_disabled_or_empty_control_is_not_tapped(self):
        for enabled, bounds in [('false', '[0,0][50,50]'), ('true', '[0,0][0,0]')]:
            xml = f'<hierarchy><node text="Start" clickable="true" enabled="{enabled}" bounds="{bounds}" /></hierarchy>'
            self.assertIsNone(find_target(xml, 'Start'))

    def test_duplicate_targets_fail_instead_of_guessing(self):
        xml = '<hierarchy><node text="Start" clickable="true" enabled="true" bounds="[0,0][50,50]"/><node text="Start" clickable="true" enabled="true" bounds="[0,60][50,100]"/></hierarchy>'
        with self.assertRaises(ValueError):
            find_target(xml, 'Start')

    def test_ambiguous_candidates_preserve_exact_source_and_selected_ancestor(self):
        xml = ('<hierarchy><node class="android.widget.ListView" enabled="true" bounds="[0,0][100,500]">'
            '<node bounds="[0,20][100,80]"><node text="查看详情" resource-id="row-label" /></node></node>'
            '<node class="android.widget.Button" text="查看详情" content-desc="查看详情" clickable="true" '
            'enabled="true" bounds="[120,20][220,80]"/></hierarchy>')
        with self.assertRaisesRegex(ValueError,'ambiguous Mock control: 查看详情') as caught:
            find_target(xml,'查看详情')
        error=caught.exception
        self.assertEqual([[50,50],[170,50]],error.centers)
        self.assertEqual(2,len(error.candidates))
        self.assertEqual(['text'],error.candidates[0]['matchedSources'])
        self.assertEqual(['text','content-desc'],error.candidates[1]['matchedSources'])
        self.assertEqual('list-row',error.candidates[0]['selectionReason'])
        self.assertEqual('clickable-ancestor',error.candidates[1]['selectionReason'])
        self.assertEqual('row-label',error.candidates[0]['matchedNode']['attributes']['resource-id'])
        self.assertEqual([50,50],error.candidates[0]['center'])
        self.assertTrue(error.candidates[0]['ancestorChain'])

    def test_same_center_collapses_and_invalid_bounds_do_not_create_target(self):
        xml=('<hierarchy><node text="Shot" clickable="true" enabled="true" bounds="[0,0][100,100]"/>'
            '<node content-desc="Shot" clickable="true" enabled="true" bounds="[0,0][100,100]"/>'
            '<node text="Shot" clickable="true" enabled="true" bounds="bad"/></hierarchy>')
        self.assertEqual((50,50),find_target(xml,'Shot'))

    def test_main_saves_original_xml_and_never_calls_adb_after_ambiguity(self):
        xml='<hierarchy>\n<node text="查看详情" clickable="true" enabled="true" bounds="[0,0][50,50]"/>\n<node content-desc="查看详情" clickable="true" enabled="true" bounds="[0,60][50,100]"/>\n</hierarchy>'
        foreground='topResumedActivity=ActivityRecord{1 io.openhoyi.mobile.mock/io.openhoyi.mobile.HistoryActivity}'
        calls=[]
        def fake_adb(*args,**kwargs):
            calls.append(args)
            if args[:3]==('shell','dumpsys','activity'):return foreground
            if args[:3]==('shell','rm','-f'):return ''
            if args[:3]==('shell','uiautomator','dump'):return 'UI hierarchy dumped'
            if args[:2]==('shell','cat'):return xml
            self.fail(f'unexpected adb call: {args}')
        with tempfile.TemporaryDirectory() as directory,patch('mock_ui_tap.adb',side_effect=fake_adb),\
            patch.object(sys,'argv',['mock_ui_tap.py','查看详情','--scroll','--diagnostics-dir',directory]):
            with self.assertRaisesRegex(ValueError,'ambiguous Mock control: 查看详情'):
                main()
            folders=list(Path(directory).iterdir())
            self.assertEqual(1,len(folders))
            self.assertEqual(xml,(folders[0]/'hierarchy.xml').read_text())
            record=json.loads((folders[0]/'candidates.json').read_text())
            self.assertEqual(hashlib.sha256(xml.encode()).hexdigest(),record['hierarchySha256'])
            self.assertEqual(foreground,record['foregroundCheckBeforeHierarchy'])
            self.assertEqual(0,record['attempt'])
            self.assertEqual([],record['completedScrollDirections'])
            self.assertEqual('NO_TAP_AFTER_AMBIGUITY',record['outcome'])
            self.assertEqual(4,len(calls))

    def test_diagnostic_write_failure_preserves_ambiguity(self):
        xml='<hierarchy><node text="X" clickable="true" enabled="true" bounds="[0,0][50,50]"/><node text="X" clickable="true" enabled="true" bounds="[0,60][50,100]"/></hierarchy>'
        with tempfile.TemporaryDirectory() as directory:
            blocked=Path(directory)/'not-a-directory';blocked.write_text('occupied')
            with patch('mock_ui_tap.ensure_mock',return_value='topResumedActivity=mock'),\
                patch('mock_ui_tap.read_hierarchy',return_value=xml),\
                patch.object(sys,'argv',['mock_ui_tap.py','X','--diagnostics-dir',str(blocked)]):
                captured=io.StringIO()
                with redirect_stderr(captured):
                    with self.assertRaisesRegex(ValueError,'ambiguous Mock control: X'):
                        main()
                self.assertIn('diagnostic write failed',captured.getvalue())
