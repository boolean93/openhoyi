import unittest
from pathlib import Path
import subprocess
import tempfile
from unittest.mock import patch

from apk_distribution import inspect_apk, inspect_metadata, compare_upgrade, write_manifest


CERT = "c0f426397c43ff9a8a4ecd3d1159b124584161b6163c375338e84ca6966a0f54"
SIGNATURE = f"Signer #1 certificate SHA-256 digest: {CERT}\n"
PACKAGE = "package: name='io.openhoyi.mobile' versionCode='1' versionName='0.1.0' compileSdkVersion='35'\n"


class DistributionChecks(unittest.TestCase):
    def test_actual_upgrade_identity_and_version(self):
        old = {"packageName": "io.openhoyi.mobile", "versionCode": 1, "signerCertificateSha256": CERT, "apkSha256": "1" * 64}
        new = {**old, "versionCode": 2, "apkSha256": "2" * 64}
        result = compare_upgrade(old, new)
        self.assertEqual(result["previousVersionCode"], 1)
        self.assertFalse(result["installationVerified"])
        self.assertFalse(result["dataPreservationVerified"])
        for mismatch in [{**new, "versionCode": 1}, {**new, "versionCode": 0}, {**new, "packageName": "io.openhoyi.mobile.mock"}, {**new, "signerCertificateSha256": "0" * 64}]:
            with self.subTest(mismatch=mismatch), self.assertRaises(ValueError):
                compare_upgrade(old, mismatch)

    def test_actual_tool_metadata(self):
        value = inspect_metadata(PACKAGE + "application-debuggable\n", SIGNATURE, "alpha", CERT)
        self.assertEqual(value["packageName"], "io.openhoyi.mobile")
        self.assertEqual(value["versionCode"], 1)
        self.assertEqual(value["versionName"], "0.1.0")
        self.assertTrue(value["debuggable"])
        self.assertEqual(value["signerCertificateSha256"], CERT)

    def test_mock_package_is_separate(self):
        value = inspect_metadata(PACKAGE.replace("io.openhoyi.mobile'", "io.openhoyi.mobile.mock'"), SIGNATURE, "mock", CERT)
        self.assertEqual(value["packageName"], "io.openhoyi.mobile.mock")

    def test_rejects_other_certificate(self):
        with self.assertRaises(ValueError):
            inspect_metadata(PACKAGE, SIGNATURE, "alpha", "0" * 64)

    def test_rejects_missing_or_multiple_signers(self):
        for signature in ["", SIGNATURE + SIGNATURE.replace("#1", "#2")]:
            with self.subTest(signature=signature), self.assertRaises(ValueError):
                inspect_metadata(PACKAGE, signature, "alpha", CERT)

    def test_rejects_wrong_variant_or_package(self):
        for variant, package in [("mock", PACKAGE), ("release", PACKAGE), ("alpha", PACKAGE.replace("io.openhoyi.mobile", "com.hoyi.personal"))]:
            with self.subTest(variant=variant), self.assertRaises(ValueError):
                inspect_metadata(package, SIGNATURE, variant, CERT)

    def test_rejects_invalid_version_and_duplicate_package(self):
        for package in ["", PACKAGE + PACKAGE, PACKAGE.replace("versionCode='1'", "versionCode='0'"), PACKAGE.replace("versionCode='1'", "versionCode='2147483648'"), PACKAGE.replace("versionName='0.1.0'", "versionName=''"), PACKAGE.replace("versionCode='1'", "versionCode='x'")]:
            with self.subTest(package=package), self.assertRaises(ValueError):
                inspect_metadata(package, SIGNATURE, "alpha", CERT)

    def test_certificate_is_exact_hex_digest(self):
        with self.assertRaises(ValueError):
            inspect_metadata(PACKAGE, SIGNATURE, "alpha", "invalid")
        self.assertEqual(inspect_metadata(PACKAGE, SIGNATURE.replace(CERT, CERT.upper()), "alpha", CERT.upper())["signerCertificateSha256"], CERT)

    def test_unsigned_apk_stops_before_badging(self):
        with tempfile.TemporaryDirectory() as directory:
            apk = Path(directory) / "app.apk"
            apk.write_bytes(b"fixture")
            with patch("apk_distribution.subprocess.run", side_effect=subprocess.CalledProcessError(1, "verify")) as tool:
                with self.assertRaises(subprocess.CalledProcessError):
                    inspect_apk(apk, "aapt", "apksigner", "alpha", CERT, "1" * 40)
                self.assertEqual(tool.call_count, 1)

    def test_inspection_commands_are_bounded(self):
        with tempfile.TemporaryDirectory() as directory:
            apk = Path(directory) / "app.apk"
            apk.write_bytes(b"fixture")
            results = [subprocess.CompletedProcess([], 0, stdout=SIGNATURE), subprocess.CompletedProcess([], 0, stdout=PACKAGE)]
            with patch("apk_distribution.subprocess.run", side_effect=results) as tool:
                inspect_apk(apk, "aapt", "apksigner", "alpha", CERT, "1" * 40)
            self.assertEqual([call.kwargs.get("timeout") for call in tool.call_args_list], [60, 60])

    def test_rejects_changed_apk_during_inspection(self):
        with tempfile.TemporaryDirectory() as directory:
            apk = Path(directory) / "app.apk"
            apk.write_bytes(b"fixture")
            def tool(argv, **kwargs):
                if argv[0] == "aapt":
                    apk.write_bytes(b"changed")
                return subprocess.CompletedProcess(argv, 0, stdout=PACKAGE if argv[0] == "aapt" else SIGNATURE)
            with patch("apk_distribution.subprocess.run", side_effect=tool), self.assertRaises(ValueError):
                inspect_apk(apk, "aapt", "apksigner", "alpha", CERT, "1" * 40)

    def test_manifest_hash_and_declared_provenance(self):
        with tempfile.TemporaryDirectory() as directory:
            apk = Path(directory) / "app.apk"
            output = Path(directory) / "manifest.json"
            apk.write_bytes(b"fixture")
            results = [subprocess.CompletedProcess([], 0, stdout=SIGNATURE), subprocess.CompletedProcess([], 0, stdout=PACKAGE)]
            with patch("apk_distribution.subprocess.run", side_effect=results):
                result = inspect_apk(apk, "aapt", "apksigner", "alpha", CERT, "1" * 40)
            self.assertFalse(result["sourceCommitVerified"])
            self.assertEqual(result["declaredSourceCommit"], "1" * 40)
            write_manifest(output, result, apk)
            saved = output.read_bytes()
            apk.write_bytes(b"changed")
            with self.assertRaises(ValueError):
                write_manifest(output, result, apk)
            self.assertEqual(output.read_bytes(), saved)
            with self.assertRaises(ValueError):
                write_manifest(apk, result, apk)
            self.assertEqual(apk.read_bytes(), b"changed")

    def test_invalid_declared_commit_never_invokes_tools(self):
        with patch("apk_distribution.subprocess.run") as tool, self.assertRaises(ValueError):
            inspect_apk("missing.apk", "aapt", "apksigner", "alpha", CERT, "short")
        tool.assert_not_called()

    def test_output_requires_explicit_overwrite(self):
        with tempfile.TemporaryDirectory() as directory:
            apk = Path(directory) / "app.apk"
            output = Path(directory) / "manifest.json"
            apk.write_bytes(b"fixture")
            results = [subprocess.CompletedProcess([], 0, stdout=SIGNATURE), subprocess.CompletedProcess([], 0, stdout=PACKAGE)]
            with patch("apk_distribution.subprocess.run", side_effect=results):
                result = inspect_apk(apk, "aapt", "apksigner", "alpha", CERT, "1" * 40)
            output.write_text("preserve me")
            with self.assertRaises(FileExistsError):
                write_manifest(output, result, apk)
            self.assertEqual(output.read_text(), "preserve me")
            write_manifest(output, result, apk, overwrite=True)
            self.assertIn('"apkSha256"', output.read_text())


if __name__ == "__main__":
    unittest.main()
