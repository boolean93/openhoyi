#!/usr/bin/env python3
"""Fail-closed same-run Mock APK pair transport. All executed tools come from checkout/SDK."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess

from mock_runner_contract import APP, TEST, RUNNERS
COMMAND = './gradlew :mobile:assembleMock :mobile:assembleMockAndroidTest --no-daemon --console=plain'
DESTINATIONS = {'app.apk': 'mobile/build/outputs/apk/mock/mobile-mock.apk',
                'test.apk': 'mobile/build/outputs/apk/androidTest/mock/mobile-mock-androidTest.apk'}


def require(ok, message):
    if not ok:
        raise ValueError(message)


def run(*args):
    return subprocess.run(args, check=True, capture_output=True, text=True, timeout=120).stdout.strip()


def digest(path):
    result = hashlib.sha256()
    with Path(path).open('rb') as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b''):
            result.update(chunk)
    return result.hexdigest()


def identity():
    names = ('repository', 'event_name', 'ref', 'run_id', 'workflow_ref', 'workflow_sha', 'expected_sha')
    value = {name: os.environ['CI_' + name.upper()] for name in names}
    require(all(value.values()), 'Missing platform identity')
    value['pr'] = {name: os.environ.get('CI_PR_' + name.upper(), '') for name in ('number', 'base_sha', 'head_sha', 'merge_sha')}
    return value


def checkout():
    require(subprocess.run(['git', 'diff', '--quiet', 'HEAD', '--'], check=False).returncode == 0, 'Tracked checkout changed during build/transport')
    sha, tree = run('git', 'rev-parse', 'HEAD'), run('git', 'rev-parse', 'HEAD^{tree}')
    require(sha == identity()['expected_sha'], 'Checkout is not this run expected SHA')
    configs = [p for p in run('git', 'ls-files').splitlines()
               if p.endswith(('.gradle', '.gradle.kts', 'gradle.properties')) or p.startswith('gradle/') or p in ('gradlew', 'gradlew.bat')]
    return {'sha': sha, 'tree': tree, 'configHashes': {p: digest(p) for p in configs}}


def sdk_tool(name):
    return str(Path(os.environ['ANDROID_HOME']) / 'build-tools/35.0.0' / name)


def inspect_apk(path, kind):
    badging = run(sdk_tool('aapt'), 'dump', 'badging', str(path))
    match = re.search(r"^package: name='([^']+)' versionCode='([^']*)' versionName='([^']*)'", badging, re.M)
    require(match is not None, 'APK package/version unreadable')
    package, code, name = match.groups()
    require(package == (APP if kind == 'app.apk' else TEST), 'Wrong APK package or swapped pair')
    require((code, name) == (('1', '0.1.0') if kind == 'app.apk' else ('', '')), 'Unexpected APK version override')
    require("sdkVersion:'26'" in badging and "targetSdkVersion:'35'" in badging and "compileSdkVersion='35'" in badging,
            'APK SDK contract changed')
    require('application-debuggable' in badging, 'Expected CI debug APK')
    xmltree = run(sdk_tool('aapt'), 'dump', 'xmltree', str(path), 'AndroidManifest.xml')
    runners = {}
    for block in re.split(r'(?m)^    E: ', xmltree)[1:]:
        if block.startswith('instrumentation '):
            runner = re.search(r'android:name\([^\n]+?="([^"]+)"', block)
            target = re.search(r'android:targetPackage\([^\n]+?="([^"]+)"', block)
            require(runner is not None and target is not None and runner[1] not in runners, 'Invalid runner declaration')
            runners[runner[1]] = target[1]
    require(runners == ({} if kind == 'app.apk' else RUNNERS), 'Unexpected instrumentation/target map')
    signing = run(sdk_tool('apksigner'), 'verify', '--verbose', '--print-certs', str(path))
    certs = re.findall(r'^Signer #\d+ certificate SHA-256 digest: ([0-9a-f]{64})$', signing, re.M)
    require(len(certs) == 1 and 'CN=Android Debug' in signing, 'Expected one CI Android Debug signer')
    require('Verified using v2 scheme (APK Signature Scheme v2): true' in signing, 'APK v2 signature verification failed')
    return {'package': package, 'versionCode': code, 'versionName': name, 'runners': runners,
            'certificateSha256': certs[0], 'signatureVerified': True, 'minSdk': 26, 'targetSdk': 35, 'compileSdk': 35}


def tools():
    gradle = run('./gradlew', '--version', '--no-daemon', '--console=plain')
    require(re.search(r'^Gradle 8\.13$', gradle, re.M), 'Unexpected actual Gradle version')
    root_build = Path('build.gradle.kts').read_text()
    require('version "2.3.10"' in root_build and 'version "8.13.2"' in root_build, 'Plugin configuration changed')
    java = subprocess.run(['java', '-version'], check=True, capture_output=True, text=True, timeout=15).stderr.strip()
    require(re.search(r'version "17[."]', java) is not None, 'Expected actual JDK17')
    sdk = Path(os.environ['ANDROID_HOME'])
    return {'gradle': '8.13', 'agp': '8.13.2', 'kotlin': '2.3.10',
            'java': java,
            'aapt': run(sdk_tool('aapt'), 'version'), 'apksigner': run(sdk_tool('apksigner'), 'version'),
            'adb': '\n'.join(line for line in run(str(sdk / 'platform-tools/adb'), '--version').splitlines() if not line.startswith(('Installed as ', 'Running on '))),
            'platformProperties': (sdk / 'platforms/android-35/source.properties').read_text(),
            'buildToolsProperties': (sdk / 'build-tools/35.0.0/source.properties').read_text(),
            'buildTools': '35.0.0', 'runnerImage': os.environ['ImageOS'], 'runnerImageVersion': os.environ['ImageVersion']}


def build_manifest(folder, attempt):
    require(attempt.isdecimal() and int(attempt) > 0, 'Missing producer attempt')
    folder.mkdir(parents=True, exist_ok=False)
    files = {}
    for name, source in DESTINATIONS.items():
        require(Path(source).is_file() and not Path(source).is_symlink(), 'Missing actual built APK')
        shutil.copyfile(source, folder / name)
        files[name] = {'size': (folder / name).stat().st_size, 'sha256': digest(folder / name),
                       'apk': inspect_apk(folder / name, name)}
    require(files['app.apk']['apk']['certificateSha256'] == files['test.apk']['apk']['certificateSha256'], 'Pair signer mismatch')
    manifest = {'schema': 'same-run-mock-pair-v1', 'identity': identity(), 'producerAttempt': attempt,
                'checkout': checkout(), 'command': COMMAND, 'properties': {}, 'variant': 'mock',
                'mockMode': True, 'tools': tools(), 'files': files}
    (folder / 'manifest.json').write_text(json.dumps(manifest, sort_keys=True, indent=2) + '\n')
    return manifest


def validate(folder, expected_manifest_hash, expected_sha, producer_attempt, producer_result, actual_checkout, actual_tools, inspect=inspect_apk):
    require(producer_result == 'success', 'Producer did not succeed; no fallback build')
    require(re.fullmatch(r'[0-9a-f]{64}', expected_manifest_hash or '') is not None, 'Missing producer manifest hash')
    require(re.fullmatch(r'[0-9a-f]{40}', expected_sha or '') is not None, 'Missing producer resolved SHA')
    require(set(p.name for p in folder.iterdir()) == {'app.apk', 'test.apk', 'manifest.json'}, 'Artifact must contain exactly the three owned files')
    require(all(p.is_file() and not p.is_symlink() for p in folder.iterdir()), 'Artifact contains symlink/non-file')
    require(digest(folder / 'manifest.json') == expected_manifest_hash, 'Manifest hash differs from producer output')
    manifest = json.loads((folder / 'manifest.json').read_text())
    require(manifest.get('schema') == 'same-run-mock-pair-v1', 'Unknown manifest schema')
    require(manifest['identity'] == identity(), 'Cross-run/repository/event/ref/workflow source mismatch')
    require(manifest['producerAttempt'] == producer_attempt and producer_attempt.isdecimal() and
            0 < int(producer_attempt) <= int(os.environ['GITHUB_RUN_ATTEMPT']), 'Wrong producer attempt')
    require(manifest['checkout'] == actual_checkout and actual_checkout['sha'] == expected_sha == identity()['expected_sha'], 'Wrong commit/tree/config')
    require(manifest['command'] == COMMAND and manifest['properties'] == {} and manifest['variant'] == 'mock' and manifest['mockMode'] is True,
            'Build command/properties/variant changed')
    require(manifest['tools'] == actual_tools, 'Build/consumer toolchain differs')
    require(set(manifest['files']) == set(DESTINATIONS), 'Missing APK pair')
    for name, record in manifest['files'].items():
        path = folder / name
        require(record['size'] == path.stat().st_size and record['sha256'] == digest(path), 'APK size/hash mismatch')
        require(record['apk'] == inspect(path, name), 'Actual APK identity/version/runner/signature differs')
    require(manifest['files']['app.apk']['apk']['certificateSha256'] == manifest['files']['test.apk']['apk']['certificateSha256'], 'Pair signer mismatch')
    return manifest


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('mode', choices=['produce', 'consume'])
    parser.add_argument('--directory', type=Path, required=True)
    parser.add_argument('--producer-attempt', required=True)
    parser.add_argument('--producer-result', default='')
    parser.add_argument('--manifest-sha', default='')
    parser.add_argument('--resolved-sha', default='')
    args = parser.parse_args()
    if args.mode == 'produce':
        manifest = build_manifest(args.directory, args.producer_attempt)
        with open(os.environ['GITHUB_OUTPUT'], 'a') as out:
            out.write('resolved-sha=' + manifest['checkout']['sha'] + '\n')
            out.write('manifest-sha=' + digest(args.directory / 'manifest.json') + '\n')
            out.write('producer-attempt=' + args.producer_attempt + '\n')
    else:
        require(re.fullmatch(r'[1-9][0-9]*', os.environ.get('CI_ARTIFACT_ID', '')) is not None, 'Missing immutable artifact ID')
        manifest = validate(args.directory, args.manifest_sha, args.resolved_sha, args.producer_attempt,
                            args.producer_result, checkout(), tools())
        for name, destination in DESTINATIONS.items():
            path = Path(destination)
            path.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(args.directory / name, path)
        evidence = Path('build/single-screen-ui/shared-build.json')
        evidence.parent.mkdir(parents=True, exist_ok=True)
        evidence.write_text(json.dumps({'artifactId': os.environ['CI_ARTIFACT_ID'], 'manifestSha256': args.manifest_sha,
                                       'manifest': manifest}, indent=2) + '\n')
        print('SHARED_MOCK_PAIR_ACCEPTED sha=' + manifest['checkout']['sha'])


if __name__ == '__main__':
    main()
