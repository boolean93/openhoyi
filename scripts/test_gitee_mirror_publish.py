"""Failure-injected transaction tests. No credentials, network or real APKs."""
from copy import deepcopy
import unittest

from gitee_mirror_publish import ManifestState, OutcomeUnknown, publish
import test_gitee_mirror_plan as fixture


class Fake:
    def __init__(self):
        self.state = None
        self.id = None
        self.files = {}
        self.events = []
        self.lose = set()
        self.skip = set()
        self.corrupt = False
        self.before_final_read = None
        self.reads = 0

    def manifest(self):
        self.reads += 1
        if self.reads == 2 and self.before_final_read:
            self.before_final_read(self)
        return deepcopy(self.state)

    def release(self, tag): return self.id

    def create_release(self, tag):
        self.events.append('release')
        if 'release' not in self.skip: self.id = 1
        if 'release' in self.lose: raise OutcomeUnknown()

    def attachments(self, release_id): return list(self.files)

    def upload(self, release_id, name, data):
        self.events.append(name)
        if name not in self.skip: self.files[name] = data
        if name in self.lose: raise OutcomeUnknown()

    def anonymous(self, url, limit):
        self.events.append('readback')
        return b'bad' if self.corrupt else self.files[url.rsplit('/', 1)[1]]

    def published_manifest(self): return deepcopy(self.state.index)

    def compare_and_swap(self, previous, index):
        self.events.append('manifest')
        if previous != self.state: raise ValueError('CAS conflict')
        if 'manifest' not in self.skip: self.state = ManifestState('revision', deepcopy(index))
        if 'manifest' in self.lose: raise OutcomeUnknown()


class PublishTests(unittest.TestCase):
    def setUp(self):
        fixture.MirrorPlanTest.setUp(self)
        self.backend = Fake()

    def run_publish(self, **kwargs):
        return publish(self.index, self.root, self.backend, 'aapt', 'apksigner',
                       inspect=self.api.inspect, allow_first_manifest=True, **kwargs)

    def test_order_and_idempotent_rerun(self):
        self.assertEqual(self.run_publish()['status'], 'published')
        self.assertEqual(self.backend.events[-1], 'manifest')
        self.backend.events.clear()
        self.assertEqual(self.run_publish()['status'], 'already-published')
        self.assertEqual(self.backend.events, ['readback'] * 3)

    def test_lost_success_responses_reconcile_without_duplicates(self):
        self.backend.lose = {'release', 'manifest', *self.api.files}
        self.assertEqual(self.run_publish()['status'], 'published')
        self.assertEqual(len(self.backend.files), 3)
        self.assertEqual(self.backend.events.count('manifest'), 1)
        self.assertEqual(self.backend.events.count('release'), 1)

    def test_unknown_missing_outcome_never_reposts(self):
        for operation in ('release', self.index['apkFile'], 'manifest'):
            with self.subTest(operation=operation):
                self.backend = Fake()
                self.backend.lose = self.backend.skip = {operation}
                with self.assertRaises(ValueError): self.run_publish()
                self.assertEqual(self.backend.events.count(operation), 1)
                self.assertIsNone(self.backend.state)

    def test_conflicting_asset_is_not_overwritten(self):
        self.backend.id = 1
        self.backend.files[self.index['apkFile']] = b'other'
        with self.assertRaises(ValueError): self.run_publish()
        self.assertNotIn(self.index['apkFile'], self.backend.events)
        self.assertIsNone(self.backend.state)

    def test_bad_anonymous_readback_blocks_manifest(self):
        self.backend.corrupt = True
        with self.assertRaises(ValueError): self.run_publish()
        self.assertNotIn('manifest', self.backend.events)

    def test_signature_failure_occurs_before_any_mutation(self):
        def bad(*args): raise ValueError('invalid signature')
        with self.assertRaises(ValueError):
            publish(self.index, self.root, self.backend, 'a', 's', inspect=bad,
                    allow_first_manifest=True)
        self.assertEqual(self.backend.events, [])

    def test_missing_baseline_needs_explicit_bootstrap(self):
        with self.assertRaises(ValueError):
            publish(self.index, self.root, self.backend, 'a', 's', inspect=self.api.inspect)
        self.assertEqual(self.backend.events, [])

    def test_concurrent_identity_change_blocks_manifest(self):
        other = {**self.index, 'apkSha256': 'f' * 64}
        self.backend.before_final_read = lambda b: setattr(b, 'state', ManifestState('new', other))
        with self.assertRaises(ValueError): self.run_publish()
        self.assertNotIn('manifest', self.backend.events)

    def test_public_manifest_cache_lag_not_reported_as_success(self):
        self.backend.published_manifest = lambda: {}
        with self.assertRaises(ValueError): self.run_publish()
        self.assertIsNotNone(self.backend.state)
        with self.assertRaises(ValueError): self.run_publish()
        self.assertEqual(self.backend.events.count('manifest'), 1)

    def test_read_failure_not_treated_as_missing(self):
        def fail(): raise OSError('network unavailable')
        self.backend.manifest = fail
        with self.assertRaises(OSError): self.run_publish()
        self.assertEqual(self.backend.events, [])

if __name__ == '__main__': unittest.main()
