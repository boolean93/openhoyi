# Extraction Workbench Implementation Plan

> REQUIRED SUB-SKILL: superpowers:subagent-driven-development. Implementer then independent spec and quality reviews; root runs native and CI verification.

## Goal / scope

Implement approved task-first spec §5/6/9 on real ExtractionActivity. One four-metric shared-time chart, fixed safety and stop, prominent elapsed time and actual quantity. No machine commands, gates, storage, tare evidence, notification eligibility or feedback ownership changes. No fake output or future prediction. Keep whole page non-scrolling; only supporting details locally scroll. PR#4 stays draft, no merge/release/device install.

Current actual phone screenshot is too crowded; ru/es single-screen tests report plot GONE. Do not lower plot minima or skip valid plot checks. Home uses a stable TaskWorkspaceLayout; reuse its actual width/height policy, not only HoyiUi.wide. Real default normal window must retain usable plot, wide chart >=40% of content. Whole 21-page goal remains.

## Implementer ownership

- ExtractionActivity.kt only layout construction and read-only presentation fields/render; preserve root's already-written reference source/alignment fix.
- New values*/task_extraction.xml all 8 languages, no MissingTranslation suppression.
- Do not change chart widget/projection, TaskWorkspaceLayout, MobileService/control methods, tests/CI, other workers' files.

## Required changes

- Use TaskWorkspaceLayout weight1 inside content. Primary has compact two-column native readings (elapsed and quantity), single chart gets all remaining primary height. Supporting local scroll contains actual curve/device summary and original toolbar/secondary actions, not a second whole-page scroller. Retain every callback/disabled gate/slot identity.
- Default quantity: fresh raw scale value labelled 秤读数 when any scale data is used; otherwise machine cumulative water ml labelled 累计注水. A connected-but-stale scale shows — g, never a stale value; no-scale uses water ml. Never claim 出液量 without valid cup reference. Existing flowValue remains only in details (advanced cup-derived g/s), never default primary card.
- Pressure and raw temperature remain compact subordinate native text with actual units, not four equal cards. Keep machine temperature distinct from corrected readiness temperature. No injected flow integration.
- Safety readiness, notification permission warnings, all failed/writing/unknown preparation and alarms remain fixed outside supporting scroll. Original render priority and stop/start conditional actions must not be weakened. Normal idle explanatory preparation prose may move to supporting panel only if non-actionable; WRITING/WAITING_TEMP/CANCELLING/FAILED/UNKNOWN/CANCEL_WRITTEN remain fixed. Avoid hiding unknown or stale alarms.
- Fixed start/stop and original confirmStart/confirmPrepare/cancel/stop callbacks unchanged. No shared Home confirm extraction in this batch (next task).
- Header/curves/details remain labelled/reachable; active/unknown/manual must not imply a pref curve is actual cup. Don’t rebuild views or callbacks every250ms, no writes in render.
- Preserve saved reference id and details source/alignment, focus-only gray reference. Existing root reference fix must remain.
- New strings complete all8 locales. Min target48dp, native text not shrunk merely to fit. Labels/ranges remain legible at1.3, no whole page scrolling. Preserve RTL.

## Verification (root)

- Static spec then quality review; compare existing callbacks and safety branches exactly. Run existing295 JVM and both Mock APK builds with exact SDK aapt2 override. Run original language/lifecycle/17 business/upgrade checks plus unchanged normal plot geometry.
- New Home normal/180dp/font1.3/recovery native checks in same next CI push. Actual tablet landscape/portrait and phone screenshots read after CI; not claim completed based on compile.
