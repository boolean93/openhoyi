#!/usr/bin/env python3
"""Offline contract checks against the already verified factory wire corpus."""

import hashlib
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parent))
from generate_factory_wire_oracle import normalized_rows


ROOT = Path(__file__).resolve().parents[1]
BUNDLE = ROOT.parent / "hoyi-project/app/assets/apps/__UNI__7D80DAB/www/app-service.js"
FACTORY = ROOT / "mobile/src/main/assets/factory_curves_v3.tsv"
FACTORY_WIRE = ROOT / "mobile/src/main/assets/factory_wire_v1.tsv"
FACTORY_SLOTS = ROOT / "mobile/src/main/assets/factory_slot_wire_v1.tsv"
GENERATOR = ROOT / "scripts/generate_legacy_curve_oracle.py"


@unittest.skipUnless(BUNDLE.is_file(), "local legacy app bundle required")
class LegacyCurveOracleTest(unittest.TestCase):
    def run_generator(self, source: dict, output: Path, bundle: Path = BUNDLE) -> subprocess.CompletedProcess:
        export = output.with_suffix(".json")
        export.write_text(json.dumps(source, ensure_ascii=False), encoding="utf-8")
        return subprocess.run(["python3", str(GENERATOR), str(bundle), str(export), str(output)],
                              text=True, capture_output=True)

    def test_all_factory_rows_reproduce_both_modes_at_all_six_slots(self):
        _, rows = normalized_rows(FACTORY)
        source = {"format": "openhoyi-legacy-curves-v1", "factoryVersion": 3,
                  "items": [row["curve"] for row in rows]}
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory) / "proof.tsv"
            result = self.run_generator(source, output)
            self.assertEqual(result.returncode, 0, result.stderr)
            lines = output.read_text().splitlines()
            self.assertEqual(len(lines), 601)
            export_hash = hashlib.sha256(output.with_suffix(".json").read_bytes()).hexdigest()
            self.assertIn("export-sha256=" + export_hash, lines[0])
            produced = [line.split("\t") for line in lines[1:]]
            temp_lines = FACTORY_WIRE.read_text().splitlines()[1:]
            slot_lines = FACTORY_SLOTS.read_text().splitlines()[1:]
            for index in range(100):
                expected_temp = temp_lines[index].split("\t")
                self.assertEqual(produced[index * 6],
                                 [str(index), "7", expected_temp[1], expected_temp[2]])
                for slot in range(1, 6):
                    expected = slot_lines[index * 5 + slot - 1].split("\t")
                    self.assertEqual(produced[index * 6 + slot],
                                     [str(index), str(slot), expected[2], expected[3]])

    def test_malformed_curve_does_not_publish_partial_proof(self):
        source = {"format": "openhoyi-legacy-curves-v1", "items": [{"name": "broken"}]}
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory) / "proof.tsv"
            result = self.run_generator(source, output)
            self.assertNotEqual(result.returncode, 0)
            self.assertFalse(output.exists())

    def test_changed_legacy_encoder_is_rejected_before_publishing_proof(self):
        _, rows = normalized_rows(FACTORY)
        source = {"format": "openhoyi-legacy-curves-v1", "items": [rows[0]["curve"]]}
        with tempfile.TemporaryDirectory() as directory:
            bundle = Path(directory) / "changed-app-service.js"
            text = BUNDLE.read_text()
            marker = "startTempChart: function (t, i, a) {"
            self.assertIn(marker, text)
            bundle.write_text(text.replace(marker, marker + "\nvar unexpected = 1;", 1))
            output = Path(directory) / "proof.tsv"
            result = self.run_generator(source, output, bundle)
            self.assertNotEqual(result.returncode, 0)
            self.assertFalse(output.exists())

    def test_empty_slot_is_preserved_by_index_but_receives_no_start_proof(self):
        _, rows = normalized_rows(FACTORY)
        source = {"format": "openhoyi-legacy-curves-v1", "items": [None, rows[0]["curve"]]}
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory) / "proof.tsv"
            result = self.run_generator(source, output)
            self.assertEqual(result.returncode, 0, result.stderr)
            lines = output.read_text().splitlines()
            self.assertEqual(len(lines), 7)
            self.assertTrue(all(line.startswith("1\t") for line in lines[1:]))


if __name__ == "__main__":
    unittest.main()
