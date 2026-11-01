import unittest

from check_mock_upgrade import require_emulator, require_phase_success


class EmulatorGuardTests(unittest.TestCase):
    def test_accepts_verified_emulator(self):
        require_emulator("emulator-5554", "1\n")

    def test_refuses_physical_or_unverified_targets(self):
        for serial, qemu in [("physical-usb-serial", "1"), ("192.168.1.8:5555", "1"), ("emulator-5554", "0"), ("emulator-5554", ""), ("emulator-5554; command", "1")]:
            with self.subTest(serial=serial), self.assertRaises(ValueError):
                require_emulator(serial, qemu)


class AdvancedUpgradeEvidenceTests(unittest.TestCase):
    def test_legacy_success_alone_does_not_prove_advanced_stores(self):
        for phase in ("seed", "verify"):
            with self.subTest(phase=phase), self.assertRaises(RuntimeError):
                require_phase_success(phase, f"MOCK_UPGRADE_{phase.upper()}_PASSED\n")

    def test_both_exact_markers_are_required_and_failures_still_reject(self):
        for phase in ("seed", "verify"):
            output = (f"MOCK_UPGRADE_{phase.upper()}_PASSED\n"
                f"MOCK_UPGRADE_ADVANCED_{phase.upper()}_PASSED stores=5 protocolPair=true readOnlyDraft=true doseIdempotent=true\n")
            require_phase_success(phase, output)
            for bad in (output.replace("stores=5", "stores=4"), output + "CHECKS_FAILED", output + "FAILURE"):
                with self.subTest(phase=phase, bad=bad), self.assertRaises(RuntimeError):
                    require_phase_success(phase, bad)


if __name__ == "__main__":
    unittest.main()
