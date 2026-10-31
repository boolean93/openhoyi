#!/usr/bin/env python3
"""Single-owner full-AVD snapshot transaction for fixed Scale/Bean CI profiles."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import signal
import subprocess
import sys
from types import MappingProxyType
import uuid
from typing import NamedTuple

from probe_ci_font_scale_snapshot import ProbeError, parse_state, require, snapshot_names

PACKAGE = 'io.openhoyi.mobile.mock'
ROOT = Path('build/task-first-evidence/scale-visual-migration').resolve()
SCOPE = ROOT / 'snapshot-scope'


class Profile(NamedTuple):
    root: Path
    business_directory: str
    business_script: str
    avd_prefix: str
    serial_key: str
    owner_key: str
    snapshot_prefix: str


PROFILES = MappingProxyType({
    'scale': Profile(ROOT, 'scale-visual-migration', 'scripts/check_scale_visual_migration.sh',
                     'font-scale-scale-', 'SCALE_SNAPSHOT_SERIAL', 'SCALE_SNAPSHOT_SCOPE_FILE', 'scale_scope_'),
    'bean': Profile(Path('build/task-first-evidence/bean-workflow-visual-migration').resolve(),
                    'bean-workflow-visual-migration', 'scripts/check_bean_workflow_visual_migration.sh',
                    'font-scale-bean-', 'BEAN_SNAPSHOT_SERIAL', 'BEAN_SNAPSHOT_SCOPE_FILE', 'bean_scope_'),
})


def profile_config(profile):
    require(profile in PROFILES, 'unknown snapshot scope profile')
    return PROFILES[profile]


def digest(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def durable_json(path, value):
    path = Path(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_name(path.name + '.tmp')
    with temporary.open('w') as stream:
        json.dump(value, stream, indent=2)
        stream.write('\n')
        stream.flush()
        os.fsync(stream.fileno())
    temporary.replace(path)
    descriptor = os.open(path.parent, os.O_RDONLY)
    try:
        os.fsync(descriptor)
    finally:
        os.close(descriptor)


def durable_text(path, value):
    with Path(path).open('w') as stream:
        stream.write(value)
        stream.flush()
        os.fsync(stream.fileno())


def durable_tree(root):
    for path in sorted(root.rglob('*')):
        if path.is_file():
            with path.open('rb') as stream:
                os.fsync(stream.fileno())
    directories = [root] + [p for p in root.rglob('*') if p.is_dir()]
    for path in sorted(directories, key=lambda p: len(p.parts), reverse=True):
        descriptor = os.open(path, os.O_RDONLY)
        try:
            os.fsync(descriptor)
        finally:
            os.close(descriptor)


def strict_console(result):
    require(result.returncode == 0 and result.stderr == '' and result.stdout.strip() == 'OK',
            'unknown/failed snapshot console response')


def checked_env(env, profile='scale'):
    config = profile_config(profile)
    require(env.get('GITHUB_ACTIONS') == 'true', 'CI only')
    identity = '-'.join(env.get(key, '') for key in ('GITHUB_RUN_ID', 'GITHUB_RUN_ATTEMPT', 'GITHUB_JOB'))
    require(re.fullmatch(r'[0-9]+-[0-9]+-[A-Za-z0-9_-]+', identity) is not None, 'missing run identity')
    require(env.get('CI_DISPOSABLE_AVD') == config.avd_prefix + identity, 'wrong disposable AVD scope')
    require(env.get(config.serial_key) == 'emulator-5554', 'explicit dedicated serial required')
    return identity


class Scope:
    def __init__(self, env=None, output=None, profile='scale'):
        self.profile = profile
        self.config = profile_config(profile)
        self.env = dict(os.environ if env is None else env)
        self.identity_name = checked_env(self.env, profile)
        self.serial = self.env[self.config.serial_key]
        self.output = Path(output if output is not None else self.root / 'snapshot-scope').resolve()
        self.owner_path = self.output / 'owner.json'
        self.report_path = self.output / 'result.json'
        self.operations = []
        self.owner = None
        self.record_prefix = uuid.uuid4().hex
        self.identity_checks = 0
        self.list_checks = 0

    @property
    def root(self):
        # Keep the original Scale test's patched ROOT seam; Bean always uses its fixed profile root.
        return ROOT if self.profile == 'scale' else self.config.root

    def adb(self, *args, timeout=90):
        return subprocess.run(['adb', '-s', self.serial, *args], capture_output=True, text=True, timeout=timeout)

    def checked(self, *args):
        result = self.adb(*args)
        require(result.returncode == 0 and result.stderr == '', 'adb command failed: ' + ' '.join(args))
        return result.stdout

    def shell(self, command):
        return self.checked('shell', command)

    def identity(self):
        self.identity_checks += 1
        record = self.output / 'identity-checks' / f'{self.record_prefix}-{self.identity_checks:03d}.json'
        raw = {}
        inventory = subprocess.run(['adb', 'devices'], capture_output=True, text=True, timeout=15)
        raw['inventory'] = {'exit': inventory.returncode, 'stdout': inventory.stdout, 'stderr': inventory.stderr}
        durable_json(record, {'raw': raw})
        require(inventory.returncode == 0 and inventory.stderr == '' and
                inventory.stdout.strip().splitlines() == ['List of devices attached', self.serial + '\tdevice'],
                'not exactly one owned emulator')
        qemu = self.adb('shell', 'getprop ro.kernel.qemu')
        raw['qemu'] = {'exit': qemu.returncode, 'stdout': qemu.stdout, 'stderr': qemu.stderr}
        durable_json(record, {'raw': raw})
        require(qemu.returncode == 0 and qemu.stderr == '' and qemu.stdout.strip() == '1', 'not QEMU')
        avd = self.adb('emu', 'avd', 'name')
        raw['avd'] = {'exit': avd.returncode, 'stdout': avd.stdout, 'stderr': avd.stderr}
        durable_json(record, {'raw': raw})
        require(avd.returncode == 0 and avd.stderr == '' and
                avd.stdout.strip().splitlines() == [self.env['CI_DISPOSABLE_AVD'], 'OK'], 'AVD name mismatch')
        result = {'adbTransportSerial': self.serial, 'avd': self.env['CI_DISPOSABLE_AVD']}
        for prop in ('ro.serialno', 'ro.build.fingerprint', 'ro.build.version.sdk', 'ro.hardware'):
            response = self.adb('shell', 'getprop ' + prop)
            raw[prop] = {'exit': response.returncode, 'stdout': response.stdout, 'stderr': response.stderr}
            durable_json(record, {'raw': raw})
            require(response.returncode == 0 and response.stderr == '', 'identity property command failed ' + prop)
            result[prop] = response.stdout.strip()
            require(result[prop], 'missing ' + prop)
        require(result['ro.build.version.sdk'] == '34', 'SDK 34 required')
        durable_json(record, {'parsed': result, 'raw': raw})
        return result

    def state(self, prefix):
        settings = self.shell('settings --user 0 list system')
        configuration = self.shell('dumpsys activity processes')
        (self.output / (prefix + '.settings')).write_text(settings)
        (self.output / (prefix + '.configuration')).write_text(configuration)
        return parse_state(settings, configuration)

    def names(self):
        response = self.adb('emu', 'avd', 'snapshot', 'list')
        self.list_checks += 1
        durable_json(self.output / 'snapshot-lists' / f'{self.record_prefix}-{self.list_checks:03d}.json',
                     {'command': ['emu', 'avd', 'snapshot', 'list'], 'exit': response.returncode,
                      'stdout': response.stdout, 'stderr': response.stderr})
        return snapshot_names(response)

    def operation(self, action):
        self.operations.append({'operation': action, 'intent': True, 'ack': False})
        durable_json(self.output / 'operations.json', self.operations)
        result = self.adb('emu', 'avd', 'snapshot', action, self.owner['snapshot'])
        durable_text(self.output / (action + '.stdout'), result.stdout)
        durable_text(self.output / (action + '.stderr'), result.stderr)
        durable_text(self.output / (action + '.exit'), str(result.returncode) + '\n')
        strict_console(result)
        self.operations[-1]['ack'] = True
        durable_json(self.output / 'operations.json', self.operations)

    def marker(self):
        path = self.owner['remoteMarker']
        return self.shell('cat ' + path).strip()

    def verify_owner(self, require_snapshot=True):
        require(self.owner_path.is_file(), 'host owner missing')
        persisted = json.loads(self.owner_path.read_text())
        require(persisted == self.owner, 'host owner changed')
        require(persisted.get('scopeProfile', 'scale') == self.profile, 'scope profile mismatch')
        require(persisted['runIdentity'] == self.identity_name, 'run owner mismatch')
        snapshot = persisted['snapshot']
        require(re.fullmatch(re.escape(self.config.snapshot_prefix + self.identity_name.replace('-', '_') + '_')
                             + r'[0-9a-f]{8}', snapshot) is not None, 'snapshot profile/name mismatch')
        require(persisted['remoteDirectory'] == '/data/local/tmp/' + snapshot and
                persisted['remoteMarker'] == '/data/local/tmp/' + snapshot + '/owner', 'remote owner path mismatch')
        require(persisted['sourceSha'] == self.git_head(), 'source HEAD changed')
        require(persisted['sourceDirty'] == bool(subprocess.run(['git','status','--porcelain'],capture_output=True,text=True,check=True).stdout), 'source dirty state changed')
        require(persisted['apkSha256'] == digest('mobile/build/outputs/apk/mock/mobile-mock.apk'), 'APK changed')
        require(persisted['deviceIdentity'] == self.identity(), 'device identity changed')
        require(self.marker() == persisted['scopeUuid'], 'device marker changed')
        if require_snapshot:
            require(self.names().count(persisted['snapshot']) == 1, 'owned snapshot not unique')

    @staticmethod
    def git_head():
        return subprocess.run(['git', 'rev-parse', 'HEAD'], check=True, capture_output=True, text=True).stdout.strip()

    def initialize(self):
        require(not self.output.exists(), 'scope output already exists')
        self.output.mkdir(parents=True)
        scope_uuid = uuid.uuid4().hex
        snapshot = self.config.snapshot_prefix + self.identity_name.replace('-', '_') + '_' + scope_uuid[:8]
        remote = '/data/local/tmp/' + snapshot
        source = self.git_head()
        require(re.fullmatch(r'[0-9a-f]{40}', source) is not None, 'invalid source SHA')
        device = self.identity()
        require(snapshot not in self.names(), 'snapshot collision')
        baseline = self.state('before')
        remote_marker = remote + '/owner'
        self.owner = {'scopeProfile': self.profile, 'runIdentity': self.identity_name, 'scopeUuid': scope_uuid,
                      'snapshot': snapshot, 'remoteMarker': remote_marker, 'remoteDirectory': remote,
                      'sourceSha': source, 'sourceDirty': bool(subprocess.run(['git','status','--porcelain'],capture_output=True,text=True,check=True).stdout),
                      'apkSha256': digest('mobile/build/outputs/apk/mock/mobile-mock.apk'),
                      'deviceIdentity': device, 'baseline': baseline}
        self.shell('mkdir ' + remote)
        self.shell('printf %s ' + scope_uuid + ' > ' + remote_marker)
        durable_json(self.owner_path, self.owner)
        self.operation('save')
        require(self.names().count(snapshot) == 1, 'save not confirmed by exact list')
        return True

    def preserve(self):
        """No load until every existing generated file and host proof is durable and hashed."""
        source = self.owner['sourceSha']
        remote = f'/sdcard/Android/data/{PACKAGE}/files/{self.config.business_directory}/{source}'
        exists = self.adb('shell', 'test -d ' + remote)
        require(exists.stderr == '' and exists.returncode in (0, 1), 'scope directory status unknown')
        root = self.output / 'pulled-business'
        root.mkdir()
        if exists.returncode == 0:
            listing = self.shell('find ' + remote + ' -type f -exec sha256sum {} \\;')
            expected = {}
            for row in listing.splitlines():
                match = re.fullmatch(r'([0-9a-f]{64})\s\s(.+)', row)
                require(match is not None, 'unknown device sha256sum row')
                path = Path(match.group(2))
                rel = path.relative_to(remote)
                require(rel.parts and '..' not in rel.parts and str(rel) not in expected, 'unsafe/duplicate remote path')
                expected[str(rel)] = match.group(1)
            pulled = self.adb('pull', remote, str(root), timeout=180)
            require(pulled.returncode == 0, 'business scope pull failed')
            local = root / source
            found = {str(p.relative_to(local)): digest(p) for p in local.rglob('*') if p.is_file()}
            require(found == expected, 'business scope hashes/inventory mismatch')
        else:
            expected = {}
        # The original business script owns ROOT and any pre-load diagnostic files.
        for manifest in self.root.rglob('manifest.json'):
            if self.output in manifest.parents and root not in manifest.parents:
                continue
            data = json.loads(manifest.read_text())
            if 'sourceSha' in data:
                require(data['sourceSha'] == source, 'manifest source SHA mismatch: ' + str(manifest))
            for name, claimed in data.get('fileSha256', {}).items():
                relative = Path(name)
                require(not relative.is_absolute() and relative.parts and '..' not in relative.parts,
                        'unsafe manifest relative path')
                candidate = manifest.parent / name
                require(candidate.is_file() and digest(candidate) == claimed, 'manifest reference mismatch: ' + str(candidate))
        files = {str(p.relative_to(self.root)): digest(p) for p in self.root.rglob('*')
                 if p.is_file() and self.output not in p.parents}
        pulled_files = {str(p.relative_to(root)): digest(p) for p in root.rglob('*') if p.is_file()}
        durable_json(self.output / 'evidence-inventory.json',
                     {'deviceGenerated': expected, 'pulled': pulled_files, 'hostBusiness': files,
                      'missingDeviceScope': exists.returncode == 1})
        durable_tree(self.root)
        durable_json(self.output / 'evidence-preserved.json',
                     {'evidencePreserved': True, 'inventorySha256': digest(self.output / 'evidence-inventory.json'),
                      'sourceSha': source, 'scopeUuid': self.owner['scopeUuid']})
        return True

    def finish(self, business_exit, quiescent=True):
        errors = []
        preserved = restored = deleted = load_ack = False
        try:
            self.verify_owner()
            preserved = self.preserve()
            require(quiescent, 'business/collector process group not proven quiescent')
            # The script process has exited; force-stop only after evidence is fully durable.
            self.checked('shell', 'am force-stop ' + PACKAGE)
            pid = self.adb('shell', 'pidof ' + PACKAGE)
            require(pid.returncode == 1 and pid.stdout.strip() == '' and pid.stderr == '', 'product process still alive/unknown')
            self.verify_owner()
            require(preserved, 'evidence not preserved')
            self.operation('load')  # Exactly one attempt, after verified evidence.
            load_ack = True
            require(self.adb('wait-for-device', timeout=90).returncode == 0, 'reconnect failed')
            self.verify_owner(require_snapshot=False)
            require(self.state('restored') == self.owner['baseline'], 'exact settings/framework restoration mismatch')
            restored = True
            require(self.names().count(self.owner['snapshot']) == 1, 'loaded snapshot name changed')
            self.operation('delete')
            require(self.owner['snapshot'] not in self.names(), 'own snapshot delete not confirmed')
            deleted = True
        except Exception as exc:
            errors.append(str(exc))
        result = {'businessExitStatus': business_exit, 'evidencePreserved': preserved,
                  'restoreStatus': 'PASS_EXACT' if restored else 'FAIL_OR_NOT_ATTEMPTED',
                  'snapshotCleanupStatus': 'DELETED_OWN_ONLY' if deleted else 'NOT_VERIFIED',
                  'saveVerified': True, 'loadAcknowledged': load_ack, 'errors': errors,
                  'owner': self.owner, 'operations': self.operations}
        try:
            durable_json(self.report_path, result)
        except Exception as exc:
            print('snapshot final report write failed: ' + str(exc), file=sys.stderr)
            return business_exit if business_exit else 1
        return business_exit if business_exit else (0 if preserved and restored and deleted and not errors else 1)

    def run(self):
        try:
            self.initialize()
        except Exception as exc:
            try:
                durable_json(self.report_path, {'businessExitStatus': None, 'saveVerified': False,
                                                'evidencePreserved': False, 'restoreStatus': 'NOT_ATTEMPTED',
                                                'snapshotCleanupStatus': 'NOT_ATTEMPTED', 'errors': [str(exc)],
                                                'operations': self.operations})
            except Exception as report_error:
                print('snapshot initialization/report failed: ' + str(exc) + '; ' + str(report_error), file=sys.stderr)
            return 1
        env = dict(self.env)
        env[self.config.owner_key] = str(self.owner_path)
        try:
            child = subprocess.Popen(['bash', self.config.business_script], env=env,
                                     start_new_session=True)
        except OSError as exc:
            try:
                durable_json(self.output / 'business-start-error.json', {'error': str(exc)})
            except Exception:
                pass
            return self.finish(1)
        terminated = False
        pgid = child.pid
        def stop_child(_signum, _frame):
            nonlocal terminated
            terminated = True
            try:
                os.killpg(pgid, signal.SIGTERM)
            except ProcessLookupError:
                pass
        previous = signal.signal(signal.SIGTERM, stop_child)
        quiescent = True
        try:
            while True:
                try:
                    business_exit = child.wait(timeout=1)
                    break
                except subprocess.TimeoutExpired:
                    if not terminated:
                        continue
                    try:
                        business_exit = child.wait(timeout=20)
                    except subprocess.TimeoutExpired:
                        quiescent = False
                        try:
                            os.killpg(pgid, signal.SIGKILL)
                        except ProcessLookupError:
                            pass
                        try:
                            business_exit = child.wait(timeout=10)
                        except subprocess.TimeoutExpired:
                            business_exit = 143
                    break
        finally:
            signal.signal(signal.SIGTERM, previous)
        try:
            os.killpg(pgid, 0)
            quiescent = False
        except ProcessLookupError:
            pass
        except PermissionError:
            quiescent = False
        if terminated and business_exit == 0:
            business_exit = 143
        elif business_exit < 0:
            business_exit = 128 - business_exit
        try:
            durable_json(self.output / 'business-exit.json',
                         {'businessExitStatus': business_exit, 'scopeUuid': self.owner['scopeUuid'],
                          'sourceSha': self.owner['sourceSha'], 'processGroupQuiescent': quiescent})
        except Exception as exc:
            print('business exit host record failed: ' + str(exc), file=sys.stderr)
            return business_exit if business_exit else 1
        return self.finish(business_exit, quiescent)


def verify(path, profile='scale'):
    config = profile_config(profile)
    owner_path = Path(path).resolve()
    output = (ROOT if profile == 'scale' else config.root) / 'snapshot-scope'
    require(owner_path == output / 'owner.json', 'unexpected owner path')
    scope = Scope(output=output, profile=profile)
    scope.owner = json.loads(owner_path.read_text())
    scope.verify_owner()


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    sub = parser.add_subparsers(dest='action', required=True)
    run_parser = sub.add_parser('run')
    run_parser.add_argument('--profile', choices=tuple(PROFILES), default='scale')
    verify_parser = sub.add_parser('verify')
    verify_parser.add_argument('--profile', choices=tuple(PROFILES), default='scale')
    verify_parser.add_argument('--owner', required=True)
    args = parser.parse_args()
    if args.action == 'verify':
        verify(args.owner, args.profile)
    else:
        sys.exit(Scope(profile=args.profile).run())
