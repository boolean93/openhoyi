"""Fault-inject the exact M5 host case-reference reader; this is not Android acceptance."""
import copy
import hashlib
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

from extraction_case_refs import CASE_NAMES, PASS, SCHEMA, iter_extraction_cases


class ExtractionCaseReferences(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        self.source = 'a' * 40
        self.profile = 'tablet-user-landscape-light-font-1.3'
        self.root = Path(temporary.name) / self.source
        self.base = self.root / self.profile / 'extraction' / 'special-states'
        self.base.mkdir(parents=True)
        self.ids = {name: 'm5-extraction-' + name.lower().replace('_', '-') for name in CASE_NAMES}
        self.summary = dict(caseStorageSchema=SCHEMA, sourceSha=self.source, profile=self.profile,
                            page='extraction', completed=True, actualAdditionalActivityCaptures=12,
                            expectedCases=list(CASE_NAMES), scenarioShotIds=self.ids, cases=[])
        self.originals = []
        for ordinal, name in enumerate(CASE_NAMES, 1):
            case = dict(case=name, sourceSha=self.source, profile=self.profile,
                        declaredShotId=self.ids[name], status=PASS,
                        originalBusinessProof=dict(trace='current-cup', points=[1, 2, 3], journal='unchanged'))
            path = self.base / name / 'case.json'
            path.parent.mkdir()
            path.write_text(json.dumps(case, ensure_ascii=False))
            payload = path.read_bytes()
            self.summary['cases'].append(dict(ordinal=ordinal, case=name, path=f'{name}/case.json',
                sizeBytes=len(payload), sha256=hashlib.sha256(payload).hexdigest(), status=PASS,
                sourceSha=self.source, profile=self.profile, declaredShotId=self.ids[name]))
            self.originals.append(case)

    def read(self):
        return iter_extraction_cases(self.base, self.summary, self.source, self.profile,
                                     CASE_NAMES, self.ids, self.root)

    def reject(self):
        with self.assertRaises((AssertionError, KeyError, ValueError, OSError)):
            list(self.read())

    def rewrite(self, ordinal, case):
        path = self.base / CASE_NAMES[ordinal] / 'case.json'
        path.write_text(json.dumps(case))
        payload = path.read_bytes()
        self.summary['cases'][ordinal].update(sizeBytes=len(payload), sha256=hashlib.sha256(payload).hexdigest())

    def test_original_case_objects_and_order_are_preserved(self):
        self.assertEqual(list(self.read()), self.originals)

    def test_reader_is_lazy_and_does_not_parse_all_cases(self):
        for name in CASE_NAMES[1:]:
            (self.base / name / 'case.json').unlink()
        reader = self.read()
        self.assertEqual(next(reader), self.originals[0])
        with self.assertRaises(AssertionError):
            next(reader)

    def test_missing_or_truncated_original_is_rejected(self):
        path = self.base / CASE_NAMES[0] / 'case.json'
        original = path.read_bytes()
        path.unlink()
        self.reject()
        path.write_bytes(original[:-1])
        self.reject()

    def test_equal_size_tamper_is_rejected_by_raw_sha(self):
        path = self.base / CASE_NAMES[0] / 'case.json'
        original = path.read_bytes()
        path.write_bytes(original.replace(b'current-cup', b'changed-cup'))
        self.assertEqual(path.stat().st_size, self.summary['cases'][0]['sizeBytes'])
        self.reject()

    def test_wrong_schema_completion_count_or_order_is_rejected(self):
        for key, value in [('caseStorageSchema', 'INLINE_V0'), ('completed', False),
                           ('actualAdditionalActivityCaptures', 11), ('expectedCases', list(reversed(CASE_NAMES))),
                           ('sourceSha', 'b' * 40), ('profile', 'tablet-user-landscape-dark-font-1.3')]:
            original = self.summary[key]
            self.summary[key] = value
            with self.subTest(key=key): self.reject()
            self.summary[key] = original

    def test_duplicate_missing_or_reordered_reference_is_rejected(self):
        original = copy.deepcopy(self.summary['cases'])
        for refs in (original[:-1], original + [original[0]],
                     [original[0], original[0], *original[2:]], list(reversed(original))):
            self.summary['cases'] = refs
            self.reject()
        self.summary['cases'] = original

    def test_absolute_traversal_wrong_case_and_noncanonical_path_are_rejected(self):
        ref = self.summary['cases'][0]
        for path in ('/tmp/case.json', '../STARTING/case.json', 'STARTING/../STARTING/case.json',
                     'STOP_REQUESTED/case.json', 'starting/case.json', 'STARTING/./case.json'):
            ref['path'] = path
            with self.subTest(path=path): self.reject()

    def test_bad_hash_size_status_or_extra_proof_in_index_is_rejected(self):
        original = copy.deepcopy(self.summary['cases'][0])
        for key, value in [('sha256', '0' * 64), ('sha256', 'INVALID'), ('sizeBytes', 0),
                           ('sizeBytes', True), ('ordinal', True), ('status', 'FAIL'),
                           ('sourceSha', 'b' * 40), ('profile', 'other-profile'),
                           ('declaredShotId', 'another-shot'), ('wholeProof', {'points': [1, 2]})]:
            self.summary['cases'][0] = dict(original, **{key: value})
            with self.subTest(key=key, value=value): self.reject()
        self.summary['cases'][0] = original

    def test_hash_bound_but_wrong_original_identity_or_failed_case_is_rejected(self):
        for key, value in [('case', 'STOP_REQUESTED'), ('sourceSha', 'b' * 40),
                           ('profile', 'wrong'), ('declaredShotId', 'another-shot'), ('status', 'FAIL')]:
            changed = dict(self.originals[0], **{key: value})
            self.rewrite(0, changed)
            with self.subTest(key=key): self.reject()
        self.rewrite(0, self.originals[0])

    def test_symlink_file_directory_or_profile_escape_is_rejected(self):
        path = self.base / CASE_NAMES[0] / 'case.json'
        moved = self.root.parent / 'outside-case.json'
        path.rename(moved)
        path.symlink_to(moved)
        self.reject()
        path.unlink(); moved.rename(path)
        directory = path.parent
        outside = self.root.parent / 'outside-case'
        directory.rename(outside); directory.symlink_to(outside, target_is_directory=True)
        self.reject()
        directory.unlink(); outside.rename(directory)
        profile = self.root / self.profile
        elsewhere = self.root.parent / 'outside-profile'
        profile.rename(elsewhere); profile.symlink_to(elsewhere, target_is_directory=True)
        self.reject()

    def test_case_changed_during_read_is_rejected(self):
        actual_read = Path.read_bytes
        target = self.base / CASE_NAMES[0] / 'case.json'
        def changing_read(path):
            payload = actual_read(path)
            if path == target: path.write_bytes(b'changed while reading')
            return payload
        with patch.object(Path, 'read_bytes', changing_read):
            self.reject()

    def test_directory_swapped_for_symlink_during_read_is_rejected(self):
        actual_read = Path.read_bytes
        target = self.base / CASE_NAMES[0] / 'case.json'
        outside = self.root.parent / 'moved-during-read'
        def changing_parent(path):
            payload = actual_read(path)
            if path == target:
                path.parent.rename(outside)
                path.parent.symlink_to(outside, target_is_directory=True)
            return payload
        with patch.object(Path, 'read_bytes', changing_parent):
            self.reject()

    def test_wrong_evidence_root_and_inline_case_payload_are_rejected(self):
        self.root = self.root.parent
        self.reject()
        self.root = self.root / self.source
        self.summary['cases'] = self.originals
        self.reject()


if __name__ == '__main__':
    unittest.main()
