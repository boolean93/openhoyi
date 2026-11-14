import json
from pathlib import Path
import shutil
import tempfile
import unittest

import curve_import_prompt as prompt

ROOT = Path(__file__).resolve().parents[1]
MANIFEST = dict(versionCode=123, versionName="0.2.0-dev.123", declaredSourceCommit="a" * 40)

class PromptTest(unittest.TestCase):
    def test_current_contract_and_deterministic_output(self):
        self.assertIsNotNone(prompt.verify_source(ROOT))
        with tempfile.TemporaryDirectory() as folder:
            first = prompt.build_prompt(ROOT, folder, MANIFEST)
            data = (Path(folder) / prompt.ASSET_NAME).read_bytes()
            self.assertEqual(first, prompt.build_prompt(ROOT, folder, MANIFEST))
            self.assertEqual(first["sha256"], prompt.digest(data))
            self.assertIn(b"0.2.0-dev.123", data)
            self.assertIn(b"a" * 40, data)
            self.assertIn('"controlMode": "flow"', data.decode())
            self.assertLessEqual(len(data), prompt.MAX_BYTES)

    def test_old_source_and_partial_contract_are_distinct(self):
        with tempfile.TemporaryDirectory() as folder:
            self.assertIsNone(prompt.build_prompt(folder, Path(folder) / "output", MANIFEST))
            (Path(folder) / prompt.DIRECTORY).mkdir(parents=True)
            with self.assertRaises(FileNotFoundError):
                prompt.build_prompt(folder, Path(folder) / "output", MANIFEST)

    def test_changed_parser_or_template_fails_until_reviewed(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            for name in (*prompt.CONTRACT_FILES, prompt.DIRECTORY + "/contract.json"):
                dest = root / name
                dest.parent.mkdir(parents=True, exist_ok=True)
                shutil.copyfile(ROOT / name, dest)
            self.assertIsNotNone(prompt.verify_source(root))
            (root / prompt.CONTRACT_FILES[0]).write_text("changed contract")
            with self.assertRaisesRegex(ValueError, "drift"):
                prompt.build_prompt(root, root / "out", MANIFEST)

    def test_metadata_rejects_path_url_wrong_hash_and_boolean_version(self):
        good = dict(file=prompt.ASSET_NAME, sha256="b" * 64, schema=3, revision=1)
        self.assertEqual(good, prompt.validate_metadata(good))
        for key, value in (("file", "../evil"), ("file", "https://elsewhere"), ("sha256", "wrong"),
                           ("schema", True), ("revision", True), ("revision", 0)):
            with self.assertRaises(ValueError):
                prompt.validate_metadata(dict(good, **{key:value}))
        with self.assertRaises(ValueError):
            prompt.validate_metadata(dict(good, url="https://elsewhere"))

if __name__ == "__main__":
    unittest.main()
