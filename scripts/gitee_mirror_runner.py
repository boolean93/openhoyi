#!/usr/bin/env python3
"""Protected-job entrypoint; verifies source using existing GitHub release pipeline.

No direct arbitrary index upload. Disabled unless explicit workflow variable is set.
"""
import argparse
import json
import os
from pathlib import Path
import re
import tempfile

from develop_release_store import GitHub
from gitee_mirror_http import GiteeBackend
from gitee_mirror_publish import publish
from release_update_index import authorize_event, build_index, public_bytes, REPO


def authorize(environment, event):
    if environment.get('GITEE_MIRROR_ENABLED') != 'true':
        raise ValueError('Mirror is disabled pending protected-environment setup')
    if environment.get('GITHUB_REPOSITORY') != REPO:
        raise ValueError('Unexpected mirror repository')
    name = environment.get('GITHUB_EVENT_NAME')
    if name not in ('workflow_dispatch', 'workflow_run'):
        raise ValueError('Unsupported mirror trigger')
    authorize_event(event, name, environment.get('GITHUB_REF'))
    first = environment.get('GITEE_ALLOW_FIRST_MANIFEST', 'false')
    if first not in ('false', 'true') or (first == 'true' and name != 'workflow_dispatch'):
        raise ValueError('First publication requires explicit manual bootstrap')
    certificate = environment.get('HOYI_RELEASE_CERT_SHA256', '')
    if not re.fullmatch('[0-9a-f]{64}', certificate):
        raise ValueError('Approved signer is missing')
    return certificate, first == 'true'


def run(api, backend, certificate, aapt, apksigner, first=False, *, download=public_bytes,
        builder=build_index, publisher=publish):
    # Capture EXACT verified source bytes, rather than redownloading mutable URLs.
    captured = {}
    def capture(url, limit):
        data = download(url, limit)
        captured[url] = data
        return data
    index = builder(api, certificate, aapt, apksigner, download=capture)
    with tempfile.TemporaryDirectory(prefix='openhoyi-mirror-source-') as directory:
        root = Path(directory)
        for name, key in ((index['apkFile'], 'downloadUrl'), ('distribution.json', 'distributionUrl'),
                          ('SHA256SUMS.txt', 'checksumsUrl')):
            if not re.fullmatch(r'[A-Za-z0-9][A-Za-z0-9._-]*', name):
                raise ValueError('Unsafe verified asset name')
            (root / name).write_bytes(captured[index[key]])
        return publisher(index, root, backend, aapt, apksigner, allow_first_manifest=first)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--aapt', required=True)
    parser.add_argument('--apksigner', required=True)
    args = parser.parse_args()
    try:
        event = json.loads(Path(os.environ['GITHUB_EVENT_PATH']).read_text())
        certificate, first = authorize(os.environ, event)
        result = run(GitHub(REPO, os.environ['GH_TOKEN']),
                     GiteeBackend(os.environ['GITEE_MIRROR_TOKEN']), certificate,
                     args.aapt, args.apksigner, first)
    except Exception:
        # API/network/subprocess exceptions can contain URLs, bodies or signed query
        # parameters. Do not emit traceback or arbitrary exception messages in CI.
        raise SystemExit('Gitee mirror failed; existing GitHub release remains available. Reconcile state before rerunning.') from None
    print(json.dumps(result))


if __name__ == '__main__': main()
