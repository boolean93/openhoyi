"""Source verification and workflow permission gates, entirely offline."""
import json
from pathlib import Path
import unittest
from gitee_mirror_runner import authorize, run
from test_release_update_index import API, CERT


class RunnerTests(unittest.TestCase):
    def setUp(self):
        self.env = {'GITEE_MIRROR_ENABLED': 'true', 'GITHUB_REPOSITORY': 'boolean93/openhoyi',
                    'GITHUB_REF': 'refs/heads/develop', 'GITHUB_EVENT_NAME': 'workflow_dispatch',
                    'HOYI_RELEASE_CERT_SHA256': CERT}
        self.event = {'repository': {'full_name': 'boolean93/openhoyi'}}

    def test_manual_default_no_bootstrap(self):
        self.assertEqual(authorize(self.env, self.event), (CERT, False))
        self.assertEqual(authorize({**self.env, 'GITEE_ALLOW_FIRST_MANIFEST': 'true'}, self.event), (CERT, True))

    def test_disabled_wrong_source_and_bad_signer_fail(self):
        for changes in ({'GITEE_MIRROR_ENABLED': 'false'}, {'GITHUB_REF': 'refs/pull/1/merge'},
                        {'GITHUB_EVENT_NAME': 'pull_request'}, {'GITHUB_REPOSITORY': 'other/repo'},
                        {'HOYI_RELEASE_CERT_SHA256': 'untrusted'}):
            with self.subTest(changes=changes), self.assertRaises(ValueError):
                authorize({**self.env, **changes}, self.event)

    def test_workflow_run_requires_trusted_origin_and_no_bootstrap(self):
        env = {**self.env, 'GITHUB_EVENT_NAME': 'workflow_run'}
        run_event = {**self.event, 'action': 'completed', 'workflow_run': {
            'status': 'completed', 'repository': self.event['repository'],
            'head_repository': self.event['repository'], 'head_branch': 'develop',
            'path': '.github/workflows/develop-apk.yml', 'event': 'workflow_run'}}
        self.assertEqual(authorize(env, run_event), (CERT, False))
        with self.assertRaises(ValueError): authorize({**env, 'GITEE_ALLOW_FIRST_MANIFEST': 'true'}, run_event)
        run_event['workflow_run']['head_repository'] = {'full_name': 'attacker/fork'}
        with self.assertRaises(ValueError): authorize(env, run_event)

    def test_verified_capture_exact_bytes_passed_to_publisher(self):
        api = API()
        def builder(api, cert, aapt, signer, download):
            import release_update_index
            return release_update_index.build_index(api, cert, aapt, signer, download, api.inspect)
        def publisher(index, root, backend, aapt, signer, allow_first_manifest):
            self.assertFalse(allow_first_manifest)
            for name, data in api.files.items(): self.assertEqual((root/name).read_bytes(), data)
            return {'status': 'fixture-complete'}
        self.assertEqual(run(api, object(), CERT, 'aapt', 'apksigner', download=api.download,
                             builder=builder, publisher=publisher), {'status': 'fixture-complete'})

    def test_workflow_off_by_default_no_untrusted_checkout(self):
        text = (Path(__file__).resolve().parent.parent/'.github/workflows/gitee-mirror.yml').read_text()
        self.assertIn("vars.GITEE_MIRROR_ENABLED == 'true'", text)
        self.assertIn('environment: gitee-mirror', text)
        self.assertIn('ref: refs/heads/develop', text)
        self.assertNotIn('ref: ${{', text)
        self.assertIn('cancel-in-progress: false', text)
        self.assertNotIn('pull_request:', text)
        self.assertNotIn('contents: write', text)

if __name__ == '__main__': unittest.main()
