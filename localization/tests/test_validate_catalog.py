import importlib.util
import unittest
from unittest.mock import patch
import tempfile
import hashlib
import json
from pathlib import Path

MODULE = Path(__file__).resolve().parents[1] / "validate_catalog.py"
spec = importlib.util.spec_from_file_location("validate_catalog", MODULE)
validator = importlib.util.module_from_spec(spec)
spec.loader.exec_module(validator)


class CatalogValidationTest(unittest.TestCase):
    def test_complete_catalog_allows_argument_reordering(self):
        self.assertEqual([], validator.validate({"a": "%1$s %2$02d\n结束"},
                                              {"a": "%2$02d %1$s\nEnd"}))

    def test_missing_extra_empty_and_wrong_type_are_reported(self):
        errors = validator.validate({"a": "%1$s", "b": "中文", "c": "中文"},
                                    {"a": "%1$d", "b": "", "extra": "text"})
        self.assertEqual({"a: format", "b: empty", "c: missing", "extra: unexpected"}, set(errors))

    def test_padding_argument_count_newlines_and_unknown_formats_are_checked(self):
        for value in ["%1$d\nEnd", "%1$02d %1$02d\nEnd", "%1$02d End", "%1$02d\n%s"]:
            self.assertTrue(validator.validate({"a": "%1$02d\n中文"}, {"a": value}), value)

    def test_control_and_hidden_direction_characters_are_rejected(self):
        for character in ["\x00", "\x07", "\u061c", "\u200e", "\u200f", "\u202e", "\u2066"]:
            self.assertIn("a: control", validator.validate({"a": "中文"}, {"a": "text" + character}))

    def test_duplicate_json_keys_are_not_silently_overwritten(self):
        with self.assertRaises(ValueError):
            validator.decode_json('{"a":"first","a":"second"}')

    def test_values_must_be_strings(self):
        self.assertIn("a: type", validator.validate({"a": "中文"}, {"a": 1}))


class CatalogSourceValidationTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.root = Path(self.directory.name)
        xml = self.root / "mobile/src/main/res/values/strings.xml"
        xml.parent.mkdir(parents=True)
        xml.write_text('<resources><string name="a">"正文\\n%1$s"</string></resources>')
        self.xml = xml
        self.catalog = self.root / "source.json"
        self.document = {"source": "mobile/src/main/res/values/strings.xml",
                         "sha256": hashlib.sha256(xml.read_bytes()).hexdigest(),
                         "strings": {"a": "正文\n%1$s"}}
        self.catalog.write_text(json.dumps(self.document))

    def test_source_snapshot_matches_actual_resource_templates(self):
        with patch.object(validator, "ROOT", self.root):
            self.assertEqual(self.document["strings"], validator.source_catalog(self.catalog))

    def test_stale_hash_and_changed_snapshot_content_are_rejected(self):
        with patch.object(validator, "ROOT", self.root):
            self.xml.write_text('<resources><string name="a">changed</string></resources>')
            with self.assertRaisesRegex(ValueError, "stale"):
                validator.source_catalog(self.catalog)
            self.document["sha256"] = hashlib.sha256(self.xml.read_bytes()).hexdigest()
            self.catalog.write_text(json.dumps(self.document))
            with self.assertRaisesRegex(ValueError, "content differs"):
                validator.source_catalog(self.catalog)

    def test_partial_language_set_cannot_be_reported_complete(self):
        (self.root / "en.json").write_text(json.dumps({"a": "Text\n%1$s"}))
        with patch.object(validator, "ROOT", self.root):
            result = validator.report(self.root)
        self.assertFalse(result["complete"])
        self.assertTrue(result["languages"]["en"]["valid"])
        self.assertFalse(result["languages"]["ar"]["valid"])

    def test_non_object_source_is_a_validation_error(self):
        self.catalog.write_text("[]")
        with patch.object(validator, "ROOT", self.root):
            with self.assertRaises(ValueError):
                validator.source_catalog(self.catalog)
