"""Small GitHub client and append-only version ledger for the Develop publisher.

The dedicated automation ref contains metadata only, never application source or keys.
Every update is a new commit with one parent; force=false provides compare-and-swap.
"""
import base64
import hashlib
import json
import os
from pathlib import Path
import urllib.error
import urllib.parse
import urllib.request

STATE_REF = "heads/automation/apk-publishing"
STATE_BRANCH = "automation/apk-publishing"


class ApiError(RuntimeError):
    def __init__(self, status):
        self.status = status
        super().__init__(f"GitHub API rejected request ({status}); no automatic write retry")


class SafeRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        result = super().redirect_request(req, fp, code, msg, headers, newurl)
        if urllib.parse.urlsplit(newurl).scheme != "https":
            raise ValueError("Non-HTTPS redirect rejected")
        if result is not None and urllib.parse.urlsplit(newurl).netloc != urllib.parse.urlsplit(req.full_url).netloc:
            result.remove_header("Authorization")
        return result


class GitHub:
    def __init__(self, repo, token):
        self.repo = repo
        self.token = token
        self.opener = urllib.request.build_opener(SafeRedirect())

    def request(self, path, method="GET", data=None, binary=False, upload_name=None):
        host = "uploads.github.com" if upload_name else "api.github.com"
        url = f"https://{host}/repos/{self.repo}/{path}"
        if upload_name:
            url += "?name=" + urllib.parse.quote(upload_name, safe="")
        headers = {"Authorization": "Bearer " + self.token,
                   "X-GitHub-Api-Version": "2022-11-28", "User-Agent": "OpenHOYI-Develop-Publisher",
                   "Accept": "application/octet-stream" if binary else "application/vnd.github+json"}
        body = data if upload_name else (json.dumps(data).encode() if data is not None else None)
        if body is not None:
            headers["Content-Type"] = "application/octet-stream" if upload_name else "application/json"
        try:
            with self.opener.open(urllib.request.Request(url, body, headers, method=method), timeout=120) as response:
                raw = response.read()
        except urllib.error.HTTPError as error:
            raise ApiError(error.code) from None
        except (urllib.error.URLError, TimeoutError):
            raise RuntimeError("GitHub response unknown; reconcile server state before retry") from None
        return raw if binary else (json.loads(raw) if raw else None)

    def optional(self, path):
        try:
            return self.request(path)
        except ApiError as error:
            if error.status == 404:
                return None
            raise

    def pages(self, path, key=None):
        page = 1
        while True:
            separator = "&" if "?" in path else "?"
            result = self.request(f"{path}{separator}per_page=100&page={page}")
            values = result[key] if key else result
            if not isinstance(values, list):
                raise ValueError("Invalid paginated GitHub response")
            yield from values
            if len(values) < 100:
                break
            page += 1

    def asset(self, asset):
        if type(asset.get("id")) is not int or asset["id"] <= 0:
            raise ValueError("Invalid release asset")
        data = self.request(f"releases/assets/{asset['id']}", binary=True)
        if len(data) != asset["size"]:
            raise ValueError("Downloaded asset size mismatch")
        digest = asset.get("digest")
        if digest and digest != "sha256:" + hashlib.sha256(data).hexdigest():
            raise ValueError("Downloaded asset digest mismatch")
        return data


def check_protection(api):
    """Inspect actual server settings, not the JSON files in the checkout."""
    tag_ok = state_ok = False
    for summary in api.pages("rulesets?includes_parents=true"):
        rule = api.request(f"rulesets/{summary['id']}")
        if rule.get("enforcement") != "active" or rule.get("bypass_actors"):
            continue
        refs = rule.get("conditions", {}).get("ref_name", {})
        if refs.get("exclude"):
            continue
        types = {entry["type"] for entry in rule.get("rules", [])}
        includes = refs.get("include", [])
        if rule.get("target") == "tag" and "refs/tags/dev-*" in includes:
            tag_ok |= {"update", "deletion"} <= types
        if rule.get("target") == "branch" and "refs/heads/" + STATE_BRANCH in includes:
            state_ok |= {"non_fast_forward", "deletion"} <= types
    if not tag_ok or not state_ok:
        raise ValueError("Develop tags and automation ledger must have active immutable/fast-forward protection")


class Ledger:
    def __init__(self, api, run_id, attempt):
        self.api, self.run_id, self.attempt = api, int(run_id), int(attempt)
        if self.run_id <= 0 or self.attempt <= 0:
            raise ValueError("Invalid workflow run identity")

    def read(self):
        ref = self.api.optional("git/ref/" + STATE_REF)
        if ref is None:
            return None, {"schemaVersion": 1, "allocations": {}, "lease": None}
        sha = ref["object"]["sha"]
        commit = self.api.request("git/commits/" + sha)
        tree = self.api.request("git/trees/" + commit["tree"]["sha"])
        entries = tree["tree"]
        if len(entries) != 1 or entries[0].get("path") != "state.json" or entries[0].get("type") != "blob":
            raise ValueError("Unexpected automation ledger tree")
        blob = self.api.request("git/blobs/" + entries[0]["sha"])
        state = json.loads(base64.b64decode(blob["content"], validate=False))
        if state.get("schemaVersion") != 1 or not isinstance(state.get("allocations"), dict):
            raise ValueError("Invalid automation ledger")
        for number, allocation in state["allocations"].items():
            if str(allocation.get("versionCode")) != number or type(allocation.get("versionCode")) is not int:
                raise ValueError("Invalid reserved version in ledger")
        return sha, state

    def write(self, parent, state):
        tree = self.api.request("git/trees", "POST", {"tree": [
            {"path": "state.json", "mode": "100644", "type": "blob", "content": json.dumps(state, sort_keys=True)}]})
        commit = self.api.request("git/commits", "POST", {
            "message": f"Develop APK ledger: run {self.run_id}/{self.attempt}",
            "tree": tree["sha"], "parents": [parent] if parent else []})
        if parent:
            self.api.request("git/refs/" + STATE_REF, "PATCH", {"sha": commit["sha"], "force": False})
        else:
            self.api.request("git/refs", "POST", {"ref": "refs/" + STATE_REF, "sha": commit["sha"]})

    def owns(self, state):
        return state.get("lease") == {"runId": self.run_id, "attempt": self.attempt}

    def acquire(self):
        parent, state = self.read()
        lease = state.get("lease")
        if lease and not self.owns(state):
            previous = self.api.request(f"actions/runs/{lease['runId']}")
            rerun = lease["runId"] == self.run_id and lease["attempt"] < self.attempt
            if not rerun and previous.get("status") != "completed":
                raise ValueError("Another publisher still holds the global lease; do not steal it")
        state["lease"] = {"runId": self.run_id, "attempt": self.attempt}
        self.write(parent, state)
        return state

    def update(self, change):
        parent, state = self.read()
        if not self.owns(state):
            raise ValueError("Publication lease lost")
        change(state)
        self.write(parent, state)
        return state

    def release(self):
        parent, state = self.read()
        lease = state.get("lease")
        if not lease:
            return
        completed = False
        if not self.owns(state):
            previous = self.api.request(f"actions/runs/{lease['runId']}")
            completed = previous.get("status") == "completed"
        # Also permits safe recovery while the entry point is paused. Never
        # clear another active run's lease merely because a timeout elapsed.
        if self.owns(state) or completed:
            state["lease"] = None
            self.write(parent, state)


def sha256(data):
    return hashlib.sha256(data).hexdigest()


def output(name, value):
    path = os.environ.get("GITHUB_OUTPUT")
    if path:
        with Path(path).open("a", encoding="utf-8") as stream:
            stream.write(f"{name}={json.dumps(value, separators=(',', ':'))}\n")
