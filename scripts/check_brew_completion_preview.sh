#!/usr/bin/env bash
set -Eeuo pipefail
[[ "${GITHUB_ACTIONS:-}" == true && "${GITHUB_EVENT_NAME:-}" == workflow_dispatch ]] || {
  echo 'Disposable manually dispatched GitHub CI required' >&2; exit 1;
}
sha="$(git rev-parse HEAD)"
[[ "$sha" == "${GITHUB_SHA:-}" ]] || { echo 'Checkout differs from dispatched SHA' >&2; exit 1; }
mapfile -t devices < <(adb devices | awk 'NR>1 && NF>=2 {print $1 " " $2}')
[[ ${#devices[@]} == 1 && "${devices[0]}" =~ ^emulator-[0-9]+\ device$ ]] || {
  echo 'Exactly one local CI emulator must be running' >&2; exit 1;
}
serial="${devices[0]%% *}"
ADB=(adb -s "$serial")
[[ "$("${ADB[@]}" shell getprop ro.kernel.qemu | tr -d '\r')" == 1 ]] || {
  echo 'Disposable emulator required' >&2; exit 1;
}
app=io.openhoyi.mobile.mock
apk=mobile/build/outputs/apk/mock/mobile-mock.apk
preview="$app/io.openhoyi.mobile.BrewCompletionPreviewActivity"
aapt="${ANDROID_HOME:?}/build-tools/35.0.0/aapt"
installed="$("${ADB[@]}" shell pm list packages -u | tr -d '\r')"
if grep -Eq '^package:io\.openhoyi\.mobile(\.|$)' <<<"$installed"; then
  echo 'Existing OpenHOYI package or retained state, refusing install' >&2; exit 1
fi
[[ "$("$aapt" dump badging "$apk" | sed -n "s/^package: name='\([^']*\)'.*/\1/p")" == "$app" ]] || {
  echo 'Only the isolated Mock shall be installed' >&2; exit 1;
}
"$aapt" dump xmltree "$apk" AndroidManifest.xml | grep -F 'io.openhoyi.mobile.BrewCompletionPreviewActivity' > /dev/null
if "$aapt" dump permissions "$apk" | grep -E 'android\.permission\.BLUETOOTH' > /dev/null; then
  echo 'Mock APK must not ask for hardware Bluetooth' >&2; exit 1
fi

out=build/task-frame-ui/brew-completion
mkdir -p "$out"
{
  printf 'sourceCommit=%s\nappPackage=%s\nfixture=%s\nserial=%s\n' "$sha" "$app" "$preview" "$serial"
  sha256sum "$apk"
} > "$out/apk-identity.txt"
trap '"${ADB[@]}" logcat -d -s AndroidRuntime:E > "$out/logcat.txt" || true' EXIT
"${ADB[@]}" install "$apk"
"${ADB[@]}" shell settings put system accelerometer_rotation 0
"${ADB[@]}" shell settings put system user_rotation 0
"${ADB[@]}" shell settings put system font_scale 1.0
"${ADB[@]}" shell cmd uimode night no
"${ADB[@]}" shell input keyevent KEYCODE_WAKEUP
"${ADB[@]}" shell wm dismiss-keyguard

check_png() {
  python3 - "$@" <<'PYPNG'
import struct
import sys
from pathlib import Path

file, w, h = sys.argv[1:]
data = Path(file).read_bytes()
assert data.startswith(b"\x89PNG\r\n\x1a\n") and data[12:16] == b"IHDR"
assert struct.unpack(">II", data[16:24]) == (int(w), int(h)), "Unexpected PNG dimensions"
assert data[-12:] == b"\x00\x00\x00\x00IEND\xaeB\x60\x82", "Incomplete PNG"
PYPNG
}

capture() {
  local name="$1" show="$2" long="$3" width="$4" height="$5" pace="$6"
  "${ADB[@]}" shell am force-stop "$app"
  "${ADB[@]}" shell am start -W -n "$preview" --ez showReview "$show" --ez longCurve "$long" --ez exportFixture true --es flowPace "$pace" > "$out/$name-launch.txt"
  grep -q '^Status: ok' "$out/$name-launch.txt"
  for attempt in {1..5}; do
    "${ADB[@]}" shell uiautomator dump /sdcard/brew-completion-preview.xml > /dev/null
    "${ADB[@]}" exec-out cat /sdcard/brew-completion-preview.xml > "$out/$name.xml"
    if python3 - "$out/$name.xml" "$app" "$show" "$pace" <<'PY'
import re
import sys
import xml.etree.ElementTree as ET

path, package, show, pace = sys.argv[1:]
nodes = [n.attrib for n in ET.parse(path).iter("node") if n.get("package") == package]
def labeled(n, label):
    return n.get("content-desc") == label or (n.get("text") or "").replace("\ufffc", "").strip() == label
action = lambda label: [n for n in nodes if labeled(n, label) and
                        n.get("clickable") == "true" and n.get("enabled") == "true"]
close = [n for n in nodes if n.get("content-desc") == "关闭" and n.get("clickable") == "true"]
review_visible = [n for n in nodes if labeled(n, "AI 评价")]
assert len(close) == 1 and len(action("分享")) == 1, "Expected close and Share"
assert len(review_visible) == (show == "true") and len(action("AI 评价")) == (show == "true"), "Wrong AI review action state"
assert all(n.get("text") != "本次萃取" for n in nodes), "Duplicate external title"
pace_labels = {"slow": "流速 · 偏慢", "balanced": "流速 · 适中", "fast": "流速 · 偏快"}
shown_pace = [n for n in nodes if any(labeled(n, label) for label in pace_labels.values())]
assert len(shown_pace) == (pace != "none"), "Flow indicator count or omission is wrong"
if shown_pace:
    assert labeled(shown_pace[0], pace_labels[pace]), "Wrong flow indicator label"
photo = [n for n in nodes if n.get("class") == "android.widget.ImageView"
         and (n.get("content-desc") or "").startswith("本次萃取")]
assert len(photo) == 1, "A single accessible share image is expected"
def bounds(n):
    return [int(x) for x in re.findall(r"-?\d+", n["bounds"])]
picture = bounds(photo[0])
assert abs((picture[2] - picture[0]) * 1.25 - (picture[3] - picture[1])) <= 4, "Image view is not fitted to the full 4:5 card"
share = bounds(action("分享")[0])
leading = bounds(action("AI 评价")[0]) if show == "true" else share
assert abs(leading[0] - picture[0]) <= 4 and abs(share[2] - picture[2]) <= 4, "Image and outer action edges differ"
assert abs(leading[1] - share[1]) <= 4 and abs(leading[3] - share[3]) <= 4, "Unequal action heights"
assert picture[3] <= share[1], "Actions overlap the image"
PY
    then
      "${ADB[@]}" exec-out screencap -p > "$out/$name.png.tmp"
      check_png "$out/$name.png.tmp" "$width" "$height"
      mv "$out/$name.png.tmp" "$out/$name.png"
      # Explicit preview hook writes the very bitmap drawn by the production dialog.
      # This is not a chooser or FileProvider test.
      "${ADB[@]}" exec-out run-as "$app" cat cache/completion-card-fixture.png > "$out/$name-card.png.tmp"
      check_png "$out/$name-card.png.tmp" 1120 1400
      mv "$out/$name-card.png.tmp" "$out/$name-card.png"
      printf 'COMPLETION_PREVIEW_PASSED %s showReview=%s longCurve=%s %sx%s\n' "$name" "$show" "$long" "$width" "$height"
      return
    fi
    sleep 1
  done
  echo "Popup controls not visible in $name" >&2
  exit 1
}
"${ADB[@]}" shell wm size 1080x2400
"${ADB[@]}" shell wm density 420
capture phone-no-ai false false 1080 2400 slow
capture phone-with-ai true false 1080 2400 balanced
"${ADB[@]}" shell cmd uimode night yes
capture phone-dark-with-ai true false 1080 2400 fast
"${ADB[@]}" shell cmd uimode night no
"${ADB[@]}" shell wm size 2560x1600
"${ADB[@]}" shell wm density 360
capture tablet-long-no-ai false true 2560 1600 none
cmp "$out/phone-no-ai-card.png" "$out/phone-with-ai-card.png"
cmp "$out/phone-dark-with-ai-card.png" "$out/phone-with-ai-card.png"
if cmp -s "$out/phone-no-ai-card.png" "$out/tablet-long-no-ai-card.png"; then
  echo 'Different synthetic fixtures unexpectedly produced the same image' >&2; exit 1
fi
(cd "$out" && sha256sum ./*.png > png-sha256.txt)
