"""Offline failures for fixed three-page shards and strict twelve-cell aggregation."""
import ast
import copy
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch

import settings_matrix_isolation as matrix
import settings_shard_gate as gate
import test_settings_matrix_isolation as legacy

SOURCE, APK, arguments = legacy.SOURCE, legacy.APK, legacy.arguments

CONTEXT = {'sourceSha': SOURCE, 'headSha': 'c'*40, 'ciRun': '123',
           'ciRunAttempt': '2', 'repository': 'owner/repo'}
IDENTITY = dict(CONTEXT, schema=1, apkSha256=APK, testApkSha256='d'*64)


def save(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2)+'\n')


class ScopeContract(unittest.TestCase):
    def harness(self, scope):
        helper = legacy.GateTest(methodName='runTest');helper.setUp();self.addCleanup(helper.doCleanups)
        data = matrix.load(helper.root)
        data.update(scope=scope, headSha=CONTEXT['headSha'], ciRunAttempt='2', testApkSha256=None,
                    cells=matrix.expected(SOURCE, scope))
        matrix.write(matrix.ledger_path(helper.root), data)
        return helper

    def test_full_is_twelve_and_each_fixed_scope_is_exact_three_in_original_order(self):
        self.assertEqual(len(matrix.expected(SOURCE)), 12)
        for scope in matrix.SHARDS:
            cells = matrix.expected(SOURCE, scope)
            self.assertEqual([c['page'] for c in cells], list(matrix.PAGES))
            self.assertTrue(all(f"{c['theme']}-{c['fontScale']}" == scope for c in cells))
        for invalid in ('light', 'full-app', 'dark-2.0', ''):
            with self.assertRaises(matrix.Unsafe):
                matrix.expected(SOURCE, invalid)

    def test_shard_ledger_rejects_missing_extra_wrong_profile_and_reordering(self):
        helper = self.harness('light-1.3')
        original = matrix.load(helper.root)
        changes = [lambda d: d['cells'].pop(),
                   lambda d: d['cells'].append(copy.deepcopy(d['cells'][0])),
                   lambda d: d['cells'].reverse(),
                   lambda d: d['cells'][0].update(profile='tablet-user-landscape-dark-font-1.3'),
                   lambda d: d.update(scope='full')]
        for change in changes:
            candidate = copy.deepcopy(original);change(candidate)
            matrix.write(matrix.ledger_path(helper.root), candidate)
            with self.assertRaises(matrix.Unsafe):
                matrix.load(helper.root)

    def test_safe_business_failure_continues_only_this_shard_and_preserves_first_exit(self):
        helper = self.harness('dark-1.3')
        for index, cell in enumerate(matrix.expected(SOURCE, 'dark-1.3')):
            helper.start(cell)
            manifest = helper.fixture(cell)
            if cell['page'] == 'machine':
                path = matrix.profile_dir(helper.root, cell) / 'fixture-isolation.json'
                isolation = gate.read(path);isolation.update({key: True for key in matrix.MACHINE_TRUE});save(path, isolation)
            manifest.update(headSha=CONTEXT['headSha'], ciRunAttempt='2', testApkSha256=None, sourceDirty='false', pid=700+index)
            self.assertEqual(helper.after(cell, helper.result(cell), shell_exit=7+index, manifest=manifest), 0)
        data = matrix.load(helper.root)
        self.assertEqual(data['firstFailureExitStatus'], 7)
        self.assertEqual([c['status'] for c in data['cells']], ['FAILED_ISOLATED_CONTINUED']*3)
        self.assertEqual(matrix.finalize(arguments(output=helper.root, verify_exit=1)), 7)

    def test_unknown_isolation_stops_only_remaining_real_three_cells(self):
        helper = self.harness('light-1.0');cell = matrix.expected(SOURCE, 'light-1.0')[0]
        helper.start(cell)
        manifest = helper.fixture(cell, broken='typedPreferencesRestored')
        manifest.update(headSha=CONTEXT['headSha'], ciRunAttempt='2', testApkSha256=None, sourceDirty='false')
        self.assertEqual(helper.after(cell, helper.result(cell), manifest=manifest), 1)
        self.assertEqual([c['status'] for c in matrix.load(helper.root)['cells']],
                         ['FAILED_UNSAFE_STOP', 'NOT_RUN_UNSAFE_PREDECESSOR', 'NOT_RUN_UNSAFE_PREDECESSOR'])

    def test_shard_requires_head_attempt_and_test_apk_at_init(self):
        with tempfile.TemporaryDirectory() as directory:
            with self.assertRaises(matrix.Unsafe):
                matrix.init(arguments(output=directory, source=SOURCE, apk=APK, scope='dark-1.3'))


class ReceiptContract(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory();self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.bundle = self.root / 'bundle';self.bundle.mkdir()
        save(self.bundle / 'build-identity.json', IDENTITY)

    def original(self, scope):
        evidence = self.root / 'evidence' / scope;evidence.mkdir(parents=True)
        cells = matrix.expected(SOURCE, scope)
        for index, cell in enumerate(cells):
            cell.update(status=gate.PASS, originalSuccessGates='PASS_ORIGINAL_FULL_VERIFY',
                        originalShellExitStatus=0, originalAdbExitStatus=0,
                        successMarker=True, manifestPid=700+index)
            folder = evidence / cell['sourceSha'] / cell['profile'] / cell['page']
            files = {}
            for name in ('fixture-isolation.json', 'fixture.json', 'window.png', 'window-frame-sync.json',
                         'geometry.json', 'special-states/manifest.json'):
                target = folder / name;target.parent.mkdir(parents=True, exist_ok=True)
                target.write_bytes(('synthetic fullverify already checked '+name).encode())
                files[name] = gate.sha(target)
            manifest = dict(IDENTITY, page=cell['page'], profile=cell['profile'], pid=700+index,
                            ciJob='settings-cell', sourceDirty='false', overallStatus=gate.PASS,
                            isolationStatus='PASS_ISOLATION_ONLY', fileSha256=files)
            save(folder / 'manifest.json', manifest)
            cell['evidenceFileCount'] = len(files)+1
        device = {'serial': 'emulator-5554', 'sourceSha': SOURCE, 'apkSha256': APK,
                  'properties': {'ro.kernel.qemu': '1', 'ro.build.version.sdk': '34'}}
        ledger = {key: value for key, value in IDENTITY.items() if key != 'repository'}
        ledger.update(scope=scope, ciJob='settings-cell', deviceIdentity=device, cells=cells,
                      firstFailureExitStatus=None, unsafeStop=None, fullVerifyExitStatus=0)
        save(evidence / 'matrix-ledger.json', ledger)
        save(evidence / 'matrix-verification.json', {'results': [
            {'page': c['page'], 'profile': c['profile'], 'status': 'PASS_ORIGINAL_FULL_VERIFY'}
            for c in cells], 'errors': []})
        before = {'present': False, 'value': None}
        save(evidence / 'font-scale-before.json', before)
        save(evidence / 'font-scale-restore.json', {'status': 'PASS_SETTING_RESTORE_ONLY', 'before': before, 'after': before})
        save(evidence / 'runner-exit.json', {'exitStatus': 0})
        return evidence

    def make_gate(self, scope, *, mutate=None, artifact=None, capture='success', upload='success'):
        evidence = self.original(scope)
        if mutate:
            mutate(evidence)
        directory = self.root / 'gates' / scope
        gate.freeze(evidence, directory, CONTEXT, scope)
        ordinal = str(100+matrix.SHARDS.index(scope))
        artifact = artifact or {'id': ordinal, 'digest': 'e'*64,
            'url': f'https://github.com/owner/repo/actions/runs/123/artifacts/{ordinal}'}
        result = gate.receipt(evidence, directory, self.bundle, CONTEXT, scope, artifact, capture, upload)
        return result, evidence, directory

    def all_gates(self):
        for scope in matrix.SHARDS:
            self.assertEqual(self.make_gate(scope)[0], 0)

    def aggregate(self, build='success', workers='success'):
        return gate.aggregate(self.root / 'gates', self.root / 'aggregate', CONTEXT, build, workers)

    def test_four_exact_scopes_same_batch_twelve_cells_pass_even_same_pid_on_different_avds(self):
        self.all_gates();self.assertEqual(self.aggregate(), 0)
        report = gate.read(self.root / 'aggregate/matrix-aggregate.json')
        self.assertEqual(len(report['cells']), 12)
        self.assertEqual(len({c['manifestPid'] for c in report['cells']}), 3)

    def test_missing_duplicate_scope_and_dependency_failures_are_red(self):
        self.all_gates()
        for state in ('failure', 'cancelled', 'skipped', ''):
            self.assertEqual(self.aggregate(workers=state), 1)
            self.assertEqual(self.aggregate(build=state), 1)
        shutil.move(self.root / 'gates/dark-1.0', self.root / 'gates/extra-scope')
        self.assertEqual(self.aggregate(), 1)

    def test_failed_worker_receipt_and_original_reason_survive_incomplete_gate(self):
        for scope in matrix.SHARDS:
            self.make_gate(scope, capture='failure' if scope == 'dark-1.3' else 'success')
        missing = self.root / 'gates/dark-1.3/cell-preservation.json'
        missing.unlink()
        self.assertEqual(self.aggregate(workers='failure'), 1)
        report = gate.read(self.root / 'aggregate/matrix-aggregate.json')
        failed = [receipt for receipt in report['receipts'] if receipt['scope'] == 'dark-1.3']
        self.assertEqual(len(failed), 1)
        self.assertEqual(failed[0]['status'], 'FAIL')
        self.assertIn('capture action failed/cancelled/unknown', failed[0]['errors'])

    def test_changed_uploaded_original_or_inventory_never_gets_green_receipt(self):
        _, evidence, directory = self.make_gate('light-1.3')
        target = evidence / 'runner-exit.json';target.chmod(0o644)
        save(target, {'exitStatus': 9});target.chmod(0o444)
        artifact = gate.read(directory / 'receipt.json')['evidenceArtifact']
        self.assertEqual(gate.receipt(evidence, directory, self.bundle, CONTEXT, 'light-1.3', artifact, 'success', 'success'), 1)

    def test_capture_upload_reference_missing_or_failed_is_red(self):
        for scope, kwargs in zip(matrix.SHARDS, [dict(capture='failure'), dict(upload='failure'),
                dict(artifact={'id': '', 'digest': '', 'url': ''}),
                dict(artifact={'id': '103', 'digest': 'e'*64,
                               'url': 'https://github.com/other/repo/actions/runs/123/artifacts/103'})]):
            self.assertEqual(self.make_gate(scope, **kwargs)[0], 1)

    def test_original_failure_restore_reinsert_and_fullverify_incomplete_are_red(self):
        def change_file(name, edit):
            def mutate(root):
                value = gate.read(root / name);edit(value);save(root / name, value)
            return mutate
        changes = [
            change_file('matrix-ledger.json', lambda d: d.update(firstFailureExitStatus=7)),
            change_file('font-scale-restore.json', lambda d: d.update(status='FAIL', after={'present': True, 'value': '1.0'})),
            change_file('matrix-verification.json', lambda d: d['results'].pop()),
            change_file('matrix-ledger.json', lambda d: d['cells'][1].update(manifestPid=d['cells'][0]['manifestPid'])),
        ]
        for scope, change in zip(matrix.SHARDS, changes):
            self.assertEqual(self.make_gate(scope, mutate=change)[0], 1)

    def test_deleted_cell_manifest_listed_file_or_false_count_is_rejected_before_receipt(self):
        def folder(root):
            return root / SOURCE / ('tablet-user-landscape-'+root.name.replace('-', '-font-', 1)) / 'app'
        changes = [lambda root: shutil.rmtree(folder(root)),
                   lambda root: (folder(root) / 'manifest.json').unlink(),
                   lambda root: (folder(root) / 'window.png').unlink(),
                   lambda root: self.change_count(root)]
        for scope, change in zip(matrix.SHARDS, changes):
            self.assertEqual(self.make_gate(scope, mutate=change)[0], 1)

    @staticmethod
    def change_count(root):
        data = gate.read(root / 'matrix-ledger.json')
        data['cells'][0]['evidenceFileCount'] += 1
        save(root / 'matrix-ledger.json', data)

    def test_mixed_source_head_run_attempt_apk_or_gate_record_mutation_is_red(self):
        self.all_gates();directory = self.root / 'gates/light-1.3'
        original = gate.read(directory / 'receipt.json')
        for key in ('sourceSha', 'headSha', 'ciRun', 'ciRunAttempt', 'scope'):
            value = copy.deepcopy(original);value[key] = 'wrong';save(directory / 'receipt.json', value)
            self.assertEqual(self.aggregate(), 1)
        save(directory / 'receipt.json', original)
        identity = gate.read(directory / 'build-identity.json');identity['testApkSha256'] = 'f'*64
        save(directory / 'build-identity.json', identity)
        self.assertEqual(self.aggregate(), 1)

    def test_duplicate_raw_artifact_id_and_changed_inventory_are_red(self):
        self.all_gates()
        directory = self.root / 'gates/light-1.3'
        proof = gate.read(directory / 'receipt.json')
        proof['evidenceArtifact'] = gate.read(self.root / 'gates/light-1.0/receipt.json')['evidenceArtifact']
        save(directory / 'receipt.json', proof)
        self.assertEqual(self.aggregate(), 1)
        rows = gate.read(directory / 'inventory.json');rows[0]['sha256'] = '0'*64
        save(directory / 'inventory.json', rows)
        self.assertEqual(self.aggregate(), 1)

    def test_gate_must_be_external_and_inventory_paths_safe(self):
        evidence = self.original('light-1.3')
        with self.assertRaises(matrix.Unsafe):
            gate.freeze(evidence, evidence / 'gate', CONTEXT, 'light-1.3')
        for name in ('../secret', '/secret', 'a/../../secret', '.hidden', 'a//b'):
            with self.assertRaises(matrix.Unsafe):
                gate.validate_inventory([{'path': name, 'bytes': 1, 'sha256': 'e'*64}])
        (evidence / 'link').symlink_to(evidence / 'runner-exit.json')
        with self.assertRaises(matrix.Unsafe):
            gate.inventory(evidence)

    def test_build_pair_hashes_and_run_identity_are_checked_before_copy(self):
        producer = self.root / 'producer';worker = self.root / 'worker'
        for key, (_, path) in gate.APKS.items():
            target = producer / path;target.parent.mkdir(parents=True, exist_ok=True);target.write_bytes(key.encode())
        bundle = self.root / 'real-bundle';gate.build(bundle, CONTEXT, producer)
        gate.verify_build(bundle, CONTEXT, worker)
        for key, (_, path) in gate.APKS.items():
            self.assertEqual(gate.sha(producer / path), gate.sha(worker / path))
        other = dict(CONTEXT, ciRunAttempt='1')
        with self.assertRaises(matrix.Unsafe):
            gate.verify_build(bundle, other, self.root / 'old-attempt')
        original_identity = gate.read(bundle / 'build-identity.json')
        for key in gate.CONTEXT:
            candidate = dict(original_identity);candidate[key] = 'wrong'
            with self.subTest(identity=key), self.assertRaises(matrix.Unsafe):
                gate.require_build(candidate, CONTEXT)
        for key in gate.APKS:
            candidate = dict(original_identity);candidate[key] = 'missing'
            with self.subTest(hash=key), self.assertRaises(matrix.Unsafe):
                gate.require_build(candidate, CONTEXT)
        (bundle / 'test.apk').write_bytes(b'changed')
        with self.assertRaises(matrix.Unsafe):
            gate.verify_build(bundle, CONTEXT, self.root / 'bad-apk')


class RestoreContract(unittest.TestCase):
    def test_actual_restore_body_rejects_default_key_reinsertion_and_keeps_original_failure(self):
        shell = Path(__file__).with_name('check_settings_visual_migration.sh').read_text()
        source = shell.split("<<'FONT_RESTORE_PY'\n", 1)[1].split('\nFONT_RESTORE_PY', 1)[0]
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            before = root / 'before.json';result = root / 'restore.json'
            save(before, {'present': False, 'value': None})
            calls = []
            def adb(command, **kwargs):
                calls.append(command)
                output = 'font_scale=1.0\n' if command[-3:] == ['list', 'system'] or 'list' in command else ''
                return subprocess.CompletedProcess(command, 0, output, '')
            with patch.object(sys, 'argv', ['restore', str(before), str(result), '7']), \
                    patch('subprocess.run', side_effect=adb), patch('time.sleep'):
                with self.assertRaisesRegex(AssertionError, 'presence/value not restored'):
                    exec(compile(source, 'FONT_RESTORE_PY', 'exec'), {})
            proof = gate.read(result)
            self.assertEqual(proof['status'], 'FAIL')
            self.assertEqual(proof['originalExitStatus'], 7)
            self.assertEqual(len(proof['restoreAttempts']), 5)
            self.assertEqual(proof['after'], {'present': True, 'value': '1.0'})
            self.assertTrue(all('delete' in command for command in calls if 'list' not in command))

    def test_actual_restore_body_preserves_originally_absent_or_exact_present_value(self):
        shell = Path(__file__).with_name('check_settings_visual_migration.sh').read_text()
        source = shell.split("<<'FONT_RESTORE_PY'\n", 1)[1].split('\nFONT_RESTORE_PY', 1)[0]
        for snapshot in ({'present': False, 'value': None}, {'present': True, 'value': '1.3'}):
            with self.subTest(snapshot=snapshot), tempfile.TemporaryDirectory() as directory:
                root = Path(directory);save(root / 'before.json', snapshot)
                calls = []
                def adb(command, **kwargs):
                    calls.append(command)
                    output = ('font_scale='+snapshot['value']+'\n') if snapshot['present'] and 'list' in command else ''
                    return subprocess.CompletedProcess(command, 0, output, '')
                with patch.object(sys, 'argv', ['restore', str(root / 'before.json'), str(root / 'result.json'), '0']), \
                        patch('subprocess.run', side_effect=adb), patch('time.sleep'):
                    exec(compile(source, 'FONT_RESTORE_PY', 'exec'), {})
                proof = gate.read(root / 'result.json')
                self.assertEqual(proof['status'], 'PASS_SETTING_RESTORE_ONLY')
                self.assertEqual(proof['after'], snapshot)
                if snapshot['present']:
                    self.assertEqual(calls[0][-4:], ['put', 'system', 'font_scale', '1.3'])
                else:
                    self.assertEqual(calls[0][-3:], ['delete', 'system', 'font_scale'])


class WorkflowContract(unittest.TestCase):
    def test_workflow_keeps_original_trigger_check_and_budgets_with_fixed_shards(self):
        workflow = (Path(__file__).resolve().parents[1] / '.github/workflows/settings-visual-ui.yml').read_text()
        for text in ('pull_request:\n    branches: [develop]', 'workflow_dispatch:',
                     'contents: read', 'cancel-in-progress:', 'fail-fast: false',
                     'scope: [light-1.3, light-1.0, dark-1.3, dark-1.0]',
                     '  settings-visual:\n    needs: [settings-build, settings-cell]\n    if: always()',
                     'compression-level: 6', 'disk-size: 16G'):
            self.assertIn(text, workflow)
        self.assertEqual(workflow.count('timeout-minutes: 45'), 3)
        self.assertEqual(workflow.count('assembleMock :mobile:assembleMockAndroidTest'), 1)
        self.assertNotIn('continue-on-error', workflow)
        self.assertNotIn('compression-level: 0', workflow)
        shell = (Path(__file__).with_name('check_settings_visual_migration.sh')).read_text()
        self.assertIn('timeout=600', shell)
        self.assertIn("proof['timeoutMs']==5000", shell)
        self.assertIn('for attempt in range(5 if not before[\'present\'] else 1)', shell)


if __name__ == '__main__':
    unittest.main()
