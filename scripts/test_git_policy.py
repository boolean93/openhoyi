import importlib.util
import pathlib
import unittest

SPEC = importlib.util.spec_from_file_location('git_policy', pathlib.Path(__file__).with_name('git_policy.py'))
policy = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(policy)

BODY = '''## 规划与范围
Plan: docs/superpowers/plans/2026-10-09-git-governance.md
## 验证证据
Python tests passed
## 协议与设备风险
No BLE change
## 发布与回退
No release; revert PR
'''

class GitPolicyTests(unittest.TestCase):
    def test_work_branch_names(self):
        for name in ['feature/scale-reconnect', 'fix/stop', 'chore/git-governance', 'docs/protocol', 'release/1.2.3', 'hotfix/1.2.4']:
            with self.subTest(name=name): policy.check_branch(name)
        for name in ['main', 'develop', 'feature/', 'feature/Bad', 'test', 'release/no-version', 'hotfix/01.2.3', 'feature/a/b']:
            with self.subTest(name=name), self.assertRaises(ValueError): policy.check_branch(name)

    def test_integration_routes(self):
        for head,base in [('feature/scale','develop'),('fix/stop','develop'),('chore/ci','develop'),('docs/architecture','develop'),('release/1.2.3','main'),('release/1.2.3','develop'),('hotfix/1.2.4','main'),('hotfix/1.2.4','develop')]:
            policy.check_pr(head,base,BODY)
        for head,base in [('feature/scale','main'),('develop','main'),('main','develop'),('fix/stop','release/1.2.3'),('feature/scale','feature/other')]:
            with self.subTest(head=head,base=base), self.assertRaises(ValueError): policy.check_pr(head,base,BODY)

    def test_pr_needs_real_plan_and_all_sections(self):
        for body in ['', BODY.replace('## 验证证据','## Evidence'), BODY.replace('2026-10-09-git-governance.md','missing.md')]:
            with self.assertRaises(ValueError): policy.check_pr('chore/ci','develop',body)
        policy.check_pr('chore/ci','develop',BODY)

    def test_release_tags_and_commit_membership(self):
        policy.check_tag('v1.2.3', True, False)
        policy.check_tag('v1.2.3-rc.1', False, True)
        for tag,main,release in [('v1.2.3',False,True),('v1.2.3-rc.1',True,False),('1.2.3',True,True),('v01.2.3',True,True),('v1.2.3-rc.0',True,True),('v1.2.3-beta',True,True)]:
            with self.subTest(tag=tag), self.assertRaises(ValueError): policy.check_tag(tag,main,release)

    def test_local_push_protected_and_deletions(self):
        policy.check_push_ref('refs/heads/feature/scale','abc','def')
        for ref,local,remote in [('refs/heads/main','abc','def'),('refs/heads/develop','abc','def'),('refs/heads/feature/scale','0'*40,'def'),('refs/tags/v1.2.3','abc','def')]:
            with self.subTest(ref=ref), self.assertRaises(ValueError): policy.check_push_ref(ref,local,remote)


class ReleaseRecordTests(unittest.TestCase):
    def test_real_git_release_record_can_reference_previous_source_commit(self):
        import json
        import subprocess
        import tempfile
        with tempfile.TemporaryDirectory() as temp:
            root = pathlib.Path(temp)
            def git(*args):
                return subprocess.check_output(['git', *args], cwd=root, text=True, stderr=subprocess.DEVNULL).strip()
            git('init', '-q'); git('config', 'user.name', 'Fixture'); git('config', 'user.email', 'fixture@example.invalid')
            (root / 'source.txt').write_text('fixture')
            git('add', '.'); git('commit', '-qm', 'source')
            source = git('rev-parse', 'HEAD')
            record = root / 'docs/releases/v1.2.3.json'; record.parent.mkdir(parents=True)
            data = dict(tag='v1.2.3', sourceCommit=source, approved=True, versionCode=2,
                        signerCertificateSha256='a'*64, apkSha256='b'*64,
                        approvalReference='fixture approval', validationEvidence='fixture CI',
                        upgradeEvidence='fixture upgrade', rollbackPlan='fixture rollback', hardwareAcceptanceEvidence='fixture hardware')
            record.write_text(json.dumps(data));git('add','.');git('commit','-qm','approval')
            commit=git('rev-parse','HEAD');git('update-ref','refs/remotes/origin/main',commit);git('tag','-a','v1.2.3','-m','fixture release',commit)
            previous=policy.ROOT;policy.ROOT=root
            try:
                policy.tag_gate('v1.2.3',commit)
                for key,value in [('approved',False),('sourceCommit','0'*40),('apkSha256','bad'),('versionCode',True),('hardwareAcceptanceEvidence','')]:
                    changed=dict(data);changed[key]=value;record.write_text(json.dumps(changed))
                    git('add','.');git('commit','-qm','invalid approval')
                    bad=git('rev-parse','HEAD');git('tag','-fa','v1.2.3','-m','fixture release',bad);git('update-ref','refs/remotes/origin/main',bad)
                    with self.subTest(key=key),self.assertRaises(ValueError):policy.tag_gate('v1.2.3',bad)
                    git('reset','--hard',commit);git('tag','-fa','v1.2.3','-m','fixture release',commit);git('update-ref','refs/remotes/origin/main',commit)
                record.write_text(json.dumps(dict(data,approved=False)))
                policy.tag_gate('v1.2.3',commit)  # Ignore uncommitted changes; inspect tag tree.
                git('tag','-fa','v1.2.3','-m','fixture release',source);git('update-ref','refs/remotes/origin/main',source)
                record.write_text(json.dumps(data))
                with self.assertRaises(ValueError):policy.tag_gate('v1.2.3',source)  # Source has no approval file.
                git('tag','-fa','v1.2.3','-m','fixture release',commit);git('update-ref','refs/remotes/origin/main',commit)
                (root/'source.txt').write_text('changed code')
                git('add','.');git('commit','-qm','changed source');changed_commit=git('rev-parse','HEAD');git('update-ref','refs/remotes/origin/main',changed_commit);git('tag','-fa','v1.2.3','-m','fixture release',changed_commit)
                with self.assertRaises(ValueError):policy.tag_gate('v1.2.3',changed_commit)
            finally:policy.ROOT=previous

    def test_template_comments_do_not_count_as_evidence(self):
        body=BODY.replace('Python tests passed','<!-- fill later -->')
        with self.assertRaises(ValueError):policy.check_pr('docs/workflow','develop',body)

class HookCommandTests(unittest.TestCase):
    def test_empty_manual_pre_push_is_rejected(self):
        import subprocess
        import sys
        result=subprocess.run([sys.executable,str(policy.ROOT/'scripts/git_policy.py'),'pre-push'],input='',text=True,capture_output=True)
        self.assertNotEqual(0,result.returncode)
        self.assertNotIn('GIT_POLICY_CHECKS_PASSED',result.stdout)

if __name__ == "__main__": unittest.main()
