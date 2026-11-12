import importlib.util
from pathlib import Path
import unittest

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
