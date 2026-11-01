# Curve delivery preview workspace

Approved spec §7 CurveShare/CurveImportPreview and §9; F4. Continue feature/task-first-ui/develop draft PR#4 with recorded develop66ebda9/ed01de0 base; docs/git-workflow.md applies. No merge/release/device install or external share invocation. UI-only additive revert preserves data.

Shared profileCard currently puts name/capability/plot/caption/summary/details in one vertical strip. Use stable TaskWorkspace inside the existing bounded card: primary name/capability/actual pressure stages; supporting full temperature/target/all-stage parameters and Full details. Narrow windows stack with bounded supporting scroll, large valid windows split. Explicit stage-not-time caption remains visible, flow-only source keeps raw/read-only warning and never fabricates a pressure plot. Full name/rawJSON details stay reachable. Existing caller share/clipboard/SAF/save/conflict/idempotency callbacks unchanged; preview is display-only.

Only profileCard helper in CurveShareActivity, this plan and evidence. Existing native advanced page fixtures, four-stage business persistence, import conflicts, SAF/clipboard and single-screen stage-height assertions remain. Local builds then screenshots/independent review; no acceptance inferred from compilation.
