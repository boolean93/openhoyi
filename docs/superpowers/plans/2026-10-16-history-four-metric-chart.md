# Historical four-metric chart

Approved spec §5, §7 HistoryDetail, §9 and roadmap F2/F5. Continue feature/task-first-ui (develop 66ebda9 with ed01de0 dependency), existing draft PR #4/develop. Follow docs/git-workflow.md. WIP CI allowed after local verification, independent review/native evidence required before acceptance. No schema/protocol/control/storage changes, merge/release/physical install. Additive revert only.

Evidence: ShotChartView still draws machineFlowTenthsMlPerSecond and scaleFlowHundredths alongside pressure/weight/temperature, old 11dp text, and bridges gaps. Historical sample waterTenthsMl is already available. This contradicts shared four-metric semantics.

- Replace historical rendering implementation with a thin stable native wrapper of ExtractionOverlayView. Preserve public points API and class identity for page routing. Read-only points; reset previous display ranges when a different historical data list is assigned; no inferred yield/tare, no integration, no synthetic missing samples. Missing scale hides weight; gap/reset logic uses existing tested projection.
- Update native legend checks to inspect real native legend names/units/ranges and focus targets, plot text size and all four physical series across original 8 languages/2 themes/4 widths. Verify absent weight removes the fourth legend; no ml/s or g/s in primary chart. Preserve legacy scientific ordering checks and explicitly measure new view hierarchy before drawing.
- Historical page must retain raw scale label, original status/unknown/reason, full notes/review/compare. Page layout migration remains separate; this batch does not claim full HistoryDetail acceptance.
- Run JVM + both Mock APK builds, native language/legend and single-screen CI with screenshots. Local build cannot replace native assertions. Existing project agent account limit means independent review pending.

Original machine/scale flow remains accessible from full details through an explicit advanced flow-sample table. Uses actual elapsedMs/ml-per-second/optional g-per-second, missing remains —, no integrated quantities or fabricated wall clock. Changes HistoryDetailActivity and eight task_history.xml files solely for this source-preserving advanced view.
