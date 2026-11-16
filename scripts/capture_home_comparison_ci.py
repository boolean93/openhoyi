"""Identical capture harness for the exact PR119 parent and merge commits."""
import os
import subprocess
import capture_home_viewports_ci as capture

if __name__ == "__main__":
    # APK is built from this checked-out tree, not the workflow tooling commit.
    os.environ["GITHUB_SHA"] = subprocess.check_output(
        ["git", "rev-parse", "HEAD"], text=True).strip()
    capture.CASES = (
        ("landscape-3x2-medium", 1800, 1200, "medium"),
        ("landscape-16x9-medium", 1920, 1080, "medium"),
    )
    capture.main()
