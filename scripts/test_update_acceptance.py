import importlib.util
from pathlib import Path
import unittest
import json
import os
import subprocess
import sys
import tempfile

spec = importlib.util.spec_from_file_location('update_acceptance', Path(__file__).with_name('check_update_acceptance.py'))
checks = importlib.util.module_from_spec(spec)
spec.loader.exec_module(checks)


class UpdateAcceptanceGuardTests(unittest.TestCase):
    def test_fresh_emulator_leaves_existing_production_namespaces_out_of_scope(self):
        checks.require_fresh_emulator('emulator-5554', '1', ['package:io.openhoyi.mobile', 'package:io.openhoyi.mobile.mock'])

    def test_physical_or_unconfirmed_emulator_is_rejected_before_install(self):
        for serial, qemu in [('192.0.2.15:5555', '0'), ('emulator-5554', '0'), ('unknown', '1')]:
            with self.assertRaises(ValueError):
                checks.require_fresh_emulator(serial, qemu, [])

    def test_any_existing_test_namespace_stops_instead_of_overwriting_data(self):
        for package in [checks.HOST, checks.TEST, checks.INSTALLER_TARGET]:
            with self.assertRaises(ValueError):
                checks.require_fresh_emulator('emulator-5554', '1', ['package:' + package])


class UpdateAcceptanceDispatchTests(unittest.TestCase):
    def invoke(self, mode):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            capture = root / 'capture.json'
            for name in ('python3', 'bash'):
                stub = root / name
                stub.write_text('#!' + sys.executable + '\n' +
                    'import json, os, sys\nfrom pathlib import Path\n' +
                    'Path(os.environ["DISPATCH_CAPTURE"]).write_text(json.dumps({"command": Path(sys.argv[0]).name, "args": sys.argv[1:]}))\n')
                stub.chmod(0o755)
            env = dict(os.environ, PATH=str(root) + os.pathsep + '/usr/bin:/bin',
                       ANDROID_HOME='/fixed/test-sdk', DISPATCH_CAPTURE=str(capture))
            if mode is None:
                env.pop('UPDATE_CHECKS', None)
            else:
                env['UPDATE_CHECKS'] = mode
            result = subprocess.run(['/bin/sh', str(Path(__file__).with_name('check_mock_or_update_acceptance.sh'))],
                                    env=env, capture_output=True, text=True)
            return result.returncode, json.loads(capture.read_text()) if capture.exists() else None

    def test_fixed_wrapper_dispatches_both_modes_without_device_commands(self):
        for mode in ('false', None):
            code, command = self.invoke(mode)
            self.assertEqual(code, 0)
            self.assertEqual(command, {'command': 'bash', 'args': ['scripts/check_mock_lifecycle.sh']})
        code, command = self.invoke('true')
        self.assertEqual(code, 0)
        self.assertEqual(command, {'command': 'python3', 'args': ['scripts/check_update_acceptance.py', '--sdk', '/fixed/test-sdk']})

    def test_unrecognized_or_shell_like_input_stops_before_dispatch(self):
        for mode in ('1', 'TRUE', 'true; echo unsafe', 'true\nfalse'):
            code, command = self.invoke(mode)
            self.assertEqual(code, 2)
            self.assertIsNone(command)
