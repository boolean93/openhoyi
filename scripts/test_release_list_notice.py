"""Offline release body updates; no GitHub writes."""
from copy import deepcopy
import unittest
import release_list_notice as notice
from test_release_update_index import fixture


class API:
    def __init__(self):
        self.releases = [fixture(n)[2] for n in (99, 104, 100)]
        for r in self.releases:
            r['body'] = '## 本次更新\n\n- 保留原更新说明。\n\n源码与安全说明。\n'
        self.writes = []

    def pages(self, path):
        return deepcopy(self.releases)

    def request(self, path, method='GET', data=None):
        release = next(r for r in self.releases if r['id'] == int(path.split('/')[-1]))
        if method == 'PATCH':
            self.writes.append(deepcopy(data))
            release.update(data)
        return deepcopy(release)


class NoticeTests(unittest.TestCase):
    def test_numeric_latest_on_first_item_only_body_and_idempotent(self):
        api = API()
        before = deepcopy(api.releases)
        notice.sync_notice(api)
        self.assertEqual(len(api.writes), 1)
        self.assertEqual(set(api.writes[0]), {'body'})
        self.assertIn('/releases/tag/dev-104', api.releases[0]['body'])
        self.assertTrue(api.releases[0]['body'].endswith(before[0]['body'][len(notice.HEADING):]))
        notice.sync_notice(api)
        self.assertEqual(len(api.writes), 1)
        for old, new in zip(before, api.releases):
            self.assertEqual({k:v for k,v in old.items() if k != 'body'}, {k:v for k,v in new.items() if k != 'body'})

    def test_new_first_and_previous_notice_both_advance(self):
        api = API()
        notice.sync_notice(api)
        newer = fixture(105)[2]
        newer['body'] = '## 本次更新\n\n- 新版\n'
        api.releases.insert(0, newer)
        notice.sync_notice(api)
        for r in api.releases[:2]:
            self.assertEqual(r['body'].count(notice.START), 1)
            self.assertIn('/releases/tag/dev-105', r['body'])

    def test_refuse_stable_first_or_ambiguous_notice(self):
        api = API()
        api.releases[0]['prerelease'] = False
        with self.assertRaises(ValueError):
            notice.sync_notice(api)
        self.assertEqual(api.writes, [])
        with self.assertRaises(ValueError):
            notice.notice_body(notice.HEADING + notice.START, 'dev-104')


if __name__ == '__main__':
    unittest.main()
