"""Reduction of attached Extraction chart draws; no emulator control here."""
from collections import defaultdict
import math


def evaluate(evidence):
    result = {"status": "INCONCLUSIVE"}
    if evidence.get("complete") is not True or evidence.get("synthetic") is not True or evidence.get("nativeBle") is not False:
        return {**result, "reason": "incomplete or non-Mock fixture"}
    inputs = evidence["inputs"]
    draws = evidence["draws"]
    schedule = list(range(200, 2801, 200)) + [4600, 4800, 5000]
    if [row["elapsedMs"] for row in inputs] != schedule or any(row["actualNewPoints"] != 1 for row in inputs):
        return {**result, "reason": "typed inputs were dropped or changed before chart"}
    lags = [row["offeredAtRealMs"] - row["plannedElapsedRealtimeMs"] for row in inputs]
    intervals = [b["offeredAtRealMs"] - a["offeredAtRealMs"] for a, b in zip(inputs, inputs[1:])]
    result.update(inputLagsMs=lags, inputIntervalsMs=intervals, inputCount=len(inputs), drawCount=len(draws))
    if any(not 0 <= lag <= 45 for lag in lags) or not draws:
        return {**result, "reason": "host did not sustain a representative 200 ms stimulus"}
    frame_times = [row["elapsedNanos"] for row in draws]
    if frame_times != sorted(set(frame_times)):
        return {**result, "reason": "draw timestamps not strictly increasing"}
    by_target = defaultdict(set)
    changed_intermediate = set()
    max_reveal = None
    wrong_axes = False
    bad_labels = False
    backwards = False
    gap_draws = 0
    bridged_gap = False
    # Product Extraction aligns the displayed time to the first open-valve observation.
    origin = schedule[0]
    target_pressure = {row["elapsedMs"] - origin: row["pressureTenthsBar"] for row in inputs}
    last_pre_gap, first_post_gap = 2800 - origin, 4600 - origin
    first_geometry = draws[0]["boundsPx"]
    for frame in draws:
        t, reveal = frame["observedMs"], frame["revealMs"]
        left, top, right, bottom = frame["boundsPx"]
        width = right - left
        if width <= 0 or bottom <= top or frame["timeEndMs"] != 40000 or any(
            not math.isclose(a, b, abs_tol=0.25) for a, b in zip(frame["boundsPx"], first_geometry)
        ):
            wrong_axes = True
            continue
        if t not in target_pressure or frame["targetPressureTenthsBar"] != target_pressure[t] or not 0 <= reveal <= t:
            return {**result, "reason": "draw not matched to a typed input and owned clock"}
        if max_reveal is not None and reveal < max_reveal:
            backwards = True
        max_reveal = reveal
        # The actual drawn pressure path consumes the current measured vertices;
        # the interpolated Canvas clip and label head move over them.
        ax, ay, caption = (frame["pressureAnchorX"], frame["pressureAnchorY"], frame["pressureCaption"])
        if caption is not None:
            expected_x = left + width * reveal / 40000
            if ax is None or ay is None or not math.isclose(ax, expected_x, abs_tol=0.75) or caption != f"{target_pressure[t]/10:.1f} bar":
                bad_labels = True
            expected_y = bottom - (bottom - top) * (target_pressure[t] / 120)
            if (t <= 1200 - origin or reveal == t) and (
                ay is None or not math.isclose(ay, expected_y, abs_tol=0.75)
            ):
                bad_labels = True
            if 1400 - origin <= t <= last_pre_gap and t - 200 < reveal < t and ay is not None:
                prior_y = bottom - (bottom - top) * (target_pressure[t - 200] / 120)
                if expected_y + 0.3 < ay < prior_y - 0.3:
                    changed_intermediate.add(t)
        paths = frame["pressurePaths"]
        if caption is not None and len(paths) and paths[-1]["interpolation"] == "monotone-hermite":
            by_target[t].add(reveal)
        if t >= first_post_gap:
            gap_draws += 1
            time_units = [[(x - left) / width * 40000 for x, _ in path["verticesPx"]] for path in paths]
            before = any(path and max(path) <= last_pre_gap + 1 for path in time_units)
            after = any(path and min(path) >= first_post_gap - 1 for path in time_units)
            flow_units = [[(x - left) / width * 40000 for x, _ in path["verticesPx"]] for path in frame["flowPaths"]]
            bridge = any(min(path) < last_pre_gap + 1 and max(path) > first_post_gap - 1
                         for path in time_units + flow_units if path)
            if not before or not after or bridge:
                bridged_gap = True
    # Each cluster uses two distinct attached draws strictly between adjacent real observations.
    chart_times = [ms - origin for ms in schedule]
    chains = list(zip(chart_times, chart_times[1:]))
    stable = [b for a, b in chains if b <= 1200 - origin and sum(a < r < b for r in by_target[b]) >= 2]
    changing = [b for a, b in chains if 1400 - origin <= b <= last_pre_gap and sum(a < r < b for r in by_target[b]) >= 2
                and b in changed_intermediate]
    failures = []
    if wrong_axes:
        failures.append("fixed-axis geometry changed")
    if bad_labels:
        failures.append("actual pressure annotation separated from its reveal position or newest measured caption")
    if backwards:
        failures.append("reveal moved backwards")
    if len(stable) < 2 or len(changing) < 2:
        failures.append("insufficient real intermediate draws between adjacent inputs in stable or changing runs")
    if gap_draws == 0 or bridged_gap:
        failures.append("measured pressure segments did not remain separated across the missing interval")
    result.update(status="FAIL" if failures else "PASS", failures=failures, distinctObservedTargets=sorted({r["observedMs"] for r in draws}),
                  stableAnimatingTargetsMs=stable, changingAnimatingTargetsMs=changing, gapDraws=gap_draws)
    return result
