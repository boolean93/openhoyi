import importlib.util
from pathlib import Path
import sys
import unittest
import tempfile

LOCALIZATION = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(LOCALIZATION))
SPEC = importlib.util.spec_from_file_location("apk_resources", LOCALIZATION / "verify_apk_resources.py")
apk = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(apk)


class ApkResourcesTest(unittest.TestCase):
    def test_literal_percentage_is_valid_and_must_be_preserved(self):
        self.assertEqual([], apk.validator.validate({'battery': '%1$d%%'}, {'battery': 'Battery %1$d%%'}))
        self.assertTrue(apk.validator.validate({'battery': '%1$d%%'}, {'battery': '%1$d'}))
        self.assertTrue(apk.validator.validate({'battery': '%1$d%%'}, {'battery': '%1$d%'}))
    def test_split_feature_resources_are_verified_without_weakening_key_checks(self):
        with tempfile.TemporaryDirectory() as directory:
            root=Path(directory)
            (root/'values').mkdir()
            (root/'values'/'advanced.xml').write_text('<resources><string name="extra">Bean: %1$s\\nDose</string></resources>')
            for tag in apk.validator.LANGUAGES:
                (root/f'values-{tag}').mkdir()
                (root/f'values-{tag}'/'advanced.xml').write_text('<resources><string name="extra">'+tag+': %1$s\\nDose</string></resources>')
            source,catalogs=apk.extend_resources({'message':'中文'},{tag:{'message':tag} for tag in apk.validator.LANGUAGES},root)
            self.assertEqual('Bean: %1$s\nDose',source['extra'])
            self.assertEqual('en: %1$s\nDose',catalogs['en']['extra'])
            (root/'values-en'/'advanced.xml').unlink()
            with self.assertRaises(ValueError):apk.extend_resources({'message':'中文'},{tag:{'message':tag} for tag in apk.validator.LANGUAGES},root)
    def test_dump_parser_preserves_multiline_spaces_and_literal_quotes(self):
        dump = '\n'.join(['Binary APK', '  type string id=03',
            '    resource 0x7f030000 string/app_name', '      () "Alpha"',
            '    resource 0x7f030001 string/message', '      () "  First "quote"',
            '      ', '       Last  "', '      (en) "Translated\\path"',
            '  type style id=04', '    resource 0x7f040000 style/theme'])
        self.assertEqual({'app_name': {'': 'Alpha'}, 'message': {'': '  First "quote"\n\n Last  ', 'en': 'Translated\\path'}}, apk.parse_dump(dump))

    def test_real_aapt_empty_lines_and_final_dump_newline_are_preserved(self):
        dump = '    resource 0x1 string/a\n      () "one\n\n      two"\n'
        self.assertEqual({'a': {'': 'one\n\ntwo'}}, apk.parse_dump(dump))

    def test_missing_closing_quote_and_duplicate_config_are_rejected(self):
        for dump in ['    resource 0x1 string/a\n      () "unfinished',
                     '    resource 0x1 string/a\n      () "one"\n      () "two"']:
            with self.assertRaises(ValueError): apk.parse_dump(dump)

    def test_every_language_and_variant_identity_are_verified(self):
        source = {'app_name': 'Alpha', 'message': '中文'}
        catalogs = {tag: {'app_name': 'wrong', 'message': tag} for tag in apk.validator.LANGUAGES}
        table = {'app_name': {'': 'Mock'}, 'message': {'': '中文', **{tag: tag for tag in apk.validator.LANGUAGES}}}
        self.assertEqual(9, apk.verify(table, source, catalogs, 'Mock'))
        table['app_name']['en'] = 'wrong'
        with self.assertRaises(ValueError): apk.verify(table, source, catalogs, 'Mock')
        del table['app_name']['en']
        table['message']['ar'] = 'incorrect'
        with self.assertRaises(ValueError): apk.verify(table, source, catalogs, 'Mock')
        del table['message']['ar']
        with self.assertRaises(ValueError): apk.verify(table, source, catalogs, 'Mock')
