"""Static regressions for the fast PR gate, AVD split and release wake-up."""
import importlib.util
import pathlib
import unittest

SPEC = importlib.util.spec_from_file_location(
    "ci_workflow_policy", pathlib.Path(__file__).with_name("ci_workflow_policy.py"))
policy = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(policy)


class WorkflowPolicyTests(unittest.TestCase):
    def test_no_pull_request_triggered_avd_workflow(self):
        self.assertEqual(policy.validate_pr_avd(), [])

    def test_required_contexts_are_only_branch_policy_and_verify(self):
        self.assertEqual(policy.validate_required_contexts(), [])

    def test_verify_is_stable_and_keeps_full_guards(self):
        self.assertEqual(policy.validate_verify_shape(), [])

    def test_develop_prerelease_gate_keeps_four_push_workflows(self):
        self.assertEqual(policy.validate_release_gate(), [])

    def test_no_blanket_continue_on_error(self):
        self.assertEqual(policy.validate_no_continue_on_error(), [])

    def test_expected_avd_workflows_exist(self):
        workflows = {path.name for path in policy.workflow_files()}
        self.assertIn("mock-lifecycle.yml", workflows)
        self.assertIn("mock-upgrade.yml", workflows)
        self.assertIn("single-screen-ui.yml", workflows)
        self.assertIn("settings-visual-ui.yml", workflows)


if __name__ == "__main__":
    unittest.main()
