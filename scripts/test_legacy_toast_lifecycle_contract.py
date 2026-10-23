import copy
import unittest

def validate(record, page, profile, pid, expected_cases):
    assert record['claim'] == 'owned_import_toasts_ended'
    assert record['page'] == page and record['profile'] == profile and record['pid'] == pid
    assert record['scopeClosed'] is True and record['pending'] == 0 and record['unattributedEvents'] == []
    operations = record['operations']
    assert [item['case'] for item in operations] == expected_cases
    assert [item['sequence'] for item in operations] == list(range(1, len(operations) + 1))
    source = {'curve': 'legacy-curve-import', 'history': 'legacy-history-import', 'detail': None}[page]
    seen_objects = set()
    for operation in operations:
        assert operation['pid'] == pid
        assert operation['startedNs'] < operation['deadlineNs'] <= operation['startedNs'] + 10_000_000_000
        cancelled = operation['case'] == 'saf-cancel'
        assert operation['source'] == (None if cancelled else source)
        assert len(operation['toasts']) == (0 if cancelled else 1)
        for toast in operation['toasts']:
            assert toast['observerSequence'] > 0 and toast['observerSequence'] not in seen_objects
            seen_objects.add(toast['observerSequence'])
            assert toast['objectIdentity'] > 0 and toast['operationSequence'] == operation['sequence']
            assert toast['source'] == source and toast['pid'] == pid and toast['duration'] == 'LENGTH_LONG'
            events = toast['events']
            assert [event['event'] for event in events] == ['show-request', 'shown', 'hidden']
            assert all(event['mainThread'] is True for event in events)
            times = [event['elapsedNs'] for event in events]
            assert operation['startedNs'] <= times[0] < times[1] < times[2] <= operation['deadlineNs']


def sample():
    return {
        'claim': 'owned_import_toasts_ended', 'page': 'curve',
        'profile': 'tablet-user-landscape-light-font-1.0', 'pid': 123,
        'scopeClosed': True, 'unattributedEvents': [], 'pending': 0,
        'operations': [
            {'sequence': 1, 'case': 'saf-cancel', 'source': None, 'pid': 123,
             'startedNs': 10, 'deadlineNs': 10_000_000_010, 'toasts': []},
            {'sequence': 2, 'case': 'wrong-format', 'source': 'legacy-curve-import', 'pid': 123,
             'startedNs': 20, 'deadlineNs': 10_000_000_020, 'toasts': [
                 {'observerSequence': 1, 'objectIdentity': 456, 'source': 'legacy-curve-import',
                  'operationSequence': 2, 'pid': 123, 'duration': 'LENGTH_LONG',
                  'events': [{'event': 'show-request', 'elapsedNs': 30, 'mainThread': True},
                             {'event': 'shown', 'elapsedNs': 40, 'mainThread': True},
                             {'event': 'hidden', 'elapsedNs': 50, 'mainThread': True}]}]},
        ]}


class ToastContractTest(unittest.TestCase):
    def test_valid(self):
        validate(sample(), 'curve', 'tablet-user-landscape-light-font-1.0', 123,
                 ['saf-cancel', 'wrong-format'])

    def test_mutations_fail_closed(self):
        def mutate(case, change):
            value = copy.deepcopy(sample()); change(value)
            with self.subTest(case=case), self.assertRaises((AssertionError, ValueError)):
                validate(value, 'curve', 'tablet-user-landscape-light-font-1.0', 123,
                         ['saf-cancel', 'wrong-format'])
        mutate('missing-hidden', lambda x: x['operations'][1]['toasts'][0]['events'].pop())
        mutate('missing-shown', lambda x: x['operations'][1]['toasts'][0]['events'].pop(1))
        mutate('duplicate-toast', lambda x: x['operations'][1]['toasts'].append(copy.deepcopy(x['operations'][1]['toasts'][0])))
        mutate('wrong-source', lambda x: x['operations'][1]['toasts'][0].update(source='legacy-history-import'))
        mutate('cross-case', lambda x: x['operations'][1]['toasts'][0].update(operationSequence=1))
        mutate('timeout', lambda x: x['operations'][1]['toasts'][0]['events'][-1].update(elapsedNs=10_000_000_021))
        mutate('old-pid', lambda x: x.update(pid=122))
        mutate('missing-record', lambda x: x['operations'].pop())
        mutate('cancel-toast', lambda x: x['operations'][0]['toasts'].append(copy.deepcopy(x['operations'][1]['toasts'][0])))
        mutate('off-main', lambda x: x['operations'][1]['toasts'][0]['events'][0].update(mainThread=False))


if __name__ == '__main__':
    unittest.main()
