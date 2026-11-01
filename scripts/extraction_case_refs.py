"""Read one original M5 case at a time from a versioned, hash-bound summary."""
import hashlib
import json
import re
import stat
from pathlib import Path

SCHEMA = 'M5_EXTRACTION_CASE_REFS_V1'
CASE_NAMES = ('STARTING', 'STOP_REQUESTED', 'UNKNOWN_live', 'no_scale', 'stale_scale',
              'no_service_ready', 'manual', 'historical_reference_toggle',
              'missing_samples', 'water_reset', 'sample_gap', 'journal_unavailable')
PASS = 'PASS_MECHANICAL_ONLY_NOT_VISUAL_ACCEPTANCE'
REF_KEYS = {'ordinal', 'case', 'path', 'sizeBytes', 'sha256', 'status',
            'sourceSha', 'profile', 'declaredShotId'}


def _file_identity(info):
    return (info.st_dev, info.st_ino, info.st_size, info.st_mtime_ns, info.st_ctime_ns)


def iter_extraction_cases(base, summary, source_sha, profile, expected_names, scenario_ids, evidence_root):
    """Validate the complete small index, then yield each original case for the old gates."""
    base = Path(base)
    assert re.fullmatch(r'[0-9a-f]{40}', source_sha)
    assert profile in {f'tablet-user-landscape-{theme}-font-{scale}'
                       for theme in ('light', 'dark') for scale in ('1.0', '1.3')}
    assert tuple(expected_names) == CASE_NAMES
    assert summary['caseStorageSchema'] == SCHEMA, 'Unsupported or inline M5 case storage'
    assert summary['sourceSha'] == source_sha and summary['profile'] == profile and summary['page'] == 'extraction'
    assert summary['completed'] is True and summary['actualAdditionalActivityCaptures'] == 12
    assert summary['expectedCases'] == list(CASE_NAMES) and summary['scenarioShotIds'] == scenario_ids
    assert set(scenario_ids) == set(CASE_NAMES) and len(set(scenario_ids.values())) == 12
    refs = summary['cases']
    assert isinstance(refs, list) and len(refs) == 12
    for ordinal, (name, ref) in enumerate(zip(CASE_NAMES, refs), 1):
        assert isinstance(ref, dict) and set(ref) == REF_KEYS, 'Case index must contain only bounded metadata'
        assert type(ref['ordinal']) is int and ref['ordinal'] == ordinal and ref['case'] == name
        assert ref['path'] == f'{name}/case.json', 'Case path is not the exact owned relative path'
        assert ref['sourceSha'] == source_sha and ref['profile'] == profile
        assert ref['declaredShotId'] == scenario_ids[name] and ref['status'] == PASS
        assert type(ref['sizeBytes']) is int and ref['sizeBytes'] > 0
        assert isinstance(ref['sha256'], str) and re.fullmatch(r'[0-9a-f]{64}', ref['sha256'])
    evidence_root = Path(evidence_root)
    assert evidence_root.name == source_sha
    assert base == evidence_root / profile / 'extraction' / 'special-states'
    ownership_chain = (evidence_root, evidence_root / profile, base.parent, base)
    for directory in ownership_chain:
        assert directory.is_dir() and not directory.is_symlink()
    owned_root = base.resolve(strict=True)
    for name, ref in zip(CASE_NAMES, refs):
        directory = base / name
        path = directory / 'case.json'
        assert directory.is_dir() and not directory.is_symlink()
        assert directory.resolve(strict=True).parent == owned_root
        assert not path.is_symlink() and path.is_file()
        before = path.stat(follow_symlinks=False)
        assert stat.S_ISREG(before.st_mode) and before.st_size == ref['sizeBytes']
        assert path.resolve(strict=True).parent == directory.resolve(strict=True)
        payload = path.read_bytes()
        after = path.stat(follow_symlinks=False)
        for owner in (*ownership_chain, directory):
            assert owner.is_dir() and not owner.is_symlink(), 'Case ownership changed during read'
        assert base.resolve(strict=True) == owned_root
        assert directory.resolve(strict=True).parent == owned_root
        assert stat.S_ISREG(after.st_mode) and _file_identity(before) == _file_identity(after), 'Case changed during read'
        assert len(payload) == ref['sizeBytes'] and hashlib.sha256(payload).hexdigest() == ref['sha256']
        case = json.loads(payload)
        del payload
        assert isinstance(case, dict) and case['case'] == name
        assert case['sourceSha'] == source_sha and case['profile'] == profile
        assert case['declaredShotId'] == scenario_ids[name] and case['status'] == ref['status'] == PASS
        yield case
        del case
