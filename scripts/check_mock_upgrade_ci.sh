#!/usr/bin/env bash
set -euo pipefail
hoyi_test_cert="$(timeout 60 "$ANDROID_HOME/build-tools/35.0.0/apksigner" verify --print-certs build/mock-upgrade-apks/old.apk | awk '/Signer #1 certificate SHA-256 digest:/ { print $NF }')"
python3 scripts/check_mock_upgrade.py \
  --serial emulator-5554 --sdk "$ANDROID_HOME" \
  --old-apk build/mock-upgrade-apks/old.apk \
  --new-apk build/mock-upgrade-apks/new.apk \
  --test-apk build/mock-upgrade-apks/test.apk \
  --expected-cert-sha256 "$hoyi_test_cert" --source-commit "$GITHUB_SHA" \
  --output build/mock-upgrade
