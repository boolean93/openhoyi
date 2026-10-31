#!/usr/bin/env python3
"""Offline contract tests for the existing M8 matrix's fail-closed host gate."""
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch

import settings_matrix_isolation as gate

SOURCE = 'a' * 40
APK = 'b' * 64
IDENTITY = {'serial': 'emulator-5554', 'properties': {'ro.kernel.qemu': '1'}, 'sourceSha': SOURCE, 'apkSha256': APK}


def arguments(**values):
    return type('Args', (), values)()


class GateTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.env = patch.dict('os.environ', {'GITHUB_RUN_ID': '123', 'GITHUB_JOB': 'settings-visual'})
        self.env.start(); self.addCleanup(self.env.stop)
        self.identity = patch.object(gate, 'identity', return_value=IDENTITY)
        self.identity.start(); self.addCleanup(self.identity.stop)
        gate.init(arguments(output=self.root, source=SOURCE, apk=APK))
        self.first = gate.expected(SOURCE)[0]
        self.next = gate.expected(SOURCE)[1]

    def args_for(self, cell):
        return dict(output=self.root, page=cell['page'], theme=cell['theme'], scale=cell['fontScale'])

    def start(self, cell):
        with patch.object(gate, 'adb', return_value=subprocess.CompletedProcess([], 1, '', '')):
            gate.before(arguments(**self.args_for(cell)))

    def fixture(self, cell, *, broken=None):
        folder = gate.profile_dir(self.root, cell)
        folder.mkdir(parents=True)
        iso = {key: True for key in gate.COMMON_TRUE}
        iso.update({'status': 'PASS_ISOLATION_ONLY', 'noServiceWasStartedForThisPage': True,
                    'ownedAsyncReaderCount': 0, 'activityDestroyedCount': 1,
                    'clipboard': 'NOT_READ_OR_WRITTEN', 'providerLifecycle': [],
                    'expectedActualExportFailures': 0,
                    'scopedTraceStatusAfterTermination': 'closed dropped=0 errors=0 retentionEvictedFiles=0'})
        if broken:
            iso.pop(broken)
        (folder / 'fixture-isolation.json').write_text(json.dumps(iso))
        manifest = {'isolationStatus': 'PASS_ISOLATION_ONLY', 'overallStatus': 'FAIL',
                    'sourceSha': SOURCE, 'profile': cell['profile'], 'page': cell['page'],
                    'ciRun': '123', 'ciJob': 'settings-visual', 'apkSha256': APK, 'pid': 777}
        (folder / 'manifest.json').write_text(json.dumps(manifest))
        return manifest

    def result(self, cell, *, marker=False, rc=0):
        name = self.root / f"{cell['theme']}-{cell['fontScale']}-{cell['page']}-result.txt"
        name.write_text('original instrumentation stdout/stderr')
        name.with_suffix('.run.json').write_text(json.dumps({'adbReturnCode': rc, 'successMarker': marker}))
        name.with_suffix('.fresh-process.json').write_text(json.dumps({
            'freshProcessVerified': True, 'adbReturnCode': 0,
            'stdout': 'FRESH_PIDOF_STATUS=1\nFRESH_PIDOF_OUTPUT=\n', 'stderr': ''}))
        return name

    def after(self, cell, result, shell_exit=7, manifest=None):
        if manifest is None:
            manifest = json.loads((gate.profile_dir(self.root, cell) / 'manifest.json').read_text())
        with patch.object(gate, 'preserve', return_value=(manifest, {'manifest.json': 'a'})):
            return gate.after(arguments(**self.args_for(cell), result=result, exit_status=shell_exit))

    def test_failed_isolated_cell_allows_exactly_next_and_keeps_first_code(self):
        self.start(self.first)
        self.fixture(self.first)
        self.assertEqual(0, self.after(self.first, self.result(self.first)))
        self.start(self.next)
        data = gate.load(self.root)
        self.assertEqual('FAILED_ISOLATED_CONTINUED', data['cells'][0]['status'])
        self.assertEqual('NOT_REACHED_ORIGINAL_FAILURE', data['cells'][0]['originalSuccessGates'])
        self.assertEqual('STARTED', data['cells'][1]['status'])
        self.assertEqual(7, data['firstFailureExitStatus'])
        with self.assertRaises(gate.Unsafe):
            gate.finalize(arguments(output=self.root, verify_exit=0))

    def test_missing_isolation_field_stops_remaining(self):
        self.start(self.first)
        self.fixture(self.first, broken='mainQueueBarrierAfterOwners')
        self.assertEqual(1, self.after(self.first, self.result(self.first)))
        data = gate.load(self.root)
        self.assertEqual('FAILED_UNSAFE_STOP', data['cells'][0]['status'])
        self.assertTrue(all(c['status'] == 'NOT_RUN_UNSAFE_PREDECESSOR' for c in data['cells'][1:]))
        self.assertEqual(7, gate.finalize(arguments(output=self.root, verify_exit=1)))

    def test_false_isolation_and_wrong_source_stop(self):
        for corruption in ('false', 'source'):
            with self.subTest(corruption=corruption):
                root = self.root / corruption
                gate.init(arguments(output=root, source=SOURCE, apk=APK))
                cell = gate.expected(SOURCE)[0]
                with patch.object(gate, 'adb', return_value=subprocess.CompletedProcess([], 1, '', '')):
                    gate.before(arguments(output=root, page='app', theme='light', scale='1.3'))
                old = self.root
                self.root = root
                try:
                    manifest = self.fixture(cell)
                    if corruption == 'false':
                        path = gate.profile_dir(root, cell) / 'fixture-isolation.json'
                        iso = json.loads(path.read_text()); iso['allOwnedCallbacksDrained'] = False
                        path.write_text(json.dumps(iso))
                    else:
                        manifest['sourceSha'] = 'c' * 40
                    self.assertEqual(1, self.after(cell, self.result(cell), manifest=manifest))
                    self.assertEqual('FAILED_UNSAFE_STOP', gate.load(root)['cells'][0]['status'])
                finally:
                    self.root = old

    def test_preexisting_remote_manifest_forbids_replay(self):
        with patch.object(gate, 'adb', return_value=subprocess.CompletedProcess([], 0, '', '')):
            with self.assertRaises(gate.Unsafe):
                gate.before(arguments(**self.args_for(self.first)))
        self.assertEqual('FAILED_UNSAFE_STOP', gate.load(self.root)['cells'][0]['status'])

    def test_remote_hash_mismatch_forbids_continue(self):
        cell = self.first
        remote = gate.remote_dir(cell)
        target = gate.profile_dir(self.root, cell)
        def fake_adb(*parts, **_kwargs):
            if 'find ' in ' '.join(parts):
                return subprocess.CompletedProcess([], 0, '0'*64 + '  ' + remote + '/manifest.json\n' + '0'*64 + '  ' + remote + '/fixture-isolation.json\n', '')
            if 'pull' in parts:
                target.mkdir(parents=True)
                (target / 'manifest.json').write_text('{}')
            return subprocess.CompletedProcess([], 0, '', '')
        with patch.object(gate, 'adb', side_effect=fake_adb):
            with self.assertRaisesRegex(gate.Unsafe, 'hash'):
                gate.preserve(self.root, cell)

    def test_pull_failure_and_old_pid_stop(self):
        self.start(self.first)
        self.fixture(self.first)
        self.assertEqual(0, self.after(self.first, self.result(self.first)))
        self.start(self.next)
        self.fixture(self.next)
        self.assertEqual(1, self.after(self.next, self.result(self.next)))
        self.assertIn('old process PID', gate.load(self.root)['cells'][1]['stopReason'])

        cell = self.first
        remote = gate.remote_dir(cell)
        def fake_adb(*parts, **_kwargs):
            if 'find ' in ' '.join(parts):
                listing = '0'*64 + '  ' + remote + '/manifest.json\n' + '0'*64 + '  ' + remote + '/fixture-isolation.json\n'
                return subprocess.CompletedProcess([], 0, listing, '')
            if 'pull' in parts:
                return subprocess.CompletedProcess([], 1, '', 'transport failure')
            return subprocess.CompletedProcess([], 0, '', '')
        with patch.object(gate, 'adb', side_effect=fake_adb):
            with self.assertRaisesRegex(gate.Unsafe, 'pull failed'):
                gate.preserve(self.root / 'new-output', cell)

    def test_abort_keeps_first_business_failure_and_marks_unrun(self):
        self.start(self.first)
        self.fixture(self.first)
        self.assertEqual(0, self.after(self.first, self.result(self.first)))
        self.start(self.next)
        gate.abort(arguments(output=self.root, exit_status=9))
        data = gate.load(self.root)
        self.assertEqual(7, data['firstFailureExitStatus'])
        self.assertEqual('FAILED_UNSAFE_STOP', data['cells'][1]['status'])
        self.assertTrue(all(c['status'] == 'NOT_RUN_UNSAFE_PREDECESSOR' for c in data['cells'][2:]))

    def test_full_verify_report_keeps_first_failure_and_fixed_set(self):
        data = gate.load(self.root)
        data['cells'][0]['status'] = 'FAILED_ISOLATED_CONTINUED'
        data['cells'][0]['originalShellExitStatus'] = 7
        data['firstFailureExitStatus'] = 7
        for cell in data['cells'][1:]:
            cell['status'] = 'PASS_CAPTURED_PENDING_FULL_VERIFY'
        gate.write(gate.ledger_path(self.root), data)
        results = [{'page': cell['page'], 'profile': cell['profile'],
                    'status': 'PASS_ORIGINAL_FULL_VERIFY'} for cell in data['cells'][1:]]
        (self.root / 'matrix-verification.json').write_text(json.dumps({'results': results, 'errors': []}))
        self.assertEqual(7, gate.finalize(arguments(output=self.root, verify_exit=1)))
        final = gate.load(self.root)
        self.assertEqual('FAILED_ISOLATED_CONTINUED', final['cells'][0]['status'])
        self.assertTrue(all(c['status'] == 'PASS_MECHANICAL_ONLY_NOT_VISUAL_ACCEPTANCE' for c in final['cells'][1:]))

    def test_all_twelve_verified_can_pass_only_with_all_twelve_results(self):
        data = gate.load(self.root)
        for cell in data['cells']:
            cell['status'] = 'PASS_CAPTURED_PENDING_FULL_VERIFY'
        gate.write(gate.ledger_path(self.root), data)
        results = [{'page': cell['page'], 'profile': cell['profile'],
                    'status': 'PASS_ORIGINAL_FULL_VERIFY'} for cell in data['cells']]
        (self.root / 'matrix-verification.json').write_text(json.dumps({'results': results, 'errors': []}))
        self.assertEqual(0, gate.finalize(arguments(output=self.root, verify_exit=0)))

    def test_shell_exit_selection_keeps_first_seven_over_later_nine(self):
        data = gate.load(self.root)
        data['firstFailureExitStatus'] = 7
        gate.write(gate.ledger_path(self.root), data)
        helper = Path(__file__).with_name('settings_matrix_isolation.py')
        selection = subprocess.run(
            ['bash', '-c', 'selected_exit=$(python3 "$HELPER" select-exit --output "$OUT" --fallback 9); '
             'if [[ "$selected_exit" =~ ^([1-9]|[1-9][0-9]|1[0-9][0-9]|2[0-4][0-9]|25[0-5])$ ]]; '
             'then exit_status=$selected_exit; else exit_status=9; fi; exit "$exit_status"'],
            env={'PATH': os.environ['PATH'], 'HELPER': str(helper), 'OUT': str(self.root)},
            capture_output=True, text=True)
        self.assertEqual(7, selection.returncode)
        gate.ledger_path(self.root).write_text('{invalid')
        fallback = subprocess.run([sys.executable, str(helper), 'select-exit',
                                   '--output', str(self.root), '--fallback', '9'],
                                  capture_output=True, text=True, check=True)
        self.assertEqual('9', fallback.stdout.strip())

    def test_original_success_with_missing_cells_never_passes(self):
        self.start(self.first)
        self.fixture(self.first)
        self.assertEqual(0, self.after(self.first, self.result(self.first)))
        with self.assertRaises(gate.Unsafe):
            gate.finalize(arguments(output=self.root, verify_exit=0))


if __name__ == '__main__':
    unittest.main()
