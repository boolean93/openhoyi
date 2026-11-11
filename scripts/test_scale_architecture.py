"""Guard the product boundary against reviving a BOOKOO-only business data path."""
from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[1]


class ScaleArchitectureTest(unittest.TestCase):
    def source(self, path: str) -> str:
        return (ROOT / path).read_text(encoding="utf-8")

    def test_mobile_business_uses_generic_observations_only(self):
        source = ROOT / "mobile/src/main/kotlin/io/openhoyi/mobile"
        for path in source.glob("*.kt"):
            text = path.read_text(encoding="utf-8")
            self.assertNotIn("BookooSample", text, path.name)
            self.assertNotRegex(text, r"\b(?:snapshot|s|value)\.weightAt\b", path.name)
            self.assertNotRegex(text, r"\b(?:snapshot|s)\.weight\b", path.name)
        service = self.source("mobile/src/main/kotlin/io/openhoyi/mobile/MobileService.kt")
        self.assertEqual(2, service.count("ScaleBusinessReading.terminalWeight("))
        self.assertIn("ScaleBusinessReading.recordMachine(", service)

    def test_legacy_callback_bridge_stays_removed(self):
        for path in (
            "device-session/src/main/kotlin/io/openhoyi/session/DeviceSession.kt",
            "bluetooth-android/src/main/kotlin/io/openhoyi/bluetooth/AndroidDevice.kt",
            "bluetooth-android/src/main/kotlin/io/openhoyi/bluetooth/NativeDeviceHub.kt",
        ):
            text = self.source(path)
            self.assertNotRegex(text, r"\b(?:weightFrame|onWeight|BookooSample)\b", path)
        self.assertNotIn("onWeight", self.source("app/src/main/kotlin/io/openhoyi/lab/LabService.kt"))
        host = self.source("scripts/check_scale_visual_migration.sh")
        self.assertNotIn("'weightAt'", host)
        self.assertNotIn("value['weight']", host)
        self.assertIn("'scaleObservation'", host)
        self.assertIn("assert state['serviceSnapshotSchemaVersion']==2", host)
        fixture = self.source("mobile/src/androidTest/kotlin/io/openhoyi/mobile/ScaleVisualMigrationChecks.kt")
        self.assertIn('put("serviceSnapshotSchemaVersion",2)', fixture)


if __name__ == "__main__":
    unittest.main()
