#!/usr/bin/env python3
"""Repository branch/PR/tag gate. No build, BLE, credentials or publishing."""
import argparse
import json
import os
from pathlib import Path
import re
import subprocess
import sys

ROOT = Path(__file__).resolve().parent.parent
VERSION = r'(?:0|[1-9][0-9]*)\.(?:0|[1-9][0-9]*)\.(?:0|[1-9][0-9]*)'
WORK = re.compile(r'(?:(?:feature|fix|chore|docs)/[a-z0-9]+(?:-[a-z0-9]+)*|(?:release|hotfix)/' + VERSION + r')\Z')
TAG = re.compile(r'v(' + VERSION + r')(?:-rc\.([1-9][0-9]*))?\Z')


def check_branch(name):
    if not WORK.fullmatch(name):
        raise ValueError('Use feature|fix|chore|docs/<lowercase-slug> or release|hotfix/<X.Y.Z>')


def check_pr(head, base, body):
    check_branch(head)
    kind = head.split('/', 1)[0]
    if base not in ('main', 'develop') or (base == 'main' and kind not in ('release', 'hotfix')):
        raise ValueError('Work PRs target develop; only release/hotfix PRs may target main')
    for section in ('规划与范围', '验证证据', '协议与设备风险', '发布与回退'):
        match = re.search(r'^## ' + section + r'\s*\n(.*?)(?=^## |\Z)', body, re.M | re.S)
        if not match or not re.sub(r'<!--.*?-->', '', match[1], flags=re.S).strip():
            raise ValueError('PR needs a nonempty section: ' + section)
    plans = re.findall(r'docs/(?:superpowers/plans|superpowers/specs|plans)/[A-Za-z0-9_./-]+\.md', body)
    if not any((ROOT / p).is_file() and (ROOT / p).resolve().is_relative_to(ROOT / 'docs') for p in plans):
        raise ValueError('PR must link an existing repository plan/spec')


def check_tag(tag, on_main, on_release):
    match = TAG.fullmatch(tag)
    if not match:
        raise ValueError('Only vX.Y.Z or vX.Y.Z-rc.N tags are allowed')
    if (match[2] and not on_release) or (not match[2] and not on_main):
        raise ValueError('Stable tag must be on main; RC must be on matching release/X.Y.Z')


def check_push_ref(ref, local_sha, remote_sha):
    if local_sha == '0' * 40 or local_sha == '0' * 64:
        raise ValueError('Delete remote branches/tags only via reviewed GitHub cleanup')
    if ref.startswith('refs/heads/'):
        check_branch(ref[len('refs/heads/'):])
    elif ref.startswith('refs/tags/'):
        if remote_sha.strip('0'):
            raise ValueError('Existing tags cannot be overwritten')
    else:
        raise ValueError('Unsupported remote ref')


def git(*args):
    return subprocess.check_output(['git', *args], cwd=ROOT, text=True, stderr=subprocess.PIPE).strip()


def ancestor(commit, ref):
    return subprocess.run(['git', 'merge-base', '--is-ancestor', commit, ref], cwd=ROOT,
                          stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL).returncode == 0


def tag_gate(tag, commit):
    match = TAG.fullmatch(tag)
    if not match:
        raise ValueError('Invalid release tag')
    if git('cat-file', '-t', 'refs/tags/' + tag) != 'tag':
        raise ValueError('Release requires an annotated tag')
    if git('rev-parse', 'refs/tags/' + tag + '^{commit}') != commit:
        raise ValueError('Release tag must resolve to the checked commit')
    version = match[1]
    check_tag(tag, ancestor(commit, 'origin/main'), ancestor(commit, 'origin/release/' + version))
    record = 'docs/releases/' + tag + '.json'
    try:
        data = json.loads(git('show', commit + ':' + record))
    except subprocess.CalledProcessError as error:
        raise ValueError('Missing reviewed release record in tagged commit: ' + record) from error
    if not isinstance(data, dict):
        raise ValueError('Release record must be a JSON object')
    if data.get('tag') != tag or data.get('approved') is not True:
        raise ValueError('Release record must match tag/commit and explicit release approval')
    source = data.get('sourceCommit', '')
    if not re.fullmatch(r'[a-f0-9]{40}', str(source)) or not ancestor(source, commit):
        raise ValueError('Release source must be a recorded ancestor of tag commit')
    changed = git('diff', '--name-only', source, commit).splitlines()
    if any(p != 'docs/releases/' + tag + '.json' for p in changed):
        raise ValueError('Tag may add only its approval record after the APK source commit')
    if not isinstance(data.get('versionCode'), int) or isinstance(data['versionCode'], bool) or data['versionCode'] <= 0:
        raise ValueError('Release needs a positive versionCode')
    for key in ('signerCertificateSha256', 'apkSha256'):
        if not re.fullmatch(r'[a-f0-9]{64}', str(data.get(key, ''))):
            raise ValueError('Release needs actual APK verification hash: ' + key)
    for key in ('approvalReference', 'validationEvidence', 'upgradeEvidence', 'rollbackPlan'):
        if not isinstance(data.get(key), str) or not data[key].strip():
            raise ValueError('Release needs reviewed evidence: ' + key)
    if not match[2] and not data.get('hardwareAcceptanceEvidence'):
        raise ValueError('Stable release needs hardware acceptance evidence')


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('mode', choices=['ci', 'pre-push', 'branch', 'tag'])
    parser.add_argument('--name')
    args = parser.parse_args()
    if args.mode == 'branch':
        check_branch(args.name or git('branch', '--show-current'))
    elif args.mode == 'tag':
        if not args.name:
            raise ValueError('Use tag --name vX.Y.Z[-rc.N]')
        tag_gate(args.name, git('rev-parse', 'refs/tags/' + args.name + '^{commit}'))
    elif args.mode == 'pre-push':
        lines = list(sys.stdin)
        if not lines:
            raise ValueError('pre-push needs Git ref input; use tag --name for manual preflight')
        for line in lines:
            local, sha, remote, previous = line.split()
            check_push_ref(remote, sha, previous)
            if remote.startswith('refs/tags/'):
                tag_gate(remote[len('refs/tags/'):], git('rev-parse', sha + '^{commit}'))
            elif previous.strip('0') and not ancestor(previous, sha):
                raise ValueError('Non-fast-forward push refused; fetch and integrate first')
    else:
        event = json.loads(Path(os.environ['GITHUB_EVENT_PATH']).read_text())
        if os.environ['GITHUB_EVENT_NAME'] == 'pull_request':
            pr = event['pull_request']
            check_pr(pr['head']['ref'], pr['base']['ref'], pr.get('body') or '')
        elif os.environ.get('GITHUB_REF_TYPE') == 'tag':
            tag_gate(os.environ['GITHUB_REF_NAME'], git('rev-parse', 'HEAD'))
        else:
            name = os.environ['GITHUB_REF_NAME']
            if name not in ('main', 'develop'):
                check_branch(name)
    print('GIT_POLICY_CHECKS_PASSED')


if __name__ == '__main__':
    try:
        main()
    except (ValueError, OSError, subprocess.CalledProcessError) as error:
        print('Git policy rejected: ' + str(error), file=sys.stderr)
        sys.exit(1)
