#!/usr/bin/env python3
"""Fail-closed capability probe. Never run against a user/device AVD."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import time


class ProbeError(RuntimeError):
    pass


def require(condition, message):
    if not condition:
        raise ProbeError(message)


def console_ok(result):
    require(result.returncode == 0, 'console exit failure')
    require(not result.stderr.strip(), 'console stderr is not empty')
    require(result.stdout.strip() == 'OK', 'unknown/error console response')


def snapshot_names(result):
    require(result.returncode == 0 and not result.stderr.strip(), 'snapshot list command failed')
    lines = result.stdout.strip().splitlines()
    require(lines and lines[-1] == 'OK', 'snapshot list missing terminal OK')
    names = []
    for line in lines[:-1]:
        line = line.strip()
        if not line or line in ('List of snapshots present on all disks:', 'No snapshots available'):
            continue
        if re.fullmatch(r'ID\s+TAG\s+VM SIZE\s+DATE\s+VM CLOCK(?:\s+ICOUNT)?', line):
            continue
        # QEMU snapshot table: numeric ID, tag, VM size, date, VM clock.
        match = re.fullmatch(r'\d+\s+([A-Za-z0-9_-]+)\s+\d+(?:\.\d+)?\s*[KMGT]?i?B?\s+\d{4}-\d{2}-\d{2}\s+\d{2}:\d{2}:\d{2}\s+\d{2}:\d{2}:\d{2}(?:\.\d+)?(?:\s+\d+)?', line)
        require(match is not None, 'unknown snapshot list row: ' + line)
        names.append(match.group(1))
    require(len(names) == len(set(names)), 'duplicate snapshot names')
    return names


def parse_state(settings, configuration):
    values = [line[len('font_scale='):] for line in settings.splitlines() if line.startswith('font_scale=')]
    require(len(values) <= 1, 'duplicate font_scale')
    # android14-release ActivityTaskManagerService.java:6573, dumpForProcesses:
    # https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android14-release/services/core/java/com/android/server/wm/ActivityTaskManagerService.java
    # ActivityTaskManager dumps the actual global Configuration, whose first field is fontScale.
    matches = re.findall(r'^\s*mGlobalConfiguration: \{([0-9]+(?:\.[0-9]+)?)\s+[^\n]*\}\s*$', configuration, re.M)
    require(len(matches) == 1, 'missing/ambiguous actual framework mGlobalConfiguration')
    return {'present': bool(values), 'value': values[0] if values else None, 'frameworkFontScale': matches[0]}


def guard(env, serial):
    require(env.get('GITHUB_ACTIONS') == 'true', 'GitHub Actions only')
    require(re.fullmatch(r'emulator-[0-9]+', serial) is not None, 'explicit emulator serial required')
    identity = '-'.join(env.get(k, '') for k in ('GITHUB_RUN_ID', 'GITHUB_RUN_ATTEMPT', 'GITHUB_JOB'))
    require(re.fullmatch(r'[0-9]+-[0-9]+-[A-Za-z0-9_-]+', identity) is not None, 'missing run identity')
    require(env.get('CI_DISPOSABLE_AVD') == 'font-scale-probe-' + identity, 'missing disposable AVD attestation')
    return identity


class Probe:
    def __init__(self, serial, output, env=None):
        self.env = os.environ if env is None else env
        self.identity = guard(self.env, serial)
        self.serial, self.output = serial, Path(output)
        self.output.mkdir(parents=True, exist_ok=False)
        self.name = 'font_probe_' + self.identity.replace('-', '_')
        self.remote = '/data/local/tmp/' + self.name
        self.index = 0
        self.operations = []

    def command(self, *args):
        self.index += 1
        stem = self.output / f'{self.index:03d}'
        (stem.with_suffix('.command.json')).write_text(json.dumps(args))
        try:
            result = subprocess.run(['adb', '-s', self.serial, *args], capture_output=True, text=True, timeout=90)
        except subprocess.TimeoutExpired as exc:
            (stem.with_suffix('.stdout')).write_bytes(exc.stdout or b'')
            (stem.with_suffix('.stderr')).write_bytes(exc.stderr or b'')
            (stem.with_suffix('.exit')).write_text('TIMEOUT')
            raise ProbeError('adb timeout; outcome unknown') from exc
        (stem.with_suffix('.stdout')).write_text(result.stdout)
        (stem.with_suffix('.stderr')).write_text(result.stderr)
        (stem.with_suffix('.exit')).write_text(str(result.returncode))
        return result

    def shell(self, script):
        result = self.command('shell', script)
        require(result.returncode == 0 and not result.stderr.strip(), 'shell failed')
        return result.stdout

    def console(self, operation):
        self.operations.append({'operation': operation, 'attempt': True, 'ack': False})
        self.persist_operations()
        result = self.command('emu', 'avd', 'snapshot', operation, self.name)
        console_ok(result)
        self.operations[-1]['ack'] = True
        self.persist_operations()

    def names(self):
        return snapshot_names(self.command('emu', 'avd', 'snapshot', 'list'))

    def state(self, label):
        self.shell(f'settings --user 0 list system > {self.remote}/{label}.settings && dumpsys activity processes > {self.remote}/{label}.configuration')
        settings = self.shell(f'cat {self.remote}/{label}.settings')
        config = self.shell(f'cat {self.remote}/{label}.configuration')
        return parse_state(settings, config)

    def persist_operations(self):
        with (self.output / 'snapshot-operations.json').open('w') as stream:
            json.dump(self.operations, stream, indent=2)
            stream.flush()
            os.fsync(stream.fileno())

    def device_identity(self):
        inventory = self.command('devices')
        require(inventory.returncode == 0 and not inventory.stderr.strip(), 'device inventory failed')
        require(inventory.stdout.strip().splitlines() == ['List of devices attached', self.serial + '\tdevice'], 'requires exactly one explicitly owned emulator')
        require(self.shell('getprop ro.kernel.qemu').strip() == '1', 'not qemu')
        avd = self.command('emu', 'avd', 'name')
        require(avd.returncode == 0 and not avd.stderr.strip() and avd.stdout.strip().splitlines() == [self.env['CI_DISPOSABLE_AVD'], 'OK'], 'wrong AVD ownership')
        identity = {'adbTransportSerial': self.serial, 'avd': self.env['CI_DISPOSABLE_AVD']}
        for prop in ('ro.serialno', 'ro.build.fingerprint', 'ro.build.version.sdk', 'ro.hardware'):
            identity[prop] = self.shell('getprop ' + prop).strip()
            require(bool(identity[prop]), 'missing identity property ' + prop)
        return identity

    def restored_state(self):
        # Host captures do not depend on scope files being preserved by the rollback.
        settings = self.shell('settings --user 0 list system')
        configuration = self.shell('dumpsys activity processes')
        (self.output / 'restored.settings').write_text(settings)
        (self.output / 'restored.configuration').write_text(configuration)
        return parse_state(settings, configuration)

    def pull_scope(self, baseline, changed):
        destination = self.output / 'pulled-before-load'
        result = self.command('pull', self.remote, str(destination))
        require(result.returncode == 0, 'scope evidence pull failed')
        require((destination / 'identity').read_text().strip() == self.identity, 'pulled scope identity mismatch')
        for filename in ('before.settings', 'before.configuration', 'build-properties'):
            require((destination / filename).is_file() and (destination / filename).stat().st_size > 0, 'missing scope evidence ' + filename)
        require(parse_state((destination / 'before.settings').read_text(), (destination / 'before.configuration').read_text()) == baseline, 'pulled baseline mismatch')
        if changed:
            mutation = parse_state((destination / 'mutated.settings').read_text(), (destination / 'mutated.configuration').read_text())
            require(mutation['present'] and mutation['value'] == '1.3' and float(mutation['frameworkFontScale']) == 1.3, 'pulled mutation proof mismatch')

    def run(self):
        save_attempted = save_verified = pulled = load_ack = restored_verified = False
        baseline = identity = None
        changed = simulated_failure = False
        errors = []
        try:
            identity = self.device_identity()
            (self.output / 'device-identity.json').write_text(json.dumps(identity, indent=2))
            self.shell(f'mkdir {self.remote} && echo {self.identity} > {self.remote}/identity')
            self.shell(f'getprop > {self.remote}/build-properties')
            baseline = self.state('before')
            require(float(baseline['frameworkFontScale']) != 1.3, 'baseline already 1.3; no mutation proof possible')
            require(self.name not in self.names(), 'snapshot name already exists')
            save_attempted = True
            self.console('save')
            require(self.names().count(self.name) == 1, 'save absent from exact snapshot list')
            save_verified = True
            self.shell('settings --user 0 put system font_scale 1.3')
            for _ in range(20):
                current = self.state('mutated')
                if current['present'] and current['value'] == '1.3' and float(current['frameworkFontScale']) == 1.3:
                    changed = True
                    break
                time.sleep(0.5)
            require(changed, 'mutation did not reach real framework Configuration')
            simulated_failure = True
            raise ProbeError('EXPECTED_SIMULATED_BUSINESS_FAILURE')
        except Exception as exc:
            if str(exc) != 'EXPECTED_SIMULATED_BUSINESS_FAILURE':
                errors.append(str(exc))
        finally:
            if save_attempted:
                try:
                    self.pull_scope(baseline, changed)
                    pulled = True
                except Exception as exc:
                    errors.append(str(exc))
                if not (save_verified and pulled):
                    errors.append('LOAD_NOT_AUTHORIZED_BY_VERIFIED_EVIDENCE')
                else:
                    try:
                        require(self.device_identity() == identity, 'pre-load identity changed')
                        require(self.shell(f'cat {self.remote}/identity').strip() == self.identity, 'pre-load scope identity changed')
                        require(self.names().count(self.name) == 1, 'pre-load snapshot not unique')
                        self.console('load')  # Single attempt; never retry/fallback.
                        load_ack = True
                        require(self.command('wait-for-device').returncode == 0, 'reconnect failed')
                        require(self.device_identity() == identity, 'post-load identity changed')
                        require(self.shell(f'cat {self.remote}/identity').strip() == self.identity, 'post-load scope identity changed')
                        require(self.restored_state() == baseline, 'exact restoration mismatch')
                        restored_verified = True
                        require(self.names().count(self.name) == 1, 'snapshot identity not unique after load')
                        self.console('delete')
                        require(self.name not in self.names(), 'snapshot delete not confirmed')
                    except Exception as exc:
                        errors.append(str(exc))
        success = not errors and changed and simulated_failure and pulled and restored_verified
        status = ('PASS' if not baseline['present'] else 'CAPABILITY_PASS_ABSENCE_NOT_PROVED') if success else 'FAIL'
        report = {'status': status, 'errors': errors, 'snapshot': self.name, 'baseline': baseline, 'deviceIdentity': identity,
                  'absenceCaseProved': bool(success and not baseline['present']), 'changedTo1_3': changed,
                  'simulatedBusinessFailure': simulated_failure, 'hostEvidencePulled': pulled,
                  'saveVerified': save_verified, 'loadAcknowledged': load_ack, 'restorationVerified': restored_verified,
                  'snapshotOperations': self.operations, 'androidBinaryCapabilityBeforeCI': 'UNKNOWN'}
        report['rawEvidenceSha256'] = {str(p.relative_to(self.output)): hashlib.sha256(p.read_bytes()).hexdigest() for p in sorted(self.output.rglob('*')) if p.is_file()}
        (self.output / 'result.json').write_text(json.dumps(report, indent=2) + '\n')
        return 0 if success else 1


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--serial', required=True)
    parser.add_argument('--output', required=True)
    args = parser.parse_args()
    raise SystemExit(Probe(args.serial, args.output).run())
