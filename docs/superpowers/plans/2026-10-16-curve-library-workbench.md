# Curve library task workspace implementation plan

> REQUIRED SUB-SKILL: superpowers:subagent-driven-development; independent spec then quality review. Existing approved design and no new permission gate.

## Scope / ownership

Only CurveActivity.kt, new TaskCollectionLayout.kt if needed, and8locales new task_curve_library.xml. Other workers own Extraction/Comparison; root owns tests/CI. No schema/protocol/start/usage/counting change. All6curve pages remain roadmap scope; this batch handles library only, no claim others complete. No push/release/device install or Gradle without root window.

## Observed evidence / requirements

56d0684 landscape real screenshot: browser tools occupy roughly top40%, list only ~3.5rows; blank preview huge white if no selected. Current HoyiUi.wide>=600 forces2panes even711dp user tablet portrait, contradicts spec840/min content thresholds. Keep continuous ListView, sorting recent/most-used and category/search/position. No pagination or extra screen for primary selection.

- Use actual measured width/height plus TaskLayoutPolicy for2panes, not HoyiUi.wide alone. Only>=840window and actual main320/support280+24gap andheight>=360. Approx40% list/60% preview without violating min widths. 600–839 and narrow must list→detail with explicitback, preserve selected/query/category/sort/liststate. Resizing cannot recreate fields, lose focus, rebind observers each frame or writeprefs; only orientation/params/show state. Back callback remains lifecycle safe.
- Reduce decorative/action height: new/import/legacy are explicit secondary management tools, may group in single clear native menu instead of2full-width toolbar rows. All original actions remain explicit/reachable, disabled customStore preserved. Existing tests route by original labels; root will adapt actual menu interaction if necessary, never weaken save/share/import checks. Keep sort selector and horizontal category row>=48dp, no clipping longlanguage or1.3text. No full-page scroll.
- Preview title, real stage schematic with full pressureunits and “不是时间预测” always visible for validtargets; no pressure source gives explicit actual reason not a blank graph. Raw flow-only source never fabricated pressure. No selected item shows intentional compact empty prompt rather than giant empty card, no fake series. Can preview first visible item automatically only as local browser preview (not pref selection/start/usage), but prefer preserve original no-selection if unclear.
- One primary “用于本杯” fixed in detail area, original canStart and pref callback unchanged (no automatic execution). Secondary share/edit-or-copy/assignslot/parameterdetails remain accessible, not4equal primary buttons. Full name/parameters can be reached, summary ellipsis never written to model. Continuous row thumbnails still pressure-only schematic, source/usage count honest; stats failure distinct fromzero.
- Filtering selected out must not leave actions pointing to another row; no implicit writes. Existing sortpref persistence only original user interaction, no usageincrement on preview. Source readonlylegacy unchanged.
- Main pressure plot in normal preview>=96dp. Short-height explicit details/fallback if truly cannot plot, no silently cropped axes, no font shrink. Root retains SingleScreen checks, listposition and all17business paths.

## Verification

Read diff for callbacks/source/no writes,8locale XML/keys, independent spec/quality. Root unified295JVM+2APK/CI3windows+8language2theme/listrestore tests and read screenshots. Static or compile alone is not visual acceptance. Keep PR#4draft and whole21pages not done.

## Git / delivery boundary

Continue existing `feature/task-first-ui` worktree, originally from `develop` (`66ebda9`) with the explicitly recorded advanced-feature dependency merge (`ed01de0`). Target `develop` through existing draft PR #4; feature-branch WIP push may collect CI evidence after local verification; independent review remains required before batch acceptance, never direct push or force-push to protected branches. Follow `docs/git-workflow.md`. No release, tag, signed artifact publication, physical-device install, schema/protocol or device control change. CI uses Mock emulator package only. Roll back this UI batch through a new revert commit on the feature branch, preserving stored user data and unrelated changes; no reset of shared history. Full required CI and visual evidence remain mandatory before merge, which is not authorized in this batch.
