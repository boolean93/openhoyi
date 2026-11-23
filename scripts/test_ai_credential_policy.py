"""Static backup/sharing contracts only, not cloud backup or device-transfer runtime proof."""
from pathlib import Path
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
MAIN = ROOT / "mobile/src/main"
ANDROID = "{http://schemas.android.com/apk/res/android}"
NORMAL_DOMAINS = {"root", "file", "database", "sharedpref", "external"}
DEVICE_DOMAINS = {"device_root", "device_file", "device_database", "device_sharedpref"}


class AiCredentialPolicyTest(unittest.TestCase):
    def application(self):
        return ET.parse(MAIN / "AndroidManifest.xml").getroot().find("application")

    def assert_full_excludes(self, section, domains):
        self.assertIsNotNone(section)
        self.assertFalse(list(section.iter("include")), "No backup include may reopen a path")
        excludes = [(node.get("domain"), node.get("path")) for node in section.findall("exclude")]
        self.assertEqual(len(excludes), len(set(excludes)), "Duplicate exclusions obscure the contract")
        self.assertEqual({(domain, ".") for domain in domains}, set(excludes))
        self.assertEqual({"exclude"}, {node.tag for node in section})

    def test_manifest_disables_backup_and_references_both_platform_rules(self):
        app = self.application()
        self.assertIsNotNone(app)
        self.assertEqual("false", app.get(ANDROID + "allowBackup"))
        self.assertEqual("@xml/backup_rules", app.get(ANDROID + "fullBackupContent"))
        self.assertEqual("@xml/data_extraction_rules", app.get(ANDROID + "dataExtractionRules"))

    def test_legacy_rules_exclude_all_credential_and_device_storage_domains(self):
        root = ET.parse(MAIN / "res/xml/backup_rules.xml").getroot()
        self.assertEqual("full-backup-content", root.tag)
        self.assert_full_excludes(root, NORMAL_DOMAINS | DEVICE_DOMAINS)

    def test_android_31_cloud_and_device_transfer_exclude_all_normal_storage(self):
        root = ET.parse(MAIN / "res/xml/data_extraction_rules.xml").getroot()
        self.assertEqual("data-extraction-rules", root.tag)
        self.assertEqual(["cloud-backup", "device-transfer"], [node.tag for node in root])
        self.assertFalse(list(root.iter("include")))
        for tag in ("cloud-backup", "device-transfer"):
            with self.subTest(section=tag):
                self.assert_full_excludes(root.find(tag), NORMAL_DOMAINS)
                # AI ciphertext lives in normal credential-encrypted shared preferences.
                self.assertIsNotNone(root.find(f"{tag}/exclude[@domain='sharedpref'][@path='.']"))

    def test_screenshot_permission_is_chat_only_and_key_protection_remains(self):
        chat = (MAIN / "kotlin/io/openhoyi/mobile/AiChatActivity.kt").read_text()
        key = (MAIN / "kotlin/io/openhoyi/mobile/AiKeyActivity.kt").read_text()
        self.assertNotIn("FLAG_SECURE", chat)
        self.assertIn("if (BuildConfig.MOCK_MODE && memory.failureAttemptId != null && memory.failureNotice == memory.notice)", chat)
        self.assertIn("AiErrorPresentation.mockDetails(BuildConfig.VERSION_CODE, memory.protocol.modelId, memory.diagnostic, memory.failureAttemptId)", chat)
        self.assertIn("window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)", key)
        self.assertIn("isLongClickable = false", key)
        self.assertIn("setTextIsSelectable(false)", key)

    def test_file_providers_are_private_and_share_only_verified_updates_or_shot_images(self):
        providers = self.application().findall("provider")
        contracts = {
            "${applicationId}.updates": ("androidx.core.content.FileProvider", "update_paths",
                "files-path", {"name": "verified_updates", "path": "updates/verified/"}),
            "${applicationId}.shot-share": (".ShotShareCardFileProvider", "shot_share_paths",
                "cache-path", {"name": "shot-images", "path": "shot-share/"}),
        }
        self.assertEqual(len(contracts), len(providers))
        self.assertEqual(set(contracts), {provider.get(ANDROID + "authorities") for provider in providers})
        for provider in providers:
            authority = provider.get(ANDROID + "authorities")
            with self.subTest(authority=authority):
                class_name, resource, tag, attrib = contracts[authority]
                self.assertEqual(class_name, provider.get(ANDROID + "name"))
                self.assertEqual("false", provider.get(ANDROID + "exported"))
                self.assertEqual("true", provider.get(ANDROID + "grantUriPermissions"))
                metadata = provider.findall("meta-data")
                self.assertEqual(1, len(metadata))
                self.assertEqual("android.support.FILE_PROVIDER_PATHS", metadata[0].get(ANDROID + "name"))
                self.assertEqual(f"@xml/{resource}", metadata[0].get(ANDROID + "resource"))
                paths = ET.parse(MAIN / f"res/xml/{resource}.xml").getroot()
                self.assertEqual("paths", paths.tag)
                self.assertEqual(1, len(paths))
                self.assertEqual(tag, paths[0].tag)
                self.assertEqual(attrib, paths[0].attrib)

    def test_full_verification_runs_this_static_policy_check(self):
        script = (ROOT / "scripts/verify-pr-checks").read_text()
        full = script.split("  full)", 1)[1].split("    ;;", 1)[0]
        self.assertIn("python3 -m unittest discover -s scripts -p test_ai_credential_policy.py", full)


if __name__ == "__main__":
    unittest.main()
