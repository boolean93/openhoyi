"""Fixed-repository Gitee backend; no credentials are read from the environment.

Construct explicitly with a caller-provided token only in an approved protected job.
All reads are anonymous. Write tokens are body fields, never URLs or redirect headers.
This adapter is tested against a fake HTTP service; real token scope remains unverified.
"""
import base64
import json
import re
import secrets
import urllib.error
import urllib.parse
import urllib.request

from gitee_mirror_plan import DESTINATION, MAX_ATTACHMENT
from gitee_mirror_publish import ManifestState, OutcomeUnknown, PublishError

API = 'https://gitee.com/api/v5/repos/boolean93/openhoyi'
MANIFEST = 'contents/updates/dev.json'
BRANCH = 'main'


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl): return None


class Transport:
    """Single attempt, bounded body, no redirects; errors never include URLs/bodies."""
    def request(self, method, url, body=None, content_type=None, limit=2_000_000):
        headers = {'Accept': 'application/json', 'User-Agent': 'OpenHOYI-Mirror'}
        if content_type: headers['Content-Type'] = content_type
        request = urllib.request.Request(url, data=body, headers=headers, method=method)
        try:
            response = urllib.request.build_opener(NoRedirect).open(request, timeout=30)
        except urllib.error.HTTPError as error:
            response = error
        except (OSError, urllib.error.URLError):
            if method != 'GET': raise OutcomeUnknown('Remote mutation outcome unknown') from None
            raise PublishError('Remote read unavailable') from None
        with response:
            try:
                data = b'' if 300 <= response.status < 400 else response.read(limit + 1)
            except (OSError, ValueError):
                if method != 'GET': raise OutcomeUnknown('Remote mutation response interrupted') from None
                raise PublishError('Remote response interrupted') from None
            if len(data) > limit: raise PublishError('Remote response exceeds limit')
            return response.status, dict(response.headers), data


class GiteeBackend:
    def __init__(self, token, transport=None):
        if not isinstance(token, str) or not token or '\r' in token or '\n' in token:
            raise PublishError('A protected-job token is required')
        self._token = token
        self.http = transport or Transport()

    def api(self, method, path, payload=None, missing=False, multipart=None):
        # Paths are internal constants plus validated numeric IDs/tags only.
        body, mime = None, None
        if method != 'GET':
            if multipart is not None:
                name, data = multipart
                boundary = 'OpenHOYI' + secrets.token_hex(16)
                body = (f'--{boundary}\r\nContent-Disposition: form-data; name="access_token"\r\n\r\n'
                        f'{self._token}\r\n--{boundary}\r\nContent-Disposition: form-data; name="file"; '
                        f'filename="{name}"\r\nContent-Type: application/octet-stream\r\n\r\n').encode() + data + f'\r\n--{boundary}--\r\n'.encode()
                mime = 'multipart/form-data; boundary=' + boundary
            else:
                body = json.dumps({**payload, 'access_token': self._token}).encode()
                mime = 'application/json'
        status, _, raw = self.http.request(method, API + '/' + path, body, mime)
        if method != 'GET' and status >= 500:
            raise OutcomeUnknown('Remote mutation outcome unknown')
        if status == 404 and missing and method == 'GET': return None
        if status not in (200, 201): raise PublishError('Gitee API rejected request')
        try: return json.loads(raw)
        except (ValueError, UnicodeError): raise PublishError('Invalid Gitee JSON response') from None

    def manifest(self):
        value = self.api('GET', MANIFEST + '?ref=' + BRANCH, missing=True)
        if value is None: return None
        try:
            if value['encoding'] != 'base64' or not re.fullmatch('[0-9a-f]{40}', value['sha']):
                raise ValueError()
            raw = base64.b64decode(''.join(value['content'].split()), validate=True)
            return ManifestState(value['sha'], json.loads(raw))
        except (KeyError, TypeError, ValueError): raise PublishError('Invalid Gitee manifest file') from None

    def release(self, tag):
        if not re.fullmatch(r'dev-[1-9][0-9]*', tag): raise PublishError('Invalid release tag')
        value = self.api('GET', 'releases/tags/' + tag, missing=True)
        if value is None: return None
        if value.get('tag_name') != tag or type(value.get('id')) is not int or value['id'] <= 0:
            raise PublishError('Invalid Gitee release identity')
        return value['id']

    def create_release(self, tag):
        if not re.fullmatch(r'dev-[1-9][0-9]*', tag): raise PublishError('Invalid release tag')
        # Empty repo must have a separately authorized bootstrap; never initialize here.
        self.api('GET', 'branches/' + BRANCH)
        self.api('POST', 'releases', {'tag_name': tag, 'name': 'OpenHOYI ' + tag,
                 'body': 'GitHub Release mirror: https://github.com/boolean93/openhoyi/releases/tag/' + tag,
                 'target_commitish': BRANCH, 'prerelease': True})

    @staticmethod
    def release_path(release_id):
        if type(release_id) is not int or release_id <= 0: raise PublishError('Invalid release id')
        return f'releases/{release_id}/attach_files'

    def attachments(self, release_id):
        value = self.api('GET', self.release_path(release_id))
        if not isinstance(value, list) or any(not isinstance(x, dict) or not isinstance(x.get('name'), str) for x in value):
            raise PublishError('Invalid Gitee attachment list')
        return [x['name'] for x in value]

    def upload(self, release_id, name, data):
        if not re.fullmatch(r'[A-Za-z0-9][A-Za-z0-9._-]*', name) or not 0 < len(data) <= MAX_ATTACHMENT:
            raise PublishError('Invalid mirror attachment')
        self.api('POST', self.release_path(release_id), multipart=(name, data))

    def anonymous(self, url, limit):
        parsed = urllib.parse.urlsplit(url)
        if (parsed.scheme != 'https' or parsed.netloc != 'gitee.com' or parsed.query or parsed.fragment
                or not re.fullmatch(r'/boolean93/openhoyi/releases/download/dev-[1-9][0-9]*/[A-Za-z0-9][A-Za-z0-9._-]*', parsed.path)):
            raise PublishError('Invalid anonymous source URL')
        name = parsed.path.rsplit('/', 1)[1]
        for _ in range(5):
            status, headers, data = self.http.request('GET', url, limit=limit)
            if status == 200: return data
            location = next((v for k, v in headers.items() if k.lower() == 'location'), None)
            if status not in (301, 302, 303, 307, 308) or not location:
                raise PublishError('Anonymous mirror download unavailable')
            url = urllib.parse.urljoin(url, location)
            u = urllib.parse.urlsplit(url)
            allowed = ((u.netloc == 'gitee.com' and re.fullmatch(r'/boolean93/openhoyi/attach_files/[0-9]+/download/' + re.escape(name), u.path))
                       or (u.netloc == 'foruda.gitee.com' and re.fullmatch(r'/attach_file/[0-9]+/' + re.escape(name), u.path)))
            if u.scheme != 'https' or u.fragment or not allowed:
                raise PublishError('Anonymous redirect destination rejected')
        raise PublishError('Anonymous redirect limit exceeded')

    def compare_and_swap(self, previous, index):
        payload = {'content': base64.b64encode((json.dumps(index, ensure_ascii=False, indent=2) + '\n').encode()).decode(),
                   'message': 'Publish verified ' + index['releaseTag'] + ' mirror index', 'branch': BRANCH}
        if previous is not None:
            payload['sha'] = previous.revision
        self.api('PUT' if previous else 'POST', MANIFEST, payload)

    def published_manifest(self):
        path = '/boolean93/openhoyi/raw/main/updates/dev.json'
        url = 'https://gitee.com' + path
        for _ in range(3):
            status, headers, raw = self.http.request('GET', url, limit=1_048_576)
            if status == 200:
                try: return json.loads(raw)
                except (ValueError, UnicodeError): raise PublishError('Invalid public mirror manifest') from None
            location = next((v for k, v in headers.items() if k.lower() == 'location'), None)
            if status not in (301, 302, 303, 307, 308) or location != 'https://raw.giteeusercontent.com' + path:
                raise PublishError('Public mirror manifest unavailable')
            url = location
        raise PublishError('Public manifest redirect loop')
