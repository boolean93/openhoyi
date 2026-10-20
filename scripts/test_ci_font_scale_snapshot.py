import os
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch
from probe_ci_font_scale_snapshot import Probe, ProbeError, console_ok, guard, parse_state, snapshot_names


def response(stdout='OK\n', stderr='', code=0):
    return subprocess.CompletedProcess([], code, stdout, stderr)


class Contracts(unittest.TestCase):
    def test_console_rejects_false_success(self):
        for result in (response('cannot save\nOK\n'), response('OK\n', 'warning'), response(code=1), response('OK\nSKIPPED\n'), response('')):
            with self.assertRaises(ProbeError):
                console_ok(result)

    def test_snapshot_list_closed_parser(self):
        table = 'List of snapshots present on all disks:\nID TAG VM SIZE DATE VM CLOCK\n1 own 20M 2026-10-19 10:00:00 00:00:02.000\nOK\n'
        self.assertEqual(snapshot_names(response(table)), ['own'])
        for text in ('cannot list\nOK\n', 'own\nOK\n', table.replace('OK', 'FAIL'), table.replace('OK', '1 own 20M 2026-10-19 10:00:00 00:00:02.000\nOK')):
            with self.assertRaises(ProbeError):
                snapshot_names(response(text))

    def test_presence_is_not_default_equivalence(self):
        config = '  mGlobalConfiguration: {1.0 310mcc en_US}\n'
        self.assertNotEqual(parse_state('', config), parse_state('font_scale=1.0\n', config))
        with self.assertRaises(ProbeError):
            parse_state('', 'config: en_US fontScale=1.0')

    def test_official_android14_process_dump_style(self):
        # ATMS dumpForProcesses uses colon; Configuration.toString begins with font scale.
        dump = 'ACTIVITY MANAGER RUNNING PROCESSES (dumpsys activity processes)\n  mGlobalConfiguration: {1.3 310mcc260mnc [en_US] ldltr sw411dp w411dp h773dp 420dpi nrml long port finger -keyb/v/h -nav/h winConfig={ mBounds=Rect(0, 0 - 1080, 2160) } s.12 fontWeightAdjustment=0}\n'
        self.assertEqual(parse_state('other=value\nfont_scale=1.3\n', dump)['frameworkFontScale'], '1.3')
        for invalid in (dump.replace('mGlobalConfiguration: ', 'mGlobalConfiguration='), dump.replace('mGlobalConfiguration', 'mLastReportedConfiguration'), dump + dump, 'ACTIVITY MANAGER SETTINGS\n  max_cached_processes=32\n'):
            with self.assertRaises(ProbeError):
                parse_state('', invalid)

    def test_guard_before_subprocess(self):
        with patch('probe_ci_font_scale_snapshot.subprocess.run') as run:
            with self.assertRaises(ProbeError):
                Probe('emulator-5554', '/unused', {})
            run.assert_not_called()
        env = {'GITHUB_ACTIONS': 'true', 'GITHUB_RUN_ID': '123', 'GITHUB_RUN_ATTEMPT': '1', 'GITHUB_JOB': 'probe', 'CI_DISPOSABLE_AVD': 'font-scale-probe-123-1-probe'}
        self.assertEqual(guard(env, 'emulator-5554'), '123-1-probe')
        with self.assertRaises(ProbeError):
            guard(env, 'physical')

    def exercise_cleanup(self, fault=None, present=False):
        env = {'GITHUB_ACTIONS': 'true', 'GITHUB_RUN_ID': '123', 'GITHUB_RUN_ATTEMPT': '1', 'GITHUB_JOB': 'probe', 'CI_DISPOSABLE_AVD': 'font-scale-probe-123-1-probe'}
        config = '  mGlobalConfiguration: {1.0 310mcc [en_US]}\n'
        baseline_settings = 'other=value\n' + ('font_scale=1.0\n' if present else '')
        baseline = parse_state(baseline_settings, config)
        mutated = parse_state('font_scale=1.3\n', config.replace('1.0', '1.3'))
        with tempfile.TemporaryDirectory() as root:
            probe = Probe('emulator-5554', Path(root) / 'out', env)
            active = [False]
            identities = [0]
            def identity():
                identities[0] += 1
                return {'serialno': 'changed' if fault == 'identity' and identities[0] == 3 else 'EMULATOR34X0X0'}
            def command(*args):
                if args[0] == 'pull':
                    destination = Path(args[2])
                    destination.mkdir()
                    if fault != 'pull_missing':
                        (destination / 'identity').write_text(probe.identity)
                        (destination / 'before.settings').write_text(baseline_settings)
                        (destination / 'before.configuration').write_text(config)
                        (destination / 'mutated.settings').write_text('font_scale=1.3\n')
                        (destination / 'mutated.configuration').write_text(config.replace('1.0', '1.3'))
                        (destination / 'build-properties').write_text('captured-properties')
                if args[:3] == ('emu', 'avd', 'snapshot'):
                    operation = args[3]
                    if operation == 'save':
                        active[0] = True
                        if fault == 'save_unknown':
                            return response('cannot save\nOK\n')
                    if operation == 'delete':
                        active[0] = False
                return response()
            def names():
                if fault == 'list_missing':
                    return []
                return [probe.name] if active[0] else []
            with patch.object(probe, 'device_identity', side_effect=identity), patch.object(probe, 'shell', return_value=probe.identity), patch.object(probe, 'state', side_effect=lambda label: baseline if label == 'before' else mutated), patch.object(probe, 'names', side_effect=names), patch.object(probe, 'command', side_effect=command), patch.object(probe, 'restored_state', return_value=mutated if fault == 'restore' else baseline):
                code = probe.run()
            report = __import__('json').loads((probe.output / 'result.json').read_text())
            return code, report

    def test_unverified_save_list_or_pull_never_loads(self):
        for fault in ('save_unknown', 'list_missing', 'pull_missing'):
            with self.subTest(fault=fault):
                code, report = self.exercise_cleanup(fault)
                self.assertEqual(code, 1)
                self.assertEqual([entry['operation'] for entry in report['snapshotOperations']], ['save'])
                self.assertIn('LOAD_NOT_AUTHORIZED_BY_VERIFIED_EVIDENCE', report['errors'])

    def test_postload_identity_or_restore_failure_preserves_snapshot(self):
        for fault in ('identity', 'restore'):
            with self.subTest(fault=fault):
                code, report = self.exercise_cleanup(fault)
                self.assertEqual(code, 1)
                self.assertEqual([entry['operation'] for entry in report['snapshotOperations']], ['save', 'load'])
                self.assertFalse(report['restorationVerified'])

    def test_simulated_failure_uses_single_restore_and_evidence_hashes(self):
        code, report = self.exercise_cleanup()
        self.assertEqual(code, 0)
        self.assertEqual(report['status'], 'PASS')
        self.assertTrue(report['simulatedBusinessFailure'])
        self.assertEqual([entry['operation'] for entry in report['snapshotOperations']], ['save', 'load', 'delete'])
        self.assertTrue(all(entry['attempt'] and entry['ack'] for entry in report['snapshotOperations']))
        self.assertIn('pulled-before-load/before.configuration', report['rawEvidenceSha256'])

    def test_present_baseline_does_not_claim_absence_proof(self):
        code, report = self.exercise_cleanup(present=True)
        self.assertEqual(code, 0)
        self.assertEqual(report['status'], 'CAPABILITY_PASS_ABSENCE_NOT_PROVED')
        self.assertFalse(report['absenceCaseProved'])


if __name__ == '__main__':
    unittest.main()
