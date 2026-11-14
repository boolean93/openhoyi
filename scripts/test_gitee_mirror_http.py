"""HTTP adapter contract tests with entirely local fake transport."""
import base64
import json
import unittest
from gitee_mirror_http import API, GiteeBackend
from gitee_mirror_publish import ManifestState, OutcomeUnknown


class HTTP:
    def __init__(self, *responses):
        self.responses = list(responses)
        self.calls = []
    def request(self, method, url, body=None, content_type=None, limit=2_000_000):
        self.calls.append((method, url, body, content_type))
        return self.responses.pop(0)


def ok(value): return (200, {}, json.dumps(value).encode())


class HTTPTests(unittest.TestCase):
    def test_anonymous_reads_and_token_in_write_body_only(self):
        http = HTTP(ok({'tag_name': 'dev-42', 'id': 7}), ok({}), ok({}))
        backend = GiteeBackend('fake-token-for-offline-test', http)
        self.assertEqual(backend.release('dev-42'), 7)
        backend.create_release('dev-42')
        for method, url, body, _ in http.calls:
            self.assertNotIn('fake-token', url)
            if method == 'GET': self.assertIsNone(body)
            else: self.assertEqual(json.loads(body)['access_token'], 'fake-token-for-offline-test')

    def test_upload_is_multipart_and_never_follows_write_redirect(self):
        http = HTTP((302, {'Location': 'https://evil.test/'}, b''))
        backend = GiteeBackend('fake', http)
        with self.assertRaises(ValueError): backend.upload(7, 'app.apk', b'apk')
        self.assertEqual(len(http.calls), 1)
        method, url, body, mime = http.calls[0]
        self.assertEqual(url, API + '/releases/7/attach_files')
        self.assertIn('multipart/form-data', mime)
        self.assertIn(b'name="access_token"', body)
        self.assertIn(b'filename="app.apk"', body)

    def test_mutation_server_error_is_unknown_and_no_retry(self):
        http = HTTP((503, {}, b'private details'))
        with self.assertRaises(OutcomeUnknown): GiteeBackend('fake', http).upload(7, 'app.apk', b'apk')
        self.assertEqual(len(http.calls), 1)

    def test_get_404_only_is_missing(self):
        self.assertIsNone(GiteeBackend('fake', HTTP((404, {}, b''))).manifest())
        for code in (401, 403, 429, 500):
            with self.subTest(code=code), self.assertRaises(ValueError):
                GiteeBackend('fake', HTTP((code, {}, b''))).manifest()

    def test_manifest_revision_and_cas_payload(self):
        content = base64.b64encode(b'{"example":1}').decode()
        http = HTTP(ok({'encoding': 'base64', 'sha': 'a'*40, 'content': content}), ok({}))
        backend = GiteeBackend('fake', http)
        previous = backend.manifest()
        self.assertEqual(previous, ManifestState('a'*40, {'example': 1}))
        backend.compare_and_swap(previous, {'releaseTag': 'dev-42'})
        method, _, body, _ = http.calls[-1]
        self.assertEqual(method, 'PUT')
        self.assertEqual(json.loads(body)['sha'], 'a'*40)
        self.assertEqual(json.loads(body)['branch'], 'main')

    def test_realistic_anonymous_redirect_contract(self):
        first = 'https://gitee.com/boolean93/openhoyi/releases/download/dev-42/app.apk'
        intermediate = 'https://gitee.com/boolean93/openhoyi/attach_files/123/download/app.apk'
        final = 'https://foruda.gitee.com/attach_file/456/app.apk?signature=public-temporary'
        http = HTTP((302, {'Location': intermediate}, b''), (302, {'Location': final}, b''), (200, {}, b'apk'))
        self.assertEqual(GiteeBackend('fake', http).anonymous(first, 3), b'apk')
        self.assertTrue(all(method == 'GET' and body is None for method, _, body, _ in http.calls))

    def test_bad_redirects_rejected(self):
        first = 'https://gitee.com/boolean93/openhoyi/releases/download/dev-42/app.apk'
        for dest in ('http://foruda.gitee.com/attach_file/1/app.apk',
                     'https://evil.test/app.apk',
                     'https://gitee.com/other/repo/attach_files/1/download/app.apk',
                     'https://foruda.gitee.com/attach_file/1/wrong.apk',
                     'https://foruda.gitee.com:443/attach_file/1/app.apk'):
            with self.subTest(dest=dest), self.assertRaises(ValueError):
                GiteeBackend('fake', HTTP((302, {'Location': dest}, b''))).anonymous(first, 3)

    def test_raw_redirect_and_foreign_rejection(self):
        target = 'https://raw.giteeusercontent.com/boolean93/openhoyi/raw/main/updates/dev.json'
        http = HTTP((302, {'Location': target}, b''), ok({'versionCode': 42}))
        self.assertEqual(GiteeBackend('fake', http).published_manifest(), {'versionCode': 42})
        with self.assertRaises(ValueError):
            GiteeBackend('fake', HTTP((302, {'Location': target.replace('openhoyi', 'other')}, b''))).published_manifest()

if __name__ == '__main__': unittest.main()
