#!/usr/bin/env python3
"""M3 disposable-CI IME tracing collector. Never hides/shows IME or stops an unknown trace.

Wire envelope source (not a claim that the emulator binary has identical implementation):
https://chromium.googlesource.com/external/github.com/google/perfetto/+/99ca8b80a7dfa3ab1994cf150377aff526aa2124/protos/perfetto/trace/android/inputmethodeditor.proto
Only the declared file/entry envelope is decoded. Nested served/next/Insets fields remain
raw for Winscope; a byte occurrence of the package is not a decoded package-name field.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import struct
import subprocess
import time

FILES = {
    'ime_trace_clients.winscope': b'IMCTRACE',
    'ime_trace_service.winscope': b'IMSTRACE',
    'ime_trace_managerservice.winscope': b'IMMTRACE',
}
REMOTE = '/data/misc/wmtrace/'
PACKAGE = 'io.openhoyi.mobile.mock'


def save(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_suffix(path.suffix + '.tmp')
    temporary.write_text(json.dumps(value, indent=2) + '\n')
    temporary.replace(path)


def call(arguments, timeout=15, input_text=None):
    began = time.monotonic_ns()
    try:
        result = subprocess.run(arguments, input=input_text, capture_output=True, text=True, timeout=timeout)
        return {'arguments': arguments, 'returnCode': result.returncode, 'stdout': result.stdout,
                'stderr': result.stderr, 'completed': True, 'elapsedNs': time.monotonic_ns() - began}
    except (subprocess.TimeoutExpired, OSError) as error:
        return {'arguments': arguments, 'completed': False, 'error': repr(error), 'elapsedNs': time.monotonic_ns() - began}


def ok(value):
    return value.get('completed') and value.get('returnCode') == 0


def ci_guard():
    if os.environ.get('GITHUB_ACTIONS') != 'true':
        raise RuntimeError('Native IME tracing is confined to this guarded disposable GitHub CI emulator')
    qemu = call(['adb', 'shell', 'getprop', 'ro.kernel.qemu'])
    if not ok(qemu) or qemu['stdout'].strip() != '1':
        raise RuntimeError('Actual emulator identity is unavailable or not qemu; no root/trace attempted')
    return qemu


def stat_files():
    result = {}
    for name in FILES:
        path = REMOTE + name  # Fixed allowlist, not arbitrary input.
        script = f"if [ -f '{path}' ]; then stat -c '%s %Y' '{path}' && sha256sum '{path}'; else printf 'IME_TRACE_FILE_ABSENT\\n'; fi\n"
        result[name] = call(['adb', 'shell', 'sh'], input_text=script)
    return result


def probe(directory):
    report = {'status': 'DIAGNOSTIC_UNAVAILABLE', 'qemu': ci_guard(), 'filesBefore': None}
    report['sdk'] = call(['adb', 'shell', 'getprop', 'ro.build.version.sdk'])
    report['fingerprint'] = call(['adb', 'shell', 'getprop', 'ro.build.fingerprint'])
    report['buildType'] = call(['adb', 'shell', 'getprop', 'ro.build.type'])
    debug = ok(report['buildType']) and report['buildType']['stdout'].strip() in ('userdebug', 'eng')
    if debug:
        report['adbRoot'] = call(['adb', 'root'])
        report['waitForDevice'] = call(['adb', 'wait-for-device'])
        report['rootUid'] = call(['adb', 'shell', 'id', '-u'])
    report['help'] = call(['adb', 'shell', 'cmd', 'input_method', 'help'])
    help_text = report['help'].get('stdout', '') + report['help'].get('stderr', '')
    capable = (debug and ok(report.get('rootUid', {})) and report['rootUid']['stdout'].strip() == '0'
               and ok(report['sdk']) and report['sdk']['stdout'].strip() == '34'
               and ok(report['help']) and all(word in help_text for word in ('tracing', 'start', 'stop')))
    if capable:
        report['status'] = 'CAPABLE_NOT_STARTED'
        report['filesBefore'] = stat_files()
    save(directory / 'capability-probe.json', report)
    # Unsupported diagnosis does not silently skip the original business fixture.
    return 0


def varint(data, pos):
    value = 0
    for shift in range(0, 70, 7):
        if pos >= len(data):
            raise ValueError('truncated varint')
        byte = data[pos]; pos += 1; value |= (byte & 127) << shift
        if not byte & 128:
            return value, pos
    raise ValueError('oversize varint')


def fields(data):
    pos = 0
    while pos < len(data):
        tag, pos = varint(data, pos); number, wire = tag >> 3, tag & 7
        if number == 0:
            raise ValueError('invalid field number')
        if wire == 0:
            value, pos = varint(data, pos)
        elif wire in (1, 5):
            size = 8 if wire == 1 else 4
            if pos + size > len(data): raise ValueError('truncated fixed field')
            value = int.from_bytes(data[pos:pos+size], 'little'); pos += size
        elif wire == 2:
            size, pos = varint(data, pos)
            if pos + size > len(data): raise ValueError('truncated message')
            value = data[pos:pos+size]; pos += size
        else:
            raise ValueError(f'unsupported wire type {wire}')
        yield number, wire, value


def decode_envelope(data, magic):
    if not data.startswith(b'\x09' + magic):
        raise ValueError('wrong file magic')
    envelope = list(fields(data))
    offsets = [v for n, w, v in envelope if n == 3 and w == 1]
    if len(offsets) != 1: raise ValueError('missing/ambiguous real-to-elapsed offset')
    entries = []
    for n, wire, value in envelope:
        if n != 2: continue
        if wire != 2: raise ValueError('entry is not a message')
        entry = list(fields(value))
        stamps = [v for n, w, v in entry if n == 1 and w == 1]
        if len(stamps) != 1: raise ValueError('missing/ambiguous elapsed timestamp')
        where = [v.decode('utf-8', errors='replace') for n, w, v in entry if n == 2 and w == 2]
        positions = []; start = 0
        while True:
            index = value.find(PACKAGE.encode(), start)
            if index < 0: break
            positions.append(index); start = index + len(PACKAGE)
        entries.append({'elapsedRealtimeNanos': stamps[0], 'where': where,
                        'entrySha256': hashlib.sha256(value).hexdigest(), 'entrySizeBytes': len(value),
                        'mockPackageByteOffsetsWithinEntry': positions})
    if not entries: raise ValueError('header-only trace has no entries')
    return {'magicAscii': magic.decode(), 'realToElapsedOffsetNanos': offsets[0], 'entries': entries,
            'minElapsedNs': min(e['elapsedRealtimeNanos'] for e in entries),
            'maxElapsedNs': max(e['elapsedRealtimeNanos'] for e in entries),
            'nestedFieldsDecoded': False, 'packageEvidenceMeaning': 'literal_byte_occurrence_only_not_named_proto_field'}


def after(directory, device_directory, probe_directory):
    completed = directory / 'collection-complete.json'
    if completed.is_file():
        return 0 if json.loads(completed.read_text()).get('status') == 'COMPLETE_NATIVE_TRACE_ONLY_NOT_ROOT_CAUSE' else 1
    report = {'status': 'DIAGNOSTIC_UNAVAILABLE', 'commands': [], 'filesAfter': {}, 'fallbackStopAttempted': False}
    try:
        ci_guard()
        state_path = directory / 'trace-state.json'
        pulled = call(['adb', 'pull', device_directory + '/trace-state.json', str(state_path)])
        report['commands'].append(pulled)
        if not ok(pulled): raise ValueError('No instrumented owner state; no unknown trace stopped')
        owner = json.loads(state_path.read_text()); report['owner'] = owner
        context = json.loads((directory / 'fixture-context.json').read_text()); report['fixtureContext'] = context
        if context.get('sourceSha') != owner.get('sourceSha') or context.get('profile') != owner.get('profile') or context.get('fixture') != owner.get('fixture'):
            raise ValueError('Host and instrumented trace fixture identities differ; no unknown trace stopped')
        if owner.get('package') != PACKAGE or not re.fullmatch(r'[0-9a-f-]{36}', owner.get('traceOwnerToken', '')):
            raise ValueError('Unknown trace owner identity')
        if owner.get('ownedStart') is True and owner.get('stopAttempted') is False:
            fallback = directory / 'fallback-stop.json'
            if fallback.exists(): raise ValueError('Owned fallback already attempted; no repeated stop')
            save(fallback, {'traceOwnerToken': owner['traceOwnerToken'], 'attempted': True, 'status': 'IN_PROGRESS'})
            stop = call(['adb', 'shell', 'cmd', 'input_method', 'tracing', 'stop'])
            report['fallbackStopAttempted'] = True
            ack = ok(stop) and 'Stopping tracing and writing traces in /data/misc/wmtrace/:' in stop.get('stdout', '')
            save(fallback, {'traceOwnerToken': owner['traceOwnerToken'], 'attempted': True, 'command': stop, 'acknowledged': ack})
            if not ack: raise ValueError('Owned fallback stop result unknown; no second stop')
        elif owner.get('ownedStart') is not True or owner.get('stopAcknowledged') is not True:
            raise ValueError('No acknowledged owned start/stop; existing traces are not causal evidence')
        capability = json.loads((probe_directory / 'capability-probe.json').read_text())
        if capability.get('status') != 'CAPABLE_NOT_STARTED': raise ValueError('Rooted SDK34 trace capability unavailable')
        report['filesAfter'] = stat_files()
        # Archive all three raw binaries before interpreting any one file. A decode failure
        # must not prevent retention of the other two domains.
        for name in FILES:
            pull = call(['adb', 'pull', REMOTE + name, str(directory / name)])
            report['commands'].append(pull)
        before = json.loads((directory / 'files-before.json').read_text())
        named = {event['name']: event['elapsedRealtimeNanos'] for event in owner.get('events', [])}
        begin = named.get('recreate_call_before'); end = named.get('after_comparison_before')
        if begin is None or end is None: raise ValueError('Missing recreation/failure comparison markers')
        decoded = {}
        for name, magic in FILES.items():
            destination = directory / name
            if not destination.is_file(): raise ValueError(f'Trace pull failed: {name}')
            data = destination.read_bytes()
            if len(data) > 4 * 1024 * 1024 + 65536: raise ValueError('Trace exceeds expected bounded Android14 buffer envelope')
            item = decode_envelope(data, magic)
            item['fileSha256'] = hashlib.sha256(data).hexdigest(); item['fileSizeBytes'] = len(data)
            prior_record = before.get(name, {})
            if not ok(prior_record): raise ValueError(f'Before-file metadata unknown: {name}')
            prior = prior_record.get('stdout', '')
            after_stat = report['filesAfter'][name]
            if not ok(after_stat) or item['fileSha256'] not in after_stat.get('stdout', ''):
                raise ValueError(f'Pulled file does not match actual stopped-device SHA: {name}')
            item['differentFromBefore'] = item['fileSha256'] not in prior
            item['coversRecreateAndComparisonTime'] = item['minElapsedNs'] <= begin and item['maxElapsedNs'] >= end
            decoded[name] = item
            save(directory / (name + '.envelope.json'), item)
            if not item['differentFromBefore'] or not item['coversRecreateAndComparisonTime']:
                raise ValueError(f'Stale or incomplete time coverage: {name}')
        clients = decoded['ime_trace_clients.winscope']
        if not any(e['mockPackageByteOffsetsWithinEntry'] for e in clients['entries']):
            raise ValueError('Client trace contains no byte occurrence of the Mock package')
        report['decoded'] = decoded
        report['status'] = 'COMPLETE_NATIVE_TRACE_ONLY_NOT_ROOT_CAUSE'
    except Exception as error:
        report['status'] = 'PARTIAL_OR_UNAVAILABLE'; report['failure'] = repr(error)
    finally:
        # Pull every fixed raw file even if the first decode/marker check failed. Never delete/overwrite device files.
        for name in FILES:
            if not (directory / name).exists():
                report['commands'].append(call(['adb', 'pull', REMOTE + name, str(directory / name)]))
        report['rawArtifacts'] = {name: {'sizeBytes': (directory / name).stat().st_size,
            'sha256': hashlib.sha256((directory / name).read_bytes()).hexdigest()} for name in FILES if (directory / name).is_file()}
        save(completed, report)
    return 0 if report['status'] == 'COMPLETE_NATIVE_TRACE_ONLY_NOT_ROOT_CAUSE' else 1


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('mode', choices=('probe', 'before', 'after'))
    parser.add_argument('--directory', required=True, type=Path)
    parser.add_argument('--device-directory')
    parser.add_argument('--probe-directory', type=Path)
    args = parser.parse_args(); args.directory.mkdir(parents=True, exist_ok=True)
    if args.mode == 'probe': return probe(args.directory)
    ci_guard()
    if args.mode == 'before':
        save(args.directory / 'files-before.json', stat_files()); return 0
    expected = r'/sdcard/Android/data/io\.openhoyi\.mobile\.mock/files/curve-visual-migration/[0-9a-f]{40}/tablet-user-landscape-(light|dark)-font-1\.(0|3)/import/ime-recreation-native'
    if not args.device_directory or not re.fullmatch(expected, args.device_directory) or args.probe_directory is None:
        raise ValueError('Unexpected trace source path; no arbitrary pull/stop')
    return after(args.directory, args.device_directory, args.probe_directory)


if __name__ == '__main__':
    raise SystemExit(main())
