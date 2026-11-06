"""Static, dependency-free checks for the PR/AVD CI split.

This module reads the checked-in workflow and ruleset files only. It does not
call GitHub and cannot prove that the remote ruleset is active; that still
requires an API read-back after any remote change.
"""
from __future__ import annotations

import importlib.util
import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
WORKFLOWS = ROOT / ".github" / "workflows"
INTEGRATION = ROOT / ".github" / "rulesets" / "integration.json"
AVD_MARKER = "android-emulator-runner"
POST_MERGE_AVD = {"mock-lifecycle.yml", "mock-upgrade.yml"}


def workflow_files() -> list[Path]:
    return sorted(WORKFLOWS.glob("*.yml"))


def read_workflow(name: str) -> str:
    return (WORKFLOWS / name).read_text(encoding="utf-8")


def trigger_block(text: str) -> str:
    lines = text.splitlines()
    for index, line in enumerate(lines):
        if not re.match(r"^on:", line):
            continue
        if line.strip() != "on:":
            return line
        block = []
        for following in lines[index + 1:]:
            if not following.strip() or following[0] in " \t":
                block.append(following)
                continue
            break
        return "\n".join(block)
    raise ValueError("workflow has no top-level on: block")


def has_trigger(text: str, trigger: str) -> bool:
    return re.search(r"(?m)^  " + re.escape(trigger) + r"\s*:", trigger_block(text)) is not None


def trigger_branches(text: str, trigger: str) -> list[str]:
    lines = trigger_block(text).splitlines()
    start = None
    for index, line in enumerate(lines):
        if re.match(r"^  " + re.escape(trigger) + r"\s*:", line):
            start = index
            break
    if start is None:
        return []
    for line in lines[start + 1:]:
        if line and not line.startswith(" "):
            break
        if not line.startswith("    "):
            continue
        match = re.match(r"^\s+branches:\s*\[(.*)\]\s*$", line)
        if match:
            return [item.strip().strip("'\"") for item in match.group(1).split(",") if item.strip()]
    return []


def jobs(text: str) -> dict[str, str]:
    lines = text.splitlines()
    jobs_index = next((index for index, line in enumerate(lines) if line.rstrip() == "jobs:"), None)
    if jobs_index is None:
        return {}
    result = {}
    index = jobs_index + 1
    while index < len(lines):
        match = re.match(r"^  ([A-Za-z0-9_.-]+):\s*$", lines[index])
        if not match:
            index += 1
            continue
        job_id = match.group(1)
        end = index + 1
        while end < len(lines):
            if re.match(r"^  [A-Za-z0-9_.-]+:\s*$", lines[end]):
                break
            if lines[end] and not lines[end].startswith(" "):
                break
            end += 1
        block = "\n".join(lines[index:end])
        name = re.search(r"(?m)^    name:\s*(.+?)\s*$", block)
        result[job_id] = name.group(1) if name else job_id
        index = end
    return result


def _required_workflows() -> dict[str, str]:
    spec = importlib.util.spec_from_file_location(
        "develop_release_policy", ROOT / "scripts" / "develop_release_policy.py")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return dict(module.REQUIRED_WORKFLOWS)


def validate_pr_avd() -> list[str]:
    issues = []
    for path in workflow_files():
        text = path.read_text(encoding="utf-8")
        if AVD_MARKER not in text:
            continue
        name = path.name
        if has_trigger(text, "pull_request"):
            issues.append(name + " is AVD-based but still runs on pull_request")
        if name in POST_MERGE_AVD:
            if not has_trigger(text, "push"):
                issues.append(name + " lost its post-merge push trigger")
            if not has_trigger(text, "workflow_dispatch"):
                issues.append(name + " lost its manual trigger")
        elif not has_trigger(text, "workflow_dispatch"):
            issues.append(name + " is AVD-based and has no manual trigger")
    return issues


def validate_required_contexts() -> list[str]:
    data = json.loads(INTEGRATION.read_text(encoding="utf-8"))
    issues = []
    if data.get("name") != "OpenHOYI integration branches":
        issues.append("integration ruleset name changed")
    if data.get("target") != "branch" or data.get("enforcement") != "active":
        issues.append("integration ruleset must stay an active branch ruleset")
    if data.get("bypass_actors") != []:
        issues.append("integration ruleset must not gain bypass actors")
    conditions = data.get("conditions", {}).get("ref_name", {})
    if sorted(conditions.get("include", [])) != ["refs/heads/develop", "refs/heads/main"]:
        issues.append("integration ruleset must still cover main and develop")
    rules = {rule.get("type"): rule for rule in data.get("rules", [])}
    for required in ("deletion", "non_fast_forward", "pull_request", "required_status_checks"):
        if required not in rules:
            issues.append("integration ruleset is missing " + required)
    checks = rules.get("required_status_checks", {}).get("parameters", {})
    contexts = [item.get("context") for item in checks.get("required_status_checks", [])]
    if contexts != ["branch-policy", "verify"]:
        issues.append("required PR contexts must be exactly branch-policy and verify")
    if checks.get("strict_required_status_checks_policy") is not True:
        issues.append("strict required status checks must stay enabled")
    if checks.get("do_not_enforce_on_create") is not False:
        issues.append("do_not_enforce_on_create must stay false")
    review = rules.get("pull_request", {}).get("parameters", {})
    expected_review = {
        "dismiss_stale_reviews_on_push": True,
        "require_code_owner_review": False,
        "require_last_push_approval": False,
        "required_approving_review_count": 0,
        "required_review_thread_resolution": True,
    }
    for key, value in expected_review.items():
        if review.get(key) != value:
            issues.append("pull_request rule changed: " + key)
    return issues


def validate_verify_shape() -> list[str]:
    text = read_workflow("verify.yml")
    issues = []
    if not has_trigger(text, "pull_request"):
        issues.append("verify.yml must run on pull_request")
    if "paths:" in trigger_block(text) or "paths-ignore:" in trigger_block(text):
        issues.append("verify.yml must not use workflow-level paths filters")
    if jobs(text).get("verify") != "verify":
        issues.append("verify job context must remain exactly verify")
    for command in ("scripts/pr_verify_scope.py", "test_git_policy.py",
                    "localization/validate_catalog.py", "./gradlew",
                    "localization/verify_apk_resources.py",
                    "scripts/check_release_configuration.py"):
        if command not in text:
            issues.append("verify.yml lost required command: " + command)
    if "if: steps.scope.outputs.mode == 'full'" not in text:
        issues.append("verify.yml must gate the complete path on the classifier")
    branches = trigger_branches(text, "push")
    for branch in ("main", "develop", "release/**", "hotfix/**"):
        if branch not in branches:
            issues.append("verify push trigger lost " + branch)
    return issues


def validate_release_gate() -> list[str]:
    required = _required_workflows()
    expected = {
        "branch-policy.yml": "branch-policy",
        "verify.yml": "verify",
        "mock-lifecycle.yml": "lifecycle",
        "mock-upgrade.yml": "upgrade",
    }
    issues = []
    if required != expected:
        issues.append("Develop release required workflow mapping changed")
    for filename, job_name in expected.items():
        text = read_workflow(filename)
        if job_name not in jobs(text).values():
            issues.append(filename + " lost exact job name " + job_name)
    text = read_workflow("develop-apk.yml")
    trigger = trigger_block(text)
    if "workflow_run:" not in trigger:
        issues.append("develop-apk.yml lost its workflow_run wake-up")
    for filename, job_name in expected.items():
        display = read_workflow(filename)
        name_match = re.search(r"(?m)^name:\s*(.+?)\s*$", display)
        workflow_name = name_match.group(1) if name_match else filename
        if workflow_name not in trigger:
            issues.append("develop-apk.yml no longer waits for " + workflow_name)
    if "develop" not in trigger_branches(text, "push"):
        issues.append("develop-apk.yml no longer listens to develop pushes")
    for command in ("scripts/develop_release.py plan",
                    "scripts/develop_release.py publish",
                    "scripts/develop_release.py release-lease"):
        if command not in text:
            issues.append("develop-apk.yml lost " + command)
    return issues


def validate_no_continue_on_error() -> list[str]:
    issues = []
    for path in workflow_files():
        if "continue-on-error" in path.read_text(encoding="utf-8"):
            issues.append(path.name + " uses continue-on-error")
    return issues


def validate() -> list[str]:
    return (validate_pr_avd() + validate_required_contexts() + validate_verify_shape()
            + validate_release_gate() + validate_no_continue_on_error())


if __name__ == "__main__":
    problems = validate()
    if problems:
        raise SystemExit("\n".join(problems))
    print("CI_WORKFLOW_POLICY_PASSED")
