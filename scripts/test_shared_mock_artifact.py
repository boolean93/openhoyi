import copy
import json
import os
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import shared_mock_artifact as shared


class SharedPairTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.folder = Path(self.tmp.name)
        self.sha = 'a' * 40
        self.environment = {'CI_REPOSITORY': 'owner/repo', 'CI_EVENT_NAME': 'pull_request',
                            'CI_REF': 'refs/pull/5/merge', 'CI_RUN_ID': '123',
                            'CI_WORKFLOW_REF': 'owner/repo/.github/workflows/single-screen-ui.yml@refs/pull/5/merge',
                            'CI_WORKFLOW_SHA': self.sha, 'CI_EXPECTED_SHA': self.sha,
                            'GITHUB_RUN_ATTEMPT': '2'}
        self.env = patch.dict(os.environ, self.environment)
        self.env.start()
        self.addCleanup(self.env.stop)
        self.checkout = {'sha': self.sha, 'tree': 'b' * 40, 'configHashes': {'build.gradle.kts': 'c' * 64}}
        self.tools = {'java': '17', 'gradle': '8.13'}
        self.actual = {}
        files = {}
        for name in shared.DESTINATIONS:
            (self.folder / name).write_bytes(name.encode())
            self.actual[name] = {'package': shared.APP if name == 'app.apk' else shared.TEST,
                                 'certificateSha256': 'd' * 64, 'versionCode': '1',
                                 'runners': {} if name == 'app.apk' else shared.RUNNERS}
            files[name] = {'size': (self.folder / name).stat().st_size,
                           'sha256': shared.digest(self.folder / name), 'apk': self.actual[name]}
        self.manifest = {'schema': 'same-run-mock-pair-v1', 'identity': shared.identity(),
                         'producerAttempt': '1', 'checkout': copy.deepcopy(self.checkout),
                         'command': shared.COMMAND, 'properties': {}, 'variant': 'mock',
                         'mockMode': True, 'tools': self.tools, 'files': copy.deepcopy(files)}

    def write(self):
        path = self.folder / 'manifest.json'
        path.write_text(json.dumps(self.manifest))
        return shared.digest(path)

    def validate(self, result='success', manifest_hash=None):
        return shared.validate(self.folder, manifest_hash or self.write(), self.sha, '1', result,
                               self.checkout, self.tools, inspect=lambda path, name: self.actual[name])

    def test_same_run_pair_accepts_needs_bound_original_producer_attempt(self):
        self.assertEqual(self.sha, self.validate()['checkout']['sha'])

    def test_failed_cancelled_missing_producer_never_falls_back(self):
        for result in ('failure', 'cancelled', 'skipped', ''):
            with self.subTest(result=result), self.assertRaises(ValueError):
                self.validate(result)

    def test_wrong_source_tree_run_event_attempt_or_command_fails(self):
        original = copy.deepcopy(self.manifest)
        mutations = [lambda m: m['identity'].update(run_id='122'),
                     lambda m: m['identity'].update(repository='other/repo'),
                     lambda m: m['identity'].update(expected_sha='e' * 40),
                     lambda m: m['identity'].update(event_name='push'),
                     lambda m: m['checkout'].update(sha='f' * 40),
                     lambda m: m['checkout'].update(tree='e' * 40),
                     lambda m: m['checkout'].update(configHashes={}),
                     lambda m: m.update(producerAttempt='2'),
                     lambda m: m.update(command='./gradlew other'),
                     lambda m: m.update(properties={'hoyiVersionCode': '2'}),
                     lambda m: m.update(mockMode=False)]
        for mutate in mutations:
            self.manifest = copy.deepcopy(original)
            mutate(self.manifest)
            with self.subTest(manifest=self.manifest), self.assertRaises(ValueError):
                self.validate()

    def test_actual_version_runner_certificate_mismatch_fails(self):
        for key, value in [('versionCode', '2'), ('runners', {}), ('certificateSha256', 'e' * 64)]:
            old = self.manifest['files']['test.apk']['apk'][key]
            self.manifest['files']['test.apk']['apk'][key] = value
            with self.subTest(key=key), self.assertRaises(ValueError):
                self.validate()
            self.manifest['files']['test.apk']['apk'][key] = old

    def test_corrupted_swapped_missing_extra_and_symlink_files_fail(self):
        self.write()
        for action in ('corrupt', 'swap', 'missing', 'extra', 'symlink'):
            with self.subTest(action=action):
                for name in ('app.apk', 'test.apk', 'extra'):
                    path = self.folder / name
                    if path.exists() or path.is_symlink():
                        path.unlink()
                for name in shared.DESTINATIONS:
                    (self.folder / name).write_bytes(name.encode())
                if action == 'corrupt':
                    (self.folder / 'app.apk').write_bytes(b'corrupt')
                elif action == 'swap':
                    (self.folder / 'app.apk').write_bytes(b'test.apk')
                    (self.folder / 'test.apk').write_bytes(b'app.apk')
                elif action == 'missing':
                    (self.folder / 'test.apk').unlink()
                elif action == 'extra':
                    (self.folder / 'extra').write_text('unexpected')
                else:
                    (self.folder / 'test.apk').unlink()
                    (self.folder / 'test.apk').symlink_to(self.folder / 'app.apk')
                with self.assertRaises(ValueError):
                    self.validate()

    def test_manifest_digest_from_producer_is_independent_gate(self):
        self.write()
        with self.assertRaises(ValueError):
            self.validate(manifest_hash='0' * 64)

    def test_missing_sha_and_hash_outputs_fail(self):
        self.write()
        for hash_value, sha in [('', self.sha), ('0' * 64, '')]:
            with self.assertRaises(ValueError):
                shared.validate(self.folder, hash_value, sha, '1', 'success', self.checkout, self.tools)

    def test_real_apk_parser_requires_identity_sdk_version_runners_and_signature(self):
        badging = "package: name='io.openhoyi.mobile.mock.test' versionCode='' versionName='' compileSdkVersion='35'\nsdkVersion:'26'\ntargetSdkVersion:'35'\napplication-debuggable"
        xml = '\n'.join('    E: instrumentation (line=1)\n      A: android:name(0x1)="' + name + '"\n      A: android:targetPackage(0x2)="' + target + '"' for name, target in shared.RUNNERS.items())
        signing = 'Verified using v2 scheme (APK Signature Scheme v2): true\nSigner #1 certificate DN: C=US, O=Android, CN=Android Debug\nSigner #1 certificate SHA-256 digest: ' + 'd' * 64
        outputs = [badging, xml, signing]
        with patch.object(shared, 'sdk_tool', side_effect=lambda name: name), patch.object(shared, 'run', side_effect=outputs):
            self.assertEqual(shared.RUNNERS, shared.inspect_apk('test.apk', 'test.apk')['runners'])
        bad_cases = [(badging.replace('versionCode=\'\'', 'versionCode=\'2\''), xml, signing),
                     (badging.replace("compileSdkVersion='35'", "compileSdkVersion='34'"), xml, signing),
                     (badging, xml.replace(shared.TEST + '"', shared.APP + '"'), signing),
                     (badging, xml, signing.replace('CN=Android Debug', 'CN=Release')),
                     (badging, xml, signing.replace('true', 'false'))]
        for values in bad_cases:
            with self.subTest(values=values), patch.object(shared, 'sdk_tool', side_effect=lambda name: name), patch.object(shared, 'run', side_effect=values), self.assertRaises(ValueError):
                shared.inspect_apk('test.apk', 'test.apk')
