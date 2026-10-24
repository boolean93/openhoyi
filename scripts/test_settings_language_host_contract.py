"""Replay the original language Dialog evidence against the actual host verifier."""

import ast
import copy
import hashlib
import json
from pathlib import Path, PurePosixPath
import tempfile
import unittest
from zipfile import ZipFile


REPO = Path(__file__).resolve().parent.parent
ARTIFACT = REPO / 'build/task-first-evidence/ci-3a78c4c/settings-artifact.zip'
ARTIFACT_SHA = '3bbb97e13964d7b116551f8dedf8d5df1c085d94cb05abb4fdb2a8dfbc55d315'
SOURCE = REPO / 'scripts/check_settings_visual_migration.sh'
CELL = ('5b32705446f285afc12e0d403484f3ab66d01495/'
        'tablet-user-landscape-light-font-1.3/app/special-states/language-write-rejected/')
FILES = ('window-frame-sync.json', 'geometry.json', 'safe-controls.json',
         'dialog-typography.json', 'window.png', 'window-display-after.png',
         'window-layer-before.png', 'window-layer-after.png')


def verifier_functions():
    source = SOURCE.read_text().split("<<'VERIFY_PY'\n", 1)[1].split('\nVERIFY_PY', 1)[0]
    tree = ast.parse(source)
    functions = ast.Module(body=[node for node in tree.body if isinstance(node, ast.FunctionDef)],
                           type_ignores=[])
    namespace = {'json': json, 'sha': lambda path: hashlib.sha256(path.read_bytes()).hexdigest()}
    exec(compile(functions, str(SOURCE), 'exec'), namespace)
    return namespace


class ArchivePath:
    """Read-only Path subset used to run the original VERIFY_PY body over ZIP entries."""

    def __init__(self, archive, names, path):
        self.archive, self.names, self.path = archive, names, str(PurePosixPath(path))

    def __truediv__(self, child):
        return ArchivePath(self.archive, self.names, PurePosixPath(self.path) / str(child))

    @property
    def parent(self):
        return ArchivePath(self.archive, self.names, PurePosixPath(self.path).parent)

    @property
    def name(self):
        return PurePosixPath(self.path).name

    def is_file(self):
        return self.path in self.names

    def read_bytes(self):
        return self.archive.read(self.path)

    def read_text(self):
        return self.read_bytes().decode()

    def rglob(self, pattern):
        return [ArchivePath(self.archive, self.names, name) for name in sorted(self.names)
                if name.startswith(self.path + '/') and PurePosixPath(name).match(pattern)]

    def __eq__(self, other):
        return isinstance(other, ArchivePath) and self.path == other.path

    def __hash__(self):
        return hash(self.path)

    def __str__(self):
        return self.path


class SettingsLanguageHostContract(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        digest = hashlib.sha256()
        with ARTIFACT.open('rb') as stream:
            for block in iter(lambda: stream.read(1024 * 1024), b''):
                digest.update(block)
        assert digest.hexdigest() == ARTIFACT_SHA
        cls.functions = verifier_functions()
        with ZipFile(ARTIFACT) as archive:
            cls.original = {name: archive.read(CELL + name) for name in FILES}

    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.folder = (Path(self.temp.name) / 'app/special-states/language-write-rejected')
        self.folder.mkdir(parents=True)
        for name, contents in self.original.items():
            (self.folder / name).write_bytes(contents)

    def proof(self):
        return json.loads((self.folder / 'window-frame-sync.json').read_text())

    def save_proof(self, proof):
        (self.folder / 'window-frame-sync.json').write_text(json.dumps(proof))

    def accepted(self):
        proof = self.proof()
        return proof['attempts'][proof['acceptedAttempt'] - 1]['geometryBefore']['target']

    def safe(self):
        return json.loads((self.folder / 'safe-controls.json').read_text())

    def require_protocol(self):
        return self.functions['require_protocol'](self.folder / 'window-frame-sync.json', 'EXPLICIT_WINDOW')

    def validate_dialog(self, accepted=None, safe=None):
        proof = self.proof()
        self.functions['validate_language_dialog_target'](
            accepted or self.accepted(), safe or self.safe(), proof)

    def test_original_four_artifacts_are_accepted(self):
        proof = self.require_protocol()
        self.assertEqual(proof['acceptedAttempt'], 1)
        self.assertEqual(len(proof['captureArtifacts']), 4)

    def test_original_null_control_focus_is_accepted(self):
        self.assertIsNone(self.safe()['focusedControlIdentity'])
        self.validate_dialog()

    def test_artifact_mutations_are_rejected(self):
        original = self.proof()
        changes = {
            'missing_role': lambda p: p['captureArtifacts'].pop(),
            'duplicate_role': lambda p: p['captureArtifacts'][3].update(role='display'),
            'extra_role': lambda p: p['captureArtifacts'].append(copy.deepcopy(p['captureArtifacts'][0])),
            'wrong_attempt': lambda p: p['captureArtifacts'][0].update(attempt=2),
            'wrong_filename': lambda p: p['captureArtifacts'][0].update(file='other.png'),
            'reused_filename': lambda p: p['captureArtifacts'][0].update(file='window.png'),
            'wrong_sha': lambda p: p['captureArtifacts'][0].update(fileSha256='0' * 64),
            'wrong_bitmap_role': lambda p: p['captureArtifacts'][0].update(bitmap=p['attempts'][0]['display']),
        }
        for name, change in changes.items():
            with self.subTest(name=name):
                candidate = copy.deepcopy(original)
                change(candidate)
                self.save_proof(candidate)
                with self.assertRaises(AssertionError):
                    self.require_protocol()

    def test_four_roles_rejected_outside_exact_measured_dialog(self):
        original = self.proof()
        for name in ('theme-write-rejected', 'ordinary-dialog'):
            with self.subTest(path=name):
                old = self.folder
                self.folder = old.parent / name
                self.folder.mkdir(parents=True, exist_ok=True)
                for filename, contents in self.original.items():
                    (self.folder / filename).write_bytes(contents)
                with self.assertRaises(AssertionError):
                    self.require_protocol()
                self.folder = old
        for key, value in (('captureWindowKind', 'ACTIVITY'),
                           ('mappingMode', 'EXPLICIT_WINDOW_MEASURED_DECOR')):
            with self.subTest(spoof=key):
                candidate = copy.deepcopy(original)
                candidate[key] = value
                self.save_proof(candidate)
                with self.assertRaises(AssertionError):
                    self.require_protocol()

    def test_window_focus_and_identity_remain_strict(self):
        original = self.proof()
        changes = {
            'window_focus_lost': lambda p: p['attempts'][0]['geometryBefore'].update(hasWindowFocus=False),
            'window_identity_changed': lambda p: p['attempts'][0]['geometryBefore'].update(windowIdentity=999999),
        }
        for name, change in changes.items():
            with self.subTest(name=name):
                candidate = copy.deepcopy(original)
                change(candidate)
                self.save_proof(candidate)
                with self.assertRaises(AssertionError):
                    self.require_protocol()

    def test_real_unique_focused_control_is_accepted(self):
        accepted = copy.deepcopy(self.accepted())
        safe = copy.deepcopy(self.safe())
        control = safe['controls'][0]
        control['roles'].append('focused')
        safe['focusedControlIdentity'] = control['viewIdentity']
        accepted['safeControls'] = safe
        self.validate_dialog(accepted, safe)

    def test_inconsistent_focus_identity_and_ownership_are_rejected(self):
        changes = {
            'null_with_focused': lambda a, s: s['controls'][0]['roles'].append('focused'),
            'id_without_focused': lambda a, s: s.update(focusedControlIdentity=s['controls'][0]['viewIdentity']),
            'wrong_focused_id': lambda a, s: (s['controls'][0]['roles'].append('focused'),
                                               s.update(focusedControlIdentity=999999)),
            'hidden_focused': lambda a, s: (s['controls'][0]['roles'].append('focused'),
                                             s['controls'][0].update(isShown=False),
                                             s.update(focusedControlIdentity=s['controls'][0]['viewIdentity'])),
            'duplicate_control': lambda a, s: s['controls'].append(copy.deepcopy(s['controls'][0])),
            'duplicate_node': lambda a, s: a['nodes'].append(copy.deepcopy(next(
                n for n in a['nodes'] if n['viewIdentity'] == s['controls'][0]['viewIdentity']))),
            'duplicate_role': lambda a, s: s['controls'][0]['roles'].append('negative'),
            'duplicate_button_role': lambda a, s: s['buttonRoles'][0].update(role='negative'),
            'false_negative_identity': lambda a, s: s['buttonRoles'][1].update(identity=999999),
            'ime_visible': lambda a, s: a.update(imeVisible=True),
            'edit_text': lambda a, s: s['controls'][0].update(isEditText=True),
            'window_focus_lost': lambda a, s: s.update(decorHasWindowFocus=False),
            'wrong_owner': lambda a, s: s.update(ownerMatches=False),
        }
        for name, change in changes.items():
            with self.subTest(name=name):
                accepted = copy.deepcopy(self.accepted())
                safe = copy.deepcopy(self.safe())
                change(accepted, safe)
                accepted['safeControls'] = safe
                with self.assertRaises(AssertionError):
                    self.validate_dialog(accepted, safe)

    def test_entire_app_original_verify_body_passes_offline(self):
        source = SOURCE.read_text().split("<<'VERIFY_PY'\n", 1)[1].split('\nVERIFY_PY', 1)[0]
        tree = ast.parse(source)
        start = next(i for i, node in enumerate(tree.body) if isinstance(node, ast.Assign)
                     and any(isinstance(target, ast.Name) and target.id == 'references'
                             for target in node.targets))
        stop = next(i for i, node in enumerate(tree.body) if isinstance(node, ast.Assign)
                    and any(isinstance(target, ast.Name) and target.id == 'verification_errors'
                            for target in node.targets))
        loop = next(node for node in tree.body[stop:] if isinstance(node, ast.For)
                    and isinstance(node.target, ast.Name) and node.target.id == 'path')
        body = next(node.body for node in loop.body if isinstance(node, ast.Try))
        with ZipFile(ARTIFACT) as archive:
            names = set(archive.namelist())
            root = ArchivePath(archive, names, CELL.split('/', 1)[0])
            path = ArchivePath(archive, names, CELL.rsplit('/special-states/', 1)[0] + '/manifest.json')
            namespace = {'json': json, 'sha': lambda item: hashlib.sha256(item.read_bytes()).hexdigest(),
                         'root': root, 'seen': set(), 'pending': [], 'protocol_count': 0,
                         'struct': __import__('struct')}
            definitions = ast.Module(body=tree.body[start:stop], type_ignores=[])
            exec(compile(definitions, str(SOURCE), 'exec'), namespace)
            namespace.update(path=path, folder=path.parent, m=json.loads(path.read_text()))
            exec(compile(ast.Module(body=body, type_ignores=[]), str(SOURCE), 'exec'), namespace)
            self.assertEqual(namespace['seen'], {('app', 'tablet-user-landscape-light-font-1.3')})
            self.assertGreater(namespace['protocol_count'], 0)


if __name__ == '__main__':
    unittest.main()
