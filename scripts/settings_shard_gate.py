#!/usr/bin/env python3
"""Fixed four-AVD settings evidence receipts; never deletes or retries evidence."""
import argparse
import hashlib
import json
import os
from pathlib import Path, PurePosixPath
import re
import shutil
import subprocess
import sys

from settings_matrix_isolation import SHARDS, expected, load, need, write

APKS = {'apkSha256': ('mock.apk', 'mobile/build/outputs/apk/mock/mobile-mock.apk'),
        'testApkSha256': ('test.apk', 'mobile/build/outputs/apk/androidTest/mock/mobile-mock-androidTest.apk')}
RECORDS = ('matrix-ledger.json', 'matrix-verification.json', 'font-scale-before.json',
           'font-scale-restore.json', 'runner-exit.json')
PASS = 'PASS_MECHANICAL_ONLY_NOT_VISUAL_ACCEPTANCE'
CONTEXT = ('sourceSha', 'headSha', 'ciRun', 'ciRunAttempt', 'repository')


def sha(path):
    digest = hashlib.sha256()
    with Path(path).open('rb') as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b''):
            digest.update(block)
    return digest.hexdigest()


def read(path):
    return json.loads(Path(path).read_text())


def valid_context(value):
    need(all(re.fullmatch(r'[0-9a-f]{40}', value[key]) for key in ('sourceSha', 'headSha')), 'invalid source/head')
    need(all(re.fullmatch(r'[1-9][0-9]*', value[key]) for key in ('ciRun', 'ciRunAttempt')), 'invalid run/attempt')
    need(re.fullmatch(r'[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+', value['repository']), 'invalid repository')


def environment():
    value = dict(zip(CONTEXT, (os.environ['GITHUB_SHA'], os.environ['PRODUCT_PR_HEAD_SHA'],
                              os.environ['GITHUB_RUN_ID'], os.environ['GITHUB_RUN_ATTEMPT'],
                              os.environ['GITHUB_REPOSITORY'])))
    valid_context(value)
    current = subprocess.check_output(['git', 'rev-parse', 'HEAD'], text=True).strip()
    need(current == value['sourceSha'], 'checkout source differs from workflow source')
    return value


def require_build(document, context):
    valid_context(context)
    need(document.get('schema') == 1 and all(document.get(k) == context[k] for k in CONTEXT),
         'build source/head/run/attempt/repository mismatch')
    for key in APKS:
        need(re.fullmatch(r'[0-9a-f]{64}', document.get(key, '')), 'invalid APK hash')
    return document


def build(bundle, context, repo=Path('.')):
    bundle = Path(bundle);need(not bundle.exists(), 'build bundle overwrite forbidden')
    bundle.mkdir(parents=True)
    document = dict(context, schema=1)
    for key, (name, source) in APKS.items():
        shutil.copyfile(repo / source, bundle / name)
        document[key] = sha(bundle / name)
    require_build(document, context)
    write(bundle / 'build-identity.json', document)


def verify_build(bundle, context, repo=Path('.')):
    bundle = Path(bundle)
    need(not bundle.is_symlink(), 'unsafe bundle root')
    need({p.name for p in bundle.iterdir()} == {'build-identity.json', 'mock.apk', 'test.apk'},
         'unexpected APK bundle contents')
    need(not any(p.is_symlink() or not p.is_file() for p in bundle.iterdir()), 'unsafe bundle file')
    document = require_build(read(bundle / 'build-identity.json'), context)
    for key, (name, target) in APKS.items():
        need(sha(bundle / name) == document[key], 'downloaded APK hash mismatch')
        need(not (repo / target).exists(), 'worker APK overwrite forbidden')
    for key, (name, target) in APKS.items():
        target = repo / target;target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(bundle / name, target)
    return document


def external(evidence, gate):
    need(not Path(evidence).is_symlink() and not Path(gate).is_symlink(), 'symlink root forbidden')
    evidence, gate = Path(evidence).resolve(), Path(gate).resolve()
    need(evidence != gate and evidence not in gate.parents and gate not in evidence.parents,
         'gate and evidence roots must be separate')
    return evidence, gate


def inventory(evidence):
    evidence = Path(evidence)
    need(evidence.is_dir() and not evidence.is_symlink(), 'missing/unsafe evidence root')
    rows = []
    for path in sorted(evidence.rglob('*')):
        need(not path.is_symlink(), 'evidence symlink forbidden')
        if path.is_dir():
            continue
        need(path.is_file(), 'nonregular evidence entry')
        relative = path.relative_to(evidence).as_posix()
        need(not any(part.startswith('.') for part in PurePosixPath(relative).parts), 'hidden evidence entry forbidden')
        rows.append({'path': relative, 'bytes': path.stat().st_size, 'sha256': sha(path)})
    need(rows, 'empty evidence tree')
    return rows


def validate_inventory(rows):
    need(isinstance(rows, list) and rows, 'empty inventory')
    paths = []
    for row in rows:
        name = row['path'];path = PurePosixPath(name)
        need(path.parts and not path.is_absolute() and '..' not in path.parts and
             not any(part.startswith('.') for part in path.parts) and str(path) == name,
             'unsafe inventory path')
        need(type(row['bytes']) is int and row['bytes'] >= 0 and
             re.fullmatch(r'[0-9a-f]{64}', row['sha256']), 'invalid inventory metadata')
        paths.append(name)
    need(paths == sorted(set(paths)), 'duplicate/unordered inventory')


def rows_sha(rows):
    return hashlib.sha256(json.dumps(rows, sort_keys=True, separators=(',', ':')).encode()).hexdigest()


def manifest_metadata(manifest):
    keys = ('sourceSha', 'headSha', 'ciRun', 'ciRunAttempt', 'ciJob', 'apkSha256', 'testApkSha256',
            'sourceDirty', 'page', 'profile', 'pid', 'overallStatus', 'isolationStatus')
    return {key: manifest.get(key) for key in keys}


def cell_witnesses(evidence, rows, data):
    witnesses = []
    for cell in data['cells']:
        prefix = f'{cell["sourceSha"]}/{cell["profile"]}/{cell["page"]}/'
        path = Path(evidence) / prefix / 'manifest.json'
        manifest = read(path)
        subtree = [row for row in rows if row['path'].startswith(prefix)]
        witnesses.append({'page': cell['page'], 'profile': cell['profile'], 'prefix': prefix,
                          'manifestSha256': sha(path), 'manifest': manifest_metadata(manifest),
                          'fileSha256': manifest['fileSha256'], 'fileCount': len(subtree),
                          'inventorySha256': rows_sha(subtree)})
    return witnesses


def validate_cells(rows, witnesses, data, context, identity):
    need(len(witnesses) == 3 and [(w['page'], w['profile']) for w in witnesses] ==
         [(c['page'], c['profile']) for c in data['cells']], 'missing/duplicate cell preservation witness')
    for cell, witness in zip(data['cells'], witnesses):
        prefix = f'{cell["sourceSha"]}/{cell["profile"]}/{cell["page"]}/'
        need(witness['prefix'] == prefix, 'cell preservation prefix mismatch')
        subtree = [row for row in rows if row['path'].startswith(prefix)]
        need(type(cell['evidenceFileCount']) is int and len(subtree) == witness['fileCount'] == cell['evidenceFileCount'] and
             rows_sha(subtree) == witness['inventorySha256'], 'cell subtree missing/count/digest mismatch')
        indexed = {row['path'][len(prefix):]: row['sha256'] for row in subtree}
        files = witness['fileSha256']
        need(isinstance(files, dict) and {'fixture-isolation.json', 'fixture.json', 'window.png',
             'window-frame-sync.json', 'geometry.json', 'special-states/manifest.json'} <= set(files),
             'missing cell manifest/fixture/window/proof/geometry/special evidence')
        for name, value in files.items():
            validate_inventory([{'path': name, 'bytes': 0, 'sha256': value}])
        need(indexed == dict(files, **{'manifest.json': witness['manifestSha256']}),
             'cell manifest file inventory incomplete/changed')
        manifest = witness['manifest']
        need(all(manifest.get(key) == context[key] for key in ('sourceSha', 'headSha', 'ciRun', 'ciRunAttempt')) and
             all(manifest.get(key) == identity[key] for key in APKS) and manifest.get('ciJob') == data['ciJob'] and
             manifest.get('sourceDirty') == 'false', 'preserved manifest build/workflow identity mismatch')
        need(manifest.get('page') == cell['page'] and manifest.get('profile') == cell['profile'] and
             manifest.get('pid') == cell['manifestPid'] and manifest.get('overallStatus') == PASS and
             manifest.get('isolationStatus') == 'PASS_ISOLATION_ONLY', 'preserved manifest cell/PID/status mismatch')


def freeze(evidence, gate, context, scope):
    need(scope in SHARDS, 'unknown shard')
    evidence, gate = external(evidence, gate)
    need(not gate.exists(), 'gate overwrite forbidden')
    gate.mkdir(parents=True)
    rows = inventory(evidence);validate_inventory(rows)
    write(gate / 'inventory.json', rows)
    for path in sorted(evidence.rglob('*'), reverse=True):
        path.chmod(0o555 if path.is_dir() else 0o444)
    evidence.chmod(0o555)
    need(inventory(evidence) == rows, 'evidence changed while freezing')
    write(gate / 'freeze.json', dict(context, scope=scope, status='FROZEN_READ_ONLY',
                                   inventorySha256=sha(gate / 'inventory.json')))


def validate_original(gate, context, scope, identity):
    data = load(gate)
    need(data.get('scope') == scope, 'ledger scope mismatch')
    for key in ('sourceSha', 'headSha', 'ciRun', 'ciRunAttempt'):
        need(data.get(key) == context[key], 'ledger workflow identity mismatch')
    need(data.get('ciJob') == 'settings-cell', 'unexpected worker job identity')
    need(all(data.get(key) == identity[key] for key in APKS), 'ledger APK/test APK mismatch')
    device = data['deviceIdentity']
    need(device['serial'] == 'emulator-5554' and device['properties']['ro.kernel.qemu'] == '1' and
         device['properties']['ro.build.version.sdk'] == '34' and device['sourceSha'] == context['sourceSha'] and
         device['apkSha256'] == identity['apkSha256'], 'device identity missing/changed')
    cells = data['cells']
    need(len(cells) == 3 and all(c['status'] == PASS and
         c['originalSuccessGates'] == 'PASS_ORIGINAL_FULL_VERIFY' and
         c['originalShellExitStatus'] == c['originalAdbExitStatus'] == 0 and c['successMarker'] is True
         for c in cells), 'original cell failure/unresolved')
    pids = [c['manifestPid'] for c in cells]
    need(all(type(pid) is int and pid > 0 for pid in pids) and len(set(pids)) == 3, 'same-AVD PID reuse')
    need(data['fullVerifyExitStatus'] == 0 and data['firstFailureExitStatus'] is None and data['unsafeStop'] is None,
         'original failure or unsafe stop')
    report = read(gate / 'matrix-verification.json')
    keys = {(c['page'], c['profile']) for c in cells}
    need(len(report['results']) == 3 and {(r['page'], r['profile']) for r in report['results']} == keys and
         all(r['status'] == 'PASS_ORIGINAL_FULL_VERIFY' for r in report['results']) and report['errors'] == [],
         'original fullverify incomplete/failed')
    before = read(gate / 'font-scale-before.json')
    restored = read(gate / 'font-scale-restore.json')
    need(type(before['present']) is bool and (isinstance(before['value'], str) if before['present'] else before['value'] is None),
         'invalid font key snapshot')
    need(restored['status'] == 'PASS_SETTING_RESTORE_ONLY' and restored['before'] == before == restored['after'],
         'font key presence/value restore failed')
    need(read(gate / 'runner-exit.json')['exitStatus'] == 0, 'original runner exit failed')
    return data


def artifact_reference(artifact, context):
    need(re.fullmatch(r'[1-9][0-9]*', artifact.get('id', '')), 'missing/invalid evidence artifact id')
    need(re.fullmatch(r'(?:sha256:)?[0-9a-f]{64}', artifact.get('digest', '')), 'missing/invalid evidence artifact digest')
    url = f'https://github.com/{context["repository"]}/actions/runs/{context["ciRun"]}/artifacts/{artifact["id"]}'
    need(artifact.get('url') == url, 'wrong evidence artifact repository/run/id URL')
    return artifact


def receipt(evidence, gate, bundle, context, scope, artifact, capture_outcome, upload_outcome):
    evidence, gate = external(evidence, gate);gate.mkdir(parents=True, exist_ok=True)
    errors = []
    def check(action):
        try:
            return action()
        except Exception as error:
            errors.append(str(error))
    identity = check(lambda: require_build(read(Path(bundle) / 'build-identity.json'), context))
    if identity is not None:
        shutil.copyfile(Path(bundle) / 'build-identity.json', gate / 'build-identity.json')
    check(lambda: need(scope in SHARDS, 'unknown shard'))
    check(lambda: need(capture_outcome == 'success', 'capture action failed/cancelled/unknown'))
    check(lambda: need(upload_outcome == 'success', 'evidence preservation FAILED/UNKNOWN'))
    valid_artifact = check(lambda: artifact_reference(artifact, context))
    def frozen_tree():
        frozen = read(gate / 'freeze.json')
        need(frozen['status'] == 'FROZEN_READ_ONLY' and frozen['scope'] == scope and
             all(frozen.get(key) == context[key] for key in CONTEXT), 'frozen tree identity mismatch')
        need(sha(gate / 'inventory.json') == frozen['inventorySha256'], 'frozen inventory modified')
        rows = read(gate / 'inventory.json');validate_inventory(rows)
        need(inventory(evidence) == rows, 'evidence changed after freeze/upload')
        need(all(not (path.stat().st_mode & 0o222) for path in [evidence, *evidence.rglob('*')]),
             'evidence tree became writable')
    check(frozen_tree)
    record_hashes = {}
    for name in RECORDS:
        def copy_record(name=name):
            need((evidence / name).is_file(), 'missing original ' + name)
            shutil.copyfile(evidence / name, gate / name)
            record_hashes[name] = sha(gate / name)
        check(copy_record)
    if identity is not None:
        data = check(lambda: validate_original(gate, context, scope, identity))
        if data is not None:
            def preserve_cells():
                rows = read(gate / 'inventory.json')
                witnesses = cell_witnesses(evidence, rows, data)
                validate_cells(rows, witnesses, data, context, identity)
                write(gate / 'cell-preservation.json', witnesses)
            check(preserve_cells)
    document = dict(context, schema=1, scope=scope, status='FAIL' if errors else 'PASS_RECEIPT_ONLY',
                    evidencePreservation='UPLOADED_REFERENCE_RECORDED' if upload_outcome == 'success' and
                    valid_artifact is not None else 'FAILED_OR_UNKNOWN',
                    evidenceArtifact=artifact, captureOutcome=capture_outcome, uploadOutcome=upload_outcome,
                    recordSha256=record_hashes, errors=errors)
    if (gate / 'inventory.json').is_file():
        document['inventorySha256'] = sha(gate / 'inventory.json')
    if identity is not None:
        document['buildIdentitySha256'] = sha(gate / 'build-identity.json')
    if (gate / 'cell-preservation.json').is_file():
        document['cellPreservationSha256'] = sha(gate / 'cell-preservation.json')
    write(gate / 'receipt.json', document)
    return 1 if errors else 0


def aggregate(gates, output, context, build_result, workers_result):
    gates, output = Path(gates), Path(output);output.mkdir(parents=True, exist_ok=True)
    errors = [];receipts = [];cells = [];identities = [];artifact_ids = []
    if build_result != 'success' or workers_result != 'success':
        errors.append('build or worker dependency failed/cancelled/skipped/unknown')
    if not gates.is_dir() or {p.name for p in gates.iterdir()} != set(SHARDS):
        errors.append('missing/extra fixed shard gate')
    for scope in SHARDS:
        gate = gates / scope
        try:
            need(not gate.is_symlink(), 'gate symlink forbidden')
            receipt_path = gate / 'receipt.json'
            need(receipt_path.is_file() and not receipt_path.is_symlink(), 'missing/unsafe worker receipt')
            proof = read(receipt_path);receipts.append(proof)
            allowed = set(RECORDS) | {'receipt.json', 'inventory.json', 'freeze.json', 'build-identity.json', 'cell-preservation.json'}
            need({p.name for p in gate.iterdir()} == allowed and
                 all(p.is_file() and not p.is_symlink() for p in gate.iterdir()), 'incomplete/extra gate records')
            need(proof['schema'] == 1 and proof['scope'] == scope and
                 all(proof.get(key) == context[key] for key in CONTEXT), 'receipt scope/workflow identity mismatch')
            need(proof['status'] == 'PASS_RECEIPT_ONLY' and proof['errors'] == [] and
                 proof['captureOutcome'] == proof['uploadOutcome'] == 'success' and
                 proof['evidencePreservation'] == 'UPLOADED_REFERENCE_RECORDED', 'worker receipt/preservation failed')
            artifact_reference(proof['evidenceArtifact'], context)
            artifact_ids.append(proof['evidenceArtifact']['id'])
            identity = require_build(read(gate / 'build-identity.json'), context)
            need(sha(gate / 'build-identity.json') == proof['buildIdentitySha256'], 'build identity record modified')
            identities.append(identity)
            need(sha(gate / 'inventory.json') == proof['inventorySha256'], 'inventory digest modified')
            rows = read(gate / 'inventory.json');validate_inventory(rows)
            indexed = {row['path']: row for row in rows}
            frozen = read(gate / 'freeze.json')
            need(frozen['status'] == 'FROZEN_READ_ONLY' and frozen['scope'] == scope and
                 all(frozen.get(key) == context[key] for key in CONTEXT) and
                 frozen['inventorySha256'] == proof['inventorySha256'], 'freeze/inventory identity changed')
            need(set(proof['recordSha256']) == set(RECORDS), 'missing/extra original record digest')
            for name in RECORDS:
                need(sha(gate / name) == proof['recordSha256'][name] == indexed[name]['sha256'] and
                     (gate / name).stat().st_size == indexed[name]['bytes'], 'original record not bound to uploaded inventory')
            data = validate_original(gate, context, scope, identity)
            need(sha(gate / 'cell-preservation.json') == proof['cellPreservationSha256'], 'cell preservation record modified')
            validate_cells(rows, read(gate / 'cell-preservation.json'), data, context, identity)
            cells.extend(dict(cell, shard=scope, deviceIdentity=data['deviceIdentity']) for cell in data['cells'])
        except Exception as error:
            errors.append(scope + ': ' + str(error))
    if len(identities) != 4 or not all(identity == identities[0] for identity in identities):
        errors.append('workers did not use the same build APK/test APK identity')
    if len(artifact_ids) != 4 or len(set(artifact_ids)) != 4:
        errors.append('missing/reused evidence artifact reference')
    frozen = {(c['page'], c['profile']) for c in expected(context['sourceSha'])}
    if len(cells) != 12 or {(c['page'], c['profile']) for c in cells} != frozen:
        errors.append('global matrix is not exactly twelve successful original cells')
    document = dict(context, status='FAIL' if errors else 'PASS_MECHANICAL_ONLY_NOT_VISUAL_ACCEPTANCE',
                    visualAcceptance='ASTRA_PENDING', evidencePreservation='UNKNOWN_OR_FAILED' if errors else
                    'FOUR_UPLOADED_REFERENCES_RECORDED_NOT_REDOWNLOADED',
                    errors=errors, receipts=receipts, cells=cells,
                    buildDependencyResult=build_result, workerDependencyResult=workers_result)
    write(output / 'matrix-aggregate.json', document)
    return 1 if errors else 0


def main():
    parser = argparse.ArgumentParser()
    sub = parser.add_subparsers(dest='action', required=True)
    for action in ('build', 'verify-build', 'freeze', 'receipt', 'aggregate'):
        command = sub.add_parser(action)
        if action in ('build', 'verify-build', 'receipt'):
            command.add_argument('--bundle', required=True)
        if action in ('freeze', 'receipt'):
            command.add_argument('--evidence', required=True)
            command.add_argument('--gate', required=True)
            command.add_argument('--scope', choices=SHARDS, required=True)
        if action == 'receipt':
            for name in ('artifact-id', 'artifact-digest', 'artifact-url', 'capture-outcome', 'upload-outcome'):
                command.add_argument('--' + name, required=True)
        if action == 'aggregate':
            for name in ('gates', 'output', 'build-result', 'workers-result'):
                command.add_argument('--' + name, required=True)
    args = parser.parse_args();context = environment()
    if args.action == 'build':
        build(args.bundle, context)
    elif args.action == 'verify-build':
        verify_build(args.bundle, context)
    elif args.action == 'freeze':
        freeze(args.evidence, args.gate, context, args.scope)
    elif args.action == 'receipt':
        return receipt(args.evidence, args.gate, args.bundle, context, args.scope,
                       {'id': args.artifact_id, 'digest': args.artifact_digest, 'url': args.artifact_url},
                       args.capture_outcome, args.upload_outcome)
    else:
        return aggregate(args.gates, args.output, context, args.build_result, args.workers_result)
    return 0


if __name__ == '__main__':
    try:
        sys.exit(main())
    except Exception as error:
        print('SETTINGS_SHARD_UNSAFE: ' + str(error), file=sys.stderr)
        sys.exit(1)
