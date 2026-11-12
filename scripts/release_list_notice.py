#!/usr/bin/env python3
"""Keep the first public Develop release's latest-version notice current."""
import os
import re

from develop_release_store import GitHub
from release_update_index import BASE, REPO, newest_release

START = '<!-- openhoyi-latest-develop:start -->'
END = '<!-- openhoyi-latest-develop:end -->'
HEADING = '## 本次更新\n\n'


def notice_body(body, tag):
    if not isinstance(body, str) or not re.fullmatch(r'dev-[1-9][0-9]*', tag):
        raise ValueError('Invalid release notice input')
    if START in body or END in body:
        if body.count(START) != 1 or body.count(END) != 1 or body.index(START) > body.index(END):
            raise ValueError('Ambiguous managed notice')
        begin, end = body.index(START), body.index(END) + len(END)
        body = body[:begin] + body[end:].removeprefix('\n\n')
    if not body.startswith(HEADING):
        raise ValueError('Release update heading missing')
    notice = (f'{START}\n> **最新测试版：[ {tag} ]({BASE}/releases/tag/{tag})**\n'
              '> GitHub 列表顺序可能与版本先后不同，请点击上方最新版本链接下载。\n'
              f'{END}\n\n')
    return HEADING + notice + body[len(HEADING):]


def sync_notice(api):
    releases = list(api.pages('releases'))
    latest = newest_release(releases)  # Numeric version, not GitHub list order.
    public = [r for r in releases if r.get('draft') is False]
    first = public[0]
    if first.get('prerelease') is not True or not re.fullmatch(r'dev-[1-9][0-9]*', first.get('tag_name', '')):
        raise ValueError('First public release is not a Develop prerelease')
    targets = [r for r in public if r['id'] == first['id'] or START in (r.get('body') or '')]
    for release in targets:
        path = f"releases/{release['id']}"
        current = api.request(path)  # Preserve the most recently edited notes.
        if (current.get('draft') is not False or current.get('prerelease') is not True
                or current.get('tag_name') != release['tag_name']):
            raise ValueError('Release identity changed')
        body = notice_body(current.get('body'), latest['tag_name'])
        if body != current['body']:
            api.request(path, 'PATCH', {'body': body})  # Never change tags, assets or channel.
        if api.request(path).get('body') != body:
            raise ValueError('Release notice readback failed')
    print('Release list notice verified: ' + latest['tag_name'])


if __name__ == '__main__':
    if os.environ.get('GITHUB_REPOSITORY') != REPO or os.environ.get('GITHUB_REF') != 'refs/heads/develop':
        raise SystemExit('Only the approved develop repository is allowed')
    sync_notice(GitHub(REPO, os.environ['GH_TOKEN']))
