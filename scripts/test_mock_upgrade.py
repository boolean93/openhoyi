import unittest

from check_mock_upgrade import require_emulator


class EmulatorGuardTests(unittest.TestCase):
    def test_accepts_verified_emulator(self):
        require_emulator("emulator-5554", "1\n")

    def test_refuses_physical_or_unverified_targets(self):
        for serial, qemu in [("physical-usb-serial", "1"), ("192.168.1.8:5555", "1"), ("emulator-5554", "0"), ("emulator-5554", ""), ("emulator-5554; command", "1")]:
            with self.subTest(serial=serial), self.assertRaises(ValueError):
                require_emulator(serial, qemu)


if __name__ == "__main__":
    unittest.main()
