import json
import hashlib
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch

from ci_font_scale_snapshot_scope import Scope, ProbeError, checked_env, strict_console


ENV = {'GITHUB_ACTIONS': 'true', 'GITHUB_RUN_ID': '39422164468', 'GITHUB_RUN_ATTEMPT': '1',
       'GITHUB_JOB': 'scale-visual', 'CI_DISPOSABLE_AVD': 'font-scale-scale-39422164468-1-scale-visual',
       'SCALE_SNAPSHOT_SERIAL': 'emulator-5554'}


def completed(code=0, out='OK\n', err=''):
    return subprocess.CompletedProcess([], code, out, err)


class SnapshotScopeTests(unittest.TestCase):
    def test_ci_owner_guard(self):
        self.assertEqual(checked_env(ENV), '39422164468-1-scale-visual')
        for key, value in [('GITHUB_ACTIONS', 'false'), ('CI_DISPOSABLE_AVD', 'other'),
                           ('SCALE_SNAPSHOT_SERIAL', 'device123'), ('GITHUB_JOB', '')]:
            candidate = dict(ENV, **{key: value})
            with self.assertRaises(ProbeError):
                checked_env(candidate)

    def test_console_ack_rejects_unknown_and_whitespace_stderr(self):
        strict_console(completed())
        for result in (completed(1), completed(0, 'cannot save\nOK\n'), completed(0, 'OK\n', ' \n')):
            with self.assertRaises(ProbeError):
                strict_console(result)

    def make_scope(self, directory, baseline=None):
        scope = Scope(ENV, Path(directory) / 'scope')
        scope.output.mkdir()
        scope.owner = {'snapshot': 'ours', 'baseline': baseline or
                       {'present': False, 'value': None, 'frameworkFontScale': '1.0'}}
        scope.verify_owner = lambda require_snapshot=True: None
        scope.preserve = lambda: True
        scope.checked = lambda *args: ''
        scope.adb = lambda *args, **kwargs: completed(1, '', '') if args[0] == 'shell' else completed()
        scope.operation = lambda name: scope.operations.append({'operation': name})
        scope.state = lambda prefix: scope.owner['baseline']
        scope.names = lambda: ['ours'] if not any(x['operation'] == 'delete' for x in scope.operations) else []
        return scope

    def test_unknown_save_prevents_business_and_load(self):
        with tempfile.TemporaryDirectory() as directory:
            scope = self.make_scope(directory)
            scope.initialize = lambda: (_ for _ in ()).throw(ProbeError('save unknown'))
            with patch('ci_font_scale_snapshot_scope.subprocess.Popen') as start:
                self.assertEqual(scope.run(), 1)
                start.assert_not_called()
            self.assertEqual(scope.operations, [])

    def test_owner_identity_mismatch_and_pull_failure_zero_load(self):
        with tempfile.TemporaryDirectory() as directory:
            scope = self.make_scope(directory)
            scope.verify_owner = lambda require_snapshot=True: (_ for _ in ()).throw(ProbeError('owner mismatch'))
            self.assertEqual(scope.finish(0), 1)
            self.assertEqual(scope.operations, [])
        with tempfile.TemporaryDirectory() as directory:
            scope = self.make_scope(directory)
            scope.preserve = lambda: (_ for _ in ()).throw(ProbeError('pull/hash failure'))
            self.assertEqual(scope.finish(0), 1)
            self.assertEqual(scope.operations, [])

    def test_original_business_nonzero_survives_successful_restore(self):
        with tempfile.TemporaryDirectory() as directory:
            scope = self.make_scope(directory)
            self.assertEqual(scope.finish(7), 7)
            self.assertEqual([x['operation'] for x in scope.operations], ['load', 'delete'])
            report = json.loads(scope.report_path.read_text())
            self.assertEqual(report['businessExitStatus'], 7)
            self.assertEqual(report['restoreStatus'], 'PASS_EXACT')

    def test_absent_and_present_exact_value_branches(self):
        with tempfile.TemporaryDirectory() as directory:
            scope = self.make_scope(directory, {'present': True, 'value': '1.25', 'frameworkFontScale': '1.25'})
            self.assertEqual(scope.finish(0), 0)
        with tempfile.TemporaryDirectory() as directory:
            scope = self.make_scope(directory)
            scope.state = lambda _: {'present': True, 'value': '1.0', 'frameworkFontScale': '1.0'}
            self.assertEqual(scope.finish(0), 1)
            self.assertEqual([x['operation'] for x in scope.operations], ['load'])

    def test_framework_mismatch_and_unknown_load_do_not_delete_or_retry(self):
        with tempfile.TemporaryDirectory() as directory:
            scope = self.make_scope(directory)
            scope.state = lambda _: {'present': False, 'value': None, 'frameworkFontScale': '1.3'}
            self.assertEqual(scope.finish(0), 1)
            self.assertEqual([x['operation'] for x in scope.operations], ['load'])
        with tempfile.TemporaryDirectory() as directory:
            scope = self.make_scope(directory)
            def operation(name):
                scope.operations.append({'operation': name})
                if name == 'load':
                    raise ProbeError('load unknown')
            scope.operation = operation
            self.assertEqual(scope.finish(5), 5)
            self.assertEqual([x['operation'] for x in scope.operations], ['load'])

    def test_business_cleanup_error_not_overwritten(self):
        with tempfile.TemporaryDirectory() as directory:
            scope = self.make_scope(directory)
            scope.preserve = lambda: (_ for _ in ()).throw(ProbeError('evidence not complete'))
            self.assertEqual(scope.finish(9), 9)
            report = json.loads(scope.report_path.read_text())
            self.assertEqual(report['businessExitStatus'], 9)
            self.assertIn('evidence not complete', report['errors'])

    def test_real_preserve_checks_pulled_manifest_and_device_hash(self):
        import ci_font_scale_snapshot_scope as module
        source = 'a' * 40
        payload = b'actual geometry\n'
        sha = hashlib.sha256(payload).hexdigest()
        with tempfile.TemporaryDirectory() as directory:
            business = Path(directory) / 'business'
            output = business / 'snapshot-scope'
            output.mkdir(parents=True)
            scope = Scope(ENV, output)
            scope.owner = {'sourceSha': source, 'scopeUuid': 'owned'}
            remote = f'/sdcard/Android/data/io.openhoyi.mobile.mock/files/scale-visual-migration/{source}'
            def adb(*args, **_kwargs):
                if args[0] == 'shell':
                    return completed(0, '', '')
                if args[0] == 'pull':
                    root = output / 'pulled-business' / source / 'profile' / 'scale'
                    root.mkdir(parents=True)
                    (root / 'geometry.json').write_bytes(payload)
                    (root / 'manifest.json').write_text(json.dumps({
                        'sourceSha': source, 'fileSha256': {'geometry.json': sha}}))
                    return completed(0, '', '')
                raise AssertionError(args)
            scope.adb = adb
            scope.shell = lambda _command: sha + '  ' + remote + '/profile/scale/geometry.json\n' + \
                hashlib.sha256(json.dumps({'sourceSha': source, 'fileSha256': {'geometry.json': sha}}).encode()).hexdigest() + \
                '  ' + remote + '/profile/scale/manifest.json\n'
            with patch.object(module, 'ROOT', business):
                self.assertTrue(scope.preserve())
                self.assertTrue((output / 'evidence-preserved.json').is_file())
            # Same on-device/local file hashes alone are insufficient if a manifest lies.
            (output / 'pulled-business').rename(output / 'old-pull')
            scope.shell = lambda _command: sha + '  ' + remote + '/profile/scale/geometry.json\n' + \
                hashlib.sha256(json.dumps({'sourceSha': source, 'fileSha256': {'geometry.json': '0'*64}}).encode()).hexdigest() + \
                '  ' + remote + '/profile/scale/manifest.json\n'
            def bad_pull(*args, **kwargs):
                result = adb(*args, **kwargs)
                if args[0] == 'pull':
                    manifest = output / 'pulled-business' / source / 'profile' / 'scale' / 'manifest.json'
                    manifest.write_text(json.dumps({'sourceSha': source, 'fileSha256': {'geometry.json': '0'*64}}))
                return result
            scope.adb = bad_pull
            with patch.object(module, 'ROOT', business), self.assertRaises(ProbeError):
                scope.preserve()

    def test_final_report_failure_preserves_business_nonzero(self):
        with tempfile.TemporaryDirectory() as directory:
            scope = self.make_scope(directory)
            with patch('ci_font_scale_snapshot_scope.durable_json', side_effect=OSError('disk full')):
                self.assertEqual(scope.finish(7), 7)

    def test_unquiesced_collector_cannot_load(self):
        with tempfile.TemporaryDirectory() as directory:
            scope = self.make_scope(directory)
            self.assertEqual(scope.finish(9, quiescent=False), 9)
            self.assertEqual(scope.operations, [])

    def test_business_exit_is_durable_before_finish(self):
        with tempfile.TemporaryDirectory() as directory:
            scope = self.make_scope(directory)
            scope.owner.update({'scopeUuid': 'owned', 'sourceSha': 'a'*40})
            scope.initialize = lambda: True
            class Child:
                pid = 999999
                def wait(self, timeout=None):
                    return 7
            def finish(code, quiescent=True):
                recorded = json.loads((scope.output / 'business-exit.json').read_text())
                self.assertEqual(recorded['businessExitStatus'], 7)
                self.assertTrue(recorded['processGroupQuiescent'])
                return code
            scope.finish = finish
            with patch('ci_font_scale_snapshot_scope.subprocess.Popen', return_value=Child()):
                self.assertEqual(scope.run(), 7)

    def test_two_process_scope_instances_never_overwrite_identity_or_list_records(self):
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory) / 'scope'
            output.mkdir()
            first, second = Scope(ENV, output), Scope(ENV, output)
            def adb(*args, **_kwargs):
                if args == ('emu', 'avd', 'snapshot', 'list'):
                    return completed(0, 'There is no snapshot available.\nOK\n', '')
                if args == ('emu', 'avd', 'name'):
                    return completed(0, ENV['CI_DISPOSABLE_AVD'] + '\nOK\n', '')
                if args == ('shell', 'getprop ro.kernel.qemu'):
                    return completed(0, '1\n', '')
                if args[0] == 'shell' and args[1].startswith('getprop '):
                    prop = args[1].split()[-1]
                    return completed(0, ('34' if prop == 'ro.build.version.sdk' else 'owned') + '\n', '')
                raise AssertionError(args)
            first.adb = second.adb = adb
            inventory = completed(0, 'List of devices attached\nemulator-5554\tdevice\n', '')
            with patch('ci_font_scale_snapshot_scope.subprocess.run', return_value=inventory):
                for scope in (first, second):
                    self.assertEqual(scope.names(), [])
                    self.assertEqual(scope.identity()['ro.build.version.sdk'], '34')
            self.assertEqual(len(list((output / 'snapshot-lists').glob('*.json'))), 2)
            self.assertEqual(len(list((output / 'identity-checks').glob('*.json'))), 2)


if __name__ == '__main__':
    unittest.main()
