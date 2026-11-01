"""Synthetic current-contract fixtures for the real weekly-editor route host gate."""
import copy
from pathlib import Path
import unittest

from test_settings_language_error_contract import node
from test_settings_language_host_contract import verifier_functions


def fixture(expanded, locale='zh-Hans'):
    labels = [f'编辑 {day} 的睡眠/唤醒时间' for day in ('周日', '周一', '周二', '周三', '周四', '周五', '周六')]
    edit, collapse = ('编辑每周时间', '收起每周编辑') if locale.startswith('zh') else ('Edit weekly times', 'Collapse weekly editor')
    toggle = node(9, 'android.widget.Button', value=collapse if expanded else edit)
    toggle.update(rectPx=[0, 0, 200, 108], visibleRectPx=[0, 0, 200, 108])
    nodes, days = [toggle], []
    for index, label in enumerate(labels):
        actual = node(20+index, 'android.widget.Button', value=label)
        rect = [0, 120+index*120, 200, 228+index*120] if expanded else [0, 0, 0, 0]
        actual.update(rectPx=rect, visibleRectPx=rect, complete=expanded and index < 3)
        if not actual['complete']:
            actual['textLineBoxes'][0]['fullViewVisible'] = False
        nodes.append(actual)
        days.append({'index': index, 'viewIdentity': 20+index, 'parentIdentity': 7,
                     'tokenIdentity': 4, 'rootViewIdentity': 3, 'text': label,
                     'isShown': expanded, 'widthPx': 200 if expanded else 0, 'heightPx': 108 if expanded else 0})
    route = {'contract': 'ACTUAL_WEEKLY_EDITOR_ROUTE_V1', 'ownerActivityIdentity': 1,
             'windowIdentity': 2, 'decorIdentity': 3, 'windowTokenIdentity': 4, 'rootIdentity': 5,
             'cardIdentity': 6, 'editorIdentity': 7, 'toggleParentIdentity': 6, 'editorParentIdentity': 6,
             'editorVisibility': 0 if expanded else 8, 'editorShown': expanded, 'editorLayoutRequested': False,
             'density': 2.25, 'locale': locale, 'editResource': 'machine_settings_schedule_edit', 'editLabel': edit,
             'collapseResource': 'machine_settings_schedule_collapse', 'collapseLabel': collapse,
             'dayLabelResource': 'machine_settings_edit_sleep_day',
             'dayNameResources': ['machine_settings_'+day for day in ('sunday','monday','tuesday','wednesday','thursday','friday','saturday')],
             'expectedDayLabels': labels, 'days': days, 'toggle': copy.deepcopy(toggle), 'firstDay': copy.deepcopy(nodes[1]),
             'scheduleState': {'ownerServiceIdentity': 8, 'state': 'IDLE', 'pendingIsNull': True,
                               'firstReadback': None, 'secondReadback': None}}
    target = {'weeklyEditorRoute': route, 'locale': locale, 'densityDpi': 360,
              'appOriginPx': [0, 0], 'appSizePx': [600, 500], 'nodes': nodes}
    before = {'target': target}
    return {'captureWindowKind': 'ACTIVITY', 'accepted': True, 'timeoutMs': 5000, 'acceptedAttempt': 1,
            'windowIdentity': 2, 'decorIdentity': 3, 'windowTokenIdentity': 4, 'rootIdentity': 5,
            'attempts': [{'geometryBefore': before, 'geometryAfter': copy.deepcopy(before)}]}


def target(proof):
    return proof['attempts'][0]['geometryBefore']['target']


class WeeklyEditorRouteContract(unittest.TestCase):
    def setUp(self):
        self.validate = verifier_functions()['validate_weekly_editor_route']

    def test_real_collapsed_to_expanded_route_accepts_only_first_day_full_frame(self):
        for locale in ('zh-Hans', 'en'):
            with self.subTest(locale=locale):
                self.validate(fixture(False, locale), fixture(True, locale))

    def test_real_wire_readback_bytes_must_stay_identical_across_route(self):
        collapsed, expanded = fixture(False), fixture(True)
        for proof in (collapsed, expanded):
            state=target(proof)['weeklyEditorRoute']['scheduleState']
            state.update(firstReadback=[0x83,0x40,0]+[0]*17, secondReadback=[0x83,0x80]+[0]*13)
            proof['attempts'][0]['geometryAfter']=copy.deepcopy(proof['attempts'][0]['geometryBefore'])
        self.validate(collapsed, expanded)
        target(expanded)['weeklyEditorRoute']['scheduleState']['firstReadback'][3]=1
        expanded['attempts'][0]['geometryAfter']=copy.deepcopy(expanded['attempts'][0]['geometryBefore'])
        with self.assertRaises(AssertionError):
            self.validate(collapsed, expanded)

    def test_contract_mutations_are_rejected_after_matching_before_after_geometry(self):
        changes = {
            'not_expanded': lambda t, r: r.update(editorVisibility=8, editorShown=False),
            'layout_pending': lambda t, r: r.update(editorLayoutRequested=True),
            'wrong_resource': lambda t, r: r.update(editResource='wrong'),
            'wrong_day_resource': lambda t, r: r.update(dayLabelResource='wrong'),
            'wrong_day_order': lambda t, r: r['dayNameResources'].reverse(),
            'wrong_locale': lambda t, r: r.update(locale='other'),
            'small_density': lambda t, r: r.update(density=0.1),
            'parent_replaced': lambda t, r: r.update(editorParentIdentity=99),
            'old_window': lambda t, r: r.update(windowIdentity=99),
            'old_token': lambda t, r: r.update(windowTokenIdentity=99),
            'old_activity': lambda t, r: r.update(ownerActivityIdentity=99),
            'changed_editor': lambda t, r: r.update(editorIdentity=99),
            'missing_day': lambda t, r: r['days'].pop(),
            'duplicate_day': lambda t, r: r['days'][1].update(viewIdentity=r['days'][0]['viewIdentity']),
            'day_parent_replaced': lambda t, r: r['days'][0].update(parentIdentity=99),
            'day_token_replaced': lambda t, r: r['days'][0].update(tokenIdentity=99),
            'day_node_missing': lambda t, r: t['nodes'].pop(),
            'day_node_duplicate': lambda t, r: t['nodes'].append(copy.deepcopy(t['nodes'][1])),
            'day_wrong_order': lambda t, r: r['days'].reverse(),
            'wrong_first_day': lambda t, r: r['firstDay'].update(viewIdentity=21),
            'schedule_written': lambda t, r: r['scheduleState'].update(state='CONFIRMED'),
            'pending_schedule': lambda t, r: r['scheduleState'].update(pendingIsNull=False),
            'service_replaced': lambda t, r: r['scheduleState'].update(ownerServiceIdentity=99),
            'unknown_readback': lambda t, r: r['scheduleState'].update(firstReadback='unknown'),
            'readback_changed': lambda t, r: r['scheduleState'].update(firstReadback=[0x83,0x40]+[0]*18),
            'toggle_hidden': lambda t, r: self.mutate_node(t, r, 'toggle', lambda n: n.update(complete=False)),
            'toggle_truncated': lambda t, r: self.mutate_node(t, r, 'toggle', lambda n: n['textLineBoxes'][0].update(ellipsisCount=1)),
            'wrong_toggle_label': lambda t, r: self.mutate_node(t, r, 'toggle', lambda n: n['textLineBoxes'][0].update(text='wrong')),
            'undersized_toggle': lambda t, r: self.mutate_node(t, r, 'toggle', lambda n: n.update(rectPx=[0,0,200,107])),
            'first_day_partial': lambda t, r: self.mutate_node(t, r, 'firstDay', lambda n: n.update(complete=False)),
            'first_day_outside': lambda t, r: self.mutate_node(t, r, 'firstDay', lambda n: n.update(rectPx=[0,600,200,708], visibleRectPx=[0,600,200,708])),
        }
        for name, change in changes.items():
            collapsed, expanded = fixture(False), fixture(True)
            current = target(expanded);change(current, current['weeklyEditorRoute'])
            expanded['attempts'][0]['geometryAfter'] = copy.deepcopy(expanded['attempts'][0]['geometryBefore'])
            with self.subTest(mutation=name), self.assertRaises((AssertionError, KeyError)):
                self.validate(collapsed, expanded)

    @staticmethod
    def mutate_node(target, route, key, change):
        change(route[key])
        identity = route[key]['viewIdentity']
        position = next(index for index, node in enumerate(target['nodes']) if node['viewIdentity'] == identity)
        target['nodes'][position] = copy.deepcopy(route[key])

    def test_missing_frame_and_geometry_change_are_rejected(self):
        with self.assertRaises((AssertionError, KeyError)):
            self.validate(fixture(False), {})
        proof = fixture(True)
        proof['attempts'][0]['geometryAfter']['target']['locale'] = 'changed'
        with self.assertRaises(AssertionError):
            self.validate(fixture(False), proof)

    def test_actual_driver_keeps_normal_click_and_original_schedule_dialog_gates(self):
        source = (Path(__file__).resolve().parents[1] /
                  'mobile/src/androidTest/kotlin/io/openhoyi/mobile/SettingsVisualMigrationChecks.kt').read_text()
        route = source.split('val routeActivity=activity;', 1)[1].split('val schedule=requireDialog(activity)', 1)[0]
        self.assertIn('click(activity,toggle)', route)
        self.assertIn('reveal(activity,toggle)', route)
        self.assertIn('reveal(activity,day)', route)
        self.assertNotIn('setVisibility(', route)
        self.assertNotIn('.performClick(', route)
        self.assertIn('click(activity,toggle) {weeklyEditorObservation()}', route)
        self.assertIn('click(activity,day) {weeklyEditorObservation()}', route)
        self.assertIn('click(activity,day) {weeklyEditorObservation()};val reopened=', source)
        click_body=source.split('private fun click(activity:Activity,view:View,',1)[1].split('private fun click(activity:Activity,tag:',1)[0]
        self.assertLess(click_body.index('beforeClick?.invoke()'),click_body.index('view.performClick()'))
        for state in ('schedule-draft-native-ime', 'schedule-cancel-no-write'):
            self.assertIn('File(folder,"'+state+'")', source)
        self.assertIn('==draft', source)


if __name__ == '__main__':
    unittest.main()
