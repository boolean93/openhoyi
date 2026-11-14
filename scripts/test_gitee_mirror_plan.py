"""No network, credentials, APK tools or external writes."""
from copy import deepcopy
import hashlib
import json
from pathlib import Path
import tempfile
import unittest

from gitee_mirror_plan import plan
from test_release_update_index import API


class MirrorPlanTest(unittest.TestCase):
    def setUp(self):
        self.api = API()
        self.index = self.api.build()
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        for name, data in self.api.files.items():
            (self.root / name).write_bytes(data)

    def test_plan_preserves_original_urls_and_verification_gate(self):
        result = plan(self.index, self.root)
        self.assertEqual(result['mode'], 'offline-plan-only')
        self.assertTrue(result['requiresActualApkSignatureVerification'])
        self.assertEqual(result['candidateManifest']['downloadUrl'], self.index['downloadUrl'])
        self.assertEqual(len(result['assets']), 3)
        self.assertIn('commit-updates/dev.json-last', result['orderedSteps'][-1])
        self.assertEqual(plan(self.index, self.root, self.index), result)

    def test_bad_apk_and_missing_attachment_fail(self):
        apk = self.root / self.index['apkFile']
        apk.write_bytes(b'wrong')
        with self.assertRaises(ValueError): plan(self.index, self.root)
        apk.unlink()
        with self.assertRaises(ValueError): plan(self.index, self.root)

    def test_symlink_fails(self):
        p = self.root / 'SHA256SUMS.txt'
        p.unlink()
        p.symlink_to(self.root / 'distribution.json')
        with self.assertRaises(ValueError): plan(self.index, self.root)

    def test_conflicting_manifest_fails_even_with_matching_checksum(self):
        p = self.root / 'distribution.json'
        m = json.loads(p.read_text()); m['versionCode'] += 1
        p.write_text(json.dumps(m))
        (self.root / 'SHA256SUMS.txt').write_text(''.join(
            hashlib.sha256((self.root/n).read_bytes()).hexdigest()+'  '+n+'\n'
            for n in [self.index['apkFile'], 'distribution.json']))
        with self.assertRaises(ValueError): plan(self.index, self.root)

    def test_wrong_checksum_fails(self):
        (self.root/'SHA256SUMS.txt').write_text('wrong')
        with self.assertRaises(ValueError): plan(self.index,self.root)

    def test_previous_signer_and_same_version_identity_fail(self):
        old=deepcopy(self.index);old['signerCertificateSha256']='f'*64
        with self.assertRaises(ValueError):plan(self.index,self.root,old)
        old=deepcopy(self.index);old['apkSha256']='f'*64
        with self.assertRaises(ValueError):plan(self.index,self.root,old)

    def test_downgrade_and_size_limit_fail(self):
        old=deepcopy(self.index);old['versionCode']=43;old['releaseTag']='dev-43'
        old['versionName']='1.0.0-dev.43'
        for k in ['releaseUrl','downloadUrl','distributionUrl','checksumsUrl']:
            old[k]=old[k].replace('dev-42','dev-43')
        with self.assertRaises(ValueError):plan(self.index,self.root,old)
        large=deepcopy(self.index);large['apkSize']=100_000_001
        with self.assertRaises(ValueError):plan(large,self.root)

    def test_wrong_source_and_path_traversal_fail(self):
        for updates in [{'repository':'other/repo'}, {'downloadUrl':'https://evil.test/file.apk'},
                        {'apkFile':'../outside.apk'}]:
            with self.subTest(updates=updates),self.assertRaises(ValueError):
                plan({**self.index,**updates},self.root)

if __name__ == '__main__': unittest.main()
