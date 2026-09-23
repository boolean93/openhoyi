#!/bin/sh
# Instrumentation restarts the target process. Never run it while Lab owns a BLE service.
set -eu
serial=${1:?Usage: scripts/run_lab_smoke.sh DEVICE_SERIAL}
service_state=$(adb -s "$serial" shell dumpsys activity services io.openhoyi.lab)
if printf '%s\n' "$service_state" | grep -q 'ServiceRecord{'; then
    printf '%s\n' 'OpenHOYI Lab service exists. Stop the diagnostic service and close its page before running instrumentation.' >&2
    exit 2
fi
adb -s "$serial" shell am instrument -w io.openhoyi.lab.test/io.openhoyi.lab.LabSmokeInstrumentation
