#!/usr/bin/env python3
"""M8 host-only per-cell evidence and isolation gate. Never retries a cell."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys

PAGES = ('app', 'machine', 'scale-settings')
THEMES = ('light', 'dark')
SCALES = ('1.3', '1.0')
PACKAGE = 'io.openhoyi.mobile.mock'
SUCCESS_MARKER = 'SETTINGS_VISUAL_MIGRATION_CAPTURED fixtures=1 realActivities=true visualAcceptance=ASTRA_PENDING noBle=true isolationVerified=true'
COMMON_TRUE = ('businessDiskByteUnchanged', 'typedPreferencesRestored',
    'originalLazyIdentityAndInitializedRestored', 'legacyUnchanged', 'allActivitiesDestroyed',
    'noServiceBeforeOriginalDelegateRestore', 'scopedWritersTerminated', 'allOwnedCallbacksDrained',
    'feedbackPreviewStoppedAndUnobserved', 'providerSessionRemoved', 'mockClockOwnerAbsentBeforeAndAfter',
    'mainQueueBarrierAfterWriters', 'mainQueueBarrierAfterOwners',
    'serviceRecoveryLazyIdentityRestored', 'serviceVisibilityObserverRemoved',
    'serviceFeedbackObserverRemoved', 'scaleOwnersUnboundAndRefreshRemoved',
    'originalVisibilityObserversPreserved', 'serviceOriginalLazyIdentityAndInitializedRestored',
    'ownedSampleRepositoryCreated', 'ownedTraceCreated')
MACHINE_TRUE = ('actualServiceDestroyed', 'serviceHandlersDrained', 'serviceHubClosed')


class Unsafe(RuntimeError):
    pass


def need(condition, message):
    if not condition:
        raise Unsafe(message)


def sha(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def write(path, value):
    path = Path(path)
    tmp = path.with_name(path.name + '.tmp')
    with tmp.open('w') as output:
        json.dump(value, output, indent=2)
        output.write('\n')
        output.flush()
        os.fsync(output.fileno())
    tmp.replace(path)
    descriptor = os.open(path.parent, os.O_RDONLY)
    try:
        os.fsync(descriptor)
    finally:
        os.close(descriptor)


def run(*args, timeout=30):
    return subprocess.run(args, capture_output=True, text=True, timeout=timeout)


def adb(*args, timeout=30):
    return run('adb', *args, timeout=timeout)


def identity(source, apk):
    inventory = adb('devices')
    need(inventory.returncode == 0 and inventory.stderr == '' and
         inventory.stdout.strip().splitlines() == ['List of devices attached', 'emulator-5554\tdevice'],
         'unknown/non-unique adb inventory')
    props = {}
    for prop in ('ro.kernel.qemu', 'ro.serialno', 'ro.build.fingerprint', 'ro.build.version.sdk', 'ro.hardware'):
        result = adb('-s', 'emulator-5554', 'shell', 'getprop ' + prop)
        need(result.returncode == 0 and result.stderr == '' and result.stdout.strip(), 'unknown device property ' + prop)
        props[prop] = result.stdout.strip()
    need(props['ro.kernel.qemu'] == '1' and props['ro.build.version.sdk'] == '34', 'wrong emulator identity')
    current_source = run('git', 'rev-parse', 'HEAD')
    need(current_source.returncode == 0 and current_source.stdout.strip() == source, 'source changed')
    need(sha('mobile/build/outputs/apk/mock/mobile-mock.apk') == apk, 'APK changed')
    return {'serial': 'emulator-5554', 'properties': props, 'sourceSha': source, 'apkSha256': apk}


def expected(source):
    return [{'key': f'{source}/{page}/{theme}/{scale}/tablet-user-landscape-{theme}-font-{scale}',
             'sourceSha': source, 'page': page, 'theme': theme, 'fontScale': scale,
             'profile': f'tablet-user-landscape-{theme}-font-{scale}', 'status': 'NOT_RUN'}
            for theme in THEMES for scale in SCALES for page in PAGES]


def ledger_path(out):
    return Path(out) / 'matrix-ledger.json'


def load(out):
    data = json.loads(ledger_path(out).read_text())
    need(len(data['cells']) == 12, 'invalid fixed matrix size')
    for cell, frozen in zip(data['cells'], expected(data['sourceSha'])):
        need(all(cell.get(name) == value for name, value in frozen.items() if name != 'status'),
             'matrix order/key/metadata changed')
    return data


def unsafe_stop(data, cell, reason, exit_status=1):
    cell['status'] = 'FAILED_UNSAFE_STOP'
    cell['stopReason'] = str(reason)
    data['unsafeStop'] = str(reason)
    if data['firstFailureExitStatus'] is None:
        data['firstFailureExitStatus'] = exit_status or 1
    for remaining in data['cells']:
        if remaining['status'] == 'NOT_RUN':
            remaining['status'] = 'NOT_RUN_UNSAFE_PREDECESSOR'


def profile_dir(out, cell):
    return Path(out) / cell['sourceSha'] / cell['profile'] / cell['page']


def remote_dir(cell):
    return f'/sdcard/Android/data/{PACKAGE}/files/settings-visual-migration/{cell["sourceSha"]}/{cell["profile"]}/{cell["page"]}'


def init(args):
    out = Path(args.output)
    out.mkdir(parents=True, exist_ok=True)
    need(not ledger_path(out).exists() and not (out / 'matrix-verification.json').exists(),
         'existing matrix ledger/verification; no replay')
    need(re.fullmatch(r'[0-9a-f]{40}', args.source) is not None, 'invalid source')
    baseline = identity(args.source, args.apk)
    data = {'sourceSha': args.source, 'apkSha256': args.apk, 'ciRun': os.environ['GITHUB_RUN_ID'],
            'ciJob': os.environ['GITHUB_JOB'], 'deviceIdentity': baseline,
            'cells': expected(args.source), 'firstFailureExitStatus': None, 'unsafeStop': None}
    write(ledger_path(out), data)


def cell_for(data, page, theme, scale):
    key = (page, theme, scale)
    match = [c for c in data['cells'] if (c['page'], c['theme'], c['fontScale']) == key]
    need(len(match) == 1, 'unknown matrix cell')
    return match[0]


def check_turn(data, cell):
    first = next((c for c in data['cells'] if c['status'] == 'NOT_RUN'), None)
    need(first is cell and data['unsafeStop'] is None, 'cell replay/order or matrix stopped')
    position = next(index for index, value in enumerate(data['cells']) if value is cell)
    need(all(prior['status'] in ('PASS_CAPTURED_PENDING_FULL_VERIFY', 'FAILED_ISOLATED_CONTINUED')
             for prior in data['cells'][:position]), 'prior cell unresolved/unsafe')


def before(args):
    data = load(args.output)
    cell = cell_for(data, args.page, args.theme, args.scale)
    try:
        check_turn(data, cell)
        need(identity(data['sourceSha'], data['apkSha256']) == data['deviceIdentity'], 'device/source/APK identity changed')
        need(not profile_dir(args.output, cell).exists(), 'host cell already present')
        old = adb('-s', 'emulator-5554', 'shell', 'test -e ' + remote_dir(cell))
        need(old.stderr == '' and old.returncode == 1, 'device cell scope exists or unknown')
        cell['status'] = 'STARTED'
        write(ledger_path(args.output), data)
    except Exception as exc:
        if cell['status'] == 'NOT_RUN':
            unsafe_stop(data, cell, exc)
        else:
            data['unsafeStop'] = str(exc)
            if data['firstFailureExitStatus'] is None:
                data['firstFailureExitStatus'] = 1
            for remaining in data['cells']:
                if remaining['status'] == 'NOT_RUN':
                    remaining['status'] = 'NOT_RUN_UNSAFE_PREDECESSOR'
        write(ledger_path(args.output), data)
        raise


def check_isolation(folder, page):
    manifest = json.loads((folder / 'manifest.json').read_text())
    iso = json.loads((folder / 'fixture-isolation.json').read_text())
    need(manifest.get('isolationStatus') == 'PASS_ISOLATION_ONLY' and iso.get('status') == 'PASS_ISOLATION_ONLY',
         'original isolation status not PASS')
    for name in COMMON_TRUE + (MACHINE_TRUE if page == 'machine' else ()):
        need(iso.get(name) is True, 'missing/false isolation ' + name)
    if page != 'machine':
        need(iso.get('noServiceWasStartedForThisPage') is True, 'non-machine service started/unknown')
    need(iso.get('ownedAsyncReaderCount') == 0 and iso.get('activityDestroyedCount', 0) > 0,
         'reader/activity cleanup unknown')
    need(iso.get('clipboard') == 'NOT_READ_OR_WRITTEN', 'clipboard touched/unknown')
    need(isinstance(iso.get('providerLifecycle'), list), 'provider lifecycle unknown')
    for event in iso['providerLifecycle']:
        need(isinstance(event, dict) and isinstance(event.get('session'), str) and
             event.get('operation') in ('created_owned_provider', 'owned_provider_cleanup_returned'),
             'provider lifecycle malformed')
    sessions = {event['session'] for event in iso['providerLifecycle']}
    for session in sessions:
        need([event['operation'] for event in iso['providerLifecycle'] if event['session'] == session] ==
             ['created_owned_provider', 'owned_provider_cleanup_returned'], 'provider session cleanup unknown')
    need(type(iso.get('expectedActualExportFailures')) is int and iso['expectedActualExportFailures'] >= 0, 'trace error count unknown')
    trace = iso.get('scopedTraceStatusAfterTermination', '').split()
    need(trace and trace[0] == 'closed' and 'dropped=0' in trace and
         'errors=' + str(iso['expectedActualExportFailures']) in trace, 'trace writer/stop unknown')
    if page == 'machine':
        need(iso.get('noServiceWasStartedForThisPage') is False, 'machine service scope unknown')
    return manifest, iso


def preserve(out, cell):
    target = profile_dir(out, cell)
    need(not target.exists(), 'host cell overwrite forbidden')
    remote = remote_dir(cell)
    status = adb('-s', 'emulator-5554', 'shell', 'test -d ' + remote)
    need(status.stderr == '' and status.returncode == 0, 'missing/unknown generated cell scope')
    listed = adb('-s', 'emulator-5554', 'shell', 'find ' + remote + ' -type f -exec sha256sum {} \\;', timeout=90)
    need(listed.returncode == 0 and listed.stderr == '', 'remote file inventory failed')
    expected_files = {}
    for row in listed.stdout.splitlines():
        match = re.fullmatch(r'([0-9a-f]{64})  (.+)', row)
        need(match is not None, 'unknown remote hash line')
        path = Path(match.group(2))
        rel = path.relative_to(remote)
        need(rel.parts and '..' not in rel.parts and str(rel) not in expected_files, 'unsafe/duplicate file')
        expected_files[str(rel)] = match.group(1)
    need(expected_files and 'manifest.json' in expected_files and 'fixture-isolation.json' in expected_files,
         'incomplete generated cell scope')
    target.parent.mkdir(parents=True, exist_ok=True)
    pulled = adb('-s', 'emulator-5554', 'pull', remote, str(target.parent), timeout=180)
    need(pulled.returncode == 0, 'cell pull failed')
    need(not any(path.is_symlink() for path in target.rglob('*')), 'pulled symlink forbidden')
    found = {str(path.relative_to(target)): sha(path) for path in target.rglob('*') if path.is_file()}
    need(found == expected_files, 'device/host hash or file inventory mismatch')
    manifest = json.loads((target / 'manifest.json').read_text())
    need(manifest.get('sourceSha') == cell['sourceSha'] and manifest.get('profile') == cell['profile'] and
         manifest.get('page') == cell['page'] and isinstance(manifest.get('pid'), int) and manifest['pid'] > 0,
         'manifest source/profile/page/pid mismatch')
    need(isinstance(manifest.get('fileSha256'), dict) and manifest['fileSha256'] and
         'fixture-isolation.json' in manifest['fileSha256'], 'manifest fileSHA inventory missing')
    for name, value in manifest.get('fileSha256', {}).items():
        path = Path(name)
        need(not path.is_absolute() and path.parts and '..' not in path.parts, 'unsafe manifest file path')
        candidate = target / path
        need(candidate.is_file() and sha(candidate) == value, 'manifest fileSHA mismatch')
    return manifest, found


def after(args):
    data = load(args.output)
    cell = cell_for(data, args.page, args.theme, args.scale)
    need(cell['status'] == 'STARTED', 'cell not started')
    cell['originalShellExitStatus'] = args.exit_status
    if data['firstFailureExitStatus'] is None and args.exit_status:
        data['firstFailureExitStatus'] = args.exit_status
    try:
        need(identity(data['sourceSha'], data['apkSha256']) == data['deviceIdentity'], 'device/source/APK identity changed')
        result = Path(args.result)
        need(result.is_file(), 'instrumentation result missing')
        run_record = result.with_suffix('.run.json')
        fresh_record = result.with_suffix('.fresh-process.json')
        need(run_record.is_file() and fresh_record.is_file(), 'run/fresh-process proof missing')
        run_data = json.loads(run_record.read_text())
        fresh = json.loads(fresh_record.read_text())
        need(fresh.get('freshProcessVerified') is True and fresh.get('adbReturnCode') == 0 and
             fresh.get('stdout', '').replace('\r\n', '\n') == 'FRESH_PIDOF_STATUS=1\nFRESH_PIDOF_OUTPUT=\n' and fresh.get('stderr') == '',
             'fresh-process guard unknown/failed')
        need(run_data.get('adbReturnCode') is not None and run_data.get('successMarker') is not None,
             'instrumentation run status unknown')
        cell['originalAdbExitStatus'] = run_data['adbReturnCode']
        cell['successMarker'] = run_data['successMarker']
        manifest, hashes = preserve(args.output, cell)
        need(manifest.get('sourceSha') == cell['sourceSha'] and manifest.get('profile') == cell['profile'] and
             manifest.get('page') == cell['page'] and manifest.get('ciRun') == data['ciRun'] and manifest.get('ciJob') == data['ciJob'] and
             manifest.get('apkSha256') == data['apkSha256'], 'manifest run/APK owner mismatch')
        previous_pids = {other.get('manifestPid') for other in data['cells'] if other is not cell and
                         other['status'] in ('PASS_CAPTURED_PENDING_FULL_VERIFY', 'FAILED_ISOLATED_CONTINUED')}
        need(manifest.get('pid') not in previous_pids, 'old process PID reused; distinct launched process unknown')
        cell['manifestStatus'] = manifest.get('overallStatus')
        cell['manifestPid'] = manifest['pid']
        cell['evidenceFileCount'] = len(hashes)
        check_isolation(profile_dir(args.output, cell), cell['page'])
        if args.exit_status == 0:
            need(run_data['adbReturnCode'] == 0 and run_data['successMarker'] is True and
                 manifest.get('overallStatus') == 'PASS_MECHANICAL_ONLY_NOT_VISUAL_ACCEPTANCE',
                 'success status/marker/manifest mismatch')
            cell['status'] = 'PASS_CAPTURED_PENDING_FULL_VERIFY'
            cell['originalSuccessGates'] = 'PENDING_ORIGINAL_FULL_VERIFY'
        else:
            need(manifest.get('overallStatus') == 'FAIL' and
                 (run_data['adbReturnCode'] != 0 or run_data['successMarker'] is False),
                 'nonzero shell/manifest failure mismatch')
            cell['status'] = 'FAILED_ISOLATED_CONTINUED'
            cell['originalSuccessGates'] = 'NOT_REACHED_ORIGINAL_FAILURE'
        write(ledger_path(args.output), data)
        return 0
    except Exception as exc:
        unsafe_stop(data, cell, exc, args.exit_status)
        write(ledger_path(args.output), data)
        return 1


def finalize(args):
    data = load(args.output)
    need(all(c['status'] in ('PASS_CAPTURED_PENDING_FULL_VERIFY', 'FAILED_ISOLATED_CONTINUED',
                            'FAILED_UNSAFE_STOP', 'NOT_RUN_UNSAFE_PREDECESSOR') for c in data['cells']),
         'fixed matrix has unresolved cells')
    data['fullVerifyExitStatus'] = args.verify_exit
    report_path = Path(args.output) / 'matrix-verification.json'
    pending = [cell for cell in data['cells'] if cell['status'] == 'PASS_CAPTURED_PENDING_FULL_VERIFY']
    if report_path.is_file():
        results = json.loads(report_path.read_text())['results']
        indexed = {(row['page'], row['profile']): row for row in results}
        need(len(indexed) == len(results) == len(pending) and
             set(indexed) == {(cell['page'], cell['profile']) for cell in pending},
             'original VERIFY report does not cover every successful capture')
        for cell in pending:
            result = indexed[(cell['page'], cell['profile'])]
            need(result['status'] in ('PASS_ORIGINAL_FULL_VERIFY', 'FAIL_ORIGINAL_FULL_VERIFY'),
                 'unknown original VERIFY result')
            cell['originalSuccessGates'] = result['status']
            if result['status'] == 'PASS_ORIGINAL_FULL_VERIFY':
                cell['status'] = 'PASS_MECHANICAL_ONLY_NOT_VISUAL_ACCEPTANCE'
            else:
                cell['status'] = 'FAILED_ORIGINAL_FULL_VERIFY'
                cell['verifyError'] = result.get('error')
    else:
        need(not pending or args.verify_exit != 0, 'missing successful-cell VERIFY report')
        for cell in pending:
            cell['status'] = 'FAILED_ORIGINAL_FULL_VERIFY_UNRESOLVED'
            cell['originalSuccessGates'] = 'NOT_REACHED_ORIGINAL_FULL_VERIFY'
    need(args.verify_exit != 0 or (len(data['cells']) == 12 and all(
         cell['status'] == 'PASS_MECHANICAL_ONLY_NOT_VISUAL_ACCEPTANCE' for cell in data['cells']) and
         data['unsafeStop'] is None and data['firstFailureExitStatus'] is None),
         'VERIFY success conflicts with fixed matrix ledger')
    write(ledger_path(args.output), data)
    first = data['firstFailureExitStatus']
    return first if first else (args.verify_exit if args.verify_exit else 0)


def abort(args):
    """Record an unexpected runner exit without changing its original shell status."""
    if not ledger_path(args.output).is_file():
        return
    data = load(args.output)
    unresolved = [c for c in data['cells'] if c['status'] in ('STARTED', 'NOT_RUN')]
    if not unresolved:
        return
    current = next((c for c in unresolved if c['status'] == 'STARTED'), None)
    if current is not None:
        unsafe_stop(data, current, 'runner exited before cell evidence/isolation gate', args.exit_status)
    else:
        data['unsafeStop'] = 'runner exited before completing fixed matrix'
        if data['firstFailureExitStatus'] is None:
            data['firstFailureExitStatus'] = args.exit_status or 1
        for remaining in unresolved:
            remaining['status'] = 'NOT_RUN_UNSAFE_PREDECESSOR'
    write(ledger_path(args.output), data)


def select_exit(args):
    """EXIT trap preserves the first business failure over later shell/cleanup failures."""
    fallback = args.fallback
    need(0 <= fallback <= 255, 'invalid shell exit status')
    if not ledger_path(args.output).is_file():
        print(fallback)
        return
    try:
        first = load(args.output).get('firstFailureExitStatus')
        if type(first) is int and 1 <= first <= 255:
            print(first)
        else:
            print(fallback)
    except Exception:
        print(fallback)


if __name__ == '__main__':
    p = argparse.ArgumentParser()
    sub = p.add_subparsers(dest='action', required=True)
    for action in ('init', 'before', 'after', 'finalize', 'abort', 'select-exit'):
        command = sub.add_parser(action)
        command.add_argument('--output', required=True)
        if action == 'init':
            command.add_argument('--source', required=True)
            command.add_argument('--apk', required=True)
        if action in ('before', 'after'):
            command.add_argument('--page', required=True, choices=PAGES)
            command.add_argument('--theme', required=True, choices=THEMES)
            command.add_argument('--scale', required=True, choices=SCALES)
        if action == 'after':
            command.add_argument('--result', required=True)
            command.add_argument('--exit-status', required=True, type=int)
        if action == 'finalize':
            command.add_argument('--verify-exit', required=True, type=int)
        if action == 'abort':
            command.add_argument('--exit-status', required=True, type=int)
        if action == 'select-exit':
            command.add_argument('--fallback', required=True, type=int)
    args = p.parse_args()
    try:
        answer = {'init': init, 'before': before, 'after': after, 'finalize': finalize,
                  'abort': abort, 'select-exit': select_exit}[args.action](args)
        sys.exit(answer or 0)
    except Exception as exc:
        print('SETTINGS_MATRIX_UNSAFE: ' + str(exc), file=sys.stderr)
        sys.exit(1)
