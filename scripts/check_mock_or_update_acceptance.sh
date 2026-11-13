#!/bin/sh
# android-emulator-runner executes each script line separately; keep its entry one fixed command.
set -eu
if [ "${PREHEAT_CHECKS:-false}" = true ]; then
  [ "${UPDATE_CHECKS:-false}" = false ] || { echo 'Choose only one isolated suite' >&2; exit 2; }
  exec bash scripts/check_studio_preheat.sh
fi
case "${UPDATE_CHECKS:-false}" in
  true)
    exec python3 scripts/check_update_acceptance.py --sdk "${ANDROID_HOME:?ANDROID_HOME is required}"
    ;;
  false)
    exec bash scripts/check_mock_lifecycle.sh
    ;;
  *)
    printf '%s\n' 'UPDATE_CHECKS must be true or false' >&2
    exit 2
    ;;
esac
