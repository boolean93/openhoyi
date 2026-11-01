import base64
import unittest
import mock_capture_continuity as continuity


class ContinuityTest(unittest.TestCase):
    def test_actual_history_identity_and_status(self):
        enc = lambda s: base64.urlsafe_b64encode(s.encode()).decode().rstrip('=')
        row = '\t'.join([enc('shot-a'), enc('capture-2'), '1000', '', '', 'RUNNING', '', '', '7', 'true'])
        actual = continuity.rows('<map><string name="entries_v1">' + row + '</string></map>')
        self.assertEqual([{'id': 'shot-a', 'curve': 'capture-2', 'startedAtMs': 1000,
                           'status': 'RUNNING', 'observedRunning': True}], actual)
        with self.assertRaises(ValueError):
            continuity.rows('<map/>')

    def test_process_service_and_cup_each_remain_hard_gates(self):
        before = {'pid': '42', 'serviceToken': 'abc', 'shot': {'id': 'cup'}}
        continuity.same_owner(before, dict(before))
        for key in before:
            with self.subTest(key=key), self.assertRaises(ValueError):
                continuity.same_owner(before, dict(before, **{key: 'other'}))

    def test_persisted_samples_span_capture_without_exact_cadence_assumption(self):
        def samples(times):
            return '# openhoyi-shot-points-v4\n' + ''.join(
                '\t'.join([str(t), '10', '20', '30', '9000', '', '', 'true', '1']) + '\n' for t in times)
        self.assertEqual(4, continuity.sample_span(samples([0, 501, 9000, 31750]), 2000, 8500)['count'])
        for times, lower, upper in [([0, 500, 500, 31750], 2000, 8500),
                                    ([0, 500, 400, 31750], 2000, 8500),
                                    ([3000, 31750], 2000, 8500), ([0, 8000], 2000, 8500)]:
            with self.subTest(times=times), self.assertRaises(ValueError):
                continuity.sample_span(samples(times), lower, upper)

    def test_android_shared_preferences_indent_is_not_a_record(self):
        enc = lambda s: base64.urlsafe_b64encode(s.encode()).decode().rstrip('=')
        fields = [enc('current'), enc('capture-2'), '1000', '', '', 'RUNNING', '', '', '7', 'true']
        xml = '<map>\n    <string name="entries_v1">' + '&#9;'.join(fields) + '&#10;    </string>\n</map>'
        records = continuity.rows(xml)
        self.assertEqual(1, len(records))
        self.assertEqual('current', records[0]['id'])
        self.assertTrue(records[0]['observedRunning'])
        for count in (8, 9):
            old = '<map><string name="entries_v1">' + '&#9;'.join(fields[:count]) + '&#10;    </string></map>'
            self.assertFalse(continuity.rows(old)[0]['observedRunning'])
        for bad in ('not-a-row', '&#9;bad', 'unknown nonblank schema'):
            with self.subTest(bad=bad), self.assertRaises(ValueError):
                continuity.rows(xml.replace('&#10;    </string>', '&#10;' + bad + '&#10;    </string>'))
