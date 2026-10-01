import importlib.util
import json
import hashlib
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch
import xml.etree.ElementTree as ET

LOCALIZATION = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(LOCALIZATION))
SPEC = importlib.util.spec_from_file_location("android_resources", LOCALIZATION / "android_resources.py")
converter = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(converter)


class AndroidResourcesTest(unittest.TestCase):
    def test_android_literals_preserve_whitespace_quotes_and_escapes(self):
        self.assertEqual('"  A\\nB\\tC\\\\D\\\"E\\\'F  "', converter.android_literal('  A\nB\tC\\D"E\'F  '))
        self.assertEqual('"\\@ref"', converter.android_literal('@ref'))
        self.assertEqual('"\\?theme"', converter.android_literal('?theme'))

    def test_render_is_deterministic_xml_and_never_overrides_variant_name(self):
        content = converter.render({"z": 'A & <B>\n%1$s', "app_name": "wrong", "a": "first"})
        root = ET.fromstring(content)
        self.assertEqual(["a", "z"], [n.attrib['name'] for n in root])
        self.assertEqual('"A & <B>\\n%1$s"', root[1].text)
        self.assertEqual(content, converter.render({"a": "first", "app_name": "wrong", "z": 'A & <B>\n%1$s'}))

    def fixture(self, root):
        xml = root / "mobile/src/main/res/values/strings.xml"
        xml.parent.mkdir(parents=True)
        xml.write_text('<resources><string name="app_name">Brand</string><string name="message">%1$s\\n结束</string></resources>')
        directory = root / "catalog"
        directory.mkdir()
        (directory / "source.json").write_text(json.dumps({"source": "mobile/src/main/res/values/strings.xml",
            "sha256": hashlib.sha256(xml.read_bytes()).hexdigest(), "strings": {"app_name": "Brand", "message": "%1$s\n结束"}}))
        for tag in converter.validator.LANGUAGES:
            (directory / f"{tag}.json").write_text(json.dumps({"app_name": "Translated", "message": "%1$s\nEnd"}))
        return directory

    def test_generation_and_readonly_check_require_all_languages(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            directory = self.fixture(root)
            target = root / "generated"
            with patch.object(converter.validator, "ROOT", root):
                result = converter.generate(directory, target, check=True)
                self.assertEqual(7, len(result["drift"]))
                self.assertFalse(target.exists())
                self.assertEqual([], converter.generate(directory, target)["drift"])
                files = sorted(target.glob("values-*/strings.xml"))
                self.assertEqual(7, len(files))
                for path in files:
                    self.assertEqual(["message"], [n.attrib['name'] for n in ET.parse(path).getroot()])
                self.assertEqual([], converter.generate(directory, target, check=True)["drift"])
                files[0].write_text("manually changed")
                self.assertEqual(1, len(converter.generate(directory, target, check=True)["drift"]))
                self.assertEqual("manually changed", files[0].read_text())

    def test_invalid_catalog_never_writes_any_output(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            directory = self.fixture(root)
            (directory / "es.json").write_text('{"app_name":"Brand","message":"wrong %1$d"}')
            target = root / "generated"
            with patch.object(converter.validator, "ROOT", root):
                with self.assertRaises(ValueError): converter.generate(directory, target)
                self.assertFalse(target.exists())

    def test_stale_source_and_duplicate_keys_are_rejected(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            directory = self.fixture(root)
            with patch.object(converter.validator, "ROOT", root):
                (directory / "en.json").write_text('{"message":"x","message":"y"}')
                with self.assertRaises(ValueError): converter.generate(directory, root / "generated")
                (root / "mobile/src/main/res/values/strings.xml").write_text("changed")
                with self.assertRaises(ValueError): converter.generate(directory, root / "generated")
