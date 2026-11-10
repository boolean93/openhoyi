"""Offline release summary regressions; actual visibility baseline, not build plans."""
import unittest
from unittest import mock

import develop_release as runner


class ChangelogTests(unittest.TestCase):
    def record(self, source, code=40, channel='develop-test', draft=False):
        return dict(channel=channel, versionCode=code, declaredSourceCommit=source,
                    _release=dict(draft=draft))

    def pull(self, source, number, note='修复冲煮页面显示。', title='historical title'):
        return dict(number=number, merged_at='2026-11-10', merge_commit_sha=source,
                    body='## 本次更新\n\n' + note + '\n\n## 验证\npassed', title=title,
                    base=dict(ref='develop', repo=dict(full_name=runner.REPO)))

    def test_batch_and_missing_build_use_latest_actual_public_same_channel(self):
        api = mock.Mock()
        api.pages.side_effect = lambda path: {
            'commits/b/pulls': [self.pull('b', 2)],
            'commits/c/pulls': [self.pull('c', 3, note='', title='旧版变更标题')],
        }[path]
        records = [self.record('a'), self.record('c', 100, draft=True),
                   self.record('unrelated', 99, channel='rc')]
        with mock.patch.object(runner, 'git', return_value='c\nb\na\nroot'):
            body = runner.release_body(api, 'c', records, '构建信息及校验说明')
        self.assertEqual(body, '## 本次更新\n\n- 修复冲煮页面显示。（#2）\n- 旧版变更标题（#3）\n\n构建信息及校验说明')
        # Next publication in the same batch excludes changes already made public.
        api.pages.side_effect = lambda path: [self.pull('c', 3)]
        with mock.patch.object(runner, 'git', return_value='c\nb\na\nroot'):
            body = runner.release_body(api, 'c', [self.record('b', 41)], '证据')
        self.assertNotIn('#2', body)
        self.assertIn('#3', body)

    def test_first_release_and_idempotent_section_replacement(self):
        api = mock.Mock()
        api.pages.return_value = []
        with mock.patch.object(runner, 'configuration', return_value=dict(startAfterCommit='root')), \
                mock.patch.object(runner, 'git', side_effect=['a\nroot', '实际提交标题', 'a\nroot', '实际提交标题']):
            body = runner.release_body(api, 'a', [], '哈希与风险说明')
            self.assertEqual(runner.release_body(api, 'a', [], body), body)
        self.assertTrue(body.startswith('## 本次更新\n'))
        self.assertIn('实际提交标题', body)
        self.assertIn('哈希与风险说明', body)

    def test_out_of_order_reports_omitted_changes(self):
        api = mock.Mock()
        api.pages.return_value = [self.pull('later', 9)]
        with mock.patch.object(runner, 'git', side_effect=['a\nroot', 'later\na\nroot']):
            body = runner.release_body(api, 'a', [self.record('later')], '安全说明')
        self.assertIn('此版使用较早源码', body)
        self.assertIn('此版不含：修复冲煮页面显示。', body)
        self.assertIn('/compare/a...later', body)
        self.assertTrue(body.endswith('安全说明'))

    def test_unrelated_history_stops(self):
        with mock.patch.object(runner, 'git', side_effect=['a\nroot', 'other\nroot']):
            with self.assertRaisesRegex(runner.PublisherError, 'first-parent'):
                runner.release_body(mock.Mock(), 'a', [self.record('other')], 'evidence')

    def test_notes_escape_markdown_html_and_mentions(self):
        note = runner.safe_note('<b>@everyone</b> [link](https://evil) ![image](x)')
        self.assertNotIn('<b>', note)
        self.assertNotIn('@everyone', note)
        self.assertIn(r'\[link\]\(https&#58;//evil\)', note)

    def test_only_actual_merged_develop_prs_are_included(self):
        api = mock.Mock()
        valid = self.pull('a', 1)
        wrong = self.pull('other', 2)
        unmerged = {**self.pull('a', 3), 'merged_at': None}
        api.pages.return_value = [valid, valid, wrong, unmerged]
        with mock.patch.object(runner, 'git', return_value='a\nroot'):
            body = runner.release_body(api, 'a', [self.record('root')], 'evidence')
        self.assertEqual(body.count('#1'), 1)
        self.assertNotIn('#2', body)
        self.assertNotIn('#3', body)


if __name__ == '__main__':
    unittest.main()
