"""Offline contract checks for the fixed Bean snapshot-scope adapter."""
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch

from ci_font_scale_snapshot_scope import PROFILES, ProbeError, Scope, checked_env, profile_config, verify


SCALE_ENV = {'GITHUB_ACTIONS': 'true', 'GITHUB_RUN_ID': '4001', 'GITHUB_RUN_ATTEMPT': '1',
             'GITHUB_JOB': 'scale-visual', 'CI_DISPOSABLE_AVD': 'font-scale-scale-4001-1-scale-visual',
             'SCALE_SNAPSHOT_SERIAL': 'emulator-5554'}
BEAN_ENV = {'GITHUB_ACTIONS': 'true', 'GITHUB_RUN_ID': '4001', 'GITHUB_RUN_ATTEMPT': '1',
            'GITHUB_JOB': 'bean-workflow-visual',
            'CI_DISPOSABLE_AVD': 'font-scale-bean-4001-1-bean-workflow-visual',
            'BEAN_SNAPSHOT_SERIAL': 'emulator-5554'}


class BeanSnapshotScopeAdapterTests(unittest.TestCase):
    def test_default_scale_and_fixed_bean_paths_are_disjoint(self):
        self.assertEqual(checked_env(SCALE_ENV), '4001-1-scale-visual')
        self.assertEqual(checked_env(BEAN_ENV, 'bean'), '4001-1-bean-workflow-visual')
        self.assertEqual(set(PROFILES), {'scale', 'bean'})
        scale = Scope(SCALE_ENV)
        bean = Scope(BEAN_ENV, profile='bean')
        self.assertEqual(scale.root, Path('build/task-first-evidence/scale-visual-migration').resolve())
        self.assertEqual(bean.root, Path('build/task-first-evidence/bean-workflow-visual-migration').resolve())
        self.assertEqual(scale.owner_path, scale.root / 'snapshot-scope/owner.json')
        self.assertEqual(bean.owner_path, bean.root / 'snapshot-scope/owner.json')
        self.assertEqual(scale.config.business_script, 'scripts/check_scale_visual_migration.sh')
        self.assertEqual(bean.config.business_script, 'scripts/check_bean_workflow_visual_migration.sh')
        self.assertEqual(scale.config.business_directory, 'scale-visual-migration')
        self.assertEqual(bean.config.business_directory, 'bean-workflow-visual-migration')
        self.assertEqual(scale.config.owner_key, 'SCALE_SNAPSHOT_SCOPE_FILE')
        self.assertEqual(bean.config.owner_key, 'BEAN_SNAPSHOT_SCOPE_FILE')
        self.assertEqual(scale.config.snapshot_prefix, 'scale_scope_')
        self.assertEqual(bean.config.snapshot_prefix, 'bean_scope_')
        with self.assertRaises(ProbeError):
            checked_env(BEAN_ENV)
        with self.assertRaises(ProbeError):
            checked_env(SCALE_ENV, 'bean')

    def test_bean_owner_cannot_be_verified_as_scale_or_legacy_scale_owner_as_bean(self):
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory)
            owner = {'scopeProfile': 'bean'}
            (output / 'owner.json').write_text(json.dumps(owner))
            scale = Scope(SCALE_ENV, output)
            scale.owner = owner
            with self.assertRaisesRegex(ProbeError, 'scope profile mismatch'):
                scale.verify_owner()
            legacy = {}
            (output / 'owner.json').write_text(json.dumps(legacy))
            bean = Scope(BEAN_ENV, output, profile='bean')
            bean.owner = legacy
            with self.assertRaisesRegex(ProbeError, 'scope profile mismatch'):
                bean.verify_owner()

    def test_legacy_scale_owner_still_verifies_but_bean_snapshot_name_cannot_cross(self):
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory)
            scope = Scope(SCALE_ENV, output)
            snapshot = 'scale_scope_4001_1_scale_visual_1234abcd'
            owner = {'runIdentity': scope.identity_name, 'snapshot': snapshot,
                     'remoteDirectory': '/data/local/tmp/' + snapshot,
                     'remoteMarker': '/data/local/tmp/' + snapshot + '/owner',
                     'sourceSha': 'b' * 40, 'sourceDirty': False,
                     'apkSha256': 'a' * 64, 'deviceIdentity': {'owned': True}, 'scopeUuid': 'owned'}
            scope.owner = owner
            scope.git_head = lambda: 'b' * 40
            scope.identity = lambda: {'owned': True}
            scope.marker = lambda: 'owned'
            scope.names = lambda: [snapshot]
            (output / 'owner.json').write_text(json.dumps(owner))
            with patch('ci_font_scale_snapshot_scope.digest', return_value='a' * 64), \
                 patch('ci_font_scale_snapshot_scope.subprocess.run',
                       return_value=subprocess.CompletedProcess([], 0, '', '')):
                scope.verify_owner()
                owner['snapshot'] = 'bean_scope_4001_1_scale_visual_1234abcd'
                (output / 'owner.json').write_text(json.dumps(owner))
                with self.assertRaisesRegex(ProbeError, 'snapshot profile/name mismatch'):
                    scope.verify_owner()

    def test_unknown_profile_and_wrong_owner_path_are_rejected_without_device_access(self):
        with self.assertRaises(ProbeError):
            profile_config('other')
        with self.assertRaises(ProbeError):
            Scope(BEAN_ENV, profile='other')
        with self.assertRaisesRegex(ProbeError, 'unexpected owner path'):
            verify('/tmp/other/owner.json', 'bean')
        command = [sys.executable, 'scripts/ci_font_scale_snapshot_scope.py', 'run', '--profile', 'other']
        result = subprocess.run(command, capture_output=True, text=True)
        self.assertEqual(result.returncode, 2)
        self.assertIn('invalid choice', result.stderr)

    def test_bean_run_uses_only_bean_child_and_owner_environment(self):
        with tempfile.TemporaryDirectory() as directory:
            scope = Scope(BEAN_ENV, Path(directory) / 'snapshot-scope', profile='bean')
            scope.output.mkdir()
            scope.owner = {'scopeUuid': 'owned', 'sourceSha': 'a' * 40}
            scope.initialize = lambda: True
            scope.finish = lambda code, quiescent=True: code

            class Child:
                pid = 999999

                def wait(self, timeout=None):
                    return 0

            with patch('ci_font_scale_snapshot_scope.subprocess.Popen', return_value=Child()) as start:
                self.assertEqual(scope.run(), 0)
            args, kwargs = start.call_args
            self.assertEqual(args[0], ['bash', 'scripts/check_bean_workflow_visual_migration.sh'])
            self.assertEqual(kwargs['env']['BEAN_SNAPSHOT_SCOPE_FILE'], str(scope.owner_path))
            self.assertNotIn('SCALE_SNAPSHOT_SCOPE_FILE', kwargs['env'])
            self.assertTrue(kwargs['start_new_session'])


if __name__ == '__main__':
    unittest.main()
