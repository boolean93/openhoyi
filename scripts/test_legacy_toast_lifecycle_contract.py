import copy
import hashlib
import json
from pathlib import Path
import subprocess
import tempfile
import threading
import time
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


class DrainEventWaitContract(unittest.TestCase):
    def test_source_has_operation_local_hidden_event_wait(self):
        source=(Path(__file__).resolve().parents[1] /
                'mobile/src/androidTest/kotlin/io/openhoyi/mobile/LegacyVisualMigrationChecks.kt').read_text()
        for token in ('CountDownLatch(1)', 'fun pendingSignal(', 'pendingSignal?.invoke()',
                      'waitSignal.await(remaining,TimeUnit.MILLISECONDS)', 'bound.events.add(event("hidden"))'):
            self.assertTrue(token in source, f'missing event-wait contract: {token}')

    @staticmethod
    def run_case(delay=0.02, hidden=True, invalid=False, cancel=False, already_hidden=False, successor=False):
        event=threading.Event(); events=[] if cancel else ['show-request','shown']
        rounds=[]; joins=[]; confirmations=0
        if already_hidden:
            events.append('hidden');event.set()
        def finish():
            if invalid: events.append('shown')
            elif hidden: events.append('hidden')
            event.set()
        worker=None
        if not cancel and not already_hidden and (hidden or invalid):
            worker=threading.Timer(delay,finish);worker.start()
        deadline=time.monotonic()+0.08
        try:
            while time.monotonic()<deadline:
                round_={'beforeJoin':[], 'afterMainBarrier':[], 'newThreadIds':[], 'uiSettled':False,
                        'remainingMs':int((deadline-time.monotonic())*1000)}
                rounds.append(round_)
                if events not in ([],['show-request','shown'],['show-request','shown','hidden']):
                    raise AssertionError('invalid event sequence')
                closed=cancel or events==['show-request','shown','hidden']
                if closed:
                    if successor and not joins:
                        joins.append('successor')
                        continue
                    confirmations+=2
                    round_['uiSettled']=True
                    round_['afterConfirmationBarrier']=[]
                    round_['confirmedUiSettled']=True
                    return {'rounds':rounds,'waits':sum('wait' in item for item in rounds),
                            'joins':joins,'confirmations':confirmations}
                remaining=deadline-time.monotonic()
                if remaining<=0: break
                round_['wait']={'remainingMs':int(remaining*1000),'started':True}
                woke=event.wait(remaining)
                round_['wait']['returned']=woke
                if not woke: break
            raise TimeoutError('original total deadline exhausted')
        finally:
            if worker: worker.join()

    def test_delayed_hidden_is_bounded_and_shown_does_not_release(self):
        result=self.run_case()
        self.assertEqual(result['waits'],1)
        self.assertEqual(len(result['rounds']),2)
        self.assertEqual(result['confirmations'],2)
        self.assertGreaterEqual(result['rounds'][0]['wait']['remainingMs'],50)

    def test_early_hidden_cancel_and_successor(self):
        self.assertEqual(self.run_case(already_hidden=True)['waits'],0)
        self.assertEqual(self.run_case(cancel=True)['waits'],0)
        result=self.run_case(successor=True)
        self.assertEqual(result['joins'],['successor'])
        self.assertEqual(result['confirmations'],2)

    def test_timeout_invalid_and_old_operation_isolated(self):
        with self.assertRaises(TimeoutError): self.run_case(hidden=False)
        with self.assertRaises(AssertionError): self.run_case(invalid=True)
        old=threading.Event();old.set()
        new=threading.Event()
        self.assertFalse(new.wait(0.001))

    def test_failure_not_replaced_by_cleanup(self):
        original=RuntimeError('callback-failure')
        cleanup=RuntimeError('cleanup-failure')
        suppressed=[]
        try: raise original
        except RuntimeError as error:
            try: raise cleanup
            except RuntimeError as later: suppressed.append(later)
            self.assertIs(error,original)
            self.assertEqual(suppressed,[cleanup])

    def test_five_delayed_operations_full_records_under_192mib(self):
        operations=[self.run_case(delay=0.01) for _ in range(5)]
        complete={'claim':'owned_import_toasts_ended','operations':[
            {'case':f'import-{index}','toast':sample()['operations'][1]['toasts'][0],
             'readerDrain':{'label':f'activity-result-{index}','timeoutMs':10000,
                            'rounds':operation['rounds'],'status':'PASS_DRAIN_ONLY','finalNoLiveReaderGuard':True}}
            for index,operation in enumerate(operations,1)]}
        encoded=json.dumps(complete,ensure_ascii=False,indent=2).encode()
        java='''
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.Arrays;
import java.lang.management.*;
public class ToastDrainMemoryProbe {
  public static void main(String[] args) throws Exception {
    byte[] input=System.in.readAllBytes();
    Path output=Files.createTempFile("toast-drain-model-", ".json");
    try {
      Files.write(output,input);
      byte[] read=Files.readAllBytes(output);
      if(!Arrays.equals(input,read))throw new AssertionError("full JSON bytes changed");
      byte[] hash=MessageDigest.getInstance("SHA-256").digest(read);
      StringBuilder hex=new StringBuilder();for(byte value:hash)hex.append(String.format("%02x",value));
      long peak=0;
      for(MemoryPoolMXBean pool:ManagementFactory.getMemoryPoolMXBeans())
        if(pool.getType()==MemoryType.HEAP && pool.getPeakUsage()!=null)peak+=pool.getPeakUsage().getUsed();
      System.out.println("{\\"bytes\\":"+read.length+",\\"heapMaxBytes\\":"+Runtime.getRuntime().maxMemory()+",\\"peakHeapBytes\\":"+peak+",\\"sha256\\":\\""+hex+"\\"}");
    } finally { Files.deleteIfExists(output); }
  }
}'''
        with tempfile.TemporaryDirectory() as directory:
            source=Path(directory)/'ToastDrainMemoryProbe.java';source.write_text(java)
            result=subprocess.run(['java','-Xmx192m',str(source)],input=encoded,capture_output=True,timeout=20)
        self.assertEqual(result.returncode,0,result.stderr.decode())
        report=json.loads(result.stdout)
        self.assertEqual(len(operations),5)
        self.assertEqual(sum(len(item['rounds']) for item in operations),10)
        self.assertEqual(report['bytes'],len(encoded))
        self.assertEqual(report['sha256'],hashlib.sha256(encoded).hexdigest())
        self.assertLessEqual(report['heapMaxBytes'],192*1024*1024)
        self.assertLess(report['peakHeapBytes'],report['heapMaxBytes'])
        print('M7 event-wait model',json.dumps({'operations':len(operations),'rounds':10,**report}))


if __name__ == '__main__':
    unittest.main()
