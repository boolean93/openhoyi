#!/usr/bin/env python3
"""Mirror transaction engine. All remote operations are injected, never implicit.

No CLI, credentials or default network backend: this module cannot publish by itself.
Backend contract: reads fail closed; manifest absence requires confirmed HTTP 404;
release-tag absence also permits Gitee's confirmed HTTP 200 JSON null response;
mutations are single-attempt; timeout/5xx/connection loss raise OutcomeUnknown;
CAS uses the file's revision, never unconditional writes. Anonymous reads have no auth.
"""
from dataclasses import dataclass
import hashlib
import json
from pathlib import Path
import tempfile
from typing import Protocol

from apk_distribution import inspect_apk
from gitee_mirror_plan import local_file, plan, release_identity, MAX_ATTACHMENT


class OutcomeUnknown(RuntimeError):
    """A mutation may have completed. Reconcile reads; never blindly resubmit."""


class PublishError(ValueError):
    """Fixed, non-secret diagnostic safe for workflow logs."""


@dataclass(frozen=True)
class ManifestState:
    revision: str
    index: dict


class Backend(Protocol):
    def manifest(self) -> ManifestState | None: ...
    def release(self, tag: str) -> int | None: ...
    def create_release(self, tag: str) -> None: ...
    def attachments(self, release_id: int) -> list[str]: ...
    def upload(self, release_id: int, name: str, data: bytes) -> None: ...
    def anonymous(self, url: str, limit: int) -> bytes: ...
    def compare_and_swap(self, previous: ManifestState | None, index: dict) -> None: ...
    def published_manifest(self) -> dict: ...


def checked_state(state):
    if state is not None:
        if not isinstance(state, ManifestState) or not state.revision:
            raise PublishError('Invalid manifest revision')
        release_identity(state.index)
    return state


def publish(index, directory, backend: Backend, aapt, apksigner, *, inspect=inspect_apk,
            allow_first_manifest=False):
    """Verify immutable local bytes before mutations, publish manifest LAST.

    inspect is injectable ONLY for offline tests; production defaults to actual
    aapt/apksigner inspection. Backend requires independent production verification.
    First manifest creation must be explicitly enabled after repository bootstrap.
    """
    index = release_identity(index)
    previous = checked_state(backend.manifest())
    if previous is None and not allow_first_manifest:
        raise PublishError('Missing mirror baseline requires explicit bootstrap')
    # Copy bytes before validation so local edits during remote IO cannot change upload.
    source = Path(directory).resolve(strict=True)
    with tempfile.TemporaryDirectory(prefix='gitee-mirror-') as tmp:
        root = Path(tmp)
        for name in (index['apkFile'], 'distribution.json', 'SHA256SUMS.txt'):
            (root / name).write_bytes(local_file(source, name, MAX_ATTACHMENT if name.endswith('.apk') else 1_048_576))
        planned = plan(index, root, previous.index if previous else None)
        inspected = inspect(root / index['apkFile'], aapt, apksigner, 'alpha',
                            index['signerCertificateSha256'], index['declaredSourceCommit'])
        for key in ('packageName', 'versionCode', 'versionName', 'apkSha256',
                    'signerCertificateSha256', 'declaredSourceCommit', 'sourceCommitVerified'):
            if inspected.get(key) != index[key]:
                raise PublishError('Actual APK inspection differs from index')
        if inspected.get('debuggable') is not False or inspected.get('variant') != 'alpha':
            raise PublishError('Actual APK is not an alpha release')
        tag = index['releaseTag']
        release_id = backend.release(tag)
        if release_id is None:
            try:
                backend.create_release(tag)
            except OutcomeUnknown:
                pass
            release_id = backend.release(tag)
        if type(release_id) is not int or release_id <= 0:
            raise PublishError('Release creation outcome unresolved; no retry mutation')
        for asset in planned['assets']:
            names = backend.attachments(release_id)
            if not isinstance(names, list) or any(not isinstance(n, str) for n in names):
                raise PublishError('Invalid attachment inventory')
            if names.count(asset['name']) > 1:
                raise PublishError('Duplicate mirror attachment')
            if asset['name'] not in names:
                try:
                    backend.upload(release_id, asset['name'], (root / asset['name']).read_bytes())
                except OutcomeUnknown:
                    pass
                # Lost response must reconcile one unambiguous uploaded asset.
                if backend.attachments(release_id).count(asset['name']) != 1:
                    raise PublishError('Attachment upload outcome unresolved; no retry mutation')
            data = backend.anonymous(asset['targetUrl'], asset['size'])
            if len(data) != asset['size'] or hashlib.sha256(data).hexdigest() != asset['sha256']:
                raise PublishError('Anonymous mirror attachment differs from source')
        current = checked_state(backend.manifest())
        candidate = planned['candidateManifest']
        # Check concurrent newer publication/signing changes BEFORE any manifest write.
        plan(index, root, current.index if current else None)
        if current is None and previous is not None:
            raise PublishError('Mirror baseline disappeared')
        if current is not None and current.index == candidate:
            if backend.published_manifest() != candidate:
                raise PublishError('Public manifest not yet verified; retry reads later')
            return {'status': 'already-published', 'releaseTag': tag}
        try:
            backend.compare_and_swap(current, candidate)
        except OutcomeUnknown:
            pass
        observed = checked_state(backend.manifest())
        if observed is None or observed.index != candidate:
            raise PublishError('Manifest publication unresolved or changed; no blind retry')
        if backend.published_manifest() != candidate:
            raise PublishError('Public manifest not yet verified; retry reads later')
        return {'status': 'published', 'releaseTag': tag}
