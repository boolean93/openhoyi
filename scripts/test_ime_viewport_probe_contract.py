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
    # Resolver diagnostics must be captured by the same install, with explicit
    # not-evaluated values rather than turning a failed first check into count 0.
    assert 'resolutionAttempts' in source
    assert 'ResolutionAttempt' in source and 'resolve(owner,attempt)' in source
    assert 'resolve_${phase}_${error.javaClass.name}' in source
    assert 'unknown_named_reference_resolution' in source
    assert 'resolutionFailureStack' in source and 'diagnosticFailure' in source
    assert 'fun viewReference(name:String, view:View?)' in source
    assert 'reference("${name}Parent",view?.parent)' in source
    for owner_source, names in ((imported, ('input', 'pageRoot', 'formScroll', 'parentHost')),
                                (editor, ('pageRoot', 'target', 'host'))):
        for name in names:
            assert f'attempt.viewReference("{name}",' in owner_source
    assert 'val rawCount=nodes(pageRoot).count { it===raw }' in imported
    assert 'val middleCount=if(rawCount==1)' in imported
    assert 'val hostCount=if(host!=null)' in imported
    assert 'val targetCount=if(target!=null)' in editor
    assert 'val hostCount=views(pageRoot).count { it===host }' in editor
    for owner_source, ordered in ((imported, ('input-read', 'page-root-read', 'form-scroll-read',
                                           'raw-count', 'middle-count', 'first-check',
                                           'parent-host-read', 'host-count', 'second-check', 'targets')),
                                  (editor, ('page-root-read', 'field-map-read', 'target-count',
                                            'first-check', 'form-reflection', 'host-reflection',
                                            'host-count', 'second-check', 'targets'))):
        positions=[owner_source.index(f'attempt.stage("{stage}")') for stage in ordered]
        assert positions==sorted(positions)
    assert imported.count('val rawCount=nodes(pageRoot).count { it===raw }') == 1
    assert imported.count('val middleCount=if(rawCount==1) nodes(pageRoot).count { it===middle } else null') == 1
    assert editor.count('val targetCount=if(target!=null) views(pageRoot).count { it===target } else null') == 1
    assert imported.index('attempt.value("rawCount",null)') < imported.index('attempt.stage("input-read")')
    assert editor.index('attempt.value("targetCount",null)') < editor.index('attempt.stage("page-root-read")')
    def valid_attempt(a):
        page, stage, values = a['fixture'], a['stage'], a['values']
        assert a['mainThread'] is True
        assert isinstance(a['startedElapsedRealtimeNanos'], int)
        assert isinstance(a['endedElapsedRealtimeNanos'], int)
        assert a['endedElapsedRealtimeNanos'] >= a['startedElapsedRealtimeNanos']
        assert a['ownerIdentity'] is not None and a['ownerClass']
        for count in (('rawCount', 'middleCount', 'hostCount') if page == 'import'
                      else ('targetCount', 'hostCount')):
            status = values[count + 'Status']
            assert status in ('evaluated', 'not_evaluated')
            assert (values[count] is None) == (status == 'not_evaluated')
        if stage == 'first-check' and page == 'import':
            assert values['rawCountStatus'] == 'evaluated'
            assert (values['rawCount'] != 1 or values['middleCount'] != 1)
            assert values['middleCountStatus'] == ('evaluated' if values['rawCount'] == 1 else 'not_evaluated')
            assert values['hostCountStatus'] == 'not_evaluated'
        if stage == 'second-check' and page == 'import':
            assert values['rawCount'] == values['middleCount'] == 1
            assert values['hostCount'] in (None, 0, 2)
            assert values['hostCount'] is None or values['hostCountStatus'] == 'evaluated'
        if stage == 'first-check' and page == 'editor':
            assert values['targetNull'] or values['targetCount'] in (0, 2)
            assert values['targetCountStatus'] == ('not_evaluated' if values['targetNull'] else 'evaluated')
            assert values['hostCountStatus'] == 'not_evaluated'
        if stage == 'second-check' and page == 'editor':
            assert values['targetNull'] is False and values['targetCount'] == 1
            assert values['hostCount'] in (0, 2)
        if stage == 'targets':
            assert a['result'] == 'exact_named_references'
            assert 'resolutionFailureClass' not in a
        else:
            assert a['result'] == 'fallback_partial'
            assert a['resolutionFailureClass'] and a['resolutionFailureMessage']
            assert a['resolutionFailureStack'].startswith(a['resolutionFailureClass'] + ': ' + a['resolutionFailureMessage'])
    def make_attempt(page, stage, values, failure_class='java.lang.IllegalStateException', message='check failed'):
        a={'fixture':page,'stage':stage,'mainThread':True,'startedElapsedRealtimeNanos':1,
           'endedElapsedRealtimeNanos':2,'ownerIdentity':101,'ownerClass':'Activity',
           'values':values,'result':'fallback_partial',
           'resolutionFailureClass':failure_class,'resolutionFailureMessage':message,
           'resolutionFailureStack':failure_class + ': ' + message + '\n  at Resolver.resolve(Resolver.kt:1)'}
        valid_attempt(a)
        return a
    def import_values(raw=None, middle=None, host=None):
        result={}
        for name,value in (('rawCount',raw),('middleCount',middle),('hostCount',host)):
            result[name]=value
            result[name+'Status']='not_evaluated' if value is None else 'evaluated'
        return result
    def editor_values(target=None, host=None, target_null=False):
        return {**{'targetNull':target_null},
                **{key:val for name,value in (('targetCount',target),('hostCount',host))
                   for key,val in ((name,value),(name+'Status','not_evaluated' if value is None else 'evaluated'))}}
    # Each fixture has a different first and second check and different early read failures.
    import_cases=[make_attempt('import','first-check',import_values(0)),
                  make_attempt('import','first-check',import_values(2)),
                  make_attempt('import','first-check',import_values(1,0)),
                  make_attempt('import','first-check',import_values(1,2)),
                  make_attempt('import','second-check',import_values(1,1,None)),
                  make_attempt('import','second-check',import_values(1,1,0)),
                  make_attempt('import','second-check',import_values(1,1,2))]
    editor_cases=[make_attempt('editor','first-check',editor_values(target_null=True)),
                  make_attempt('editor','first-check',editor_values(0)),
                  make_attempt('editor','first-check',editor_values(2)),
                  make_attempt('editor','second-check',editor_values(1,0)),
                  make_attempt('editor','second-check',editor_values(1,2))]
    early_import=make_attempt('import','form-scroll-read',import_values(),
                              'java.lang.ClassCastException','formScroll is not ScrollView')
    early_editor=make_attempt('editor','form-reflection',editor_values(1),
                              'java.lang.NoSuchFieldException','form')
    assert early_import['resolutionFailureClass'] != early_editor['resolutionFailureClass']
    assert early_import['resolutionFailureStack'] != early_editor['resolutionFailureStack']
    success=deepcopy(editor_cases[-1]);success['stage']='targets'
    success['values']=editor_values(1,1)
    for key in ('resolutionFailureClass','resolutionFailureMessage','resolutionFailureStack'):
        success.pop(key)
    success['result']='exact_named_references';valid_attempt(success)
    for bad in (lambda a:a['values'].update(middleCount=0,middleCountStatus='evaluated'),
                lambda a:a.update(mainThread=False),
                lambda a:a.pop('resolutionFailureStack')):
        candidate=deepcopy(import_cases[0]);bad(candidate)
        try: valid_attempt(candidate)
        except (AssertionError,KeyError): pass
        else: raise AssertionError('resolver counterexample unexpectedly passed')
    # Simulate one diagnostic put failure: each finish field is isolated, and fallback stays partial.
    assert source.count('safely { proof.put("resolutionFailure') == 3
    def finish_model(original, fail_key):
        proof={'stage':'form-reflection'};caught=original;resolve_calls=1
        issues=['resolve_replacement-post-created_'+caught['resolutionFailureClass']]
        writes={'result':'fallback_partial','resolutionFailureClass':original['resolutionFailureClass'],
                'resolutionFailureMessage':original['resolutionFailureMessage'],
                'resolutionFailureStack':original['resolutionFailureStack']}
        for key,value in writes.items():
            if key==fail_key:
                proof['diagnosticFailure']='org.json.JSONException: failed '+key
                issues.append('resolution_diagnostic_replacement-post-created_org.json.JSONException')
            else: proof[key]=value
        status='PARTIAL_OR_ERROR' if issues else 'COMPLETE'
        return proof,issues,resolve_calls,caught,status
    for failed in ('result','resolutionFailureClass','resolutionFailureMessage','resolutionFailureStack'):
        proof,issues,calls,caught,status=finish_model(early_editor,failed)
        assert calls==1 and caught is early_editor and status=='PARTIAL_OR_ERROR'
        assert len(issues)==2 and proof['diagnosticFailure']
        assert all(proof[key]==early_editor[key] for key in
                   ('resolutionFailureClass','resolutionFailureMessage','resolutionFailureStack') if key!=failed)
    distinct=[deepcopy(import_cases[0]),deepcopy(editor_cases[0])]
    distinct[0]['ownerIdentity']=101;distinct[1]['ownerIdentity']=202
    assert len({a['ownerIdentity'] for a in distinct})==2
    fallback={'resolution':'unknown_named_reference_resolution','issues':['resolve_replacement-post-created_java.lang.IllegalStateException']}
    assert fallback['issues'] and fallback['resolution']=='unknown_named_reference_resolution'
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
