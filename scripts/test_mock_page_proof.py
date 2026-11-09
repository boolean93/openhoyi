import unittest
import mock_ui_tap as tap

SHOT = {'id': 'actual-cup', 'curve': 'factory-v3-002', 'status': 'RUNNING', 'observedRunning': True}
XML = ('<hierarchy><node class="android.widget.Button" text="立即停止萃取" clickable="true" enabled="true" visible-to-user="true" bounds="[306,1321][2488,1447]"/>'
       '<node text="正在萃取，请守在机器旁"/><node text="SOE浅烘"/>'
       '<node text="00:07 · Mock" content-desc="00:07 · Mock · 模拟模式 · 不发送蓝牙命令"/></hierarchy>')

class PageProofTest(unittest.TestCase):
    def check_running(self, xml=XML, shot=SHOT):
        return tap.require_mock_page(xml, 'ExtractionActivity', 'extraction-running-dark', shot)

    def test_exact_current_action_context_and_observed_cup(self):
        self.assertEqual([1397,1384], self.check_running()['reachableCenter'])
        self.assertEqual('立即停止萃取', self.check_running()['stopAction']['text'])

    def test_old_or_fuzzy_stop_text_is_not_accepted(self):
        for text in ('停止', '立即停止', '立即停止其他操作'):
            with self.subTest(text=text), self.assertRaises(ValueError):
                self.check_running(XML.replace('立即停止萃取', text))

    def test_unique_enabled_visible_reachable_action_is_required(self):
        for xml in (XML.replace('enabled="true"', 'enabled="false"'),
                    XML.replace('visible-to-user="true"', 'visible-to-user="false"'),
                    XML.replace('clickable="true"', 'clickable="false"'),
                    XML.replace('[306,1321][2488,1447]', '[0,0][0,0]'),
                    XML.replace('</hierarchy>', '<node class="android.widget.Button" text="立即停止萃取"/></hierarchy>')):
            with self.subTest(xml=xml), self.assertRaises(ValueError): self.check_running(xml)

    def test_wrong_page_or_missing_running_context_is_rejected(self):
        for xml in (XML.replace('正在萃取，请守在机器旁', '等待开始'),
                    XML.replace('SOE浅烘', '其他曲线'), XML.replace(' · 模拟模式 · 不发送蓝牙命令', '')):
            with self.subTest(xml=xml), self.assertRaises(ValueError): self.check_running(xml)

    def test_unobserved_or_ended_wrong_cup_cannot_replace_actual_running_state(self):
        for shot in (None, dict(SHOT, observedRunning=False), dict(SHOT, status='ENDED'), dict(SHOT, curve='other')):
            with self.subTest(shot=shot), self.assertRaises(ValueError): self.check_running(shot=shot)

    def test_ready_page_markers_and_curve_hint_contract_remain(self):
        tap.require_mock_page('<hierarchy><node text="开始萃取"/></hierarchy>', 'ExtractionActivity', 'extraction-ready-dark')
        tap.require_mock_page('<hierarchy><node hint="搜索曲线"/></hierarchy>', 'CurveActivity')
        with self.assertRaises(ValueError): tap.require_mock_page('<hierarchy/>', 'ExtractionActivity')
