#!/usr/bin/env python3
"""Offline schema/ownership counterexamples; this does not execute Android callbacks."""

from copy import deepcopy
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PROBE = ROOT / "mobile/src/androidTest/kotlin/io/openhoyi/mobile/ImeViewportTransitionProbe.kt"
EDITOR = ROOT / "mobile/src/androidTest/kotlin/io/openhoyi/mobile/AdvancedBusinessUiChecks.kt"
IMPORT = ROOT / "mobile/src/androidTest/kotlin/io/openhoyi/mobile/CurveVisualMigrationChecks.kt"


def event(seq, ordinal, *, ime=True, scroll=4885, pid=7, field=101, token=201):
    return {"seq": seq, "pid": pid, "elapsedRealtimeNanos": seq * 100,
            "eventSource": "pre-draw", "actual": {"activityOrdinal": ordinal,
            "fieldIdentity": field, "fieldTokenIdentity": token,
            "imeVisible": ime, "rawScrollY": scroll, "scrolls": [],
            "productRestoreStatus": None}}


def validate(proof):
    assert proof["requestTagRelationship"] == "unknown_not_inferred_from_time_proximity"
    assert proof["imeTokenRelationship"] == "unknown_distinct_from_app_window_token"
    assert proof["maximumEvents"] == 256 and proof["callbackEventLimit"] < 256
    assert proof["listenerInstallCount"] == proof["listenerRemoveCount"]
    assert proof["droppedCount"] == 0 or proof["truncatedAtElapsedRealtimeNanos"] is not None
    assert len(proof["events"]) <= 256
    assert proof["issues"] == []
    seqs = [e["seq"] for e in proof["events"]]
    assert seqs == sorted(set(seqs))
    assert len({e["pid"] for e in proof["events"]}) == 1
    assert [e["actual"]["activityOrdinal"] for e in proof["events"]] == [1, 1, 2, 2]
    assert proof["oldNewIdentity"]["sameField"] is False
    assert proof["oldNewIdentity"]["sameAppWindowToken"] is None
    assert proof["oldNewIdentity"]["tokenComparisonStatus"] == "unknown_unattached"
    required = {"fieldIdentity", "fieldTokenIdentity", "imeVisible", "rawScrollY", "scrolls",
                "productRestoreStatus"}
    for entry in proof["events"]:
        assert required <= entry["actual"].keys()
        assert isinstance(entry["actual"]["imeVisible"], (bool, type(None)))
        assert isinstance(entry["actual"]["rawScrollY"], (int, type(None)))
    # Both transitions must remain observable, not a scroll-only or IME-only summary.
    new = [e["actual"] for e in proof["events"] if e["actual"]["activityOrdinal"] == 2]
    assert new[0]["imeVisible"] is True and new[-1]["imeVisible"] is False
    assert new[0]["rawScrollY"] == 4885 and new[-1]["rawScrollY"] == 4281


BASE = {"requestTagRelationship": "unknown_not_inferred_from_time_proximity",
        "imeTokenRelationship": "unknown_distinct_from_app_window_token",
        "maximumEvents": 256, "callbackEventLimit": 248,
        "listenerInstallCount": 18, "listenerRemoveCount": 18,
        "droppedCount": 0, "truncatedAtElapsedRealtimeNanos": None,
        "issues": [], "oldNewIdentity": {"sameField": False, "sameAppWindowToken": None,
                                      "tokenComparisonStatus": "unknown_unattached"},
        "events": [event(1, 1, field=101, token=None),
                   event(2, 1, field=101, token=None),
                   event(3, 2, field=202, token=None),
                   event(4, 2, ime=False, scroll=4281, field=202, token=None)]}


def main():
    source = PROBE.read_text()
    editor = EDITOR.read_text()
    imported = IMPORT.read_text()
    forbidden = ("setOnApplyWindowInsetsListener", "setWindowInsetsAnimationCallback",
                 "setOnFocusChangeListener", "showSoftInput(", "hideSoftInputFromWindow(",
                 "requestFocus(", "clearFocus(", "setSelection(", "scrollTo(",
                 "requestLayout(", "requestApplyInsets(", "SystemClock.sleep(")
    for api in forbidden:
        assert api not in source, api
    for installed, removed in (("addOnGlobalFocusChangeListener", "removeOnGlobalFocusChangeListener"),
                               ("addOnWindowFocusChangeListener", "removeOnWindowFocusChangeListener"),
                               ("addOnGlobalLayoutListener", "removeOnGlobalLayoutListener"),
                               ("addOnScrollChangedListener", "removeOnScrollChangedListener"),
                               ("addOnPreDrawListener", "removeOnPreDrawListener"),
                               ("addOnAttachStateChangeListener", "removeOnAttachStateChangeListener")):
        assert installed in source and removed in source
    assert "true }" in source and "maximumEvents=256" in source
    assert "callbackEventLimit=248" in source and "ViewGroup.MarginLayoutParams" in source
    assert "ime-viewport-transitions.json" in editor and "ime-viewport-transitions.json" in imported
    assert "transitionProbe?.sample(source,frameIndex,force=false)" in editor
    assert 'transitionProbe?.sample("stable-frame-$phase",samples.length()+1,force=false)' in imported
    assert 'check(after.toString()==before.toString())' in imported
    assert 'check(equal) { "M2 form recreation changed draft/focus/selection/identity/scroll: $label" }' in editor
    # SDK34 can dispatch onActivityCreated from super.onCreate before product fields exist.
    # The exact named resolver may bind replacement refs only after onCreate returns.
    for owner_source in (editor, imported):
        lifecycle = owner_source[owner_source.index('val lifecycle=object:Application.ActivityLifecycleCallbacks'):]
        created = lifecycle.split('override fun onActivityCreated', 1)[1].split('override fun onActivityPostCreated', 1)[0]
        post_created = lifecycle.split('override fun onActivityPostCreated', 1)[1].split('override fun onActivityStarted', 1)[0]
        assert 'transitionProbe' not in created
        assert 'transitionProbe' in post_created and 'install(owner,"replacement-post-created")' in post_created
        assert 'fun preserveFinalization(error:Throwable,' in owner_source
    lifecycle_order = [('created', False), ('post-created', True)]
    def first_binding(install_phase):
        # A failed resolver still consumes the owner ordinal; duplicate install cannot repair it.
        for phase, product_fields_ready in lifecycle_order:
            if phase == install_phase:
                return 'exact' if product_fields_ready else 'unknown_permanently_bound'
        raise AssertionError('missing installation phase')
    assert first_binding('created') == 'unknown_permanently_bound'
    assert first_binding('post-created') == 'exact'
    validate(BASE)
    cases = [
        lambda p: p["events"][2]["actual"].update(fieldIdentity=101),  # old/new field reused
        lambda p: p["oldNewIdentity"].update(sameField=True),
        lambda p: p["oldNewIdentity"].update(sameAppWindowToken=True),  # null token is unknown
        lambda p: p["events"][3].update(pid=8),
        lambda p: p["events"][3].update(seq=3),
        lambda p: p.update(droppedCount=1),  # truncation timestamp missing
        lambda p: p.update(events=p["events"] * 65),  # cap exceeded
        lambda p: p.update(listenerRemoveCount=17),
        lambda p: p.update(issues=["duplicate_activity_install"]),
        lambda p: p["events"][3]["actual"].pop("imeVisible"),
        lambda p: p["events"][3]["actual"].update(imeVisible=True),
        lambda p: p["events"][3]["actual"].update(imeVisible=None),  # unknown cannot masquerade as false
        lambda p: p["events"][3]["actual"].update(rawScrollY=4885),
        lambda p: p.update(requestTagRelationship="same_request_by_nearby_time"),
    ]
    # Field identity must remain fixed across all samples for each activity.
    def stable_fields(proof):
        for ordinal in (1, 2):
            ids = {e["actual"]["fieldIdentity"] for e in proof["events"]
                   if e["actual"]["activityOrdinal"] == ordinal}
            assert len(ids) == 1
    stable_fields(BASE)
    for index, mutate in enumerate(cases):
        candidate = deepcopy(BASE)
        mutate(candidate)
        try:
            validate(candidate)
            stable_fields(candidate)
        except (AssertionError, KeyError, TypeError):
            continue
        raise AssertionError(f"counterexample {index} unexpectedly passed")
    print(f"imeViewportProbeOffline=true counterexamples={len(cases)} lifecycleCounterexamples=1 androidExecution=false")


if __name__ == "__main__":
    main()
